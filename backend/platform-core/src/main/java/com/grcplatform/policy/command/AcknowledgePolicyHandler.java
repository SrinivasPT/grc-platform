package com.grcplatform.policy.command;

import java.util.List;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.policy.AcknowledgePolicyCommand;
import com.grcplatform.policy.PolicyAcknowledgment;
import com.grcplatform.policy.PolicyAcknowledgmentDto;
import com.grcplatform.policy.PolicyAcknowledgmentRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AcknowledgePolicyHandler {

    private final PolicyAcknowledgmentRepository ackRepository;
    private final AuditService auditService;
    private final List<Validator<AcknowledgePolicyCommand>> validators;

    public AcknowledgePolicyHandler(PolicyAcknowledgmentRepository ackRepository,
            AuditService auditService, List<Validator<AcknowledgePolicyCommand>> validators) {
        this.ackRepository = ackRepository;
        this.auditService = auditService;
        this.validators = validators;
    }

    public PolicyAcknowledgmentDto handle(AcknowledgePolicyCommand cmd) {
        if (cmd.policyVersion() == null || cmd.policyVersion().isBlank()) {
            throw new ValidationException("policyVersion", "Policy version is required");
        }
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var ack = PolicyAcknowledgment.create(ctx.orgId(), cmd.policyRecordId(), ctx.userId(),
                cmd.policyVersion(), "platform", cmd.ipAddress());
        var saved = ackRepository.save(ack);

        auditService.log(AuditEvent.of("POLICY_ACKNOWLEDGED", cmd.policyRecordId(), ctx.userId(),
                null, "version=" + cmd.policyVersion()));

        return saved.toDto();
    }
}
