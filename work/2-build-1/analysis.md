# GRC Platform — Phase 4 Architecture Analysis

> **Author:** GitHub Copilot  
> **Date:** April 6, 2026  
> **Scope:** Phase 4 Tier A implementation review — Modules 26, 15, 16, 17  
> **Status:** Recommendations for Phase 4 Tier B onwards

---

## 1. Executive Summary

The current Tier A implementation is **correct and coherent for Phase 1 of 14 GRC modules**. The platform core (record engine, rule DSL, audit chain, workflow engine) is well-architected and will scale. The concern is not the platform core — it's the **GRC domain layer** (`platform-core/service/`) and where it will be in 18 months when all 13 remaining domain modules are built.

**The problem is not complexity today. The problem is the trajectory.**

At current pace:

- Tier A: 4 modules added 5 services, 7 entities, 8 repositories, 4 resolvers into `platform-core`
- Tiers B+C: 9 more modules will add another ~15 services, 20+ entities, 30+ repositories
- All living in `com.grcplatform.core.service.*` and `com.grcplatform.core.domain.*`

Without re-structuring, by Phase 4 completion you will have:

- ~20 `*ServiceImpl` classes in one package
- ~35 JPA entities in one `domain` package
- ~40 repository interfaces in one `repository` package
- A `ServiceConfig.java` with 30+ `@Bean` methods
- No discoverable seam between GRC domain concerns and the platform engine

**The recommendation is not to separate into separate Gradle projects** (that would break the module boundary rules and create deployment complexity for what is — by ADR-001 — intentionally a modular monolith). Instead, the recommendation is to **restructure within the existing Gradle module structure** using three techniques:

1. **Domain-scoped packages** (vertical slices within `platform-core`)
2. **Command Handler pattern** (not full CQRS — command objects already exist, add handlers)
3. **Functional composition for pipeline concerns** (audit, outbox, validation)

---

## 2. Current Structure — What Works, What Will Hurt

### 2.1 What Works Well (Keep It)

| Aspect                                                                  | Why It Works                                                    |
| ----------------------------------------------------------------------- | --------------------------------------------------------------- |
| `platform-core` with no Spring Boot dependency                          | Testable at 8s per run; cognitive simplicity                    |
| Repository interface in `platform-core` + adapter/JPA in `platform-api` | Clean hexagonal boundary; database detail never leaks to domain |
| `ScopedValue<SessionContext>` for org/user propagation                  | No parameter threading, no ThreadLocal risk                     |
| Sealed class `RuleNode` AST                                             | Security-by-construction; prevents polymorphic deserialization  |
| `@BatchMapping` mandate                                                 | GraphQL N+1 eliminated at the architecture level                |
| Outbox for all side-effects                                             | Reliable at-least-once delivery, no transaction coupling        |
| Audit in same transaction                                               | Immutable chain never diverges from state                       |

### 2.2 What Will Hurt at Scale (Change)

#### Pain Point 1: Monolithic `service` package

```
platform-core/service/
  RecordService.java          ← platform engine
  RecordServiceImpl.java      ← platform engine
  OrgHierarchyService.java    ← GRC domain
  OrgHierarchyServiceImpl.java
  PolicyService.java          ← GRC domain
  PolicyServiceImpl.java
  RiskService.java            ← GRC domain
  RiskServiceImpl.java
  ControlService.java         ← GRC domain
  ControlServiceImpl.java
  ... + 15 more by Phase 4 end
```

No structural separation between the platform engine (deserves its own immutable API surface) and the GRC domain services (fast-moving, module-specific). A developer working on Risk cannot tell where Risk ends and the platform begins by looking at imports.

#### Pain Point 2: God-class ServiceImpl with no internal structure

`OrgHierarchyServiceImpl` has 5+ distinct operations: `createUnit`, `moveUnit`, `deactivateUnit`, path cascade, manager resolution. These share some infrastructure (repositories, audit) but are otherwise independent flows. As requirements grow — e.g., per-org custom validation rules for unit creation, approval workflows for org moves — the service method will grow to 80+ lines and exceed the cognitive complexity limit.

**Real-world customization pressure on `OrgHierarchyServiceImpl.createUnit()` alone:**

- Custom validation: some orgs require code uniqueness, others don't
- Approval workflow: some orgs require manager approval before activation
- Integration hooks: notify HRMS on org change
- Import paths: bulk CSV import vs. UI creation should share the same core logic
- Audit granularity: some orgs require field-level diff audit on org changes

All of these will try to live in `createUnit()`. Within 12 months it will be untestable.

#### Pain Point 3: `ServiceConfig.java` doing all wiring

Currently 14 `@Bean` methods; will be 35+ by Phase 4 completion. Every new module requires touching `ServiceConfig.java`. Any mistake there breaks the entire application context. There is no compile-time enforcement that all dependencies of a new service bean are present.

#### Pain Point 4: `domain` package has no internal structure

All JPA entities in one flat package makes it impossible to enforce domain-module cohesion. `ControlEffectiveness` can freely import `PolicyAcknowledgment`. By Phase 4 end, nothing prevents cross-module entity coupling that the module spec explicitly prohibits.

#### Pain Point 5: Commands as data-only records in `dto` package

`CreateOrgUnitCommand` is a record in `dto` — the command object. But the command _execution logic_ is buried inside `OrgHierarchyServiceImpl.createUnit()`. They are conceptually tightly coupled but structurally miles apart. This made sense with one module; with eight modules it forces the developer to understand the entire service class to understand what one command does.

---

## 3. Recommended Architecture

### 3.1 Core Principle: Vertical Slice Packaging Within `platform-core`

Stop organizing by technical layer (`domain/`, `service/`, `repository/`, `dto/`) and start organizing by **domain concern within each layer**, using sub-packages.

#### Proposed package structure

```
platform-core/
  src/main/java/com/grcplatform/
    ├── core/                             # Platform Engine — stable API surface
    │   ├── audit/                        # (unchanged)
    │   ├── context/                      # (unchanged)
    │   ├── domain/                       # ONLY platform engine entities
    │   │   ├── GrcRecord.java
    │   │   ├── FieldValue*.java
    │   │   ├── Application.java
    │   │   ├── RuleDefinition.java
    │   │   ├── WorkflowInstance.java
    │   │   └── BaseEntity.java
    │   ├── dto/                          # ONLY platform engine commands/queries
    │   ├── repository/                   # ONLY platform engine repository interfaces
    │   ├── rule/                         # (unchanged — sealed AST)
    │   ├── service/                      # ONLY RecordService
    │   ├── workflow/                     # (unchanged)
    │   └── exception/                    # (unchanged)
    │
    ├── org/                              # Module 26 — Org Hierarchy domain slice
    │   ├── OrgUnit.java                  # entity (was OrganizationUnit)
    │   ├── UserOrgUnit.java              # entity
    │   ├── OrgUnitRepository.java        # repository interface
    │   ├── UserOrgUnitRepository.java
    │   ├── OrgUnitDto.java               # query result
    │   ├── command/
    │   │   ├── CreateOrgUnitCommand.java # data record (was in dto/)
    │   │   ├── CreateOrgUnitHandler.java # execution logic (extracted from ServiceImpl)
    │   │   ├── MoveOrgUnitCommand.java
    │   │   ├── MoveOrgUnitHandler.java
    │   │   └── DeactivateOrgUnitHandler.java
    │   ├── query/
    │   │   ├── GetOrgUnitQuery.java
    │   │   └── OrgUnitQueryHandler.java
    │   └── OrgHierarchyService.java      # façade — delegates to handlers
    │
    ├── policy/                           # Module 15 — Policy Management
    │   ├── PolicyAcknowledgment.java
    │   ├── PolicyAcknowledgmentRepository.java
    │   ├── PolicyAcknowledgmentDto.java
    │   ├── command/
    │   │   ├── AcknowledgePolicyCommand.java
    │   │   └── AcknowledgePolicyHandler.java
    │   └── PolicyService.java
    │
    ├── risk/                             # Module 16 — Risk Management
    │   ├── RiskScore.java
    │   ├── RiskAppetiteThreshold.java
    │   ├── RiskScoreRepository.java
    │   ├── RiskAppetiteThresholdRepository.java
    │   ├── RiskScoreDto.java
    │   ├── command/
    │   │   ├── ComputeRiskScoreCommand.java
    │   │   ├── ComputeRiskScoreHandler.java
    │   │   ├── UpdateResidualScoreCommand.java
    │   │   ├── UpdateResidualScoreHandler.java
    │   │   └── SetRiskAppetiteHandler.java
    │   └── RiskService.java
    │
    └── control/                          # Module 17 — Control Management
        ├── ControlTestResult.java
        ├── ControlEffectiveness.java
        ├── (repositories)
        ├── ControlEffectivenessDto.java
        ├── command/
        │   ├── RecordTestResultCommand.java
        │   ├── RecordTestResultHandler.java
        │   └── ComputeEffectivenessHandler.java
        └── ControlService.java
```

**What changes, what doesn't:**

- `ServiceConfig.java` still wires everything — but now imports from `com.grcplatform.risk.*` instead of a flat list
- The database adapter layer (`platform-api/repository/`) maps 1:1 to domain repository interfaces, just from different packages
- Platform engine (`com.grcplatform.core.*`) is now a **stable API** — GRC modules depend on it, never the reverse
- Gradle configuration is unchanged — still one `platform-core` Gradle module

### 3.2 Command Handler Pattern (Not Full CQRS)

The current pattern:

```
GraphQL Resolver → XService interface → XServiceImpl.someMethod(params) → repos + audit
```

The proposed pattern:

```
GraphQL Resolver → XService (façade) → XCommandHandler.handle(XCommand) → repos + audit
```

#### Why command handlers help here

A **command handler** is just a class whose single responsibility is executing one command. It is not CQRS (no separate read model, no event sourcing). It is just good single-responsibility decomposition.

**Current `OrgHierarchyServiceImpl.createUnit()` — mixed concerns:**

```java
public OrgUnitDto createUnit(CreateOrgUnitCommand cmd) {
    // 1. Input validation
    if (cmd.name() == null || cmd.name().isBlank()) { ... }
    if (cmd.unitType() == null || cmd.unitType().isBlank()) { ... }

    // 2. Parent resolution
    String parentPath = "/";
    int depth = 0;
    if (parentId != null) { ... parent lookup ... }

    // 3. Path computation
    String path = parentPath + newId.replace("-", "") + "/";

    // 4. Entity construction
    var unit = OrganizationUnit.create(...);
    unit.setId(newId);

    // 5. Persistence
    var saved = orgUnitRepository.save(unit);

    // 6. Mapping to DTO
    return toDto(saved);
}
```

This is already 50 lines and has exactly 0 extension points. When a customer requires "notify HR system on new org unit creation" — where does that code go? In `createUnit()`. When they require "approval workflow before org unit is activated" — where? In `createUnit()`. When they require custom validation (e.g., org code must match a regex) — where? In `createUnit()`.

**Proposed `CreateOrgUnitHandler`:**

```java
public class CreateOrgUnitHandler {

    private final OrgUnitRepository orgUnitRepository;
    private final AuditService auditService;
    private final List<OrgUnitValidator> validators;        // extension point ①
    private final List<OrgUnitCreatedListener> listeners;  // extension point ②

    public OrgUnitDto handle(CreateOrgUnitCommand cmd) {
        var ctx = SessionContextHolder.current();

        // Validate — delegates to pluggable validators
        validators.forEach(v -> v.validate(cmd, ctx));

        // Core logic — pure and testable
        var unit = buildUnit(cmd, ctx);
        var saved = orgUnitRepository.save(unit);
        auditService.log(AuditEvent.of("ORG_UNIT_CREATED", saved.getId(), ctx.userId(), null, cmd.name()));

        // Post-creation hooks — pluggable listeners
        var dto = toDto(saved);
        listeners.forEach(l -> l.onCreated(dto, ctx));

        return dto;
    }

    private OrgUnit buildUnit(CreateOrgUnitCommand cmd, SessionContext ctx) { ... }
}
```

Extension points ① and ② are `List<>` constructor parameters — injected by `ServiceConfig`. An empty list by default. A custom validator or listener is added by wiring a new bean in `ServiceConfig` — no modification of `CreateOrgUnitHandler` required.

#### Query handlers (read side)

Queries don't need handlers unless they are complex. Keep simple queries directly in the service façade. Add a query handler only when the query involves non-trivial business logic (e.g., "get org tree with effectiveness scores rolled up").

#### The Façade

`OrgHierarchyService` (the interface) remains the stable external contract. Its implementation delegates:

```java
public class OrgHierarchyService {

    private final CreateOrgUnitHandler createHandler;
    private final MoveOrgUnitHandler moveHandler;
    private final DeactivateOrgUnitHandler deactivateHandler;
    private final OrgUnitQueryHandler queryHandler;

    public OrgUnitDto createUnit(CreateOrgUnitCommand cmd) {
        return createHandler.handle(cmd);
    }

    public OrgUnitDto moveUnit(UUID unitId, UUID newParentId) {
        return moveHandler.handle(new MoveOrgUnitCommand(unitId, newParentId));
    }
    // ...
}
```

GraphQL resolvers and tests never change — they still talk to `OrgHierarchyService`.

### 3.3 Functional Composition for Cross-Cutting Concerns

Several concerns must run for every state-mutating command:

1. Audit logging
2. Idempotency check
3. Input validation
4. Outbox publishing (for async side-effects)

Currently these are imperatively chained inside each `ServiceImpl`. Functional composition makes this pipeline explicit and parameterizable.

#### The `CommandPipeline<C, R>` abstraction

```java
@FunctionalInterface
public interface CommandHandler<C, R> {
    R handle(C command);

    /** Wraps this handler with a pre/post behavior. */
    default CommandHandler<C, R> then(UnaryOperator<R> postProcessor) {
        return cmd -> postProcessor.apply(this.handle(cmd));
    }
}
```

Audit decoration example (replaces repetitive `auditService.log(...)` in each handler):

```java
public class AuditingHandler<C, R> implements CommandHandler<C, R> {

    private final CommandHandler<C, R> delegate;
    private final AuditService auditService;
    private final Function<C, String> operationName;
    private final Function<R, UUID> entityId;

    @Override
    public R handle(C command) {
        var ctx = SessionContextHolder.current();
        R result = delegate.handle(command);
        auditService.log(AuditEvent.of(
            operationName.apply(command),
            entityId.apply(result),
            ctx.userId(), null, null));
        return result;
    }
}
```

Usage in `ServiceConfig`:

```java
@Bean
CreateOrgUnitHandler createOrgUnitHandler(OrgUnitRepository repo, AuditService audit) {
    var core = new CreateOrgUnitHandler(repo, List.of(), List.of());
    return new AuditingHandler<>(core, audit,
        cmd -> "ORG_UNIT_CREATED",
        dto -> dto.id());
}
```

Now `CreateOrgUnitHandler` has **zero audit code** — it is a pure domain operation. Audit is assembled in `ServiceConfig`. This is Java's function composition applied to the command handler pattern.

#### Validation pipeline

Instead of validation logic scattered inside each handler:

```java
@FunctionalInterface
public interface Validator<C> {
    void validate(C command);  // throws ValidationException

    default Validator<C> and(Validator<C> other) {
        return cmd -> { this.validate(cmd); other.validate(cmd); };
    }
}
```

Validators are small, single-purpose, independently testable, and composable:

```java
Validator<CreateOrgUnitCommand> nameRequired = cmd -> {
    if (cmd.name() == null || cmd.name().isBlank())
        throw new ValidationException("name", "Name is required");
};

Validator<CreateOrgUnitCommand> codeUnique = cmd -> {
    if (orgUnitRepository.findByOrgIdAndCode(orgId, cmd.code()).isPresent())
        throw new ValidationException("code", "Code already exists");
};

// Composed in ServiceConfig per org configuration
Validator<CreateOrgUnitCommand> validator = nameRequired.and(codeUnique);
```

Custom per-org validation rules (loaded from `Application.config` via the rule engine) become another composable validator in the chain. This is exactly the extension point the single bank will need.

### 3.4 Module Config — Self-Registering Beans

To avoid `ServiceConfig.java` becoming a 600-line god-class:

Each domain slice gets its own `@Configuration` class in `platform-api`:

```
platform-api/config/
  CoreServiceConfig.java        ← RecordService, AuditService, RuleEngine
  OrgSliceConfig.java           ← OrgHierarchyService + all handlers
  PolicySliceConfig.java        ← PolicyService + handlers
  RiskSliceConfig.java          ← RiskService + handlers
  ControlSliceConfig.java       ← ControlService + handlers
```

`@Configuration` classes are discovered by Spring Boot auto-configuration. `CoreServiceConfig` becomes stable and rarely touched. Each new domain module adds one new `*SliceConfig.java` without touching existing files.

Enforce this with a convention: `platform-api/config/*SliceConfig.java` for GRC domain beans; `platform-api/config/CoreServiceConfig.java` for engine beans.

### 3.5 Should GRC Modules Be Separate Gradle Projects?

**No.** This would contradict ADR-001 and introduce deployment complexity the architecture deliberately avoided.

**The right boundary is within the Gradle module, at the package level.**

In a modular monolith, the module boundary is enforced by:

1. Package visibility (Java package-private access)
2. Convention (documented in instruction files)
3. ArchUnit tests for production (optional, Phase 5)

What does separation into separate Gradle projects buy?

- Compile-time import prohibition ✓ (but you already have this in `platform-core` for Spring Boot)
- Independent deployment ✗ (not needed, contrary to ADR-001)
- Independent versioning ✗ (creates diamond-dependency hell with the shared `platform-core`)
- Clearer graph ✓ (but package-level structure provides the same clarity without the overhead)

**Verdict:** Keep the current Gradle structure. Restructure at the package level.

---

## 4. Prioritized Recommendations

### Immediate (before Tier B begins — next sprint)

| #   | Recommendation                                                                                         | Effort | Impact                                     |
| --- | ------------------------------------------------------------------------------------------------------ | ------ | ------------------------------------------ |
| 1   | **Restructure `platform-core` packages** into vertical slices (`org/`, `risk/`, `policy/`, `control/`) | Medium | High — every new module gets a home        |
| 2   | **Split `ServiceConfig.java` into `*SliceConfig.java` files**                                          | Low    | Medium — prevents future breakage          |
| 3   | **Add `Validator<C>` and compose validators in SliceConfig**                                           | Low    | High — removes if-statements from handlers |

### Before Tier B (recommended, not blocking)

| #   | Recommendation                                                          | Effort | Impact                                                     |
| --- | ----------------------------------------------------------------------- | ------ | ---------------------------------------------------------- |
| 4   | **Extract command handlers from ServiceImpls**                          | Medium | High — enables per-handler testing and extension           |
| 5   | **`AuditingHandler` decorator** removes audit boilerplate from handlers | Low    | Medium                                                     |
| 6   | **`OrgUnitValidator` / `RiskValidator` extension list in SliceConfig**  | Low    | High — enables org-specific validation without code change |

### Phase 5 (hardening, not Phase 4)

| #   | Recommendation                                                     | Effort | Impact                                                                |
| --- | ------------------------------------------------------------------ | ------ | --------------------------------------------------------------------- |
| 7   | ArchUnit tests for package boundaries                              | Medium | Medium — compile-time enforcement                                     |
| 8   | Domain event publishing (not full CQRS) for cross-module reactions | High   | High — eliminates direct service-to-service calls between GRC modules |

### Not Recommended

| Pattern                                 | Reason                                                                                                                                              |
| --------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Full CQRS / Event Sourcing              | Complexity far exceeds the benefit for a single-bank GRC platform. The audit hash chain already provides tamper-evident history.                    |
| Hexagonal ports/adapters per GRC module | Already achieved at the Gradle level. Adding more interfaces creates abstraction without value.                                                     |
| Separate microservices                  | Contradicts ADR-001. Network hops for Risk ↔ Control interactions would kill transactional integrity without significant infrastructure investment. |
| Kotlin / coroutines                     | No return on migration cost; Java 21 virtual threads already solve the concurrency problem.                                                         |

---

## 5. Extensibility Design for Customization

The single-bank deployment creates a specific pattern of customization: the bank **will** want to:

- Add custom validation rules to standard GRC processes
- Add org-specific computed fields (beyond the standard risk scoring)
- Route domain events to bank-specific systems (JIRA for issues, ServiceNow for controls)
- Override the standard risk appetite thresholds and ratings per regulatory requirement (BCBS 239, etc.)

The architecture handles these through **four existing mechanisms** — they just need to be surfaced cleanly:

### 5.1 The Rule Engine DSL (already built)

The `ComputeContext` and `ValidateContext` rule evaluators are the primary extension mechanism for field logic. The bank's configuration team can define custom risk scoring formulas, validation rules, and computed fields without code changes. **This is the most powerful extension point and is already built.**

### 5.2 Pluggable Validators (proposed above)

Command handlers accept `List<Validator<C>>`. The bank's specific requirements (e.g., "every Risk must have an owner from the approved risk owner list") become a named `Validator` bean wired in a bank-specific `@Configuration`. The base product ships with a default empty list; the bank profile overrides it.

### 5.3 Domain Event Listeners (proposed above)

`List<RiskCreatedListener>` in `ComputeRiskScoreHandler`. The base product ships with no listeners. The bank wires a `JiraIssueCreatorListener` for risks above appetite. No base code changes.

### 5.4 The Workflow Engine (already built)

`WorkflowDefinition` JSON configs are the extension point for approval workflows. A bank that requires manager approval before org unit activation creates a workflow definition — no code change.

### 5.5 What Cannot Be Extended Without Code

Currently: entity structure (JPA annotations), GraphQL schema types, and repository query methods. These require code changes. **This is acceptable** — they represent the structural schema contract and should be changed as PR-reviewed code, not configuration.

---

## 6. Proposed Structure for Tier B (Compliance, Audit, Issues)

Following the recommendations above, Tier B modules should be built as:

```
platform-core/
  compliance/
    ComplianceFramework.java
    ComplianceRequirement.java
    ComplianceRequirementRepository.java
    ComplianceRequirementDto.java
    command/
      MapControlToRequirementCommand.java
      MapControlToRequirementHandler.java   ← single-purpose, testable
    query/
      ComplianceGapQueryHandler.java
    ComplianceService.java                   ← façade only

platform-api/
  config/
    ComplianceSliceConfig.java              ← wires handlers + validators
  graphql/
    ComplianceResolver.java
  repository/
    SpringComplianceRequirementRepository.java
    ComplianceRequirementRepositoryAdapter.java
```

Note: `Compliance*` entities are **never imported from the `risk/` package** — modules interact only through the `GrcRecord` / `RecordRelation` layer (the shared data model) and through service interfaces.

---

## 7. Test Impact of Recommendations

The proposed restructuring has **zero impact on test code** in the current phase:

- `OrgHierarchyServiceTest`, `RiskServiceTest`, `PolicyServiceTest`, `ControlServiceTest` all test through the service façade interface — they are unaffected by internal handler decomposition
- Moving entity classes to sub-packages requires updating import statements only
- Splitting `ServiceConfig` to `*SliceConfig` requires only import changes in `platform-api`

The new handler classes each get their own test class. Tests become narrower and more focused:  
`CreateOrgUnitHandlerTest` tests only unit creation, not the entire `OrgHierarchyService` lifecycle.

---

## 8. Summary Decision Table

| Question                                    | Decision                  | Rationale                                                                                |
| ------------------------------------------- | ------------------------- | ---------------------------------------------------------------------------------------- |
| Separate Gradle projects per GRC module?    | **No**                    | Contradicts ADR-001; vertical package slices achieve the same cohesion                   |
| Full CQRS?                                  | **No**                    | Unnecessary complexity; existing audit chain provides history                            |
| Command handlers for mutations?             | **Yes**                   | Single-responsibility; extension points without modification                             |
| Functional validators?                      | **Yes**                   | Testable, composable, bank-customizable without code changes                             |
| Domain event listeners?                     | **Yes (lightweight)**     | Decouples cross-module side-effects; uses existing outbox                                |
| Per-module `*SliceConfig.java`?             | **Yes**                   | Prevents `ServiceConfig` from becoming a maintenance bottleneck                          |
| Vertical package slices in `platform-core`? | **Yes**                   | Cohesion, discoverability, enforced-by-convention boundaries                             |
| Functional programming style?               | **Where it aids clarity** | Validator composition; stream pipelines for projections; avoid for stateful domain flows |

---

## 9. The Mapping Problem — DTOs, Commands, and Entity Structure

### 9.1 Current State Diagnosis

There are three distinct representations of the same data in the system:

| Layer             | Class                  | Fields (CreateOrgUnit example)                                       |
| ----------------- | ---------------------- | -------------------------------------------------------------------- |
| **GraphQL input** | `CreateOrgUnitInput`   | name, unitType, code, description, parentId, managerId, displayOrder |
| **Core command**  | `CreateOrgUnitCommand` | name, unitType, code, description, parentId, managerId, displayOrder |
| **Entity**        | `OrganizationUnit`     | + id, orgId, path, depth, active (system-assigned)                   |
| **Query DTO**     | `OrgUnitDto`           | all entity fields minus factory methods and JPA annotations          |

The command side (`Input` → `Command`) is **pure duplication** — identical classes, identical fields, resolver does nothing but copy:

```java
// OrgHierarchyResolver — mechanical copy with no transformation
var cmd = new CreateOrgUnitCommand(input.name(), input.unitType(), input.code(),
        input.description(), input.parentId(), input.managerId(), input.displayOrder());
```

The query side (`Entity` → `DTO`) has **legitimate purpose** — it strips JPA annotations, computed/internal fields (`orgId`, `path`), and creates a stable API contract decoupled from ORM detail — but the implementation is 100% mechanical field copy with no transformation logic.

**The field naming taxonomy is already consistent.** Every entity field, DTO field, command field, and GraphQL schema field uses the same camelCase name (`likelihoodScore`, `inherentRating`, `displayOrder`). The problem is not naming divergence; it is pure mechanical boilerplate that grows linearly with every new entity field.

By Phase 4 completion (all 13 remaining modules), the platform will have:

- ~25 entities with `toDto()` methods in their respective `*ServiceImpl`
- ~25 `Input` records that duplicate their `Command` counterparts
- ~500 lines of field-copy code that adds zero logic, only maintenance surface

---

### 9.2 Should Commands Follow Entity Structure? Yes — They Already Do

**Feasibility:** Yes. The naming strategy is already correct. Commands contain exactly the fields the user supplies; entities contain commands fields plus system-assigned fields (`id`, `orgId`, `createdAt`, `path`, etc.). This is the right split.

**Advisability:** Yes, enforce it as a convention:

> **Convention:** Every mutable field on an entity that originates from user input must have the same name in the corresponding command record. System-assigned fields (`id`, `orgId`, `createdAt`, `path`, `depth`, computed scores) must never appear in command records.

This convention means field names are the single taxonomy across all four layers. It is already true today — it must be _documented_ so it remains true as the team grows.

---

### 9.3 Lombok — Narrow, Targeted Use

Lombok is a compile-time annotation processor. It is **not a Spring Boot dependency** and is fully compatible with `platform-core`.

**What Lombok solves:**

| Use Case                               | Verdict                                                                                                                                                           |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@Getter` on JPA entities              | **Yes — worthwhile.** Eliminates 300+ getter methods across 25+ entities. Zero runtime cost.                                                                      |
| `@Setter` on JPA entities              | **Selective only.** Only on fields that are genuinely mutable after construction. Not blanket.                                                                    |
| `@Builder` on entities                 | **No.** Lombok `@Builder` with `@Entity` causes well-documented issues: no-arg constructor conflict, `equals`/`hashCode` on mutable state.                        |
| `@Value` / `@Builder` on DTOs/commands | **No.** DTOs and commands are Java records already. Records provide canonical constructor, `equals`, `hashCode`, `toString` natively. Adding Lombok is redundant. |
| `@Slf4j` on service classes            | **Yes.** Cosmetic but reduces boilerplate on every class that logs.                                                                                               |
| `@RequiredArgsConstructor` on services | **No.** The command handler + `*SliceConfig` pattern uses explicit `@Bean` methods — constructor injection is already visible.                                    |

**Recommendation:** Add Lombok to `platform-core` and `platform-api` `build.gradle` with scope restricted to `@Getter` on entities and `@Slf4j` on services. No `@Builder`, no `@Data`, no `@EqualsAndHashCode` — those are the Lombok footguns that cause JPA issues and are frequently over-used.

```groovy
// platform-core/build.gradle
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

---

### 9.4 MapStruct — Use It, but Not Everywhere

MapStruct generates type-safe mapping code at compile time. Because field names are already aligned, zero-configuration mappers work:

```java
@Mapper
public interface RiskMapper {
    RiskScoreDto toDto(RiskScore entity);
    // No @Mapping annotations needed — field names match
}
```

MapStruct generates a `RiskMapperImpl` at compile time. The `toDto()` method in `RiskServiceImpl` becomes `riskMapper.toDto(score)` — or better, is removed entirely and called directly at the resolver layer.

**What MapStruct solves:**

| Scenario                                             | Verdict                                                                                            |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `Entity → QueryDto` (same field names, same types)   | **Yes — this is the ideal MapStruct use case.** Zero config, type-safe, validated at compile time. |
| `Input → Command` (identical records)                | **Not needed.** The better solution is to eliminate the duplication (§9.5).                        |
| `Entity → partial projection DTO` (subset of fields) | **Yes.** MapStruct handles this with `@Mapping(target="...", ignore=true)`.                        |
| Entity with computed/transformed fields              | **Selective `@Mapping`.** Use only for the fields that differ; let MapStruct handle the rest.      |
| Complex domain translation (e.g., score computation) | **No.** Never use MapStruct for logic. Logic belongs in the domain service or entity.              |

**Cons to weigh:**

- Adds annotation processor to every module build
- Generated code is invisible without IDE plugin (IntelliJ MapStruct plugin resolves this)
- Stack traces through generated code can be confusing for new team members
- Compile-time errors from MapStruct are sometimes cryptic

**Recommendation:** Add MapStruct for the `Entity → QueryDto` direction only, in `platform-core`. Do not use it for the `Input → Command` direction (address that by restructuring — see §9.5). Do not use it for any mapping that involves logic.

```groovy
// platform-core/build.gradle
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
```

---

### 9.5 Eliminating the Input → Command Duplication

The `CreateOrgUnitInput` / `CreateOrgUnitCommand` duplication has two candidate solutions:

**Option A — Remove the Input layer, use the Command directly in the resolver**

```java
// OrgHierarchyResolver
@MutationMapping
public OrgUnitDto createOrgUnit(@Argument CreateOrgUnitCommand input) {
    return orgHierarchyService.createUnit(input);
}
```

Spring for GraphQL maps JSON fields to Java record components by name. Because the command record field names already match the GraphQL schema field names, this works with zero configuration. The `Input` record is deleted entirely.

**Pros:** Eliminates one class and all manual field copies. The command is the contract.  
**Cons:** `platform-api` now directly references `platform-core` command records for GraphQL argument binding. This is already the case (resolvers call services that take commands) — the import was always there, just via the Input intermediary.  
**Verdict:** **Adopt this pattern.** The boundary between `platform-api` and `platform-core` is by design: `platform-api` depends on `platform-core`. An `@Argument CreateOrgUnitCommand input` annotation does not blur that boundary — it removes an indirection layer that was never protecting anything.

**Option B — Keep the Input layer, use a single-line MapStruct mapper**

If the team wants a hard wall between GraphQL schema evolution and domain command evolution (e.g., if the GraphQL schema might diverge from the command in future), keep a thin mapper:

```java
@Mapper
public interface OrgUnitInputMapper {
    CreateOrgUnitCommand toCommand(CreateOrgUnitInput input);
}
```

The resolver becomes `orgHierarchyService.createUnit(mapper.toCommand(input))`. This is one line, type-safe, and maintains separation.

**Recommendation:** Use Option A as the default (simpler, no new class). If a module's GraphQL schema genuinely diverges from its command signature — for example, a `CreateControlInput` that must send different field names than `CreateControlCommand` for GraphQL schema readability — use Option B with a mapper interface for that specific module only.

---

### 9.6 The Simplest Structural Fix — `toDto()` on the Entity

Before adding any library, the minimal improvement with no new dependencies:

Move `toDto()` from `*ServiceImpl` onto the entity itself. The service then calls one method:

```java
// On RiskScore entity
public RiskScoreDto toDto() {
    return new RiskScoreDto(recordId, likelihoodScore, impactScore, inherentScore,
            inherentRating, residualScore, residualRating, appetiteAlignment, computedAt);
}

// In RiskServiceImpl — before
private RiskScoreDto toDto(RiskScore s) { return new RiskScoreDto(s.getRecordId(), ...); }

// In RiskServiceImpl — after
// method deleted; usage becomes: riskScore.toDto()
```

The entity owns its projection. When a field is added to the entity, the `toDto()` method is in the same file — the developer cannot miss it. The compile error is immediate.

This is not as zero-maintenance as MapStruct (new fields require manual update), but it has zero new dependencies, is immediately readable, and removes the service-level mapping boilerplate. It is the right starting point for a codebase that hasn't yet validated its MapStruct tolerance.

---

### 9.7 Recommended Rollout Sequence

| Phase                   | Action                                                      | Benefit                                                 |
| ----------------------- | ----------------------------------------------------------- | ------------------------------------------------------- |
| **Immediate (no deps)** | Move `toDto()` onto each entity                             | One fewer method in every ServiceImpl; co-location      |
| **Immediate (no deps)** | Delete `*Input` records; use command records as `@Argument` | Eliminates 25 duplicate classes                         |
| **Phase 4 Tier B**      | Add Lombok `@Getter` to all entities                        | Eliminates ~300 getter methods                          |
| **Phase 4 Tier B**      | Add MapStruct `Entity → DTO` mappers                        | Eliminates `toDto()` from entities; compile-time safety |
| **Phase 5**             | Spring Data projections for read-heavy queries              | Eliminates DTO instantiation entirely for query paths   |

---

## 10. Unified Taxonomy — One Mental Model Across All Layers

### 10.1 The Current State (It's Mostly Right)

The naming taxonomy is consistent today. However, it is implicit. To keep it consistent across 25 modules built by multiple developers, it needs to be **explicit and enforced**:

| Layer                          | Convention                                                                     | Status                |
| ------------------------------ | ------------------------------------------------------------------------------ | --------------------- |
| DB column names                | `snake_case` (`likelihood_score`, `display_order`)                             | ✅ Already consistent |
| Java entity fields             | `camelCase` (`likelihoodScore`, `displayOrder`) — Hibernate maps automatically | ✅ Already consistent |
| Java DTO/command record fields | Same camelCase as entity                                                       | ✅ Already consistent |
| GraphQL schema fields          | camelCase — Spring for GraphQL maps automatically                              | ✅ Already consistent |
| TypeScript generated types     | camelCase — `@graphql-codegen/cli` generates from schema                       | ✅ Already consistent |

**The taxonomy is already unified.** What it lacks is documentation and enforcement.

### 10.2 Enforcement Mechanisms

**1. A naming rule in the coding standards (`.github/instructions/java-coding-standards.instructions.md`):**

> Every mutable field that originates in user input must have the same name across all four layers: DB column (snake_case), Java entity field (camelCase), command record field (camelCase), GraphQL schema field (camelCase). Deviations require an ADR justification.

**2. GraphQL codegen as the single source of truth for TypeScript:**
The frontend `codegen.yml` already generates TypeScript types from the GraphQL schema. As long as the GraphQL schema field names match the Java DTO field names (which they do automatically through Spring for GraphQL's default mapping), the TypeScript types are always in sync. No manual TypeScript interface maintenance.

**3. Liquibase as the single source of truth for DB schema:**
Column names in Liquibase changesets → Hibernate column mapping override only when name diverges (it should not) → entity field name follows automatically. If a column is `likelihood_score`, the entity field is `likelihoodScore` — no `@Column(name=...)` override needed (Hibernate default naming strategy handles this).

**4. A DTO projection test pattern:**
For each module, a single test that verifies no field is silently dropped between entity and DTO:

```java
@Test
void riskScoreDto_containsAllExpectedFields() {
    var score = RiskScore.compute(...);
    var dto = score.toDto();
    assertThat(dto.likelihoodScore()).isEqualTo(score.getLikelihoodScore());
    assertThat(dto.impactScore()).isEqualTo(score.getImpactScore());
    // ... one assertion per field — fails immediately when entity gains a field the DTO doesn't
}
```

This is more valuable than MapStruct for catching taxonomy drift: it is a living contract test.

### 10.3 Where the Taxonomy Genuinely Diverges — And That Is Correct

Some divergence is intentional and correct:

| Entity field                | DTO exclusion              | Reason                                                                                       |
| --------------------------- | -------------------------- | -------------------------------------------------------------------------------------------- |
| `orgId`                     | Excluded from most DTOs    | Implicit in session context; leaking it is unnecessary and potentially a data boundary issue |
| `path` (org hierarchy)      | Excluded from `OrgUnitDto` | Implementation detail of the materialized path pattern                                       |
| `depth`                     | Excluded from some DTOs    | Redundant if hierarchy is represented structurally                                           |
| `computedAt` (risk/control) | Included in DTOs           | Necessary for cache invalidation reasoning on the frontend                                   |
| `inherentScore` (risk)      | Included in DTO            | Useful for display; not an internal detail                                                   |

These are **deliberate divergences** — not drift. Document them in the module spec (`docs/modules/16-risk-management.md`) under a "Projection Contract" subsection.

---

## 11. Ten Platform-Wide Suggestions for Minimal, Extensible Code

These are ordered from highest leverage to lowest, given the platform's current state.

### 11.1 Spring Data Projections (Highest Impact on Query Side)

For every query that returns a DTO that is a strict subset of entity fields, use a Spring Data projection interface instead of a concrete DTO class:

```java
// No DTO class needed — Spring Data generates the proxy
public interface RiskScoreSummary {
    BigDecimal getLikelihoodScore();
    BigDecimal getInherentScore();
    String getInherentRating();
}

// Repository
Optional<RiskScoreSummary> findSummaryByRecordId(UUID recordId);
```

Spring Data generates an interface proxy at runtime. No `toDto()`, no record class, no mapping code. The projection IS the contract. Adding a new field means editing one interface. This eliminates an entire class of DTOs for read-only data.

**Caveat:** Does not work for computed fields (use `@Value("#{target.likelihoodScore.multiply(target.impactScore)}")`) or for DTOs that cross entity boundaries. For those, use entity `toDto()` or MapStruct.

### 11.2 `@Embeddable` for Repeated Field Groups

Several field groups repeat across entities (scoring fields, address fields, audit metadata). Extract them as `@Embeddable` classes:

```java
@Embeddable
public record RiskScoreFields(
    BigDecimal likelihoodScore,
    BigDecimal impactScore,
    BigDecimal inherentScore,
    String inherentRating
) {}
```

`@Embeddable` records cannot yet be used with JPA (no-arg constructor requirement — records don't have one). Use an `@Embeddable` class with `@Getter` instead. The benefit: 8 scoring fields become 1 field in the entity, and the `RiskScoreFields` embeddable has its own `toDto()`.

### 11.3 Functional Validator Pipeline (Already Recommended — Formalize It)

As described in §5, make `Validator<C>` a functional interface and store module validators in `List<Validator<C>>`. The addition:

```java
// Compose all validators for a command type at wiring time
List<Validator<CreateRiskCommand>> validators = List.of(
    CreateRiskCommand::validateScoreRange,    // static method reference
    notNullValidator("name", CreateRiskCommand::name),
    orgBoundaryValidator()
);
```

This makes a module's full validation surface visible in one place — the `*SliceConfig` — without reading every validator class. Bank customization is adding a validator to the list; core platform changes are never required.

### 11.4 Domain Events Over Direct Cross-Module Calls

When module A needs to react to module B's state change, never call module B's service from module A. Use a lightweight domain event through the outbox:

```java
// RiskServiceImpl — after residual score update
outboxPublisher.publish(new RiskScoreUpdatedEvent(recordId, dto.residualRating()));

// ControlServiceImpl — subscribes via @TransactionalEventListener
@TransactionalEventListener
void onRiskScoreUpdated(RiskScoreUpdatedEvent event) {
    // Suggest re-evaluation of linked controls
}
```

This enforces the module boundary (`platform-*` modules never importing siblings) and makes cross-domain coupling explicit, observable, and retryable.

### 11.5 `@ConfigurationProperties` for Module-Level Tuning Parameters

Every GRC module has numeric thresholds and configuration (risk appetite thresholds, escalation timeouts, notification intervals). Move these out of `@Value("${...}")` scattered in services into typed `@ConfigurationProperties` records per module:

```java
@ConfigurationProperties("grc.risk")
public record RiskProperties(
    BigDecimal appetiteThreshold,
    BigDecimal criticalThreshold,
    int residualComputationTimeoutSeconds
) {}
```

One file per module in `src/main/resources/application.yml`. Bank-level customization is editing YAML, not code. IDE auto-completion via the Spring configuration processor. Testing uses constructor injection, not `@TestPropertySource`.

### 11.6 Record-Based Command Return Types (Richer Responses)

Current commands often return a DTO directly. For operations that need to communicate multiple outcomes (e.g., workflow transition + notification triggered + audit ID), return a typed result record:

```java
public record CreateControlResult(
    ControlEffectivenessDto control,
    boolean workflowTriggered,
    UUID auditEventId
) {}
```

The service returns this; the resolver projects only what GraphQL needs. This avoids multiple service calls from the resolver and prevents the "add one more field to the DTO" pressure from GraphQL requirements.

### 11.7 Explicit `@Transactional(readOnly = true)` on All Query Methods

Every service method that is a query (no mutation) should declare `@Transactional(readOnly = true)`. This:

- Enables Hibernate to skip dirty checking on the session (measurable performance gain at scale)
- Documents intent explicitly — a reviewer sees immediately if a read-only method accidentally modifies state
- Prevents Hibernate second-level cache issues with stale reads

Currently absent from `QueryServiceImpl` and `OrgHierarchyServiceImpl` query methods.

### 11.8 Pagination Contract — Enforce It Now Before It's Too Late

Every list query in every resolver currently returns `List<T>`. At scale, this will fail. Enforce a `PagedResult<T>` return type for all collection endpoints now:

```java
public record PagedResult<T>(List<T> items, long totalCount, int page, int pageSize) {}
```

Adding pagination later requires a GraphQL schema change (breaking), a service interface change, and likely a migration for sorted queries. Adding it now costs one type declaration and a change to 10 resolver methods. The migration cost ratio is 1:100.

### 11.9 `@NotNull` / Bean Validation at System Boundaries (Controller/Resolver Layer Only)

The copilot instructions correctly say: validate at system boundaries, never in service internals. Formalise this:

- Every `@MutationMapping` parameter annotated with `@Valid`
- Every command record field annotated with `@NotNull`, `@Size`, `@DecimalMin` as needed
- Spring for GraphQL enforces these before the resolver method is called

This moves validation out of service `if (field == null) throw` blocks into declarative annotations on the command records themselves. The `Validator<C>` functional pipeline handles _business rule_ validation; Bean Validation handles _structural_ validation (null checks, size limits, format).

### 11.10 GraphQL Schema-First With a Codegen Feedback Loop

The final suggestion is the most impactful at the platform-wide level and the most investment-heavy:

Move to a fully **schema-first** workflow:

1. Define GraphQL schema (`.graphqls`) — this is the contract
2. `@graphql-codegen/cli` generates TypeScript types (already done)
3. Add `graphql-java-generator` or `DGS` codegen to generate Java interfaces from the same schema
4. Java services implement the generated interfaces — the schema IS the service contract

Benefits: GraphQL schema drift from implementation becomes a compile error on the Java side. New fields added to the schema must be implemented. Breaking changes are caught in CI. The schema truly becomes the single source of truth across all three layers.

**Trade-off:** Requires DGS (Netflix) or a custom Gradle codegen task. The current Spring for GraphQL with hand-written schema is simpler but relies on developer discipline to keep schema and code in sync. Defer this to Phase 5 if Phase 4 budget is constrained.

---

## 12. Summary Decision Table (Mapping and Taxonomy)

| Question                                                             | Decision                                                             | Rationale                                                             |
| -------------------------------------------------------------------- | -------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Should commands follow entity field names?                           | **Yes — already do; enforce by convention**                          | Naming taxonomy is consistent today; document it                      |
| Keep `*Input` GraphQL records separate from `*Command` core records? | **No — collapse them; use `@Argument *Command input`**               | Zero logic difference; `platform-api` already imports `platform-core` |
| Add Lombok?                                                          | **Yes — `@Getter` on entities and `@Slf4j` only**                    | Eliminates ~300 getters; no footguns                                  |
| Add MapStruct?                                                       | **Yes — `Entity → DTO` direction only**                              | Compile-time safe; zero-config for aligned fields                     |
| Where does `toDto()` live?                                           | **On the entity (immediate), replaced by MapStruct mapper (Tier B)** | Co-location first; then eliminate mechanical code in bulk             |
| Unified taxonomy across UI/API/DB?                                   | **Already unified; make it explicit in coding standards**            | GraphQL codegen → TypeScript handles frontend automatically           |
| Spring Data projections?                                             | **Yes, for read-only query paths**                                   | Eliminates DTO class + mapping for the majority of queries            |
| Pagination?                                                          | **Add `PagedResult<T>` now for all list endpoints**                  | Breaking change to defer is far more costly                           |
| Bean Validation on commands?                                         | **Yes — structural validation only at system boundary**              | Separates structural from business validation cleanly                 |
