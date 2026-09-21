# Viet Template Language (VTL) Undefined and Null Semantics

> **Document Version:** 1.0.0  
> **Target Specification:** Apache Velocity 2.4.x Compatible  
> **Compiler & Runtime Baseline:** Java 21 (`--release 21`)  
> **Diagnostic Code:** `INTERPRETER:VARIABLE_UNDEFINED`  
> **Companion Guides:**
> - [Syntax Reference](syntax-reference.md)
> - [Foreach Loops and Scopes](foreach-and-scopes.md)
> - [Macros and Layouts](macros-and-layouts.md)
> - [TCK Compatibility Matrix](../migration/compatibility-matrix.md)

---

## 1. Executive Summary & Conceptual Model

One of the most frequent sources of ambiguity and subtle defects in template engines is the treatment of missing data versus explicitly null values. Apache Velocity historically conflated uninitialized references with null attributes, leading to complex rendering heuristics and inconsistent fallback behaviors.

Viet Template resolves this architectural challenge through an authoritative **Three-State Evaluation Model** (`EvaluationValue`):
1. **`UNDEFINED`**: The reference identifier was never registered in the `RenderContext` or any local execution scope.
2. **`DEFINED_NULL`**: The key exists in the context, but its associated value is explicitly `null`.
3. **`DEFINED_VALUE`**: The reference resolves to a concrete, non-null Java object.

This clear tripartite distinction enables strict compile-time checking, safe property navigation, and deterministic migration behavior from legacy Velocity codebases.

---

## 2. The Three-State Evaluation Model

```text
                             ┌───────────────────────────────┐
                             │        Reference Lookup       │
                             └───────────────┬───────────────┘
                                             │
                      ┌──────────────────────┼──────────────────────┐
                      ▼                      ▼                      ▼
               [ Key Not Found ]      [ Key Exists == null ] [ Key Exists != null ]
                      │                      │                      │
                      ▼                      ▼                      ▼
               ┌─────────────┐        ┌──────────────┐       ┌──────────────┐
               │  UNDEFINED  │        │ DEFINED_NULL │       │DEFINED_VALUE │
               └─────────────┘        └──────────────┘       └──────────────┘
```

### 2.1 State: `UNDEFINED`
A reference is `UNDEFINED` when neither the root `RenderContext`, any contributor context, nor any active `#set` / `#foreach` / `#macro` scope contains the referenced variable name.

- **Standard Rendering Mode** (`runtime.strict_mode=false`): Renders the raw identifier verbatim (e.g. `$user` -> `"$user"`).
- **Strict Rendering Mode** (`runtime.strict_mode=true`): Halts evaluation immediately by throwing `TemplateRenderException` with diagnostic code `INTERPRETER:VARIABLE_UNDEFINED`.
- **Quiet Reference (`$!user`)**: Suppresses output and renders an empty string (`""`).
- **Fallback Expression (`$user|'Guest'`)**: Evaluates the fallback expression and renders `'Guest'`.
- **Truthiness in `#if($user)`**: Evaluates to `false`.

### 2.2 State: `DEFINED_NULL`
A reference is `DEFINED_NULL` when a key exists in the context with an explicit `null` value (e.g. `context.put("middleName", null)` or `#set($var = null)`).

- **Standard Rendering Mode**: Conforms to Apache Velocity 2.4.x by rendering the literal identifier string (e.g. `$middleName` -> `"$middleName"`).
- **Strict Rendering Mode**: In strict mode, resolving a `DEFINED_NULL` reference for rendering also triggers `TemplateRenderException` with `INTERPRETER:VARIABLE_UNDEFINED`, ensuring templates do not inadvertently expose null values to end users.
- **Quiet Reference (`$!middleName`)**: Suppresses output and renders an empty string (`""`).
- **Fallback Expression (`$middleName|'N/A'`)**: Evaluates the fallback expression and renders `'N/A'`.
- **Truthiness in `#if($middleName)`**: Evaluates to `false`.

### 2.3 State: `DEFINED_VALUE`
A reference is `DEFINED_VALUE` when it binds to an active, non-null object instance.

- **Standard / Strict Rendering**: Formats and streams the string representation of the object into `TemplateOutput`.
- **Quiet Reference (`$!user`)**: Renders the object value identically to non-quiet notation.
- **Fallback Expression (`$user|'Guest'`)**: Evaluates to the object value; the fallback expression is never evaluated.
- **Truthiness**: Evaluated according to the comprehensive truthiness rules outlined in [Centralized Truthiness Rules](#5-centralized-truthiness-rules-vtltruthiness).

---

## 3. Evaluation Matrix

The following matrix contrasts observable behavior across reference notations and execution modes:

| Scenario / Expression | Evaluation State | Standard Non-Strict Output | Strict Mode Outcome | Quiet Mode (`$!`) Output | Fallback (`\|'Default'`) Output |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `$missing` | `UNDEFINED` | `"$missing"` | Throws `INTERPRETER:VARIABLE_UNDEFINED` | `""` | `"Default"` |
| `${missing}` | `UNDEFINED` | `"${missing}"` | Throws `INTERPRETER:VARIABLE_UNDEFINED` | `""` | `"Default"` |
| `$nullVar` | `DEFINED_NULL` | `"$nullVar"` | Throws `INTERPRETER:VARIABLE_UNDEFINED` | `""` | `"Default"` |
| `$user.name` (when `$user` is null) | `UNDEFINED` | `"$user.name"` | Throws `INTERPRETER:VARIABLE_UNDEFINED` | `""` | `"Default"` |
| `$user.name` (`name` is null) | `DEFINED_NULL` | `"$user.name"` | Throws `INTERPRETER:VARIABLE_UNDEFINED` | `""` | `"Default"` |
| `$user.name` (`name="Alice"`) | `DEFINED_VALUE` | `"Alice"` | `"Alice"` | `"Alice"` | `"Alice"` |

---

## 4. Variable Assignment Semantics (`#set`)

The behavior of the `#set` directive when evaluating null or undefined right-hand side (RHS) expressions depends on the engine's configuration policy:

### 4.1 Modern Standard Semantics (Default)
In standard execution, assigning a null RHS expression sets the variable to `DEFINED_NULL`:

```velocity
#set($var = "initial")
#set($var = $nullValue)
## $var is now DEFINED_NULL.
#if($var)
  This will not print.
#end
```

### 4.2 Legacy Null RHS Preservation (`DIFF-003`)
In Apache Velocity 1.x, assigning a null or undefined RHS to an existing variable silently left the variable's previous value intact. Velocity 2.0 altered this behavior, which broke thousands of enterprise templates relying on cascading default assignments.

Viet Template provides configurable support for legacy preservation (`DIFF-003`, `options.setNullAllowed(false)`):

```velocity
#set($title = "Default Title")
#set($title = $article.optionalTitle) ## If optionalTitle is null:
## Under DIFF-003 preservation: $title remains "Default Title".
## Under standard 2.x mode:      $title becomes DEFINED_NULL.
```

---

## 5. Centralized Truthiness Rules (`VtlTruthiness`)

Truthiness evaluation in `#if`, `#elseif`, and logical expressions (`&&`, `||`, `!`) is governed by `VtlTruthiness.java`, matching Apache Velocity 2.4.x truthiness specification:

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                           VtlTruthiness Priority Flow                         │
│                                                                               │
│  1. Null / UNDEFINED                   ───► FALSE                             │
│  2. java.lang.Boolean                  ───► boolean value                     │
│  3. Custom getAsBoolean() method       ───► return value (if permitted)       │
│  4. Empty Check (when enabled):                                               │
│     a. Array                           ───► length > 0                        │
│     b. CharSequence (String, etc.)     ───► !cs.isEmpty()                     │
│     c. java.util.Collection            ───► !col.isEmpty()                    │
│     d. java.util.Map                   ───► !map.isEmpty()                    │
│     e. Custom isEmpty()                ───► !isEmpty()                        │
│     f. Custom length() / size()        ───► number > 0                        │
│     g. java.lang.Number                ───► !isZero()                         │
│     h. Custom getAsString()            ───► non-null & non-empty              │
│     i. Custom getAsNumber()            ───► non-null & non-zero               │
│  5. Any other non-null Object          ───► TRUE                              │
└───────────────────────────────────────────────────────────────────────────────┘
```

### 5.1 Detailed Truthiness Criteria

1. **Null & Undefined**: Any expression evaluating to `UNDEFINED` or `DEFINED_NULL` always evaluates to `false`.
2. **Booleans**: Instances of `java.lang.Boolean` evaluate directly to their primitive `boolean` value (`true` or `false`).
3. **`getAsBoolean()` Protocol**: If an object declares a public zero-argument `getAsBoolean()` returning `boolean` or `Boolean` (and access is permitted by `VtlSecurityPolicy`), it is invoked unconditionally before any collection checks.
4. **Strings & CharSequences**: Empty strings (`""`, length 0) evaluate to `false`; strings containing one or more characters evaluate to `true`.
5. **Collections & Maps**: Empty `Collection` or `Map` instances (`isEmpty() == true`) evaluate to `false`.
6. **Arrays**: Arrays with length 0 evaluate to `false`; arrays with length >= 1 evaluate to `true`.
7. **Numbers**:
   - Primitive wrapper numbers (`Integer`, `Long`, `Short`, `Byte`, `Double`, `Float`) and arbitrary-precision math types (`BigInteger`, `BigDecimal`) evaluate to `false` if their value is numerically zero (`0`, `0.0`, `BigDecimal.ZERO`).
   - Non-zero numbers evaluate to `true`.
8. **Introspection Fallbacks**: If enabled by policy, objects exposing zero-argument `isEmpty()`, `size()`, `length()`, `getAsString()`, or `getAsNumber()` are evaluated dynamically.
9. **General Objects**: Any other non-null object reference evaluates to `true`.

---

## 6. Safe Chained Navigation

Navigating nested object hierarchies (e.g. `$order.customer.address.zipCode`) can easily result in `NullPointerException` failures in unmanaged runtimes.

In Viet Template:
- **Short-Circuit Navigation**: When evaluating `$a.b.c`, if `$a` is `UNDEFINED` or `DEFINED_NULL`, traversal halts immediately. Step `.b` and step `.c` are not evaluated.
- **Safe Halting**: The entire compound expression evaluates safely to `UNDEFINED`.
- **Zero Reflection Overhead**: Negative lookups on null references do not trigger reflective introspection on parent types.

```velocity
## Even if $customer has no active subscription ($customer.subscription is null):
#if($customer.subscription.plan.isActive())
  ## Safely skipped without NullPointerException
#end
```
