# Backend — Copilot Instructions

> **Reading order:** Read [`.github/copilot-instructions.md`](../../.github/copilot-instructions.md) (global) first, then this file. All global rules apply here without exception.

## Tech Stack

Java 21 · Spring Boot 3.5.x · Spring for GraphQL 1.3.x · Hibernate 6.x · Liquibase 4.x · Gradle 8.x · Testcontainers 1.19.x

---

## Project Structure

```
backend/
├── platform-api/       ← Spring Boot entry point; GraphQL + REST controllers
├── platform-core/      ← Domain entities, services, rule engine, audit (no Spring Boot dep)
├── platform-workflow/  ← Workflow state machine (depends only on platform-core)
├── platform-notification/ ← Notification engine (depends only on platform-core)
├── platform-graph/     ← Neo4j projection worker (depends only on platform-core)
├── platform-search/    ← Search service abstraction (depends only on platform-core)
├── platform-reporting/ ← Reporting engine (depends only on platform-core)
├── platform-integration/ ← Integration framework (depends only on platform-core)
└── db/migrations/      ← Liquibase changelogs only
```

---

## Module Dependency Enforcement

The `build.gradle` in each module declares only its allowed dependencies.
`platform-core` has no Spring Boot imports — verifiable via `./gradlew :platform-core:dependencies`.
Any sibling-module import is a compile error. Raise an ADR before adding cross-module dependencies.

---

## Java 21 Mandatory Patterns

```java
// Context propagation — ALWAYS ScopedValue, NEVER ThreadLocal
static final ScopedValue<SessionContext> SESSION = ScopedValue.newInstance();

// Immutable DTOs — use records
public record CreateRecordCommand(UUID orgId, String name, UUID moduleTypeId) {}

// Discriminated unions — use sealed classes
public sealed interface FieldValue permits TextValue, NumericValue, DateValue, BooleanValue {}

// Pattern matching switch — no instanceof casts
String display = switch (fieldValue) {
    case TextValue tv    -> tv.text();
    case NumericValue nv -> nv.value().toPlainString();
    case DateValue dv    -> dv.date().toString();
    case BooleanValue bv -> bv.flag() ? "Yes" : "No";
};
```

---

## Service Layer Rules

- All business logic in `@Service` classes — never in controllers or repositories.
- `@Transactional` goes on service methods — never on controllers or resolvers.
- Every mutating service method calls `auditService.log(...)` within the same transaction.
- Virtual threads for all I/O-bound work: `Executors.newVirtualThreadPerTaskExecutor()`.

---

## Security Rules

- Input validation via `@Valid` + `jakarta.validation` annotations at controller boundary.
- Never validate inside service internals — only at system entry points.
- SQL: named JPA parameters or JPQL only — never string-concatenated queries.
- Secrets: `@Value("${...}")` resolved from environment or vault — never hardcoded literals.

---

## Testing

- JUnit 5 + AssertJ + Testcontainers for integration tests.
- Unit tests: pure Java, no Spring context, no mocks of databases.
- Integration tests: `@SpringBootTest` + Testcontainers — test against real SQL Server and Neo4j.
- Test class naming: `{Subject}Test` for unit tests, `{Subject}IntegrationTest` for integration tests.

---

## Agent Checklist (Backend)

1. Read the relevant `docs/modules/NN-*.md` spec before writing code.
2. Check `docs/adr/` — an ADR may already constrain the design.
3. Write the failing test first. Confirm it fails with the right error before implementing.
4. Implement using the patterns above.
5. Update `docs/modules/` if the implementation diverges from the spec.
