package com.grcplatform.api.repository;

import com.grcplatform.core.domain.PolicyAcknowledgment;
import com.grcplatform.core.repository.PolicyAcknowledgmentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyAcknowledgmentRepositoryAdapter implements PolicyAcknowledgmentRepository {

    private final SpringPolicyAcknowledgmentRepository jpa;

    public PolicyAcknowledgmentRepositoryAdapter(SpringPolicyAcknowledgmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PolicyAcknowledgment save(PolicyAcknowledgment ack) {
        return jpa.save(ack);
    }

    @Override
    public List<PolicyAcknowledgment> findByOrgIdAndPolicyRecordId(UUID orgId,
            UUID policyRecordId) {
        return jpa.findByOrgIdAndPolicyRecordId(orgId, policyRecordId);
    }

    @Override
    public List<PolicyAcknowledgment> findByOrgIdAndUserId(UUID orgId, UUID userId) {
        return jpa.findByOrgIdAndUserId(orgId, userId);
    }

    @Override
    public Optional<PolicyAcknowledgment> findLatestByOrgIdAndPolicyRecordIdAndUserId(UUID orgId,
            UUID policyRecordId, UUID userId) {
        return jpa.findLatest(orgId, policyRecordId, userId);
    }

    @Override
    public long countByOrgIdAndPolicyRecordId(UUID orgId, UUID policyRecordId) {
        return jpa.countByOrgIdAndPolicyRecordId(orgId, policyRecordId);
    }
}
