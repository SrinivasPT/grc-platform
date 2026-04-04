# Module 04 — API Layer (GraphQL & REST)

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** Module 01, 02, 03, 07 (Auth)

---

## 1. Purpose

This module defines the complete API surface of the GRC platform — the contract between the frontend and backend, and between the platform and external integrators. It specifies the GraphQL schema design, REST endpoint inventory, authentication flow, error handling, and performance strategies.

---

## 2. API Strategy

### 2.1 GraphQL as Primary API

GraphQL is chosen as the primary API layer for internal client-server communication because:

| Requirement | How GraphQL Addresses It |
|-------------|-------------------------|
| Config-driven forms have variable field sets | Clients declare exactly what fields they need |
| GRC records have deeply nested relationships | Single query traverses record → fields → related records |
| Dashboard widgets need different data shapes | Each widget sends its own query — no over-fetching |
| Real-time workflow status updates | GraphQL Subscriptions over WebSocket |
| Self-documenting API contract | GraphQL schema is the API contract |
| Type safety across client-server | Schema generates TypeScript types for the frontend |

**Implementation:** Spring for GraphQL 1.3.x with `graphql-java` 21.x under the hood.

### 2.2 REST for Specific Cases

REST endpoints are used where GraphQL is not the right fit:

| Use Case | Why REST, Not GraphQL |
|----------|----------------------|
| File upload | Multipart form data — not suited for GraphQL |
| Incoming webhooks from third-party systems | Simple HTTP callback, no query language needed |
| Health/readiness checks | Standard Spring Actuator endpoints |
| Integration connector APIs | Third parties expect REST, not GraphQL |
| Bulk data export (CSV, Excel) | Streaming response — not suited for GraphQL |
| OAuth2/SAML callback URLs | Protocol-defined HTTP endpoints |

### 2.3 API Versioning

- **GraphQL:** Schema is evolved using field deprecation. Old fields are deprecated with `@deprecated(reason: "...")` before removal. Breaking changes follow a 2-release deprecation window.
- **REST:** URL-versioned: `/api/v1/...`. New major versions introduced only for breaking changes.

---

## 3. GraphQL Schema Design

### 3.1 Scalar Types

```graphql
scalar UUID
scalar DateTime
scalar JSON          # For rule DSL, layout config, computed values
scalar BigDecimal
scalar Upload        # For file uploads via GraphQL multipart (limited use)
```

### 3.2 Core Record Types

```graphql
type Organization {
  id:         UUID!
  name:       String!
  slug:       String!
  status:     OrgStatus!
  settings:   [OrgSetting!]!
  createdAt:  DateTime!
}

type Application {
  id:             UUID!
  orgId:          UUID!
  name:           String!
  internalKey:    String!
  description:    String
  icon:           String
  isActive:       Boolean!
  configVersion:  Int!
  fieldDefinitions(includeSystem: Boolean): [FieldDefinition!]!
  layouts(type: LayoutType): [LayoutDefinition!]!
  rules(type: RuleType): [RuleDefinition!]!
  recordCount:    Int!
}

type Record {
  id:             UUID!
  orgId:          UUID!
  application:    Application!
  recordNumber:   Int!
  displayName:    String
  status:         String!
  workflowState:  String
  version:        Int!
  fieldValues:    [FieldValue!]!
  computedValues: JSON
  relations(type: String, direction: RelationDirection): [RecordRelation!]!
  attachments:    [RecordAttachment!]!
  workflowInstance: WorkflowInstance
  auditHistory(limit: Int, offset: Int): AuditPage!
  createdAt:      DateTime!
  updatedAt:      DateTime!
  createdBy:      User!
  updatedBy:      User!
}

type FieldDefinition {
  id:             UUID!
  applicationId:  UUID!
  name:           String!
  internalKey:    String!
  fieldType:      FieldType!
  isRequired:     Boolean!
  isSystem:       Boolean!
  isSearchable:   Boolean!
  displayOrder:   Int!
  config:         JSON
  validationRules: JSON
  configVersion:  Int!
}

union FieldValue = TextFieldValue | NumberFieldValue | DateFieldValue | ReferenceFieldValue

type TextFieldValue {
  fieldDefinitionId: UUID!
  fieldKey:          String!
  value:             String
}

type NumberFieldValue {
  fieldDefinitionId: UUID!
  fieldKey:          String!
  value:             BigDecimal
}

type DateFieldValue {
  fieldDefinitionId: UUID!
  fieldKey:          String!
  value:             DateTime
}

type ReferenceFieldValue {
  fieldDefinitionId: UUID!
  fieldKey:          String!
  refs: [RecordReference!]!
}

type RecordReference {
  refType:  RefType!
  refId:    UUID!
  label:    String
}

type RecordRelation {
  id:           UUID!
  relationType: String!
  direction:    RelationDirection!
  record:       Record!
  metadata:     JSON
  createdAt:    DateTime!
}

type RecordAttachment {
  id:           UUID!
  originalName: String!
  mimeType:     String!
  fileSizeBytes: Int!
  scanStatus:   ScanStatus!
  downloadUrl:  String!
  version:      Int!
  createdAt:    DateTime!
  createdBy:    User!
}
```

### 3.3 Query Operations

```graphql
type Query {
  # ─── Application Config ───────────────────────────────────────────
  applications(orgId: UUID!): [Application!]!
  application(id: UUID!): Application
  applicationByKey(orgId: UUID!, key: String!): Application

  # ─── Record Queries ───────────────────────────────────────────────
  record(id: UUID!): Record
  records(
    appId:    UUID!
    filter:   RecordFilterInput
    sort:     [RecordSortInput!]
    page:     PageInput
  ): RecordPage!

  # Related records via relationship graph
  relatedRecords(
    recordId:     UUID!
    relationType: String!
    direction:    RelationDirection
    filter:       RecordFilterInput
    page:         PageInput
  ): RecordPage!

  # ─── Search ───────────────────────────────────────────────────────
  searchRecords(input: SearchInput!): SearchResult!

  # ─── Users & Org ──────────────────────────────────────────────────
  currentUser: User!
  users(orgId: UUID!, filter: UserFilterInput, page: PageInput): UserPage!
  orgUnits(orgId: UUID!): [OrgUnit!]!
  roles(orgId: UUID!): [Role!]!

  # ─── Workflow ─────────────────────────────────────────────────────
  workflowDefinitions(appId: UUID!): [WorkflowDefinition!]!
  myWorkflowTasks(filter: TaskFilterInput, page: PageInput): TaskPage!

  # ─── Reports ──────────────────────────────────────────────────────
  reportDefinitions(orgId: UUID!): [ReportDefinition!]!
  runReport(reportId: UUID!, params: JSON): ReportResult!

  # ─── Value Lists ──────────────────────────────────────────────────
  valueLists(orgId: UUID!): [ValueList!]!
  valueList(id: UUID!): ValueList
  valueListByKey(orgId: UUID!, key: String!): ValueList
}
```

### 3.4 Mutation Operations

```graphql
type Mutation {
  # ─── Record CRUD ──────────────────────────────────────────────────
  createRecord(input: CreateRecordInput!):    Record!
  updateRecord(input: UpdateRecordInput!):    Record!    # expects version for optimistic concurrency
  deleteRecord(id: UUID!, reason: String):    Boolean!
  restoreRecord(id: UUID!):                   Record!

  # ─── Relationships ────────────────────────────────────────────────
  addRelation(input: AddRelationInput!):      RecordRelation!
  removeRelation(id: UUID!):                  Boolean!

  # ─── Workflow ─────────────────────────────────────────────────────
  triggerWorkflowTransition(input: WorkflowTransitionInput!): WorkflowInstance!
  completeWorkflowTask(input: CompleteTaskInput!):             WorkflowTask!
  assignWorkflowTask(taskId: UUID!, assigneeId: UUID!):       WorkflowTask!

  # ─── Application Config (Admin) ───────────────────────────────────
  createApplication(input: CreateApplicationInput!):              Application!
  updateApplication(input: UpdateApplicationInput!):              Application!
  createFieldDefinition(input: CreateFieldDefInput!):             FieldDefinition!
  updateFieldDefinition(input: UpdateFieldDefInput!):             FieldDefinition!
  saveLayoutDefinition(input: SaveLayoutInput!):                  LayoutDefinition!
  saveRuleDefinition(input: SaveRuleInput!):                      RuleDefinition!
  deleteRuleDefinition(id: UUID!):                                Boolean!

  # ─── Value Lists ──────────────────────────────────────────────────
  createValueList(input: CreateValueListInput!):   ValueList!
  addValueListItem(input: AddValueListItemInput!): ValueListItem!
  updateValueListItem(input: UpdateVLItemInput!):  ValueListItem!

  # ─── User Management ──────────────────────────────────────────────
  inviteUser(input: InviteUserInput!):       User!
  updateUser(input: UpdateUserInput!):       User!
  deactivateUser(id: UUID!):                 Boolean!
  assignRole(userId: UUID!, roleId: UUID!, orgUnitId: UUID): Boolean!
  revokeRole(userId: UUID!, roleId: UUID!, orgUnitId: UUID): Boolean!
}
```

### 3.5 Subscription Operations

```graphql
type Subscription {
  # Real-time record updates (for collaborative editing / workflow inbox)
  recordUpdated(recordId: UUID!):       Record!
  workflowTaskAssigned(userId: UUID!):  WorkflowTask!
  notificationReceived(userId: UUID!):  Notification!
  recordWorkflowStateChanged(recordId: UUID!): WorkflowInstance!
}
```

### 3.6 Input Types (Key Examples)

```graphql
input CreateRecordInput {
  appId:        UUID!
  fieldValues:  [FieldValueInput!]!
}

input UpdateRecordInput {
  id:           UUID!
  version:      Int!                   # optimistic concurrency
  fieldValues:  [FieldValueInput!]!
}

input FieldValueInput {
  fieldKey:     String!
  textValue:    String
  numberValue:  BigDecimal
  dateValue:    DateTime
  refValues:    [UUID!]               # for reference/multi-select fields
}

input RecordFilterInput {
  fieldKey:     String
  operator:     FilterOperator!
  value:        JSON
  and:          [RecordFilterInput!]
  or:           [RecordFilterInput!]
}

input PageInput {
  page:         Int! = 1
  pageSize:     Int! = 25            # max 200
}

input SearchInput {
  orgId:        UUID!
  query:        String!
  appIds:       [UUID!]
  filters:      [RecordFilterInput!]
  page:         PageInput
}

enum FilterOperator {
  EQ NEQ GT GTE LT LTE IN NOT_IN CONTAINS STARTS_WITH IS_NULL NOT_NULL
}

enum RelationDirection { CHILDREN PARENTS BOTH }
enum RefType          { VALUE_LIST_ITEM RECORD USER ORG_UNIT }
enum FieldType        { TEXT_SHORT TEXT_LONG INTEGER DECIMAL CURRENCY PERCENTAGE
                        DATE DATETIME BOOLEAN SINGLE_SELECT MULTI_SELECT
                        USER_REFERENCE USER_MULTI RECORD_REFERENCE RECORD_MULTI
                        ORG_UNIT ATTACHMENT CALCULATED MATRIX }
enum LayoutType       { RECORD_FORM LIST_VIEW DASHBOARD REPORT }
enum RuleType         { CALCULATION AGGREGATION VALIDATION VISIBILITY
                        WORKFLOW_CONDITION NOTIFICATION_TRIGGER DATA_FILTER }
enum ScanStatus       { PENDING CLEAN INFECTED SKIPPED }
enum OrgStatus        { ACTIVE SUSPENDED ARCHIVED }
```

---

## 4. N+1 Problem — DataLoader Pattern

GraphQL's field resolver architecture is susceptible to N+1 queries (1 query for the list + N queries for each item's sub-fields). This is solved using **Spring for GraphQL's `@BatchMapping`** support (built on Facebook's DataLoader):

```java
@Controller
public class RecordGraphQlController {

    // BAD: Would trigger N queries for N records
    // @SchemaMapping(typeName = "Record", field = "fieldValues")

    // GOOD: Batches all record IDs into one DB call
    @BatchMapping(typeName = "Record", field = "fieldValues")
    public Map<Record, List<FieldValueProjection>> fieldValues(List<Record> records) {
        var ids = records.stream().map(Record::getId).toList();
        var values = fieldValueService.loadBatch(ids);
        return records.stream().collect(toMap(r -> r, r -> values.getOrDefault(r.getId(), List.of())));
    }

    @BatchMapping(typeName = "Record", field = "createdBy")
    public Map<Record, User> createdBy(List<Record> records) {
        var userIds = records.stream().map(Record::getCreatedBy).collect(toSet());
        var users = userService.loadByIds(userIds);
        return records.stream().collect(toMap(r -> r, r -> users.get(r.getCreatedBy())));
    }
}
```

All `@BatchMapping` resolvers must be implemented for any list-returning query to prevent accidental N+1 in production.

---

## 5. Authentication Flow

### 5.1 JWT Token Flow

```
Client                      API Layer                    Identity Provider
  │                              │                              │
  │──── POST /auth/token ────────►│                              │
  │     (user credentials)       │──── Validate via OIDC ──────►│
  │                              │◄─── ID + Access Token ───────│
  │◄─── { access_token,          │                              │
  │      refresh_token }         │                              │
  │                              │                              │
  │──── GraphQL: query { ... }   │                              │
  │     Authorization: Bearer    │                              │
  │                              │── decode JWT, extract org_id, user_id, roles
  │                              │── inject TenantContext
  │◄─── GraphQL response ────────│
```

### 5.2 JWT Claims Structure

```json
{
  "sub":    "user-uuid",
  "org_id": "org-uuid",
  "email":  "user@example.com",
  "name":   "John Smith",
  "roles":  ["risk_manager", "compliance_viewer"],
  "iat":    1712196000,
  "exp":    1712282400
}
```

### 5.3 Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)  // using stateless JWT, CSRF not applicable
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/readiness").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/graphql", "/api/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(grcJwtConverter())))
            .build();
    }
}
```

---

## 6. Error Handling

### 6.1 GraphQL Error Envelope

```json
{
  "data": null,
  "errors": [
    {
      "message": "Due date must be in the future",
      "locations": [{ "line": 2, "column": 3 }],
      "path": ["updateRecord"],
      "extensions": {
        "code": "VALIDATION_ERROR",
        "field": "due_date",
        "recordId": "uuid"
      }
    }
  ]
}
```

### 6.2 Error Code Registry

| Code | HTTP Equivalent | Meaning |
|------|----------------|---------|
| `VALIDATION_ERROR` | 400 | Field-level validation failed |
| `CONCURRENCY_CONFLICT` | 409 | Optimistic lock version mismatch |
| `NOT_FOUND` | 404 | Entity does not exist |
| `PERMISSION_DENIED` | 403 | Insufficient permissions |
| `UNAUTHENTICATED` | 401 | No valid auth token |
| `WORKFLOW_TRANSITION_INVALID` | 422 | Invalid state transition |
| `RULE_CONFIG_ERROR` | 500 | Rule DSL invalid (config error, not user error) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

### 6.3 REST Error Envelope

```json
{
  "error": {
    "code":    "VALIDATION_ERROR",
    "message": "One or more fields failed validation",
    "details": [
      { "field": "due_date", "message": "Due date must be in the future" }
    ],
    "traceId": "abc-123-def"
  }
}
```

---

## 7. REST Endpoint Inventory

### 7.1 File Management

```
POST   /api/v1/files/upload
       Body: multipart/form-data, field name: file
       Params: recordId (UUID), fieldKey (optional)
       Response: { fileId, originalName, mimeType, fileSizeBytes, scanStatus }

GET    /api/v1/files/{fileId}/download
       Auth: Bearer token + file access check
       Response: file stream with Content-Disposition header

DELETE /api/v1/files/{fileId}
       Soft-deletes the attachment
```

### 7.2 Authentication

```
POST   /auth/token
       Body: { grantType: 'password'|'refresh_token', ... }
       Response: { accessToken, refreshToken, expiresIn }

POST   /auth/refresh
       Body: { refreshToken }
       Response: { accessToken, expiresIn }

GET    /auth/saml/init?orgSlug={slug}
       Redirects to IdP

POST   /auth/saml/callback
       SAML assertion callback

GET    /auth/oidc/callback
       OIDC code callback
```

### 7.3 Bulk Operations

```
POST   /api/v1/records/import
       Body: multipart CSV or JSON
       Response: { jobId } (async import)

GET    /api/v1/records/import/{jobId}/status
       Response: { status, processed, failed, errors }

POST   /api/v1/records/export
       Body: { appId, filter, format: 'csv'|'excel'|'json' }
       Response: { downloadUrl } (async export)
```

### 7.4 Webhooks (Inbound Integration)

```
POST   /api/v1/integrations/{connectorKey}/inbound
       Body: connector-specific JSON payload
       Response: 202 Accepted
```

### 7.5 Health & Ops

```
GET    /actuator/health
GET    /actuator/readiness
GET    /actuator/info
GET    /actuator/metrics
GET    /actuator/prometheus
```

---

## 8. Rate Limiting

All API endpoints are rate-limited per organization:

| Endpoint Class | Default Limit |
|---------------|--------------|
| GraphQL queries | 1000 req/min per org |
| GraphQL mutations | 200 req/min per org |
| File upload | 50 req/min per org |
| Bulk import | 5 concurrent jobs per org |
| Integration webhooks | 500 req/min per connector |

Limits are configurable per organization in `org_settings`. Rate limiting is implemented via `bucket4j` (token bucket algorithm) backed by an in-memory store (upgradeable to Redis for clustered deployments).

---

## 9. CORS Configuration

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of(allowedOriginsPattern));  // from config
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-ID"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

## 10. Performance Considerations

### 10.1 Query Complexity Limiting

Deeply nested GraphQL queries can be expensive. A query complexity limit is enforced:

```java
@Bean
GraphQlSourceBuilderCustomizer complexityLimiter() {
    return builder -> builder.configureGraphQl(graphQl ->
        graphQl.queryExecutionStrategy(
            new InstrumentedExecutionStrategy(new MaxQueryComplexityInstrumentation(200))
        )
    );
}
```

### 10.2 Query Depth Limiting

Maximum query depth: 12 levels. Prevents recursive relationship traversal attacks.

### 10.3 Response Caching

- Application config (field definitions, layouts, rules): cached 5 minutes per org
- Value lists: cached 10 minutes per org
- User permissions: cached 2 minutes per user
- Record queries: not cached (GRC data must be current)

### 10.4 Pagination Enforcement

Every list-returning query **requires** pagination. Offset pagination for most lists; cursor-based pagination for audit log and large datasets.

```graphql
type RecordPage {
  items:      [Record!]!
  totalCount: Int!
  page:       Int!
  pageSize:   Int!
  hasNext:    Boolean!
}
```

---

## 11. API Documentation

The GraphQL schema is the living API contract. It is:
- Auto-published at `/graphql-schema` (development) and accessible via GraphiQL (dev only)
- Exported to TypeScript types during frontend build via `graphql-codegen`
- Versioned in source control alongside the backend code

---

## 12. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should GraphQL subscriptions use WebSocket or SSE? WebSocket more capable, SSE simpler. | Medium |
| 2 | Do we need a public API for third-party integrators (separate from internal API)? | High |
| 3 | API gateway needed for production (Kong, NGINX)? Affects rate limiting implementation. | Medium |
| 4 | Should we support GraphQL persisted queries (for mobile/offline support)? | Low |
| 5 | Query cost analysis: who defines complexity scores for custom types? | Medium |

---

*Previous: [03 — Rule Engine](03-rule-engine.md) | Next: [05 — Form & Layout Engine](05-form-layout-engine.md)*
