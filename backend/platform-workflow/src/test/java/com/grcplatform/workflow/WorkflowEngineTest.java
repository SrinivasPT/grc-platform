package com.grcplatform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.domain.WorkflowDefinition;
import com.grcplatform.core.domain.WorkflowHistory;
import com.grcplatform.core.domain.WorkflowInstance;
import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.exception.WorkflowConcurrentModificationException;
import com.grcplatform.core.exception.WorkflowTransitionException;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.WorkflowDefinitionRepository;
import com.grcplatform.core.repository.WorkflowHistoryRepository;
import com.grcplatform.core.repository.WorkflowInstanceRepository;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import com.grcplatform.core.workflow.TransitionCommand;
import com.grcplatform.core.workflow.WorkflowConfigParser;
import com.grcplatform.core.workflow.WorkflowInstanceDto;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    static final String SIMPLE_CONFIG = """
            {
              "id": "test-wf",
              "name": "Test Workflow",
              "appKey": "test",
              "version": 1,
              "initialState": "draft",
              "states": [
                {"key": "draft",     "label": "Draft",     "terminal": false},
                {"key": "in_review", "label": "In Review", "terminal": false},
                {"key": "approved",  "label": "Approved",  "terminal": false},
                {"key": "closed",    "label": "Closed",    "terminal": true}
              ],
              "transitions": [
                {
                  "key": "submit",
                  "label": "Submit",
                  "fromStates": ["draft"],
                  "toState": "in_review",
                  "requireComment": false,
                  "onEnterActions": [
                    {"type": "create_task", "label": "Review Risk"}
                  ]
                },
                {
                  "key": "approve",
                  "label": "Approve",
                  "fromStates": ["in_review"],
                  "toState": "approved",
                  "requireComment": false
                },
                {
                  "key": "close",
                  "label": "Close",
                  "fromStates": ["approved"],
                  "toState": "closed",
                  "requireComment": false
                },
                {
                  "key": "reject",
                  "label": "Reject",
                  "fromStates": ["in_review"],
                  "toState": "draft",
                  "requireComment": true
                }
              ]
            }
            """;

    @Mock
    WorkflowDefinitionRepository definitionRepo;
    @Mock
    WorkflowInstanceRepository instanceRepo;
    @Mock
    WorkflowHistoryRepository historyRepo;
    @Mock
    WorkflowTaskRepository taskRepo;
    @Mock
    EventOutboxRepository outboxRepo;

    WorkflowEngine engine;
    UUID orgId;
    UUID actorId;
    UUID sharedDefId;

    @BeforeEach
    void setUp() {
        WorkflowConfigParser parser = new WorkflowConfigParser();
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowOutboxPublisher publisher = new WorkflowOutboxPublisher(outboxRepo, objectMapper);
        engine = new WorkflowEngine(definitionRepo, instanceRepo, historyRepo, taskRepo, outboxRepo,
                parser, publisher);
        orgId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        sharedDefId = UUID.randomUUID();
    }

    // ---- transition_movesInstance_toNextState ----

    @Test
    void transition_movesInstance_toNextState() {
        WorkflowInstance instance = instanceInState("draft", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));
        when(instanceRepo.updateStateIfVersion(any(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "submit", actorId, null);
        engine.transition(cmd);

        verify(instanceRepo).updateStateIfVersion(instance.getId(), "in_review", "active", 0);
        verify(historyRepo).save(any(WorkflowHistory.class));
    }

    // ---- transition_blocks_onInvalidFromState ----

    @Test
    void transition_blocks_onInvalidFromState() {
        WorkflowInstance instance = instanceInState("draft", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "approve", actorId, null);

        assertThatThrownBy(() -> engine.transition(cmd))
                .isInstanceOf(WorkflowTransitionException.class)
                .hasMessageContaining("not allowed from state 'draft'");
    }

    // ---- transition_requiresComment_whenFlagSet ----

    @Test
    void transition_requiresComment_whenFlagSet() {
        WorkflowInstance instance = instanceInState("in_review", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "reject", actorId, null);

        assertThatThrownBy(() -> engine.transition(cmd))
                .isInstanceOf(WorkflowTransitionException.class)
                .hasMessageContaining("requires a comment");
    }

    // ---- transition_throwsOptimisticLock_onConcurrentCompletion ----

    @Test
    void transition_throwsOptimisticLock_onConcurrentCompletion() {
        WorkflowInstance instance = instanceInState("draft", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));
        when(instanceRepo.updateStateIfVersion(any(), anyString(), anyString(), anyInt()))
                .thenReturn(0); // simulates concurrent modification

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "submit", actorId, null);

        assertThatThrownBy(() -> engine.transition(cmd))
                .isInstanceOf(WorkflowConcurrentModificationException.class);
    }

    // ---- transition_setsStatusCompleted_onTerminalState ----

    @Test
    void transition_setsStatusCompleted_onTerminalState() {
        WorkflowInstance instance = instanceInState("approved", 2);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));
        when(instanceRepo.updateStateIfVersion(any(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "close", actorId, null);
        engine.transition(cmd);

        // "closed" is terminal → newStatus should be "completed"
        verify(instanceRepo).updateStateIfVersion(instance.getId(), "closed", "completed", 2);
    }

    // ---- transition_createsTask_forCreateTaskAction ----

    @Test
    void transition_createsTask_forCreateTaskAction() {
        WorkflowInstance instance = instanceInState("draft", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));
        when(instanceRepo.updateStateIfVersion(any(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "submit", actorId, null);
        engine.transition(cmd);

        verify(taskRepo).saveAll(argThat(tasks -> !((List<?>) tasks).isEmpty()));
    }

    // ---- allSideEffects_goThroughOutbox_notDirectCalls ----

    @Test
    void allSideEffects_goThroughOutbox_notDirectCalls() {
        WorkflowInstance instance = instanceInState("draft", 0);
        WorkflowDefinition def = definitionWithConfig();

        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(definitionRepo.findById(def.getId())).thenReturn(Optional.of(def));
        when(instanceRepo.updateStateIfVersion(any(), anyString(), anyString(), anyInt()))
                .thenReturn(1);
        when(instanceRepo.findById(instance.getId())).thenReturn(Optional.of(instance));

        TransitionCommand cmd = new TransitionCommand(instance.getId(), "submit", actorId, null);
        engine.transition(cmd);

        // All side effects go through outbox
        verify(outboxRepo, atLeastOnce()).save(any());
        // Notification service is NEVER called directly (not injected into WorkflowEngine)
    }

    // ---- helpers ----

    private WorkflowInstance instanceInState(String state, int version) {
        WorkflowInstance inst = new WorkflowInstance();
        setField(inst, "id", UUID.randomUUID());
        setField(inst, "orgId", orgId);
        setField(inst, "recordId", UUID.randomUUID());
        setField(inst, "definitionId", sharedDefId);
        setField(inst, "currentState", state);
        setField(inst, "status", "active");
        setField(inst, "enteredStateAt", Instant.now());
        setField(inst, "createdAt", Instant.now());
        setField(inst, "updatedAt", Instant.now());
        setField(inst, "version", version);
        return inst;
    }

    private WorkflowDefinition definitionWithConfig() {
        WorkflowDefinition def = new WorkflowDefinition();
        setField(def, "id", sharedDefId);
        setField(def, "orgId", orgId);
        setField(def, "applicationId", UUID.randomUUID());
        setField(def, "name", "Test WF");
        setField(def, "config", SIMPLE_CONFIG);
        setField(def, "active", true);
        setField(def, "version", 1L);
        return def;
    }

    /** Sets a private field by name using reflection (avoids exposing setters just for tests). */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            Field f;
            try {
                f = clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                f = clazz.getSuperclass().getDeclaredField(fieldName);
            }
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
