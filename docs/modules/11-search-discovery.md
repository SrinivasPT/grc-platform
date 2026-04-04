# Module 11 — Search & Discovery

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model), Module 06 (Graph Projection), Module 07 (Auth)

---

## 1. Purpose

Search is the primary entry point for finding GRC records. Users need to locate risks, policies, controls, vendors, and other records quickly — by keyword, by structured filter, by relationship proximity, or by combination. This module defines the search architecture, query types, indexing strategy, and API design.

---

## 2. Search Types

| Type | Description | Example |
|------|-------------|---------|
| **Full-text keyword** | Search record titles, descriptions, and text fields | "phishing attack risk" |
| **Structured filter** | Exact/range/set filters on field values | `risk_rating = High AND owner = Jane Doe` |
| **Compound** | Combined keyword + filters | keyword "encryption" AND `status = active` |
| **Saved search** | Named, reusable queries | "My Open High Risks" |
| **Graph-aware** | Search based on relationship context | "All controls linked to NIST AC-1" |
| **Global search** | Search across all application types simultaneously | Cmd+K universal search |

---

## 3. Search Architecture

### 3.1 SQL Server Full-Text Search (Primary)

SQL Server 2025's built-in Full-Text Search handles the majority of search needs:

- Full-text indexes on `field_values_text.value`
- Tenant-filtered queries (always include `org_id = ?`)
- Ranked results via `CONTAINSTABLE` or `FREETEXTTABLE`
- Works well up to ~10 million text value rows per org

```sql
-- Full-text catalog and index
CREATE FULLTEXT CATALOG grc_ftcat AS DEFAULT;
CREATE FULLTEXT INDEX ON field_values_text(value LANGUAGE 1033)
    KEY INDEX pk_field_values_text
    ON grc_ftcat;
```

### 3.2 Structured Filter (SQL)

Structured filters translate directly to parameterized SQL WHERE clauses. No external search engine is needed for filtered queries.

### 3.3 Graph Search (Neo4j)

Relationship-based search queries are delegated to Neo4j (e.g., "all risks linked to a given vendor through any path").

### 3.4 Global Search Cache

For the global `Cmd+K` search (search across all application types), results are assembled from:
1. Full-text SQL search on `field_values_text` for matching records
2. Results grouped by `application.name`
3. Top 5 results per application type returned
4. Total < 200ms target (SQL FTS is fast enough at this scale)

---

## 4. Search Filter DSL

Search filters are expressed as a recursive JSON structure (reuses the same pattern as `RecordFilterInput` in the GraphQL API):

```json
{
  "and": [
    { "fieldKey": "risk_rating",   "operator": "IN",       "value": ["High","Critical"] },
    { "fieldKey": "owner",         "operator": "EQ",       "value": "user-uuid" },
    { "fieldKey": "due_date",      "operator": "LT",       "value": "2026-12-31" },
    { "or": [
        { "fieldKey": "category",  "operator": "EQ",       "value": "IT Security" },
        { "fieldKey": "category",  "operator": "EQ",       "value": "Legal" }
    ]}
  ]
}
```

### 4.1 Supported Operators

| Operator | Field Types | SQL Translation |
|----------|------------|----------------|
| `EQ` | all | `= ?` |
| `NEQ` | all | `<> ?` |
| `GT`, `GTE`, `LT`, `LTE` | number, date | `> ? `, `>= ?`, etc. |
| `IN` | all | `IN (?)` |
| `NOT_IN` | all | `NOT IN (?)` |
| `CONTAINS` | text | `LIKE '%?%'` |
| `STARTS_WITH` | text | `LIKE '?%'` |
| `IS_NULL` | all | `IS NULL` |
| `NOT_NULL` | all | `IS NOT NULL` |
| `FULL_TEXT` | text only | `CONTAINS(value, ?)` |
| `BETWEEN` | number, date | `BETWEEN ? AND ?` |

### 4.2 Filter Translation (SQL)

```java
public class FilterTranslator {
    public String toSql(RecordFilter filter, Map<String, Object> params) {
        return switch (filter.operator()) {
            case EQ      -> addParam(filter.fieldKey(), "=",        filter.value(), params);
            case IN      -> addParam(filter.fieldKey(), "IN",       filter.value(), params);
            case GT      -> addParam(filter.fieldKey(), ">",        filter.value(), params);
            case FULL_TEXT -> buildFtsJoin(filter.fieldKey(), filter.value(), params);
            case AND     -> filter.children().stream()
                                  .map(c -> toSql(c, params))
                                  .collect(joining(" AND ", "(", ")"));
            case OR      -> filter.children().stream()
                                  .map(c -> toSql(c, params))
                                  .collect(joining(" OR ",  "(", ")"));
            // ...
        };
    }
}
```

---

## 5. Saved Searches

Users can save frequently-used searches as named queries:

```sql
CREATE TABLE saved_searches (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    owner_id        UNIQUEIDENTIFIER  NULL REFERENCES users(id),  -- NULL = shared org-wide
    application_id  UNIQUEIDENTIFIER  NULL REFERENCES applications(id),
    name            NVARCHAR(255)     NOT NULL,
    search_query    NVARCHAR(MAX)     NOT NULL,  -- JSON: keywords + filter DSL + sort
    is_pinned       BIT               NOT NULL DEFAULT 0,
    is_shared       BIT               NOT NULL DEFAULT 0,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

Saved search payload:
```json
{
  "keywords": "encryption",
  "appId": "risk-app-uuid",
  "filters": {
    "and": [
      { "fieldKey": "risk_rating", "operator": "IN", "value": ["High","Critical"] },
      { "fieldKey": "status",      "operator": "EQ", "value": "active" }
    ]
  },
  "sort": { "fieldKey": "risk_score", "direction": "desc" }
}
```

---

## 6. Global Search (Cmd+K)

The global search is a cross-application keyword search accessible from the top navigation:

```graphql
type Query {
  globalSearch(orgId: UUID!, query: String!, limit: Int): GlobalSearchResult!
}

type GlobalSearchResult {
  apps: [AppSearchResult!]!
  totalCount: Int!
  query: String!
}

type AppSearchResult {
  application: Application!
  records: [RecordSummary!]!
  totalCount: Int!
}

type RecordSummary {
  id:          UUID!
  recordNumber: Int!
  displayName: String!
  appKey:      String!
  workflowState: String
  highlight:   String   # text excerpt showing matched keyword in context
}
```

Global search enforces per-application read permissions — a user only sees results for applications they have access to.

---

## 7. Search API (GraphQL)

```graphql
type Query {
  searchRecords(input: SearchInput!): SearchResult!
  savedSearches(appId: UUID): [SavedSearch!]!
}

type Mutation {
  createSavedSearch(input: CreateSavedSearchInput!): SavedSearch!
  updateSavedSearch(input: UpdateSavedSearchInput!): SavedSearch!
  deleteSavedSearch(id: UUID!): Boolean!
  pinSavedSearch(id: UUID!, isPinned: Boolean!): SavedSearch!
}

input SearchInput {
  orgId:     UUID!
  appId:     UUID
  keywords:  String
  filters:   RecordFilterInput
  sort:      RecordSortInput
  page:      PageInput
}

type SearchResult {
  records:    [Record!]!
  totalCount: Int!
  facets:     [SearchFacet!]!
}

type SearchFacet {
  fieldKey:   String!
  fieldName:  String!
  values:     [FacetValue!]!
}

type FacetValue {
  value:      String!
  label:      String
  count:      Int!
}
```

---

## 8. Faceted Search

Search results include **facets** — aggregate counts by filterable field values — enabling sidebar filtering (similar to e-commerce "filter by category"):

```json
// Example facets response
{
  "facets": [
    {
      "fieldKey": "risk_rating",
      "fieldName": "Risk Rating",
      "values": [
        { "value": "Critical", "count": 5 },
        { "value": "High",     "count": 18 },
        { "value": "Medium",   "count": 34 },
        { "value": "Low",      "count": 12 }
      ]
    },
    {
      "fieldKey": "category",
      "fieldName": "Category",
      "values": [
        { "value": "IT Security", "count": 27 },
        { "value": "Legal",       "count": 15 },
        { "value": "Operational", "count": 27 }
      ]
    }
  ]
}
```

Facets are computed via SQL `GROUP BY` on the filter result set (before pagination is applied).

### 8.1 High-Cardinality Facets

For fields with many distinct values (e.g., `owner_user`, `vendor_name`), returning all distinct values in a facet is impractical. Fields with > 20 distinct values in the result set are **truncated to the top 20** by frequency, with a `hasMore: true` flag in the response:

```graphql
type SearchFacet {
  fieldKey:   String!
  fieldName:  String!
  values:     [FacetValue!]!  # limited to 20
  hasMore:    Boolean!         # true if total distinct values > 20
  totalCount: Int!             # total distinct value count
}
```

When `hasMore = true`, the UI renders a searchable typeahead input for that facet instead of a checkbox list.

---

## 8a. Field-Level Permission Filtering

Search results must respect field-level read permissions. The search engine returns record IDs first, then field-level permissions are applied as a post-filter before returning results to the caller:

```java
public SearchResult search(SearchRequest request, User currentUser) {
    // Step 1: FTS + filter → record IDs (fast, SQL-level)
    List<UUID> candidateIds = searchAdapter.findMatchingIds(request);

    // Step 2: Batch permission check (one query per app type)
    List<UUID> permittedIds = permissionService
        .filterReadable(candidateIds, currentUser); // checks field+record ABAC

    // Step 3: Fetch display data only for permitted records
    return searchAdapter.hydrateResults(permittedIds, request.getPageable());
}
```

**Performance constraint:** Permission filtering is done in a single batch query using a temporary table / table-valued parameter, not per-record. For large result sets (> 1000 candidates), only the top 200 candidates are permission-checked (with a note in the response: `resultsTruncatedForPermissions: true`).

---

## 9. Performance Strategy

| Concern | Strategy |
|---------|---------|
| Full-text search on large text fields | SQL Server FTS index on `field_values_text.value` |
| Filter queries across field value tables | Composite indexes on (record_id, field_def_id, org_id) |
| Facet computation | SQL GROUP BY with proper index cover |
| Global search response time < 200ms | Limited to top 5 per app type; FTS is indexed |
| Saved search execution | Parsed + cached for 30 seconds |
| High-cardinality filter fields | Index on (field_def_id, value_text, org_id) for reference fields |

### 9.1 Search Result Caching

Search results are **not cached** by default (GRC data must be current). However, saved search results for admin dashboards may be cached with a configurable TTL (default: 60 seconds).

---

## 10. Future: Dedicated Search Engine

If SQL Server FTS becomes insufficient at scale (> 50M text rows, < 500ms FTS latency), the search module is designed to be augmented with an external engine:

- **Option A:** Azure Cognitive Search (managed, integrates with Azure SQL)
- **Option B:** OpenSearch / Elasticsearch (self-hosted)
- **Decision trigger:** FTS query p99 > 500ms under production load

The search module's service layer abstracts the search backend — `SearchService` interface has `SqlServerSearchAdapter` today and could add `OpenSearchAdapter` without changing the API contract.

---

## 11. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | Should search history (recent searches) be stored per user? | Low | |
| 2 | ~~Should field-level permissions filter search results?~~ | High | **Resolved:** Yes — see Section 8a. Batch permission post-filter after ID retrieval. Top 200 candidates checked for large result sets. |
| 3 | Phonetic / fuzzy search support? (SQL Server FTS has limited fuzzy support) | Medium | |
| 4 | Cross-tenant search for platform admins? | Medium | N/A — single bank deployment. |
| 5 | Search result ranking — should relevance score be exposed to users? | Low | |

---

*Previous: [10 — Notification Engine](10-notification-engine.md) | Next: [12 — Reporting & Dashboards](12-reporting-dashboards.md)*
