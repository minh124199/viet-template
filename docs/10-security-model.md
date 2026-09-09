# 10 — Security Model

## 1. Position

A server-side template language is code-adjacent. Object graph traversal and arbitrary method calls can expose application capabilities far beyond presentation logic. Viet Template treats access, invocation, resource loading and runtime evaluation as capabilities.

## 2. Threat models

- **Trusted developer templates:** primarily accidental data exposure/XSS.
- **Semi-trusted author/CMS:** data exposure, service access, filesystem/network pivots, DoS.
- **Untrusted templates:** require strict sandbox; do not promise risk-free arbitrary server-side code execution.

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

## 12. XSS

HTML integration should auto-escape by default. Initial contexts:

```text
HTML_TEXT
HTML_ATTRIBUTE_QUOTED
URL_COMPONENT
JS_STRING
CSS_STRING
```

Do not claim universal context-sensitive XSS protection until HTML-context analysis is actually implemented and audited.

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

### 16.2 Static Parsing and AST Complexity Limits

To mitigate parser-level algorithmic complexity and memory exhaustion attacks, `VtlParserOptions` and `VtlParser` enforce strict pre-compilation limits:

- **`maxSourceCharacters`** (default 1,000,000): Rejects oversized template inputs before tokenization.
- **`maxAstNodes`** (default 20,000): Enforces an absolute ceiling on the number of AST nodes generated during parsing.
- **`maxExpressionDepth`** (default 50): Aborts parsing when binary, unary, or ternary expression nesting exceeds safe bounds.
- **`maxDirectiveNesting`** (default 50): Bounds the depth of nested `#if`, `#foreach`, and macro directive blocks (`PARSER:MAX_NESTING_EXCEEDED`).

### 16.3 Unified Monotonic Render Budget (`RenderBudget`)

All runtime evaluation tiers (AST interpreter, IR interpreter, AOT bytecode backend) share a single monotonic `RenderBudget`:

- **Output Character Limits**: Counts every character emitted to `TemplateOutput` (including raw text chunks, formatted numbers, and escaped references).
- **Loop Iteration Limits**: Counts iterations across all top-level loops, nested loops, and macro iterations against `maxLoopIterations`.
- **Wall-Clock Time Budget (`maxExecutionTimeMillis`)**: Enforces execution deadlines using monotonic nanosecond clocks (`System.nanoTime()`), aborting long-running rendering pipelines.
- **Sub-render Propagation**: A single shared `RenderBudget` instance is propagated through `#parse`, `#include`, `#evaluate`, macro invocations, and two-stage layout rendering (`DefaultLayoutRenderPlan`), preventing sub-templates from resetting or evading limits.

### 16.4 Strict Resource Root Confinement

Template path resolution and repository access are hardened against directory traversal and host escape:

- **`TemplateId` Hardening**: Rejects null bytes (`\0`), URL-encoded traversal sequences (`%2e`, `%2f`, `%5c`, `%00`), URI schemes (`:`), and Windows drive letter paths (`C:`).
- **`FilesystemTemplateRepository` Confinement**: Resolves canonical real paths against the configured root directory (`toRealPath()`). Symlinks pointing outside the repository root are strictly rejected with `TemplateSecurityException`.

### 16.5 Protected Context Variables and Contributor Safety

- **Protected Variables (`protectedKeys`)**: `MutableRenderContext` enforces read-only protection on critical engine variables (e.g., layout `$screen_content`). Any attempt by template code (`#set`) or context contributors to overwrite a protected key throws `TemplateSecurityException`.
- **Contributor Collision Detection**: Under `ContextCollisionPolicy.FAIL`, conflicting keys contributed by different contributors throw `ContextCollisionException` with origin tracking.

### 16.6 Cache Isolation and `#evaluate` Hardening

- **SHA-256 Partitioning**: The template compilation cache (`CompileCacheKey`) hashes the security policy fingerprint, execution tier, and compiler options into every cache entry. Distinct security policies never share compiled bytecode or cached template definitions.
- **`#evaluate` Sandboxing**: `#evaluate` is disabled by default in standard and safe profiles. When explicitly enabled, evaluation shares the monotonic parent budget, applies source size limits, and caches AST/IR in bounded, policy-partitioned caches.

### 16.7 Cross-Tier Parity and Regression Verification

Security protections are verified to be strictly identical across all execution backends (AST interpreter, IR interpreter, dynamic linker PIC, and AOT bytecode):

- `SecurityRegressionCorpusTest`: 22 standardized adversarial attack categories tested across all backends.
- `SecurityFuzzTest`: Deterministic, seeded adversarial input fuzzing testing grammar edge cases and boundary limits.
- `SecurityConcurrencyTest`: High-concurrency thread-safety, protected variable contention, and cross-policy cache isolation verification.

