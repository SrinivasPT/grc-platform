package com.grcplatform.api.repository;

import com.grcplatform.core.domain.RuleDefinition;
import com.grcplatform.core.repository.RuleDefinitionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RuleDefinitionRepositoryAdapter implements RuleDefinitionRepository {

    private final SpringRuleDefinitionRepository jpa;

    public RuleDefinitionRepositoryAdapter(SpringRuleDefinitionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RuleDefinition save(RuleDefinition ruleDefinition) {
        return jpa.save(ruleDefinition);
    }

    @Override
    public Optional<RuleDefinition> findByIdAndOrgId(UUID id, UUID orgId) {
        return jpa.findByIdAndOrgId(id, orgId);
    }

    @Override
    public List<RuleDefinition> findActiveByApplicationIdAndRuleType(UUID applicationId,
            String ruleType, UUID orgId) {
        return jpa.findActiveByApplicationIdAndRuleTypeAndOrgId(applicationId, ruleType, orgId);
    }

    @Override
    public List<RuleDefinition> findByApplicationIdAndOrgId(UUID applicationId, UUID orgId) {
        return jpa.findByApplicationIdAndOrgId(applicationId, orgId);
    }
}
