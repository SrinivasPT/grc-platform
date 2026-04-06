package com.grcplatform.risk.command;

import java.util.List;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.risk.ComputeRiskScoreCommand;
import com.grcplatform.risk.RiskScore;
import com.grcplatform.risk.RiskScoreDto;
import com.grcplatform.risk.RiskScoreRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ComputeRiskScoreHandler {

    private final RiskScoreRepository riskScoreRepository;
    private final AuditService auditService;
    private final List<Validator<ComputeRiskScoreCommand>> validators;

    public ComputeRiskScoreHandler(RiskScoreRepository riskScoreRepository,
            AuditService auditService, List<Validator<ComputeRiskScoreCommand>> validators) {
        this.riskScoreRepository = riskScoreRepository;
        this.auditService = auditService;
        this.validators = validators;
    }

    public RiskScoreDto handle(ComputeRiskScoreCommand cmd) {
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var score =
                RiskScore.compute(cmd.riskRecordId(), ctx.orgId(), cmd.likelihood(), cmd.impact());
        var saved = riskScoreRepository.save(score);

        auditService.log(AuditEvent.of("RISK_SCORE_COMPUTED", cmd.riskRecordId(), ctx.userId(),
                null, "inherent=" + saved.getInherentScore()));
        return saved.toDto();
    }
}
