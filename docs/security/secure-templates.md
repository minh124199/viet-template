# Secure Templates & Sandboxing Guide

## 1. Threat Model & Security Principles

Viet Template is engineered from the ground up with defense-in-depth security principles. In web and enterprise applications, template engines frequently process data models originating from external clients or render templates edited by non-developer users.

Viet Template distinguishes between two fundamentally different threat postures:

1. **Trusted Templates with Untrusted Data**:
   Templates are maintained by developers in application source code, but data models contain user-supplied strings, form inputs, or database entities.
   *Primary threats*: Cross-Site Scripting (XSS), prototype pollution, sensitive data leakage, reflection traversal.
2. **Untrusted / Multi-Tenant Templates**:
   Templates are authored or customized by end-users or external tenants (e.g. CMS, reporting, email builders).
   *Primary threats*: Remote Code Execution (RCE), Denial of Service (infinite loops, CPU exhaustion, heap exhaustion), arbitrary file read, reflection sandbox breakout.

---

## 2. Member Access Policies (`MemberAccessPolicy`)

The `MemberAccessPolicy` interface governs class, property, field, and method resolution across both dynamic AST/IR interpretation and Ahead-of-Time (AOT) compiled bytecode.

### 2.1 Available Security Profiles

| Policy Profile | Factory Method | Behavior | Target Use Case |
|---|---|---|---|
| **Standard Profile** | `MemberAccessPolicy.standard()` | Default-deny policy blocking Java reflection (`Class`, `Method`, `ClassLoader`), OS execution (`Runtime`, `ProcessBuilder`), threading, and system internals. | Production applications with developer-authored templates. |
| **Safe Profile** | `MemberAccessPolicy.safe()` | Strict **allowlist** sandbox. Only primitives, boxed types, strings, standard collections, maps, records, classes annotated with `@TemplateData`, and explicitly registered members are accessible. | Multi-tenant applications, user-authored email templates, or CMS platforms. |
| **Deny-All Profile** | `MemberAccessPolicy.denyAll()` | Unconditionally denies all reflection and member accesses. Only root variables and string literals can be rendered. | Maximum-isolation static text expansion. |

### 2.2 Configuring `MemberAccessPolicy`

When configuring a standalone `TemplateEngine`:

```java
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateEngine;

// Option A: Standard defense-in-depth policy (Default)
TemplateEngine standardEngine = TemplateEngine.builder()
    .repository(repository)
    .memberAccessPolicy(MemberAccessPolicy.standard())
    .build();

// Option B: Strict allowlist sandbox for untrusted templates
TemplateEngine safeEngine = TemplateEngine.builder()
    .repository(repository)
    .memberAccessPolicy(MemberAccessPolicy.safe())
    .build();

// Option C: Custom policy with explicit allowlist additions
MemberAccessPolicy customPolicy = MemberAccessPolicy.builder()
    .allowClass(com.example.dto.UserProfile.class)
    .allowMethod(com.example.dto.UserProfile.class, "getDisplayName")
    .denyMethod("getInternalPasswordHash")
    .build();

TemplateEngine customEngine = TemplateEngine.builder()
    .repository(repository)
    .memberAccessPolicy(customPolicy)
    .build();
```

#### Complete `MemberAccessPolicy.Builder` Methods Reference

The `MemberAccessPolicy.Builder` provides comprehensive controls for tuning sandboxing policies:
- `allowClass(Class<?> clazz)`: Grants access to all permitted methods/properties of the specified class.
- `allowClasses(Class<?>... classes)`: Grants access to multiple classes simultaneously.
- `allowMethod(Class<?> clazz, String methodName)`: Permits invocation of a specific method on a class.
- `allowProperty(Class<?> clazz, String propertyName)`: Permits reading a specific property getter on a class.
- `allowHelper(Class<?> helperClass)`: Registers a helper class whose static methods are accessible.
- `allowHelperMethod(Class<?> helperClass, String methodName)`: Registers a specific static helper method.
- `allowPropertyMutation(Class<?> clazz, String propertyName)`: Permits in-template property mutation (`#set($target.prop = ...)`) for a specific property.
- `allowIndexMutation(Class<?> clazz)`: Permits in-template index mutation (`#set($target[idx] = ...)`) for a class.
- `allowAllPropertyMutations(boolean allow)`: Globally toggles permission for in-template property mutations.
- `allowAllIndexMutations(boolean allow)`: Globally toggles permission for in-template index mutations.
- `denyClass(Class<?> clazz)`: Explicitly denies all access to the specified class.
- `denyPackage(String packagePrefix)`: Explicitly denies all classes under a package prefix.
- `denyMethod(String methodName)`: Denies any method matching the method name across all classes.
- `sensitiveClassifier(SensitiveObjectClassifier classifier)`: Configures custom logic for identifying sensitive runtime objects.
- `safeProfile(boolean safeProfile)`: Switches builder defaults between standard fail-closed and safe sandbox baselines.

### 2.3 The `@TemplateData` Annotation

In strict safe mode (`MemberAccessPolicy.safe()`), custom domain classes are blocked by default unless marked with `@TemplateData` or explicitly registered on the policy:

```java
import io.github.minh124199.viettemplate.api.TemplateData;

@TemplateData
public record InvoiceSummary(String invoiceNumber, BigDecimal total, LocalDate issueDate) {
    // Record components are accessible in safe mode
}
```

> [!IMPORTANT]
> **Safe Mode Accessibility Constraints**:
> In safe mode (`MemberAccessPolicy.safe()`), `@TemplateData` does **not** expose arbitrary public methods:
> - **JavaBeans**: Only JavaBean getter accessors (`get<Name>()`, `is<Name>()`) and record components/accessors are accessible.
> - **Explicit Callables**: Non-getter methods must be annotated with `@TemplateCallable` or explicitly permitted via `allowMethod(...)`.
> - **Action/Mutation Denial**: Arbitrary public action methods (e.g. `delete()`, `execute()`) and mutating methods remain strictly denied.

---

## 3. Resource & Execution Confinement (`RenderBudget`)

To prevent Denial of Service (DoS) attacks caused by infinite loops, deeply nested macros, or malicious memory exhaustion, Viet Template provides monotonic execution budgets via `RenderBudget` in package `io.github.minh124199.viettemplate.runtime`.

```java
import io.github.minh124199.viettemplate.runtime.RenderBudget;

RenderBudget budget = new RenderBudget(
    1_000_000L, // maxOutputCharacters: Max rendered output characters (1 MB)
    5_000L,     // maxExecutionTimeMillis: Max execution deadline (5,000 ms = 5s)
    100_000     // maxLoopIterations: Max iterations across all #foreach loops
);
```

For unconstrained development scenarios, `RenderBudget.unlimited()` creates an unrestricted budget instance.

### 3.1 Enforced Limits & Diagnostic Codes

1. **Execution Time Deadline (`LIMIT:TIME_LIMIT_EXCEEDED`)**:
   Guards against regex catastrophic backtracking, slow dynamic calls, or recursive macro depth. When elapsed time exceeds the deadline, rendering immediately aborts, throwing `TemplateLimitException` with diagnostic code `LIMIT:TIME_LIMIT_EXCEEDED`.
   > [!NOTE]
   > Deadline checks occur cooperatively at engine **safe points** (such as loop iteration steps and character consumption checks), rather than through OS-level preemption or asynchronous thread interruption.
2. **Output Character Limit (`LIMIT:LIMIT_EXCEEDED`)**:
   Guards against memory exhaustion attacks where loops generate gigabytes of repetitive text. Exceeding the character cap immediately aborts, throwing `TemplateLimitException` with diagnostic code `LIMIT:LIMIT_EXCEEDED`.
3. **Loop Iteration Cap (`LIMIT:LIMIT_EXCEEDED`)**:
   Monotonically counts loop steps across all `#foreach` invocations using saturating arithmetic. Exceeding the threshold aborts execution with `LIMIT:LIMIT_EXCEEDED`.

---

## 4. Path Traversal & Filesystem Confinement

When loading templates from the filesystem via `FilesystemTemplateRepository`, Viet Template verifies that every template identifier resolves within the configured base directory:

- **Path Normalization & Traversal Rejection**: Template identifiers containing relative traversal sequences (`..`), null bytes (`\0`), or backslash path escapes are rejected immediately with `TemplateResourceException` (`RESOURCE:NOT_FOUND`).
- **Canonical Path Containment**: Resolves real canonical paths to ensure symbolic links escaping the repository root fail closed unless explicitly allowed by the host environment.
- **Concurrency & Confinement**: Path resolution guarantees consistent containment checks even under concurrent lookups.

```java
// Secure: Confined to /app/templates
FilesystemTemplateRepository repo = FilesystemTemplateRepository.of(Path.of("/app/templates"));

// The following lookups fail closed and throw TemplateResourceException:
// engine.get("../../../etc/passwd");
// engine.get("..\\Windows\\win.ini");
// engine.get("templates\0secret.vtl");
```

---

## 5. Output Escaping, `SafeHtml` & `SafeUrl`

Viet Template implements **contextual auto-escaping** on all reference insertions by default:

```vtl
<p>User: $username</p>
```
If `$username` contains `<script>alert(1)</script>`, it is automatically escaped to `&lt;script&gt;alert(1)&lt;/script&gt;`.

### 5.1 High-Performance Zero-Allocation Escaping
Unlike legacy templating engines that generate intermediate `String.replace()` or substring allocations, Viet Template's `HtmlTextEscaper` streams character ranges directly into the output buffer (`TemplateOutput`), delivering zero heap allocation during escaping.

### 5.2 Emitting Trusted Content: `SafeHtml` & `SafeUrl`

When pre-verified or trusted content must be rendered without default escaping, Viet Template provides explicit capability wrappers in package `io.github.minh124199.viettemplate.runtime`:

#### `SafeHtml` (Trust Wrapper for HTML Markup)
- **Record**: `io.github.minh124199.viettemplate.runtime.SafeHtml`.
- **Capability Scope**: Bypasses `HTML_TEXT` auto-escaping only.
- **Active Protections in Other Contexts**: When placed inside `HTML_ATTRIBUTE_QUOTED`, the content remains entity-escaped to prevent attribute delimiter breakout.
- **Not a Sanitizer**: `SafeHtml` simply wraps a `CharSequence`. The host application asserts that markup has already been sanitized (e.g. via OWASP Java HTML Sanitizer) or originates from a trusted compile-time constant.

```java
import io.github.minh124199.viettemplate.runtime.SafeHtml;

context.put("formattedArticle", SafeHtml.of("<p>Sanitized <em>article</em> body</p>"));
```

#### `SafeUrl` (Validated URL Wrapper)
- **Class**: `io.github.minh124199.viettemplate.runtime.SafeUrl`.
- **Capability Scope**: Bypasses RFC 3986 percent-encoding in `URL_COMPONENT` output context only.
- **Scheme Validation**: `SafeUrl.of(url)` and `SafeUrl.tryOf(url)` validate the scheme against an approved safe allowlist (`http`, `https`, `mailto`, `tel`, and safe relative paths) via `SafeUrlValidator`, rejecting dangerous protocols (`javascript:`, `data:`) and obfuscation.
- **Unchecked Escape Hatch**: `SafeUrl.ofTrusted(url)` bypasses validation where the caller takes full responsibility for safety.
- **Remaining Protections**: In `HTML_TEXT` or `HTML_ATTRIBUTE_QUOTED`, `SafeUrl` is still entity-escaped to prevent tag and attribute delimiter breakouts.

```java
import io.github.minh124199.viettemplate.runtime.SafeUrl;

context.put("profileLink", SafeUrl.of("https://example.com/users?id=123"));
```

---

## 6. Spring Security Integration Sandboxing

In standard Spring MVC applications, templates frequently reference security context variables. Exposing raw `SecurityContext` or `Authentication` objects risks exposing sensitive credentials, password hashes, or internal authentication provider details.

`viet-template-spring-security` solves this by injecting read-only facades:

1. **`$security` (`SecurityView`)**:
   Exposes only:
   - `getName()`: Authenticated principal name (HTML-escaped).
   - `isAuthenticated()`: Boolean flag.
   - `isAnonymous()`: Boolean flag.
   - `getAuthorities()`: Collection of string authority names.
   - `hasAuthority(String)` / `hasAnyAuthority(String...)`: Boolean checks.
   - `toString()`: Redacts internal credentials.
2. **`$csrf` (`CsrfView`)**:
   Exposes only:
   - `getToken()`: CSRF token value.
   - `getParameterName()`: Form parameter name (e.g. `_csrf`).
   - `getHeaderName()`: HTTP header name (e.g. `X-CSRF-TOKEN`).

Raw Spring Security framework objects (`SecurityContextHolder`, `AuthenticationProvider`, `GrantedAuthority` implementations) are never linked or accessible from templates.

---

## 7. Dynamic Evaluation (`#evaluate`) Guardrails

The `#evaluate` directive compiles and executes VTL code dynamically at runtime:

```vtl
#evaluate($dynamicSnippet)
```

> [!WARNING]
> **Compatibility Profile Invariant**:
> Dynamic `#evaluate` is **disabled by default** in `VTL_CORE`, `VTL_MIGRATION`, and `VTL_SAFE` compatibility profiles. It is only permitted when explicitly configured under `VTL_DYNAMIC`.
>
> Even in `VTL_DYNAMIC`, `#evaluate` should **never** be executed on raw, unvalidated user input. If untrusted users are permitted to author dynamic snippets:
> 1. Enforce `MemberAccessPolicy.safe()`.
> 2. Bind a restrictive `RenderBudget` to the execution.
> 3. Keep `#evaluate` disabled if dynamic template generation is not required.

---

## 8. What Viet Template Security Does NOT Guarantee

> [!CAUTION]
> **Template Sandbox $\neq$ OS Process Sandbox**
>
> Viet Template's security model isolates template expressions within the Java Virtual Machine. However, it does not replace operating system or container isolation:
> - **Host Application Vulnerabilities**: If the host application injects a custom helper object containing dangerous methods (e.g. `deleteDatabase()`) into `RenderContext`, the template engine will permit invocation unless restricted by `MemberAccessPolicy`.
> - **Denial of Service at Process Level**: While `RenderBudget` bounds CPU loops and output strings, host memory allocation (JVM heap size) and OS process limits must be governed by container cgroups, Kubernetes resource limits, and JVM flags (`-Xmx`).
