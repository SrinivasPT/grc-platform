# Module 20 — Audit Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 07, Module 08 (Workflow), Module 17 (Control), Module 19 (Issues)

---

## 1. Domain Context

Audit Management orchestrates the full internal audit lifecycle — from audit universe and planning through fieldwork, observations, and final reporting. This module supports:

- **Internal Audit function** — Risk-based audit planning and execution
- **IT Audit** — Technology-focused reviews
- **Compliance Reviews** — Assessing adherence to policies and regulations
- **Control Assurance** — Testing whether controls are designed and operating effectively

This is distinct from the platform's own audit log (Module 09). This module is the business process for conducting audits, not the technical audit trail.

---

## 2. Audit Hierarchy

```
Audit Universe (all auditable areas)
    └── Audit Plan (annual plan)
        └── Audit Engagement (specific audit)
            ├── Audit Program (set of test procedures)
            │   └── Test Steps (individual procedures)
            └── Observations / Findings ──► Issues (Module 19)
                    └── Workpapers (evidence)
```

---

## 3. Audit Universe

The Audit Universe is the inventory of all areas the organization could audit. Each audit entity represents a business process, system, or area of the organization:

| Field Key | Label | Type |
|-----------|-------|------|
| `title` | Entity Name | text |
| `category` | Category | value_list: Business Process, IT System, Third Party, Regulatory Area |
| `business_unit` | Business Unit | reference |
| `inherent_risk_rating` | Inherent Risk Rating | value_list |
| `control_environment` | Control Environment Quality | value_list: strong, adequate, weak, unknown |
| `audit_priority` | Audit Priority | value_list: high, medium, low |
| `last_audit_date` | Last Audited | date |
| `next_audit_date` | Next Audit Due | date |
| `owner` | Process Owner | user_reference |
| `auditable_units` | Sub-Units | multi_reference | (self-referential) |

---

## 4. Audit Plan

An Audit Plan groups planned audit engagements for a period (typically 1 year):

| Field Key | Label | Type |
|-----------|-------|------|
| `title` | Plan Title | text (e.g., "2026 Internal Audit Plan") |
| `plan_year` | Plan Year | number |
| `plan_period_start` | Start Date | date |
| `plan_period_end` | End Date | date |
| `total_audit_days` | Total Audit Days | calculated |
| `status` | Status | value_list: draft, approved, active, closed |
| `approved_by` | Approved By | user_reference |
| `total_budget_hours` | Budget (hours) | number |

---

## 5. Audit Engagement

An Audit Engagement is a specific audit project:

| Field Key | Label | Type |
|-----------|-------|------|
| `title` | Audit Title | text |
| `audit_type` | Audit Type | value_list: operational, IT, compliance, financial, follow-up |
| `audit_plan` | Audit Plan | reference |
| `audit_universe_entity` | Auditable Entity | reference |
| `lead_auditor` | Lead Auditor | user_reference |
| `audit_team` | Audit Team | multi_user_reference |
| `planned_start` | Planned Start Date | date |
| `planned_end` | Planned End Date | date |
| `actual_start` | Actual Start Date | date |
| `actual_end` | Actual End Date | date |
| `scope` | Audit Scope | rich_text |
| `objectives` | Audit Objectives | rich_text |
| `methodology` | Methodology | value_list: risk-based, compliance, operational |
| `status` | Status | value_list: planned, fieldwork, reporting, final_report_issued, closed |
| `overall_rating` | Overall Audit Rating | value_list: satisfactory, needs_improvement, unsatisfactory, critical |
| `draft_report` | Draft Report | attachment |
| `final_report` | Final Report | attachment |
| `management_response` | Management Response | rich_text |

---

## 6. Audit Program & Test Steps

Each audit engagement is executed against an **Audit Program** — a set of test procedures the auditors run:

| Field Key (Test Step) | Label | Type |
|----------------------|-------|------|
| `parent_engagement` | Audit Engagement | reference |
| `title` | Test Step Title | text |
| `objective` | Test Objective | rich_text |
| `procedure` | Test Procedure | rich_text |
| `related_control` | Control Being Tested | reference |
| `assigned_to` | Assigned Auditor | user_reference |
| `status` | Status | value_list: not_started, in_progress, complete |
| `result` | Test Result | value_list: passed, failed, not_applicable |
| `conclusion` | Auditor Conclusion | rich_text |
| `evidence` | Evidence | attachment |
| `finding_ref` | Finding Reference | reference |

---

## 7. Audit Observations (Findings)

When a test step identifies a deficiency, an **Audit Observation** is raised. Observations aggregate into the Issues module:

| Field Key | Label | Type |
|-----------|-------|------|
| `parent_engagement` | Audit Engagement | reference |
| `test_step` | Test Step | reference |
| `title` | Observation Title | text |
| `condition` | Condition (What we found) | rich_text |
| `criteria` | Criteria (What should be) | rich_text |
| `cause` | Cause (Why it happened) | rich_text |
| `effect` | Effect (Risk/Impact) | rich_text |
| `severity` | Severity | value_list: critical, high, medium, low |
| `management_response` | Management Response | rich_text |
| `agreed_action_date` | Agreed Remediation Date | date |
| `action_owner` | Action Owner | user_reference |
| `issue_record` | Created Issue | reference | Auto-created Issue |

When an observation is finalized, an **Issue record** is automatically created in Module 19, with:
- `issue_type = audit_observation`
- `source_audit = this engagement`
- `severity = this observation's severity`
- `owner = action_owner`
- `due_date = agreed_action_date`

---

## 8. Audit Lifecycle

```
[Planning]
  Audit Universe ──► Annual Plan ──► Engagement Planned
                                            │
[Fieldwork]                                 ▼
  ┌─────────────────────────────── Fieldwork In Progress
  │  Execute test steps, collect evidence, raise observations
  │                                         │
[Reporting]                                 ▼
  ├──── Draft Report Issued ──► Management Response ──► Final Report Issued
  │
[Follow-up]
  └──── Follow-up Audit ──► Verify closure of prior observations
```

### 8.1 Follow-up Engagement

When prior audit observations have agreed action dates, a follow-up engagement can be scheduled to verify closure. The follow-up tests are linked back to the original observations and auto-close the corresponding Issues.

---

## 9. Audit Resource Planning

Track auditor time against the budget:

```sql
CREATE TABLE audit_time_tracking (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    engagement_id   UNIQUEIDENTIFIER  NOT NULL,
    auditor_id      UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    work_date       DATE              NOT NULL,
    hours_worked    DECIMAL(4,2)      NOT NULL,
    activity_desc   NVARCHAR(500)     NULL
);
```

---

## 10. Key Reports

| Report | Description |
|--------|-------------|
| Annual Audit Plan Status | Engagements planned vs. completed |
| Audit Universe Coverage | % of high-risk entities audited in 12 months |
| Open Observations Tracker | All open findings with days overdue |
| Follow-up Status | Prior period observations and closure status |
| Repeat Observation Rate | % of findings that recur across audit cycles |
| Auditor Utilization | Hours by auditor vs. budget |
| Observations by Severity | Count and trend of findings by severity |

---

## 11. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should audit programs be templated (reusable across engagements)? | High |
| 2 | Should external auditor findings be tracked separately or in the same engagement? | Medium |
| 3 | Continuous auditing — polling data automatically to detect anomalies? | Future |
| 4 | Should audit reports be generated in-platform (PDF from template) or uploaded as files? | Medium |

---

*Previous: [19 — Issues & Findings](19-issues-findings.md) | Next: [21 — Vendor & Third-Party Risk](21-vendor-third-party-risk.md)*
