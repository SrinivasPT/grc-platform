# Module 09 — Audit Log & Versioning

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model)

---

## 1. Purpose

The audit log and versioning system provides a **complete, immutable history** of every change made to every record in the platform. It is not an optional feature — it is a core compliance requirement. Regulations including SOX, HIPAA, ISO 27001, and GDPR require demonstrable evidence of change control, authorization trails, and data integrity.

This module defines: what gets logged, how snapshots are captured, how version conflicts are resolved, how historical records are retrieved, and how long data is retained.

---

## 2. Audit Log Design

### 2.1 What Is Logged

| Category | Events Logged |
|----------|--------------|
| Record lifecycle | create, update, delete (soft), restore |
| Field value changes | every field change with before/after values |
| Relationship changes | link created, link removed |
| Workflow transitions | state change, task completion, SLA breach, escalation |
| Authentication | login, logout, login failed, session revoked, API key used |
| Configuration changes | application created/updated, field definition changed, rule changed, layout changed |
| User management | user invited, role assigned, role revoked, user deactivated |
| Attachment events | file uploaded, file downloaded, file deleted |
| Permission changes | role permissions updated, access rule changed |

### 2.2 Audit Log Table (Append-Only)

```sql
-- All columns already defined in Module 02; re-stated here for context
CREATE TABLE audit_log (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID(),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_time      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    user_id         UNIQUEIDENTIFIER  NULL,
    entity_type     NVARCHAR(100)     NOT NULL,
    entity_id       UNIQUEIDENTIFIER  NOT NULL,
    action          NVARCHAR(50)      NOT NULL,
    old_value       NVARCHAR(MAX)     NULL,    -- JSON snapshot of before state
    new_value       NVARCHAR(MAX)     NULL,    -- JSON snapshot of after state
    ip_address      NVARCHAR(45)      NULL,
    session_id      NVARCHAR(200)     NULL,
    correlation_id  UNIQUEIDENTIFIER  NULL,    -- groups related events in one operation
    PRIMARY KEY (id, event_time)
)
ON [AuditPartitionScheme];  -- monthly partitions
```

**Immutability enforcement:**
- The application DB user has INSERT permissions only on `audit_log`
- No UPDATE or DELETE permissions granted to any application role
- SQL Server RLS prevents all modifications
- Periodic hash-chaining ensures tamper evidence (see section 2.5)

### 2.3 Audit Event Structure

Every audit event captures a structured snapshot — not just a description:

```json
// Field value change example
{
  "entity_type":  "field_value",
  "entity_id":    "record-uuid",
  "action":       "update",
  "correlation_id": "operation-uuid",
  "old_value": {
    "field_key":  "risk_rating",
    "field_name": "Risk Rating",
    "value":      "Medium"
  },
  "new_value": {
    "field_key":  "risk_rating",
    "field_name": "Risk Rating",
    "value":      "High"
  }
}
```

```json
// Workflow transition example
{
  "entity_type":  "workflow_instance",
  "entity_id":    "instance-uuid",
  "action":       "state_change",
  "new_value": {
    "from_state":      "draft",
    "to_state":        "in_review",
    "transition_key":  "submit_for_review",
    "actor_user_id":   "user-uuid",
    "comment":         null,
    "duration_in_state_seconds": 86400
  }
}
```

```json
// Config change example
{
  "entity_type":  "field_definition",
  "entity_id":    "field-def-uuid",
  "action":       "update",
  "new_value": {
    "changed_fields": ["is_required", "display_order"],
    "config_version_before": 3,
    "config_version_after":  4
  }
}
```

### 2.4 Correlation ID Pattern

When a single user action triggers multiple audit events (e.g., saving a record changes 5 fields), all events share the same `correlation_id`. This allows the audit log viewer to group and collapse related changes:

```java
// AuditContext thread-local
@Component
public class AuditContext {
    private static final ThreadLocal<UUID> correlationId = new ThreadLocal<>();

    public static UUID begin() {
        UUID id = UUID.randomUUID();
        correlationId.set(id);
        return id;
    }

    public static UUID current() { return correlationId.get(); }
    public static void clear()   { correlationId.remove(); }
}
```

### 2.5 Tamper Evidence — Hash Chaining

Every 1000 rows (configurable batch), a SHA-256 hash of all preceding rows in the batch is computed and stored in a `audit_chain_hashes` table. This allows detection of any row deletion or modification:

```sql
CREATE TABLE audit_chain_hashes (
    batch_id        BIGINT           NOT NULL PRIMARY KEY IDENTITY,
    org_id          UNIQUEIDENTIFIER NOT NULL,
    batch_start_id  UNIQUEIDENTIFIER NOT NULL,
    batch_end_id    UNIQUEIDENTIFIER NOT NULL,
    row_count       INT              NOT NULL,
    chain_hash      NCHAR(64)        NOT NULL,  -- SHA-256 of batch rows + previous hash
    prev_hash       NCHAR(64)        NULL,       -- NULL for first batch
    created_at      DATETIME2        NOT NULL DEFAULT SYSUTCDATETIME()
);
```

A background job runs daily to verify chain integrity and alerts if tampering is detected.

---

## 3. Record Versioning

### 3.1 Optimistic Concurrency

Every record carries a `version` integer. On update, the caller must provide the current version. If the version in the database differs from the provided version, the update is rejected with `HTTP 409 Conflict`:

```java
public Record updateRecord(UpdateRecordInput input) {
    int rowsUpdated = recordRepository.updateWithVersionCheck(
        input.id(), input.version(), updatedFields, currentUserId
    );
    if (rowsUpdated == 0) {
        // Either record doesn't exist, wrong tenant, or version conflict
        Record current = recordRepository.findById(input.id())
            .orElseThrow(() -> new NotFoundException(input.id()));
        throw new ConcurrencyConflictException(
            "Record was modified by another user. Current version: " + current.getVersion()
        );
    }
}
```

### 3.2 Record Snapshots

In addition to the field-level audit log, complete record snapshots are captured at key points:
- On workflow state transition
- On every save (configurable — may be disabled for high-frequency applications)
- On explicit "lock version" action (for compliance checkpoints)

```sql
CREATE TABLE record_versions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    version_number  INT               NOT NULL,
    snapshot        NVARCHAR(MAX)     NOT NULL,  -- JSON: full record + all field values
    trigger_event   NVARCHAR(100)     NOT NULL,  -- 'save','workflow_transition','manual'
    trigger_state   NVARCHAR(100)     NULL,       -- workflow state at snapshot time
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_rv_record_version UNIQUE (record_id, version_number)
);
CREATE INDEX idx_rv_record ON record_versions(record_id, version_number);
```

### 3.3 Snapshot JSON Structure

```json
{
  "record": {
    "id":           "uuid",
    "recordNumber": 42,
    "displayName":  "Data exfiltration via phishing",
    "status":       "active",
    "workflowState": "in_review",
    "version":      5
  },
  "fieldValues": {
    "title":        "Data exfiltration via phishing",
    "category":     "IT Security",
    "likelihood":   4,
    "impact":       5,
    "risk_score":   20,
    "risk_rating":  "Critical",
    "owner":        { "id": "user-uuid", "name": "Jane Doe" },
    "due_date":     "2026-09-30"
  },
  "computedValues": {
    "risk_score":   20,
    "risk_rating":  "Critical"
  },
  "snapshotAt": "2026-04-04T10:00:00Z"
}
```

---

## 4. Audit Log API

### 4.1 GraphQL

```graphql
type Query {
  auditHistory(
    entityId:   UUID!
    entityType: AuditEntityType
    action:     AuditAction
    page:       PageInput
  ): AuditPage!

  recordVersions(recordId: UUID!): [RecordVersionSummary!]!
  recordVersion(recordId: UUID!, version: Int!): RecordVersionDetail!
  compareVersions(recordId: UUID!, v1: Int!, v2: Int!): VersionDiff!
}

type AuditEvent {
  id:           UUID!
  eventTime:    DateTime!
  user:         User
  entityType:   String!
  entityId:     UUID!
  action:       String!
  oldValue:     JSON
  newValue:     JSON
  correlationId: UUID
}

type AuditPage {
  items:      [AuditEvent!]!
  totalCount: Int!
  hasNext:    Boolean!
}

type VersionDiff {
  fields: [FieldDiff!]!
}

type FieldDiff {
  fieldKey:   String!
  fieldName:  String!
  v1Value:    JSON
  v2Value:    JSON
  changed:    Boolean!
}

enum AuditEntityType { RECORD FIELD_VALUE WORKFLOW ATTACHMENT CONFIGURATION AUTH USER }
enum AuditAction     { CREATE UPDATE DELETE STATE_CHANGE LOGIN LOGOUT TRANSITION }
```

---

## 5. Compliance Reports from Audit Log

The audit log powers several compliance-critical reports:

| Report | Query Pattern |
|--------|--------------|
| Who accessed record X? | `WHERE entity_id = X AND action IN ('read','update')` |
| What changed on record X between date A and B? | `WHERE entity_id = X AND event_time BETWEEN A AND B` |
| All changes made by user Y this quarter | `WHERE user_id = Y AND event_time >= QStart` |
| All config changes in last 30 days | `WHERE entity_type = 'field_definition' AND event_time >= NOW-30d` |
| All role permission changes | `WHERE entity_type IN ('role','role_permission')` |
| SLA breach summary | Join with `workflow_instances.is_sla_breached = 1` |

---

## 6. Retention Policy

Data retention is configurable per organization:

| Data | Default Retention | Minimum Regulatory |
|------|-----------------|-------------------|
| Audit log | 7 years | SOX: 7 years |
| Record versions | 5 years | ISO 27001: 3 years |
| Workflow history | 5 years | varies |
| Auth events | 1 year | Industry: 1 year |

Expired records are archived (written to cold storage) rather than deleted. The archive is queryable via a separate `archive_audit_log` table using the same schema.

Archival runs as a scheduled quarterly batch job:

```java
@Scheduled(cron = "0 0 2 1 */3 *")  // 2 AM on first day of every quarter
public void archiveExpiredAuditRecords() {
    Instant cutoff = Instant.now().minus(retentionPeriod);
    auditArchiveService.archiveBefore(cutoff);
}
```

---

## 7. Audit Service — Java Implementation

```java
@Service
public class AuditService {

    public void logRecordCreate(UUID orgId, UUID recordId, UUID userId,
                                Map<String, Object> fieldValues) {
        audit(orgId, "record", recordId, "create", null, fieldValues, userId);
    }

    public void logFieldChange(UUID orgId, UUID recordId, String fieldKey,
                               String fieldName, Object oldVal, Object newVal, UUID userId) {
        audit(orgId, "field_value", recordId, "update",
              Map.of("field_key", fieldKey, "field_name", fieldName, "value", oldVal),
              Map.of("field_key", fieldKey, "field_name", fieldName, "value", newVal),
              userId);
    }

    public void logWorkflowTransition(UUID orgId, UUID instanceId,
                                      String fromState, String toState,
                                      String transitionKey, UUID actorId, String comment) {
        audit(orgId, "workflow_instance", instanceId, "state_change", null,
              Map.of("from_state", fromState, "to_state", toState,
                     "transition_key", transitionKey, "comment", comment),
              actorId);
    }

    private void audit(UUID orgId, String entityType, UUID entityId, String action,
                       Object oldValue, Object newValue, UUID userId) {
        var entry = AuditLogEntry.builder()
            .orgId(orgId).entityType(entityType).entityId(entityId)
            .action(action)
            .oldValue(oldValue != null ? toJson(oldValue) : null)
            .newValue(newValue != null ? toJson(newValue) : null)
            .userId(userId)
            .correlationId(AuditContext.current())
            .ipAddress(RequestContext.currentIpAddress())
            .sessionId(RequestContext.currentSessionId())
            .build();
        auditLogRepository.insert(entry);  // synchronous, same transaction as the triggering action
    }
}
```

**Important:** Audit log writes happen **in the same transaction** as the triggering action. If the action rolls back, the audit log entry also rolls back. There is no scenario where an action succeeds without an audit entry.

---

## 8. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should read access be logged? (Creates high volume — 10x the write audit volume) | High |
| 2 | Should record snapshots be stored compressed? Large text fields make snapshots very large. | Medium |
| 3 | Archive storage: local cold disk vs Azure Blob cold tier vs S3 Glacier? | Medium |
| 4 | Should the audit chain hash verification run automatically on a schedule and alert? | High |
| 5 | GDPR right-to-erasure: how to handle when a user requests deletion of their data from audit logs? | High — legal question |

---

*Previous: [08 — Workflow Engine](08-workflow-engine.md) | Next: [10 — Notification Engine](10-notification-engine.md)*
