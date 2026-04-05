package com.grcplatform.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.audit.AuditServiceImpl;
import com.grcplatform.core.repository.ApplicationRepository;
import com.grcplatform.core.repository.AuditChainHeadRepository;
import com.grcplatform.core.repository.AuditLogRepository;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.FieldDefinitionRepository;
import com.grcplatform.core.repository.FieldValueDateRepository;
import com.grcplatform.core.repository.FieldValueNumberRepository;
import com.grcplatform.core.repository.FieldValueReferenceRepository;
import com.grcplatform.core.repository.FieldValueTextRepository;
import com.grcplatform.core.repository.GrcRecordRepository;
import com.grcplatform.core.repository.RuleDefinitionRepository;
import com.grcplatform.core.rule.ComputeRuleEvaluator;
import com.grcplatform.core.rule.RuleDslParser;
import com.grcplatform.core.rule.TriggerRuleEvaluator;
import com.grcplatform.core.rule.ValidateRuleEvaluator;
import com.grcplatform.core.service.RecordService;
import com.grcplatform.core.service.RecordServiceImpl;

/**
 * Wires platform-core services as Spring beans. platform-core has zero Spring Boot dependency, so
 * classes are instantiated here rather than via @Service annotations (see ADR-001 and module
 * boundary rules).
 */
@Configuration
public class ServiceConfig {

    @Bean
    public RuleDslParser ruleDslParser(ObjectMapper objectMapper) {
        return new RuleDslParser(objectMapper);
    }

    @Bean
    public ComputeRuleEvaluator computeRuleEvaluator() {
        return new ComputeRuleEvaluator();
    }

    @Bean
    public ValidateRuleEvaluator validateRuleEvaluator() {
        return new ValidateRuleEvaluator();
    }

    @Bean
    public TriggerRuleEvaluator triggerRuleEvaluator() {
        return new TriggerRuleEvaluator();
    }

    @Bean
    public AuditService auditService(AuditChainHeadRepository auditChainHeadRepository,
            AuditLogRepository auditLogRepository) {
        return new AuditServiceImpl(auditChainHeadRepository, auditLogRepository);
    }

    @Bean
    public RecordService recordService(GrcRecordRepository recordRepository,
            ApplicationRepository applicationRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            RuleDefinitionRepository ruleDefinitionRepository,
            FieldValueTextRepository textRepository, FieldValueNumberRepository numberRepository,
            FieldValueDateRepository dateRepository,
            FieldValueReferenceRepository referenceRepository,
            EventOutboxRepository outboxRepository, AuditService auditService,
            RuleDslParser ruleDslParser, ComputeRuleEvaluator computeEvaluator,
            ValidateRuleEvaluator validateEvaluator, TriggerRuleEvaluator triggerEvaluator,
            ObjectMapper objectMapper) {
        return new RecordServiceImpl(recordRepository, applicationRepository,
                fieldDefinitionRepository, ruleDefinitionRepository, textRepository,
                numberRepository, dateRepository, referenceRepository, outboxRepository,
                auditService, ruleDslParser, computeEvaluator, validateEvaluator, triggerEvaluator,
                objectMapper);
    }
}
