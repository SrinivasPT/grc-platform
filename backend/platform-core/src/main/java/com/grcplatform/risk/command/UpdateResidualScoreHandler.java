package com.grcplatform.risk.command;

import java.util.List;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.validation.Validator;
import com.grcplatform.risk.RiskAppetiteThreshold;
import com.grcplatform.risk.RiskAppetiteThresholdRepository;
import com.grcplatform.risk.RiskScoreDto;
import com.grcplatform.risk.RiskScoreRepository;
import com.grcplatform.risk.UpdateResidualScoreCommand;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateResidualScoreHandler {

    private static final int DEFAULT_APPETITE_THRESHOLD = 12;

    private final RiskScoreRepository riskScoreRepository;
    private final RiskAppetiteThresholdRepository appetiteRepository;
    private final AuditService auditService;
    private final List<Validator<UpdateResidualScoreCommand>> validators;

    public UpdateResidualScoreHandler(RiskScoreRepository riskScoreRepository,
            RiskAppetiteThresholdRepository appetiteRepository, AuditService auditService,
            List<Validator<UpdateResidualScoreCommand>> validators) {
        this.riskScoreRepository = riskScoreRepository;
        this.appetiteRepository = appetiteRepository;
        this.auditService = auditService;
        this.validators = validators;
    }

    public RiskScoreDto handle(UpdateResidualScoreCommand cmd) {
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var score = riskScoreRepository.findByRecordIdAndOrgId(cmd.riskRecordId(), ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("RiskScore", cmd.riskRecordId()));

        int threshold = appetiteRepository.findActiveByOrgIdAndCategory(ctx.orgId(), null)
                .map(RiskAppetiteThreshold::getThresholdScore).orElse(DEFAULT_APPETITE_THRESHOLD);
        score.applyResidual(cmd.residualLikelihood(), cmd.residualImpact(), threshold);
        var saved = riskScoreRepository.save(score);

        auditService.log(AuditEvent.of("RISK_RESIDUAL_UPDATED", cmd.riskRecordId(), ctx.userId(),
                null, "residual=" + saved.getResidualScore()));
        return saved.toDto();
    }
}
