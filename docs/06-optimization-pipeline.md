# 06 — Optimization Pipeline

## 1. Correctness rule

Every optimization must preserve output, defined side effects, error semantics and security decisions for the selected profile.

## 2. Optimization pipeline

```text
O00 Normalize
O10 RemoveNoOps
O20 MergeTextConstants
O30 ConstantFold
O40 ConstantPropagateLocals
O45 AssignVariableSlots
O50 DeadBranchElimination
O60 LowerTruthiness
O70 BindDirectAccess
O80 SpecializeLoops
O90 LowerWrites
O100 InlineSmallMacros
O110 InlineStaticPartials (optional)
O120 EscapeSpecialization
O130 PrimitiveSpecialization
O140 DynamicCallSiteCanonicalization
O150 MethodSizePlanning
O160 Verify
```

Every pass can be disabled for debugging and A/B benchmarking.

## 3. Normalize (O00)

Canonicalize logical aliases, numeric literal representation, null-render mode, escape mode and alternate-reference representation.

## 4. Remove no-ops (O10)

Eliminate empty blocks, no-op comments, and redundant identity conversions.

## 5. Merge text constants (O20)

```text
WRITE "<div>"
WRITE "\n"
WRITE "  "
```

becomes one static chunk.

## 6. Constant folding and propagation (O30, O40)

Fold pure compile-time expressions and local constants where semantics permit.

```velocity
#set($x = 5)
#if($x > 2)
A
#end
```

can become unconditional static output if `x` is truly local and unchanged.

## 7. Variable slot assignment (O45 AssignVariableSlots)

Introduced in Release 0.2.0:

Analyzes symbol scopes, variable declarations (`#set`), loop variables (`#foreach`), and macro parameters to assign local variables to stable compiler-assigned integer slot IDs in `ExecutionFrame.slots` (`EvaluationValue[] slots`).
- **Stable Slot Assignment**: Assigns stable integer slot IDs without slot reuse; slot reuse remains explicitly deferred as a later optional optimization requiring separate correctness and benchmark evidence.
- **Lowering**: Replaces named variable references (`LoadLocal("name")`, `StoreLocal("name")`) with direct indexed instructions (`LoadSlot(slotIndex)`, `StoreSlot(slotIndex)`).
- **Direct $O(1)$ Array Indexing**: Variable loads and stores compile to direct array accesses (`ALOAD`/`ASTORE` or `slots[slotIndex]`), completely eliminating hash calculations, bucket searches, and map entry allocations.
- **3-State Semantics**: Preserves the 3-state evaluation model (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`). Because Java reference-array elements are initially `null`, the runtime implementation explicitly decides and tests how internal empty slots represent undefined.
- **Name-Based Fallback**: A name-based fallback is retained for variable accesses whose identity cannot safely be resolved to a static slot while preserving Velocity-compatible semantics.

## 8. Dead branch elimination (O50)

Remove unreachable branches and empty blocks after constant analysis.

## 9. Truthiness specialization (O60)

Replace generic object truthiness with primitive, string, or collection-specific operations when type is known statically.

## 10. Direct property binding (O70)

Replace dynamic get with direct getter, record component, map lookup, or index operation whenever semantic analysis has sufficient type information.

## 11. Loop specialization (O80)

Do not blindly compile all `List` to index loops: a `LinkedList` could become $O(n^2)$. Use indexed loops only when random access is statically known or guarded. Arrays are indexed; general `Iterable/List` can use iterator until profiling justifies polymorphic specialization.

## 12. Write lowering (O90)

Specialize:

```text
WRITE_I32
WRITE_I64
WRITE_BOOL
WRITE_CHAR_SEQUENCE
WRITE_ESCAPED_CHAR_SEQUENCE
WRITE_OBJECT_TO_STRING
WRITE_CONST_UTF8
```

so primitive values avoid boxing.

## 13. Direct UTF-8 constants

For UTF-8 output mode, pre-encode static content once at build time. Dynamic values still pass through streaming escaping and encoding.

## 14. Escape specialization (O120)

Compile-time constants may be escaped at compile time. Explicit safe-content types may bypass escaping. Never infer trust from arbitrary runtime type names.

## 15. Primitive specialization (O130)

Prefer:

```text
int -> decimal encoder -> output
```

over:

```text
int -> Integer -> Object -> String -> output
```

## 16. Macro inlining (O100)

Use a cost model based on instruction count, recursion, call count and generated method size. Large-scale inlining can hurt JIT compiler inlining heuristics.

## 17. Static partial inlining (O110)

Default to direct compiled calls. Inline only small private partials when measurements justify code duplication.

## 18. Dynamic call-site canonicalization (O140)

Assign stable ids to dynamic sites. Do not merge sites with different expected types or security policies.

## 19. Method-size planning (O150)

Split large generated render methods at natural template boundaries to avoid JVM JIT 64 KB method-size limits and compilation tier bailouts. Thresholds are benchmark-driven.

## 20. IR Verification (O160)

Verify all IR invariants, typing rules, and slot index bounds before passing the optimized plan to backends.

## 21. Optimization levels

```text
O0 correctness/debug
O1 cheap deterministic passes
O2 production default
O3 aggressive experimental
```

## 22. Explain plan

CLI output should reveal why each expression is fast or dynamic:

```text
$user.name
  type: User -> String
  access: DIRECT_RECORD User.name()
  slot: 2
  null: strict
  escape: HTML_TEXT
  expected temporary allocation: none

$dynamic.foo
  type: dynamic
  access: PIC_DYNAMIC site=7 property=foo (AccessLink[] array scan, max depth 4)
  security: runtime READ_PROPERTY linkage check
```

## 23. Complexity vs. JVM Execution Cost

Optimization passes must account for actual JVM runtime execution characteristics:
- **Array Memory Locality**: For small $N$ ($N \le 4\text{--}8$), contiguous array scanning outperforms hash table lookups due to low constant factors, sequential element access, and zero object header or pointer indirection.
- **Slot Array vs. Hash Map**: Variable slot indices compile to direct array loads (`ALOAD`/`AALOAD`), eliminating hash code computation, bucket search, and node pointer chasing.
- **Secondary Indices for Hotspots**: Large caches (such as compilation units) avoid $O(N)$ full table scans by maintaining secondary reverse indices (`TemplateId -> Set<CompileCacheKey>`), performing average $O(1)$ key set lookup + $O(K)$ entry removals to reduce overall invalidation work to $O(K)$.

## 24. Optimization Acceptance Gates

Every new optimization pass, algorithmic change, or custom data structure must satisfy the Four-Tier Implementation Preference Hierarchy and the 7-Part DSA Acceptance Rule (see [15 — Benchmark and Performance Engineering Plan](15-benchmark-plan.md)):
1. Baseline measurement exists under realistic single-threaded and concurrent workloads.
2. Proven hotspot representing $\ge 5\%$ of render time or heap allocations.
3. Practical JVM runtime execution characteristics favor the approach over simpler alternatives.
4. Balanced performance tradeoff: evaluate throughput, latency, allocation rate, retained memory, contention, and implementation complexity together on representative workloads.
5. Maintainable design with well-defined invariants and concurrent stress tests.
6. Verified with JMH benchmarks demonstrating $\ge 15\%$ throughput improvement or $\ge 20\%$ allocation reduction across JDK 17, 21, and 25.
7. Immediate fallback to simple standard JDK collections if gains are marginal ($< 5\text{--}10\%$).
