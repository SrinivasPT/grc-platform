package com.grcplatform.api.graphql;

import com.grcplatform.api.graphql.dto.StartWorkflowInput;
import com.grcplatform.api.graphql.dto.TransitionWorkflowInput;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.WorkflowHistory;
import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.repository.WorkflowHistoryRepository;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import com.grcplatform.core.workflow.StartWorkflowCommand;
import com.grcplatform.core.workflow.TransitionCommand;
import com.grcplatform.core.workflow.WorkflowInstanceDto;
import com.grcplatform.core.workflow.WorkflowService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL resolver for workflow queries and mutations.
 * No @BatchMapping needed here — WorkflowInstance is a singleton per record, not a collection field.
 */
@Controller
public class WorkflowResolver {

    private final WorkflowService workflowService;
    private final WorkflowHistoryRepository historyRepository;
    private final WorkflowTaskRepository taskRepository;

    public WorkflowResolver(WorkflowService workflowService,
            WorkflowHistoryRepository historyRepository,
            WorkflowTaskRepository taskRepository) {
        this.workflowService = workflowService;
        this.historyRepository = historyRepository;
        this.taskRepository = taskRepository;
    }

    @QueryMapping
    public WorkflowInstanceDto workflowInstance(@Argument UUID recordId) {
        UUID orgId = SessionContextHolder.current().orgId();
        return workflowService.findByRecordId(orgId, recordId);
    }

    @QueryMapping
    public List<WorkflowHistory> workflowHistory(@Argument UUID instanceId) {
        return historyRepository.findByInstanceId(instanceId);
    }

    @QueryMapping
    public List<WorkflowTask> myWorkflowTasks(@Argument String status) {
        var ctx = SessionContextHolder.current();
        String resolvedStatus = status != null ? status : "pending";
        return taskRepository.findByAssignedToAndStatus(ctx.orgId(), ctx.userId(), resolvedStatus);
    }

    @MutationMapping
    public WorkflowInstanceDto startWorkflow(@Argument StartWorkflowInput input) {
        var ctx = SessionContextHolder.current();
        var command = new StartWorkflowCommand(
                input.recordId(), input.applicationId(), ctx.userId());
        return workflowService.start(command);
    }

    @MutationMapping
    public WorkflowInstanceDto transitionWorkflow(@Argument TransitionWorkflowInput input) {
        var ctx = SessionContextHolder.current();
        var command = new TransitionCommand(
                input.instanceId(), input.transitionKey(), ctx.userId(), input.comment());
        return workflowService.transition(command);
    }
}
