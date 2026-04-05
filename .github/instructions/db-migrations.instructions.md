---
applyTo: 'backend/db/**'
description: 'Liquibase migration rules for GRC platform. Use when writing or reviewing database migration changesets, schema changes, or seed data.'
---

# db — Liquibase Migration Rules

## Purpose

`backend/db/migrations/` contains all Liquibase changesets for the SQL Server schema. The application schema and seed data are managed here — never via JPA `hbm2ddl.auto` or ad-hoc SQL scripts.

---

## Naming Convention

**Mandatory:** `V{YYYYMMDD}_{NNN}_{short-description}.xml`

Examples:

- `V20260405_001_init-org-schema.xml`
- `V20260405_002_init-user-schema.xml`
- `V20260406_001_add-record-fts-index.xml`

The `NNN` sequence restarts each day at `001`. If multiple migrations land on the same day, use `001`, `002`, `003`, etc.

---

## Changeset Rules

1. **Always include a `<rollback>` block.** If rollback is not possible (e.g., data-destroying drop), document why with `<rollback><!-- not reversible: ... --></rollback>`.
2. **Context tagging:**
    - `context="main"` — production schema changes (DDL, indexes, FKs).
    - `context="test"` — seed data only (test org, test users, reference data).
    - Never mix schema DDL and seed data in the same changeset.
3. **Column naming:** `snake_case` always. No camelCase, no PascalCase.
4. **Table naming:** plural `snake_case` (e.g., `organizations`, `workflow_instances`, `audit_log_entries`).
5. **Never modify an existing changeset.** If a correction is needed, add a new changeset.
6. **Every `id` column:** `UNIQUEIDENTIFIER DEFAULT NEWSEQUENTIALID() NOT NULL PRIMARY KEY`.
7. **Every table:** must have `org_id UNIQUEIDENTIFIER NOT NULL` as a tenant discriminator (except `organizations` itself).

---

## Changeset Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.9.xsd">

    <changeSet id="V20260405_001_init-org-schema" author="grc-platform" context="main">
        <createTable tableName="organizations">
            <column name="id" type="UNIQUEIDENTIFIER" defaultValueComputed="NEWSEQUENTIALID()">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="name" type="NVARCHAR(255)">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <rollback>
            <dropTable tableName="organizations"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
```

---

## Performance Rules

- Add indexes for all FK columns, all `org_id` columns, and any column used in a `WHERE` clause in hot queries.
- SQL Server Full-Text Search: use `<sql>` block with `CREATE FULLTEXT INDEX` — not Liquibase primitives (not supported).
- SQL Server Change Tracking: enable per-table via `<sql>ALTER TABLE ... ENABLE CHANGE_TRACKING</sql>`.

---

## Agent Checklist (db)

1. Date in filename matches actual date (`date +%Y%m%d`).
2. Rollback block present and correct.
3. Context is `main` (DDL) or `test` (seed data) — not mixed.
4. New table has `org_id` column (unless it is a platform-wide reference table).
5. Indexes added for all FK and search columns.
6. Run `./gradlew :db:liquibaseValidate` to verify XML before committing.
