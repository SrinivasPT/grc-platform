package com.grcplatform.control;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.grcplatform.control.command.RecordTestResultHandler;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.repository.RecordRelationRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ControlServiceImpl implements ControlService {

    static final String RELATION_MITIGATES = "MITIGATES";

    private final ControlTestResultRepository testResultRepository;
    private final ControlEffectivenessRepository effectivenessRepository;
    private final RecordRelationRepository relationRepository;
    private final RecordTestResultHandler recordTestResultHandler;

    public ControlServiceImpl(ControlTestResultRepository testResultRepository,
            ControlEffectivenessRepository effectivenessRepository,
            RecordRelationRepository relationRepository,
            RecordTestResultHandler recordTestResultHandler) {
        this.testResultRepository = testResultRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.relationRepository = relationRepository;
        this.recordTestResultHandler = recordTestResultHandler;
    }

    @Override
    @Transactional
    public ControlEffectivenessDto recordTestResult(RecordTestResultCommand cmd) {
        return recordTestResultHandler.handle(cmd);
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
            return effectiveness.toDto();
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
        return effectiveness.toDto();
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
                .map(ControlEffectiveness::toDto);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String computeRating(int score) {
        if (score >= 90) return "Effective";
        if (score >= 70) return "Largely Effective";
        if (score >= 40) return "Partially Effective";
        return "Ineffective";
    }
}
