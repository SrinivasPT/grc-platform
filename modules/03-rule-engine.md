# Module 03 — Rule Engine

> **Tier:** 1 — Foundation
> **Status:** In Design
> **Dependencies:** Module 02 (Data Model & Schema)

---

## 1. Purpose

The Rule Engine is the **computational brain** of the GRC platform. It evaluates declarative, JSON-defined rules against record data to produce: calculated field values, validation results, visibility decisions, aggregated scores, workflow conditions, and notification triggers.

The single most important property of this engine: **determinism**. Given the same inputs and rule DSL, it always produces the same output. No side effects. No random behavior. No hidden state.

---

## 2. Why a Custom DSL (Not a Scripting Engine)

| Scripting Engine (`eval`, Groovy, MVEL) | JSON DSL Rule Engine |
|----------------------------------------|---------------------|
| Arbitrary code execution | Bounded operations only |
| Untestable without runtime | Fully testable: input/output |
| Silent failures possible | Explicit error on invalid rule |
| Security risk (injection) | No injection surface |
| Black box audit | Every operation is traceable |
| Cannot version behavior | Versioned with config_version |
| Requires developer to debug | Business users can inspect rules |

---

## 3. Rule Types

| Type | Trigger | Scope | Example |
|------|---------|-------|---------|
| `calculation` | on_save, on_field_change | Single record | `risk_score = likelihood × impact` |
| `aggregation` | on_child_save, scheduled | Parent record (cross-entity) | `control_effectiveness = avg(linked_tests.score)` |
| `validation` | on_save, on_submit | Single record | `due_date must be > today` |
| `visibility` | on_field_change | Single record, client + server | Show `residual_risk` only if `treatment = 'accept'` |
| `workflow_condition` | workflow transition | Single record | Allow approval only if `risk_score < 15` |
| `notification_trigger` | schedule, on_save, state_change | Record + subscribers | Notify owner when `days_until_due < 7` |
| `data_filter` | on_query | Record set | Auto-filter records by `owner = current_user` |

---

## 4. Rule DSL Specification

### 4.1 Rule Structure

A rule is a JSON object stored in `rule_definitions.rule_dsl`. It has the following top-level structure:

```json
{
  "id": "uuid-of-rule-definition",
  "name": "Risk Score Calculation",
  "type": "calculation",
  "target_field": "risk_score",
  "expression": { }
}
```

### 4.2 Expression Types

All expressions resolve to a typed value. Complex expressions are built by nesting.

#### 4.2.1 Literal

```json
{ "literal": 5 }
{ "literal": "High" }
{ "literal": true }
{ "literal": null }
```

#### 4.2.2 Field Reference

```json
{ "field": "likelihood" }
{ "field": "impact" }
{ "field": "due_date" }
{ "field": "owner.email" }        // traverse reference field
{ "field": "current_user.id" }    // system context fields
{ "field": "record.created_at" }  // record metadata
```

#### 4.2.3 Arithmetic

```json
{ "*": [ { "field": "likelihood" }, { "field": "impact" } ] }
{ "+": [ { "field": "base_score" }, { "literal": 2 } ] }
{ "-": [ { "field": "max_score" }, { "field": "actual_score" } ] }
{ "/": [ { "field": "sum" }, { "literal": 3 } ] }
{ "%": [ { "field": "value" }, { "literal": 100 } ] }
{ "pow": [ { "field": "base" }, { "literal": 2 } ] }
{ "abs": { "field": "variance" } }
{ "round": [ { "field": "score" }, { "literal": 2 } ] }
{ "floor": { "field": "score" } }
{ "ceil":  { "field": "score" } }
```

#### 4.2.4 Comparison

```json
{ "eq":  [ { "field": "status" }, { "literal": "active" } ] }
{ "neq": [ { "field": "status" }, { "literal": "closed" } ] }
{ "gt":  [ { "field": "risk_score" }, { "literal": 15 } ] }
{ "gte": [ { "field": "risk_score" }, { "literal": 15 } ] }
{ "lt":  [ { "field": "risk_score" }, { "literal": 5 } ] }
{ "lte": [ { "field": "risk_score" }, { "literal": 5 } ] }
{ "in":  [ { "field": "category" }, { "literal": ["IT","Security","Legal"] } ] }
{ "not_in": [ { "field": "status" }, { "literal": ["closed","archived"] } ] }
{ "is_null":  { "field": "approved_by" } }
{ "not_null": { "field": "owner" } }
```

#### 4.2.5 Logical

```json
{ "and": [ <expression1>, <expression2>, ... ] }
{ "or":  [ <expression1>, <expression2>, ... ] }
{ "not": <expression> }
```

#### 4.2.6 Conditional

```json
{
  "if": {
    "condition": { "gt": [ { "field": "risk_score" }, { "literal": 20 } ] },
    "then": { "literal": "Critical" },
    "else": {
      "if": {
        "condition": { "gt": [ { "field": "risk_score" }, { "literal": 15 } ] },
        "then": { "literal": "High" },
        "else": { "literal": "Medium" }
      }
    }
  }
}
```

#### 4.2.7 String Operations

```json
{ "concat":  [ { "field": "first_name" }, { "literal": " " }, { "field": "last_name" } ] }
{ "upper":   { "field": "category" } }
{ "lower":   { "field": "email" } }
{ "trim":    { "field": "input" } }
{ "length":  { "field": "description" } }
{ "contains": [ { "field": "tags" }, { "literal": "critical" } ] }
{ "starts_with": [ { "field": "ref_code" }, { "literal": "POL-" } ] }
{ "matches":  [ { "field": "email" }, { "literal": "^[\\w.+-]+@[\\w-]+\\.[\\w.]+$" } ] }
```

#### 4.2.8 Date Operations

```json
{ "date_diff_days":  [ { "field": "due_date" }, { "field": "current_user.now" } ] }
{ "date_add_days":   [ { "field": "start_date" }, { "literal": 30 } ] }
{ "date_add_months": [ { "field": "review_date" }, { "literal": 12 } ] }
{ "year":   { "field": "created_at" } }
{ "month":  { "field": "review_date" } }
{ "day":    { "field": "due_date" } }
```

#### 4.2.9 Aggregation (Server-Side Only)

Aggregations operate over a set of **related records** (from `record_relations`).

```json
{
  "aggregate": {
    "source": "related_records",
    "relation_type": "control_tests",           // from record_relations.relation_type
    "direction": "children",                     // "children" | "parents" | "both"
    "filter": {
      "eq": [ { "field": "status" }, { "literal": "completed" } ]
    },
    "function": "avg",                           // avg | sum | count | min | max | stddev
    "field": "test_score"
  }
}
```

Supported aggregate functions: `sum`, `avg`, `min`, `max`, `count`, `count_distinct`, `stddev`, `median`.

#### 4.2.10 Lookup

Retrieve a value from another record or value list item:

```json
{
  "lookup": {
    "source": "value_list",
    "list_key": "risk_categories",
    "match_field": "value",
    "match_value": { "field": "risk_category" },
    "return_field": "numeric_weight"
  }
}
```

```json
{
  "lookup": {
    "source": "related_record",
    "relation_type": "policy_owner",
    "direction": "parents",
    "field": "owner.email"
  }
}
```

---

## 5. Full Rule Examples

### 5.1 Risk Score (Calculation)

```json
{
  "name": "Risk Score",
  "type": "calculation",
  "target_field": "risk_score",
  "expression": {
    "*": [
      { "field": "likelihood" },
      { "field": "impact" }
    ]
  }
}
```

### 5.2 Risk Rating (Conditional Calculation)

```json
{
  "name": "Risk Rating",
  "type": "calculation",
  "target_field": "risk_rating",
  "expression": {
    "if": {
      "condition": { "gte": [ { "field": "risk_score" }, { "literal": 20 } ] },
      "then": { "literal": "Critical" },
      "else": {
        "if": {
          "condition": { "gte": [ { "field": "risk_score" }, { "literal": 12 } ] },
          "then": { "literal": "High" },
          "else": {
            "if": {
              "condition": { "gte": [ { "field": "risk_score" }, { "literal": 6 } ] },
              "then": { "literal": "Medium" },
              "else": { "literal": "Low" }
            }
          }
        }
      }
    }
  }
}
```

### 5.3 Control Effectiveness (Aggregation)

```json
{
  "name": "Control Effectiveness Score",
  "type": "aggregation",
  "target_field": "effectiveness_score",
  "expression": {
    "round": [
      {
        "aggregate": {
          "source": "related_records",
          "relation_type": "control_tests",
          "direction": "children",
          "filter": {
            "eq": [ { "field": "status" }, { "literal": "completed" } ]
          },
          "function": "avg",
          "field": "test_score"
        }
      },
      { "literal": 1 }
    ]
  }
}
```

### 5.4 Validation Rule

```json
{
  "name": "Due Date Must Be Future",
  "type": "validation",
  "trigger_event": "on_save",
  "expression": {
    "or": [
      { "is_null": { "field": "due_date" } },
      { "gt": [
          { "field": "due_date" },
          { "field": "current_user.today" }
        ]
      }
    ]
  },
  "error_message": "Due date must be in the future",
  "error_field": "due_date"
}
```

### 5.5 Visibility Rule

```json
{
  "name": "Show Residual Risk Only When Risk Treatment Is Accept",
  "type": "visibility",
  "target_field": "residual_risk_note",
  "expression": {
    "eq": [
      { "field": "risk_treatment" },
      { "literal": "accept" }
    ]
  }
}
```

---

## 6. Execution Model

### 6.1 Execution Context

Every rule execution receives an **EvaluationContext** object:

```java
public record EvaluationContext(
    UUID orgId,
    UUID recordId,
    UUID userId,
    Map<String, Object>   fieldValues,        // current field values
    Map<String, Object>   computedValues,     // previously computed calculations
    Map<String, Object>   systemContext,      // now, today, current_user.*
    Map<String, RuleDefinition> ruleIndex,   // all rules for this application
    RelatedRecordLoader   relatedLoader       // lazy-loads aggregation data
) {}
```

### 6.2 Execution Order (DAG)

When a record has multiple `calculation` rules, they may depend on each other (e.g., `risk_score` depends on `likelihood` × `impact`, and `risk_rating` depends on `risk_score`). The engine resolves a **Directed Acyclic Graph (DAG)** from field references to determine evaluation order.

Cycle detection:
- If a circular dependency is detected in the rule DAG at **config load time**, the configuration is rejected with an error. Cycles are never allowed to reach runtime.

```
DAG Example:
  likelihood (input field)
  impact     (input field)
  risk_score = likelihood × impact          ← depends on: likelihood, impact
  risk_rating = if risk_score >= 20 ...     ← depends on: risk_score
  residual_risk = risk_score × (1 - control_coverage)  ← depends on: risk_score, control_coverage
```

### 6.3 Execution Trace

Every execution returns a `RuleTrace` alongside the result:

```json
{
  "rule_id": "uuid",
  "rule_name": "Risk Score",
  "result": 12,
  "steps": [
    { "op": "field", "ref": "likelihood", "resolved": 3 },
    { "op": "field", "ref": "impact", "resolved": 4 },
    { "op": "*", "operands": [3, 4], "result": 12 }
  ],
  "duration_ms": 1
}
```

Traces are:
- Returned in the GraphQL response when `includeTrace: true` is requested
- Stored in the audit log for compliance evidence
- Used by UI "Explain this score" feature

---

## 7. Isomorphic Execution (Client + Server)

The rule engine is designed for **isomorphic execution** — the same rule DSL runs on both client and server.

| Scenario | Execution |
|----------|-----------|
| User changes a field value | Client evaluates calculations + visibility instantly |
| User submits the form | Server re-evaluates all rules (authoritative) |
| Aggregation rules | Server only (requires database access) |
| Notification trigger rules | Server only (requires scheduler context) |

**Frontend Implementation:** A TypeScript rule engine (`rule-engine.ts`) is generated from the same DSL spec. It handles all non-aggregation rule types using the same logical operations. The TypeScript engine is tested against a shared test suite to ensure parity.

**Guarantee:** Even if the client produces a different intermediate result (e.g., due to stale related data), the **server result is always authoritative**. The client engine is an optimization for UX responsiveness only.

---

## 8. Java Implementation Design

### 8.1 Package Structure

```
com.grc.core.rules
├── RuleEngine.java              // Main entry point: evaluate(context, rules) → RuleResult
├── ExpressionEvaluator.java     // Recursive expression tree evaluator
├── RuleDagResolver.java         // DAG ordering for rule execution
├── EvaluationContext.java       // Context record (fields, system vars, related loader)
├── RuleTrace.java               // Trace output
├── RuleResult.java              // Typed result: value + trace
├── dsl/
│   ├── RuleDefinition.java      // Deserialized rule (from JSON)
│   ├── Expression.java          // Base sealed interface
│   ├── LiteralExpr.java
│   ├── FieldRefExpr.java
│   ├── ArithmeticExpr.java
│   ├── ComparisonExpr.java
│   ├── LogicalExpr.java
│   ├── ConditionalExpr.java
│   ├── AggregateExpr.java
│   ├── LookupExpr.java
│   └── StringExpr.java
└── validation/
    ├── RuleDslValidator.java    // Validates DSL at config-save time
    └── CycleDetector.java       // DAG cycle detection
```

### 8.2 Sealed Interface Pattern (Java 21)

```java
// Expression hierarchy using sealed interfaces + records (Java 21)
public sealed interface Expression
    permits LiteralExpr, FieldRefExpr, ArithmeticExpr, ComparisonExpr,
            LogicalExpr, ConditionalExpr, AggregateExpr, LookupExpr, StringExpr {}

public record LiteralExpr(Object value) implements Expression {}
public record FieldRefExpr(String fieldPath) implements Expression {}

public record ArithmeticExpr(
    ArithmeticOp op,
    List<Expression> operands
) implements Expression {}

public enum ArithmeticOp { ADD, SUB, MUL, DIV, MOD, POW, ABS, ROUND, FLOOR, CEIL }
```

### 8.3 Evaluator Core

```java
public final class ExpressionEvaluator {
    public Object evaluate(Expression expr, EvaluationContext ctx) {
        return switch (expr) {
            case LiteralExpr   e -> e.value();
            case FieldRefExpr  e -> resolveField(e.fieldPath(), ctx);
            case ArithmeticExpr e -> evaluateArithmetic(e, ctx);
            case ComparisonExpr e -> evaluateComparison(e, ctx);
            case LogicalExpr    e -> evaluateLogical(e, ctx);
            case ConditionalExpr e -> evaluateConditional(e, ctx);
            case AggregateExpr  e -> evaluateAggregate(e, ctx);   // DB call
            case LookupExpr     e -> evaluateLookup(e, ctx);
            case StringExpr     e -> evaluateString(e, ctx);
        };
    }
}
```

### 8.4 Rule Config Caching

Rule definitions for an application are cached in-memory (Caffeine cache) keyed by `(org_id, application_id, config_version)`. Cache is invalidated when `config_version` changes:

```java
@Cacheable(value = "rule-configs", key = "#orgId + ':' + #appId + ':' + #version")
public List<RuleDefinition> loadRules(UUID orgId, UUID appId, int version) { ... }
```

---

## 9. Rule Engine API

### 9.1 GraphQL (for Admin/Config UI)

```graphql
type Query {
  validateRule(orgId: ID!, ruleInput: RuleDslInput!): RuleValidationResult!
  previewRule(orgId: ID!, recordId: ID!, ruleId: ID!, includeTrace: Boolean): RulePreviewResult!
}

type Mutation {
  saveRuleDefinition(input: SaveRuleInput!): RuleDefinition!
  deleteRuleDefinition(id: ID!): Boolean!
  reorderRules(appId: ID!, orderedIds: [ID!]!): Boolean!
}

type RuleValidationResult {
  isValid: Boolean!
  errors: [RuleValidationError!]!
}

type RulePreviewResult {
  result: JSON
  trace: RuleTrace
}
```

### 9.2 Internal Service API (Java)

```java
public interface RuleEngineService {
    RuleResult evaluateCalculations(UUID orgId, UUID recordId, UUID userId);
    List<ValidationError> evaluateValidations(UUID orgId, UUID recordId, UUID userId);
    Map<String, Boolean> evaluateVisibility(UUID orgId, UUID recordId, UUID userId);
    boolean evaluateWorkflowCondition(UUID orgId, UUID recordId, UUID ruleId, UUID userId);
}
```

---

## 10. Testing Strategy

### 10.1 Unit Tests (Pure Rule Logic)

Every rule operation is unit-tested with a plain Java `EvaluationContext` (no database):

```java
@Test
void riskScore_multipliesLikelihoodByImpact() {
    var expr = ArithmeticExpr.multiply(
        FieldRefExpr.of("likelihood"),
        FieldRefExpr.of("impact")
    );
    var ctx = EvaluationContext.of(Map.of("likelihood", 3, "impact", 4));
    assertThat(evaluator.evaluate(expr, ctx)).isEqualTo(new BigDecimal("12"));
}
```

### 10.2 Shared DSL Test Cases (Cross-Language Parity)

A JSON test case file defines input, rule DSL, and expected output. Both Java tests and TypeScript tests run against the same files to ensure isomorphic parity.

```json
{
  "test": "risk_score_calculation",
  "fields": { "likelihood": 3, "impact": 4 },
  "rule": { "*": [ { "field": "likelihood" }, { "field": "impact" } ] },
  "expected": 12
}
```

### 10.3 Integration Tests

Aggregation rules are integration-tested against a real SQL Server instance (in-memory or containerized for CI) to verify cross-entity calculations.

---

## 11. Open Questions

| # | Question | Priority |
|---|----------|----------|
| 1 | Should there be a `formula_builder` UI for non-technical admin users? | Medium |
| 2 | How to handle rule failures gracefully — fail open or fail closed? | High |
| 3 | Maximum expression depth (to prevent DoS via deeply nested rules)? Default: 20 levels. | Medium |
| 4 | Should `notification_trigger` rules be in this engine or in the Notification module's engine? | Design |
| 5 | Support for running aggregations asynchronously on large datasets? | Future |

---

*Previous: [02 — Data Model & Schema](02-data-model-schema.md) | Next: [04 — API Layer](04-api-layer.md)*
