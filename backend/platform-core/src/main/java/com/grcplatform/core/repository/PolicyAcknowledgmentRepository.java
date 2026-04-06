package com.grcplatform.core.repository;

import com.grcplatform.core.domain.PolicyAcknowledgment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyAcknowledgmentRepository {

    PolicyAcknowledgment save(PolicyAcknowledgment ack);

    List<PolicyAcknowledgment> findByOrgIdAndPolicyRecordId(UUID orgId, UUID policyRecordId);

    List<PolicyAcknowledgment> findByOrgIdAndUserId(UUID orgId, UUID userId);

    Optional<PolicyAcknowledgment> findLatestByOrgIdAndPolicyRecordIdAndUserId(UUID orgId,
            UUID policyRecordId, UUID userId);

    long countByOrgIdAndPolicyRecordId(UUID orgId, UUID policyRecordId);
}
