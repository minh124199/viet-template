# Production & AOT Deployment Guide

## 1. Overview & Deployment Philosophy

Viet Template supports two distinct operating modes:

1. **Development Mode (Dynamic Interpretation & Hot Reload)**:
   Templates are read from the filesystem or classpath, parsed into AST/IR, and dynamically compiled or interpreted. Changes to template files can be reloaded on the fly without restarting the JVM.
2. **Production Mode (Ahead-Of-Time Precompiled Bytecode)**:
   Templates are compiled into Java bytecode at build time by the Maven or Gradle plugin. At runtime, templates execute as direct bytecode without runtime compilation, reducing startup latency, eliminating parser overhead, and enabling full GraalVM Native Image compilation.

---

## 2. Recommended Deployment Profiles

### 2.1 Profile A: Development Profile

In development, prioritize rapid iteration, developer productivity, and live feedback:

```properties
# application-dev.properties
viet-template.prefix=templates/
viet-template.suffix=.vm
viet-template.cache=false
viet-template.runtime-compilation-enabled=true
viet-template.hot-reload=true
viet-template.watch-debounce-millis=50
```

- **Runtime Compilation**: Enabled (`runtimeCompilationEnabled = true`).
- **Template Source**: Loaded from `src/main/resources/templates` or external filesystem directories.
- **Hot Reload**: Filesystem watcher listens for file modifications and invalidates cache entries automatically.
- **Spring Boot DevTools**: Fully integrated. Mode A supports in-place template refresh without restarting; Mode B supports ClassLoader turnover on full application restart.

### 2.2 Profile B: Production Profile

In production, prioritize maximum throughput, deterministic memory footprint, and hardened security:

```properties
# application-prod.properties
viet-template.prefix=templates/
viet-template.suffix=.vm
viet-template.cache=true
viet-template.max-cache-entries=1000
viet-template.negative-cache-ttl-millis=60000
viet-template.runtime-compilation-enabled=false
viet-template.hot-reload=false
```

- **Runtime Compilation**: **Disabled** (`runtimeCompilationEnabled = false`).
  Enforces `rejectRuntimeCompilation(true)`. The engine rejects any uncompiled or dynamic template compilation requests, guaranteeing that only build-time verified templates execute.
- **Template Packaging**: Precompiled template `.class` files packaged inside the application JAR with `META-INF/viet-template/templates.idx`.
- **Cache**: View caching and compiled template index caching enabled.

---

## 3. Ahead-Of-Time (AOT) Build Tooling

> [!NOTE]
> **Tested Production & Native Environments**:
> Ahead-Of-Time precompiled templates run on all standard Java 21+ JVMs. For GraalVM Native Image compilation, verification is empirically qualified on **Linux x86_64** with Oracle GraalVM 25.0.4+7.1 (Spring Boot 3/4) and Mandrel 25.0.4.1-Final (Quarkus 3). macOS and Windows environments remain experimental and unverified for native image packaging.

### 3.1 Maven Build Configuration

Add `viet-template-maven-plugin` to your `pom.xml`:

```xml
<build>
    <plugins>
        <!-- Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC1 -->
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
            <configuration>
                <sourceDirectory>${project.basedir}/src/main/resources/templates</sourceDirectory>
                <outputDirectory>${project.build.outputDirectory}</outputDirectory>
                <incremental>true</incremental>
                <failOnWarning>false</failOnWarning>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 3.2 Gradle Build Configuration

Apply `io.github.minh124199.viet-template` in your `build.gradle.kts`:

```kotlin
plugins {
    // Latest Published Stable: 0.2.2 | Latest Published Prerelease: 1.0.0-RC1
    id("io.github.minh124199.viet-template") version "0.2.2" // or "1.0.0-RC1"
}

vietTemplate {
    sourceDirectory.set(file("src/main/resources/templates"))
    incremental.set(true)
    failOnWarning.set(false)
}
```

### 3.3 What AOT Generates

During the build phase, the compiler inspects all template files matching `**/*.vtl` and `**/*.vm`:

1. **Bytecode Classfiles**:
   Compiles each template into an immutable, standalone JVM class (e.g. `io.github.minh124199.viettemplate.generated.T_order_vm_4b7f85021d94.class`).
2. **Template Index (`templates.idx`)**:
   Generates a newline-delimited index file at `META-INF/viet-template/templates.idx` mapping template paths to generated class names:
   ```text
   templates/order.vm=io.github.minh124199.viettemplate.generated.T_order_vm_4b7f85021d94
   templates/header.vm=io.github.minh124199.viettemplate.generated.T_header_vm_b8d670427c39
   ```

---

## 4. Runtime Template Discovery & ClassLoading

When `VtlTemplateEngine` starts up in production:

1. It searches the classpath for `META-INF/viet-template/templates.idx`.
2. Pre-loads and links the compiled template classes into its internal executable registry.
3. If a template lookup occurs for a registered path (e.g. `templates/order.vm`), it directly instantiates and executes the precompiled class without invoking the lexer, parser, or semantic analyzer.
4. If `runtimeCompilationEnabled=false` and an unindexed template is requested, it fails fast with `TemplateSecurityException` (`SECURITY:COMPILATION_REJECTED`), preventing unauthorized dynamic template execution.

---

## 5. Cache Architecture & Sizing

Viet Template incorporates an enterprise-grade, concurrent LRU cache (`TemplateCompileCache`) with batched deferred maintenance:

- **Contention-Free Reads**: Utilizes 16 shared atomic sampler stripes and amortized drain thresholds, achieving 50M+ operations/second under multi-threaded concurrency.
- **Negative Caching**: Lookups for non-existent templates are cached for a configurable duration (`negativeCacheTtlMillis`, default: 5,000 ms) to prevent disk I/O thrashing from repeated 404s.
- **Capacity Sizing**: Set `viet-template.max-cache-entries` to accommodate the total count of distinct templates plus active dynamic permutations.

---

## 6. Engine Lifecycle & Graceful Shutdown

The `TemplateEngine` implements `AutoCloseable`:

```java
try (TemplateEngine engine = TemplateEngine.builder().repository(repo).build()) {
    // Render templates
}
```

In Spring Boot applications, `VietTemplateAutoConfiguration` automatically manages engine lifecycle, registering `destroyMethod = "close"`:
- Closes file watchers and background daemon threads.
- Flushes and shuts down buffer pools (`Utf8BufferPool`).
- Clears dynamic linker inline caches.
