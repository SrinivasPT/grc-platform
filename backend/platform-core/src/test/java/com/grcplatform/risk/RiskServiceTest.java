package com.grcplatform.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.risk.command.ComputeRiskScoreHandler;
import com.grcplatform.risk.command.UpdateResidualScoreHandler;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskServiceTest {

    @Mock
    RiskScoreRepository riskScoreRepository;
    @Mock
    RiskAppetiteThresholdRepository appetiteRepository;
    @Mock
    AuditService auditService;

    private RiskServiceImpl service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RISK_RECORD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var computeHandler =
                new ComputeRiskScoreHandler(riskScoreRepository, auditService, List.of());
        var updateHandler = new UpdateResidualScoreHandler(riskScoreRepository, appetiteRepository,
                auditService, List.of());
        service = new RiskServiceImpl(riskScoreRepository, appetiteRepository, auditService,
                computeHandler, updateHandler);
    }

    private void withContext(Runnable action) {
        var ctx = new SessionContext(ORG_ID, USER_ID, "analyst", List.of("analyst"), 1);
        ScopedValue.where(SessionContextHolder.SESSION, ctx).run(action);
    }

    // ─── computeAndSaveScore ─────────────────────────────────────────────────

    @Test
    void computeAndSaveScore_inherentScore_isLikelihoodTimesImpact() {
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> {
            var dto = service.computeAndSaveScore(new ComputeRiskScoreCommand(RISK_RECORD_ID,
                    new BigDecimal("3"), new BigDecimal("4")));
            assertThat(dto.inherentScore()).isEqualByComparingTo("12");
            assertThat(dto.inherentRating()).isEqualTo("High");
        });
    }

    @Test
    void computeAndSaveScore_ratingIsCritical_whenScoreGte20() {
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> {
            var dto = service.computeAndSaveScore(new ComputeRiskScoreCommand(RISK_RECORD_ID,
                    new BigDecimal("5"), new BigDecimal("5")));
            assertThat(dto.inherentRating()).isEqualTo("Critical");
            assertThat(dto.inherentScore()).isEqualByComparingTo("25");
        });
    }

    @Test
    void computeAndSaveScore_writesAuditEntry() {
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> service.computeAndSaveScore(
                new ComputeRiskScoreCommand(RISK_RECORD_ID, BigDecimal.TWO, BigDecimal.TWO)));

        var captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).log(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo("RISK_SCORE_COMPUTED");
        assertThat(captor.getValue().entityId()).isEqualTo(RISK_RECORD_ID);
    }

    // ─── updateResidualScore ─────────────────────────────────────────────────

    @Test
    void updateResidualScore_appliesAppetiteAlignment() {
        var existing =
                RiskScore.compute(RISK_RECORD_ID, ORG_ID, new BigDecimal("4"), new BigDecimal("4"));
        when(riskScoreRepository.findByRecordIdAndOrgId(RISK_RECORD_ID, ORG_ID))
                .thenReturn(Optional.of(existing));
        when(appetiteRepository.findActiveByOrgIdAndCategory(eq(ORG_ID), isNull()))
                .thenReturn(Optional.empty()); // falls back to 12
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> {
            var dto = service.updateResidualScore(new UpdateResidualScoreCommand(RISK_RECORD_ID,
                    new BigDecimal("2"), new BigDecimal("2")));
            assertThat(dto.residualScore()).isEqualByComparingTo("4");
            assertThat(dto.appetiteAlignment()).isEqualTo("within_appetite");
        });
    }

    @Test
    void updateResidualScore_marksAboveAppetite_whenScoreExceedsThreshold() {
        var existing =
                RiskScore.compute(RISK_RECORD_ID, ORG_ID, new BigDecimal("4"), new BigDecimal("4"));
        when(riskScoreRepository.findByRecordIdAndOrgId(RISK_RECORD_ID, ORG_ID))
                .thenReturn(Optional.of(existing));
        when(appetiteRepository.findActiveByOrgIdAndCategory(eq(ORG_ID), isNull()))
                .thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        withContext(() -> {
            var dto = service.updateResidualScore(new UpdateResidualScoreCommand(RISK_RECORD_ID,
                    new BigDecimal("4"), new BigDecimal("4"))); // 16 > 12
            assertThat(dto.appetiteAlignment()).isEqualTo("above_appetite");
        });
    }

    @Test
    void updateResidualScore_throwsNotFound_whenScoreDoesNotExist() {
        when(riskScoreRepository.findByRecordIdAndOrgId(RISK_RECORD_ID, ORG_ID))
                .thenReturn(Optional.empty());

        withContext(() -> assertThatThrownBy(() -> service.updateResidualScore(
                new UpdateResidualScoreCommand(RISK_RECORD_ID, BigDecimal.ONE, BigDecimal.ONE)))
                        .isInstanceOf(RecordNotFoundException.class));
    }

    // ─── getScoresForRecords ─────────────────────────────────────────────────

    @Test
    void getScoresForRecords_delegatesToRepository() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        var s1 = RiskScore.compute(id1, ORG_ID, BigDecimal.ONE, BigDecimal.ONE);
        var s2 = RiskScore.compute(id2, ORG_ID, BigDecimal.TWO, BigDecimal.TWO);
        when(riskScoreRepository.findByOrgIdAndRecordIdIn(eq(ORG_ID), eq(List.of(id1, id2))))
                .thenReturn(List.of(s1, s2));

        withContext(() -> {
            var results = service.getScoresForRecords(List.of(id1, id2));
            assertThat(results).hasSize(2);
        });
    }
}
