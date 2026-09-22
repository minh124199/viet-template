# Quarkus Extension Guide

## 1. Overview & Architectural Design

Viet Template provides first-class, idiomatic integration with [Quarkus](https://quarkus.io):
- **Canonical Stack**: Quarkus 3.39.4+, Jakarta EE 10 (CDI 4.0, REST), Java 21+ (`--release 21`, bytecode classfile major version 65).
- **Architecture**: Separated into a lightweight runtime module (`viet-template-quarkus`) and an ahead-of-time build step module (`viet-template-quarkus-deployment`).
- **Framework-Neutral Invariant**: The core engine (`viet-template-api`, `runtime`, `language-vtl`, `vtl-interpreter`) contains zero Quarkus dependencies. The extension adapts the framework-neutral contracts to Quarkus CDI, SmallRye Config, GraalVM native image build items, and dev-mode hot reload.
- **Qute Coexistence**: Viet Template runs harmoniously alongside Quarkus Qute within the same application without bean ambiguity or template path conflicts.
- **Public API Surface**: `viet-template-quarkus` exports 4 stable types (3 API + 1 SPI: `VietTemplateConfig`, `VietTemplateRenderer`, `QuarkusSecurityView` as `STABLE_API`, and `QuarkusSecurityRenderContextContributor` as `STABLE_SPI`), tracked in `config/api-baseline/1.0-quarkus-public-api.txt`. Total stable types across the repository: 105 types (85 API + 20 SPI).

---

## 2. Getting Started

### 2.1 Maven Configuration

Add the `viet-template-quarkus` extension dependency to your `pom.xml`:

```xml
<!-- Development / Snapshot Build -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-quarkus</artifactId>
    <version>0.2.3</version>
</dependency>
```

```xml
<!-- Release Build -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-quarkus</artifactId>
    <version>0.2.2</version>
</dependency>
```

The Quarkus Maven plugin automatically discovers the deployment artifact `viet-template-quarkus-deployment` via `META-INF/quarkus-extension.properties`.

### 2.2 Gradle Configuration

In `build.gradle.kts`:

```kotlin
// Development / Snapshot Build
implementation("io.github.minh124199:viet-template-quarkus:0.2.3")

// Release Build
implementation("io.github.minh124199:viet-template-quarkus:0.2.2")
```

---

## 3. CDI Injection & Rendering

The extension registers `@ApplicationScoped` beans for dependency injection:
- `TemplateEngine`: The core engine configured with classpath resolution and caching.
- `VietTemplateRenderer`: A high-level helper providing convenient string rendering and container-safe streaming to output streams.

### 3.1 JAX-RS / Quarkus REST Resource Example

```java
package com.example;

import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import java.util.Map;

@Path("/hello")
public class HelloResource {

    @Inject
    VietTemplateRenderer renderer;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String renderString(@QueryParam("name") String name) {
        return renderer.render("hello.vtl", Map.of("name", name != null ? name : "World"));
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.TEXT_HTML)
    public StreamingOutput renderStream(@QueryParam("name") String name) {
        return output -> renderer.render(
            "hello.vtl",
            Map.of("name", name != null ? name : "World"),
            output
        );
    }
}
```

### 3.2 Template File (`src/main/resources/templates/hello.vtl`)

```html
<h1>Hello, $name!</h1>
```

---

## 4. Configuration Reference

All properties are configured under the `quarkus.viet-template` namespace in `application.properties`.

All settings currently use `ConfigPhase.BUILD_AND_RUN_TIME_FIXED` because template discovery, suffix filtering, character encoding, and AOT compilation occur during build-time augmentation; having runtime properties disagree with build-time generated artifacts would produce silent discrepancies or missing classes.

| Configuration Property | Type | Phase | Default | Description & Rationale |
|---|---|---|---|---|
| `quarkus.viet-template.path` | String | `BUILD_AND_RUN_TIME_FIXED` | `templates` | Classpath directory where templates are discovered at build time and loaded at runtime. |
| `quarkus.viet-template.suffix` | String | `BUILD_AND_RUN_TIME_FIXED` | `.vtl` | Primary template file suffix scanned during augmentation. |
| `quarkus.viet-template.additional-suffixes` | List<String> | `BUILD_AND_RUN_TIME_FIXED` | *(empty)* | Additional template suffixes (e.g. `.vm`, `.html.vtl`) compiled into `templates.idx`. |
| `quarkus.viet-template.runtime-compilation-enabled` | Boolean | `BUILD_AND_RUN_TIME_FIXED` | `false` | Enables on-demand runtime compilation/interpretation. Defaults to `true` in dev mode, `false` in production. |
| `quarkus.viet-template.cache-max-entries` | Integer | `BUILD_AND_RUN_TIME_FIXED` | `500` | Maximum number of compiled templates retained in memory cache. |
| `quarkus.viet-template.negative-cache-ttl-millis` | Long | `BUILD_AND_RUN_TIME_FIXED` | `5000` | Duration (ms) to cache negative template lookup misses. |
| `quarkus.viet-template.undefined-reference-policy` | String | `BUILD_AND_RUN_TIME_FIXED` | `SILENT` | Missing variable policy: `SILENT` (Velocity default), `WARN` (log diagnostics), `ERROR` (fail-fast). |
| `quarkus.viet-template.encoding` | String | `BUILD_AND_RUN_TIME_FIXED` | `UTF-8` | Character encoding used to decode template sources at build and runtime. |

### Configuration Example

```properties
quarkus.viet-template.path=templates
quarkus.viet-template.suffix=.vtl
quarkus.viet-template.additional-suffixes=.html.vtl,.vm
quarkus.viet-template.undefined-reference-policy=WARN
quarkus.viet-template.cache-max-entries=1000
```

---

## 5. Ahead-of-Time (AOT) Compilation & GraalVM Native Image

During application build (`mvn package` or `gradle build`), `VietTemplateProcessor` executes ahead-of-time compilation:
1. **Template Discovery**: Scans `src/main/resources/templates` matching configured primary and additional suffixes.
2. **Bytecode Compilation**: Invokes `TemplateAotCompiler` to compile VTL templates into Java 21 classfiles (`major version 65`).
3. **Class Registration**: Emits `GeneratedClassBuildItem` for each compiled class into the application archive.
4. **Index Generation**: Emits `META-INF/viet-template/templates.idx` mapping template paths to compiled classes.
5. **GraalVM Reflection & Resources**: Emits `ReflectiveClassBuildItem` and `NativeImageResourceBuildItem` ensuring seamless compilation and instant execution in GraalVM Native Images without dynamic classloading.

### 5.1 Verified Native Image Toolchain

| Tool | Version |
|---|---|
| **Quarkus Baseline** | `3.39.4` (LTS baseline: `3.33.x`) |
| **Mandrel / GraalVM** | `Mandrel 25.0.4.1-Final (Java 25 LTS)` / GraalVM 25.0.0+ |
| **Java Target** | Java 21 (`--release 21`, classfile major version 65) |
| **C Compiler / OS** | GCC 16.2.1 / Linux x86_64 |
| **Native Binary Size** | ~52 MB executable |
| **Startup / Latency** | < 20 ms startup, < 1 ms HTTP response |

> [!NOTE] Host Platform Support Boundary
> Native executable compilation and live execution have been verified empirically on **Linux x86_64** (`Mandrel 25.0.4.1-Final` / GCC 16.2.1). Standard JVM test suites and CI matrices validate cross-platform execution on Ubuntu, macOS, and Windows (`ubuntu-latest`, `macos-latest`, `windows-latest`). Native compilation on macOS and Windows hosts is experimental and not covered by automated CI validation in this release.

### 5.2 Building Native Executables

```bash
# Maven
./mvnw package -Dnative

# Gradle
./gradlew build -Dquarkus.package.type=native
```

---

## 6. Capability Matrix

| Capability | JVM Mode | Native Executable | Dev Mode (`quarkus dev`) |
|---|---|---|---|
| Static VTL Templates | Supported | Supported (AOT Bytecode) | Supported (Live Reload) |
| Multiple Suffixes (`.vtl`, `.html.vtl`) | Supported | Supported | Supported |
| Undefined Reference Policies (`SILENT`, `WARN`, `ERROR`) | Supported | Supported | Supported |
| Dynamic Directives (`#parse`, `#evaluate`) | Supported | Not supported (pure-AOT invariant) | Supported (Dynamic Interpreter fallback) |
| Streaming HTTP Output (`StreamingOutput`) | Supported | Supported | Supported |
| Quarkus Security Context (`$security`) | Supported (Optional) | Supported (Optional) | Supported (Optional) |
| Qute Coexistence | Supported | Supported | Supported |
| Zero ClassLoader Retention | Supported | N/A | Supported (Zero static Class/Loader leaks) |

> [!IMPORTANT] Pure-AOT Dynamic Directive Constraint
> Pure ahead-of-time bytecode compilation converts static VTL templates directly into Java 21 classfiles. Because dynamic directives (`#parse` with non-constant expressions and `#evaluate`) require runtime parsing and AST construction, they cannot be compiled to static bytecode without an interpreter. In pure AOT environments (`quarkus.viet-template.runtime-compilation-enabled=false` or GraalVM Native Image), templates using dynamic directives fail fast at build time with diagnostic codes `VTLAOT:1101` (`#evaluate`) or `VTLAOT:1102` (`#parse`). In JVM / Dev Mode with runtime compilation enabled, the engine falls back to `VtlInterpreter` for dynamic templates.

---

## 7. Dev Mode Hot Reload & Dependency Invalidation

In Quarkus dev mode (`quarkus dev` or `gradle quarkusDev`):
- `HotDeploymentWatchedFileBuildItem` watches the template directory and all discovered template files.
- Modifying any template file triggers instant application redeployment and template recompilation without restarting the JVM process.
- **Transitive Dependency Invalidation**: Editing an included subtemplate (e.g. `#parse("header.vtl")`) triggers reload across all dependent templates.
- **ClassLoader Safety**: All static fields in `viet-template-quarkus` and `viet-template-quarkus-deployment` are strictly confined to immutable constants; zero application classes, ClassLoaders, or TemplateEngine instances survive across reload generations.
- `runtimeCompilationEnabled` defaults to `true` in dev mode for agile prototyping.

---

## 8. Security Integration (`$security`)

When `quarkus-security` is present on the application classpath, the extension registers `QuarkusSecurityRenderContextContributor`:
- Binds a presentation-safe `$security` view into every template execution context:
  - `$security.authenticated`: Boolean indicating whether current user is authenticated.
  - `$security.anonymous`: Boolean indicating whether current user is anonymous.
  - `$security.name`: Principal name or empty string if anonymous.
  - `$security.hasRole('ROLE_NAME')`: Checks if current user has the specified security role.
  - `$security.roles`: Set of assigned role names.
- **Optionality Invariant**: `quarkus-security` is purely optional (`<optional>true</optional>` in Maven, `compileOnly` in Gradle). Downstream applications without Quarkus Security boot cleanly without missing-bean errors or class resolution failures via runtime-safe Arc bean and reflection inspection.

### 8.1 CSRF Protection Model
Unlike `viet-template-spring-security` which automatically binds an active Spring Security CSRF token into `$csrf`, Quarkus REST does not expose an automatic template-level CSRF model out of the box. Applications using CSRF protection in Quarkus should provide the token to the template model or render context explicitly via `VietTemplateRenderer.render("template.vtl", Map.of("csrfToken", token))`.

---

## 9. Coexistence with Quarkus Qute

Viet Template and Quarkus Qute can be used simultaneously in the same application:
- **No Bean Conflicts**: Viet Template produces `io.github.minh124199.viettemplate.api.TemplateEngine`, while Qute produces `io.quarkus.qute.Engine`.
- **Distinct Template Identification**: Standard VTL files (`.vtl`, `.vm`, `.html.vtl`) are processed by Viet Template, while Qute files (`.qute.html`, `.txt`) are handled by Qute.
- **Side-by-Side Injection**: Both engines can be injected and used within the same resource or service class.

