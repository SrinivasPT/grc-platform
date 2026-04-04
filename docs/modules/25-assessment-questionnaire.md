# Module 25 — Assessment & Questionnaire Engine

> **Tier:** 3 — GRC Domain
> **Status:** In Design
> **Dependencies:** Module 02, Module 03 (Rule Engine), Module 05 (Form Engine), Module 07, Module 08 (Workflow), Module 21 (Vendor Risk)

---

## 1. Domain Context

The Assessment and Questionnaire Engine enables the organization to collect structured data from internal and external parties through configurable questionnaires. Use cases include:

- **Vendor Risk Assessments** — Security and privacy questionnaires sent to third parties
- **Internal Control Self-Assessments (CSA)** — Business units assess their own control environment
- **Risk Owner Surveys** — Periodic risk rating confirmation from risk owners
- **Employee Awareness Assessments** — Security awareness knowledge checks
- **Regulatory Submissions** — Structured data collection for regulatory examinations
- **Due Diligence Questionnaires** — M&A or partnership evaluation
- **Onboarding Assessments** — New employee or contractor security review

This is one of the highest-value differentiators for an Archer-level platform — the ability to send scorable, adaptive questionnaires and aggregate results automatically.

---

## 2. Assessment Types

| Assessment Type | Respondent | Example |
|----------------|-----------|---------|
| **Vendor Assessment** | External (vendor portal or email link) | Information Security Assessment |
| **Internal CSA** | Internal user (specific role/org unit) | Annual Control Self-Assessment |
| **Risk Survey** | Risk owners | Quarterly risk rating confirmation |
| **Awareness** | All employees | Phishing awareness quiz |
| **Due Diligence** | External (M&A target, partner) | Third-party due diligence |

---

## 3. Questionnaire Design

Questionnaires are built using the Form & Layout Engine (Module 05) with additional assessment-specific features: question scoring, conditional branching, and response validation.

### 3.1 Question Types

| Question Type | Description | Scoring |
|--------------|-------------|---------|
| `yes_no` | Binary yes/no or yes/no/not_applicable | Configurable points per option |
| `multiple_choice` | Single select from options | Configurable points per option |
| `multi_select` | Multiple select from options | Points per selected option |
| `text` | Open-ended text response | Manual review / no auto-score |
| `scale` | Likert scale (1–5, 1–10) | Points = selected value × weight |
| `date` | Date input | No score |
| `number` | Numeric input | Threshold-based scoring |
| `attachment` | File upload (evidence document) | Required/optional validation |
| `matrix` | Grid of questions with same response scale | Sub-score per row |

### 3.2 Questionnaire Definition Schema (JSON)

```json
{
  "id":          "infosec-assessment-v2",
  "name":        "Information Security Assessment",
  "description": "Annual vendor information security review (ISO 27001 aligned)",
  "version":     "2.0",
  "sections": [
    {
      "id":    "sec-access-control",
      "title": "Access Control",
      "description": "ISO 27001 Annex A.9",
      "weight": 20,
      "questions": [
        {
          "id":       "q-mfa",
          "text":     "Is Multi-Factor Authentication (MFA) enforced for all privileged user accounts?",
          "type":     "multiple_choice",
          "required": true,
          "weight":   10,
          "options": [
            { "value": "yes_all",     "label": "Yes, MFA enforced for all privileged accounts",   "score": 10 },
            { "value": "yes_partial", "label": "Yes, but only some privileged accounts have MFA", "score": 5 },
            { "value": "no",          "label": "No, MFA is not currently in use",                  "score": 0 }
          ],
          "evidence_requested": "Please attach screenshots or policy evidence",
          "follow_up": {
            "condition": { "op": "eq", "left": "q-mfa", "right": "no" },
            "question": {
              "id":   "q-mfa-plan",
              "text": "Is there a remediation plan to implement MFA?",
              "type": "yes_no"
            }
          }
        }
      ]
    }
  ],
  "scoring": {
    "method":     "weighted_percentage",
    "thresholds": [
      { "label": "Satisfactory",       "min_score": 80 },
      { "label": "Needs Improvement",  "min_score": 60 },
      { "label": "Unsatisfactory",     "min_score": 0 }
    ]
  }
}
```

---

## 4. Assessment Campaigns

An Assessment Campaign sends a questionnaire to multiple respondents and tracks completion:

```sql
CREATE TABLE assessment_campaigns (
    id                  UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id              UNIQUEIDENTIFIER  NOT NULL,
    questionnaire_id    UNIQUEIDENTIFIER  NOT NULL,
    name                NVARCHAR(255)     NOT NULL,
    campaign_type       NVARCHAR(50)      NOT NULL,
    target_type         NVARCHAR(50)      NOT NULL,   -- 'vendor', 'user', 'org_unit'
    target_ids          NVARCHAR(MAX)     NOT NULL,   -- JSON: list of target IDs
    access_type         NVARCHAR(20)      NOT NULL DEFAULT 'authenticated'
                        CHECK (access_type IN ('authenticated','token_link')),
    due_date            DATE              NOT NULL,
    reminder_days       NVARCHAR(100)     NULL,       -- JSON: [7, 3, 1] = remind 7d, 3d, 1d before
    status              NVARCHAR(20)      NOT NULL DEFAULT 'draft',
    launched_at         DATETIME2         NULL,
    total_recipients    INT               NOT NULL DEFAULT 0,
    completed_count     INT               NOT NULL DEFAULT 0,
    created_by          UNIQUEIDENTIFIER  NOT NULL,
    created_at          DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

---

## 5. Assessment Responses

`assessment_responses` tracks each respondent's submission:

```sql
CREATE TABLE assessment_responses (
    id                  UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id              UNIQUEIDENTIFIER  NOT NULL,
    campaign_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES assessment_campaigns(id),
    respondent_type     NVARCHAR(20)      NOT NULL,  -- 'user', 'vendor_contact', 'anonymous'
    respondent_id       UNIQUEIDENTIFIER  NULL,
    respondent_email    NVARCHAR(500)     NULL,
    access_token        NCHAR(64)         NULL,       -- hashed; for token_link access
    status              NVARCHAR(20)      NOT NULL DEFAULT 'not_started'
                        CHECK (status IN ('not_started','in_progress','submitted','reviewed','accepted','rejected')),
    started_at          DATETIME2         NULL,
    submitted_at        DATETIME2         NULL,
    reviewed_at         DATETIME2         NULL,
    reviewer_id         UNIQUEIDENTIFIER  NULL,
    score_raw           DECIMAL(6,2)      NULL,
    score_pct           DECIMAL(5,2)      NULL,
    score_label         NVARCHAR(100)     NULL,
    reviewer_notes      NVARCHAR(MAX)     NULL,
    -- Answers stored in child table
    linked_vendor_id    UNIQUEIDENTIFIER  NULL        -- denormalized for vendor risk use case
);

CREATE TABLE assessment_answers (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    response_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES assessment_responses(id),
    question_id     NVARCHAR(100)     NOT NULL,
    answer_value    NVARCHAR(MAX)     NULL,           -- JSON: "yes_all" or ["a","b"] for multi
    score_earned    DECIMAL(6,2)      NULL,
    evidence_file_id UNIQUEIDENTIFIER NULL,
    reviewer_note   NVARCHAR(MAX)     NULL
);
```

---

## 6. External Respondent Access (Vendor Portal)

For vendor assessments, the respondent receives a link — they do **not** need a platform account:

1. Campaign launched → unique access token generated per respondent
2. Token hashed and stored; plaintext token embedded in email link
3. Respondent clicks link → token validated → assessment opened in isolated form
4. Respondent fills in answers, uploads evidence, submits
5. No access to any other data in the platform

**Security controls:**
- Token is 64-byte cryptographically random value (not UUID)
- Token expires at campaign `due_date` + 7 days
- Each token is single-use per response (token invalidated after submission)
- Rate limiting: max 20 requests per token per minute

### 6.1 Respondent Identity Verification (High-Risk Assessments)

For assessments flagged as `high_risk = true` (e.g., critical infrastructure vendor assessments, regulatory self-assessments), additional identity verification is required before the respondent can submit:

| Method | Description |
|--------|-------------|
| **Email domain validation** | Respondent must be at the expected vendor domain. The `invitation_email` domain is checked against `campaign.expected_respondent_domain`. |
| **Shared secret** | A 6-digit pin included separately from the access link (sent via a different channel — phone call). Must be entered before access is granted. |
| **IP logging** | Respondent's IP address is recorded with every page load and on submission. Stored in `assessment_responses.respondent_ip`. |

```sql
ALTER TABLE assessment_campaigns ADD
    high_risk                BIT NOT NULL DEFAULT 0,
    expected_respondent_domain NVARCHAR(255) NULL,
    require_shared_secret    BIT NOT NULL DEFAULT 0;

ALTER TABLE assessment_responses ADD
    respondent_ip            NVARCHAR(45) NULL,
    identity_verified_at     DATETIME2    NULL,
    identity_verification_method NVARCHAR(50) NULL;
```

### 6.2 Server-Side Branching Validation

Branching logic (showing/hiding questions based on previous answers) is executed **both client-side** (UX) and **server-side** (on submission validation). This prevents respondents from submitting answers to hidden questions or skipping mandatory questions by manipulating the browser:

```java
@Service
public class AssessmentSubmissionValidator {
    public void validate(AssessmentResponse response, QuestionnaireDefinition def,
                         Map<String, Object> answers) {
        Set<String> visibleQuestions = branchingEngine.computeVisibleQuestions(def, answers);

        // Reject answers for questions that should be hidden
        for (String answeredKey : answers.keySet()) {
            if (!visibleQuestions.contains(answeredKey)) {
                throw new SubmissionValidationException(
                    "Answer provided for hidden question: " + answeredKey);
            }
        }

        // Require answers for mandatory visible questions
        for (QuestionDefinition q : def.getMandatoryQuestions()) {
            if (visibleQuestions.contains(q.getKey()) && !answers.containsKey(q.getKey())) {
                throw new SubmissionValidationException(
                    "Required question not answered: " + q.getKey());
            }
        }
    }
}
```

---

## 7. Scoring Engine

After submission, the scoring engine calculates the response score:

```java
public ResponseScore calculate(AssessmentResponse response, QuestionnaireDefinition questionnaire) {
    double totalWeight = 0, earnedScore = 0;

    for (Section section : questionnaire.sections()) {
        double sectionWeight = section.weight();
        double sectionEarned = 0;

        for (Question question : section.questions()) {
            Answer answer = response.getAnswer(question.id());
            double questionScore = scoreQuestion(question, answer);
            sectionEarned += questionScore * question.weight();
            totalWeight += question.weight() * sectionWeight;
        }

        earnedScore += sectionEarned * sectionWeight;
    }

    double pct = totalWeight == 0 ? 0 : (earnedScore / totalWeight) * 100;
    String label = questionnaire.scoring().labelForScore(pct);
    return new ResponseScore(earnedScore, pct, label);
}
```

---

## 8. Review Workflow

After a response is submitted, platform users review answers:

```
Submitted ──► Under Review ──► Accepted ──► [Score written to vendor/record]
                         └──► Rejected ──► [Respondent notified; resubmission allowed]
```

Reviewers can accept auto-scored answers or override individual question scores with justification.

---

## 9. Assessment Results → Vendor Risk

When an assessment campaign is for a vendor (`campaign_type = vendor`):

1. On `Accepted` status: write `score_pct` and `score_label` back to the linked Vendor record
2. Update `current_risk_rating` on the vendor
3. Update `last_assessed_date`
4. Trigger a notification to the relationship owner
5. If score dropped significantly (configurable threshold), create an Issue record

---

## 10. Pre-Built Questionnaire Library

The platform ships with a library of standard questionnaires:

| Questionnaire | Standard | Questions |
|--------------|---------|-----------|
| Information Security Assessment | ISO 27001 Annex A | ~80 questions |
| NIST CSF Vendor Assessment | NIST CSF 2.0 | ~60 questions |
| Data Privacy Assessment | GDPR Art 28 / CCPA | ~40 questions |
| Business Continuity Assessment | ISO 22301 | ~30 questions |
| Financial Stability Assessment | Custom | ~20 questions |
| DORA ICT Third-Party Assessment | DORA RTS | ~50 questions |
| Caiq Lite (CSA) | CSA CAIQ v4 | ~145 questions |

Additional questionnaires can be built using the questionnaire designer UI.

---

## 11. Key Reports

| Report | Description |
|--------|-------------|
| Campaign Response Rate | Submitted vs. total sent per campaign |
| Score Distribution | Histogram of response scores |
| Vendor Assessment Summary | All vendors with latest score and date |
| Overdue Assessments | Campaigns past due date |
| Low-Scoring Responses | Responses below satisfactory threshold |
| Question-Level Analysis | Per-question pass rate across all respondents |
| Assessment History | All campaigns and scores over time |

---

## 12. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should the questionnaire designer have a UI builder or just JSON editing? | High | |
| 2 | ~~Should branching logic be executed client-side only, or validated server-side too?~~ | High | **Resolved:** Both. Client-side for UX; server-side validation on submission rejects answers for hidden questions and enforces mandatory visible questions. See Section 6.2. |
| 3 | Should response evidence (attachments) be downloadable in bulk? (Evidence package) | Medium | |
| 4 | Should questionnaires support multiple response languages? (Vendor localization) | Medium | |
| 5 | Standardized format import: can we import CAIQ (CSV/Excel format)? | Medium | |

---

*Previous: [24 — Vulnerability Management](24-vulnerability-management.md) | Back to: [Index](../index.md)*
