# Quickstart: 5 Minutes to Viet Template

Get started with **Viet Template** in under five minutes. This guide covers standalone Java usage, Spring Boot 4 integration, and the three-step transition from Apache Velocity.

---

## Prerequisites

- **Java Development Kit (JDK)**: Java 21 LTS or newer (Java 25 recommended for peak performance).
- **Build Tool**: Apache Maven 3.9+ or Gradle 8.5+.

---

## 1. Standalone Plain Java

Use Viet Template directly in any Java application without framework dependencies.

### 1.1 Add Maven Dependencies

Add `viet-template-api`, `viet-template-runtime`, and `viet-template-vtl-interpreter` to your `pom.xml`:

```xml
<!-- Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC1 -->
<dependencies>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-api</artifactId>
        <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
    </dependency>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-runtime</artifactId>
        <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
    </dependency>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-vtl-interpreter</artifactId>
        <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
    </dependency>
</dependencies>
```

*(For Gradle, use `implementation("io.github.minh124199:viet-template-api:0.2.2")` (or `1.0.0-RC1`), etc.)*

### 1.2 Create a Template

Create `src/main/resources/templates/greeting.vtl`:

```vtl
<h1>Hello, $name!</h1>
#if($items.isEmpty())
    <p>No items found.</p>
#else
    <ul>
    #foreach($item in $items)
        <li>[$foreach.count] $item</li>
    #end
    </ul>
#end
```

### 1.3 Render in Java

```java
package com.example;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        // Initialize engine with classpath repository
        TemplateEngine engine = TemplateEngine.builder()
            .repository(ClasspathTemplateRepository.of("templates/"))
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build();

        // Populate RenderContext
        RenderContext context = RenderContext.builder()
            .put("name", "Developer")
            .put("items", List.of("Speed", "Safety", "Simplicity"))
            .build();

        // Render directly to String
        String result = engine.render("greeting.vtl", context);
        System.out.println(result);
    }
}
```

---

## 2. Spring Boot 4 Integration

Viet Template provides a turnkey Spring Boot 4 starter with automated view resolution, AOT precompilation, and Spring Security integration.

### 2.1 Add Starter Dependency

```xml
<!-- Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC1 -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
</dependency>
```

### 2.2 Configure Properties (`application.properties`)

```properties
# Template location and suffix
viet-template.prefix=templates/
viet-template.suffix=.vtl
viet-template.charset=UTF-8

# View caching and runtime compilation
viet-template.cache=true
viet-template.runtime-compilation-enabled=true
```

### 2.3 Create Spring Controller & Template

Save your template to `src/main/resources/templates/dashboard.vtl`:

```vtl
<!DOCTYPE html>
<html>
<head><title>$title</title></head>
<body>
    <h2>$title</h2>
    <p>Welcome, $user.name!</p>
</body>
</html>
```

Create a standard Spring MVC Controller:

```java
package com.example.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Executive Dashboard");
        model.addAttribute("user", Map.of("name", "Alice"));
        return "dashboard"; // Resolves to classpath:/templates/dashboard.vtl
    }
}
```

---

## 3. Transitioning from Apache Velocity in 3 Steps

Migrating from Apache Velocity 2.4.1 to Viet Template is direct and requires no template grammar modifications:

1. **Step 1: Replace Dependencies**:
   Remove `org.apache.velocity:velocity-engine-core` and add `viet-template-spring-boot-starter` (or `viet-template-api`).
2. **Step 2: Keep Existing Templates**:
   Retain all existing `.vm` or `.vtl` files. Viet Template achieves **100% TCK coverage** across all 80 standard VTL grammar features.
3. **Step 3: Switch Initialization Code**:
   Replace `VelocityEngine` and `VelocityContext` with `VietTemplateEngine` and standard Java collections (`Map<String, Object>`).

For detailed architectural differences and security enhancements, read the [Velocity Migration Guide](file:///home/lynguyen/current_source/viet-template-repo/docs/migration/velocity-migration-guide.md).

---

## Next Steps

- **[Syntax Reference](file:///home/lynguyen/current_source/viet-template-repo/docs/language/syntax-reference.md)**: Explore the complete grammar, operator precedence, and loop metadata.
- **[Support Matrix](file:///home/lynguyen/current_source/viet-template-repo/docs/getting-started/support-matrix.md)**: Review supported JDKs, frameworks, and operating systems.
- **[Security Architecture](file:///home/lynguyen/current_source/viet-template-repo/docs/security/secure-templates.md)**: Configure `MemberAccessPolicy` and execution budgets.
- **[AOT & Production Deployment](file:///home/lynguyen/current_source/viet-template-repo/docs/deployment/production-aot.md)**: Precompile templates to zero-reflection JVM bytecode.
- **[GraalVM Native Image](file:///home/lynguyen/current_source/viet-template-repo/docs/native-image/graalvm-native-image.md)**: Compile into instant-startup native binaries.
