# Module 19 — Issues & Findings

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 08 (Workflow), Module 16 (Risk), Module 17 (Control)

---

## 1. Domain Context

Issues and Findings capture identified problems, weaknesses, deficiencies, and audit findings that require remediation. They are the operational output of:

- **Control testing failures** (Module 17) — a control test fails, producing a finding
- **Internal audit reviews** (Module 20) — auditors raise observations
- **Risk events** — a risk materializes and produces an issue
- **External audit / assessment results** — SOC 2, ISO audit, penetration test, regulatory exam
- **Ad hoc identification** — any employee or manager can raise an issue

Issues track remediation progress through a structured workflow, ensuring nothing falls through the cracks.

---

## 2. Issue Types

| Type | Description | Source |
|------|-------------|--------|
| **Control Finding** | Control test failure or deficiency | Control testing |
| **Audit Observation** | Finding from internal/external audit | Audit Management |
| **Policy Violation** | Breach of a policy requirement | Various |
| **Compliance Gap** | A compliance requirement not being met | Compliance Management |
| **Risk Event** | A risk that has materialized | Risk Management |
| **Security Finding** | Penetration test or vulnerability assessment finding | Security team / Vulnerability Mgmt |
| **Ad Hoc Issue** | General issue raised by any user | Any |

---

## 3. Entity Design — Issues Application

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `title` | Issue Title | text | |
| `issue_type` | Issue Type | value_list | Types above |
| `severity` | Severity | value_list | critical, high, medium, low, informational |
| `description` | Description | rich_text | |
| `root_cause` | Root Cause | rich_text | Identified root cause |
| `root_cause_category` | Root Cause Category | value_list | People, Process, Technology, Third Party |
| `owner` | Issue Owner | user_reference | |
| `assignee` | Assigned To | user_reference | |
| `business_unit` | Business Unit | reference | |
| `source_control` | Source Control | reference | If from control test |
| `source_audit` | Source Audit | reference | If from audit |
| `source_risk` | Source Risk | reference | |
| `related_policy` | Related Policy | reference | |
| `related_framework_req` | Framework Requirement | reference | |
| `due_date` | Remediation Due Date | date | |
| `priority_score` | Priority Score | calculated | Derived from severity × age × business_impact (0–100) |
| `status` | Status | value_list | open, in_progress, pending_verification, closed, exception_requested, cancelled |
| `closure_date` | Closure Date | date | |
| `closure_notes` | Closure Evidence | rich_text | |
| `closure_evidence` | Evidence | attachment | |
| `age_days` | Age (Days) | calculated | |
| `is_repeat_finding` | Repeat Finding | boolean | Was this issue previously identified? |
| `previous_instance` | Previous Issue | reference | |

---

## 4. Issue Lifecycle

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    ISSUE LIFECYCLE                       │
                    └─────────────────────────────────────────────────────────┘

[Identified]  ──Open────► In Progress ──► Pending Verification ──► Closed
                               │                    │
                               │              (Verification Fails)
                               │                    │
                               │◄───────────────────┘
                               │
                               └──► Exception Requested ──► Exception Approved ──► Closed (exception)
                                                        └──► Exception Denied  ──► In Progress
```

### 4.1 Workflow Definition (Issue Remediation)

```json
{
  "key": "issue_remediation",
  "name": "Issue Remediation Workflow",
  "states": [
    { "key": "open",                 "label": "Open",                  "is_initial": true },
    { "key": "in_progress",          "label": "In Progress" },
    { "key": "pending_verification", "label": "Pending Verification" },
    { "key": "closed",               "label": "Closed",                "is_terminal": true },
    { "key": "exception_requested",  "label": "Exception Requested" },
    { "key": "cancelled",            "label": "Cancelled",             "is_terminal": true }
  ],
  "transitions": [
    {
      "from": "open",                "to": "in_progress",
      "label": "Assign & Begin Remediation",
      "actors": [{ "type": "role", "role": "issue_manager" }]
    },
    {
      "from": "in_progress",        "to": "pending_verification",
      "label": "Submit for Verification",
      "actors": [{ "type": "field", "field": "assignee" }],
      "requires_comment": true,
      "requires_attachment": true
    },
    {
      "from": "pending_verification","to": "closed",
      "label": "Verify & Close",
      "actors": [{ "type": "field", "field": "owner" }],
      "sla_days": 5,
      "on_enter_actions": [
        { "type": "set_field", "field": "closure_date", "expression": "today()" }
      ]
    },
    {
      "from": "pending_verification","to": "in_progress",
      "label": "Return for Rework",
      "actors": [{ "type": "field", "field": "owner" }],
      "requires_comment": true
    }
  ]
}
```

### 4.2 Root Cause Verification Workflow

For issues with `severity = critical` or `high`, root cause closure requires **two-person verification**:

1. The issue `assignee` submits their remediation evidence and enters a root cause analysis (`root_cause` field must be filled) — transitions to `pending_verification`.
2. A **second user** (the issue `owner` or a designated `root_cause_verifier` role) must review the root cause analysis and verify that the remediation addresses the actual root cause.
3. Only after verification can the issue transition to `closed`. The `owner` cannot self-verify if they are also the `assignee`.

The root cause verifier's identity and verification notes are recorded in the workflow history (Part of the audit trail in Module 09).

```java
// Enforced in WorkflowTransitionService
if ("pending_verification".equals(fromState) && "closed".equals(toState)) {
    if (issue.getSeverity() == Severity.CRITICAL || issue.getSeverity() == Severity.HIGH) {
        if (currentUser.getId().equals(issue.getAssigneeId())) {
            throw new WorkflowValidationException(
                "Root cause verification must be performed by a different user than the assignee.");
        }
    }
}
```

---

## 5. SLA Tracking

Issues have mandatory remediation SLAs based on severity:

| Severity | Default SLA | Escalation at |
|----------|------------|---------------|
| Critical | 7 days | 3 days (50% SLA) |
| High | 30 days | 20 days (67% SLA) |
| Medium | 90 days | 60 days (67% SLA) |
| Low | 180 days | 120 days (67% SLA) |
| Informational | No SLA | — |

SLAs are configurable per organization. The SLA timer runs on calendar days from `open` state. Paused when in `exception_requested` state.

---

## 6. Remediation Actions (Child Records)

Like risks, issues can have multiple **remediation action items** as child records:

| Field | Type |
|-------|------|
| `parent_issue` | reference |
| `title` | text |
| `assignee` | user_reference |
| `due_date` | date |
| `status` | value_list: open, in_progress, complete |
| `evidence` | attachment |

When all action items are `complete`, the issue workflow transitions to `pending_verification` automatically.

---

## 7. Repeat Finding Detection

When an issue is created from a control test failure, the system checks:
1. Has the same control (`source_control`) had a finding in the last 365 days?
2. If yes: auto-set `is_repeat_finding = true` and link `previous_instance`

Repeat findings are treated as higher severity in reporting and escalated to senior management.

---

## 8. Key Metrics & Reports

| Metric | Description |
|--------|-------------|
| Open Issues by Severity | Current open issue count by severity |
| Issues Past Due | Count of issues past their SLA due date |
| Mean Time to Close (MTTC) | Average days from open to close by severity |
| Repeat Finding Rate | % of issues that are repeat occurrences |
| Issues by Owner | Open issue distribution by owner (workload view) |
| Closed Issues Trend | Issues closed per month (remediation velocity) |
| Exception Rate | % of issues closed via exception vs remediation |
| Issues by Source | Breakdown by issue type / source module |

---

## 9. Graph Relationships (Neo4j)

```cypher
(:Issue)-[:RAISED_FOR]->(:Control)
(:Issue)-[:RAISED_IN]->(:Audit)
(:Issue)-[:EVIDENCE_OF]->(:Risk)
(:Issue)-[:VIOLATES]->(:Policy)
(:Issue)-[:GAPS]->(:ComplianceRequirement)
(:Issue)-[:REPEAT_OF]->(:Issue)

// Impact: number of open critical issues per business unit
MATCH (bu:OrgUnit)<-[:BELONGS_TO]-(i:Issue)
WHERE i.severity = 'critical' AND i.status IN ['open','in_progress']
RETURN bu.name, count(i) AS critical_issue_count
ORDER BY critical_issue_count DESC
```

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should exceptions be their own record type / sub-module? | Medium | |
| 2 | Should issues have a public-facing portal for employees to report incidents? | Low | |
| 3 | Integration with IT Service Management — automatic Jira/ServiceNow ticket on creation? | High | |
| 4 | Should repeat finding detection look across organizations? | Low | N/A — single bank deployment. |

---

*Previous: [18 — Compliance Management](18-compliance-management.md) | Next: [20 — Audit Management](20-audit-management.md)*
