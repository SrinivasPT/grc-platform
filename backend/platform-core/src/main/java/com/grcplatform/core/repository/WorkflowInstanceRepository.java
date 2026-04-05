package com.grcplatform.core.repository;

import com.grcplatform.core.domain.WorkflowInstance;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository {

    WorkflowInstance save(WorkflowInstance instance);

    Optional<WorkflowInstance> findById(UUID id);

    Optional<WorkflowInstance> findByRecordId(UUID orgId, UUID recordId);

    /**
     * Optimistic-lock update: sets current_state and increments version only if the current version
     * matches. Returns the number of rows updated (0 = conflict).
     */
    int updateStateIfVersion(UUID id, String newState, String newStatus, int expectedVersion);
}
