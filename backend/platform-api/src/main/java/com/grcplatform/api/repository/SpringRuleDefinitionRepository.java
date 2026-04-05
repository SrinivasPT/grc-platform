package com.grcplatform.api.repository;

import com.grcplatform.core.domain.RuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringRuleDefinitionRepository extends JpaRepository<RuleDefinition, UUID> {

    Optional<RuleDefinition> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("""
            SELECT r FROM RuleDefinition r
            WHERE r.applicationId = :appId AND r.ruleType = :ruleType AND r.orgId = :orgId
            AND r.active = true
            """)
    List<RuleDefinition> findActiveByApplicationIdAndRuleTypeAndOrgId(
            @Param("appId") UUID applicationId, @Param("ruleType") String ruleType,
            @Param("orgId") UUID orgId);

    List<RuleDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId);
}
