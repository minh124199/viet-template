# Spring Boot & Spring Framework Integration Guide

## 1. Overview & Ecosystem Architecture

Viet Template offers first-class, production-ready integration with the modern Spring ecosystem:
- **Canonical Stack**: Spring Framework 7.0+, Spring Boot 4.0+, Spring Security 7.0+, Jakarta Servlet 6.1 (Tomcat 11), Java 25.
- **Compatible Stack**: Spring Framework 6.2+, Spring Boot 3.3+, Spring Security 6.3+, Jakarta Servlet 6.0 (Tomcat 10.1), Java 21.

The integration is modular:
- `viet-template-spring`: Provides `VietTemplateView`, `VietTemplateViewResolver`, `SpringRenderAttributes`, and `VietTemplateEngineCustomizer`.
- `viet-template-spring-boot-autoconfigure`: Provides `VietTemplateAutoConfiguration`, `VietTemplateProperties`, and Spring AOT `VietTemplateRuntimeHints`.
- `viet-template-spring-boot-starter`: Aggregator dependency bringing in auto-configuration and the standard VTL interpreter.
- `viet-template-spring-security`: Provides optional, safe read-only facades `SecurityView` (`$security`) and `CsrfView` (`$csrf`).

---

## 2. Getting Started with Spring Boot

Add the starter dependency to your project:

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.2</version>
</dependency>
```

```kotlin
// Gradle
implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.2")
```

### 2.1 Writing a Spring MVC Controller

```java
package com.example.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Viet Template on Spring Boot");
        model.addAttribute("message", "Welcome to modern Java templating!");
        return "index"; // Resolves to templates/index.vm or templates/index.vtl
    }
}
```

### 2.2 Template File (`src/main/resources/templates/index.vm`)

```html
<!DOCTYPE html>
<html>
<head>
    <title>$title</title>
</head>
<body>
    <h1>$message</h1>
</body>
</html>
```

---

## 3. Comprehensive Configuration Properties Reference

All properties reside under the `viet-template.*` namespace and map to `VietTemplateProperties`:

| Property | Type | Default Value | Description & Production Recommendation |
|---|---|---|---|
| `viet-template.enabled` | `boolean` | `true` | Enables or disables Viet Template auto-configuration. |
| `viet-template.prefix` | `String` | `""` | Prefix prepended to view names when constructing template identifiers (e.g. `templates/`). |
| `viet-template.suffix` | `String` | `""` | Legacy suffix appended to view names (e.g. `.vm` or `.vtl`). Used when `suffixes` is unconfigured. |
| `viet-template.suffixes` | `List<String>` | `[]` | Ordered list of template suffixes to probe (e.g. `[.vtl, .vm]`). Takes precedence over `suffix` when non-empty. |
| `viet-template.content-type` | `String` | `text/html;charset=UTF-8` | Content-Type header emitted by `VietTemplateView`. |
| `viet-template.charset` | `Charset` | `UTF-8` | Character encoding used for template rendering and byte streaming. |
| `viet-template.cache` | `boolean` | `true` | Enables view instance caching in `VietTemplateViewResolver`. Keep `true` in production. |
| `viet-template.check-template-location` | `boolean` | `true` | Verifies at startup that template locations exist on the classpath or AOT index. |
| `viet-template.runtime-compilation-enabled` | `boolean` | `true` | Permits dynamic runtime template compilation. **Set to `false` in production** to enforce AOT precompiled templates. |
| `viet-template.max-cache-entries` | `int` | `500` | Maximum number of compiled template entries retained in `TemplateCompileCache`. |
| `viet-template.negative-cache-ttl-millis` | `long` | `5000` | Time-to-live in milliseconds for caching non-existent template lookups (prevents repeated disk scans). |
| `viet-template.hot-reload` | `boolean` | `false` | Enables filesystem watcher for dynamic template reloading during local development. |
| `viet-template.watch-debounce-millis` | `long` | `50` | Debounce window in milliseconds for filesystem modification events. |
| `viet-template.order` | `int` | `Ordered.LOWEST_PRECEDENCE` | Order priority of `VietTemplateViewResolver` in the Spring MVC view resolver chain. |
| `viet-template.security.enabled` | `boolean` | `true` | Enables Spring Security integration and exposes `$security` and `$csrf` in templates. |

---

### 3.1 Multiple Suffix Resolution & Gradual Migration

Applications undergoing gradual migration from Apache Velocity or supporting multiple template file extensions can configure `viet-template.suffixes`:

```yaml
viet-template:
  prefix: templates/
  suffixes:
    - .vtl
    - .vm
```

When a controller returns a logical view name such as `"dashboard"`, `VietTemplateViewResolver` probes candidate paths in deterministic configured order:
1. `templates/dashboard.vtl`
2. `templates/dashboard.vm`
3. Returns `null` if neither exists, permitting subsequent `ViewResolver`s in the Spring MVC chain to resolve the view.

#### Precedence & Semantics
- **Precedence**: `viet-template.suffixes` takes precedence whenever it is configured and non-empty. `viet-template.suffix` remains supported for backward compatibility.
- **Single-suffix fallback**: If `suffixes` is empty or omitted, resolution falls back to `suffix` (default `""`).
- **Explicit extension**: If the logical view name already ends with one of the configured non-empty suffixes (e.g. `return "dashboard.vm"`), that exact template is probed first without redundant suffix appending (`dashboard.vm` instead of `dashboard.vm.vtl`).
- **Location check disabled**: When `viet-template.check-template-location=false`, no existence probes are performed; the resolver deterministically resolves using the first configured suffix.
- **Broken-template isolation**: Fallback occurs only when a candidate is considered absent. If a preferred candidate is located but template syntax, compilation, semantic, or another non-resource failure occurs while probing or loading it, that candidate remains selected. Viet Template does not silently switch to a lower-priority suffix merely because the preferred template is broken; the template error is surfaced through the normal compilation/rendering path for the selected view.
- **Resource location vs. language syntax**: Configured suffixes control template resource location ordering exclusively. All resolved templates—regardless of file extension (`.vtl`, `.vm`, `.html`, etc.)—are parsed, compiled, and executed under Viet Template's unified VTL compatibility contract and security sandboxing, not distinct dialect runtimes.

---

## 4. Programmatic Customization (`VietTemplateEngineCustomizer`)

To customize the underlying `TemplateEngine.Builder` programmatically (e.g. adding global context contributors, custom macro libraries, or custom member access policies), register a `VietTemplateEngineCustomizer` bean:

```java
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TemplateConfig {

    @Bean
    public VietTemplateEngineCustomizer customizer() {
        return builder -> {
            builder.addContextContributor((context, request) -> {
                context.put("appVersion", "1.0.0");
                context.put("environment", "production");
            });
        };
    }
}
```

---

## 5. Spring Security & CSRF Facades

When `viet-template-spring-security` is on the classpath and `viet-template.security.enabled=true`:

```html
<form method="post" action="/logout">
    <!-- CSRF Token Input -->
    <input type="hidden" name="$csrf.parameterName" value="$csrf.token"/>
    
    #if($security.authenticated)
        <p>Logged in as: <strong>$security.name</strong></p>
        #if($security.hasAuthority('ROLE_ADMIN'))
            <a href="/admin">Admin Dashboard</a>
        #end
        <button type="submit">Sign Out</button>
    #else
        <a href="/login">Sign In</a>
    #end
</form>
```

---

## 6. Spring Boot DevTools & Live Reload

Viet Template is tested for seamless integration with `spring-boot-devtools`:
- **Mode A (In-Place Template Reload)**: When templates reside in external directories or resources, edits reload automatically without restarting the application context.
- **Mode B (AOT Recompile & Restart)**: When Java source code or templates trigger a DevTools restart, `VtlTemplateEngine` shuts down cleanly via its `close()` lifecycle, releasing thread pools and buffer allocators with zero ClassLoader leaks.
