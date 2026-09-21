# Typed Models & Semantic Analysis Guide

## 1. Overview & Conceptual Architecture

While standard VTL executes dynamically, Viet Template features a static **Semantic Analyzer** and **Type System** (`VType`) capable of performing compile-time verification, type inference, and Ahead-of-Time (AOT) direct bytecode generation.

When templates include typed model declarations:
1. **Compile-Time Diagnostics**: Property typos, non-existent methods, and type mismatches are caught during Maven/Gradle builds rather than during production HTTP requests.
2. **Zero-Reflection AOT Execution**: The compiler emits direct JVM bytecode instructions (`invokevirtual`, `invokeinterface`, `invokestatic`), bypassing dynamic call sites, polymorphic inline cache (PIC) array scanning, and boxing overhead.
3. **IDE Autocompletion & Tooling Parity**: Typed variable annotations follow industry conventions recognized by IntelliJ IDEA and Eclipse VTL plugins.

---

## 2. Declaring Typed Model Variables

To declare the type of a root context variable, use the standard VDoc annotation comment syntax at the top of your template:

```vtl
#* @vtlvariable name="user" type="com.example.dto.UserDto" *#
#* @vtlvariable name="orders" type="java.util.List<com.example.dto.OrderDto>" *#

<h1>Welcome, $user.displayName!</h1>

<ul>
#foreach($order in $orders)
    <li>Order #$order.id - Amount: $order.formattedAmount</li>
#end
</ul>
```

### 2.1 Supported Types in `VType`
- **Primitives & Wrappers**: `int`, `long`, `boolean`, `double`, `java.lang.String`, etc.
- **Java Records & POJOs**: Custom DTOs and entity classes.
- **Generic Collections & Maps**: `java.util.List<T>`, `java.util.Map<K, V>`, `java.util.Set<T>`.
- **Arrays**: `T[]`, `int[]`, `byte[]`.
- **Containers**: `java.util.Optional<T>`.

---

## 3. Compile-Time Semantic Diagnostics

When templates are compiled via `viet-template-maven-plugin` or `viet-template-gradle-plugin`, the semantic analyzer validates references against the declared types:

| Diagnostic Code | Name | Description | Example Trigger |
|---|---|---|---|
| `VTLS:2101` | `UNRESOLVED_ROOT` | Reference to a root variable that has no declaration or binding. | `$unknownVar` in strict model mode. |
| `VTLS:2102` | `INVALID_ASSIGNMENT` | Incompatible type assigned to a variable via `#set`. | `#set($user = 123)` where `$user` is typed. |
| `VTLS:2103` | `TYPE_MISMATCH` | Argument type does not match method signature. | `$calc.add("text", 42)` where `add(int, int)` expected. |
| `VTLS:2104` | `PROPERTY_NOT_FOUND` | Property does not exist on the declared receiver class. | `$user.nonExistentField` |
| `VTLS:2105` | `METHOD_NOT_FOUND` | Method name or arity does not match declared methods. | `$user.calculate(1, 2, 3)` |
| `VTLS:2106` | `INVALID_ITERABLE` | Target of `#foreach` is not an iterable, collection, or array. | `#foreach($x in $user.age)` where age is `int`. |
| `VTLSEC:2401` | `SECURITY_DENIED` | Method or property access violates `MemberAccessPolicy`. | `$user.getClass()` |

---

## 4. Typed vs Dynamic Resolution: Performance & AOT

### 4.1 Dynamic Resolution Path (Untyped)
When variables are undeclared:
1. The engine checks root `RenderContext` by variable name.
2. Property/method lookups dispatch through `DynamicCallSite`.
3. An internal polymorphic inline cache (PIC) scans receiver classes linearly.
4. Megamorphic dispatch escalates to a bounded weak class cache.

### 4.2 Direct Bytecode Invocation Path (Typed)
When variables are statically typed:
1. The compiler resolves the exact `Method` or `Field` descriptor during build-time AOT compilation.
2. The compiler emits direct bytecode:
   ```bytecode
   aload 1             // Load user DTO from frame slot
   checkcast com/example/dto/UserDto
   invokevirtual com/example/dto/UserDto.getDisplayName:()Ljava/lang/String;
   ```
3. Runtime call-site lookups, reflection checks, and hash probes are completely bypassed.

---

## 5. Graceful Dynamic Fallback

You do **not** need to type every variable in your application:
- Templates without `@vtlvariable` annotations compile and execute dynamically with zero friction.
- You can mix typed and untyped variables in the same template. Variables without static type annotations seamlessly use the dynamic linker path.

### When Should You Use Typed Models?
- **High-Throughput Hotspots**: Product listing pages, search result tables, and high-frequency JSON/HTML APIs.
- **Enterprise Maintenance**: Large codebases where refactoring a Java DTO field should surface compile-time errors in templates rather than runtime breakages.
