# 05 — Template Intermediate Representation

## 1. Goals

The IR is the backend boundary. It must preserve types, null/security semantics and source locations while removing VTL syntax quirks.

It should be easy to interpret, optimize, verify and compile to JVM bytecode.

## 2. Structure

```java
record IrTemplate(
    TemplateId id,
    List<IrParameter> parameters,
    IrBlock root,
    List<IrConstant> constants,
    TemplateMetadata metadata
) {}
```

Use structured IR first; SSA is unnecessary for the initial compiler.

## 3. Statements

```text
IrWriteConst
IrWriteValue
IrStoreLocal
IrIf
IrLoop
IrCallTemplate
IrCallMacro
IrBreak
IrStop
IrBudgetCheck
IrNoOp
```

Expressions:

```text
IrConst
IrLoadParameter
IrLoadLocal
IrGetProperty
IrGetIndex
IrInvoke
IrUnary
IrBinary
IrConvert
IrIsNull
IrTruthiness
IrDynamicGet
IrDynamicInvoke
```

## 4. Constant table

```java
record IrTextConstant(
    int id,
    String text,
    Optional<byte[]> utf8,
    SourceSpan span
) {}
```

Adjacent literal text collapses before constant finalization. Pre-encode UTF-8 only for modes that use it.

## 5. Write operation

```java
record IrWriteValue(
    IrExpression value,
    EscapeMode escapeMode,
    NullRenderMode nullMode,
    SourceSpan span
) implements IrStatement {}
```

Escaping and null behavior are explicit and optimization-safe.

## 6. Property operation

```java
record IrGetProperty(
    IrExpression receiver,
    VType resultType,
    AccessPlan accessPlan,
    NullAccessMode nullMode,
    SourceSpan span
) implements IrExpression {}
```

Access plans:

```text
DirectVirtual(owner,name,descriptor)
DirectInterface(owner,name,descriptor)
DirectRecord(owner,name,descriptor)
MapLookup(keyConstant)
DynamicCallSite(callSiteId, propertyName)
ExtensionCall(...)
```

## 7. Loop operation

```java
record IrLoop(
    LoopPlan plan,
    int elementSlot,
    int loopStateSlot,
    IrBlock body,
    IrBlock elseBody,
    LoopLimits limits,
    SourceSpan span
) implements IrStatement {}
```

Plans:

```text
ArrayLoop
ListIndexedLoop
IterableLoop
IteratorLoop
DynamicLoop
```

Loop metadata should map to primitive locals when `$foreach` metadata is used, not necessarily a heap object.

## 8. Example lowering

```velocity
#if($user.admin)
A
#else
B
#end
```

```text
IF TRUTHY(GET_PROPERTY(LOAD_PARAM user, admin))
  THEN WRITE_CONST A
  ELSE WRITE_CONST B
```

## 9. Parse/include

Static parsed template:

```text
CALL_TEMPLATE partials/header
```

Optional optimization may inline small private partials. Dynamic targets become explicit runtime template lookup operations and are forbidden in strict AOT unless declared.

## 10. Macros

Represent static macros as IR functions:

```java
record IrFunction(
    FunctionId id,
    List<IrParameter> parameters,
    List<IrLocal> locals,
    IrBlock body,
    SourceSpan definitionSpan
) {}
```

## 11. Control signals

Represent `BREAK` and `STOP_RENDER` directly. Do not compile ordinary control flow as heavyweight exceptions.

## 12. Source mapping

Every instruction that can fail user-visibly retains source span id. Generated runtime errors map back to template/macro/include frame.

## 13. Verification

IR verifier checks:

- local definition before read;
- type compatibility;
- valid branch/loop targets;
- denied capabilities absent;
- no dynamic op in strict AOT;
- valid constant/resource ids;
- valid escape modes;
- resolved static template dependencies.

## 14. Serialization

Internal build cache format may be serialized but is not a v1 public contract. Include magic/version/source/schema/policy/compiler fingerprints and invalidate aggressively across compiler changes.

## 15. Variable Slot Assignment (0.2.0)

Deterministic, stable integer slots are assigned to parameters, locals, and loop variables:
- `IrSlotLayout`: Computes frame size, active slot map, context-seeded slots, and loop-owned locals for templates and functions.
- `SlotMetadata`: Retains slot ID, identifier name, `BindingKind`, `InitializationPolicy`, and source span.
- Slots are monotonic and deterministic across compilations; slot reuse is deferred.
- Verified by mandatory pass `O45 AssignVariableSlots` and `IrVerifier`.
