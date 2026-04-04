# Module 08 — Workflow Engine

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model), Module 03 (Rule Engine), Module 07 (Auth)

---

## 1. Purpose

The Workflow Engine drives every approval, review, escalation, and multi-step process in the GRC platform. It provides a **declarative state machine** where organizations configure workflow behaviors without writing code. Every GRC process — policy approval, risk acceptance, control testing, audit finding remediation, vendor assessment — is an instance of this engine.

This is not a task manager. It is a **business process engine** with:
- Configurable states and transitions
- Role-based actor assignment (static, dynamic, delegated)
- Conditional transitions (via Rule Engine DSL)
- SLA tracking and escalation
- Parallel approval paths
- Full audit trail

---

## 2. Core Concepts

| Concept | Description |
|---------|-------------|
| **Workflow Definition** | The template: states, transitions, actors, SLAs. Stored in config. |
| **Workflow Instance** | A running instance of a definition tied to a specific record. |
| **State** | A named step in the process (e.g., `draft`, `in_review`, `approved`). |
| **Transition** | A possible move from one state to another with conditions and actor constraints. |
| **Task** | An action item assigned to a specific person or role to perform a transition. |
| **Actor** | Who can perform a transition: static role, dynamic field reference, system trigger. |
| **SLA** | Time limit on a state; triggers escalation or auto-transition when breached. |

---

## 3. Workflow Definition Schema (JSON Config)

Stored in `workflow_definitions.config` (JSON):

```json
{
  "id":          "risk-approval-workflow-v1",
  "name":        "Risk Approval Workflow",
  "app_key":     "risk",
  "version":     1,
  "initial_state": "draft",
  "states": [
    {
      "key":         "draft",
      "label":       "Draft",
      "terminal":    false,
      "color":       "#6B7280",
      "description": "Risk is being authored"
    },
    {
      "key":         "in_review",
      "label":       "In Review",
      "terminal":    false,
      "color":       "#F59E0B",
      "sla_hours":   72,
      "sla_action":  "escalate",
      "sla_escalate_to_role": "risk_manager"
    },
    {
      "key":         "approved",
      "label":       "Approved",
      "terminal":    false,
      "color":       "#10B981"
    },
    {
      "key":         "rejected",
      "label":       "Rejected",
      "terminal":    false,
      "color":       "#EF4444"
    },
    {
      "key":         "closed",
      "label":       "Closed",
      "terminal":    true,
      "color":       "#374151"
    }
  ],
  "transitions": [
    {
      "key":          "submit_for_review",
      "label":        "Submit for Review",
      "from_states":  ["draft", "rejected"],
      "to_state":     "in_review",
      "actors": {
        "type":       "field_reference",
        "field_key":  "owner"
      },
      "conditions":   [
        {
          "rule_dsl": { "not_null": { "field": "risk_treatment" } },
          "error":    "Risk treatment must be selected before submitting"
        }
      ],
      "on_enter_actions": [
        { "type": "create_task", "role": "risk_reviewer", "label": "Review Risk" },
        { "type": "notify",      "template": "risk_submitted_for_review",
          "recipients": [{ "type": "role", "role": "risk_reviewer" }] }
      ]
    },
    {
      "key":          "approve",
      "label":        "Approve",
      "from_states":  ["in_review"],
      "to_state":     "approved",
      "actors": {
        "type":       "role",
        "role":       "risk_reviewer"
      },
      "conditions":   [],
      "on_enter_actions": [
        { "type": "notify", "template": "risk_approved",
          "recipients": [{ "type": "field", "field_key": "owner" }] }
      ]
    },
    {
      "key":          "reject",
      "label":        "Reject",
      "from_states":  ["in_review"],
      "to_state":     "rejected",
      "actors": {
        "type":       "role",
        "role":       "risk_reviewer"
      },
      "require_comment": true,
      "on_enter_actions": [
        { "type": "notify", "template": "risk_rejected",
          "recipients": [{ "type": "field", "field_key": "owner" }] }
      ]
    },
    {
      "key":          "close",
      "label":        "Close",
      "from_states":  ["approved"],
      "to_state":     "closed",
      "actors": {
        "type":       "role",
        "role":       "risk_manager"
      }
    }
  ]
}
```

---

## 4. Parallel Approval Paths

For workflows requiring multiple approvers simultaneously (e.g., a risk requiring sign-off from both the CISO and CFO):

```json
{
  "key":          "dual_approve",
  "label":        "Awaiting Dual Approval",
  "from_states":  ["in_review"],
  "to_state":     "approved",
  "parallel_actors": [
    { "type": "role", "role": "ciso",   "task_label": "CISO Approval" },
    { "type": "role", "role": "cfo",    "task_label": "CFO Approval" }
  ],
  "completion_mode": "all",    // "all" = all must approve; "any" = first approval wins
  "on_enter_actions": [
    { "type": "create_task", "role": "ciso", "label": "Review and Approve Risk (CISO)" },
    { "type": "create_task", "role": "cfo",  "label": "Review and Approve Risk (CFO)" }
  ]
}
```

The workflow instance tracks partial completion:
- Both tasks created simultaneously
- Each completes independently
- When `completion_mode=all` and all tasks complete → state transitions to `approved`
- If any actor rejects → transition to `rejected`, remaining tasks cancelled

---

## 5. Database Schema

```sql
CREATE TABLE workflow_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    version         INT               NOT NULL DEFAULT 1,
    is_active       BIT               NOT NULL DEFAULT 1,
    config          NVARCHAR(MAX)     NOT NULL,  -- JSON workflow definition
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

-- One instance per record (a record has one active workflow instance)
CREATE TABLE workflow_instances (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    record_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES records(id),
    definition_id   UNIQUEIDENTIFIER  NOT NULL REFERENCES workflow_definitions(id),
    current_state   NVARCHAR(100)     NOT NULL,
    started_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    completed_at    DATETIME2         NULL,
    sla_due_at      DATETIME2         NULL,       -- computed from state SLA config
    is_sla_breached BIT               NOT NULL DEFAULT 0,
    CONSTRAINT uq_one_instance_per_record UNIQUE (record_id)
);
CREATE INDEX idx_wi_state ON workflow_instances(current_state, org_id);
CREATE INDEX idx_wi_sla   ON workflow_instances(sla_due_at, is_sla_breached) WHERE completed_at IS NULL;

-- History of all state transitions
CREATE TABLE workflow_history (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    instance_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES workflow_instances(id),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    from_state      NVARCHAR(100)     NULL,       -- NULL for initial state
    to_state        NVARCHAR(100)     NOT NULL,
    transition_key  NVARCHAR(100)     NULL,
    actor_id        UNIQUEIDENTIFIER  NULL,       -- user who triggered
    comment         NVARCHAR(2000)    NULL,
    transitioned_at DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    system_triggered BIT             NOT NULL DEFAULT 0
);

-- Tasks assigned to users for review/approval
CREATE TABLE workflow_tasks (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    instance_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES workflow_instances(id),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    transition_key  NVARCHAR(100)     NOT NULL,
    label           NVARCHAR(500)     NOT NULL,
    instructions    NVARCHAR(2000)    NULL,
    assigned_role   NVARCHAR(100)     NULL,
    assigned_user_id UNIQUEIDENTIFIER NULL REFERENCES users(id),
    status          NVARCHAR(20)      NOT NULL DEFAULT 'open'
                    CHECK (status IN ('open','in_progress','completed','cancelled')),
    due_at          DATETIME2         NULL,
    completed_at    DATETIME2         NULL,
    completed_by    UNIQUEIDENTIFIER  NULL REFERENCES users(id),
    decision        NVARCHAR(20)      NULL,       -- 'approved' | 'rejected' | null
    comment         NVARCHAR(2000)    NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX idx_wt_user   ON workflow_tasks(assigned_user_id, status, org_id);
CREATE INDEX idx_wt_role   ON workflow_tasks(assigned_role, status, org_id);
CREATE INDEX idx_wt_inst   ON workflow_tasks(instance_id, status);
```

---

## 6. WorkflowService — Java Implementation

### 6.1 Core Interface

```java
public interface WorkflowService {

    WorkflowInstance startWorkflow(UUID orgId, UUID recordId, UUID definitionId,
                                   UUID initiatorId);

    WorkflowInstance triggerTransition(UUID orgId, UUID instanceId,
                                       String transitionKey, UUID actorId,
                                       String comment);

    WorkflowInstance completeTask(UUID orgId, UUID taskId, UUID actorId,
                                  TaskDecision decision, String comment);

    WorkflowInstance assignTask(UUID orgId, UUID taskId, UUID actorId,
                                UUID assigneeId);

    List<WorkflowTask> getMyTasks(UUID orgId, UUID userId, TaskFilter filter,
                                  PageRequest page);

    WorkflowDefinition getDefinition(UUID orgId, UUID appId);
}
```

### 6.2 Transition Execution Logic

```
triggerTransition(instanceId, transitionKey, actorId):
  1. Load WorkflowInstance + WorkflowDefinition
  2. Verify current_state is in transition.from_states → else throw InvalidTransitionException
  3. Verify actorId is authorized for this transition:
       - actor.type == 'role'            → check actorId has that role
       - actor.type == 'field_reference' → check actorId matches field value on record
  4. Evaluate conditions (Rule Engine):
       - All conditions must pass → else throw WorkflowConditionException(condition.error)
  5. Check require_comment → if true and comment is blank → throw ValidationException
  6. Execute on_enter_actions in order:
       a. 'create_task' → insert workflow_tasks rows
       b. 'notify'      → queue notification (async, non-blocking)
       c. 'set_field'   → update field value on record (via RecordService)
  7. Update workflow_instances.current_state to transition.to_state
  8. Insert workflow_history row
  9. Update records.workflow_state to match
  10. If new state is terminal → set workflow_instances.completed_at
  11. Compute new sla_due_at from new state's SLA config
  12. Write audit_log entry
  13. Emit WorkflowStateChangedEvent (for subscriptions)
  14. Return updated WorkflowInstance
```

All steps within a transition execute in a single database transaction. If any step fails, the entire transition is rolled back.

---

## 7. SLA Tracking and Escalation

A scheduled job runs every minute to check for breached SLAs:

```java
@Scheduled(fixedDelay = 60_000)
public void checkSlaBreaches() {
    List<WorkflowInstance> breached = workflowRepository
        .findBreachedSlas(Instant.now());

    for (WorkflowInstance instance : breached) {
        String slaAction = getStateConfig(instance).slaAction();
        switch (slaAction) {
            case "escalate"       -> escalate(instance);
            case "auto_transition" -> autoTransition(instance);
            case "notify_only"    -> notifySlaBreached(instance);
        }
        instance.setSlaBreached(true);
        workflowRepository.save(instance);
    }
}
```

**Escalation:** Creates a new task for the `sla_escalate_to_role` and sends an escalation notification.

**Auto-transition:** Moves the workflow to the configured fallback state (e.g., auto-reject after 72 hours).

---

## 8. Workflow Delegation

Users can temporarily delegate their workflow tasks to another user:

```sql
CREATE TABLE workflow_delegations (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    delegator_id    UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    delegate_id     UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    valid_from      DATETIME2         NOT NULL,
    valid_until     DATETIME2         NOT NULL,
    escalation_days INT               NOT NULL DEFAULT 0,  -- 0 = no escalation
    scope           NVARCHAR(MAX)     NULL,   -- JSON: optional app/workflow scope restriction
    is_active       BIT               NOT NULL DEFAULT 1,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 8.1 Delegation with Escalation

Standard one-to-one delegation is extended to support **escalation** when a delegate does not act within the SLA:

1. A `workflow_delegation` record includes an `escalation_days` field (default `0` = no escalation).
2. A scheduled job checks active delegations daily: if the delegate has tasks older than `escalation_days` from the delegation start date, a new task is created for the **delegator's direct manager** (resolved via `org_unit` hierarchy from Module 26).
3. The original delegate task is retained (not cancelled) — both the delegate and the manager's manager see the task.
4. An escalation notification is sent to the manager and the original delegator.

```java
@Scheduled(cron = "0 0 8 * * *")  // 8 AM daily
public void checkDelegationEscalations() {
    List<WorkflowDelegation> escalatable = delegationRepository
        .findActiveWithPendingEscalation(LocalDate.now());

    for (WorkflowDelegation delegation : escalatable) {
        User manager = orgUnitService.findDirectManager(delegation.getDelegatorId());
        if (manager != null) {
            workflowTaskService.createEscalationTask(delegation, manager);
            notificationService.sendEscalationNotification(delegation, manager);
        }
    }
}
```

When a user has an active delegation, tasks assigned to them appear in both their and their delegate's task inbox.

---

## 9. Workflow Task Inbox (User-Facing)

The workflow task inbox is a first-class UI feature — the primary homepage for most GRC users:

```graphql
type Query {
  myWorkflowTasks(filter: TaskFilterInput, page: PageInput): TaskPage!
  allWorkflowTasks(orgId: UUID!, filter: TaskFilterInput, page: PageInput): TaskPage!  # admin
}

type WorkflowTask {
  id:             UUID!
  label:          String!
  instructions:   String
  status:         TaskStatus!
  record:         Record!
  transitionKey:  String!
  dueAt:          DateTime
  isOverdue:      Boolean!
  assignedUser:   User
  assignedRole:   String
  createdAt:      DateTime!
}

type Subscription {
  workflowTaskAssigned(userId: UUID!): WorkflowTask!
}
```

---

## 10. Workflow Metrics

KPIs available for reporting (Module 12):

| Metric | Description |
|--------|-------------|
| `avg_cycle_time` | Average time from start to terminal state per workflow |
| `state_duration` | Average/median time spent in each state |
| `sla_breach_rate` | Percentage of instances that breached SLA |
| `pending_tasks_by_role` | Count of open tasks by assigned role |
| `bottleneck_states` | States with longest average wait time |

---

## 11. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should workflows support loops (e.g., review → reject → revise → review)? | Confirmed | Supported via `from_states` array on transitions. |
| 2 | Should there be a visual workflow designer in the admin UI? | High (MVP?) | |
| 3 | Webhook trigger for external systems when workflow state changes? | High | |
| 4 | ~~Multiple active workflows per record?~~ | Medium | **Resolved:** No for MVP — one active workflow instance per record. Multiple workflows can be defined but only one may be active at a time. |
| 5 | ~~Workflow versioning?~~ | High | **Resolved:** Active instances stay on the version they started with. An **admin migration tool** (`WorkflowMigrationService`) allows explicit migration of individual instances to a newer version with field mapping rules. Migration requires recording the `migrated_by` user and a `migration_note` in `workflow_history`. |

---

*Previous: [07 — Auth & Access Control](07-auth-access-control.md) | Next: [09 — Audit Log & Versioning](09-audit-versioning.md)*
