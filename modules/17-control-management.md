# Module 17 — Control Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 03 (Rule Engine), Module 07, Module 08 (Workflow), Module 16 (Risk)

---

## 1. Domain Context

Controls are the safeguards and countermeasures that reduce risk and demonstrate compliance. Control Management provides a centralized Control Library that can be:

- Linked to risks as mitigating controls
- Linked to compliance requirements as evidence of implementation
- Tested periodically for design and operational effectiveness
- Tracked for issues when controls fail

This is the connective tissue between Risk Management, Compliance Management, and Issues Management.

---

## 2. Control Types

| Type | Description | Example |
|------|-------------|---------|
| **Preventive** | Stops a risk event from occurring | Multi-factor authentication |
| **Detective** | Detects when a risk event has occurred | Security log monitoring |
| **Corrective** | Reduces impact after a risk event | Incident response procedure |
| **Directive** | Provides governance authority | Password policy |
| **Compensating** | Alternative when primary control is not feasible | Manual review replacing automated check |

---

## 3. Entity Design — Control Application

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `title` | Control Title | text | |
| `control_id` | Control ID | text | Unique identifier e.g. CTL-001 |
| `description` | Description | rich_text | |
| `control_type` | Control Type | value_list | preventive, detective, corrective, directive, compensating |
| `category` | Category | value_list | Technical, Administrative, Physical |
| `domain` | Control Domain | value_list | Access Control, Change Mgmt, Monitoring, etc. |
| `owner` | Control Owner | user_reference | |
| `operator` | Control Operator | user_reference | Who runs the control daily |
| `frequency` | Testing Frequency | value_list | continuous, daily, monthly, quarterly, annually |
| `automation_type` | Automation | value_list | manual, semi_automated, fully_automated |
| `effectiveness_score` | Effectiveness Score | calculated | 0–100, see scoring model |
| `effectiveness_rating` | Effectiveness Rating | calculated | Effective, Partially Effective, Ineffective |
| `last_test_date` | Last Test Date | date | |
| `next_test_date` | Next Test Date | date | Calculated from frequency |
| `test_status` | Test Status | value_list | not_tested, passed, failed, partially_passed |
| `related_risks` | Mitigated Risks | multi_reference | |
| `related_policies` | Source Policies | multi_reference | |
| `framework_refs` | Framework Controls | multi_reference | Links to ComplianceRequirements |
| `related_assets` | Applicable To | multi_reference | Systems/assets the control covers |
| `status` | Status | value_list | active, inactive, remediation, retired |
| `implementation_notes` | Implementation Notes | rich_text | |
| `evidence_description` | Evidence Guide | rich_text | What evidence is expected for testing |

---

## 4. Control Effectiveness Scoring

Effectiveness is calculated based on test history and automation level:

```json
{
  "key": "control_effectiveness_score",
  "type": "aggregation",
  "config": {
    "source":        "control_tests",
    "relation_type": "control_has_tests",
    "direction":     "outbound",
    "filter": {
      "fieldKey": "test_date",
      "operator": "GTE",
      "value":    { "op": "date_subtract", "left": { "type": "now" }, "right": { "type": "literal", "value": "P365D" } }
    },
    "function": "PERCENT_PASSED",
    "field":    "test_result"
  }
}
```

| Effectiveness Score | Rating | Meaning |
|--------------------|--------|---------|
| 90–100 | Effective | Control is consistently operating as designed |
| 70–89 | Largely Effective | Minor exceptions; control intent is met |
| 40–69 | Partially Effective | Significant exceptions; control needs attention |
| < 40 | Ineffective | Control is not operating; risk is unmitigated |
| No test in 12 months | Not Assessed | Cannot determine effectiveness |

---

## 5. Control Testing

Control tests are child records of a Control, capturing each test event:

| Field Key | Label | Type |
|-----------|-------|------|
| `parent_control` | Control | reference |
| `test_date` | Test Date | date |
| `tester` | Tester | user_reference |
| `test_result` | Test Result | value_list: passed, failed, partially_passed, not_applicable |
| `test_method` | Test Method | value_list: inspection, interview, observation, reperformance |
| `population_size` | Population Size | number |
| `sample_size` | Sample Size | number |
| `exceptions_count` | Exceptions Found | number |
| `exception_description` | Exception Details | rich_text |
| `evidence` | Test Evidence | attachment |
| `linked_issue` | Related Issue | reference | Auto-created when test fails |

### 5.1 Automated Test Schedule

A scheduler job runs daily and creates **draft test records** for controls where `next_test_date <= today`. The draft test record is assigned to the `operator` as a workflow task, requiring them to complete the test.

---

## 6. Control Workflow

```
Draft ──► In Testing ──► Passed
                    └──► Failed ──► Issue Created ──► Remediation ──► Re-test
        └──► Not Applicable
```

When a test **fails**, the system automatically:
1. Creates an Issue record (Module 19) linked to the control
2. Notifies the control owner and risk owner
3. Flags all linked risks as potentially having reduced effectiveness
4. Recalculates the control's effectiveness score

---

## 7. Control Library vs Instance Model

The platform supports two levels:

| Level | Description |
|-------|-------------|
| **Library Control** | Template control (from a framework or internal library) |
| **Instance Control** | Applied control — a library control applied to a specific system or process |

This mirrors how Archer's Control Standards vs. Key Controls work.

A Library Control (e.g., "Multi-Factor Authentication") can be instantiated multiple times:
- MFA — HR Systems
- MFA — Financial Systems
- MFA — External-Facing Applications

Each instance is tested independently and has its own effectiveness score.

---

## 8. Framework Mapping

Controls are mapped to framework requirements (from the Compliance module):

```
NIST SP 800-53 IA-2 (Identification and Authentication)
  ├── MFA — HR Systems            (Effective)
  ├── MFA — Financial Systems     (Largely Effective)
  └── MFA — External Applications (Effective)
```

This mapping drives the Compliance Coverage Report.

---

## 9. Graph Relationships (Neo4j)

```cypher
(:Control)-[:MITIGATES]->(:Risk)
(:Control)-[:IMPLEMENTS]->(:ComplianceRequirement)
(:Control)-[:DERIVED_FROM]->(:Policy)
(:Control)-[:APPLIES_TO]->(:Asset)
(:ControlTest)-[:TESTS]->(:Control)
(:Issue)-[:RAISED_FOR]->(:Control)

// Control gap query: risks with no effective controls
MATCH (r:Risk {status:'active'})
WHERE NOT EXISTS {
  MATCH (r)<-[:MITIGATES]-(c:Control)
  WHERE c.effectiveness_rating IN ['Effective','Largely Effective']
}
RETURN r.title, r.residual_rating
ORDER BY r.residual_score DESC
```

---

## 10. Key KPIs

| Metric | Description |
|--------|-------------|
| Control Coverage | % of active risks with ≥1 effective control |
| Controls Past Test Due | Count of controls with overdue tests |
| Overall Control Effectiveness | Org-wide average effectiveness score |
| Pass Rate | % of tests passed in last 12 months |
| Open Control Failures | Count of controls with open failed tests |
| Untested Controls | Count of controls never tested |
| Controls by Domain | Distribution across control domains |

---

## 11. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should control inheritance work across org units (parent unit shares control down)? | Medium |
| 2 | SOC 2 Type II: should test populations/samples be stored for audit evidence? | High |
| 3 | Should compensating controls be tracked separately vs. just typed? | Medium |
| 4 | Continuous control monitoring: automated evidence collection from APIs (log counts, etc.)? | Future |

---

*Previous: [16 — Risk Management](16-risk-management.md) | Next: [18 — Compliance Management](18-compliance-management.md)*
