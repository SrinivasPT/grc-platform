# Module 18 — Compliance Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 07, Module 15 (Policy), Module 16 (Risk), Module 17 (Control)

---

## 1. Domain Context

Compliance Management tracks the organization's adherence to external regulations, industry standards, and internal policies. It provides a structured way to:

- Import or manually build out compliance frameworks (NIST, ISO 27001, SOX, GDPR, etc.)
- Map requirements to controls, policies, and risks
- Track compliance posture with coverage metrics
- Manage regulatory change — new requirements, updates to existing obligations
- Document exceptions and derogations
- Produce evidence packages for auditors

---

## 2. Supported Frameworks (Shipped Out-of-Box)

| Framework | Domain |
|-----------|--------|
| NIST SP 800-53 Rev 5 | Federal information systems |
| NIST Cybersecurity Framework (CSF) 2.0 | Cybersecurity |
| ISO/IEC 27001:2022 Annex A | Information security management |
| ISO/IEC 27002:2022 | Information security controls |
| SOX (Sarbanes-Oxley) IT Controls | Public company financial reporting |
| GDPR | EU data privacy |
| HIPAA Security Rule | US healthcare |
| PCI DSS v4.0 | Payment card data |
| CIS Controls v8 | Practical cybersecurity |
| COBIT 2019 | IT governance |
| NIST Privacy Framework | Privacy management |

Frameworks are stored as structured data (not hardcoded). New frameworks can be imported via JSON or created manually.

---

## 3. Entity Design

### 3.1 Compliance Frameworks

```sql
CREATE TABLE compliance_frameworks (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,     -- NULL = global/shared framework
    name            NVARCHAR(255)     NOT NULL,
    abbreviation    NVARCHAR(50)      NOT NULL,
    version         NVARCHAR(50)      NOT NULL,
    publisher       NVARCHAR(255)     NULL,
    effective_date  DATE              NULL,
    description     NVARCHAR(2000)    NULL,
    is_locked       BIT               NOT NULL DEFAULT 1,  -- shared/system frameworks are locked
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 3.2 Compliance Requirements

```sql
CREATE TABLE compliance_requirements (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NULL,    -- NULL = system-level (shared across orgs)
    framework_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES compliance_frameworks(id),
    parent_id       UNIQUEIDENTIFIER  NULL REFERENCES compliance_requirements(id),
    req_number      NVARCHAR(100)     NOT NULL,     -- e.g. "AC-2", "A.9.4.3"
    title           NVARCHAR(500)     NOT NULL,
    description     NVARCHAR(MAX)     NOT NULL,
    rationale       NVARCHAR(MAX)     NULL,
    guidance        NVARCHAR(MAX)     NULL,
    is_leaf         BIT               NOT NULL DEFAULT 1,  -- false = section/group header
    sort_order      INT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_req_framework_number UNIQUE (framework_id, req_number)
);

CREATE INDEX ix_reqs_framework ON compliance_requirements(framework_id, parent_id);
```

### 3.3 Organization Compliance Programs

An organization subscribes to one or more frameworks as **Compliance Programs**:

```sql
CREATE TABLE compliance_programs (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    framework_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES compliance_frameworks(id),
    name            NVARCHAR(255)     NOT NULL,
    scope           NVARCHAR(MAX)     NULL,         -- JSON: in-scope systems/org units
    program_owner   UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    assessment_date DATE              NULL,
    next_review_date DATE             NULL,
    status          NVARCHAR(50)      NOT NULL DEFAULT 'active',
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_program_org_framework UNIQUE (org_id, framework_id)
);
```

### 3.4 Compliance Posture (per requirement)

Each in-scope requirement within a compliance program has a posture assessment:

```sql
CREATE TABLE compliance_postures (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    program_id      UNIQUEIDENTIFIER  NOT NULL REFERENCES compliance_programs(id),
    requirement_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES compliance_requirements(id),
    status          NVARCHAR(50)      NOT NULL DEFAULT 'not_assessed'
                    CHECK (status IN ('compliant','partially_compliant','non_compliant',
                                      'not_applicable','not_assessed','exception_approved')),
    coverage_pct    TINYINT           NULL,     -- 0–100%
    notes           NVARCHAR(MAX)     NULL,
    assessed_by     UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    assessed_at     DATETIME2         NULL,
    CONSTRAINT uq_posture_program_req UNIQUE (program_id, requirement_id)
);
```

---

## 4. Compliance Lifecycle

```
Framework Imported ──► Program Created ──► Requirements In-Scope
                                                 │
                   ┌─────────────────────────────┘
                   ▼
            Map Controls & Policies ──► Assess Posture ──► Identify Gaps
                                                                 │
                        ┌────────────────────────────────────────┘
                        ▼
              Create Remediation Plan ──► Track to Closure ──► Re-assess
                        │
                        └──► Request Exception (if remediation not feasible)
```

---

## 5. Coverage Calculation

Compliance coverage is calculated at four levels:

| Level | Formula |
|-------|---------|
| Requirement level | Derived from linked controls' effectiveness |
| Family/section level | % of child requirements that are compliant |
| Framework level | % of all leaf requirements that are compliant |
| Program level | Weighted by requirement criticality |

```sql
-- Compliance coverage per family (section) in a program
SELECT
    cr_parent.req_number,
    cr_parent.title,
    COUNT(cp.id) AS total,
    SUM(CASE WHEN cp.status = 'compliant' THEN 1 ELSE 0 END) AS compliant,
    CAST(SUM(CASE WHEN cp.status = 'compliant' THEN 1 ELSE 0 END) * 100.0
         / COUNT(cp.id) AS DECIMAL(5,1)) AS coverage_pct
FROM compliance_requirements cr_parent
JOIN compliance_requirements cr_child ON cr_child.parent_id = cr_parent.id
     AND cr_child.is_leaf = 1
LEFT JOIN compliance_postures cp ON cp.requirement_id = cr_child.id
     AND cp.program_id = @programId
WHERE cr_parent.framework_id = @frameworkId
GROUP BY cr_parent.req_number, cr_parent.title
ORDER BY coverage_pct ASC;
```

---

## 6. Compliance Exceptions

When a requirement cannot be met (technical limitation, business decision, or compensating control accepted), a formal exception is documented:

| Field Key | Label | Type |
|-----------|-------|------|
| `requirement` | Requirement | reference |
| `title` | Exception Title | text |
| `justification` | Business Justification | rich_text |
| `risk_accepted` | Risk Accepted | reference → Risk record |
| `compensating_control` | Compensating Control | reference → Control record |
| `exception_owner` | Exception Owner | user_reference |
| `approver` | Approved By | user_reference |
| `expiry_date` | Expiry Date | date |
| `renewal_review` | Renewal Review Date | date |
| `status` | Status | value_list: pending_approval, approved, expired, denied |

Exceptions feed into the compliance posture as `exception_approved` status.

---

## 7. Regulatory Change Management

When a regulation or standard is updated (e.g., GDPR amendment, PCI DSS 4.0 release):

1. New framework version is imported (alongside old version — not replacing it)
2. A **Gap Analysis Report** compares old vs. new version requirements
3. Requirements that changed or are new are flagged for review
4. Program owner creates a remediation plan for new/changed requirements

```sql
-- Track control gap when switching from old to new framework version
MATCH (new:ComplianceRequirement {framework_version:'v2'})-[:REPLACES]->(old:ComplianceRequirement)
WHERE NOT EXISTS {
    MATCH (new)<-[:IMPLEMENTS]-(:Control {status:'active'})
}
RETURN new.req_number, new.title, old.req_number AS previous_req
```

---

## 8. Evidence Management

Each compliance requirement can have evidence attached proving satisfaction:

- Evidence is linked via **record_relations** between compliance_posture records and specific record/attachment records
- Evidence types: control test results, policies, procedure documents, third-party attestations, screenshots
- Evidence expiry: timestamps on evidence used to flag stale evidence

---

## 9. Key Reports

| Report | Description |
|--------|-------------|
| Compliance Coverage Heatmap | Color-coded grid of requirement families by coverage % |
| Executive Compliance Summary | High-level scorecard by framework |
| Gap Analysis | Non-compliant and not-assessed requirements |
| Exception Register | All active exceptions with expiry dates |
| Regulatory Change Impact | New/changed requirements needing attention |
| Evidence Expiry Report | Evidence records expiring within 90 days |
| Multi-framework Crosswalk | Single control mapped to multiple framework requirements |

---

## 10. Cross-Framework Mapping

Many controls satisfy requirements across multiple frameworks. The platform maintains a **crosswalk** where a single control can map to:

- NIST SP 800-53 AC-2 (Account Management)
- ISO 27001 A.9.2.1 (User Registration)
- CIS Control 5.1 (Account Inventory)

This prevents duplicate remediation work and reveals where a single control investment satisfies multiple compliance obligations.

---

## 11. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should system-level frameworks be updateable by platform admins (not org admins)? | High |
| 2 | How to handle jurisdiction-specific appendices (e.g., GDPR + CCPA on same base standard)? | Medium |
| 3 | Automated compliance scoring from control effectiveness vs. manual assessment — which takes precedence? | Design |
| 4 | Should compliance programs be shareable across organizations? (Multi-org holding companies) | Low |

---

*Previous: [17 — Control Management](17-control-management.md) | Next: [19 — Issues & Findings](19-issues-findings.md)*
