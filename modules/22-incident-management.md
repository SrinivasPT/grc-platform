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

## 5. GDPR Breach Notification (72-Hour Timer)

For incidents involving personal data of EU residents, GDPR Article 33 requires notification to supervisory authority within 72 hours of becoming aware.

The platform:
1. When `incident_type = privacy` AND `individuals_affected > 0` AND `impact_category CONTAINS Confidentiality`:
   - Sets `regulatory_notification = true`
   - Sets `notification_deadline = discovery_date + 72 hours`
2. At 48 hours from discovery: alert incident commander "24 hours remaining for GDPR notification"
3. At 68 hours: critical escalation to CISO and DPO
4. After 72 hours with `notification_sent_at = null`: SLA breach logged and escalated

```sql
-- Regulatory notification deadlines view
CREATE VIEW v_incident_notification_deadlines AS
SELECT
    i.id,
    i.title,
    i.severity,
    i.discovery_date,
    i.notification_deadline,
    i.notification_sent_at,
    DATEDIFF(HOUR, GETUTCDATE(), i.notification_deadline) AS hours_remaining,
    CASE WHEN i.notification_sent_at IS NULL
              AND i.notification_deadline < SYSUTCDATETIME()
         THEN 1 ELSE 0 END AS is_overdue
FROM incidents i
WHERE i.regulatory_notification = 1
  AND i.status NOT IN ('closed','false_positive')
  AND i.notification_sent_at IS NULL;
```

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

| # | Question | Priority |
|---|----------|----------|
| 1 | Should the platform support a public incident reporting form (for external reports)? | Medium |
| 2 | Integration with SIEM (Splunk, Sentinel) for auto-incident creation from alerts? | High |
| 3 | War room / case collaboration tool — real-time chat during P1 incident? | Low |
| 4 | Should breach notification letters be generated from templates within the platform? | Medium |
| 5 | Multiple regulatory frameworks simultaneously (GDPR + HIPAA + State laws) for same incident? | High |

---

*Previous: [21 — Vendor & Third-Party Risk](21-vendor-third-party-risk.md) | Next: [23 — Business Continuity](23-business-continuity.md)*
