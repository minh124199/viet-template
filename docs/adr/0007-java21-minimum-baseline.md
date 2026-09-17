# ADR-0007 — Java 21 Minimum Baseline

- Status: Accepted
- Date: 2026-09-17
- Supersedes: [ADR-0003](0003-java-baseline.md)

## Context

Viet Template is designed as a next-generation high-throughput template engine. The original Java 17 baseline constrained the project to legacy classfile version 61 and prevented full utilization of modern JVM primitives such as Virtual Threads (JEP 444), pattern matching with record patterns (JEP 440/441), and modernized collections. Furthermore, enterprise ecosystems including Spring Boot 4 and Jakarta EE 11 increasingly require Java 17+ or Java 21+, with modern deployments targeting Java 21 LTS as the baseline standard.

Retaining Java 17 runtime compatibility required maintaining split code paths, avoiding modern language features in core AST and IR abstractions, and running bytecode generation with legacy JVM version targets.

## Decision

Adopt **Java 21** as the strict minimum runtime and language baseline across all Viet Template modules:

1. **Compilation Baseline**: All production modules compile with `-release 21` (JVM classfile major version 65, minor version 0).
2. **First-Class Virtual Thread Integration**: The engine, runtime context, and web integration are designed and validated to run seamlessly on Virtual Threads without thread-pinning or thread-affinity hazards.
3. **Modern Language Features in Core**: Sealed interfaces and records are utilized throughout the AST, Template IR, and execution plan models, guaranteeing exhaustive pattern matching and type-safe optimization passes.
4. **Single Bytecode Target**: All released JARs target Java 21 directly; no backward compilation to Java 17 is supported.
5. **Enforcement**: Builds fail via Maven Enforcer and Gradle toolchain enforcement if compiled or executed on any runtime earlier than Java 21.

## Consequences

### Positive

- Enables exhaustive pattern matching across sealed Template IR statement and expression hierarchies.
- Native support for Virtual Threads across core rendering, cache eviction, and Spring MVC streaming pipelines.
- Standardizes on JVM bytecode version 65 across all runtime compilers (`ClassFileWriter`) and static AOT generators.
- Simplifies dependency management and eliminates legacy compatibility shims.

### Negative

- Runtimes running Java 17 or earlier cannot load Viet Template artifacts (version 0.2.x+).
- Requires consumers still on Java 17 to upgrade their JVM runtime or remain on the Viet Template 0.1.x maintenance branch.

## Rejected Alternatives

1. **Retain Java 17 Baseline with Optional Java 21 Extensions**: Rejected because it fractured the core IR abstractions, prevented sealed record patterns in core visitor passes, and doubled the testing and maintenance matrix.
2. **Multi-Release JARs (MRJARs)**: Rejected per [ADR-0011](0011-no-multi-release-jars.md); MRJARs introduce packaging complexity and unpredictable classloader behaviors without providing tangible architectural benefits for a clean-slate engine.
