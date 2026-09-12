# Viet Template — Implementation Documentation Pack

> **Goal:** Build a new, framework-independent JVM template engine with a Velocity-compatible surface language, a modern compiler/runtime architecture, strong security defaults, and first-class Spring Framework 7 / Spring Boot 4 integration.
>
> **Status:** Architecture and implementation specification, revision 0.2, 2026-09-05.


## Project identity

The project identity is fixed for the initial implementation and public repository:

| Item | Value |
|---|---|
| Project / brand | **Viet Template** |
| GitHub repository | `minh124199/viet-template` |
| Maven group | `io.github.minh124199` |
| Java root package | `io.github.minh124199.viettemplate` |
| Artifact prefix | `viet-template-*` |
| Spring Boot property prefix | `viet.template` |
| Metrics prefix | `viet.template` |
| CLI / build-tool prefix | `viet-template` |

The project starts under the maintainer's personal GitHub account. Creating an organization is not a prerequisite. If the repository is transferred to an organization later, existing published Maven coordinates and Java packages should remain stable unless there is a compelling compatibility reason to change them.

## Design thesis

Viet Template is **not** an Apache Velocity fork. It is a clean-room implementation of a new template engine that intentionally supports a large, explicitly versioned subset of Velocity Template Language (VTL) syntax while using a different internal architecture.

```text
Velocity-like source
      │
      ▼
Lexer / Parser
      │
      ▼
Normalized AST
      │
      ▼
Semantic analysis + type information
      │
      ▼
Template IR
      │
      ├──────────────► Interpreter backend          (development / fallback)
      ├──────────────► Dynamic optimized backend   (MethodHandle / inline cache)
      └──────────────► AOT bytecode backend         (production / typed)
                              │
                              ▼
                      streaming output
```

The hot production path should approach handwritten Java rendering: static output chunks plus direct typed property access and branches, with little or no AST traversal, generic resolver dispatch, reflection, or intermediate string construction.

## Core principles

1. **Velocity-compatible syntax is a frontend, not the architecture.**
2. **Compile first.** Parse and validate before production traffic whenever possible.
3. **Typed when possible, dynamic when necessary.**
4. **Zero/low-allocation streaming is a primary objective.**
5. **Secure by default.** Templates must not automatically gain arbitrary Java reflection or application-container access.
6. **Framework independent core.** Spring, Quarkus, Micronaut, servlet, reactive, CLI, and email use cases are adapters.
7. **Compatibility claims are testable.** Every claimed Velocity behavior must have a compatibility test.
8. **Performance claims are reproducible.** JMH, hardware metadata, JVM flags, allocations, and latency distributions are mandatory.
9. **Diagnostics are compiler-grade.** Template errors should include source spans, suggestions, type information, and include/macro stack.
10. **No hidden slow path.** Generated plans expose why an expression is direct, cached-dynamic, reflective, or rejected.

## Documentation map

| Document | Purpose |
|---|---|
| [00-project-charter.md](docs/00-project-charter.md) | Product scope, goals, non-goals, success criteria |
| [01-system-architecture.md](docs/01-system-architecture.md) | Module boundaries and end-to-end architecture |
| [02-vtl-compatibility-spec.md](docs/02-vtl-compatibility-spec.md) | VTL syntax/behavior compatibility contract |
| [03-lexer-parser-ast.md](docs/03-lexer-parser-ast.md) | Lexer, parser, source model, AST |
| [04-semantics-type-system.md](docs/04-semantics-type-system.md) | Binding, typing, truthiness, nullability, diagnostics |
| [05-template-ir.md](docs/05-template-ir.md) | Typed IR and lowering rules |
| [06-velocity-2.4.1-compatibility.md](docs/06-velocity-2.4.1-compatibility.md) | Apache Velocity 2.4.1 differential TCK specification and report |
| [06-optimization-pipeline.md](docs/06-optimization-pipeline.md) | Compiler optimization passes |
| [07-execution-backends.md](docs/07-execution-backends.md) | Interpreter, dynamic, and AOT backends |
| [08-runtime-output.md](docs/08-runtime-output.md) | Rendering API, output pipeline, escaping, allocation policy |
| [09-dynamic-resolution.md](docs/09-dynamic-resolution.md) | MethodHandle/PIC/`invokedynamic` design |
| [10-security-model.md](docs/10-security-model.md) | Threat model, capability policy, sandboxing |
| [11-public-api-spi.md](docs/11-public-api-spi.md) | Stable Java API and extension points |
| [12-spring-integration.md](docs/12-spring-integration.md) | Spring Framework 7 / Boot 4 integration |
| [13-build-aot-native.md](docs/13-build-aot-native.md) | Maven/Gradle, precompile, JPMS, native-image strategy |
| [14-testing-tck.md](docs/14-testing-tck.md) | Unit, differential, compatibility, fuzz and TCK strategy |
| [15-benchmark-plan.md](docs/15-benchmark-plan.md) | JMH benchmarks and performance gates |
| [16-observability-debugging.md](docs/16-observability-debugging.md) | Metrics, tracing, source maps, explain-plan |
| [17-repository-engineering.md](docs/17-repository-engineering.md) | Repository layout, CI, coding rules |
| [17-release-process.md](docs/17-release-process.md) | Canonical release procedure and Central Portal publishing |
| [18-roadmap.md](docs/18-roadmap.md) | Milestones from parser MVP to Spring starter |
| [19-risk-register.md](docs/19-risk-register.md) | Technical/product/legal risks and mitigations |
| [20-implementation-checklist.md](docs/20-implementation-checklist.md) | Detailed implementation task checklist |
| [SOURCES.md](docs/SOURCES.md) | Current upstream references used in this specification |

Architecture decisions are recorded under [`docs/adr/`](docs/adr/).

## Recommended implementation order

```text
M0 repository + test harness
M1 source model + lexer
M2 parser + AST
M3 interpreter for core VTL
M4 compatibility corpus and differential tests against Velocity
M5 semantic/type model
M6 Template IR
M7 typed direct-call compiler
M8 dynamic inline-cache backend
M9 Spring MVC adapter + Boot starter
M10 hardening, native image, optimization
```

The first meaningful end-to-end target is:

```velocity
Hello $user.name
#if($user.admin)
  Admin
#else
  User
#end

#foreach($item in $items)
  $item.name
#end
```

rendered through both the interpreter and compiled backend with byte-for-byte identical output and diagnostics that point to precise template spans.

## Baseline recommendation

### Runtime

- Java 17 minimum for `core`, `parser`, `runtime`, and Spring 7 compatibility.
- Test Java 17, 21, and 25.
- No mandatory dependency on Spring, servlet APIs, or logging implementation in core modules.

### Compiler

- Keep the compiler behind an SPI.
- Support a Java-17-compatible backend strategy.
- Add a JDK 25 compiler module that can use the standard `java.lang.classfile` API.
- Treat exact generated class-file target levels as a tested build feature, not an assumption.

### Spring

- Primary stable target at the time of this document: Spring Framework 7.0.x and Spring Boot 4.1.x.
- Keep Spring 7.1 compatibility in CI as preview until it reaches GA.

## Performance objectives

These are **engineering targets, not public claims**:

- Typed/AOT render throughput: within 85–98% of equivalent handwritten Java rendering for simple templates.
- Allocation: approach output-buffer-only allocation for fully typed templates.
- Velocity: target 1.5–3× render throughput on representative templates.
- Thymeleaf: target 3–7× render throughput on representative templates.
- Qute typed mode: target meaningful improvement, initially 10–50%, while preserving equivalent functionality.
- Track p50/p95/p99, allocation bytes/op, GC, startup, compile time, class size, and generated method size.

## Definition of “Velocity compatible”

Never use a blanket statement such as “100% compatible” when behavioral differences exist.

Viet Template uses an authoritative, side-by-side differential test kit (`viet-template-tck`) that compiles and evaluates templates against official **Apache Velocity Engine 2.4.1** in real time. The generated compatibility report (`build/reports/velocity-compat/velocity-compatibility-report.json`) is the authoritative source of truth, protected by strict invariant checks.

> **Current Compatibility Status**: Apache Velocity 2.4.1 differential compatibility: **295/301 scenarios exact (98.01%)**, **5 documented intentional differences**, **1 Viet Template extension**, **0 unsupported scenarios**, and **0 unclassified regressions** (**100.00% accounted behavior coverage** across **20 functional categories**).

### Differential TCK Scorecard (Milestone M4)

| Metric | Count | Percentage | Description |
| :--- | :--- | :--- | :--- |
| **Total Scenarios Evaluated** | **301** | **100.00%** | Total differential scenarios evaluated |
| **`EXACT_MATCH`** | **295** | **98.01%** | Byte-for-byte output and outcome match |
| **`EXPECTED_DIFFERENCE`** | **5** | **1.66%** | Documented intentional architectural differences |
| **`VIET_EXTENSION`** | **1** | **0.33%** | Intentional Viet Template extensions |
| **`UNSUPPORTED`** | **0** | **0.00%** | Unsupported Apache Velocity features |
| **`BUG`** | **0** | **0.00%** | Unclassified differences or defects |
| **Accounted Behavior Coverage** | **301** | **100.00%** | Scenarios conforming to specification |

See [06-velocity-2.4.1-compatibility.md](docs/06-velocity-2.4.1-compatibility.md) for the detailed specification, ADRs for expected differences, and complete scenario breakdown across all 20 active categories.

Compatibility is versioned as profiles:

- `VTL_CORE`: references, formal/quiet references, `#set`, `#if`, `#foreach`, static `#include`, static `#parse`, comments, core operators.
- `VTL_MIGRATION`: broader Velocity behavior including legacy property resolution, macros, dynamic includes/parses where allowed.
- `VTL_DYNAMIC`: explicit opt-in to runtime-evaluated constructs such as `#evaluate` and arbitrary method invocation.
- `VTL_SAFE`: strict sandbox profile designed for untrusted or externally editable template source when the host follows the documented embedding requirements. It constrains template-visible Java capabilities, model mutation, dynamic evaluation, resource access, output handling, and execution budgets across AST, IR, and AOT execution. Application security still depends on which objects and data the host places in the template context, which trusted capabilities it grants, the configured template repository, and the selected output context.

Feature states:

```text
SUPPORTED_EXACT
SUPPORTED_WITH_DECLARED_DIFFERENCE
SUPPORTED_ONLY_IN_DYNAMIC_MODE
SUPPORTED_ONLY_IN_INTERPRETER
PLANNED
INTENTIONALLY_UNSUPPORTED
```

## Clean-room rule

Use public language documentation and black-box compatibility testing to define behavior. Do not copy Apache Velocity implementation code into Viet Template. If any code is intentionally adapted from an Apache-licensed source, retain required notices and provenance explicitly.

## Installation

Viet Template artifacts are published to Maven Central under group ID `io.github.minh124199`:

### Maven

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-vtl-interpreter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("io.github.minh124199:viet-template-vtl-interpreter:0.1.0")
```

## Building from source

Prerequisites: JDK 17+ (JDK 21 and 25 also supported for build and testing).

Viet Template supports **first-class dual-build parity** under both Gradle Kotlin DSL and Apache Maven:

### Gradle (Reference Build)

```bash
# Build all modules and run all verification checks and tests
./gradlew clean build

# On Windows:
gradlew.bat clean build

# Run formatting checks
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply
```

### Apache Maven

```bash
# Build all modules, package artifacts, and run tests and checks
./mvnw clean verify

# On Windows:
mvnw.cmd clean verify

# Run formatting checks
./mvnw spotless:check

# Apply code formatting
./mvnw spotless:apply
```

### Build Parity Verification

To verify that the Gradle and Maven builds remain in full parity (module definitions, versions, dependencies, Java release target, compiler flags, and compiled JAR contents):

```bash
./scripts/verify-build-parity.sh
```

## Community & Contributing

Contributions are welcome! Please read our contributing guidelines before submitting code:

- [Contributing Guide](CONTRIBUTING.md) — Workflow, build parity requirements, and TCK guidelines.
- [Code of Conduct](CODE_OF_CONDUCT.md) — Contributor Covenant v2.1 standards.
- [Security Policy](SECURITY.md) — Responsible disclosure process and security boundaries.

## License

Viet Template is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

## Notice & Trademark Clarification

Apache Velocity and Apache are trademarks of the Apache Software Foundation. This project is an independent clean-room implementation and is not affiliated with, sponsored by, or endorsed by the Apache Software Foundation.
