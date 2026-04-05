# ADR-007 — Liquibase Dual-Context Migration Strategy

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The GRC platform needs a schema migration strategy that:
1. Applies production DDL cleanly to live databases.
2. Seeds test data for integration tests without polluting production.
3. Supports rollback for every migration.
4. Works with Testcontainers (ephemeral databases spun up per test run).

Using `spring.jpa.hibernate.ddl-auto` is forbidden — it is non-deterministic and produces no rollback capability.

## Decision

**Liquibase 4.x with dual contexts:**

| Context | Purpose | Run in |
|---------|---------|--------|
| `main` | All DDL: tables, indexes, FKs, FTS, Change Tracking | Production, staging, local dev, CI |
| `test` | Seed data: test org, test users, reference lookups | CI integration tests, local test run only |

**Testcontainers integration:**
- `@SpringBootTest` integration tests start a SQL Server container via Testcontainers.
- Liquibase runs on container startup with contexts `main,test` — seeds the standard test org.
- Test cleanup: each test runs in a `@Transactional` method that is rolled back after the test (where possible). Destructive tests use explicit cleanup.

**Naming convention:** `V{YYYYMMDD}_{NNN}_{description}.xml` (see `backend/db/.github/copilot-instructions.md`).

**Rollback requirement:** Every changeset must include a `<rollback>` block. If DDL is not reversible (e.g., data-destructive `DROP`), document why in the rollback block comment.

## Consequences

**Positive:**
- Production and test schema are kept in sync by the same migration files.
- Test data seeding is separate from DDL — production runs never accidentally include test rows.
- Rollback capability on every migration enables safe deployments.
- Liquibase locks prevent concurrent migration runs (important for multi-instance deployments).

**Negative:**
- Liquibase changeset XML is verbose compared to plain SQL or Flyway.
- Changeset IDs must be globally unique — naming convention enforced to prevent collisions.
- Rollback support for some SQL Server operations (e.g., `DROP INDEX`) requires explicit `<sql>` blocks.

**Neutral:**
- `liquibaseValidate` Gradle task is a CI quality gate — invalid changeset XML blocks the pipeline.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| Flyway | Does not support rollback scripts out of the box. Rollback is a Flyway Teams (paid) feature. |
| JPA `hbm2ddl.auto` | Non-deterministic, no rollback, cannot manage indexes or FTS. Forbidden by coding standards. |
| Plain SQL scripts | No conflict detection, no checksums, no rollback tracking. Migration state invisible without custom tooling. |
