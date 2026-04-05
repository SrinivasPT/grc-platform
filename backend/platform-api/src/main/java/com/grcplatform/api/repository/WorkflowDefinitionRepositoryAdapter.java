package com.grcplatform.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.WorkflowDefinition;
import com.grcplatform.core.repository.WorkflowDefinitionRepository;

@Repository
public class WorkflowDefinitionRepositoryAdapter implements WorkflowDefinitionRepository {

    private final SpringWorkflowDefinitionRepository jpa;

    public WorkflowDefinitionRepositoryAdapter(SpringWorkflowDefinitionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkflowDefinition save(WorkflowDefinition definition) {
        return jpa.save(definition);
    }

    @Override
    public Optional<WorkflowDefinition> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<WorkflowDefinition> findActiveByApplicationId(UUID orgId, UUID applicationId) {
        return jpa.findActiveByOrgIdAndApplicationId(orgId, applicationId);
    }

    @Override
    public List<WorkflowDefinition> findByOrgId(UUID orgId) {
        return jpa.findAll().stream().filter(d -> orgId.equals(d.getOrgId())).toList();
    }
}
