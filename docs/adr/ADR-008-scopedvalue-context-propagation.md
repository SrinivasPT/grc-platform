# ADR-008 — Java 21 ScopedValue for Request Context Propagation

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The GRC platform is multi-tenant. Every database query must be scoped to the requesting organization (`org_id`). The `org_id` (and `user_id`) must be accessible deep in the service and repository layer without threading it as a method parameter through every call site.

The traditional approach uses `ThreadLocal`. However, Java 21 introduces Virtual Threads, and `ThreadLocal` values that have mutable state are not safe to carry across thread boundaries in structured concurrency. When a virtual thread is unmounted and remounted, `ThreadLocal` context can leak or be absent.

## Decision

**`ScopedValue<SessionContext>` (Java 21) replaces all `ThreadLocal` usage for request context propagation.**

```java
// In platform-core
public final class SessionContextHolder {
    public static final ScopedValue<SessionContext> SESSION = ScopedValue.newInstance();
}

// SessionContext record
public record SessionContext(UUID orgId, UUID userId, String username, List<String> roles) {}
```

**Binding in the filter chain (platform-api):**
```java
// SessionContextFilter — runs after JwtFreshnessFilter
ScopedValue.where(SessionContextHolder.SESSION, context)
    .run(() -> filterChain.doFilter(request, response));
```

**SQL Server `SESSION_CONTEXT` propagation:**
- On each JDBC connection checkout from the pool, a `ConnectionPreparer` sets:
  ```sql
  EXEC sp_set_session_context 'org_id', @orgId
  ```
- SQL Server row-level security policies read `SESSION_CONTEXT('org_id')` to filter rows.
- This is set via Hibernate `ConnectionProvider` interceptor — no manual call in service code.

## Consequences

**Positive:**
- Safe for Java 21 Virtual Threads — `ScopedValue` is immutable and thread-safe by design.
- `SessionContext` is immutable (record) — no risk of mutation mid-request.
- SQL layer automatically scoped — no risk of cross-tenant data leakage from a forgotten filter.
- `platform-core` code accesses `org_id` via `SessionContextHolder.SESSION.get()` without parameter threading.

**Negative:**
- `ScopedValue` is only accessible inside the `ScopedValue.where(...).run(...)` call stack — background workers must re-bind context explicitly.
- Slightly more boilerplate for background tasks than `ThreadLocal.set(...)`.

**Neutral:**
- Background workers (outbox worker, projection worker) bind `SessionContext` explicitly at task start using the `org_id` stored in the outbox/projection event record.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| `ThreadLocal` | Unsafe with Virtual Threads — values can leak between tasks sharing a carrier thread. Explicitly deprecated for context propagation in Java 21+. |
| Method parameter threading (pass `orgId` everywhere) | Requires every service and repository method signature to carry `orgId`. Dozens of method signatures changed. Error-prone and pollutes the API. |
| Spring Security `SecurityContextHolder` (also ThreadLocal) | Same problem as `ThreadLocal`. Spring 6 is moving toward `ReactiveSecurityContextHolder` (reactive) or `ScopedValue` patterns. Not yet propagated by Spring automatically in MVC. |
