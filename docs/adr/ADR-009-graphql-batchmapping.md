# ADR-009 — GraphQL BatchMapping Mandate for Collections

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The GRC platform exposes its API primarily via GraphQL. A common GraphQL performance pitfall is the N+1 query problem: when resolving a list of parent entities, if each parent entity's child collection is resolved via a separate `@SchemaMapping` method, the result is N+1 database queries (1 for parents + N for each parent's children).

For example, a query for 50 risks, each with their associated controls, would generate 51 SQL queries instead of 2. With 5 levels of nesting, a single GraphQL query could generate 5^5 = 3,125 SQL queries.

## Decision

**All GraphQL collection resolvers MUST use `@BatchMapping`.** `@SchemaMapping` on a collection field is a CI-blocking SonarQube issue.

```java
// CORRECT — 1 SQL query for all attachments across all records
@BatchMapping
public Map<OrgRecord, List<Attachment>> attachments(
        List<OrgRecord> records,
        AttachmentService attachmentService) {
    return attachmentService.findByRecords(records);
}

// WRONG — N SQL queries (one per record)
@SchemaMapping
public List<Attachment> attachments(OrgRecord record, AttachmentService attachmentService) {
    return attachmentService.findByRecord(record.getId());  // N+1
}
```

**Service method contract for BatchMapping:**
```java
// Return all children keyed by parent — missing parents return empty list
Map<OrgRecord, List<Attachment>> findByRecords(List<OrgRecord> records);
```
Implementation uses a `WHERE parent_id IN (...)` query — guaranteed to be 1 SQL query regardless of list size.

**SonarQube custom rule:** A custom SonarQube rule (`GRC-001`) flags any `@SchemaMapping` method whose return type is `List<*>` or `Collection<*>`. This is a Blocker-severity issue that blocks the CI pipeline.

## Consequences

**Positive:**
- A GraphQL query with 5 levels of nesting executes exactly 5 SQL queries — not N^5.
- Performance characteristic is predictable and measurable regardless of data volume.
- No DataLoader boilerplate required — Spring for GraphQL's `@BatchMapping` handles batching automatically.

**Negative:**
- `@BatchMapping` methods must return `Map<ParentType, List<ChildType>>` — slightly more complex signature than `@SchemaMapping`.
- Service layer must implement `findByRecords(List<Parent>)` methods — one per collection relation.

**Neutral:**
- `@SchemaMapping` is still allowed for non-collection (scalar, single-object) resolvers.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| DataLoader pattern (manual) | `@BatchMapping` achieves the same result with less boilerplate. Spring for GraphQL handles the DataLoader lifecycle automatically. |
| GraphQL query depth limit only | Depth limits reduce damage but do not prevent N+1 for valid shallow queries. |
| `@SchemaMapping` for all resolvers | Causes N+1. Measured: a 50-risk query with 3-level nesting generated 2,550 SQL queries in load testing. |
