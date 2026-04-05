# ADR-004 — Keycloak as Identity Broker (Apigee → Keycloak → Ping Identity)

**Status:** Accepted
**Date:** 2026-04-05
**Deciders:** Platform Architecture Team

## Context

The bank has an existing enterprise identity provider (Ping Identity) that issues SAML 2.0 assertions. The GRC platform needs JWT-based auth for its React SPA and API layer. The bank also uses Apigee for API governance, rate-limiting, and external developer portal.

Direct integration between the GRC platform and Ping Identity would tightly couple the platform to the bank's IdP vendor. Any IdP migration (common in banking M&A) would require platform code changes.

## Decision

**Three-layer identity chain:**

```
React SPA / External Client
       ↓ HTTPS
Apigee (API Gateway)         ← rate limiting, API key management, mTLS termination
       ↓ forwarded request
Spring Boot (GRC API)
       ↓ 401 → redirect / token exchange
Keycloak 24.x                ← OIDC token issuer, identity broker
       ↓ SAML assertion
Ping Identity                ← bank's enterprise IdP (SAML 2.0)
```

**JWT specification:**
- Issuer: Keycloak realm `grc-platform`
- Expiry: **5 minutes** (short-lived, freshness enforced by `JwtFreshnessFilter`)
- Claims: `sub` (user id), `org_id`, `roles[]`, `role_version` (incremented on any permission change)
- `role_version` checked against Redis cache on every request — stale version triggers re-auth

**SCIM 2.0 inbound provisioning** from Ping Identity to Keycloak for user lifecycle management.

## Consequences

**Positive:**
- Platform is IdP-agnostic — swap Ping for another SAML provider without changing GRC code.
- Keycloak handles SAML→OIDC bridging — GRC platform only speaks OIDC/JWT.
- `role_version` claim + freshness filter ensures permission revocation is effective within 5 minutes.
- Apigee provides rate limiting, audit logging, and developer portal independently of application code.

**Negative:**
- Three-layer chain adds latency to the first token exchange (~50ms).
- Keycloak and Apigee are additional infrastructure components to operate.
- 5-minute token expiry requires SPA to handle token refresh silently (handled by Apollo Client auth link).

**Neutral:**
- Keycloak realm configuration is in `infrastructure/keycloak/realm-export.json` — source of truth.

## Alternatives Considered

| Alternative | Reason Rejected |
|-------------|----------------|
| Direct Spring Security SAML to Ping Identity | Tight coupling to Ping Identity. Platform code changes required on any IdP migration. |
| Longer JWT expiry (15-60 min) | Permission revocation takes up to 60 min to be effective. Unacceptable for banking-grade security. |
| No Apigee | Loses API governance, rate limiting, and external developer portal managed by the bank's platform team. |
