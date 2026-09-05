# 12 — Spring Framework 7 / Spring Boot 4 Integration

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

## 2. Baseline as of 2026-09-05

- Spring Framework 7.0.9 stable.
- Spring Framework 7.1 preview, scheduled for November 2026.
- Spring Boot 4.1.1 stable.

Target Framework 7.0.x/Boot 4.1.x first. Test 7.1/Boot 4.2 previews separately without declaring stable support prematurely.

## 3. Modules

```text
viet-template-spring
viet-template-spring-boot-autoconfigure
viet-template-spring-boot-starter
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

Define collisions: `MODEL_WINS`, `REQUEST_WINS`, `ERROR_ON_COLLISION`.

## 8. Boot namespace

The canonical Spring Boot property prefix is `viet.template`. YAML uses the equivalent nested `viet.template` structure:

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
