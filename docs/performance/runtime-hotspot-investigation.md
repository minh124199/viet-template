# Runtime Hotspot Investigation — Viet Template Engine

## 1. Scope

Following the closure of the variable-access and output micro-optimization phases (Phase 15 lazy metadata deferral, Phase 16 cached enum-array indexing, and SlottedRenderContext static seeding), this investigation establishes a fresh, profiling-driven architectural audit of the remaining runtime performance costs in Viet Template.

The scope of this investigation includes:
1. Auditing and resolving untracked benchmark infrastructure (`DirectAccessorBenchmark.java` and `OutputMetadataHotPathBenchmark.java`).
2. Tracing the end-to-end compiled (AOT) rendering pipeline with exact source locations.
3. Capturing fresh JMH throughput baselines and GC allocation metrics across representative workloads.
4. Capturing Java Flight Recorder (JFR) CPU execution samples and allocation profiles on the current repository HEAD.
5. Evaluating whether remaining hotspots in dynamic dispatch (`DynamicCallSite`, `AccessLink`), output bridging (`BytecodeRuntimeBridge.writeValue`), escaping (`HtmlTextEscaper`), and buffer sinks (`StringTemplateOutput`) represent avoidable overhead or essential intrinsic semantics.
6. Formulating a definitive go/no-go recommendation for future performance engineering.

---

## 2. Baseline Performance Measurements

Measurements were captured using JMH 1.37 on OpenJDK 21 across all representative workloads in `TemplateComplexityBenchmark` and all direct/PIC scenarios in `DirectAccessorBenchmark`.

### 2.1 Representative Workload Suite (`TemplateComplexityBenchmark`)

Protocol: `-f 1 -wi 3 -i 5 -w 1s -r 1s -prof gc`

| Workload | Template Pattern | Throughput (NameBased) | Allocation (NameBased) | Throughput (Slotted) | Allocation (Slotted) |
|---|---|---|---|---|---|
| `SIMPLE_VARS` | `$user $name $age` | 6,220,678 ± 1,096,544 ops/s | 480.0 B/op | 6,248,462 ± 127,593 ops/s | 480.0 B/op |
| `PROPERTIES` | `$user.name $user.email $user.address.city` | 19,488,818 ± 469,790 ops/s | **0.000 B/op** | 19,903,535 ± 2,730,083 ops/s | **0.000 B/op** |
| `REPEATED_PROPERTIES` | `$user.name` × 5 | 11,968,116 ± 2,660,404 ops/s | **0.000 B/op** | 11,790,191 ± 1,023,702 ops/s | **0.000 B/op** |
| `CONDITIONALS` | `#if($user.active)...#else...#end` | 44,096,661 ± 9,200,614 ops/s | **0.000 B/op** | 44,847,347 ± 8,087,702 ops/s | **0.000 B/op** |
| `LOOPS` | `#foreach($item in $items)$item.name ($item.price) #end` | 5,480,726 ± 265,556 ops/s | 72.0 B/op | 4,853,306 ± 691,794 ops/s | 72.0 B/op |
| `NESTED_EXPR` | `#if($user.age > 18 && $user.active)...#end` | 4,769,455 ± 1,190,459 ops/s | 264.0 B/op | 4,656,390 ± 566,355 ops/s | 264.0 B/op |
| `METHOD_CALLS` | `$user.formattedName() - $user.email` | 11,482,730 ± 1,014,278 ops/s | 56.0 B/op | 11,360,235 ± 2,369,223 ops/s | 56.0 B/op |
| `MIXED_STATIC_DYNAMIC` | `$user.name \| $dynamicVal \| $unknown` | 13,858,286 ± 2,558,274 ops/s | **0.000 B/op** | 15,045,074 ± 3,080,990 ops/s | **0.000 B/op** |
| `REALISTIC_APP` | HTML User Card with conditionals & item loop | 3,452,890 ± 643,907 ops/s | 72.0 B/op | 3,467,537 ± 638,309 ops/s | 72.0 B/op |

### 2.2 Direct Accessor vs Dynamic PIC (`DirectAccessorBenchmark`)

Protocol: `-f 1 -wi 3 -i 5 -w 1s -r 1s -prof gc`

| Scenario | Dispatch Architecture | Throughput (ops/s) | Error (±) | Alloc Rate (B/op) |
|---|---|---|---|---|
| `DIRECT_GETTER` | AOT Direct `invokevirtual User.getName()` | 11,129,146 | ± 1,973,774 | 120.0 |
| `DIRECT_RECORD` | AOT Direct `invokevirtual Record.getName()` | 11,020,386 | ± 723,909 | 120.0 |
| `DIRECT_SUBTYPE` | Subtype polymorphic direct call | 10,076,829 | ± 787,218 | 120.0 |
| `DIRECT_UNEXPECTED_FALLBACK` | Runtime fallback to PIC on unexpected type | 8,772,549 | ± 221,071 | 120.0 |
| `DIRECT_NULL` | Direct null check fast-exit (`$!user.name`) | 11,192,668 | ± 1,489,915 | 64.0 |
| `DYNAMIC_MONOMORPHIC` | Dynamic PIC (1 receiver shape) | 7,564,880 | ± 376,714 | 120.0 |
| `DYNAMIC_POLYMORPHIC_2` | Dynamic PIC (2 alternating shapes) | 6,606,856 | ± 1,353,628 | 120.0 |
| `DYNAMIC_POLYMORPHIC_4` | Dynamic PIC (4 alternating shapes, max depth) | 6,049,347 | ± 2,155,116 | 120.0 |
| `DYNAMIC_MEGAMORPHIC` | Megamorphic fallback (`BoundedWeakClassCache`) | 5,270,597 | ± 841,729 | 144.0 |

Key Observations:
- Direct accessor devirtualization yields **11.1M ops/s** (+47% throughput over monomorphic dynamic PIC).
- Dynamic PIC scales gracefully: 7.56M ops/s (mono) → 6.61M (poly 2) → 6.05M (poly 4) → 5.27M (megamorphic). Even under worst-case megamorphic thrashing across 8 distinct classes, the engine delivers over 5.2M ops/s.

---

## 3. Benchmark Environment

- **JDK Version**: OpenJDK 21.0.12.1 (build 21.0.12.1+1)
- **JVM Runtime**: OpenJDK 64-Bit Server VM (mixed mode, sharing)
- **JMH Version**: 1.37
- **JVM Flags**: `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC`
- **CPU**: Intel(R) Core(TM) i5-8350U CPU @ 1.70GHz (4 physical cores, 8 threads, 6 MiB L3 cache)
- **OS**: Linux x86_64 (CachyOS / Arch Linux kernel)
- **Protocol Settings**: Warmups: 3 iterations × 1s; Measurements: 5 iterations × 1s; Forks: 1; Profilers: `gc`, `jfr`

---

## 4. Current Rendering Pipeline Architecture

For a compiled AOT template, the runtime execution flow traces as follows:

```
Template Execution Entrypoint
      ↓
[1] Parameter Seeding Prologue
    CompiledTemplate.render(RenderContext, TemplateOutput)
    ├── If Slotted: SlottedArrayRenderContext.getBySlot(slot) -> local var
    └── If NameBased: RenderContext.get(paramName) -> local var
      ↓
[2] Expression Evaluation
    ├── Static path (with TemplateContract):
    │   instanceof <Type> -> checkcast -> invokevirtual <Type>.getter()
    └── Dynamic path (no contract / dynamic variable):
        DynamicCallSite.invoke(target)
        ├── DynamicCallSite.resolveLink(targetClass)
        │   ├── State.MONOMORPHIC: mono.matches(targetClass)
        │   ├── State.POLYMORPHIC: linear scan (links[i].matches(targetClass), depth ≤ 4)
        │   └── State.MEGAMORPHIC: BoundedWeakClassCache.get(targetClass)
        └── AccessLink.isMissing() -> AccessLink.invoke(target) -> MethodHandle.invoke(target)
      ↓
[3] Output Bridging & Null Handling
    BytecodeRuntimeBridge.writeValue(val, output, escapeOrdinal, nullOrdinal, ...)
    ├── EvaluationValue unwrapping: (val instanceof EvaluationValue ev) ? ev.value() : val
    ├── Null / Undefined check: isNullOrUndef = (val == null || ev.isNull() || ev.isUndefined())
    └── Policy evaluation: NullRenderMode.EMPTY_STRING vs literal emission
      ↓
[4] Escaping & Content Security
    BytecodeRuntimeBridge.renderEscaped(unwrapped, output, escapeMode, ...)
    ├── Boxed primitive fast-paths: output.writeInt(), output.writeLong(), etc.
    ├── SafeHtml / SafeUrl passthrough verification
    ├── Security policy assertion: LinkerAccessPolicy.isClassPermitted(clazz)
    └── Escaper dispatch: ESCAPERS_BY_IR_MODE[escapeModeOrdinal].escape(cs, output)
        └── HtmlTextEscaper.escape(cs, output):
            ├── Fast-path: single scan for [ <, >, &, ", ' ]; if none -> output.write(cs)
            └── Entity replacement: range write output.write(cs, last, i) + static entity string
      ↓
[5] Output Sink
    TemplateOutput.write(CharSequence) / write(CharSequence, int, int)
    └── StringTemplateOutput: StringBuilder.append(CharSequence, start, end)
```

### Exact Source Locations
- Seeding Prologue: `io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler#compileRenderMethod` (lines 480–520)
- Dynamic Property Dispatch: `io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge#dynamicGetProperty` (lines 887–929)
- Call Site PIC: `io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite#resolveLink` (lines 125–170)
- Link Invocation: `io.github.minh124199.viettemplate.runtime.linker.AccessLink#invoke` (lines 101–108)
- Output Bridge: `io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge#writeValue` (lines 123–231)
- Contextual Escaper: `io.github.minh124199.viettemplate.runtime.HtmlTextEscaper#escape` (lines 25–81)
- Sink Buffer: `io.github.minh124199.viettemplate.runtime.StringTemplateOutput#write` (lines 43–56)

---

## 5. JFR CPU Profiling Evidence

Execution profiling via Java Flight Recorder (`jdk.ExecutionSample`) during steady-state execution across representative workloads produced the following application frame distribution:

### 5.1 `REALISTIC_APP` Workload (HTML Card with foreach loop)

Total RUNNABLE samples: 330

| Rank | Method Frame | Samples | Share (%) | Subsystem / Operation |
|---|---|---|---|---|
| 1 | `AccessLink.isMissing()` | 66 | 20.00% | PIC link sentinel resolution check |
| 2 | `BytecodeRuntimeBridge.writeValue(...)` (delegates) | 57 | 17.27% | Output bridge argument dispatch & null checks |
| 3 | `BytecodeRuntimeBridge.renderEscaped(...)` | 41 | 12.42% | Primitive check, security validation, escaper dispatch |
| 4 | `BytecodeRuntimeBridge.writeValue(...)` (body) | 38 | 11.52% | Unwrapping, policy branch evaluation |
| 5 | `T_REALISTIC_APP_...render(...)` | 32 | 9.70% | Generated template method body (control flow, loads) |
| 6 | `DynamicCallSite.resolveLink(Class)` | 19 | 5.76% | Class guard check against cached `WeakReference` |
| 7 | `BlackholeOutput.write(...)` | 11 | 3.33% | Productive sink character writing |
| 8 | `MethodHandle / Invokers.invoke_MT(...)` | 7 | 2.12% | JDK MethodHandle dynamic dispatch |
| 9 | `Double.valueOf(double)` / `boxDouble` | 4 | 1.21% | JDK auto-boxing of primitive `item.price` |
| 10 | `AccessLink.matches / checkStatus / invoke` | 9 | 2.73% | Security status check and receiver comparison |
| 11 | `LinkerStatistics.recordPicHit()` | 3 | 0.91% | AtomicLong hit counter update |

### 5.2 `REPEATED_PROPERTIES` Workload (Static direct accessor path)

Total RUNNABLE samples: 331

| Rank | Method Frame | Samples | Share (%) | Subsystem / Operation |
|---|---|---|---|---|
| 1 | `BytecodeRuntimeBridge.renderEscaped(...)` | 120 | 36.25% | Primitive check, security validation, escaper dispatch |
| 2 | `BytecodeRuntimeBridge.writeValue(...)` (delegates) | 98 | 29.61% | Output bridge dispatch |
| 3 | `BytecodeRuntimeBridge.writeValue(...)` (body) | 36 | 10.88% | Null / policy evaluation |
| 4 | `T_REPEATED_PROPERTIES_...render(...)` | 27 | 8.16% | Generated template method body |
| 5 | `RawEscaper.escape(...)` | 12 | 3.63% | Stream write to sink |
| 6 | `BytecodeRuntimeBridge.writeConst(...)` | 11 | 3.32% | Static HTML boilerplate emission |
| - | `DynamicCallSite.*` / `AccessLink.*` | **0** | **0.00%** | Completely bypassed via AOT direct devirtualization |

### 5.3 `METHOD_CALLS` Workload ($user.formattedName() calling String.toUpperCase())

Total RUNNABLE samples: 338

| Rank | Method Frame | Samples | Share (%) | Subsystem / Operation |
|---|---|---|---|---|
| 1 | `StringLatin1.toUpperCase(...)` | 120 | 35.50% | JDK String manipulation inside user method |
| 2 | `CharacterDataLatin1.toUpperCaseEx(...)` | 44 | 13.02% | JDK Unicode character casing table lookup |
| 3 | `BytecodeRuntimeBridge.renderEscaped(...)` | 37 | 10.95% | Engine output bridge |
| 4 | `BytecodeRuntimeBridge.writeValue(...)` | 37 | 10.95% | Engine output bridge |
| 5 | `T_METHOD_CALLS_...render(...)` | 14 | 4.14% | Generated template body |
| 6 | `StringLatin1.canEncode(...)` | 11 | 3.25% | JDK String encoding |
| 7 | `UserBean.formattedName()` | 8 | 2.37% | User business method body |

Interpretation:
- When templates invoke business methods (`METHOD_CALLS`), 48.5% of CPU time is consumed directly by JDK standard library string algorithms (`String.toUpperCase`), dwarfing engine dispatch costs.
- When templates are statically typed with `TemplateContract`, dynamic call site frames drop to exactly 0 samples.
- When dynamic property accesses are performed (`REALISTIC_APP`), `AccessLink.isMissing()` and `DynamicCallSite.resolveLink()` appear prominently because the inner property lookup is so fast that the small method frames represent the few operations left before jumping to the getter.

---

## 6. Allocation Profiling Evidence

JMH `-prof gc` and JFR `allocation-by-class` analysis reveal the exact sources of heap allocation:

| Workload | B/op | Major Allocation Type | Source of Allocation | Avoidable? |
|---|---|---|---|---|
| `PROPERTIES` | **0.000** | None | Zero framework allocations | N/A |
| `REPEATED_PROPERTIES`| **0.000** | None | Zero framework allocations | N/A |
| `CONDITIONALS` | **0.000** | None | Zero framework allocations | N/A |
| `MIXED_STATIC_DYNAMIC`| **0.000** | None | Zero framework allocations | N/A |
| `METHOD_CALLS` | 56.0 | `java.lang.String` | Result of user method `name.toUpperCase()` | **No** (user code) |
| `LOOPS` | 72.0 | `java.lang.Double` | Boxed double for 3 items × 24 B | **No** (reflection boxing) |
| `REALISTIC_APP` | 72.0 | `java.lang.Double` (97.9%) | Boxed double for `$item.price` (3 items × 24 B) | **No** (reflection boxing) |
| `SIMPLE_VARS` | 480.0 | Array / context entry | Context seeding in untyped test fixture | **No** (fixture artifact) |

Key Findings:
1. **Zero Framework Garbage**: Following Phase 16's elimination of per-write enum array cloning, `BytecodeRuntimeBridge.writeValue` allocates **0 bytes per write**. All enum cloning, `TemplateId` creation, and `SourceSpan` construction are completely absent on successful execution paths.
2. **The 72 B/op in Realistic Workloads**: JFR allocation tracking confirmed that 97.86% of allocation pressure in `REALISTIC_APP` consists of `java.lang.Double`. This originates from `sun.invoke.util.ValueConversions.boxDouble` when the dynamic `MethodHandle` invokes `Item.getPrice()` (which returns a primitive `double`) through an untyped dynamic call site returning `Object`.
3. **Escaping Garbage**: `HtmlTextEscaper` allocates **0 B/op** in both fast-path and escaping paths.

---

## 7. Dynamic Dispatch Architecture Audit

The polymorphic inline cache (PIC) in `DynamicCallSite.java` implements a 4-state lifecycle:

```
[ UNLINKED ]
     │ (first invocation)
     ▼
[ MONOMORPHIC ] ──(same receiver class)──► [ Fast Path: mono.matches() ]
     │ (new receiver class)
     ▼
[ POLYMORPHIC ] ──(depth ≤ 4)────────────► [ Fast Path: linear scan in links[] ]
     │ (> 4 receiver classes)
     ▼
[ MEGAMORPHIC ] ─────────────────────────► [ Fast Path: BoundedWeakClassCache.get() ]
```

### Invariants Verified:
1. **Lock-Free Fast Paths**: Fast-path reads across `MONOMORPHIC`, `POLYMORPHIC`, and `MEGAMORPHIC` states execute purely over volatile fields (`monomorphicLinkRef`, `polymorphicLinks`, `megamorphicCache`) without acquiring locks.
2. **Synchronized State Transitions**: Misses and transitions between states are guarded by `synchronized (this)`, ensuring race-free transition and link registration.
3. **Class Unloading Safety**: Receiver classes are stored exclusively through `WeakReference<Class<?>>` in `AccessLink`, and `AccessLink` instances are held via `WeakReference<AccessLink>`. Megamorphic storage uses `BoundedWeakClassCache` (bounded at 1024 entries). Dynamic linking introduces zero classloader retention risks.
4. **Security Policy Enforcement**: Every `AccessLink` verifies permissions during initial linking via `DynamicLinker.link(targetClass, memberKey, policy)` and re-checks status on invocation (`checkStatus`), throwing `TemplateSecurityException` on violations.

---

## 8. AccessLink & DynamicCallSite Deep-Dive

### 8.1 Is `AccessLink.isMissing()` Avoidable?
`AccessLink.isMissing()` evaluates:
```java
public boolean isMissing() {
  return status == Status.MISSING;
}
```
- **Execution Frequency**: Executes on every dynamic property access (`DynamicCallSite.invoke`).
- **Nature**: Reference comparison against `Status.MISSING` enum constant.
- **Why it is present**: In Apache Velocity (VTL) semantics, dereferencing a non-existent property on a valid object (e.g. `$user.foo`) evaluates to silent null or undefined reference rather than an exception. The linker caches a `Status.MISSING` sentinel link for classes that lack the requested member to avoid repeated reflection lookups.
- **Cost Classification**: **INTRINSIC**. Without checking `isMissing()`, missing properties would either fail with `NullPointerException` or require repeated reflective attempts on every call.

### 8.2 AtomicLong Contention in `LinkerStatistics`
In `DynamicCallSite.java`:
```java
if (mono != null && mono.matches(targetClass)) {
  stats.recordPicHit();
  return mono;
}
```
`stats.recordPicHit()` executes `picHits.incrementAndGet()` on an `AtomicLong`.
- In multithreaded environments with high concurrent rendering volume, updating an `AtomicLong` on every property hit incurs memory bus synchronization and cache-line invalidation.
- While measured overhead in single-threaded JMH is low (~0.91% of CPU samples), this represents one of the few non-intrinsic instructions on the fast path.
- **Evaluation**: Could be bypassed or made opt-in in a future release if high-core concurrency benchmarks demonstrate atomic contention.

---

## 9. Escaping and Output Sink Analysis

### 9.1 Escaping Overhead
- `HtmlTextEscaper.escape` scans the input `CharSequence` with a single linear pass looking for characters `<, >, &, ", '`.
- If no special character exists (the common case for names, IDs, dates, numbers), it returns immediately with `output.write(input)` without allocation.
- In `HtmlEscapingBenchmark`, throughput between unescaped and heavy-escaped templates differed by only ~4.3%.
- **Conclusion**: Escaping consumes a negligible fraction of execution time. SIMD vectorization or custom byte-scanning would add substantial native/JNI complexity or unsafe memory access with minimal throughput benefit.

### 9.2 Output Sink Overhead
- `StringTemplateOutput` appends directly to an internal `StringBuilder`.
- When pre-sized or reused within thread confinement, buffer resizing is 0.
- Copying UTF-16 character arrays to the buffer is the fundamental physical work of a template engine.
- Zero-copy approaches (e.g. rope data structures or direct socket writes) would add severe architectural overhead and break compatibility with standard Java `Writer` and `OutputStream` consumers.

---

## 10. Intrinsic vs. Avoidable Cost Matrix

| Hotspot / Subsystem | Representative Workload | Measured CPU Share | Allocation Share | Cost Classification | Optimization Feasibility | Risk & Tradeoffs |
|---|---|---|---|---|---|---|
| `Output Sink Copying` (`write`) | All workloads | ~3–5% | 0% | **INTRINSIC** | None | Fundamental requirement of string generation |
| `Escaping Scan` (`HtmlTextEscaper`) | Escaped workloads | ~3–5% | 0% | **INTRINSIC** | Low | SIMD would introduce unsafe/native dependencies |
| `PIC Class Guard` (`matches()`) | Dynamic workloads | ~6–9% | 0% | **INTRINSIC** | None | Required for type safety in dynamic dispatch |
| `Sentinel Check` (`isMissing()`) | Dynamic workloads | ~15–20% | 0% | **INTRINSIC** | None | Required for VTL null/missing property semantics |
| `MethodHandle Invocation` | Dynamic workloads | ~4–7% | 0% | **INTRINSIC** | Low | Native JVM invocation mechanism |
| `Primitive Double Boxing` | Dynamic loops (`REALISTIC_APP`) | ~1–2% | 98% (72 B/op) | **INTRINSIC** | Medium (via typed contract) | Dynamic dispatch returns `Object`; cannot unbox without contracts |
| `LinkerStatistics Hit Counter` | Dynamic workloads | ~1% | 0% | **AMORTIZABLE** | High | Minor win; only relevant under heavy multi-threaded contention |
| `Direct Accessor Path` | Statically typed | 0% (PIC bypassed) | 0% | **ALREADY OPTIMIZED** | N/A | Delivered in Phase 11/12 via `TemplateContract` (>11.1M ops/s) |

---

## 11. Architectural Constraints

Any prospective future optimization must adhere to these strict platform constraints:
1. **GraalVM Native Image**: Must not rely on runtime bytecode generation, dynamic ClassLoader creation, or unsupported `MethodHandle` combinators.
2. **Class Unloading & Multi-Tenancy**: Must never retain strong references to application `Class<?>` or `ClassLoader` instances. All caches must remain `WeakReference` or `ClassValue`-backed.
3. **Security Policy Invariants**: Must not bypass `LinkerAccessPolicy.isClassPermitted()` or member denial sentinels.
4. **VTL Semantics & Parity**: Must strictly preserve Velocity-compatible undefined/null evaluation behavior across both AOT and IR interpreter execution tiers.
5. **Thread Safety**: All inline cache fast paths must remain lock-free, and cache transitions must be thread-safe.

---

## 12. Benchmark Artifact Resolution (Part A)

### 12.1 `DirectAccessorBenchmark.java`
- **Audit Result**: High-value benchmark isolating `TemplateContract` direct accessor devirtualization against dynamic PIC (monomorphic, polymorphic depth 2 & 4, megamorphic). Compiles cleanly, respects JMH conventions, and was refined to pre-allocate polymorphic contexts to prevent context allocation bias.
- **Action**: **COMMIT AS PERMANENT BENCHMARK INFRASTRUCTURE** (`git commit -m "perf(bench): retain direct accessor benchmark"`).

### 12.2 `OutputMetadataHotPathBenchmark.java` and `CountingTemplateOutput.java`
- **Audit Result**: The investigation originally identified that `OutputMetadataHotPathBenchmark` defined `CountingOutput`, which was directly imported and instantiated by the committed `WriteValuePairedBenchmark.java` (`private OutputMetadataHotPathBenchmark.CountingOutput countingOutput;`), while also providing unique micro-benchmark dimensions for isolated `TemplateId` and `SourceSpan` construction costs.
- **Action & Follow-up Hygiene Resolution**: In a follow-up repository-hygiene cleanup, the reusable counting sink was extracted into a dedicated tracked benchmark fixture (`CountingTemplateOutput.java`), decoupling `WriteValuePairedBenchmark.java` and establishing clean-checkout reproducibility. `OutputMetadataHotPathBenchmark.java` was retained as permanent benchmark infrastructure to measure isolated metadata construction costs. The runtime performance conclusions remain unchanged, and no new optimization phase has been opened.

---

## 13. Final Recommendation

### Decision: **NO NEW OPTIMIZATION PHASE JUSTIFIED**

### Detailed Justification:
1. **Remaining Execution Profile Costs are Intrinsic**:
   - The prominent runtime frames identified in JFR profiling (`AccessLink.isMissing()`, `DynamicCallSite.resolveLink()`, `renderEscaped`, `output.write`) represent the minimal, essential semantic invariants of dynamic language execution and string materialization.
   - Guard checks cannot be eliminated without compromising dynamic type safety or VTL null tolerance.
   - Character copying cannot be eliminated without abandoning standard buffer contracts.
2. **Framework Garbage is Already Fully Eliminated**:
   - Heap garbage on the core rendering path is **0.000 B/op** across all property, repeated-property, conditional, and mixed workloads.
   - In realistic application workloads, the only observable allocation (72 B/op) is caused by standard JVM primitive boxing (`Double.valueOf`) when reading a primitive `double` property through dynamic reflection.
3. **Throughput Exceeds Practical Demand**:
   - AOT template rendering operates between **3.45M ops/s** (complex realistic HTML cards with loops and conditionals) and **44.8M ops/s** (branching templates).
   - In micro-benchmarks, direct accessor specialization delivers **11.1M ops/s**, and dynamic PIC delivers **5.27M – 7.56M ops/s**.
4. **Disproportionate Risk-to-Reward Ratio**:
   - Speculative optimizations (such as removing atomic counters, attempting unsafe bytecode generation for untyped properties, or SIMD escaping) would introduce major complexity, risk regressions in security or GraalVM native image compatibility, and yield negligible real-world benefits.

The runtime performance architecture of Viet Template is mature, highly optimized, and sound. No further optimization phase should be pursued.
