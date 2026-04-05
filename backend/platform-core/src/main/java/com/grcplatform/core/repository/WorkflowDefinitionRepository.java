package com.grcplatform.core.repository;

import com.grcplatform.core.domain.WorkflowDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowDefinitionRepository {

    WorkflowDefinition save(WorkflowDefinition definition);

    Optional<WorkflowDefinition> findById(UUID id);

    Optional<WorkflowDefinition> findActiveByApplicationId(UUID orgId, UUID applicationId);

    List<WorkflowDefinition> findByOrgId(UUID orgId);
}
