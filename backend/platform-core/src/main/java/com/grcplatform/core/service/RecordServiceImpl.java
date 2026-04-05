package com.grcplatform.core.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grcplatform.core.audit.AuditEvent;
import com.grcplatform.core.audit.AuditService;
import com.grcplatform.core.context.SessionContextHolder;
import com.grcplatform.core.domain.EventOutbox;
import com.grcplatform.core.domain.FieldValueDate;
import com.grcplatform.core.domain.FieldValueNumber;
import com.grcplatform.core.domain.FieldValueReference;
import com.grcplatform.core.domain.FieldValueText;
import com.grcplatform.core.domain.GrcRecord;
import com.grcplatform.core.dto.CreateRecordCommand;
import com.grcplatform.core.dto.FieldValueInput;
import com.grcplatform.core.dto.Page;
import com.grcplatform.core.dto.RecordDto;
import com.grcplatform.core.dto.RecordListQuery;
import com.grcplatform.core.dto.RecordSummaryDto;
import com.grcplatform.core.dto.UpdateRecordCommand;
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
import com.grcplatform.core.rule.EvaluationInput;
import com.grcplatform.core.rule.EvaluationResult;
import com.grcplatform.core.rule.RuleDslParser;
import com.grcplatform.core.rule.TriggerRuleEvaluator;
import com.grcplatform.core.rule.ValidateRuleEvaluator;
import jakarta.transaction.Transactional;

/**
 * Core record lifecycle implementation. Resolves orgId from SessionContext. Writes audit entries in
 * the same transaction. Dispatches outbox events via EventOutboxRepository for async side-effects.
 */
public class RecordServiceImpl implements RecordService {

    private static final Logger log = LoggerFactory.getLogger(RecordServiceImpl.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GrcRecordRepository recordRepository;
    private final ApplicationRepository applicationRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final FieldValueTextRepository textRepository;
    private final FieldValueNumberRepository numberRepository;
    private final FieldValueDateRepository dateRepository;
    private final FieldValueReferenceRepository referenceRepository;
    private final EventOutboxRepository outboxRepository;
    private final AuditService auditService;
    private final RuleDslParser ruleDslParser;
    private final ComputeRuleEvaluator computeEvaluator;
    private final ValidateRuleEvaluator validateEvaluator;
    private final TriggerRuleEvaluator triggerEvaluator;
    private final ObjectMapper objectMapper;

    public RecordServiceImpl(GrcRecordRepository recordRepository,
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
        this.recordRepository = recordRepository;
        this.applicationRepository = applicationRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.textRepository = textRepository;
        this.numberRepository = numberRepository;
        this.dateRepository = dateRepository;
        this.referenceRepository = referenceRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.ruleDslParser = ruleDslParser;
        this.computeEvaluator = computeEvaluator;
        this.validateEvaluator = validateEvaluator;
        this.triggerEvaluator = triggerEvaluator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RecordDto create(CreateRecordCommand command) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();
        var app =
                applicationRepository.findByIdAndOrgId(command.applicationId(), orgId).orElseThrow(
                        () -> new RecordNotFoundException("Application", command.applicationId()));

        var fieldValues = resolveIncomingFieldValues(command.fieldValues());
        runValidateRules(command.applicationId(), orgId, fieldValues);

        int recordNumber = recordRepository.nextRecordNumber(orgId, command.applicationId());
        var displayNumber = app.getRecordPrefix() + "-" + String.format("%04d", recordNumber);
        var record = GrcRecord.create(orgId, command.applicationId(), recordNumber, displayNumber,
                ctx.userId());
        record.setDisplayName(command.displayName());

        var computedValues = runComputeRules(command.applicationId(), orgId, record, fieldValues);
        record.setComputedValues(toJson(computedValues));

        recordRepository.save(record);
        persistFieldValues(record.getId(), orgId, command.fieldValues());
        runTriggerRules(command.applicationId(), orgId, record, fieldValues, computedValues);
        auditService.log(AuditEvent.of("RECORD_CREATED", record.getId(), ctx.userId(), null,
                toJson(fieldValues)));

        return toDto(record, computedValues);
    }

    @Override
    @Transactional
    public RecordDto update(UpdateRecordCommand command) {
        var ctx = SessionContextHolder.current();
        var orgId = ctx.orgId();
        var record = recordRepository.findByIdAndOrgId(command.recordId(), orgId)
                .orElseThrow(() -> new RecordNotFoundException("GrcRecord", command.recordId()));

        var oldValues = loadCurrentFieldValues(record.getId(), orgId);
        var newValues = resolveIncomingFieldValues(command.fieldValues());
        runValidateRules(record.getApplicationId(), orgId, newValues);

        record.setDisplayName(command.displayName());
        record.setUpdatedBy(ctx.userId());

        var computedValues = runComputeRules(record.getApplicationId(), orgId, record, newValues);
        record.setComputedValues(toJson(computedValues));

        recordRepository.save(record);
        persistFieldValues(record.getId(), orgId, command.fieldValues());
        runTriggerRules(record.getApplicationId(), orgId, record, newValues, computedValues);
        auditService.log(AuditEvent.of("RECORD_UPDATED", record.getId(), ctx.userId(),
                toJson(oldValues), toJson(newValues)));

        return toDto(record, computedValues);
    }

    @Override
    @Transactional
    public void softDelete(UUID recordId) {
        var ctx = SessionContextHolder.current();
        var record = recordRepository.findByIdAndOrgId(recordId, ctx.orgId())
                .orElseThrow(() -> new RecordNotFoundException("GrcRecord", recordId));

        record.softDelete(Instant.now());
        record.setUpdatedBy(ctx.userId());
        recordRepository.save(record);
        auditService.log(AuditEvent.of("RECORD_DELETED", record.getId(), ctx.userId(), null, null));
    }

    @Override
    public RecordDto get(UUID recordId) {
        var record =
                recordRepository.findByIdAndOrgId(recordId, SessionContextHolder.current().orgId())
                        .orElseThrow(() -> new RecordNotFoundException("GrcRecord", recordId));
        return toDto(record, parseComputedValues(record.getComputedValues()));
    }

    @Override
    public Page<RecordSummaryDto> list(RecordListQuery query) {
        var orgId = SessionContextHolder.current().orgId();
        int offset = query.page() * query.pageSize();
        var records = recordRepository.findByApplicationIdAndOrgId(query.applicationId(), orgId,
                offset, query.pageSize());
        long total = recordRepository.countByApplicationIdAndOrgId(query.applicationId(), orgId);
        return new Page<>(records.stream().map(this::toSummaryDto).toList(), query.page(),
                query.pageSize(), total);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void runValidateRules(UUID appId, UUID orgId, Map<String, Object> fieldValues) {
        var input = EvaluationInput.of(orgId, null, appId, fieldValues, Map.of());
        var errors = ruleDefinitionRepository
                .findActiveByApplicationIdAndRuleType(appId, "VALIDATE", orgId).stream()
                .map(rd -> (EvaluationResult.ValidateResult) validateEvaluator
                        .evaluate(ruleDslParser.parse(rd.getRuleDsl()), input))
                .filter(vr -> !vr.valid())
                .map(vr -> new ValidationException.ValidationError(vr.fieldKey(), vr.message()))
                .toList();
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private Map<String, Object> runComputeRules(UUID appId, UUID orgId, GrcRecord record,
            Map<String, Object> fieldValues) {
        var computed = new LinkedHashMap<String, Object>();
        // Stateful fold: each rule may read values computed by earlier rules in the same pass
        ruleDefinitionRepository.findActiveByApplicationIdAndRuleType(appId, "COMPUTE", orgId)
                .forEach(rd -> {
                    var input =
                            EvaluationInput.of(orgId, record.getId(), appId, fieldValues, computed);
                    var result = (EvaluationResult.ComputeResult) computeEvaluator
                            .evaluate(ruleDslParser.parse(rd.getRuleDsl()), input);
                    if (rd.getTargetField() != null)
                        computed.put(rd.getTargetField(), result.value());
                });
        return computed;
    }

    private void runTriggerRules(UUID appId, UUID orgId, GrcRecord record,
            Map<String, Object> fieldValues, Map<String, Object> computedValues) {
        var input = EvaluationInput.of(orgId, record.getId(), appId, fieldValues, computedValues);
        var events = ruleDefinitionRepository
                .findActiveByApplicationIdAndRuleType(appId, "TRIGGER", orgId).stream()
                .filter(rd -> ((EvaluationResult.TriggerResult) triggerEvaluator
                        .evaluate(ruleDslParser.parse(rd.getRuleDsl()), input)).triggered())
                .map(rd -> EventOutbox.create(orgId, rd.getTriggerEvent(), "RECORD", record.getId(),
                        toJson(Map.of("recordId", record.getId(), "appId", appId))))
                .toList();
        if (!events.isEmpty()) outboxRepository.saveAll(events);
    }

    private void persistFieldValues(UUID recordId, UUID orgId, List<FieldValueInput> inputs) {
        inputs.forEach(input -> {
            switch (input.fieldType()) {
                case "TEXT" -> textRepository.saveAll(List.of(
                        FieldValueText.of(orgId, recordId, input.fieldDefId(), input.textValue())));
                case "NUMBER" -> numberRepository.saveAll(List.of(FieldValueNumber.of(orgId,
                        recordId, input.fieldDefId(), input.numberValue())));
                case "DATE" -> dateRepository.saveAll(List.of(
                        FieldValueDate.of(orgId, recordId, input.fieldDefId(), input.dateValue())));
                case "REFERENCE" -> referenceRepository
                        .saveAll(List.of(FieldValueReference.of(orgId, recordId, input.fieldDefId(),
                                "RECORD", input.referenceId(), input.referenceLabel())));
                default -> log.warn("Unknown fieldType '{}' on fieldDef {}", input.fieldType(),
                        input.fieldDefId());
            }
        });
    }

    private Map<String, Object> resolveIncomingFieldValues(List<FieldValueInput> inputs) {
        var values = new LinkedHashMap<String, Object>();
        inputs.forEach(
                input -> values.put(input.fieldDefId().toString(), switch (input.fieldType()) {
                    case "TEXT" -> input.textValue();
                    case "NUMBER" -> input.numberValue();
                    case "DATE" -> input.dateValue();
                    case "REFERENCE" -> input.referenceId();
                    default -> null;
                }));
        return values;
    }

    private Map<String, Object> loadCurrentFieldValues(UUID recordId, UUID orgId) {
        var values = new LinkedHashMap<String, Object>();
        textRepository.findByRecordId(recordId, orgId)
                .forEach(v -> values.put(v.getFieldDefId().toString(), v.getValue()));
        numberRepository.findByRecordId(recordId, orgId)
                .forEach(v -> values.put(v.getFieldDefId().toString(), v.getValue()));
        dateRepository.findByRecordId(recordId, orgId)
                .forEach(v -> values.put(v.getFieldDefId().toString(), v.getValue()));
        referenceRepository.findByRecordId(recordId, orgId)
                .forEach(v -> values.put(v.getFieldDefId().toString(), v.getRefId()));
        return values;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> parseComputedValues(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private RecordDto toDto(GrcRecord record, Map<String, Object> computedValues) {
        return new RecordDto(record.getId(), record.getOrgId(), record.getApplicationId(),
                record.getDisplayName(), record.getDisplayNumber(), record.getStatus(),
                record.getWorkflowState(), computedValues, record.getCreatedAt(),
                record.getUpdatedAt(), record.getVersion() != null ? record.getVersion() : 0L);
    }

    private RecordSummaryDto toSummaryDto(GrcRecord record) {
        return new RecordSummaryDto(record.getId(), record.getDisplayName(),
                record.getDisplayNumber(), record.getStatus(), record.getUpdatedAt());
    }
}
