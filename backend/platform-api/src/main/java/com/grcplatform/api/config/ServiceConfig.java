package com.grcplatform.api.config;

import org.neo4j.driver.Driver;
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
import com.grcplatform.core.repository.InAppNotificationRepository;
import com.grcplatform.core.repository.RuleDefinitionRepository;
import com.grcplatform.core.repository.WorkflowDefinitionRepository;
import com.grcplatform.core.repository.WorkflowHistoryRepository;
import com.grcplatform.core.repository.WorkflowInstanceRepository;
import com.grcplatform.core.repository.WorkflowTaskRepository;
import com.grcplatform.core.rule.ComputeRuleEvaluator;
import com.grcplatform.core.rule.RuleDslParser;
import com.grcplatform.core.rule.TriggerRuleEvaluator;
import com.grcplatform.core.rule.ValidateRuleEvaluator;
import com.grcplatform.core.service.RecordService;
import com.grcplatform.core.service.RecordServiceImpl;
import com.grcplatform.core.workflow.WorkflowConfigParser;
import com.grcplatform.core.workflow.WorkflowService;
import com.grcplatform.graph.ChangeTrackingRepository;
import com.grcplatform.graph.GraphProjectionWorker;
import com.grcplatform.graph.GraphSyncStateRepository;
import com.grcplatform.notification.OutboxEventRouter;
import com.grcplatform.notification.OutboxWorker;
import com.grcplatform.notification.delivery.InAppDeliveryService;
import com.grcplatform.workflow.EscalationScheduler;
import com.grcplatform.workflow.EscalationManagerResolver;
import com.grcplatform.workflow.WorkflowEngine;
import com.grcplatform.workflow.WorkflowOutboxPublisher;

/**
 * Wires platform-core engine beans as Spring beans. platform-core has zero Spring Boot dependency,
 * so classes are instantiated here rather than via @Service annotations (see ADR-001 and module
 * boundary rules). GRC domain slice beans live in their dedicated *SliceConfig files.
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

    // ─── Workflow ────────────────────────────────────────────────────────────

    @Bean
    public WorkflowConfigParser workflowConfigParser() {
        return new WorkflowConfigParser();
    }

    @Bean
    public WorkflowOutboxPublisher workflowOutboxPublisher(EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        return new WorkflowOutboxPublisher(outboxRepository, objectMapper);
    }

    @Bean
    public WorkflowService workflowService(WorkflowDefinitionRepository definitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowHistoryRepository historyRepository, WorkflowTaskRepository taskRepository,
            EventOutboxRepository outboxRepository, WorkflowConfigParser workflowConfigParser,
            WorkflowOutboxPublisher workflowOutboxPublisher) {
        return new WorkflowEngine(definitionRepository, instanceRepository, historyRepository,
                taskRepository, outboxRepository, workflowConfigParser, workflowOutboxPublisher);
    }

    @Bean
    public EscalationScheduler escalationScheduler(WorkflowTaskRepository taskRepository,
            WorkflowOutboxPublisher workflowOutboxPublisher,
            EscalationManagerResolver escalationManagerResolver) {
        return new EscalationScheduler(taskRepository, workflowOutboxPublisher,
                escalationManagerResolver);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    @Bean
    public InAppDeliveryService inAppDeliveryService(
            InAppNotificationRepository notificationRepository) {
        return new InAppDeliveryService(notificationRepository);
    }

    @Bean
    public OutboxEventRouter outboxEventRouter(InAppDeliveryService inAppDeliveryService,
            ObjectMapper objectMapper) {
        return new OutboxEventRouter(inAppDeliveryService, objectMapper);
    }

    @Bean
    public OutboxWorker outboxWorker(EventOutboxRepository outboxRepository,
            OutboxEventRouter outboxEventRouter) {
        return new OutboxWorker(outboxRepository, outboxEventRouter);
    }

    // ─── Graph Projection ─────────────────────────────────────────────────────

    @Bean
    public GraphProjectionWorker graphProjectionWorker(
            ChangeTrackingRepository changeTrackingRepository,
            GraphSyncStateRepository graphSyncStateRepository, Driver neo4jDriver) {
        return new GraphProjectionWorker(changeTrackingRepository, graphSyncStateRepository,
                neo4jDriver);
    }
}
