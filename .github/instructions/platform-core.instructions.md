---
applyTo: 'backend/platform-core/**'
description: 'platform-core module rules: domain entities, rule engine, audit hash chain, ScopedValue context. Use when writing or reviewing platform-core code.'
---

# platform-core — Module Rules

## Purpose

`platform-core` is the domain heart of the GRC platform. It contains:

- Platform engine entities (records, field values, module types, relationships, workflow, audit)
- The rule engine (`RuleDslParser`, `RuleEvaluator`, three execution contexts)
- Audit service and hash-chain logic
- Access materialization (`record_access` table)
- `ScopedValue`-based session context (`SessionContext`)
- GRC domain slices: one sub-package per module (`org/`, `policy/`, `risk/`, `control/`, …)

**This module has ZERO Spring Boot dependencies.** It must compile and all unit tests must pass without a Spring container.

---

## Package Structure — Vertical Slices

`platform-core` is organised as **two zones**:

### Zone 1 — Platform Engine (`com.grcplatform.core.*`)

Stable API surface. GRC modules depend on it; it never imports from GRC slices.

```
com.grcplatform.core/
  audit/          AuditService, AuditServiceImpl, AuditEvent
  context/        SessionContext, SessionContextHolder
  domain/         GrcRecord, FieldValue*, Application, RuleDefinition, WorkflowInstance, BaseEntity
  dto/            CreateRecordCommand, Page<T>, RecordDto, RecordListQuery, RecordSummaryDto
  exception/      ValidationException, RecordNotFoundException, …
  repository/     GrcRecordRepository, FieldValue*Repository, RuleDefinitionRepository, …
  rule/           RuleDslParser, RuleEvaluator, RuleNode (sealed), …
  service/        RecordService, RecordServiceImpl
  validation/     Validator<C> functional interface
  workflow/       WorkflowService, WorkflowConfig, …
```

### Zone 2 — GRC Domain Slices (`com.grcplatform.<module>.*`)

Fast-moving, module-specific code. Each new GRC module gets its own top-level slice package.

```
com.grcplatform.org/           Module 26 — Org Hierarchy
  OrganizationUnit.java
  UserOrgUnit.java
  OrgUnitRepository.java
  UserOrgUnitRepository.java
  OrgUnitDto.java
  CreateOrgUnitCommand.java
  OrgHierarchyService.java     ← façade interface
  OrgHierarchyServiceImpl.java ← façade implementation; delegates to handlers
  command/
    CreateOrgUnitHandler.java
    MoveOrgUnitHandler.java

com.grcplatform.policy/        Module 15 — Policy Management
com.grcplatform.risk/          Module 16 — Risk Management
com.grcplatform.control/       Module 17 — Control Management
com.grcplatform.compliance/    Module 18 — (Tier B)
…
```

**Rules:**

- GRC slice packages may import from `com.grcplatform.core.*` freely.
- GRC slice packages **must never import from sibling slice packages** (e.g., `com.grcplatform.risk` must not import from `com.grcplatform.control`). Cross-module reactions go through domain events in the outbox.
- New GRC modules always get a new top-level package — never placed inside `com.grcplatform.core`.

---

## Command Handler Pattern

Every mutable operation in a GRC domain slice uses a **command handler**:

```
GRC Service (façade) → CommandHandler.handle(Command) → repos + audit
```

- The façade (`OrgHierarchyService`) is the stable external contract — resolvers and tests call it.
- Each handler is a plain class with a single `handle(Command)` method.
- Handlers accept **extension lists**: `List<Validator<C>>` for validation, `List<DomainEventListener>` for side-effects. Default: `List.of()`.
- Audit code is **not inside the handler** — it is applied via `AuditingCommandHandler` decorator wired in `*SliceConfig`.

```java
public class CreateOrgUnitHandler {
    private final OrgUnitRepository orgUnitRepository;
    private final List<Validator<CreateOrgUnitCommand>> validators;

    public OrgUnitDto handle(CreateOrgUnitCommand cmd) {
        validators.forEach(v -> v.validate(cmd));
        // … core domain logic …
        return unit.toDto();
    }
}
```

---

## Validator Functional Interface

```java
// com.grcplatform.core.validation.Validator
@FunctionalInterface
public interface Validator<C> {
    void validate(C command);   // throws ValidationException

    default Validator<C> and(Validator<C> other) {
        return cmd -> { this.validate(cmd); other.validate(cmd); };
    }
}
```

- Structural validation (null, size, format) uses Jakarta Bean Validation on the command record.
- Business-rule validation uses `Validator<C>` implementations composed in `*SliceConfig`.
- Never add `if (field == null)` checks inside a handler or service method — express constraints as `Validator` beans.

---

## toDto() — Entity Owns Its Projection

Every GRC domain entity must have a `toDto()` method that returns its corresponding DTO record. The service layer calls `entity.toDto()` — never contains manual field-copy logic.

```java
// CORRECT — on the entity
public OrgUnitDto toDto() {
    return new OrgUnitDto(getId(), getOrgId(), parentId, path, depth, unitType, code,
            name, description, managerId, displayOrder, active);
}

// WRONG — in the service
private OrgUnitDto toDto(OrganizationUnit u) {
    return new OrgUnitDto(u.getId(), u.getOrgId(), ...);
}
```

Once MapStruct mappers are added (Tier B onwards), the `toDto()` method on the entity is replaced by a MapStruct-generated interface — but `entity.toDto()` remains as the calling convention in service/handler code.

---

## Lombok — Narrow Use Only

Add `@Getter` to JPA entity classes to eliminate hand-written getter methods. Add `@Slf4j` to service/handler classes for logging.

**Allowed:** `@Getter` on entities, `@Slf4j` on service/handler classes.
**Forbidden:** `@Builder` (JPA no-arg constructor conflict), `@Data`, `@EqualsAndHashCode`, `@Value`, `@RequiredArgsConstructor` on beans in `platform-core`.

```java
@Entity
@Table(name = "organization_units")
@Getter                                // ← CORRECT: eliminates ~15 getter methods
public class OrganizationUnit extends BaseEntity { ... }
```

---

## Rule Engine Rules

- Rule definitions are parsed **exclusively** by `RuleDslParser` — never by raw `ObjectMapper`.
- No `@JsonTypeInfo` anywhere in the rule engine. Use strict, closed AST node types.
- Three execution contexts: `COMPUTE` (field derivation), `VALIDATE` (pre-save validation), `TRIGGER` (post-save side effect).
- Rule depth limit: max 5 nested rule nodes. Enforced in `RuleDslParser.parse()`.
- Rule evaluation is pure and side-effect-free — `RuleEvaluator` never writes to the database.

```java
// CORRECT: parse via RuleDslParser
RuleNode root = ruleDslParser.parse(ruleJson);

// WRONG: direct ObjectMapper deserialization of rule JSON
RuleNode root = objectMapper.readValue(ruleJson, RuleNode.class);
```

---

## Audit Hash Chain

- Hash computation is **synchronous**, within the same transaction as the mutation.
- Uses per-org monotonic `SEQUENCE` — no async window, no tamper gap.
- `AuditService.log(...)` acquires the per-org application-level lock before computing hash.
- `TransactionSynchronization.afterCommit()` updates `lastHash` — never in the main transaction body.

---

## Entity Patterns

- All entities extend `BaseEntity` (id, org_id, created_at, updated_at, version).
- `version` column is used for optimistic locking (`@Version`).
- `org_id` is propagated via `ScopedValue<SessionContext>` — never passed as a method parameter through service layers.
- No `@ManyToMany` JPA mappings — always explicit join entities with their own lifecycle.

---

## Field Value Variants

```java
// FieldValue is a sealed interface — switch dispatch only
public sealed interface FieldValue
    permits TextValue, NumericValue, DateValue, BooleanValue, ReferenceValue, MultiSelectValue {}
```

New field types require: (1) a new `permits` entry, (2) a new persistence converter, (3) DSL parser support, (4) a new test case — all in the same PR.

---

## Testing

- Unit tests: pure Java. No mocks for `RuleEvaluator` — test with real rule inputs.
- Integration tests for `AuditService` use Testcontainers SQL Server — never an H2 in-memory DB.
- Every `RuleDslParser` test asserts both the parse result and the rejection of malformed input.
