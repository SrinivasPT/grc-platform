# Module 16 — Risk Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 03 (Rule Engine), Module 07, Module 08 (Workflow), Module 12 (Reporting), Module 17 (Controls)

---

## 1. Domain Context

Risk Management is the core of any GRC platform. It encompasses identifying, assessing, treating, monitoring, and communicating risks across the organization. This module supports:

- **Enterprise Risk Management (ERM)** — Strategic, operational, financial, reputational risks
- **IT Risk Management** — Technology and cybersecurity risks
- **Operational Risk Management** — Process failure, people, and system risks

Risks are assessed, scored, linked to controls that mitigate them, and tracked through treatment plans to closure or acceptance.

---

## 2. Risk Taxonomy

| Level | Description | Example |
|-------|-------------|---------|
| **Risk Category** | Broad classification | Cybersecurity, Regulatory, Operational |
| **Risk Sub-category** | Specific within category | Data Breach, Third-Party, Process Failure |
| **Risk** | Individual risk instance | "Phishing attack on employee credentials" |
| **Risk Event** | Materialized risk occurrence | An actual data breach (links to Incident module) |
| **Treatment (Action Item)** | Steps to reduce risk | "Implement MFA for all employees" |

---

## 3. Entity Design — Risk Application

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `title` | Risk Title | text | Required |
| `description` | Description | rich_text | Detailed narrative |
| `category` | Category | value_list | Cybersecurity, Operational, etc. |
| `sub_category` | Sub-category | value_list | Domain-specific sub-types |
| `risk_type` | Risk Type | value_list | inherent, residual |
| `owner` | Risk Owner | user_reference | Accountable person |
| `business_unit` | Business Unit | reference | Org unit |
| `related_assets` | Related Assets | multi_reference | IT assets, systems |
| `likelihood` | Likelihood | value_list | 1-Very Low to 5-Critical |
| `impact` | Impact | value_list | 1-Very Low to 5-Critical |
| `inherent_score` | Inherent Risk Score | calculated | `likelihood × impact` |
| `inherent_rating` | Inherent Rating | calculated | Lookup from score |
| `related_controls` | Mitigating Controls | multi_reference | Links to Control records |
| `control_effectiveness` | Control Effectiveness | calculated | Avg effectiveness of linked controls |
| `residual_likelihood` | Residual Likelihood | value_list | After controls |
| `residual_impact` | Residual Impact | value_list | After controls |
| `residual_score` | Residual Risk Score | calculated | `residual_likelihood × residual_impact` |
| `residual_rating` | Residual Rating | calculated | Lookup from residual score |
| `risk_appetite_alignment` | Appetite Alignment | calculated | Is residual score within appetite? |
| `treatment_strategy` | Treatment Strategy | value_list | accept, mitigate, transfer, avoid |
| `target_risk_date` | Target Resolution Date | date | When residual risk should reach target |
| `risk_appetite_threshold` | Risk Appetite | number | Organization's tolerance ceiling |
| `status` | Status | value_list | identified, assessed, in_treatment, monitored, accepted, closed |
| `regulation_refs` | Regulatory Context | multi_reference | Regulations that make this risk relevant |

### 3.1 Risk Scoring Rule (Rule Engine DSL)

```json
{
  "key": "risk_inherent_score",
  "type": "calculation",
  "expression": {
    "op": "*",
    "left":  { "type": "field_value", "field_key": "likelihood" },
    "right": { "type": "field_value", "field_key": "impact" }
  }
}
```

```json
{
  "key": "risk_inherent_rating",
  "type": "calculation",
  "expression": {
    "op": "if",
    "condition": { "op": "gte", "left": { "type": "field_value", "field_key": "inherent_score" }, "right": { "type": "literal", "value": 20 } },
    "then": { "type": "literal", "value": "Critical" },
    "else": {
      "op": "if",
      "condition": { "op": "gte", "left": { "type": "field_value", "field_key": "inherent_score" }, "right": { "type": "literal", "value": 12 } },
      "then": { "type": "literal", "value": "High" },
      "else": {
        "op": "if",
        "condition": { "op": "gte", "left": { "type": "field_value", "field_key": "inherent_score" }, "right": { "type": "literal", "value": 6 } },
        "then": { "type": "literal", "value": "Medium" },
        "else": { "type": "literal", "value": "Low" }
      }
    }
  }
}
```

---

## 4. Risk Treatment Plans

**Treatment Actions** are child records linked to a Risk. They represent concrete remediation steps:

| Field Key | Label | Type |
|-----------|-------|------|
| `title` | Action Title | text |
| `description` | Description | rich_text |
| `parent_risk` | Parent Risk | reference |
| `owner` | Action Owner | user_reference |
| `due_date` | Due Date | date |
| `effort_estimate` | Effort (days) | number |
| `status` | Status | value_list: open, in_progress, complete, cancelled |
| `completion_date` | Actual Completion | date |
| `evidence` | Evidence of Completion | attachment |

When all treatment actions for a risk are `complete`, the risk's status is auto-updated to `monitored` via a Rule Engine aggregation.

---

## 5. Risk Assessment Workflow

```
Identified ──► Assessed ──► In Treatment ──► Monitored ──► Accepted/Closed
    │               │                              │
    │         (Risk Accepted)                  (Re-assess)
    └──────────────────────────────────────────────┘
```

### 5.1 Risk Review Cadence

| Risk Rating | Review Frequency |
|-------------|-----------------|
| Critical | Monthly |
| High | Quarterly |
| Medium | Semi-annually |
| Low | Annually |

A scheduled job checks all active risks against their last_reviewed_at date and flags overdue risks.

---

## 6. Risk Appetite

Risk appetite thresholds are stored in a **dedicated first-class table** — not embedded in `org_settings`. This supports audit trail of appetite changes and per-category granularity:

```sql
CREATE TABLE risk_appetite_thresholds (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    category        NVARCHAR(200)     NULL,   -- NULL = global default
    threshold_score INT               NOT NULL,
    critical_multiplier DECIMAL(4,2)  NOT NULL DEFAULT 1.5, -- residual > threshold×1.5 = critical
    effective_from  DATE              NOT NULL DEFAULT GETUTCDATE(),
    effective_to    DATE              NULL,   -- NULL = currently active
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    notes           NVARCHAR(1000)    NULL
);
-- Only one active (effective_to IS NULL) record per org_id+category
CREATE UNIQUE INDEX uq_appetite_active
    ON risk_appetite_thresholds(org_id, category)
    WHERE effective_to IS NULL;
```

Example thresholds:

| Category | Threshold | Critical at |
|----------|-----------|-------------|
| Global (default) | 12 | 18 |
| Cybersecurity | 10 | 15 |
| Regulatory | 6 | 9 |
| Operational | 15 | 22 |

The `risk_appetite_alignment` calculated field compares `residual_score` to the applicable threshold:
- `within_appetite`: residual_score ≤ threshold
- `above_appetite`: residual_score > threshold (triggers mandatory treatment)
- `critical_exceedance`: residual_score > threshold × critical_multiplier (triggers executive escalation)

---

## 7. Risk Register: Standard Reporting

| Report | Description |
|--------|-------------|
| Risk Heat Map | 5×5 inherent and residual heat maps side-by-side |
| Risk Trend | Risk score trends over time (uses record_versions) |
| Top 10 Risks | Highest residual score risks |
| Risks Above Appetite | Risks exceeding risk appetite threshold |
| Treatment Progress | Action items by status and owner |
| Overdue Reviews | Risks past next review date |
| Risk by Business Unit | Risk distribution across org units |

---

## 8. Graph Intelligence (Neo4j)

```cypher
// Risk-to-Control graph
(:Risk)-[:MITIGATED_BY]->(:Control)
(:Control)-[:MITIGATES]->(:Risk)
(:Risk)-[:CAUSED_BY]->(:Asset)
(:Risk)-[:REALIZED_AS]->(:Incident)
(:Risk)-[:RELEVANT_TO]->(:ComplianceRequirement)

// Impact propagation: knock-on risks from an asset failure
MATCH (a:Asset {id: $assetId})<-[:DEPENDS_ON]-(a2:Asset)
      <-[:CAUSED_BY]-(r:Risk)
WHERE r.status <> 'closed'
RETURN r.title, r.residual_rating
ORDER BY r.residual_score DESC
```

---

## 9. Industry Frameworks Supported

| Framework | Applicability |
|-----------|--------------|
| NIST RMF (SP 800-37) | Risk Management Framework for federal systems |
| ISO 31000 | General risk management principles |
| ISO 27005 | Information security risk management |
| FAIR (Factor Analysis of Information Risk) | Quantitative risk analysis |
| COSO ERM | Enterprise Risk Management |

Each framework can be mapped via compliance requirements in the Compliance module.

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | ~~Quantitative risk scoring (FAIR model)?~~ | Medium | **Resolved Post-MVP:** FAIR quantitative scoring deferred to post-MVP. The platform's current 5×5 matrix (qualitative) is the primary scoring method. A `fair_quantification` extension may be added as a supplementary calculation in a future release. |
| 2 | Risk correlation: should highly correlated risks be grouped for aggregate reporting? | Medium | |
| 3 | Scenario analysis / Monte Carlo simulation for risk portfolio modeling? | Low — post-MVP | |
| 4 | Should residual score be allowed to exceed inherent score? (Can controls increase risk?) | Design | |
| 5 | Risk-to-risk relationships (parent risk → child risks / compound risks)? | Medium | |

---

*Previous: [15 — Policy Management](15-policy-management.md) | Next: [17 — Control Management](17-control-management.md)*
