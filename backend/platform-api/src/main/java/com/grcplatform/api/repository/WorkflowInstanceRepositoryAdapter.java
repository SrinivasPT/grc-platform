package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowInstance;
import com.grcplatform.core.repository.WorkflowInstanceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkflowInstanceRepositoryAdapter implements WorkflowInstanceRepository {

    private final SpringWorkflowInstanceRepository jpa;

    public WorkflowInstanceRepositoryAdapter(SpringWorkflowInstanceRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkflowInstance save(WorkflowInstance instance) {
        return jpa.save(instance);
    }

    @Override
    public Optional<WorkflowInstance> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<WorkflowInstance> findByRecordId(UUID orgId, UUID recordId) {
        return jpa.findActiveByRecordId(orgId, recordId);
    }

    @Override
    @Transactional
    public int updateStateIfVersion(UUID id, String newState, String newStatus,
            int expectedVersion) {
        return jpa.updateStateIfVersion(id, newState, newStatus, expectedVersion);
    }
}
