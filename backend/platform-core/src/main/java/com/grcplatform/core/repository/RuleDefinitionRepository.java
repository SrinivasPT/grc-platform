package com.grcplatform.core.repository;

import com.grcplatform.core.domain.RuleDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleDefinitionRepository {

    RuleDefinition save(RuleDefinition ruleDefinition);

    Optional<RuleDefinition> findByIdAndOrgId(UUID id, UUID orgId);

    List<RuleDefinition> findActiveByApplicationIdAndRuleType(UUID applicationId, String ruleType,
            UUID orgId);

    List<RuleDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId);
}
