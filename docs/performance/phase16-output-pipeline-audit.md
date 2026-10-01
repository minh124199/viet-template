# Phase 16: Post-Lazy-Metadata Profiling & Output Pipeline Audit

## 1. Executive Summary

Following Phase 15's lazy metadata optimization (`TemplateId` and `SourceSpan` construction moved off the successful rendering path), this phase executed an evidence-driven performance investigation, validation, and audit of Viet Template's remaining output runtime path (`BytecodeRuntimeBridge`).

Key findings:
1. **Phase 15 & 16 Baseline Methodology**: Controlled paired microbenchmarking (`WriteValuePairedBenchmark`) confirmed Phase 15 eliminated exactly 56 bytes per write (16 B `TemplateId` + 40 B `SourceSpan`) and Phase 16 eliminated the remaining 64 bytes per write (`NullRenderMode.values()` and `IrEscapeMode.values()`). Because Phase 15 was never committed as an isolated historical commit in repository history, the Phase 15 comparison was established as a **controlled reconstruction of the lazy path with enum values cloning** side-by-side with pre-Phase-15 eager metadata and Phase 16 optimized cached indexing.
2. **Escaping Audit**: Profiling and benchmarking (`HtmlEscapingBenchmark`) confirmed that escaping is **NOT** a bottleneck. `HtmlTextEscaper.INSTANCE` is zero-allocation (0.001 to 0.009 B/op). In end-to-end templates (`HtmlEscapingBenchmark.endToEnd_realisticHtml_aot`), throughput between `NO_ESCAPE` (1,240,557 ops/s) and `DENSE_ESCAPE` (1,187,043 ops/s) differed by only ~4.3% even under synthetic worst-case escaping. Escaping was left untouched; no SIMD or custom vectorization is warranted.
3. **TemplateOutput & Buffer Management**: Buffer growth and sink conversions are minimal when appropriately sized. Discard sinks (`CountingOutput`) and in-memory sinks (`StringTemplateOutput`) exhibit nearly identical throughput and identical zero-allocation curves on the output bridge.
4. **Dynamic Dispatch / PIC**: Dynamic dispatch is performant under tested monomorphic and bimorphic access patterns. Guard checks such as `AccessLink.isMissing()` represent necessary dynamic type invariants rather than avoidable waste. PIC requires no redesign.
5. **The Actual Dominant Overhead Discovered**: CPU profiling (JFR execution sampling) and allocation tracking (`-prof gc`) revealed that `NullRenderMode.values()` and `IrEscapeMode.values()` accounted for significant CPU samples and **64 bytes of heap garbage per write**. In Java (§8.9.3), `Enum.values()` clones and allocates a fresh array on every call.
6. **Implemented & Minimized Optimization**: Cached static final arrays `NULL_RENDER_MODES`, `ESCAPE_MODES_BY_IR_MODE`, `ESCAPERS_BY_IR_MODE`, `BINARY_OP_KINDS`, `UNARY_OP_KINDS`, and `MEMBER_OPERATIONS` were established in `BytecodeRuntimeBridge`.
   - `ESCAPE_MODES_BY_IR_MODE` and `ESCAPERS_BY_IR_MODE` are initialized using compile-time exhaustive switch methods over `IrEscapeMode.values()`, eliminating fragile manual array ordering and making constant reordering/addition compile-safe.
   - Option A (Direct Indexing) was enforced: out-of-bounds ordinals throw `ArrayIndexOutOfBoundsException`, strictly preserving pre-Phase-16 failure semantics and eliminating arbitrary fallback branches.
7. **Measured Impact**:
   - Heap allocation on the successful write path dropped from **64 B/write to 0 B/write** (100% elimination of framework garbage on writeValue).
   - In `TemplateComplexityBenchmark`, `PROPERTIES`, `REPEATED_PROPERTIES`, `CONDITIONALS`, and `MIXED_STATIC_DYNAMIC` allocations dropped to **0.000 B/op**.
   - `REALISTIC_APP` allocations dropped from **712 B/op to 72 B/op** (-89.9% allocation reduction).
   - `REALISTIC_APP` throughput reached **3.14M–3.28M ops/s**.
   - In JFR execution sampling, `NullRenderMode.values()` and `IrEscapeMode.values()` dropped from prominent frames to **0 samples (completely absent)**.

---

## 2. Problem Observed

After Phase 15 deferred `TemplateId` and `SourceSpan` construction to warning and error branches:
- AOT template rendering still exhibited **64 bytes of heap allocation per output write**.
- In `TemplateComplexityBenchmark.REALISTIC_APP`, 712 bytes were allocated per render operation despite all variables being pre-allocated strings or primitive values.
- In CPU profiling (JFR and JMH stack profiling), `BytecodeRuntimeBridge.writeValue` remained the single largest runtime method frame, consuming 30–31% of total RUNNABLE CPU.

---

## 3. Profiling Evidence

### 3.1 JFR Top Frame Sampling (`REALISTIC_APP`, Pre-Optimization)

From Java Flight Recorder (`jdk.ExecutionSample`) during steady-state execution:

| Rank | Method Frame | Samples | Sample Share (%) | Nature of Operation |
|---|---|---|---|---|
| 1 | `AccessLink.isMissing()` | 24 | 15.1% | PIC link resolution check |
| 2 | `BytecodeRuntimeBridge.writeValue(...)` (delegates) | 20 | 12.6% | Output bridge dispatch |
| 3 | `NullRenderMode.values()` | 15 | **9.4%** | `Enum.values()` array clone |
| 4 | `T_REALISTIC_APP_vtl_...render()` | 13 | 8.2% | Generated template method body |
| 5 | `IrEscapeMode.values()` | 11 | **6.9%** | `Enum.values()` array clone |
| 6 | `BlackholeOutput.write(CharSequence)` | 9 | 5.7% | Productive sink write |
| 7 | `BytecodeRuntimeBridge.writeValue` (inner body) | 8 | 5.0% | Null and error policy checks |
| 8 | `renderEscaped` | 5 | 3.1% | Escape dispatch |
| - | **Combined `Enum.values()` Overhead** | **26** | **16.3%** | Avoidable framework allocation |

*Note: JFR figures represent thread-execution sample counts during the profiling run and describe relative frame frequency, not absolute continuous CPU percentages.*

### 3.2 Allocation Tracking (`-prof gc`)

Across microbenchmarks and complexity suites, allocations scaled linearly at **64 bytes per write**:
- `WriteValuePairedBenchmark` (1 write): 64 B/op
- `WriteValuePairedBenchmark` (4 writes): 256 B/op (4 × 64 B)
- `WriteValuePairedBenchmark` (16 writes): 1,024 B/op (16 × 64 B)
- `WriteValuePairedBenchmark` (64 writes): 4,096 B/op (64 × 64 B)
- `WriteValuePairedBenchmark` (256 writes): 16,384 B/op (256 × 64 B)
- `WriteValuePairedBenchmark` (1024 writes): 65,536 B/op (1024 × 64 B)
- `TemplateComplexityBenchmark.PROPERTIES` (3 writes): 192 B/op (3 × 64 B)
- `TemplateComplexityBenchmark.REPEATED_PROPERTIES` (5 writes): 320 B/op (5 × 64 B)
- `TemplateComplexityBenchmark.CONDITIONALS` (1 write): 64 B/op (1 × 64 B)
- `TemplateComplexityBenchmark.REALISTIC_APP` (11 writes): 704 B/op (11 × 64 B) + 8 B padding = 712 B/op

---

## 4. Root Cause

In Java, the language specification (§8.9.3) mandates that `E.values()` returns a freshly cloned copy of the enum constants array on every invocation:
```java
// Java compiler synthesizes for any enum:
public static E[] values() {
    return (E[])$VALUES.clone();
}
```
In `BytecodeRuntimeBridge.writeValue`:
```java
NullRenderMode nullMode = NullRenderMode.values()[nullModeOrdinal];
IrEscapeMode irMode = IrEscapeMode.values()[escapeModeOrdinal];
```
For every dynamic reference emitted into the output stream:
1. `NullRenderMode.values()` clones an array of 3 references (32 bytes on 64-bit JVM with compressed OOPs).
2. `IrEscapeMode.values()` clones an array of 4 references (32 bytes on 64-bit JVM with compressed OOPs).
Total: **64 bytes of heap allocation per output write**.

In templates rendering loops, tables, or complex records, this generated megabytes of short-lived heap garbage per second, triggering frequent GC cycles.

---

## 5. Optimization & Minimization Implemented

### 5.1 Compile-Safe Array Initialization

Rather than manual array literals that could silently drift if enums are reordered, `BytecodeRuntimeBridge` uses static final arrays initialized via exhaustive switch expressions:

```java
  private static final NullRenderMode[] NULL_RENDER_MODES = NullRenderMode.values();
  private static final EscapeMode[] ESCAPE_MODES_BY_IR_MODE = initEscapeModesByIrMode();
  private static final Escaper[] ESCAPERS_BY_IR_MODE = initEscapersByIrMode();
  private static final BinaryOpKind[] BINARY_OP_KINDS = BinaryOpKind.values();
  private static final UnaryOpKind[] UNARY_OP_KINDS = UnaryOpKind.values();
  private static final MemberOperation[] MEMBER_OPERATIONS = MemberOperation.values();

  private static EscapeMode[] initEscapeModesByIrMode() {
    IrEscapeMode[] irModes = IrEscapeMode.values();
    EscapeMode[] modes = new EscapeMode[irModes.length];
    for (IrEscapeMode irMode : irModes) {
      modes[irMode.ordinal()] =
          switch (irMode) {
            case RAW -> EscapeMode.RAW;
            case HTML_TEXT -> EscapeMode.HTML_TEXT;
            case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
            case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
          };
    }
    return modes;
  }

  private static Escaper[] initEscapersByIrMode() {
    IrEscapeMode[] irModes = IrEscapeMode.values();
    Escaper[] escapers = new Escaper[irModes.length];
    for (IrEscapeMode irMode : irModes) {
      escapers[irMode.ordinal()] =
          switch (irMode) {
            case RAW -> StandardEscapers.raw();
            case HTML_TEXT -> StandardEscapers.htmlText();
            case HTML_ATTRIBUTE_QUOTED -> StandardEscapers.htmlAttribute();
            case URL_COMPONENT -> StandardEscapers.urlComponent();
          };
    }
    return escapers;
  }
```

### 5.2 Option A: Direct Indexing and Strict Semantic Preservation

In `BytecodeRuntimeBridge.writeValue`:
```java
    NullRenderMode nullMode = NULL_RENDER_MODES[nullModeOrdinal];
    EscapeMode escapeMode = ESCAPE_MODES_BY_IR_MODE[escapeModeOrdinal];
```
Direct indexing enforces:
- **Exact Semantic Parity**: An out-of-bounds ordinal throws `ArrayIndexOutOfBoundsException`, matching original pre-Phase-16 behavior and preventing accidental fallback to quiet rendering.
- **Zero Branching Overhead**: Eliminates conditional bound checks on the hot execution path.
- **Contractual Safety**: The AOT compiler (`BytecodeTemplateCompiler`) strictly generates valid ordinals from IR enums.

---

## 6. Paired A/B Benchmarks

### 6.1 Isolated Write Microbenchmark (`WriteValuePairedBenchmark`)

Harness: OpenJDK 21.0.12.1, JMH 1.37, `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC`, **3 forks, 5 warmup iterations, 5 measurement iterations** (`-f 3 -wi 5 -i 5 -prof gc`).

The comparison evaluates three controlled implementations:
- **Variant A (Pre-Phase 15 Eager)**: Eager `TemplateId.of()` + `makeSpan()` on entry, plus repeated `.values()` calls.
- **Variant B (Phase 15 Lazy + `.values()`)**: Controlled reconstruction of lazy metadata with repeated `.values()` calls.
- **Variant C (Phase 16 Optimized)**: Lazy metadata with cached static array indexing.

#### Throughput (ops/sec)
| Write Count | Variant A (Eager) | Variant B (Phase 15 Baseline) | Variant C (Phase 16 Optimized) | Delta C vs B (%) | Delta C vs A (%) |
|---|---|---|---|---|---|
| 1 | 3,984,530 | 8,206,066 | **8,850,072** | **+7.8%** | **+122.1% (2.22x)** |
| 4 | 955,653 | 2,399,669 | **2,562,871** | **+6.8%** | **+168.2% (2.68x)** |
| 16 | 248,917 | 650,827 | **643,630** | -1.1% | **+158.6% (2.59x)** |
| 64 | 66,392 | 156,814 | **176,150** | **+12.3%** | **+165.3% (2.65x)** |
| 256 | 15,170 | 37,645 | **42,930** | **+14.0%** | **+183.0% (2.83x)** |
| 1,024 | 3,976 | 9,987 | **10,406** | **+4.2%** | **+161.7% (2.62x)** |

#### Allocations (Bytes/op)
| Write Count | Variant A (Eager) | Variant B (Phase 15 Baseline) | Variant C (Phase 16 Optimized) | Elimination vs B (%) |
|---|---|---|---|---|
| 1 | 120 B/op | 64 B/op | **0.001 B/op (≈ 0)** | **-100.0%** |
| 4 | 480 B/op | 256 B/op | **0.003 B/op (≈ 0)** | **-100.0%** |
| 16 | 1,920 B/op | 1,024 B/op | **0.011 B/op (≈ 0)** | **-100.0%** |
| 64 | 7,680 B/op | 4,096 B/op | **0.040 B/op (≈ 0)** | **-100.0%** |
| 256 | 30,720 B/op | 16,384 B/op | **0.162 B/op (≈ 0)** | **-100.0%** |
| 1,024 | 122,882 B/op | 65,537 B/op | **0.666 B/op (≈ 0)** | **-100.0%** |

---

## 7. Template Complexity Matrix Impact (`TemplateComplexityBenchmark`)

All workloads benchmarked under OpenJDK 21, 3 forks, 5 warmup, 5 measurement iterations (`-f 3 -wi 5 -i 5 -prof gc`):

| Workload | Features Evaluated | Pre-Phase 15 Alloc | Phase 15 Baseline Alloc | Phase 16 Optimized Alloc | Slotted Thrpt (ops/s) |
|---|---|---|---|---|---|
| `SIMPLE_VARS` | 3 scalar variables | 936 B/op | 672 B/op | **480 B/op** | 6,022,289 |
| `PROPERTIES` | Object property chain | 456 B/op | 192 B/op | **0.000 B/op** | 17,416,996 |
| `REPEATED_PROPERTIES` | 5x repeated property | 800 B/op | 320 B/op | **0.001 B/op** | 11,567,049 |
| `CONDITIONALS` | `#if` / `#else` branch | 152 B/op | 64 B/op | **0.000 B/op** | 41,331,373 |
| `LOOPS` | `#foreach` iteration | 984 B/op | 456 B/op | **72 B/op** | 4,857,889 |
| `NESTED_EXPR` | Multi-term boolean logic | 488 B/op | 400 B/op | **264 B/op** | 4,115,616 |
| `METHOD_CALLS` | Guarded direct method call | 360 B/op | 184 B/op | **56 B/op** | 10,222,455 |
| `MIXED_STATIC_DYNAMIC` | Statically typed + PIC | 480 B/op | 192 B/op | **0.000 B/op** | 14,119,771 |
| `REALISTIC_APP` | HTML blocks, cards, loops | 1,672 B/op | 712 B/op | **72 B/op** | 3,277,520 |

---

## 8. Post-Optimization JFR CPU Profile

From Java Flight Recorder (`jdk.ExecutionSample`) during sustained steady-state execution after Phase 16:

- `NullRenderMode.values()`: **0 samples (0.0%)** — completely eliminated from the profile.
- `IrEscapeMode.values()`: **0 samples (0.0%)** — completely eliminated from the profile.
- Prominent remaining frames:
  - `AccessLink.isMissing()` and `AccessLink.invoke()`: dynamic PIC guard resolution.
  - `BytecodeRuntimeBridge.dynamicGetProperty`: property dispatch.
  - `renderEscaped`: escape dispatch and character scanning.
  - `BytecodeRuntimeBridge.writeValue` and `writeConst`: output bridge dispatch.
  - Sinks (`BlackholeOutput.write` / `StringTemplateOutput`).

---

## 9. Verification & Safety

### 9.1 Semantic & Diagnostic Parity
- **Full Test Suite**: All tests in `viet-template-parent` (15 reactor modules, 2,000+ tests) passed with 0 failures, 0 errors.
- **Dedicated Regression Suite**: `BytecodeRuntimeBridgeTest` (26 tests) verified:
  - Exhaustive `IrEscapeMode` to `EscapeMode` and `Escaper` mappings.
  - `NullRenderMode`, `BinaryOpKind`, `UnaryOpKind`, and `MemberOperation` ordinal mappings.
  - Negative and out-of-bounds ordinals strictly throw `ArrayIndexOutOfBoundsException`.
  - Primitive fast paths and safe content (`SafeHtml`, `SafeUrl`).

### 9.2 Security Confinement
- `LinkerAccessPolicy` checks remain strictly enforced prior to any output write.
- Unauthorized class renders continue to throw identical `TemplateSecurityException` instances with exact coordinates.
- All 18 category tests in `SecurityRegressionCorpusTest` passed.

### 9.3 Public API & Bytecode ABI Compatibility
- **Public API/SPI Compatibility**: 0 breaking changes detected across all 5 canonical baselines.
- **Public Surface Classification**: 359 compiled types verified, 0 unclassified types, 0 signature leaks.
- **Generated Bytecode Contract**: Unchanged. `BytecodeTemplateCompiler` emits identical `invokestatic BytecodeRuntimeBridge.writeValue(...)` instructions.

---

## 10. Conclusion & Stop-Gate Recommendation

Phase 16 has achieved its intended goal:
1. Eliminated **64 bytes of heap garbage per write** on the runtime hot path.
2. Achieved **0 B/op heap allocation** for successful property writes.
3. Preserved strict semantic equivalence, ABI stability, and exception boundaries via Option A direct indexing.
4. Protected enum ordering maintainability via javac-checked exhaustive mapping methods.

**Recommendation**: Stop optimization. The remaining execution profile costs (`AccessLink.isMissing()`, property dispatch, string conversion, buffer writes) represent essential runtime semantics of a dynamically linked template engine. No speculative redesign or SIMD escaping should be pursued.
