---
applyTo: 'backend/**'
description: 'Backend Java rules for GRC platform. Use when writing or reviewing any backend Java, Gradle, or Spring Boot code.'
---

# Backend — Java / Spring Boot Rules

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
    case TextValue  tv -> tv.text();
    case NumericValue nv -> nv.value().toPlainString();
    case DateValue  dv -> dv.date().toString();
    case BooleanValue bv -> bv.flag() ? "Yes" : "No";
};
```

> **Full coding standards, idioms, anti-patterns, and before/after examples are in
> `.github/instructions/java-coding-standards.instructions.md`.**
> That file has `applyTo: "backend/**/*.java"` and is automatically applied to all Java files.

Summary of the style contract:

- Streams over for-loops for transformations; `allMatch`/`anyMatch` over boolean loops
- `switch` expressions over `if-else` chains; `var` where type is obvious
- Fail-fast: throw domain exceptions immediately; one `@ExceptionHandler` maps each to HTTP/GraphQL
- No log-and-throw — log **once** at the handler boundary
- Validate at the controller boundary via `@Valid`; services trust their inputs
- `HexFormat`, `Objects.equals()`, `stream().toList()`, static `TypeReference` constants
- Line length: 140 chars. No aggressive wrapping.

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
