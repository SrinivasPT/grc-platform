# Phase 1 — Core Platform Engine (COMPLETE)

## Status: All 55 tests pass, 0 failures

## Key design decisions

- Package: `com.grcplatform.core` (not `com.grc.core`)
- Entity naming: `GrcRecord` (not `Record` — avoids java.lang.Record clash)
- Repository interfaces in platform-core (plain Java); Spring Data JPA impls in platform-api
- `@Transactional` = jakarta.transaction (no Spring Boot in platform-core)
- `ScopedValue` is NOT inherited by `Thread.ofVirtual().start()` in Java 21 — only via StructuredTaskScope. Virtual threads need explicit `ScopedValue.where()` rebinding.

## Files created (platform-core)

### Migrations: V004–V009 (db/migrations/sql/) + changelog-master.xml updated

### Exceptions: ValidationException, RecordNotFoundException, OptimisticLockConflictException, RuleParseException, RuleDepthExceededException, RuleCountExceededException, RuleEvaluationException

### Domain: Organization, Application, FieldDefinition, RuleDefinition, GrcRecord, FieldValueText/Number/Date/Reference, RecordRelation, AuditLogEntry, AuditChainHead, EventOutbox

### Repositories: GrcRecord, FieldValueText/Number/Date/Reference, Application, FieldDefinition, RuleDefinition, AuditLog, AuditChainHead, EventOutbox

### Rule engine: EvaluationInput, EvaluationResult, RuleEvaluator(interface), BaseRuleEvaluator, ComputeRuleEvaluator, ValidateRuleEvaluator, TriggerRuleEvaluator, RuleDslParser

### Audit: AuditEvent, AuditService(interface), AuditServiceImpl (SHA-256 hash chain, 3-retry optimistic lock)

### DTOs: FieldValueInput, CreateRecordCommand, UpdateRecordCommand, RecordListQuery, RecordDto, RecordSummaryDto, Page<T>

### Services: RecordService(interface), RecordServiceImpl

### Tests: OrgContextPropagationTest, RuleDslParserTest, ComputeRuleEvaluatorTest, ValidateRuleEvaluatorTest, TriggerRuleEvaluatorTest, AuditServiceTest, RecordServiceTest

## RuleNode JSON key dispatch (RuleDslParser)

- `and`/`or`/`not` → logical nodes
- `compare` → CompareNode {field, op, value}
- `field`/`value` → FieldRefNode/LiteralNode
- `fn` → FunctionCallNode {name, args}
- `arithmetic` → ArithmeticNode {op, operands}
- `if` → IfNode {condition, then, else}
- `aggregate` → AggregateNode {source, relationType, direction, filter, function, field}
- `lookup` → LookupNode {source, listKey, matchField, matchValue, returnField}

## AuditServiceImpl hash formula

SHA-256(prevHash + orgId + entityId + operation + newValue + sequenceNumber)
3-retry with exponential backoff (50ms \* 2^attempt) on OptimisticLockException
