package com.grcplatform.control.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.grcplatform.control.ControlEffectiveness;
import com.grcplatform.control.ControlEffectivenessDto;
import com.grcplatform.control.ControlEffectivenessRepository;
import com.grcplatform.control.ControlTestResult;
import com.grcplatform.control.ControlTestResultRepository;
import com.grcplatform.control.RecordTestResultCommand;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RecordTestResultHandler {

    private final ControlTestResultRepository testResultRepository;
    private final ControlEffectivenessRepository effectivenessRepository;
    private final AuditService auditService;
    private final List<Validator<RecordTestResultCommand>> validators;

    public RecordTestResultHandler(ControlTestResultRepository testResultRepository,
            ControlEffectivenessRepository effectivenessRepository, AuditService auditService,
            List<Validator<RecordTestResultCommand>> validators) {
        this.testResultRepository = testResultRepository;
        this.effectivenessRepository = effectivenessRepository;
        this.auditService = auditService;
        this.validators = validators;
    }

    public ControlEffectivenessDto handle(RecordTestResultCommand cmd) {
        validators.forEach(v -> v.validate(cmd));

        var ctx = SessionContextHolder.current();
        var result = ControlTestResult.create(cmd.controlRecordId(), ctx.orgId(), ctx.userId(),
                cmd.testDate(), cmd.testResult(), cmd.exceptionsCount(), cmd.notes());
        testResultRepository.save(result);

        auditService.log(AuditEvent.of("CONTROL_TEST_RECORDED", cmd.controlRecordId(), ctx.userId(),
                null, cmd.testResult()));

        return computeEffectiveness(cmd.controlRecordId(), ctx.orgId());
    }

    private ControlEffectivenessDto computeEffectiveness(UUID controlRecordId, UUID orgId) {
        var cutoff = LocalDate.now().minusYears(1);
        var tests = testResultRepository.findByOrgIdAndControlRecordIdSince(orgId, controlRecordId,
                cutoff);
        var applicable =
                tests.stream().filter(t -> !"not_applicable".equals(t.getTestResult())).toList();

        if (applicable.isEmpty()) {
            var effectiveness = ControlEffectiveness.notAssessed(controlRecordId, orgId);
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

        var effectiveness = ControlEffectiveness.compute(controlRecordId, orgId, score, rating,
                applicable.size());
        effectivenessRepository.save(effectiveness);
        return effectiveness.toDto();
    }

    private static String computeRating(int score) {
        if (score >= 90) return "Effective";
        if (score >= 70) return "Largely Effective";
        if (score >= 40) return "Partially Effective";
        return "Ineffective";
    }
}
