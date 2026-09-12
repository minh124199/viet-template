# 15 — Benchmark and Performance Engineering Plan

## 1. Core Performance Engineering Principle

Viet Template adopts a strict, maintainability-first, benchmark-driven performance engineering philosophy:

1. **Maintainability and Correctness First**: Readability, simplicity, and architectural invariants take precedence over speculative micro-optimizations. Code must never sacrifice safety, security sandboxing, or Velocity semantic parity for unverified performance claims.
2. **Benchmark-Driven Decisions**: Every optimization, data structure change, or algorithmic alteration must be justified by reproducible empirical measurements using Java Microbenchmark Harness (JMH), Java Flight Recorder (JFR), and async-profiler.
3. **Complexity vs. JVM Cost**: Theoretical asymptotic complexity ($O(1)$ vs. $O(N)$) must be evaluated against physical JVM runtime realities: CPU cache locality, pointer indirection, object header overhead, memory allocation pressure, GC write barriers, and JIT compiler inlining heuristics.
4. **Java 17 Baseline**: Design idiomatically for the modern JVM (Java 17 baseline), utilizing contiguous memory arrays, compact representations, records, sealed interfaces, and standard JDK collections before considering custom or complex alternatives.

---

## 2. The 7-Part DSA Acceptance Rule

No custom data structure, non-standard algorithm, or complex caching mechanism may be introduced into the Viet Template codebase unless it satisfies all seven acceptance gates:

1. **Baseline Measurement**: A clean JMH benchmark and profiling session must exist for the current JDK standard collection or naive baseline under both single-threaded and realistic concurrent multi-threaded workloads.
2. **Proven Hotspot**: Profiling evidence (CPU sampling via async-profiler or allocation flame graphs via JFR) must prove that the component is a dominant hotspot representing $\ge 5\%$ of execution time or allocation volume in realistic rendering scenarios.
3. **Theoretical vs. Practical JVM Cost**: The proposal must demonstrate why the JVM's hardware interactions (e.g., L1/L2 cache prefetching on contiguous arrays) do not already favor the simpler structure. An $O(N)$ array scan that hits a single 64-byte cache line is preferred over an $O(1)$ hash table that introduces pointer chasing, heap allocation, and hash calculation overhead for small $N$.
4. **Allocation and GC Impact**: The change must decrease or maintain allocation rate (`bytes/op` and `gc.alloc.rate`). A data structure that reduces CPU cycles at the expense of heavy auxiliary object allocations (e.g., map entry nodes, wrapper boxes) will be rejected.
5. **Maintenance and Complexity Budget**: The implementation must have well-defined, provable invariants, be under 300 lines of code where possible, introduce no unsafe or internal JVM hacks, and include exhaustive concurrent stress tests.
6. **Benchmark Verification**: A reproducible JMH benchmark across JDK 17, 21, and 25 must demonstrate a statistically significant improvement ($\ge 15\%$ throughput improvement or $\ge 20\%$ allocation reduction) with overlapping confidence intervals excluded.
7. **Fallback and Simplicity Clause**: If profiling or benchmark results indicate parity, marginal gains ($< 5\text{--}10\%$), or degradation under specific JVM configurations, the code must immediately revert to standard JDK collections (`ArrayDeque`, `ArrayList`, `HashMap`, `ConcurrentHashMap`) or simple arrays.

---

## 3. Java-First Design Policy (Java 17 Baseline)

Viet Template enforces a Java-first design policy targeting the Java 17 LTS baseline:

- **Zero External Collection Dependencies**: The core, runtime, and compiler modules must not depend on third-party collection libraries (e.g., FastUtil, Trove, Guava, Apache Commons Collections). Rely exclusively on optimized standard library primitives and collections.
- **Contiguous Memory and Cache Locality**: Favor flat arrays (e.g., `EvaluationValue[]`, `AccessLink[]`, `Object[]`) over node-based collections. Sequential memory access patterns leverage modern CPU hardware prefetchers and avoid cacheline misses.
- **Object Header and Pointer Minimization**: Minimize heap allocations. On 64-bit JVMs with compressed OOPs enabled, each heap object incurs a 12-byte header (padded to 16 bytes) plus reference overhead. Deep object graphs degrade GC throughput and memory bandwidth.
- **Leverage Modern JDK Capabilities**: Utilize records for immutable carrier types, sealed type hierarchies for exhaustive pattern matching, compact strings, and primitive-specialized paths to avoid boxing.
- **Idiomatic Standard Collections**:
  - Use `ArrayDeque` for LIFO stacks and FIFO queues (e.g., lexical scope stacks, AST visitor queues, graph BFS queues); never use `LinkedList` or `Stack`.
  - Use `ArrayList` with explicit initial capacity sizing when element count is estimable.
  - Use `HashMap` and `ConcurrentHashMap` with carefully chosen load factors for large, dynamic lookup tables, but avoid them for small fixed collections ($N \le 4\text{--}8$).

---

## 4. Complexity vs. JVM Execution Cost

In server-side template rendering, algorithmic analysis must be tempered by JVM execution characteristics:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CPU L1/L2 CACHE LINE (64 BYTES)                  │
│  ┌───────────────┬───────────────┬───────────────┬───────────────┐          │
│  │ Array Slot 0  │ Array Slot 1  │ Array Slot 2  │ Array Slot 3  │          │
│  └───────────────┴───────────────┴───────────────┴───────────────┘          │
│   Contiguous memory: single cacheline fetch, zero pointer chasing, zero GC  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      VS.
┌─────────────────────────────────────────────────────────────────────────────┐
│                            NODE-BASED HASH TABLE                            │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐               │
│  │ Bucket Table │ ───► │  Node Entry  │ ───► │  Key Object  │ (Cache Miss)  │
│  └──────────────┘      └──────┬───────┘      └──────────────┘               │
│                               ▼                                             │
│                        ┌──────────────┐                                     │
│                        │ Value Object │ (Cache Miss, Header, Write Barrier) │
│                        └──────────────┘                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Small $N$ Array Scanning**:
   - For collections where $N \le 4\text{--}8$ (such as Polymorphic Inline Cache links, local variable scopes in typical templates, or macro argument lists), linear scanning of a contiguous array (`AccessLink[]` or `EvaluationValue[]`) consistently outperforms `HashMap.get()`.
   - An array traversal requires zero hash code computations, zero modulo operations, and no bucket linked-list traversal. The entire array fits inside a single 64-byte L1 cache line.
2. **Compiler-Assigned Slots vs. Name Lookup**:
   - Looking up a variable by string name in a `HashMap<String, EvaluationValue>` requires computing `String.hashCode()`, resolving the hash bucket, verifying `.equals()`, and dereferencing the entry node.
   - Assigning variables to integer slots at compile time (`EvaluationValue[] slots`) reduces variable reads and writes to a single array index load (`ALOAD`/`AALOAD`), converting an expensive hash lookup into a predictable, branchless memory operation.
3. **Secondary Index vs. Full Scan**:
   - While small arrays favor linear scanning, cache invalidation across thousands of compiled templates requires true $O(1)$ indexing. Scanning all keys in a concurrent cache via `entries.keySet().removeIf(...)` is an $O(N)$ operation that holds locks or creates iterator churn. Maintaining a secondary reverse index (`TemplateId -> Set<CompileCacheKey>`) provides instant, scalable invalidation without linear scanning.

---

## 5. Dedicated Benchmark Module (`viet-template-benchmarks`)

Performance testing is isolated in a dedicated build module:

- **Module Name**: `viet-template-benchmarks`
- **Build Configuration**: Dual Gradle (`build.gradle.kts`) and Maven (`pom.xml`) parity.
- **Framework**: JMH (Java Microbenchmark Harness) pinned to the latest stable release, leveraging `jmh-generator-annprocess`.
- **Packaging**: Produces a self-contained executable benchmark JAR (`benchmarks.jar`).
- **Dependencies**: Depends strictly on `viet-template-api`, `viet-template-runtime`, `viet-template-engine`, and competitor comparator engines in test/benchmark scope.

---

## 6. Nine Benchmark Classes

The benchmark suite contains nine focused JMH benchmark classes covering every critical rendering and compilation phase:

| Class | Benchmark Name | Target Workload & Primary Focus |
|---|---|---|
| 1 | `StaticHtmlBenchmark` | Workload **B01**: 20 KB static HTML chunk rendering. Measures raw streaming throughput, zero-allocation literal writing, and buffer flushing efficiency. |
| 2 | `ScalarVariableBenchmark` | Workload **B02**: 50 scalar variable substitutions. Measures variable resolution, compiler-assigned variable slots (`EvaluationValue[] slots`), and primitive conversion. |
| 3 | `DeepPropertyChainBenchmark` | Workload **B03**: Repeated deep property navigation (`$order.customer.address.city`). Measures getter linkage, null-checking overhead, and call-site stability. |
| 4 | `ConditionalBranchBenchmark` | Workload **B04**: 100 mixed conditionals (`#if/#elseif/#else`) with varying truthiness rules. Measures branch prediction, short-circuit evaluation, and `VtlTruthiness` dispatch. |
| 5 | `ForeachLoopBenchmark` | Workloads **B05, B06, B07**: Iteration over 10-row, 1,000-row, and nested 100×10 loops across collections, arrays, and ranges. Measures loop metadata (`$foreach`), iterator allocation, and index access. |
| 6 | `EscapingBenchmark` | Workload **B08**: Escaping-heavy HTML strings and attribute contexts. Measures `Escaper` streaming throughput, SIMD-friendly scanning, and temporary string allocation avoidance. |
| 7 | `DynamicCallSitePicBenchmark` | Workloads **B09, B10**: Dynamic property resolution under monomorphic, 2-to-4 shape polymorphic (PIC), and megamorphic conditions. Measures `AccessLink[]` array scan vs. hash lookup vs. megamorphic cache. |
| 8 | `TemplateCompilationCacheBenchmark` | Workload **B15**: Parsing, analyzing, compiling, and invalidating 1,000 templates. Measures cache concurrency, lock contention, and secondary index invalidation (`TemplateId -> Set<CompileCacheKey>`). |
| 9 | `MacroAndLayoutBenchmark` | Workloads **B11, B12**: Macro-heavy rendering, block macros (`#@blockMacro`), global macro library dispatch, and two-stage layout rendering (`LayoutRenderPlan`). Measures context stacking and template recursion budgets. |

---

## 7. Comprehensive Workload Matrix

| ID | Workload | Description & Target Metrics |
|---|---|---|
| **B01** | Static HTML (20 KB) | Large static HTML with minimal directives. Target: pure buffer write speed, zero allocations. |
| **B02** | Scalar Substitutions (50 scalars) | Flat template binding 50 variables of mixed types (strings, integers, booleans). Target: slot access vs. map lookup. |
| **B03** | Deep Property Chain | Chain depth 4–5 on domain records and POJOs. Target: inline getter dispatch vs. reflection. |
| **B04** | Mixed Conditionals (100 branches) | Complex boolean logic, empty checks, numeric truthiness. Target: branch misprediction reduction. |
| **B05** | Small Table Loop (10 rows × 5 cols) | Standard web table rendering. Target: minimal per-row frame allocation. |
| **B06** | Large Table Loop (1,000 rows × 5 cols) | High-volume batch rendering. Target: maximum streaming throughput, zero GC pauses. |
| **B07** | Nested Loops (100 × 10) | Hierarchical data structures with `$foreach.parent` navigation. Target: loop scope stack management. |
| **B08** | Heavy Escaping | High density of unsafe characters (`<`, `>`, `&`, `"`, `'`). Target: streaming escape buffer efficiency. |
| **B09** | Monomorphic Property Access | Single receiver type at dynamic call site. Target: single branch class guard check. |
| **B10** | Polymorphic PIC Access (4 types) | Dynamic call site invoked with 4 distinct receiver shapes. Target: `AccessLink[]` linear scan efficiency. |
| **B11** | Partial / Include Calls (20 sub-templates) | Template composition via `#parse` and `#include`. Target: resource resolution and child context isolation. |
| **B12** | Macro-Heavy Rendering | Repeated calls to local and global macros with argument passing. Target: macro frame allocation and parameter binding. |
| **B13** | Large Report Streaming (1–5 MiB) | Massive text generation to `OutputStream`. Target: backpressure, chunk flushing, heap stability. |
| **B14** | Missing / Denied Property Failure | Stressing strict mode errors and security policy denial paths. Target: clean exception diagnostic assembly without hotspot memory leaks. |
| **B15** | Compile & Invalidate 1,000 Templates | Rapid compilation, dependency tracking, and transitive invalidation. Target: cache concurrency and zero memory leaks. |

---

## 8. Comparators and Baselines

All benchmarks compare Viet Template against current stable versions of:

1. **Handwritten Java Renderer**: Direct `StringBuilder` / `Writer` writes with direct getter calls. Represents the physical performance ceiling (100%).
2. **Apache Velocity 2.4.1**: Direct legacy migration baseline.
3. **Quarkus Qute**:
   - Compare Qute dynamic/reflection vs. Viet Template dynamic mode.
   - Compare Qute type-safe/generated resolver vs. Viet Template typed/AOT mode.
   - Never compare Viet Template typed mode solely against Qute dynamic mode.
4. **jte (Java Template Engine)**: Modern compiled template engine benchmark.
5. **Thymeleaf**: Standard enterprise Spring template engine.
6. **Mustache.java**: Lightweight logic-less template engine baseline.

---

## 9. Execution Modes and Metrics

### Engine Execution Modes

```text
1. VTL Reference Interpreter (0.1.x baseline, AST execution)
2. VTL Reference Interpreter (0.2.0 slot architecture, ExecutionFrame slots)
3. Dynamic Optimized Cold (Unlinked dynamic call sites)
4. Dynamic Optimized Warm (Linked monomorphic / PIC sites)
5. Typed / AOT Writer (Precompiled bytecode to java.io.Writer)
6. Typed / AOT UTF-8 Stream (Precompiled bytecode to OutputStream with pre-encoded UTF-8 literals)
7. Typed / AOT Auto-Escape (Full contextual HTML escaping enabled)
```

### Measured Metrics

- **Throughput**: Operations per second (`ops/s`, mode `Throughput`).
- **Latency**: Average time per operation (`ns/op` or `us/op`, mode `AverageTime`).
- **Tail Latency**: Percentiles (p50, p90, p95, p99, p99.9) using JMH sampling.
- **Allocation Rate**: Bytes allocated per operation (`bytes/op` via `-prof gc`).
- **GC Churn**: Allocation rate (`gc.alloc.rate.norm`) and GC collection time.
- **Compilation Latency**: Milliseconds per template compile.
- **Class Footprint**: Generated bytecode size in bytes per template.
- **Startup Time**: Cold initialization latency to first rendered output.

---

## 10. CI, Environment and Hygiene Rules

1. **Dedicated Benchmark Workflow**: CI runs a smoke benchmark suite on every PR, and full regression runs on scheduled nightly builds.
2. **Fixed JVM Flags**:
   ```bash
   -server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC
   ```
3. **JMH Harness Hygiene**:
   - Minimum 3 forks, 5 warmup iterations (1s each), 5 measurement iterations (1s each).
   - All results consumed via JMH `Blackhole` to prevent dead-code elimination.
   - Models populated with identical, non-trivial test datasets across all engines.
   - Identical escaping requirements and output charsets (UTF-8) enforced across all comparators.
4. **Environment Recording**: Every benchmark artifact must record:
   - Git commit SHA and branch;
   - OS name, architecture, and kernel version;
   - Exact CPU model, physical core count, and clock speed;
   - Exact JDK build (`java -version`), JVM vendor, and runtime arguments;
   - Ambient temperature / CPU throttling status if available.

---

## 11. Regression Gates and Statistical Thresholds

The following gates are enforced in continuous integration and release qualification:

- **Throughput Regression Gate**: Any change causing a $\ge 5\%$ drop in throughput on any standard benchmark (B01–B15) fails qualification unless accompanied by an approved ADR and performance note.
- **Allocation Regression Gate**: Zero allocation regressions permitted in typed/AOT rendering paths. Any increase in `bytes/op` triggers a build failure.
- **Compilation & Footprint Gate**: Generated class size must not regress by $\ge 10\%$, and template compilation latency must not regress by $\ge 10\%$.
- **Statistical Significance**: Results must be evaluated with $95\%$ confidence intervals. Overlapping error margins require increasing measurement iterations.

---

## 12. Nine-Part Performance PR Review Checklist

Every pull request introducing optimizations, changing data structures, or altering runtime execution paths must be reviewed against this 9-part checklist:

- [ ] **1. JMH Benchmark Evidence**: Does the PR include or execute against the relevant JMH benchmark suite? Are before/after raw results provided?
- [ ] **2. Allocation & GC Footprint**: Has the allocation rate (`bytes/op`) been measured using `-prof gc`? Does it avoid adding temporary object allocations?
- [ ] **3. Scalability & Contention**: Has the change been verified under multi-threaded concurrency (threads $\ge 8$) to ensure no new lock contention, CAS spinning, or false sharing?
- [ ] **4. Memory Footprint**: Does the change maintain or reduce long-term memory footprint per template and per render context?
- [ ] **5. Java 17 Baseline Idioms**: Does the code adhere to Java 17 idiomatic practices, utilizing compact flat arrays, records, and standard collections without third-party dependencies?
- [ ] **6. Avoidance of Premature Hacks**: Does the code avoid unsafe tricks, undocumented JVM intrinsics, reflection tampering, or premature micro-optimizations that harm maintainability?
- [ ] **7. Thread-Safety & Invariants**: Are all concurrency invariants, thread-safety guarantees, and immutability constraints rigorously preserved and verified with stress tests?
- [ ] **8. Tail Latency & Branch Predictability**: Are branches structured predictably for CPU branch predictors? Are expensive operations kept out of the inner loop?
- [ ] **9. Architectural Simplicity & DSA Acceptance**: Does the change satisfy all 7 parts of the DSA Acceptance Rule? If gains are marginal ($< 5\text{--}10\%$), has the simpler JDK standard structure been retained?

---

## 13. Application-Level HTTP Benchmarks

Isolated microbenchmarks are complemented by realistic application-level HTTP benchmarks:

```text
Spring Boot 4 + Viet Template (AOT Bytecode)
Spring Boot 4 + Apache Velocity 2.4.1
Spring Boot 4 + Thymeleaf
Quarkus + Qute (Type-safe)
```

- **Environment**: Containerized deployment with pinned vCPUs and memory limits.
- **Load Generators**: `wrk` / `k6` executing realistic traffic profiles with HTTP keep-alive.
- **Metrics**: Requests per second, p50/p95/p99 latency, CPU utilization, Resident Set Size (RSS), and GC pause distribution.

---

## 14. Publication and Reproducibility Standards

Every publicly released performance report must contain:
1. Exact git commit SHA and repository URL.
2. Complete source code for all templates, data models, and benchmark drivers.
3. Full hardware and cloud instance specifications.
4. Complete raw JMH JSON outputs and GC logs.
5. Exact build tool configurations (Gradle/Maven) and JVM options.
6. Explicit caveats, known limitations, and comparative fairness notes (e.g., Qute typed vs. dynamic fairness).
