package com.grcplatform.policy;

import java.util.List;
import com.grcplatform.policy.command.AcknowledgePolicyHandler;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PolicyServiceImpl implements PolicyService {

    private final PolicyAcknowledgmentRepository ackRepository;
    private final AcknowledgePolicyHandler acknowledgeHandler;

    public PolicyServiceImpl(PolicyAcknowledgmentRepository ackRepository,
            AcknowledgePolicyHandler acknowledgeHandler) {
        this.ackRepository = ackRepository;
        this.acknowledgeHandler = acknowledgeHandler;
    }

    @Override
    @Transactional
    public PolicyAcknowledgmentDto acknowledgePolicy(AcknowledgePolicyCommand cmd) {
        return acknowledgeHandler.handle(cmd);
    }

    @Override
    public List<PolicyAcknowledgmentDto> getAcknowledgments(java.util.UUID policyRecordId) {
        var ctx = com.grcplatform.core.context.SessionContextHolder.current();
        return ackRepository.findByOrgIdAndPolicyRecordId(ctx.orgId(), policyRecordId).stream()
                .map(PolicyAcknowledgment::toDto).toList();
    }

    @Override
    public long countAcknowledgments(java.util.UUID policyRecordId) {
        var ctx = com.grcplatform.core.context.SessionContextHolder.current();
        return ackRepository.countByOrgIdAndPolicyRecordId(ctx.orgId(), policyRecordId);
    }

    @Override
    public List<PolicyAcknowledgmentDto> getMyAcknowledgments() {
        var ctx = com.grcplatform.core.context.SessionContextHolder.current();
        return ackRepository.findByOrgIdAndUserId(ctx.orgId(), ctx.userId()).stream()
                .map(PolicyAcknowledgment::toDto).toList();
    }
}
