# Module 12 — Reporting & Dashboards

> **Tier:** 2 — Platform Services
> **Status:** In Design
> **Dependencies:** Module 02, Module 07 (Auth), Module 11 (Search)

---

## 1. Purpose

Reporting and dashboards translate raw GRC data into actionable insights for executives, managers, and auditors. This module provides configurable dashboards, parameterized reports, heat maps, trend charts, and scheduled exports. It is the primary output layer of the platform — evidence of GRC program health.

---

## 2. Report Types

| Type | Description | Example |
|------|-------------|---------|
| **List report** | Paginated, filterable, sortable rows | "All open high risks by owner" |
| **Summary report** | Aggregated counts and metrics | "Risk counts grouped by category and rating" |
| **Trend report** | Metrics over time | "Risk score trend over last 12 months" |
| **Crosstab (matrix)** | Two-dimensional aggregation | "Risks by likelihood × impact" |
| **Heat map** | Risk register 5×5 matrix visualization | Standard risk heat map |
| **KPI scorecard** | Key metrics with threshold indicators | "Control effectiveness scorecard by domain" |
| **Compliance coverage** | Framework requirement coverage % | "NIST SP 800-53 coverage by control family" |
| **Gantt/timeline** | Date-range activities | "Policy review schedule" |

---

## 3. Dashboard Architecture

A dashboard is a configurable grid of **widgets**. Each widget is independently configured and queries the backend independently.

### 3.1 Dashboard Configuration (JSON)

```json
{
  "id":      "risk-overview-dashboard",
  "name":    "Risk Overview",
  "grid":    12,
  "widgets": [
    {
      "id":     "kpi-total-risks",
      "type":   "kpi_card",
      "col":    0, "row": 0, "w": 3, "h": 2,
      "config": {
        "title":        "Total Active Risks",
        "query":        { "appKey": "risk", "filters": [{"fieldKey":"status","operator":"EQ","value":"active"}] },
        "metric":       "count",
        "comparison":   { "type": "previous_period", "period": "30d" },
        "color_rule":   { "threshold": 50, "above_color": "red", "below_color": "green" }
      }
    },
    {
      "id":   "chart-risks-by-rating",
      "type": "bar_chart",
      "col":  3, "row": 0, "w": 5, "h": 4,
      "config": {
        "title":      "Risks by Rating",
        "query":      { "appKey": "risk", "filters": [], "groupBy": "risk_rating" },
        "metric":     "count",
        "color_map":  { "Critical":"#EF4444","High":"#F97316","Medium":"#EAB308","Low":"#22C55E" }
      }
    },
    {
      "id":   "heat-map",
      "type": "risk_heat_map",
      "col":  8, "row": 0, "w": 4, "h": 4,
      "config": {
        "title":       "Risk Heat Map",
        "x_axis":      "likelihood",
        "y_axis":      "impact",
        "x_list_key":  "likelihood_levels",
        "y_list_key":  "impact_levels",
        "group_by":    "risk_rating"
      }
    },
    {
      "id":   "top-risks-table",
      "type": "record_list",
      "col":  0, "row": 4, "w": 12, "h": 5,
      "config": {
        "title":   "Top 10 Highest-Scoring Risks",
        "query":   {
          "appKey":  "risk",
          "filters": [{"fieldKey":"status","operator":"EQ","value":"active"}],
          "sort":    { "fieldKey": "risk_score", "direction": "desc" }
        },
        "columns": ["record_number","title","risk_rating","owner","workflow_state","due_date"],
        "limit":   10
      }
    }
  ]
}
```

### 3.2 Widget Types

| Widget Type | Description |
|------------|-------------|
| `kpi_card` | Single metric with trend indicator |
| `bar_chart` | Vertical or horizontal bar chart |
| `line_chart` | Trend over time |
| `pie_chart` | Proportional breakdown |
| `donut_chart` | Like pie with center metric |
| `risk_heat_map` | 2D risk matrix grid |
| `record_list` | Tabular list of records (mini list report) |
| `compliance_coverage` | Progress bars per framework requirement family |
| `workflow_funnel` | Count of records per workflow state |
| `text` | Static text/markdown content |
| `iframe` | Embedded external URL (restricted to allowlist) |

---

## 4. Report Definitions

Reports are configured and stored in the database:

```sql
CREATE TABLE report_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    description     NVARCHAR(1000)    NULL,
    report_type     NVARCHAR(50)      NOT NULL
                    CHECK (report_type IN ('list','summary','trend','crosstab','heatmap',
                                           'kpi','compliance_coverage','custom')),
    application_id  UNIQUEIDENTIFIER  NULL REFERENCES applications(id),
    config          NVARCHAR(MAX)     NOT NULL,  -- JSON: full report definition
    is_public       BIT               NOT NULL DEFAULT 0,  -- visible to all users in org
    owner_id        UNIQUEIDENTIFIER  NOT NULL REFERENCES users(id),
    last_run_at     DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dashboard_definitions (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    name            NVARCHAR(255)     NOT NULL,
    dashboard_type  NVARCHAR(50)      NOT NULL DEFAULT 'custom',
    config          NVARCHAR(MAX)     NOT NULL,  -- JSON widget grid config
    is_default      BIT               NOT NULL DEFAULT 0,
    is_public       BIT               NOT NULL DEFAULT 0,
    owner_id        UNIQUEIDENTIFIER  NOT NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);

-- Scheduled report delivery
CREATE TABLE scheduled_reports (
    id              UNIQUEIDENTIFIER  NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
    org_id          UNIQUEIDENTIFIER  NOT NULL,
    report_id       UNIQUEIDENTIFIER  NOT NULL REFERENCES report_definitions(id),
    schedule_cron   NVARCHAR(100)     NOT NULL,   -- e.g. '0 8 * * 1' = every Monday 8am
    export_format   NVARCHAR(20)      NOT NULL DEFAULT 'pdf'
                    CHECK (export_format IN ('pdf','excel','csv')),
    recipients      NVARCHAR(MAX)     NOT NULL,   -- JSON: [{type:'user',id:...},{type:'email',...}]
    is_active       BIT               NOT NULL DEFAULT 1,
    last_run_at     DATETIME2         NULL,
    created_at      DATETIME2         NOT NULL DEFAULT SYSUTCDATETIME()
);
```

---

## 5. Report Query Engine

Each report definition includes a `query` section that the reporting engine translates to SQL:

```json
{
  "report_type": "summary",
  "app_key":     "risk",
  "filters": [
    { "fieldKey": "status",      "operator": "EQ",  "value": "active" },
    { "fieldKey": "created_at",  "operator": "GTE", "value": "${params.start_date}" }
  ],
  "group_by": ["risk_rating", "category"],
  "metrics": [
    { "name": "count",           "function": "COUNT", "field": "id" },
    { "name": "avg_risk_score",  "function": "AVG",   "field": "risk_score" },
    { "name": "max_risk_score",  "function": "MAX",   "field": "risk_score" }
  ],
  "sort": [
    { "metric": "count", "direction": "desc" }
  ]
}
```

The `${params.start_date}` syntax supports runtime parameterization — reports can accept parameters from the user at runtime.

### 5.1 Report Parameterization

```json
{
  "parameters": [
    {
      "key":      "start_date",
      "label":    "From Date",
      "type":     "date",
      "required": true,
      "default":  "FIRST_DAY_OF_YEAR"
    },
    {
      "key":      "rating_filter",
      "label":    "Risk Rating",
      "type":     "multi_select",
      "list_key": "risk_ratings",
      "required": false
    }
  ]
}
```

---

## 6. Risk Heat Map

The risk heat map is a first-class widget type given its importance in GRC:

```
           IMPACT
           Low  Med  High  V.High  Critical
L    High  [ ]  [ ]  [2]   [4]     [1]
I    Med   [ ]  [3]  [6]   [3]     [0]
K    Low   [2]  [4]  [5]   [1]     [0]
E  V.Low   [1]  [2]  [2]   [0]     [0]
L    Min   [3]  [1]  [0]   [0]     [0]
```

Each cell shows the count of active risks at that position. Cells are color-coded by risk severity zone. Clicking a cell opens a filtered record list for that cell's coordinate.

---

## 7. Export Engine

Reports can be exported in three formats:

| Format | Use Case | Technology |
|--------|----------|-----------|
| PDF | Formal reports for executives/auditors | iText 8 or JasperReports |
| Excel (.xlsx) | Data analysis by business users | Apache POI |
| CSV | Data import into other tools | Standard CSV writer |

Exports are generated **asynchronously** for large result sets:

```
POST /api/v1/reports/{id}/export?format=excel
→ 202 Accepted { "jobId": "uuid" }

GET  /api/v1/reports/exports/{jobId}/status
→ { "status": "complete", "downloadUrl": "..." }
```

Small result sets (< 1000 rows) are generated synchronously and returned inline.

---

## 8. Compliance Coverage Report

A specialized report type that shows how many compliance requirements have mapped controls, and the effectiveness of those controls:

```
NIST SP 800-53 Coverage — April 2026

Control Family     | Requirements | Mapped Controls | Avg Effectiveness | Coverage %
─────────────────────────────────────────────────────────────────────────────────────
AC - Access Control |     25      |       18        |       78%         |    72%
AU - Audit         |     16      |       12        |       91%         |    75%
CA - Assessment    |     9       |       6         |       65%         |    67%
...
```

This report is generated via a join between:
- `compliance_requirements` (from the Compliance module)
- `record_relations` (requirement → control mappings)
- `records.computed_values` (control effectiveness score)

---

## 9. GraphQL API

```graphql
type Query {
  dashboards(orgId: UUID!): [Dashboard!]!
  dashboard(id: UUID!): Dashboard
  myDefaultDashboard: Dashboard

  reportDefinitions(orgId: UUID!, appId: UUID): [ReportDefinition!]!
  reportDefinition(id: UUID!): ReportDefinition

  runReport(reportId: UUID!, params: JSON, page: PageInput): ReportResult!
  runDashboardWidget(widgetConfig: JSON!): WidgetResult!
}

type Mutation {
  saveDashboard(input: SaveDashboardInput!): Dashboard!
  deleteDashboard(id: UUID!): Boolean!

  saveReportDefinition(input: SaveReportInput!): ReportDefinition!
  deleteReportDefinition(id: UUID!): Boolean!

  scheduleReport(input: ScheduleReportInput!): ScheduledReport!
  exportReport(reportId: UUID!, format: ExportFormat!, params: JSON): ExportJob!
}

type ReportResult {
  columns:    [ReportColumn!]!
  rows:       [JSON!]!
  totalCount: Int!
  params:     JSON
  generatedAt: DateTime!
}

type ExportJob {
  jobId:      UUID!
  status:     String!
  downloadUrl: String
}

enum ExportFormat { PDF EXCEL CSV }
```

---

## 10. Pre-built System Reports

The platform ships with a library of pre-built reports that cover common GRC reporting needs:

| Report Name | Type | Module |
|------------|------|--------|
| Open Risks by Rating and Owner | list | Risk |
| Risk Trend (12 months) | trend | Risk |
| Risk Heat Map | heatmap | Risk |
| Control Effectiveness Scorecard | kpi | Control |
| Controls Due for Testing | list | Control |
| Policy Review Schedule | list | Policy |
| Overdue Policy Reviews | list | Policy |
| Open Issues by Age | list | Issues |
| NIST SP 800-53 Coverage | compliance | Compliance |
| ISO 27001 Annex A Coverage | compliance | Compliance |
| Vendor Risk Summary | summary | Vendor |
| Workflow SLA Breach Summary | summary | Workflow |
| Pending Approval Tasks by Role | list | Workflow |
| Recent Audit Activity | list | Audit Log |

---

## 11. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should dashboards support drill-down (click a chart bar → filtered record list)? | High |
| 2 | Row-level security on reports: should record-level access rules filter report results? | High |
| 3 | Should reports be embeddable (iFrame) for external portals? | Low |
| 4 | Real-time vs snapshot dashboards? (Real-time requires polling or subscription per widget) | Medium |
| 5 | Should trend reports use `record_versions` snapshots or the audit log for historical data? | Design |

---

*Previous: [11 — Search & Discovery](11-search-discovery.md) | Next: [13 — File & Document Management](13-file-document-management.md)*
