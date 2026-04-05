package com.grcplatform.workflow;

import com.grcplatform.core.domain.WorkflowDefinition;
import com.grcplatform.core.domain.WorkflowHistory;
import com.grcplatform.core.domain.WorkflowInstance;
import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.WorkflowConcurrentModificationException;
import com.grcplatform.core.exception.WorkflowTransitionException;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.WorkflowDefinitionRepository;
import com.grcplatform.core.repository.WorkflowHistoryRepository;
import com.grcplatform.core.repository.WorkflowInstanceRepository;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import com.grcplatform.core.workflow.StartWorkflowCommand;
import com.grcplatform.core.workflow.TransitionCommand;
import com.grcplatform.core.workflow.WorkflowConfig;
import com.grcplatform.core.workflow.WorkflowConfig.TransitionConfig;
import com.grcplatform.core.workflow.WorkflowConfigParser;
import com.grcplatform.core.workflow.WorkflowInstanceDto;
import com.grcplatform.core.workflow.WorkflowService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State machine implementation for workflow execution.
 *
 * Rules:
 * - Transitions are validated against WorkflowDefinition config before execution.
 * - Optimistic lock on workflow_instances.version prevents parallel completion race.
 * - All side effects (notifications, tasks) are published to event_outbox — never called directly.
 * - org_id is resolved from the existing WorkflowInstance — never passed directly.
 */
public class WorkflowEngine implements WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowHistoryRepository historyRepository;
    private final WorkflowTaskRepository taskRepository;
    private final EventOutboxRepository outboxRepository;
    private final WorkflowConfigParser configParser;
    private final WorkflowOutboxPublisher outboxPublisher;

    public WorkflowEngine(WorkflowDefinitionRepository definitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowHistoryRepository historyRepository,
            WorkflowTaskRepository taskRepository,
            EventOutboxRepository outboxRepository,
            WorkflowConfigParser configParser,
            WorkflowOutboxPublisher outboxPublisher) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.historyRepository = historyRepository;
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.configParser = configParser;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    @Transactional
    public WorkflowInstanceDto start(StartWorkflowCommand command) {
        UUID orgId = SessionContextHolder.SESSION.get().orgId();
        WorkflowDefinition definition = definitionRepository
                .findActiveByApplicationId(orgId, command.applicationId())
                .orElseThrow(() -> new RecordNotFoundException("WorkflowDefinition", command.applicationId()));

        WorkflowConfig config = configParser.parse(definition.getConfig());

        WorkflowInstance instance = new WorkflowInstance();
        instance.setOrgId(definition.getOrgId());
        instance.setRecordId(command.recordId());
        instance.setDefinitionId(definition.getId());
        instance.setCurrentState(config.initialState());
        instance.setStatus("active");
        WorkflowInstance saved = instanceRepository.save(instance);

        outboxPublisher.publishWorkflowStarted(saved, config.initialState(), command.actorId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public WorkflowInstanceDto transition(TransitionCommand command) {
        WorkflowInstance instance = instanceRepository.findById(command.instanceId())
                .orElseThrow(() -> new RecordNotFoundException("WorkflowInstance", command.instanceId()));

        WorkflowDefinition definition = definitionRepository.findById(instance.getDefinitionId())
                .orElseThrow(() -> new RecordNotFoundException("WorkflowDefinition", instance.getDefinitionId()));

        WorkflowConfig config = configParser.parse(definition.getConfig());
        TransitionConfig transition = resolveTransition(config, instance.getCurrentState(),
                command.transitionKey());

        if (transition.requireComment() && (command.comment() == null || command.comment().isBlank())) {
            throw new WorkflowTransitionException(
                    "Transition '" + command.transitionKey() + "' requires a comment");
        }

        String targetState = transition.toState();
        String newStatus = isTerminalState(config, targetState) ? "completed" : "active";

        int updated = instanceRepository.updateStateIfVersion(
                instance.getId(), targetState, newStatus, instance.getVersion());
        if (updated == 0) {
            throw new WorkflowConcurrentModificationException(instance.getId());
        }

        writeHistory(instance, transition, command);
        cancelOpenTasks(instance.getId());
        createTasksForActions(instance, transition, command.actorId());
        outboxPublisher.publishTransitioned(instance, transition, command.actorId(), targetState);

        // Re-read to get updated version
        WorkflowInstance refreshed = instanceRepository.findById(instance.getId()).orElseThrow();
        return toDto(refreshed);
    }

    @Override
    public WorkflowInstanceDto findByRecordId(UUID orgId, UUID recordId) {
        return instanceRepository.findByRecordId(orgId, recordId)
                .map(this::toDto)
                .orElse(null);
    }

    // ---- private helpers ----

    private TransitionConfig resolveTransition(WorkflowConfig config, String currentState,
            String transitionKey) {
        TransitionConfig transition = config.transitionByKey(transitionKey);
        if (transition == null) {
            throw new WorkflowTransitionException(
                    "Unknown transition key: '" + transitionKey + "'");
        }
        if (!transition.fromStates().contains(currentState)) {
            throw new WorkflowTransitionException(
                    "Transition '" + transitionKey + "' is not allowed from state '" + currentState
                            + "'");
        }
        return transition;
    }

    private boolean isTerminalState(WorkflowConfig config, String stateKey) {
        WorkflowConfig.StateConfig state = config.stateByKey(stateKey);
        return state != null && state.terminal();
    }

    private void writeHistory(WorkflowInstance instance, TransitionConfig transition,
            TransitionCommand command) {
        WorkflowHistory history = new WorkflowHistory();
        history.setOrgId(instance.getOrgId());
        history.setInstanceId(instance.getId());
        history.setFromState(instance.getCurrentState());
        history.setToState(transition.toState());
        history.setTransitionKey(transition.key());
        history.setActorId(command.actorId());
        history.setComment(command.comment());
        historyRepository.save(history);
    }

    private void cancelOpenTasks(UUID instanceId) {
        taskRepository.cancelByInstanceId(instanceId);
    }

    private void createTasksForActions(WorkflowInstance instance, TransitionConfig transition,
            UUID actorId) {
        if (transition.onEnterActions() == null) return;
        List<WorkflowTask> tasks = transition.onEnterActions().stream()
                .filter(a -> "create_task".equals(a.type()))
                .map(action -> buildTask(instance, action))
                .toList();
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }
    }

    private WorkflowTask buildTask(WorkflowInstance instance,
            WorkflowConfig.ActionConfig action) {
        WorkflowTask task = new WorkflowTask();
        task.setOrgId(instance.getOrgId());
        task.setInstanceId(instance.getId());
        task.setTaskKey(action.label() != null ? action.label() : action.type());
        task.setDueDate(Instant.now().plus(7, ChronoUnit.DAYS));
        return task;
    }

    private WorkflowInstanceDto toDto(WorkflowInstance instance) {
        return new WorkflowInstanceDto(
                instance.getId(),
                instance.getRecordId(),
                instance.getDefinitionId(),
                instance.getCurrentState(),
                instance.getStatus(),
                instance.getEnteredStateAt(),
                instance.getVersion());
    }
}
