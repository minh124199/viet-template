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
