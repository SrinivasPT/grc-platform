# GRC Platform — Design Documentation Index

> **Status:** In Design | **Last Updated:** 2026-04-04
> **Tech Stack:** React · GraphQL · Java 21 LTS · SQL Server 2025 · Neo4j 5.x LTS

---

## Critical Review of the Foundation Document

The `notes.md` establishes a philosophically sound foundation. Before proceeding to module design, the following assessment identifies strengths, critical gaps, and design corrections that must be addressed.

### ✅ What the Foundation Gets Right

| Principle | Why It Holds |
|---|---|
| SQL Server as source of truth | ACID guarantees, auditable, mature operational tooling |
| Neo4j as read-only graph projection | Correct — avoids dual-write hell, right tool for traversal |
| JSON DSL rule engine | Safe, versionable, auditable — the right call |
| No scripting engines (no eval) | Security and debuggability are non-negotiable in GRC |
| Config is data, not code | Enables versioning, safer for non-developers, supports upgrades |
| Relationships over nesting | Scalable, reusable, mirrors real-world GRC structures |
| Avoiding event sourcing initially | 80% value at 20% complexity — pragmatic and correct |
| Server is the source of truth | Prevents UI drift, required for compliance auditability |

---

### ⚠️ Critical Gaps That Must Be Addressed

The foundation document describes *infrastructure philosophy* but is **entirely silent on GRC domain coverage**. An Archer-equivalent platform requires the following that are not mentioned anywhere:

#### 1. GRC Domain Modules Not Defined
The notes mention Policy, Control, Test, and Issue as examples — but a full Archer-equivalent platform requires:
- Enterprise Risk Management
- Operational Risk Management
- Regulatory & Compliance Management
- Audit Management
- Vendor / Third-Party Risk Management
- Incident Management
- Business Continuity & Disaster Recovery
- Vulnerability Management
- Assessment & Questionnaire Engine

#### 2. Workflow / BPM Engine — Completely Absent
Archer's most powerful feature is its declarative workflow engine. Every GRC process — policy approval, risk acceptance, control testing, vendor assessment — is driven by configurable workflow. **Without a workflow engine, this is a data platform, not a GRC platform.**

#### 3. Authentication & Authorization Not Mentioned
Enterprise GRC platforms require:
- SSO (SAML 2.0, OIDC)
- Fine-grained RBAC + ABAC (field-level, record-level permissions)
- Multi-tenancy (department-level, organizational-unit isolation)

#### 4. Multi-Tenancy Architecture Not Considered
Whether this platform is SaaS or deployed for a single large enterprise, **tenant isolation strategy must be decided early** — it affects the entire data model.

#### 5. Notification & Alerting Engine Absent
GRC workflows are deadline-driven. Without notifications (email, in-app, webhook), the platform cannot support real-world operations.

#### 6. Reporting & Dashboard Engine Not Addressed
Archer's reporting is one of its most-used features: heat maps, dashboards, scorecards, trend charts. This needs first-class treatment.

#### 7. Assessment & Questionnaire Engine Not Mentioned
A critical use case: sending questionnaires to vendors, employees, or sub-departments to collect GRC data at scale. This is fundamentally different from record management.

#### 8. Integration Framework Absent
GRC platforms must integrate with: SIEM tools, ticketing systems (Jira, ServiceNow), identity providers, vulnerability scanners, asset management systems.

#### 9. GraphQL Design Not Specified
The notes mention GraphQL but its role — schema design, federation strategy, N+1 handling, subscriptions for real-time updates — is undefined.

#### 10. Java Microservices vs Monolith Decision Missing
The architecture diagram shows a flat stack. For a production-grade system, the deployment and service boundary strategy must be defined.

#### 11. Performance Strategy Is Thin
"Lazy load tabs" and "pagination" are UI patterns, not a performance architecture. Caching strategy (Redis?), database indexing, connection pooling, and read replica strategy are absent.

#### 12. Operational Strategy Missing
A "100-year architecture" needs: backup/restore procedures, database migration strategy (Flyway/Liquibase), zero-downtime deployment approach, disaster recovery.

---

### 🔧 Design Corrections and Clarifications

| Item | Current State | Corrected Approach |
|---|---|---|
| GraphQL | Mentioned as optional | Adopted as primary API layer; REST only for file upload, webhooks, and legacy integrations |
| Java version | "Latest LTS" | Java 21 LTS with Spring Boot 3.3.x, Spring GraphQL, Spring Security |
| Neo4j sync | "Projection Worker" (undefined) | Change Data Capture (CDC) via SQL Server change tracking → async event queue → Neo4j writer |
| Rule DSL | Arithmetic only shown | Full DSL covering: arithmetic, comparison, logical, aggregation, lookup, conditional, string ops |
| Form system | Implied | Explicit Form & Layout Engine module with JSON schema spec |
| Tenant model | Absent | Org-based tenancy with row-level tenant isolation |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        FRONTEND (React)                         │
│  Form Engine · Dashboards · Workflow UI · Search · Reports      │
└──────────────────────────┬──────────────────────────────────────┘
                           │ GraphQL / REST
┌──────────────────────────▼──────────────────────────────────────┐
│                    API LAYER (Spring Boot 3 / Java 21)          │
│  GraphQL Schema · REST Endpoints · Auth Filter · Rate Limiter   │
└───┬────────────┬─────────────┬──────────────┬───────────────────┘
    │            │             │              │
┌───▼──┐  ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼──────┐
│ Rule │  │ Workflow  │ │  Notif.   │ │  Search    │
│Engine│  │  Engine   │ │  Engine   │ │  Engine    │
└───┬──┘  └─────┬─────┘ └─────┬─────┘ └─────┬──────┘
    │            │             │              │
┌───▼────────────▼─────────────▼──────────────▼──────────────────┐
│                    SQL SERVER 2025 (Source of Truth)            │
│  Records · Fields · Relationships · Workflow State · Audit Log  │
└──────────────────────────┬──────────────────────────────────────┘
                           │ CDC / Change Tracking
┌──────────────────────────▼──────────────────────────────────────┐
│                  PROJECTION WORKER (Async)                      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    NEO4J 5.x LTS (Graph Projection)             │
│  Relationship Traversal · Impact Analysis · Dependency Maps     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Module Index

Modules are organized into three tiers. **Each module is independently designed, buildable, testable, and deployable.**

---

### Tier 1 — Platform Foundation
*The meta-platform layer. Everything else is built on top of this.*

| # | Module | Purpose |
|---|--------|---------|
| 01 | [Platform Architecture](modules/01-platform-architecture.md) | Overall architecture, technology choices, project structure, deployment model |
| 02 | [Data Model & Schema](modules/02-data-model-schema.md) | Core entity model, SQL Server schema, field system, relationship tables |
| 03 | [Rule Engine](modules/03-rule-engine.md) | JSON DSL specification, execution model, aggregations, trace/explain |
| 04 | [API Layer — GraphQL & REST](modules/04-api-layer.md) | GraphQL schema design, REST endpoints, N+1 handling, subscriptions |
| 05 | [Form & Layout Engine](modules/05-form-layout-engine.md) | Config-driven forms, field types, tabs, conditional logic, validation |
| 06 | [Graph Projection — Neo4j](modules/06-graph-projection.md) | Sync strategy, node/relationship types, traversal queries, impact analysis |

---

### Tier 2 — Platform Services
*Cross-cutting platform services shared by all GRC domain modules.*

| # | Module | Purpose |
|---|--------|---------|
| 07 | [Authentication & Access Control](modules/07-auth-access-control.md) | SSO, JWT, RBAC/ABAC, record/field-level permissions, multi-tenancy |
| 08 | [Workflow Engine](modules/08-workflow-engine.md) | State machines, approval chains, parallel flows, SLA tracking |
| 09 | [Audit Log & Versioning](modules/09-audit-versioning.md) | Change history, optimistic concurrency, compliance trail, record snapshots |
| 10 | [Notification Engine](modules/10-notification-engine.md) | Email, in-app, webhooks, rules-driven triggers, templates, digests |
| 11 | [Search & Discovery](modules/11-search-discovery.md) | Full-text, filtered search, saved searches, graph-aware search |
| 12 | [Reporting & Dashboards](modules/12-reporting-dashboards.md) | List/summary/chart reports, heat maps, KPI dashboards, scheduled exports |
| 13 | [File & Document Management](modules/13-file-document-management.md) | Upload, storage, versioning, access control, virus scanning hookpoint |
| 14 | [Integration Framework](modules/14-integration-framework.md) | Webhooks, REST connectors, SIEM integration, import/export, API keys |

---

### Tier 3 — GRC Domain Modules
*Individual GRC use cases, each independently configurable and deployable.*

| # | Module | Purpose |
|---|--------|---------|
| 15 | [Policy Management](modules/15-policy-management.md) | Policy lifecycle, review/approval, mapping to controls and frameworks |
| 16 | [Risk Management](modules/16-risk-management.md) | Risk register, risk scoring, appetite/tolerance, risk treatment |
| 17 | [Control Management](modules/17-control-management.md) | Control library, effectiveness scoring, test mapping, evidence |
| 18 | [Compliance Management](modules/18-compliance-management.md) | Framework mapping (NIST, ISO 27001, SOX, GDPR), gap analysis |
| 19 | [Issues & Findings](modules/19-issues-findings.md) | Issue tracking, root cause, remediation plans, exceptions |
| 20 | [Audit Management](modules/20-audit-management.md) | Audit planning, fieldwork, findings, audit reports |
| 21 | [Vendor & Third-Party Risk](modules/21-vendor-third-party-risk.md) | Vendor register, due diligence, tiering, ongoing monitoring |
| 22 | [Incident Management](modules/22-incident-management.md) | Incident intake, triage, escalation, resolution, post-mortem |
| 23 | [Business Continuity](modules/23-business-continuity.md) | BCP/DR plans, BIA, testing schedules, RTO/RPO tracking |
| 24 | [Vulnerability Management](modules/24-vulnerability-management.md) | Vulnerability tracking, CVSS scoring, remediation SLAs, scanner integration |
| 25 | [Assessment & Questionnaire](modules/25-assessment-questionnaire.md) | Configurable questionnaires, bulk dispatch, scoring, response tracking |

---

## Design Principles (Enforced Across All Modules)

1. **Server is always the source of truth** — UI is a renderer, never a decision-maker
2. **Config is data, not code** — All behavior is stored in versioned config records
3. **Deterministic rule execution** — Same input → same output, always. No hidden state
4. **Relationships over nesting** — All entity connections via explicit relationship tables
5. **Explicit over implicit** — No magic, no inference, no convention-based behavior in core logic
6. **Every module is independently deployable** — No circular dependencies between modules
7. **Auditability is not optional** — Every state change is recorded; every decision is explainable
8. **Boring is good** — No trendy dependencies; use proven technology at every layer
9. **Fail loudly** — Errors surface immediately; silent failures are never acceptable in GRC
10. **Multi-tenant by design** — All data models carry tenant context from day one

---

## Technology Stack Summary

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Frontend | React | 18.x | Component model fits config-driven UI; rich ecosystem |
| API | GraphQL | Spring GraphQL 1.3.x | Self-documenting, efficient data fetching, subscriptions for real-time |
| API (secondary) | REST (Spring MVC) | Spring Boot 3.3.x | File upload, webhooks, third-party integrations |
| Backend | Java | 21 LTS | Virtual threads, records, sealed classes; production-stable |
| Framework | Spring Boot | 3.3.x | Mature, enterprise-grade, excellent security integration |
| Primary DB | SQL Server | 2025 | ACID, JSON support, change tracking for CDC, full-text search |
| Graph DB | Neo4j | 5.x LTS | Property graph model, Cypher query language, APOC library |
| Auth | Spring Security + OAuth2/OIDC | — | SAML 2.0, OIDC, JWT — all supported |
| Migration | Flyway | 10.x | Schema versioning, repeatable migrations |
| Build | Gradle | 8.x | Faster than Maven for multi-module Java builds |

---

## Working Agreement

- Each module document is the **design contract** for implementation
- Modules are reviewed and signed off before coding begins on that module
- Code is developed module by module, in the order defined by dependency
- Each module ships with: unit tests, integration tests, and documented API schema
- No module may introduce a circular dependency on another module

---

## Module Dependency Map

```
[Auth & Access Control] ← required by ALL modules
[Data Model & Schema]   ← required by ALL modules
[Rule Engine]           ← required by: Form Engine, Workflow, Reporting
[API Layer]             ← required by: ALL modules (exposes them)
[Workflow Engine]       ← required by: Policy, Risk, Control, Audit, Vendor, Incident
[Audit Log]             ← required by: ALL modules
[Notification Engine]   ← required by: Workflow, Issues, Incidents
[Form & Layout Engine]  ← required by: ALL domain modules
[Graph Projection]      ← required by: Risk, Compliance, Impact Analysis
[Search]                ← required by: ALL domain modules
[Reporting]             ← required by: ALL domain modules
```

---

*This document is the master index. Each linked module file contains the full design for that module including entities, APIs, rules, integration points, and implementation guidance.*
