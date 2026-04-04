# Module 05 — Form & Layout Engine

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model), Module 03 (Rule Engine)

---

## 1. Purpose

The Form & Layout Engine is the **rendering layer** for all GRC record UI. It translates server-provided JSON layout configurations into React component trees. All layout decisions — tabs, panels, field order, column widths, conditional visibility, inline sub-forms — are driven by server-side config, not hard-coded in the frontend.

This engine means zero frontend code changes are required when adding fields, reorganizing tabs, or changing visibility rules for any application.

---

## 2. Design Principles

- **Server-driven layout.** The React client is a renderer; it does not decide what to show.
- **Config, not code.** Layouts are JSON stored in `layout_definitions`. No JSX per record type.
- **Rule-driven behavior.** Field visibility, conditional labels, and computed values come from the Rule Engine.
- **Composable.** Layouts are composed from a small set of primitive components (tabs, panels, fields).
- **Accessible by default.** All rendered fields meet WCAG 2.1 AA requirements.
- **Performance.** Tabs are lazy-loaded. Related record lists are paginated and loaded on demand.

---

## 3. Layout Configuration Schema

A `layout_definition.layout_config` JSON object fully specifies a form or view:

```json
{
  "version": "1",
  "layout_type": "record_form",
  "header": {
    "title_field":    "title",
    "subtitle_field": "risk_id",
    "status_field":   "status",
    "badge_fields":   ["risk_rating", "workflow_state"]
  },
  "tabs": [
    {
      "key":         "details",
      "label":       "Details",
      "icon":        "info-circle",
      "visible_roles": ["risk_manager", "risk_viewer", "admin"],
      "visible_if":  null,
      "panels": [
        {
          "key":     "basic_info",
          "label":   "Basic Information",
          "columns": 2,
          "fields":  [
            { "field_key": "title",       "col_span": 2 },
            { "field_key": "category" },
            { "field_key": "owner" },
            { "field_key": "description", "col_span": 2, "rows": 4 }
          ]
        },
        {
          "key":     "scoring",
          "label":   "Risk Scoring",
          "columns": 3,
          "fields":  [
            { "field_key": "likelihood" },
            { "field_key": "impact" },
            {
              "field_key": "risk_score",
              "readonly":  true,
              "highlight": true
            }
          ]
        }
      ]
    },
    {
      "key":   "treatment",
      "label": "Treatment",
      "panels": [
        {
          "key":    "treatment_plan",
          "label":  "Treatment Plan",
          "fields": [
            { "field_key": "risk_treatment",   "col_span": 2 },
            { "field_key": "residual_risk_note",
              "visibility_rule_key": "show_residual_risk_note" }
          ]
        }
      ]
    },
    {
      "key":         "controls",
      "label":       "Linked Controls",
      "lazy":        true,
      "visible_roles": ["risk_manager", "control_manager", "admin"],
      "visible_if":  null,
      "content":  {
        "type":           "related_records",
        "relation_type":  "risk_controls",
        "direction":      "children",
        "app_key":        "control",
        "columns":        ["title","status","effectiveness_score","owner"],
        "allow_link":     true,
        "allow_unlink":   true,
        "allow_create":   false
      }
    },
    {
      "key":      "documents",
      "label":    "Documents",
      "lazy":     true,
      "content":  {
        "type":    "attachments"
      }
    },
    {
      "key":      "history",
      "label":    "History",
      "lazy":     true,
      "content":  {
        "type":    "audit_log"
      }
    }
  ],
  "actions": [
    { "key": "save",           "label": "Save",    "type": "submit",   "primary": true },
    { "key": "submit_review",  "label": "Submit for Review",
      "type": "workflow_transition", "transition_key": "submit_for_review",
      "confirm": "Are you sure you want to submit this risk for review?" },
    { "key": "delete",         "label": "Delete",  "type": "delete",   "variant": "danger",
      "permission": "record:delete" }
  ]
}
```

---

## 4. Field Rendering Spec

Each field type maps to a React component. The mapping is fixed and registered at component library initialization.

### 4.1 Field Type → Component Mapping

| Field Type | React Component | Notes |
|-----------|----------------|-------|
| `text_short` | `<TextInput />` | Max length from field config |
| `text_long` | `<RichTextEditor />` | Quill-based, HTML output sanitized |
| `integer`, `decimal` | `<NumberInput />` | Locale-aware formatting |
| `currency` | `<CurrencyInput />` | Currency symbol from org settings |
| `percentage` | `<PercentageInput />` | 0-100 range, `%` suffix |
| `date` | `<DatePicker />` | ISO 8601, locale-aware |
| `datetime` | `<DateTimePicker />` | With timezone display |
| `boolean` | `<Toggle />` | |
| `single_select` | `<Select />` | Loads from value list |
| `multi_select` | `<MultiSelect />` | Loads from value list, chips display |
| `user_reference` | `<UserPicker />` | Searchable by name/email |
| `user_multi` | `<UserMultiPicker />` | |
| `record_reference` | `<RecordPicker />` | Searchable, shows display_name |
| `record_multi` | `<RecordMultiPicker />` | |
| `org_unit` | `<OrgUnitTreePicker />` | Hierarchical tree selector |
| `attachment` | `<FileUpload />` | Drag-drop, size/type limits |
| `calculated` | `<ComputedField />` | Read-only; shows value + explain button |
| `matrix` | `<RiskMatrix />` | 2D grid (e.g., 5×5 likelihood/impact) |

### 4.2 Field Config Schema (per field type)

Each field definition stores a `config` JSON object with field-type-specific options:

```json
// single_select / multi_select
{
  "value_list_key": "risk_categories",
  "display_style": "dropdown",          // "dropdown" | "radio" | "chips"
  "allow_other":   false
}

// record_reference / record_multi
{
  "target_app_key":     "control",
  "display_field_keys": ["title", "status"],
  "filter": {
    "fieldKey":  "status",
    "operator":  "EQ",
    "value":     "active"
  }
}

// text_long
{
  "max_length":    50000,
  "enable_formatting": true
}

// number / decimal
{
  "min":        0,
  "max":        100,
  "step":       0.5,
  "format":     "0.0"
}

// matrix
{
  "x_axis_list_key": "likelihood_levels",
  "y_axis_list_key": "impact_levels",
  "result_map": {
    "1x1": "Low", "1x2": "Low", "2x2": "Medium",
    "3x3": "High", "5x5": "Critical"
  }
}
```

---

## 6. Role-Based Tab and Panel Visibility

Each tab and panel in the layout config supports two visibility controls:

| Property | Type | Description |
|----------|------|-------------|
| `visible_roles` | `string[]` | If set, tab/panel is only shown to users with at least one of these roles. Omit to show to all roles. |
| `visible_if` | rule DSL expression | If set, tab/panel is shown only when this rule expression evaluates to `true` (same DSL as Module 03 visibility rules). |

Both conditions are checked on the **server** when generating the layout response for a user. The filtered layout JSON is sent to the client — the client never receives tabs/panels the user is not allowed to see. This prevents UI-only security bypass.

```java
// Server-side layout filtering before returning to client
List<TabConfig> visibleTabs = layoutConfig.tabs().stream()
    .filter(tab -> hasRequiredRole(currentUser, tab.visibleRoles()))
    .filter(tab -> tab.visibleIf() == null || ruleEngine.evaluate(tab.visibleIf(), ctx).asBoolean())
    .toList();
```

---

## 5. React Component Architecture

### 5.1 Component Tree

```
<RecordPage>
  ├── <RecordHeader>           (title, status badge, workflow state, action buttons)
  ├── <RecordTabs>
  │   ├── <RecordTab key="details">
  │   │   ├── <RecordPanel key="basic_info">
  │   │   │   ├── <FieldRenderer fieldKey="title" />
  │   │   │   ├── <FieldRenderer fieldKey="category" />
  │   │   │   └── <FieldRenderer fieldKey="description" />
  │   │   └── <RecordPanel key="scoring">
  │   │       ├── <FieldRenderer fieldKey="likelihood" />
  │   │       ├── <FieldRenderer fieldKey="impact" />
  │   │       └── <ComputedField fieldKey="risk_score" />
  │   ├── <RecordTab key="controls" lazy>
  │   │   └── <RelatedRecordList relationType="risk_controls" />
  │   └── <RecordTab key="history" lazy>
  │       └── <AuditLogPanel />
  └── <RecordActions>          (save, workflow transitions, delete)
```

### 5.2 FieldRenderer Component

The central routing component — given a `field_key`, retrieves its definition, renders the appropriate component, and connects it to form state:

```tsx
// Simplified pseudocode
function FieldRenderer({ fieldKey }: { fieldKey: string }) {
  const fieldDef = useFieldDefinition(fieldKey);
  const visibility = useVisibilityRule(fieldKey);      // from Rule Engine
  const { value, onChange, error } = useFieldState(fieldKey);

  if (!visibility.isVisible) return null;

  const Component = FIELD_COMPONENT_MAP[fieldDef.fieldType];

  return (
    <FormField
      label={fieldDef.name}
      required={fieldDef.isRequired}
      error={error}
      hint={fieldDef.config?.hint}
    >
      <Component
        value={value}
        onChange={onChange}
        config={fieldDef.config}
        readonly={fieldDef.readonly || !hasPermission('field:write', fieldKey)}
      />
    </FormField>
  );
}
```

### 5.3 Form State Management

Form state is managed with a flat map `{ [fieldKey]: value }` in a Zustand store. This avoids deeply nested state and makes rule evaluation straightforward (the Rule Engine gets the flat map as its context).

On every field change:
1. Update the field value in the store
2. Run client-side Rule Engine (calculations + visibility) synchronously
3. Update derived computed values and visibility flags in the same store update
4. UI re-renders only changed field components (React's reconciler handles this)

### 5.4 Unsaved Changes Detection

The form tracks a `isDirty` flag per-field. On navigation away with unsaved changes, a browser confirmation dialog appears. On save, all dirty fields are included in the `UpdateRecordInput` mutation.

---

## 6. Conditional Visibility

Visibility rules (type `visibility` in Module 03) are evaluated in real time as field values change.

```
State: { risk_treatment: "accept" }

Rule: { "eq": [ { "field": "risk_treatment" }, { "literal": "accept" } ] }
Result: SHOW residual_risk_note

State: { risk_treatment: "transfer" }
Result: HIDE residual_risk_note
```

The client Rule Engine evaluates all `visibility` type rules on every field change. Hidden fields:
- Are not rendered in the DOM
- Are not validated
- Are not included in the save payload

---

## 7. Layout Editor (Admin)

An admin-accessible Layout Editor provides a **form-based interface** for configuring layouts for MVP. Drag-and-drop enhancement is deferred post-MVP.

**MVP Layout Editor capabilities:**
- Select which fields to include (choose from defined field list)
- Set display order and column spans via number inputs
- Assign fields to tabs and panels via dropdowns
- Set `visible_roles` per tab/panel via a multi-select role picker
- Set `visible_if` per tab/panel by selecting a rule from the rule picker

It:
- Reads the current `layout_config` JSON
- Renders a structured form editor (no JSON editing required by admins)
- Saves changes to `layout_definitions` as a new version
- Change is immediately live for all users (config cache invalidated)

The Layout Editor is itself a React component built on the same primitive components as record forms — it bootstraps itself.

---

## 8. List View Layout

In addition to `record_form`, the engine supports `list_view`:

```json
{
  "version": "1",
  "layout_type": "list_view",
  "columns": [
    { "field_key": "record_number", "label": "#",       "width": 80,  "sortable": true },
    { "field_key": "title",         "label": "Title",   "width": 300, "sortable": true, "link": true },
    { "field_key": "risk_rating",   "label": "Rating",  "width": 100, "render": "badge" },
    { "field_key": "owner",         "label": "Owner",   "width": 150, "render": "user_avatar" },
    { "field_key": "workflow_state","label": "Status",  "width": 120, "render": "workflow_badge" },
    { "field_key": "due_date",      "label": "Due",     "width": 120, "sortable": true, "render": "relative_date" }
  ],
  "default_sort":   { "field_key": "updated_at", "direction": "desc" },
  "filters":        ["risk_rating", "owner", "workflow_state", "category"],
  "row_actions":    ["view", "edit", "delete"],
  "bulk_actions":   ["export", "assign_owner", "archive"],
  "empty_state":    "No risks found matching your filters."
}
```

---

## 9. Performance Strategy

| Concern | Strategy |
|---------|---------|
| Large forms with many fields | Panels within tabs are lazy-loaded on tab activation |
| Related record lists | Loaded separately with independent pagination |
| Attachment lists | Loaded separately; thumbnails lazy |
| Audit history | Infinite scroll / paginated |
| Rule evaluation on field change | Client-side, synchronous, < 5ms target |
| Initial form load | Config loaded once and cached; record data in single GraphQL query |
| Layout config versioning | Cached in Apollo Client by (appId + configVersion) |

---

## 10. Accessibility Requirements

- All form fields have associated `<label>` elements
- Required fields marked with ARIA `aria-required`
- Error messages linked via `aria-describedby`
- Keyboard navigation through tabs and fields
- Screen reader announcements for field changes and validation errors
- Minimum contrast ratio: 4.5:1 for text (WCAG AA)
- No time-limited interactions without user acknowledgment

---

## 11. Open Questions

| # | Question | Priority | Resolution |
|---|----------|----------|-----------|
| 1 | ~~Should the Layout Editor be built in MVP or deferred?~~ | High | **Resolved:** A form-based Layout Editor is included in MVP (no JSON editing). Drag-and-drop is deferred post-MVP. See Section 7. |
| 2 | How to handle layout differences between `create` and `edit` modes for same application? | Medium | |
| 3 | Print/PDF layout — separate layout config or CSS print stylesheet? | Low | |
| 4 | Mobile/responsive layout — should panels stack vertically on small screens automatically? | Medium | |
| 5 | How to handle inline record creation (create a Control while editing a Risk)? | Medium | |

---

*Previous: [04 — API Layer](04-api-layer.md) | Next: [06 — Graph Projection](06-graph-projection.md)*
