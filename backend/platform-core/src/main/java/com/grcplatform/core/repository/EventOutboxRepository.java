package com.grcplatform.core.repository;

import com.grcplatform.core.domain.EventOutbox;

import java.util.List;
import java.util.UUID;

public interface EventOutboxRepository {

    EventOutbox save(EventOutbox event);

    void saveAll(List<EventOutbox> events);

    List<EventOutbox> findPendingEvents(int limit);

    void markProcessed(UUID id);

    void markFailed(UUID id, String errorMessage);
}
