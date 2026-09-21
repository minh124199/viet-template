# Apache Velocity 2.4.1 to Viet Template Migration Guide

## 1. Overview & Migration Philosophy

Viet Template is an evidence-driven, high-performance, and secure template engine designed as a drop-in semantic successor to **Apache Velocity 2.4.1**. It preserves exact VTL (Velocity Template Language) syntax and runtime behavior across 76 standard features, while introducing hardened security sandboxing, a Java 21+ compiler baseline, Spring Framework 7 / Spring Boot 4 canonical integration, GraalVM Native Image support, and Ahead-of-Time (AOT) compilation.

This guide provides a comprehensive roadmap for migrating existing Velocity-based applications to Viet Template, mapping architectural concepts, configuration parameters, template directives, and Spring integration.

---

## 2. Core API Comparison & Replacement

### 2.1 Engine Initialization

In Apache Velocity, the engine is typically initialized via static methods or a mutable `VelocityEngine` instance configured with untyped properties:

```java
// Apache Velocity 2.4.1 (Legacy)
VelocityEngine velocityEngine = new VelocityEngine();
velocityEngine.setProperty("resource.loader.file.path", "/templates");
velocityEngine.setProperty("velocimacro.library", "macros/global.vm");
velocityEngine.init();
```

In Viet Template, engines are configured via a type-safe, immutable builder with explicit repository resolution and security policies:

```java
// Viet Template (Modern)
import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;

TemplateEngine engine = TemplateEngine.builder()
    .repository(ClasspathTemplateRepository.of("templates"))
    .memberAccessPolicy(MemberAccessPolicy.standard())
    .globalMacroLibraries(List.of(TemplateId.of("macros/global.vm")))
    .build();
```

### 2.2 Context Construction

In Velocity, `VelocityContext` is a mutable map-backed bag that templates can write back into:

```java
// Apache Velocity 2.4.1
VelocityContext context = new VelocityContext();
context.put("username", "Alice");
context.put("orders", orderList);
```

In Viet Template, the root model is encapsulated in an **immutable** `RenderContext`, protecting request state across concurrent threads and execution tiers:

```java
// Viet Template
import io.github.minh124199.viettemplate.api.RenderContext;

RenderContext context = RenderContext.builder()
    .put("username", "Alice")
    .put("orders", orderList)
    .build();
```

### 2.3 Template Rendering

Velocity merges templates into a `java.io.Writer`:

```java
// Apache Velocity 2.4.1
org.apache.velocity.Template template = velocityEngine.getTemplate("order.vm");
StringWriter writer = new StringWriter();
template.merge(context, writer);
String result = writer.toString();
```

Viet Template provides high-level convenience methods for string rendering, character streaming via `WriterTemplateOutput`, and zero-allocation binary streaming via `Utf8OutputStreamTemplateOutput`:

```java
// Viet Template: High-level rendering
String result = engine.render("order.vm", context);

// Viet Template: Streaming to a Writer
import io.github.minh124199.viettemplate.runtime.WriterTemplateOutput;
StringWriter writer = new StringWriter();
engine.get("order.vm").render(context, new WriterTemplateOutput(writer));

// Viet Template: Zero-allocation streaming to Servlet OutputStream
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(response.getOutputStream())) {
    engine.get("order.vm").render(context, out);
}
```

---

## 3. Template Repositories & Resource Loaders

Velocity's resource loaders are configured via reflective class names. Viet Template replaces these with explicit `TemplateRepository` SPI implementations:

| Velocity Resource Loader | Viet Template Replacement | Description |
|---|---|---|
| `ClasspathResourceLoader` | `ClasspathTemplateRepository.of("templates")` | Resolves templates from JVM classpath or JAR resources. |
| `FileResourceLoader` | `FilesystemTemplateRepository.of(path)` | Resolves templates from filesystem with strict traversal sandboxing. |
| `StringResourceLoader` | `InMemoryTemplateRepository.create()` | In-memory repository for dynamic string templates or unit tests. |
| Multi-loader configurations | `CompositeTemplateRepository.of(repoA, repoB)` | Chains multiple repositories with deterministic priority. |

---

## 4. Spring MVC & Spring Boot Migration

### 4.1 Dependency Coordinates

Replace legacy Velocity dependencies with the official Viet Template starter:

```xml
<!-- Replace: org.apache.velocity:velocity-engine-core -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.2</version>
</dependency>
```

### 4.2 View Resolver Configuration

In Velocity:
```java
// Legacy Velocity View Resolver
@Bean
public VelocityViewResolver velocityViewResolver() {
    VelocityViewResolver resolver = new VelocityViewResolver();
    resolver.setPrefix("/templates/");
    resolver.setSuffix(".vm");
    resolver.setCache(true);
    return resolver;
}
```

In Viet Template, Spring Boot auto-configuration automatically registers `VietTemplateViewResolver` and configures the engine using `application.properties`:

```properties
# application.properties
viet-template.prefix=templates/
viet-template.suffix=.vm
viet-template.cache=true
viet-template.charset=UTF-8
viet-template.content-type=text/html;charset=UTF-8
viet-template.runtime-compilation-enabled=true
```

#### 4.2.1 Incremental Migration with Multiple Suffixes

When migrating large enterprise Velocity codebases, converting hundreds of `.vm` templates to native `.vtl` templates in a single commit is often impractical. Viet Template provides first-class multiple-suffix support, allowing native and legacy templates to coexist and resolve deterministically:

```yaml
# application.yml
viet-template:
  prefix: templates/
  suffixes:
    - .vtl
    - .vm
```

Or in `application.properties`:

```properties
viet-template.prefix=templates/
viet-template.suffixes=.vtl,.vm
```

##### Directory Layout Example
```text
src/main/resources/templates/
├── dashboard.vtl     # Migrated to native Viet Template
├── users.vtl         # Migrated to native Viet Template
├── account.vm        # Legacy Apache Velocity template
└── reports.vm        # Legacy Apache Velocity template
```

##### Controllers Require Zero Changes
```java
@Controller
public class AppController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard"; // Resolves templates/dashboard.vtl
    }

    @GetMapping("/account")
    public String account() {
        return "account"; // Resolves templates/account.vm
    }
}
```

##### Precedence During Transition
If both `account.vtl` and `account.vm` exist simultaneously:
- `templates/account.vtl` is selected because `.vtl` appears first in the configured suffixes list.
- `templates/account.vm` is ignored.

This allows engineers to migrate files one by one at their own pace: creating `account.vtl` immediately takes over from `account.vm` without touching controller routes or view resolver configuration.

> [!NOTE]
> **Resource Location Ordering vs. Template Syntax Contract**: Configured suffixes control template resource location ordering exclusively. All resolved templates—regardless of file extension (`.vtl`, `.vm`, etc.)—are parsed, compiled, and executed under Viet Template's unified VTL compatibility contract and security sandbox, not by separate dialect interpreters.

### 4.3 Spring Security Integration

In legacy applications, developers frequently passed raw Spring Security authentication objects into the template context. Viet Template provides safe, read-only view facades via `viet-template-spring-security`:

- `$security.name`: Authenticated principal username.
- `$security.authenticated`: Boolean authentication status.
- `$security.hasAuthority('ROLE_ADMIN')`: Authority check.
- `$csrf.token`, `$csrf.parameterName`, `$csrf.headerName`: CSRF token values with standard HTML escaping.

---

## 5. Directives & Macro Migration

### 5.1 Directives Reference

| Directive | Velocity Compatibility | Migration Notes |
|---|---|---|
| `#set` | Identical | Creates local scope variable. By default, setting to an undefined RHS assigns `null` (Velocity 2.x standard). |
| `#if`, `#elseif`, `#else` | Identical | Evaluates conditionals using `VtlTruthiness` (Booleans, nulls, emptiness, custom `getAsBoolean()`). |
| `#foreach` | Compatible + Extension | Provides standard `$foreach.index`, `$foreach.count`, `$foreach.hasNext`, `$foreach.first`, `$foreach.last`. Extends with `$foreach.stop()`. |
| `#break` | Identical | Immediately terminates the nearest enclosing `#foreach` loop. |
| `#macro` | Identical | Defines local template macros. Supports body blocks (`#@macroCall() ... #end`). |
| `#parse` | Identical | Lexically parses and executes a sub-template in the current scope with cycle detection. |
| `#include` | Identical | Includes raw text without VTL interpretation. |
| `#evaluate` | Compatible with Restrictions | Evaluates dynamic VTL expressions. Guarded by execution budgets. |
| `#stop` | Identical | Immediately halts rendering of the current template. |
| `#define` | Identical | Defines reusable content blocks bound to a variable. |

### 5.2 Global Velocimacro Libraries

In Velocity:
```properties
velocimacro.library = macros/common.vm, macros/ui.vm
velocimacro.permissions.allow.inline = true
```

In Viet Template:
Configure global macro libraries on the `TemplateEngine.Builder`:
```java
engineBuilder.globalMacroLibraries(List.of(
    TemplateId.of("macros/common.vm"),
    TemplateId.of("macros/ui.vm")
));
```

---

## 6. Layout Rendering Migration

If your application used `VelocityLayoutView` from `velocity-tools`:

1. Define your layout template (e.g. `layout/default.vm`):
   ```html
   <!DOCTYPE html>
   <html>
   <head><title>$title</title></head>
   <body>
     <div class="content">
       $screen_content
     </div>
   </body>
   </html>
   ```
2. Enable layout rendering via `LayoutConfiguration`:
   ```java
   LayoutConfiguration layoutConfig = LayoutConfiguration.builder()
       .defaultLayout(TemplateId.of("layout/default.vm"))
       .contentKey("screen_content")
       .build();

   TemplateEngine engine = TemplateEngine.builder()
       .repository(repository)
       .layoutConfiguration(layoutConfig)
       .build();
   ```
3. In individual views, specify layout overrides or disable layouts:
   ```vtl
   ## Override layout:
   #set($layout = "layout/admin.vm")

   ## Disable layout for fragments or modals:
   #set($layout = false)
   ```

---

## 7. Custom Tools and Helpers

Legacy Velocity applications heavily utilized `VelocityTools` (such as `DateTool`, `NumberTool`, or custom request-scoped helper classes):

### 7.1 The Anti-Pattern to Avoid
Do **not** register tools that expose dangerous reflective capabilities (`Class`, `Method`, `Runtime`, or file I/O). Viet Template's security model will deny access to reflective methods.

### 7.2 The Recommended Migration Approach
1. **Pass Clean Java Records / POJOs in the Model**:
   ```java
   public record FormatterTool() {
       public String formatCurrency(BigDecimal amount) {
           return "$" + amount.setScale(2, RoundingMode.HALF_UP);
       }
   }
   ```
2. **Use `RenderContextContributor` for Global Request Helpers**:
   ```java
   RenderContextContributor globalToolsContributor = (context, request) -> {
       context.put("formatter", new FormatterTool());
       context.put("currentYear", Year.now().getValue());
   };

   TemplateEngine engine = TemplateEngine.builder()
       .repository(repo)
       .addContextContributor(globalToolsContributor)
       .build();
   ```

---

## 8. Velocity Configuration Property Translation Table

| Velocity Property Key | Viet Template Configuration | Status | Action / Notes |
|---|---|---|---|
| `resource.loaders` | `TemplateEngine.builder().repository(...)` | Replaced | Configure type-safe `TemplateRepository`. |
| `resource.loader.file.path` | `FilesystemTemplateRepository.of(path)` | Replaced | Path is sandboxed against `..` traversal. |
| `velocimacro.library` | `builder.globalMacroLibraries(...)` | Replaced | Pass list of `TemplateId`. |
| `runtime.strict_mode.enable` | `VtlInterpreterOptions.builder().strictReferences(true)` | Compatible | Throws `TemplateRenderException` on missing variables. |
| `parser.space_gobbling` | Built-in | Identical | Handled automatically according to VTL whitespace rules. |
| `runtime.interpolate.string.literals` | Built-in | Identical | Double-quoted strings interpolate references by default. |
| `velocimacro.permissions.allow.inline` | Always allowed | Obsolete | Local template macros are always supported. |
| `runtime.introspector.uberspect` | `MemberAccessPolicy` | Replaced | Configure `standard()`, `safe()`, or custom allowlists. |
| `event_handler.reference_insertion.class` | Built-in | Obsolete | Handled by contextual HTML escaping and `TemplateOutput`. |

---

## 9. Things You Should NOT Mechanically Port

When migrating, audit template files for these common Velocity anti-patterns:

1. **Direct Class and Reflection Access**:
   - ❌ **Anti-pattern**: `$user.getClass().getName()` or `$user.class.classLoader`
   - ✅ **Fix**: Remove reflection calls from templates. Viet Template's default `MemberAccessPolicy.standard()` explicitly forbids access to `getClass()`, `.class`, and classloaders.
2. **Arbitrary Process / OS Execution**:
   - ❌ **Anti-pattern**: Tools exposing `Runtime.getRuntime().exec(...)` or `ProcessBuilder`.
   - ✅ **Fix**: Compute results in the backend controller and pass pure data attributes into the model.
3. **Template-Driven Context Mutation for Subsequent Requests**:
   - ❌ **Anti-pattern**: Relying on `#set` inside a template to modify state for other concurrent or future requests.
   - ✅ **Fix**: Viet Template treats `RenderContext` as an immutable root snapshot. Variables assigned via `#set` exist only within the current template execution frame.
4. **Dynamic Code Injection via `#evaluate`**:
   - ❌ **Anti-pattern**: `#evaluate($untrustedUserInput)`
   - ✅ **Fix**: Restrict `#evaluate` to trusted template expressions and enforce execution limits via `RenderBudget`.
5. **Reliance on Silent Division by Zero**:
   - ❌ **Anti-pattern**: Relying on `$a / 0` evaluating silently to `null`.
   - ✅ **Fix**: Check for zero explicitly: `#if($b != 0)#set($res = $a / $b)#else#set($res = 0)#end`.

---

## 10. Summary Checklist for Migration

- [ ] Update build dependencies to `viet-template-spring-boot-starter:0.2.2`.
- [ ] Replace `VelocityEngine` initialization with `TemplateEngine.builder()`.
- [ ] Replace `VelocityContext` with `RenderContext.builder()`.
- [ ] Verify template directory locations and configure `TemplateRepository`.
- [ ] Replace custom reflective tools with immutable Java records or `RenderContextContributor`.
- [ ] Check templates for `$user.getClass()` or `$a / 0` and update them.
- [ ] Enable Ahead-Of-Time (AOT) compilation via Maven or Gradle plugin for production deployment.
