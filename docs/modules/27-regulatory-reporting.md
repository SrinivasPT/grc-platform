# Module 27 — Regulatory Reporting

## 1. Purpose

The Regulatory Reporting module generates, validates, and submits formal regulatory filings required of the bank. It differs from the internal dashboards of Module 12 in that:

- Report formats are **mandated by regulators** (Basel III, COREP, FINREP, FR Y-14, BRRD, local central bank formats)
- Submissions are **signed off** through a structured preparer → reviewer → approver workflow before submission
- A complete **submission history** is retained with the original filed document and metadata
- Data is **auto-populated** from GRC modules (risk scores, control coverage, incident counts, capital requirements)

---

## 2. Data Model

### 2.1 Report Template Registry

```sql
CREATE TABLE regulatory_report_templates (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    template_key    NVARCHAR(200)     NOT NULL,   -- e.g. 'corep_c_07_00', 'frb_y14_q'
    display_name    NVARCHAR(500)     NOT NULL,
    jurisdiction    NVARCHAR(100)     NOT NULL,   -- 'EU', 'US', 'UK', 'SG', 'HK', 'LOCAL'
    framework       NVARCHAR(100)     NOT NULL
                    CHECK (framework IN ('basel3','corep','finrep','fr_y14',
                                         'brrd','ifrs9','local_central_bank','custom')),
    report_format   NVARCHAR(50)      NOT NULL
                    CHECK (report_format IN ('xbrl','xml','csv','json','pdf')),
    -- JSON schema describing field_key -> GRC data source mapping
    field_mappings  NVARCHAR(MAX)     NOT NULL,   -- JSON
    -- Cron expression for submission schedule, e.g. "0 0 1 1/3 * ?" for quarterly
    schedule_cron   NVARCHAR(100)     NULL,
    is_active       BIT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_rrt_key UNIQUE (org_id, template_key)
);
```

### 2.2 Field Mapping Format

Each `field_mappings` JSON entry describes where to fetch data in the GRC platform:

```json
{
  "fields": [
    {
      "xbrl_concept": "ifrs-full:CapitalRequirements",
      "field_key":    "tier1_capital_ratio",
      "label":        "Tier 1 Capital Ratio",
      "source":       "risk_metric",
      "query":        "SELECT metric_value FROM risk_kpi_snapshots WHERE metric_key = 'tier1_capital_ratio' AND snapshot_date = :period_end",
      "data_type":    "decimal",
      "unit":         "percentage"
    },
    {
      "xbrl_concept": "corep:NumberOfOpenCriticalFindings",
      "field_key":    "open_critical_findings",
      "label":        "Open Critical Issues/Findings",
      "source":       "grc_query",
      "query":        "SELECT COUNT(*) FROM issues WHERE severity = 'critical' AND status != 'closed' AND created_at <= :period_end",
      "data_type":    "integer"
    }
  ]
}
```

### 2.3 Submission Lifecycle

```sql
CREATE TABLE regulatory_report_submissions (
    id                  UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id              UNIQUEIDENTIFIER  NOT NULL,
    template_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES regulatory_report_templates(id),
    period_start        DATE              NOT NULL,
    period_end          DATE              NOT NULL,
    status              NVARCHAR(50)      NOT NULL DEFAULT 'draft'
                        CHECK (status IN ('draft','prepared','under_review','approved',
                                           'submitted','rejected','superseded')),
    -- Data snapshot auto-populated from GRC
    data_snapshot       NVARCHAR(MAX)     NULL,    -- JSON: field_key -> resolved_value
    -- Generated file
    report_file_id      UNIQUEIDENTIFIER  NULL,    -- FK to record_attachments (Module 13)
    report_checksum     NVARCHAR(128)     NULL,    -- SHA-256 of the filed document
    -- Workflow
    prepared_by         UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    prepared_at         DATETIME2         NULL,
    reviewed_by         UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    reviewed_at         DATETIME2         NULL,
    review_notes        NVARCHAR(MAX)     NULL,
    approved_by         UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    approved_at         DATETIME2         NULL,
    -- Submission
    submitted_by        UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    submitted_at        DATETIME2         NULL,
    regulator_ref       NVARCHAR(500)     NULL,    -- Reference number returned by regulator portal
    submission_method   NVARCHAR(100)     NULL,    -- 'xbrl_api', 'sftp', 'portal_upload', 'manual'
    -- Versioning
    version             INT               NOT NULL DEFAULT 1,
    previous_version_id UNIQUEIDENTIFIER  NULL REFERENCES regulatory_report_submissions(id),
    is_amendment        BIT               NOT NULL DEFAULT 0,
    created_at          DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE INDEX ix_rrs_template ON regulatory_report_submissions(template_id, period_end, status);
CREATE INDEX ix_rrs_approved  ON regulatory_report_submissions(approved_by, approved_at);
```

### 2.4 Sign-Off Comments

```sql
CREATE TABLE regulatory_report_comments (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    submission_id   UNIQUEIDENTIFIER  NOT NULL REFERENCES regulatory_report_submissions(id),
    field_key       NVARCHAR(200)     NULL,        -- if comment relates to a specific field
    comment_type    NVARCHAR(50)      NOT NULL CHECK (comment_type IN ('review','approval','rejection','clarification')),
    comment_text    NVARCHAR(MAX)     NOT NULL,
    commented_by    UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    commented_at    DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

---

## 3. Sign-Off Workflow

Each submission follows a **three-stage sign-off**:

```
┌──────────────────────────────────────────────────────────────────┐
│  DRAFT          →   PREPARED    →   UNDER_REVIEW   →  APPROVED  │
│  (auto-pop)         (preparer)       (reviewer)       (approver) │
│                                                                  │
│  APPROVED  →  SUBMITTED  (preparer or approver files with        │
│                           regulator)                             │
│                                                                  │
│  UNDER_REVIEW  →  DRAFT  (reviewer sends back for correction)    │
│  APPROVED      →  DRAFT  (approver rejects)                      │
└──────────────────────────────────────────────────────────────────┘
```

**Role requirements:**

| Transition | Required Permission |
|-----------|-------------------|
| `draft → prepared` | `REG_REPORT_PREPARE` |
| `prepared → under_review` | `REG_REPORT_REVIEW` (reviewer ≠ preparer) |
| `under_review → approved` | `REG_REPORT_APPROVE` (approver ≠ reviewer ≠ preparer) |
| `approved → submitted` | `REG_REPORT_SUBMIT` |

Segregation of duties is enforced: the same user cannot occupy two roles on a single submission.

```java
@Service
public class RegulatoryReportWorkflowService {
    public SubmissionResult transition(UUID submissionId, String toStatus, UUID actorId) {
        RegulatoryReportSubmission sub = repo.findById(submissionId).orElseThrow();
        validateTransition(sub, toStatus, actorId);
        enforceSegregationOfDuties(sub, toStatus, actorId);
        applyTransition(sub, toStatus, actorId);
        return new SubmissionResult(sub);
    }

    private void enforceSegregationOfDuties(RegulatoryReportSubmission sub,
                                             String toStatus, UUID actorId) {
        if ("under_review".equals(toStatus) && actorId.equals(sub.getPreparedBy())) {
            throw new WorkflowValidationException(
                "Reviewer cannot be the same as the preparer.");
        }
        if ("approved".equals(toStatus)) {
            if (actorId.equals(sub.getPreparedBy()) || actorId.equals(sub.getReviewedBy())) {
                throw new WorkflowValidationException(
                    "Approver must differ from preparer and reviewer.");
            }
        }
    }
}
```

---

## 4. Data Auto-Population

When a submission is created (or refreshed), the reporting engine resolves all `field_mappings` queries against the GRC database for the specified `period_end`:

```java
@Service
public class ReportDataPopulationService {
    public Map<String, Object> populate(RegulatoryReportTemplate template,
                                        LocalDate periodStart, LocalDate periodEnd) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<FieldMapping> fields = parseFieldMappings(template.getFieldMappings());

        for (FieldMapping field : fields) {
            Object value = switch (field.getSource()) {
                case "risk_metric"  -> riskMetricService.getMetric(field.getQuery(), periodEnd);
                case "grc_query"    -> jdbcTemplate.queryForObject(
                    field.getQuery(),
                    Map.of("period_end", periodEnd, "period_start", periodStart),
                    Object.class);
                case "manual"       -> null;   // must be entered by preparer
                default -> throw new IllegalArgumentException("Unknown source: " + field.getSource());
            };
            snapshot.put(field.getFieldKey(), value);
        }
        return snapshot;
    }
}
```

Fields with `"source": "manual"` are left null in the snapshot and flagged in the UI for the preparer to complete.

---

## 5. XBRL Rendering

XBRL (eXtensible Business Reporting Language) is the mandatory format for regulatory filings in the EU (COREP/FINREP) and increasingly elsewhere (FR Y-14 XBRL pilot).

The platform generates XBRL instances using the **Arelle XBRL toolkit** via a REST sidecar service, or a Java-based template renderer for simpler reports:

```
GRC Data Snapshot  ─►  XBRL Renderer  ─►  .xbrl / .zip (taxonomy + instance document)
                         (Arelle API 
                          or Java)
```

**Supported taxonomies (via configurable taxonomy packs):**

| Framework | Taxonomy | Formats |
|-----------|---------|---------|
| Basel III | BIS XBRL taxonomy | XBRL instance (.xbrl) |
| COREP | EBA XBRL 3.x | XBRL + ZIP (ITS taxonomy) |
| FINREP | EBA FINREP XBRL | XBRL + ZIP |
| FR Y-14 | FRB XBRL pilot schema | XML/XBRL |
| BRRD | EBA resolution taxonomy | XBRL |

**Taxonomy source:** Taxonomy ZIPs are downloaded from the official regulator websites, stored in the platform's file store (Module 13), and resolved at render time. They are versioned — submissions reference the taxonomy version they were rendered with.

---

## 6. Submission Delivery

| Method | When Used |
|--------|-----------|
| **XBRL API push** | EBA/SNB XBRL upload endpoints (COREP, FINREP) |
| **SFTP** | Legacy central bank formats |
| **Portal upload** | US Fed reporting portals (manual upload with confirmation ref) |
| **Manual / out-of-band** | Preparer downloads the file and submits separately |

The `submission_method` is recorded on the submission record. For API-based delivery, the `regulator_ref` is populated from the API response. For manual delivery, the preparer enters it.

---

## 7. Amendment Workflow

If a previously submitted report needs correction (e.g., restatement):

1. A new submission record is created with `is_amendment = true` and `previous_version_id` pointing to the original
2. The amended submission goes through the full sign-off workflow
3. On approval, the original submission is moved to `superseded` status
4. Both versions are retained in full for audit trail purposes

---

## 8. GraphQL API

```graphql
type RegulatoryReportTemplate {
  id:           UUID!
  templateKey:  String!
  displayName:  String!
  jurisdiction: String!
  framework:    String!
  reportFormat: String!
  scheduleDescription: String
  submissions(periodYear: Int): [RegulatoryReportSubmission!]!
}

type RegulatoryReportSubmission {
  id:           UUID!
  template:     RegulatoryReportTemplate!
  periodStart:  Date!
  periodEnd:    Date!
  status:       SubmissionStatus!
  preparedBy:   User
  reviewedBy:   User
  approvedBy:   User
  submittedAt:  DateTime
  regulatorRef: String
  isAmendment:  Boolean!
  reportFile:   FileRef
}

type Query {
  regulatoryReportTemplates:                    [RegulatoryReportTemplate!]!
  regulatoryReportSubmission(id: UUID!):        RegulatoryReportSubmission
  upcomingReportDeadlines(withinDays: Int = 30):[RegulatoryReportTemplate!]!
}

type Mutation {
  createReportSubmission(templateId: UUID!, periodStart: Date!, periodEnd: Date!): RegulatoryReportSubmission!
  refreshReportData(submissionId: UUID!):       RegulatoryReportSubmission!
  transitionSubmission(id: UUID!, toStatus: SubmissionStatus!, notes: String): RegulatoryReportSubmission!
  recordManualField(submissionId: UUID!, fieldKey: String!, value: String!): RegulatoryReportSubmission!
}
```

---

## 9. Notifications and Deadlines

The Notification Engine (Module 10) sends automated reminders for upcoming regulatory filing deadlines:

| Trigger | Notification | Channel |
|---------|-------------|---------|
| 14 days before `period_end` | "Report due in 14 days: {report_name}" | Email |
| 7 days before `period_end` | Same, escalated to approver | Email |
| 1 day before `period_end` | Urgent — report not submitted | Email + In-App |
| Report submitted successfully | Confirmation with `regulator_ref` | Email |
| Review rejection | "Submission returned for correction" | Email + In-App |

---

## 10. Cross-Module Data Sources

| Data | Source Module |
|------|-------------|
| Capital adequacy data (Tier 1/Tier 2) | Risk Management (Module 16) |
| Number of open critical findings | Issues & Findings (Module 19) |
| Internal audit completion rate | Audit Management (Module 20) |
| Regulatory compliance coverage % | Compliance Management (Module 18) |
| Operational incident counts by category | Incident Management (Module 22) |
| Control effectiveness ratings | Control Management (Module 17) |
| Vendor risk summary | Vendor & Third-Party Risk (Module 21) |

---

## 11. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should the platform connect directly to EBA XBRL API, or use a middleware (e.g., ClayTablet, Invoke)? | High | |
| 2 | How are custom local central bank formats (non-XBRL) templated — Excel-to-XML mapping? | High | Use `field_mappings` with `report_format = 'csv'` or `'xml'` and a handlebars template. |
| 3 | Should report submissions be immutable after `submitted` status? | Medium | Yes — amendments require new version with `is_amendment = true`. |
| 4 | Should the platform notify the compliance officer when a taxonomy pack update is published? | Low | |

---

*Previous: [26 — Organizational Hierarchy](26-org-hierarchy.md)*
