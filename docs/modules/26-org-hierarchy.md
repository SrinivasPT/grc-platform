# Module 26 — Organizational Hierarchy

## 1. Purpose

The Organizational Hierarchy module provides the foundational `organization_units` tree that underpins:

- **Record scoping:** Who can see which records based on their org unit membership
- **Workflow delegation+escalation:** Resolving manager chains for escalation (Module 08)
- **Risk/compliance roll-up:** Aggregating risk scores across business units
- **SCIM user provisioning:** Mapping HR department codes to GRC org units (Module 14)
- **Policy acknowledgment escalation:** Notifying the direct manager of non-respondents (Module 15)
- **Audit team scoping:** Limiting audit field to specific org units (Module 20)
- **Reporting:** Breaking down metrics by business unit / division / region

The platform is a **single-bank deployment**. The organizational hierarchy represents the bank's internal structure: divisions, departments, subsidiaries, branches, regions, and cost centers.

---

## 2. Hierarchy Model

The `organization_units` table uses a **materialized path** design (also known as the path enumeration model). This gives O(1) subtree queries using SQL `LIKE` on the `path` column, with no recursive CTE required for most queries.

```sql
CREATE TABLE organization_units (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,   -- always the single bank org
    parent_id       UNIQUEIDENTIFIER  NULL REFERENCES organization_units(id),
    -- Materialized path: '/rootId/childId/grandchildId/' — each segment is a UUID fragment
    path            NVARCHAR(2000)    NOT NULL,   -- e.g. '/a1b2.../c3d4.../'
    depth           INT               NOT NULL DEFAULT 0,  -- 0 = root
    unit_type       NVARCHAR(50)      NOT NULL
                    CHECK (unit_type IN ('division','department','branch','region',
                                         'cost_center','subsidiary','team')),
    code            NVARCHAR(100)     NULL,        -- HR/GL code, e.g. "DIV-001"
    name            NVARCHAR(500)     NOT NULL,
    description     NVARCHAR(MAX)     NULL,
    manager_id      UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    display_order   INT               NOT NULL DEFAULT 0,
    is_active       BIT               NOT NULL DEFAULT 1,
    hr_dept_code    NVARCHAR(100)     NULL,        -- Maps to SCIM HR system department code
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_orgunit_code UNIQUE (org_id, code)
);

CREATE INDEX ix_orgunit_path   ON organization_units(org_id, path);
CREATE INDEX ix_orgunit_parent ON organization_units(parent_id);
CREATE INDEX ix_orgunit_manager ON organization_units(manager_id);
```

### 2.1 Path Computation

Paths are maintained by the application (not a database trigger):

```java
public OrganizationUnit createUnit(CreateOrgUnitInput input) {
    String parentPath = "/";
    if (input.parentId() != null) {
        OrganizationUnit parent = orgUnitRepository.findById(input.parentId())
            .orElseThrow(() -> new NotFoundException(input.parentId()));
        parentPath = parent.getPath();
        validateNoCycle(input.parentId(), input.id());
    }
    String newPath = parentPath + input.id().toString().replace("-","") + "/";
    // depth = count of "/" separators in path - 1
    int depth = (int) newPath.chars().filter(c -> c == '/').count() - 1;
    return orgUnitRepository.save(new OrganizationUnit(input, newPath, depth));
}
```

### 2.2 Subtree Query Pattern

```java
// Get all units in a subtree (including the root)
List<OrganizationUnit> subtree = orgUnitRepository
    .findByPathStartingWith(rootUnit.getPath()); // SQL: WHERE path LIKE '{path}%'

// Get all siblings of a unit
List<OrganizationUnit> siblings = orgUnitRepository
    .findByParentIdAndIdNot(unit.getParentId(), unitId);

// Get ancestors (breadcrumb)
List<OrganizationUnit> ancestors = orgUnitRepository
    .findAncestors(unitId); // Extracts IDs from path, IN (...) query
```

---

## 3. User-to-Unit Membership

Users belong to one or more org units. Their **primary unit** drives default scoping; their **additional units** grant supplementary access.

```sql
CREATE TABLE user_org_units (
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    org_unit_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES organization_units(id),
    is_primary      BIT               NOT NULL DEFAULT 1,
    role_override   NVARCHAR(100)     NULL,   -- override role within this unit (optional)
    joined_at       DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (user_id, org_unit_id)
);
CREATE INDEX ix_uou_unit ON user_org_units(org_unit_id);
```

### 3.1 Access Scoping Rules

| User's org_unit_scope | Visibility |
|----------------------|-----------|
| `unit_only` | Records owned by or scoped to this exact unit |
| `unit_and_descendants` | Records in this unit and all descendant units (default) |
| `global` | All records (reserved for executive/platform admin roles) |

The default for most users is `unit_and_descendants`. This is enforced by passing the user's `org_unit_id` and all descendant IDs to every record query. The list of descendant IDs is cached in Redis with a 5-minute TTL (invalidated on org unit changes).

---

## 4. Manager Hierarchy Resolution

The `manager_id` field on `organization_units` defines the **head of that unit**. Direct manager lookup for a user is resolved by finding the user's primary unit manager:

```java
public Optional<User> findDirectManager(UUID userId) {
    return userOrgUnitRepository.findPrimaryUnitByUserId(userId)
        .map(uou -> uou.getOrgUnit().getManagerId())
        .flatMap(userRepository::findById);
}
```

If the user IS the unit manager, the parent unit's manager is returned (manager's manager).

This hierarchy is used by:
- **Module 08 (Workflow):** Escalation of overdue delegated tasks
- **Module 15 (Policy):** Escalation of overdue acknowledgments
- **Module 19 (Issues):** Escalation of overdue remediation items

---

## 5. org_unit_id on Domain Records

Every domain record carries an `org_unit_id` foreign key that indicates the org unit that "owns" or is "responsible for" that record:

```sql
-- Enforced on all major domain tables:
-- risks, controls, policies, incidents, vendors, audit_engagements, vulnerabilities, etc.
ALTER TABLE records ADD
    org_unit_id UNIQUEIDENTIFIER NULL REFERENCES organization_units(id);

CREATE INDEX ix_records_orgunit ON records(org_unit_id, application_id, org_id);
```

This single column enables:
1. Access scoping (subtree visibility check)
2. Roll-up reporting by org unit hierarchy
3. Assignment routing (tasks go to unit members)

---

## 6. GraphQL API

```graphql
type OrganizationUnit {
  id:           UUID!
  name:         String!
  code:         String
  unitType:     OrgUnitType!
  path:         String!
  depth:        Int!
  manager:      User
  parent:       OrganizationUnit
  children:     [OrganizationUnit!]!
  ancestors:    [OrganizationUnit!]!   # breadcrumb
  isActive:     Boolean!
  memberCount:  Int!
}

enum OrgUnitType { DIVISION DEPARTMENT BRANCH REGION COST_CENTER SUBSIDIARY TEAM }

type Query {
  orgUnitTree(rootId: UUID): [OrganizationUnit!]!   # full tree from root or specific node
  orgUnit(id: UUID!):        OrganizationUnit
  myOrgUnits:                [OrganizationUnit!]!   # units the calling user belongs to
}

type Mutation {
  createOrgUnit(input: CreateOrgUnitInput!): OrganizationUnit!
  updateOrgUnit(id: UUID!, input: UpdateOrgUnitInput!): OrganizationUnit!
  moveOrgUnit(id: UUID!, newParentId: UUID!): OrganizationUnit!  # updates path for subtree
  deactivateOrgUnit(id: UUID!, reassignTo: UUID): Boolean!
}
```

**`moveOrgUnit` cascade:** When a unit is moved, all paths in the subtree are updated in a single transaction. This updates potentially thousands of rows for large subtrees — a background task is used for subtrees with > 100 descendants.

---

## 7. Neo4j Projection

The org unit hierarchy is projected to Neo4j for impact traversal queries:

```cypher
(:OrgUnit)-[:PARENT_OF]->(:OrgUnit)
(:User)-[:MEMBER_OF]->(:OrgUnit)
(:Risk)-[:OWNED_BY_UNIT]->(:OrgUnit)

// Find all open critical risks in a division and all sub-units
MATCH (div:OrgUnit {code: 'DIV-RISK'})-[:PARENT_OF*0..5]->(unit:OrgUnit)
    <-[:OWNED_BY_UNIT]-(r:Risk)
WHERE r.severity = 'critical' AND r.status = 'active'
RETURN unit.name, count(r) AS critical_risk_count
ORDER BY critical_risk_count DESC
```

---

## 8. Reporting Roll-Up

All KPI reports in Module 12 support **org unit drill-down**:

- Top-level view: all accessible org units aggregated
- Click into a division: see only that division's metrics
- Continue drilling into departments

Roll-up is performed at query time using the subtree path query (`path LIKE '{path}%'`). Pre-aggregated summary tables are used for expensive roll-ups on large trees (materialized daily).

---

## 9. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Matrix organizations: should a user be able to be "primary" in more than one unit? | Medium | Supported via `user_org_units.is_primary = 0` for secondary units. |
| 2 | Should org unit changes trigger re-evaluation of all in-progress workflows? | High | |
| 3 | Legal entity hierarchy vs. operational hierarchy (may differ for a bank)? | Medium | |
| 4 | Should org units support cost code integration for financial reporting? | Low | `code` field maps to GL/HR codes. Extension point. |

---

*Previous: [25 — Assessment & Questionnaire](25-assessment-questionnaire.md) | Next: [27 — Regulatory Reporting](27-regulatory-reporting.md)*
