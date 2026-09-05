# 04 — Semantic Analysis and Type System

## 1. Purpose

The semantic layer is where most of the performance advantage is created. A property chain should be converted from syntax into an executable access plan before render time whenever type information exists.

Example:

```velocity
$user.address.city
```

Analyzed form:

```text
user       : User       NON_NULL
.address   : Address    NULLABLE   DIRECT_GETTER User::getAddress
.city      : String     NULLABLE   RECORD_COMPONENT Address::city
```

## 2. Type model

Do not use `Class<?>` as the entire type system.

```java
sealed interface VType { Nullability nullability(); }
record PrimitiveType(PrimitiveKind kind) implements VType {}
record ClassType(ClassDesc name, List<VType> args, Nullability nullability) implements VType {}
record ArrayType(VType component, Nullability nullability) implements VType {}
record DynamicType(Nullability nullability) implements VType {}
record NullType() implements VType {}
record UnionType(List<VType> options, Nullability nullability) implements VType {}
record ErrorType() implements VType {}
```

`ClassDesc` here is conceptual; implementation may use a project-owned type descriptor to keep Java-17 modules independent of newer APIs.

## 3. Model declarations

### Java model interface

```java
@TemplateModel("orders/list.vm")
public interface OrdersListModel {
    User user();
    List<Order> orders();
}
```

### Typed template interface

```java
@Template("orders/list.vm")
public interface OrdersListTemplate {
    void render(User user, List<Order> orders, TemplateOutput out);
}
```

### Build descriptor

```yaml
templates:
  orders/list.vm:
    parameters:
      user: com.acme.User
      orders: java.util.List<com.acme.Order>
```

### Dynamic API

```java
engine.get("orders/list").render(Context.of(
    "user", user,
    "orders", orders
), out);
```

Unknown roots receive `DynamicType` unless schema metadata exists.

## 4. Scopes

```text
ROOT_MODEL
LOCAL
LOOP
MACRO
INCLUDE_PARSE
BUILTIN
```

Modern semantics:

- root model names immutable unless shadowing is explicit;
- `#set` creates/updates template locals;
- loop variable and loop metadata are scoped to loop;
- macro parameters are local;
- parse/include context behavior is profile-defined.

Migration semantics may broaden mutation to match Velocity behavior but should not leak into typed safe mode.

## 5. Binding phases

1. Resolve root/local symbols.
2. Resolve members/indexes/methods.
3. Resolve operators/conversions.
4. Perform flow-sensitive null/type refinement where safe.
5. Apply security capability decisions.
6. Produce access plans and diagnostics.

Example:

```velocity
#if($user != $null)
  $user.name
#end
```

can refine `$user` to non-null inside the branch.

## 6. Safe typed member resolution

Recommended property order:

1. record component exact name;
2. JavaBean `getX()`;
3. boolean `isX()`;
4. explicitly exported field;
5. registered extension property;
6. map key only for Map/map-like type;
7. not found.

Do not call arbitrary zero-argument methods merely because their names match a property in safe mode.

## 7. Velocity compatibility binding

Use a separate binder implementing tested Velocity lookup ordering and conversions. Once resolved, record a concrete access plan so runtime does not repeat lookup.

## 8. Method invocation binding

```java
record BoundCall(
    MethodSignature signature,
    InvocationKind invocationKind,
    List<ConversionPlan> argumentConversions,
    CapabilityDecision capability,
    VType returnType
) {}
```

Method calls must be policy-governed separately from property reads.

## 9. Conversion categories

```text
IDENTITY
REFERENCE_UPCAST
BOX
UNBOX
NUMERIC_WIDEN
STRING_PARSE_COMPAT
TO_STRING
DYNAMIC
INVALID
```

Safe typed mode is conservative. Migration mode may reproduce broader Velocity conversions only when verified.

## 10. Null semantics

Every access/write carries explicit mode:

```text
STRICT
QUIET_REFERENCE
PROPAGATE
LEGACY
```

Backends must not invent different null behavior.

## 11. Truthiness specialization

Resolve by static type:

```text
Boolean      -> direct value
int/long     -> != 0
String       -> !isEmpty()
Collection   -> !isEmpty()
Map          -> !isEmpty()
array        -> length != 0
Object       -> non-null under strict policy
Dynamic      -> runtime TruthinessStrategy
```

This removes generic truthiness dispatch from typed branches.

## 12. Arithmetic typing

For:

```velocity
$order.quantity * $order.unitPrice
```

with `int` × `long`:

```text
LOAD_I32 quantity
CONVERT_I64
LOAD_I64 unitPrice
MUL_I64
```

Typed v1 should use Java primitive overflow semantics unless a future explicit checked-math mode is added.

## 13. String concatenation

If concatenation is rendered immediately, lower to streaming writes when semantics permit rather than materializing an intermediate `String`.

## 14. Collection element typing

- `List<Order>` -> loop variable `Order`.
- raw `List` -> `Dynamic`.
- array -> component type.
- `Map<K,V>` iteration behavior defined by compatibility spec, not guessed.

## 15. Capability analysis

Actions:

```text
READ_PROPERTY
READ_INDEX
CALL_METHOD
MUTATE_PROPERTY
MUTATE_INDEX
LOAD_TEMPLATE_STATIC
LOAD_TEMPLATE_DYNAMIC
INCLUDE_RESOURCE
EVALUATE_SOURCE
ACCESS_FRAMEWORK_OBJECT
RAW_OUTPUT
```

Denied static actions fail compilation. Dynamic actions carry runtime linkage checks.

## 16. Diagnostics

```text
VTLS2104 property 'nmae' does not exist on com.acme.User
 --> user.vm:4:9
4 | Hello $user.nmae
  |             ^^^^
help: did you mean 'name'?
```

```text
VTLSEC2401 method calls are disabled by SAFE policy
 --> user.vm:8:3
8 | $user.deleteAll()
  |       ^^^^^^^^^^^
help: export an approved template method or move this operation to application code
```

## 17. Semantic cache key

Include at least:

```text
sourceHash
compatibilityProfileVersion
modelSchemaFingerprint
extensionRegistryFingerprint
securityPolicyFingerprint
escapePolicy
featureFlags
```
