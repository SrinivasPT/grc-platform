# Module 07 — Authentication & Access Control

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 01 (Architecture), Module 02 (Data Model)

---

## 1. Purpose

This module defines how users are authenticated, how their identity is established per request, how permissions are enforced at every layer (application, record, field), and how multi-tenancy is isolated. It is the most critical security module — every other module depends on it.

---

## 2. Authentication

### 2.1 Supported Identity Modes

| Mode | Protocol | Use Case |
|------|----------|----------|
| External SSO | SAML 2.0 | Enterprise IdPs (Azure AD, Okta, PingIdentity) |
| External SSO | OIDC / OAuth2 | Cloud IdPs (Google Workspace, Azure AD, Auth0) |
| Local (fallback) | Username + password + JWT | Dev/test environments; orgs without SSO |
| API Key | Static bearer token | Machine-to-machine integration access |

**SAML 2.0 and OIDC are mandatory for production enterprise deployments.** Local authentication may be disabled per org via `org_settings`.

### 2.1a Multi-Factor Authentication (MFA)

MFA is **mandatory for all local accounts** in a banking environment. SSO-provided MFA (via Azure AD, Okta, PingIdentity) satisfies this requirement; the platform trusts the IdP's authentication assurance level.

For local accounts (dev/test or fallback):

| Method | Support | Notes |
|--------|---------|-------|
| TOTP (RFC 6238) | **Mandated** | Google Authenticator, Authy, any TOTP app |
| WebAuthn / FIDO2 | Supported | Hardware keys; phishing-resistant |
| Email OTP | Discouraged | Only for password reset flows |
| SMS OTP | Not supported | SMS is not considered secure for banking |

MFA enforcement rules:
- All local accounts must enroll TOTP before first login completes
- `user_settings.mfa_enrolled = false` results in redirect to TOTP setup page on every login
- Bypass is not available; org-level `org_settings.mfa_required = true` is hardcoded for banking

```sql
ALTER TABLE users ADD
    mfa_secret_encrypted  NVARCHAR(500) NULL,  -- AES-256 encrypted TOTP secret
    mfa_enrolled          BIT NOT NULL DEFAULT 0,
    mfa_backup_codes      NVARCHAR(MAX) NULL;  -- JSON: encrypted backup codes
```

### 2.1a Multi-Factor Authentication (MFA)

MFA is **mandatory for all local accounts** in a banking environment. SSO-provided MFA (via Azure AD, Okta, PingIdentity) satisfies this requirement; the platform trusts the IdP's authentication assurance level.

For local accounts (dev/test or fallback):

| Method | Support | Notes |
|--------|---------|-------|
| TOTP (RFC 6238) | **Mandated** | Google Authenticator, Authy, any TOTP app |
| WebAuthn / FIDO2 | Supported | Hardware keys; phishing-resistant |
| Email OTP | Discouraged | Only for password reset flows |
| SMS OTP | Not supported | SMS is not considered secure for banking |

MFA enforcement rules:
- All local accounts must enroll TOTP before first login completes
- `user_settings.mfa_enrolled = false` results in redirect to TOTP setup page on every login
- Bypass is not available; org-level `org_settings.mfa_required = true` is hardcoded for banking

```sql
ALTER TABLE users ADD
    mfa_secret_encrypted  NVARCHAR(500) NULL,  -- AES-256 encrypted TOTP secret
    mfa_enrolled          BIT NOT NULL DEFAULT 0,
    mfa_backup_codes      NVARCHAR(MAX) NULL;  -- JSON: encrypted backup codes
```

### 2.2 JWT Token Design

Tokens are issued by the platform's auth endpoint after successful identity validation. Short-lived access tokens + longer-lived refresh tokens:

| Token Type | Lifetime | Transport |
|-----------|----------|-----------|
| Access Token | 15 minutes | Authorization: Bearer header |
| Refresh Token | 8 hours (configurable) | HttpOnly secure cookie |

**JWT Payload:**

```json
{
  "sub":        "user-uuid",
  "org_id":     "org-uuid",
  "org_slug":   "acme-corp",
  "email":      "jane.doe@acme.com",
  "name":       "Jane Doe",
  "roles":      ["risk_manager", "compliance_viewer"],
  "session_id": "session-uuid",
  "iat":        1712196000,
  "exp":        1712196900,
  "jti":        "token-uuid"
}
```

Tokens are signed with **RS256** (asymmetric). The public key is published at `/.well-known/jwks.json`.

### 2.3 SAML 2.0 Flow

```
Browser               Platform API           Enterprise IdP (Azure AD / Okta)
  │                       │                           │
  │─ GET /auth/saml/init ─►│                           │
  │                       │─── AuthnRequest (redirect) ►│
  │◄──── IdP redirect ────│                           │
  │                       │                           │
  │──── POST credentials ──────────────────────────────►│
  │◄──── SAML Response ────────────────────────────────│
  │                       │                           │
  │─ POST /auth/saml/callback (assertion) ─►│          │
  │                       │─── validate assertion     │
  │                       │─── lookup/create user     │
  │                       │─── issue JWT              │
  │◄── set refresh cookie ─│                           │
  │◄── return access token─│                           │
```

### 2.4 SAML Config per Org

Each organization configures its own SAML IdP in `org_settings`:

```json
{
  "sso_provider":     "saml2",
  "saml_entity_id":   "https://grc.example.com/saml/acme",
  "saml_idp_url":     "https://login.microsoftonline.com/tenant/saml2",
  "saml_idp_cert":    "-----BEGIN CERTIFICATE-----...",
  "saml_email_attr":  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/email",
  "saml_name_attr":   "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name",
  "saml_roles_attr":  "http://schemas.xmlsoap.org/claims/Group",
  "role_mappings": {
    "GRC_Admins":    "platform_admin",
    "Risk_Managers": "risk_manager",
    "Viewers":       "compliance_viewer"
  }
}
```

### 2.5 Session Management

- Refresh tokens are stored server-side in the `user_sessions` table (allows forced logout)
- On logout: refresh token is invalidated; access token expires naturally (short TTL)
- On password change / account lock: all sessions revoked

```sql
CREATE TABLE user_sessions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    user_id         UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    refresh_token_hash NCHAR(64)      NOT NULL,  -- SHA-256 hash; never store raw token
    expires_at      DATETIME2         NOT NULL,
    is_revoked      BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    last_used_at    DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME(),
    ip_address      NVARCHAR(45)      NULL,
    user_agent      NVARCHAR(500)     NULL
);
CREATE INDEX idx_sessions_user ON user_sessions(user_id, is_revoked, expires_at);
```

---

## 3. Authorization Model

### 3.1 Hybrid RBAC + ABAC

The platform uses a **Role-Based Access Control (RBAC)** foundation with **Attribute-Based Access Control (ABAC)** extensions for record-level and field-level conditions.

| Layer | Mechanism | Example |
|-------|-----------|---------|
| Application-level | RBAC | "Risk Managers can read all records in the Risk application" |
| Record-level | ABAC | "Users can only see risks they own" |
| Field-level | RBAC | "Salary field only visible to HR roles" |
| Action-level | RBAC | "Only Approvers can trigger the approve workflow transition" |
| Org-unit-scoped | Scoped RBAC | "Regional Manager can only see records from their business unit" |

### 3.2 Permission Vocabulary

Permissions are expressed as: `{resource_type}:{action}`

| Resource Type | Actions |
|--------------|---------|
| `application` | `read`, `create`, `update`, `delete`, `configure` |
| `record` | `read`, `create`, `update`, `delete`, `approve`, `export` |
| `field` | `read`, `write` (field-level permission) |
| `report` | `read`, `create`, `run`, `export` |
| `workflow` | `transition:{transition_key}` |
| `admin` | `users`, `roles`, `config`, `audit_log`, `integrations` |

### 3.3 System Roles (Seeded on Org Creation)

| Role | Description |
|------|-------------|
| `platform_admin` | Full access to everything including admin config |
| `application_admin` | Configure applications, fields, layouts, rules (no user admin) |
| `risk_manager` | CRUD on risk records; read controls, issues |
| `risk_viewer` | Read-only access to risk records |
| `compliance_manager` | CRUD on compliance/policy records |
| `audit_manager` | CRUD on audit records and findings |
| `vendor_manager` | CRUD on vendor records and assessments |
| `control_owner` | Update controls assigned to them; read related risks |
| `workflow_approver` | Can approve/reject workflow transitions when assigned |
| `read_only` | Read-only access to all records in permitted applications |

Custom roles can be defined by `application_admin`.

### 3.4 Permission Evaluation Algorithm

```
function canAccess(user, resource, action):
  1. Load user's roles (from JWT claims, cached 2 min)
  2. Load role_permissions for those roles (cached per role, 5 min)
  3. Check application-level permission:
       any role has permission({resource_type: 'application', resource_id: appId, action: 'read'})
     → if false: DENY
  4. Check record-level ABAC rules (if any defined for this app):
       evaluate record_access_rules for this user/record combination
     → if DENY rule matches: DENY
  5. Check action-level permission:
       any role has permission({resource_type: 'record', action: action})
     → if false: DENY
  6. ALLOW
```

### 3.5 Record-Level Access Rules (ABAC)

Administrators can define record-level access rules per application. These are stored in `record_access_rules` and evaluated using a subset of the Rule Engine's expression DSL:

```sql
CREATE TABLE record_access_rules (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    application_id  UNIQUEIDENTIFIER  NOT NULL REFERENCES applications(id),
    role_id         UNIQUEIDENTIFIER  NULL REFERENCES roles(id),   -- NULL = all roles
    effect          NVARCHAR(10)      NOT NULL CHECK (effect IN ('allow', 'deny')),
    condition_dsl   NVARCHAR(MAX)     NOT NULL,  -- Rule Engine JSON expression
    -- Example: { "eq": [{"field":"owner"},{"field":"current_user.id"}] }
    -- Example: { "eq": [{"field":"org_unit_id"},{"field":"current_user.org_unit_id"}] }
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

### 3.6 Field-Level Permissions

Fields marked with `field_level_access` in their config are only included in API responses if the requesting user's roles include the `field:read` permission for that field definition:

```json
// field_definitions.config for a sensitive field
{
  "field_level_access": {
    "read_roles":  ["hr_manager", "platform_admin"],
    "write_roles": ["hr_manager"]
  }
}
```

---

## 4. Multi-Tenancy Enforcement

### 4.1 Tenant Resolution on Every Request

Every authenticated request resolves the tenant (organization) from the JWT `org_id` claim. This is available throughout the request lifecycle via a Spring `TenantContext` thread-local:

```java
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            UUID orgId = UUID.fromString(jwt.getToken().getClaimAsString("org_id"));
            UUID userId = UUID.fromString(jwt.getToken().getSubject());
            TenantContext.set(new TenantInfo(orgId, userId));
        }
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();  // always clear after request
        }
    }
}
```

### 4.2 JPA Hibernate Filter

A Hibernate `@Filter` is applied globally to all entities carrying `org_id`:

```java
@Entity
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "orgId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "org_id = :orgId")
public class RecordEntity { ... }
```

The filter is enabled in a `TenantAwareSessionFactory` interceptor that sets `orgId` from `TenantContext` on every session open. This means a developer cannot accidentally query across tenant boundaries — they would have to explicitly disable the filter.

### 4.3 SQL Server Row-Level Security (Defense in Depth)

As a secondary enforcement layer, SQL Server RLS policies are applied:

```sql
CREATE FUNCTION dbo.fn_tenant_predicate(@org_id UNIQUEIDENTIFIER)
RETURNS TABLE WITH SCHEMABINDING
AS RETURN SELECT 1 AS fn_result
WHERE @org_id = CAST(SESSION_CONTEXT(N'org_id') AS UNIQUEIDENTIFIER);

CREATE SECURITY POLICY tenant_isolation_policy
ADD FILTER PREDICATE dbo.fn_tenant_predicate(org_id) ON dbo.records,
ADD FILTER PREDICATE dbo.fn_tenant_predicate(org_id) ON dbo.record_relations,
ADD FILTER PREDICATE dbo.fn_tenant_predicate(org_id) ON dbo.field_values_text,
-- ... all tenant-scoped tables
WITH (STATE = ON);
```

The application sets `SESSION_CONTEXT` on connection acquisition from the pool.

---

## 5. API Key Authentication (Machine-to-Machine)

For integration scenarios where a human user is not present:

```sql
CREATE TABLE api_keys (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    key_hash        NCHAR(64)         NOT NULL UNIQUE,  -- SHA-256 of the raw key; never store raw
    scopes          NVARCHAR(MAX)     NOT NULL,          -- JSON: ["record:read","record:create"]
    expires_at      DATETIME2         NULL,
    last_used_at    DATETIME2         NULL,
    is_active       BIT               NOT NULL DEFAULT 1,
    created_by      UNIQUEIDENTIFIER  NOT NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

API keys are presented as `Authorization: Bearer grc_<base64-encoded-random-64-bytes>`. The platform hashes the incoming key (SHA-256) and looks it up in the table. The key's `scopes` array is used as a synthetic role set.

---

## 6. Audit Trail for Auth Events

All authentication events are written to the `audit_log` with `entity_type = 'auth'`:

| Event | When |
|-------|------|
| `login_success` | Successful authentication |
| `login_failed` | Failed login attempt (tracks IP, capped at 5 per minute) |
| `token_refresh` | Refresh token used |
| `logout` | Explicit logout |
| `session_revoked` | Admin-forced logout |
| `saml_assertion_received` | SAML callback received |
| `password_changed` | Password update |
| `account_locked` | Account locked after failed attempts |
| `api_key_created` | New API key generated |
| `api_key_revoked` | API key deactivated |

---

## 7. Password Policy (Local Auth)

When local authentication is enabled:
- Minimum 12 characters
- Must include uppercase, lowercase, number, and special character
- Last 12 passwords may not be reused
- Account locked after 5 consecutive failures (unlocked by admin or time-based policy)
- Passwords hashed with **bcrypt** (cost factor 12)

---

## 8. Spring Security Configuration Summary

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)  // for @PreAuthorize
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/graphql/**", "/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(grcJwtConverter())))
            .build();
    }

    @Bean
    SecurityFilterChain auth(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/auth/**", "/actuator/health", "/actuator/readiness")
            .csrf(csrf -> csrf.ignoringRequestMatchers("/auth/saml/callback"))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
```

---

## 9. Permission Annotations (Service Layer)

```java
@Service
public class RecordService {

    @PreAuthorize("@permissionEvaluator.canCreate(#appId, authentication)")
    public Record createRecord(UUID appId, CreateRecordInput input) { ... }

    @PreAuthorize("@permissionEvaluator.canRead(#recordId, authentication)")
    public Record getRecord(UUID recordId) { ... }

    @PreAuthorize("@permissionEvaluator.canUpdate(#recordId, authentication)")
    public Record updateRecord(UUID recordId, UpdateRecordInput input) { ... }
}
```

---

## 10. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | ~~Should MFA (TOTP) be supported for local accounts?~~ | Medium | **Resolved:** MFA is **mandatory** for all local accounts (TOTP via RFC 6238). See Section 2.1a. |
| 2 | IP allowlisting per org — requirement for high-security deployments? | Medium | |
| 3 | Should field-level permissions be evaluated on every record in list queries? (Performance impact) | High | |
| 4 | Cross-org data sharing (for orgs in the same corporate group)? | Future | |
| 5 | Should API keys support expiry and rotation reminders? | Medium | |

---

*Previous: [06 — Graph Projection](06-graph-projection.md) | Next: [08 — Workflow Engine](08-workflow-engine.md)*
