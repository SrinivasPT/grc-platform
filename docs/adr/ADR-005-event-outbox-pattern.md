# ADR-005 — Transactional Outbox for All Async Side-Effects

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

Service methods that mutate state (create record, transition workflow, assign task) often need to trigger side effects: send email notification, POST to external webhook, sync to Neo4j. These side effects must not execute if the originating transaction rolls back (phantom notifications, phantom webhooks).

Early designs called notification and integration services directly within transactions. This creates phantom side-effects on rollback and couples the core domain to notification/integration infrastructure.

## Decision

**All async side-effects go through the `event_outbox` table.** No service in `platform-core` or `platform-workflow` calls notification or integration services directly.

**Pattern:**

```java
@Transactional
public void transitionWorkflow(TransitionCommand command) {
    WorkflowInstance updated = engine.transition(instance, command);
    workflowInstanceRepository.save(updated);

    // Publish to outbox — committed or rolled back with the same transaction
    eventOutboxService.publish(WorkflowTransitionedEvent.of(updated, command.actor()));
    // No direct call to notificationService, integrationService, neo4jService
}
```

**`event_outbox` table schema:**
- `id`, `org_id`, `event_type`, `payload` (JSON), `status` (PENDING/PROCESSING/DELIVERED/FAILED), `attempts`, `next_attempt_at`, `created_at`

**Outbox worker:**
- Polls `event_outbox WHERE status = 'PENDING' AND next_attempt_at <= NOW()` every 500ms.
- Runs in a virtual thread pool.
- Exponential backoff on failure: 1s, 2s, 4s, 8s, 16s, ...
- After `max_attempts` (configurable, default 10): mark `FAILED`, write to `event_deadletter`.
- At-least-once delivery — consumers must be idempotent.
- Notification and Integration services are consumers of the outbox worker.

## Consequences

**Positive:**
- Zero phantom side-effects — outbox record rolls back with the main transaction.
- Single retry/DLQ model — no separate retry logic in notification and integration.
- Notification and integration services are decoupled from the domain — swappable without touching `platform-core`.

**Negative:**
- Minimum ~500ms latency from event to delivery (tunable, but not synchronous).
- Additional `event_outbox` table to monitor and maintain DLQ.
- Consumers must be idempotent (delivery is at-least-once).

**Neutral:**
- The outbox worker runs in-process — no Kafka/RabbitMQ broker needed for this deployment scale.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| Direct call within transaction | Phantom side-effects on rollback. Email sent for a record that was never saved. |
| Message broker (Kafka) | Adds broker infrastructure, exactly-once complexity, and operational overhead. Not justified for single-bank scale. Outbox pattern achieves the same guarantee in-process. |
| `@TransactionalEventListener` | Fires after commit but is still in-process synchronous. Cannot be retried independently. No DLQ model. |
