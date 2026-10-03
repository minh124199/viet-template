# Viet Template

A compile-first, low-allocation JVM template engine featuring Velocity Template Language (VTL) compatibility, modern execution backends, and defense-in-depth security defaults.

[![CI](https://github.com/minh124199/viet-template/actions/workflows/ci.yml/badge.svg)](https://github.com/minh124199/viet-template/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.minh124199/viet-template-api)](https://central.sonatype.com/artifact/io.github.minh124199/viet-template-api)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Viet Template is an independent JVM template engine designed for modern Java applications that value the familiar syntax of Apache Velocity templates but require modern JVM performance, low object allocation, native GraalVM compilation, and robust security boundaries.

---

## Why Viet Template?

Many enterprise JVM applications still rely on legacy template engines that depend on heavy dynamic reflection, lack GraalVM native image support, allocate excessive heap memory, or expose dangerous reflection attack surfaces. Viet Template solves this:

- **Drop-in Velocity Syntax Compatibility**: Drop-in syntax compatibility for Velocity Template Language (VTL) covering 80/80 supported grammar features and 100% accounted differential behavior coverage across 301 differential scenarios against Apache Velocity 2.4.1 in the Technology Compatibility Kit (TCK): 295 exact matches (98.01%), 5 documented expected differences, 1 extension, 0 unsupported, 0 regressions.
- **High-Throughput Multi-Tier Execution**: Offers both a lightweight development interpreter (Viet-IR) and a high-performance Ahead-Of-Time bytecode compiler (Viet-AOT) delivering **1.3x to 5.3x higher throughput** than Apache Velocity 2.4.1.
- **Precompiled Bytecode & Zero Reflection**: Compiles templates to standard Java 21 bytecode (`.class` files) with compiler-assigned variable slots and pre-encoded UTF-8 literals, eliminating runtime reflection and AST traversal.
- **GraalVM Native Image Ready**: Seamlessly compiles to native executables via out-of-the-box `VietTemplateRuntimeHints`, Spring AOT, and Quarkus deployment build steps (empirically qualified on Linux x86_64 Mandrel 25.0.4.1-Final and Oracle GraalVM 25.0.4+7.1).
- **First-Class Spring & Quarkus Ecosystems**: Turnkey auto-configuration for Spring Boot 4 / Spring Framework 7 / Spring Security 7 with secure-by-default HTML auto-escaping for web views, and idiomatic CDI extension for Quarkus 3 with build-time AOT compilation and dev-mode hot reload.
- **Defense-in-Depth Security**: Denies access to reflection (`java.lang.reflect.*`, `java.lang.invoke.*`), classloaders, and system resources by default. Distinguishes developer-friendly denylist defense-in-depth (`MemberAccessPolicy.standard()`) from strict fail-closed sandboxing (`MemberAccessPolicy.safe()`), with monotonic execution budgets (`RenderBudget`) and macro recursion limits.

---

## Project Status

| Dimension | Detail |
|---|---|
| **Latest Published Stable Release** | `1.1.0` (published 2026-10-03; 1.0.1 published 2026-09-28; 1.0.0 GA published 2026-09-26; available on Maven Central & GitHub Releases) |
| **Active Development** | `1.1.1-SNAPSHOT` (post-1.1.0 development on `main`) |
| **Java Baseline** | Java 21 LTS (`--release 21`, major version 65) |
| **Primary Target** | Java 25 (optimized memory & runtime qualification) |
| **Maven Group ID** | `io.github.minh124199` |
| **Gradle Plugin ID** | `io.github.minh124199.viet-template` |
| **JPMS Status** | Ordinary non-modular JARs (no `module-info.java`, no `Automatic-Module-Name` header) |

> [!NOTE] Java Module System (JPMS) Disclaimer
> Viet Template 1.0.0 ships as ordinary non-modular JARs. When placed on the Java module path they may be treated by the JVM as automatic modules using derived names, but those derived names are not a frozen compatibility contract.

---

## Framework & Build Tool Compatibility

| Integration | Declared Minimum | Verified Versions | Canonical Version | Native Status |
|---|---|---|---|---|
| **Spring Boot** | `3.3.0` | `3.3.5`, `4.1.1` | `4.1.1` | Supported (Oracle GraalVM 25.0.4+7.1) |
| **Spring Framework** | `6.1.0` | `6.1.14`, `7.0.9` | `7.0.9` | Supported (Oracle GraalVM 25.0.4+7.1) |
| **Spring Security** | `6.3.0` | `6.3.4`, `6.5.11`, `7.0.7`, `7.1.1` | `7.1.1` | Supported (Oracle GraalVM 25.0.4+7.1) |
| **Quarkus** | `3.33.0` | `3.33.3` (LTS), `3.39.4` | `3.39.4` | Supported (Mandrel 25.0.4.1-Final) |
| **Apache Maven** | `3.8.0` | `3.9.9` | `3.9.9` (wrapper) | N/A (Build Tool) |
| **Gradle** | `8.5` | `9.7.1` | `9.7.1` (wrapper) | N/A (Build Tool) |

### Native Image Toolchains
- **Spring Native**: Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS) on Linux x86_64.
- **Quarkus Native**: Mandrel 25.0.4.1-Final (mandrel-java25-25.0.4.1-Final, Java 25) on Linux x86_64.
- **Other Platforms**: Experimental and unverified for native compilation; standard JVM runs cross-platform.

---

## 5-Minute Quickstart

### 1. Standalone Java Application

Add `viet-template-api`, `viet-template-runtime`, and `viet-template-vtl-interpreter` to your `pom.xml`:

```xml
<!-- Latest Published Stable: 1.1.0 -->
<dependencies>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-api</artifactId>
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-runtime</artifactId>
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.minh124199</groupId>
        <artifactId>viet-template-vtl-interpreter</artifactId>
        <version>1.1.0</version>
    </dependency>
</dependencies>
```

Render templates using the clean, type-safe API:

```java
import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        TemplateEngine engine = TemplateEngine.builder()
            .repository(ClasspathTemplateRepository.of("templates/"))
            .memberAccessPolicy(MemberAccessPolicy.standard())
            .build();

        RenderContext context = RenderContext.builder()
            .put("name", "World")
            .put("items", List.of("Fast", "Safe", "Modern"))
            .build();

        String html = engine.render("greeting.vtl", context);
        System.out.println(html);
    }
}
```

### 2. Spring Boot 4 Starter

Add the starter dependency:

```xml
<!-- Latest Published Stable: 1.1.0 -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

Configure `application.properties`:

```properties
# Template resolution and encoding
viet-template.prefix=templates/
# Multi-suffix support (ordered lookup, e.g. .vtl before legacy .vm):
viet-template.suffixes=.vtl,.vm
# or legacy single suffix: viet-template.suffix=.vtl
viet-template.charset=UTF-8

# View caching and runtime compilation
viet-template.cache=true
viet-template.runtime-compilation-enabled=true
```

Controllers return view names resolved automatically by `VietTemplateViewResolver`:

```java
@Controller
public class WebController {
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Spring Boot 4 on Viet Template");
        return "index"; // Resolves to templates/index.vtl
    }
}
```

### 3. Quarkus 3 Extension

Add the extension dependency:

```xml
<!-- Latest Published Stable: 1.1.0 -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-quarkus</artifactId>
    <version>1.1.0</version>
</dependency>
```

Inject and render inside any CDI bean or JAX-RS resource:

```java
@Path("/hello")
public class HelloResource {

    @Inject
    VietTemplateRenderer renderer;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String hello(@QueryParam("name") String name) {
        return renderer.render("hello.vtl", Map.of("name", name != null ? name : "World"));
    }
}
```

Templates placed in `src/main/resources/templates/` are compiled ahead-of-time during `mvn package` or `gradle build` (producing ~52 MB native binaries on Linux x86_64) and hot-reloaded during `quarkus dev`. See the [Quarkus Extension Guide](docs/extensions/quarkus.md) for details.

---

## Migrating from Apache Velocity in 3 Steps

Viet Template was engineered as an independent, modern replacement for Apache Velocity:

1. **Step 1: Swap Dependencies**: Replace `org.apache.velocity:velocity-engine-core` with `viet-template-spring-boot-starter` or `viet-template-api`.
2. **Step 2: Keep Existing Templates**: Retain all existing `.vm` and `.vtl` files. Configure `viet-template.suffixes=.vtl,.vm` for ordered multi-extension lookup during gradual migration. Viet Template achieves 100% compatibility across all 80 standard VTL grammar features.
3. **Step 3: Update Engine Initialization**: Replace `VelocityEngine` and `VelocityContext` with `TemplateEngine` and `RenderContext`.

See the comprehensive [Apache Velocity Migration Guide](docs/migration/velocity-migration-guide.md) and [Velocity Differences Catalog](docs/migration/velocity-differences.md) for full details.

---

## Comparative Performance Highlights

Evaluated across workloads **C01–C08** in JMH 1.37 benchmarks on a reference Linux x86_64 host (Intel Core i5-8350U @ 1.70GHz, 4 physical cores / 8 logical threads, 12 GB RAM, Linux 7.2.4-3-cachyos) across **Java 21 LTS** (OpenJDK 21.0.12.1+1) and **Java 25** (Oracle GraalVM 25.0.4+7.1) using standardized flags (`-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC`; canonical 1.0.0 baseline commit `b951021`):

| Workload | Viet-IR | Viet-AOT | Apache Velocity 2.4.1 | Quarkus Qute 3.39.4 | jte 3.2.4 | Thymeleaf 3.1.5 |
|---|---|---|---|---|---|---|
| **C01 Static HTML** (J25) | 10.76M ops/s | **55.30M ops/s** | 14.99M ops/s | 21.35M ops/s | 9.26M ops/s | 1.34M ops/s |
| **C02 Scalar Vars** (J25) | 1.85M ops/s | **2.27M ops/s** | 1.07M ops/s | 2.12M ops/s | 5.33M ops/s | 290.3K ops/s |
| **C03 Deep Chains** (J25) | 192.7K ops/s | **924.9K ops/s** | 298.8K ops/s | 1.01M ops/s | 5.62M ops/s | 78.3K ops/s |
| **C04 Conditionals** (J25) | 1.50M ops/s | **6.63M ops/s** | 1.25M ops/s | 2.79M ops/s | 6.70M ops/s | 277.4K ops/s |
| **C07 Nested Loop** (J25) | 99.9K ops/s | **547.9K ops/s** | 220.8K ops/s | 432.8K ops/s | 3.22M ops/s | 52.3K ops/s |

- **Velocity Modernization Payoff**: Under the evaluated benchmark configuration, Viet-AOT achieved higher throughput than Apache Velocity 2.4.1 across all C01–C08 scenarios, delivering up to **5.3x higher throughput** on conditional logic (C04).
- **Compiled Standing**: Under the evaluated benchmark environment, Viet-AOT achieved higher throughput on raw static HTML rendering (**55.30M ops/s**) than tested alternatives and reached parity with jte 3.2.4 on conditional logic (**6.63M vs 6.70M ops/s**). On deep reflection graphs and collection iterations, jte leads compiled throughput.
- **Low Memory Allocation**: Allocated as few as **176 bytes/op** on static output (C01) on Java 25 (fewer bytes per operation than all tested alternatives).

For full methodology, allocation metrics, confidence intervals, and Java 21 results, see [Comparative Engine Benchmarks](docs/performance/comparative-benchmarks.md).

---

## Documentation Directory

Explore the complete documentation suite organized by topic:

### Getting Started
- **[5-Minute Quickstart](docs/getting-started/quickstart.md)** — Step-by-step standalone Java and Spring Boot setup.
- **[Support Matrix](docs/getting-started/support-matrix.md)** — JDK runtimes (Java 21/25), build tools, and OS support.

### Language & Grammar
- **[VTL Syntax Reference](docs/language/syntax-reference.md)** — Comprehensive grammar, directives, expressions, and operators.
- **[3-State Undefined & Null Semantics](docs/language/undefined-null-semantics.md)** — Precise behavior of `UNDEFINED`, `DEFINED_NULL`, and `DEFINED_VALUE`.
- **[Foreach & Scopes](docs/language/foreach-and-scopes.md)** — Loop metadata (`$foreach.index`, `$foreach.hasNext`), break semantics, and frame scoping.
- **[Macros & Layouts](docs/language/macros-and-layouts.md)** — Velocimacros, global libraries, and two-stage layout rendering.
- **[Typed Models & Strict Contract Validation](docs/language/typed-models.md)** — Optional static contracts (`TemplateContract`), strict type checking (`OFF`, `WARN`, `ERROR`), and compile-time diagnostics.

### Migration from Apache Velocity
- **[Velocity Migration Guide](docs/migration/velocity-migration-guide.md)** — End-to-end migration roadmap, tools, and best practices.
- **[Velocity Differences Catalog](docs/migration/velocity-differences.md)** — Architectural and behavioral differences (arithmetic, null `#set`, path sandboxing).
- **[Authoritative Compatibility Matrix](docs/migration/compatibility-matrix.md)** — Machine-verified status of all 80 VTL features.

### Security & Sandboxing
- **[Secure Templates Guide](docs/security/secure-templates.md)** — `MemberAccessPolicy` sandboxing, `RenderBudget` CPU/memory limits, path confinement, and Spring Security projection facades.
- **[Security Threat Model](docs/10-security-model.md)** — Core security architecture and capability policies.

### Production & Ahead-Of-Time (AOT)
- **[Production & AOT Deployment](docs/deployment/production-aot.md)** — Dev vs Prod profiles, precompiled bytecode, `templates.idx`, and cache tuning.
- **[GraalVM Native Image Guide](docs/native-image/graalvm-native-image.md)** — Ahead-of-Time compilation, runtime hints, and native binary packaging.

### Build Tooling
- **[Apache Maven Plugin](docs/build-tooling/maven.md)** — Build-time AOT precompilation and verification via `viet-template-maven-plugin`.
- **[Gradle Plugin](docs/build-tooling/gradle.md)** — Gradle Kotlin/Groovy DSL plugin configuration (`plugins { id("io.github.minh124199.viet-template") version "1.0.1" }`) and incremental build-cache.

### Developer Tooling & Schemas
- **[Canonical Tooling Schemas](docs/schema/contract-schema-v1.md)** — Language-neutral JSON schema format (`*.vt-schema.json`) for template parameters and types.
- **[TypeScript Declaration Projection](docs/schema/typescript-projection-v1.md)** — Automated, deterministic generation of TypeScript declaration files (`*.d.ts`) from contract schemas.
- **[Language Server Protocol (LSP)](docs/tooling/language-server-foundation.md)** — Language server implementation providing diagnostics, autocompletion, hover, and definition navigation.
- **[Visual Studio Code Extension](docs/tooling/vscode-extension.md)** — Official VS Code editor integration (`editors/vscode`) delivering language features, TextMate syntax highlighting, and LSP integration.
- **[IntelliJ IDEA Plugin](docs/tooling/intellij-plugin.md)** — Official IntelliJ IDEA plugin (`editors/intellij`) providing file type registration, syntax highlighting, commenter, and LSP integration backed by the Viet Template Language Server.

### Framework Integrations
- **[Spring Boot Integration Guide](docs/spring/spring-boot-integration.md)** — Spring Boot 4 / Framework 7 starter, property catalog, and reactive view resolution.
- **[Spring Security Integration](docs/36-spring-security-integration.md)** — `$security` and `$csrf` template facades with contextual escaping.
- **[Quarkus Extension Guide](docs/extensions/quarkus.md)** — Quarkus 3 CDI extension, AOT template compilation, live reload, and Qute coexistence.

### Diagnostics & Extensions
- **[Diagnostics & Error Catalog](docs/diagnostics/error-catalog.md)** — Complete catalog of parse-time, compile-time, and runtime error codes with remedies.
- **[Extension Guide & SPIs](docs/extensions/extension-guide.md)** — Implement custom loaders, escapers, member resolvers, and engine customizers.

### Performance & Benchmarks
- **[Comparative Engine Benchmarks](docs/performance/comparative-benchmarks.md)** — C01–C08 throughput and allocation results across Java 21 and Java 25.
- **[1.1 Performance Foundation](docs/performance/1.1-performance-foundation.md)** — 1.0.0 canonical performance baseline, hotspot analysis, and 1.1 optimization ranking.
- **[M25 Typed Specialization Benchmarks](docs/performance/1.1-m25-specialization-benchmarks.md)** — Multi-argument direct dispatch, runtime divergence guards, and root-slot profiling evidence.
- **[Benchmark Plan](docs/15-benchmark-plan.md)** — JMH methodology and 7-part DSA acceptance rule.

### Architecture & Readiness
- **[1.0 Readiness Gap Analysis](docs/1.0-readiness-gap-analysis.md)** — 15-point readiness audit and public surface encapsulation roadmap.
- **[System Architecture](docs/01-system-architecture.md)** — Pipeline architecture from AST lowering to bytecode execution.
- **[Project Charter](docs/00-project-charter.md)** — Design goals, scope, and non-goals.
- **[Implementation Roadmap](docs/18-roadmap.md)** — Phased development roadmap.
- **[Implementation Checklist](docs/20-implementation-checklist.md)** — Engineering milestone checklist.

---

## Building from Source

Viet Template enforces **first-class dual-build parity** between Apache Maven and Gradle (Kotlin DSL). Both build systems produce byte-for-byte verified artifacts:

### Using Apache Maven

```bash
# Build all modules, run test suites, and package artifacts
./mvnw clean verify

# Check code formatting
./mvnw spotless:check

# Format source code
./mvnw spotless:apply
```

### Using Gradle

```bash
# Build all modules and run test suites
./gradlew clean build

# Check code formatting
./gradlew spotlessCheck

# Format source code
./gradlew spotlessApply
```

### Build Parity Verification

```bash
# Verify dual-build parity between Maven and Gradle
python3 scripts/verify-build-parity.py

# Verify documentation consistency and links
python3 scripts/verify-documentation.py
```

---

## Contributing

Contributions are welcome! Please review [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull requests.

Key contributor rules:
1. **Independent implementation rule**: Do not copy or decompile Apache Velocity source code. Syntax and behavior are derived from public documentation, language specifications, and black-box differential tests against an external test-scope oracle.
2. **Dual-build parity**: All code changes must pass `python3 scripts/verify-build-parity.py` and build cleanly under both `./mvnw clean verify` and `./gradlew clean build`.
3. **TCK regression protection**: Differential compatibility tests must pass under `viet.tck.mode=STRICT`.

Please also review our [Code of Conduct](CODE_OF_CONDUCT.md) and [Security Policy](SECURITY.md).

---

## Authorship & Agent-Assisted Development Disclosure

Viet Template is maintained by Minh Nguyen (`minh124199`). Automated, agent-assisted workflows (`M18 Implementation`) have been utilized for milestones implementation, differential TCK harness development, and documentation synchronization under strict maintainer direction and human review.

---

## License & Trademark Notice

Viet Template is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

*Apache Velocity and Apache are trademarks of the Apache Software Foundation. This project is an independent implementation with clean-room dependency isolation and is not affiliated with, sponsored by, or endorsed by the Apache Software Foundation.*
