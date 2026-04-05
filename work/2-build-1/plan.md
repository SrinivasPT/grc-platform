# GRC Platform — Build Plan

> **Version:** 1.0
> **Date:** April 5, 2026
> **Based on:** `docs/` design documentation + `work/1-design/revised-architecture.md`
> **Tech Stack:** React 18 · Java 21 · Spring Boot 3.5.x · SQL Server 2025 · Neo4j 5.x LTS · Keycloak · Apigee

---

## 0. Guiding Principles

These principles govern every decision in this build plan. Every coding agent prompt, every instruction file, and every code review must be measured against them.

| Principle | Mandate |
|-----------|---------|
| **TDD First** | Write test before implementation. No feature code without a failing test. |
| **Agent-Optimized Code** | Small, pure functions. Low cognitive complexity. No magic. Agents must be able to read, understand, and extend any class without reading more than that class. |
| **Living Docs** | Every ADR and design document is updated in the same PR as the code change it documents. Design ↔ Code are never out of sync. |
| **Security by Design** | Checkmarx-clean by construction. OWASP Top 10 addressed structurally, not patched after the fact. |
| **Zero Regressions** | All tests are regression tests. CI blocks any merge that breaks existing tests. |
| **Boring Technology** | Proven libraries only. No experimental dependencies in production path. |

---

## 1. Repository & Agent Infrastructure Setup

> **Goal:** Before writing a single line of GRC code, establish the developer experience, AI agent guidance, and quality gates that will govern the entire build.

### 1.1 GitHub Copilot Instruction Files

The instruction system is the backbone of consistent, high-quality AI-generated code. It must be in place before any code is generated.

**Files to create:**

```
.github/
└── copilot-instructions.md          ← Global: TDD mandate, OWASP rules, commit style, ADR policy

frontend/
└── .github/
    └── copilot-instructions.md      ← React patterns, Apollo, Zustand, form engine conventions

backend/
├── .github/
│   └── copilot-instructions.md      ← Java 21 patterns, Spring Boot conventions
├── platform-core/
│   └── .github/
│       └── copilot-instructions.md  ← Rule engine, audit, entity patterns
├── platform-api/
│   └── .github/
│       └── copilot-instructions.md  ← GraphQL resolvers, REST controllers, BatchMapping mandate
├── platform-workflow/
│   └── .github/
│       └── copilot-instructions.md  ← Workflow state machine, outbox patterns
└── db/
    └── .github/
        └── copilot-instructions.md  ← Liquibase conventions, naming, context rules

infrastructure/
└── .github/
    └── copilot-instructions.md      ← Docker Compose, Kubernetes, Tekton patterns
```

**Global instruction mandates (`.github/copilot-instructions.md`):**

- TDD: Always write the test method signature + assertion first, then the implementation
- Never use `ThreadLocal` — use Java 21 `ScopedValue` for context propagation
- Never catch `Exception` generically — catch specific exception types only
- Never hardcode secrets — all secrets via environment variable or vault reference
- All GraphQL collection resolvers must use `@BatchMapping` (never `@SchemaMapping` on collections)
- Rule engine JSON is parsed only via the strict `RuleDslParser` — never via raw `ObjectMapper`
- Every mutation service method must write to `audit_log` within the same transaction
- Liquibase migrations: file naming `V{YYYYMMDD}_{NNN}_{description}.xml`, always include rollback
- ADR must be created/updated for any change that affects architecture or module interfaces
- Sonar: cognitive complexity limit 10 per method, no TODO comments in committed code
- Test coverage target: 90% branch coverage on service layer

### 1.2 ADR (Architecture Decision Record) Directory

```
docs/
└── adr/
    ├── README.md                  ← ADR index and template
    ├── ADR-001-modular-monolith.md
    ├── ADR-002-sql-server-source-of-truth.md
    ├── ADR-003-neo4j-derived-graph.md
    ├── ADR-004-keycloak-identity-broker.md
    ├── ADR-005-event-outbox-pattern.md
    ├── ADR-006-rule-engine-dsl.md
    ├── ADR-007-liquibase-dual-context.md
    ├── ADR-008-scopedvalue-context-propagation.md
    └── ADR-009-graphql-batchmapping.md
```

Every ADR follows the template: **Status / Context / Decision / Consequences / Alternatives Considered**.

### 1.3 Design ↔ Code Sync Procedure

1. **Before starting a feature:** The relevant `docs/modules/NN-*.md` is the spec. Read it. If any section is incomplete or incorrect, update it as the first commit.
2. **During implementation:** If the implementation diverges from the spec (intentionally or discovered), update the spec in the same PR.
3. **After implementation:** Run `docs/scripts/sync-check.sh` (to be created in Phase 1) — a simple script that verifies every module doc references at least one test file and one source file by convention.
4. **PR gate:** PRs may not be merged if the commit message references a module but the corresponding `docs/modules/` file has not been touched since the prior release.

---

## 2. Technology Stack Versions (Locked)

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 LTS | Virtual threads, ScopedValue, sealed classes |
| Spring Boot | 3.5.x | Auto-configures Spring for GraphQL 1.3.x |
| Spring for GraphQL | 1.3.x | BatchMapping, AnnotatedControllerConfigurer |
| Hibernate | 6.x | Bundled with Spring Boot 3.5 |
| Liquibase | 4.x | Dual-context: `main` and `test` |
| Gradle | 8.x | Multi-module, compile-time boundary enforcement |
| React | 18.x | Concurrent rendering, Suspense |
| TypeScript | 5.x | Strict mode, no `any` |
| Apollo Client | 3.x | InMemoryCache, reactive variables |
| Vite | 5.x | Build tool |
| shadcn/ui | Latest | Tailwind-based component primitives |
| SQL Server | 2025 | Change Tracking, FTS, vector columns |
| Neo4j | 5.x LTS | APOC library enabled |
| Keycloak | 24.x | SAML broker, SCIM inbound |
| Redis | 7.x | Session cache, idempotency key store |
| Testcontainers | 1.19.x | SQL Server + Neo4j containers for tests |

---

## 3. Build Phases

The platform is built in five phases. Each phase produces working, tested, deployable software. Later phases depend on earlier ones but never require rework of fundamentals.

```
Phase 0 → Foundation & Infrastructure         (Weeks 1–2)
Phase 1 → Core Platform Engine                (Weeks 3–6)
Phase 2 → Identity, Auth & API Layer          (Weeks 7–9)
Phase 3 → Workflow, Notification & Graph      (Weeks 10–13)
Phase 4 → GRC Domain Modules                  (Weeks 14–26)
Phase 5 → Reporting, Integration & Hardening  (Weeks 27–32)
```

---

## Phase 0 — Foundation & Infrastructure

> **Output:** Local dev environment running, project skeletons compiling with tests green, CI pipeline wired.

### P0.1 — Infrastructure: Docker Compose (Local Dev)

**File:** `infrastructure/docker-compose.yml`

Services:
- `sqlserver` — SQL Server 2025 (port 1433), volume-mounted for persistence
- `neo4j` — Neo4j 5.x LTS (ports 7474 HTTP, 7687 Bolt), APOC plugin enabled
- `redis` — Redis 7.x (port 6379)
- `keycloak` — Keycloak 24.x (port 8080), realm import from `infrastructure/keycloak/realm-export.json`

**Developer commands:**
```bash
# Start all services
docker-compose up -d

# Reset DB only (preserves Neo4j)
docker-compose rm -sf sqlserver && docker-compose up -d sqlserver
```

**Password management:**
All credentials in `infrastructure/.env` (gitignored). Template committed as `infrastructure/.env.example`. CI reads from vault (HashiCorp Vault or Azure Key Vault — configured per environment).

### P0.2 — Gradle Multi-Module Skeleton

```
backend/
├── settings.gradle          ← declares all subprojects
├── build.gradle             ← shared dependency management (BOM, plugins)
├── platform-api/            ← app entry point (Spring Boot main class)
├── platform-core/           ← domain: entities, services, rule engine, audit
├── platform-workflow/       ← workflow engine (depends on platform-core)
├── platform-notification/   ← notification engine (depends on platform-core)
├── platform-graph/          ← Neo4j projection worker (depends on platform-core)
├── platform-search/         ← search service (depends on platform-core)
├── platform-reporting/      ← reporting engine (depends on platform-core)
├── platform-integration/    ← integration framework (depends on platform-core)
└── db/
    └── migrations/          ← Liquibase changelogs
```

**Module dependency rules (enforced at compile time):**
- `platform-api` depends on all modules (top of graph)
- All modules depend only on `platform-core` — never on each other
- `platform-core` has zero Spring Boot dependencies (testable without container)

### P0.3 — React Frontend Skeleton

```
frontend/
├── package.json             ← React 18, Apollo Client, Zustand, shadcn/ui, Vite
├── tsconfig.json            ← strict: true, no implicit any
├── vite.config.ts
├── src/
│   ├── app/                 ← Root providers, router, shell layout
│   ├── components/
│   │   ├── form-engine/     ← FieldRenderer, LayoutRenderer (placeholder)
│   │   ├── workflow/        ← WorkflowStatusBadge, TransitionButton (placeholder)
│   │   └── shared/          ← Button, Table, Modal wired to shadcn/ui
│   ├── modules/             ← One folder per GRC domain (empty, ready for Phase 4)
│   ├── graphql/             ← .graphql operation files + codegen config
│   └── lib/                 ← Apollo client setup, auth context
```

**GraphQL Codegen:** `@graphql-codegen/cli` configured to generate typed hooks from `.graphql` files. Run as a pre-build step.

### P0.4 — Liquibase Foundation Migration

**File:** `db/migrations/changelog-master.xml`

Initial migration (context: `main`):
- Create `organizations`, `org_settings` tables
- Create `users`, `roles`, `role_permissions`, `user_roles` tables
- Enable SQL Server Change Tracking on database

Initial migration (context: `test`):
- Same schema as `main` (inherits via include)
- Seed one org (`org_id = WELL_KNOWN_TEST_ORG_UUID`) and three users (admin, analyst, viewer)

**Liquibase naming convention:** `V{YYYYMMDD}_{NNN}_{description}.xml`

### P0.5 — CI Pipeline (Tekton)

```
infrastructure/
└── tekton/
    ├── pipeline-build.yaml           ← build + compile
    ├── pipeline-test.yaml            ← unit + integration tests
    ├── pipeline-quality.yaml         ← SonarQube gate + Checkmarx SAST
    ├── pipeline-package.yaml         ← Docker image build + push
    ├── task-liquibase-migrate.yaml
    ├── task-gradle-build.yaml
    └── task-sonar-scan.yaml
```

**Quality gates (all blocking):**
- SonarQube: 0 Critical/Blocker issues, coverage ≥ 90% on service layer
- Checkmarx: 0 High/Critical findings
- All tests green
- No TODO/FIXME in committed code

---

## Phase 1 — Core Platform Engine

> **Output:** The record storage engine, field value system, rule engine, and audit log are fully built and tested. This is the foundation everything else sits on.

### P1.1 — Data Model: Full Schema Migration

Implement all tables from `docs/modules/02-data-model-schema.md`:

**Batch 1 — Application Config (meta-layer):**
- `applications`, `field_definitions`, `layout_definitions`, `value_lists`, `value_list_items`

**Batch 2 — Record Data (runtime):**
- `records`, `field_values_text`, `field_values_number`, `field_values_date`, `field_values_reference`, `record_relations`, `record_attachments`

**Batch 3 — Workflow:**
- `workflow_definitions`, `workflow_instances`, `workflow_history`, `workflow_tasks`

**Batch 4 — Audit & Versioning:**
- `audit_log`, `record_versions`

**Batch 5 — Identity & Access (supplement P0.4):**
- `permission_policies`, `record_access_rules`, `materialized_record_access`

**Batch 6 — Infrastructure tables:**
- `event_outbox`, `event_outbox_dlq`, `graph_sync_state`, `idempotency_keys`

Each Liquibase file includes:
1. `<changeSet>` with `context="main"` for the production create
2. `<changeSet>` with `context="test"` only for test seed data
3. `<rollback>` block for every create

### P1.2 — Domain Entities (JPA)

**Package:** `com.grc.core.domain`

For each major table: JPA `@Entity` class with:
- `@Column` on every field (explicit name mapping — no convention magic)
- Auditing fields via `@EntityListeners(AuditingEntityListener.class)`
- `org_id` non-nullable on all entities
- No bidirectional lazy relationships (fetch explicitly in service layer)

**Key entities:** `Organization`, `Application`, `FieldDefinition`, `LayoutDefinition`, `Record`, `FieldValueText`, `FieldValueNumber`, `FieldValueDate`, `FieldValueReference`, `RecordRelation`, `AuditLogEntry`, `EventOutbox`

**Testing:** Each entity has a persistence slice test (`@DataJpaTest`) verifying:
- Insert and reload round-trip
- Constraint violations (nulls, unique violations) throw expected exceptions
- `org_id` filter correctly excludes data from other orgs

### P1.3 — Record Service

**Package:** `com.grc.core.service`
**Class:** `RecordService`

Operations:
- `createRecord(CreateRecordCommand cmd) → RecordDto`
- `updateRecord(UpdateRecordCommand cmd) → RecordDto`
- `getRecord(UUID recordId) → RecordDto`
- `listRecords(RecordListQuery query) → Page<RecordSummaryDto>`
- `softDeleteRecord(UUID recordId)`

**Design rules enforced:**
- All field values written in a single batched `INSERT` (no N+1 writes)
- `org_id` injected from `ScopedValue<OrgContext>` — never from method parameter
- Rule engine (compute context) invoked after field value assembly, before persist
- Audit log entry written in same transaction via `AuditService.record()`

**TDD tests (written first):**
```
RecordServiceTest:
  createRecord_setsOrgIdFromContext
  createRecord_runsComputeRules_andPersistsComputedValues
  createRecord_writesAuditEntry_inSameTransaction
  updateRecord_bumpsVersionAndWritesAuditEntry
  softDelete_setsDeletedAtAndWritesAuditEntry
  getRecord_throwsNotFound_whenOrgIdDoesNotMatch
```

### P1.4 — Rule Engine (Three Contexts)

**Package:** `com.grc.core.rules`

This is the most complex component. It must be built with strict security and testability.

**AST model (sealed classes — Java 21):**
```java
public sealed interface RuleNode permits
    ConditionNode, LogicalNode, ComparisonNode,
    ArithmeticNode, LookupNode, AggregateNode {}
```

**Strict DSL parser (`RuleDslParser`):**
- Parses JSON DSL using only typed AST model classes
- Jackson configured with `MapperFeature.DEFAULT_VIEW_INCLUSION = false`
- Polymorphic typing completely disabled — no `@JsonTypeInfo`
- Input validated against schema allowlist before deserialization
- Max nesting depth: 4 levels (throws `RuleDepthExceededException`)
- Max rules per save: 50 (throws `RuleCountExceededException`)

**Three execution contexts (`RuleExecutor`):**
| Context class | `ComputeContext` | `ValidateContext` | `TriggerContext` |
|---|---|---|---|
| Timeout | 2 seconds | 2 seconds | 500ms |
| DB access | Read-only lookups | Existence checks | None |
| Side effects | None | None | Writes to `event_outbox` |

**TDD tests (written first):**
```
RuleDslParserTest:
  parse_rejectsPolymorphicTypeInfo
  parse_throwsOnDepthExceeding4
  parse_throwsOnRuleCountExceeding50

ComputeContextTest:
  evaluate_computesFieldValue_fromOtherFields
  evaluate_respectsTimeout_andThrowsOnExceed

ValidateContextTest:
  evaluate_returnsFalse_onUniqueConstraintViolation  
  evaluate_handlesWorkflowCondition

TriggerContextTest:
  evaluate_writesToEventOutbox_notDirectToNotification
```

### P1.5 — Audit Service

**Package:** `com.grc.core.audit`
**Class:** `AuditService`

**SHA-256 hash chain design (deadlock-free):**
- One SEQUENCE per org (`audit_seq_{org_id}`) — monotonically increasing, no gaps
- Hash computation: `SHA256(prev_hash + org_id + record_id + operation + payload_json + sequence_number)`
- Hash computed synchronously within the transaction
- Per-org serialization via optimistic locking on `audit_chain_head` table (one row per org)
- On conflict (concurrent audit writes for same org), the transaction retries up to 3 times with exponential backoff
- This avoids table-level locks while preserving the chain

```sql
-- audit_chain_head (one row per org — the lock point)
CREATE TABLE audit_chain_head (
    org_id        UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    last_sequence BIGINT           NOT NULL DEFAULT 0,
    last_hash     NCHAR(64)        NOT NULL,
    version       INT              NOT NULL DEFAULT 0  -- optimistic lock
);
```

**TDD tests:**
```
AuditServiceTest:
  record_writesHashedEntry_inSameTransaction
  record_chainsHash_toPreviousEntry
  record_retriesOnOptimisticLockConflict
  verifyChain_detectsTampering_onModifiedEntry
```

### P1.6 — Context Propagation

**Package:** `com.grc.core.context`

**`OrgContext` design (Java 21 ScopedValue):**
```java
public final class OrgContext {
    public static final ScopedValue<OrgContext> CURRENT = ScopedValue.newInstance();

    private final UUID orgId;
    private final UUID userId;
    private final Set<String> roles;
    private final int roleVersion;
    // ...
}
```

**SQL Server SESSION_CONTEXT on every connection:**

`HikariCP` connection listener sets `SESSION_CONTEXT(N'org_id', ...)` on every connection checkout:
```java
// Called by HikariCP's connectionInitSql equivalent via custom DataSource proxy
connection.prepareStatement(
    "EXEC sp_set_session_context N'org_id', ?, @read_only = 1"
).execute(orgContext.getOrgId().toString());
```

**Background thread propagation:**
- All `@Async` methods and background workers receive `OrgContext` via explicit method parameter
- `WorkflowEngine` and `EventOutboxWorker` store `org_id` in their task rows and restore context at execution time
- No `ThreadLocal` is used anywhere — `ScopedValue.where(...)` wraps all virtual thread executions

**TDD tests:**
```
OrgContextPropagationTest:
  scopedValue_isVisibleInSameThread
  scopedValue_isInheritedByVirtualChildThread
  sessionContext_isSetOnConnectionCheckout
  backgroundWorker_restoresContextFromTaskRow
```

---

## Phase 2 — Identity, Auth & API Layer

> **Output:** Secure JWT validation, RBAC enforcement, GraphQL API, and REST endpoints are fully functional and tested.

### P2.1 — Keycloak Realm Configuration

**File:** `infrastructure/keycloak/realm-export.json`

Configuration:
- Realm: `grc`
- SAML 2.0 identity provider pointing to Ping Identity (template with env var substitution for SAML metadata URL)
- Client: `grc-api` (confidential, JWT bearer only)
- Client: `grc-spa` (public, PKCE only — for React SPA)
- Role mapper: Ping group → Keycloak role → JWT `roles` claim
- Token lifespan: 5 minutes access token, 8 hours refresh token
- `role_version` custom claim: incremented by Keycloak admin event hook when role assignments change

### P2.2 — Spring Security Configuration

**Package:** `com.grc.api.security`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // OAuth2 Resource Server — validates Keycloak JWTs only
    // Never issues tokens
    // Extracts org_id, user_id, roles, role_version from JWT claims
    // Sets OrgContext.CURRENT via ScopedValue for request thread
    // Sets SESSION_CONTEXT on DB connection after OrgContext is set
}
```

**JWT freshness filter:**
- `role_version` in JWT compared to cached `user_role_versions` map (Redis, TTL 5 min)
- If cached version > JWT version → 401 with `role_version_stale` error code
- Forces re-authentication; new token carries current `role_version`

**Permission evaluation (materialized access table):**
- `record_access_rules` evaluated against `materialized_record_access` table (O(1) join)
- Table rebuilt asynchronously when permissions or record changes occur
- Checked in `RecordService` before any field-level operation

### P2.3 — GraphQL Schema & Resolvers

**File:** `platform-api/src/main/resources/graphql/schema.graphqls`

Schema organized into sections:
1. Scalar types (`UUID`, `DateTime`, `JSON`)
2. Core types (`Record`, `FieldValue`, `Application`, `FieldDefinition`)
3. Workflow types (`WorkflowInstance`, `WorkflowTask`, `WorkflowHistory`)
4. GRC domain types (one section per module — added phase by phase)

**BatchMapping mandate (enforced by instruction file):**
```java
// CORRECT — one SQL query for all records' field values
@BatchMapping
public Map<Record, List<FieldValue>> fieldValues(List<Record> records) {
    return fieldValueRepository.findAllByRecordIds(
        records.stream().map(Record::getId).toList()
    );  // single IN(...) query
}

// WRONG — N+1 — never do this
@SchemaMapping
public List<FieldValue> fieldValues(Record record) {
    return fieldValueRepository.findByRecordId(record.getId()); // N queries!
}
```

**Depth and cost limits:**
- Max query depth: 8 levels
- Max query cost: 200 (calculated by field weight)
- Introspection disabled in production

**TDD tests:**
```
RecordGraphQlTest:
  query_record_withFieldValues_executes_exactly_2_sql_queries
  query_records_with5LevelDepth_executes_exactly_5_sql_queries
  mutation_createRecord_returnsCreatedRecord
  mutation_createRecord_withInvalidOrgId_returns403
```

### P2.4 — Idempotency Filter

**Package:** `com.grc.api.filter`

All state-changing API calls (GraphQL mutations + REST POST/PUT/DELETE):
1. Client sends `Idempotency-Key: <uuid>` header
2. Filter checks `idempotency_keys` table (or Redis) for existing response
3. If found: return cached response immediately (no re-execution)
4. If not found: execute, store response with 24h TTL

```sql
CREATE TABLE idempotency_keys (
    key_hash      NCHAR(64)        NOT NULL PRIMARY KEY,  -- SHA256 of org_id+key
    org_id        UNIQUEIDENTIFIER NOT NULL,
    response_body NVARCHAR(MAX)    NOT NULL,
    status_code   INT              NOT NULL,
    created_at    DATETIME2        NOT NULL DEFAULT SYSUTCDATETIME(),
    expires_at    DATETIME2        NOT NULL
);
```

---

## Phase 3 — Workflow, Notification & Graph Projection

> **Output:** Configurable workflow engine, notification delivery, and Neo4j projection are built and tested end-to-end.

### P3.1 — Workflow Engine

**Package:** `com.grc.workflow`

**State machine design:**
- `WorkflowDefinition` = directed graph of states and transitions (stored as JSON in `workflow_definitions.config`)
- `WorkflowInstance` = runtime instance with `current_state`, `version` (optimistic lock), `org_id`
- Transitions validated against `WorkflowDefinition` by `WorkflowEngine`

**Race condition prevention:**
```sql
-- Optimistic lock prevents two concurrent completions
UPDATE workflow_instances
SET current_state = @nextState, version = version + 1
WHERE id = @id AND version = @expectedVersion
-- If 0 rows affected → throw OptimisticLockException → retry or 409
```

**Delegation and escalation:**
- `workflow_tasks.assigned_to` = UUID of user or group
- `workflow_tasks.escalation_days` = number of days before escalation
- Nightly scheduled job: `EscalationJob` queries overdue tasks, re-assigns to manager (from org hierarchy), writes to `event_outbox` for notification

**All workflow side effects via outbox (not direct calls):**
```java
// CORRECT
eventOutboxService.publish(new WorkflowTransitionedEvent(instanceId, fromState, toState));

// WRONG — direct call in transaction
notificationService.sendEmail(assignee, "Your task is ready");
```

**TDD tests:**
```
WorkflowEngineTest:
  transition_movesInstance_toNextState
  transition_blocks_whenConditionRuleFails
  transition_throwsOptimisticLock_onConcurrentCompletion
  escalation_reassignsTasks_afterDeadlineExpires
  allSideEffects_goThroughOutbox_notDirectCalls
```

### P3.2 — Event Outbox Worker

**Package:** `com.grc.notification`

**Worker design:**
- Polls `event_outbox` every 2 seconds (configurable)
- Processes each event at-least-once (idempotent consumer handles duplicates)
- Routes to consumer: `NotificationDeliveryService`, `IntegrationDispatchService`, `SlackConnector`, `JiraConnector`
- On max retries: moves to `event_outbox_dlq`

**Notification consumers:**
- Email via SMTP (template engine: Thymeleaf)
- In-app notification stored in `notifications` table (polled by frontend via GraphQL subscription)
- Webhook delivery with HMAC signature on payload

### P3.3 — Neo4j Projection Worker

**Package:** `com.grc.graph`

**CDC-based sync design:**
```java
// GraphProjectionWorker (virtual thread pool, runs every 2s)
1. Read last processed CT version from graph_sync_state
2. Query SQL Server Change Tracking for changed record_relations rows
3. For each change: execute Cypher MERGE/DELETE (idempotent)
4. Update graph_sync_state.last_ct_version
5. On worker restart: re-reads last_ct_version → no duplicates, no missed changes
```

**Neo4j schema (node labels + relationship types):**
- Nodes: `(:Record {id, orgId, appKey, displayName, active, graphSchemaVersion})`
- Relationships: `[:RELATED_TO {relationTypeKey, active, createdAt}]`
- Domain-specific relationship types added in Phase 4 (e.g., `[:MITIGATED_BY]`, `[:OWNED_BY]`)

**graphSyncPending flag in record API:**
- `records.graph_updated_at` column stores when the record was last graph-synced
- API response includes `graphSyncPending: records.updated_at > graph_updated_at`
- React uses this flag to show "syncing..." indicator on graph views

**TDD tests:**
```
GraphProjectionWorkerTest:
  worker_syncsNewRelation_toNeo4j
  worker_isIdempotent_onReplay
  worker_resumesFromLastVersion_onRestart
  worker_marksRelationInactive_onSoftDelete
```

---

## Phase 4 — GRC Domain Modules

> **Output:** All 14 GRC domain modules built on top of the platform engine. Each module is a set of Liquibase migrations (tables), Liquibase seed data (test context), service classes, GraphQL types, and React screens.

### Build Order (dependency-driven)

Modules are built in tiers. Lower tiers must be complete before higher tiers begin.

**Tier A — Foundation GRC (no cross-module dependencies):**
1. **Module 26 — Org Hierarchy** (`org_units`, `org_unit_members`) — required by all
2. **Module 15 — Policy Management** (policies, approval workflows)
3. **Module 16 — Risk Management** (risks, risk scoring, heat maps)
4. **Module 17 — Control Management** (controls, control testing)

**Tier B — Dependent GRC (reference Tier A):**
5. **Module 18 — Compliance Management** (regulatory frameworks, compliance mapping)
6. **Module 20 — Audit Management** (audit programs, findings)
7. **Module 25 — Assessment & Questionnaire Engine**
8. **Module 19 — Issues & Findings** (cross-module issue tracking)

**Tier C — Operational GRC (reference Tier A + B):**
9. **Module 22 — Incident Management**
10. **Module 21 — Vendor / Third-Party Risk**
11. **Module 24 — Vulnerability Management**
12. **Module 23 — Business Continuity**
13. **Module 27 — Regulatory Reporting**

### Per-Module Build Checklist

For every GRC domain module, the following artifacts are produced in order:

```
[ ] 1. Liquibase migration — schema tables (context: main)
[ ] 2. Liquibase migration — test seed data (context: test)
[ ] 3. Update docs/modules/NN-*.md — confirm spec matches implementation
[ ] 4. JPA entities + repositories
[ ] 5. Service class skeleton with empty methods
[ ] 6. Write all TDD test cases (all failing)
[ ] 7. Implement service methods until all tests pass
[ ] 8. GraphQL schema additions (types + queries + mutations)
[ ] 9. GraphQL resolvers with BatchMapping
[ ] 10. Module-level E2E test (Testcontainers, real DB schema, rollback per test)
[ ] 11. React module folder: list view, detail view, form (driven by form engine)
[ ] 12. Apollo operations (.graphql files) + codegen run
[ ] 13. React component tests (Vitest + Testing Library)
[ ] 14. Update ADR if module introduces any architectural decisions
```

### Module 16 — Risk Management (Sample Detailed Breakdown)

**Schema additions:**
```sql
-- risk-specific extensions on top of generic records/field_values tables
CREATE TABLE risk_scores (
    record_id         UNIQUEIDENTIFIER NOT NULL REFERENCES records(id),
    org_id            UNIQUEIDENTIFIER NOT NULL,
    likelihood_score  DECIMAL(5,2)     NOT NULL,
    impact_score      DECIMAL(5,2)     NOT NULL,
    computed_score    DECIMAL(5,2)     NOT NULL,
    score_method      NVARCHAR(50)     NOT NULL DEFAULT 'LIKELIHOOD_TIMES_IMPACT',
    computed_at       DATETIME2        NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (record_id)
);
```

**Rule engine integration:**
- Compute rule: `computed_score = likelihood_score × impact_score` — runs on every risk save
- Trigger rule: if `computed_score > 15` → publish `HighRiskIdentifiedEvent` to outbox

**Neo4j relationships (added to projection worker):**
```cypher
(:Risk)-[:MITIGATED_BY]->(:Control)
(:Risk)-[:OWNED_BY]->(:OrgUnit)
(:Risk)-[:CATEGORIZED_AS]->(:RiskCategory)
```

**React screens:**
- Risk Register (filterable table — TanStack Table)
- Risk Detail (form engine + related records panel)
- Risk Heat Map (Recharts scatter plot: likelihood vs impact)
- Risk Impact Graph (React Force Graph — fetches from Neo4j via GraphQL)

---

## Phase 5 — Reporting, Integration & Hardening

> **Output:** The platform is production-ready. Reporting dashboards are live. External integrations are connected. Security hardening is complete.

### P5.1 — Reporting & Dashboard Engine (Module 12)

- Report definitions stored in `report_definitions` table (JSON DSL)
- `ReportExecutor` compiles DSL to SQL query plan (no arbitrary SQL execution — strict AST)
- Scheduled reports: `report_schedules` table, executed by `ReportSchedulerJob`
- Dashboard widgets: React components reading from report execution results
- Chart types: bar, line, pie, heat map, KPI scorecard (all Recharts-based)

### P5.2 — Integration Framework (Module 14)

- `IntegrationDefinition` table: connector type, config (encrypted), event subscriptions
- Connector implementations: `JiraConnector`, `ServiceNowConnector`, `SplunkConnector`
- All outbound calls via `IntegrationDispatchService` (reads from `event_outbox`, dispatches via connector)
- Circuit breaker per connector (Resilience4j)
- Inbound webhooks via REST endpoints, validated by HMAC signature

### P5.3 — Search & Discovery (Module 11)

- `SearchProvider` interface with `SqlServerFtsSearchProvider` as default implementation
- Full-text search indexes on `records.display_name` and field value tables
- Search results include record type, org unit, relevance score
- Architecture designed for future Elasticsearch plug-in (no SQL FTS logic leaks past the interface)

### P5.4 — File & Document Management (Module 13)

- Files stored in external blob storage (Azure Blob or S3 — configurable)
- `record_attachments` table stores metadata only (never binary in DB)
- Upload via REST (not GraphQL — multipart not suited)
- Virus scan integration point (ClamAV or enterprise scanner)
- Signed URL pattern for download (short-lived, org-scoped)

### P5.5 — Security Hardening

All items below are verified in CI via Checkmarx and SonarQube:

| Hardening Item | Implementation |
|---|---|
| OWASP A01 Broken Access Control | `materialized_record_access` table + RLS + Hibernate filter |
| OWASP A02 Cryptographic Failures | AES-256 for stored MFA secrets; TLS 1.3 enforced; no MD5/SHA1 |
| OWASP A03 Injection | No dynamic SQL; Liquibase parameterized; GraphQL depth/cost limits |
| OWASP A04 Insecure Design | Strict AST rule engine; no `eval`; no script engines |
| OWASP A05 Security Misconfiguration | Keycloak hardened realm; introspection off in prod; CSP headers |
| OWASP A06 Vulnerable Components | Dependabot + OWASP dependency-check in CI |
| OWASP A07 Auth Failures | JWT freshness filter; `role_version` check; TOTP mandate |
| OWASP A08 Insecure Deserialization | Strict Jackson config; sealed AST classes; no polymorphic typing |
| OWASP A09 Security Logging | Immutable hash-chained audit log; all auth events logged |
| OWASP A10 SSRF | Outbound HTTP via allowlisted `HttpClient` with connector registry |

---

## 4. Testing Strategy

### 4.1 Test Pyramid

```
              ┌─────────────────────────────┐
              │     E2E (Playwright)        │  ← 30 user-journey tests
              │     Slow but high-value     │
              ├─────────────────────────────┤
              │  Module-Level Integration   │  ← ~200 tests (Testcontainers)
              │  (real DB, real rules,      │  ← stub: blob storage, SMTP
              │   rollback per test)        │
              ├─────────────────────────────┤
              │     Unit Tests              │  ← ~800 tests
              │  (rule engine, audit,       │  ← no Spring, no DB
              │   pure logic)               │
              └─────────────────────────────┘
```

### 4.2 Integration Test Design (Testcontainers)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@Transactional  // Rollback after each test — no manual cleanup
class RiskServiceIntegrationTest {

    @Container
    static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2025-latest");

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5-community");

    @BeforeAll
    static void runMigrations() {
        // Liquibase runs once for the entire test class (context: test)
        // NOT before every test method
    }

    @Test
    void createRisk_withHighScore_publishesHighRiskEvent() {
        // Given: org context, risk data
        // When: riskService.create(...)
        // Then: event_outbox has HIGH_RISK_IDENTIFIED event
        // Transaction rolls back — no state leaks to next test
    }
}
```

**Why this is fast:**
- DB container starts **once per test class** (not per test method)
- Liquibase runs **once per test class**
- Each test uses `@Transactional` for automatic rollback
- A 200-test integration suite runs in under 4 minutes

### 4.3 RBAC Test Coverage

Every service operation must have explicit tests for:
1. Admin role — succeeds
2. Read-only role — returns 403 on write
3. Cross-org access attempt — returns 404 (not 403 — don't reveal existence)
4. Deleted record access — returns 404

### 4.4 Regression Policy

- All tests in the repository are regression tests. There is no concept of "just for this PR."
- CI blocks merge when any test fails, regardless of which module changed.
- `// NOTEST:` comments are banned by SonarQube quality profile.
- Coverage drops below 90% block merge.

---

## 5. Coding Agent Usage Strategy

### 5.1 Agent Task Decomposition

Coding agents perform best on well-bounded, self-contained tasks. Structure every agent invocation as:

**Format for agent prompts:**
```
Context: [point to relevant docs/modules/NN-*.md and relevant ADRs]
Task: [exactly one thing — a service method, a migration, a component]
Constraints: [reference the relevant .github/copilot-instructions.md rules]
Tests: [list the test method names that must pass when this task is done]
Definition of done: [explicit checklist]
```

**Good agent task examples:**
- "Implement `RiskService.computeScore()` per docs/modules/16-risk-management.md §4.2. Use `ComputeContext`. Must pass the 5 tests in `RiskServiceTest.java`."
- "Write `V20260406_001_add_risk_scores.xml` Liquibase migration for the `risk_scores` table per the schema in docs/modules/16-risk-management.md §3.1. Include rollback."
- "Add `riskHeatMap` GraphQL query to schema, resolver using `@BatchMapping`. Must execute in 2 SQL queries for a list of 100 risks."

**Bad agent task examples (too broad, will produce inconsistent output):**
- "Build the Risk module." ← needs to be 14+ separate tasks
- "Add authentication." ← needs to be decomposed by component

### 5.2 Agent Review Checklist

After every agent-generated file, verify:
- [ ] Test file exists and tests pass
- [ ] No `ThreadLocal` used
- [ ] No generic `catch (Exception e)`
- [ ] No hardcoded connection strings or credentials
- [ ] GraphQL collection resolvers use `@BatchMapping`
- [ ] Liquibase migration has `<rollback>` block
- [ ] `org_id` comes from `OrgContext.CURRENT` (never from method params)
- [ ] Any new architectural decision has an ADR entry

### 5.3 Instruction File Maintenance

When an agent generates code that violates a principle (e.g., uses `ThreadLocal` instead of `ScopedValue`), the fix is:
1. Fix the code
2. Update the relevant instruction file to make the violation impossible to miss
3. Consider adding a SonarQube custom rule if the pattern is easy to encode

---

## 6. Living Documentation Procedure

### 6.1 Design ↔ Code Sync Rules

| Event | Action |
|-------|--------|
| New table added | Update `docs/modules/02-data-model-schema.md` in same PR |
| New service method added | Verify `docs/modules/NN-*.md` covers the operation |
| Architecture changes | Create ADR before implementing |
| API contract changes | Update `schema.graphqls` comments AND `docs/modules/04-api-layer.md` |
| Security decision | Update relevant ADR and `docs/modules/07-auth-access-control.md` |
| Test strategy changes | Update this plan AND global `copilot-instructions.md` |

### 6.2 Module Doc Status Tracking

Each `docs/modules/NN-*.md` header includes a `Status` field:

| Status | Meaning |
|--------|---------|
| `In Design` | Not yet built |
| `In Build` | Currently being implemented — may diverge from spec temporarily |
| `Implemented` | Code complete, tests passing, spec accurate |
| `Needs Review` | Code and spec diverged — sync needed |

### 6.3 ADR Lifecycle

- **Proposed:** New ADR created, decision under discussion
- **Accepted:** Decision made, implementation in progress
- **Superseded:** Replaced by a newer ADR (linked)
- **Deprecated:** Feature removed or approach abandoned

---

## 7. Phase Deliverables Summary

| Phase | Weeks | Key Deliverable |
|-------|-------|-----------------|
| Phase 0 | 1–2 | Local dev running; CI pipeline green; Gradle + React skeletons compiling; Instruction files authored |
| Phase 1 | 3–6 | Full schema migrated; RecordService, RuleEngine, AuditService built and tested; Context propagation verified |
| Phase 2 | 7–9 | JWT auth end-to-end; GraphQL API operational; RBAC enforced; Idempotency working |
| Phase 3 | 10–13 | Workflow engine live; Notifications delivered via outbox; Neo4j projection syncing |
| Phase 4 | 14–26 | All 14 GRC domain modules built (Tier A → B → C); React UI for each module |
| Phase 5 | 27–32 | Reporting dashboards; Integration connectors; Full security hardening; SonarQube + Checkmarx clean |

---

## 8. Immediate Next Steps (Week 1)

Execute in this order:

1. **Create `.github/copilot-instructions.md`** (global) with all mandates from §1.1
2. **Create path-specific instruction files** for `frontend/`, `backend/`, `db/`
3. **Create `docs/adr/` directory** with README and first 9 ADRs
4. **Author `infrastructure/docker-compose.yml`** with all four services
5. **Scaffold Gradle multi-module backend** — all modules compiling, `./gradlew build` passes
6. **Scaffold React frontend** — Vite + TypeScript strict + shadcn/ui + Apollo Client installed, `npm run build` passes
7. **Author first Liquibase migration** — `organizations` + `users` tables with test seed data
8. **Set up Tekton pipeline** — build and test stages wired, SonarQube configured
9. **Author `docs/adr/ADR-001` through `ADR-009`** — decisions locked before code is written
10. **Tag `v0.1.0-scaffold`** — the baseline for all future work
