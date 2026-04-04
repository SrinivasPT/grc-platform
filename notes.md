📘 GRC Platform Architecture — Final Summary
🧭 1. Core Objective

Build a system that can support:

Archer-level complexity (Policies, Controls, Tests, Issues)
Hundreds of fields, multi-tab forms
Deep relationships and aggregations
Long-term maintainability (10–100 years mindset)
🧠 2. Foundational Philosophy (Most Important)

These principles drive every decision:

1. Server is the source of truth

Why:

Prevents inconsistencies
Enables auditability
Required for GRC compliance
2. Config is data, not code

Why:

Avoids runtime unpredictability
Enables versioning
Safer for non-developers
3. Deterministic rule engine (no scripting)

Why:

Debuggable
Auditable
Prevents chaos from “hidden logic”
4. UI is replaceable

Why:

Avoids coupling business logic to frontend
Enables future rewrites (Angular → React → something else)
5. Relationships over nesting

Why:

Scalability
Reusability
Matches real-world GRC models
🏗️ 3. Evolution of the Design
Phase 1 Thinking (Initial)
Config-driven forms
Dynamic field rendering
Client-heavy logic
Problem:

❌ Logic scattered
❌ Hard to scale
❌ Not enterprise-grade

Phase 2 (Improved)
Introduced rule engine
Added backend validation
Introduced DAG for calculations
Improvement:

✔ Better structure
✔ Deterministic behavior

Phase 3 (Final Design)

Shift from:

“Dynamic form builder”

To:

Relational data platform + rule engine + UI renderer

🧱 4. Final Architecture
Frontend (React)
   ↓
API Layer
   ↓
Rule Engine (deterministic)
   ↓
SQL Server (source of truth)
   ↓
Projection Worker → Graph DB (Neo4j)
🧩 5. Key Design Decisions (What + Why)
5.1 SQL Server as Primary Database

SQL Server 2025

What:
Stores all records, relationships, audit logs
Why:
Strong consistency (ACID)
Mature ecosystem
JSON support + relational power
5.2 Neo4j as Secondary (Projection)
What:
Read-only graph projection
Why:
Efficient traversal (impact analysis)
Avoids complex SQL joins for deep relationships
Critical Rule:

👉 SQL = source of truth
👉 Neo4j = derived, eventually consistent

5.3 Multi-Entity Model
What:

Separate entities:

Policy
Control
Test
Issue
Why:
Avoids massive forms
Enables reuse
Matches domain reality
5.4 Relationship Table (Not Nested JSON)
What:
record_relations
Why:
Supports many-to-many
Enables graph modeling
Avoids duplication
5.5 Rule Engine (Core System Brain)
What:
JSON-based DSL
Pure function execution
Why:
Predictable
Safe
Auditable
5.6 No Scripting / No eval
What:
No dynamic JS execution
Why:
Security
Debuggability
Prevents platform collapse
5.7 Aggregation Rules (Server-Side Only)
What:
Cross-entity calculations

Example:

Control effectiveness = avg(Test scores)
Why:
Needs full dataset
Must be consistent
5.8 Isomorphic Execution (Optional Optimization)
What:
Same rule engine on client + server
Why:
Instant UI feedback
Server remains authoritative
5.9 Optimistic Concurrency
What:
Version-based updates
Why:
Prevents overwrites
Supports multi-user editing
5.10 Audit Log
What:
Track all changes
Why:
Compliance
Debugging
Historical traceability
5.11 Avoid Event Sourcing (Initially)
What:
Use simple audit log instead
Why:
80% value with 20% complexity
Event sourcing adds heavy operational burden
🔗 6. How Complex Forms Actually Work
Key Insight

There is NO “big nested form”

Instead:

Control Page =
  Control record
  + related Test records
  + computed aggregations
Flow Example
Load:
Fetch Control
Fetch related Tests
Compute aggregations
Return composed response
Update:
Save Test
Find parent Control
Recompute aggregation
Update Control
Why this works
Decoupled entities
Independent lifecycle
Scales to large systems
🧮 7. Rule Engine Design
DSL (JSON)
{
  "*": ["likelihood", "impact"]
}
Supported Concepts
arithmetic
comparison
conditionals
aggregation (server-side)
Why JSON DSL
safe
serializable
versionable
language-independent
🧠 8. Separation of Concerns
Layer	Responsibility
UI	Rendering only
API	orchestration
Rule Engine	logic
SQL	persistence
Neo4j	graph queries
⚠️ 9. What We Explicitly Avoid
❌ Frontend business logic

Why: causes drift

❌ Giant monolithic forms

Why: unmanageable

❌ Dual writes (SQL + Neo4j)

Why: inconsistency

❌ Scripting engines

Why: un-debuggable

❌ Over-engineering early (CQRS/Event sourcing)

Why: slows delivery

📊 10. Performance Strategy
Lazy load tabs
Pagination for related entities
Incremental aggregation (future)
Config caching
🔍 11. Observability
Every rule execution returns trace

Why:

debugging
explainability
audit support
🔐 12. Versioning Strategy

Each record stores:

{
  "config_version": "v1"
}
Why:
prevents silent behavior changes
ensures historical correctness
🧠 13. Final Mental Model
❌ Wrong Model

“Dynamic form builder”

✅ Correct Model

Relational data platform + rule engine + composable UI

🚀 14. What Makes This “100-Year Architecture”

This system survives because it is:

✔ Explicit

No hidden behavior

✔ Deterministic

Same input → same output

✔ Layered

Each part replaceable

✔ Boring (in a good way)

No trendy dependencies

🔥 Final Takeaway

The biggest shift in thinking:

You are not building forms
You are building a data + rules platform

✅ If You Follow This Exactly

You will get:

Archer-level capability
Maintainable system
Scalable architecture
Debuggable behavior