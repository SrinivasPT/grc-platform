# platform-core — Copilot Instructions

> **Reading order:** Read in sequence — (1) [`.github/copilot-instructions.md`](../../../.github/copilot-instructions.md) → (2) [`backend/.github/copilot-instructions.md`](../../.github/copilot-instructions.md) → (3) this file. All parent rules apply without exception.

## Purpose

`platform-core` is the domain heart of the GRC platform. It contains:

- Domain entities (records, field values, module types, relationships)
- The rule engine (`RuleDslParser`, `RuleEvaluator`, three execution contexts)
- Audit service and hash-chain logic
- Access materialization (`record_access` table)
- `ScopedValue`-based session context (`SessionContext`)

**This module has ZERO Spring Boot dependencies.** It must compile and all unit tests must pass without a Spring container.

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
