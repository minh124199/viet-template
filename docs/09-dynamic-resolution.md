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

## 4. PIC

Cache a small number of shapes:

```text
User      -> getName()
AdminUser -> getName()
Map       -> get("name")
```

Benchmark depth roughly 3–6. After threshold, use bounded megamorphic cache keyed by class.

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
