package com.grcplatform.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.notification.delivery.InAppDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Routes outbox events to the correct delivery service based on event_type.
 *
 * Supported event types: - WORKFLOW_STARTED → in-app notification to initiator -
 * WORKFLOW_TRANSITIONED → in-app notification to task assignees - WORKFLOW_TASK_ESCALATED → in-app
 * notification to escalated-to user
 *
 * All other event types are logged and skipped (routed in Phase 4+ by integration module).
 */
public class OutboxEventRouter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRouter.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final InAppDeliveryService inAppDelivery;
    private final ObjectMapper objectMapper;

    public OutboxEventRouter(InAppDeliveryService inAppDelivery, ObjectMapper objectMapper) {
        this.inAppDelivery = inAppDelivery;
        this.objectMapper = objectMapper;
    }

    public void route(EventOutbox event) {
        Map<String, Object> payload = parsePayload(event.getPayload());
        switch (event.getEventType()) {
            case "WORKFLOW_STARTED" -> inAppDelivery.notifyWorkflowStarted(event, payload);
            case "WORKFLOW_TRANSITIONED" -> inAppDelivery.notifyTransitioned(event, payload);
            case "WORKFLOW_TASK_ESCALATED" -> inAppDelivery.notifyEscalated(event, payload);
            default -> log.debug("No handler for event type '{}' (id={}), skipping.",
                    event.getEventType(), event.getId());
        }
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse outbox payload: " + e.getMessage(),
                    e);
        }
    }
}
