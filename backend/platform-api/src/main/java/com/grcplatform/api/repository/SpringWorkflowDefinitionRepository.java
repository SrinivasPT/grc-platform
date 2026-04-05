package com.grcplatform.api.repository;

import com.grcplatform.core.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringWorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    @Query("SELECT d FROM WorkflowDefinition d WHERE d.orgId = :orgId AND d.applicationId = :appId AND d.active = true")
    Optional<WorkflowDefinition> findActiveByOrgIdAndApplicationId(
            @Param("orgId") UUID orgId, @Param("appId") UUID applicationId);
}
