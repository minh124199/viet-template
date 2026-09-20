# Viet Template

A compile-first, low-allocation JVM template engine featuring Velocity Template Language (VTL) compatibility, modern execution backends, and defense-in-depth security defaults.

[![CI](https://github.com/minh124199/viet-template/actions/workflows/ci.yml/badge.svg)](https://github.com/minh124199/viet-template/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.minh124199/viet-template-api)](https://central.sonatype.com/artifact/io.github.minh124199/viet-template-api)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Viet Template is a clean-room JVM template engine designed for applications that value the concise syntax of Velocity templates but require modern JVM performance, type checking, low object allocation, and strict security isolation.

---

## Why Viet Template?

Many existing JVM template engines require teams to choose between familiar, flexible syntax and modern runtime efficiency:

- **Clean-Room VTL Surface**: Retains familiar Velocity Template Language (VTL) syntax without carrying legacy runtime architecture or deprecated reflection mechanisms.
- **Compile-First Architecture**: Parses templates into an immutable AST, lowers them to a control-flow-aware Intermediate Representation (IR), and applies an IR optimization pipeline of 11 distinct optimization pass implementations (13 transformation executions, with static UTF-8 pre-encoding lowering and O160 invariant verification) before rendering.
- **Multi-Tier Execution**: Supports AST interpretation for rapid development, an optimized IR interpreter, dynamic method handles with Polymorphic Inline Caches (PIC), and direct Java 21 bytecode generation (AOT).
- **Streaming & Low Allocation**: Emits static text as pre-encoded UTF-8 byte chunks, formats primitive numbers without intermediate `String` allocations, and streams output directly to `Writer` or `OutputStream`.
- **Defense-in-Depth Security**: Denies access to reflection (`java.lang.reflect.*`, `java.lang.invoke.*`), classloaders, processes, threads, and system resources by default. Enforces monotonic execution budgets (`RenderBudget`) across includes, macros, and layouts.
- **Zero Core Framework Bloat**: The core engine and runtime have zero mandatory dependencies on Spring, servlet containers, logging frameworks, or third-party bytecode manipulators.

---

## Project Status

| Item | Value |
| :--- | :--- |
| **Current Published Release** | `0.2.1` (2026-09-17) |
| **Development Branch** | `0.2.2-SNAPSHOT` | `main` | Under active development (M17 GraalVM native image, Spring 7/Boot 4 modernization, and DevTools restart hardening complete; M18 TCK release gates next) |
| **Maturity Level** | **Pre-1.0 (`0.2.x`)** |
| **Maven Group** | `io.github.minh124199` |
| **Java Baseline** | Java 21 (`--release 21`, major version 65), runtime optimized for Java 25 |

> [!NOTE]
> Viet Template is in active pre-1.0 development. The core public API, runtime, and differential TCK are thoroughly tested and published to Maven Central, but interfaces and internal representations may evolve prior to the 1.0 release. It should not be treated as a finalized, production-frozen library.

### Feature Maturity Matrix

- **Implemented**:
  - Clean-room lexer and Pratt expression parser with compiler-grade source span diagnostics.
  - AST interpreter and lower-level IR interpreter.
  - 11-pass IR compiler optimization pipeline (13 transformation executions including repeated dead-code elimination and text-chunk merging, constant folding, loop specialization, and escape specialization), supplemented by mandatory O45 variable slot assignment, a static UTF-8 pre-encoding lowering stage, and an unconditional O160 invariant verification gate.
  - 0.2.0 High-performance `ExecutionFrame` backed by compiler-assigned variable slots (`EvaluationValue[] slots`) for IR interpreter and AOT bytecode backends, preserving 3-state evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`) and dynamic fallback coherence.
  - Indexed compilation cache invalidation (`TemplateId -> Set<CompileCacheKey>` reverse index) with per-template lock striping.
  - Dynamic linker with monomorphic and Polymorphic Inline Caches (PIC, depth 4) backed by classloader-safe weak references.
  - Architecture Tier 3 direct Java 21 bytecode compiler (`ClassFileWriter`) producing major version 65 classfiles with full `StackMapTable` tracking.
  - Bounded, thread-safe template compilation cache with LRU eviction and atomic handle swapping.
  - Pluggable template repository SPI (`Classpath`, `Filesystem`, `Composite`, `InMemory`) with directory traversal protection.
  - Two-stage layout rendering plans (`DefaultLayoutRenderPlan`) and global Velocimacro library caching.
  - Pluggable member access policies (`MemberAccessPolicy`, `SensitiveObjectClassifier`) and monotonic render budgets (`RenderBudget`).
  - Authoritative Technology Compatibility Kit (`viet-template-tck`) evaluating 301 differential scenarios against Apache Velocity 2.4.1.
  - Dedicated JMH benchmark module (`viet-template-benchmarks`) under Milestone M19.1 with 10 canonical suites and dual Gradle/Maven build parity.
  - Public API & SPI stabilization and surface containment (Milestones M14 and M14.1): 80 core stable baseline contracts (`1.0-public-api.txt`), 91 stable-classified public types (76 `STABLE_API` + 15 `STABLE_SPI`), exact 352-type deterministic public surface classification, signature leak protection, integration boundary architecture enforcement, and hardened concrete output stream lifecycles.
  - Ahead-Of-Time (AOT) build tooling (`viet-template-maven-plugin` and `viet-template-gradle-plugin`) under Milestone M15: precompiling templates at build time into self-contained Java 21 bytecode with automated ClassLoader discovery (`templates.idx`), incremental SHA-256 caching, and 100% byte-for-byte dual-build parity.
  - Spring Framework 7 & Spring Boot 4 integration (`viet-template-spring-boot-starter`, `viet-template-spring`, `viet-template-spring-boot-autoconfigure`) under Milestone M16: verified canonical baseline in CI against Spring Framework 7.0.9, Spring Boot 4.1.1, and Spring Security 7.1.1 on Java 21+ / Jakarta Servlet 6.1.0 / Tomcat 11.0.24 with full virtual threads support, featuring thread-safe `VietTemplateView`, caching `VietTemplateViewResolver` with AOT index discovery fallback, non-closing servlet stream ownership, full configuration properties (`viet-template.*`), and 100% byte-for-byte dual-build parity.
  - Spring Security 7 integration (`viet-template-spring-security`) under Milestone M16.1: optional, zero-core-dependency module providing read-only facades `SecurityView` and `CsrfView`, factory SPIs `SecurityViewFactory` and `CsrfViewFactory`, and auto-configuration `VietTemplateSecurityAutoConfiguration` (`viet-template.security.enabled=true`). Strictly protects boundaries with zero raw framework objects in template scope, sensitive token redaction in `toString()`, automatic HTML contextual escaping against XSS, multi-generation binary compatibility (Spring Security 6.3.x, 6.5.x, 7.0.x, 7.1.x), and 100% byte-for-byte dual-build AOT parity.
  - GraalVM Native Image & Spring AOT compatibility (Milestone M17 Phase A): out-of-the-box runtime hints (`VietTemplateRuntimeHints`, `VietTemplateSecurityRuntimeHints`) registering precompiled template bytecode, `templates.idx` resource discovery, and security view reflection. GraalVM 25 native execution is verified by building and running the native Spring Boot fixture and serving real Viet Template endpoints.
  - Spring Boot DevTools restart & ClassLoader lifecycle hardening (Milestone M17 Phase B): robust `close()` lifecycle and thread termination, Mode A dynamic hot reload without restart, Mode B AOT recompile + trigger restart with ClassLoader turnover, stale template deletion handling, zero ClassLoader leaks across multi-generation restarts, and 4 dual-build DevTools fixtures.
  - Maven Central publication metadata hardening: canonical per-module deep links (`/tree/main/<module>`), root SCM inheritance suppression controls (`child.scm.*.inherit.append.path="false"`), and automated CI metadata and effective POM verification.
- **Experimental**:
  - Dynamic call-site specialization in AOT bytecode when complete type signatures are absent.
  - File-system hot-reload watcher (`DevelopmentFileWatcher`) using NIO `WatchService`.
- **Planned (Future Releases)**:
  - Milestone M18: Technology Compatibility Kit (TCK) release gate & multi-engine performance benchmarks.

---

## Installation

Viet Template artifacts are published to Maven Central under group ID `io.github.minh124199`.

### Dependency Selection

- **`viet-template-vtl-interpreter`** *(Recommended)*: The complete template engine for standard applications. Declaring this dependency transitively brings in `viet-template-api`, `viet-template-runtime`, and `viet-template-language-vtl`, providing the full parser, runtime, compiler, cache, and execution backends.
- **`viet-template-api`**: Core public interfaces and records only. Useful for libraries or modules defining template contracts without pulling in the runtime engine.
- **`viet-template-spring-boot-starter`**: Spring Boot starter providing auto-configuration for Spring MVC `View` and `ViewResolver`, Ahead-Of-Time precompiled template discovery, and configuration properties.
- **`viet-template-spring-security`**: Optional Spring Security integration providing `$security` (authentication, roles, authorities) and `$csrf` (parameter, header, token) projection facades for templates without exposing raw framework objects.
- **`viet-template-runtime`**: Streaming output primitives, escapers, and dynamic linker. Only declared directly when developing custom output buffers or standalone escapers without the interpreter.
- **`viet-template-language-vtl`**: VTL grammar parser, AST model, semantic analyzer, and IR compiler.

### Apache Maven

Add the engine dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-vtl-interpreter</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

Add the dependency to your `build.gradle.kts`:

```kotlin
implementation("io.github.minh124199:viet-template-vtl-interpreter:0.2.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'io.github.minh124199:viet-template-vtl-interpreter:0.2.0'
```

---

## Quick Start

### 1. Create a Template

Place your template file (e.g. `src/main/resources/templates/user-card.vm`):

```velocity
<div class="user-card">
  <h2>$user.name</h2>
  #if($user.admin)
    <span class="badge admin">Administrator</span>
  #else
    <span class="badge user">Standard User</span>
  #end

  <ul>
  #foreach($role in $user.roles)
    <li>[$foreach.count] $role</li>
  #end
  </ul>
</div>
```

### 2. Parse and Render in Java (Published 0.1.0 API)

In the published `0.1.0` release on Maven Central, templates are parsed directly using `VtlParser` and rendered using `VtlInterpreter`:

```java
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.WriterTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class QuickStartExample {

  public record User(String name, boolean admin, List<String> roles) {}

  public static void main(String[] args) throws IOException {
    // 1. Read template source (from classpath, filesystem, or String)
    String templateString;
    try (InputStream in = QuickStartExample.class.getResourceAsStream("/templates/user-card.vm")) {
      if (in == null) {
        throw new IllegalStateException("Template resource not found: /templates/user-card.vm");
      }
      templateString = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    SourceText source = SourceText.of("user-card.vm", templateString);

    // 2. Parse into an immutable AST
    VtlTemplate ast = VtlParser.parse(source).template();

    // 3. Populate render context
    User user = new User("Alice", true, List.of("DEVELOPER", "SECURITY_AUDITOR"));
    MapRenderContext context = MapRenderContext.of("user", user);

    // 4. Render with VtlInterpreter
    VtlInterpreter interpreter = new VtlInterpreter();
    StringTemplateOutput output = new StringTemplateOutput();
    interpreter.interpret(ast, source, context, output);

    System.out.println(output);

    // Or stream directly to a Writer without String buffering:
    // interpreter.interpret(ast, source, context, new WriterTemplateOutput(response.getWriter()));
  }
}
```

#### Programmatic In-Memory Testing (0.1.0)

For unit testing without filesystem resources:

```java
SourceText source = SourceText.of("hello.vm", "Hello $name from Viet Template!");
VtlTemplate ast = VtlParser.parse(source).template();

StringTemplateOutput output = new StringTemplateOutput();
new VtlInterpreter().interpret(ast, source, MapRenderContext.of("name", "World"), output);

assert output.toString().equals("Hello World from Viet Template!");
```

### 3. Unified TemplateEngine API (0.2.0 / Milestone M12+)

Starting in `0.2.0` (Milestone M12+), Viet Template provides a high-level `TemplateEngine` facade and pluggable `TemplateRepository` abstraction:

```java
import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.WriterTemplateOutput;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class TemplateEngineExample {

  public record User(String name, boolean admin, List<String> roles) {}

  public static void main(String[] args) throws IOException {
    // 1. Build the engine with a classpath template repository
    TemplateRepository repository = TemplateRepository.classpath("templates/");
    try (TemplateEngine engine = TemplateEngine.builder()
        .repository(repository)
        .build()) {

      // 2. Retrieve the template
      Template template = engine.get("user-card.vm");

      // 3. Populate the render context
      User user = new User("Alice", true, List.of("DEVELOPER", "SECURITY_AUDITOR"));
      RenderContext context = RenderContext.builder()
          .put("user", user)
          .build();

      // 4. Render directly to a String:
      String rendered = template.render(context);
      System.out.println(rendered);

      // Or stream directly to a Writer (e.g. HTTP response writer) without intermediate String buffering:
      // template.render(context, new WriterTemplateOutput(response.getWriter()));
    }
  }
}
```

#### Programmatic In-Memory Testing (0.2.0)

For unit testing without filesystem resources, use `InMemoryTemplateRepository`:

```java
InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
repository.put("hello.vm", "Hello $name from Viet Template!");

try (TemplateEngine engine = TemplateEngine.builder()
    .repository(repository)
    .build()) {

  // Convenient single-call string rendering:
  String output = engine.render("hello.vm", RenderContext.of("name", "World"));
  assert output.equals("Hello World from Viet Template!");
}
```

### 4. Spring Boot Integration (Milestone M16)

Viet Template provides production integration for Spring MVC and Spring Boot via `viet-template-spring-boot-starter`. Canonical baseline: **Spring Framework 7.0.9**, **Spring Boot 4.1.1**, and **Spring Security 7.1.1** on Java 21+ / Jakarta Servlet 6.1.0 (Tomcat 11.0.24) with full virtual thread execution support.

#### Dependencies

##### Apache Maven
```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.1</version>
</dependency>
```

##### Gradle (Kotlin DSL)
```kotlin
implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.1")
```

##### Gradle (Groovy DSL)
```groovy
implementation 'io.github.minh124199:viet-template-spring-boot-starter:0.2.1'
```

#### Application Properties

Configure template resolution and AOT options in `application.properties`:

```properties
# Suffix appended to logical view names
viet-template.suffix=.vtl

# Enforce pure Ahead-Of-Time (AOT) precompiled execution in production
viet-template.runtime-compilation-enabled=false

# Cache resolved View instances
viet-template.cache=true

# View resolver order in the Spring MVC chain
viet-template.order=1
```

#### Template Placement

When using AOT build tooling (`viet-template-maven-plugin` or `viet-template-gradle-plugin`), place templates in `src/main/viet-template/` (e.g. `src/main/viet-template/hello.vtl`):

```velocity
<!DOCTYPE html>
<html>
<head><title>Viet Template</title></head>
<body>
  <h1>Hello, $name!</h1>
  <p>Welcome to $location.</p>
</body>
</html>
```

#### Spring MVC Controller

Return the logical view name corresponding to your template. Spring MVC and `VietTemplateViewResolver` automatically bind the model:

```java
package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HelloController {

  @GetMapping("/hello")
  public String hello(
      @RequestParam(name = "name", defaultValue = "World") String name,
      @RequestParam(name = "location", defaultValue = "Vietnam") String location,
      Model model) {
    model.addAttribute("name", name);
    model.addAttribute("location", location);
    return "hello";
  }
}
```

For complete architecture details, streaming invariants, and configuration properties, see [docs/35-m16-spring-integration.md](docs/35-m16-spring-integration.md).

### 5. Spring Security Integration (Milestone M16.1)

Applications using Spring Security can add the optional `viet-template-spring-security` module alongside `spring-boot-starter-security`.

> [!WARNING]
> ### PRESENTATION-ONLY UI AUTHORIZATION WARNING
> **Template authorization helpers (`$security.hasAuthority(...)`, `$security.authenticated`) control visual presentation only.**
> They conditionally show or hide UI elements in rendered HTML. They **do not** replace server-side access controls (`@PreAuthorize`, `SecurityFilterChain.authorizeHttpRequests`). All sensitive endpoints and mutations must be protected by server-side authorization.

#### Dependency Configuration

##### Apache Maven
```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-security</artifactId>
    <version>0.2.1</version>
</dependency>
```

##### Gradle (Kotlin DSL)
```kotlin
implementation("io.github.minh124199:viet-template-spring-security:0.2.1")
```

#### Template Usage

Templates gain access to `$security` and `$csrf` automatically when auto-configuration is enabled:

```velocity
#if($security.authenticated)
  <p>Welcome back, $security.name!</p>
  #if($security.hasAuthority('ROLE_ADMIN'))
    <a href="/admin">Admin Console</a>
  #end
  <form action="/logout" method="POST">
    <input type="hidden" name="$csrf.parameterName" value="$csrf.token"/>
    <button type="submit">Sign Out</button>
  </form>
#else
  <p>Browsing as guest.</p>
  <a href="/login">Sign In</a>
#end
```

For complete architecture details and security boundary guarantees, see [docs/36-spring-security-integration.md](docs/36-spring-security-integration.md).

### 6. GraalVM Native Image & Spring AOT (Milestone M17 Phase A)

Viet Template supports Ahead-Of-Time (AOT) compilation to standalone GraalVM Native Image binaries out of the box with zero manual reflection JSON configuration for template bytecode or security views.

#### Architecture & Reachability

1. **Build-Time Template AOT**: Templates in `src/main/viet-template/` are precompiled to self-contained Java 21 bytecode classes during build (`mvn process-classes` or `gradle compileVietTemplates`), generating `META-INF/viet-template/templates.idx`.
2. **Spring AOT Runtime Hints**: `VietTemplateRuntimeHints` automatically scans `templates.idx` during Spring AOT processing, registering discovered precompiled template classes and `META-INF/viet-template/*` resources.
3. **Spring Security Runtime Hints**: `VietTemplateSecurityRuntimeHints` registers reflection hints for `$security` (`SecurityView`) and `$csrf` (`CsrfView`) models.
4. **Consumer Model Registration**: Application domain models passed into `Model` attributes (e.g. `Account`, `UserProfile`) must be registered for reflection by consumer applications (e.g. via `@RegisterReflectionForBinding({Account.class, UserProfile.class})` on configuration or controller classes).

#### Native Image Build Commands

##### Apache Maven
```bash
# Compile Ahead-Of-Time and build GraalVM native binary
./mvnw -Pnative native:compile
./target/demo-app
```

##### Gradle
```bash
# Compile Ahead-Of-Time and build GraalVM native binary
./gradlew nativeCompile
./build/native/nativeCompile/demo-app
```

For complete architecture details and verification suites, see [docs/37-m17-graalvm-native-image.md](docs/37-m17-graalvm-native-image.md).

### Output Stream Lifecycle & Resource Ownership

Viet Template provides three concrete `TemplateOutput` targets with explicit lifecycle contracts:

| Output Type | Scope | Concurrency | Close Ownership Semantics |
| :--- | :--- | :--- | :--- |
| **`Utf8OutputStreamTemplateOutput`** | Render-scoped | Single-threaded | Flushes buffer, returns pooled buffer to `Utf8BufferPool`, and **closes the wrapped `OutputStream`**. Calling `close()` is idempotent. Write after close throws `IOException`. |
| **`WriterTemplateOutput`** | Render-scoped | Single-threaded | Not `AutoCloseable`; **caller retains full ownership of the wrapped `Writer`** and is responsible for closing it. |
| **`StringTemplateOutput`** | Render-scoped | Single-threaded | Purely in-memory (`StringBuilder`). Calling `reset()` clears the buffer for sequential reuse within the same thread. |

#### Managed / Servlet Response Streams Pattern
When writing to container-managed output streams (e.g. `HttpServletResponse.getOutputStream()`), where closing the stream prematurely would break container lifecycle or response filters, wrap the stream in a non-closing delegate:

```java
OutputStream nonClosingStream = new FilterOutputStream(response.getOutputStream()) {
    @Override
    public void close() throws IOException {
        flush(); // Flush content but leave response stream open for container management
    }
};

try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(nonClosingStream)) {
    engine.render(request, out);
}
```

---

## Velocity / VTL Compatibility

Viet Template targets compatibility with **Apache Velocity Engine 2.4.1** syntax and evaluation semantics, while rejecting unsafe legacy behaviors.

Compatibility claims are verified by an automated differential Technology Compatibility Kit (`viet-template-tck`) that executes identical test scenarios simultaneously against official Apache Velocity 2.4.1 and Viet Template.

The 295 / 301 (98.01%) exact match rate represents the evaluated differential TCK corpus across 20 functional categories against Apache Velocity 2.4.1, rather than an unqualified blanket claim of universal Velocity compatibility.

### Differential TCK Scorecard (Milestone M4)

| Metric | Scenarios | Percentage | Description |
| :--- | :--- | :--- | :--- |
| **Total Scenarios Evaluated** | **301** | **100.00%** | Total differential scenarios executed |
| **`EXACT_MATCH`** | **295** | **98.01%** | Byte-for-byte output and outcome match |
| **`EXPECTED_DIFFERENCE`** | **5** | **1.66%** | Documented intentional architectural divergences |
| **`VIET_EXTENSION`** | **1** | **0.33%** | Intentional Viet Template extensions |
| **`UNSUPPORTED`** | **0** | **0.00%** | Unsupported features within target scope |
| **`BUG`** | **0** | **0.00%** | Unclassified discrepancies or defects |
| **Accounted Behavior Coverage** | **301** | **100.00%** | Conforming to specification across 20 categories |

### Documented Intentional Differences

Viet Template intentionally diverges from Apache Velocity 2.4.1 in five specific scenarios:

1. **Divide-by-Zero (`arithmetic.divide-by-zero`)**:
   - *Velocity 2.4.1*: Logs a warning, evaluates expression to `null`, and renders the literal syntax (e.g. `$x`).
   - *Viet Template*: Fails fast by throwing `TemplateRenderException` (`DIVISION_BY_ZERO`) to prevent silent computation errors.
2. **`.class` Property Denial (`security.denial.class-property`)**:
   - *Velocity 2.4.1*: Permits reflection via `$object.class`.
   - *Viet Template*: Denies `.class` access under safe member policies to prevent ClassLoader escapes.
3. **`getClass()` Method Denial (`security.denial.get-class-method`)**:
   - *Velocity 2.4.1*: Permits invocation of `$object.getClass()`.
   - *Viet Template*: Blocks `getClass()` in standard execution profiles.
4. **Legacy Null-RHS Preservation for Undefined (`set.null-rhs.legacy-preserved.undefined`)**:
   - *Velocity 2.4.1*: `#set($x = $undefined)` unconditionally overwrites `$x` with `null`.
   - *Viet Template*: Supports an optional compatibility flag preserving Velocity 1.x behavior where prior variable values are retained when the RHS is undefined.
5. **Legacy Null-RHS Preservation for Null Method Returns (`set.null-rhs.legacy-preserved.method-null`)**:
   - *Velocity 2.4.1*: Overwrites `$x` with `null` when `#set($x = $service.returnsNull())`.
   - *Viet Template*: Supports preserving prior values under legacy compatibility mode.

See [docs/06-velocity-2.4.1-compatibility.md](docs/06-velocity-2.4.1-compatibility.md) and [docs/02-vtl-compatibility-spec.md](docs/02-vtl-compatibility-spec.md) for full TCK reports, category breakdowns, and Architecture Decision Records (ADRs).

---

## Viet Template Extensions

Viet Template provides targeted extensions to ease migration and support safe modern patterns:

- **`$foreach.stop()` Loop Termination**: Programmatic loop termination method retained as a convenience alongside standard `#break`.
- **Alternate Value Fallback Syntax**: `${variable|'default value'}` for clean null-coalescing and fallback rendering without verbose `#if` blocks.
- **Configurable Execution Tiers**: Explicit control over whether a template runs via AST interpretation (`AST`), intermediate representation (`IR`), or ahead-of-time bytecode compilation (`AOT_BYTECODE`).
- **Resource Execution Budgets (`RenderBudget`)**: Monotonic safeguards tracking character output length, loop iteration counts, and wall-clock timeouts across nested `#parse`, `#include`, and layout calls.
- **Defense-in-Depth Member Access**: Pluggable `MemberAccessPolicy` implementations with allowlist and deny-list builders.

---

## Repository Modules

The repository is organized into focused modules:

| Module | Published Artifact | Description | Target Consumer |
| :--- | :--- | :--- | :--- |
| **`viet-template-api`** | `viet-template-api` | Stable public contracts: `TemplateEngine`, `Template`, `RenderContext`, `TemplateOutput`, `TemplateRepository`, security policies, and diagnostics. | Library authors, embedding applications, compile-only dependencies. |
| **`viet-template-runtime`** | `viet-template-runtime` | Low-allocation streaming output buffers (`StringTemplateOutput`, `WriterTemplateOutput`, `Utf8OutputStreamTemplateOutput`), contextual escaping (`HTML`, `XML`, `JAVASCRIPT`), `SafeHtml`, and dynamic linker call sites. | Direct streaming consumers and custom escaper developers. |
| **`viet-template-language-vtl`** | `viet-template-language-vtl` | Clean-room VTL lexer, Pratt parser, AST model, semantic analyzer, IR, and 11-pass (13 transformation executions) optimization pipeline. | Compiler tooling, template analyzers, and AST inspectors. |
| **`viet-template-vtl-interpreter`** | `viet-template-vtl-interpreter` | Canonical template engine implementation (`VtlTemplateEngine`), reference AST and IR interpreters, AOT bytecode compiler, narrow public AOT facade (`io.github.minh124199.viettemplate.aot`), compile cache, layout rendering, and global macro manager. | **Normal application developers** (main runtime dependency). |
| **`viet-template-maven-plugin`** | `viet-template-maven-plugin` | Official Apache Maven plugin for build-time AOT template precompilation and class/resource generation (`viet-template:compile`). | Maven build pipelines. |
| **`viet-template-gradle-plugin`** | `viet-template-gradle-plugin` | Official Gradle plugin for build-time AOT template precompilation (`compileVietTemplates`, id `io.github.minh124199.viet-template`). | Gradle build pipelines. |
| **`viet-template-spring`** | `viet-template-spring` | Spring MVC `View` and `ViewResolver` integration, zero-allocation binary streaming, and engine customization SPI. | Spring MVC applications and custom integration authors. |
| **`viet-template-spring-boot-autoconfigure`** | `viet-template-spring-boot-autoconfigure` | Spring Boot 4 auto-configuration and configuration properties (`viet-template.*`). | Spring Boot applications. |
| **`viet-template-spring-boot-starter`** | `viet-template-spring-boot-starter` | Production starter aggregator combining view resolution, auto-configuration, and VTL interpreter. | **Spring Boot web applications** (recommended entrypoint). |
| **`viet-template-tck`** | *(Internal / Not Published)* | Technology Compatibility Kit and differential test suite running side-by-side verification against official Apache Velocity 2.4.1. | Repository contributors and verification tooling. |
| **`viet-template-benchmarks`** | *(Internal / Not Published)* | Dedicated JMH benchmark and profiling module covering workloads B01–B15 across AST, IR, PIC, and AOT tiers. | Performance engineers, CI regression tracking, and repository contributors. |

---

## Requirements

Viet Template targets Java 21 bytecode and requires Java 21 or later at runtime (primary development and performance runtime is Java 25). CI continuously verifies the project on Java 21 and Java 25 across Tier A-E test suites. Repository formatting checks use Google Java Format through Spotless and are run with JDK 25.

### Runtime Requirements

- **Minimum Java Baseline**: Java 21 (`--release 21`). Bytecode target is major version 65 classfiles.
- **Primary Runtime**: Java 25 with full virtual thread optimizations.
- **CI Verification**: Verified on Java 21 and 25 across Linux, macOS, and Windows.
- **Dependencies**: No external runtime dependencies in core production modules.

### Build & Tooling Requirements

- **Build JDK**: JDK 25 recommended (JDK 21 minimum; Gradle 9.7.1 wrapper or Apache Maven 3.9+ via `mvnw`).
- **Code Formatting**: Code formatting is standardized on Google Java Format 1.30.0 via Spotless, executed on JDK 25.
- **Operating Systems**: Continuously tested on Linux, macOS, and Windows.

---

## Performance & Benchmarks

Viet Template is engineered around low-allocation streaming and direct JVM execution:

1. **Pre-Encoded Chunks**: Static template text is pre-encoded to UTF-8 byte arrays during compilation, allowing zero-copy streaming to output streams.
2. **Non-Allocating Numeric Formatting**: Primitive numeric types (`int`, `long`, `double`, `boolean`) are formatted directly into destination buffers without intermediate `String` object creation.
3. **Compiler Optimization Pipeline**: 11 distinct pass implementations (13 transformation executions) eliminate dead branches, fold constants, specialize loop iterators, and hoist static escaping.
4. **Direct Bytecode & Inline Caches**: Known typed properties compile to direct getters; dynamic properties dispatch through monomorphic or small polymorphic inline caches (`AccessLink[]` array scan for depth $\le 4$).

### Benchmark Infrastructure (Milestone M19.1)

Milestone M19.1 establishes the dedicated `viet-template-benchmarks` module implementing canonical JMH benchmarks across 15 critical workloads (B01–B15), including variable lookup, nested scope management, full table rendering, isolated compile cache operations, O(N) full-scan cache invalidation, multi-threaded cache contention, monomorphic/PIC call sites, megamorphic caching, and end-to-end rendering across IR and AOT tiers.

Viet Template enforces a strict 7-part DSA acceptance rule: performance claims must be backed by reproducible, committed JMH benchmark results and JFR allocation profiles rather than aspirational targets. See [docs/15-benchmark-plan.md](docs/15-benchmark-plan.md) for full benchmark methodology, workload definitions, and profiling rules.

### Running Benchmarks

#### Gradle
```bash
# Execute quick smoke benchmark (1 fork, 1 warmup, 1 iteration)
./gradlew :viet-template-benchmarks:jmh -PjmhArgs="-f 1 -wi 1 -i 1 VariableLookupBenchmark"

# Run a specific benchmark suite
./gradlew :viet-template-benchmarks:jmh -PjmhArgs="ForeachRenderingBenchmark"

# Measure memory allocations with GC profiler (-prof gc)
./gradlew :viet-template-benchmarks:jmh -PjmhArgs="-prof gc ForeachRenderingBenchmark"

# Build self-contained executable benchmark JAR
./gradlew :viet-template-benchmarks:benchmarkJar
java -jar viet-template-benchmarks/build/libs/benchmarks.jar -f 1 -wi 1 -i 1 VariableLookupBenchmark
```

#### Apache Maven
```bash
# Package self-contained executable benchmark JAR via Maven Shade plugin (including upstream modules)
./mvnw clean package \
  -pl viet-template-benchmarks \
  -am \
  -DskipTests

# Run benchmarks using shaded JAR
java -jar viet-template-benchmarks/target/benchmarks.jar -f 1 -wi 1 -i 1 VariableLookupBenchmark
```

#### Environment Metadata Recording
```bash
# Capture machine, OS, Git SHA, and JVM runtime metadata
./scripts/record-benchmark-env.sh benchmark-env.json
```

### TCK & Performance Release Gates (Milestone M18)

Milestone M18 is in final qualification. Its conformance and comparative harnesses are independently
executable; clean-room release enforcement and durable SHA-bound comparative evidence must pass before
the milestone is marked complete and frozen.

**Language Conformance TCK** — 80 features, 688 tests, IR/AOT parity verified:
```bash
python3 scripts/verify-tck-coverage.py   # 100% coverage gate
./mvnw test -pl viet-template-tck -am    # conformance suite and required reactor modules
```

**Comparative benchmarks** against Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, and Thymeleaf 3.1.5.RELEASE across 8 workloads (C01–C08):
```bash
./mvnw package -pl viet-template-benchmarks -DskipTests
java -jar viet-template-benchmarks/target/benchmarks.jar ComparativeEngineBenchmark \
  -rf json -rff benchmark-evidence/m18/comparative-$(date +%Y-%m-%d).json
python3 scripts/perf/generate-benchmark-report.py
```

**Master release gate** (all 8 checks):
```bash
./scripts/verify-m18-release-gates.sh
```

See [docs/40-m18-tck-performance-release-gates.md](docs/40-m18-tck-performance-release-gates.md) for full documentation.

---

## Building from Source

Viet Template maintains **first-class dual-build parity** between Gradle (Kotlin DSL) and Apache Maven. Both build systems produce byte-for-byte verified artifacts.

### Gradle (Reference Build)

```bash
# Compile all modules, execute all unit and differential tests, and check formatting
./gradlew clean build

# On Windows:
gradlew.bat clean build

# Check code formatting (runs on JDK 21)
./gradlew spotlessCheck

# Apply code formatting automatically
./gradlew spotlessApply
```

### Apache Maven

```bash
# Build all modules, package JARs, and run test suite
./mvnw clean verify

# On Windows:
mvnw.cmd clean verify

# Check code formatting
./mvnw spotless:check

# Apply code formatting automatically
./mvnw spotless:apply
```

### Build Parity Verification

To verify that module definitions, versions, compiler flags, and JAR contents remain in complete parity across Gradle and Maven:

```bash
./scripts/verify-build-parity.sh
```

---

## Documentation

Comprehensive architecture, design, and specification documents are maintained in the [`docs/`](docs/) directory:

- [Project Charter (`docs/00-project-charter.md`)](docs/00-project-charter.md) — Scope, design goals, non-goals, and product criteria.
- [System Architecture (`docs/01-system-architecture.md`)](docs/01-system-architecture.md) — Module hierarchy and end-to-end compilation flow.
- [Velocity Compatibility TCK (`docs/06-velocity-2.4.1-compatibility.md`)](docs/06-velocity-2.4.1-compatibility.md) — Differential test suite report and compatibility scorecard.
- [VTL Compatibility Specification (`docs/02-vtl-compatibility-spec.md`)](docs/02-vtl-compatibility-spec.md) — Detailed syntax and behavior compatibility contract.
- [Security Model (`docs/10-security-model.md`)](docs/10-security-model.md) — Threat model, capability policies, and sandbox architecture.
- [Security Policy (`SECURITY.md`)](SECURITY.md) — Supported versions, boundaries, and vulnerability reporting.
- [IR Optimization Pipeline (`docs/06-optimization-pipeline.md`)](docs/06-optimization-pipeline.md) — Specification of the IR compiler optimization pipeline (11 distinct pass implementations, 13 transformation executions).
- [Execution Backends (`docs/07-execution-backends.md`)](docs/07-execution-backends.md) — AST, IR, dynamic, and AOT execution tier designs.
- [Output Runtime & Escaping (`docs/08-runtime-output.md`)](docs/08-runtime-output.md) — Streaming architecture and contextual escaping rules.
- [Dynamic Resolution (`docs/09-dynamic-resolution.md`)](docs/09-dynamic-resolution.md) — MethodHandle and polymorphic inline cache (PIC) design.
- [Benchmark Plan (`docs/15-benchmark-plan.md`)](docs/15-benchmark-plan.md) — JMH benchmark methodology and 7-part DSA acceptance rule.
- [Implementation Roadmap (`docs/18-roadmap.md`)](docs/18-roadmap.md) — Release phases from 0.1.x through 1.0.
- [Implementation Checklist (`docs/20-implementation-checklist.md`)](docs/20-implementation-checklist.md) — Detailed engineering task checklist.
- [Architecture Decision Records (`docs/adr/`)](docs/adr/) — Technical context behind major architectural choices.
- [Release 0.1.0 Notes (`docs/releases/0.1.0.md`)](docs/releases/0.1.0.md) — Initial public release notes.
- [Contributing Guide (`CONTRIBUTING.md`)](CONTRIBUTING.md) — Workflow, build parity, and coding standards.
- [Changelog (`CHANGELOG.md`)](CHANGELOG.md) — Chronological record of notable changes.

---

## Contributing

Contributions are welcome! Please review [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull requests.

Key contributor rules:
1. **Clean-room rule**: Do not copy or decompile Apache Velocity source code. Behavior must be established via public documentation and black-box tests.
2. **Dual-build parity**: All code changes must pass `./scripts/verify-build-parity.sh` and build cleanly under both `./gradlew clean build` and `./mvnw clean verify`.
3. **TCK regression protection**: Differential compatibility tests must pass under `viet.tck.mode=STRICT`.

Please also review our [Code of Conduct](CODE_OF_CONDUCT.md) and [Security Policy](SECURITY.md).

---

## Roadmap

Viet Template follows an evidence-driven, benchmark-verified phased roadmap:

- **Phase 0.1.x**: Baseline stabilization, adversarial security fuzzing, and Milestone M19.1 JMH benchmark infrastructure.
- **Phase 0.2.x (Current)**: High-performance runtime architecture with compiler-assigned variable slot execution frames (`EvaluationValue[] slots`) and indexed compilation cache invalidation. Active version is `0.2.1`, finalizing publication of `0.2.1` (M15 AOT build plugins and M16 Spring integration).
- **Phase 0.3.x+**: Subsequent roadmap milestone introducing evidence-driven optimizations guided by profiling (cache contention reduction, zero-copy token slices).
- **Phase 1.0**: GraalVM Native Image verification (M17), independently runnable public TCK and reproducible benchmark report (M18), comprehensive migration guide, stable API freeze, and formal publication.

For complete details on upcoming milestones, see [docs/18-roadmap.md](docs/18-roadmap.md).

---

## License

Viet Template is open-source software licensed under the [Apache License, Version 2.0](LICENSE).

---

## Notice & Trademark Clarification

Apache Velocity and Apache are trademarks of the Apache Software Foundation. This project is an independent clean-room implementation and is not affiliated with, sponsored by, or endorsed by the Apache Software Foundation.
