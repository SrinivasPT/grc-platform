package com.grcplatform.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.domain.WorkflowInstance;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.workflow.WorkflowConfig.TransitionConfig;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes workflow events to the transactional outbox. All outbox writes happen within the
 * caller's transaction (never as a direct side effect).
 */
public class WorkflowOutboxPublisher {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WorkflowOutboxPublisher(EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishWorkflowStarted(WorkflowInstance instance, String initialState,
            UUID actorId) {
        publish(instance.getOrgId(), "WORKFLOW_STARTED", "WorkflowInstance", instance.getId(),
                Map.of("instanceId", instance.getId().toString(), "recordId",
                        instance.getRecordId().toString(), "initialState", initialState, "actorId",
                        actorId.toString()));
    }

    public void publishTransitioned(WorkflowInstance instance, TransitionConfig transition,
            UUID actorId, String toState) {
        publish(instance.getOrgId(), "WORKFLOW_TRANSITIONED", "WorkflowInstance", instance.getId(),
                Map.of("instanceId", instance.getId().toString(), "recordId",
                        instance.getRecordId().toString(), "fromState", instance.getCurrentState(),
                        "toState", toState, "transitionKey", transition.key(), "actorId",
                        actorId.toString()));
    }

    public void publishEscalated(UUID orgId, UUID taskId, UUID instanceId, UUID originalAssignee,
            UUID escalatedTo) {
        publish(orgId, "WORKFLOW_TASK_ESCALATED", "WorkflowTask", taskId,
                Map.of("taskId", taskId.toString(), "instanceId", instanceId.toString(),
                        "originalAssignee",
                        originalAssignee != null ? originalAssignee.toString() : "", "escalatedTo",
                        escalatedTo.toString()));
    }

    private void publish(UUID orgId, String eventType, String aggregateType, UUID aggregateId,
            Map<String, Object> payload) {
        outboxRepository.save(
                EventOutbox.create(orgId, eventType, aggregateType, aggregateId, toJson(payload)));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize outbox payload", e);
        }
    }
}
