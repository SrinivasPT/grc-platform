package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowTask;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkflowTaskRepositoryAdapter implements WorkflowTaskRepository {

    private final SpringWorkflowTaskRepository jpa;

    public WorkflowTaskRepositoryAdapter(SpringWorkflowTaskRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkflowTask save(WorkflowTask task) {
        return jpa.save(task);
    }

    @Override
    public void saveAll(List<WorkflowTask> tasks) {
        jpa.saveAll(tasks);
    }

    @Override
    public Optional<WorkflowTask> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<WorkflowTask> findByInstanceId(UUID instanceId) {
        return jpa.findByInstanceId(instanceId);
    }

    @Override
    public List<WorkflowTask> findByAssignedToAndStatus(UUID orgId, UUID assignedTo, String status) {
        return jpa.findByOrgIdAndAssignedToAndStatus(orgId, assignedTo, status);
    }

    @Override
    public List<WorkflowTask> findOverdueTasks(UUID orgId, Instant cutoff) {
        // orgId = SYSTEM sentinel (00000000...) means scan all orgs
        return jpa.findOverdueWithCutoff(cutoff);
    }

    @Override
    @Transactional
    public void cancelByInstanceId(UUID instanceId) {
        jpa.cancelByInstanceId(instanceId);
    }
}
