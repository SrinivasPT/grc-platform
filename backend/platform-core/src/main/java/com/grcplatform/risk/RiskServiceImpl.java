package com.grcplatform.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.risk.command.ComputeRiskScoreHandler;
import com.grcplatform.risk.command.UpdateResidualScoreHandler;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RiskServiceImpl implements RiskService {

        private final RiskScoreRepository riskScoreRepository;
        private final RiskAppetiteThresholdRepository appetiteRepository;
        private final AuditService auditService;
        private final ComputeRiskScoreHandler computeRiskScoreHandler;
        private final UpdateResidualScoreHandler updateResidualScoreHandler;

        public RiskServiceImpl(RiskScoreRepository riskScoreRepository,
                        RiskAppetiteThresholdRepository appetiteRepository,
                        AuditService auditService, ComputeRiskScoreHandler computeRiskScoreHandler,
                        UpdateResidualScoreHandler updateResidualScoreHandler) {
                this.riskScoreRepository = riskScoreRepository;
                this.appetiteRepository = appetiteRepository;
                this.auditService = auditService;
                this.computeRiskScoreHandler = computeRiskScoreHandler;
                this.updateResidualScoreHandler = updateResidualScoreHandler;
        }

        @Override
        @Transactional
        public RiskScoreDto computeAndSaveScore(ComputeRiskScoreCommand cmd) {
                return computeRiskScoreHandler.handle(cmd);
        }

        @Override
        @Transactional
        public RiskScoreDto updateResidualScore(UpdateResidualScoreCommand cmd) {
                return updateResidualScoreHandler.handle(cmd);
        }

        @Override
        public RiskScoreDto getScore(UUID riskRecordId) {
                var ctx = SessionContextHolder.current();
                return riskScoreRepository.findByRecordIdAndOrgId(riskRecordId, ctx.orgId())
                                .orElseThrow(() -> new com.grcplatform.core.exception.RecordNotFoundException(
                                                "RiskScore", riskRecordId))
                                .toDto();
        }

        @Override
        public List<RiskScoreDto> getScoresForRecords(List<UUID> recordIds) {
                var ctx = SessionContextHolder.current();
                return riskScoreRepository.findByOrgIdAndRecordIdIn(ctx.orgId(), recordIds).stream()
                                .map(RiskScore::toDto).toList();
        }

        @Override
        @Transactional
        public void setAppetiteThreshold(String category, int thresholdScore, String notes) {
                var ctx = SessionContextHolder.current();
                appetiteRepository.findActiveByOrgIdAndCategory(ctx.orgId(), category)
                                .ifPresent(existing -> {
                                        existing.setEffectiveTo(LocalDate.now());
                                        appetiteRepository.save(existing);
                                });
                var newThreshold = RiskAppetiteThreshold.create(ctx.orgId(), category,
                                thresholdScore, ctx.userId(), notes);
                appetiteRepository.save(newThreshold);
                auditService.log(AuditEvent.of("RISK_APPETITE_SET", null, ctx.userId(), null,
                                "category=" + category + " threshold=" + thresholdScore));
        }
}
