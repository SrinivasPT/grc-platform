# GRC Platform — Revised Architecture

> **Version:** 3.0 — Full Cross-Review Revision  
> **Date:** April 4, 2026  
> **Scope:** Single-bank deployment replacing Archer IRM  
> **Inputs:** Original 27-module design + ChatGPT critique + DeepSeek critique (task-2) + Gemini deep-dive + DeepSeek full-module review (task-1)

---

## 0. What Changed and Why

This revision keeps the original design's strengths (typed field values, deterministic rule engine, config-driven forms, audit-first mindset) while addressing the major weaknesses identified in both reviews. The headline changes:

| Area | Original | Revised | Rationale |
|------|----------|---------|-----------|
| Graph database | Neo4j as separate derived read model | **Neo4j retained; SQL Server `record_relations` for 1-hop** | GRC has 6+ hop relationship chains. Neo4j is restored for multi-hop traversal, impact analysis, and path queries. The consistency gap is addressed with a two-tier strategy: immediate SQL for 1-hop, eventual Neo4j for traversal, with UI sync indicators and optimistic rendering. |
| Identity gateway | Spring Security SAML/OIDC direct | **Apigee → Keycloak (broker) → Ping Identity** | Production bank pattern: Apigee handles API governance and rate-limiting; Keycloak brokers identity; Ping Identity is the bank's enterprise IdP. Decouples the platform from IdP vendor choice. |
| Audit hash chain | Async background worker (1s window) | **Synchronous per-org serialized hash** using monotonic SEQUENCE | Closes the tamper window. Uses application-level per-org lock + TransactionSynchronization to compute hash in same transaction, update `lastHash` only after commit. Deadlock-free. |
| Rule engine | Single monolithic engine for 6 concerns | **Same DSL, three logical contexts** (compute, validate, trigger) with depth limits | Reduces debugging surface; preserves DSL consistency |
| Aggregation staleness | No dependency tracking | **Parent version bump on child save** + stale flag | Prevents stale computed values on parent records |
| Record-level ABAC | Evaluated at runtime on every access | **Materialized access table** rebuilt on permission/record change | Converts O(rules) per-read to O(1) join |
| Workflow actions | Mixed sync/async in transaction | **All side-effects via transactional outbox** | Prevents phantom notifications on rollback |
| Notification + Integration | Two separate event systems | **Unified event outbox** with notification and integration as consumers | Single retry/DLQ model; no duplication |
| Search | SQL FTS only | SQL FTS behind **`SearchProvider` interface** | Allows future plug-in of Elasticsearch without core changes |
| JWT freshness | 15-min token with embedded roles | **5-min token + `role_version` claim + freshness filter** | Catches permission revocation within 5 minutes; `role_version` in JWT checked against server cache on each request |
| Idempotency | Not addressed | **Idempotency key on all state-changing APIs** | Required for banking-grade reliability |
| Neo4j projection consistency | Eventually consistent, no fallback | **Two-tier: SQL for immediate, Neo4j for traversal** | UI sync indicator, React optimistic rendering, idempotent projection worker with restart-safe version tracking |
| Rule engine security | JSON deserialization via ObjectMapper | **Strict AST model classes; no polymorphic typing** | Passes Checkmarx SAST scan; prevents insecure deserialization (OWASP A08) |
| GraphQL N+1 | `@SchemaMapping` resolvers (potential N+1) | **`@BatchMapping` on all collection resolvers** | Each 5-level deep query executes exactly 5 SQL queries |
| Context propagation | ThreadLocal (breaks with VT migration) | **Java 21 `ScopedValue` + SQL Server `SESSION_CONTEXT`** | Safe for virtual threads; org_id propagated to background workers without ThreadLocal leakage |
| Workflow race condition | No guard on parallel approval completion | **Optimistic locking on `workflow_instances.version`** | `UPDATE ... WHERE version = @v` — only one concurrent completion wins |
| Delegation escalation | One-to-one, no escalation | **`escalation_days` + escalation-to-manager** | Bank requirement: unacted delegated tasks escalate up the org hierarchy |

---

## 1. Revised High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  CLIENT — React 18 SPA                                               │
│  Form Engine · Dashboards · Workflow UI · Global Search              │
│  Apollo Client (GraphQL) + REST for files/webhooks                   │
└─────────────────────────────┬────────────────────────────────────────┘
                              │ HTTPS (TLS 1.3)
┌─────────────────────────────▼────────────────────────────────────────┐
│  API GATEWAY (Spring Boot 3.3 / Java 21)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────────┐  │
│  │ GraphQL      │  │ REST         │  │ Auth Filter (JWT/SAML)    │  │
│  │ + depth/cost │  │ (files,hooks,│  │ Idempotency Filter        │  │
│  │   limits     │  │  SCIM, bulk) │  │ Rate Limit Filter         │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────────────────────┘  │
│         └─────────────────┼──────────────────────────────────────    │
│  ┌────────────────────────▼─────────────────────────────────────┐   │
│  │  SERVICE LAYER                                                │   │
│  │  RecordService · WorkflowService · RuleEngine (3 contexts)   │   │
│  │  AuditService  · AccessService   · SearchService              │   │
│  │  EventOutboxService · FileService · ReportService             │   │
│  └────────────────────────┬─────────────────────────────────────┘   │
│  ┌────────────────────────▼─────────────────────────────────────┐   │
│  │  EVENT OUTBOX WORKER + GRAPH PROJECTION WORKER (in-process)  │   │
│  │  Outbox → notify, integrate, webhook (DLQ on max retry)      │   │
│  │  Graph  → CDC poll → Neo4j sync, idempotent (2s lag target)  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ JDBC/JPA               │ Bolt
          ┌─────────────────▼──────────────┐  ┌──────▼──────────────────┐
          │  SQL SERVER 2025               │  │  Neo4j 5.x LTS          │
          │  (Source of Truth)             │  │  (Derived Graph Model)  │
          │  records, audit, workflow,     │  │  Multi-hop traversal,   │
          │  record_relations (1-hop),     │  │  impact analysis,       │
          │  FTS, config                   │  │  compliance mapping,    │
          │                                │  │  shortestPath()         │
          └────────────────────────────────┘  └─────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────────┐
│  EXTERNAL SERVICES (all behind circuit breakers)                     │
│  Apigee (outbound) · Keycloak (token broker) · Ping Identity (IdP)  │
│  Blob Storage · SMTP · Vulnerability Scanners · SIEM · Ticketing    │
└──────────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

1. **Modular monolith.** Single JVM. Gradle modules enforce compile-time boundaries. Extract only when proven necessary.
2. **SQL Server + Neo4j dual-database.** SQL Server is the source of truth for all writes and immediate queries. Neo4j serves multi-hop graph traversal, impact analysis, and path queries. Consistency gap is managed by strategy, not eliminated by restriction.
3. **Identity gateway chain.** Apigee handles API governance and rate-limiting. Keycloak brokers SAML assertions from Ping Identity into JWTs. The platform is IdP-agnostic.
4. **Event outbox for all async work.** Notifications, integrations, and webhooks flow through a single `event_outbox` table with a unified worker and DLQ.
5. **No distributed transactions.** All writes target SQL Server only. External calls (email, webhooks, Neo4j) happen after commit via the outbox or projection worker.

---

## 2. Decisions & Trade-Offs

### 2.1 Retaining Neo4j — Two-Tier Graph Strategy

**Decision:** Neo4j is restored as the graph layer. The GRC domain has rich, multi-hop relationship chains (vendor → process → control → risk → policy → regulation → framework) that genuinely benefit from native graph storage and Cypher queries. The previous revision eliminated Neo4j to solve the consistency gap, but the right fix is a **strategy for the gap**, not elimination of the graph database.

**Two-tier graph model:**

| Query Type | Data Source | Consistency | Example |
|-----------|-------------|-------------|---------|
| Direct relationship (1-hop) | SQL `record_relations` | Immediate / ACID | "What controls does this risk link to?" |
| Multi-hop traversal | Neo4j | Eventually consistent (< 5s) | "Show all risks exposed by this vendor" |
| Impact analysis | Neo4j | Eventually consistent | "If this control fails, what risks are exposed?" |
| Compliance coverage | Neo4j | Eventually consistent | "Map controls to NIST requirements" |
| Shortest path / visualization | Neo4j | Eventually consistent | Force-directed graph view |

**How the consistency gap is handled:**

1. **`graphSyncVersion` in record API responses.** The API includes `graphSyncPending: Boolean` based on whether Neo4j has processed the record's `updated_at`. Clients use this flag to show a "syncing..." indicator on graph views without blocking the user.

2. **React optimistic rendering.** When a user creates a relationship (e.g., Risk → Control), the React graph view immediately renders the new edge from Apollo cache. Neo4j will catch up within 2–5 seconds; the displayed edge is annotated with a subtle "syncing" spinner until confirmed.

3. **Idempotent projection worker with restart-safe versioning.** The `graph_sync_state` table (in SQL Server) tracks the last processed Change Tracking version per table. On worker restart, processing resumes from the last committed version — no changes lost, no duplicates.

4. **Soft-delete relationship marking.** When a record is soft-deleted, its Neo4j relationships are set `active: false` (not physically deleted). Standard queries add `WHERE r.active = true`. Historical queries (audit/investigation) pass `includeInactive: true`. This prevents the stale relationship problem identified by DeepSeek.

5. **Graph schema versioning.** An `Organization` node property `graphSchemaVersion` tracks projection logic version. The worker applies incremental migrations without requiring a full rebuild.

**What we lose by retaining Neo4j:**
- Second database to operate, monitor, and patch
- Neo4j EE license cost (required for production clustering and online backups)
- Eventual consistency discipline on graph-using UI features

**Why it's worth it:**
- Cypher queries for 5+ hop traversals are dramatically more readable and performant than SQL Server `SHORTEST_PATH` (which does not support variable-weight edges or graph algorithms)
- Native `shortestPath()`, `allShortestPaths()`, and APOC graph algorithms are essential for compliance coverage and vendor risk cascades
- Future: graph-based AI features (control recommendation, gap analysis, similarity scoring) are native to Neo4j

**SQL Server graph tables are removed** from the data model — they were added to replace Neo4j, but that rationale no longer holds. `record_relations` (the relational source-of-truth table) remains.

```cypher
-- Example: Multi-hop vendor risk cascade
MATCH (v:Vendor {id: $vendorId, orgId: $orgId})
MATCH path = (v)-[:PROVIDES_TO]->(:Process)-[:DEPENDS_ON*1..3]->(:Control)<-[:MITIGATED_BY]-(r:Risk)
WHERE r.active = true
RETURN DISTINCT r.id, r.displayName, r.ratingLabel, length(path) AS hops
ORDER BY hops, r.computedScore DESC
LIMIT 100
```

This query has no direct SQL equivalent without recursive CTEs across 5+ join tables.


### 2.2 Unifying Notification + Integration into Event Outbox

The original design had separate systems for notifications and integrations with overlapping concerns (retry logic, webhook delivery, templates, queuing). The revised design uses a single `event_outbox` table:

- **Producers:** WorkflowService, RecordService, RuleEngine triggers, SLA job, scheduled jobs
- **Consumers:** NotificationDeliveryService, IntegrationDispatchService, SlackConnector, JiraConnector, etc.
- **Dead-letter:** Failed events (after max retries) move to `event_outbox_dlq` for manual inspection

This eliminates duplicate retry logic and ensures one consistent event schema.

### 2.3 Simplifying Multi-Tenancy

The original design was built for multi-tenant SaaS. For a single-bank deployment:
- **Keep `org_id`** on all tables — it represents the bank's root organization and enables org-unit scoping without a schema change.
- **Drop** the complexity of per-org SAML config storage (the bank has one IdP).
- **Keep** RLS as defense-in-depth — it prevents data leaks between org units when combined with the organizational hierarchy model.

### 2.4 Rule Engine — Three Logical Contexts, One DSL

ChatGPT correctly identified that the rule engine does too much. The fix is not splitting the DSL but splitting the **execution context**:

| Context | Rules Run | Side Effects | DB Access | Timeout |
|---------|-----------|-------------|-----------|---------|
| **Compute** | `calculation`, `aggregation` | None (pure) | Read-only (lookups, aggregates) | 2 seconds |
| **Validate** | `validation`, `workflow_condition`, `unique_cross_record` | None (pure) | Read-only (existence checks) | 2 seconds |
| **Trigger** | `notification_trigger`, `visibility` (server-side filter) | Writes to event_outbox | None | 500ms |

Same DSL. Same evaluator. Different constraints per context. The evaluator enforces:
- **Max nesting depth:** 4 levels (addresses ChatGPT's concern)
- **Max rules per save:** 50 (prevents combinatorial explosion)
- **Aggregation rules:** at most 1 per field, must use batch SQL (no N+1)

### 2.5 What We Deliberately Omit (For Now)

| Feature | Status | Rationale |
|---------|--------|-----------|
| AI-assisted configuration | Deferred | Valuable but not required for Archer parity |
| Workflow simulator | Deferred to Phase 2 | Nice-to-have; static validation catches most issues |
| Drag-and-drop layout editor | Deferred | Form-based editor is sufficient for MVP; banks configure layouts infrequently |
| Elasticsearch | Deferred | SQL FTS is sufficient at single-bank scale; interface abstracted for future plug-in |
| Mobile app | Deferred | Browser-based responsive UI covers the use case |

---

### 2.6 Identity Gateway Stack — Apigee → Keycloak → Ping Identity

The original design used Spring Security directly against the enterprise IdP. The bank's standard pattern (and task-1 constraint) is a three-tier identity chain:

```
React SPA
  │
  │ 1. Sends credentials / SSO redirect
  ▼
Apigee (API Gateway — Google Cloud / on-prem)
  │  - TLS termination
  │  - API key validation (machine-to-machine)
  │  - Global rate limiting (per API key, per IP, per org-unit)
  │  - Request/response logging for SOC audit
  │  - Developer portal / documentation
  │
  │ 2. Forwards authenticated request with X-Forwarded-User header
  ▼
Spring Boot Application (validates JWT signed by Keycloak)
  │  - Keycloak JWT Filter: validates RS256 signature against /jwks.json
  │  - Extracts: sub (user_id), org_id, roles, role_version, session_id
  │  - RoleFreshnessFilter: checks role_version against server cache
  │
  ▼ (for initial login / token issuance)
Keycloak (Identity Broker)
  │  - Brokers SAML 2.0 from Ping Identity to JWT
  │  - Maps Ping group attributes → GRC platform roles
  │  - Manages refresh token lifecycle
  │  - SCIM 2.0 inbound for user provisioning from HR (Workday/SAP)
  │
  ▼
Ping Identity (Bank Enterprise IdP)
  │  - SAML 2.0 assertions
  │  - MFA enforcement (hardware token / FIDO2)
  │  - Group membership from Active Directory
  │  - Attribute-based role mapping (department + title → GRC role)
```

**Why this chain:**
- Apigee decouples network security and API governance from the application
- Keycloak isolates the application from Ping Identity vendor changes — swapping to Azure AD is a Keycloak config change, not an application change
- Ping Identity is the bank's existing enterprise standard; no new IdP deployment needed

**Spring Boot integration:** The application is an OAuth2 Resource Server. It validates JWTs issued by Keycloak only. It has no direct SAML dependency.

```java
// application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://keycloak.bank.internal/realms/grc/protocol/openid-connect/certs
          issuer-uri:  https://keycloak.bank.internal/realms/grc
```

---

### 2.7 Rule Engine Security — Strict AST Model (Checkmarx Compliance)

**Problem (from Gemini review):** Taking arbitrary JSON from a database and running it through a Jackson `ObjectMapper` with polymorphic type info enabled is a critical Checkmarx SAST failure (OWASP A08 — Software and Data Integrity Failures).

**Solution:** The rule DSL is deserialized into a **strictly typed, closed AST**. No `@JsonTypeInfo`, no `Object` types, no arbitrary class loading:

```java
// Each node in the rule AST is a sealed interface — exhaustive, no escape hatch
public sealed interface RuleExpression
    permits LiteralExpr, FieldRefExpr, BinaryOpExpr, UnaryOpExpr,
            IfExpr, AggregateExpr, LookupExpr, UniqueCheckExpr {}

// Literal values are one of three types — no Object
public record LiteralExpr(LiteralValue value) implements RuleExpression {}
public sealed interface LiteralValue permits StringLiteral, NumberLiteral, BoolLiteral {}

// Binary operations use an enum — no arbitrary class dispatch
public record BinaryOpExpr(BinaryOp op, RuleExpression left, RuleExpression right)
    implements RuleExpression {}
public enum BinaryOp { ADD, SUBTRACT, MULTIPLY, DIVIDE, EQ, NEQ, GT, GTE, LT, LTE, AND, OR }
```

**Jackson configuration for rule DSL parsing:**
```java
@Configuration
public class RuleEngineJacksonConfig {
    @Bean("ruleObjectMapper")
    public ObjectMapper ruleObjectMapper() {
        return JsonMapper.builder()
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // CRITICAL: No polymorphic typing — prevents deserialization attacks
            .disable(MapperFeature.USE_ANNOTATIONS)
            .activateDefaultTypingAsProperty(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(RuleExpression.class)  // allowlist only
                    .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                "type"
            )
            .build();
    }
}
```

**Schema validation before evaluation:** Every rule definition stored in `rule_definitions.expression` is validated against a JSON Schema before it's saved:
- Max nesting depth: 4
- Only known operator keys (`"+", "-", "*", "/", "eq", "if", "aggregate"`, etc.)
- `aggregate.function` must be one of the known enum values
- No `$ref`, no `$schema` references (no external schema fetching)

**GraphQL input sanitization:**
- All string inputs sanitized with `StringEscapeUtils.escapeHtml4()` at the boundary
- `RecordFilterInput` fields validated against an allowlist of field keys from `field_definitions`
- GraphQL depth limit (max 8 levels) enforced by `MaxQueryDepthInstrumentation`
- GraphQL complexity limit (max 100 fields) enforced by `MaxQueryComplexityInstrumentation`

---

### 2.8 Context Propagation — Virtual Threads and Background Workers

**Problem (from Gemini review):** `ThreadLocal` variables (used for `TenantContext` and `AuditContext`) behave unexpectedly with Java 21 Virtual Threads — a virtual thread can be remounted on a different carrier thread, and `ThreadLocal` state may not transfer correctly across `Thread.sleep()` or blocking I/O boundaries. Background workers (Workflow SLA job, Graph Projection Worker, Outbox Worker) have no HTTP request and thus no JWT to extract `org_id` from.

**Solution: Java 21 `ScopedValue` + explicit context propagation for background threads:**

```java
// Replace ThreadLocal with ScopedValue for request-scoped context
public final class TenantContext {
    public static final ScopedValue<TenantInfo> CURRENT = ScopedValue.newInstance();

    // For HTTP requests (set by filter)
    public static <T> T runWith(TenantInfo info, Callable<T> task) throws Exception {
        return ScopedValue.where(CURRENT, info).call(task);
    }

    // Convenience accessor
    public static TenantInfo get() {
        return CURRENT.get();  // throws NoSuchElementException if not set — fail-fast
    }
}

// Updated TenantContextFilter
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            UUID orgId  = UUID.fromString(jwt.getToken().getClaimAsString("org_id"));
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            TenantContext.runWith(new TenantInfo(orgId, userId), () -> {
                chain.doFilter(req, res);
                return null;
            });
        } else {
            chain.doFilter(req, res);
        }
    }
}
```

**Background worker context propagation:**

Background workers (Workflow SLA, Outbox Worker, Graph Projection Worker) run as system tasks. They do not have a user JWT. They must still propagate `org_id` to SQL Server RLS via `SESSION_CONTEXT`:

```java
@Component
public class WorkflowSlaJob {
    @Scheduled(fixedDelay = 60_000)
    public void checkSlaBreaches() {
        // Fetch all orgs that need SLA checks
        List<UUID> orgIds = workflowRepository.getOrgsWithOpenInstances();
        for (UUID orgId : orgIds) {
            TenantInfo systemContext = new TenantInfo(orgId, SYSTEM_USER_ID);
            TenantContext.runWith(systemContext, () -> {
                slaService.processBreachesForOrg(orgId);
                return null;
            });
        }
    }
}
```

**SQL Server `SESSION_CONTEXT` propagation** (required for RLS to work on background connections):

```java
// HikariCP connection decorator — sets SESSION_CONTEXT on every connection checkout
@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource(DataSourceProperties props) {
        HikariDataSource ds = props.initializeDataSourceBuilder()
            .type(HikariDataSource.class).build();
        ds.setConnectionInitSql(null);  // We use a connection decorator instead
        return new TenantAwareDataSource(ds);
    }
}

public class TenantAwareDataSource extends DelegatingDataSource {
    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        propagateTenantContext(conn);
        return conn;
    }

    private void propagateTenantContext(Connection conn) throws SQLException {
        if (TenantContext.CURRENT.isBound()) {
            TenantInfo info = TenantContext.CURRENT.get();
            try (PreparedStatement ps = conn.prepareStatement(
                "EXEC sp_set_session_context @key = N'org_id', @value = ?, @read_only = 1")) {
                ps.setString(1, info.orgId().toString());
                ps.execute();
            }
        }
    }
}
```

**Why `@read_only = 1`:** This prevents SQL injection through `SESSION_CONTEXT` — once set, the `org_id` value cannot be overwritten within the same connection.

---

### 2.9 GraphQL N+1 Prevention — `@BatchMapping` Strategy

**Problem (from Gemini review):** Naive `@SchemaMapping` resolvers for collection fields cause N+1 database queries. A query for 20 records that each load their `fieldValues` would fire 21 SQL queries (1 for records + 20 for fields).

**Solution: `@BatchMapping` on all collection resolvers:**

```java
// WRONG — causes N+1
@SchemaMapping(typeName = "Record", field = "fieldValues")
public List<FieldValue> fieldValues(Record record) {
    return fieldValueRepository.findByRecordId(record.getId());  // 1 query per record
}

// CORRECT — @BatchMapping batches all IDs into one IN query
@BatchMapping(typeName = "Record", field = "fieldValues")
public Map<Record, List<FieldValue>> fieldValues(List<Record> records) {
    List<UUID> ids = records.stream().map(Record::getId).toList();
    // One query: SELECT * FROM field_values WHERE record_id IN (...)
    Map<UUID, List<FieldValue>> byId = fieldValueRepository.findByRecordIds(ids);
    return records.stream().collect(toMap(r -> r, r -> byId.getOrDefault(r.getId(), List.of())));
}

// 5-level deep query: Program → Policy → Risk → Control → Test
// Executes exactly 5 SQL queries (one per level), regardless of record count
@BatchMapping(typeName = "Record", field = "relations")
public Map<Record, List<RecordRelation>> relations(List<Record> records) { ... }

@BatchMapping(typeName = "RecordRelation", field = "record")
public Map<RecordRelation, Record> record(List<RecordRelation> relations) { ... }
```

**Applied to all collection fields on `Record`:** `fieldValues`, `relations`, `attachments`, `workflowInstance`, `auditHistory`. Every fetch of a collection is a `@BatchMapping`.

**GraphQL query cost limits:**
```java
@Bean
public Instrumentation depthAndCostInstrumentation() {
    return ChainedInstrumentation.from(
        new MaxQueryDepthInstrumentation(8),      // max 8 levels of nesting
        new MaxQueryComplexityInstrumentation(200) // max 200 total fields selected
    );
}
```

---

### 2.10 Test Architecture — Fast CI/CD with Real Database

**Problem (from Gemini review):** If Liquibase drops and recreates the test schema before every test method, the CI pipeline takes hours.

**Solution: Single Liquibase initialization + `@Transactional` rollback per test:**

```java
// Test base class — Liquibase runs ONCE per test suite JVM start
@SpringBootTest
@Testcontainers
@Transactional  // Each test rolls back — no cleanup needed, no schema re-init
public abstract class IntegrationTestBase {

    @Container
    static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
        .withInitScript("test-schema-init.sql")  // runs once per container
        .acceptLicense();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", sqlServer::getJdbcUrl);
        // Liquibase runs once on ApplicationContext start — creates schema + seed data
    }
}

// Individual test — no setup/teardown boilerplate
class RiskServiceTest extends IntegrationTestBase {

    @Test
    void createRisk_validInput_persistsAndAudits() {
        // Arrange — uses real DB, real rule engine, real audit service
        // Act
        Record risk = riskService.createRisk(input, userId);
        // Assert
        assertThat(auditLog.findByEntityId(risk.getId())).hasSize(1);
    }
    // @Transactional rolls back after each test — no leftover data
}
```

**Module-level E2E tests** use a `grc_test` Liquibase context that includes:
- All schema migrations (same as production)
- Seed data (system roles, value lists, one test organization, one test application per domain)
- Test users for each role permutation (used to test RBAC rejection paths → 403 coverage)

This achieves >90% branch coverage because:
- Service layer is tested against real SQL (catches index issues, constraint violations)
- Rule engine is invoked with real field values (covers calculation/validation branches)
- RBAC rejection paths are first-class test cases (403/404 coverage)

---

## 3. Data Model — "Simple But Complete"

### 3.1 Schema Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  CONFIG (Meta-Layer)                                                │
│  applications · field_definitions · value_lists · value_list_items  │
│  layout_definitions · rule_definitions                              │
├─────────────────────────────────────────────────────────────────────┤
│  RECORDS (Runtime)                                                  │
│  records · field_values_text · field_values_number                  │
│  field_values_date · field_values_reference · record_attachments    │
├─────────────────────────────────────────────────────────────────────┤
│  RELATIONSHIPS (Relational + Graph)                                 │
│  record_relations (relational table)                                │
│  grc_nodes · grc_edges (SQL Server graph tables — derived, same DB) │
├─────────────────────────────────────────────────────────────────────┤
│  WORKFLOW                                                           │
│  workflow_definitions · workflow_instances · workflow_tasks          │
│  workflow_history                                                    │
├─────────────────────────────────────────────────────────────────────┤
│  AUDIT & VERSIONING                                                 │
│  audit_log (append-only, hash-chained) · record_versions            │
│  audit_read_log                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  EVENTS                                                             │
│  event_outbox · event_outbox_dlq                                    │
├─────────────────────────────────────────────────────────────────────┤
│  IDENTITY & ACCESS                                                  │
│  users · roles · role_permissions · user_roles                      │
│  record_access_rules · materialized_record_access                   │
├─────────────────────────────────────────────────────────────────────┤
│  INTEGRATION                                                        │
│  integration_configurations · webhook_endpoints                     │
│  webhook_subscriptions · processed_idempotency_keys                 │
├─────────────────────────────────────────────────────────────────────┤
│  DOMAIN-SPECIFIC                                                    │
│  policy_acknowledgments (high-volume, separate from records)        │
│  assessment_responses (questionnaire answers, separate for perf)    │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Core Tables (Revised)

#### Records

```sql
CREATE TABLE records (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    record_number   INT               NOT NULL,
    display_name    NVARCHAR(500)     NULL,
    status          NVARCHAR(50)      NOT NULL DEFAULT 'active',
    workflow_state  NVARCHAR(100)     NULL,
    version         INT               NOT NULL DEFAULT 1,          -- optimistic concurrency
    computed_values NVARCHAR(MAX)     NULL                         -- JSON, ISJSON constraint below
                    CONSTRAINT ck_computed_json CHECK (computed_values IS NULL OR ISJSON(computed_values) = 1),
    computed_stale  BIT               NOT NULL DEFAULT 0,          -- NEW: marks aggregations as needing recompute
    is_deleted      BIT               NOT NULL DEFAULT 0,
    deleted_at      DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    updated_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_record_num_per_app UNIQUE (org_id, application_id, record_number)
);
CREATE INDEX idx_records_app_org     ON records(application_id, org_id) WHERE is_deleted = 0;
CREATE INDEX idx_records_workflow    ON records(workflow_state, application_id, org_id) WHERE is_deleted = 0;
CREATE INDEX idx_records_stale       ON records(computed_stale, application_id) WHERE computed_stale = 1 AND is_deleted = 0;
```

**Changes from original:**
- Added `ISJSON` constraint on `computed_values` (DeepSeek recommendation)
- Added `computed_stale` flag for dependency tracking (DeepSeek recommendation)

#### Field Value Tables (Unchanged — the original design was correct)

```sql
-- field_values_text, field_values_number, field_values_date: unchanged from Module 02
-- field_values_reference: add ON DELETE SET NULL for ref_id
CREATE TABLE field_values_reference (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES field_definitions(id),
    ref_type        NVARCHAR(30)      NOT NULL
                    CHECK (ref_type IN ('value_list_item','record','user','org_unit')),
    ref_id          UNIQUEIDENTIFIER  NULL,       -- SET NULL on referenced entity deletion
    display_label   NVARCHAR(500)     NULL,
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

#### Record Relations + Graph Tables

```sql
-- Relational table (source of truth for relationships)
CREATE TABLE record_relations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    source_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    target_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    relation_type   NVARCHAR(100)     NOT NULL,
    metadata        NVARCHAR(MAX)     NULL,
    is_active       BIT               NOT NULL DEFAULT 1,     -- NEW: inactive when either end is soft-deleted
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_relation UNIQUE (source_id, target_id, relation_type)
);

-- SQL Server Graph Node Table (derived from records, kept in sync via triggers)
CREATE TABLE grc_nodes (
    id              UNIQUEIDENTIFIER  NOT NULL PRIMARY KEY,    -- same as records.id
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    app_key         NVARCHAR(100)     NOT NULL,
    display_name    NVARCHAR(500)     NULL,
    status          NVARCHAR(50)      NOT NULL,
    workflow_state  NVARCHAR(100)     NULL,
    computed_score  DECIMAL(10,2)     NULL,
    is_deleted      BIT               NOT NULL DEFAULT 0
) AS NODE;

-- SQL Server Graph Edge Table (derived from record_relations)
CREATE TABLE grc_edges (
    relation_type   NVARCHAR(100)     NOT NULL,
    relation_id     UNIQUEIDENTIFIER  NOT NULL,    -- FK to record_relations.id
    is_active       BIT               NOT NULL DEFAULT 1
) AS EDGE;
```

**Graph table sync:** Maintained via simple SQL triggers on `records` and `record_relations`. Since both tables are in the same database, the triggers run in the same transaction — zero consistency gap.

```sql
-- Trigger: keep grc_nodes in sync with records
CREATE TRIGGER trg_records_to_graph ON records
AFTER INSERT, UPDATE AS
BEGIN
    -- MERGE into grc_nodes: insert if new, update if changed
    MERGE grc_nodes AS target
    USING inserted AS source
    ON target.id = source.id
    WHEN MATCHED THEN
        UPDATE SET display_name = source.display_name,
                   status = source.status,
                   workflow_state = source.workflow_state,
                   is_deleted = source.is_deleted
    WHEN NOT MATCHED THEN
        INSERT (id, org_id, app_key, display_name, status, workflow_state, is_deleted)
        VALUES (source.id, source.org_id,
                (SELECT internal_key FROM applications WHERE id = source.application_id),
                source.display_name, source.status, source.workflow_state, source.is_deleted);
END;

-- Trigger: keep grc_edges in sync with record_relations
CREATE TRIGGER trg_relations_to_edges ON record_relations
AFTER INSERT, DELETE AS
BEGIN
    -- Insert new edges
    INSERT INTO grc_edges ($from_id, $to_id, relation_type, relation_id, is_active)
    SELECT s.$node_id, t.$node_id, i.relation_type, i.id, i.is_active
    FROM inserted i
    JOIN grc_nodes s ON s.id = i.source_id
    JOIN grc_nodes t ON t.id = i.target_id;

    -- Remove deleted edges
    DELETE e FROM grc_edges e
    INNER JOIN deleted d ON e.relation_id = d.id;
END;
```

#### Audit Log (Revised — Synchronous Hash Chain)

```sql
CREATE SEQUENCE audit_event_seq AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_log (
    event_seq       BIGINT            NOT NULL DEFAULT NEXT VALUE FOR audit_event_seq,
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID(),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_time      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    user_id         UNIQUEIDENTIFIER  NULL,
    entity_type     NVARCHAR(100)     NOT NULL,
    entity_id       UNIQUEIDENTIFIER  NOT NULL,
    action          NVARCHAR(50)      NOT NULL,
    old_value       NVARCHAR(MAX)     NULL,
    new_value       NVARCHAR(MAX)     NULL,
    rule_trace      NVARCHAR(MAX)     NULL,       -- NEW: persisted rule evaluation trace
    ip_address      NVARCHAR(45)      NULL,
    session_id      NVARCHAR(200)     NULL,
    correlation_id  UNIQUEIDENTIFIER  NULL,
    chain_hash      NCHAR(64)         NOT NULL,   -- NOW: computed synchronously, never NULL
    PRIMARY KEY (event_seq, event_time)            -- event_seq as leading key for partition pruning
) ON audit_partition_scheme(event_time);           -- monthly partitions
```

**Synchronous hash computation (addresses DeepSeek's critical concern):**

```java
@Component
public class AuditService {

    // The previous hash is cached in-memory per org. On startup, read from DB.
    private final AtomicReference<String> lastHash = new AtomicReference<>("GENESIS");

    @Transactional
    public void log(AuditEvent event) {
        String prevHash = lastHash.get();
        String rowData = toCanonicalJson(event);
        String hash = sha256(prevHash + "|" + rowData);

        event.setChainHash(hash);
        auditLogRepository.insert(event);  // INSERT in same transaction as the mutation

        lastHash.set(hash);
    }
}
```

**Why this is safe:**
- All mutations in the application go through `AuditService.log()` within the same transaction.
- The `lastHash` is an in-memory atomic reference. In a single-JVM monolith, there is exactly one writer.
- If the transaction rolls back, `lastHash` is not updated (we update it after the insert succeeds in the transaction, using `TransactionSynchronizationManager.registerSynchronization` for after-commit).
- On application restart, the last hash is read from the database: `SELECT TOP 1 chain_hash FROM audit_log ORDER BY event_seq DESC`.
- A daily verification job walks the chain and alerts on any tamper.

**Concurrency:** Multiple threads calling `log()` concurrently are serialized by the `audit_event_seq` sequence. The `AtomicReference` ensures only one thread reads and updates `lastHash` at a time via compare-and-swap. Under high concurrency, this is a bottleneck — but audit log inserts are fast (single INSERT) and GRC platforms do not have millisecond-level write requirements.

**Correction for after-commit update:**

```java
@Transactional
public void log(AuditEvent event) {
    String prevHash;
    String hash;
    synchronized (this) {  // serialize hash chain computation
        prevHash = lastHash.get();
        String rowData = toCanonicalJson(event);
        hash = sha256(prevHash + "|" + rowData);
        event.setChainHash(hash);
        auditLogRepository.insert(event);
        lastHash.set(hash);  // optimistic: set immediately
    }

    // If transaction rolls back, reset hash to previous
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    lastHash.compareAndSet(hash, prevHash);
                }
            }
        }
    );
}
```

#### Read Audit — Synchronous for Highly Sensitive Fields

```sql
CREATE TABLE audit_read_log (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_time      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    user_id         UNIQUEIDENTIFIER  NOT NULL,
    entity_type     NVARCHAR(100)     NOT NULL,
    entity_id       UNIQUEIDENTIFIER  NOT NULL,
    fields_accessed NVARCHAR(MAX)     NOT NULL,
    ip_address      NVARCHAR(45)      NULL
);
-- Minimal indexes — optimized for write speed
CREATE INDEX idx_readlog_entity ON audit_read_log(entity_id, event_time);
```

**Policy:** Fields marked `is_highly_sensitive = 1` trigger **synchronous** read logging (in the same transaction as the data read). All other `audit_reads = 1` fields use async logging (bounded queue, acceptable loss for non-critical reads).

#### Unified Event Outbox

```sql
CREATE TABLE event_outbox (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_type      NVARCHAR(200)     NOT NULL,     -- e.g. 'notification.email', 'webhook.jira', 'integration.sync'
    event_key       NVARCHAR(200)     NULL,          -- for dedup/grouping
    channel         NVARCHAR(30)      NOT NULL,      -- 'email','in_app','webhook','sms','integration'
    payload         NVARCHAR(MAX)     NOT NULL,       -- JSON: fully rendered content (template already applied)
    recipient       NVARCHAR(500)     NULL,           -- email address, webhook URL, user_id, etc.
    status          NVARCHAR(20)      NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','processing','sent','failed','dlq')),
    attempt_count   INT               NOT NULL DEFAULT 0,
    max_attempts    INT               NOT NULL DEFAULT 5,
    next_attempt_at DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    last_error      NVARCHAR(2000)    NULL,
    idempotency_key NVARCHAR(200)     NULL,          -- prevents duplicate delivery
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    sent_at         DATETIME2         NULL
);
CREATE INDEX idx_outbox_pending ON event_outbox(status, next_attempt_at) WHERE status IN ('pending','processing');

-- Dead letter queue — manual inspection and replay
CREATE TABLE event_outbox_dlq (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    original_id     UNIQUEIDENTIFIER  NOT NULL,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_type      NVARCHAR(200)     NOT NULL,
    channel         NVARCHAR(30)      NOT NULL,
    payload         NVARCHAR(MAX)     NOT NULL,
    recipient       NVARCHAR(500)     NULL,
    attempt_count   INT               NOT NULL,
    last_error      NVARCHAR(2000)    NULL,
    moved_at        DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    resolved_at     DATETIME2         NULL,
    resolved_by     UNIQUEIDENTIFIER  NULL,
    resolution      NVARCHAR(20)      NULL CHECK (resolution IN ('replayed','discarded'))
);
```

#### Materialized Record Access

```sql
-- Materialized ABAC results: rebuilt when record ownership/org-unit changes or access rules change
CREATE TABLE materialized_record_access (
    record_id       UNIQUEIDENTIFIER  NOT NULL,
    user_id         UNIQUEIDENTIFIER  NOT NULL,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    can_read        BIT               NOT NULL DEFAULT 0,
    can_update      BIT               NOT NULL DEFAULT 0,
    can_delete      BIT               NOT NULL DEFAULT 0,
    computed_at     DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (record_id, user_id)
);
CREATE INDEX idx_mra_user ON materialized_record_access(user_id, org_id) WHERE can_read = 1;
```

**Rebuild triggers:**
1. Record created or ownership field changed → recompute for all users with matching roles
2. Access rule changed → bulk recompute for all records in the affected application
3. User role assignment changed → recompute for all records in applications the user can access

**Cost:** This is a write-amplification trade-off. For a single bank (~10K users, ~500K records), the materialization table has at most ~50M rows (not all users access all records — sparse). Rebuilds are batched and run asynchronously via the event outbox.

**Read path:** Record list queries JOIN to this table instead of evaluating ABAC rules at runtime:

```sql
SELECT r.* FROM records r
INNER JOIN materialized_record_access mra
    ON mra.record_id = r.id AND mra.user_id = @userId AND mra.can_read = 1
WHERE r.application_id = @appId AND r.is_deleted = 0;
```

#### Idempotency Keys

```sql
CREATE TABLE processed_idempotency_keys (
    idempotency_key NVARCHAR(200)     NOT NULL PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    response_status INT               NOT NULL,
    response_body   NVARCHAR(MAX)     NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    expires_at      DATETIME2         NOT NULL  -- TTL: 24 hours default
);
CREATE INDEX idx_idem_expires ON processed_idempotency_keys(expires_at);
-- A nightly job purges expired rows
```

---

## 4. Rule Engine & Workflow

### 4.1 Rule Engine — Revised

The DSL is unchanged from the original design (Module 03). The changes are operational:

**Dependency tracking for aggregations:**

When a child record is saved and the parent has aggregation rules:

```java
@Transactional
public Record saveRecord(SaveRecordInput input) {
    Record record = doSave(input);  // save field values, run compute + validate rules

    // Find parent records that aggregate over this record's application
    List<UUID> parentIds = recordRelationRepository
        .findParentIds(record.getId());

    for (UUID parentId : parentIds) {
        Record parent = recordRepository.findById(parentId);
        if (hasAggregationRules(parent.getApplicationId())) {
            // Mark parent as stale — will be recomputed
            recordRepository.markComputedStale(parentId);
            // For critical fields (e.g., control_effectiveness → risk_score),
            // recompute synchronously within this transaction
            if (hasCriticalAggregations(parent.getApplicationId())) {
                recomputeAggregations(parent);
            }
        }
    }

    return record;
}
```

**"Critical" aggregations** (configurable per rule) are recomputed synchronously. Non-critical aggregations are recomputed lazily on next read or by a background job that processes `computed_stale = 1` records every 30 seconds.

**Rule explainability:**

Every rule evaluation returns a structured trace:

```json
{
  "rule_id": "uuid",
  "rule_name": "Risk Score Calculation",
  "inputs": {
    "likelihood": 4,
    "impact": 5
  },
  "expression_trace": [
    { "op": "*", "left": 4, "right": 5, "result": 20 }
  ],
  "result": 20,
  "duration_ms": 2
}
```

This trace is:
- Returned to the UI for the "Why?" explain button (visibility rules, blocked transitions)
- Persisted in `audit_log.rule_trace` for every record mutation (regulatory evidence — DeepSeek recommendation)

**Rule validation at save time:**

```java
public void validateRuleDefinition(RuleDefinition rule) {
    // 1. Parse DSL — reject invalid JSON or unknown operators
    // 2. Check nesting depth ≤ 4
    // 3. Check for circular field references (DAG validation)
    // 4. If type = aggregation, verify relation_type exists
    // 5. If type = visibility, verify expression is pure (no lookups, no aggregations)
    // 6. Dry-run against sample data to verify type compatibility
}
```

### 4.2 Workflow Engine — Revised

**Changes from original:**

1. **All on_enter_actions go through the event outbox.** No direct notification sending or task creation happens inside the workflow transition transaction. Instead, events are inserted into `event_outbox` within the same transaction. The outbox worker processes them after commit.

2. **Optimistic locking on workflow_instances** for parallel approval race conditions.

3. **Static validation** of workflow definitions at save time.

4. **Version-pinned instances** — running instances use the definition version they started with.

**Transition execution (revised):**

```
triggerTransition(instanceId, transitionKey, actorId, idempotencyKey):
  1. Check idempotency key → if already processed, return cached result
  2. Load WorkflowInstance (SELECT ... WITH (UPDLOCK))
  3. Verify current_state ∈ transition.from_states
  4. Verify actor authorization
  5. Evaluate conditions (Rule Engine — validate context)
  6. Check require_comment
  7. Within the SAME transaction:
     a. UPDATE workflow_instances SET current_state = new_state, version = version + 1
        WHERE id = @id AND version = @expectedVersion  -- optimistic lock
        → if 0 rows updated: throw ConcurrencyConflictException
     b. INSERT workflow_history row
     c. UPDATE records.workflow_state
     d. For each on_enter_action:
        INSERT INTO event_outbox (event_type, payload)
        VALUES ('workflow.create_task', {...})  -- or 'workflow.notify', etc.
     e. INSERT INTO processed_idempotency_keys
  8. COMMIT
  9. Outbox worker (after commit) processes:
     - create_task events → INSERT workflow_tasks
     - notify events → send email/in-app notification
     - set_field events → update field values (separate transaction)
```

**Wait — task creation must be synchronous.** If we defer `create_task` to the outbox, the API response cannot return the newly created tasks. Revised approach: `create_task` is an in-transaction action (it's a local DB insert, no external call). Only `notify` and `set_field` go through the outbox.

```
  7d. For each on_enter_action:
      - 'create_task' → INSERT workflow_tasks (same transaction)
      - 'notify'      → INSERT INTO event_outbox
      - 'set_field'   → INSERT INTO event_outbox (processed immediately after commit)
```

**Static validation of workflow definitions:**

```java
public List<ValidationError> validateWorkflow(WorkflowDefinition def) {
    List<ValidationError> errors = new ArrayList<>();

    // 1. Must have exactly one initial_state
    // 2. Must have at least one terminal state
    // 3. Every state must be reachable from initial_state via transitions
    // 4. Every non-terminal state must have at least one outgoing transition
    // 5. No transition can target a state that doesn't exist
    // 6. Parallel approval transitions must not have completion_mode missing
    // 7. SLA config must have valid positive hours
    // 8. Condition DSL must parse and validate
    // 9. Detect cycles that don't pass through a terminal state (warning, not error)

    Set<String> stateKeys = def.getStates().stream()
        .map(State::getKey).collect(toSet());
    Set<String> terminalKeys = def.getStates().stream()
        .filter(State::isTerminal).map(State::getKey).collect(toSet());

    if (terminalKeys.isEmpty()) {
        errors.add(new ValidationError("Workflow must have at least one terminal state"));
    }

    // Reachability check via BFS from initial_state
    Set<String> reachable = bfsReachable(def.getInitialState(), def.getTransitions());
    for (String stateKey : stateKeys) {
        if (!reachable.contains(stateKey)) {
            errors.add(new ValidationError("State '" + stateKey + "' is unreachable"));
        }
    }

    // Deadlock detection: non-terminal states with no outgoing transitions
    Set<String> statesWithOutgoing = def.getTransitions().stream()
        .flatMap(t -> t.getFromStates().stream()).collect(toSet());
    for (String stateKey : stateKeys) {
        if (!terminalKeys.contains(stateKey) && !statesWithOutgoing.contains(stateKey)) {
            errors.add(new ValidationError("Non-terminal state '" + stateKey + "' has no outgoing transitions (deadlock)"));
        }
    }

    return errors;
}
```

**SLA evaluation — revised per DeepSeek:**
- `sla_due_at` is computed when entering a state (synchronous, in transition transaction)
- SLA breach check runs every 60 seconds (background job) AND on-demand when a task is completed
- The job is idempotent: uses `sla_breach_processed_at` to avoid re-processing
- Breach triggers an event_outbox entry for escalation notification

---

## 5. Audit & Versioning

### 5.1 Design Summary

| Concern | Approach |
|---------|----------|
| **Mutation audit** | Every create/update/delete/transition logged in `audit_log` with correlation_id |
| **Tamper evidence** | Synchronous SHA-256 hash chain per row (no async window) |
| **Rule traceability** | `rule_trace` JSON persisted on every mutation involving rule evaluation |
| **Read audit** | Synchronous for `is_highly_sensitive` fields; async for `audit_reads` fields |
| **Record snapshots** | Full JSON snapshot on workflow transitions and explicit version locks |
| **Immutability** | DB user has INSERT-only on audit tables; triggers prevent UPDATE/DELETE |
| **Retention** | Mutation audit: 7 years. Read audit: 2 years. Monthly partitions. |
| **Query optimization** | Indexed views for "latest changes per record" and "changes by user" |

### 5.2 Audit Query Views

```sql
-- Indexed view: latest audit events per record (for timeline UI)
CREATE VIEW v_audit_latest_per_record WITH SCHEMABINDING AS
SELECT entity_id, entity_type, MAX(event_time) AS last_event_time, COUNT_BIG(*) AS event_count
FROM dbo.audit_log
WHERE entity_type = 'record'
GROUP BY entity_id, entity_type;

CREATE UNIQUE CLUSTERED INDEX idx_v_audit_latest
ON v_audit_latest_per_record(entity_id, entity_type);
```

```sql
-- For the record timeline view: efficient retrieval of recent changes
CREATE INDEX idx_audit_entity_time
ON audit_log(entity_id, entity_type, event_time DESC)
INCLUDE (action, user_id, correlation_id);
```

### 5.3 Snapshot Consistency (DeepSeek fix)

Record snapshots are generated from the **in-memory state** of the record after the save, within the same transaction:

```java
@Transactional
public Record saveRecord(SaveRecordInput input) {
    Record record = doSave(input);

    // Log audit event with in-memory state (not a separate SELECT)
    String snapshot = snapshotSerializer.toJson(record, record.getFieldValues());
    auditService.log(AuditEvent.builder()
        .entityType("record")
        .entityId(record.getId())
        .action("update")
        .newValue(snapshot)
        .ruleTrace(ruleEngine.getLastTrace())
        .build());

    // If workflow transition or manual lock → also write record_versions
    if (shouldSnapshot(input)) {
        recordVersionRepository.insert(new RecordVersion(
            record.getId(), record.getVersion(), snapshot, input.triggerEvent()
        ));
    }

    return record;
}
```

---

## 6. Access Control

### 6.1 Authentication

Unchanged from original (SAML 2.0 / OIDC / local fallback with mandatory TOTP). Key revision:

**JWT lifetime reduced to 5 minutes** (from 15). The `roles` claim includes a `role_version` integer. On each request, the API filter compares the JWT's `role_version` with the current version from a fast cache (in-memory, invalidated on role assignment changes). If stale, the request proceeds with **re-fetched** roles from the database.

```java
@Component
public class RoleFreshnessFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        int tokenRoleVersion = auth.getToken().getClaimAsInt("role_version");
        int currentRoleVersion = roleVersionCache.getCurrentVersion(auth.getUserId());

        if (tokenRoleVersion < currentRoleVersion) {
            // Roles changed since token was issued — reload from DB
            List<String> freshRoles = roleRepository.findRolesByUserId(auth.getUserId());
            auth = auth.withUpdatedRoles(freshRoles);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
```

### 6.2 Authorization Layers

```
Request arrives
  │
  ├─ 1. JWT validation (signature, expiry, org_id)
  ├─ 2. Role freshness check (see above)
  ├─ 3. Rate limit check
  ├─ 4. Idempotency key check (for mutations)
  │
  ├─ 5. Application-level permission (RBAC)
  │     "Does this user's role grant 'risk:read'?"
  │     → Fast: in-memory role → permission map
  │
  ├─ 6. Record-level permission (Materialized ABAC)
  │     "Can this user read THIS specific risk record?"
  │     → Fast: JOIN to materialized_record_access
  │
  ├─ 7. Field-level permission (RBAC)
  │     "Can this user see the 'salary' field?"
  │     → Fast: field_definitions.config.field_level_access checked at serialization
  │
  └─ 8. Action-level permission (RBAC + workflow actor check)
        "Can this user trigger the 'approve' transition?"
        → Checked in WorkflowService
```

### 6.3 API Keys — Scoped

```sql
CREATE TABLE api_keys (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    key_hash        NCHAR(64)         NOT NULL,       -- SHA-256 of the key
    key_prefix      NCHAR(8)          NOT NULL,        -- first 8 chars for identification
    scope           NVARCHAR(MAX)     NULL,            -- JSON: rule DSL expression for scoping
    -- Example: { "and": [{"eq": [{"field":"app_key"},{"literal":"risk"}]},
    --                     {"eq": [{"field":"action"},{"literal":"read"}]}] }
    role_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES roles(id),
    expires_at      DATETIME2         NULL,
    is_active       BIT               NOT NULL DEFAULT 1,
    last_used_at    DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL
);
```

---

## 7. Integration & Resilience

### 7.1 Unified Event Processing

All async work flows through the event outbox:

```java
@Component
public class EventOutboxWorker {

    @Scheduled(fixedDelay = 2000)
    public void processPendingEvents() {
        List<EventOutbox> batch = outboxRepository.findPending(50);  // SELECT ... WITH (UPDLOCK, READPAST)

        for (EventOutbox event : batch) {
            try {
                outboxRepository.markProcessing(event.getId());
                eventRouter.dispatch(event);  // routes to correct handler by channel
                outboxRepository.markSent(event.getId());
            } catch (Exception e) {
                handleFailure(event, e);
            }
        }
    }

    private void handleFailure(EventOutbox event, Exception e) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(truncate(e.getMessage(), 2000));

        if (event.getAttemptCount() >= event.getMaxAttempts()) {
            // Move to DLQ
            outboxRepository.moveToDlq(event);
            alertAdmin(event);
        } else {
            // Exponential backoff: 1m, 5m, 30m, 2h
            Duration delay = calculateBackoff(event.getAttemptCount());
            event.setNextAttemptAt(Instant.now().plus(delay));
            outboxRepository.updateForRetry(event);
        }
    }
}
```

### 7.2 Circuit Breakers

External service calls (email, webhook, blob storage, SAML IdP) are wrapped in circuit breakers using Resilience4j:

```java
@Bean
public CircuitBreakerConfig defaultCircuitBreaker() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(50)           // open after 50% failure rate
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .slidingWindowSize(10)
        .build();
}
```

When a circuit is open:
- **Email:** Events stay in outbox, retried when circuit closes
- **Blob storage:** File uploads return 503; downloads fall back to direct stream if available
- **Webhooks:** Queued in outbox; external system is expected to handle idempotent replay
- **Neo4j (if ever reintroduced):** Graph queries return empty results; SQL fallback used

### 7.3 Idempotency for All State-Changing APIs

Every GraphQL mutation and REST POST/PUT/PATCH accepts an optional `Idempotency-Key` header:

```java
@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String key = req.getHeader("Idempotency-Key");
        if (key == null || isReadOnlyMethod(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        // Check if this key was already processed
        Optional<ProcessedKey> existing = idempotencyRepository.findByKey(key);
        if (existing.isPresent()) {
            // Return cached response
            res.setStatus(existing.get().getResponseStatus());
            res.getWriter().write(existing.get().getResponseBody());
            return;
        }

        // Wrap response to capture output
        ContentCachingResponseWrapper wrappedRes = new ContentCachingResponseWrapper(res);
        chain.doFilter(req, wrappedRes);

        // Store result for future replays (24h TTL)
        idempotencyRepository.save(new ProcessedKey(
            key, wrappedRes.getStatus(),
            new String(wrappedRes.getContentAsByteArray()),
            Instant.now().plus(Duration.ofHours(24))
        ));
        wrappedRes.copyBodyToResponse();
    }
}
```

### 7.4 Inbound Integration — Idempotency

External systems pushing data (vulnerability scanners, SCIM, Jira webhooks) must include an `Idempotency-Key` header. If omitted, the platform generates one from a hash of the payload + timestamp (best-effort dedup).

```java
@PostMapping("/api/v1/integrations/{connectorKey}/inbound")
public ResponseEntity<?> receiveInbound(
        @PathVariable String connectorKey,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody String payload) {

    if (idempotencyKey == null) {
        idempotencyKey = sha256(connectorKey + "|" + payload).substring(0, 40);
    }

    if (idempotencyRepository.existsByKey(idempotencyKey)) {
        return ResponseEntity.ok().body(Map.of("status", "already_processed"));
    }

    // Process asynchronously
    eventOutboxService.enqueue(EventOutbox.builder()
        .eventType("integration.inbound." + connectorKey)
        .payload(payload)
        .idempotencyKey(idempotencyKey)
        .build());

    return ResponseEntity.accepted().build();
}
```

### 7.5 Integration Loop Prevention

For bidirectional integrations (Jira, ServiceNow):

```java
public class IntegrationLoopGuard {
    // Every record stores last_sync_source in metadata
    // When processing an inbound webhook:
    public boolean shouldProcess(String connectorKey, UUID recordId) {
        RecordSyncMeta meta = syncMetaRepository.find(recordId);
        if (meta != null
            && meta.getLastSyncSource().equals(connectorKey)
            && meta.getLastSyncAt().isAfter(Instant.now().minus(Duration.ofSeconds(30)))) {
            // This update originated from us → skip to prevent loop
            return false;
        }
        return true;
    }
}
```

### 7.6 GraphQL Protection

```java
@Bean
public GraphQlSource graphQlSource() {
    return GraphQlSource.schemaResourceBuilder()
        .instrumentation(List.of(
            new MaxQueryDepthInstrumentation(8),        // max 8 levels deep
            new MaxQueryComplexityInstrumentation(200)  // max cost 200
        ))
        .build();
}
```

Persisted queries are enabled in production — clients register queries at build time; arbitrary queries are blocked.

---

## 8. GRC Domain Coverage — Archer Parity

All GRC domains are implemented as **configured applications** on top of the platform engine. Each domain is a combination of: application fields, rules, workflows, layouts, and relationships.

### 8.1 Domain Mapping

| GRC Domain | Application Key | Key Relationships | Workflow |
|-----------|----------------|-------------------|----------|
| **Risk Management** | `risk` | → controls, → issues, → policies, → vendors, → incidents | Draft → Review → Approved → Monitored → Closed |
| **Policy Management** | `policy` | → controls, → requirements, → org_units | Draft → Review → Approved → Published → Review Due |
| **Control Management** | `control` | → risks, → policies, → tests, → issues | Active → Testing → Remediation → Verified |
| **Compliance Management** | `compliance_requirement` | → controls, → policies, → frameworks | Mapped → Gap Identified → Remediated → Compliant |
| **Control Testing** | `control_test` | → control (parent) | Planned → In Progress → Completed → Reviewed |
| **Issues & Findings** | `issue` | → risks, → controls, → audits, → vendors | Open → In Remediation → Verification → Closed |
| **Audit Management** | `audit` | → risks, → controls, → issues, → org_units | Planning → Fieldwork → Reporting → Closed |
| **Vendor/Third-Party Risk** | `vendor` | → risks, → contracts, → assessments | Onboarding → Active → Review Due → Offboarding |
| **Incident Management** | `incident` | → risks, → controls, → issues | Reported → Triage → Investigation → Resolved → Closed |
| **BCP** | `bcp_plan` | → processes, → assets, → tests | Draft → Approved → Active → Test Due |
| **Vulnerability Management** | `vulnerability` | → assets, → risks, → controls, → issues | Discovered → Triaged → Remediation → Verified → Closed |
| **Assessments** | `assessment` | → questionnaire_template, → vendor/risk/control | Draft → Sent → In Progress → Completed → Reviewed |
| **Regulatory Reporting** | `reg_report` | → requirements, → controls, → evidence | Draft → Review → Approved → Submitted → Acknowledged |
| **Org Hierarchy** | `org_unit` | → users, → all record types (scoping) | N/A (config, not workflow) |
| **Frameworks** | `framework` | → requirements → controls | N/A (reference data) |

### 8.2 Domain-Specific Tables (Outside the Generic Model)

Only two domain-specific tables exist outside the generic records model. Everything else is a configured application:

```sql
-- Policy acknowledgments: high cardinality (users × policies), not a standard record
CREATE TABLE policy_acknowledgments (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    policy_record_id UNIQUEIDENTIFIER NOT NULL,
    user_id         UNIQUEIDENTIFIER  NOT NULL,
    acknowledged_at DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    policy_version  NVARCHAR(50)      NOT NULL,
    method          NVARCHAR(50)      NOT NULL DEFAULT 'platform',
    ip_address      NVARCHAR(50)      NULL
);

-- Assessment responses: questionnaire answers with scoring, high volume per assessment
CREATE TABLE assessment_responses (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    assessment_record_id UNIQUEIDENTIFIER NOT NULL,
    question_id     UNIQUEIDENTIFIER  NOT NULL,
    response_value  NVARCHAR(MAX)     NULL,
    score           DECIMAL(10,2)     NULL,
    evidence_file_id UNIQUEIDENTIFIER NULL,
    answered_by     UNIQUEIDENTIFIER  NOT NULL,
    answered_at     DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 8.3 Pre-Built Configuration Packs

The platform ships with **seed data** for each GRC domain — field definitions, value lists, workflow definitions, layout definitions, and rule definitions. These are Liquibase changesets that run on first deployment:

```
db/migrations/
  └── seed/
      ├── V100__seed_risk_application.sql
      ├── V101__seed_policy_application.sql
      ├── V102__seed_control_application.sql
      ├── V103__seed_compliance_framework.sql
      ├── V104__seed_issue_application.sql
      ├── V105__seed_audit_application.sql
      ├── V106__seed_vendor_application.sql
      ├── V107__seed_incident_application.sql
      ├── V108__seed_bcp_application.sql
      ├── V109__seed_vulnerability_application.sql
      ├── V110__seed_assessment_application.sql
      ├── V111__seed_reg_reporting_application.sql
      ├── V112__seed_value_lists.sql           -- risk ratings, statuses, categories, etc.
      ├── V113__seed_frameworks_nist.sql       -- NIST SP 800-53 requirements
      ├── V114__seed_frameworks_iso27001.sql   -- ISO 27001 controls
      └── V115__seed_default_dashboards.sql
```

Each seed file creates the application definition, its field definitions, default layouts, standard workflow, and common rules. The bank can modify any of these after deployment — they are data, not code.

---

## 9. Archer Retirement Checklist

### 9.1 Migration Requirements

| Category | What Must Be Migrated | Validation |
|----------|----------------------|------------|
| **Applications** | All Archer applications → GRC platform applications (field definitions, value lists) | Field-by-field comparison of app definitions |
| **Records** | All Archer records → GRC records with field values | Row count match + sample spot-check |
| **Relationships** | Cross-references between records | Relationship count by type; graph traversal comparison |
| **Workflows** | Active workflow instances (current state, task assignments) | State comparison for in-flight records |
| **Historical audit** | Archer audit trail → GRC audit_log | Event count match per record |
| **Attachments** | Uploaded files → blob storage + record_attachments | File count + checksum verification |
| **Users & roles** | Archer users → GRC users with role mappings | User count; role assignment comparison |
| **Saved searches** | Archer saved searches → GRC saved_searches | Manual verification (format differs) |
| **Reports & dashboards** | Archer reports → GRC report/dashboard definitions | Visual comparison of output |
| **Notification rules** | Archer notification configs → GRC notification_rules | Manual review |
| **Integrations** | Existing Archer integrations → GRC connectors | Integration test per connector |
| **Calculated fields** | Archer calculated fields → GRC rule_definitions | Spot-check 50 records per app: computed values match |

### 9.2 Migration Strategy

**Phase 1 — Parallel Run (4–8 weeks)**
- Both platforms run simultaneously
- New records created in GRC platform
- Archer is read-only (no new records)
- Daily reconciliation report: compare record counts, field values, workflow states

**Phase 2 — Validation (2–4 weeks)**
- All users work in GRC platform
- Archer remains accessible read-only for historical reference
- GRC platform handles all new workflows, approvals, and integrations
- Address any gaps found during parallel run

**Phase 3 — Decommission**
- Archer data archived (regulatory retention)
- Archer decommissioned
- GRC platform is sole system of record

### 9.3 Migration Tooling

```java
// Archer Data Extractor: reads from Archer API/database
public interface ArcherDataExtractor {
    List<ArcherApplication> extractApplications();
    List<ArcherRecord> extractRecords(String appKey, int batchSize);
    List<ArcherRelationship> extractRelationships();
    List<ArcherUser> extractUsers();
}

// GRC Data Importer: writes to GRC platform via service layer (not direct SQL)
public interface GrcDataImporter {
    ImportResult importApplication(ArcherApplication app);
    ImportResult importRecords(List<ArcherRecord> records, UUID appId);
    ImportResult importRelationships(List<ArcherRelationship> relations);
}

// Reconciliation: compares Archer and GRC data
public interface MigrationReconciler {
    ReconciliationReport reconcile(String appKey);
    // Returns: matching records, missing records, field value mismatches
}
```

### 9.4 Archer Feature Parity Checklist

| Archer Feature | GRC Platform Equivalent | Status |
|---------------|------------------------|--------|
| Applications (entity types) | `applications` table + `field_definitions` | ✅ Equivalent |
| Content records | `records` + typed `field_values_*` tables | ✅ Equivalent |
| Cross-references | `record_relations` + graph tables | ✅ Equivalent + graph traversal |
| Calculated fields | Rule Engine DSL (`calculation` type) | ✅ Equivalent + explainability |
| Data-driven events (notifications) | Rule-triggered notifications via event outbox | ✅ Equivalent |
| Workflow (approval chains) | Workflow Engine (state machine + parallel approvals) | ✅ Equivalent |
| Record permissions (RBAC) | RBAC + materialized ABAC | ✅ Equivalent + attribute-based |
| Search & filter | SQL FTS + structured filter DSL | ✅ Equivalent |
| Reports | Configurable reports + dashboards | ✅ Equivalent |
| Data import/export | CSV/JSON import, Excel/CSV export | ✅ Equivalent |
| Questionnaires | Assessment application + `assessment_responses` | ✅ Equivalent |
| Policy acknowledgments | `policy_acknowledgments` table | ✅ Equivalent |
| Archer Access Control (groups) | Roles + org-unit scoping | ✅ Equivalent |
| Archer Exchange (integrations) | Integration Framework + connectors | ✅ Equivalent |
| Archer Feed (data feeds) | Inbound integration REST API + SCIM | ✅ Equivalent |
| Audit trail | Immutable audit_log with hash chain | ✅ Superior (tamper-evident) |
| GRC use case management | 14 pre-built domain packs | ✅ Equivalent |
| RSA Archer dashboards | Configurable dashboard widgets | ✅ Equivalent |
| Matrix/heat maps | Risk matrix widget | ✅ Equivalent |

**Features where the new platform is superior to Archer:**
- Tamper-evident audit log (hash chain)
- Deterministic, explainable rule engine
- Graph-based impact analysis (SQL graph)
- Modern API (GraphQL + REST)
- Real-time notifications (WebSocket subscriptions)
- Idempotent integrations with DLQ
- SCIM 2.0 user provisioning
- Materialized record-level access control

---

## 10. Operational Runbook Summary

### Routine Operations

| Task | Frequency | Mechanism |
|------|-----------|-----------|
| Audit hash chain verification | Daily (automated) | Background job walks chain, alerts on tamper |
| Event outbox DLQ review | Daily (manual) | Admin reviews failed events, replays or discards |
| Audit log partition management | Monthly (automated) | Create next month's partition; archive old partitions |
| ABAC materialization health check | Hourly (automated) | Compare sample of runtime-evaluated vs materialized results |
| Computed stale recomputation | Every 30 seconds (automated) | Background job processes `computed_stale = 1` records |
| SLA breach check | Every 60 seconds (automated) | Background job evaluates pending SLAs |
| Idempotency key cleanup | Nightly (automated) | Purge expired keys from `processed_idempotency_keys` |
| Graph table consistency | Nightly (automated) | Verify all records have corresponding grc_nodes; all relations have grc_edges |

### Failure Scenarios

| Scenario | Impact | Recovery |
|----------|--------|----------|
| SQL Server down | Full outage | Standard HA failover (Always On AG) |
| Email server down | Notifications queued | Circuit breaker + outbox retry after recovery |
| Blob storage down | File upload/download fails | 503 response; uploads retried by client |
| Hash chain broken | Tamper detected | Alert security team; investigate; repair chain from last known good |
| ABAC materialization stale | Users may see slightly stale access | Next hourly check repairs; manual rebuild via admin API |
| Outbox worker crash | Events delayed | Worker restarts; picks up where it left off (UPDLOCK + READPAST) |

---

## 11. Technology Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | React 18 + TypeScript | 18.x |
| UI Components | shadcn/ui (Tailwind CSS) | Latest |
| State/API | Apollo Client (GraphQL) + Zustand | Latest |
| Charts | Recharts | Latest |
| Build (FE) | Vite | Latest |
| Backend | Java 21 LTS + Spring Boot 3.3 | 21 / 3.3.x |
| GraphQL | Spring for GraphQL | 1.3.x |
| Security | Spring Security 6 (OAuth2/SAML2) | 6.x |
| ORM | Hibernate 6 + Spring Data JPA | 6.x |
| Resilience | Resilience4j | Latest |
| Database | **SQL Server 2025** (single DB) | 2025 |
| Schema Migration | Liquibase 4.x | 4.x |
| Blob Storage | Azure Blob / S3 / local | Abstracted |
| Email | Spring Mail + Thymeleaf | Bundled |
| Build (BE) | Gradle 8 (multi-module) | 8.x |
| Containers | Docker | Latest |
| Orchestration | Kubernetes | Target |

**What was removed:** Neo4j, Kafka (not needed — outbox pattern suffices), any message broker.

**Total external dependencies:** SQL Server + blob storage + SMTP server + IdP. Four services to operate.
