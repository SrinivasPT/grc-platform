package com.grcplatform.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.grcplatform.control.command.RecordTestResultHandler;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.RecordRelation;
import com.grcplatform.core.repository.RecordRelationRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlServiceTest {

    @Mock
    ControlTestResultRepository testResultRepository;
    @Mock
    ControlEffectivenessRepository effectivenessRepository;
    @Mock
    RecordRelationRepository relationRepository;
    @Mock
    AuditService auditService;

    private ControlServiceImpl service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONTROL_ID = UUID.randomUUID();
    private static final UUID RISK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var recordHandler = new RecordTestResultHandler(testResultRepository,
                effectivenessRepository, auditService, List.of());
        service = new ControlServiceImpl(testResultRepository, effectivenessRepository,
                relationRepository, recordHandler);
        when(effectivenessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void withContext(Runnable action) {
        var ctx = new SessionContext(ORG_ID, USER_ID, "analyst", List.of("analyst"), 1);
        ScopedValue.where(SessionContextHolder.SESSION, ctx).run(action);
    }

    // ─── computeEffectivenessScore ────────────────────────────────────────────

    @Test
    void computeEffectivenessScore_allPassed_returnsEffective() {
        var tests = List.of(passedTest("passed"), passedTest("passed"), passedTest("passed"));
        when(testResultRepository.findByOrgIdAndControlRecordIdSince(eq(ORG_ID), eq(CONTROL_ID),
                any())).thenReturn(tests);

        withContext(() -> {
            var dto = service.computeEffectivenessScore(CONTROL_ID);
            assertThat(dto.effectivenessScore()).isEqualTo(100);
            assertThat(dto.effectivenessRating()).isEqualTo("Effective");
            assertThat(dto.testCount12m()).isEqualTo(3);
        });
    }

    @Test
    void computeEffectivenessScore_noRecentTests_returnsNotAssessed() {
        when(testResultRepository.findByOrgIdAndControlRecordIdSince(eq(ORG_ID), eq(CONTROL_ID),
                any())).thenReturn(List.of());

        withContext(() -> {
            var dto = service.computeEffectivenessScore(CONTROL_ID);
            assertThat(dto.effectivenessRating()).isEqualTo("Not Assessed");
            assertThat(dto.effectivenessScore()).isEqualTo(0);
            assertThat(dto.testCount12m()).isEqualTo(0);
        });
    }

    @Test
    void computeEffectivenessScore_mixedResults_returnsLargelyEffective() {
        // 8/10 passed = 80 → Largely Effective
        var tests = List.of(passedTest("passed"), passedTest("passed"), passedTest("passed"),
                passedTest("passed"), passedTest("passed"), passedTest("passed"),
                passedTest("passed"), passedTest("passed"), passedTest("failed"),
                passedTest("failed"));
        when(testResultRepository.findByOrgIdAndControlRecordIdSince(eq(ORG_ID), eq(CONTROL_ID),
                any())).thenReturn(tests);

        withContext(() -> {
            var dto = service.computeEffectivenessScore(CONTROL_ID);
            assertThat(dto.effectivenessScore()).isEqualTo(80);
            assertThat(dto.effectivenessRating()).isEqualTo("Largely Effective");
        });
    }

    @Test
    void computeEffectivenessScore_notApplicableTestsExcluded() {
        // 1 passed + 1 not_applicable → applicables=1, score=100
        var tests = List.of(passedTest("passed"), passedTest("not_applicable"));
        when(testResultRepository.findByOrgIdAndControlRecordIdSince(eq(ORG_ID), eq(CONTROL_ID),
                any())).thenReturn(tests);

        withContext(() -> {
            var dto = service.computeEffectivenessScore(CONTROL_ID);
            assertThat(dto.effectivenessScore()).isEqualTo(100);
            assertThat(dto.testCount12m()).isEqualTo(1); // only applicable counted
        });
    }

    // ─── recordTestResult ─────────────────────────────────────────────────────

    @Test
    void recordTestResult_savesResultAndLogsAudit() {
        when(testResultRepository.findByOrgIdAndControlRecordIdSince(eq(ORG_ID), eq(CONTROL_ID),
                any())).thenReturn(List.of(passedTest("passed")));

        withContext(() -> {
            var dto = service.recordTestResult(
                    new RecordTestResultCommand(CONTROL_ID, LocalDate.now(), "passed", 0, null));
            assertThat(dto.effectivenessRating()).isEqualTo("Effective");
            verify(testResultRepository).save(any(ControlTestResult.class));
            verify(auditService).log(any());
        });
    }

    // ─── getControlsForRisk ───────────────────────────────────────────────────

    @Test
    void getControlsForRisk_returnsLinkedControlIds() {
        var controlA = UUID.randomUUID();
        var controlB = UUID.randomUUID();
        var relA = RecordRelation.create(ORG_ID, controlA, RISK_ID, "MITIGATES", USER_ID);
        var relB = RecordRelation.create(ORG_ID, controlB, RISK_ID, "MITIGATES", USER_ID);
        when(relationRepository.findByOrgIdAndTargetIdAndRelationType(eq(ORG_ID), eq(RISK_ID),
                eq("MITIGATES"))).thenReturn(List.of(relA, relB));

        withContext(() -> {
            var ids = service.getControlsForRisk(RISK_ID);
            assertThat(ids).containsExactlyInAnyOrder(controlA, controlB);
        });
    }

    // ─── getEffectiveness ─────────────────────────────────────────────────────

    @Test
    void getEffectiveness_whenPresent_returnsDto() {
        var cached = ControlEffectiveness.compute(CONTROL_ID, ORG_ID, 95, "Effective", 10);
        when(effectivenessRepository.findByControlRecordIdAndOrgId(CONTROL_ID, ORG_ID))
                .thenReturn(Optional.of(cached));

        withContext(() -> {
            var dto = service.getEffectiveness(CONTROL_ID);
            assertThat(dto).isPresent();
            assertThat(dto.get().effectivenessRating()).isEqualTo("Effective");
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ControlTestResult passedTest(String result) {
        return ControlTestResult.create(CONTROL_ID, ORG_ID, USER_ID, LocalDate.now(), result, 0,
                null);
    }
}
