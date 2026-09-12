# 10 — Security Model

## 1. Position

A server-side template language is code-adjacent. Object graph traversal and arbitrary method calls can expose application capabilities far beyond presentation logic. Viet Template treats access, invocation, resource loading and runtime evaluation as capabilities.

## 2. Threat models

- **Trusted developer templates:** primarily accidental data exposure/XSS.
- **Semi-trusted author/CMS:** data exposure, service access, filesystem/network pivots, DoS.
- **Untrusted templates:** require strict sandbox (`VTL_SAFE`); designed for untrusted or externally editable template source when the host follows the documented embedding requirements.

### Security Terminology Definitions

To prevent ambiguity, security terms are strictly distinguished across documentation, code, and APIs:

* **Escaping**: Transforms data so it cannot break the current syntax context (e.g. converting `&` to `&amp;` or `"` to `&quot;` in HTML).
* **Validation**: Determines whether input satisfies an allowed semantic policy (e.g. verifying a URL begins with an approved scheme like `https:`).
* **Sanitization**: Transforms potentially dangerous structured content into an allowed safe subset (e.g. stripping malicious HTML tags via an external HTML sanitizer).
* **Trusted**: The host application explicitly assumes full responsibility for the safety of a value.
* **SafeHtml**: Capability wrapper representing trusted HTML markup that may bypass `HTML_TEXT` auto-escaping. It does **not** perform sanitization.
* **SafeUrl**: Capability wrapper representing a URL accepted by the engine's URL validation policy or explicitly marked trusted by host application code.

### Host Application Responsibilities

The sandbox cannot protect data or operations that the hosting application deliberately exposes. When embedding `VTL_SAFE` for untrusted template source, the host application is responsible for:

1. **Do not expose secrets unnecessarily**: Only bind data required for rendering into the template context.
2. **Do not expose powerful service/framework objects**: Never bind DI containers (Spring `ApplicationContext`, `BeanFactory`), database entities with lazy loading or mutation methods, persistence repositories, or stateful transactional services into the context.
3. **Do not construct trusted wrappers from untrusted input**: Never pass unvalidated user input to `SafeHtml.of(...)`, `SafeHtml.ofTrusted(...)`, or `SafeUrl.ofTrusted(...)`.
4. **Configure constrained template repositories**: Restrict template roots to dedicated directories. Do not configure root directories such as `/`, `/home`, or application code roots.
5. **Protect repository directories against concurrent writes**: Prevent untrusted local users from writing to or racing within the template directory to maintain strong symlink escape resistance.
6. **Configure realistic `ExecutionLimits`**: Set appropriate source size, AST node count, recursion depth, iteration limit, and wall-clock timeout quotas suited to available JVM memory and CPU capacity.
7. **Use the correct output context**: Specify the appropriate output context (`HTML_TEXT`, `HTML_ATTRIBUTE_QUOTED`, etc.) matching the destination document type.
8. **Treat escape hatches as privileged APIs**: Restrict access to `SafeHtml.ofTrusted` and `SafeUrl.ofTrusted` in application code reviews.

## 3. Assets

Secrets, environment, filesystem, network clients, process execution, reflection/classloader, dependency injection container, request/session/authentication, database/services reachable through model, CPU/memory and output integrity.

## 4. Capabilities

```java
public enum Capability {
    READ_PROPERTY,
    READ_INDEX,
    CALL_EXPORTED_METHOD,
    CALL_ARBITRARY_METHOD,
    MUTATE_PROPERTY,
    MUTATE_INDEX,
    LOAD_STATIC_TEMPLATE,
    LOAD_DYNAMIC_TEMPLATE,
    INCLUDE_STATIC_RESOURCE,
    INCLUDE_DYNAMIC_RESOURCE,
    EVALUATE_TEMPLATE_SOURCE,
    RAW_OUTPUT,
    ACCESS_FRAMEWORK_OBJECT
}
```

```java
public interface TemplateSecurityPolicy {
    Decision decide(SecurityRequest request);
}
```

Static decisions happen at compile time; dynamic receiver decisions happen at linkage and are cached with policy identity.

## 5. Dangerous graph pivots

Safe defaults block access to type families/capabilities around:

```text
Class / ClassLoader / Module
Runtime / ProcessBuilder
java.lang.reflect.*
privileged MethodHandles lookup
filesystem APIs unless explicitly exported
System environment/properties
```

Do not rely solely on method-name deny lists.

## 6. Presentation DTO guidance

Preferred:

```java
@TemplateData
public record UserView(String name, boolean admin) {}
```

Do not expose services/entities/application context by convenience.

## 7. Method export

Explicit annotation/registry only:

```java
@TemplateCallable
public String formattedTotal(Locale locale) { ... }
```

Inherited dangerous methods are not automatically exported.

## 8. Spring boundary

Never expose `ApplicationContext`, `BeanFactory`, all beans, request, response, session or environment by default. Optional request/session attributes must be narrow and collision-policy controlled.

## 9. Resource confinement

For template/include paths:

1. reject invalid/NUL input;
2. normalize separators;
3. reject absolute path unless provider explicitly supports it;
4. resolve `.`/`..`;
5. enforce logical root;
6. for filesystem, account for symlink/real-path escape;
7. apply provider capability policy.

## 10. `#evaluate`

Disabled by default. If explicitly enabled:

- independent source size limit;
- separate nesting limit;
- bounded compilation cache;
- no capability escalation;
- dynamic source identity for audit/diagnostics;
- AOT-only guarantees disabled for that template.

## 11. DoS controls

- total and per-loop iteration budget;
- template/macro/evaluate recursion depth;
- output bytes/chars;
- parser source/nesting limits;
- bounded dynamic template/cache cardinality;
- coarse cancellation/time checks.

## 12. XSS and Contextual Escaping Boundaries

HTML integration auto-escapes by default in `VTL_SAFE` across AST, IR, and AOT bytecode tiers. Standard escaping contexts:

```text
HTML_TEXT
HTML_ATTRIBUTE_QUOTED
URL_COMPONENT
JS_STRING
CSS_STRING
```

- **`HTML_TEXT`**: Escapes `&`, `<`, `>`, `"`, `'` for markup body text. Bypassed only by `SafeHtml`.
- **`HTML_ATTRIBUTE_QUOTED`**: Escapes `&`, `<`, `>`, `"`, `'`, backticks, and ASCII control characters to prevent boundary breakout in quoted HTML attributes (`"..."` or `'...'`). Never bypassed by `SafeHtml` or `SafeUrl`.
- **`URL_COMPONENT`**: RFC 3986 percent-encoding for URI query parameters and path segments. Bypassed only by `SafeUrl`.
- **`JS_STRING`**: JavaScript string literal escaping (including `\u003C` and `\u003E` to prevent `</script>` breakouts).
- **`CSS_STRING`**: CSS string literal escaping.

### Quoted Attribute vs URL Scheme Validation Boundary
Viet Template performs contextual escaping and entity escaping for quoted attributes, but it **does not** perform a full HTML AST parse of template attributes to infer semantic attribute contexts (`href` vs `data-foo` vs `onclick`).

Because `HTML_ATTRIBUTE_QUOTED` strictly entity-escapes attribute delimiters without validating URL schemes, emitting an unvalidated URL into an `href` or `src` attribute (e.g. `<a href="$url">`) will not prevent dangerous pseudo-protocols like `javascript:alert(1)`.

Therefore, applications emitting URLs into attribute positions must use **`SafeUrl`** or validate schemes using **`SafeUrlValidator`**:
- **Safe Schemes**: `https:`, `http:`, `mailto:`, `tel:`, and relative paths (`/`, `./`, `../`, `?`, `#`).
- **Rejected Dangerous Schemes**: `javascript:`, `vbscript:`, `data:`, `file:`, `blob:`, and any scheme containing obfuscated whitespace, control characters, URL percent-encoding (`%3a`), or HTML entities (`&#...`, `&colon;`).
- **Safe Construction**: `SafeUrl.of(url)`, `SafeUrl.ofValidated(url)`, or `SafeUrl.tryOf(url)` validate inputs; `SafeUrl.ofTrusted(url)` and `SafeHtml.ofTrusted(html)` are reserved exclusively for compile-time verified constants with an explicit call-site audit trail.


## 13. Raw output

Only explicit safe wrapper or explicit raw function/capability. `String` does not imply trusted HTML.

## 14. Diagnostics

Production client responses must not leak filesystem paths, class/method inventories, secret values or context dumps. Server-side diagnostics may be richer by configuration.

## 15. Security tests

Attack corpus must try:

```text
$obj.class
$obj.getClass()
$class.classLoader
Runtime.getRuntime
ProcessBuilder
System.getenv
../ traversal
symlink escape
Spring applicationContext
request/session mutation
recursive parse/macro
huge loop/range/output
unbounded evaluate cache
```

Every safe-profile case must deny or bound the action.

## 16. Hardened Security Architecture (Milestone M13)

Milestone M13 establishes defense-in-depth security invariants across all compilation, parsing, dynamic linking, and runtime tiers.

### 16.1 Member Access Policy and Safe Allowlisting

Viet Template provides both default-deny policies and strict safe-allowlist configurations via `MemberAccessPolicy`:

- **Class/Package Deny-list**: Denies reflection (`java.lang.reflect.*`, `java.lang.invoke.*`), classloading (`ClassLoader`, `Module`), process execution (`Process`, `ProcessBuilder`), thread management (`Thread`, `ThreadGroup`), concurrency executors (`java.util.concurrent.ExecutorService`, `ThreadPoolExecutor`, `ForkJoinPool`), and system/runtime manipulation (`System`, `Runtime`, `SecurityManager`).
- **Method Deny-list**: Core blocked methods include `wait`, `notify`, `notifyAll`, `exit`, `halt`, `exec`, `load`, `loadLibrary`, and reflection invocations. (Note: `$foreach.stop()` on `ForeachMetadata` is permitted as loop control).
- **Strict Safe Allowlist**: Applications can build explicit allowlists using `MemberAccessPolicy.allowlistBuilder()`, restricting property reads, method calls, and index operations to specific whitelisted classes and member names.
- **Sensitive Object Classifier (`SensitiveObjectClassifier`)**: Implements zero-dependency class hierarchy and interface scanning to detect and block access to sensitive framework and runtime instances (e.g. Spring `ApplicationContext`, `BeanFactory`, Java Security/Runtime types).
- **Cryptographic Fingerprint (`SecurityPolicyFingerprint`)**: Every `MemberAccessPolicy` computes a deterministic SHA-256 fingerprint of its configuration (mode, denied classes, denied methods, allowed classes, allowed methods), guaranteeing cache key partitioning.
- **Non-Widening Capability Semantics**:
  - `@TemplateData` is explicitly non-widening (not `@Inherited`). An unannotated subclass of an approved type does not expose newly declared getters, properties, or methods; only properties/methods declared on or overriding approved ancestor types are accessible.
  - Java records in safe mode expose only record components; arbitrary non-component methods (e.g. `deleteAccount()`) are denied.
  - `@TemplateCallable` requires exact signature matching (`name` and parameter types). Overloads with different parameter types do not inherit callable permissions from an annotated sibling. Bridge and synthetic methods generated for generic overrides are resolved safely to their declaring method.

### 16.2 Static Parsing and AST Complexity Limits

To mitigate parser-level algorithmic complexity and memory exhaustion attacks, `VtlParserOptions` and `VtlParser` enforce strict pre-compilation limits:

- **`maxSourceCharacters`** (default 1,000,000): Rejects oversized template inputs before tokenization.
- **`maxAstNodes`** (default 20,000): Enforces an absolute ceiling on the number of AST nodes generated during parsing.
- **`maxExpressionDepth`** (default 50): Aborts parsing when binary, unary, or ternary expression nesting exceeds safe bounds.
- **`maxDirectiveNesting`** (default 50): Bounds the depth of nested `#if`, `#foreach`, and macro directive blocks (`PARSER:MAX_NESTING_EXCEEDED`).

### 16.3 Unified Monotonic Render Budget (`RenderBudget`)

All runtime evaluation tiers (AST interpreter, IR interpreter, AOT bytecode backend) share a single monotonic `RenderBudget`:

- **Output Character Limits**: Counts every character emitted to `TemplateOutput` (including raw text chunks, formatted numbers, and escaped references).
- **Loop Iteration Limits**: Counts iterations across all top-level loops, nested loops, and macro iterations against `maxLoopIterations`. Zero-output loops consume iteration budget identically to content loops.
- **Saturating Counter Arithmetic**: Budget accumulators use saturating arithmetic bounded at `Long.MAX_VALUE` to prevent integer overflow or wrap-around evasion attacks.
- **Wall-Clock Time Budget (`maxExecutionTimeMillis`)**: Enforces execution deadlines using monotonic nanosecond clocks (`System.nanoTime()`), aborting long-running rendering pipelines.
- **Sub-render Propagation**: A single shared `RenderBudget` instance is propagated through `#parse`, `#include`, `#evaluate`, macro invocations, and two-stage layout rendering (`DefaultLayoutRenderPlan`), preventing sub-templates from resetting or evading limits. Limits are strictly validated (negative values rejected, 0 enforced as zero allowed).

### 16.4 Resource Root Confinement & Filesystem Threat Model

Template path resolution and repository access are hardened against directory traversal and host escape:

- **`TemplateId` Hardening**: Rejects null bytes (`\0`), URL-encoded traversal sequences (`%2e`, `%2f`, `%5c`, `%00`), URI schemes (`:`), and Windows drive letter paths (`C:`).
- **`FilesystemTemplateRepository` Confinement**:
  - Resolves canonical real paths against the configured root directory (`toRealPath()`).
  - Symlinks pointing outside the repository root are strictly rejected with `TemplateSecurityException`.
  - Check/read consistency: reads directly from the verified `realCandidate` Path (`Files.readString(realCandidate, charset)`), hardening against symlink substitution.
  - **Host Concurrency Assumption**: The repository hardens against path traversal and symlink escapes under the operational prerequisite that the configured template directory itself is not writable by untrusted concurrent users who are racing template resolution.

### 16.5 Protected Context Variables and Contributor Safety

- **Protected Variables (`protectedKeys`)**: `MutableRenderContext` enforces read-only protection on critical engine variables (e.g., layout `$screen_content`). Any attempt by template code (`#set`) or context contributors to overwrite a protected key throws `TemplateSecurityException`.
- **Contributor Collision Detection**: Under `ContextCollisionPolicy.FAIL`, conflicting keys contributed by different contributors throw `ContextCollisionException` with origin tracking.

### 16.6 Cache Isolation and `#evaluate` Hardening

- **SHA-256 Partitioning**: The template compilation cache (`CompileCacheKey`) hashes the security policy fingerprint, execution tier, and compiler options into every cache entry. Distinct security policies never share compiled bytecode or cached template definitions.
- **`#evaluate` Sandboxing**: `#evaluate` is disabled by default in standard and safe profiles. When explicitly enabled, evaluation shares the monotonic parent budget, applies source size limits, and caches AST/IR in bounded, policy-partitioned caches.

### 16.7 Model Mutation and Property Escalation Denial

- **No Property-to-Method Escalation**: In `VTL_SAFE` and strict modes, property syntax `$target.action` cannot resolve or invoke zero-argument action methods (such as `cancel()`, `delete()`, `execute()`). Property lookup is confined strictly to JavaBean getters (`getFoo()`, `isFoo()`), record components, or map access.
- **Model Mutation Confinement**: In `VTL_SAFE`, mutating model properties (`#set($target.prop = val)`) or array/list/map indices (`#set($target[idx] = val)`) is forbidden by default and blocked by `isPropertyMutationPermitted` / `isIndexMutationPermitted`.
- **Truthiness Reflection Gating**: Conditional truthiness inspection of methods such as `getAsBoolean`, `isEmpty`, `length`, and `size` strictly validates target classes and member access against `VtlSecurityPolicy` before reflection.

### 16.8 Capability Sandbox Profile (`VTL_SAFE`) and Annotations

`VTL_SAFE` serves as the strict sandbox profile for externally editable or otherwise untrusted template source:

- **Capability Annotations (`@TemplateData`, `@TemplateCallable`)**:
  - Types exposed to safe templates should be annotated with `@TemplateData` (or records). In `VTL_SAFE`, access to unannotated arbitrary classes is blocked unless explicitly permitted.
  - Action methods must be explicitly marked with `@TemplateCallable` to be invokable from safe templates.
- **Automatic Sandboxing**: Configuring `VTL_SAFE` automatically engages the strict capability-based security policy, disables `#evaluate`, blocks model mutations, and enables contextual dynamic HTML auto-escaping without requiring secondary flags. Custom policies can only narrow or further restrict permissions.
- **Cross-Tier Dynamic HTML Auto-Escaping**: Dynamic string/character sequence values interpolated in `VTL_SAFE` are automatically escaped for HTML text context across AST, IR, and AOT bytecode tiers, while preserving static literal text and respecting `SafeContent` (`SafeHtml`, `SafeUrl`) wrapper bypasses.
- **Strict Limit Validation**: `ExecutionLimits` strictly validates non-negative limits (rejecting negative values with `IllegalArgumentException`), and enforces a limit of 0 as zero allowed operations.

### 16.9 Cross-Tier Parity and Regression Verification

Security protections are verified to be strictly identical across all execution backends (AST interpreter, IR interpreter, dynamic linker PIC, and AOT bytecode):

- `SecurityRegressionCorpusTest`: Standardized adversarial attack categories tested across all backends.
- `SecurityFuzzTest`: Deterministic, seeded adversarial input fuzzing testing grammar edge cases and boundary limits.
- `SecurityConcurrencyTest`: High-concurrency thread-safety, protected variable contention, and cross-policy cache isolation verification.

### 16.10 SafeUrl Scheme Validation and Obfuscation Resistance

`SafeUrl` represents an explicitly validated URL that bypasses `URL_COMPONENT` percent-encoding while remaining strictly escaped in HTML markup and quoted attribute contexts.

- **Scheme Allowlist**: Allowed absolute schemes are strictly restricted to `http:`, `https:`, `mailto:`, and `tel:`. Relative URLs (`/`, `./`, `../`, `?`, `#`, and relative path segments) are permitted.
- **Dangerous Scheme Rejection**: Dangerous schemes (`javascript:`, `vbscript:`, `data:`, `file:`, `blob:`, etc.) are unconditionally rejected.
- **Adversarial Obfuscation Resistance**:
  - Leading and trailing ASCII whitespace and control characters are stripped prior to inspection.
  - Internal whitespace or control characters (`\t`, `\n`, `\r`, `\0`, `\u0001`-`\u001F`, `\u007F`) within the scheme token trigger immediate rejection.
  - URL-encoded delimiters (e.g. `%3a`, `%3A`) within the scheme or path trigger rejection.
  - HTML entity references (numeric `&#...` or named `&colon;`) within the scheme trigger rejection.
- **Safe Construction Contracts**:
  - `SafeUrl.of(CharSequence)`: Validates input; throws `IllegalArgumentException` on invalid scheme.
  - `SafeUrl.ofValidated(CharSequence)`: Validates input; throws `IllegalArgumentException` on invalid scheme.
  - `SafeUrl.tryOf(CharSequence)`: Validates input; returns `Optional.empty()` on invalid scheme.
  - `SafeUrl.ofTrusted(CharSequence)`: Explicit escape hatch bypassing validation strictly for host-verified constants.
  - `SafeHtml.ofTrusted(CharSequence)`: Explicit capability factory for trusted HTML markup.

### 16.11 Concrete Guarantees and Security Limitations

#### What `VTL_SAFE` Guarantees:
- **Mandatory non-weakening member-access policy**: Custom policies cannot grant access to core denied classes (`ClassLoader`, `Runtime`, `ProcessBuilder`, `System`, `Thread`), reflection (`java.lang.reflect`, `java.lang.invoke`), or dangerous methods (`getClass`, `wait`, `notify`).
- **Arbitrary Java methods denied**: Only JavaBean getters, record components, `@TemplateCallable` methods, or explicitly allowlisted methods can be called.
- **Non-widening `@TemplateData` inheritance**: An unannotated subclass only exposes members declared on or overriding approved ancestor classes; newly declared subclass getters are denied.
- **Exact-signature `@TemplateCallable` matching**: Overloads with different parameter types do not inherit callable permission from an annotated sibling.
- **Record exposure limited to canonical components**: Records expose record components only; arbitrary non-component methods and `Object` methods remain blocked.
- **Application model mutation denied**: In-template property mutations (`#set($target.prop = ...)`) and index mutations (`#set($target[idx] = ...)`) are forbidden by default.
- **No property-to-method escalation**: Property syntax `$target.action` cannot invoke zero-argument action methods (e.g. `delete()`).
- **Dynamic `#evaluate` disabled**: Runtime evaluation of arbitrary strings is disabled by default in safe profile.
- **Shared monotonic render execution budgets**: A single `RenderBudget` tracks loop iterations (including zero-output loops with saturating arithmetic), output characters, and wall-clock deadlines across `#parse`, `#evaluate`, macros, and layouts without resetting.
- **Context-specific output escaping**: Auto-escapes interpolated variables based on output context (`HTML_TEXT`, `HTML_ATTRIBUTE_QUOTED`, `URL_COMPONENT`).
- **Trusted output wrappers restricted to matching contexts**: `SafeHtml` only bypasses `HTML_TEXT` (still escaped in attributes); `SafeUrl` only bypasses `URL_COMPONENT`.
- **Template repository root confinement**: Paths are normalized, traversal (`..`) and null bytes are rejected, and canonical paths are verified within the repository root.

#### Security Limitations & Threat Boundaries:
- **Deliberately exposed data**: `VTL_SAFE` cannot hide or redact data fields that the host application explicitly binds into the context.
- **Trusted escape hatches**: `SafeHtml.ofTrusted(...)` and `SafeUrl.ofTrusted(...)` bypass their respective validation or escaping protections only in the contexts for which they grant trust. Context-specific escaping rules continue to apply when those values are rendered into other output contexts.
- **Escaping vs Sanitization**: `HTML_TEXT` escaping encodes syntax characters to prevent tag injection; it is not equivalent to full HTML sanitization of complex rich-text markup.
- **URL Validation Scope**: URL scheme validation (`SafeUrlValidator`) checks for safe schemes (`https:`, `http:`, `mailto:`, `tel:`, safe relative paths); it does not guarantee that external web destinations are trustworthy or benign.
- **Filesystem Concurrency**: Canonical-path confinement hardens against symlink directory escapes and path traversal, assuming the template repository directory is not writable by an attacker actively racing filesystem modifications during template lookup.
- **Resource Limits**: Wall-clock execution limits are defensive safe-points, not hard operating-system level preemptive CPU isolation.
- **In-Process Defense-in-Depth**: In-JVM sandboxing provides defense-in-depth within a shared JVM; it does not replace process or container isolation in adversarial, untrusted multi-tenant cloud environments.



