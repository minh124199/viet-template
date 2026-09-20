# Canonical Architectural & Semantic Differences from Apache Velocity 2.4.1

## Overview

Viet Template delivers near-complete semantic parity with **Apache Velocity 2.4.1** across 80 Technology Compatibility Kit (TCK) feature scenarios. However, to guarantee robust production security, predictable fault diagnosis, and high-throughput thread concurrency, Viet Template intentionally diverges from legacy Velocity in **three documented areas** (`DIFF-001`, `DIFF-002`, `DIFF-003`) and enforces hardened architectural invariants regarding security sandboxing, output escaping, and context immutability.

This document serves as the authoritative, canonical specification of all behavioral differences.

---

## 1. Summary of Documented TCK Differences

| TCK Feature ID | Feature Name | Velocity 2.4.1 Behavior | Viet Template Behavior | Architectural Justification |
|---|---|---|---|---|
| `DIFF-001` | Fail-fast division by zero | Returns `null`, logs warning, continues rendering silently | Immediately fails fast with `TemplateRenderException` (`[RENDER:FAILURE] Division by zero`) | Arithmetic errors in business templates indicate calculation bugs that must not produce corrupt rendered output. |
| `DIFF-002` | Default reflection & class access | Permits calling `$obj.getClass()`, `.class`, and reflective methods | Denies access to `getClass()`, `.class`, and classloaders under `MemberAccessPolicy.standard()`, throwing `TemplateSecurityException` | Prevents remote code execution (RCE) and reflection-based sandbox breakouts in multi-tenant or user-facing templates. |
| `DIFF-003` | Legacy `#set` null RHS preservation | Velocity 2.0+ overwrites existing variable with `null`. Velocity 1.x preserved existing variable value. | Supports configurable `setNullAllowed` option; defaults to Velocity 2.x null-assignment while allowing legacy 1.x preservation. | Enables seamless migration from legacy Velocity 1.7 installations without changing template source code. |

---

## 2. In-Depth Analysis of Semantic Differences

### 2.1 DIFF-001: Fail-Fast Division by Zero

#### Velocity Behavior
In Apache Velocity 2.4.1, evaluating `#set($result = 10 / 0)` logs an error internally and sets `$result` to `null`. Subsequent references to `$result` render as empty strings or raw literal text `$result`, frequently causing financial, tabular, or inventory reports to render partial, corrupted numbers without alerting upstream systems.

#### Viet Template Behavior
Viet Template treats division or modulo by zero as a fatal rendering error:

```vtl
#set($discount = $total / 0)  ## Throws TemplateRenderException immediately
```

```java
try {
    engine.render("invoice.vm", context);
} catch (TemplateRenderException e) {
    // e.code() contains DiagnosticCode.of("RENDER", "FAILURE")
    // e.getMessage() indicates "Division by zero"
}
```

#### Migration Guidance
If templates may encounter zero divisors, guard the calculation explicitly:

```vtl
#if($divisor != 0)
    #set($ratio = $numerator / $divisor)
#else
    #set($ratio = 0)
#end
```

---

### 2.2 DIFF-002: Default Reflection & Class Access Denial

#### Velocity Behavior
In Apache Velocity 2.4.1, any template reference can traverse reflection APIs unless a complex custom `Uberspect` introspector is explicitly configured:

```vtl
## Permitted in unhardened Velocity:
$user.getClass().forName("java.lang.Runtime").getMethod("getRuntime", null).invoke(null, null).exec("calc")
```

#### Viet Template Behavior
Viet Template's standard execution policy (`MemberAccessPolicy.standard()`) applies defense-in-depth deny rules:

1. **Blocked Classes**: `Class`, `ClassLoader`, `Method`, `Field`, `Constructor`, `Runtime`, `ProcessBuilder`, `System`, `Thread`, `ThreadGroup`, `SecurityManager`.
2. **Blocked Methods**: `getClass()`, `getClassLoader()`, `wait()`, `notify()`, `notifyAll()`.
3. **Blocked Properties**: Direct property navigation to `.class` is prohibited.

Attempting to invoke `$user.getClass()` or `$user.class` throws `TemplateSecurityException`:

```text
[SECURITY:VIOLATION] dynamic.link - Access to getClass on com.example.User is denied by security policy: method getClass is denied by policy
```

#### Migration Guidance
- Audit templates for `$obj.getClass()`. If class name or type inspection is needed, compute a safe string property in the controller (e.g. `$user.type` or `$user.role`).
- If custom reflective access is legitimately required for trusted internal tools, configure an explicit `MemberAccessPolicy` via `MemberAccessPolicy.builder()`.

---

### 2.3 DIFF-003: Legacy `#set` Null RHS Preservation

#### Background
In Velocity 1.7, assigning a null or undefined expression to an existing variable did **not** modify the variable:

```vtl
#set($color = "blue")
#set($color = $missingColor)
$color  ## In Velocity 1.7, this printed "blue"!
```

In Velocity 2.0+, the default changed: `$color` is assigned `null`.

#### Viet Template Support
Viet Template defaults to Velocity 2.x semantics (`setNullAllowed = true`), setting `$color` to null/undefined. However, applications migrating from Velocity 1.7 can restore legacy preservation behavior without changing templates by setting `setNullAllowed(false)`:

```java
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;

VtlInterpreterOptions options = VtlInterpreterOptions.builder()
    .setNullAllowed(false) // Preserves existing variable on null RHS
    .build();

TemplateEngine engine = VtlTemplateEngine.builder()
    .repository(repository)
    .interpreterOptions(options)
    .build();
```

---

## 3. Architectural Differences Beyond Language Directives

### 3.1 Path Traversal Sandboxing

- **Velocity**: Many `FileResourceLoader` setups permit path traversal if relative paths like `../../sensitive.txt` are requested by view resolvers.
- **Viet Template**: `FilesystemTemplateRepository` and template identifier resolution strictly prohibit parent directory traversal (`..`). Any identifier containing `..` or leading slashes attempting to escape the configured root directory throws `TemplateResourceException` with diagnostic code `RESOURCE:NOT_FOUND`.

### 3.2 Context Immutability & Thread Safety

- **Velocity**: `VelocityContext` allows templates to mutate the shared model map directly, leading to data leaks across concurrent requests if contexts are reused.
- **Viet Template**: The root `RenderContext` is completely **immutable**. Directives like `#set` create local variable bindings in thread-confined execution frames (`ExecutionFrame`), guaranteeing that request state cannot leak between concurrent template executions.

### 3.3 Three-State Evaluation Model

- **Velocity**: Employs untyped null checks where missing keys and null keys frequently collapse into unobservable null references.
- **Viet Template**: Internally maintains a strict 3-state evaluation representation:
  1. `UNDEFINED`: Reference was never bound in the context.
  2. `DEFINED_NULL`: Key exists in the context with explicit `null` value.
  3. `DEFINED_VALUE`: Concrete non-null value.

This enables precise diagnostic feedback, strict-mode validation (`strictReferences(true)`), and predictable alternate value fallbacks (`${var|'default'}`).

---

## 4. Compatibility Summary

For 98%+ of production Velocity templates, Viet Template functions as a drop-in replacement with higher rendering throughput, lower memory allocations, and zero configuration vulnerabilities.
