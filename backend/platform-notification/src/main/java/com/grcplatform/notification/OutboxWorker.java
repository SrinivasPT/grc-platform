package com.grcplatform.notification;

import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.repository.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Polls the event_outbox table and dispatches events to the appropriate consumer.
 *
 * Design:
 * - At-least-once delivery: events may be dispatched more than once on restart; consumers are
 *   idempotent.
 * - Events that exceed MAX_RETRIES are moved to event_outbox_dlq by the repository.
 * - All routing is done by OutboxEventRouter — this class only polls and marks status.
 * - org_id context is NOT required here — this is a background worker.
 */
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int BATCH_SIZE = 50;

    private final EventOutboxRepository outboxRepository;
    private final OutboxEventRouter eventRouter;

    public OutboxWorker(EventOutboxRepository outboxRepository, OutboxEventRouter eventRouter) {
        this.outboxRepository = outboxRepository;
        this.eventRouter = eventRouter;
    }

    @Scheduled(fixedDelayString = "${grc.outbox.poll-interval-ms:2000}")
    public void processOutbox() {
        List<EventOutbox> pending = outboxRepository.findPendingEvents(BATCH_SIZE);
        if (pending.isEmpty()) return;

        log.debug("OutboxWorker: processing {} pending events", pending.size());
        for (EventOutbox event : pending) {
            processEvent(event);
        }
    }

    private void processEvent(EventOutbox event) {
        try {
            eventRouter.route(event);
            outboxRepository.markProcessed(event.getId());
        } catch (Exception e) {
            log.warn("Failed to process outbox event {} (type={}): {}",
                    event.getId(), event.getEventType(), e.getMessage());
            outboxRepository.markFailed(event.getId(), e.getMessage());
        }
    }
}
