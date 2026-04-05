# GRC Platform — Global Copilot Instructions

## Context

This is an enterprise GRC (Governance, Risk & Compliance) platform built for a single-bank deployment.
Tech stack: React 18 · TypeScript 5 · Java 21 · Spring Boot 3.5.x · SQL Server 2025 · Neo4j 5.x · Keycloak 24.x · Apigee · Redis 7.x

See `docs/modules/` for module-level specs. See `docs/adr/` for architecture decisions.

---

## 0. Operating System & Shell — Non-Negotiable

- **Development OS is Ubuntu Linux.** All terminal commands, scripts, and file-system interactions use **bash** (`#!/usr/bin/env bash`).
- **Never use PowerShell, `cmd.exe`, Windows paths, or Windows-specific tools** in generated code, scripts, or instructions unless explicitly creating the `*.ps1` Windows companion script.
- Use `${VAR}` syntax for shell variables, not `$env:VAR` (PowerShell).
- Path separators are `/`. Never generate `\` path separators outside the `*.ps1` file.
- Package installation uses `apt-get` (e.g. `sudo apt-get install -y openjdk-21-jdk`).
- Use `ss`, `nc`, or `/proc/net/tcp` for port checks — not `netstat` or `Test-NetConnection`.
- Dev environment scripts live in `infrastructure/scripts/`:
    - `dev-setup.sh` — canonical Ubuntu/bash setup (run this one)
    - `dev-teardown.sh` — stop all local services
    - `dev-setup.ps1` — Windows companion (do not run on Ubuntu)
    - `dev-teardown.ps1` — Windows companion teardown

---

## 1. Test-Driven Development (TDD) — Non-Negotiable

- **Always write the failing test first.** No feature code exists without a corresponding failing test.
- Test method signature and assertion must be committed before the implementation compiles.
- All tests are regression tests — they must pass on every build, forever.
- Coverage target: **≥ 90% branch coverage** on the service layer.
- Integration tests use **Testcontainers** for SQL Server and Neo4j — never mock the database in integration tests.

```java
// CORRECT: Write this first, before any implementation
@Test
void createRecord_whenNameIsBlank_throwsValidationException() {
    assertThatThrownBy(() -> recordService.create(blankNameRequest, testOrg))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("name");
}
```

---

## 2. Security — OWASP Top 10 by Construction

- **No hardcoded secrets.** All credentials via environment variable or vault reference (HashiCorp Vault / Azure Key Vault).
- **No generic exception catch.** Catch specific exception types only — never `catch (Exception e)`.
- **No raw SQL string concatenation.** Always use parameterized queries or JPA named parameters.
- **No `@JsonTypeInfo` / polymorphic Jackson deserialization.** Use strict AST model classes (OWASP A08).
- Rule engine JSON is parsed **only via `RuleDslParser`** — never via raw `ObjectMapper`.
- All user input validated at system boundaries (GraphQL resolvers, REST controllers). Never in service internals.
- JWT freshness filter: 5-minute tokens + `role_version` claim checked on every request.
- Idempotency key required on all state-changing API calls.

---

## 3. Java 21 Patterns

- **Never use `ThreadLocal`** — use Java 21 `ScopedValue` for context propagation (org_id, user_id).
- Use sealed classes for discriminated unions (rule nodes, event types, field value variants).
- Use virtual threads for I/O-bound work (`Executors.newVirtualThreadPerTaskExecutor()`).
- Use records for immutable value objects (commands, DTOs, query results).
- Use `switch` expressions with pattern matching — no `instanceof` casts.
- Cognitive complexity limit: **≤ 10 per method** (enforced by SonarQube).
- No `TODO` or `FIXME` comments in committed code.

---

## 4. Spring Boot / GraphQL Conventions

- **All GraphQL collection resolvers use `@BatchMapping`** — never `@SchemaMapping` on collections (prevents N+1).
- Every mutation service method writes to `audit_log` within the **same transaction**.
- Every async side-effect (email, webhook, Neo4j sync) goes through the **transactional outbox** — never called directly in a transaction.
- Use `ScopedValue` + `SESSION_CONTEXT` to propagate `org_id` to the SQL layer — never via parameter threading.
- No `@Transactional` on controllers or GraphQL resolvers — transactions begin in the service layer.

```java
// CORRECT: BatchMapping on collections
@BatchMapping
public Map<Record, List<Attachment>> attachments(List<Record> records) { ... }

// WRONG: SchemaMapping on collections (N+1)
@SchemaMapping
public List<Attachment> attachments(Record record) { ... }
```

---

## 5. Audit — Every Mutation is Logged

Every service method that mutates state must call `AuditService.log(...)` in the same transaction:

```java
recordRepository.save(record);
auditService.log(AuditEvent.of(RECORD_UPDATED, record.getId(), actor, changes));
// Both committed or neither — via @Transactional on the service method
```

---

## 6. Liquibase Migrations

- **Naming:** `V{YYYYMMDD}_{NNN}_{description}.xml` (e.g., `V20260405_001_init-org-schema.xml`)
- Every migration must include a `<rollback>` block.
- Use contexts: `main` for production schema, `test` for test seed data.
- Never modify an existing migration — always add a new one.
- All column names: `snake_case`. All table names: plural `snake_case`.

---

## 7. Commit and ADR Policy

- Commit messages: `type(scope): description` — e.g., `feat(record): add computed field aggregation`
- Types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`
- **Every change that affects architecture or module interfaces requires an ADR** in `docs/adr/`.
- ADR and code changes must be in the **same PR**. PRs referencing a module without touching its spec doc are blocked.
- Design docs (`docs/modules/NN-*.md`) are always updated in the same PR as the code they describe.

---

## 8. Module Boundaries

```
platform-api        → depends on all modules (top of graph)
platform-core       → zero Spring Boot dependencies; testable without container
platform-workflow   → depends only on platform-core
platform-*          → depends only on platform-core; never on sibling modules
```

- Violations are compile-time errors enforced by Gradle dependency rules.
- Any change that blurs a module boundary requires an ADR.

---

## 9. CI / Quality Gates (All Blocking)

- SonarQube: 0 Critical/Blocker issues; coverage ≥ 90% on service layer
- Checkmarx: 0 High/Critical findings
- All tests green
- No TODO/FIXME in committed code
- No merge if module spec doc (`docs/modules/`) is stale relative to code changes

---

## 10. Agent Workflow Checklist

Before generating any code:

1. Read the relevant `docs/modules/NN-*.md` spec.
2. Check `docs/adr/` for decisions that affect this area.
3. **Area-specific instructions are auto-attached — no manual read needed.** (See §11 for how this works.)
4. Write the failing test first.
5. Implement to make the test pass.
6. Update `docs/modules/NN-*.md` if the implementation diverges from the spec.
7. Create/update ADR if architecture is affected.

---

## 11. Scoped Instruction Files — Auto-Attached by Copilot

All area-specific rules live in `.github/instructions/` as `*.instructions.md` files. Each file declares an `applyTo` glob — Copilot automatically injects the relevant files into context whenever you open or edit a matching file. **No manual reading required.**

| File                                                                                                  | Auto-attaches when editing…    |
| ----------------------------------------------------------------------------------------------------- | ------------------------------ |
| [`backend.instructions.md`](.github/instructions/backend.instructions.md)                             | `backend/**`                   |
| [`java-coding-standards.instructions.md`](.github/instructions/java-coding-standards.instructions.md) | `backend/**/*.java`            |
| [`platform-core.instructions.md`](.github/instructions/platform-core.instructions.md)                 | `backend/platform-core/**`     |
| [`platform-api.instructions.md`](.github/instructions/platform-api.instructions.md)                   | `backend/platform-api/**`      |
| [`platform-workflow.instructions.md`](.github/instructions/platform-workflow.instructions.md)         | `backend/platform-workflow/**` |
| [`db-migrations.instructions.md`](.github/instructions/db-migrations.instructions.md)                 | `backend/db/**`                |
| [`frontend.instructions.md`](.github/instructions/frontend.instructions.md)                           | `frontend/**`                  |
| [`infrastructure.instructions.md`](.github/instructions/infrastructure.instructions.md)               | `infrastructure/**`            |

**Example — editing a Java file in `platform-core`:** Copilot automatically includes this global file **+** `backend.instructions.md` **+** `java-coding-standards.instructions.md` **+** `platform-core.instructions.md`. All rules are additive; none of the scoped files can loosen a rule defined here.
