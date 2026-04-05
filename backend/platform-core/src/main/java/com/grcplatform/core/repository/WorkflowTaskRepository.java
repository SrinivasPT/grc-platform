package com.grcplatform.core.repository;

import com.grcplatform.core.domain.WorkflowTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTaskRepository {

    WorkflowTask save(WorkflowTask task);

    void saveAll(List<WorkflowTask> tasks);

    Optional<WorkflowTask> findById(UUID id);

    List<WorkflowTask> findByInstanceId(UUID instanceId);

    List<WorkflowTask> findByAssignedToAndStatus(UUID orgId, UUID assignedTo, String status);

    /** Returns tasks whose due_date < cutoff and status = 'pending' (for escalation). */
    List<WorkflowTask> findOverdueTasks(UUID orgId, Instant cutoff);

    void cancelByInstanceId(UUID instanceId);
}
