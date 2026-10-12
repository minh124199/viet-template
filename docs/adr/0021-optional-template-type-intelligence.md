# ADR-0021: Optional Typed Template Parameters and Compile-Time Expression Type Propagation

## Status
**ACCEPTED / QUALIFIED** (Milestone M45A)

## Date
2026-10-12

## Context

Viet Template 1.0 through 1.4 established a high-performance, strictly sandboxed, pure-Java template engine with 100% Apache Velocity (VTL) compatibility. However, untyped template variables present persistent developer-experience challenges:
1. **Lack of Compile-Time Feedback**: Typos in property names (`$user.naem`) or method calls are only caught during test execution or production rendering.
2. **Limited IDE Intelligence**: Language servers cannot reliably provide autocompletion, type hovers, or signature help without knowing root variable shapes.
3. **Frontend Integration Friction**: Server-rendered `$clientData` payloads (established in 1.4) require manually written TypeScript definitions instead of automated end-to-end type safety.

Milestone M45A was chartered to qualify the smallest, safest, backwards-compatible type-intelligence architecture supporting optional template parameter declarations, compile-time expression type propagation, compile-time diagnostics, LSP type intelligence, and future TypeScript projection.

The architecture must strictly satisfy non-negotiable constraints:
- **100% Dynamic VTL Compatibility**: Untyped templates must execute with zero semantic changes or performance regressions.
- **Fail-Closed Security**: Type information must never weaken security sandboxing (`MemberAccessPolicy`) or grant reflection access.
- **Pure Java / Zero Node**: Zero Node/npm runtime dependency.
- **Java 21 Baseline & Native Image**: Strict Java 21 compliance and GraalVM/Mandrel native image compatibility.

## Decision

The project formally adopts the following architecture for Viet Template 1.5:

### 1. Syntax Selection: Candidate A (`#param(Type $var)`)
The engine adopts parenthesized directive syntax with leading type and variable:
```vtl
#param(Employee $employee)
#param(Department $dept)
## Or comma-separated:
#param(Employee $employee, Department $dept)
```
- **Syntax Rationale**: Familiar to Java/Spring Boot and Quarkus/Qute engineers; the leading `$` on the variable name unambiguously delimits the type expression without requiring a colon `:` or secondary keyword; adheres strictly to standard VTL directive conventions.
- **Type Support**: Primitives, class types, generic parameters (`List<String>`), array types (`int[]`), nullable markers (`?`), and default values (`$page = 1`).

### 2. Template Prologue Invariant
`#param` declarations are strictly restricted to the **template prologue** (preceding any template body text, HTML markup, or output directives). Encountering `#param` after body content triggers a fatal parse diagnostic **VTLS:2111 (`PARAM_AFTER_PROLOGUE`)**.

### 3. Four-Tier Type-Source Precedence
Type information is resolved in strict hierarchical order:
1. **Inline `#param(...)` Declaration**: Authoritative template-level contract.
2. **Companion Schema (`*.vt-schema.json`)**: External schema file.
3. **Framework-Inferred Model (`TemplateContract`)**: Build-time or controller model.
4. **Dynamic Fallback (`VType.DynamicType`)**: Undeclared variables fall back to dynamic runtime dispatch without emitting false-positive errors.

### 4. Expression Type Propagation & Flow Refinement
The semantic analyzer (`VtlSemanticAnalyzer`) propagates types across:
- Nested property accesses (`$employee.address.city`)
- Subclass inheritance (`Manager` extending `Employee`)
- Local variable bindings (`#set($addr = $employee.address)`)
- Iterables and map entries in `#foreach` loops
- Nullability flow refinement in `#if($employee.manager != null)` branches
- Method overload scoring (`format(String)` vs `format(int)`)

### 5. Fail-Closed Security & Information Leak Prevention
- Declaring types grants **zero additional authority**.
- High-risk methods (`getClass()`, `wait()`, `notify()`) and classes (`ClassLoader`, `Thread`, `Runtime`) are blocked at compile time with **`VTLSEC:2401` (`SECURITY_DENIED`)**.
- Denied members are strictly excluded from typo suggestions to prevent reconnaissance or information leaks.
- Runtime dynamic linker call sites enforce identical boundaries via `LinkerAccessPolicy.standard()`.

### 6. Advisory Nullable Dereference Warning
Dereferencing a nullable type emits an advisory `WARNING` (`VTLS:2107`), not a fatal `ERROR`. Fatal errors are strictly reserved for definitely invalid operations (`VTLS:2104`, `VTLS:2105`, `VTLSEC:2401`).

### 7. Reconciled Frontend Island Positioning
Svelte 4/5 is the core guaranteed frontend island bridge established in 1.4. React and Vue are categorized as `QUALIFY_FIRST` (demonstrated as examples in M46 without adding core runtime SPIs).

## Consequences

### Positive
- **Instant IDE Feedback**: Developers receive accurate autocompletion, type hovers, and diagnostics in VS Code and IntelliJ.
- **Zero Runtime Regressions**: Untyped templates continue to parse and execute with 100% Velocity compatibility and zero overhead.
- **Automated TypeScript Projection**: Declared template parameters map directly to TypeScript `.d.ts` definitions for frontend components.
- **Fast Compilation**: Micro-benchmarks prove full compile-time overhead is < 2.0 ms for small templates, < 20 ms for medium templates, and < 50 ms for large templates, well within IDE keystroke latency budgets (< 50-100 ms).

### Negative / Trade-offs
- The VTL lexer and parser must support `#param` directive tokenization in the template prologue.
- Tooling schemas must synchronize between inline `#param` declarations and external JSON companion files.

### Neutral
- Untyped templates require no migration or syntax adjustments.
- Public runtime ABI remains unaffected (`STABLE_API +0`, `STABLE_SPI +0`).
