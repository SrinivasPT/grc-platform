# Module 10 — Notification Engine

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02, Module 07 (Auth), Module 08 (Workflow)

---

## 1. Purpose

The Notification Engine delivers timely, relevant alerts to users across multiple channels. GRC processes are deadline-driven — risks must be reviewed, controls must be tested, vendor assessments must be returned, tasks must be completed. Without reliable notification, the platform cannot drive real-world GRC operations.

---

## 2. Delivery Channels

| Channel | Use Case | Technology |
|---------|----------|-----------|
| In-app (bell icon) | Real-time task assignments, state changes | GraphQL Subscription / WebSocket |
| Email | Formal assignment notices, SLA warnings, weekly digests | Spring Mail + Thymeleaf templates |
| Webhook | Third-party integrations (Slack, Teams, Jira) | HTTP POST to configured URL |

SMS and push notifications are explicitly out of scope for initial release.

---

## 3. Notification Triggers

Notifications are triggered by five sources:

| Source | Example |
|--------|---------|
| Workflow engine | Task assigned, state changed, SLA breached |
| Rule-defined trigger | "Notify owner when risk_score > 20" |
| Scheduled (cron) | "Send weekly digest every Monday 8am" |
| System events | User invited, API key expires in 7 days |
| Manual dispatch | Admin sends announcement to all users in a role |

---

## 4. Database Schema

```sql
-- Notification templates (HTML/text with variable interpolation)
CREATE TABLE notification_templates (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NULL,       -- NULL = system template (shared)
    key             NVARCHAR(200)     NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    channel         NVARCHAR(20)      NOT NULL CHECK (channel IN ('email','in_app','webhook')),
    subject         NVARCHAR(500)     NULL,        -- email only
    body_html       NVARCHAR(MAX)     NOT NULL,    -- Thymeleaf template
    body_text       NVARCHAR(MAX)     NULL,        -- plain-text fallback
    variables       NVARCHAR(MAX)     NULL,        -- JSON: variable definitions + descriptions
    is_system       BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_notif_tmpl_key UNIQUE (org_id, key, channel)
);

-- Notification rule definitions (rule-triggered notifications)
CREATE TABLE notification_rules (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    application_id  UNIQUEIDENTIFIER  NULL REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    trigger_type    NVARCHAR(50)      NOT NULL
                    CHECK (trigger_type IN ('workflow_transition','field_change',
                                             'scheduled','record_create','sla_breach')),
    trigger_config  NVARCHAR(MAX)     NOT NULL,    -- JSON: defines when to trigger
    condition_dsl   NVARCHAR(MAX)     NULL,         -- Rule Engine expression (optional filter)
    recipients      NVARCHAR(MAX)     NOT NULL,     -- JSON: recipient spec (see 4.1)
    template_key    NVARCHAR(200)     NOT NULL,
    channel         NVARCHAR(20)      NOT NULL CHECK (channel IN ('email','in_app','webhook','all')),
    is_active       BIT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

-- Outbound notification queue (transactional outbox pattern)
CREATE TABLE notification_outbox (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    rule_id         UNIQUEIDENTIFIER  NULL,
    channel         NVARCHAR(20)      NOT NULL,
    recipient_type  NVARCHAR(20)      NOT NULL CHECK (recipient_type IN ('user','email','webhook')),
    recipient_id    UNIQUEIDENTIFIER  NULL,         -- user_id if recipient_type='user'
    recipient_email NVARCHAR(320)     NULL,
    recipient_url   NVARCHAR(2000)    NULL,          -- webhook URL
    subject         NVARCHAR(500)     NULL,
    body            NVARCHAR(MAX)     NOT NULL,
    status          NVARCHAR(20)      NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending','sent','failed','skipped')),
    attempt_count   INT               NOT NULL DEFAULT 0,
    last_attempt_at DATETIME2         NULL,
    sent_at         DATETIME2         NULL,
    error           NVARCHAR(2000)    NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX idx_notif_outbox_pending ON notification_outbox(status, created_at) WHERE status='pending';

-- In-app notification inbox
CREATE TABLE notifications (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    title           NVARCHAR(500)     NOT NULL,
    body            NVARCHAR(2000)    NULL,
    action_url      NVARCHAR(1000)    NULL,
    entity_type     NVARCHAR(100)     NULL,
    entity_id       UNIQUEIDENTIFIER  NULL,
    is_read         BIT               NOT NULL DEFAULT 0,
    read_at         DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX idx_notif_user_unread ON notifications(user_id, is_read, created_at) WHERE is_read=0;

-- User notification preferences
CREATE TABLE notification_preferences (
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    rule_key        NVARCHAR(200)     NOT NULL,
    channel         NVARCHAR(20)      NOT NULL,
    is_enabled      BIT               NOT NULL DEFAULT 1,
    digest_mode     BIT               NOT NULL DEFAULT 0,  -- 0=immediate, 1=digest
    PRIMARY KEY (user_id, rule_key, channel)
);
```

### 4.1 Recipient Specification (JSON)

```json
[
  { "type": "role",       "role": "risk_reviewer" },
  { "type": "field",      "field_key": "owner" },
  { "type": "field",      "field_key": "assigned_to" },
  { "type": "user",       "user_id": "static-user-uuid" },
  { "type": "email",      "email": "compliance@example.com" },
  { "type": "webhook",    "webhook_key": "slack_grc_channel" }
]
```

---

## 5. Transactional Outbox Pattern

Notifications must **never be lost** even if the mail server is temporarily unavailable. This is achieved via the Transactional Outbox pattern:

1. When a notification is triggered (e.g., during a workflow transition), rows are **inserted into `notification_outbox` in the same transaction** as the triggering action.
2. A background worker (`NotificationDeliveryWorker`) polls the outbox and delivers pending notifications.
3. On successful delivery: `status = 'sent'`, `sent_at = now`
4. On failure: increment `attempt_count`, schedule retry with exponential backoff (max 5 attempts)
5. After 5 failures: `status = 'failed'`, alert admin

```java
@Scheduled(fixedDelay = 5000)
public void processOutbox() {
    List<NotificationOutbox> pending = outboxRepository.findPending(100);
    for (NotificationOutbox notification : pending) {
        try {
            deliveryRouter.deliver(notification);
            outboxRepository.markSent(notification.getId());
        } catch (Exception e) {
            outboxRepository.markFailed(notification.getId(), e.getMessage());
        }
    }
}
```

---

## 6. Email Templates

Email templates use **Thymeleaf** with a consistent HTML layout:

```html
<!-- base-layout.html (shared email shell) -->
<html xmlns:th="http://www.thymeleaf.org">
<body style="font-family: Arial, sans-serif; background: #f5f5f5;">
  <div style="max-width:600px; margin:0 auto; background:white; padding:32px;">
    <img th:src="${logoUrl}" alt="GRC Platform" style="height:40px;" />
    <hr/>
    <div th:replace="${content}"></div>
    <hr/>
    <p style="font-size:12px; color:#666;">
      You received this because you are a GRC Platform user at
      <span th:text="${orgName}"></span>.<br/>
      <a th:href="${unsubscribeUrl}">Unsubscribe from this notification</a>
    </p>
  </div>
</body>
</html>

<!-- risk_submitted_for_review.html -->
<div th:fragment="content">
  <h2>Risk Submitted for Review</h2>
  <p>A risk has been submitted for your review:</p>
  <table>
    <tr><td><strong>Risk:</strong></td><td th:text="${record.displayName}"></td></tr>
    <tr><td><strong>Score:</strong></td><td th:text="${record.riskScore}"></td></tr>
    <tr><td><strong>Rating:</strong></td><td th:text="${record.riskRating}"></td></tr>
    <tr><td><strong>Submitted by:</strong></td><td th:text="${actor.name}"></td></tr>
  </table>
  <a th:href="${actionUrl}" style="background:#1D4ED8; color:white; padding:12px 24px; 
     text-decoration:none; border-radius:4px; display:inline-block; margin-top:16px;">
    Review Risk
  </a>
</div>
```

---

## 7. Digest Mode

Users who select "digest" mode receive a single daily summary email instead of individual notifications:

```
📋 GRC Platform Daily Summary — April 4, 2026

You have 3 pending workflow tasks:
  • Review Risk: Data exfiltration (RISK-042) — Due in 2 days
  • Approve Control: MFA Implementation (CTRL-017) — Due today
  • Complete Test: Firewall Rule Review (TEST-089) — Overdue by 1 day

5 records were updated that you own since yesterday.
2 risks you own have breached their review SLA.

[Open My Tasks]  [View All Notifications]
```

The digest is generated by a `DigestService` that aggregates unread notifications grouped by entity type and urgency.

---

## 8. Webhook Channel

Webhook delivery enables Slack, Microsoft Teams, Jira, or any HTTP endpoint to receive GRC events:

```sql
CREATE TABLE webhook_endpoints (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    key             NVARCHAR(100)     NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    url             NVARCHAR(2000)    NOT NULL,
    secret          NVARCHAR(500)     NULL,       -- for HMAC-SHA256 signature verification
    headers         NVARCHAR(MAX)     NULL,       -- JSON: additional headers to include
    is_active       BIT               NOT NULL DEFAULT 1,
    CONSTRAINT uq_webhook_key_org UNIQUE (org_id, key)
);
```

Webhook payload (JSON POST):

```json
{
  "event":      "workflow.state_changed",
  "orgId":      "uuid",
  "timestamp":  "2026-04-04T10:00:00Z",
  "record": {
    "id":           "uuid",
    "recordNumber": 42,
    "displayName":  "Data exfiltration via phishing",
    "appKey":       "risk",
    "workflowState": "in_review",
    "url":          "https://grc.example.com/risks/uuid"
  },
  "transition": {
    "key":      "submit_for_review",
    "fromState": "draft",
    "toState":   "in_review",
    "actor": { "id": "user-uuid", "name": "Jane Doe" }
  }
}
```

The webhook is signed: `X-GRC-Signature: sha256=<HMAC-SHA256(secret, body)>`. Recipients should verify this signature.

---

## 9. GraphQL API

```graphql
type Query {
  myNotifications(filter: NotifFilterInput, page: PageInput): NotificationPage!
  unreadCount: Int!
  notificationPreferences: [NotificationPreference!]!
}

type Mutation {
  markNotificationRead(id: UUID!):   Boolean!
  markAllRead:                        Boolean!
  updateNotificationPreference(input: NotifPrefInput!): NotificationPreference!
}

type Subscription {
  notificationReceived(userId: UUID!): Notification!
  unreadCountChanged(userId: UUID!):   Int!
}

type Notification {
  id:         UUID!
  title:      String!
  body:       String
  actionUrl:  String
  entityType: String
  entityId:   UUID
  isRead:     Boolean!
  createdAt:  DateTime!
}
```

---

## 10. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should users be able to opt out of system-required notifications (e.g., task assignments)? | High |
| 2 | Multi-language templates? Requires i18n strategy. | Medium |
| 3 | Should digest scheduling be per-user timezone or fixed UTC? | Medium |
| 4 | Slack/Teams integrations: use webhooks (simpler) or dedicated OAuth apps (richer)? | Medium |
| 5 | Volume limits: should high-frequency rule triggers be debounced to prevent notification spam? | High |

---

*Previous: [09 — Audit Log & Versioning](09-audit-versioning.md) | Next: [11 — Search & Discovery](11-search-discovery.md)*
