package com.grcplatform.core.service;

import java.util.List;
import java.util.UUID;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.PolicyAcknowledgment;
import com.grcplatform.core.dto.PolicyAcknowledgmentDto;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.repository.PolicyAcknowledgmentRepository;
import jakarta.transaction.Transactional;

public class PolicyServiceImpl implements PolicyService {

    private final PolicyAcknowledgmentRepository ackRepository;
    private final AuditService auditService;

    public PolicyServiceImpl(PolicyAcknowledgmentRepository ackRepository,
            AuditService auditService) {
        this.ackRepository = ackRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public PolicyAcknowledgmentDto acknowledgePolicy(UUID policyRecordId, String policyVersion,
            String ipAddress) {
        var ctx = SessionContextHolder.current();

        if (policyVersion == null || policyVersion.isBlank()) {
            throw new ValidationException("policyVersion", "Policy version is required");
        }

        var ack = PolicyAcknowledgment.create(ctx.orgId(), policyRecordId, ctx.userId(),
                policyVersion, "platform", ipAddress);
        var saved = ackRepository.save(ack);

        auditService.log(AuditEvent.of("POLICY_ACKNOWLEDGED", policyRecordId, ctx.userId(), null,
                "version=" + policyVersion));

        return toDto(saved);
    }

    @Override
    public List<PolicyAcknowledgmentDto> getAcknowledgments(UUID policyRecordId) {
        var ctx = SessionContextHolder.current();
        return ackRepository.findByOrgIdAndPolicyRecordId(ctx.orgId(), policyRecordId).stream()
                .map(this::toDto).toList();
    }

    @Override
    public long countAcknowledgments(UUID policyRecordId) {
        var ctx = SessionContextHolder.current();
        return ackRepository.countByOrgIdAndPolicyRecordId(ctx.orgId(), policyRecordId);
    }

    @Override
    public List<PolicyAcknowledgmentDto> getMyAcknowledgments() {
        var ctx = SessionContextHolder.current();
        return ackRepository.findByOrgIdAndUserId(ctx.orgId(), ctx.userId()).stream()
                .map(this::toDto).toList();
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private PolicyAcknowledgmentDto toDto(PolicyAcknowledgment ack) {
        return new PolicyAcknowledgmentDto(ack.getId(), ack.getPolicyRecordId(), ack.getUserId(),
                ack.getAcknowledgedAt(), ack.getPolicyVersion(), ack.getMethod());
    }
}
