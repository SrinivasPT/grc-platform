package com.grcplatform.api.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.policy.PolicyAcknowledgmentRepository;
import com.grcplatform.policy.PolicyService;
import com.grcplatform.policy.PolicyServiceImpl;
import com.grcplatform.policy.command.AcknowledgePolicyHandler;

@Configuration
public class PolicySliceConfig {

    @Bean
    public AcknowledgePolicyHandler acknowledgePolicyHandler(
            PolicyAcknowledgmentRepository ackRepository, AuditService auditService) {
        return new AcknowledgePolicyHandler(ackRepository, auditService, List.of());
    }

    @Bean
    public PolicyService policyService(PolicyAcknowledgmentRepository ackRepository,
            AcknowledgePolicyHandler acknowledgePolicyHandler) {
        return new PolicyServiceImpl(ackRepository, acknowledgePolicyHandler);
    }
}
