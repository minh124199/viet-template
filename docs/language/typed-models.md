# Typed Models & Semantic Analysis Guide

The plugin coordinate shown in the build example is `1.1.0`, the current release-preparation version. It is not published yet; `1.0.1` remains the latest version available from Maven Central.

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
| `VTLS:2107` | `NULLABLE_DEREFERENCE` | Nullable receiver dereferenced without quiet reference syntax. | `$user.name` where `$user` is nullable. |
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

---

## 6. Strict Typed Mode & Build-Time Contract Diagnostics (M26)

Viet Template provides an opt-in static contract verification mode governed by `TypeCheckingMode`:
- **`OFF` (Default)**: Normal Velocity-compatible behavior. Unresolved properties, methods, or roots fall back cleanly to dynamic execution without diagnostics.
- **`WARN`**: Provable contract inconsistencies emit compile-time warning diagnostics, while allowing compilation and artifact generation to succeed.
- **`ERROR`**: Provable contract inconsistencies emit compile-time error diagnostics and fail template compilation.

### 6.1 Guiding Principle: Reject What Can Be Proven Wrong

The central invariant is:
> **Reject what can be proven wrong. Do not reject merely because the compiler cannot prove something right.**

Viet Template strictly distinguishes between:
- `PROVEN_VALID`: Statically resolved against the contract (e.g. record component, getter, bean property, direct field). Specializes to direct bytecode.
- `PROVEN_INVALID`: Statically proven impossible under runtime semantics (e.g. non-existent property on a sealed/final record or DTO with typo suggestions, method arity mismatch, provably incompatible argument types). Diagnosed under `WARN` and `ERROR`.
- `UNKNOWN / DYNAMIC`: Not statically knowable (e.g. untyped dynamic roots, `Map` lookups, overloaded methods with ambiguous arguments, unresolved type variables, macro parameters). **Never rejected as invalid**; falls back to runtime dynamic dispatch.

### 6.2 Contract Completeness Semantics

- When a template has an associated `TemplateContract`, strict mode (`WARN` / `ERROR`) treats the contract as authoritative for root variables: undeclared root references emit `VTLS:2101 (UNRESOLVED_ROOT)`.
- If no contract is provided (`ModelSchema.empty()`), strict checking remains permissive (`UNKNOWN / DYNAMIC`), preventing false positives for dynamic templates.
- Variables scoped within the template—such as loop variables (`#foreach`), locals (`#set`), and macro parameters (`#macro`)—are recognized by the symbol table and never falsely diagnosed as undeclared roots.

### 6.3 Nullable Navigation Analysis & Flow Refinements

When a parameter or property is declared nullable (`nullable=true`):
- Dereferencing a nullable target (`$user.name`) emits an advisory `VTLS:2107` warning under `WARN` and `ERROR` mode. Because Viet Template is null-tolerant, this warning is advisory and does not fail compilation.
- **Quiet Reference Suppression**: Syntax explicitly intended for null suppression (`$!user.name`) suppresses the nullable dereference warning.
- **Flow Refinement**: Guarding a reference with `#if($user)` or `#if($user != null)` automatically refines the nullability in the branch body, suppressing nullable warnings.

### 6.4 Security Policy Precedence

Security checks take precedence over typo suggestions:
- Attempting to access restricted members (such as `getClass()`, `ClassLoader`, or denied classes/methods under `MemberAccessPolicy`) emits `VTLSEC:2401 (SECURITY_DENIED)`.
- Typo suggestion algorithms (Levenshtein distance) strictly filter out restricted members, ensuring policy secrets are never leaked.

### 6.5 Maven & Gradle Configuration

#### Apache Maven (`pom.xml`)
```xml
<plugin>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-maven-plugin</artifactId>
    <version>1.1.0</version>
    <configuration>
        <!-- Type checking mode: OFF (default), WARN, or ERROR -->
        <typeChecking>ERROR</typeChecking>
    </configuration>
</plugin>
```

#### Gradle (`build.gradle.kts`)
```kotlin
vietTemplate {
    // Type checking mode: OFF (default), WARN, or ERROR
    typeChecking.set("ERROR")
}
```
