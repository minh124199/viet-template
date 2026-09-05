# 11 — Public API and SPI

## 1. Principle

Stable public API stays small. Parser AST, IR, bytecode generator, cache implementation and dynamic linker internals stay internal until deliberately promoted.

## 2. Engine builder

```java
TemplateEngine engine = TemplateEngine.builder()
    .repository(ClasspathTemplateRepository.of("templates"))
    .compatibility(CompatibilityProfile.VTL_CORE)
    .security(TemplateSecurityPolicy.safeDefaults())
    .escaping(EscapePolicy.htmlByExtension())
    .execution(ExecutionMode.AUTO)
    .build();
```

## 3. Repository SPI

```java
public interface TemplateRepository {
    Optional<TemplateSource> find(TemplateId id);
}
```

Built-ins: classpath, development filesystem, in-memory test, composite. Network sources are not core default.

## 4. Template API

```java
public interface Template {
    TemplateDescriptor descriptor();
    void render(RenderContext context, TemplateOutput output);
}
```

Descriptor can expose template id, fingerprint, execution kind, typed/dynamic flags and dependencies.

## 5. Context

```java
RenderContext ctx = RenderContext.builder()
    .put("user", user)
    .put("orders", orders)
    .build();
```

Root model is immutable from template by default.

## 6. Escaper SPI

```java
public interface Escaper {
    EscapeMode mode();
    void escape(CharSequence input, TemplateOutput output);
}
```

## 7. Extensions

Prefer explicit extensions over arbitrary Java methods. Performance-aware typed extensions may expose a compile-time binding descriptor so generated code can invoke a direct static method.

## 8. Model introspection SPI

```java
public interface ModelIntrospector {
    Optional<PropertyDescriptor> property(VType receiver, String name);
    Optional<MethodDescriptor> method(VType receiver, String name, List<VType> args);
}
```

Implementations: safe Java, Velocity compatibility, generated schema.

## 9. Diagnostics

```java
public record Diagnostic(
    DiagnosticSeverity severity,
    DiagnosticCode code,
    String message,
    SourceSpan primarySpan,
    List<RelatedDiagnostic> related,
    List<FixSuggestion> fixes
) {}
```

CLI, IDE and Spring format from the same model.

## 10. Events

Optional listener for compile/reload/failure. Avoid mandatory logging dependencies and avoid render-start/stop instrumentation unless opted in because hot-path cost matters.

## 11. Versioning

Track separately:

```text
engineVersion
languageVersion
compatibilityProfileVersion
artifactFormatVersion
runtimeAbiVersion
```

At 1.0, `viet-template-api` follows semver. Generated artifact compatibility is explicit and invalidated when runtime ABI requires it.
