package com.grcplatform.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.InAppNotificationRepository;
import com.grcplatform.notification.delivery.InAppDeliveryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock EventOutboxRepository outboxRepo;
    @Mock InAppNotificationRepository notifRepo;

    OutboxWorker worker;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        InAppDeliveryService delivery = new InAppDeliveryService(notifRepo);
        OutboxEventRouter router = new OutboxEventRouter(delivery, objectMapper);
        worker = new OutboxWorker(outboxRepo, router);
    }

    @Test
    void processOutbox_marksProcessed_onSuccess() {
        EventOutbox event = workflowStartedEvent();
        when(outboxRepo.findPendingEvents(anyInt())).thenReturn(List.of(event));

        worker.processOutbox();

        verify(outboxRepo).markProcessed(event.getId());
        verify(outboxRepo, never()).markFailed(any(), anyString());
    }

    @Test
    void processOutbox_marksFailedWithMessage_onException() {
        EventOutbox event = workflowStartedEvent();
        when(outboxRepo.findPendingEvents(anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("db offline")).when(notifRepo).save(any());

        worker.processOutbox();

        verify(outboxRepo).markFailed(eq(event.getId()), anyString());
        verify(outboxRepo, never()).markProcessed(any());
    }

    @Test
    void processOutbox_doesNothing_whenQueueEmpty() {
        when(outboxRepo.findPendingEvents(anyInt())).thenReturn(List.of());

        worker.processOutbox();

        verify(outboxRepo, never()).markProcessed(any());
        verify(outboxRepo, never()).markFailed(any(), any());
    }

    @Test
    void processOutbox_storesInAppNotification_forWorkflowTransitioned() {
        EventOutbox event = workflowTransitionedEvent();
        when(outboxRepo.findPendingEvents(anyInt())).thenReturn(List.of(event));

        worker.processOutbox();

        verify(notifRepo).save(any());
        verify(outboxRepo).markProcessed(event.getId());
    }

    @Test
    void processOutbox_skipsUnknownEventTypes_withoutError() {
        EventOutbox event = EventOutbox.create(UUID.randomUUID(), "UNKNOWN_EVENT",
                "SomeAggregate", UUID.randomUUID(), "{}");
        when(outboxRepo.findPendingEvents(anyInt())).thenReturn(List.of(event));

        worker.processOutbox();

        verify(notifRepo, never()).save(any());
        verify(outboxRepo).markProcessed(event.getId());
    }

    // ---- helpers ----

    private EventOutbox workflowStartedEvent() {
        UUID actorId = UUID.randomUUID();
        String payload = """
                {"instanceId":"%s","recordId":"%s","initialState":"draft","actorId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), actorId);
        return EventOutbox.create(UUID.randomUUID(), "WORKFLOW_STARTED",
                "WorkflowInstance", UUID.randomUUID(), payload);
    }

    private EventOutbox workflowTransitionedEvent() {
        UUID actorId = UUID.randomUUID();
        String payload = """
                {"instanceId":"%s","recordId":"%s","fromState":"draft","toState":"in_review","transitionKey":"submit","actorId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), actorId);
        return EventOutbox.create(UUID.randomUUID(), "WORKFLOW_TRANSITIONED",
                "WorkflowInstance", UUID.randomUUID(), payload);
    }
}
