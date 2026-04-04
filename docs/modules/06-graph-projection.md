# Module 06 — Graph Projection (Neo4j)

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model), SQL Server Change Tracking

---

## 1. Purpose

This module defines the Neo4j graph layer — its structure, synchronization mechanism, query patterns, and the boundaries of what it is and is not responsible for. Neo4j serves as a **derived read model** for relationship-intensive queries that are expensive or unreadable in SQL.

**Core Rule:**
- SQL Server = source of truth. Write here only.
- Neo4j = derived projection. Read from here for graph traversal. Never write directly from application code.

---

## 2. Why Neo4j (and When to Use It)

### Use Cases Where Neo4j Excels

| Use Case | Why SQL Fails | Why Graph Wins |
|----------|--------------|---------------|
| Impact analysis: "What risks are affected if this control fails?" | Requires recursive CTEs across 5+ joins | Single multi-hop Cypher traversal |
| Framework mapping: "Show me all controls mapped to NIST SP 800-53 AC-1" | Cross-app join through multiple relationship tables | Pattern match: `(:Control)-[:MAPPED_TO]->(:Requirement)` |
| Dependency chains: "What processes depend on this vendor?" | Self-referencing SQL is complex | Native graph traversal |
| Relationship visualization: Force-directed network graphs | Cannot efficiently retrieve neighborhood subgraphs | Subgraph projection in Cypher |
| Circular dependency detection in control chains | Expensive SQL recursion | Native cycle detection |
| Shortest path: "How is this risk connected to this regulation?" | Dijkstra in SQL is impractical | `shortestPath()` in Cypher |

### Use Cases Where SQL Remains the Right Choice

- All record mutations (create, update, delete)
- Audit log queries
- Filtered list queries with simple criteria
- Aggregation over field values
- Workflow state queries
- Full-text search (unless augmented by search module)

---

## 3. Graph Data Model

### 3.1 Node Types

Every GRC record is projected as a node in Neo4j with:
- A label derived from the `application.internal_key` (e.g., `:Risk`, `:Control`, `:Policy`)
- A `+GrcRecord` label on all nodes for cross-type querying
- A minimal set of read-optimized properties (not all field values — only indexed/key ones)

```cypher
// Node structure example
(:Risk:GrcRecord {
  id:            "uuid",
  orgId:         "uuid",
  recordNumber:  42,
  displayName:   "Data exfiltration via phishing",
  status:        "active",
  workflowState: "in_review",
  appKey:        "risk",
  computedScore: 12.0,        // from records.computed_values (key fields only)
  ratingLabel:   "High",
  updatedAt:     datetime("2026-04-04T10:00:00Z")
})
```

### 3.2 Relationship Types

Relationships in Neo4j mirror the `record_relations` table. The `relation_type` maps directly to Neo4j relationship types:

| SQL `relation_type` | Neo4j Relationship | Direction |
|--------------------|--------------------|-----------|
| `risk_controls` | `[:MITIGATED_BY]` | `(Risk)→(Control)` |
| `control_tests` | `[:TESTED_BY]` | `(Control)→(Test)` |
| `policy_controls` | `[:GOVERNED_BY]` | `(Control)→(Policy)` |
| `policy_requirements` | `[:MAPS_TO]` | `(Policy)→(ComplianceRequirement)` |
| `risk_issues` | `[:RAISED_AS]` | `(Risk)→(Issue)` |
| `control_issues` | `[:RAISED_AS]` | `(Control)→(Issue)` |
| `vendor_risks` | `[:INTRODUCES]` | `(Vendor)→(Risk)` |
| `asset_controls` | `[:PROTECTED_BY]` | `(Asset)→(Control)` |
| `incident_controls` | `[:VIOLATED]` | `(Incident)→(Control)` |
| `process_vendor` | `[:DEPENDS_ON]` | `(Process)→(Vendor)` |

Relationships carry `relationType`, `createdAt`, and any `metadata` properties from the SQL `record_relations` table.

### 3.3 Auxiliary Nodes

```cypher
// Organization node (top-level partition)
(:Organization { id: "uuid", slug: "org-slug" })

// User node (for ownership and assignment queries)
(:User:GrcRecord { id: "uuid", email: "...", displayName: "..." })

// Compliance Framework and Requirement nodes (hierarchy)
(:ComplianceFramework { id: "uuid", name: "NIST SP 800-53", version: "Rev5" })
(:ComplianceRequirement { id: "uuid", code: "AC-1", title: "...", frameworkId: "uuid" })

// Connections
(:Organization)-[:CONTAINS]->(:GrcRecord)
(:User)-[:OWNS]->(:GrcRecord)
(:ComplianceFramework)-[:CONTAINS]->(:ComplianceRequirement)
```

---

## 4. Synchronization Architecture

### 4.1 Overall Flow

```
SQL Server 2025
  └── Change Tracking enabled on:
        records, record_relations, field_values_reference

                    │
                    │ Polled every 2–5 seconds
                    ▼
          ┌─────────────────────┐
          │  Projection Worker  │  (Spring Batch / Virtual Thread)
          │  (platform-graph)   │
          └─────────┬───────────┘
                    │
          ┌─────────▼───────────┐
          │  Change Processor   │
          │  - Classify change  │
          │  - Build Cypher cmd │
          └─────────┬───────────┘
                    │ Bolt protocol
                    ▼
               Neo4j 5.x LTS
```

### 4.2 Change Tracking Polling

The Projection Worker polls SQL Server Change Tracking at a configurable interval (default: 2 seconds):

```java
@Scheduled(fixedDelayString = "${grc.graph.sync.interval-ms:2000}")
public void syncChanges() {
    long lastSyncVersion = syncStateRepository.getLastSyncVersion(orgId);
    long currentVersion  = changeTrackingRepository.getCurrentVersion();

    if (currentVersion == lastSyncVersion) return; // nothing to sync

    List<TrackedChange> changes = changeTrackingRepository
        .getChangesSince(lastSyncVersion, trackedTables);

    for (TrackedChange change : changes) {
        changeProcessor.process(change);
    }

    syncStateRepository.updateLastSyncVersion(orgId, currentVersion);
}
```

### 4.3 Change Types and Processing

| SQL Event | Affected Table | Neo4j Action |
|-----------|---------------|--------------|
| INSERT into `records` | records | `CREATE (n:AppKey:GrcRecord {...})` |
| UPDATE on `records` | records | `MATCH (n {id:$id}) SET n += $props` |
| DELETE on `records` (soft) | records | `MATCH (n {id:$id}) SET n.status='deleted'` |
| INSERT into `record_relations` | record_relations | `MATCH (a),(b) CREATE (a)-[:TYPE]->(b)` |
| DELETE from `record_relations` | record_relations | `MATCH ()-[r:TYPE {id:$id}]-() DELETE r` |
| UPDATE on `field_values_reference` | field_values_reference | Re-evaluate owner/role projections |

> **Soft Delete — Relationship Preservation:** When a record is soft-deleted, its Neo4j node is set to `status='deleted'` but its relationships are **not removed**. This preserves historical impact analysis (with `includeDeleted: true`). All standard queries must exclude deleted nodes globally.

### 4.3a Soft Delete Global Query Interceptor

All Cypher queries issued by `GraphQueryService` are subject to a global filter that excludes deleted nodes. This is enforced by a `CypherQueryInterceptor` that rewrites queries:

```java
@Component
public class CypherQueryInterceptor {
    // Wraps all MATCH clauses to exclude deleted nodes by default
    // Equivalent to appending:
    //   WHERE (n.status IS NULL OR n.status <> 'deleted')
    // Can be bypassed by passing includeDeleted = true (for audit/historical queries)
    public String applyDefaultFilters(String cypher, boolean includeDeleted) {
        if (includeDeleted) return cypher;
        return CypherRewriter.appendStatusFilter(cypher, "status", "deleted");
    }
}
```

All graph API endpoints that return traversal results accept an optional `includeDeleted: Boolean = false` parameter to enable historical analysis.

### 4.4 Sync State Tracking

```sql
-- In SQL Server (managed by Projection Worker)
CREATE TABLE graph_sync_state (
    org_id           UNIQUEIDENTIFIER NOT NULL,
    table_name       NVARCHAR(100)     NOT NULL,
    last_sync_version BIGINT           NOT NULL DEFAULT 0,
    last_sync_at     DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (org_id, table_name)
);
```

### 4.5 Full Rebuild / Reconciliation

A complete graph rebuild is triggered:
- On first deployment (empty graph)
- On schema migration that changes projection logic
- On manual admin request (via API)

```java
@PostMapping("/api/v1/admin/graph/rebuild")
public ResponseEntity<JobStatus> triggerRebuild(@RequestBody RebuildOptions opts) {
    // Async: truncate org nodes → re-project all records → re-project all relations
    UUID jobId = graphRebuildService.startRebuild(opts.orgId(), opts.fullReset());
    return ResponseEntity.accepted().body(new JobStatus(jobId, "queued"));
}
```

---

## 5. Cypher Query Patterns

### 5.1 Impact Analysis: Controls Failing → Affected Risks

```cypher
// If this control is ineffective, which risks lose coverage?
MATCH (c:Control {id: $controlId, orgId: $orgId})
MATCH (r:Risk)-[:MITIGATED_BY]->(c)
RETURN r.id, r.displayName, r.computedScore, r.ratingLabel
ORDER BY r.computedScore DESC
```

### 5.2 Multi-Hop: Risks Exposed by a Vendor

```cypher
// A vendor failure cascades through: Vendor → Process → Control → Risk
MATCH (v:Vendor {id: $vendorId, orgId: $orgId})
MATCH path = (v)-[:PROVIDES_TO]->(:Process)-[:DEPENDS_ON*1..3]->(:Control)<-[:MITIGATED_BY]-(r:Risk)
RETURN DISTINCT r.id, r.displayName, r.ratingLabel, length(path) AS hops
ORDER BY hops, r.computedScore DESC
LIMIT 100
```

### 5.3 Compliance Coverage: Framework → Requirements → Controls

```cypher
// For each requirement in NIST, how many controls are mapped?
MATCH (f:ComplianceFramework {name: 'NIST SP 800-53', orgId: $orgId})
MATCH (f)-[:CONTAINS]->(req:ComplianceRequirement)
OPTIONAL MATCH (c:Control)-[:MAPS_TO]->(req)
RETURN req.code, req.title,
       count(c) AS controlCount,
       collect(c.displayName)[..3] AS sampleControls
ORDER BY req.code
```

### 5.4 Shortest Path: Connection Between Two Records

```cypher
MATCH (a:GrcRecord {id: $sourceId}), (b:GrcRecord {id: $targetId})
MATCH path = shortestPath((a)-[*..10]-(b))
WHERE all(n IN nodes(path) WHERE n.orgId = $orgId)
RETURN [n IN nodes(path) | {id: n.id, label: labels(n)[0], name: n.displayName}] AS pathNodes,
       [r IN relationships(path) | type(r)] AS relationTypes
```

### 5.5 Neighborhood Subgraph (for Visualization)

```cypher
// Return a record and its immediate neighbors (1 hop) for force graph rendering
MATCH (center:GrcRecord {id: $recordId, orgId: $orgId})
MATCH (center)-[r]-(neighbor:GrcRecord)
RETURN center, collect({node: neighbor, rel: type(r), direction: startNode(r).id = $recordId}) AS neighbors
LIMIT 50
```

---

## 6. Neo4j Schema — Indexes and Constraints

```cypher
// Uniqueness constraints
CREATE CONSTRAINT grc_record_id FOR (n:GrcRecord) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT org_id_slug   FOR (n:Organization) REQUIRE n.slug IS UNIQUE;
CREATE CONSTRAINT user_id       FOR (n:User) REQUIRE n.id IS UNIQUE;

// Lookup indexes (tenant-aware)
CREATE INDEX grc_record_org    FOR (n:GrcRecord) ON (n.orgId);
CREATE INDEX grc_record_app    FOR (n:GrcRecord) ON (n.orgId, n.appKey);
CREATE INDEX grc_record_status FOR (n:GrcRecord) ON (n.orgId, n.status);
CREATE INDEX grc_record_score  FOR (n:GrcRecord) ON (n.orgId, n.computedScore);

// Full-text index for graph-based search
CREATE FULLTEXT INDEX grc_record_search FOR (n:GrcRecord) ON EACH [n.displayName];
```

### 6.1 Graph Schema Versioning

When new node properties or relationship types are added during platform upgrades, the graph schema must be versioned to ensure safe migration.

Every `Organization` node carries a `graphSchemaVersion` property:

```cypher
MERGE (o:Organization {orgId: $orgId})
SET o.graphSchemaVersion = 4   // bump on each schema migration
```

The `GraphProjectionWorker` checks `graphSchemaVersion` on startup and runs any pending Cypher migration scripts in order:

```java
@PostConstruct
public void migrateSchema() {
    int current = graphSchemaRepository.getSchemaVersion(orgId);
    for (GraphSchemaMigration m : migrations) {
        if (m.version() > current) {
            neo4jTemplate.APPLY(m.cypher());
            graphSchemaRepository.updateSchemaVersion(orgId, m.version());
        }
    }
}
```

Migration scripts are versioned files in `resources/graph-migrations/v{N}.cypher`. This mirrors the Liquibase approach used for SQL Server but adapted to Cypher. A full Neo4j rebuild (wipe and re-project from SQL Server) always produces the current schema version.

---



### 7.1 Project Module: `platform-graph`

```
com.grc.graph
├── ProjectionWorker.java          // Scheduled sync driver
├── ChangeTrackingRepository.java  // Reads SQL Server change tracking
├── ChangeProcessor.java           // Routes changes to correct handler
├── handlers/
│   ├── RecordNodeHandler.java     // CREATE/UPDATE/DELETE nodes
│   └── RelationHandler.java       // CREATE/DELETE relationships
├── neo4j/
│   ├── GrcNode.java               // Spring Data Neo4j entity
│   ├── GrcRelationship.java       // Spring Data Neo4j relationship entity
│   ├── GraphRepository.java       // CRUD via Spring Data Neo4j
│   └── GraphQueryService.java     // Complex Cypher queries (custom)
├── rebuild/
│   ├── GraphRebuildService.java   // Full org rebuild logic
│   └── RebuildJobRepository.java
└── api/
    └── GraphQueryController.java  // GraphQL resolver: impact analysis, paths, neighborhood
```

### 7.2 GraphQL Integration

```graphql
type Query {
  impactAnalysis(recordId: UUID!): ImpactAnalysisResult!
  relationshipPath(sourceId: UUID!, targetId: UUID!): PathResult
  neighborhoodGraph(recordId: UUID!, depth: Int): GraphData!
  complianceCoverage(frameworkId: UUID!): [RequirementCoverage!]!
}

type ImpactAnalysisResult {
  sourceRecord:     RecordSummary!
  affectedRecords:  [AffectedRecord!]!
  totalRiskScore:   BigDecimal
}

type AffectedRecord {
  record:   RecordSummary!
  hops:     Int!
  pathTypes: [String!]!
}

type PathResult {
  found:        Boolean!
  pathNodes:    [PathNode!]!
  relationTypes: [String!]!
}

type GraphData {
  nodes: [GraphNode!]!
  edges: [GraphEdge!]!
}
```

---

## 8. Consistency and Staleness

### 8.0 Two-Tier Architecture Principle (Restored from v2.0 elimination)

| Query Type | Data Source | Consistency | Rationale |
|------------|-------------|-------------|-----------|
| Single-record lookups, 1-hop relations | SQL Server | Immediate (synchronous) | Low latency; no eventual-consistency UX issues |
| Multi-hop traversal (depth ≥ 2), impact analysis, shortest-path, compliance coverage | Neo4j | Eventual (< 5 seconds typical) | SQL recursive CTEs become O(n·m) joins; Neo4j traversal is O(log n) |
| All writes, calculations, validations, workflow conditions | SQL Server | Synchronous (always) | Neo4j is derived — never the source of truth |

> **Rule:** if a query can be answered with a single `JOIN` or `EXISTS` in SQL Server, use SQL Server. If it requires traversing more than 1 hop, use Neo4j. This line prevents accidental coupling of business logic to the graph store.

### 8.1 Bounded Staleness

Neo4j data is eventually consistent with SQL Server. Under normal operations:
- Default polling interval: 2 seconds
- Maximum expected staleness: < 5 seconds

### 8.2 Cache-Aside Pattern for Graph Queries

Graph queries always return results with a `dataAsOf` timestamp. The client displays this:
> "Relationship data as of 3 seconds ago"

### 8.3 Important Invariant

**Never verify business logic using Neo4j data.** All compliance checks, workflow conditions, and validation rules use SQL Server. Neo4j is used **only** for relationship traversal and visualization.

---

## 9. Operational Considerations

### 9.1 Neo4j Configuration (Production)

```
dbms.memory.heap.initial_size=2G
dbms.memory.heap.max_size=4G
dbms.memory.pagecache.size=4G
dbms.connector.bolt.listen_address=0.0.0.0:7687
dbms.security.auth_enabled=true
dbms.ssl.policy.bolt.enabled=true
```

### 9.2 Backup Strategy

- Neo4j Online Backup: daily; retained 7 days
- Rebuild from SQL Server: always possible (Neo4j is derived data)
- If Neo4j is unavailable: graph features degrade gracefully; all other platform features continue working

### 9.3 Graceful Degradation

If the Neo4j connection is unavailable:
- Impact analysis endpoints return `503 Service Unavailable` with descriptive message
- All record CRUD, workflow, and reporting continues unaffected
- Projection Worker queues changes in-memory (bounded, configurable size) for replay when connection restores

### 9.4 Eventual Consistency — UI/UX Gap

The graph sync is **asynchronous** with a typical staleness of < 5 seconds under normal load. When a user creates a relationship (e.g., Risk → Control) and immediately navigates to the Impact Graph view, the new relationship may not yet appear in Neo4j.

**Strategy: Sync-Pending Flag + Optimistic Client Rendering**

1. **API response includes sync metadata:** Every mutation that creates/deletes a relationship returns a `graphSyncPending: Boolean` flag:

```graphql
type RelationshipMutationResult {
  relation:        RecordRelation!
  graphSyncPending: Boolean!  # true if Neo4j has not yet confirmed the change
}
```

2. **React UI implements optimistic graph rendering:** When `graphSyncPending = true`, the graph visualization component immediately renders the new relationship client-side without waiting for the Neo4j query response. The node is shown with a "syncing..." indicator.

3. **Polling for confirmation:** The graph component polls `graphSyncStatus(relationId: UUID!)` every 2 seconds until `synced = true` (maximum 30 seconds), then replaces the optimistic node with the confirmed graph data.

4. **Fallback:** If Neo4j fails to acknowledge sync within 30 seconds (e.g., service unavailable), the UI shows a toast: *"Impact graph is temporarily unavailable. Your changes are saved and will appear shortly."*

This ensures the SQL Server data (source of truth) is always reflected correctly in the UI, even when Neo4j is briefly behind.

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | ~~Neo4j Community Edition vs Enterprise Edition?~~ | High | **Resolved:** Neo4j **Enterprise Edition** for production (clustering, online backup, role-based access). Neo4j Community Edition for development and CI. |
| 2 | ~~Should each org have its own Neo4j database?~~ | High | **Resolved:** Single database with `orgId` property isolation (single bank). EE database-per-org feature not required. |
| 3 | How to handle very large organizations (millions of records, tens of millions of relationships)? Sharding? | Future | |
| 4 | Should the Projection Worker use Spring Batch (for restartable jobs) or a simpler scheduled thread? | Medium | |
| 5 | ~~Message queue vs direct polling for change propagation?~~ | Medium | **Resolved for MVP:** SQL Server Change Tracking (direct polling, 2 second interval). **Future:** Consider Debezium + Kafka CDC for higher write volumes or when staleness SLA tightens below 1 second. Kafka adds operational complexity not justified at initial scale. |

---

*Previous: [05 — Form & Layout Engine](05-form-layout-engine.md) | Next: [07 — Auth & Access Control](07-auth-access-control.md)*
