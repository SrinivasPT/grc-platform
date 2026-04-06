package com.grcplatform.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.risk.RiskAppetiteThresholdRepository;
import com.grcplatform.risk.RiskScoreRepository;
import com.grcplatform.risk.RiskService;
import com.grcplatform.risk.RiskServiceImpl;
import com.grcplatform.risk.command.ComputeRiskScoreHandler;
import com.grcplatform.risk.command.UpdateResidualScoreHandler;

@Configuration
public class RiskSliceConfig {

    @Bean
    public ComputeRiskScoreHandler computeRiskScoreHandler(RiskScoreRepository riskScoreRepository,
            AuditService auditService) {
        return new ComputeRiskScoreHandler(riskScoreRepository, auditService, List.of());
    }

    @Bean
    public UpdateResidualScoreHandler updateResidualScoreHandler(
            RiskScoreRepository riskScoreRepository,
            RiskAppetiteThresholdRepository appetiteRepository, AuditService auditService) {
        return new UpdateResidualScoreHandler(riskScoreRepository, appetiteRepository, auditService,
                List.of());
    }

    @Bean
    public RiskService riskService(RiskScoreRepository riskScoreRepository,
            RiskAppetiteThresholdRepository appetiteRepository, AuditService auditService,
            ComputeRiskScoreHandler computeRiskScoreHandler,
            UpdateResidualScoreHandler updateResidualScoreHandler) {
        return new RiskServiceImpl(riskScoreRepository, appetiteRepository, auditService,
                computeRiskScoreHandler, updateResidualScoreHandler);
    }
}
