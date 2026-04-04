# Module 15 — Policy Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 03 (Rule Engine), Module 07 (Auth), Module 08 (Workflow), Module 10 (Notifications), Module 12 (Reporting)

---

## 1. Domain Context

Policy Management governs the creation, approval, publication, distribution, and periodic review of an organization's formal policies, standards, procedures, and guidelines. It is foundational to all other GRC domains — controls, compliance programs, and risk assessments all reference and derive authority from policies.

**Key questions this module answers:**
- What policies does the organization have, and are they current?
- Which policies apply to which business units or systems?
- Have employees acknowledged the policies they are required to read?
- When was each policy last reviewed and by whom?
- How do policies map to regulatory requirements?

---

## 2. Content Hierarchy

| Type | Description | Example |
|------|-------------|---------|
| **Policy** | High-level mandatory directive | Information Security Policy |
| **Standard** | Specific mandatory requirements | Password Standard |
| **Procedure** | Step-by-step instructions | Account Provisioning Procedure |
| **Guideline** | Recommended (not mandatory) guidance | Remote Work Guideline |
| **Framework** | External compliance framework | NIST CSF, ISO 27001 |

Hierarchy: Policy → Standard → Procedure (a procedure implements a standard, which implements a policy).

---

## 3. Entity Design

### 3.1 Policy Application (Configured in App Builder)

The Policy Application is a configured GRC Application with the following field definitions:

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `title` | Policy Title | text | Required |
| `policy_type` | Type | value_list | policy, standard, procedure, guideline |
| `status` | Status | value_list | draft, under_review, approved, published, archived, retired |
| `version` | Version | text | Semantic: 1.0, 1.1, 2.0 |
| `effective_date` | Effective Date | date | When published version takes effect |
| `review_date` | Next Review Date | date | Scheduled review — SLA tracked by workflow |
| `owner` | Policy Owner | user_reference | Responsible person |
| `approver` | Approver | user_reference | Who must approve |
| `business_unit` | Business Unit | reference | Links to org_units |
| `scope` | Scope | rich_text | What this policy applies to |
| `exceptions_process` | Exception Process | rich_text | How exceptions are requested |
| `document` | Policy Document | attachment | Uploaded PDF/Word document |
| `regulation_refs` | Regulation References | multi_reference | Links to compliance requirements |
| `related_controls` | Related Controls | multi_reference | Controls that implement this policy |
| `acknowledgment_required` | Require Acknowledgment | boolean | Whether users must formally acknowledge |

### 3.2 Supporting Table: Policy Acknowledgments

Tracks individual user acknowledgments (separate from the main records table due to volume):

```sql
CREATE TABLE policy_acknowledgments (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    policy_record_id UNIQUEIDENTIFIER NOT NULL,  -- FK to records(id)
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    acknowledged_at DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    policy_version  NVARCHAR(50)      NOT NULL,
    method          NVARCHAR(50)      NOT NULL DEFAULT 'platform'
                    CHECK (method IN ('platform','email_link','sso_assertion')),
    ip_address      NVARCHAR(50)      NULL
);

CREATE INDEX ix_ack_policy_record ON policy_acknowledgments(org_id, policy_record_id);
CREATE INDEX ix_ack_user ON policy_acknowledgments(org_id, user_id);
```

---

## 4. Policy Lifecycle

```
            ┌──────────────────────────────────────────────┐
            │                 POLICY LIFECYCLE              │
            └──────────────────────────────────────────────┘

[Author]    Draft ──────────────────► Submit for Review
            (editing, version bump)       │
                     ▲                    ▼
                     │           Under Review ─── Reject ──► Draft
                     │                    │
                     │                    ▼
                     │                Approved ──────────────► Published
                     │                                              │
                     │                                      (notify & distribute)
                     │                                              │
                     └──────── Create new draft ◄── Review Due ────┤
                                                                    │
                                                      Retire ───────┘
```

### 4.1 Workflow Definition (Policy Approval)

```json
{
  "key": "policy_approval",
  "name": "Policy Approval Workflow",
  "states": [
    { "key": "draft",          "label": "Draft",           "is_initial": true },
    { "key": "under_review",   "label": "Under Review" },
    { "key": "approved",       "label": "Approved" },
    { "key": "published",      "label": "Published" },
    { "key": "needs_revision", "label": "Needs Revision" },
    { "key": "retired",        "label": "Retired",         "is_terminal": true }
  ],
  "transitions": [
    {
      "from": "draft",        "to": "under_review",
      "label": "Submit for Review",
      "actors": [{ "type": "field", "field": "owner" }]
    },
    {
      "from": "under_review", "to": "approved",
      "label": "Approve",
      "actors": [{ "type": "field", "field": "approver" }],
      "on_enter_actions": [
        { "type": "notify", "template": "policy_approved_notify" }
      ]
    },
    {
      "from": "under_review", "to": "needs_revision",
      "label": "Request Revision",
      "actors": [{ "type": "field", "field": "approver" }],
      "requires_comment": true
    },
    {
      "from": "approved",     "to": "published",
      "label": "Publish",
      "actors": [{ "type": "field", "field": "owner" }],
      "on_enter_actions": [
        { "type": "notify", "template": "policy_published_notify" },
        { "type": "set_field", "field": "effective_date", "expression": "today()" }
      ]
    }
  ]
}
```

---

## 5. Policy Review Scheduling

Each published policy has a `review_date`. A background job (daily at 6 AM UTC) checks for policies due for review:

- **30 days before:** Notify policy owner: "Policy review due in 30 days"
- **7 days before:** Notify policy owner and org admin
- **On due date:** Automatically create a draft revision record linked to the current version
- **Overdue:** Flag policy as overdue; report in Policy Review Status dashboard

---

## 6. Policy Distribution and Acknowledgment

When a policy is published with `acknowledgment_required = true`:

1. Determine the target user population (all users, specific roles, specific org units)
2. Send acknowledgment request notifications (email + in-app)
3. Track acknowledgment per user per policy version
4. Reminder notifications for non-responders at 7-day and 14-day intervals
5. **Auto re-acknowledgment:** When a policy is updated to a new version, all previously-acknowledged users must re-acknowledge the new version. A new `acknowledgment_campaign` is automatically created. The 7/14-day escalation sequence restarts.
6. **Manager escalation:** If a user does not acknowledge within 21 days, an escalation notification is sent to their direct manager (resolved via the org unit hierarchy from Module 26).

**Acknowledgment Campaign tracking:**

```sql
CREATE TABLE acknowledgment_campaigns (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    policy_record_id UNIQUEIDENTIFIER NOT NULL,
    policy_version  NVARCHAR(50)      NOT NULL,
    target_type     NVARCHAR(50)      NOT NULL  -- 'all_users', 'role', 'org_unit'
                    CHECK (target_type IN ('all_users','role','org_unit','custom_list')),
    target_ids      NVARCHAR(MAX)     NULL,      -- JSON: list of role/unit IDs
    due_date        DATE              NOT NULL,
    escalation_date DATE              NULL,      -- date when manager escalation triggers (due_date + 21 days)
    total_targets   INT               NOT NULL DEFAULT 0,
    completed_count INT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

---

## 7. Policy-to-Framework Mapping

Policies are linked to external compliance requirements via the `regulation_refs` multi-reference field. This relationship is the foundation of the compliance coverage report:

```
ISO 27001 A.5.1.1 → Information Security Policy
                   → Acceptable Use Policy
ISO 27001 A.9.4.3 → Password Standard
SOX IT Control 1  → Change Management Policy
                  → Change Management Procedure
```

---

## 8. Reporting & KPIs

| Metric | Description |
|--------|-------------|
| Policy Review Rate | % policies with current review (review_date not past) |
| Acknowledgment Rate | % required users who acknowledged latest version |
| Policy Coverage | % compliance requirements mapped to at least one policy |
| Overdue Reviews | Count of policies past review date |
| Policies by Status | Breakdown of draft/review/published counts |

Default dashboard: **Policy Management Dashboard** (pre-built widget grid).

---

## 9. Graph Relationships (Neo4j)

```cypher
// Policy relationships
(:Policy)-[:IMPLEMENTS]->(:ComplianceRequirement)
(:Policy)-[:IMPLEMENTED_BY]->(:Control)
(:Control)-[:DERIVED_FROM]->(:Policy)
(:Policy)-[:SUPERSEDES]->(:Policy)   // version lines
(:Policy)-[:APPLIES_TO]->(:OrgUnit)
```

Impact query — "if this policy is retired, what compliance requirements have no other mapped policy?":
```cypher
MATCH (p:Policy {id: $policyId})-[:IMPLEMENTS]->(cr:ComplianceRequirement)
WHERE NOT EXISTS {
  MATCH (p2:Policy)-[:IMPLEMENTS]->(cr)
  WHERE p2.id <> $policyId AND p2.status = 'published'
}
RETURN cr
```

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should policy documents be managed as files (attachments) or as rich-text within the platform? | High | |
| 2 | Policy exception workflow: employees request exceptions from a policy — is this its own module? | Medium | |
| 3 | ~~Should acknowledgment campaigns support custom expiry / annual re-acknowledgment?~~ | Medium | **Resolved:** Auto re-acknowledgment on policy version update. Manager escalation after 21 days of non-response. See Section 6. |
| 4 | Watermarking of downloaded policy PDFs with user identity? | Low | |

---

*Previous: [14 — Integration Framework](14-integration-framework.md) | Next: [16 — Risk Management](16-risk-management.md)*
