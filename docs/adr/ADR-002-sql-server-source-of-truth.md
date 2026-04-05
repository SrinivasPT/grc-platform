# ADR-002 — SQL Server as the Source of Truth

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The GRC platform requires a primary persistence store for all records, audit logs, workflow state, configuration, and relationships. The store must support: ACID transactions, full-text search, change tracking for CDC-based graph projection, and vector columns for future AI-assisted risk scoring.

## Decision

**SQL Server 2025** is the sole source of truth for all writes. Every mutation is committed to SQL Server first. All other stores (Neo4j, Redis) are derived from SQL Server state.

Key capabilities used:
- **Change Tracking** — CDC feed for Neo4j projection worker
- **Full-Text Search** — `CONTAINS` queries for record search (behind `SearchProvider` interface)
- **Optimistic concurrency** — `rowversion` / `@Version` on all entities
- **`SESSION_CONTEXT`** — org_id injected at connection level for row-level security enforcement
- **SEQUENCE** objects — per-org monotonic sequences for audit hash chain

## Consequences

**Positive:**
- Single authoritative source — no split-brain scenarios.
- All GRC data in ACID transactions — referential integrity enforced at the database level.
- Change Tracking is free / built-in — no additional CDC infrastructure (Debezium/Kafka) needed.
- Bank's DBA team is familiar with SQL Server — ops confidence high.

**Negative:**
- SQL Server licensing cost (SA + CALs) — accepted as a given for bank infrastructure.
- Multi-hop graph queries (6+ hops) are slow in SQL — addressed by two-tier graph strategy (ADR-003).

**Neutral:**
- Liquibase manages all schema changes. JPA `hbm2ddl.auto` is explicitly disabled.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| PostgreSQL | Not in bank's approved database catalog for tier-1 workloads. |
| MongoDB | Document store loses referential integrity — wrong for GRC compliance data. |
| SQL Server + Event Sourcing | Adds event store complexity without clear benefit for this domain. Standard entity-per-table model is sufficient. |
