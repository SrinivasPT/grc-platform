package com.grcplatform.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContext;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.Application;
import com.grcplatform.core.domain.GrcRecord;
import com.grcplatform.core.domain.RuleDefinition;
import com.grcplatform.core.dto.CreateRecordCommand;
import com.grcplatform.core.dto.RecordDto;
import com.grcplatform.core.exception.RecordNotFoundException;
import com.grcplatform.core.exception.ValidationException;
import com.grcplatform.core.repository.ApplicationRepository;
import com.grcplatform.core.repository.EventOutboxRepository;
import com.grcplatform.core.repository.FieldDefinitionRepository;
import com.grcplatform.core.repository.FieldValueDateRepository;
import com.grcplatform.core.repository.FieldValueNumberRepository;
import com.grcplatform.core.repository.FieldValueReferenceRepository;
import com.grcplatform.core.repository.FieldValueTextRepository;
import com.grcplatform.core.repository.GrcRecordRepository;
import com.grcplatform.core.repository.RuleDefinitionRepository;
import com.grcplatform.core.rule.ComputeRuleEvaluator;
import com.grcplatform.core.rule.EvaluationResult;
import com.grcplatform.core.rule.RuleDslParser;
import com.grcplatform.core.rule.TriggerRuleEvaluator;
import com.grcplatform.core.rule.ValidateRuleEvaluator;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordServiceTest {

    @Mock
    private GrcRecordRepository recordRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private FieldDefinitionRepository fieldDefinitionRepository;
    @Mock
    private RuleDefinitionRepository ruleDefinitionRepository;
    @Mock
    private FieldValueTextRepository textRepository;
    @Mock
    private FieldValueNumberRepository numberRepository;
    @Mock
    private FieldValueDateRepository dateRepository;
    @Mock
    private FieldValueReferenceRepository referenceRepository;
    @Mock
    private EventOutboxRepository outboxRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ComputeRuleEvaluator computeEvaluator;
    @Mock
    private ValidateRuleEvaluator validateEvaluator;
    @Mock
    private TriggerRuleEvaluator triggerEvaluator;

    private RecordServiceImpl recordService;
    private ObjectMapper objectMapper;
    private RuleDslParser ruleDslParser;

    private UUID orgId;
    private UUID userId;
    private SessionContext sessionContext;
    private UUID appId;
    private Application application;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ruleDslParser = new RuleDslParser(objectMapper);

        recordService = new RecordServiceImpl(recordRepository, applicationRepository,
                fieldDefinitionRepository, ruleDefinitionRepository, textRepository,
                numberRepository, dateRepository, referenceRepository, outboxRepository,
                auditService, ruleDslParser, computeEvaluator, validateEvaluator, triggerEvaluator,
                objectMapper);

        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        sessionContext = SessionContext.of(orgId, userId, "test.user", List.of("grc-analyst"), 1);
        appId = UUID.randomUUID();

        application = Application.create(orgId, "Test App", "TEST", "T", userId);
        when(applicationRepository.findByIdAndOrgId(eq(appId), eq(orgId)))
                .thenReturn(Optional.of(application));
    }

    @Test
    void createRecord_setsOrgIdFromContext() {
        when(recordRepository.nextRecordNumber(orgId, appId)).thenReturn(1);
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("COMPUTE"),
                eq(orgId))).thenReturn(List.of());
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("VALIDATE"),
                eq(orgId))).thenReturn(List.of());
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("TRIGGER"),
                eq(orgId))).thenReturn(List.of());

        CreateRecordCommand cmd =
                new CreateRecordCommand(appId, "Test Record", List.of(), "idem-001");

        ArgumentCaptor<GrcRecord> captor = ArgumentCaptor.forClass(GrcRecord.class);

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext).run(() -> {
            RecordDto dto = recordService.create(cmd);
            assertThat(dto.orgId()).isEqualTo(orgId);
        });

        verify(recordRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
    }

    @Test
    void createRecord_runsComputeRules_andPersistsComputedValues() {
        RuleDefinition computeRule = RuleDefinition.create(orgId, appId, "Risk Score", "COMPUTE",
                "{\"arithmetic\":{\"op\":\"*\",\"operands\":[{\"field\":\"likelihood\"},{\"field\":\"impact\"}]}}");
        computeRule.setTargetField("riskScore");

        when(recordRepository.nextRecordNumber(orgId, appId)).thenReturn(1);
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("COMPUTE"),
                eq(orgId))).thenReturn(List.of(computeRule));
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("VALIDATE"),
                eq(orgId))).thenReturn(List.of());
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("TRIGGER"),
                eq(orgId))).thenReturn(List.of());
        when(computeEvaluator.evaluate(any(), any()))
                .thenReturn(new EvaluationResult.ComputeResult(12.0));

        CreateRecordCommand cmd =
                new CreateRecordCommand(appId, "Risk Record", List.of(), "idem-002");

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext).run(() -> {
            RecordDto dto = recordService.create(cmd);
            assertThat(dto.computedValues()).containsKey("riskScore");
            assertThat(dto.computedValues().get("riskScore")).isEqualTo(12.0);
        });
    }

    @Test
    void createRecord_writesAuditEntry_inSameTransaction() {
        when(recordRepository.nextRecordNumber(orgId, appId)).thenReturn(1);
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), any(), any()))
                .thenReturn(List.of());

        CreateRecordCommand cmd =
                new CreateRecordCommand(appId, "Audit Test", List.of(), "idem-003");

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext)
                .run(() -> recordService.create(cmd));

        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void createRecord_throwsValidationException_whenValidateRuleFails() {
        RuleDefinition validateRule = RuleDefinition.create(orgId, appId, "Name Required",
                "VALIDATE", "{\"compare\":{\"field\":\"name\",\"op\":\"NEQ\",\"value\":\"\"}}");

        when(ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(any(), eq("VALIDATE"),
                eq(orgId))).thenReturn(List.of(validateRule));
        when(validateEvaluator.evaluate(any(), any()))
                .thenReturn(EvaluationResult.ValidateResult.fail("name", "name must not be empty"));

        CreateRecordCommand cmd = new CreateRecordCommand(appId, "", List.of(), "idem-004");

        assertThatThrownBy(
                () -> ScopedValue.where(SessionContextHolder.SESSION, sessionContext).call(() -> {
                    recordService.create(cmd);
                    return null;
                })).isInstanceOf(ValidationException.class);
    }

    @Test
    void softDelete_setsDeletedAtAndWritesAuditEntry() {
        UUID recordId = UUID.randomUUID();
        GrcRecord record = GrcRecord.create(orgId, appId, 1, "T-001", userId);
        when(recordRepository.findByIdAndOrgId(recordId, orgId)).thenReturn(Optional.of(record));
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScopedValue.where(SessionContextHolder.SESSION, sessionContext)
                .run(() -> recordService.softDelete(recordId));

        assertThat(record.isDeleted()).isTrue();
        assertThat(record.getDeletedAt()).isNotNull();
        verify(auditService).log(any(AuditEvent.class));
    }

    @Test
    void getRecord_throwsNotFound_whenOrgIdDoesNotMatch() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findByIdAndOrgId(recordId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> ScopedValue.where(SessionContextHolder.SESSION, sessionContext).call(() -> {
                    recordService.get(recordId);
                    return null;
                })).isInstanceOf(RecordNotFoundException.class);
    }
}
