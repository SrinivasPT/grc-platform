# Module 01 — Platform Architecture

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** None (this is the root module)

---

## 1. Purpose

This module defines the overall system architecture, technology stack rationale, project structure, service boundaries, and deployment model for the GRC Platform. It is the reference document that all other modules anchor to.

---

## 2. Core Architecture Philosophy

The platform is built as a **relational data platform with a domain-aware rule engine**, not a form builder or workflow tool. Every feature — policy approval, risk scoring, vendor assessment — is a domain-specific expression of the same underlying infrastructure.

### Guiding Constraints

- **Boring is good.** Choose proven, well-supported technology over cutting-edge.
- **Explicit over implicit.** No magic, no convention-over-configuration in the core engine.
- **Layered, not flat.** Each layer has one responsibility and one direction of dependency.
- **Independent modules.** No circular dependencies. Each module can be built, tested, deployed alone.
- **Auditability is first-class.** Not an afterthought. Every mutation is logged.

---

## 3. System Layers

```
┌──────────────────────────────────────────────────────────────────────┐
│  CLIENT LAYER                                                        │
│  React 18 SPA — Form Engine, Dashboard, Workflow UI, Reports         │
│  Communicates exclusively via GraphQL (queries/mutations/subscripts) │
│  Falls back to REST for file upload and webhook callbacks            │
└─────────────────────────────┬────────────────────────────────────────┘
                              │ HTTPS
┌─────────────────────────────▼────────────────────────────────────────┐
│  API LAYER (Spring Boot 3.3.x / Java 21)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ GraphQL      │  │ REST (MVC)   │  │ Auth Filter (JWT/SAML)   │   │
│  │ Controllers  │  │ Controllers  │  │ Tenant Resolver          │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────────────────────┘   │
│         └─────────────────┼──────────────────────────────────────    │
│                           │                                          │
│  ┌────────────────────────▼─────────────────────────────────────┐   │
│  │  SERVICE LAYER                                                │   │
│  │  RecordService · RelationshipService · WorkflowService       │   │
│  │  RuleEngine    · AuditService        · NotificationService    │   │
│  └────────────────────────┬─────────────────────────────────────┘   │
└───────────────────────────┼──────────────────────────────────────────┘
                            │ JDBC / JPA (SQL Server)
┌───────────────────────────▼──────────────────────────────────────────┐
│  PERSISTENCE LAYER — SQL Server 2025 (Source of Truth)               │
│  All records, config, relationships, workflow state, audit log       │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ Change Data Capture (SQL Server CT)
┌───────────────────────────▼──────────────────────────────────────────┐
│  PROJECTION WORKER (Async, Spring Batch / Virtual Threads)           │
│  Reads CDC feed → writes to Neo4j                                    │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ Bolt Protocol
┌───────────────────────────▼──────────────────────────────────────────┐
│  GRAPH LAYER — Neo4j 5.x LTS (Derived Read Model)                   │
│  Relationship traversal, impact analysis, dependency mapping         │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. Technology Stack

### 4.1 Frontend — React 18

| Decision | Detail |
|----------|--------|
| Framework | React 18 with TypeScript |
| State Management | Zustand (local UI state) + Apollo Client (server state via GraphQL) |
| Component Library | shadcn/ui (Tailwind-based, unstyled primitives — avoids vendor lock) |
| Routing | React Router v7 |
| Form Rendering | Custom Form Engine driven by server-side JSON config |
| Charts/Visualization | Recharts for standard charts; React Force Graph for relationship maps |
| Build Tool | Vite |

**Why React (not Angular/Vue):** Component model aligns with config-driven UI — each field type is a component, each layout is a component tree. The ecosystem for data-heavy applications (Apollo, React Query, TanStack Table) is unmatched.

### 4.2 API Layer — Java 21 / Spring Boot 3.3.x

| Decision | Detail |
|----------|--------|
| Runtime | Java 21 LTS |
| Framework | Spring Boot 3.3.x |
| GraphQL | Spring for GraphQL 1.3.x |
| REST | Spring MVC (for file upload, webhooks, integration APIs) |
| Security | Spring Security 6.x (OAuth2 Resource Server, SAML2) |
| Persistence | JPA (Hibernate 6.x) + Spring Data JPA ; raw JDBC for bulk operations |
| Schema Migration | Liquibase 4.x (context-aware — `grc_main` for production, `grc_test` for E2E tests) |
| Build | Gradle 8.x (multi-module) |

**Why Java 21:** Virtual threads (Project Loom) enable high concurrency without the complexity of reactive programming. Records and sealed classes improve domain modeling. Java 21 is production-proven and will be supported until 2031.

**Why Spring Boot:** Most mature enterprise Java framework. Unmatched integration ecosystem. Spring Security for SAML/OIDC/JWT is production-grade and battle-tested.

### 4.3 Primary Database — SQL Server 2025

| Decision | Detail |
|----------|--------|
| Engine | SQL Server 2025 |
| Change Tracking | SQL Server Change Tracking (for Neo4j CDC feed) |
| Full-Text Search | SQL Server Full-Text Search (for basic search; see Module 11) |
| JSON Support | `FOR JSON`, `OPENJSON` for config and metadata columns |
| Connection Pool | HikariCP (bundled with Spring Boot) |
| Schema Style | Normalized tables. No EAV anti-patterns. Typed field values. |

**Why SQL Server over PostgreSQL:** The user specified SQL Server 2025. It adds native vector search, improved JSON handling, and enhanced change tracking. ACID semantics with row-level locking and snapshot isolation handle multi-user GRC workflows correctly.

### 4.4 Graph Database — Neo4j 5.x LTS

| Decision | Detail |
|----------|--------|
| Engine | Neo4j 5.x LTS |
| Query Language | Cypher + APOC library |
| Driver | Neo4j Java Driver 5.x |
| Mode | Read-mostly. Never written to by application code directly. Only by Projection Worker. |
| Sync Strategy | Async, eventually consistent. Bounded staleness (< 5 seconds under normal load). |

**Why Neo4j:** GRC relationship traversal (Control → Policy → Regulation → Framework → Risk) requires graph traversal, not recursive SQL. Multi-hop relationship queries that would require 6-way JOINs in SQL become single Cypher statements.

### 4.5 GraphQL — Spring for GraphQL

**Why GraphQL fits this platform:**
- GRC forms are deeply nested (Record → Fields → Related Records → Their Fields)
- Clients need to declare exactly what they need — avoids over-fetching on dashboard views
- Mutations with input validation map naturally to GRC record mutations
- Subscriptions enable real-time workflow status updates and notification badges
- Self-documenting schema doubles as API contract between frontend and backend

**Where REST is used instead:**
- File upload (multipart form data — not suited for GraphQL)
- Incoming webhooks from third-party systems
- Health check / readiness endpoints
- Legacy integration connectors

---

## 5. Project Structure (Gradle Multi-Module)

```
grc-platform/
├── docs/                          ← Design documentation (this repo)
│   ├── index.md
│   └── modules/
├── frontend/                      ← React 18 SPA
│   ├── src/
│   │   ├── components/
│   │   │   ├── form-engine/       ← Config-driven form renderer
│   │   │   ├── workflow/          ← Workflow step UI
│   │   │   └── shared/
│   │   ├── modules/               ← One folder per GRC domain
│   │   │   ├── risk/
│   │   │   ├── policy/
│   │   │   └── ...
│   │   ├── graphql/               ← Apollo operations (.graphql files)
│   │   └── app/                   ← Routes, providers, shell
│   └── vite.config.ts
├── backend/
│   ├── build.gradle               ← Root build
│   ├── settings.gradle
│   ├── platform-api/              ← Spring Boot application (GraphQL + REST entry point)
│   │   └── src/main/java/com/grc/api/
│   ├── platform-core/             ← Domain entities, services, rule engine
│   │   └── src/main/java/com/grc/core/
│   ├── platform-workflow/         ← Workflow engine module
│   │   └── src/main/java/com/grc/workflow/
│   ├── platform-notification/     ← Notification engine
│   │   └── src/main/java/com/grc/notification/
│   ├── platform-graph/            ← Neo4j projection worker
│   │   └── src/main/java/com/grc/graph/
│   ├── platform-search/           ← Search service
│   │   └── src/main/java/com/grc/search/
│   ├── platform-reporting/        ← Reporting engine
│   │   └── src/main/java/com/grc/reporting/
│   ├── platform-integration/      ← Integration framework
│   │   └── src/main/java/com/grc/integration/
│   └── db/
│       └── migrations/            ← Liquibase changelogs (contexts: main, test)
└── infrastructure/
    ├── docker-compose.yml         ← Local dev: SQL Server + Neo4j + app
    └── k8s/                       ← Kubernetes manifests (future)
```

---

## 6. Service Boundaries

### Monolith-First Strategy

The platform starts as a **modular monolith** — all service modules run in a single JVM process. The multi-module Gradle structure enforces boundaries at compile time. No module may import from another module's internal packages.

**When to extract to microservices:** Only when a specific module demonstrates independent scaling requirements (e.g., the Notification Engine under high volume, or the Graph Projection Worker). This is deferred until those metrics are observed.

### Module Dependency Rules (enforced via Gradle)

```
platform-api       → platform-core, platform-workflow, platform-notification, 
                     platform-search, platform-reporting, platform-integration
platform-core      → no internal dependencies
platform-workflow  → platform-core
platform-notification → platform-core
platform-graph     → platform-core (reads from SQL, writes to Neo4j)
platform-search    → platform-core
platform-reporting → platform-core
platform-integration → platform-core
platform-orgunit   → platform-core  (Organizational Hierarchy — Module 26)
```

**Circular dependencies are a build failure.**

---

## 7. Multi-Tenancy Model

**Approach: Single-bank organization with internal hierarchy**

This platform is deployed for a **single bank**. Multi-tenant SaaS design is not required. Every table carries an `org_id` column (scoped to the one bank's organization record) to keep the data model clean and future-proof. The primary isolation units are **organizational units** (business divisions, departments, subsidiaries, regions) — see Module 26.

The `org_id` from the Keycloak JWT is injected into every database operation at two levels:
1. **JPA level:** Hibernate `@Filter` applied globally for all queries via `OrgContextHolder`
2. **DB level:** SQL Server Row-Level Security (RLS) using `SESSION_CONTEXT(N'org_id')` as defense-in-depth

### Background Thread Context Propagation (Virtual Threads — Java 21)

Background workers (Workflow Engine, Notification Dispatcher, Graph Projection Worker) do not have an active HTTP request. The `org_id` must be propagated explicitly:

- **HTTP requests:** `OrgContextFilter` extracts `org_id` from the JWT claim and stores it in a `ScopedValue<UUID>` (Java 21 — not `ThreadLocal`, which may not propagate correctly across virtual thread mount/unmount boundaries).
- **Background tasks:** Before dispatching a task to a virtual thread, the dispatcher captures the `ScopedValue` carrier and re-binds it in the new thread scope via `ScopedValue.runWhere(ORG_CONTEXT, orgId, task)`.
- **SQL Server session context:** `HikariCP` `ConnectionInitSql` is not used; instead, a custom `DataSourceConnectionInterceptor` executes `EXEC sp_set_session_context @key=N'org_id', @value=@orgId, @read_only=1` on each connection borrow, reading the value from `OrgContextHolder.get()`.

This ensures RLS is correctly applied for all queries regardless of whether they originate from an HTTP thread or a background virtual thread.

---

## 8. Deployment Model

### Local Development
- Docker Compose: SQL Server 2025 Developer Edition + Neo4j 5 Community
- Spring Boot app runs locally (Gradle bootRun)
- Vite dev server for React with proxy to Spring Boot

### Production (Target)
- Containerized: All services in Docker containers
- Orchestration: Kubernetes (target) — manifests in `/infrastructure/k8s/`
- Database: SQL Server on dedicated VM or Azure SQL MI (SQL Server 2025 compatible)
- Neo4j: **Neo4j Enterprise Edition** (production) for clustering and online backup; Neo4j Community Edition for development and CI

### Test Schema Strategy (Liquibase Dual-Context)

The CI pipeline uses a `grc_test` schema alongside the production `grc_main` schema:

- Liquibase changesets use `context="main"` (production) and `context="test"` (seed data).
- Liquibase runs **once** at test suite start using `@TestcontainersConfig` — *not* before each test method.
- Each test method is annotated `@Transactional` and rolls back its data mutations after completion.
- This ensures tests execute in milliseconds while still exercising real SQL Server logic, stored procedures, and RLS policies.

```java
// Example: single Liquibase init for all integration tests
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BaseIntegrationTest {
    @Container
    static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest");

    // Liquibase runs once: grc_test schema + seed data created once per suite
    // Individual tests roll back via @Transactional
}
```
- Reverse Proxy: nginx or Azure Application Gateway
- Secrets: Kubernetes Secrets (or Azure Key Vault in cloud deployment)

---

## 9. Cross-Cutting Concerns

### Security
- All endpoints require authenticated JWT (Spring Security Resource Server)
- CSRF protection on REST endpoints
- Input validation at API boundary (not in frontend only)
- SQL injection prevention via parameterized queries (never string concatenation)
- File upload: size limits, type validation, virus scan hookpoint
- Rate limiting on all API endpoints (configurable per org)
- Audit log is append-only — never mutable after write

### Observability
- Structured logging (JSON format via Logback)
- Metrics: Micrometer → Prometheus-compatible endpoint
- Distributed tracing: OpenTelemetry (configurable exporter)
- Health checks: Spring Boot Actuator `/health`, `/readiness`
- Every rule engine execution returns a structured trace (see Module 03)

### Schema Management
- All schema changes via Flyway versioned migrations
- Migrations run automatically on startup (non-destructive only in production)
- Rollback scripts maintained for every migration

### Error Handling
- Standard error envelope for all GraphQL and REST responses
- Machine-readable error codes (not just HTTP status codes)
- No stack traces exposed to clients in production
- Validation errors returned as structured field-level error objects

---

## 10. Non-Functional Requirements

| Requirement | Target | How Achieved |
|-------------|--------|-------------|
| Response time (API, p99) | < 500ms | Connection pooling, DB indexes, query optimization |
| Response time (complex report) | < 5s | Async report generation, result caching |
| Concurrent users | 500 minimum | Virtual threads (Java 21 Loom), stateless API |
| Availability | 99.9% | Health checks, graceful shutdown, K8s restarts |
| Audit retention | 7+ years | Partitioned audit tables, archival strategy |
| Data isolation | 100% tenant | RLS + JPA filter + application-level enforcement |
| Schema evolution | Non-breaking | Flyway migrations, API versioning, config versioning |

---

## 11. Open Questions

| # | Question | Owner | Priority | Resolution |
|---|----------|-------|----------|-----------|
| 1 | ~~SaaS vs single-enterprise deployment?~~ | Stakeholder | Critical | **Resolved:** Single bank. `org_id` columns are retained for internal organizational hierarchy (business units, subsidiaries) — see Module 26. Only one `organizations` record exists. |
| 2 | On-premise vs cloud-only? Affects storage, backup, and networking design | Stakeholder | Critical | |
| 3 | Required SSO providers? (Azure AD, Okta, PingIdentity, etc.) | Stakeholder | High | Apigee → Keycloak (broker) → Ping Identity per constraints. |
| 4 | Expected peak concurrent users for initial launch? | Stakeholder | High | |
| 5 | Data residency / sovereignty requirements? | Legal/Compliance | High | |
| 6 | Initial GRC modules to launch (MVP scope)? | Product | High | MVP: Risk, Control, Policy, Issues, Compliance, and basic Reporting. |

*Next Module: [02 — Data Model & Schema](02-data-model-schema.md)*
