# ADR-006 — Rule Engine DSL with Strict AST Parsing

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The GRC platform needs a rule engine for three distinct concerns:
1. **COMPUTE** — derive field values from other fields (e.g., risk score = likelihood × impact)
2. **VALIDATE** — enforce data integrity rules before save (e.g., "due date must be in future")
3. **TRIGGER** — post-save conditions that emit events (e.g., "if risk score > 15, notify CISO")

Rules are configured by GRC administrators, stored as JSON in SQL Server, and evaluated at runtime. Allowing raw `ObjectMapper` deserialization of rule JSON with polymorphic types creates an insecure deserialization vulnerability (OWASP A08).

## Decision

**All rule JSON is parsed exclusively by `RuleDslParser`.** Raw `ObjectMapper` deserialization of rule structures is prohibited by coding standards and Checkmarx SAST rules.

**Rule AST — closed sealed hierarchy:**

```java
public sealed interface RuleNode
    permits AndNode, OrNode, NotNode, CompareNode, FieldRefNode, LiteralNode, FunctionCallNode {}
```

Each `RuleNode` subtype is a plain record with only the fields its type requires. No `@JsonTypeInfo`, no `type: "..."` polymorphic dispatch — the parser makes all type decisions.

**Parser contract:**
- `RuleDslParser.parse(String json)` returns `RuleNode` or throws `RuleParseException`.
- Maximum nesting depth: 5 levels (enforced in parser — prevents stack overflow and complexity).
- Unknown fields in JSON throw `RuleParseException` (strict mode, not lenient).

**Three execution contexts:**
- `COMPUTE` — evaluated before field is stored; result replaces field value.
- `VALIDATE` — evaluated on save; failure throws `ValidationException` with field errors.
- `TRIGGER` — evaluated after commit; match → publish `RuleTriggerEvent` to outbox.

## Consequences

**Positive:**
- Zero polymorphic deserialization vulnerabilities (OWASP A08) — Checkmarx scan passes.
- Rule structure is fully inspectable as a typed AST — tooling and testing are straightforward.
- Maximum depth limit prevents malicious/accidental complexity bomb rules.
- Three explicit contexts reduce the debugging surface compared to a monolithic engine.

**Negative:**
- Adding a new rule node type requires updating the sealed interface, parser, evaluator, and tests — more files than a dynamic approach.
- Stored rules must be re-validated on parser version upgrades.

**Neutral:**
- Rules are stored as JSON in SQL Server but never deserialized without going through `RuleDslParser`.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| Jackson polymorphic `@JsonTypeInfo` | OWASP A08 insecure deserialization. Fails Checkmarx. Rejected by construction. |
| Groovy/SpEL scripting rules | Dynamic code execution is a security and auditability risk. Script injection possible. |
| Drools / external rule engine | Adds a large operational dependency. The GRC rule surface is well-bounded — a custom DSL is simpler and fully auditable. |
