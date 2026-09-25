# GraalVM Native Image Guide

## 1. Overview & Architecture

Viet Template provides full, first-class support for **GraalVM Native Image** and **Spring Boot Ahead-of-Time (AOT)** compilation across both Spring Boot 3 (Java 21) and Spring Boot 4 (Java 25 canonical stack).

Because GraalVM Native Image relies on closed-world static analysis, dynamic class generation and arbitrary Java reflection are restricted at runtime. Viet Template achieves native compatibility through a pure Ahead-of-Time design:

1. **Build-Time Compilation**: The Maven or Gradle AOT plugin compiles templates into standard JVM bytecode `.class` files and indexes them in `META-INF/viet-template/templates.idx`.
2. **Spring AOT `RuntimeHints`**: Automated `RuntimeHintsRegistrar` implementations automatically register the template index, generated classes, and security facades for native reflection and resource inclusion.
3. **Zero Runtime Compilation**: At runtime, the engine executes directly against the precompiled classes with `rejectRuntimeCompilation(true)`.

---

## 2. Automated Spring Runtime Hints

Viet Template ships with built-in `RuntimeHintsRegistrar` components that require zero manual `reflect-config.json` configuration for core template rendering:

### 2.1 `VietTemplateRuntimeHints`

Packaged inside `viet-template-spring-boot-autoconfigure`:
- **Resources**: Registers pattern matching for `META-INF/viet-template/templates.idx` and all assets under `META-INF/viet-template/*`.
- **Precompiled Template Classes**: Reads `templates.idx` at Spring AOT build time and registers all discovered template classes for constructor invocation and public method reflection.
- **Configuration Properties**: Registers `VietTemplateProperties` and `VietTemplateProperties.Security` for reflection.

### 2.2 `VietTemplateSecurityRuntimeHints`

Packaged inside `viet-template-spring-security`:
- Registers reflection hints for `SecurityView`, `DefaultSecurityView`, `CsrfView`, and `DefaultCsrfView` so properties like `$security.name` and `$csrf.token` resolve seamlessly in native binaries.

---

## 3. Building Native Executables

> [!NOTE]
> **Tested Native Environments**:
> Native compilation is empirically verified on **Linux x86_64** with:
> - **Spring Boot Native**: Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS).
> - **Quarkus Native**: Mandrel 25.0.4.1-Final (Java 25).
> macOS and Windows environments remain experimental and unverified for native binaries; standard JVM runs cross-platform.

### 3.1 Maven Native Build

Ensure `viet-template-maven-plugin` and `native-maven-plugin` are configured:

```xml
<dependencies>
    <!-- Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC1 -->
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-spring-boot-starter</artifactId>
        <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- 1. AOT compile templates into classes before packaging -->
        <plugin>
            <groupId>io.github.minh124199</groupId>
            <artifactId>viet-template-maven-plugin</artifactId>
            <version>0.2.2</version> <!-- or 1.0.0-RC1 -->
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                    </goals>
                    <phase>process-classes</phase>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Compile the native image binary:

```bash
./mvnw -Pnative native:compile
```

The native binary is generated at `target/<application-name>`.

### 3.2 Gradle Native Build

Apply the GraalVM native plugin alongside `io.github.minh124199.viet-template`:

```kotlin
plugins {
    id("org.graalvm.buildtools.native") version "0.10.3"
    // Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC3
    id("io.github.minh124199.viet-template") version "1.0.0-RC3" // or "0.2.2"
}

dependencies {
    implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.2") // or "1.0.0-RC3"
}
```

Compile the native image binary:

```bash
./gradlew nativeCompile
```

The native binary is generated at `build/native/nativeCompile/<application-name>`.

---

## 4. Registering Model Classes for Reflection

In GraalVM Native Image, any model or DTO class whose getters or fields are accessed by template expressions must be registered for reflection:

### Option A: Spring `@RegisterReflectionForBinding`
Place `@RegisterReflectionForBinding` on a controller or configuration class:

```java
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@Controller
@RegisterReflectionForBinding({UserProfile.class, OrderSummary.class})
public class OrderController {
    // ...
}
```

### Option B: Viet Template `@TemplateData`
Annotate domain records and model classes with `@TemplateData`:

```java
import io.github.minh124199.viettemplate.api.TemplateData;

@TemplateData
public record UserProfile(String username, String email) {}
```

---

## 5. Troubleshooting Common Native Image Issues

### 5.1 `TemplateSecurityException: [SECURITY:COMPILATION_REJECTED]`
- **Cause**: A template was requested that was not compiled during the build and is not present in `META-INF/viet-template/templates.idx`.
- **Solution**: Verify that your template files reside in `src/main/resources/templates` and that the `viet-template-maven-plugin` or `viet-template-gradle-plugin` ran before the native image build.

### 5.2 Empty or Missing Output for Properties
- **Cause**: The receiver class or method was not registered for reflection, so dynamic property linkage returned null.
- **Solution**: Register the model class using `@RegisterReflectionForBinding(MyClass.class)` or `@TemplateData`.

### 5.3 Missing `templates.idx` in Native Image
- **Cause**: Resource hints were not registered because the index did not exist when Spring AOT processing occurred.
- **Solution**: Ensure the build order executes `viet-template:compile` prior to `process-aot` / `nativeCompile`.
