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

1. **Compiler-Assigned Variable Slots (`EvaluationValue[] slots`)**:
   - In 0.1.x, template variable evaluation relies on `ExecutionContext` managing an `ArrayDeque<LocalScope>` containing `HashMap<String, EvaluationValue>`.
   - In 0.2.0, semantic analysis and IR optimization assign every statically declared and inferred local variable (parameters, `#set` targets, loop counters, macro parameters) to a stable compiler-assigned integer slot ID.
   - The execution frame in both the reference interpreter and AOT bytecode backend evolves to an indexed `ExecutionFrame` backed by `EvaluationValue[] slots`.
   - Variable reads and writes compile to direct array slot accesses (`ALOAD`/`AALOAD`/`AASTORE`), reducing variable resolution from an hash map lookup with string hashing and object node traversal to a direct array index operation.
   - **Preservation of 3-State Semantics**: The runtime explicitly preserves `UNDEFINED`, `DEFINED_NULL`, and `DEFINED_VALUE`. `ExecutionFrame` explicitly initializes each slot to `EvaluationValue.undefined()`. Because Java reference arrays themselves initialize to `null`, the runtime never exposes `null` or conflates it with `DEFINED_NULL`.
   - **Name-Based Fallback**: A name-based fallback is retained for variable accesses whose identity cannot safely be resolved to a static slot while preserving Velocity-compatible semantics.
   - **Slot Reuse Deferred**: Current measured workloads do not demonstrate a need for slot packing in 0.2.0, so slot reuse remains deferred.
2. **Indexed Template Cache Invalidation**:
   - In 0.1.x, `TemplateCompileCache.invalidate(TemplateId)` performs an $O(N)$ linear scan over all cache keys: `entries.keySet().removeIf(...)`.
   - In 0.2.0, `TemplateCompileCache` introduces a concurrent secondary reverse index: `ConcurrentMap<TemplateId, Set<CompileCacheKey>>`.
   - The indexed approach performs an average $O(1)$ lookup of `TemplateId` $\to$ associated key set plus $O(K)$ removal of the $K$ associated entries, reducing overall invalidation work to $O(K)$. It is never described as "instant $O(1)$ eviction", because removing $K$ entries is proportional to $K$.
   - `put` and `invalidate` for the same template serialize on a stable lock stripe. An insertion
     therefore linearizes wholly before invalidation and is removed, or wholly after invalidation
     and remains. Concurrent invalidations are idempotent. Eviction removes reverse-index and
     conditional active-key membership after releasing the LRU lock, preventing lock-order cycles.
   - The bookkeeping tradeoff is one reverse-map entry and concurrent set per indexed template,
     plus one set membership per compile key. Empty sets are removed during eviction/invalidation,
     and full reset clears the complete index.
   - Positive mutations use a fixed set of 64 template lock stripes. This bounds synchronization
     object overhead while allowing unrelated template mutations to proceed concurrently. The
     stripe count is an implementation parameter, not a performance guarantee, and should be
     revisited only if profiling justifies it.
3. **Variable Slot Assignment Optimizer Pass (`O45 AssignVariableSlots`)**:
   - Adds pass `O45` to the optimization pipeline between local constant propagation (`O40`) and dead branch elimination (`O50`).
   - Assigns stable integer slot IDs to statically resolvable variables without slot reuse, preserving explicit symbol boundaries and 3-state evaluation semantics.
4. **JMH Benchmark Verification**:
   - Measure throughput and allocation deltas on `ScalarVariableBenchmark`, `ForeachLoopBenchmark`, and `TemplateCompilationCacheBenchmark`.
   - Verify performance and allocation profiles against 0.1.x baselines under the balanced tradeoff rule.

---

## Release Phase 0.3.x+ — Evidence-Driven Optimizations

Guided strictly by JMH profiling and JFR allocation flame graphs from 0.1.x and 0.2.0 baselines, 0.3.x introduces targeted optimizations satisfying the 7-part DSA acceptance rule, the Four-Tier Implementation Preference Hierarchy, and the balanced tradeoff evaluation. Optimizations are undertaken only for components that post-0.2.0 profiling identifies as proven hotspots.

### Candidate Optimization Areas (Milestone M19.3)

1. **LRU Cache Contention Mitigation**:
   - If concurrency stress testing on `TemplateCompilationCacheBenchmark` reveals lock contention on LRU eviction queues, introduce a concurrent striped or segmented eviction structure (e.g., modern ConcurrentLinkedHashMap or TinyLFU design) while preserving bounded memory guarantees.
2. **Lexer & Parser Allocation Reductions**:
   - Evaluate zero-copy token slice representation to eliminate intermediate `String` allocations during template parsing.
   - Investigate flyweight token recycling pools for high-frequency tokens (identifiers, punctuation).
3. **Dependency Graph Concurrency Refinements**:
   - Refine read/write concurrency in `TemplateDependencyGraph` for continuous hot-reload environments with deep dependency trees.
4. **MethodHandle `invokedynamic` Prototype**:
   - Benchmark an `invokedynamic` (Indy) call-site implementation against the existing contiguous `AccessLink[]` PIC array scan.
   - Advance Indy only if it demonstrates measurable throughput wins without native-image or classloader leak penalties.
5. **Streaming Output Buffer Enhancements**:
   - Optimize `TemplateOutput` buffer pooling and primitive byte encoding (e.g., zero-allocation integer and decimal direct UTF-8 byte encoders).

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
