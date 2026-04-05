package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowHistory;
import com.grcplatform.core.repository.WorkflowHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class WorkflowHistoryRepositoryAdapter implements WorkflowHistoryRepository {

    private final SpringWorkflowHistoryRepository jpa;

    public WorkflowHistoryRepositoryAdapter(SpringWorkflowHistoryRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkflowHistory save(WorkflowHistory history) {
        return jpa.save(history);
    }

    @Override
    public List<WorkflowHistory> findByInstanceId(UUID instanceId) {
        return jpa.findByInstanceIdOrderByTransitionedAtAsc(instanceId);
    }
}
