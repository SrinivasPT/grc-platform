# Module 02 — Data Model & Schema

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** Module 01 (Platform Architecture)

---

## 1. Purpose

This module defines the canonical SQL Server data model that underpins the entire GRC platform. It is the **source of truth** for all GRC data — records, relationships, configuration, field values, workflow state, audit history, and tenant structure.

Every other module depends on this schema. Changes to this schema go through Flyway versioned migrations only.

---

## 2. Design Philosophy

### 2.1 No EAV Anti-Pattern

Entity-Attribute-Value (EAV) — storing field names and values in generic `key/value` rows — is explicitly rejected. EAV makes SQL queries unreadable, prevents proper indexing, and destroys query performance at scale.

**Instead:** Typed field value tables per field category, with separate tables for string, numeric, date, and reference values. Each record type has its own strongly-typed extensions where performance requires it.

### 2.2 Config is Stored Data

Application definitions (what fields exist, what layouts, what rules) are rows in the database — not files or compiled code. This enables runtime configuration without deployments.

### 2.3 Tenant Isolation at Every Table

Every table includes `org_id` (organization ID). The Hibernate `@Filter` enforces this transparently across all queries.

### 2.4 Relationships are First-Class

No JSON blobs for relationships. All entity-to-entity associations are stored in an explicit `record_relations` table enabling many-to-many traversal and graph projection to Neo4j.

---

## 3. Schema Groups

```
┌─────────────────────────────────────────────────────────────────┐
│  TENANT / ORG                                                   │
│  organizations, org_settings                                    │
├─────────────────────────────────────────────────────────────────┤
│  APPLICATION CONFIG (Meta-layer)                                │
│  applications, field_definitions, layout_definitions,          │
│  value_lists, value_list_items, rule_definitions                │
├─────────────────────────────────────────────────────────────────┤
│  RECORD DATA (Runtime)                                          │
│  records, field_values_text, field_values_number,              │
│  field_values_date, field_values_reference,                     │
│  record_relations, record_attachments                           │
├─────────────────────────────────────────────────────────────────┤
│  WORKFLOW                                                       │
│  workflow_definitions, workflow_instances, workflow_history,    │
│  workflow_tasks                                                  │
├─────────────────────────────────────────────────────────────────┤
│  AUDIT & VERSIONING                                             │
│  audit_log, record_versions                                     │
├─────────────────────────────────────────────────────────────────┤
│  IDENTITY & ACCESS                                              │
│  users, roles, role_permissions, user_roles,                   │
│  permission_policies, record_access_rules                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Core Tables — Full Schema

### 4.1 Tenant / Organization

```sql
CREATE TABLE organizations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    name            NVARCHAR(255)     NOT NULL,
    slug            NVARCHAR(100)     NOT NULL UNIQUE,        -- URL-safe identifier
    status          NVARCHAR(20)      NOT NULL DEFAULT 'active'
                                      CHECK (status IN ('active','suspended','archived')),
    config          NVARCHAR(MAX)     NULL,                   -- JSON: org-level settings
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE org_settings (
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    setting_key     NVARCHAR(200)     NOT NULL,
    setting_value   NVARCHAR(MAX)     NULL,
    PRIMARY KEY (org_id, setting_key)
);
```

---

### 4.2 Application Config (The Meta-Layer)

An **Application** in this platform is equivalent to an Archer Application — it defines an entity type (e.g., "Risk", "Policy", "Control") with its fields and layouts.

```sql
-- An Application defines a type of GRC entity
CREATE TABLE applications (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    name            NVARCHAR(255)     NOT NULL,
    internal_key    NVARCHAR(100)     NOT NULL,   -- e.g. 'risk', 'policy', 'control'
    description     NVARCHAR(2000)    NULL,
    icon            NVARCHAR(100)     NULL,        -- icon name/slug
    is_active       BIT               NOT NULL DEFAULT 1,
    config_version  INT               NOT NULL DEFAULT 1,
    config          NVARCHAR(MAX)     NULL,        -- JSON: display options, color, etc.
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_app_key_per_org UNIQUE (org_id, internal_key)
);
CREATE INDEX idx_applications_org ON applications(org_id);

-- Field Definitions (the schema of an Application)
CREATE TABLE field_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    internal_key    NVARCHAR(100)     NOT NULL,   -- e.g. 'risk_score', 'owner_id'
    field_type      NVARCHAR(50)      NOT NULL,   -- see Field Types section
    is_required     BIT               NOT NULL DEFAULT 0,
    is_system       BIT               NOT NULL DEFAULT 0,   -- system fields can't be deleted
    is_searchable   BIT               NOT NULL DEFAULT 1,
    display_order   INT               NOT NULL DEFAULT 0,
    config          NVARCHAR(MAX)     NULL,        -- JSON: field-type-specific config
    validation_rules NVARCHAR(MAX)    NULL,        -- JSON: rule DSL for validation
    config_version  INT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_field_key_per_app UNIQUE (application_id, internal_key)
);
CREATE INDEX idx_field_defs_app ON field_definitions(application_id, org_id);

-- Value Lists (dropdown/multi-select options)
CREATE TABLE value_lists (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    name            NVARCHAR(255)     NOT NULL,
    internal_key    NVARCHAR(100)     NOT NULL,
    is_system       BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_vl_key_per_org UNIQUE (org_id, internal_key)
);

CREATE TABLE value_list_items (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    value_list_id   UNIQUEIDENTIFIER  NOT NULL REFERENCES value_lists(id),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    label           NVARCHAR(255)     NOT NULL,
    value           NVARCHAR(255)     NOT NULL,
    color           NVARCHAR(20)      NULL,       -- for status chips, heat maps
    numeric_weight  DECIMAL(10,4)     NULL,       -- for scored value lists
    display_order   INT               NOT NULL DEFAULT 0,
    is_active       BIT               NOT NULL DEFAULT 1,
    parent_id       UNIQUEIDENTIFIER  NULL        -- for hierarchical value lists
                                      REFERENCES value_list_items(id),
    CONSTRAINT uq_vl_item_val UNIQUE (value_list_id, value)
);
CREATE INDEX idx_vli_list ON value_list_items(value_list_id);

-- Layout Definitions (forms, tabs, panels)
CREATE TABLE layout_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    layout_type     NVARCHAR(50)      NOT NULL   -- 'record_form','list_view','dashboard'
                    CHECK (layout_type IN ('record_form','list_view','dashboard','report')),
    layout_config   NVARCHAR(MAX)     NOT NULL,  -- JSON: full tab/panel/field arrangement
    is_default      BIT               NOT NULL DEFAULT 0,
    config_version  INT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX idx_layout_defs_app ON layout_definitions(application_id, org_id);

-- Rule Definitions (calculations, validations, conditionals)
CREATE TABLE rule_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    application_id  UNIQUEIDENTIFIER  NULL       REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    rule_type       NVARCHAR(50)      NOT NULL
                    CHECK (rule_type IN ('calculation','validation','visibility',
                                         'aggregation','notification_trigger','workflow_condition')),
    trigger_event   NVARCHAR(50)      NULL,       -- 'on_save','on_field_change','scheduled'
    rule_dsl        NVARCHAR(MAX)     NOT NULL,   -- JSON rule DSL
    is_active       BIT               NOT NULL DEFAULT 1,
    config_version  INT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

---

### 4.3 Records (Runtime GRC Data)

```sql
-- A Record is one instance of an Application entity (e.g., one Risk record)
CREATE TABLE records (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    record_number   INT               NOT NULL,  -- org-scoped sequential number per app
    display_name    NVARCHAR(500)     NULL,       -- computed summary title
    display_number  AS (app.prefix + RIGHT('0000000' + CAST(record_number AS NVARCHAR), 7)) PERSISTED,
                                                  -- formatted: e.g. RISK-0000042, POL-0000007
    status          NVARCHAR(50)      NOT NULL DEFAULT 'active',
    workflow_state  NVARCHAR(100)     NULL,       -- current workflow state
    version         INT               NOT NULL DEFAULT 1,  -- optimistic concurrency
    is_deleted      BIT               NOT NULL DEFAULT 0,
    deleted_at      DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    updated_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_record_num_per_app UNIQUE (org_id, application_id, record_number)
);
CREATE INDEX idx_records_app_org  ON records(application_id, org_id) WHERE is_deleted = 0;
CREATE INDEX idx_records_workflow ON records(workflow_state, application_id, org_id) WHERE is_deleted = 0;
```

### 4.4 Field Value Tables (Typed, Not EAV)

```sql
-- Text/String values
CREATE TABLE field_values_text (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES field_definitions(id),
    value           NVARCHAR(MAX)     NULL,
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_fvt_record_field UNIQUE (record_id, field_def_id)
);
CREATE INDEX idx_fvt_record    ON field_values_text(record_id, org_id);
CREATE INDEX idx_fvt_field_def ON field_values_text(field_def_id, org_id);
-- Full-text search index
CREATE FULLTEXT INDEX ON field_values_text(value) KEY INDEX ... ;

-- Numeric values (integers + decimals)
CREATE TABLE field_values_number (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES field_definitions(id),
    value           DECIMAL(28, 10)   NULL,
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_fvn_record_field UNIQUE (record_id, field_def_id)
);
CREATE INDEX idx_fvn_record ON field_values_number(record_id, org_id);

-- Date/DateTime values
CREATE TABLE field_values_date (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES field_definitions(id),
    value           DATETIME2         NULL,
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_fvd_record_field UNIQUE (record_id, field_def_id)
);

-- Reference values (links to value list items or other records)
CREATE TABLE field_values_reference (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES field_definitions(id),
    ref_type        NVARCHAR(30)      NOT NULL
                    CHECK (ref_type IN ('value_list_item','record','user','org_unit')),
    ref_id          UNIQUEIDENTIFIER  NOT NULL,  -- ID of the referenced entity
    display_label   NVARCHAR(500)     NULL,       -- denormalized for display performance
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
    -- NOTE: Allows multiple rows per (record_id, field_def_id) for multi-select fields
);
CREATE INDEX idx_fvr_record    ON field_values_reference(record_id, org_id);
CREATE INDEX idx_fvr_ref_id    ON field_values_reference(ref_id, ref_type, org_id);
CREATE INDEX idx_fvr_field_def ON field_values_reference(field_def_id, org_id);
```

---

### 4.5 Record Relationships

```sql
-- Explicit many-to-many relationships between records (across any application types)
CREATE TABLE record_relations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    source_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    target_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    relation_type   NVARCHAR(100)     NOT NULL,  -- e.g. 'policy_controls', 'risk_mitigated_by'
    is_directional  BIT               NOT NULL DEFAULT 0,  -- 0 = bidirectional
    metadata        NVARCHAR(MAX)     NULL,       -- JSON: relationship-specific attributes
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    CONSTRAINT uq_rr_src_tgt_type UNIQUE (source_id, target_id, relation_type)
);
CREATE INDEX idx_rr_source   ON record_relations(source_id, relation_type, org_id);
CREATE INDEX idx_rr_target   ON record_relations(target_id, relation_type, org_id);
CREATE INDEX idx_rr_type_org ON record_relations(relation_type, org_id);

-- Application-level relationship type definitions
CREATE TABLE relation_type_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    internal_key    NVARCHAR(100)     NOT NULL,   -- matches relation_type in record_relations
    label           NVARCHAR(255)     NOT NULL,
    source_app_id   UNIQUEIDENTIFIER  NULL REFERENCES applications(id),
    target_app_id   UNIQUEIDENTIFIER  NULL REFERENCES applications(id),
    inverse_label   NVARCHAR(255)     NULL,       -- e.g. forward='mitigates', inverse='mitigated_by'
    cardinality     NVARCHAR(20)      NOT NULL DEFAULT 'many_to_many'
                    CHECK (cardinality IN ('one_to_one','one_to_many','many_to_many')),
    CONSTRAINT uq_rtd_key_org UNIQUE (org_id, internal_key)
);
```

---

### 4.6 Attachments

```sql
CREATE TABLE record_attachments (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    field_def_id    UNIQUEIDENTIFIER  NULL REFERENCES field_definitions(id), -- nullable if general attachment
    original_name   NVARCHAR(500)     NOT NULL,
    storage_path    NVARCHAR(2000)    NOT NULL,   -- path/key in blob storage
    mime_type       NVARCHAR(200)     NOT NULL,
    file_size_bytes BIGINT            NOT NULL,
    checksum_sha256 NCHAR(64)         NOT NULL,
    scan_status     NVARCHAR(20)      NOT NULL DEFAULT 'pending'
                    CHECK (scan_status IN ('pending','clean','infected','skipped')),
    version         INT               NOT NULL DEFAULT 1,
    is_deleted      BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by      UNIQUEIDENTIFIER  NOT NULL
);
CREATE INDEX idx_attachments_record ON record_attachments(record_id, org_id) WHERE is_deleted = 0;
```

---

### 4.7 Identity & Access

```sql
CREATE TABLE users (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    email           NVARCHAR(320)     NOT NULL,
    display_name    NVARCHAR(255)     NOT NULL,
    external_id     NVARCHAR(500)     NULL,       -- SSO subject / external IdP ID
    status          NVARCHAR(20)      NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active','inactive','locked')),
    last_login_at   DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_user_email_per_org UNIQUE (org_id, email)
);

CREATE TABLE org_units (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL REFERENCES organizations(id),
    name            NVARCHAR(255)     NOT NULL,
    parent_id       UNIQUEIDENTIFIER  NULL REFERENCES org_units(id),
    path            NVARCHAR(2000)    NULL,       -- materialized path: /root/dept/team
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE roles (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    description     NVARCHAR(1000)    NULL,
    is_system       BIT               NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_name_org UNIQUE (org_id, name)
);

CREATE TABLE role_permissions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    role_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES roles(id),
    resource_type   NVARCHAR(100)     NOT NULL,  -- 'application','record','field','report',...
    resource_id     UNIQUEIDENTIFIER  NULL,       -- NULL = applies to all of resource_type
    permission      NVARCHAR(50)      NOT NULL,  -- 'read','create','update','delete','approve'
    CONSTRAINT uq_rp_role_res_perm UNIQUE (role_id, resource_type, resource_id, permission)
);

CREATE TABLE user_roles (
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    role_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES roles(id),
    org_unit_id     UNIQUEIDENTIFIER  NULL REFERENCES org_units(id),  -- scoped role
    PRIMARY KEY (user_id, role_id, org_unit_id)
);
```

---

### 4.8 Audit Log

```sql
-- Append-only audit log (never update, never delete rows)
CREATE TABLE audit_log (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID(),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    event_time      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    user_id         UNIQUEIDENTIFIER  NULL,       -- NULL for system events
    entity_type     NVARCHAR(100)     NOT NULL,  -- 'record','field_value','workflow_state',...
    entity_id       UNIQUEIDENTIFIER  NOT NULL,
    action          NVARCHAR(50)      NOT NULL,  -- 'create','update','delete','state_change'
    old_value       NVARCHAR(MAX)     NULL,       -- JSON snapshot
    new_value       NVARCHAR(MAX)     NULL,       -- JSON snapshot
    ip_address      NVARCHAR(45)      NULL,
    session_id      NVARCHAR(200)     NULL,
    correlation_id  UNIQUEIDENTIFIER  NULL,       -- links related events in one operation
    PRIMARY KEY (id, event_time)                  -- partitioning key
)
-- Partition by event_time (monthly partitions for retention management)
;
CREATE INDEX idx_audit_entity ON audit_log(entity_id, entity_type, event_time);
CREATE INDEX idx_audit_user   ON audit_log(user_id, event_time);
CREATE INDEX idx_audit_org    ON audit_log(org_id, event_time);
```

---

## 5. Field Type Reference

| Field Type Key | Description | Storage Table | Notes |
|---------------|-------------|--------------|-------|
| `text_short` | Single-line text (≤ 500 chars) | `field_values_text` | |
| `text_long` | Multi-line / rich text | `field_values_text` | HTML sanitized |
| `integer` | Whole number | `field_values_number` | |
| `decimal` | Decimal number | `field_values_number` | |
| `currency` | Money value | `field_values_number` | Config: currency code |
| `percentage` | Percentage value | `field_values_number` | 0–100 |
| `date` | Date only | `field_values_date` | |
| `datetime` | Date + time | `field_values_date` | |
| `boolean` | True/False | `field_values_number` | Stored as 1/0 |
| `single_select` | One value from value list | `field_values_reference` | ref_type='value_list_item' |
| `multi_select` | Many values from value list | `field_values_reference` | Multiple rows |
| `user_reference` | Link to a user | `field_values_reference` | ref_type='user' |
| `user_multi` | Multiple users | `field_values_reference` | Multiple rows |
| `record_reference` | Link to another record | `field_values_reference` | ref_type='record' |
| `record_multi` | Multiple record links | `field_values_reference` | Multiple rows |
| `org_unit` | Link to org unit | `field_values_reference` | ref_type='org_unit' |
| `attachment` | File attachment | `record_attachments` | |
| `calculated` | Server-computed value | Virtual (not stored as field_value) | Result in records.computed_json |
| `matrix` | Risk matrix (2D) | `field_values_reference` (2 linked value lists) | Special rendering |

---

## 6. Computed / Calculated Field Handling

Calculated fields (e.g., `risk_score = likelihood × impact`) are **not stored** in the `field_values_*` tables. Instead:

1. The Rule Engine evaluates all `type = 'calculation'` rules for the record
2. Results are stored in a JSON column on the `records` table: `computed_values NVARCHAR(MAX)`
3. This column is refreshed on every save and on any upstream dependency change
4. The audit log captures the computed value at each version

```sql
-- Add computed_values column to records table
ALTER TABLE records ADD computed_values NVARCHAR(MAX) NULL;
-- Example computed_values JSON:
-- { "risk_score": 12, "control_effectiveness": 87.5, "residual_risk": 1.44 }
```

### 6.1 Materialized Computed Columns for Filtering

The `computed_values` JSON column cannot be indexed directly. Frequently filtered computed fields **must be materialized as persisted computed columns** for query performance:

```sql
-- Materialize high-frequency filter fields as persisted columns on records
ALTER TABLE records ADD
    risk_score            AS CAST(JSON_VALUE(computed_values, '$.risk_score') AS DECIMAL(10,4)) PERSISTED,
    residual_risk_score   AS CAST(JSON_VALUE(computed_values, '$.residual_risk_score') AS DECIMAL(10,4)) PERSISTED,
    control_effectiveness AS CAST(JSON_VALUE(computed_values, '$.control_effectiveness') AS DECIMAL(10,4)) PERSISTED;

-- Index the materialized columns
CREATE INDEX idx_records_risk_score ON records(org_id, application_id, risk_score)
    WHERE is_deleted = 0;
CREATE INDEX idx_records_effectiveness ON records(org_id, application_id, control_effectiveness)
    WHERE is_deleted = 0;
```

> **Rule:** Any computed field used in a dashboard filter, report sort, or heat map query must be persisted and indexed. The `field_definitions` table includes a `materialize_as_column BIT NOT NULL DEFAULT 0` flag to signal this requirement.

---

## 7. Optimistic Concurrency

All record updates must include the current `version` number. The service layer enforces:

```sql
UPDATE records
SET    updated_by   = @user_id,
       updated_at   = SYSUTCDATETIME(),
       version      = version + 1,
       -- ... field updates
WHERE  id           = @record_id
  AND  org_id       = @org_id
  AND  version      = @expected_version  -- ← optimistic lock check
  AND  is_deleted   = 0;
-- If 0 rows affected → version conflict → return HTTP 409 Conflict
```

---

## 8. Change Tracking for Neo4j CDC

SQL Server Change Tracking is enabled at the database and table level for the following tables:

```sql
ALTER DATABASE grc_platform SET CHANGE_TRACKING = ON
    (CHANGE_RETENTION = 7 DAYS, AUTO_CLEANUP = ON);

ALTER TABLE records           ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = ON);
ALTER TABLE record_relations  ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = ON);
ALTER TABLE field_values_reference ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = ON);
```

The Projection Worker (Module 06) polls the change tracking tables and syncs changed nodes/relationships to Neo4j.

---

## 9. Data Integrity Rules

| Rule | Implementation |
|------|---------------|
| No orphaned field values | `ON DELETE CASCADE` from records to all field_value tables |
| No orphaned relations | `ON DELETE CASCADE` from records to record_relations |
| Soft delete only for records | `is_deleted = 1`, never physical DELETE |
| Audit log is append-only | No UPDATE/DELETE permissions on audit_log for app user |
| Tenant isolation | All queries filter by org_id; Hibernate global filter + SQL Server RLS |
| Optimistic concurrency | Version check on every UPDATE to records |
| Config versioning | `config_version` increments on every change to applications/field_definitions |

---

## 10. Flyway Migration Convention

```
db/migrations/
    V001__create_organizations.sql
    V002__create_applications.sql
    V003__create_field_definitions.sql
    V004__create_value_lists.sql
    V005__create_records.sql
    V006__create_field_value_tables.sql
    V007__create_record_relations.sql
    V008__create_attachments.sql
    V009__create_audit_log.sql
    V010__create_identity_tables.sql
    V011__create_workflow_tables.sql   ← Defined in Module 08
    V012__create_notification_tables.sql ← Defined in Module 10
    R__seed_system_value_lists.sql     ← Repeatable: system-level seed data
```

Rules:
- Versioned migrations (V prefix): never modified after merge to main
- Repeatable migrations (R prefix): for seed data that can be re-run safely
- No `DROP` statements in any migration (schema only grows, never shrinks in production)
- Every migration is reviewed for reversibility before merge

---

## 11. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | ~~Should `computed_values` be materialized as a separate table for indexability?~~ | Medium | **Resolved:** Frequently filtered computed fields are materialized as persisted computed columns on the `records` table (e.g., `risk_score`, `control_effectiveness`). See Section 6.1. |
| 2 | Strategy for very large text fields (policy body > 1MB)? Store in blob with pointer? | Medium | |
| 3 | Partitioning strategy for `field_values_text` at scale (100M+ rows)? | High | |
| 4 | Should `audit_log` use temporal tables (SQL Server `SYSTEM_TIME`) vs custom implementation? | Medium | |
| 5 | ~~Record number format: global sequence per app, or per-org per-app?~~ | Low | **Resolved:** Per-org per-app sequential number; `display_number` computed column applies app-specific prefix (e.g., `RISK-0000042`). See `records.display_number`. |

---

*Previous: [01 — Platform Architecture](01-platform-architecture.md) | Next: [03 — Rule Engine](03-rule-engine.md)*
