# 12 — Spring Framework / Spring Boot Integration Design

> [!IMPORTANT]
> **Canonical Baseline Notice**: As established in ADR-0007, ADR-0008, and ADR-0009, Viet Template's canonical integration baseline is **Spring Framework 7.0.9**, **Spring Boot 4.1.1**, and **Spring Security 7.1.1** on **Java 21+**, **Jakarta Servlet 6.1.0**, and **Tomcat 11.0.24** with full Virtual Thread execution support. For the authoritative architecture, configuration properties reference (`viet-template.*`), Ahead-Of-Time (AOT) workflow, and dual-build parity guarantees, refer to:
>
> 👉 **[`docs/35-m16-spring-integration.md`](35-m16-spring-integration.md)**

## 1. Goal

Ordinary Spring MVC usage:

```java
@Controller
class HomeController {
    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("user", userService.current());
        return "home";
    }
}
```

with `src/main/resources/templates/home.vm` and no manual engine wiring.

## 2. Baseline Architecture

As formally decided in ADR-0007, ADR-0008, and ADR-0009:
- **Minimum Compiler Baseline**: Java 21 (`--release 21`, major version 65).
- **Primary Toolchain and Runtime**: Java 25.
- **Canonical Frameworks**: Spring Framework 7.0.9, Spring Boot 4.1.1, Spring Security 7.1.1.
- **Servlet Specification**: Jakarta Servlet 6.1.0 (Tomcat 11.0.24).
- **Concurrency & Threads**: Virtual Threads (Project Loom) first-class support; no pinned virtual threads were observed in tested steady-state rendering workloads. Cold concurrent runtime compilation can encounter JVM class-loader monitor pinning; AOT/precompiled execution avoids this path.
- **Configuration Property Prefix**: Canonical prefix is **`viet-template`** (kebab-case, e.g. `viet-template.prefix`, `viet-template.suffix`, `viet-template.enabled`).

## 3. Modules

```text
viet-template-spring
viet-template-spring-boot-autoconfigure
viet-template-spring-boot-starter
viet-template-spring-security (optional integration; see docs/36-spring-security-integration.md)
```

## 4. MVC view

Conceptual adapter:

```java
public final class VietTemplateView extends AbstractView {
    private final TemplateEngine engine;
    private final TemplateId templateId;

    @Override
    protected void renderMergedOutputModel(
            Map<String,Object> model,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        engine.get(templateId.value())
              .render(springModelContext(model), servletOutput(response));
    }
}
```

Actual base/interface should follow current Spring 7 API and minimize unnecessary buffering.

## 5. View resolver

```java
public final class VietTemplateViewResolver extends AbstractCachingViewResolver {
    private String prefix = "";
    private String suffix = ".vm";
}
```

Resolve logical template ids; do not assume direct filesystem paths.

## 6. Model exposure defaults

Expose controller model plus explicit helpers/locale as configured.

Do **not** expose by default:

```text
ApplicationContext
BeanFactory/all beans
HttpServletRequest/Response
session
Environment
security context
```

## 7. Request/session attributes

Opt-in properties:

```yaml
viet:
  template:
    spring:
      expose-request-attributes: false
      expose-session-attributes: false
```

Define collisions: `MODEL_WINS`, `REQUEST_WINS`, `ERROR_ON_COLLISION` (mapped to `ContextCollisionPolicy.MODEL_WINS`, `ContextCollisionPolicy.CONTRIBUTOR_WINS`, and `ContextCollisionPolicy.FAIL` via `RenderContextContributor` defined in Milestone M12.5; see [12a — Velocity Application Compatibility Architecture](12a-velocity-application-compatibility.md)).

## 8. Boot namespace

> [!NOTE]
> In production (Milestone M16), the official configuration prefix is **`viet-template`** (`@ConfigurationProperties(prefix = "viet-template")`). Properties use kebab-case: `viet-template.enabled`, `viet-template.prefix`, `viet-template.suffix`, `viet-template.cache`, etc.

The conceptual draft used `viet.template`. The production property prefix is `viet-template`:

```yaml
viet:
  template:
    enabled: true
    templates:
      prefix: classpath:/templates/
      suffix: .vm
      cache: true
      charset: UTF-8
    execution: auto
    compatibility: vtl-core
    strict: true
    html:
      auto-escape: true
    security:
      profile: safe
    development:
      hot-reload: true
```

## 9. Auto-configuration

```java
@AutoConfiguration(afterName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnClass({TemplateEngine.class, View.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(VietTemplateProperties.class)
public class VietTemplateAutoConfiguration {}
```

Use `@ConditionalOnMissingBean` for replaceable parts. Register auto-config class in:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 10. Bean graph

```text
VietTemplateProperties
  ├─ TemplateRepository
  ├─ TemplateSecurityPolicy
  ├─ EscapePolicy
  └─ TemplateEngine
       ├─ VietTemplateViewResolver
       └─ optional dev watcher
```

## 11. Resources

Support classpath/jar resources correctly. Do not convert every Spring `Resource` URL to a filesystem path. Dev-mode filesystem watching is a separate optimized path.

## 12. Charset/output

HTML default: UTF-8. A direct-byte renderer must honor servlet response charset/content type; otherwise use Writer adapter.

## 13. Error behavior

Development may show precise template diagnostics. Production throws into normal Spring exception handling and never writes stack traces directly into response.

## 14. Hot reload

```text
watch -> dependency invalidation -> recompile -> atomic registry swap
```

Boot DevTools restart remains compatible but not required for template-only changes.

## 15. Typed future API

A generated model/view result can provide compile-time model types while ordinary string view names continue to work.

## 16. Spring AOT/native

Register resources/generated classes only when needed. Direct typed access should require no reflection hints. Dynamic model fallback contributes hints only for declared types.

## 17. Integration tests

Real `MockMvc` tests for controller -> model -> resolver -> template -> response, charset/content-type, missing template, errors, escaping, user override beans, auto-config backoff, request/session default denial, AOT/native smoke where possible.

## 18. Starter metadata

Generate Boot configuration metadata and Javadoc for every property. Name starter project-first (`viet-template-spring-boot-starter`), not with `spring-boot` prefix.
