# Module 14 — Integration Framework

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02, Module 04 (API Layer), Module 07 (Auth)

---

## 1. Purpose

The Integration Framework enables the GRC platform to exchange data with external systems — vulnerability scanners, SIEM tools, ticketing systems (Jira, ServiceNow), identity providers, asset management tools, and custom enterprise systems. It provides structured inbound/outbound integration, import/export, and a connector model that can be extended with new integrations without modifying core platform code.

---

## 2. Integration Directions

| Direction | Description | Examples |
|-----------|-------------|---------|
| **Inbound** | External systems push data into the GRC platform | Vulnerability scanner pushes new vulnerabilities as risk records |
| **Outbound webhooks** | Platform notifies external systems when events occur | Jira ticket created when a GRC issue is opened |
| **Outbound pull** | External systems pull GRC data via API | BI tool pulling risk data for analytics |
| **Import** | Bulk data loaded via file (CSV, JSON) | Importing asset inventory from Excel |
| **Export** | GRC data extracted to a file | Monthly risk register export to Excel |

---

## 3. Outbound Webhooks

Webhooks are configured per organization. Already partially defined in Module 10 (Notification Engine). Here the focus is on integration-specific webhooks (not just notifications):

### 3.1 Webhook Configuration

```sql
-- Reuses webhook_endpoints table from Module 10
-- Additional event subscriptions table:
CREATE TABLE webhook_subscriptions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    endpoint_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES webhook_endpoints(id),
    event_type      NVARCHAR(200)     NOT NULL,   -- e.g. 'record.created', 'workflow.approved'
    app_key         NVARCHAR(100)     NULL,        -- scope to specific application (null = all)
    filter_dsl      NVARCHAR(MAX)     NULL,        -- Rule Engine DSL: only fire if condition is true
    is_active       BIT               NOT NULL DEFAULT 1
);
```

### 3.2 Event Types

| Event Type | Triggered When |
|-----------|----------------|
| `record.created` | New GRC record created |
| `record.updated` | Record fields updated |
| `record.deleted` | Record soft-deleted |
| `workflow.state_changed` | Workflow state transitions |
| `workflow.task_assigned` | Task assigned to user |
| `workflow.approved` | Record approved |
| `workflow.rejected` | Record rejected |
| `sla.breached` | Workflow SLA breached |
| `field.changed:{field_key}` | Specific field value changed |
| `attachment.uploaded` | File attached to record |

### 3.3 Webhook Delivery and Retry

Webhooks use the same Transactional Outbox pattern as the Notification Engine. Retry schedule:

| Attempt | Delay |
|---------|-------|
| 1 | Immediate |
| 2 | 1 minute |
| 3 | 5 minutes |
| 4 | 30 minutes |
| 5 | 2 hours |

After 5 failures, the webhook subscription is auto-disabled and the integration admin is notified.

---

## 4. Connector Framework

Connectors are predefined integration adapters for common third-party systems. Each connector has:
- An inbound handler (for data coming in)
- An outbound handler (for data going out)
- A configuration schema (what credentials/URLs the user must provide)

```java
public interface IntegrationConnector {
    String connectorKey();           // e.g. "jira", "servicenow", "qualys"
    String connectorName();
    ConnectorConfigSchema configSchema();
    InboundHandler inboundHandler(); // nullable if no inbound
    OutboundHandler outboundHandler(); // nullable if no outbound
}
```

### 4.1 Connector Registry

The platform ships with these built-in connectors:

| Connector | Direction | Purpose |
|-----------|-----------|---------|
| **Jira** | Bidirectional | Create/sync GRC issues with Jira tickets |
| **ServiceNow** | Bidirectional | Sync incidents and remediation tasks |
| **Qualys** | Inbound | Import vulnerabilities as GRC vulnerability records |
| **Tenable** | Inbound | Import vulnerabilities |
| **Splunk** | Outbound | Forward GRC events to Splunk SIEM |
| **Azure AD / Entra ID** | Inbound | Sync users and groups |
| **Okta** | Inbound | Sync users and groups |
| **Slack** | Outbound | Send GRC notifications to Slack channels |
| **Microsoft Teams** | Outbound | Send GRC notifications to Teams channels |
| **Generic REST** | Bidirectional | Custom REST API integrations |

New connectors are added as `IntegrationConnector` implementations in the `platform-integration` module — no core changes needed.

### 4.2 Connector Configuration (Stored)

```sql
CREATE TABLE integration_configurations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    connector_key   NVARCHAR(100)     NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    config          NVARCHAR(MAX)     NOT NULL,   -- JSON: connector-specific config (encrypted)
    is_active       BIT               NOT NULL DEFAULT 1,
    last_sync_at    DATETIME2         NULL,
    sync_status     NVARCHAR(20)      NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_integration_org_connector UNIQUE (org_id, connector_key)
);
```

**Security:** The `config` column is encrypted at rest using SQL Server TDE and additionally application-level encrypted (AES-256) for secrets (API keys, passwords). Secrets are never returned in API responses — only a masked indicator is shown.

---

## 5. Inbound Integration (REST API)

External systems can push data into the GRC platform via the integration REST API:

### 5.1 Inbound Webhook

```
POST /api/v1/integrations/{connectorKey}/inbound
Authorization: Bearer {api-key}
Content-Type: application/json

Body: connector-specific JSON payload
```

The inbound handler:
1. Validates the payload against the connector's schema
2. Transforms the external payload to a GRC record's field values
3. Creates or updates the corresponding GRC record (via `RecordService`)
4. Returns `202 Accepted` (async processing)

### 5.2 Jira Integration (Bidirectional Example)

**GRC Issue → Jira Ticket:**
1. GRC issue created with `create_jira_issue = true`
2. Integration outbound handler creates Jira issue via Jira REST API
3. Jira issue key stored in GRC issue's `external_ref` field

**Jira Status Change → GRC Issue Update:**
1. Jira sends webhook to `POST /api/v1/integrations/jira/inbound`
2. Inbound handler maps Jira status to GRC issue workflow state
3. GRC record updated accordingly

### 5.3 Qualys Vulnerability Import

```
Schedule: Every 4 hours (configurable)
Connector: QualysConnector

For each new vulnerability from Qualys:
  1. Check if vulnerability already exists (match on qualys_id)
  2. If new: create a Vulnerability record with mapped fields
  3. If existing: update severity, status if changed
  4. Link to Asset record if asset identifier matches
  5. Log sync result in integration_sync_log
```

---

## 6. Import / Export

### 6.1 CSV Import

```
POST /api/v1/records/import
Authorization: Bearer {token}
Content-Type: multipart/form-data

Fields:
  file:   (CSV file)
  appId:  (UUID of target application)
  config: JSON mapping of CSV columns to field keys

Response: { jobId: "uuid" }
```

**Import Validation:**
- Column mapping validated before processing begins
- Each row validated against field validation rules
- Errors collected per row (up to 1000 error details)
- All-or-nothing or partial import (configurable via `mode: strict|partial`)

**Import Job lifecycle:**

```sql
CREATE TABLE import_jobs (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    application_id  UNIQUEIDENTIFIER  NOT NULL,
    status          NVARCHAR(20)      NOT NULL DEFAULT 'pending',
    total_rows      INT               NULL,
    processed_rows  INT               NOT NULL DEFAULT 0,
    success_count   INT               NOT NULL DEFAULT 0,
    failure_count   INT               NOT NULL DEFAULT 0,
    errors          NVARCHAR(MAX)     NULL,     -- JSON: [{row:1, message:"..."}]
    started_at      DATETIME2         NULL,
    completed_at    DATETIME2         NULL,
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 6.2 Export

Reuses the async export from Module 12 (Reporting). Additionally supports:

```
POST /api/v1/records/export
Authorization: Bearer {token}
Body: {
  appId:  "uuid",
  filter: { ... },        // same filter DSL
  fields: ["title","risk_rating","owner","due_date"],
  format: "excel",        // "excel" | "csv" | "json"
  includeRelated: false
}
Response: { jobId: "uuid" }
```

---

## 7. Integration Monitoring

```sql
CREATE TABLE integration_sync_log (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    integration_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES integration_configurations(id),
    direction       NVARCHAR(10)      NOT NULL CHECK (direction IN ('inbound','outbound')),
    status          NVARCHAR(20)      NOT NULL CHECK (status IN ('success','partial','failed')),
    records_total   INT               NOT NULL DEFAULT 0,
    records_ok      INT               NOT NULL DEFAULT 0,
    records_failed  INT               NOT NULL DEFAULT 0,
    error_summary   NVARCHAR(2000)    NULL,
    started_at      DATETIME2         NOT NULL,
    completed_at    DATETIME2         NULL
);
```

Admin users can view integration health, last sync time, error rates, and re-trigger manual syncs via the admin UI.

---

## 8. GraphQL API

```graphql
type Query {
  integrationConfigurations(orgId: UUID!): [IntegrationConfiguration!]!
  integrationSyncLog(integrationId: UUID!, page: PageInput): SyncLogPage!
  importJobStatus(jobId: UUID!): ImportJob!
}

type Mutation {
  configureIntegration(input: IntegrationConfigInput!): IntegrationConfiguration!
  testIntegrationConnection(id: UUID!): ConnectionTestResult!
  disableIntegration(id: UUID!): Boolean!
  triggerManualSync(id: UUID!, direction: SyncDirection!): SyncJob!
}

type ConnectionTestResult {
  success:  Boolean!
  message:  String!
  latencyMs: Int
}
```

---

## 9. Security Considerations

| Risk | Mitigation |
|------|-----------|
| Malicious inbound webhook payload | Schema validation before any processing |
| SSRF via webhook endpoint URL | URL allowlist; block private IP ranges |
| Credential exposure in config | Application-level encryption; secrets never returned in API |
| Data exfiltration via export | Rate limiting; max export size per day per org |
| Supply chain via connectors | Connectors are code-reviewed; no dynamic code loading |

---

## 10. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | SCIM 2.0 support for automated user provisioning? | High |
| 2 | Should connectors be installable as plugins (JAR files) or only built-in? | Medium |
| 3 | Rate limits for inbound integration API? (Protection against flooding) | High |
| 4 | Bi-directional sync conflict resolution: who wins — GRC or external system? | High |
| 5 | GraphQL API exposure for external integrators: separate endpoint or same? | Medium |

---

*Previous: [13 — File & Document Management](13-file-document-management.md) | Next: [15 — Policy Management](15-policy-management.md)*
