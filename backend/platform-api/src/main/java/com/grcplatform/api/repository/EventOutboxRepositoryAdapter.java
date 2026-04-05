package com.grcplatform.api.repository;

import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.repository.EventOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class EventOutboxRepositoryAdapter implements EventOutboxRepository {

    private final SpringEventOutboxRepository jpa;

    public EventOutboxRepositoryAdapter(SpringEventOutboxRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EventOutbox save(EventOutbox event) {
        return jpa.save(event);
    }

    @Override
    public void saveAll(List<EventOutbox> events) {
        jpa.saveAll(events);
    }

    @Override
    public List<EventOutbox> findPendingEvents(int limit) {
        return jpa.findPendingWithLimit(PageRequest.of(0, limit));
    }

    @Override
    public void markProcessed(UUID id) {
        jpa.markProcessed(id);
    }

    @Override
    public void markFailed(UUID id, String errorMessage) {
        jpa.markFailed(id, errorMessage);
    }
}
