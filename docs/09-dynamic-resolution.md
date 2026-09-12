# 09 — Dynamic Resolution, MethodHandles and Inline Caches

## 1. Objective

Unknown model types should pay discovery cost when a new receiver shape appears, not on every render.

```velocity
$user.name
```

with unknown `user` becomes a dynamic call site with stable id.

## 2. Call-site identity

```java
record PropertyCallSiteKey(
    int templateSiteId,
    String property,
    AccessPolicyId policyId,
    CompatibilityProfileId compatibility
) {}
```

Never use a global property-name-only cache because policy and semantics matter.

## 3. Monomorphic cache

State:

```java
Class<?> receiverClass;
MethodHandle accessor;
```

Fast path:

```text
receiver.class == cachedClass -> invoke cached accessor
otherwise -> relink
```

## 4. Polymorphic Inline Cache (PIC)

For call sites encountering multiple receiver shapes, `DynamicCallSite` implements a Polymorphic Inline Cache (PIC) with a maximum depth of 4:

```text
User      -> getName()
AdminUser -> getName()
Map       -> get("name")
```

### Representation & Contiguous Array Scanning

Rather than using a `HashMap<Class<?>, AccessLink>` or a concurrent node map, the PIC is implemented using a copy-on-write contiguous array:

```java
private volatile AccessLink[] polymorphicLinks; // max length = 4
```

On each invocation, the site scans the array sequentially:

```java
AccessLink[] links = this.polymorphicLinks;
for (int i = 0; i < links.length; i++) {
    AccessLink link = links[i];
    if (link.receiverClass() == receiverClass) {
        return link.invoker().invokeExact(receiver);
    }
}
return handleMiss(receiverClass, receiver);
```

### Architectural Justification of Contiguous Array Scanning over HashMap

This design is strictly guided by the Core Performance Engineering Principle, the Four-Tier Implementation Preference Hierarchy, and the 7-part DSA acceptance rule (see [15 — Benchmark and Performance Engineering Plan](15-benchmark-plan.md)):

1. **Low Constant Factors & Memory Locality**:
   - Small contiguous arrays provide sequential access with low constant factors and good memory locality, avoiding pointer chasing across non-contiguous heap nodes.
   - In contrast, a `HashMap` requires dereferencing the table array, traversing separate `Node` objects, and chasing pointers across non-contiguous heap regions.
2. **Zero Hashing Overhead**:
   - Array traversal performs direct reference equality checks (`link.receiverClass() == receiverClass`).
   - A `HashMap` requires calling `Class.hashCode()`, integer spreading, modulo index masking, and bucket pointer dereferencing before the identity check can occur.
3. **Bounded Traversal & Simple Predictability**:
   - Bounded array traversal over $N \le 4$ elements offers highly predictable control flow with low execution overhead.
   - For call sites that are monomorphic or bimorphic in practice (the vast majority of real-world template sites), traversal terminates immediately on index 0 or 1.
4. **Balanced Allocation Tradeoff**:
   - The array is updated via copy-on-write only when a new receiver shape is encountered, producing no ongoing auxiliary node allocations during steady-state rendering. Tradeoffs are evaluated under the balanced performance tradeoff rule across throughput, latency, allocations, and complexity.
5. **Megamorphic Fallback**:
   - If a call site encounters more than 4 distinct receiver shapes, it ceases array expansion and delegates to `BoundedWeakClassCache`. This bounds the worst-case linear scan to at most 4 comparisons before delegating to the bounded cache.

## 5. Linker

```java
AccessLink linkProperty(
    Class<?> receiverType,
    String property,
    DynamicAccessRequest request,
    TemplateSecurityPolicy policy
)
```

Order:

1. reject forbidden receiver/type families;
2. explicit extension lookup;
3. selected safe/Velocity property semantics;
4. capability decision;
5. build/adapt MethodHandle;
6. cache approved link.

## 6. Uniform signature

Initial generic signature can be `(Object)->Object`. It boxes primitives, but dynamic mode is not the typed hot path. Add primitive-specialized links only with evidence.

## 7. `invokedynamic`

Potential second-stage backend:

```text
invokedynamic get:name (Object)Object
bootstrap=VietTemplateBootstrap.bootstrapProperty
```

Bootstrap can install class guards and fallback linker.

Benefits: JVM-native dynamic call sites, compact generated code, potential JIT optimization.

Costs: complexity, invalidation/security correctness, native-image concerns. Therefore implement explicit MethodHandle PIC first and compare.

## 8. Invalidation

Production policy/extension registries should be immutable after engine construction. Dev mode can swap a whole registry/template generation or use generation ids.

## 9. Dynamic methods

Cache key includes method name, arity, runtime argument types, conversion profile and security policy. Overload resolution must be deterministic and TCK-tested.

## 10. Maps

Map lookup is first-class, not reflection. Velocity compatibility decides precedence against object getters where relevant.

## 11. Null receiver

Null semantics are already encoded in analyzed IR: strict error, quiet/propagate, or migration-compatible behavior. Linker never decides null semantics ad hoc.

## 12. Security invariant

**A cached access is a cached approved capability decision.** Any policy identity change invalidates that access.

## 13. Metrics

Optional low-overhead counters:

```text
viet.template.dynamic.link
viet.template.dynamic.pic.hit
viet.template.dynamic.pic.miss
viet.template.dynamic.megamorphic
viet.template.dynamic.denied
```

## 14. Benchmark matrix

Reflection, cached `Method`, MethodHandle, monomorphic cache, PIC, megamorphic cache and optional `invokedynamic`; test monomorphic, 2/4-type polymorphic, 20-type megamorphic, map and method-call sites.
