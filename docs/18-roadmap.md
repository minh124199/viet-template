# 18 — Implementation Roadmap

## Overview

Viet Template follows an evidence-driven, benchmark-verified phased release roadmap. Development prioritizes semantic correctness, architectural boundaries, and safety before introducing optimizations. Architectural policy (stable principles: measure before optimizing, preserve correctness, security sandboxing, and Velocity semantics, prefer maintainable Java/JDK solutions, use simple arrays and direct indexing, avoid custom sophisticated structures without empirical evidence) is strictly separated from benchmark results (changeable empirical facts: throughput, allocations, latency profiles, competitor comparisons). Every optimization phase must be validated against the 7-part DSA acceptance rule, the Four-Tier Implementation Preference Hierarchy, the balanced tradeoff evaluation, and proven with empirical JMH benchmarks.

---

## Release Phase 0.1.x — Baseline Stabilization & Benchmark Infrastructure

Release Phase 0.1.x is completed with 0.1.0 published.

This release establishes the authoritative semantic baseline, verified differential compatibility with Apache Velocity 2.4.1, complete compiler pipeline, multi-tier execution backends, and security hardening.

### Completed Milestones

- **Milestone M0 (Repository Bootstrap)**: Dual build configuration (Gradle 9.7.1 + Maven 3.9.9), reproducible builds, ArchUnit boundaries, Spotless formatting, CI matrix across Java 17, 21, and 25.
- **Milestone M1 (Source Model & Diagnostics)**: Immutable `SourceText`, line/column mapping, source spans, diagnostic codes, and source excerpts.
- **Milestone M2 (Lexer & Parser)**: Token slices, Pratt expression parser, recursive-descent statement parser, AST immutability, and syntax recovery.
- **Milestone M2.1 (Build Parity)**: 100% byte-for-byte classfile parity and independent execution under `./gradlew clean build` and `./mvnw clean verify`.
- **Milestone M3 (VTL Reference Interpreter)**: Authoritative execution oracle, 3-state evaluation model (`EvaluationValue`: `UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`), nested lexical scopes (`ExecutionContext`), truthiness semantics (`VtlTruthiness`), space gobbling (`SpaceGobbler`), and execution limits.
- **Milestone M3.1 (Semantic Compatibility Corrections)**: Side-by-side audit of 7 critical semantic areas against Apache Velocity 2.4.1 (numeric truthiness, `#set` null-RHS, bare null rejection, strict-reference interactions, alternate-value fallbacks).
- **Milestone M4 (Velocity Compatibility Oracle & Differential Runner)**: Differential test harness (`viet-template-tck`) evaluating 301 scenarios across 20 active categories against Apache Velocity 2.4.1 (98.01% exact parity, 5 documented architectural differences, 1 extension, 0 unsupported, 0 unclassified regressions).
- **Milestone M5 (Semantic Analyzer & Model Typing)**: Symbol scoping, type inference, `VType` hierarchy, model declaration binding, and compile-time diagnostics.
- **Milestone M6 (Template IR)**: Control-flow-aware intermediate representation (`IrTemplate`), constant text pools, explicit access plans, and `IrVerifier`.
- **Milestone M7 (Reference Interpreter on IR)**: Semantic parity between AST and IR execution.
- **Milestone M8 (Output Runtime)**: `TemplateOutput` streaming to `Writer` and `OutputStream`, pre-encoded UTF-8 chunks, and contextual HTML escaping.
- **Milestone M9 (Dynamic Linker & Inline Caches)**: `MethodHandle` dynamic resolution, polymorphic inline cache (PIC), bounded megamorphic cache, and classloader-safe weak references.
- **Milestone M10 (Optimization Pipeline Baseline)**: Constant folding, text merging, dead branch elimination, and primitive specialization passes.
- **Milestone M11 (AOT Bytecode Backend)**: Java 17-compatible direct bytecode generation, deterministic naming, and verified JVM bytecode execution.
- **Milestone M12 (Template Repository, Cache & Hot Reload)**: Traversal-safe template IDs, classpath/filesystem repositories, composite resolution, compilation cache with SHA-256 fingerprinting, and atomic registry swapping.
- **Milestone M12.5 (Velocity Application Compatibility Architecture)**: Static dependency extraction from IR, template dependency graph (`TemplateDependencyGraph`), multi-source context composition (`RenderRequest`, `RenderContextContributor`), collision policies (`FAIL`, `MODEL_WINS`, `CONTRIBUTOR_WINS`), global Velocimacro libraries (`velocimacro.library`), and two-stage layout rendering (`LayoutRenderPlan`).
- **Milestone M19.1 (Benchmark and Profiling Infrastructure)**: Dedicated benchmark module `viet-template-benchmarks` with dual Gradle/Maven parity, 10 canonical JMH suites covering workloads B01–B15, environment metadata recording, and 100% fixture correctness verification.

### Completed Milestone: M19.1 — Benchmark and Profiling Infrastructure

Milestone M19.1 is complete, establishing the dedicated benchmark and profiling infrastructure prior to undertaking the slot-based runtime rewrite:

- **Dedicated Module**: Create `viet-template-benchmarks` with full Gradle and Maven dual build parity.
- **JMH Framework**: Configure JMH with annotation processing, memory profilers (`-prof gc`), and fixed JVM arguments.
- **Nine Benchmark Suites**: Implement all 9 core benchmark classes:
  1. `StaticHtmlBenchmark` (B01)
  2. `ScalarVariableBenchmark` (B02)
  3. `DeepPropertyChainBenchmark` (B03)
  4. `ConditionalBranchBenchmark` (B04)
  5. `ForeachLoopBenchmark` (B05, B06, B07)
  6. `EscapingBenchmark` (B08)
  7. `DynamicCallSitePicBenchmark` (B09, B10)
  8. `TemplateCompilationCacheBenchmark` (B15)
  9. `MacroAndLayoutBenchmark` (B11, B12)
- **Baseline Capture**:
  - **M19.1a (Internal Java 17 Baseline - Completed)**: Official internal baseline captured on commit `aad9d35` across all canonical benchmark suites (`viet-template-benchmarks/build/reports/jmh/baseline-java17.json`).
  - **M19.1b (Cross-Engine Comparators - Pending)**: Comparative benchmarks against handwritten Java, Apache Velocity 2.4.1, Quarkus Qute, jte, and Thymeleaf remain planned as separate comparator suites before adopting cross-engine claims.
  - **M19.1c (Cross-JDK Validation - Completed)**: Cross-JDK validation across Java 17, 21, and 25 completed with identical methodology on clean commit `26567ca` (`baseline-java17.json`, `baseline-java21.json`, `baseline-java25.json`).
  - Viet Template aims to reduce rendering overhead relative to reflection-heavy interpreted template execution while approaching generated or compiled Java performance where its semantics permit. Comparative performance claims against other template engines must be based on reproducible benchmarks using equivalent workloads, configuration, escaping behavior, data models, warmup, and runtime conditions.
  - M19.1 establishes the benchmark methodology and measured baseline before numerical claims are adopted.
- **Scope Discipline in 0.1.x**: In 0.1.x, work is restricted to benchmark infrastructure, baseline measurements, profiler methodology, and isolated evidence-backed fixes only.

### Preservation of Proven Simple Structures in 0.1.x

To adhere to the Java-first design policy and avoid premature complexity:
- **PIC Contiguous Array Scanning**: The dynamic call-site polymorphic inline cache (`DynamicCallSite`) retains a fixed-size `AccessLink[]` array (depth $\le 4$) scanned linearly before escalating to `BoundedWeakClassCache`. For small bounded $N \le 4$, a simple array traversal has low constant factors, avoids unnecessary hashing or node overhead, provides good memory locality, and adheres to a simple correctness model.
- **Dependency Graph BFS Traversal**: `DefaultTemplateDependencyGraph` maintains a reverse dependency index (`Map<TemplateId, Set<TemplateId>>`) and performs cycle-safe Breadth-First Search (BFS) using standard `ArrayDeque<TemplateId>` and visited `HashSet<TemplateId>` for transitive invalidation. Standard `ArrayDeque` avoids node and pointer overhead while providing bounded traversal and good locality.
- **Scope Management**: Lexical scopes in `ExecutionContext` continue using `ArrayDeque<LocalScope>` and `HashMap<String, EvaluationValue>`.

---

## Release Phase 0.2.0 — High-Performance Runtime Architecture

Release `0.2.0` introduces the next major internal runtime evolution, transitioning variable resolution from string-based hash lookups to compiler-assigned flat array slots and indexing the compilation cache for scalable invalidation.

**Release Status**: 0.2.0 is RELEASED. Published to Maven Central and GitHub Releases on 2026-09-12.

**Post-release infrastructure status**: 0.2.x release infrastructure hardening is COMPLETE. Central
submission, publication monitoring, public-coordinate verification, consumer smoke testing, and
idempotent GitHub Release finalization are separate resumable stages. This operational follow-up is
not M19.3; M19.3 remains NOT STARTED. M15 remains NOT STARTED.

### Key Milestones & Capabilities (Milestone M19.2)

Milestone M19.2 is split so cache work and variable-slot work retain independent benchmark
attribution:

- **M19.2a — Indexed compile-cache invalidation: COMPLETE.** A concurrent reverse index maps each
  `TemplateId` to all of its live `CompileCacheKey` variants. Targeted invalidation performs an
  average O(1) index lookup followed by O(K) affected-entry cleanup instead of scanning N cache
  entries. Per-template lock stripes define put/invalidate ordering without changing the existing
  access-order LRU lock.
- **M19.2b — Variable slots and `ExecutionFrame`: COMPLETE.** Deterministic compiler-assigned variable
  slots and array-backed `ExecutionFrame` activations for IR and AOT tiers, preserving Velocity 3-state
  evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`), dynamic fallback coherence
  (`#foreach`, `#macro`, `#evaluate`, `#parse`), and mutable root write-through, without variable slot reuse.
- **M19.2c — Performance Engineering Infrastructure & Cross-JDK Analysis: COMPLETE.** Comprehensive
  performance engineering harness established under 0.2.1-SNAPSHOT:
  1. Authoritative runtime profile definitions (`config/benchmark-runtime-profiles.json`, `scripts/perf/runtime_profiles.py`) and runtime validator (`scripts/perf/check-java-runtime.sh`) covering profiles `J17-G1`, `J21-G1`, `J21-ZGC`, `J25-G1`, `J25-G1-COH`, `J25-ZGC`, and `J25-AOT`.
  2. Extended environment metadata recorder (`scripts/record-benchmark-env.sh`) capturing profile ID, Git dirty state, CPU cores, RAM, GC collector, Compact Object Headers (COH), and AOT status.
  3. Profile-aware benchmark runner (`scripts/perf/run-benchmarks.sh`) with standardized directory layouts (`build/performance/<timestamp>-<sha>/<profile>/`).
  4. JMH benchmark comparison utility (`scripts/perf/compare-jmh.py`) with automated direction detection (throughput vs latency), confidence interval overlap evaluation, and Markdown table output.
  5. Java 21 Virtual-Thread stress suite (`viet-template-benchmarks/src/test/.../stress/`) maintaining `--release 17` binary compatibility via `MethodHandle` dynamic resolution, testing shared engine isolation, dynamic call-site transitions, M19.2a linearization invariants, hot reloading, transitive dependency invalidation, and execution budget confinement under high thread concurrency.
  6. Java 25 JFR profiling tools (`scripts/perf/jfr-profile.sh`, `scripts/perf/jfr-summary.sh`) extracting `hot-methods`, `allocation-by-class`, `contention-by-site`, `gc-pauses`, `thread-allocation`, and `pinned-threads`.
  7. Multi-checkpoint process startup measurement harness (`StartupBenchmarkEntrypoint.java`, `scripts/perf/measure-startup.py`, `scripts/perf/measure-startup.sh`).
  8. Java 25 JVM Ahead-of-Time (AOT) cache experiment script (`scripts/perf/jdk-aot-experiment.sh`). Earlier two-run observations (~253 ms normal and ~112 ms with an AOT cache) are initial smoke observations only: they are not statistically reliable, are not authoritative benchmark results, and must not be used as release claims.
  9. Scheduled and manual performance CI pipeline (`.github/workflows/performance.yml`).

### Java Runtime Roles & Baseline Policy

Viet Template explicitly differentiates the roles of supported JDK releases:

- **Java 17 (Authoritative Production Baseline)**:
  - The minimal compiler and runtime bytecode target (`options.release.set(17)`).
  - All published production artifacts (`viet-template-api`, `viet-template-runtime`, `viet-template-language-vtl`, `viet-template-vtl-interpreter`) are compiled to Java 17 classfiles without preview features or Java 21+ API dependencies.
  - Ensures seamless adoption across enterprise Java 17 LTS deployments without bytecode incompatibility.
- **Java 21 (LTS Runtime Target & Virtual Threads Platform)**:
  - Supported runtime execution environment for deployment under modern LTS JVMs.
  - Validates full compatibility with Virtual Threads (JEP 444) and Generational ZGC (JEP 439).
  - High-concurrency stress suites verify that shared engine instances, thread-local contexts, and cache locks execute under high concurrency with no pinning-related correctness or deadlock failure observed in the tested workload.
- **Java 25 (Advanced Runtime & Experimentation Platform)**:
  - Forward-looking performance exploration target.
  - Evaluates memory footprint optimizations via Compact Object Headers (JEP 519).
  - Measures process-level startup and class-loading acceleration via JVM AOT Cache (JEP 483 / JEP 514 / JEP 515).
  - Powers diagnostic profiling via Java Flight Recorder (JFR, including JEP 520) and `jfr view` CLI analysis.

#### Production Baseline Decision Gate

The Java 17 production baseline remains strictly frozen for the entire 0.x release series. Any future proposal to raise the production baseline to Java 21 or 25 requires:
1. An approved Architecture Decision Record (ADR).
2. Broad community consensus and documented enterprise LTS adoption metrics.
3. Quantifiable, statistically significant empirical performance justification satisfying all 7 parts of the DSA Acceptance Rule.

---

## Release Phase 0.3.x+ — Evidence-Driven Optimizations (Gated on Empirical Hotspot Evidence)

Milestone M19.3a is **COMPLETE**; subsequent M19.3 optimizations remain strictly **GATED**.

Post-0.2.0 baseline performance characterization, multi-JDK profiling, and candidate evaluation are formally documented in [`docs/21-performance-characterization.md`](21-performance-characterization.md). Production implementation of M19.3a is documented in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md).

Guided strictly by JMH profiling, JFR allocation/contention analysis, and virtual-thread stress results from M19.2c, 0.3.x introduces targeted optimizations satisfying the 7-part DSA acceptance rule, the Four-Tier Implementation Preference Hierarchy, and the balanced tradeoff evaluation. Optimizations are undertaken **only** for components that post-0.2.0 profiling proves to be dominant hotspots ($\ge 5\%$ of runtime or allocation volume). Speculative or unverified optimizations are strictly prohibited.

### Candidate Optimization Areas (Milestone M19.3 - Empirical Ranking)

1. **Rank 1 (Promoted) — LRU Cache Contention Mitigation**:
   - **Evidence**: JFR `contention-by-site` identified `TemplateCompileCache.get(CompileCacheKey)` (`synchronized (lruLock)`) as the top monitor contention site in the runtime ($25\text{ contention events}$, $14.6\text{ ms}$ average wait time). JMH `ConcurrentCacheBenchmark` proved a $>55\%$ throughput collapse under 4 and 8 concurrent worker threads.
   - **Status**: **COMPLETE (Production Implementation Documented in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md))**. Batched deferred maintenance with per-thread striped ring buffers implemented in `TemplateCompileCache`, delivering 6.85x read-hit speedup (50.32M ops/s at 8 threads) and 5.05x end-to-end rendering speedup with 0 JFR monitor contention events and zero steady-state allocation.
2. **Rank 2 (Promoted) — Streaming Output Buffer & Primitive Byte Formatting**:
   - **Evidence**: JFR `allocation-by-class` during rendering proved `byte[]` represents $33.11\%$ of steady-state allocation volume. `ForeachRenderingBenchmark` showed streaming UTF-8 output (`Utf8StreamOutput`) running $10\text{--}15\%$ slower than `StringOutput` due to lack of buffer pooling and intermediate byte conversions.
   - **Status**: **QUALIFIED & PROMOTED FOR M19.3 PLANNING**. Evaluate thread-local or pooled output buffers and direct primitive byte encoding.
3. **Disqualified / Deferred — Lexer & Parser Token Allocation Reductions**:
   - **Evidence**: Token objects represent $<0.1\%$ of allocations. Cold template compilation is a one-time startup cost ($78\text{--}90\text{ ms}$) bypassed once templates are cached.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails $\ge 5\%$ steady-state hotspot threshold.
4. **Disqualified / Deferred — Dependency Graph Concurrency Refinements**:
   - **Evidence**: JFR recorded zero contention events on `TemplateDependencyGraph`. Read-write locks operate with negligible overhead for typical hierarchy depths.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails empirical contention threshold.
5. **Disqualified / Deferred — MethodHandle `invokedynamic` Prototype**:
   - **Evidence**: Contiguous `AccessLink[]` PIC array scans achieve $51\text{--}85\text{ million ops/s}$. Property dispatch accounts for $<1.5\%$ of rendering CPU time. Transitioning to `invokedynamic` introduces risks of classloader leakage and native-image penalties for negligible gain.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails $\ge 5\%$ runtime threshold.

---

## Release Phase 1.0 — Production Readiness, Public API & Framework Integration

The 1.0 release establishes stable public APIs, seamless Spring ecosystem integration, production build tooling, and formal publication.

### Key Milestones

- **Milestone M14 (Public API & SPI Stabilization)**:
  - Finalize public abstractions: `TemplateEngine`, `Template`, `CompiledTemplate`, `TemplateRepository`, `RenderContext`, `TemplateOutput`, `Escaper`, `MemberAccessPolicy`.
  - Guarantee strict semantic versioning and backward compatibility.
- **Milestone M15 (Maven & Gradle AOT Tooling)**:
  - Dedicated `viet-template-maven-plugin` and `viet-template-gradle-plugin` for build-time template precompilation, model validation, and class generation.
- **Milestone M16 & M17 (Spring Framework 7 & Spring Boot 4 Integration)**:
  - `viet-template-spring` providing Spring MVC `View` and `ViewResolver`.
  - `viet-template-spring-boot-starter` with `@AutoConfiguration`, configuration properties (`viet.template.*`), devtools hot reload, and AOT runtime hints.
- **Milestone M18 (TCK & Performance Release Gates)**:
  - Independently executable public TCK verifying 100% of claimed language features.
  - Reproducible benchmark report published with full hardware metadata, raw JMH outputs, and comparative analyses against Velocity, Qute, jte, and Thymeleaf.
- **Production Hardening**:
  - GraalVM Native Image reachability-metadata verification.
  - Comprehensive migration guide from Apache Velocity.
  - Publication to Maven Central under `io.github.minh124199:viet-template-*`.
