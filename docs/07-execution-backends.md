# 07 — Execution Backends

## 1. Contract

All execution backends consume the same verified optimized IR and must produce equivalent semantics.

```java
public interface TemplateBackend {
    BackendId id();
    BackendCapabilities capabilities();
    BackendResult compile(IrTemplate template, BackendOptions options);
}
```

## 2. Interpreter

Purpose: bootstrap correctness, semantic oracle, development fallback, unsupported dynamic features and debugging.

Once IR exists, interpret IR rather than AST.

Initial frame may use simple locals:

```java
final class InterpretedFrame {
    Object[] locals;
    RenderContext context;
    TemplateOutput output;
    RenderBudget budget;
}
```

Optimize interpreter only after compiled path exists.

## 3. Optimized dynamic backend

For unknown model types:

- compile static control/output plan;
- use cached member linkage;
- specialize by observed receiver class;
- enforce security during linkage;
- avoid repeated resolver searches.

Implementation order:

1. explicit `MethodHandle` monomorphic/PIC cache;
2. benchmark;
3. only then prototype `invokedynamic` if it yields measurable benefit.

## 4. AOT bytecode backend

Purpose: maximum production throughput, low allocation, build-time validation and native-friendly direct access.

Conceptual generated class:

```java
final class T_orders_list_8af31 implements CompiledTemplate {
    @Override
    public void render(RenderContext context, TemplateOutput out) {
        User user = (User) context.get("user");
        List<Order> orders = (List<Order>) context.get("orders");
        // direct calls/writes
    }
}
```

Preferred typed facade removes context map entirely:

```java
void render(User user, List<Order> orders, TemplateOutput out)
```

## 5. Bytecode implementation

Keep backend behind SPI.

### JDK 25 backend

Use JDK's standard `java.lang.classfile` API in a Java-25-specific compiler module.

Pros: JDK-owned API, no third-party bytecode dependency in that module, direct class-file model.

Constraint: build JVM must support the API. Generated target/runtime combinations must be verified empirically in CI.

### Alternative backend

If users need build-time compilation on older JDKs, add ASM or generated-Java backend without changing engine semantics.

## 6. Generated-code rules

- generated classes depend on runtime + user model + JDK only;
- static literals are bulk writes;
- direct typed access uses normal JVM calls;
- frequently used values occupy locals;
- primitive values use primitive output methods;
- no reflection in fully typed path.

## 7. Development class loading

Prefer a generation-level child classloader rather than one classloader per template. Hot reload swaps generation atomically and permits old loader collection after in-flight renders complete.

Hidden classes can be explored for ephemeral runtime compile, but are not required for v1.

## 8. Verification

Tests load generated classes with verifier enabled, run every control-flow form, compare interpreter output, inspect bytecode for reflection absence on typed path, and track class/method size.

## 9. Capability result

Compilation should report:

```text
AOT_OK
AOT_OK_WITH_DYNAMIC_SITES
INTERPRETER_REQUIRED_EVALUATE
INTERPRETER_REQUIRED_RUNTIME_MACRO
DENIED_SECURITY
UNSUPPORTED_LANGUAGE_FEATURE
```

Build tools support `failOnFallback=true`.

## 10. Parity contract

Backends agree on output, null/truthiness, loop order, mutation semantics, include/parse, macros, error category/source location and security decisions.
