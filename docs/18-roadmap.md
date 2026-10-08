# 18 — Implementation Roadmap (Historical Milestone Record and Current Status)

> **Status note (2026-10-09):** This document preserves the project's milestone history. Viet Template 1.3.0 is the latest published stable release (published 2026-10-09; 1.2.0 published 2026-10-07). Earlier release-phase plans are historical records; unfinished 1.0 RC soak/readiness wording below is closed by the subsequent 1.0.0 GA release, and must not be read as current work.

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
  - **M19.1b (Cross-Engine Comparators - COMPLETE)**: The comparative C01–C08 suite covers Viet-IR, Viet-AOT, Apache Velocity 2.4.1, Quarkus Qute, jte, and Thymeleaf. See the qualification evidence in `benchmark-evidence/` and `docs/performance/comparative-benchmarks.md`.
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

**Release Status**: Version 1.3.0 was published on 2026-10-09 as the latest stable feature release, following 1.2.0 (2026-10-07), 1.1.0 (2026-10-03), 1.0.1 (2026-09-28), and 1.0.0 GA (2026-09-26). Version 0.2.2 (published 2026-09-20; 0.2.1 on 2026-09-17, 0.2.0 on 2026-09-12) represents the historical 0.2.x line. Version 0.2.3 was prepared across POMs but held without tagging or publication (`PREPARED_HELD`). Milestones M20–M42 are complete and published in 1.3.0.

**Post-release infrastructure status**: 0.2.x release infrastructure hardening is COMPLETE. Central
submission, publication monitoring, public-coordinate verification, consumer smoke testing, and
idempotent GitHub Release finalization are separate resumable stages. This operational follow-up is
not M19.3; M19.3 is COMPLETE (see Milestone M19.3 below). M15 is COMPLETE (see Milestone M15 below).

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
    - Master release gate: `./scripts/verify-m18-release-gates.sh` (9 gates: coverage, surface, API compat, TCK suite, cross-engine correctness, independent consumer, benchmark manifest, evidence contract, signing lifecycle).
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
- **Milestone M22 (Public Surface Containment & Internal Architecture, target 0.3.0) — IN PROGRESS**:
    - **M22.1 (Package-Private Candidates Reduction — Category A) — COMPLETE (0.3.0-M1)**: Demoted 34 validated package-private candidate types across parser, IR optimizer passes, verifier, and interpreter internals where cross-package access is not required, reducing accidental public surface from 259 to 225 types (-34 types) with zero package moves or file relocations. Reclassified 18 types with cross-package/cross-module usages into Categories B and C. Established mechanical runtime ABI baseline (`config/api-baseline/generated-template-runtime-abi.txt`, `scripts/verify-generated-abi.py`) and recorded ADR-0016 formalizing Model D pre-1.0 generated ABI compatibility.
    - **M22.2 (Internal Package Relocation — Category B) — COMPLETE (0.3.0-M2)**: Relocated 49 accidental public implementation types across `viet-template-language-vtl` and `viet-template-vtl-interpreter` into dedicated `*.internal.*` packages (`internal.ast`, `internal.ir.plan`, `internal.semantics`, `internal.compiler`, `internal.engine`, `internal.interpreter`). Reclassified 17 types to Category C to satisfy sealed hierarchy permits, stable API signature leak protection, Spring AOT reflection hints, benchmarks, and optimizer package constraints. Maintained 100% backward compatibility for all 105 stable public API/SPI types across 5 baselines with 0 signature leaks and 0 ABI changes. Published migration guide at `docs/migration/0.3.0-internal-package-moves.md`.
    - **M22.3 (Public Surface Role Classification & Framework/Build Entrypoint Containment — 0.3.0-M3.3) — COMPLETE (0.3.0-M3.3)**: Established the nine-category public surface taxonomy (`STABLE_API`, `STABLE_SPI`, `EXPERIMENTAL`, `GENERATED_RUNTIME_ABI`, `FRAMEWORK_ENTRYPOINT`, `BUILD_TOOL_ENTRYPOINT`, `SERVICE_ENTRYPOINT`, `INTERNAL_CROSS_MODULE`, `PUBLIC_BUT_INTERNAL_ACCIDENT`), recorded in ADR-0017. Reclassified 4 framework entrypoints, 4 build-tool entrypoints, 56 cross-module internal contracts, and 1 generated runtime ABI type. Contained `ModelSchema$Builder` to package-private (C7 resolution, 336 → 335 compiled types). Created machine-readable registries (`config/architecture/framework-and-tooling-entrypoints.json`, `config/architecture/cross-module-internal-contracts.json`). Added `scripts/verify-framework-entrypoints.py` and `scripts/verify-cross-module-contracts.py`. Stable contract: `STABLE_API` (94) + `STABLE_SPI` (25) = 119. Zero signature leaks.
    - **M22.4 (vtl-interpreter Internal-Contract Formalization — 0.3.0-M3.4) — COMPLETE (0.3.0-M3.4)**: Formalized internal interpreter contract types (`EvaluationValue`, `ExecutionContext`, `ExecutionFrame`, `ForeachMetadata`, `EngineInterpreterBridge`) as explicit INTERNAL_CROSS_MODULE contracts. Added `ExecutionTier` public API. Updated cross-module verifier. 6 interpreter types registered in cross-module-internal-contracts.json. M3.4 completed the vtl-interpreter internal-contract formalization phase.
    - **M22.5 (Final Public/Internal Architecture Convergence — 0.3.0-M3.5) — COMPLETE (0.3.0-M3.5)**: Introduced two new surface taxonomy categories: `INTERNAL_CROSS_PACKAGE` (55 types — intentionally public within their artifact due to cross-package callers in the same JAR; not architectural debt) and `BENCHMARK_SUPPORT_INTERNAL` (3 types — exposed only to the unpublished JMH benchmark suite). Updated cross-module contract registry schema to add `consumerScope` (MAIN/TEST/BENCHMARK) and `publicationStatus` fields; production internal contract count is now 56 (excluding 6 non-production contracts). Created `config/architecture/public-surface-debt-registry.json` tracking all remaining 85 PUBLIC_BUT_INTERNAL_ACCIDENT types with target milestones. Corrected documentation: JPMS export control does not reduce Java `public` visibility. Physical module boundary (language-vtl / vtl-interpreter): KEEP, re-evaluate at M22. JPMS decision: DEFER. All 10 architecture verifier scripts pass. Benchmark/TCK contract audit: all 6 M3.4 contracts are non-production (viet-template-benchmarks and viet-template-tck are both unpublished). Post-M3.5 metrics: STABLE_API 94, STABLE_SPI 25, INTERNAL_CROSS_MODULE 59, INTERNAL_CROSS_PACKAGE 55, BENCHMARK_SUPPORT_INTERNAL 3, PUBLIC_BUT_INTERNAL_ACCIDENT 85, total 335 types, 0 signature leaks.
    - **M22.6 (JPMS Qualified Exports Decision Gate) — DEFERRED**: Evaluate introducing `module-info.java` descriptors with qualified exports. Deferred pending M22 module boundary resolution. Framework compat (Spring, Quarkus) on named module path must be verified before adoption.
- **Milestone M23 / 0.3.0-M4 (Runtime Execution Model & Prepared Architecture) — COMPLETE**:
    - **Milestone M4.0 (Runtime Architecture Baseline & Diagnostic Audit) — COMPLETE (0.3.0-M4.0)**: Established authoritative pre-M4 baseline on commit `869d4abb8339893d5c9f53e6b5204cbb463d1a85`. Profiled `TemplateEngine.get(id)`, `Template.render(...)` across warm and cold paths, AOT, IR, and AST fallback tiers. Identified allocation hotspots on warmed lookups and verified P0 exception masking fixes.
    - **Milestone M4.1 (Prepared Execution Architecture & Engine Decomposition) — COMPLETE (0.3.0-M4.1)**: Precomputed immutable `EngineFingerprint` per engine configuration. Introduced sealed `ExecutionTarget` polymorphic dispatch hierarchy (`PreparedAotExecutionTarget`, `PreparedIrExecutionTarget`, `PreparedAstExecutionTarget`) with verified monomorphic C2 JIT inlining. Introduced `PreparedTemplateEntry` and canonical `Template` wrapper reuse across engine lookups. Implemented AST parse-once preparation per engine generation in `PreparedAstExecutionTarget`. Decomposed monolithic `VtlTemplateEngine` (645 -> 387 LOC, -40%) into cohesive collaborators (`AotTemplateRegistry`, `TemplateCompilationCoordinator`, `TemplateDependencyCoordinator`).
    - **Milestone M4.2 (Matched Performance Qualification & Prepared Lifecycle Verification) — COMPLETE (0.3.0-M4.2)**: Matched JMH benchmark qualification on both Java 21 and Java 25 against pre-M4.1 baseline `869d4ab`. Zero render regression confirmed across IR (-0.23% J21, -4.63% J25) and AOT (-0.84% J21, +1.05% J25 within noise) tiers with 100% monomorphic C2 inlining (`TypeProfile (26398/26398)`). Warmed `engine.get()` allocation reduced by -184 B/op (-12.2%) on Java 21 (1504 -> 1320 B/op) and -168 B/op (-11.9%) on Java 25 (1416 -> 1248 B/op); pure AOT maintained 0.0 B/op. Multi-tier lifecycle, DAG transitive invalidation, 256-thread concurrency stress, zero virtual thread pinning, and dual-build framework integration (Spring Boot, Quarkus JVM & Native) verified.
- **Milestone M5 (Generation-Aware Warmed-Lookup Simplification Program — target 0.3.0-M5) — COMPLETE & FROZEN (0.3.0-M5.9)**:
    - **M5.0 (Documentation & Benchmark Baseline Alignment) — COMPLETE**: Synchronized benchmark evidence, recorded matched Java 21 baseline, and qualified runtime architectural findings.
    - **M5.1 (Repository Freshness Capability Model) — COMPLETE**: Introduced `TemplateFreshnessProvider` and `FreshnessToken` SPI in `viet-template-api` for positive caching without stream reading or SHA-256 fingerprinting on immutable/versioned repositories. Decoupled `TemplateRepository` from freshness tokens to preserve clean SPI minimalism.
    - **M5.2 (Generation-Aware Positive Cache Lookup) — COMPLETE**: Validated template cache hits using freshness tokens and macro generation counters directly in `VtlTemplateEngine.get(id)`.
    - **M5.3 (Global Macro Generation Token) — COMPLETE**: Monotonic generation tracking in `GlobalMacroManager` eliminating repeated macro registry hashing on warmed hits.
    - **M5.4 (CompileCacheKey Construction Streamlining) — COMPLETE**: Precomputed `EngineFingerprint` reuse and zero-allocation key lookup on active cache entries.
    - **M5.5 (Negative Cache Integration & TTL Eviction) — COMPLETE**: Freshness-aware negative cache eviction and capacity bounds.
    - **M5.6 (Public Surface Classification & ABI Verification) — COMPLETE**: Strict containment of new capability interfaces to stable SPI/API boundaries. Verified `NO_PERSISTENT_CACHE_SCHEMA` and eliminated unused serialization flags.
    - **M5.7 (Matched JMH & JFR Qualification) — COMPLETE**: Incremental M4.2 -> M5 qualification demonstrated -48 B/op (-3.6% J21, -3.8% J25) and -3.73% J21 latency reduction; cumulative M4-M5 program achieved -232 B/op (-15.4%) and -14.12% latency reduction.
    - **M5.8 (Documentation & ADR Convergence) — COMPLETE**: Recorded ADR-0018 and finalized milestone reports.
    - **M5.9 (Freshness Contract Hardening, API Minimization & Architecture Freeze) — COMPLETE**: Addressed adversarial filesystem replacement via `FILESYSTEM_USES_SAFE_FALLBACK`; validated composite shadowing, legacy custom repository fallback, negative cache transitions, macro generation mutations, and 32-thread concurrency stress; verified clean ClassLoader lifecycle and zero virtual thread pinning. Runtime architecture is frozen.

- **Milestone M6 (Runtime Exception Semantics, Failure Boundaries & Diagnostic Hardening — target 0.3.0-M6) — COMPLETE & FROZEN**:
    - **M6.0 (Exception Boundary Inventory & Classification)**: Completed comprehensive inventory of all 217 catch blocks across production modules; mapped broad catches into 16 canonical categories and prioritized into P0, P1, and P2 remediation targets.
    - **M6.1 (P0 Fatal Error, Thread Death & Security Propagation)**: Guaranteed that `VirtualMachineError` (including `OutOfMemoryError`, `StackOverflowError`) and `ThreadDeath` escape unmasked across all runtime and framework layers (`QuarkusSecurityView`, `VietTemplateProducer`, `BytecodeRuntimeBridge`, `LinkedReferenceAccess`).
    - **M6.2 (Repository Resolution & Freshness Boundaries)**: Distinguished expected misses (`Optional.empty()`) from I/O failures (`TemplateResourceException`) and authorization denials (`TemplateSecurityException`). Prevented negative cache poisoning on error conditions.
    - **M6.3 (Compiler & AOT Class Loading Boundaries)**: Enforced strict separation between absent AOT classes (`ClassNotFoundException`) and corrupt/incompatible AOT classes (`LinkageError` -> `TemplateCompilationException`).
    - **M6.4 (Runtime Evaluation & Dynamic Linker Failure Semantics)**: Unmasked underlying application exception causes with `INVALID_METHOD` diagnostic code in `LinkedReferenceAccess`, `IrInterpreter`, and `BytecodeRuntimeBridge`; normalized AOT user-method and property failure semantics to `TemplateRenderException(INVALID_METHOD, cause)`; established 100% exact semantic match across AST, IR, and AOT execution tiers.
    - **M6.5 (Framework & Build-Tool Boundary Consistency)**: Ensured Spring MVC (`VietTemplateViewResolver`) and Quarkus (`VietTemplateRenderer`) rethrow `TemplateSecurityException` and fatal JVM errors; narrowed parameter parsing catches in Maven/Gradle plugins and AOT hints.
    - **M6.6 (Automated CI Exception Verifier & Allowlist)**: Created machine-readable `config/architecture/exception-boundary-allowlist.json` registering all 31 allowable broad catch blocks with justifications, categories, and owners; added `scripts/verify-exception-semantics.py` and unit tests enforcing 0 unallowlisted catches and 0 stale entries.
    - **M6.7 (Documentation & ADR-0019)**: Formulated comprehensive architecture guide (`docs/architecture/exception-semantics.md`) and recorded ADR-0019 (`docs/adr/0019-runtime-exception-and-failure-boundaries.md`).
    - **M6.9 (Evidence Reconciliation, Backend Exception-Parity Closure, API/ABI Accounting Correction & M6 Freeze) — COMPLETE & FROZEN**: Reconciled backend parity evidence table to `EXACT_SEMANTIC_MATCH`; reconciled stable public surface count to 121 (94 `STABLE_API` + 27 `STABLE_SPI`); disambiguated primary `GENERATED_RUNTIME_ABI = 1` taxonomy category from the 7 Viet-owned bytecode runtime dependencies; corrected module classification (14 physical build modules, 12 published artifacts, 2 unpublished verification modules); verified AOT tooling parity and negative build failure diagnostics across Maven and Gradle; confirmed zero unallowlisted catches, zero stale entries, fail-closed security, and fatal JVM error escape. M6 runtime exception semantics are frozen.

- **Milestone M8 (1.0 Candidate Contract Freeze & Local RC Staging) — COMPLETE**:
    - **M8.0 (Pre-Freeze Wording & Evidence Reconciliation) — COMPLETE**: Reconciled candidate baseline terminology to `1.0 Candidate Baseline` (`READY_TO_FREEZE`), refined native qualification claims to exact tested toolchain (`Linux x86_64 Mandrel 25.0.4.1-Final, Java 25`), reconciled Quarkus publication history to `Never published. First intended release: 0.3.0+ / 1.0 candidate line`, removed stale `M4.5` references in favor of `POST_1_0_MAINTENANCE`, and classified Spring AOT as `JVM_AOT_QUALIFIED`.
    - **M8.1 (1.0 API, SPI, Generated ABI, Diagnostics & Tooling Freeze) — COMPLETE & FROZEN**: Formally froze 121 stable types (94 `STABLE_API` + 27 `STABLE_SPI`), 7 generated runtime ABI types (22 invoked methods, 0 fields), 31 canonical diagnostic codes, 23 framework configuration keys, and build-tool contracts (Maven Mojo & Gradle plugin tasks/wiring). Recorded ADR-0020 and established machine-readable `config/compatibility/framework-configuration-keys.json`. Evaluated Velocity differential suite (295/301 exact, 5 expected differences, 1 extension, 100% accounted).
    - **M8.2–M8.8 (RC Version Preparation, Artifact Assembly, Local Staging & Consumer Qualification) — COMPLETE**: Prepared 1.0.0-RC1, validated release bundles, staged local repository, and qualified external consumers across Spring Boot 3/4 and Quarkus JVM/native.
- **Milestone M8.9 (RC1 Artifact Provenance Rebuild, Contract Evidence Reconciliation, Documentation Convergence, and Public-RC Publication Readiness) — COMPLETE**:
    - Rebuilt exact 1.0.0-RC1 artifact set from one authoritative final qualification commit SHA.
    - Resolved provenance gap between test fixture updates and staged release artifacts.
    - Mechanically reconciled diagnostic baseline: confirmed 31 canonical `STABLE_TOOLING_CODE` entries and classified `PARSER:*` codes (such as `[PARSER:UNCLOSED_DIRECTIVE]`) as `INTERNAL_DETAIL`.
    - Mechanically regenerated Velocity differential compatibility evidence from `StandardExpectations` (301 scenarios: 295 exact, 5 expected differences, 1 extension, 100% accounted coverage).
    - Reconciled exception hierarchy documentation to match actual public classes in `viet-template-api` (corrected `TemplateParseException` to `TemplateSyntaxException`).
    - Reconciled framework support matrix to explicit Declared Minimum, RC-Tested, and Canonical RC versions (avoiding unverified continuous range claims).
    - Reconciled native-image toolchain evidence: Spring Boot 3/4 Native on Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS), Quarkus Native on Mandrel 25.0.4.1-Final.
    - Updated release workflow to ensure GitHub Release sets `prerelease=true` for release candidates.
    - Enforced one-SHA release provenance invariant and artifact manifest rule across documentation and tooling.
    - Reached verdict: `RC1_READY_FOR_AUTHORIZED_PUBLICATION`.
- **Milestone M9 (Public RC Publication, Compatibility Reconfirmation, and 1.0 GA Promotion) — CLOSED BY RELEASE**:
    - The 1.0.0-RC1 publication and Central consumer verification recorded above were followed by the published 1.0.0 GA release on 2026-09-26 (commit `b951021`) and patch 1.0.1 on 2026-09-28 (commit `a6d96e3`). This closes the 1.0 promotion work; this roadmap does not claim that a multi-week public soak occurred.
    - Subsequent releases, including 1.1.0 on 2026-10-03, supersede this historical readiness phase. Current support and release state are in the [support matrix](getting-started/support-matrix.md) and [README](../README.md).

- **Roadmap Sequence Towards 1.0 GA**:
  - **0.2.3 Status**: Historical release prepared across POMs but held without tagging or publication (`PREPARED_HELD`).
  - **0.3.0 Development Line**: Completed public surface encapsulation (M1–M3.5), runtime architecture (M4), warmed-lookup simplification (M5), exception semantics hardening (M6), 1.0 candidate contract reconciliation (M7.9), and 1.0 candidate contract freeze (M8.1).
  - **1.0.0-RC1 (Published Prerelease / Release Candidate)**: Published to Maven Central and GitHub Releases on 2026-09-24; the RC was superseded by GA two days later.
  - **1.0.0 GA**: General Availability release locking permanent SemVer binary backwards compatibility following RC soak (published 2026-09-26, commit b951021; patch release 1.0.1 on 2026-09-28, commit a6d96e3).

---

## Release Phase 1.1.x — Static Specialization & Typed Contracts

Release Phase 1.1.x evolves Viet Template with optional compile-time typed template contracts, jte-inspired static specialization, and generated typed Java APIs, while strictly preserving full dynamic Velocity-compatible behavior and zero-overhead defaults.

### Completed Milestones

- **Milestone M24 (Optional Typed Template Contracts, Static Specialization, and Generated Typed Java APIs — target 1.1.0) — COMPLETE**:
    - **M24.1 (Public Contract Model & Adapter Foundation)**: Introduced immutable, deterministic `TemplateContract` and `TemplateParameter` in `viet-template-api` with zero internal compiler dependencies. Implemented bidirectional adaptation to internal `ModelSchema` and SHA-256 canonical fingerprinting.
    - **M24.2 (Zero-Allocation Single-Variable RenderContext)**: Introduced `SingleVariableRenderContext` in `viet-template-api` enabling zero-allocation single-variable rendering for high-frequency microservice templates.
    - **M24.3 (Security Hardening & Member Resolution Policy)**: Hardened `MemberResolver` in `viet-template-language-vtl` with `DENIED_METHODS` and `DENIED_CLASSES`, preventing compile-time bypass of class/method access restrictions (`Object.getClass()`, `ClassLoader`, `Process`, `Runtime`).
    - **M24.4 (Static Specialization in Bytecode Compiler)**: Overhauled `BytecodeTemplateCompiler` with null-safe and dynamic-fallback bytecode emission for `compileGetProperty` and `compileInvokeAllowedMethod`. Direct property reads emit `checkcast` and `invokevirtual`/`invokeinterface` with null jump labels and dynamic call-site fallback on type mismatch.
    - **M24.5 (Generated Typed Java API Facade & Build Tooling Parity)**: Implemented `TypedTemplateFacadeGenerator` producing zero-dependency static Java renderer facades (`XxxTemplate.render(output, ...)`). Added contract manifest discovery and typed facade compilation options across `viet-template-maven-plugin` and `viet-template-gradle-plugin`.
    - **M24.6 (Compatibility & Architecture Freeze)**: Verified 100% test parity across all modules, zero breaking changes against 1.0.0 API/ABI baselines, 0 unclassified types, and documented architectural parity.
- **Milestone M25 (Multi-Argument Static Invocation, Runtime Divergence Guards, Single-Evaluation Guarantees, and Typed Root-Slot Evaluation — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M25.1 (Deterministic Multi-Argument Method Resolution & Dynamic Overload Parity)**: Extended `MethodResolver` with deterministic overload resolution across exact reference matches, boxed/unboxed types, and strict primitive widening. When candidate sets have multiple same-name same-arity overloads, compilation preserves dynamic dispatch (`IrDynamicDispatch`) to guarantee 100% semantic parity with runtime `DynamicLinker` across all JVMs.
    - **M25.2 (Guarded Multi-Argument Direct Bytecode Invocation)**: Implemented guarded direct `invokevirtual` and `invokeinterface` emission in `BytecodeTemplateCompiler`. Verified runtime guards for receiver type, parameter types, primitive boxing/widening, and null safety.
    - **M25.3 (Single-Evaluation Invariant Guarantee)**: Structured IR lowering and bytecode generation so receiver and argument expressions are evaluated exactly once into local scratch slots before null checks or type guards are executed. Side effects never repeat when falling back to dynamic dispatch or encountering null receivers.
    - **M25.4 (Scratch-Slot Register Allocator)**: Implemented deterministic scratch-slot allocation assigning 1 local variable slot per reference register (`astore`/`aload`), with category-2 values (`long`/`double`) processed on the operand stack during argument evaluation, preventing local slot collisions with foreach, macro, or temporary frames.
    - **M25.5 (Security Policy Parity & Cache Isolation)**: Integrated `OptimizationContext` and `LinkerAccessPolicy` in `DirectAccessorBindingPass`. Restricted members fail closed at compile-time and runtime. Security policy fingerprints are bound to cache keys, preventing cross-policy cache reuse.
    - **M25.6 (Empirical Allocation & Dispatch Reduction)**: Created dedicated JMH suite `TypedSpecializationBenchmark` demonstrating 2.6× to 4.1× throughput speedup and 68.8% allocation reduction (384.85 B/op to 120.00 B/op) across multi-argument methods.
    - **M25.7 (Evidence-Driven Root-Slot ABI Decision — REJECTED AFTER PROFILING)**: Profiled `RenderContext.of(...)` lookup overhead (8.52M ops/s) versus retained context (8.32M ops/s). Overhead was proven negligible (<2.5%); introducing array-based root-slot ABIs (`Object[] roots`) would increase carrier allocation without measurable benefit. The root-slot optimization prototype was rejected after profiling, preserving stable public API contracts.
    - **M25.8 (Full Qualification & Zero Regression)**: Verified 100% differential compatibility against Apache Velocity 2.4.1 (301 scenarios), full Maven/Gradle tooling lifecycle, Spring Boot 3/4, Spring Security, Quarkus, GraalVM Native Image, and all 7 architectural verifiers.
- **Milestone M26 (Strict Typed Mode, Nullable Navigation Analysis, Build-Time Contract Diagnostics, and Maven/Gradle Parity — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M26.1 (Opt-In Strict Contract Validation & TypeCheckingMode)**: Introduced `TypeCheckingMode` (`OFF`, `WARN`, `ERROR`) in `viet-template-api` as a frozen `STABLE_API` enum governing static semantic validation and build-time failure semantics. Defaults to `OFF` to maintain complete backward-compatible dynamic execution for unannotated templates.
    - **M26.2 (Sound Semantic Analysis & Proven Invalid Invariant)**: Enforced the core guiding principle: *Reject what can be proven wrong. Do not reject merely because the compiler cannot prove something right.* Distinguishes `PROVEN_VALID`, `PROVEN_INVALID`, and `UNKNOWN / DYNAMIC`. Statically ambiguous expressions and dynamic values never fail compilation.
    - **M26.3 (Comprehensive Diagnostic Taxonomy & Stable Codes)**: Emitted canonical diagnostic codes from the stable diagnostic baseline: `VTLS:2101` (`UNRESOLVED_ROOT`), `VTLS:2102` (`INVALID_ASSIGNMENT`), `VTLS:2103` (`TYPE_MISMATCH`), `VTLS:2104` (`PROPERTY_NOT_FOUND`), `VTLS:2105` (`METHOD_NOT_FOUND`), `VTLS:2107` (`NULLABLE_DEREFERENCE`), and `VTLSEC:2401` (`SECURITY_DENIED`). Integrated Levenshtein distance typo suggestions for properties and methods.
    - **M26.4 (Conservative Nullable Navigation Analysis & Flow Refinement)**: Flagged nullable receivers (`nullable=true`) dereferenced without quiet reference syntax as advisory `VTLS:2107` warnings under `WARN` and `ERROR`. Quiet references (`$!user.name`) suppress warnings. Branch conditions (`#if($user)` or `#if($user != null)`) refine nullability to non-null within the active branch.
    - **M26.5 (Security Policy Precedence & Leak Protection)**: Enforced strict precedence of `VTLSEC:2401 (SECURITY_DENIED)` over property/method not found diagnostics. Denied members are excluded from typo suggestion candidate sets to prevent information leakage.
    - **M26.6 (Build-Tooling Parity & Incremental Cache Tracking)**: Exposed `typeChecking` across Maven (`VietTemplateCompileMojo`, `VietTemplateGenerateFacadesMojo`) and Gradle (`VietTemplateCompileTask`, `VietTemplateGenerateFacadesTask`, `VietTemplateExtension`). Integrated mode into compilation request fingerprints to ensure incremental cache invalidation upon configuration change.
- **Milestone M27 (Canonical Tooling Schema Foundation, Deterministic JSON, and Maven/Gradle Parity — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M27.1 (Canonical Serialized Tooling Contract Schema `*.vt-schema.json`)**: Established language-neutral JSON contract schema specification (`format = viet-template-contract-schema/1`, `schemaVersion = 1`, `$schema = https://viet-template.github.io/schemas/contract-v1.json`) derived from public `TemplateContract` / `TemplateType` models.
    - **M27.2 (Complete Type System Fidelity)**: Mapped primitive types, reference classes, parameterized generic collections, single- and multi-dimensional arrays, wildcards with bounds (`extends`, `super`), and named type variables into discriminating JSON AST.
    - **M27.3 (Bounded Member Discovery & Cycle Protection)**: Extracted public record components and JavaBean properties with deterministic sorting. Implemented cycle detection for recursive graphs (`Node -> Node`, `Parent -> Child -> Parent`) with bounded depth (max 32).
    - **M27.4 (Security Policy Invariant at Discovery Time)**: Applied `MemberAccessPolicy.standard()` at member discovery time prior to serialization, blocking reflection, `getClass()`, `ClassLoader`, `Runtime`, `Process`, and engine internals while preserving valid business getters.
    - **M27.5 (Deterministic Zero-Dependency Serializer)**: Implemented UTF-8 LF 2-space indented serializer with canonical key order and zero third-party dependencies, guaranteeing 100% reproducible byte-for-byte outputs.
    - **M27.6 (Build Tool Parity)**: Implemented Maven `generate-schemas` goal (`VietTemplateGenerateSchemasMojo`) and Gradle `generateVietTemplateSchemas` task (`VietTemplateGenerateSchemasTask`), proving byte-for-byte SHA-256 equivalence.
- **Milestone M28 (TypeScript Declaration Projection (.d.ts) and Build Parity — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M28.1 (TypeScript Declaration Projection Specification)**: Established `docs/schema/typescript-projection-v1.md` defining deterministic projection rules from M27 schema (`*.vt-schema.json`) to TypeScript declarations (`*.d.ts`).
    - **M28.2 (Zero-Dependency Projector Engine)**: Implemented `TypeScriptDeclarationProjector` with a zero-dependency JSON parser, strict envelope validation, canonical header comment, reserved keyword and prototype collision escaping, and LF/2-space formatting.
    - **M28.3 (Type System Mapping Fidelity)**: Deterministically projected primitive types, reference classes, parameterized generic collections, single- and multi-dimensional arrays, wildcards, and nullable parameters to TypeScript types (`string`, `number`, `boolean`, `any`, `Record<string, V>`, `T[]`, `T | null`).
    - **M28.4 (Maven Plugin Integration)**: Implemented `VietTemplateGenerateTypeScriptMojo` (`generate-typescript` goal) running in `process-classes` phase consuming generated schemas.
    - **M28.5 (Gradle Plugin Integration)**: Implemented `@CacheableTask` `VietTemplateGenerateTypeScriptTask` (`generateVietTemplateTypeScript` task) wired to `classes` lifecycle with `typeScriptOutputDirectory` configuration in `VietTemplateExtension`.
    - **M28.6 (Build Tool Parity Verification)**: Verified byte-for-byte and SHA-256 digest equivalence between Maven and Gradle TypeScript outputs on identical schema inputs.
- **Milestone M29 (Language Server Protocol / Developer Tooling Foundation — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M29.1 (LSP Specification & Lifecycle)**: Established `docs/tooling/language-server-foundation.md` documenting JSON-RPC 2.0 streaming transport, full lifecycle handling (`initialize`, `initialized`, `shutdown`, `exit`), and fail-closed state machines.
    - **M29.2 (Document Management & Offset-Range Mapping)**: Implemented `TemplateDocument` and `TemplateDocumentStore` supporting `didOpen`, `didChange`, and `didClose` with UTF-16 position and range coordinate mapping across LF, CRLF, and CR line endings, including multi-byte Unicode and surrogate pairs.
    - **M29.3 (Language Features Engine)**: Implemented `CompletionProvider` (directives, root parameters, member property chains), `HoverProvider` (type signatures, documentation, nullability), `DefinitionProvider` (in-template locals and schema definitions), and `DiagnosticProvider` (syntax errors, unresolved variables, property checks, nullable dereference warnings, security access violations).
    - **M29.4 (Contract Schema Integration)**: Integrated `CanonicalSchemaResolver` for discovering sibling and fallback `*.vt-schema.json` files, supporting both Map and List schema formats and recursive models.
    - **M29.5 (Security & Native Image Parity)**: Applied strict `MemberAccessPolicy` filtering in editor analysis; built exclusively with standard Java 21 library with zero external dependencies, supporting instant startup under GraalVM Native Image.
    - **M29.6 (Version Ordering & Stale Update Rejection)**: Implemented atomic monotonic document versioning via `TemplateDocumentStore.updateIfNewer()` with `ConcurrentHashMap.compute()`. Stale updates (`version < currentVersion`) are rejected without overwriting newer state or publishing stale diagnostics across the full lifecycle (`open v1 -> change v2 -> stale change v1 [rejected] -> change v3 -> close -> reopen v1`).
    - **M29.7 (Unicode Surrogate Pairs & Coordinate Boundary Safety)**: Hardened UTF-16 code unit position/range conversions across ASCII, Vietnamese diacritics, supplementary Unicode surrogate pairs (emojis), CRLF vs LF line endings, and safe boundary clamping.
    - **M29.8 (Diagnostic Lifecycle & Cleared Diagnostics)**: Guaranteed instant publication of empty diagnostics array (`[]`) to clear problem markers in the client editor upon repair or document close.
    - **M29.9 (Language Features Resilient Error Recovery)**: Protected completion, hover, and definition providers with AST `isKnown()` guards and graceful error recovery on malformed templates.
    - **M29.10 (Cancellation & Notification Handling)**: Implemented clean handling of standard `$/cancelRequest` and unknown notifications without returning protocol errors.
- **Milestone M30 (Visual Studio Code Editor Integration / Language Client Foundation — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M30.1 (Extension Architecture & Monorepo Isolation)**: Established `editors/vscode` maintaining strict separation from core Java builds with zero Node.js build dependencies required for standard Maven/Gradle execution.
    - **M30.2 (File Associations & Language Configuration)**: Registered `viet-template` language for `.vtl`, `.vm`, and `.vt` files with comprehensive language configuration covering comment toggles, brackets, auto-closing/surrounding pairs, and block directive indentation rules.
    - **M30.3 (TextMate Lexical Syntax Highlighting)**: Implemented `syntaxes/viet-template.tmLanguage.json` covering directives, silent/formal references, strings, numbers, operators, and comments.
    - **M30.4 (Language Client & Server Launch Model)**: Integrated `vscode-languageclient` over standard I/O (stdio) supporting bundled server JAR (`server/viet-template-lsp.jar`), custom user JAR path, and development classpath fallback.
    - **M30.5 (Java Runtime Discovery & Validation)**: Implemented discovery hierarchy (`vietTemplate.java.home` -> `JAVA_HOME` -> `PATH`) with Java 21+ validation and actionable diagnostics.
    - **M30.6 (Lifecycle, Diagnostics & Commands)**: Wired `vietTemplate.restartServer` command, document synchronization, and real-time diagnostic presentation and clearing.
    - **M30.7 (End-to-End Testing & VSIX Packaging)**: Built comprehensive test suites (unit tests and E2E LSP smoke tests driving the real Java language server process over stdio) and qualified `.vsix` offline packaging via `@vscode/vsce`.
    - **M30.8 (Governance & Path Safety)**: Implemented `scripts/verify-vscode-extension.py` enforcing manifest integrity, lockfile presence, grammar validity, and zero machine-specific path assumptions.
- **Milestone M31 (IntelliJ IDEA Editor Integration / Language Client Foundation — target 1.1.0) — COMPLETE / QUALIFIED**:
    - **M31.1 (Plugin Architecture & Monorepo Isolation)**: Established `editors/intellij` maintaining strict separation from root Maven and Gradle reactor builds, eliminating any IntelliJ SDK requirements for standard Java compilation.
    - **M31.2 (File Type Registration & Association)**: Registered `VietTemplateFileType` for `.vtl`, `.vm`, and `.vt` file extensions with dedicated vector icons.
    - **M31.3 (Lexical Syntax Highlighting & Tokenizing)**: Implemented native IntelliJ lexer (`VietTemplateLexer`) and syntax highlighter (`VietTemplateSyntaxHighlighter`) covering directives, silent/formal references, strings, numbers, operators, and comments.
    - **M31.4 (Commenter Integration)**: Contributed `VietTemplateCommenter` supporting line comments (`##`) and block comments (`#* *#`) with native IntelliJ comment action integration.
    - **M31.5 (Settings & Java Runtime Discovery)**: Implemented persistent configuration (`VietTemplateSettings`, `VietTemplateConfigurable`) and deterministic Java 21+ discovery hierarchy (`Java Home Path` -> `JAVA_HOME` -> `PATH`).
    - **M31.6 (Language Client & Project Lifecycle Management)**: Built stdio JSON-RPC 2.0 language client with background stderr draining, project-level lifecycle service (`VietTemplateLspServerManager`), and leak-free process termination.
    - **M31.7 (LSP Extension Point Adapters)**: Connected language server diagnostics to `ExternalAnnotator`, autocompletion to `CompletionContributor`, hover documentation to `DocumentationProvider`, definition navigation to `GotoDeclarationHandler`, and added `Restart Viet Template Language Server` menu action.
    - **M31.8 (Automated Testing & Distribution Packaging)**: Implemented 29 automated tests (unit tests, platform tests, and real LSP integration tests driving `VietTemplateLanguageServer` over stdio), bundled `server/viet-template-lsp.jar`, and qualified plugin ZIP packaging.
    - **M31.9 (Governance & Path Safety)**: Implemented `scripts/verify-intellij-plugin.py` enforcing plugin descriptor integrity, build configuration correctness, distribution ZIP structure, and zero machine-specific path assumptions.
- **Milestone M32 (Build-Time Template Validation — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M32.1 (Shared Core Validation Engine)**: Introduced `TemplateValidator`, `TemplateValidationRequest`, and `TemplateValidationResult` in `viet-template-vtl-interpreter` (`io.github.minh124199.viettemplate.validation`). Executes purely in-memory syntax parsing, contract semantic typing analysis, and static `#parse`/`#include` dependency validation without template execution or runtime rendering.
    - **M32.2 (Maven Plugin Validation Goal)**: Implemented `viet-template:validate` (`VietTemplateValidateMojo`) bound to the `validate` lifecycle phase, supporting `failOnWarning`, `validateDependencies`, contract schemas, profile selection, and configurable includes/excludes.
    - **M32.3 (Gradle Plugin Validation Task)**: Implemented `validateVietTemplates` (`VietTemplateValidateTask`) registered under the `verification` task group and wired into Gradle's standard lifecycle `check` task, providing identical configuration options and error semantics.
    - **M32.4 (Tooling Parity & Verification)**: Guaranteed 100% feature and diagnostic parity across Maven and Gradle validation tooling, verified via comprehensive parity tests (`VietTemplateMavenGradleParityTest`).
- **Milestone M33 (Compiler Explanation Tooling — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M33.1 (Authoritative Decision Extraction)**: Extracted write output specialization decision logic (`OutputSpecializationDecider`, `WriteDispatchDecision`, `WriteDispatchKind`, `OutputSpecializationContext`) into shared compiler structures, ensuring identical decision paths across `BytecodeTemplateCompiler` and explanation tooling.
    - **M33.2 (Shared Explanation Core Engine)**: Implemented `TemplateExplainer`, `TemplateExplainRequest`, `SingleTemplateExplanation`, `ExpressionExplanation`, and deterministic JSON/text formatters in `io.github.minh124199.viettemplate.explanation` (`viet-template-vtl-interpreter`). Exposes compiler truth for types, member access planning, AOT capability, and output dispatch without guessing or creating a secondary analyzer.
    - **M33.3 (Maven Plugin Explain Goal)**: Implemented `viet-template:explain` (`VietTemplateExplainMojo`), supporting human-readable CLI logging, file output, JSON/text formats, template/line/column filtering, and `failOnDynamicFallback`.
    - **M33.4 (Gradle Plugin Explain Task)**: Implemented `explainVietTemplates` (`VietTemplateExplainTask`) registered under the `help` task group with full configuration cache compatibility and identical CLI/format parameters.
    - **M33.5 (Tooling Parity & Governance)**: Verified 100% structured explanation decision parity between Maven and Gradle plugins via `VietTemplateMavenGradleParityTest`. Enforced public surface classifications and verified zero signature leaks across all API baselines.
- **Milestone M34 (Velocity Migration Report Tooling — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M34.1 (Shared Core Migration Analysis Engine)**: Implemented `TemplateMigrationAnalyzer`, `TemplateMigrationRequest`, `MigrationReport`, `MigrationFinding`, `MigrationSummary`, and canonical `MigrationRuleRegistry` in `viet-template-vtl-interpreter` (`io.github.minh124199.viettemplate.migration`). Grounded strictly in documented and tested compatibility facts (`migration finding == documented and tested compatibility fact`), mapping directly to differential TCK evidence scenarios without heuristic guesswork.
    - **M34.2 (Finding & Severity Taxonomy)**: Established 4-tier severity model (`INFO`, `WARNING`, `ERROR`, `BLOCKER`), 7 compatibility classifications (`EXACT_COMPATIBLE`, `COMPATIBLE_WITH_CONFIGURATION`, `KNOWN_BEHAVIOR_DIFFERENCE`, `DYNAMICALLY_UNVERIFIABLE`, `SECURITY_RESTRICTED`, `VIET_TEMPLATE_EXTENSION`, `UNSUPPORTED`), and 4-state readiness status (`READY`, `READY_WITH_WARNINGS`, `ATTENTION_REQUIRED`, `BLOCKED`).
    - **M34.3 (Deterministic Text and JSON Formatters)**: Implemented machine-readable JSON format (`formatVersion = 1`) and human-readable text presentation using logical template IDs, sorted deterministically by template, source span, severity, category, and rule ID.
    - **M34.4 (Maven Plugin Migration Report Goal)**: Implemented `viet-template:migration-report` (`VietTemplateMigrationReportMojo`), supporting direct CLI execution, JSON/text output formats, file emission, `failOnBlocker`, `failOnWarning`, and strict reference profiling.
    - **M34.5 (Gradle Plugin Migration Report Task)**: Implemented `migrationReport` (`VietTemplateMigrationReportTask`) registered under the `help` group, ensuring non-cacheable informational execution without interfering with standard lifecycle `check` or `build`.
    - **M34.6 (Tooling Parity & Differential Oracle Qualification)**: Verified 100% byte-for-byte and finding-for-finding parity between Maven and Gradle plugins via `VietTemplateMavenGradleParityTest`. Verified live differential comparison against Apache Velocity 2.4.1 in `viet-template-tck` (`VelocityMigrationDifferentialTest`), confirming 0 false warnings on exact parity and precise rule triggering across all compatibility difference cases.

---

- **Milestone M35 (Cross-Language Schema Interoperability — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M35.1 (Canonical Schema Model & Zero-Dependency Normalization)**: Introduced `CanonicalSchemaModel` in `io.github.minh124199.viettemplate.schema` (`viet-template-vtl-interpreter`) defining a rich, sealed `TypeRef` hierarchy (`PrimitiveTypeRef`, `ClassTypeRef`, `NamedTypeRef`, `ArrayTypeRef`, `MapTypeRef`, `EnumTypeRef`, `UnionTypeRef`, `WildcardTypeRef`, `ParameterizedTypeRef`, `DynamicTypeRef`) and `CanonicalSchema`. Enforced the core architectural invariant: every external schema format normalizes into one canonical schema model before downstream tooling consumes it.
    - **M35.2 (JSON Schema Importer)**: Implemented zero-dependency, offline JSON Schema Draft 7 / 2020-12 importer (`JsonSchemaImporter`) tracking 1-based line/column positions, resolving local `$defs`/`definitions` with cycle bounding, distinguishing all 4 nullability/optionality states, and rejecting remote network references offline (`JSON_SCHEMA_UNSUPPORTED_REMOTE_REF`).
    - **M35.3 (TypeScript Declaration Importer)**: Implemented handcrafted lexer and recursive descent parser (`TypeScriptSchemaImporter`) for TypeScript declaration subsets (`interface`, `type`, `enum`, literal unions, arrays, maps, nested objects, recursive references) without Node.js, npm, or JS execution, emitting structured diagnostics for unsupported language constructs.
    - **M35.4 (Java Model & Companion Contract Normalization)**: Implemented `JavaModelSchemaImporter` introspecting Java records, JavaBeans, interfaces, enums, collections, maps, and companion contracts using class metadata and member access policies safely without executing static initializers.
    - **M35.5 (Multi-Format Schema Resolver & Tooling Integration)**: Implemented `CanonicalSchemaResolver` auto-discovering companion schemas (`*.vt-schema.json`, `*.schema.json`, `*.d.ts`, `*.contract`). Integrated canonical schemas into M32 template validation (`TemplateValidator`), M33 compiler explanation (`TemplateExplainer`), and language server (LSP) tooling with complete semantic parity.
    - **M35.6 (Shape-Only Semantic Typing & AOT Guard)**: Extended `ModelSchema` and `MemberResolver` to resolve shape-only properties from imported external schemas with Levenshtein typo suggestions. Guaranteed compiler safety: shape-only properties lacking JVM bytecode members automatically configure dynamic member resolution, preventing incorrect static bytecode getter generation while preserving strict compile-time verification.
    - **M35.7 (TypeScript Declaration Projection & Build Tooling Parity)**: Extended `TypeScriptDeclarationProjector` to project canonical schemas into deterministic `.d.ts` declaration files. Updated Maven (`VietTemplateGenerateTypeScriptMojo`) and Gradle (`VietTemplateGenerateTypeScriptTask`) plugins to discover and project both `*.vt-schema.json` and `*.schema.json`, verified with 100% build tool parity (`VietTemplateMavenGradleParityTest`).

- **Milestone M36 (Schema-Aware LSP & Cross-Language Navigation — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M36.1 (Canonical Schema Model Consumption in Language Server)**: Unified `CompletionProvider`, `HoverProvider`, `DefinitionProvider`, and `DiagnosticProvider` to consume the universal canonical schema model (`CanonicalSchemaResolver` and `CanonicalSchemaModel`) directly. Deleted legacy duplicate LSP schema classes, establishing zero duplication between compiler schema models and editor language services. Delivered identical completion, hover, definition, and typo-tolerant diagnostics (`VTLS:2104` with Levenshtein suggestions) across Java models, TemplateContract, JSON Schema, and TypeScript `.d.ts`.
    - **M36.2 (Cross-Language Navigation & Provenance)**: Implemented exact 1-based line/column definition navigation from template references into source definitions across `.d.ts`, `.schema.json`, `.vt-schema.json`, and `.contract`. Provided graceful fallback for JVM reflection where source is absent (`definition unavailable`), never inventing false locations.
    - **M36.3 (Incremental Workspace Schema Indexing & Lifecycle)**: Added `WorkspaceSchemaIndex` with dependency tracking between templates and schemas. Supports fine-grained file watching (`workspace/didChangeWatchedFiles`) for `.schema.json`, `.d.ts`, and `.contract`: invalidates affected cache entries and republishes diagnostics automatically without requiring server restarts.
- **Milestone M37 (1.2.0 Release Qualification & Packaging Alignment — target 1.2.0) — COMPLETE / QUALIFIED**:
    - **M37.1 (Release Packaging & Metadata Verification)**: Transitioned all root, child module POMs, and Gradle build definitions from snapshot development to official 1.2.0 release. Verified exact publication topology of 14 public coordinates with zero SNAPSHOT leaks.
    - **M37.2 (Editor Extension & Integration Parity)**: Aligned VS Code extension manifest (`package.json`) and IntelliJ plugin configuration (`build.gradle.kts`, `plugin.xml`) to 1.2.0.
    - **M37.3 (Differential & Migration Oracle Convergence)**: Verified migration analyzer target version synchronization and differential compatibility qualification across all integration tests.

---

## Release Phase 1.2.x — Maintenance & Patch-Line Policy

Following the General Availability release of Viet Template 1.2.0 (published 2026-10-07), the `1.2.x` release series transitions into a strict maintenance and patch-line status.

### 1.2.x Patch-Line Scope & Invariants

- **Maintenance Mandate**:
  - The `1.2.x` line is strictly reserved for maintenance, security, and stability remediations.
  - Allowed changes: critical bug fixes, security remediations (CVEs), external compatibility fixes (JVM, Spring, Quarkus, Maven, Gradle), documentation corrections, packaging/release engineering fixes, and fixes for unintended performance regressions.
  - Prohibited changes: no new public Java API types or method signatures; no new compiler or template language features; no new external schema formats; no new LSP protocol features or editor capabilities; no changes to runtime evaluation, member resolution, or scoping semantics.
- **Maintenance Engineering Workflow**:
  - Patches (`1.2.1`, `1.2.2`, etc.) are maintained and released exclusively from branch `release/1.2`, created on demand from tag `v1.2.0`.
  - Every patch applied to `release/1.2` is immediately reconciled (cherry-picked or merged) into `main` to prevent regressions in active development.
  - All patch releases are validated against the canonical 1.0.0 and 1.2.0 compatibility baselines.
  - Active feature development moves forward exclusively on `main` under the `1.3.0` development line.

---

## Release Phase 1.3.x — Cross-Language Developer Navigation & Workspace Intelligence

Release Phase 1.3.x builds on the foundation established by 1.1.0 (typed contracts, static specialization, schema foundation) and 1.2.0 (cross-language schema interoperability, schema-aware LSP, validation/explanation tooling) to deliver deep, bidirectional developer intelligence between templates and workspace Java source code.

### Candidate Feature Families Evaluation & Strategic Selection

To determine the architectural priorities for 1.3, five candidate feature families were evaluated against the standard project governance rubric:

| Evaluation Dimension | Weight | Description |
|---|---|---|
| **Developer Ergonomics & Adoption Impact** | 30% | Direct productivity improvement, elimination of black-box barriers between templates and Java code. |
| **Architectural Layering & Clean Separation** | 25% | Unidirectional dependency flow, zero contamination of compiler bytecode generation or runtime execution paths. |
| **Zero-Dependency & Footprint Invariants** | 20% | Adherence to zero-dependency core engine, low memory overhead, virtual-thread safety, sub-millisecond editor response. |
| **Implementation Risk & Refactoring Safety** | 15% | Isolation from compiler core paths, avoidance of brittle heuristics, preservation of fail-closed security. |
| **1.3 Strategic Coherence** | 10% | Natural culmination of M35/M36 schema integration, completing the cross-language developer loop. |

#### Evaluation of Candidate Families

1. **Family A: Java Source Navigation & Refactoring (SELECTED — Primary Theme)**:
   - *Scope*: Direct navigation from template member references to workspace Java source declarations (`.java`: record components, getters, fields), cross-language find-references, and safe cross-language rename.
   - *Rubric Scoring*: High adoption impact (5/5); exceptional architectural layering via read-only LSP consumption of existing compiler binding facts (5/5); zero runtime dependency footprint (5/5); manageable implementation risk bounded by AST visitor pattern without altering compiler lowering (4/5); perfect strategic coherence with 1.2 schema navigation (5/5).
   - *Verdict*: **SELECTED as the foundational core of Release Phase 1.3**.

2. **Family B: Schema Composition & Registry (DEFERRED / LOCAL ONLY)**:
   - *Scope*: Multi-schema composition (`allOf`, union schemas, remote schema catalog fetching, centralized registry integration).
   - *Rubric Scoring*: Moderate impact (3/5); external network fetching introduces significant complexity and violates offline-first, hermetic build invariants (2/5); composition partially solved in M35 (3/5).
   - *Verdict*: **DEFERRED**. Remote registries rejected. Local workspace schema indexing and cross-file search retained within M41.

3. **Family C: Runtime Observability & Distributed Tracing (DEFERRED)**:
   - *Scope*: OpenTelemetry distributed tracing spans per template/directive, detailed Micrometer render metrics, runtime profiling hooks.
   - *Rubric Scoring*: Moderate developer impact (3/5); severe risk to steady-state rendering zero-allocation and performance guarantees established in M19.3b/M19.3c (1/5); introduces external API coupling or heavy SPI churn (2/5).
   - *Verdict*: **DEFERRED**. Steady-state rendering is frozen; runtime performance invariants take precedence.

4. **Family D: Further Typed AOT Specialization (DISQUALIFIED / DEFERRED)**:
   - *Scope*: Polymorphic call-site inline specialization, primitive unboxing in AOT bytecode, speculative devirtualization.
   - *Rubric Scoring*: Marginal performance gain as M24/M25 already specialized multi-argument methods and property accessors, and JMH profiling proved property dispatch represents <1.5% of CPU time (2/5); high risk of classfile verification issues, native image regressions, and generated ABI instability (2/5).
   - *Verdict*: **DISQUALIFIED / DEFERRED**. Fails the M19.3 empirical threshold of $\ge 5\%$ steady-state hotspot evidence.

5. **Family E: Editor Productization & Visual Tooling (INTEGRATED AT FOUNDATION)**:
   - *Scope*: Embedded webview live previews, multi-theme customization, editor UI chrome.
   - *Rubric Scoring*: Moderate impact (3/5); high ongoing maintenance burden across VS Code and IntelliJ APIs without improving language ergonomics (2/5).
   - *Verdict*: Standalone UI chrome deferred; language intelligence enhancements are delivered via LSP protocol standards, empowering both existing VS Code and IntelliJ extensions uniformly.

### 1.3 Core Theme: "Cross-Language Developer Navigation & Workspace Intelligence"

The 1.3 release series establishes deep workspace intelligence across template and host language boundaries:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│               1.3 Cross-Language Developer Intelligence Stack               │
├─────────────────────────────────────────────────────────────────────────────┤
│  M41: Workspace Schema Intelligence & Symbol Search                         │
│  ├── Workspace-wide symbol query across templates, schemas, and Java models │
│  └── Multi-format schema relationship indexing                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  M40: Safe Cross-Language Rename & Refactoring                              │
│  ├── Previewable rename from template reference to Java declaration         │
│  └── Workspace-wide edit coordinate safety across .vtl, .vt-schema, .java   │
├─────────────────────────────────────────────────────────────────────────────┤
│  M39: Cross-Language Find References & Symbol Graph                         │
│  ├── Find usages of Java record components/getters across all templates     │
│  └── Bidirectional symbol dependency graph                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  M38: Java Source Navigation & Cross-Language References                    │
│  ├── Template reference -> Workspace Java source declaration (.java)        │
│  └── Read-only locator for records, getters, fields, and companion models   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Milestone Roadmap Sequence

- **Milestone M38 (Java Source Navigation & Cross-Language References — target 1.3.0)**:
  - Direct LSP `textDocument/definition` navigation from template references to workspace Java source files (`.java`).
- **Milestone M39 (Cross-Language Find References & Symbol Graph — target 1.3.0)**:
  - Semantic workspace symbol graph enabling "Find References" (`textDocument/references`) across templates for JVM members, schemas, contracts, and local variables with zero false-positive text collisions.
- **Milestone M40 (Safe Cross-Language Rename & Refactoring — target 1.3.0)**:
  - Safe rename refactoring (`textDocument/prepareRename`, `textDocument/rename`) propagating member and local variable changes across templates and companion schemas with pre-flight validation, conflict detection, and atomic `WorkspaceEdit` generation.
- **Milestone M41 (Workspace Schema Intelligence & Symbol Search — target 1.3.0)**:
  - Workspace-wide semantic symbol search (`workspace/symbol`) projecting canonical schema types, properties, contracts, JVM-backed model symbols, and template macros with multi-tier deterministic ranking and zero textual scanning. Completes the 1.3 developer navigation arc.
- **Milestone M42 (1.3.0 GA Qualification & Release — target 1.3.0) — COMPLETE / QUALIFIED**:
  - Full qualification across 5-source semantic consistency matrix, large-workspace lifecycle and concurrency stress, dual-build parity, framework integrations, editor extension packages, publication topology, and release simulation.

---

### Detailed Milestone Specification: Milestone M38

**Milestone Name**: Java Source Navigation & Cross-Language References<br>
**Target Development Line**: `1.3.0`<br>
**Status**: Completed (1.3.0)

#### 1. Goal
Enable developers editing Viet Template files (`.vtl`, `.vm`, `.vt`) in VS Code and IntelliJ IDEA to use standard "Go to Definition" (`F12` / `Ctrl+Click`) on template references backed by JVM models and navigate directly to the exact source declaration line in the corresponding workspace Java source file (`.java`).

#### 2. Scope
- **Java Record Components**:
  - Direct navigation to the record component in the record header (`record User(String name, int age)`).
  - Navigation to explicit canonical accessor methods when explicitly declared.
- **JavaBean Getter & Accessor Methods**:
  - Navigation to `getXxx()` methods.
  - Navigation to `isXxx()` boolean accessor methods.
  - Navigation to fluent/builder accessors where recognized by member access policies.
- **Public Fields**:
  - Direct navigation to public field declarations on model types.
- **Companion Contract & Interface Declarations**:
  - Navigation to interface method declarations and companion contract definitions.
- **Root Context Type Declarations**:
  - Navigation from root context variables (e.g. `$user`) to the declaring Java class or interface file.

#### 3. Architectural Layering & Invariants
- **Unidirectional Data Flow**:
  ```text
  Compiler Binding Facts (MemberResolver / ModelSchema)
                          │ (one-way read-only)
                          ▼
            Workspace Java Source Locator
                          │ (one-way read-only)
                          ▼
               LSP Location Result (URI + Range)
  ```
- **Zero Compiler Contamination**:
  - The compiler, bytecode generator, IR lowering pipeline, and runtime execution engine remain completely unaware of the Java source locator.
  - The source locator operates as a read-only consumer of compiler semantic facts.
  - Compiler decision logic, dynamic dispatch fallback, and runtime member resolution are 100% unaltered.
- **Zero Decompilation / Offline-First**:
  - Source location operates exclusively on source files present in the local workspace source directories (`src/main/java`, etc.).
  - External JDK library classes (e.g. `java.lang.String`) or external JAR classes without local workspace source gracefully return `null` / `definition unavailable`, adhering to the M36 provenance invariant (never invent synthetic or false locations).

#### 4. Explicit Non-Goals for M38
- No cross-language rename refactoring (strictly deferred to Milestone M40).
- No external JAR source downloading, Maven Central artifact fetching, or network operations.
- No classfile decompilation, bytecode disassembly, or synthetic source stub reconstruction.
- No fuzzy, phonetic, or speculative string matching; navigation requires a deterministically verified member binding from the semantic analyzer.
- No runtime execution or bytecode generation changes.

#### 5. Public API Forecast
- **Exactly 0 new `STABLE_API` or `STABLE_SPI` types**.
- All source locator and workspace indexing logic is encapsulated within internal LSP and tooling packages (e.g., `io.github.minh124199.viettemplate.lsp.source.*`), preserving the frozen 1.0.0 / 1.2.0 public API baseline.

#### 6. Risk Register

| Risk ID | Description | Severity | Likelihood | Mitigation Strategy |
|---|---|---|---|---|
| **RISK-M38-1** | **Workspace Layout Diversity**: Maven and Gradle projects may have multi-module directory trees, custom source sets, or symlinked sources. | Medium | High | Implement deterministic source root resolution probing workspace root, sibling modules, and standard conventions (`src/main/java`). |
| **RISK-M38-2** | **Source-Class Drift**: Workspace `.java` file may have uncommitted edits differing from the compiled classfile on the classpath. | Low | Medium | Navigate to physical source declarations using robust Java AST parsing with line/column coordinates; never rely on bytecode line number tables for source navigation. |
| **RISK-M38-3** | **Record Component vs Method Ambiguity**: A record may declare `componentName` and an explicit method `componentName()`. | Low | Low | Deterministically resolve to the explicit accessor method if present; otherwise resolve to the record header component declaration. |
| **RISK-M38-4** | **Editor Performance & Latency**: Full Java source parsing could cause latency spikes during definition requests. | Medium | Medium | Perform lightweight, on-demand declaration parsing restricted to candidate model source files, caching resolved symbol offsets per file SHA. |

#### 7. Test Strategy & Verification Forecast
- **Deterministic Golden Fixtures**:
  - Test suites verifying navigation coordinates across Record components, standard JavaBean getters, boolean `is` getters, public fields, and inherited interface methods.
- **Dual-Build Multi-Module Fixtures**:
  - Maven multi-module and Gradle multi-project integration fixtures validating source discovery across inter-module dependencies.
- **Negative & Fallback Test Matrix**:
  - Verifying graceful fallback (`definition unavailable`) for JDK platform classes, binary-only dependencies, and missing source files without throwing exceptions.
- **Coordinate Boundary & Encoding Safety**:
  - Verifying UTF-16 surrogate pairs, Vietnamese diacritics, and CRLF line termination handling in Java source coordinate translations.

---

### Detailed Milestone Specification: Milestone M39

**Milestone Name**: Cross-Language Find References & Workspace Symbol Graph<br>
**Target Development Line**: `1.3.0`<br>
**Status**: Completed (1.3.0)

#### 1. Goal
Provide semantic, workspace-wide "Find References" (`textDocument/references`) across Viet Template files (`.vtl`, `.vm`, `.vt`) in VS Code, IntelliJ IDEA, and any standard LSP client, indexing and locating references solely based on compiler-proven semantic binding truth with guaranteed zero false-positive text collisions.

#### 2. Scope
- **Canonical Workspace Symbol Identity**:
  - `JvmMemberSymbolKey`: Declaring binary class name, member kind (`RECORD_COMPONENT`, `GETTER`, `BOOLEAN_GETTER`, `FIELD`, `METHOD`), member name, descriptor, parameter count.
  - `SchemaMemberSymbolKey`: Schema source, canonical type name, property name.
  - `TemplateLocalSymbolKey`: Template URI, variable name, definition AST offset span.
  - `RootParameterSymbolKey`: Schema source or template URI, root parameter name.
- **Inherited vs Overridden JVM Members**:
  - Inherited members index under the declaring superclass (e.g. `BaseUser#getName()`), converging references across subclasses.
  - Overridden members index to the overriding subclass, correctly isolating overrides.
- **Record Components vs Explicit Accessors vs Fields**:
  - Distinguishes record components, JavaBean getters, boolean getters, and public fields according to compiler binding semantics.
- **Incremental Indexing & Lifecycle**:
  - Thread-safe inverted index (`WorkspaceReferenceIndex`) protected by `ReentrantReadWriteLock`.
  - Atomic per-template eviction and replacement on document open/change/close.
  - Automatic re-indexing on companion schema changes via `workspace/didChangeWatchedFiles`.
  - Resilient recovery from syntax errors without index corruption.
- **Declaration Inclusion**:
  - Honors `ReferenceContext.includeDeclaration`, querying `DefinitionProvider` to dynamically prepend declarations when requested.
- **Exact Identifier Ranges**:
  - Emits exact identifier ranges (e.g. `name` in `$customer.name`, omitting the leading `.`).

#### 3. Architectural Invariants
- **Semantic Truth Before Indexing**:
  - References are recorded only after AST parsing and semantic analysis resolve the target symbol against compiler or schema bindings.
  - Never uses textual grep, regex search, or string-based fallback.
- **Zero Public API Leaks**:
  - Exactly 0 new `STABLE_API` or `STABLE_SPI` types. All index, symbol key, and provider classes remain internal/package-private.
- **Read-Only / No Mutation**:
  - M39 does not implement rename, `prepareRename`, `WorkspaceEdit`, or file modification.

#### 4. Explicit Non-Goals for M39
- No rename refactoring (deferred to M40).
- No textual search fallback for unresolved references.
- No public workspace symbol API (deferred to M41).

---

### Detailed Milestone Specification: Milestone M40

**Milestone Name**: Safe Cross-Language Rename & Refactoring<br>
**Target Development Line**: `1.3.0`<br>
**Status**: Completed (1.3.0)

#### 1. Goal
Provide safe, previewable, and atomic rename refactoring (`textDocument/prepareRename` and `textDocument/rename`) across Viet Template files (`.vtl`, `.vm`, `.vt`) and companion schema declarations (`.d.ts`, `.contract`), ensuring zero textual grep fallback, strict conflict detection, and conservative protection of Java source boundaries.

#### 2. Scope
- **Pre-Flight Validation (`textDocument/prepareRename`)**:
  - Validates eligibility of symbol under cursor and provides exact target identifier range and placeholder name.
  - Returns `null` for unnameable symbols (JVM-backed members, dynamic receivers, security-denied members, method invocations).
- **Semantic Rename Execution (`textDocument/rename`)**:
  - Resolves cursor target to canonical `WorkspaceSymbolKey` (`TemplateLocalSymbolKey`, `SchemaMemberSymbolKey`).
  - Validates target identifier against VTL grammar syntax and reserved keywords.
  - Enforces conflict analysis: detects local variable scope collisions, loop variable shadowing, and schema property name collisions.
  - Reuses M39 `WorkspaceReferenceIndex` to locate all exact reference ranges across all workspace templates.
  - Generates atomic `WorkspaceEdit` with deterministic URI and coordinate sorting and overlapping edit prevention.
- **Conservative JVM Member Boundary**:
  - Explicitly rejects rename of JVM-backed members (`JvmMemberSymbolKey`) because Java project refactoring across external source trees is outside Viet Template's ownership.
- **Schema Format Support**:
  - Supports renaming declarations and usages for TypeScript (`.d.ts`) and Contract (`.contract`) files.
  - Rejects JSON Schema (`.schema.json`) properties with clear unsupported format explanation.
- **Editor Integration**:
  - VS Code client (`F2` / `Rename Symbol`) and IntelliJ IDEA client (`Shift+F6` / `Rename...`) verified against live server.

#### 3. Architectural Invariants
- **Semantic Resolution Before Refactoring**:
  - Rename never discovers targets or references through textual regex or string search; only compiler/schema-proven references are modified.
- **Exact Token Coordinates**:
  - Edits isolate the identifier token without modifying sigils (`$`) or member operators (`.`).
- **Zero Direct Mutation**:
  - Emits standard `WorkspaceEdit` structures for client application; never mutates files on disk directly.
- **Zero Public API Leaks**:
  - Exactly 0 new `STABLE_API` or `STABLE_SPI` types. All rename provider, edit, and exception types remain internal/package-private.

#### 4. Explicit Non-Goals for M40
- No workspace-wide symbol search (strictly deferred to Milestone M41).
- No Java compiler or Java source AST refactoring.
- No file or directory moving/renaming.
- No textual or fuzzy rename fallbacks.

---

### Detailed Milestone Specification: Milestone M41

**Milestone Name**: Workspace Schema Intelligence & Symbol Search<br>
**Target Development Line**: `1.3.0`<br>
**Status**: Completed (1.3.0)

#### 1. Goal
Provide workspace-wide semantic symbol search (`workspace/symbol`) across Viet Template-relevant types, members, schema declarations, contracts, Java-backed model symbols, and template symbols in VS Code, IntelliJ IDEA, and any standard LSP client, projecting symbols from the canonical schema and binding graph rather than textual scanning or filesystem walking. Completes the 1.3 developer navigation arc.

#### 2. Scope
- **Canonical Symbol Categories**:
  - Canonical schema types (`Class`, `Interface`, `Enum`, `Struct`) from registered Java models, TypeScript `.d.ts`, JSON Schema, and `.contract` files.
  - Schema properties and model members (`Property`, `Field`, `Method`), respecting `MemberAccessPolicy` (security-denied and untyped dynamic members excluded).
  - JVM-backed model types and member symbols with exact declaration locations via `WorkspaceJavaSourceLocator`.
  - Template macros (`#macro(name ...)` / `Function`) with exact declaration ranges extracted from template ASTs.
  - Template root parameters from registered schemas with exact declaration coordinates.
  - Template-local variables (`#set`, `#foreach`) explicitly excluded from workspace symbol search to prevent noise.
- **Deterministic Multi-Tier Matching**:
  - Deterministic ranking: Exact case-sensitive match, exact case-insensitive match, qualified exact match, simple-name prefix case-sensitive, simple-name prefix case-insensitive, qualified-name prefix, simple-name substring, qualified-name substring, camelCase match.
  - Deterministic tie-breaking: Symbol kind priority -> simple name length -> qualified name -> document URI -> start line -> start character.
  - Empty query returns empty array `[]`.
  - Deterministic search result cap (500 entries) preventing unbounded payload explosion.
- **In-Memory Thread-Safe Index**:
  - `WorkspaceSymbolIndex` protected by `ReentrantReadWriteLock`.
  - Zero query-time filesystem walks or textual file parsing.
  - Atomic incremental updates and reference-counted deduplication across schemas, Java source, and templates.
- **Editor Integration**:
  - Advertises `workspaceSymbolProvider: true` in LSP server capabilities.
  - VS Code client (`Ctrl+T` / `Cmd+T` Go to Symbol in Workspace) and IntelliJ IDEA client (`Navigate | Symbol`) verified against live server.

#### 3. Architectural Invariants
- **Canonical Projection Principle**:
  - Workspace symbol results are strictly projections of symbols Viet Template already understands semantically; zero textual grep or heuristic inference.
- **Exact Coordinates Only**:
  - Every returned symbol includes a valid, verified `LocationInfo` pointing to an exact declaration; zero fabricated positions.
- **Zero ClassLoader Leaks**:
  - Symbol entries store binary class names and string identifiers; zero long-lived strong references to application `Class<?>` instances.
- **Zero Public API Leaks**:
  - Exactly 0 new `STABLE_API` or `STABLE_SPI` types. All index, symbol information, kind, and key classes remain internal/package-private.

#### 4. Explicit Non-Goals for M41
- No fuzzy semantic inference or edit-distance matching.
- No textual grep fallback or query-time file scanning.
- No general Java AST workspace indexing independent of Viet Template models.
- No template-local variable indexing.
- No remote schema registries or dependency source downloads.
- No automatic initiation of Milestone M42 (the 1.3 developer navigation arc is complete upon M41; subsequent work requires formal milestone planning).

---

### Detailed Milestone Specification: Milestone M42

**Milestone Name**: 1.3.0 GA Qualification & Release<br>
**Target Development Line**: `1.3.0`<br>
**Status**: Completed (1.3.0)

#### 1. Goal
Execute comprehensive release qualification, packaging reconciliation, and publication verification for Viet Template 1.3.0 GA across all 14 public coordinates, verifying complete semantic consistency across 5 schema sources, large-workspace lifecycle resilience, build parity, and documentation alignment.

#### 2. Scope & Qualification Results
- **Semantic Consistency Matrix Qualification**:
  - Full cross-feature matrix validation covering the 5 canonical schema sources (Java models, companion contracts, JSON Schema, TypeScript `.d.ts`, and internal VTL AST symbols).
  - Verified uniform behavior across completion, hover, definition, diagnostics, find references, safe rename, and workspace symbol search with zero cross-source divergence.
- **Large-Workspace Lifecycle & Concurrency Stress**:
  - Validated memory safety, cache eviction, and lock fairness under heavy simulated editor workloads with rapid concurrent document mutations, watched file updates, and workspace symbol queries.
- **Dual Build Parity & Spotless Cleanliness**:
  - 100% build parity between Apache Maven (3.9.9) and Gradle (9.7.1) with identical bytecode targets, Java 21 compilation flags, and zero Spotless formatting discrepancies.
- **Framework Integrations & Native Image Verification**:
  - Validated Spring Boot (3.3.5 / 4.1.1) and Quarkus (3.39.4) integrations across JVM and GraalVM/Mandrel native compilation.
- **Editor Packages Parity**:
  - Aligned VS Code extension (`editors/vscode`) and IntelliJ IDEA plugin (`editors/intellij`) manifests and metadata to 1.3.0 with complete end-to-end integration test verification.
- **Publication Topology & Release Simulation**:
  - Reconciled publication topology for exactly 14 public coordinates with zero snapshot leaks and clean release simulation (`scripts/simulate-release.sh`).

---

## Future Milestones (Post-1.3.0 Development Line)

Following the completion and qualification of Milestone M42 and the release of Viet Template 1.3.0 GA, the next feature milestone family is undecided / pending planning. Active maintenance for the 1.3.x series is governed by the maintenance policy.


