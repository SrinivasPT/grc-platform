package com.grcplatform.core.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.ControlEffectiveness;
import com.grcplatform.core.domain.ControlTestResult;
import com.grcplatform.core.dto.ControlEffectivenessDto;
import com.grcplatform.core.repository.ControlEffectivenessRepository;
import com.grcplatform.core.repository.ControlTestResultRepository;
import com.grcplatform.core.repository.RecordRelationRepository;
import jakarta.transaction.Transactional;

public class ControlServiceImpl implements ControlService {

    static final String RELATION_MITIGATES = "MITIGATES";

    private final ControlTestResultRepository testResultRepository;
    private final ControlEffectivenessRepository effectivenessRepository;
    private final RecordRelationRepository relationRepository;
    private final AuditService auditService;

    public ControlServiceImpl(ControlTestResultRepository testResultRepository,
            ControlEffectivenessRepository effectivenessRepository,
            RecordRelationRepository relationRepository, AuditService auditService) {
        this.testResultRepository = testResultRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.relationRepository = relationRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public ControlEffectivenessDto recordTestResult(UUID controlRecordId, LocalDate testDate,
            String testResult, int exceptionsCount, String notes) {
        var ctx = SessionContextHolder.current();
        var result = ControlTestResult.create(controlRecordId, ctx.orgId(), ctx.userId(), testDate,
                testResult, exceptionsCount, notes);
        testResultRepository.save(result);
        auditService.log(AuditEvent.of("CONTROL_TEST_RECORDED", controlRecordId, ctx.userId(), null,
                testResult));
        return computeEffectivenessScore(controlRecordId);
    }

    @Override
    @Transactional
    public ControlEffectivenessDto computeEffectivenessScore(UUID controlRecordId) {
        var ctx = SessionContextHolder.current();
        var cutoff = LocalDate.now().minusYears(1);
        var tests = testResultRepository.findByOrgIdAndControlRecordIdSince(ctx.orgId(),
                controlRecordId, cutoff);

        var applicable =
                tests.stream().filter(t -> !"not_applicable".equals(t.getTestResult())).toList();

        if (applicable.isEmpty()) {
            var effectiveness = ControlEffectiveness.notAssessed(controlRecordId, ctx.orgId());
            effectivenessRepository.save(effectiveness);
            return toDto(effectiveness);
        }

        double totalScore = applicable.stream().mapToDouble(t -> switch (t.getTestResult()) {
            case "passed" -> 1.0;
            case "partially_passed" -> 0.5;
            default -> 0.0;
        }).sum();
        int score = (int) Math.round((totalScore / applicable.size()) * 100);
        String rating = computeRating(score);

        var effectiveness = ControlEffectiveness.compute(controlRecordId, ctx.orgId(), score,
                rating, applicable.size());
        effectivenessRepository.save(effectiveness);
        return toDto(effectiveness);
    }

    @Override
    public List<UUID> getControlsForRisk(UUID riskRecordId) {
        var ctx = SessionContextHolder.current();
        return relationRepository.findByOrgIdAndTargetIdAndRelationType(ctx.orgId(), riskRecordId,
                RELATION_MITIGATES).stream().map(r -> r.getSourceId()).toList();
    }

    @Override
    public Optional<ControlEffectivenessDto> getEffectiveness(UUID controlRecordId) {
        var ctx = SessionContextHolder.current();
        return effectivenessRepository.findByControlRecordIdAndOrgId(controlRecordId, ctx.orgId())
                .map(this::toDto);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String computeRating(int score) {
        if (score >= 90) return "Effective";
        if (score >= 70) return "Largely Effective";
        if (score >= 40) return "Partially Effective";
        return "Ineffective";
    }

    private ControlEffectivenessDto toDto(ControlEffectiveness e) {
        return new ControlEffectivenessDto(e.getControlRecordId(), e.getEffectivenessScore(),
                e.getEffectivenessRating(), e.getTestCount12m(), e.getComputedAt());
    }
}
