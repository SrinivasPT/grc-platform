package com.grcplatform.notification.delivery;

import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.domain.InAppNotification;
import com.grcplatform.core.repository.InAppNotificationRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Delivers in-app (bell-icon) notifications for workflow events.
 * Notifications are stored in the in_app_notifications table and polled by the frontend.
 */
public class InAppDeliveryService {

    private final InAppNotificationRepository notificationRepository;

    public InAppDeliveryService(InAppNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notifyWorkflowStarted(EventOutbox event, Map<String, Object> payload) {
        UUID actorId = uuidFrom(payload, "actorId");
        String recordId = stringFrom(payload, "recordId");
        String initialState = stringFrom(payload, "initialState");
        notificationRepository.save(InAppNotification.create(
                event.getOrgId(),
                actorId,
                "Workflow started",
                "A workflow has been started. Initial state: " + initialState,
                "/records/" + recordId));
    }

    public void notifyTransitioned(EventOutbox event, Map<String, Object> payload) {
        UUID actorId = uuidFrom(payload, "actorId");
        String recordId = stringFrom(payload, "recordId");
        String fromState = stringFrom(payload, "fromState");
        String toState = stringFrom(payload, "toState");
        notificationRepository.save(InAppNotification.create(
                event.getOrgId(),
                actorId,
                "Workflow state changed",
                "Workflow transitioned from '" + fromState + "' to '" + toState + "'",
                "/records/" + recordId));
    }

    public void notifyEscalated(EventOutbox event, Map<String, Object> payload) {
        UUID escalatedTo = uuidFrom(payload, "escalatedTo");
        String taskId = stringFrom(payload, "taskId");
        notificationRepository.save(InAppNotification.create(
                event.getOrgId(),
                escalatedTo,
                "Task escalated to you",
                "A workflow task has been escalated to you.",
                "/tasks/" + taskId));
    }

    private static String stringFrom(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : "";
    }

    private static UUID uuidFrom(Map<String, Object> payload, String key) {
        String v = stringFrom(payload, key);
        if (v.isEmpty()) return null;
        return UUID.fromString(v);
    }
}
