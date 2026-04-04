# Module 23 — Business Continuity & Disaster Recovery

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 07, Module 08 (Workflow), Module 22 (Incident)

---

## 1. Domain Context

Business Continuity Management (BCM) and Disaster Recovery (DR) planning ensures the organization can continue operations during and after disruptive events. This module covers:

- **Business Impact Analysis (BIA)** — Identifying critical processes and their tolerance for disruption
- **Business Continuity Plans (BCP)** — Documented plans for maintaining operations
- **Disaster Recovery Plans (DRP)** — IT-focused recovery plans
- **Crisis Management Plans** — Executive-level decision frameworks
- **BCP/DR Testing** — Exercising plans through tabletop, simulation, and live tests

---

## 2. Entity Design

### 2.1 Business Process (BIA Entity)

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `process_name` | Process Name | text | |
| `process_owner` | Process Owner | user_reference | |
| `business_unit` | Business Unit | reference | |
| `description` | Description | rich_text | |
| `criticality` | Criticality | value_list | critical, high, medium, low |
| `rto` | Recovery Time Objective (RTO) | number | Hours the process can be down |
| `rpo` | Recovery Point Objective (RPO) | number | Hours of data loss tolerance |
| `mtpd` | Max Tolerable Period of Disruption | number | Maximum hours before permanent damage |
| `min_staff_required` | Minimum Staff Required | number | |
| `dependencies_internal` | Internal Dependencies | multi_reference | Other business processes |
| `dependencies_systems` | IT System Dependencies | multi_reference | |
| `dependencies_vendors` | Vendor Dependencies | multi_reference | |
| `manual_workaround` | Manual Workaround Available? | boolean | |
| `workaround_description` | Workaround Description | rich_text | |
| `financial_impact_per_hour` | Financial Impact/Hour ($) | number | |
| `regulatory_impact` | Regulatory Consequences | rich_text | |
| `reputational_impact` | Reputational Consequences | rich_text | |

### 2.2 BIA Priority Score (Calculated)

```json
{
  "key": "bia_priority_score",
  "type": "calculation",
  "expression": {
    "op": "if",
    "condition": { "op": "lte", "left": {"type":"field_value","field_key":"rto"}, "right": {"type":"literal","value":4} },
    "then": { "type": "literal", "value": 100 },
    "else": {
      "op": "if",
      "condition": { "op": "lte", "left": {"type":"field_value","field_key":"rto"}, "right": {"type":"literal","value":24} },
      "then": { "type": "literal", "value": 75 },
      "else": { "type": "literal", "value": 50 }
    }
  }
}
```

---

## 3. Business Continuity Plan

| Field Key | Label | Type |
|-----------|-------|------|
| `plan_name` | Plan Name | text |
| `plan_type` | Plan Type | value_list: bcp, drp, crisis_management, crisis_communications, it_recovery |
| `covered_processes` | Covered Processes | multi_reference |
| `covered_systems` | Covered Systems | multi_reference |
| `plan_owner` | Plan Owner | user_reference |
| `version` | Version | text |
| `status` | Status | value_list: draft, approved, active, under_review, retired |
| `last_tested_date` | Last Test Date | date |
| `next_test_date` | Next Test Date | date |
| `test_frequency` | Test Frequency | value_list: monthly, quarterly, semi-annually, annually |
| `plan_document` | Plan Document | attachment |
| `call_tree` | Notification/Call Tree | attachment |
| `approved_by` | Approved By | user_reference |
| `approval_date` | Approval Date | date |
| `recovery_location` | Recovery Location | text |
| `recovery_location_address` | Recovery Site Address | text |

---

## 4. BCP/DR Testing

Each plan must be tested on a regular schedule. Test types:

| Test Type | Description | Disruption |
|-----------|-------------|-----------|
| **Document Review** | Verify plan accuracy and currency | None |
| **Checklist Review** | Walk through plan activation checklist | None |
| **Tabletop Exercise** | Scenario discussion with stakeholders | None |
| **Parallel Test** | Run backup systems alongside production | Low |
| **Full Simulation** | Simulate actual disruption; invoke plan | High |
| **Failover Test** | Actually fail over to DR environment | High |

### 4.1 BCP Test Record

| Field Key | Label | Type |
|-----------|-------|------|
| `parent_plan` | BCP/DRP Plan | reference |
| `test_type` | Test Type | value_list (above) |
| `test_date` | Test Date | date |
| `test_lead` | Test Lead | user_reference |
| `participants` | Participants | multi_user_reference |
| `rto_achieved` | RTO Achieved (hours) | number |
| `rpo_achieved` | RPO Achieved (hours) | number |
| `rto_target_met` | RTO Target Met? | boolean |
| `rpo_target_met` | RPO Target Met? | boolean |
| `overall_result` | Test Result | value_list: passed, failed, partially_passed |
| `issues_identified` | Issues Found | rich_text |
| `improvements_required` | Required Improvements | rich_text |
| `evidence` | Test Evidence | attachment |
| `linked_issues` | Created Issues | multi_reference |

When a test identifies gaps, Issues (Module 19) are automatically created for each identified improvement.

---

## 5. Crisis Activation

When an incident (Module 22) reaches **Critical severity**, the BCP module is triggered:

1. Relevant BCP plans are identified based on affected systems/processes
2. Crisis management team is notified
3. A BCP activation record is created
4. The plan's call tree/notification chain is triggered

### 5.1 BCP Activation Record

```sql
CREATE TABLE bcp_activations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    plan_id         UNIQUEIDENTIFIER  NOT NULL,
    incident_id     UNIQUEIDENTIFIER  NULL,     -- triggering incident (optional)
    activated_at    DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    activated_by    UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    status          NVARCHAR(50)      NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active','stand_down','closed')),
    stand_down_at   DATETIME2         NULL,
    rto_met         BIT               NULL,     -- populated on close
    rpo_met         BIT               NULL,
    notes           NVARCHAR(MAX)     NULL,
    closed_at       DATETIME2         NULL
);
```

---

## 6. Recovery Objectives Dashboard

| Metric | Description |
|--------|-------------|
| % Processes with BCP Coverage | Processes with an active, approved plan |
| % Plans Tested on Schedule | Plans with current (non-overdue) test dates |
| RTO Achievement Rate | % of tests where RTO target was met |
| RPO Achievement Rate | % of tests where RPO target was met |
| Critical Process Coverage | % critical-rated processes with BCP |
| Untested Plans | Plans never tested or > 12 months since test |
| BCP Test Schedule | Calendar view of upcoming tests |

---

## 7. Industry Standards References

| Standard | Applicability |
|----------|--------------|
| ISO 22301 | Business continuity management systems |
| ISO 22317 | Business impact analysis guidelines |
| NIST SP 800-34 | Contingency planning for federal systems |
| DRII Professional Practices | Business continuity best practices |
| BCI Good Practice Guidelines | BCM lifecycle |

---

## 8. Graph Relationships (Neo4j)

```cypher
// BCP dependency chain
(:BusinessProcess)-[:DEPENDS_ON]->(:BusinessProcess)
(:BusinessProcess)-[:DEPENDS_ON]->(:ItSystem)
(:ItSystem)-[:HOSTED_BY]->(:Vendor)
(:BcpPlan)-[:COVERS]->(:BusinessProcess)

// Impact of a system outage on critical processes
MATCH (sys:ItSystem {id: $systemId})<-[:DEPENDS_ON*1..3]-(bp:BusinessProcess)
WHERE bp.criticality IN ['critical','high']
RETURN bp.process_name, bp.rto, bp.criticality
ORDER BY bp.rto ASC
```

---

## 9. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should the platform send crisis notifications via SMS/push? (Not just email) | High |
| 2 | Should BCP plans support step-by-step digital playbooks (checklist during activation)? | High |
| 3 | Supply chain BCP: should critical vendor BCP health status be tracked? | Medium |
| 4 | Should recovery cost tracking be included (cost of activation)? | Low |

---

*Previous: [22 — Incident Management](22-incident-management.md) | Next: [24 — Vulnerability Management](24-vulnerability-management.md)*
