# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for the GRC Platform.

## What is an ADR?

An ADR is a document that captures an important architectural decision, including the context in which it was made, the decision itself, and its consequences.

## ADR Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](ADR-001-modular-monolith.md) | Modular Monolith Architecture | Accepted |
| [ADR-002](ADR-002-sql-server-source-of-truth.md) | SQL Server as the Source of Truth | Accepted |
| [ADR-003](ADR-003-neo4j-derived-graph.md) | Neo4j as Derived Graph Read Model | Accepted |
| [ADR-004](ADR-004-keycloak-identity-broker.md) | Keycloak as Identity Broker (Apigee → Keycloak → Ping Identity) | Accepted |
| [ADR-005](ADR-005-event-outbox-pattern.md) | Transactional Outbox for All Async Side-Effects | Accepted |
| [ADR-006](ADR-006-rule-engine-dsl.md) | Rule Engine DSL with Strict AST Parsing | Accepted |
| [ADR-007](ADR-007-liquibase-dual-context.md) | Liquibase Dual-Context Migration Strategy | Accepted |
| [ADR-008](ADR-008-scopedvalue-context-propagation.md) | Java 21 ScopedValue for Request Context Propagation | Accepted |
| [ADR-009](ADR-009-graphql-batchmapping.md) | GraphQL BatchMapping Mandate for Collections | Accepted |

## ADR Template

```markdown
# ADR-NNN — Title

**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date:** YYYY-MM-DD
**Deciders:** {Names or roles}

## Context

{Describe the situation that necessitated a decision. What was the problem?}

## Decision

{Describe the decision that was made.}

## Consequences

**Positive:**
- {Positive outcome 1}

**Negative:**
- {Negative outcome/trade-off 1}

**Neutral:**
- {Neutral observation}

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| {Alt 1} | {Why not} |
```

## Policy

- Every change to architecture or module interfaces requires a new or updated ADR.
- ADR and code changes must be in the **same PR**.
- ADRs are immutable once Accepted — create a new ADR to supersede.
