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
- `*SliceConfig` Spring configuration classes — one per GRC domain module

---

## SliceConfig — Per-Module Spring Configuration

`ServiceConfig.java` contains **only platform engine beans** (RecordService, AuditService, RuleEvaluator, WorkflowEngine, etc.). Every GRC domain module gets its own `@Configuration` class in `platform-api/config/`.

```
platform-api/config/
  ServiceConfig.java        ← engine beans only (RecordService, AuditService, …)
  OrgSliceConfig.java       ← OrgHierarchyService + handlers + validators
  PolicySliceConfig.java    ← PolicyService + handlers + validators
  RiskSliceConfig.java      ← RiskService + handlers + validators
  ControlSliceConfig.java   ← ControlService + handlers + validators
```

**Rule:** Adding a new GRC module means adding a new `*SliceConfig.java` — never touching `ServiceConfig.java`.

```java
@Configuration
public class RiskSliceConfig {
    @Bean
    public RiskService riskService(RiskScoreRepository repo,
            RiskAppetiteThresholdRepository appetiteRepo, AuditService auditService) {
        return new RiskServiceImpl(
            new ComputeRiskScoreHandler(repo, List.of()),
            new UpdateResidualScoreHandler(repo, appetiteRepo, auditService, List.of()),
            appetiteRepo, auditService);
    }
}
```

---

## Mutation Pattern — Use Command Record as @Argument

Do NOT create a duplicate `*Input` record that mirrors a `*Command` record. Use the command record directly as the `@Argument`:

```java
// CORRECT — no Input class needed; Command IS the contract
@MutationMapping
public OrgUnitDto createOrgUnit(@Argument CreateOrgUnitCommand input) {
    return orgHierarchyService.createUnit(input);
}

// WRONG — mechanical duplication with no transformation
@MutationMapping
public OrgUnitDto createOrgUnit(@Argument CreateOrgUnitInput input) {
    var cmd = new CreateOrgUnitCommand(input.name(), input.unitType(), ...);
    return orgHierarchyService.createUnit(cmd);
}
```

Spring for GraphQL maps JSON field names to Java record components by name automatically. Create a separate `*Input` record **only** when the GraphQL schema field names genuinely differ from the command record field names.

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

## Mutation Pattern — Transactions and Audit

Every GraphQL mutation method follows this pattern:

```java
@MutationMapping
public RecordPayload createRecord(@Argument @Valid CreateRecordCommand input,
                                  @AuthenticationPrincipal JwtPrincipal principal) {
    return recordService.create(input);   // service owns @Transactional + audit
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

1. New GRC module? → Create `*SliceConfig.java` in `config/`. Do not touch `ServiceConfig.java`.
2. New mutation? → Use `@Argument *Command input` directly. No `*Input` record unless schema fields differ.
3. New collection resolver? → Must use `@BatchMapping`.
4. New command record? → Define it in the GRC slice package in `platform-core` first.
5. New filter? → Raise ADR first, then add after the last existing filter in the chain.

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
