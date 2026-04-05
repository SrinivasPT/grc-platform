---
applyTo: 'backend/platform-api/**'
description: 'platform-api module rules: GraphQL resolvers, REST controllers, security filters, BatchMapping mandate. Use when writing or reviewing platform-api code.'
---

# platform-api — Module Rules

## Purpose

`platform-api` is the entry point for all external communication. It contains:

- Spring Boot `main` class
- GraphQL controllers (queries + mutations + subscriptions)
- REST controllers (file upload, webhook callbacks, SCIM, bulk export)
- Security filters (JWT freshness, idempotency, rate limit)
- `ScopedValue` binding filter (extracts org_id/user_id from JWT into `SESSION_CONTEXT`)

---

## GraphQL — BatchMapping Mandate

**All collection resolvers MUST use `@BatchMapping`.** `@SchemaMapping` on a collection causes N+1 queries and is a CI-blocking Sonar issue.

```java
// CORRECT
@BatchMapping
public Map<OrgRecord, List<Attachment>> attachments(List<OrgRecord> records,
                                                     AttachmentService service) {
    return service.findByRecords(records);
}

// WRONG — causes N+1
@SchemaMapping
public List<Attachment> attachments(OrgRecord record, AttachmentService service) {
    return service.findByRecord(record.getId());
}
```

---

## Mutation Pattern

Every GraphQL mutation method follows this pattern:

```java
@MutationMapping
public RecordPayload createRecord(@Argument CreateRecordInput input,
                                  @AuthenticationPrincipal JwtPrincipal principal) {
    var command = CreateRecordCommand.from(input, principal.orgId());
    return recordService.create(command);   // service owns @Transactional + audit
}
```

- Controllers never declare `@Transactional`.
- Controllers never call `auditService` directly.
- All input validated via `@Valid` before service call.

---

## Security Filters (Execution Order)

1. `JwtFreshnessFilter` — validates JWT age ≤ 5 min, checks `role_version` claim matches Redis cache.
2. `IdempotencyFilter` — reads `X-Idempotency-Key` header; returns cached response for duplicate `POST`/`PATCH`/`DELETE`.
3. `SessionContextFilter` — extracts `org_id`, `user_id` from validated JWT; binds to `ScopedValue<SessionContext>`.
4. `RateLimitFilter` — per-org token bucket via Redis.

Never reorder these filters. If a new filter is needed, raise an ADR first.

---

## REST Controllers

- File upload: `POST /api/v1/files` — multipart, max 100MB, virus-scanned before write.
- Webhook callbacks: `POST /api/v1/webhooks/{integrationId}` — signature-verified.
- SCIM 2.0: `GET/POST/PUT/DELETE /api/v1/scim/Users` and `/Groups`.
- Bulk export: `GET /api/v1/export/{moduleType}` — streams NDJSON, never loads full result set into memory.

---

## Agent Checklist (platform-api)

1. Is this a new collection resolver? → Must use `@BatchMapping`.
2. Does the mutation need a new `Command` record in `platform-core`? → Define the record there first.
3. New filter? → Raise ADR first, then add after the last existing filter in the chain.
