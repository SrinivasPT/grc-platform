# Module 21 — Vendor & Third-Party Risk Management

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 07, Module 08 (Workflow), Module 16 (Risk), Module 25 (Assessment/Questionnaire)

---

## 1. Domain Context

Third-Party Risk Management (TPRM) governs the risks introduced by vendors, suppliers, partners, service providers, and other external organizations. Major regulatory frameworks (DORA, OCC Bulletin 2023-17, GDPR Article 28, PCI DSS 12.8) mandate formal third-party risk programs. The typical questions answered:

- What vendors does the organization use, and what data/systems do they access?
- How risky is each vendor relationship (inherent risk)?
- Has each vendor been assessed and what is their residual risk rating?
- Are vendor contracts and SLAs tracked and current?
- How are vendor-induced incidents and issues tracked?

---

## 2. Entity Design

### 2.1 Vendor Registry (Core Vendor Record)

| Field Key | Label | Type | Notes |
|-----------|-------|------|-------|
| `vendor_name` | Vendor Name | text | |
| `legal_name` | Legal Name | text | |
| `vendor_id` | Vendor ID | text | Internal reference number |
| `relationship_type` | Relationship Type | value_list | technology_provider, professional_services, cloud_provider, subprocessor, reseller |
| `services_provided` | Services Provided | rich_text | |
| `data_access` | Data Accessed | multi_value | PII, Financial, PHI, Confidential, Public |
| `system_access` | Systems Accessed | multi_reference | |
| `relationship_owner` | Relationship Owner | user_reference | |
| `business_unit` | Business Unit | reference | |
| `country_of_incorporation` | Country | value_list | |
| `concentration_risk` | Concentration Risk | value_list | Sole Source, Primary, Redundant |
| `inherent_risk_tier` | Inherent Risk Tier | calculated | Tier 1-4 based on risk factors |
| `current_risk_rating` | Current Risk Rating | calculated | From latest assessment |
| `contract_expiry` | Contract Expiry Date | date | |
| `annual_spend` | Annual Spend ($) | number | |
| `status` | Status | value_list | active, under_review, offboarding, terminated |
| `sub4th_party_vendors` | Sub-processors | multi_reference | (self-referential) |

### 2.2 Inherent Risk Tiering

Vendors are automatically tiered based on risk factors:

```json
{
  "key": "vendor_inherent_tier",
  "type": "calculation",
  "expression": {
    "op": "if",
    "condition": {
      "op": "or",
      "children": [
        { "op": "contains", "left": {"type":"field_value","field_key":"data_access"}, "right": {"type":"literal","value":"PII"} },
        { "op": "contains", "left": {"type":"field_value","field_key":"data_access"}, "right": {"type":"literal","value":"PHI"} },
        { "op": "eq", "left": {"type":"field_value","field_key":"concentration_risk"}, "right": {"type":"literal","value":"Sole Source"} }
      ]
    },
    "then": { "type": "literal", "value": "Tier 1" },
    "else": { "type": "literal", "value": "Tier 2" }
  }
}
```

| Tier | Criteria | Assessment Frequency |
|------|---------|----------------------|
| Tier 1 — Critical | Accesses PII/PHI/financial data OR sole source | Annual (with on-site option) |
| Tier 2 — High | Significant system access without sensitive data | Annual |
| Tier 3 — Medium | Limited access, standard SaaS | Biennial |
| Tier 4 — Low | No data/system access | On contract renewal |

---

## 3. Vendor Assessment

Vendor assessments are sent via the Assessment/Questionnaire module (Module 25). Typical questionnaire types:

| Questionnaire | Coverage |
|--------------|---------|
| Information Security Assessment | ISO 27001, SOC 2 controls |
| Data Privacy Assessment | GDPR, CCPA obligations |
| Business Continuity Assessment | RTO/RPO, DR capability |
| Subcontractor Assessment | 4th-party risks |
| Financial Stability Assessment | Business viability |

### 3.1 Assessment Cadence

- **Tier 1:** Annual questionnaire + evidence review + optional on-site
- **Tier 2:** Annual questionnaire + evidence review
- **Tier 3:** Biennial questionnaire
- **Tier 4:** At contract renewal

Overdue assessments (> 30 days past due date) trigger escalation to relationship owner and procurement.

---

## 4. Contract Management (Lightweight)

The platform tracks contract metadata for vendor relationships:

| Field Key | Label | Type |
|-----------|-------|------|
| `parent_vendor` | Vendor | reference |
| `contract_type` | Contract Type | value_list: MSA, DPA, NDA, SLA, SOW, Order Form |
| `contract_number` | Contract Ref | text |
| `effective_date` | Effective Date | date |
| `expiry_date` | Expiry Date | date |
| `auto_renewal` | Auto Renewal | boolean |
| `renewal_notice_days` | Notice Period (days) | number |
| `contract_value` | Contract Value | number |
| `owner` | Contract Owner | user_reference |
| `document` | Contract Document | attachment |
| `key_terms_summary` | Key Terms | rich_text |
| `data_processing_agreement` | DPA in Place? | boolean |
| `sla_penalties` | SLA Penalties Defined? | boolean |

Contracts expiring within 90 days generate automated notifications to the relationship owner and procurement team.

---

## 5. Vendor Incident Tracking

When a vendor-induced incident occurs, an Incident record (Module 22) is created and linked to the vendor:

- Vendor data breach — creates incident + potentially creates new Risk record
- Vendor outage — creates incident + SLA breach tracking
- Vendor non-compliance — creates Issue record (Module 19)

---

## 6. 4th-Party Risk (Sub-processor Tracking)

Organizations must understand their vendors' vendors — particularly for subprocessors under GDPR:

```cypher
// Graph: find all 4th parties that have access to PII via a Tier 1 vendor
MATCH (org:Organization)-[:USES]->(v:Vendor)-[:SUBCONTRACTS_TO]->(v2:Vendor)
WHERE v.tier = 'Tier 1' AND 'PII' IN v.data_access
RETURN v.name AS vendor, v2.name AS fourth_party, v2.country
```

The platform models this as a `sub4th_party_vendors` multi-reference field on the vendor record, and the Neo4j graph enables impact analysis:

---

## 7. DORA Compliance (Digital Operational Resilience Act)

For financial sector organizations, DORA mandates specific vendor management requirements:

| DORA Requirement | Platform Coverage |
|-----------------|-------------------|
| Register of ICT third-party providers | Vendor Registry |
| Contractual arrangements content | Contract Management |
| DORA Risk assessments | Vendor Assessments |
| Sub-outsourcing visibility | 4th-party tracking |
| Exit strategies | Contract field + offboarding workflow |
| Incident reporting for vendor-caused incidents | Incident Management |

---

## 8. Vendor Offboarding

When a vendor relationship ends, a structured offboarding workflow ensures:
1. Data deletion/return confirmed from vendor
2. Access revoked from all systems
3. All open issues formally transferred or closed
4. Contract archived with termination evidence
5. Final risk assessment marked as closed
6. Vendor status changed to `terminated`

The offboarding process is tracked as a **checklist workflow** with mandatory sign-off on each item:

```sql
CREATE TABLE vendor_offboarding_tasks (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    vendor_id       UNIQUEIDENTIFIER  NOT NULL,
    task_key        NVARCHAR(100)     NOT NULL,  -- 'data_deletion', 'access_revoke', etc.
    task_label      NVARCHAR(255)     NOT NULL,
    is_complete     BIT               NOT NULL DEFAULT 0,
    completed_at    DATETIME2         NULL,
    completed_by    UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    evidence_note   NVARCHAR(2000)    NULL,
    evidence_file   UNIQUEIDENTIFIER  NULL  -- FK to record_attachments
);
```

A vendor cannot be moved to `terminated` status until all mandatory offboarding tasks are complete (`is_complete = 1`). The system blocks this transition at the workflow level. A **Vendor Offboarding Summary** report is generated at completion for contract records.

### 8.1 Vendor Portal REST APIs (Future)

For future vendor self-service (vendors completing their own assessments):

```
POST /vendor-portal/v1/assessments/{token}/responses  — vendor submits assessment answers
GET  /vendor-portal/v1/assessments/{token}            — vendor retrieves their pending assessment
```

These endpoints are **not part of MVP** — they use time-limited, single-use tokens and have no authentication beyond the token itself. Implementation is deferred until vendor portal UI is built.

---

## 9. Key Reports

| Report | Description |
|--------|-------------|
| Vendor Risk Heat Map | Vendors plotted by inherent risk tier vs. assessment rating |
| Overdue Assessments | Vendors with past-due assessments |
| Contract Expiry Calendar | Contracts expiring in next 90/180 days |
| Vendor Concentration Risk | Sole source dependencies |
| 4th-Party Exposure | Sub-processors with data access |
| Open Vendor Issues | Issues by vendor severity |
| Vendor Portfolio Summary | Count by tier, status, type |

---

## 10. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should vendor records be shared globally across the platform (common vendor search) or org-specific? | High |
| 2 | Should contract management be deeper (obligation tracking, milestones)? Or keep lightweight? | Medium |
| 3 | How to handle parent-subsidiary vendor structures (e.g., AWS ↔ Amazon)? | Medium |
| 4 | Vendor portal — should vendors be able to log in and complete assessments directly? | Future |

---

*Previous: [20 — Audit Management](20-audit-management.md) | Next: [22 — Incident Management](22-incident-management.md)*
