# platform-workflow — Copilot Instructions

Extends `backend/.github/copilot-instructions.md` and the global instructions. All parent rules apply.

## Purpose

`platform-workflow` implements the workflow state machine. It depends only on `platform-core`. It contains:
- `WorkflowEngine` — state machine transitions, guard evaluation, action dispatch
- `WorkflowInstance` entity lifecycle
- Delegation and escalation logic
- Outbox event publishing for side effects (notifications, integrations)

---

## State Machine Rules

- All valid transitions declared in `WorkflowDefinition` — never hardcoded `if/else` transition logic.
- Transitions are guarded by `RuleEvaluator` (from `platform-core`) — no custom guard impl.
- After a successful transition, publish to `event_outbox` — never call notification or integration services directly.
- **Optimistic locking on `workflow_instances.version`** prevents parallel completion race:
  ```sql
  UPDATE workflow_instances SET status = ?, version = version + 1
  WHERE id = ? AND version = ?
  ```
  If 0 rows updated → throw `WorkflowConcurrentModificationException`.

---

## Delegation & Escalation

- `delegation_assignments` table tracks open delegations: `delegated_to`, `due_date`, `escalation_days`.
- `EscalationScheduler` runs every hour (virtual thread pool) — promotes past-due delegations to manager.
- Escalation is capped at the org root — never escalates above `org_hierarchy.root_node_id`.

---

## Outbox Pattern

```java
// CORRECT: publish to outbox within the same transaction
workflowInstanceRepository.save(updated);
eventOutboxService.publish(WorkflowTransitionedEvent.of(instance, transition, actor));
// Both committed or neither

// WRONG: call notification service directly
notificationService.send(...)  // may succeed even if transaction rolls back
```

---

## Testing

- Test every valid transition path with a unit test (no Spring context needed).
- Test every guard rejection (guard returns false → transition blocked).
- Test the optimistic locking path: two concurrent completion attempts → one succeeds, one gets `WorkflowConcurrentModificationException`.
- Integration tests for `EscalationScheduler` use Testcontainers SQL Server with `@SpringBootTest`.

---

## Agent Checklist (platform-workflow)

1. New transition? → Add to `WorkflowDefinition` first, then write failing test.
2. New action? → Goes into outbox, never direct call. Write outbox-publish test.
3. Touching delegation? → Verify escalation boundary test still passes.
