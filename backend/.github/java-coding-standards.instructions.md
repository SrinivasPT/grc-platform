---
applyTo: '**/*.java'
---

# Java Coding Standards — GRC Platform

> Extends `backend/.github/copilot-instructions.md`. All parent rules apply without exception.

## Philosophy: Signal Over Noise

Every line is a maintenance burden. Less code = less to read, test, and break.
The goal is **code that reads like a specification**, not a transcript of its own execution.

- Bias toward immutability and expression-oriented code
- Fail loudly; handle centrally
- Validate at system entry points only — trust inputs inside service internals
- Log once, at the right level, at the right boundary

---

## 1. Expression-Oriented — Declarations Over Imperative Steps

### switch expressions over if-else chains

```java
// ✅
return switch (fieldType) {
    case "TEXT"      -> input.textValue();
    case "NUMBER"    -> input.numberValue();
    case "DATE"      -> input.dateValue();
    case "REFERENCE" -> input.referenceId();
    default          -> null;
};

// ❌
if (fieldType.equals("TEXT")) {
    return input.textValue();
} else if (fieldType.equals("NUMBER")) {
    return input.numberValue();
}
```

### Streams over for-loops (for transformations)

```java
// ✅
var errors = rules.stream()
        .map(rd -> (ValidateResult) validateEvaluator.evaluate(ruleDslParser.parse(rd.getRuleDsl()), input))
        .filter(vr -> !vr.valid())
        .map(vr -> new ValidationError(vr.fieldKey(), vr.message()))
        .toList();
if (!errors.isEmpty()) throw new ValidationException(errors);

// ❌
List<ValidationError> errors = new ArrayList<>();
for (RuleDefinition rd : rules) {
    EvaluationResult result = validateEvaluator.evaluate(...);
    if (result instanceof ValidateResult vr && !vr.valid()) {
        errors.add(new ValidationError(vr.fieldKey(), vr.message()));
    }
}
if (!errors.isEmpty()) { throw new ValidationException(errors); }
```

### allMatch / anyMatch instead of boolean for-loops

```java
// ✅
return operands.stream().allMatch(op -> evaluateBoolean(op, input));
return operands.stream().anyMatch(op -> evaluateBoolean(op, input));

// ❌
for (RuleNode op : operands) {
    if (!evaluateBoolean(op, input)) return false;
}
return true;
```

### Pattern matching in switch — no instanceof casts

```java
// ✅
return switch (node) {
    case RuleNode.AndNode and -> evaluateAnd(and, input);
    case RuleNode.OrNode  or  -> evaluateOr(or, input);
    default -> throw new RuleEvaluationException("Cannot evaluate as boolean: " + node.getClass().getSimpleName());
};

// ❌
if (node instanceof RuleNode.AndNode) {
    RuleNode.AndNode and = (RuleNode.AndNode) node;
    return evaluateAnd(and, input);
}
```

### var for locals when type is obvious from the right-hand side

```java
// ✅
var record  = recordRepository.findByIdAndOrgId(id, orgId).orElseThrow(...);
var errors  = rules.stream()...toList();
var input   = EvaluationInput.of(orgId, null, appId, fieldValues, Map.of());

// ❌ (type repetition adds noise, not information)
GrcRecord record = recordRepository.findByIdAndOrgId(id, orgId).orElseThrow(...);
List<ValidationError> errors = rules.stream()...toList();
```

### Ternary / method-reference over trivial if-return

```java
// ✅
return valid ? ValidateResult.pass() : ValidateResult.fail(null, "Validation rule failed");

// ❌
if (valid) {
    return ValidateResult.pass();
}
return ValidateResult.fail(null, "Validation rule failed");
```

---

## 2. Fail-Fast + Centralized Error Handling

### Services throw domain exceptions immediately. No defensive wrapping.

```java
// ✅ — throw and done; the global handler maps it to HTTP/GraphQL
var record = recordRepository.findByIdAndOrgId(id, orgId)
        .orElseThrow(() -> new RecordNotFoundException("GrcRecord", id));

// ❌ — silent failure propagates null to callers
if (record == null) {
    log.warn("Record {} not found", id);
    return null;
}
```

### Global exception handler (platform-api only) maps domain exceptions once

Every new domain exception class needs one matching `@ExceptionHandler` in `GlobalExceptionHandler`.
No mapping logic anywhere else.

```java
@ExceptionHandler(RecordNotFoundException.class)
ProblemDetail handle(RecordNotFoundException ex) {
    log.warn("Not found: {}", ex.getMessage());
    return ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
}
```

### No log-and-throw. Pick one.

```java
// ✅ — throw; the handler logs
throw new RecordNotFoundException("GrcRecord", id);

// ❌ — double-logging pollutes the call stack
log.warn("Record not found: {}", id);
throw new RecordNotFoundException("GrcRecord", id);
```

### IllegalStateException for "can't happen" conditions

```java
// ✅
} catch (NoSuchAlgorithmException e) {
    throw new IllegalStateException("SHA-256 unavailable", e);  // can't happen on any compliant JVM
}

// ❌
} catch (NoSuchAlgorithmException e) {
    log.error("SHA-256 not available", e);
    throw new RuntimeException(e);
}
```

### Catch specific exceptions only — never catch (Exception e)

```java
// ✅
} catch (OptimisticLockException e) { ... }
} catch (JsonProcessingException e) { ... }

// ❌
} catch (Exception e) { ... }
```

---

## 3. Validate at System Boundaries Only

Input validation happens **once**, at the GraphQL resolver or REST controller entry point via `@Valid`.
Services trust their inputs — no redundant null/blank checks inside service internals.

```java
// ✅ — validation at the controller boundary
@MutationMapping
public RecordPayload createRecord(@Argument @Valid CreateRecordInput input, ...) { ... }

// ❌ — redundant check inside service
public RecordDto create(CreateRecordCommand command) {
    if (command.name() == null || command.name().isBlank())
        throw new ValidationException("name", "must not be blank");
    ...
}
```

**Domain rules** (field validation, business constraints) go through the rule engine — not ad-hoc checks scattered through service methods.

---

## 4. Logging — Once, at the Right Level

| Situation                        | Level   | Where                                  |
| -------------------------------- | ------- | -------------------------------------- |
| Unexpected exception             | `ERROR` | Global exception handler               |
| Domain exception (expected flow) | `WARN`  | Global exception handler               |
| Suppressed/ignored error         | `WARN`  | At the suppression site                |
| Optimistic lock retry            | `WARN`  | At the retry site                      |
| Structured diagnostic            | `DEBUG` | Service (only if operationally useful) |

**Never log in service code just before throwing** — the handler will log.
**Always use SLF4J parameterized syntax** — never string concatenation in log calls.

```java
// ✅
log.warn("Audit lock conflict for org {}, attempt {}/{}", orgId, attempt, MAX_RETRIES);

// ❌
log.warn("Audit lock conflict for org " + orgId);  // string concatenation
log.warn("Record not found: " + id);               // log-before-throw
throw new RecordNotFoundException("GrcRecord", id);
```

---

## 5. Standard Library — Use What Java 21 Provides

### HexFormat instead of manual byte-to-hex loops

```java
// ✅
return HexFormat.of().formatHex(digest.digest(bytes));

// ❌
StringBuilder hex = new StringBuilder(64);
for (byte b : hashBytes) { hex.append(String.format("%02x", b)); }
return hex.toString();
```

### Objects.equals() instead of null-guarded equality

```java
// ✅
return Objects.equals(left, right);

// ❌
if (left == null && right == null) return true;
if (left == null || right == null) return false;
return left.equals(right);
```

### stream().toList() — not Collectors.toList()

```java
return rules.stream().map(this::toSummaryDto).toList();       // ✅
return rules.stream().collect(Collectors.toList());           // ❌
```

### Static TypeReference constants — no anonymous classes inline

```java
// ✅
private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
...
objectMapper.readValue(json, MAP_TYPE);

// ❌
objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
```

### Iterable → Stream via StreamSupport or for-each — no manual iterator loops

```java
// ✅ (Jackson JsonNode implements Iterable)
return StreamSupport.stream(arrayNode.spliterator(), false)
        .map(el -> parseNode(el, depth, ruleCount))
        .toList();

// ❌
List<RuleNode> result = new ArrayList<>();
for (JsonNode element : arrayNode) { result.add(parseNode(element, depth, ruleCount)); }
return result;
```

---

## 6. Keep Methods Small and Named

Cognitive complexity ≤ 10 per method. If a block needs a comment, extract a well-named private method instead.

```java
// ✅ — method name replaces the comment
private boolean isNumericPair(Object left, Object right) {
    return left instanceof Number && right instanceof Number;
}

// ❌ — comment substituting for a name
// Compare as numbers if both sides are numeric
if (left instanceof Number l && right instanceof Number r) { ... }
```

---

## Anti-Pattern Checklist

Before committing, verify none of these exist:

- [ ] `catch (Exception e)` — always catch specific exceptions
- [ ] `log.warn(...); throw ...` — log **or** throw, not both
- [ ] `if (x == null) return null` inside a service — use `orElseThrow()`
- [ ] `new ArrayList<>()` + `for` loop + `.add()` — use streams
- [ ] Manual hex loop (`String.format("%02x", b)`) — use `HexFormat`
- [ ] `left == null && right == null ... left.equals(right)` — use `Objects.equals()`
- [ ] Anonymous `TypeReference<>() {}` inline — make it a static constant
- [ ] `Collectors.toList()` — use `.toList()`
- [ ] Javadoc on private helper methods — let the method name speak
- [ ] `@SuppressWarnings("unchecked")` on a raw cast — use pattern matching or a typed method ref
