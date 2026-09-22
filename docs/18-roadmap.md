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

**Release Status**: 0.2.2 is RELEASED. Published to Maven Central and GitHub Releases on 2026-09-20 (0.2.0 on 2026-09-12, 0.2.1 on 2026-09-17). The supported release line is `0.2.x`. The current active development version is `0.2.3-SNAPSHOT` on `main`. Subsequent minor milestones target `0.3.x` (evidence-driven optimizations) and `1.0`.

**Post-release infrastructure status**: 0.2.x release infrastructure hardening is COMPLETE. Central
submission, publication monitoring, public-coordinate verification, consumer smoke testing, and
idempotent GitHub Release finalization are separate resumable stages. This operational follow-up is
not M19.3; M19.3 remains NOT STARTED. M15 is COMPLETE (see Milestone M15 below).

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

- **Java 17 (Historical Benchmark Baseline)**:
  - Retained in historical M19.1 reports and the 0.1.x maintenance context only.
  - Not part of the active 0.2.x+ optimization acceptance matrix.
- **Java 21 (Minimum Compile and Runtime Baseline)**:
  - The minimum compiler, classfile, and runtime target (`--release 21`).
  - Validates full compatibility with Virtual Threads (JEP 444) and Generational ZGC (JEP 439).
  - High-concurrency stress suites verify that shared engine instances, thread-local contexts, and cache locks execute under high concurrency with no pinning-related correctness or deadlock failure observed in the tested workload.
- **Java 25 (Primary Development and Performance Runtime)**:
  - The primary current CI, profiling, and optimization qualification runtime.
  - Evaluates memory footprint optimizations via Compact Object Headers (JEP 519).
  - Measures process-level startup and class-loading acceleration via JVM AOT Cache (JEP 483 / JEP 514 / JEP 515).
  - Powers diagnostic profiling via Java Flight Recorder (JFR, including JEP 520) and `jfr view` CLI analysis.

#### Production Baseline Decision Gate

The active baseline is Java 21 under ADR-0007 and ADR-0010. Java 25 is the primary performance runtime. Future baseline changes require an approved architecture decision and repository-wide compatibility validation.

---

## Release Phase 0.3.x+ — Evidence-Driven Optimizations (Gated on Empirical Hotspot Evidence)

Milestone M19.3a, M19.3a.1, and M19.3a.2 are **COMPLETE**; subsequent M19.3 optimizations remain strictly **GATED**.

Post-0.2.0 baseline performance characterization, multi-JDK profiling, and candidate evaluation are formally documented in [`docs/21-performance-characterization.md`](21-performance-characterization.md). Production implementation of M19.3a is documented in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md), memory-model hardening in [`docs/24-m19.3a1-recency-memory-model-hardening.md`](24-m19.3a1-recency-memory-model-hardening.md), and low-concurrency fast-path tuning in [`docs/25-m19.3a2-low-concurrency-fast-path.md`](25-m19.3a2-low-concurrency-fast-path.md).

Guided strictly by JMH profiling, JFR allocation/contention analysis, and virtual-thread stress results from M19.2c, 0.3.x introduces targeted optimizations satisfying the 7-part DSA acceptance rule, the Four-Tier Implementation Preference Hierarchy, and the balanced tradeoff evaluation. Optimizations are undertaken **only** for components that post-0.2.0 profiling proves to be dominant hotspots ($\ge 5\%$ of runtime or allocation volume). Speculative or unverified optimizations are strictly prohibited.

### Candidate Optimization Areas (Milestone M19.3 - Empirical Ranking)

1. **Rank 1 (Promoted) — LRU Cache Contention Mitigation, Memory-Model Hardening & Low-Concurrency Fast-Path Tuning**:
   - **Evidence**: JFR `contention-by-site` identified `TemplateCompileCache.get(CompileCacheKey)` (`synchronized (lruLock)`) as the top monitor contention site in the runtime ($25\text{ contention events}$, $14.6\text{ ms}$ average wait time). JMH `ConcurrentCacheBenchmark` proved a $>55\%$ throughput collapse under 4 and 8 concurrent worker threads.
   - **Status**: **COMPLETE (Production Implementation in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md), Hardening in [`docs/24-m19.3a1-recency-memory-model-hardening.md`](24-m19.3a1-recency-memory-model-hardening.md), & Fast-Path Tuning in [`docs/25-m19.3a2-low-concurrency-fast-path.md`](25-m19.3a2-low-concurrency-fast-path.md))**. Batched deferred maintenance with 16 shared atomic slot sampler stripes and amortized maintenance threshold (`READ_DRAIN_THRESHOLD = 256`) implemented in `TemplateCompileCache`, delivering 6.02x–8.97x read-hit speedup (up to 56.73M ops/s at 8 threads), 5.11x concurrent rendering speedup, complete 1T throughput recovery (+145.0% to 20.11M ops/s, surpassing legacy locked baseline by +10.9%), 0 JFR monitor contention events, 0.000 B/op recency allocation, and full release-acquire happens-before publication safety.
2. **Rank 2 (Promoted) — Streaming Output Buffer & Primitive Byte Formatting**:
   - **Evidence**: JFR `allocation-by-class` during rendering proved `byte[]` represents $33.11\%$ of steady-state allocation volume. Exhaustive qualification study ([`docs/26-m19.3b-output-allocation-qualification.md`](26-m19.3b-output-allocation-qualification.md)) across 14 workloads and 5 output targets attributed streaming allocations: 99.6% of `byte[]` in unpooled streaming stems from 8KB buffer setup and ByteArrayOutputStream growth. Isolated temporary array leak in `NumberFormatting` (saving 32 B/int and 40 B/long, +72.3% throughput gain) and substring leak in `HtmlTextEscaper` (67.6% allocation drop).
   - **Status**: **M19.3b COMPLETE & FROZEN ([`docs/26-m19.3b-output-allocation-qualification.md`](26-m19.3b-output-allocation-qualification.md), [`docs/27-m19.3b1-direct-number-formatting.md`](27-m19.3b1-direct-number-formatting.md), [`docs/28-m19.3b2-zero-allocation-html-escaping.md`](28-m19.3b2-zero-allocation-html-escaping.md), [`docs/29-m19.3b2-1-range-write-spi-hardening.md`](29-m19.3b2-1-range-write-spi-hardening.md), [`docs/30-m19.3b3-bounded-utf8-buffer-reuse.md`](30-m19.3b3-bounded-utf8-buffer-reuse.md), [`docs/31-m19.3b3-1-post-merge-validation.md`](31-m19.3b3-1-post-merge-validation.md))**. Candidate 1 (Zero-Allocation Direct Primitive Number Formatting) productionized in `NumberFormatting`, eliminating 100% of temporary formatting arrays (32 B/int -> 0 B/op, 40 B/long -> 0 B/op). Candidate 2 (Zero-Allocation HTML Escaping) productionized via range streaming in `TemplateOutput` and `HtmlTextEscaper`, eliminating 100% of substring/subSequence allocations (184-880 B/op -> 0.000 B/op), delivering up to +53.4% (J17) / +66.0% (J21) / +58.0% (J25) throughput speedups on escaping workloads. Milestone M19.3b.2.1 hardened the `TemplateOutput.write(CharSequence, int, int)` public SPI contract and eliminated non-String range write heap churn in `WriterTemplateOutput` via lazy instance-buffer batching ($0.000\text{ B/op}$, up to $+16.7\%$ speedup on small slices) while avoiding per-character monitor synchronization bottlenecks. Milestone M19.3b.3 productionized Candidate 3 (Bounded UTF-8 Stream-Buffer Reuse) via `Utf8BufferPool` (capacity 16, 128 KiB retained payload, lock-free `AtomicReferenceArray`), eliminating 8,208 B/op per streaming render and delivering up to +142.6% authoritative speedup on tiny templates and +21.1% on medium templates with 0 contention events. Milestone M19.3b.3.1 completed post-merge validation (3 forks, 5 warmups, 10 measurements, concurrency scaling across 1–32 threads, 10,000 virtual-thread tasks, and lifecycle audits), proving zero regressions and freezing the streaming output subsystem. Entire M19.3b optimization line is COMPLETE & FROZEN.
3. **Rank 3 (Promoted) — Steady-State Execution Preparation & DSA Specialization**:
   - **Evidence**: JFR profiling of warmed rendering identified generation-invariant work on the render path (repeated IR optimization/verification, root/function layouts, and reflection on precompiled templates), $O(N)$ collection materialization in loops (arrays to `ArrayList`, ranges to boxed lists, eager iterator draining), request-scope map churn (`HashMap` allocation and duplicate slot/scope writes), and unnecessary `$foreach` metadata construction when metadata is unobservable.
   - **Status**: **M19.3c COMPLETE & FROZEN ([`docs/39-m19.3c-steady-state-dsa.md`](39-m19.3c-steady-state-dsa.md))**. Comprises five evidence-backed phases:
     - **M19.3c.1 (Engine-Scoped Precompiled AOT Reuse — COMPLETE & FROZEN)**: Generated template instances are prepared once per engine generation and reused concurrently without static caching or request-state leakage.
     - **M19.3c.2 (Prepared IR Execution — COMPLETE & FROZEN)**: Moved IR optimization, verification, and root/function layout preparation out of warmed rendering into generation-scoped immutable prepared executables.
     - **M19.3c.3 (LoopPlan-Driven Iteration Specialization — COMPLETE & FROZEN)**: Direct traversal of arrays and ranges via constant-state iterators; direct caller iterator consumption without eager draining; immediate stop on `#break`. (RandomAccess indexing REJECTED as insignificant end-to-end).
     - **M19.3c.4 (Request-Scope Representation Cleanup — COMPLETE & FROZEN)**: Deleted redundant temporary foreach/macro maps and duplicate slot/scope writes; single-probe template-variable lookup; preserved public scope copy guarantees and 3-state null/undefined semantics. (SmallLocalScope REJECTED as standard `HashMap` churn dropped to $\approx 0.15\%$).
     - **M19.3c.5 (Foreach Metadata Observability & Elision — COMPLETE & FROZEN)**: Conservative compile-time analysis elides metadata objects, wrappers, slot writes, and scope synchronization when unobservable (~60% IR allocation drop for $N=100$, ~5.65 KB/op eliminated, neutral on J25 AOT), while strictly preserving immutable snapshots when observed or dynamic hazards exist. (Raw/tagged `ExecutionFrame` REJECTED).
   - **Stop Rule**: Fresh post-M19.3c.5 profiling did not identify another production optimization with a favorable performance-to-complexity ratio. No M19.3c.6 was selected; M19.3c is complete and frozen. Any future performance work requires a fresh baseline and new profiling justification.
4. **Disqualified / Deferred — Lexer & Parser Token Allocation Reductions**:
   - **Evidence**: Token objects represent $<0.1\%$ of allocations. Cold template compilation is a one-time startup cost ($78\text{--}90\text{ ms}$) bypassed once templates are cached.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails $\ge 5\%$ steady-state hotspot threshold.
5. **Disqualified / Deferred — Dependency Graph Concurrency Refinements**:
   - **Evidence**: JFR recorded zero contention events on `TemplateDependencyGraph`. Read-write locks operate with negligible overhead for typical hierarchy depths.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails empirical contention threshold.
6. **Disqualified / Deferred — MethodHandle `invokedynamic` Prototype**:
   - **Evidence**: Contiguous `AccessLink[]` PIC array scans achieve $51\text{--}85\text{ million ops/s}$. Property dispatch accounts for $<1.5\%$ of rendering CPU time. Transitioning to `invokedynamic` introduces risks of classloader leakage and native-image penalties for negligible gain.
   - **Status**: **DISQUALIFIED / DEFERRED**. Fails $\ge 5\%$ runtime threshold.

---

## Release Phase 1.0 — Production Readiness, Public API & Framework Integration

The 1.0 release establishes stable public APIs, seamless Spring ecosystem integration, production build tooling, and formal publication.

### Key Milestones

- **Milestone M14 (Public API & SPI Stabilization) — COMPLETE**:
  - Finalized 8 core public abstractions: `TemplateEngine`, `Template`, `CompiledTemplate`, `TemplateRepository`, `RenderContext`, `TemplateOutput`, `Escaper`, `MemberAccessPolicy`.
  - Established the frozen 80-type core API baseline in `config/api-baseline/1.0-public-api.txt`.
  - Contract hardening: strict 3-state evaluation preservation (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`), `AutoCloseable` lifecycle, fail-closed security enforcement, thread-confinement documentation.
  - Automated binary & source compatibility tooling (`scripts/verify-api-compatibility.py`, CI enforcement).
  - Third-party SPI implementor fixtures and external consumer smoke tests.
  - Formally documented in [`docs/32-m14-public-api-spi-stabilization.md`](32-m14-public-api-spi-stabilization.md).
- **Milestone M14.1 (Public Surface Containment + Lifecycle Finalization) — COMPLETE**:
  - Reduced visibility on 4 unneeded public internal types/members to private or package-private.
  - Established `config/api-baseline/public-surface-classification.txt` with full 4-category classification (initially 341 types at M14.1; subsequently expanded to 360 types with 99 stable types [80 `STABLE_API`, 19 `STABLE_SPI`] following M15, M16, and M16.1 additions).
  - Automated CI verification (`scripts/verify-public-surface-classification.py`) enforcing 0 unclassified types, 0 stale types, baseline parity, and 0 signature leaks.
  - Hardened concrete output stream lifecycles (`Utf8OutputStreamTemplateOutput`, `WriterTemplateOutput`, `StringTemplateOutput`, `TemplateEngine`), verified by `ConcreteOutputLifecycleContractTest`.
  - Added ArchUnit rule `integration_and_tooling_boundary_must_not_access_internal_packages` in `viet-template-tck`.
  - Audited M15 AOT readiness; mandated a narrow build-time compiler facade (`TemplateAotCompiler`) in M15 to decouple build tooling from internal compiler machinery.
  - Formally documented in [`docs/33-m14-1-public-surface-containment.md`](33-m14-1-public-surface-containment.md).
- **Milestone M15 (Maven & Gradle AOT Tooling) — COMPLETE / RELEASED (v0.2.1)**:
  - Implemented narrow public build-time compiler facade `TemplateAotCompiler` in `io.github.minh124199.viettemplate.aot`, adding 6 `STABLE_API` types (expanding classification to 347 types; 72 `STABLE_API`).
  - Implemented self-contained `<clinit>` bytecode generation for constant arrays and dynamic call sites, enabling isolated classloading without runtime reflection.
  - Built `VtlTemplateEngine` ClassLoader discovery of precompiled templates via `META-INF/viet-template/templates.idx` (`rejectRuntimeCompilation(true)`).
  - Delivered `viet-template-maven-plugin` with `compile` goal bound to `process-classes`.
  - Delivered `viet-template-gradle-plugin` with `@CacheableTask` `VietTemplateCompileTask` and configuration cache compatibility.
  - Established automated 100% byte-for-byte dual-build parity verification via `scripts/verify-aot-tooling-parity.sh` and black-box consumer test fixtures.
  - Formally documented in [`docs/34-m15-aot-build-tooling.md`](34-m15-aot-build-tooling.md).
- **Milestone M16 (Spring Framework & Spring Boot Integration) — COMPLETE / RELEASED (v0.2.1)**:
  - Delivered `viet-template-spring` providing thread-safe, immutable `VietTemplateView`, caching `VietTemplateViewResolver` with AOT fallback, non-closing servlet stream ownership (`NonClosingOutputStream`), zero-allocation binary streaming via `Utf8OutputStreamTemplateOutput`, strict path traversal rejection, and `VietTemplateEngineCustomizer` SPI (adding 1 `STABLE_SPI` and 2 `STABLE_API` types).
  - Delivered `viet-template-spring-boot-autoconfigure` providing `VietTemplateAutoConfiguration`, comprehensive configuration properties (`viet-template.*`), template location verification with AOT index discovery (`templates.idx`), and Spring lifecycle management (`destroyMethod = "close"`), adding 2 `STABLE_API` types (expanding total classification to 352 types; 76 `STABLE_API`, 15 `STABLE_SPI`).
  - Delivered `viet-template-spring-boot-starter` aggregator starter combining view resolution, auto-configuration, and VTL interpreter.
  - Established dual-build AOT consumer test fixtures (`integration-tests/spring/maven-mvc-aot` and `integration-tests/spring/gradle-mvc-aot`) testing MockMvc and real embedded HTTP server execution.
  - Verified 100% byte-for-byte bytecode and index dual-build parity and pure AOT execution via `scripts/verify-spring-integration-parity.sh`.
  - Formally documented in [`docs/35-m16-spring-integration.md`](35-m16-spring-integration.md).
- **Milestone M16.1 (Spring Security Integration) — COMPLETE / RELEASED (v0.2.1)**:
  - Delivered optional `viet-template-spring-security` module providing read-only facades `SecurityView` and `CsrfView` with minimized JavaBean accessors (`getName()`, `isAuthenticated()`, `isAnonymous()`, `getAuthorities()`, `hasAuthority()`, `hasAnyAuthority()`, `getToken()`, `getParameterName()`, `getHeaderName()`), factory SPIs `SecurityViewFactory` and `CsrfViewFactory`, and `SpringSecurityRenderContextContributor`.
  - Added generic request metadata attribute bridge `SpringRenderAttributes` (`SERVLET_REQUEST`) in `viet-template-spring`.
  - Added Spring Boot auto-configuration `VietTemplateSecurityAutoConfiguration` under `viet-template.security.enabled` (default `true`) in `viet-template-spring-boot-autoconfigure`.
  - Verified strict isolation: raw framework objects never exposed to templates, sensitive tokens redacted in `toString()`, full compatibility with `VTL_SAFE` sandbox profile, and standard HTML auto-escaping applied to principal names and authorities.
  - Expanded public surface classification baseline to 360 types (80 `STABLE_API`, 19 `STABLE_SPI`, 6 `EXPERIMENTAL`, 255 `PUBLIC_BUT_INTERNAL_ACCIDENT`).
  - Verified single-artifact binary compatibility across Spring Security 6.3.4, 6.5.11, 7.0.7, and 7.1.1 via `scripts/verify-spring-security-compatibility.py`.
  - Delivered dual-generation, dual-build AOT consumer fixtures:
    - Generation 1: `integration-tests/spring/maven-security-aot` & `integration-tests/spring/gradle-security-aot` (Spring Boot 3.3.5 / Spring Security 6.3.4)
    - Generation 2: `integration-tests/spring/maven-security7-aot` & `integration-tests/spring/gradle-security7-aot` (Spring Boot 4.1.1 / Spring Framework 7.0.9 / Spring Security 7.1.1)
  - Automated 10-step dual-build parity and live HTTP server verification via `scripts/verify-spring-security-parity.sh` and `scripts/verify-spring-security7-integration.sh`.
  - Formally documented in [`docs/36-spring-security-integration.md`](36-spring-security-integration.md).
- **Pre-1.0 Stable Surface Convergence Gate — COMPLETE**:
  - Reconciled `config/api-baseline/public-surface-classification.txt` with 4 layered baselines (`1.0-core-public-api.txt` [80 types], `1.0-aot-public-api.txt` [6 types], `1.0-spring-public-api.txt` [8 types], `1.0-spring-security-public-api.txt` [5 types]), protecting all 99 stable types mechanically with 0 duplicate ownership and full bijection enforced by `scripts/verify-api-compatibility.py` and `scripts/verify-public-surface-classification.py` on CI.
- **Milestone M17 (GraalVM Native Image & Advanced Framework Features)**:
  - **M17 Phase A (GraalVM Native Image & Spring AOT) — COMPLETE**:
    - Delivered `VietTemplateRuntimeHints` in `viet-template-spring-boot-autoconfigure` automatically registering precompiled template reflection from `templates.idx`, template resource patterns, and configuration properties.
    - Delivered `VietTemplateSecurityRuntimeHints` in `viet-template-spring-security` registering reflection hints for `SecurityView`, `DefaultSecurityView`, `CsrfView`, and `DefaultCsrfView` via `TypeReference`.
    - Automated native image dual-build consumer fixtures (`maven-boot3-native`, `gradle-boot3-native`, `maven-boot4-native`, `gradle-boot4-native`).
    - Verified full native binary compilation and live HTTP request rendering with runtime compilation disabled via `scripts/verify-native-image-integration.sh` and `.github/workflows/native-image.yml`.
    - Formally documented in [`docs/37-m17-graalvm-native-image.md`](37-m17-graalvm-native-image.md).
  - **M17 Phase B (DevTools Restart/Refresh Hardening) — COMPLETE**:
    - Spring Boot DevTools live reload lifecycle hook hardening and ClassLoader boundary isolation.
    - Delivered `close()` lifecycle and thread shutdown for `DevelopmentFileWatcher` and `VtlTemplateEngine`.
    - Added 4 dual-build DevTools integration fixtures (`maven-boot3-devtools`, `gradle-boot3-devtools`, `maven-boot4-devtools`, `gradle-boot4-devtools`).
    - Verified Mode A (dynamic hot reload without restart), Mode B (AOT recompile + trigger restart with ClassLoader turnover), stale template deletion, and 10x restart stress test with zero ClassLoader leaks via `scripts/verify-devtools-restart-integration.sh` and `.github/workflows/devtools-restart.yml`.
    - Formally documented in [`docs/38-m17-devtools-restart-hardening.md`](38-m17-devtools-restart-hardening.md).
  - **M17 Phase C (Java 21/25 Baseline & Spring 7/Boot 4/Security 7 Modernization) — COMPLETE**:
    - Raised repository compiler baseline from Java 17 to Java 21 (`--release 21`, bytecode major version 65) via ADR-0007.
    - Designated Java 25 as primary build toolchain, CI execution environment, and performance deployment runtime via ADR-0008.
    - Promoted Spring Framework 7.0, Spring Boot 4.0, Spring Security 7.0, and Jakarta Servlet 6.1 (Tomcat 11) to canonical integration baseline via ADR-0009.
    - Formally retired Java 17 for 0.2.x+ active development while designating 0.1.x as maintenance line via ADR-0010.
    - Enforced architectural invariants: single-version bytecode without MRJARs (ADR-0011), ClassFile API evaluation retaining zero-dependency `ClassFileWriter` (ADR-0012), virtual thread non-pinning and thread-confinement invariants (ADR-0013), zero preview features in published APIs (ADR-0014), and legacy Spring 6 / Boot 3 compatibility policy (ADR-0015).
    - Verified Spring 7 and Spring Security 7 virtual thread execution on Tomcat 11 with `Thread.currentThread().isVirtual()` assertions across Maven and Gradle AOT consumer fixtures.
    - Modernized CI workflows (`ci.yml`, `native-image.yml`, `performance.yml`, `fuzz.yml`, `release.yml`, `devtools-restart.yml`) across Tier A-E suites.
- **Milestone M18 (TCK & Performance Release Gates) — COMPLETE & FROZEN** ([`docs/40-m18-tck-performance-release-gates.md`](40-m18-tck-performance-release-gates.md)):
    - Language feature claim matrix: 80 features across 20 categories (`config/tck/vtl-feature-matrix.json`), 100% TCK coverage enforced by `python3 scripts/verify-tck-coverage.py`.
    - 80 publicly executable conformance scenarios in `viet-template-tck`; JUnit expansion is derived rather than treated as a fixed contract. Architecture boundary enforced: zero internal imports.
    - Backend parity verified: 75 dual-backend features produce bit-identical output; 5 IR-only features documented with rationale (`BackendParityTest.java`).
    - Independent consumer fixture: Maven and Gradle standalone projects verify external TCK consumption without reactor access (`integration-tests/tck-consumer/`, `scripts/verify-tck-consumer.sh`).
    - Comparative JMH benchmarks against Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, Thymeleaf 3.1.5.RELEASE across workloads C01–C08 (`ComparativeEngineBenchmark`); cross-engine fixture correctness verified (48 tests).
    - Master release gate: `./scripts/verify-m18-release-gates.sh` (8 gates: coverage, surface, API compat, TCK suite, cross-engine correctness, independent consumer, JSON validity × 2).
    - Evidence infrastructure: `benchmark-evidence/m18/`, report generator `scripts/perf/generate-benchmark-report.py`, `config/benchmark-manifest.json` (C01–C08 + B01–B15).
- **Milestone M20 (1.0 Adoption Readiness, Migration & Documentation Suite) — COMPLETE**:
    - Complete user documentation suite across 11 thematic areas in `docs/` (`getting-started/`, `language/`, `migration/`, `security/`, `deployment/`, `native-image/`, `build-tooling/`, `spring/`, `diagnostics/`, `extensions/`, `performance/`).
    - Authoritative Apache Velocity 2.4.1 migration roadmap (`docs/migration/velocity-migration-guide.md`) and differences catalog (`docs/migration/velocity-differences.md`) with executable test fixtures.
    - Synchronized compatibility matrix (`docs/migration/compatibility-matrix.md`) derived from `config/tck/vtl-feature-matrix.json` (80 features, 100% TCK coverage).
    - Diagnostic error catalog (`docs/diagnostics/error-catalog.md`) covering all parse, compile, semantic, and runtime codes with remedies.
    - Comparative benchmark evidence report (`docs/performance/comparative-benchmarks.md`) qualifying C01–C08 on Java 21/25 against Velocity, Qute, jte, and Thymeleaf.
    - 1.0 Readiness Gap Analysis (`docs/1.0-readiness-gap-analysis.md`) evaluating all 15 architectural areas and establishing the public surface containment plan (105 stable vs 259 accidental types).
    - Automated CI documentation verification engine (`scripts/verify-documentation.py`) and 30 unit tests.
    - Restructured adoption-focused `README.md`, 5-minute quickstart, and standalone plain Java executable fixture (`examples/plain-java/`).
- **Milestone M21 (First-Class Quarkus Extension & Multi-Framework Foundation) — COMPLETE / QUALIFIED FOR 0.2.3**:
    - **M21.1 (Module Architecture & Framework-Neutral Core)**: Introduced separated runtime (`viet-template-quarkus`) and deployment (`viet-template-quarkus-deployment`) modules. Preserved 100% framework-neutral core engine (`viet-template-api`, `runtime`, `language-vtl`, `vtl-interpreter`) enforced by ArchUnit boundaries. Delivered canonical `TemplateSuffixConfiguration`, configurable `UndefinedReferencePolicy` (`SILENT`, `WARN`, `ERROR`), and framework-neutral `NonClosingOutputStream`.
    - **M21.2 (Quarkus CDI & Configuration Mapping)**: Implemented `VietTemplateProducer` exposing `@ApplicationScoped` `TemplateEngine` and `VietTemplateRenderer`. Mapped `quarkus.viet-template.*` via SmallRye `@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)`.
    - **M21.3 (Build-Time AOT Template Compilation & Index Generation)**: `VietTemplateProcessor` scans templates, compiles to Java 21 bytecode (`classfile major version 65`), emits `GeneratedClassBuildItem`, registers reflection via `ReflectiveClassBuildItem`, and generates deterministic `templates.idx` resource.
    - **M21.4 (Dev Mode Live Reload & Transitive Dependency Invalidation)**: Configured `HotDeploymentWatchedFileBuildItem` watching template root and discovered sources. Verified automated live reload on port 18098: initial template -> source modification -> instant refresh without restart -> transitive `#parse` subtemplate inclusion and live edit propagation (`scripts/verify-quarkus-dev-mode.sh`). Zero static Class/ClassLoader retention across reload generations.
    - **M21.5 (GraalVM Native Image Qualification — Maven & Gradle)**: Compiled actual 52 MB native executables using Mandrel 25.0.4.1-Final (Java 25 LTS) for Quarkus 3.39.4. Executed and verified HTTP 200 responses across all endpoints (`/hello`, `/hello/page`, `/hello/stream`, `/hello/undefined`) on ports 18095 (Maven) and 18097 (Gradle) in < 100ms on Linux x86_64. Pure-AOT invariant enforced: dynamic `#parse` and `#evaluate` fail fast at build time (`VTLAOT:1101`, `VTLAOT:1102`). Standard JVM execution qualified across Ubuntu, macOS, and Windows.
    - **M21.6 (Quarkus REST Streaming & Security Integration)**: Implemented zero-copy streaming rendering in `VietTemplateRenderer` utilizing `NonClosingOutputStream` for `StreamingOutput`. Provided optional `QuarkusSecurityRenderContextContributor` exposing presentation-safe `$security` context view via runtime-safe reflection and Arc bean lookup without hard runtime dependencies on `quarkus-security`. Clarified CSRF model: Spring MVC provides automatic `$csrf`; Quarkus requires manual model contribution.
    - **M21.7 (Qute Coexistence & Consumer Verification)**: Verified seamless coexistence with Quarkus Qute within identical application runtime without bean collisions or template path conflicts (`QuteCoexistenceTest`). Automated end-to-end integration across both Maven and Gradle consumer fixtures (`scripts/verify-quarkus-integration.sh`).
- **Milestone M22 (Public Surface Containment & Internal Architecture, target 0.3.0) — PLANNED**:
    - **M22.1 (Package-Private Candidates Reduction — Category A)**: Demote 52 identified package-private candidate types across parser, AST, and compiler internals where cross-package access is not required.
    - **M22.2 (Internal Package Relocation — Category B)**: Move 40 internal implementation types to dedicated `*.internal.*` packages across `runtime`, `vtl-interpreter`, and `api`.
    - **M22.3 (Cross-Module Internal SPI & ABI Hardening — Category C & F)**: Define formal internal SPI contracts for the 158 cross-module internal types and freeze the generated bytecode runtime ABI (9 Category F types: `BytecodeRuntimeBridge`, `ExecutionFrame`, `EvaluationValue`, etc.).
    - **M22.4 (JPMS Qualified Exports & Encapsulation Decision Gate)**: Evaluate introducing `module-info.java` descriptors with qualified exports post-package relocation, ensuring standard classpath consumers and build plugins are strictly protected without breaking multi-framework tooling.
- **Roadmap Sequence Towards 1.0 GA**:
  - **0.2.3 (Immediate Stabilization Release)**: Release documentation suite, migration guides, diagnostic catalog, Quarkus extension, and framework-neutral enhancements to external developers.
  - **0.3.0 (Public Surface Encapsulation Milestone)**: Execute Milestone M22 to relocate and encapsulate 259 accidental public types (`config/api-baseline/accidental-public-types-inventory.json`), securing binary compatibility before 1.0 GA.
  - **1.0.0 GA**: General Availability release locking permanent SemVer binary backwards compatibility, validated by production adopters.
