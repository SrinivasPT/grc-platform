# ADR-003 — Neo4j as Derived Graph Read Model

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

GRC data has rich multi-hop relationship chains: `vendor → process → control → risk → policy → regulation → framework`. SQL Server handles 1-hop lookups efficiently via the `record_relations` table. However, 6+-hop traversal (impact analysis, compliance mapping, shortest-path queries) is impractical in relational SQL.

An earlier design revision removed Neo4j to eliminate the consistency gap. The gap was real, but the solution (elimination) created a worse problem: the loss of graph traversal capability fundamental to GRC use cases.

## Decision

**Neo4j 5.x LTS** is retained as a derived read model. SQL Server remains the source of truth for all writes.

**Two-tier graph strategy:**
| Query | Source | Consistency |
|-------|--------|-------------|
| 1-hop lookup (direct relations) | SQL Server `record_relations` | Immediate (same transaction) |
| Multi-hop traversal, impact analysis, path queries | Neo4j | Eventually consistent (~2s lag) |

**Consistency gap management:**
- Graph projection worker polls SQL Server Change Tracking every 2 seconds.
- UI shows a sync indicator when a record's Neo4j representation is stale (version mismatch).
- React optimistic rendering used for relationship mutations — UI updates immediately, confirmed on next poll.
- Graph projection worker is idempotent and restart-safe — uses `last_projected_version` in SQL Server to resume after crashes.

## Consequences

**Positive:**
- Multi-hop GRC queries (impact analysis, compliance path, shortest path) execute in milliseconds via Cypher.
- APOC library enables advanced graph algorithms (community detection, centrality) for future risk analytics.
- Neo4j schema-less — new node types and relationship types added without migration.

**Negative:**
- Two databases to operate and monitor.
- 2-second eventual consistency window requires UI handling (sync indicators, optimistic rendering).
- Projection worker must be fault-tolerant and idempotent (addressed in design).

**Neutral:**
- Neo4j is read-only from the application perspective. All writes go to SQL Server first.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| SQL Server only (no Neo4j) | Multi-hop queries become O(n) recursive CTEs. Impact analysis for a 6-hop chain is 300-400ms+ per query. Unacceptable for interactive use. |
| Apache AGE (PostgreSQL extension) | Not in bank's approved software catalog. PostgreSQL not approved (see ADR-002). |
| In-process adjacency list cache | Memory-prohibitive for GRC graphs with 500k+ nodes. Stale between deploys. |
