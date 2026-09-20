# Extension & SPI Author Guide

## 1. Overview & SPI Architectural Principles

Viet Template provides a carefully bounded set of **Service Provider Interfaces (SPIs)** designated as `STABLE_SPI`. These interfaces allow third-party developers, enterprise frameworks, and tooling authors to extend engine functionality without compromising runtime performance, security boundaries, or forward binary compatibility.

### Guiding Principles for Extension Authors
- **Extensibility via Interfaces**: All extension points are documented Java interfaces with explicit lifecycle and concurrency contracts.
- **Fail-Closed Security**: Extensions must adhere to the project's security invariants and must not bypass `MemberAccessPolicy` or sandbox confinement.
- **Boundary Discipline**: Depend **only** on types classified as `STABLE_API` or `STABLE_SPI` in `config/api-baseline/`. Do **not** import or subclass classes classified as `PUBLIC_BUT_INTERNAL_ACCIDENT` (such as `DynamicCallSite`, `AccessLink`, or internal compiler visitors); these are internal implementation details subject to encapsulation prior to 1.0.

---

## 2. Stable SPI Inventory

| SPI Interface | Module | Stability | Purpose |
|---|---|---|---|
| `TemplateRepository` | `viet-template-api` | `STABLE_SPI` | Resolves and loads template source content by `TemplateId`. |
| `TemplateOutput` | `viet-template-api` | `STABLE_SPI` | High-performance streaming output sink (Writer, Stream, Buffer). |
| `RenderContextContributor` | `viet-template-api` | `STABLE_SPI` | Supplies contextual attributes to `RenderContext` across requests. |
| `MemberAccessPolicy` | `viet-template-api` | `STABLE_SPI` | Filters and approves class, property, and method reflective access. |
| `SecurityViewFactory` | `viet-template-spring-security` | `STABLE_SPI` | Constructs `$security` view facades from Spring Security authentication. |
| `CsrfViewFactory` | `viet-template-spring-security` | `STABLE_SPI` | Constructs `$csrf` view facades from Spring Security CSRF tokens. |
| `VietTemplateEngineCustomizer` | `viet-template-spring` | `STABLE_SPI` | Programmatically customizes `TemplateEngine.Builder` in Spring Boot. |

---

## 3. Implementing Core SPIs

### 3.1 `TemplateRepository` (Custom Template Sources)

Use `TemplateRepository` to resolve templates from databases, S3 buckets, or distributed key-value stores:

```java
package com.example.extension;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateSource;
import java.io.IOException;
import java.util.Optional;

public final class DatabaseTemplateRepository implements TemplateRepository {

    private final DatabaseClient db;

    public DatabaseTemplateRepository(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) throws IOException {
        String content = db.fetchTemplate(id.value());
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(TemplateSource.of(id, content));
    }
}
```

- **Lifecycle**: Long-lived; instantiated once per `TemplateEngine` instance.
- **Thread Safety**: **Must be thread-safe**. `find(TemplateId)` will be called concurrently by worker threads.
- **Error Behavior**: Throw `IOException` for unrecoverable I/O failures. Return `Optional.empty()` when a template does not exist.

---

### 3.2 `RenderContextContributor` (Global & Request Attributes)

Use `RenderContextContributor` to enrich the template context with shared helpers, localization tools, or request metadata:

```java
package com.example.extension;

import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;

public final class LocalizationContextContributor implements RenderContextContributor {

    @Override
    public void contribute(MutableRenderContext context, RenderRequest request) {
        context.put("i18n", new MessageSourceTool(request.locale()));
        context.put("userLocale", request.locale().toLanguageTag());
    }
}
```

- **Lifecycle**: Invoked once per render operation during request context initialization.
- **Thread Safety**: The `MutableRenderContext` passed to `contribute()` is thread-confined to the current request.
- **Collision Policy**: Governed by the engine's `ContextCollisionPolicy` (default: `FAIL` on duplicate keys).

---

### 3.3 `MemberAccessPolicy` (Custom Security Filtering)

Implement `MemberAccessPolicy` to provide fine-grained authorization for domain classes in multi-tenant environments:

```java
package com.example.extension;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.lang.reflect.Method;

public final class TenantRestrictedAccessPolicy implements MemberAccessPolicy {

    private final String allowedPackagePrefix;

    public TenantRestrictedAccessPolicy(String allowedPackagePrefix) {
        this.allowedPackagePrefix = allowedPackagePrefix;
    }

    @Override
    public boolean isClassPermitted(Class<?> clazz) {
        if (clazz == null) return false;
        // Deny reflection and system types
        if (clazz.getName().startsWith("java.lang.reflect") || clazz.getName().startsWith("java.lang.invoke")) {
            return false;
        }
        return clazz.getName().startsWith(allowedPackagePrefix) || clazz.getName().startsWith("java.lang.");
    }

    @Override
    public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
        // Block dangerous method names
        if ("getClass".equals(methodName) || "wait".equals(methodName) || "notify".equals(methodName)) {
            return false;
        }
        return isClassPermitted(receiverClass);
    }

    @Override
    public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
        return !"class".equals(propertyName) && isClassPermitted(receiverClass);
    }

    @Override
    public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
        return isClassPermitted(receiverClass);
    }
}
```

- **Thread Safety**: **Must be completely thread-safe and stateless or immutable**. Polling methods (`isClassPermitted`, `isMethodPermitted`) reside on hot execution paths.

---

### 3.4 `VietTemplateEngineCustomizer` (Spring Customization)

In Spring Boot applications, register a `VietTemplateEngineCustomizer` bean to hook into engine configuration:

```java
package com.example.extension;

import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomEngineConfiguration {

    @Bean
    public VietTemplateEngineCustomizer customizer() {
        return builder -> {
            builder.addContextContributor(new LocalizationContextContributor());
        };
    }
}
```

---

## 4. ArchUnit Compliance for Extensions

Viet Template's Technology Compatibility Kit enforces that public extensions do not touch internal packages:

```java
@ArchTest
public static final ArchRule extensions_must_not_access_internal_packages =
    noClasses()
        .that().resideInAPackage("com.example.extension..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..internal..", "..vtl.compiler..", "..vtl.parser..");
```
