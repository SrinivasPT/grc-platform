package com.grcplatform.core.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.RiskAppetiteThreshold;
import com.grcplatform.core.domain.RiskScore;
import com.grcplatform.core.dto.RiskScoreDto;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.repository.RiskAppetiteThresholdRepository;
import com.grcplatform.core.repository.RiskScoreRepository;
import jakarta.transaction.Transactional;

public class RiskServiceImpl implements RiskService {

    private static final int DEFAULT_APPETITE_THRESHOLD = 12;

    private final RiskScoreRepository riskScoreRepository;
    private final RiskAppetiteThresholdRepository appetiteRepository;
    private final AuditService auditService;

    public RiskServiceImpl(RiskScoreRepository riskScoreRepository,
            RiskAppetiteThresholdRepository appetiteRepository, AuditService auditService) {
        this.riskScoreRepository = riskScoreRepository;
        this.appetiteRepository = appetiteRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public RiskScoreDto computeAndSaveScore(UUID riskRecordId, BigDecimal likelihood,
            BigDecimal impact) {
        var ctx = SessionContextHolder.current();
        var score = RiskScore.compute(riskRecordId, ctx.orgId(), likelihood, impact);
        var saved = riskScoreRepository.save(score);
        auditService.log(AuditEvent.of("RISK_SCORE_COMPUTED", riskRecordId, ctx.userId(), null,
                "inherent=" + saved.getInherentScore()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public RiskScoreDto updateResidualScore(UUID riskRecordId, BigDecimal residualLikelihood,
            BigDecimal residualImpact) {
        var ctx = SessionContextHolder.current();
        var score = riskScoreRepository.findByRecordIdAndOrgId(riskRecordId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("RiskScore", riskRecordId));

        int threshold = resolveAppetite(ctx.orgId(), null);
        score.applyResidual(residualLikelihood, residualImpact, threshold);
        var saved = riskScoreRepository.save(score);

        auditService.log(AuditEvent.of("RISK_RESIDUAL_UPDATED", riskRecordId, ctx.userId(), null,
                "residual=" + saved.getResidualScore()));
        return toDto(saved);
    }

    @Override
    public RiskScoreDto getScore(UUID riskRecordId) {
        var ctx = SessionContextHolder.current();
        return toDto(riskScoreRepository.findByRecordIdAndOrgId(riskRecordId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("RiskScore", riskRecordId)));
    }

    @Override
    public List<RiskScoreDto> getScoresForRecords(List<UUID> recordIds) {
        var ctx = SessionContextHolder.current();
        return riskScoreRepository.findByOrgIdAndRecordIdIn(ctx.orgId(), recordIds).stream()
                .map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void setAppetiteThreshold(String category, int thresholdScore, String notes) {
        var ctx = SessionContextHolder.current();
        // Expire all current active rows for this category
        appetiteRepository.findActiveByOrgIdAndCategory(ctx.orgId(), category)
                .ifPresent(existing -> {
                    existing.setEffectiveTo(LocalDate.now());
                    appetiteRepository.save(existing);
                });
        var newThreshold = RiskAppetiteThreshold.create(ctx.orgId(), category, thresholdScore,
                ctx.userId(), notes);
        appetiteRepository.save(newThreshold);
        auditService.log(AuditEvent.of("RISK_APPETITE_SET", null, ctx.userId(), null,
                "category=" + category + " threshold=" + thresholdScore));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int resolveAppetite(UUID orgId, String category) {
        return appetiteRepository.findActiveByOrgIdAndCategory(orgId, category)
                .map(RiskAppetiteThreshold::getThresholdScore).orElse(DEFAULT_APPETITE_THRESHOLD);
    }

    private RiskScoreDto toDto(RiskScore s) {
        return new RiskScoreDto(s.getRecordId(), s.getLikelihoodScore(), s.getImpactScore(),
                s.getInherentScore(), s.getInherentRating(), s.getResidualScore(),
                s.getResidualRating(), s.getAppetiteAlignment(), s.getComputedAt());
    }
}
