package com.grcplatform.api.repository;

import com.grcplatform.core.domain.PolicyAcknowledgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringPolicyAcknowledgmentRepository extends JpaRepository<PolicyAcknowledgment, UUID> {

    List<PolicyAcknowledgment> findByOrgIdAndPolicyRecordId(UUID orgId, UUID policyRecordId);

    List<PolicyAcknowledgment> findByOrgIdAndUserId(UUID orgId, UUID userId);

    @Query("""
            SELECT a FROM PolicyAcknowledgment a
            WHERE a.orgId = :orgId AND a.policyRecordId = :policyRecordId AND a.userId = :userId
            ORDER BY a.acknowledgedAt DESC
            LIMIT 1
            """)
    Optional<PolicyAcknowledgment> findLatest(@Param("orgId") UUID orgId,
            @Param("policyRecordId") UUID policyRecordId, @Param("userId") UUID userId);

    long countByOrgIdAndPolicyRecordId(UUID orgId, UUID policyRecordId);
}
