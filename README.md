# Viet Template

A compile-first JVM template engine featuring Velocity Template Language (VTL) compatibility, modern execution backends, and defense-in-depth security defaults.

[![CI](https://github.com/minh124199/viet-template/actions/workflows/ci.yml/badge.svg)](https://github.com/minh124199/viet-template/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.minh124199/viet-template-api)](https://central.sonatype.com/artifact/io.github.minh124199/viet-template-api)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Viet Template is an independent JVM template engine for modern Java applications that use Apache Velocity-style templates and need compiled execution, GraalVM native-image integration, and explicit security boundaries. Performance and allocation depend on the workload; see the qualified comparative report below.

---

## Why Viet Template?

Many enterprise JVM applications still rely on legacy template engines that depend on heavy dynamic reflection, lack GraalVM native image support, allocate excessive heap memory, or expose dangerous reflection attack surfaces. Viet Template solves this:

- **Velocity Language Compatibility**: Implements 80 supported VTL grammar features and accounts for 301 differential scenarios against Apache Velocity 2.4.1 in the Technology Compatibility Kit (295 exact matches, 5 documented expected differences, 1 extension, and 0 unsupported or unclassified regressions).
- **Multi-Tier Execution**: Offers a development interpreter (Viet-IR) and ahead-of-time bytecode compilation (Viet-AOT); performance depends on workload and is summarized in the qualified comparative report below.
- **Compiled Bytecode**: Compiles templates to standard Java 21 bytecode (`.class` files), with compiler-assigned variable slots and pre-encoded UTF-8 literals. Dynamic property and method access remains governed by the runtime access policy.
- **GraalVM Native Image Ready**: Seamlessly compiles to native executables via out-of-the-box `VietTemplateRuntimeHints`, Spring AOT, and Quarkus deployment build steps (empirically qualified on Linux x86_64 Mandrel 25.0.4.1-Final and Oracle GraalVM 25.0.4+7.1).
- **Spring & Quarkus Integrations**: Spring Boot / MVC integration defaults HTML auto-escaping on for views; optional Spring Security integration supplies presentation facades. Quarkus provides a CDI extension with build-time template compilation and dev-mode reload; its optional security integration supplies `$security` and CSRF presentation facades when Quarkus Security is present.
- **Defense-in-Depth Security**: Blocks universal reflection and class-loading pivots by default. Distinguishes the developer-oriented denylist (`MemberAccessPolicy.standard()`) from the strict allowlist (`MemberAccessPolicy.safe()`), with monotonic render budgets and a separate macro invocation limit.

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
> Viet Template releases are ordinary non-modular JARs. When placed on the Java module path they may be treated by the JVM as automatic modules using derived names, but those derived names are not a frozen compatibility contract.

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

Templates placed in `src/main/resources/templates/` are discovered and compiled by the Quarkus build-time extension; Quarkus dev mode watches templates and reloads changes. Native executable generation is a separate native-image build step. See the [Quarkus Extension Guide](docs/extensions/quarkus.md) for details.

---

## Migrating from Apache Velocity in 3 Steps

Viet Template was engineered as an independent, modern replacement for Apache Velocity:

1. **Step 1: Swap Dependencies**: Replace `org.apache.velocity:velocity-engine-core` with `viet-template-spring-boot-starter` or `viet-template-api`.
2. **Step 2: Keep Existing Templates**: Retain existing `.vm` and `.vtl` files where their syntax and behavior are covered by Viet Template's compatibility contract. Configure `viet-template.suffixes=.vtl,.vm` for ordered multi-suffix lookup during gradual migration; review the [compatibility matrix](docs/migration/compatibility-matrix.md) and [differences catalog](docs/migration/velocity-differences.md) before migrating.
3. **Step 3: Update Engine Initialization**: Replace `VelocityEngine` and `VelocityContext` with `TemplateEngine` and `RenderContext`.

See the [Apache Velocity Migration Guide](docs/migration/velocity-migration-guide.md), [Velocity Differences Catalog](docs/migration/velocity-differences.md), and [Compatibility Matrix](docs/migration/compatibility-matrix.md) before migration.

---

## Comparative Performance Highlights

Viet Template 1.1.0 release qualification was evaluated across two runtime profiles: **genuine OpenJDK 21** (`21.0.12.1+1`) and **genuine OpenJDK 25** (`25.0.4.1`) with JMH 1.37 (3 forks, 5 warmups, 10 measurements; G1, fixed 2 GiB heap). Measurements were conducted on Linux x86_64, kernel `7.2.8-2-cachyos`, Intel Core i5-8350U (4 physical / 8 logical cores). Tested comparators were Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, and Thymeleaf 3.1.5.RELEASE.

### OpenJDK 21 (J21-G1) Highlights

| Workload | Viet-AOT | Velocity | Qute | jte |
|---|---:|---:|---:|---:|
| C01 static HTML | 11.56M ops/s | 5.60M ops/s | 10.88M ops/s | 7.42M ops/s |
| C03 deep property chains | 1.61M ops/s | 288K ops/s | 909K ops/s | 4.52M ops/s |
| C04 conditionals | 3.71M ops/s | 1.14M ops/s | 2.34M ops/s | 5.66M ops/s |
| C06 large table (100 rows) | 23.5K ops/s | 14.1K ops/s | 21.4K ops/s | 75.5K ops/s |
| C08 HTML escaping | 760K ops/s | 581K ops/s | 734K ops/s | 1.53M ops/s |

### OpenJDK 25 (J25-G1) Highlights

| Workload | Viet-AOT | Velocity | Qute | jte |
|---|---:|---:|---:|---:|
| C01 static HTML | 11.34M ops/s | 5.87M ops/s | 12.13M ops/s | 7.38M ops/s |
| C03 deep property chains | 1.68M ops/s | 357K ops/s | 1.01M ops/s | 4.24M ops/s |
| C04 conditionals | 5.30M ops/s | 1.33M ops/s | 2.56M ops/s | 5.49M ops/s |
| C06 large table (100 rows) | 25.4K ops/s | 15.4K ops/s | 22.6K ops/s | 80.3K ops/s |
| C08 HTML escaping | 667K ops/s | 481K ops/s | 561K ops/s | 960K ops/s |

These workload-specific scores do not establish an overall engine ranking. Across all C01–C08 workloads on both OpenJDK 21 and OpenJDK 25, Viet-AOT exceeded Apache Velocity. On J21, Viet-IR matches the immutable 1.0.0 baseline within policy on all eight workloads. On J25, genuine OpenJDK 25 was qualified; the historical 1.0.0 baseline was recorded on Oracle GraalVM 25 with the JVMCI compiler, whereas empirical same-runtime re-evaluation of 1.0.0 on genuine OpenJDK 25 HotSpot confirms 1.1.0 exhibits zero code regressions across all eight workloads (see [`benchmark-evidence/1.0.0-openjdk25-rerun/`](benchmark-evidence/1.0.0-openjdk25-rerun/)).

See the [Comparative Engine Benchmarks](docs/performance/comparative-benchmarks.md) report for full six-engine C01–C08 results, JMH error margins, allocation data, and baseline comparison. Durable raw evidence and environment records are in [`benchmark-evidence/1.1.0/`](benchmark-evidence/1.1.0/), with [`benchmark-evidence/1.1.0-j21-partial/`](benchmark-evidence/1.1.0-j21-partial/) preserved as the initial post-release qualification.

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
- **[Gradle Plugin](docs/build-tooling/gradle.md)** — Gradle Kotlin/Groovy DSL plugin configuration (`plugins { id("io.github.minh124199.viet-template") version "1.1.0" }`) and incremental build-cache.

### Developer Tooling & Schemas
- **[Canonical Tooling Schemas](docs/schema/contract-schema-v1.md)** — Language-neutral JSON schema format (`*.vt-schema.json`) for template parameters and types.
- **[TypeScript Declaration Projection](docs/schema/typescript-projection-v1.md)** — Automated, deterministic generation of TypeScript declaration files (`*.d.ts`) from contract schemas.
- **[Language Server Protocol (LSP)](docs/tooling/language-server-foundation.md)** — Language server implementation providing diagnostics, autocompletion, hover, and definition navigation.
- **[Visual Studio Code Extension](docs/tooling/vscode-extension.md)** — Repository-maintained VS Code client (`editors/vscode`) with TextMate syntax highlighting and LSP integration; local VSIX packaging is documented, marketplace publication is not established.
- **[IntelliJ IDEA Plugin](docs/tooling/intellij-plugin.md)** — Repository-maintained IntelliJ client (`editors/intellij`) with file type registration, syntax highlighting, commenter, and LSP integration; local ZIP packaging is documented, marketplace publication is not established.

### Framework Integrations
- **[Spring Boot Integration Guide](docs/spring/spring-boot-integration.md)** — Spring Boot starter, current property catalog, and Spring MVC view resolution.
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
