# Module 22 — Incident Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 07, Module 08 (Workflow), Module 10 (Notifications), Module 16 (Risk), Module 19 (Issues)

---

## 1. Domain Context

Incident Management tracks the occurrence, investigation, containment, and recovery from adverse events that impact (or threaten to impact) the organization's assets, data, operations, or reputation. In GRC, incidents include:

- **Cybersecurity incidents** — Data breaches, malware, phishing successes, unauthorized access
- **Privacy incidents** — Personal data exposure, GDPR Article 33 reportable breaches
- **Operational incidents** — System outages, process failures, fraud
- **Vendor-induced incidents** — Third-party failures affecting the organization
- **Physical security incidents** — Facility breaches, hardware theft

This module supports NIST SP 800-61, ISO 27035, and GDPR incident notification timelines.

---

## 2. Entity Design — Incident Application

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `title` | Incident Title | text | |
| `incident_type` | Type | value_list | cybersecurity, privacy, operational, vendor, physical, other |
| `severity` | Severity | value_list | critical, high, medium, low |
| `status` | Status | value_list | detected, triaged, containment, investigation, recovery, closed, false_positive |
| `description` | Description | rich_text | |
| `discovery_date` | Discovery Date | datetime | When incident was detected |
| `occurrence_date` | Occurrence Date | datetime | When incident actually started (if known) |
| `reporter` | Reported By | user_reference | |
| `incident_commander` | Incident Commander | user_reference | Lead responder |
| `response_team` | Response Team | multi_user_reference | |
| `affected_systems` | Affected Systems | multi_reference | |
| `affected_data` | Data Types Affected | multi_value | PII, Financial, PHI, IP, Credentials |
| `records_affected` | Records Affected (count) | number | For breach notification calculations |
| `individuals_affected` | Individuals Affected | number | |
| `impact_category` | Impact Category | multi_value | Confidentiality, Integrity, Availability |
| `business_impact` | Business Impact | rich_text | |
| `root_cause` | Root Cause | rich_text | Post-remediation |
| `root_cause_category` | Root Cause Category | value_list | People, Process, Technology, Third Party |
| `related_vendor` | Related Vendor | reference | |
| `related_vulnerability` | Related Vulnerability | reference | |
| `linked_risk` | Realized Risk | reference | Risk that materialized |
| `regulatory_notification` | Notification Required | boolean | |
| `notification_deadline` | Notification Deadline | datetime | |
| `notification_sent_at` | Notification Sent | datetime | |
| `containment_date` | Contained At | datetime | |
| `recovery_date` | Recovered At | datetime | |
| `closure_date` | Closed At | datetime | |
| `lessons_learned` | Lessons Learned | rich_text | |
| `evidence` | Evidence | attachment | |

---

## 3. Incident Severity Definitions

| Severity | Criteria | Response SLA |
|----------|---------|-------------|
| **Critical (P1)** | Active breach; data confirmed exfiltrated; major operational outage (> 50% systems) | Immediate (war room within 15 min) |
| **High (P2)** | Potential data exposure; contained breach; significant outage (> 20% systems) | 1 hour |
| **Medium (P3)** | Suspicious activity; limited impact; localized outage | 4 hours |
| **Low (P4)** | Minor anomaly; no confirmed impact; contained quickly | 24 hours |

---

## 4. Incident Lifecycle

```
[Detection]
  Detected ──► Triaged ──► Containment ──► Investigation ──► Recovery ──► Closed
      │              │
      └──► False Positive
```

### 4.1 Workflow States and Transitions

```
Detected:
  - Initial record created (manual or auto-detection)
  - Severity assessed
  - Incident commander assigned

Triaged:
  - Initial scope confirmed
  - Response team assembled
  - Stakeholders notified per severity

Containment:
  - Immediate containment actions taken
  - Evidence preservation begins
  - Regulatory notification clock starts (if applicable)

Investigation:
  - Root cause analysis
  - Full impact assessment
  - Evidence collection and forensics

Recovery:
  - Systems restored
  - Monitoring heightened
  - Affected parties notified

Closed:
  - Lessons learned documented
  - Post-incident review conducted
  - Linked Issue records verified closed
  - Risk record updated to reflect materialization
```

---

## 5. Multi-Jurisdiction Breach Notification

For incidents involving personal data, **multiple regulatory frameworks** may require simultaneous notification with different deadlines:

| Regulation | Jurisdiction | Deadline | Recipient |
|-----------|-------------|---------|-----------|
| GDPR Art. 33 | EU | 72 hours | Supervisory Authority |
| PDPA (Thailand) | TH | 72 hours | PDPC |
| PIPL (China) | CN | Immediate / 10 days | CAC |
| US State Laws (CCPA, NYDFS) | US | Expedient / 30–72 hours | AG / DFS |
| Central Bank (local regs) | Varies | 24–48 hours | Banking regulator |

The platform tracks each jurisdiction's notification obligation independently:

```sql
CREATE TABLE incident_notification_obligations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    incident_id     UNIQUEIDENTIFIER  NOT NULL,
    jurisdiction    NVARCHAR(100)     NOT NULL,   -- 'GDPR', 'NYDFS', 'PDPA', etc.
    regulation_ref  NVARCHAR(200)     NULL,        -- e.g. 'GDPR Art. 33'
    deadline        DATETIME2         NOT NULL,
    recipient       NVARCHAR(500)     NULL,        -- name of regulatory body
    status          NVARCHAR(50)      NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','submitted','acknowledged','waived','not_required')),
    submitted_at    DATETIME2         NULL,
    submission_ref  NVARCHAR(500)     NULL,        -- confirmation/reference number
    evidence_file   UNIQUEIDENTIFIER  NULL,        -- FK to record_attachments (submission proof)
    notes           NVARCHAR(2000)    NULL
);
CREATE INDEX idx_notif_incident ON incident_notification_obligations(incident_id, deadline);
```

Notification deadlines are calculated from `discovery_date` when an incident is flagged as a privacy breach. The system generates one `incident_notification_obligations` row per applicable jurisdiction (configured in `org_settings.applicable_jurisdictions`).

Escalation rules:
- At 50% of deadline remaining: notify incident commander
- At 80% of deadline remaining: critical alert to CISO and DPO
- After deadline with `status = 'pending'`: SLA breach logged and escalated to regulators

### 5.1 Incident Timeline Audit Linkage

Every `incident_timeline` entry is linked to the corresponding `audit_log` entry to create a tamper-evident chain of evidence:

```sql
ALTER TABLE incident_timeline ADD
    audit_log_id UNIQUEIDENTIFIER NULL REFERENCES audit_log(id);
```

When a timeline entry is created, the `auditService.logCreate()` result ID is stored in `audit_log_id`. This allows the regulatory investigator to trace every incident action to the audit chain.

---

## 6. Incident Response Timeline (Activity Log)

A detailed timeline of all actions during incident response:

```sql
CREATE TABLE incident_timeline (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    incident_id     UNIQUEIDENTIFIER  NOT NULL,
    timestamp       DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    entry_type      NVARCHAR(50)      NOT NULL
                    CHECK (entry_type IN ('action','decision','communication',
                                          'evidence','status_change','note')),
    title           NVARCHAR(500)     NOT NULL,
    description     NVARCHAR(MAX)     NULL,
    entered_by      UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    attachment_id   UNIQUEIDENTIFIER  NULL
);
```

The timeline is append-only and tamper-evident (part of the audit chain from Module 09).

---

## 7. Incident-to-Risk Linkage

When an incident closes, if `linked_risk` is set, the risk record is updated:

1. A risk event is recorded on the risk record
2. `last_materialization_date` field updated
3. Risk rating is reviewed: if the incident was worse than risk impact predicted, the risk is flagged for re-assessment
4. A control finding is raised for any controls that should have prevented the incident but did not

---

## 8. Lessons Learned → Action Items

The post-incident review produces action items that become:
- Issues (Module 19) for process/control improvements
- New Risk records if a new risk type was identified
- New/updated Control records if new controls are needed

---

## 9. Key Reports

| Report | Description |
|--------|-------------|
| Incident Summary | Count by type and severity, open vs. closed |
| Mean Time to Detect (MTTD) | Average time from occurrence to detection |
| Mean Time to Contain (MTTC) | Average time from detection to containment |
| Mean Time to Recover (MTTR) | Average time from detection to recovery |
| Regulatory Notification Status | All P1/P2 privacy incidents and notification status |
| Incidents by Root Cause | Distribution of incidents by root cause category |
| Repeat Incidents | Same root cause or same system affected multiple times |
| Vendor-Related Incidents | Incidents attributable to third parties |

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should the platform support a public incident reporting form (for external reports)? | Medium | |
| 2 | Integration with SIEM (Splunk, Sentinel) for auto-incident creation from alerts? | High | |
| 3 | War room / case collaboration tool — real-time chat during P1 incident? | Low | |
| 4 | Should breach notification letters be generated from templates within the platform? | Medium | |
| 5 | ~~Multiple regulatory frameworks simultaneously (GDPR + HIPAA + State laws) for same incident?~~ | High | **Resolved:** Yes — see Section 5. `incident_notification_obligations` table tracks one row per jurisdiction per incident. Org-configured `applicable_jurisdictions` drives which obligations are auto-generated. |

---

*Previous: [21 — Vendor & Third-Party Risk](21-vendor-third-party-risk.md) | Next: [23 — Business Continuity](23-business-continuity.md)*
