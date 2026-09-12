# 00 — Project Charter

## 1. Problem statement

The JVM ecosystem has several mature server-side template engines, but their trade-offs differ. Velocity provides concise syntax and an installed base but is highly dynamic. Thymeleaf favors natural HTML authoring. Qute performs build-time validation and generates optimized resolvers. Compiled engines such as jte/Rocker show how close template rendering can get to handwritten Java.

Viet Template targets teams that like or already own Velocity-style templates but want modern compilation, type checking, lower allocations, safer execution, and first-class current-framework integration.

## 2. Product statement

> Viet Template is a clean-room JVM template engine with a Velocity-compatible language frontend and a compile-first runtime designed to approach handwritten Java rendering performance without giving templates unrestricted access to the JVM object graph.

## 3. Project identity

```text
Name:         Viet Template
Repository:   minh124199/viet-template
Maven group:  io.github.minh124199
Java root:    io.github.minh124199.viettemplate
Artifacts:    viet-template-*
```

The project is intentionally independent of Apache Velocity branding. Velocity is a compatibility target/front-end contract, not the engine's identity.

## 4. Primary users

### Migration users

Applications with existing `.vm` templates that want minimal template rewriting, migration reports, side-by-side output tests, dynamic fallback for hard cases, and progressive conversion to typed templates.

### Performance-sensitive SSR users

Need precompiled templates, direct streaming, low allocation, deterministic startup, stable p99 latency, and profiler-friendly generated code.

### Email/report/code-generation users

Need very high throughput, non-HTTP API, deterministic charset/escaping, and large-output streaming.

### Security-sensitive users

Need strict capability allowlists, no reflection by default, resource-root confinement, bounded recursion/loops/output, and audit/explain facilities.

## 5. Goals

1. **VTL familiarity.** Ordinary Velocity templates should look familiar.
2. **Production compilation.** Parse, validate and compile before traffic when possible.
3. **Fast typed path.** Known property access becomes direct JVM access.
4. **Predictable dynamic path.** Unknown types use cached dynamic dispatch rather than repeated member discovery.
5. **Streaming.** Output incrementally; `String` is convenience only.
6. **Security policy.** Every dangerous operation is capability-controlled.
7. **Framework neutrality.** Core works without Spring/Quarkus/servlet/CDI.
8. **Compiler-grade tooling.** Precise diagnostics and suggestions.
9. **Reproducible compatibility.** Public matrix + automated TCK.
10. **Reproducible performance.** Version-controlled JMH harness and raw results.

## 6. Non-goals

- Not HTML-only.
- Not a browser-side template runtime.
- Not a general-purpose Java scripting language.
- Not perfect legacy compatibility at the expense of architecture/security.
- Not Spring-specific in core.
- Not optimization before correctness.

## 7. Release success criteria

### `0.1` language/interpreter MVP

- lexer/parser with precise spans;
- interpreter;
- `$ref`, `${ref}`, `$!ref`;
- property/index access;
- `#set`, `#if/#elseif/#else`, `#foreach`;
- static `#include` and `#parse`;
- comments;
- strict mode;
- escaping API;
- 1,000+ syntax/semantic tests;
- differential tests for claimed Velocity behavior.

### `0.2` compiler preview

- normalized IR;
- typed model declarations;
- constant chunk merging;
- direct accessors;
- compiled conditionals/loops;
- explain-plan;
- JMH suite.

### `0.3` migration preview

- macros;
- migration scanner;
- dynamic resolver with inline cache;
- Velocity compatibility profile;
- output diff harness.

### `0.4` framework preview

- Spring MVC view/resolver;
- Boot starter/autoconfiguration;
- dev hot reload;
- configuration metadata;
- MVC integration tests.

### `1.0`

- stable API and language profile;
- public TCK;
- reproducible benchmark report;
- security review;
- native-image documented path;
- Maven Central automation;
- migration guide.

## 8. Performance SLOs and Engineering Policy

- **Core Performance Objective**: Viet Template aims to reduce rendering overhead relative to reflection-heavy interpreted template execution while approaching generated or compiled Java performance where its semantics permit. Comparative performance claims against other template engines must be based on reproducible benchmarks using equivalent workloads, configuration, escaping behavior, data models, warmup, and runtime conditions.
- **Baseline Establishment (Milestone M19.1)**: Milestone M19.1 establishes the benchmark methodology and measured baseline across workloads comparing Viet Template against Apache Velocity, Thymeleaf, Quarkus Qute, and handwritten Java before numerical claims are adopted.
- **Balanced Performance Tradeoff**: Evaluate throughput, latency, allocation rate, retained memory, contention, and implementation complexity together. A regression in one dimension may be acceptable when it enables a materially greater improvement in another dimension, provided the tradeoff is measured on representative workloads and documented.
- **Policy vs. Benchmark Evidence**: Architectural policy defines stable principles (measure before optimizing, preserve correctness, security sandboxing, and Velocity semantics, prefer maintainable Java/JDK solutions, use simple arrays and direct indexing, avoid custom sophisticated structures without empirical evidence). Benchmark results are changeable empirical facts recorded in benchmark reports.
- **Implementation Hierarchy**: Follows a four-tier preference hierarchy: (1) Java/JDK standard structures, (2) simple project-owned structures, (3) mature third-party libraries when materially better, and (4) custom specialized structures only with empirical justification.
- Static templates should collapse to a small number of bulk writes.
- Warm monomorphic dynamic access should perform no repeated reflective discovery.
- AOT deployment should require no production parsing unless validation/reload is explicitly enabled.

## 9. Governance

1. ADR required for language-semantic changes.
2. Benchmark evidence required for optimization complexity.
3. Security-default changes require explicit changelog callout.
4. AST/IR stay internal unless deliberately promoted to SPI.
5. Public API must not expose backend implementation classes.
