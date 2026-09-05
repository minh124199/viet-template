# 06 — Optimization Pipeline

## 1. Correctness rule

Every optimization must preserve output, defined side effects, error semantics and security decisions for the selected profile.

## 2. Initial pipeline

```text
O00 Normalize
O10 RemoveNoOps
O20 MergeTextConstants
O30 ConstantFold
O40 ConstantPropagateLocals
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

## 3. Normalize

Canonicalize logical aliases, numeric literal representation, null-render mode, escape mode and alternate-reference representation.

## 4. Merge text constants

```text
WRITE "<div>"
WRITE "\n"
WRITE "  "
```

becomes one static chunk.

## 5. Constant folding/propagation

Fold pure compile-time expressions and local constants where semantics permit.

```velocity
#set($x = 5)
#if($x > 2)
A
#end
```

can become unconditional static output if `x` is truly local and unchanged.

## 6. Dead branch elimination

Remove unreachable branches/empty blocks after constant analysis.

## 7. Truthiness specialization

Replace generic object truthiness with primitive/string/collection-specific operations when type is known.

## 8. Direct property binding

Replace dynamic get with direct getter/record/map/index operation whenever semantic analysis has enough information.

## 9. Loop specialization

Do not blindly compile all `List` to index loops: a `LinkedList` could become O(n²). Use indexed loops only when random access is statically known or guarded. Arrays are indexed; general `Iterable/List` can use iterator until profiling justifies polymorphic specialization.

## 10. Write lowering

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

## 11. Direct UTF-8 constants

For UTF-8 output mode, pre-encode static content once at build time. Dynamic values still pass through streaming escaping/encoding.

## 12. Escape specialization

Compile-time constants may be escaped at compile time. Explicit safe-content types may bypass escaping. Never infer trust from arbitrary runtime type names.

## 13. Primitive specialization

Prefer:

```text
int -> decimal encoder -> output
```

over:

```text
int -> Integer -> Object -> String -> output
```

## 14. Macro inlining

Use a cost model based on instruction count, recursion, call count and generated method size. Large-scale inlining can hurt JIT quality.

## 15. Static partial inlining

Default to direct compiled calls. Inline only small private partials when measurements justify code duplication.

## 16. Dynamic call-site canonicalization

Assign stable ids to dynamic sites. Do not merge sites with different expected types or security policies.

## 17. Method-size planning

Split large generated render methods at natural template boundaries to avoid JIT problems. Thresholds are benchmark-driven, not hard-coded doctrine.

## 18. Optimization levels

```text
O0 correctness/debug
O1 cheap deterministic passes
O2 production default
O3 aggressive experimental
```

## 19. Explain plan

CLI output should reveal why each expression is fast or dynamic:

```text
$user.name
  type: User -> String
  access: DIRECT_RECORD User.name()
  null: strict
  escape: HTML_TEXT
  expected temporary allocation: none

$dynamic.foo
  type: dynamic
  access: PIC_DYNAMIC site=7 property=foo
  security: runtime READ_PROPERTY linkage check
```
