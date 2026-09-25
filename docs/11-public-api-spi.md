# 11 — Public API and SPI

## 1. Principle

Stable public API stays small. Parser AST, IR, bytecode generator, cache implementation and dynamic linker internals stay internal until deliberately promoted.

## 2. Engine builder

> [!NOTE]
> **Conceptual API Design**: The builder example below reflects the original conceptual API design. The stabilized public contract is formalized in `TemplateEngine.Builder` (see [`docs/32-m14-public-api-spi-stabilization.md`](32-m14-public-api-spi-stabilization.md)), configuring repository resolution, cache constraints, macro libraries, context contributors, collision policies, and member access policies.

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

## 12. Public Surface Classification & Layered Compatibility Baselines

Viet Template maintains structured, machine-readable baselines to govern API stability:

1. **Layered Public API Baselines (`config/api-baseline/`)**:
   - **Core Public API Baseline (`1.0-core-public-api.txt` / `1.0-public-api.txt`)**: Covers **98 core types** across `viet-template-api`, `viet-template-runtime`, and canonical engine entrypoints.
   - **AOT Facade Public API Baseline (`1.0-aot-public-api.txt`)**: Covers **6 stable AOT compiler facade types** (`TemplateAotArtifact`, `TemplateAotCompiler`, `TemplateAotDiagnostic`, `TemplateAotRequest`, `TemplateAotRequest$Builder`, `TemplateAotResult`) in `io.github.minh124199.viettemplate.aot`.
   - **Spring Public API Baseline (`1.0-spring-public-api.txt`)**: Covers **8 Spring integration types** (`VietTemplateAutoConfiguration`, `VietTemplateProperties`, `VietTemplateProperties$Security`, `VietTemplateSecurityAutoConfiguration`, `SpringRenderAttributes`, `VietTemplateEngineCustomizer`, `VietTemplateView`, `VietTemplateViewResolver`).
   - **Spring Security Public API Baseline (`1.0-spring-security-public-api.txt`)**: Covers **5 Spring Security integration types** (`CsrfView`, `CsrfViewFactory`, `SecurityView`, `SecurityViewFactory`, `SpringSecurityRenderContextContributor`).
   - **Quarkus Public API Baseline (`1.0-quarkus-public-api.txt`)**: Covers **4 Quarkus integration types** (`VietTemplateConfig`, `VietTemplateRenderer`, `QuarkusSecurityView`, `QuarkusSecurityRenderContextContributor`).
   - Verified by `scripts/verify-api-compatibility.py` on CI to enforce 100% binary and source backward compatibility across all **121 stable types** (98 + 6 + 8 + 5 + 4 = 121).

2. **Repository-Wide Public Surface Classification (`config/api-baseline/public-surface-classification.txt`)**:
   - Classifies all compiled `public` and `protected` types across all production modules (exactly **339 total types**).
   - Contains **121 stable-classified types** (94 `STABLE_API` + 27 `STABLE_SPI`), 5 `EXPERIMENTAL` types, 4 `FRAMEWORK_ENTRYPOINT`, 4 `BUILD_TOOL_ENTRYPOINT`, 1 `GENERATED_RUNTIME_ABI`, 59 `INTERNAL_CROSS_MODULE`, 57 `INTERNAL_CROSS_PACKAGE`, 3 `BENCHMARK_SUPPORT_INTERNAL`, and 85 `PUBLIC_BUT_INTERNAL_ACCIDENT` (PBCIA) debt types.
   - Enforces the machine-checked invariant: `union(all 1.0 compatibility baseline types) == all STABLE_API + STABLE_SPI types` (121 types).
   - Verified by `scripts/verify-public-surface-classification.py` to ensure 0 unclassified types, 0 stale entries, baseline parity & bijection, and zero internal signature leaks.

### Pre-1.0 Convergence Guarantee

The pre-1.0 convergence requirement is fully satisfied through the layered baseline architecture: 100% of stable public types (`STABLE_API` and `STABLE_SPI`) across core, AOT, spring, spring security, and quarkus modules are formally registered and mechanically verified against breaking changes on every CI build.
