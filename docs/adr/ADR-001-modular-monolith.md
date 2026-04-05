# ADR-001 — Modular Monolith Architecture

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

Building an enterprise GRC platform for a single-bank deployment. The system has 20+ functional modules (risk, control, compliance, vendor, workflow, etc.) that need well-defined boundaries, independent testability, and the ability to evolve separately over a multi-year roadmap.

The question was: microservices, modular monolith, or standard monolith?

## Decision

Build a **modular monolith**: a single JVM process partitioned into Gradle subprojects with compile-time enforced dependency boundaries.

Module structure:
- `platform-core` — zero Spring Boot dependencies; domain entities, services, rule engine
- `platform-api` — Spring Boot entry point; depends on all modules
- `platform-workflow`, `platform-notification`, `platform-graph`, `platform-search`, `platform-reporting`, `platform-integration` — each depends only on `platform-core`

Cross-module calls at runtime are via service interfaces defined in `platform-core`, never via direct class imports between sibling modules.

## Consequences

**Positive:**
- Single deployment unit — no distributed transaction complexity.
- Compile-time boundary enforcement — violations are build errors, not runtime surprises.
- Simple local development — one `docker-compose up`, one `./gradlew bootRun`.
- Easy to extract a module to a microservice later if a specific boundary proves unsustainable (strangler fig pattern).

**Negative:**
- All modules share the same JVM — a memory leak in one module affects all.
- Cannot scale modules independently (mitigated: single-bank deployment, predictable load).

**Neutral:**
- Tekton CI tests all modules on every commit — slightly longer pipeline than selective builds.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| Microservices from day 1 | Premature distribution. Network overhead, distributed transactions, and service mesh complexity are not justified for a single-bank deployment. Extract when proven necessary. |
| Standard monolith (no module boundaries) | Boundaries become implicit conventions, not enforced contracts. AI agents and new developers would struggle to reason about coupling. |
