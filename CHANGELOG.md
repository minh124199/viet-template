# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **First-Class Quarkus Extension Foundation (`viet-template-quarkus`, `viet-template-quarkus-deployment`)**:
  - **Quarkus 3 Integration**: Introduced runtime (`viet-template-quarkus`) and deployment (`viet-template-quarkus-deployment`) modules providing first-class, idiomatic Quarkus 3.39.4+ support alongside Spring Boot.
  - **CDI Injection**: Registered `@ApplicationScoped` beans for `TemplateEngine` and `VietTemplateRenderer` with streaming `OutputStream` support and container-safe lifecycle.
  - **Quarkus Configuration**: Implemented `@ConfigMapping(prefix = "quarkus.viet-template")` for configuring template paths, suffixes, cache sizing, negative cache TTL, runtime compilation, and undefined reference policies.
  - **Build-Time AOT Compilation**: Build steps automatically discover templates, compile them to Java 21 bytecode via `TemplateAotCompiler`, generate `templates.idx`, and register `GeneratedClassBuildItem` and `GeneratedResourceBuildItem`.
  - **GraalVM Native Image Support**: Emits `ReflectiveClassBuildItem` and `NativeImageResourceBuildItem` for all compiled templates and index metadata, enabling out-of-the-box native compilation without dynamic reflection. Empirically verified on Mandrel 25.0.4.1-Final on Linux x86_64, producing ~52 MB native executables with < 20ms startup. Enforces pure-AOT invariant: dynamic `#parse` and `#evaluate` directives fail fast at build time (`VTLAOT:1101`, `VTLAOT:1102`).
  - **Dev-Mode Hot Reload**: Registers `HotDeploymentWatchedFileBuildItem` for template directories and files, enabling immediate live reload during `quarkus dev`.
  - **Quarkus Security Integration**: Provides presentation-safe `$security` facade via `QuarkusSecurityRenderContextContributor` exposing `authenticated`, `anonymous`, `name`, and `hasRole()` using runtime-safe reflection and Arc bean lookup without hard linkage requirements. Clarified CSRF model: Spring MVC provides automatic `$csrf`; Quarkus requires manual model contribution when CSRF protection is active.
  - **Qute Coexistence**: Verified zero-conflict side-by-side execution with Quarkus Qute within the same application.
  - **Dual-Build Consumer Integration**: Created consumer fixtures for Apache Maven (`integration-tests/quarkus/maven-quarkus-aot`) and Gradle Kotlin DSL (`integration-tests/quarkus/gradle-quarkus-aot`), verified by `scripts/verify-quarkus-integration.sh`. Standard JVM execution qualified on Ubuntu, macOS, and Windows.
- **Canonical Framework-Neutral Multiple-Suffix Model (`viet-template-api`)**:
  - Introduced `TemplateSuffixConfiguration` with fail-closed path traversal validation, deduplication, deterministic order preservation, and candidate resolution.
  - Refactored `VietTemplateViewResolver` and `VietTemplateProperties` to delegate to `TemplateSuffixConfiguration` while preserving 100% backward compatibility.
- **Configurable Undefined Reference Policy (`viet-template-api`)**:
  - Added `UndefinedReferencePolicy` (`SILENT`, `WARN`, `ERROR`) governing variable evaluation behavior.
  - Integrated into `VtlInterpreterOptions`, `BackendOptions`, `VtlInterpreter`, `IrInterpreter`, and `BytecodeRuntimeBridge`.
- **Framework-Neutral Non-Closing Output Stream (`viet-template-runtime`)**:
  - Extracted `NonClosingOutputStream` into `io.github.minh124199.viettemplate.runtime.stream` for container-safe streaming across Spring MVC, Quarkus REST, and generic servlet containers.
  - Preserved backward compatibility for Spring with `io.github.minh124199.viettemplate.spring.web.servlet.NonClosingOutputStream` subclassing the runtime type.
- **First-Class Multiple-Suffix View Resolution & Gradual Velocity Migration**:
  - **Ordered Multi-Suffix Resolution (`VietTemplateViewResolver`)**: Added support for ordered suffix candidate evaluation via `suffixes` (`getSuffixes()`, `setSuffixes(List<String>)`). Logical view names probe candidate suffixes deterministically in configured order, returning `null` on miss to preserve Spring MVC `ViewResolver` chaining.
  - **Single-Suffix Backward Compatibility**: Retained existing `suffix` (`getSuffix()`, `setSuffix(String)`) and `viet-template.suffix` property. When `suffixes` is empty or unconfigured, the resolver seamlessly falls back to the legacy single suffix.
  - **Extension Matching & Fallback Semantics**: If a view name already ends with one of the configured suffixes, the exact candidate is probed first without redundant suffix concatenation. When location checking is disabled (`checkTemplateLocation=false`), the primary candidate is returned deterministically without probing.
  - **Security & Path Traversal Hardening**: Enforced strict suffix validation (`validateSuffix`), rejecting path separators (`/`, `\`), path traversal (`..`), URI schemes (`:`), null bytes, and encoded traversal sequences.
  - **Cache Correctness & Thread Safety**: Mutating resolver configuration (`setPrefix`, `setSuffix`, `setSuffixes`, `setContentType`, `setCharset`, `setCheckTemplateLocation`, `setEngine`) automatically invalidates stale cached views. Suffix state is published safely across threads with defensive unmodifiable copying (`List.copyOf`).
  - **Spring Boot Property Binding**: Added `viet-template.suffixes` in `VietTemplateProperties` supporting YAML list, indexed properties, and comma-delimited binding, wired automatically via `VietTemplateAutoConfiguration`.
- **1.0 Adoption Readiness, Migration, & Documentation Suite**:
  - **Authoritative User Documentation Suite**: Added comprehensive guides organized in thematic directories: Quickstart (`docs/getting-started/quickstart.md`), Support Matrix (`docs/getting-started/support-matrix.md`), VTL Syntax Reference (`docs/language/syntax-reference.md`), 3-State Undefined/Null Evaluation (`docs/language/undefined-null-semantics.md`), Foreach & Scopes (`docs/language/foreach-and-scopes.md`), Macros & Layouts (`docs/language/macros-and-layouts.md`), Typed Models (`docs/language/typed-models.md`), Build Tooling for Maven & Gradle (`docs/build-tooling/`), Spring Boot Integration (`docs/spring/spring-boot-integration.md`), Secure Templates (`docs/security/secure-templates.md`), Production & AOT Deployment (`docs/deployment/production-aot.md`), GraalVM Native Image (`docs/native-image/graalvm-native-image.md`), Extension Guide & SPIs (`docs/extensions/extension-guide.md`), and Comparative Performance Benchmarks (`docs/performance/comparative-benchmarks.md`).
  - **Apache Velocity 2.4.1 Migration Guide & Differences Catalog**: Published step-by-step transition roadmap (`docs/migration/velocity-migration-guide.md`, `docs/migration/velocity-differences.md`) backed by executable test verification in `VelocityMigrationPatternsTest.java`.
  - **Machine-Verified Compatibility Matrix**: Created `scripts/generate-compatibility-matrix.py` generating `docs/migration/compatibility-matrix.md` directly from `config/tck/vtl-feature-matrix.json` (80 features, 20 categories, 76 exact matches, 3 differences, 1 extension, 100% TCK coverage).
  - **Comprehensive Diagnostics & Error Code Catalog**: Authored `docs/diagnostics/error-catalog.md` detailing all parse-time, compile-time, semantic, security, limit, and runtime diagnostic codes with concrete examples, likely causes, and actionable remedies.
  - **Comparative Performance Evidence Qualification**: Documented M18 C01–C08 comparative benchmarks across Java 21 and Java 25 against Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, and Thymeleaf 3.1.5, confirming all four 1.0 performance success gates.
  - **1.0 Readiness Gap Analysis & Surface Audit**: Produced `docs/1.0-readiness-gap-analysis.md` assessing all 15 architectural areas, cataloging the 105 stable types (85 `STABLE_API`, 20 `STABLE_SPI`) vs 259 accidental public types across 5 baselines (`1.0-core-public-api.txt`, `1.0-aot-public-api.txt`, `1.0-spring-public-api.txt`, `1.0-spring-security-public-api.txt`, `1.0-quarkus-public-api.txt`), and outlining the encapsulation roadmap for 0.3.0 (`config/api-baseline/accidental-public-types-inventory.json`).
  - **Automated Documentation Verification Infrastructure**: Implemented `scripts/verify-documentation.py` and unit test suite `scripts/tests/test_verify_documentation.py` (30 tests) verifying release date integrity, consumer version references, coordinates, baseline compiler flags, Spring properties, internal link resolution, public types, diagnostic codes, and compatibility matrix synchronization. Integrated into `.github/workflows/ci.yml`.
  - **Standalone Executable Plain Java Fixture**: Added standalone project `examples/plain-java/` with verified build and test suite demonstrating direct programmatic engine usage.

### Changed
- **Release Date Corrections**: Aligned release date for `v0.2.2` to `2026-09-20` (matching official Maven Central and GitHub Releases timestamps) across `README.md`, `CHANGELOG.md`, `docs/18-roadmap.md`, and `docs/20-implementation-checklist.md`.
- **Restructured README**: Overhauled `README.md` to be user-oriented and adoption-focused, featuring a 5-minute quickstart, Velocity migration summary, performance highlights, and a structured documentation sitemap.

## [0.2.2] - 2026-09-20

### Added
- **Steady-State Execution Preparation & DSA Specialization (Milestone M19.3c)**:
  - **Faster Warmed AOT Lookup (M19.3c.1)**: Precompiled generated template instances are constructed once per engine generation and reused concurrently, eliminating repetitive reflection, wrapper overhead, and key reconstruction during warmed lookups while preserving engine isolation and classloader collectability.
  - **Prepared IR Execution (M19.3c.2)**: Structural IR preparation (final optimization, invariant verification, root layout, and function dispatch maps) executes once per compilation generation. Warmed IR renders execute directly through immutable prepared metadata with request-only mutable state, completely eliminating render-time optimizer, verifier, and layout overhead.
  - **Lower Loop Materialization and Allocation (M19.3c.3)**: Loop execution consumes compiler-assigned `LoopPlan`s (`ARRAY`, `RANGE`, `ITERATOR`, `ITERABLE`, `DYNAMIC`), executing array iterations via constant-state reflective traversal without temporary `ArrayList` copying, streaming ranges without boxed `Integer` list materialization, and streaming caller iterators directly with immediate `#break` termination.
  - **Reduced Request-Scope Churn (M19.3c.4)**: Eliminated temporary `HashMap` allocations and duplicate slot/scope writes during foreach and macro invocations. Introduced single-probe template-variable lookups while preserving Velocity-compatible defined-null vs. undefined 3-state semantics and public scope copy guarantees.
  - **Compiler-Elided Unused Foreach Metadata (M19.3c.5)**: Implemented conservative compile-time analysis (`ForeachMetadataObservability`) to omit metadata object construction, wrapper allocation, slot writes, and scope synchronization when `$foreach` is provably unobservable, while strictly retaining immutable snapshot semantics when observed or when dynamic hazards (`#evaluate`, `#parse`, macros) are present.
  - **Java 21/25 Runtime Policy Clarification**: Established Java 21 as the minimum supported compile and runtime baseline (`--release 21`), designating Java 25 as the primary development, CI, profiling, and performance runtime.
  - **Virtual-Thread-Safe Caller Execution Model**: Hardened synchronous, caller-thread-bound rendering invariants. Proved zero carrier pinning under high concurrency and strict request isolation without internal executors, thread pools, or ThreadLocal state.
- **GraalVM Native Image & Spring AOT Compatibility (Milestone M17 Phase A)**:
  - Spring AOT Runtime Hints (`viet-template-spring-boot-autoconfigure`):
    - Introduced `VietTemplateRuntimeHints` implementing `RuntimeHintsRegistrar` and registered in `META-INF/spring/aot.factories` and `@ImportRuntimeHints` on `VietTemplateAutoConfiguration`.
    - Automated precompiled template discovery: dynamically parses `META-INF/viet-template/templates.idx` at Spring AOT compilation time and registers all discovered precompiled template classes for reflection (`INVOKE_DECLARED_CONSTRUCTORS`, `INVOKE_PUBLIC_METHODS`).
    - Resource pattern registration: registers `META-INF/viet-template/templates.idx` and `META-INF/viet-template/*` in native reachability resource metadata.
    - Core properties reflection: registers `VietTemplateProperties`, `VietTemplateProperties.Security`, and `VtlTemplateEngineProvider` for reflection.
  - Spring Security AOT Runtime Hints (`viet-template-spring-security`):
    - Introduced `VietTemplateSecurityRuntimeHints` implementing `RuntimeHintsRegistrar` and registered in `META-INF/spring/aot.factories` and `@ImportRuntimeHints` on `VietTemplateSecurityAutoConfiguration`.
    - Registered reflection hints for public interfaces (`SecurityView`, `CsrfView`) and package-private implementations (`DefaultSecurityView`, `DefaultCsrfView`) via `TypeReference` without breaking package encapsulation.
  - Native Image Integration Fixtures & Parity Testing:
    - Added dedicated black-box native image consumer fixtures for Maven (`integration-tests/native/maven-boot3-native`, `integration-tests/native/maven-boot4-native`) and Gradle (`integration-tests/native/gradle-boot3-native`, `integration-tests/native/gradle-boot4-native`).
    - Implemented automated native image parity and live execution suite `scripts/verify-native-image-integration.sh` compiling native executables, verifying bytecode release 17 (major version 61), starting native executables on dynamic ports, and asserting live HTTP 200 responses, CSRF token handling, and security authorization invariants under runtime compilation rejection (`viet-template.runtime-compilation-enabled=false`).
    - Added dedicated GitHub Actions workflow `.github/workflows/native-image.yml` with GraalVM JDK 21 and pinned action SHAs.
- **Spring Boot DevTools Restart & ClassLoader Lifecycle Hardening (Milestone M17 Phase B)**:
  - Hardened Engine Lifecycle & Watcher Termination (`viet-template-vtl-interpreter`):
    - Added `close()` lifecycle method to `VtlTemplateEngine` and `DevelopmentFileWatcher` ensuring clean shutdown of watch service thread and debounced executor thread pool upon Spring `ApplicationContext` closure (`destroyMethod = "close"`).
    - Hardened idempotency of `close()`, negative and positive compilation cache clearing, and classloader reference releases.
    - Verified `BoundedWeakClassCache` and `DynamicCallSite` turnover safety under `URLClassLoader` replacement with zero class retention.
  - DevTools Containment & Classpath Isolation (`viet-template-spring-boot-autoconfigure`):
    - Added `DevToolsContainmentTest` asserting exact 0 references to `spring-boot-devtools` classes across all 8 production modules and absence of `RestartClassLoader` from runtime production classpath.
  - Dual-Build DevTools Test Fixtures (`integration-tests/devtools/`):
    - Added 4 dual-build consumer fixtures: `maven-boot3-devtools`, `gradle-boot3-devtools`, `maven-boot4-devtools`, and `gradle-boot4-devtools`.
    - Fully isolated compile-time classpath from DevTools classes using reflection for test inspection endpoints (`/__test/restart-generation`, `/__test/classloader-leak-check`).
  - Automated Parity & Lifecycle Verification (`scripts/verify-devtools-restart-integration.sh`, `.github/workflows/devtools-restart.yml`):
    - Verified Mode A (Dynamic hot reload without restart), Mode B (AOT recompile + trigger-file restart with `RestartClassLoader` turnover), stale template deletion, Spring Security & CSRF isolation, and 10x restart stress test with zero ClassLoader leaks (`leakFree = true`).
    - Formally documented in [`docs/38-m17-devtools-restart-hardening.md`](docs/38-m17-devtools-restart-hardening.md).
- **Spring Framework 7 & Spring Boot 4 Canonical Modernization**:
  - Established Spring Framework 7 and Spring Boot 4 as the canonical integration baseline on Java 21+, verified against Jakarta Servlet 6.1.0 and Tomcat 11.0.24.
  - Added multi-generation compatibility fixtures for Spring MVC, Spring Boot, and Spring Security under both Boot 3 / Security 6 and Boot 4 / Security 7.
  - Verified non-blocking, pin-free virtual-thread execution under concurrent HTTP server load in `Spring7VirtualThreadServerTest` and `Spring7SecurityVirtualThreadServerTest`.
- **Technology Compatibility Kit (TCK) & Performance Release Gates (Milestone M18)**:
  - **Language Feature Claim Matrix**: Established machine-verifiable feature claim matrix (`config/tck/vtl-feature-matrix.json`) defining 80 language features across 20 categories, verified with complete coverage (80/80 features) via `scripts/verify-tck-coverage.py`.
  - **TCK Conformance Suite & Backend Parity**: Implemented executable conformance test suite in `viet-template-tck` (`TckSuiteRegistry`, `TckConformanceTest`) and automated backend execution parity tests (`BackendParityTest`) verifying identical behavior between IR Interpreter and AOT Bytecode backends.
  - **Independent Consumer Fixtures**: Added isolated external consumer test fixtures for Maven (`integration-tests/tck-consumer/maven`) and Gradle (`integration-tests/tck-consumer/gradle`) consuming published artifacts via `scripts/verify-tck-consumer.sh`.
  - **Multi-Engine Comparative Benchmark Harness**: Built comparative benchmark harness in `viet-template-benchmarks` evaluating 8 canonical workloads against Velocity 2.4.1, Thymeleaf 3.1.5, JTE 3.2.4, and Quarkus Qute 3.39.4 across Java 21 and Java 25.
  - **Formal Evidence Contract & Master Release Gates**: Implemented durable, SHA-bound evidence generation and verification (`benchmark-evidence/m18/`, `scripts/perf/build-m18-evidence-package.py`, `scripts/perf/verify-m18-evidence.py`), and unified master qualification gate `scripts/verify-m18-release-gates.sh` integrated as mandatory blocking dependency in release CI workflow.

### Fixed
- **BoundedWeakClassCache & ClassLoader Turnover**:
  - Fixed observable cache size calculation in `BoundedWeakClassCache.size()` by actively removing entries whose weak references have been cleared by GC (`wk.get() == null`) in addition to draining the reference queue.
  - Prioritized dead entry removal in `evictOne()` prior to evicting live entries when capacity is reached.
  - Hardened ClassLoader turnover test assertion in `VtlTemplateEngineLifecycleTest` to poll until weak references and cache size reach zero without pinning.
  - Added targeted unit test coverage in `BoundedWeakClassCacheTest`.
- **Virtual Thread Carrier Pinning & Dynamic Linker Reuse**:
  - Resolved carrier thread pinning under high concurrency by reusing `CallSiteRegistry` and `VtlInterpreter` across renders in `VtlTemplateEngine` and `VtlTemplate`, passing shared `ReferenceAccess` to `IrInterpreter`.
  - Ensured steady-state rendering reuses inline-cached dynamic call sites with zero incremental links (`links()` invariance).
  - Ensured long-lived engine instances do not retain transient application ClassLoaders or classes by converting call site links to `WeakReference<AccessLink>` and caching dynamic linkages in JVM `ClassValue<ClassLinkTable>` on receiver classes.
  - Hardened concurrency safety: audited `VtlInterpreter` and `IrInterpreter` for zero mutable execution state, verified complete execution isolation across 10,000 concurrent virtual threads in `SharedInterpreterConcurrencyTest`.
  - Enforced engine and security policy isolation via `policyId` partitioning in `CallSiteRegistry` and `DynamicLinker`, verified in `EngineAndPolicyIsolationTest`.
  - Hardened JFR pinning audits in `SharedEngineVirtualThreadStressTest` with narrow classification and detailed diagnostic logging for ultra-short JVM-internal `MethodType` maintenance events.
  - Added regression test suite `VtlDynamicLinkerReuseTest` verifying call site reuse and registry clearing on close.
  - Documented architectural invariants and JFR classification boundaries in `docs/adr/0013-virtual-thread-invariants.md`.

## [0.2.1] - 2026-09-17

### Added
- **Maven & Gradle Ahead-Of-Time (AOT) Tooling (Milestone M15)**:
  - Narrow public AOT facade: Introduced `io.github.minh124199.viettemplate.aot` with 5 public types (`TemplateAotCompiler`, `TemplateAotRequest`, `TemplateAotResult`, `TemplateAotDiagnostic`, `TemplateAotArtifact`), fully classified as `STABLE_API` with zero signature leaks.
  - Bytecode self-containment: `BytecodeTemplateCompiler` emits JVM `<clinit>` bytecode allocating and populating `UTF8_CHUNKS`, `SITES`, and `TEMPLATE_ID`, making generated `.class` files 100% self-contained and runnable in standalone ClassLoaders without reflection.
  - Runtime AOT discovery: `VtlTemplateEngine` automatically discovers `META-INF/viet-template/templates.idx` from ClassLoaders at startup, executing precompiled templates with zero runtime compilation (`rejectRuntimeCompilation(true)`).
  - Official Maven plugin: Introduced `viet-template-maven-plugin` (`VietTemplateCompileMojo`, goal `compile`, phase `process-classes`) supporting incremental compilation, glob scanning, line:column diagnostics, and stale file cleanup.
  - Official Gradle plugin: Introduced `viet-template-gradle-plugin` (`VietTemplatePlugin`, id `io.github.minh124199.viet-template`) with `@CacheableTask` `VietTemplateCompileTask`, lazy Gradle property wiring, and Configuration Cache compatibility.
  - Deterministic dual-build parity: Verified 100% byte-for-byte identical bytecode and index output across Maven and Gradle builds via `scripts/verify-aot-tooling-parity.sh` and black-box consumer fixtures (`integration-tests/aot/*`).
  - Architectural boundary enforcement: Added ArchUnit rule `aot_public_api_must_not_depend_on_internal_packages` and verified `integration_and_tooling_boundary_must_not_access_internal_packages`.
- **Spring Framework & Spring Boot Integration (Milestone M16)**:
  - Spring MVC View & ViewResolver (`viet-template-spring`):
    - Introduced `VietTemplateView`: thread-safe, immutable, and request-stateless Spring MVC `View` streaming pre-encoded UTF-8 byte chunks directly to the servlet output stream via `Utf8OutputStreamTemplateOutput`.
    - Non-closing servlet stream ownership: implemented `NonClosingOutputStream` delegating flush on `close()` while preserving servlet response stream lifecycle for container and filter management.
    - Introduced `VietTemplateViewResolver`: caching Spring MVC `ViewResolver` with path traversal defense (`validateViewName`), configurable prefix/suffix, view caching (`ConcurrentHashMap`), and seamless fallback to AOT index discovery (`META-INF/viet-template/templates.idx`) when source files are absent.
    - Introduced `VietTemplateEngineCustomizer`: `@FunctionalInterface` callback allowing ordered customization of `TemplateEngine.Builder` prior to engine initialization.
  - Spring Boot 3 auto-configuration (`viet-template-spring-boot-autoconfigure`):
    - Introduced `VietTemplateAutoConfiguration`: Spring Boot auto-configuration registering `TemplateEngine` (with `destroyMethod = "close"`) and `VietTemplateViewResolver`, backing off cleanly when custom beans are declared.
    - Comprehensive configuration properties: introduced `VietTemplateProperties` mapped under prefix `viet-template.*` supporting prefix, suffix, content type, charset, cache, location check, runtime compilation rejection, negative cache TTL, and hot reload settings.
    - Template location validation with AOT detection: verifies presence of `META-INF/viet-template/templates.idx` before falling back to source classpath checks.
  - Production starter (`viet-template-spring-boot-starter`):
    - Aggregator starter POM/JAR pulling in `viet-template-spring`, `viet-template-spring-boot-autoconfigure`, and `viet-template-vtl-interpreter`.
  - Dual-build AOT fixtures & live server verification:
    - Established black-box consumer test fixtures for Maven (`integration-tests/spring/maven-mvc-aot`) and Gradle (`integration-tests/spring/gradle-mvc-aot`) testing MockMvc and real embedded HTTP server flows.
    - Automated parity verification via `scripts/verify-spring-integration-parity.sh` proving 100% byte-for-byte bytecode and index parity, absence of `.vtl` source files in runtime JARs, and successful HTTP 200 execution in pure AOT mode (`viet-template.runtime-compilation-enabled=false`).
  - Architectural boundary enforcement:
    - Added ArchUnit rule `spring_integration_must_not_access_internal_packages` ensuring `viet-template-spring` depends exclusively on public engine APIs and does not leak into internal compiler or interpreter packages.
- **Spring Security Integration (Milestone M16.1)**:
  - Dedicated module `viet-template-spring-security`: Provides seamless, thread-safe, and zero-leak Spring Security and CSRF context variables (`$security` and `$csrf`) in templates.
  - Facade contracts (`SecurityView`, `CsrfView`): Narrow, read-only interfaces exposing essential security information (username, roles, authentication status, permissions, CSRF parameter and header names, token values) without exposing mutable or internal framework objects.
  - Auto-configuration (`VietTemplateSecurityAutoConfiguration`): Automatically registers `SpringSecurityRenderContextContributor` as a `RenderContextContributor` bean, wiring request-bound security facades into every render request.
  - Extensibility SPI (`SecurityViewFactory`, `CsrfViewFactory`): Pluggable factories allowing applications to provide custom security and CSRF view implementations.
- **Spring Security 6 and 7 / Boot 3 and 4 Multi-Generation Validation**:
  - Validated compatibility across Spring Boot 3.3.x / Spring Security 6.3.x (baseline) and forward compatibility with Spring Boot 4.0.0-M1 / Spring Security 7.0.0-M1.
  - Verified via dedicated black-box integration tests in Maven (`integration-tests/spring/maven-security-aot`, `integration-tests/spring/maven-security7-aot`) and Gradle (`integration-tests/spring/gradle-security-aot`, `integration-tests/spring/gradle-security7-aot`) covering both MockMvc and embedded real HTTP servers.
  - Automated dual-version parity verification via `scripts/verify-spring-security-parity.sh` and `scripts/verify-spring-security7-integration.sh`.
- **Public API & SPI Stabilization (Milestone M14)**:
  - Lifecycle management: `TemplateEngine` implements `AutoCloseable` with `default void close() {}`, providing graceful resource cleanup in try-with-resources blocks.
  - Engine invalidation conveniences: Added `TemplateEngine.invalidate(TemplateId)` and `TemplateEngine.invalidateAll()`.
  - Convenience rendering methods: Added `TemplateEngine.render(String/TemplateId, RenderContext)` and `Template.render(RenderContext)` returning rendered strings directly.
  - Architecture isolation: Added ArchUnit rule `api_must_not_depend_on_runtime_or_interpreter` to strictly enforce `viet-template-api` modular independence.
  - Dedicated contract test suites: Added `PublicApiContractTest`, `TemplateOutputSpiContractTest`, `TemplateEngineContractTest`, `TemplateRepositorySpiContractTest`, `SecuritySpiContractTest`, and `ApiConsumerSmokeTest`.
- **Public Surface Containment & Output Lifecycle (Milestone M14.1)**:
  - Safe visibility reductions: Lowered visibility on 4 unneeded public internal classes/members (`CallSiteRegistry.CallSiteKey` to private; `AccessLink.Status`, `AccessLink.status()`, `VtlAstDumper`, and `VtlSemanticDiagnosticCodes` to package-private).
  - Public surface classification baseline: Established `config/api-baseline/public-surface-classification.txt` classifying all 360 public types across production modules into 80 `STABLE_API`, 19 `STABLE_SPI`, 6 `EXPERIMENTAL`, and 255 `PUBLIC_BUT_INTERNAL_ACCIDENT` (0 unclassified types).
  - Output stream lifecycle & resource ownership contracts: Added comprehensive class and method-level Javadoc to `Utf8OutputStreamTemplateOutput`, `WriterTemplateOutput`, `StringTemplateOutput`, and `TemplateEngine` detailing thread confinement, stream ownership on close, double-close idempotency, flush semantics, and the container-managed non-closing stream pattern.
  - Dedicated lifecycle contract test suite: Added `ConcreteOutputLifecycleContractTest` in `viet-template-runtime` covering 9 comprehensive streaming lifecycle scenarios.
  - Integration boundary ArchUnit rule: Added `integration_and_tooling_boundary_must_not_access_internal_packages` in `viet-template-tck`.
- **API Compatibility & Baseline Protection**:
  - Layered baseline definitions: Four modular baselines (`1.0-core-public-api.txt`, `1.0-aot-public-api.txt`, `1.0-spring-public-api.txt`, `1.0-spring-security-public-api.txt`) protecting all 99 stable types (80 `STABLE_API` + 19 `STABLE_SPI`).
  - Automated CI surface & leak verification: Added `scripts/generate-public-surface.py`, `scripts/verify-public-surface-classification.py`, and `scripts/verify-api-compatibility.py` integrated into CI to enforce zero unclassified types, zero stale types, baseline parity, binary compatibility, and zero internal/experimental signature leaks.

### Fixed
- **JVM StackMapTable & AOT Verifier Correctness**:
  - Resolved `java.lang.VerifyError: Expecting a stackmap frame at branch target` in generated bytecode for complex branching templates.
  - Corrected frame calculation in `BytecodeTemplateCompiler` to emit compliant `StackMapTable` attributes across forward conditional jumps, loop back-edges, and nested control flow, passing strict JVM bytecode verification without `-noverify`.
  - Added dedicated regression and fuzzing coverage in `AotStackMapRegressionTest` and `AstIrAotDifferentialFuzzTest`.
- **AOT Logical Short-Circuit Semantics (`&&` and `||`)**:
  - Corrected short-circuit evaluation in `BytecodeTemplateCompiler` for binary logical expressions: right-hand operands are now strictly skipped when the left-hand operand satisfies short-circuit criteria (`false` for `&&`, `true` for `||`).
  - Preserved identical evaluation semantics and side-effect guarantees between IR interpreter and AOT compiled bytecode.
- **Maven Central Publication Metadata and SCM URLs**:
  - Project & SCM inheritance controls: Configured `child.project.url.inherit.append.path="false"` on root `<project>` and `child.scm.*.inherit.append.path="false"` on root `<scm>`, eliminating defective Maven path auto-appending across child module project and SCM URLs.
  - HTTPS SCM read-only connection: Replaced obsolete `scm:git:git://` protocol with secure HTTPS read-only connection `scm:git:https://github.com/minh124199/viet-template.git`.
  - Corrected Git developer connection: Updated developer connection to standard `scm:git:ssh://git@github.com/minh124199/viet-template.git` across Maven and Gradle builds.
  - Explicit child module homepage URLs: Added canonical `<url>${github.repository.url}/tree/main/<module></url>` across published production modules, ensuring accurate GitHub source tree deep-links.
  - Non-published module protection: Verified that internal verification modules (`viet-template-tck`, `viet-template-benchmarks`) strictly omit `<url>` and enforce multi-tier publication skipping (`maven.deploy.skip`, `skipPublishing`, `central.publishing.skip`).
- **Contract & Null Semantics Hardening**:
  - `RenderContext.of`, `RenderContext.Builder`, and `MapBackedRenderContext` now preserve null variable values (`DEFINED_NULL`) without throwing `NullPointerException` or collapsing into `UNDEFINED`.
  - `DefaultMutableRenderContext` preserves `null` values via internal sentinel instead of removing the variable from the context.
  - `MemberAccessPolicy` no longer extends `java.io.Serializable`.
  - Fail-closed security enforced in `MemberAccessPolicyVtlAdapter` and `MemberAccessPolicyLinkerAdapter` against dangerous types (`Class`, `ClassLoader`, `Runtime`, `ProcessBuilder`, `Thread`, reflection).
  - Documented thread-confinement of `TemplateOutput` and thread-safety of `TemplateEngine`, `Template`, `RenderContext`, `Escaper`, and `MemberAccessPolicy`.

### Security
- **Spring Security Facade Isolation**:
  - Templates only receive immutable, unmodifiable `SecurityView` and `CsrfView` facade instances; raw Spring Security framework objects (`SecurityContext`, `Authentication`, `CsrfToken`, `HttpServletRequest`) are never exposed into the rendering context.
  - Cross-session authentication and CSRF token isolation verified under heavy concurrent virtual-thread load in `SpringSecurityCrossSessionCsrfAndAuthIsolationTest` and `SpringSecurityConcurrencyStressTest`.
- **CSRF Facade Token Masking in `toString()`**:
  - Implemented secure `toString()` on `DefaultCsrfView` masking sensitive CSRF token values (`CsrfView[parameterName=..., headerName=..., token=***]`), preventing accidental token leakage into logs, diagnostic dumps, or error reports.
- **`VTL_SAFE` Sandbox Compatibility**:
  - All public methods and properties on `SecurityView` and `CsrfView` are compatible with `MemberAccessPolicy.SANDBOX` and `VTL_SAFE` execution profiles, allowing safe interrogation of authentication and CSRF attributes in restricted environments.
- **Security Boundary Enforcement**:
  - Added ArchUnit rules `spring_security_must_not_access_internal_packages` and `spring_integration_must_not_access_internal_packages` enforcing that Spring integrations interact strictly through public engine APIs.
  - Validated that internal sensitive types (`ApplicationContext`, `BeanFactory`, `SecurityContextHolder`) remain completely inaccessible to template code.

### Build / Release
- **Resumable Central Publication Workflow**:
  - Hardened release automation in `.github/workflows/release.yml` with decoupled staging, deployment, and verification stages supporting idempotent re-runs, staging repository reuse, and safe failure recovery.
- **Immutable-Coordinate Guard**:
  - Integrated `scripts/verify-central-release.py` and pre-flight publication guards preventing accidental coordinate overwrite or re-publication of existing released versions to Maven Central.
- **Public Artifact Verification**:
  - Comprehensive verification suite in `scripts/validate-release-bundle.py` verifying parent POM and all 10 published modules for main, sources, and Javadoc JARs, bytecode clean-room hygiene (zero test/TCK/Velocity classes, zero credentials/secrets), and valid package hierarchies.
- **Maven/Gradle Dual-Build Parity**:
  - Automated parity verification via `scripts/verify-build-parity.py` ensuring Maven and Gradle builds produce matching artifact coordinates, dependencies, compiler configurations, publication metadata, and AOT bytecode output.

## [0.2.0] - 2026-09-12

### Added
- **High-Performance Runtime Architecture (Milestone M19.2)**:
  - **Compiler-Assigned Variable Slots (`O45 AssignVariableSlots`)**: Introduced optimizer pass `O45` assigning stable, static integer slot IDs to template variables (parameters, `#set` targets, loop counters, macro arguments) without slot reuse, replacing string-based `HashMap` lookups with direct array indexing.
  - **Flat Array-Backed `ExecutionFrame`**: Runtime activation frame backed by `EvaluationValue[] slots` across both the reference IR interpreter and Ahead-Of-Time (AOT) bytecode backend. Explicitly initializes each slot to `EvaluationValue.undefined()`, guaranteeing strict preservation of Velocity 3-state evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`) without exposing Java reference array `null`s.
  - **AOT Bytecode Slot Emission**: Bytecode compiler emits direct JVM array-slot bytecode instructions (`ALOAD`, `ASTORE`, `AALOAD`, `AASTORE`) for variable reads and writes, drastically reducing variable resolution overhead and GC allocation.
  - **Dynamic Fallback Coherence**: Seamless dynamic fallback ensuring that dynamic features (`#evaluate`, unresolvable dynamic references, reflective tool contexts) maintain 100% coherence and bidirectional synchronization between `ExecutionFrame` slots and lexical `ExecutionContext` scopes.
  - **Indexed Template Compile-Cache Invalidation (Milestone M19.2a)**: Introduced a concurrent secondary reverse index (`ConcurrentMap<TemplateId, Set<CompileCacheKey>>`) mapping each template ID to its active compile cache keys. Invalidation reduces from an $O(N)$ linear cache scan to average $O(1)$ index lookup followed by $O(K)$ removal of the $K$ associated entries. Put and invalidate operations are serialized via 64 template lock stripes, preventing race conditions and ensuring linearizable cache mutations without lock convoying.
- **Benchmark & Profiling Infrastructure (Milestone M19.1)**:
  - Dedicated `viet-template-benchmarks` module with dual Gradle Kotlin DSL and Apache Maven parity.
  - 10 canonical JMH suites covering workloads B01 through B15, including memory profiling (`-prof gc`) and verified cross-JDK baselines across Java 17, 21, and 25.
- **Security Hardening (Milestone M13)**:
  - Pluggable defense-in-depth member access security (`MemberAccessPolicy`, `DefaultMemberAccessPolicy`, `DenyAllMemberAccessPolicy`, `AllowlistMemberAccessPolicy`) providing default-deny policies blocking reflection (`java.lang.reflect.*`, `java.lang.invoke.*`), classloaders (`ClassLoader`, `Module`), process execution (`Process`, `ProcessBuilder`), thread management (`Thread`, `ThreadGroup`), concurrency executors (`ExecutorService`, `ThreadPoolExecutor`, `ForkJoinPool`), and system/runtime manipulation (`System`, `Runtime`, `SecurityManager`), as well as strict safe allowlists via `MemberAccessPolicy.allowlistBuilder()`.
  - Cryptographic security policy fingerprinting (`SecurityPolicyFingerprint`) computing deterministic SHA-256 digests over policy rules to guarantee cache key partitioning across security configurations.
  - Zero-dependency sensitive object classification (`SensitiveObjectClassifier`) dynamically inspecting class hierarchies and interfaces to block internal framework objects (e.g. Spring `ApplicationContext`, `BeanFactory`, Java Security/Runtime types) without runtime framework dependencies.
  - Static parser and AST complexity bounding in `VtlParser` and `VtlParserOptions` enforcing static limits on template source characters (`maxSourceCharacters`), AST node counts (`maxAstNodes`), expression depth (`maxExpressionDepth`), and directive nesting (`maxDirectiveNesting`).
  - Unified monotonic render execution budget (`RenderBudget`) tracking output character counts, loop iteration budgets, and wall-clock execution deadlines (`maxExecutionTimeMillis` via `System.nanoTime()`), propagated seamlessly across top-level templates, `#parse`, `#include`, `#evaluate`, macros, and screen/layout plans (`DefaultLayoutRenderPlan`).
  - Resource root confinement and path sandboxing in `TemplateId` (rejecting null bytes, encoded traversals `%2e`/`%2f`/`%5c`/`%00`, URI scheme colons, and Windows drive letters) and `FilesystemTemplateRepository` (canonical real-path boundary verification and symlink escape rejection).
  - Protected context variable enforcement in `MutableRenderContext` (`protectedKeys`), guarding engine-reserved variables (such as layout `$screen_content`) against in-template `#set` directives and contributor overwrites with `TemplateSecurityException`.
  - `#evaluate` security controls: disabled by default in standard/safe profiles, sharing parent execution budgets, and partitioning dynamic compilation caches by policy fingerprint.
  - Strict security parity across all execution tiers (AST interpreter, IR interpreter, dynamic linker PIC via `LinkerAccessPolicy`, and AOT bytecode backend via `BytecodeTemplateCompiler`).
  - Comprehensive adversarial test suite including 22-category security regression corpus (`SecurityRegressionCorpusTest`), multi-tier grammar fuzzing (`SecurityFuzzTest`), and concurrent stress testing (`SecurityConcurrencyTest`).
- **Velocity Application Compatibility Architecture (Milestone M12.5)**:
  - Directed template dependency graph (`TemplateDependencyGraph`, `TemplateDependencyKind`, `TemplateDependency`) with thread-safe atomic updates, cycle-safe graph traversal, and precise transitive dependent eviction (`invalidateWithDependents`).
  - Static dependency extraction pass (`StaticDependencyExtractor`) identifying `#parse`, `#include`, global macro library, and layout template dependencies directly from canonical IR.
  - Multi-source context composition (`RenderRequest`, `ContributorContext`, `RenderContextContributor`, `ContributingContextComposer`) allowing clean, framework-neutral injection of model maps, helper facades, request/session attributes, and tools.
  - Granular context collision policies (`ContextCollisionPolicy`: `FAIL`, `MODEL_WINS`, `CONTRIBUTOR_WINS`) and value origin tracking (`ValueOrigin`), with strict protection preventing external overwrite of engine-reserved variables (`$screen_content`).
  - Thread-confined mutable rendering context (`MutableRenderContext`, `DefaultMutableRenderContext`) providing write-through support for in-template `#set` directives during evaluation across both IR interpreter and AOT bytecode tiers.
  - Global Velocimacro library caching and management (`GlobalMacroManager`, `GlobalMacroPrecedence`) parsing `velocimacro.library` templates once into IR functions, hashing fingerprints into compilation cache keys (`globalMacrosFingerprint`), remapping constant pool IDs, and enforcing the invariant that local template macros unconditionally shadow global macros.
  - Reusable two-stage layout rendering plan (`LayoutRenderPlan`, `DefaultLayoutRenderPlan`, `LayoutResolver`, `LayoutConfiguration`) executing screen capture to bounded in-memory output (`maxOutputCharacters`), post-screen layout resolution supporting in-template `#set($layout = ...)` override and bypass, recursion cycle detection (`LAYOUT:CYCLE_DETECTED`), depth limits (`maxLayoutDepth`), and configurable context propagation (`LayoutContextScope`).
  - Zero external production dependencies: core engine remains completely framework-neutral with no runtime dependencies on Spring Framework or Apache Velocity.
  - Complete ArchUnit verification ensuring zero architecture boundary violations across all modules.
- **Template Repository, Cache, and Hot Reload (Milestone M12)**:
  - Traversal-safe, normalized template identifiers (`TemplateId.normalize`, `TemplateId.isTraversalSafe`) guarding against directory traversal attacks, root escapes (`../`), duplicate/redundant slashes, and null-byte injection.
  - Pluggable template repository SPI (`TemplateRepository`) with factory methods and production implementations:
    - `ClasspathTemplateRepository`: Resource-confined classpath loader verifying path boundaries and resolving templates with configurable prefixes and UTF-8 charset defaults.
    - `FilesystemTemplateRepository`: Root-confined filesystem loader enforcing strict path confinement, symlink resolution boundaries, and preventing directory escaping.
    - `CompositeTemplateRepository`: Deterministic multi-repository chain with first-match-wins ordering.
    - `InMemoryTemplateRepository`: Concurrency-safe registry supporting dynamic registration and testing.
  - Multi-dimensional compilation cache key (`CompileCacheKey`) capturing template identifier, source fingerprint (SHA-256), compiler version, optimization level, execution tier, access policy ID, model signature, and backend options hash.
  - High-performance, bounded template compile cache (`TemplateCompileCache`) with LRU eviction and generation tracking:
    - Atomic replacement of compiled template handles (`CompiledTemplateHandle`) ensuring in-flight renders safely complete against their generation without interruption or race conditions.
    - Configurable negative caching (`NegativeCacheEntry`) preventing denial-of-service from repeated lookups of non-existent templates, with millisecond-precision TTL eviction.
    - Zero global `ClassLoader` leaks: evicting or replacing a compiled template drops all references to its generation-scoped `TemplateClassLoader`, enabling clean garbage collection of dynamic bytecode classes.
  - Development file watcher (`DevelopmentFileWatcher`) using NIO `WatchService` with configurable debounce windows (default 200ms) coalescing rapid filesystem events and triggering asynchronous cache invalidation.
  - Strict production mode enforcement (`rejectRuntimeCompilation`): optionally rejects all on-the-fly compilation in production deployments, enforcing pre-compiled AOT bytecode artifacts and preventing arbitrary code generation.
  - Unified public engine API (`TemplateEngine`, `TemplateEngineProvider`, `VtlTemplateEngine`, `VtlTemplateEngineBuilder`) discovering engines via `ServiceLoader` and exposing high-level template retrieval, descriptor inspection, and rendering.
- **AOT Bytecode Backend (Milestone M11)**:
  - Architecture Tier 3 direct bytecode compiler (`io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeTemplateCompiler`) implementing `TemplateBackend` SPI.
  - Zero-external-dependency, pure Java JVM SE 17 classfile writer (`ClassFileWriter`) producing major version 61 classfiles with full constant pool, line number tables, and JVM split-verifier compliant `StackMapTable` (`full_frame`, tag 255) local variable and branch target tracking.
  - Deterministic class naming (`BytecodeNaming`) generating collision-free class names `T_<sanitizedId>_<sha256Hex>` and chunk method names `renderChunk_<name>`.
  - Canonical public ABI interface `CompiledTemplate` in `io.github.minh124199.viettemplate.api` declaring `void render(RenderContext, TemplateOutput)` and metadata accessors.
  - Runtime bridge (`BytecodeRuntimeBridge`) handling contextual escaping, safe content unwrapping, truthiness and null checking, range generation, dynamic property and method dispatch, and foreach budget limits.
  - Dynamic member and method calls routed exclusively through M9 `DynamicCallSite` and `DynamicLinker` with zero runtime reflection and zero unapproved `invokedynamic`.
  - Static accessor specialization for Java Records, JavaBeans getters, and public fields.
  - Automatic method splitting support integrating M10 `MethodSizePlanningPass` to segment oversized template blocks into partitioned chunk methods (`renderChunk_*`).
  - Generation-level classloader isolation (`TemplateClassLoader`) allowing clean garbage collection and development hot reload without classloader leaks.
  - Capability reporting via `CompilationStatus` (`AOT_OK`, `AOT_OK_WITH_DYNAMIC_SITES`, `INTERPRETER_REQUIRED_EVALUATE`, `DENIED_SECURITY`, `UNSUPPORTED_LANGUAGE_FEATURE`).
  - Seamless tier execution integration in `VtlInterpreter` with automatic compilation and fallback for `ExecutionTier.AOT_BYTECODE`.
  - Sidecar metadata indexing (`TemplateSidecarIndex`, `CompiledArtifact`) providing bytecode offset-to-source line mappings and parameter metadata.
  - Verified across Java 17, 21, and 25 with 100% test pass rate and dual-build parity between Apache Maven and Gradle Kotlin DSL.
- **Intermediate Representation Optimizer (Milestone M10)**:
  - Architecture Tier 2/3 optimizer pipeline (`io.github.minh124199.viettemplate.language.vtl.ir.optimization`) operating directly on compiler-internal `IrTemplate`.
  - Configurable optimization levels (`OptimizationLevel`: `O0`, `O1`, `O2`, `O3`) with granular per-pass toggles (`IrOptimizationOptions`).
  - Thread-safe optimization metrics collector (`OptimizationStatistics`) capturing counts for all 12 optimization categories.
  - Implementation of the IR optimization pipeline (11 distinct optimization pass classes and static UTF-8 pre-encoding lowering, executing 13 transformation passes with repeated passes):
    - Dead code elimination (`DeadCodeEliminationPass`): removes unreachable blocks after terminal statements (`IrReturn`, `IrStop`, `IrBreak`), statically determines dead branches, and strips redundant `IrNoOp` statements.
    - Constant folding (`ConstantFoldingPass`): folds compile-time constants for binary arithmetic, comparisons, string concatenation, logical operations, unary negation/not, and truthiness checks while strictly preserving runtime range iteration semantics.
    - Boolean simplification (`BooleanSimplificationPass`): applies boolean algebra reductions (`true && x -> x`, `false || x -> x`, `!(!x) -> x`), eliminates redundant truthiness conversions on booleans, and inverts negated conditionals with both branches.
    - Text constant merging (`MergeTextConstantsPass`): combines adjacent static text writes (`IrWriteConst`) into unified text chunks to minimize dispatch and buffer overhead.
    - UTF-8 chunk pre-encoding (`PreEncodeUtf8Pass`): pre-computes UTF-8 byte arrays for static text constants into the constant pool for zero-copy streaming.
    - Redundant local load and conversion elimination (`RedundantConversionPass`): strips identity and redundant type conversions and stores.
    - Direct accessor binding (`DirectAccessorBindingPass`): statically binds known record components, getters, fields, and map accessors to eliminate dynamic PIC lookups.
    - Primitive operation specialization (`PrimitiveSpecializationPass`): specializes primitive arithmetic and comparison operations, removing boxing and unboxing wrappers.
    - Loop specialization (`LoopSpecializationPass`): specializes loop iteration plans to `ARRAY`, `LIST_INDEXED`, `ITERABLE`, `ITERATOR`, and `RANGE` based on static collection types.
    - Macro inlining (`MacroInliningPass`): inlines small non-recursive macros within statement and depth budgets with local slot remapping.
    - Method size planning (`MethodSizePlanningPass`): segments oversized blocks exceeding bytecode method limits into partitioned helper chunk functions (`IrFunction`).
    - Escape hoisting and pre-escaping (`EscapeSpecializationPass`): pre-escapes static strings and hoists constant writes to static constant pool chunks when safe.
  - Unconditional invariant verification (`O160 Verify`): executes `IrVerifier.verify(optimizedTemplate)` on every optimization run.
  - Explain plan diagnostic utility (`IrExplainPlan`): formats structured plan diagnostics with access plans, loop specializations, constant stats, and pass metrics.
  - Integration with reference runtime: `VtlInterpreterOptions` and `VtlInterpreter` transparently optimize lowered IR templates across execution tiers.
- **Dynamic Linker & Inline Caches (Milestone M9)**:
  - Architecture Tier 2 dynamic linker (`io.github.minh124199.viettemplate.runtime.linker`) for high-performance dynamic dispatch across unspecialized and evolving call sites.
  - Member identifier `MemberKey` modeling operation kind (`PROPERTY_GET`, `PROPERTY_SET`, `METHOD_CALL`, `INDEX_GET`, `INDEX_SET`), member name, and arity.
  - Explicit capability-governing `LinkerAccessPolicy` security abstraction isolating cache entries across security contexts.
  - Adaptive inline cache call sites (`DynamicCallSite`) with monomorphic fast-path, small polymorphic inline cache (PIC, depth 4), and megamorphic fallback.
  - Thread-safe, bounded class cache (`BoundedWeakClassCache`) utilizing `ReferenceQueue` and `WeakReference<Class<?>>` keys to strictly prevent ClassLoader leaks during redeployments and dynamic code generation.
  - Low-overhead atomic statistics tracking (`LinkerStatistics`) recording call site hits, misses, link creations, and security denials.
  - Denied member invariant verification ensuring that disallowed methods, fields, and classes never become executable through cache reuse.
  - High-performance `LinkedReferenceAccess` adapter in `viet-template-vtl-interpreter` connecting the reference IR interpreter to inline-cached dynamic call sites with transparent fallback.
- **Output Runtime Architecture (Milestone M8)**:
  - Low-allocation, streaming output runtime implementing the complete `TemplateOutput` abstraction across `WriterTemplateOutput`, `Utf8OutputStreamTemplateOutput`, and `StringTemplateOutput`.
  - Zero-allocation primitive numeric formatting (`NumberFormatting`) formatting `int`, `long`, `double`, `float`, `short`, `byte`, and `boolean` directly to `Writer` and byte buffers without intermediate `String` creation.
  - Zero-allocation `CharSequence` traversal writing character chunks without forcing `.toString()` conversions.
  - Zero-copy static UTF-8 byte chunk streaming (`writeUtf8`) directly into output streams.
  - Contextual escaping framework (`Escaper` SPI, `StandardEscapers`, and thread-safe `EscaperRegistry`) streaming escaped output directly to `TemplateOutput`.
  - Streaming `HtmlTextEscaper` neutralizing HTML delimiters (`&`, `<`, `>`, `"`, `'`) with fast-path scanning emitting clean content without allocation.
  - Secure `HtmlAttributeEscaper` policy neutralizing HTML delimiters, backtick (`` ` ``), and non-printable control characters (`0x00`-`0x1F`, `0x7F`).
  - RFC 3986 `UrlComponentEscaper` implementing percent-encoding for URI query parameters and path segments.
  - JavaScript (`JsStringEscaper`) and CSS (`CssStringEscaper`) literal escapers neutralizing tag breakouts (`</script>`, `</style>`) and quotes.
  - Explicit contextual escaping boundary decisions and back-pressure architecture documented in `docs/08-runtime-output.md`.
  - Explicit safe content types (`SafeContent`, `SafeHtml`, `SafeUrl`) allowing trusted or pre-sanitized markup to bypass escaping in appropriate contexts.
- **Reference IR Interpreter & Execution Backend (Milestone M7)**:
  - IR execution engine (`IrInterpreter`, `InterpretedFrame`) executing the exact same intermediate representation (`IrTemplate`) used by compiled backends.
  - Streaming output support in `TemplateOutput` emitting zero-copy UTF-8 bytes (`writeUtf8`) for static text constants and primitive values directly (`writeInt`, `writeLong`, `writeDouble`, `writeBoolean`) with budget limit counting (`CountingTemplateOutput`).
  - Full `VTL_CORE` semantic coverage across directives, expressions, assignments, foreach loops with metadata (`ForeachMetadata`), macros, and alternate values (`IrAlternateValue`).
  - Capability-gated dynamic features including `#evaluate` (`IrEvaluate`) restricted to `VTL_DYNAMIC` profile with depth and length limits.
  - Property and index mutation statements (`IrSetProperty`, `IrSetIndex`) for navigated and indexed assignment.
  - Source-position error reporting mapping runtime failures back to `SourceSpan` across templates and includes.
  - Deterministic execution safety budgets via `ExecutionLimits` (output characters, loop iterations, recursion depth, dynamic source length).
  - Development hot reload system (`TemplateHotReloader`) supporting thread-safe atomic template swaps, SHA-256 content hashing, generation tracking, and granular cache invalidation.
  - Multi-tier execution architecture (`ExecutionTier`: `IR`, `AST`) selectable via `VtlInterpreterOptions`.
- **Template Intermediate Representation (Milestone M6)**:
  - Compiler-internal intermediate representation (`io.github.minh124199.viettemplate.language.vtl.ir`) modeling templates as immutable `IrTemplate` containing parameters, local slots, blocks, functions/macros, and constant pools.
  - Constant text pool (`IrConstantPool`, `IrTextConstant`) with adjacent static text chunk collapsing and pre-computed UTF-8 byte representations.
  - Value-effect separation: value expressions (`IrExpression`) decoupled from output writes (`IrWriteValue`, `IrWriteConst`).
  - Explicit member access plans (`AccessPlan`) distinguishing `DirectRecord`, `DirectGetter`, `DirectField`, `MapLookup`, `ExtensionCall`, and `DynamicCallSite`.
  - Polymorphic callsite representation via `DynamicAccessSite` with kinds `PROPERTY_GET`, `METHOD_CALL`, and `INDEX_GET`.
  - Explicit escaping and null rendering strategies (`IrEscapeMode`, `NullRenderMode`, `NullAccessMode`).
  - Primitive and typed arithmetic, comparison, and logical expressions (`IrBinaryOp`, `IrUnaryOp`, `IrConst`, `IrIsNull`, `IrTruthiness`, `IrConvert`).
  - Explicit loop plans (`LoopPlan`: `ARRAY`, `LIST_INDEXED`, `ITERABLE`, `ITERATOR`, `RANGE`, `DYNAMIC`) and control flow statements (`IrLoop`, `IrLoopSetup`, `IrLoopNext`, `IrLoopEnd`, `IrIf`, `IrBranch`, `IrBranchIf`, `IrBreak`, `IrStop`, `IrReturn`, `IrCallTemplate`, `IrCallMacro`, `IrBudgetCheck`).
  - AST-to-IR lowerer (`AstToIrLowerer`) lowering parsed AST and semantic analysis metadata into validated IR.
  - Comprehensive IR verifier (`IrVerifier`) enforcing local variable definitions before use, type consistency, valid block/branch targets, constant pool reference integrity, and strict AOT constraints (forbidding unlinked dynamic dispatch).
- **Semantic Analysis and Model Typing (Milestone M5)**:
  - Strong typing model (`VType` sealed hierarchy) covering `PrimitiveType`, `ClassType`, `ArrayType`, `DynamicType`, `NullType`, `UnionType`, and `ErrorType`, with explicit `Nullability` (`NON_NULL`, `NULLABLE`, `UNKNOWN`).
  - Model declaration and schema introspection (`ModelSchema`, `@TemplateModel`) supporting Java records, interfaces, and JavaBeans.
  - Hierarchical lexical scoping (`SymbolTable`, `SymbolScope`, `ScopeKind`) supporting root model immutability, `#set` local variables, `#foreach` loop variables, `$foreach` loop metadata (`index`, `count`, `first`, `last`, `hasNext`, `parent`, `stop()`), and macro parameter scopes.
  - Safe typed member resolution (`MemberResolver`) with precedence: record component -> getter (`getX()`) -> boolean getter (`isX()`) -> field -> extension -> map key -> missing.
  - Levenshtein-based typo suggestions and compile-time diagnostics with stable codes (`VTLS2104` missing property, `VTLS2101` missing root reference, `VTLS2102` invalid assignment, `VTLS2106` invalid iterable).
  - Method overload resolution (`MethodResolver`) with parameter scoring and security denial checks (`VTLSEC2401`).
  - Comprehensive static capability analysis (`TemplateCapabilities`) computing 7 capability flags including Ahead-Of-Time (`eligibleForStaticAot`) lowering eligibility.

### Changed
- **Build & CI Engineering**:
  - Upgraded Gradle wrapper from 8.12 to 9.7.1; Java 25 execution is supported by Gradle 9.7.1 and remains covered by the GitHub Actions matrix.
  - Explicitly configured `org.junit.platform:junit-platform-launcher` in test runtime dependencies across all modules to ensure JUnit 5 test discovery and execution under Gradle 9.
  - Upgraded Google Java Format from 1.24.0 to 1.30.0 across Gradle and Maven.
  - Decoupled source code formatting verification from the multi-JDK compatibility matrix: established a dedicated, mandatory CI quality gate (`formatting`) on JDK 21 running `./gradlew spotlessCheck` and `./mvnw spotless:check -B`.
  - Application runtime compatibility matrix continues to verify compilation, test suites, and TCK compliance across Java 17, 21, and 25, passing `-Dspotless.check.skip=true` to skip formatting execution under matrix JVMs. This isolates `google-java-format` javac-internal runtime dependencies from application JDK compatibility while maintaining full local developer formatting enforcement.

### Fixed
- **Bytecode Compiler Hardening**: Fixed method size planning to prevent duplicate method emission when splitting oversized template chunks into partitioned helper methods.
- **Optimizer Idempotence**: Resolved optimizer pass ordering edge cases to ensure strict idempotence and valid IR verification under O3 optimization level.
- **Runtime Fallback Synchronization**: Hardened nested lexical scope coherence and fallback synchronization across `#foreach`, `#macro`, and `#evaluate` boundaries.
- **Release Bundle Validation**: Enhanced release bundle validation to verify non-publishing exclusions for both `viet-template-tck` and `viet-template-benchmarks` across Maven and Gradle.

### Performance & Allocation Evidence
- **Throughput Improvements (Apples-to-Apples Java 17 Baseline)**:
  - B02 (Scalar Variables): IR +3.9%, AOT +27.6%
  - B05 (Small Table): IR +18.7%, AOT +18.3%
  - B06 (Large Table): IR +16.1%, AOT +18.1%
  - B07 (Nested Loops): IR +4.4%, AOT +13.1%
  - B11 (Macros): IR -1.9% (within measurement error), AOT +32.9%
- **Microbenchmarks**:
  - `assignStaticSlot`: ~406M ops/s vs dynamic `assignTemplateLocal`: ~27.5M ops/s (~14.8x speedup).
  - Direct static slot reads remain effectively insensitive to lexical scope depth in the isolated Java 17 microbenchmark.
- **Allocation Profile**:
  - Milestone M19.2b primarily improved execution throughput rather than reducing AOT allocation.
  - In measured workloads, AOT bytecode exhibited no material allocation change (e.g. 14.5 KB/op on B02, 9.2 KB/op on B05).
  - The IR interpreter pays bounded frame-allocation overhead (+0.1% to +3.5% on loops/scalars, ~+10.7% on macros) while avoiding repeated scope-map work.
- **Slot Invariants**:
  - `ExecutionFrame` explicitly initializes slots to `EvaluationValue.undefined()` (Java reference arrays themselves initialize to `null`).
  - Current measured workloads do not demonstrate a need for slot packing in 0.2.0, so slot reuse remains deferred.

### Compatibility & Migration
- **Java Baseline**: Built against Java 17 baseline (`--release 17`), fully tested and verified across Java 17, 21, and 25.
- **Apache Velocity Compatibility**: 100% accounted behavioral coverage across 301 differential test scenarios against Apache Velocity Engine 2.4.1 in `viet-template-tck` (98.01% exact behavioral parity, 5 intentional security/correctness differences, 1 extension).
- **Migration Assessment**:
  - **Template Syntax**: Zero template changes required for standard Velocity templates (`.vm`). Full support for VTL directives, macros, expressions, and alternate value fallbacks.
  - **Java API**: No incompatible public API changes were found in the 0.1.0 → 0.2.0 public API review; observed API changes are additive. Standard rendering via `TemplateEngine` or `VtlInterpreter` operates with 100% binary and source compatibility.
  - **Internal Architecture**: Applications relying directly on compiler-internal AST or IR classes should note the introduction of `ExecutionFrame` and `AssignVariableSlotsPass`.

## [0.1.0] - 2026-09-06

### Added
- **Public API (`viet-template-api`)**:
  - Core interfaces and value records: `TemplateEngine`, `Template`, `RenderContext`, `TemplateOutput`, `TemplateDescriptor`, `TemplateId`.
  - Compiler-grade diagnostics: `Diagnostic`, `DiagnosticCode`, `DiagnosticSeverity`, `SourceSpan`.
  - Structured unchecked exception hierarchy with diagnostic codes and source location tracking (`TemplateException`, `TemplateSyntaxException`, `TemplateSemanticException`, `TemplateRenderException`, `TemplateLimitException`, `TemplateSecurityException`, `TemplateResourceException`).
- **Runtime Execution (`viet-template-runtime`)**:
  - Streaming output abstractions: `StringTemplateOutput` (in-memory) and `WriterTemplateOutput` (character/byte streaming).
  - Contextual escaping support with `EscapeMode` (`NONE`, `HTML`, `XML`, `JAVASCRIPT`).
  - Immutable context implementations and map adapters.
- **VTL Language and AST (`viet-template-language-vtl`)**:
  - Clean-room lexer (`VtlLexer`) with resilient error recovery and source tracking.
  - Pratt parser (`VtlParser`) supporting full VTL grammar: references, directives (`#set`, `#if/#elseif/#else`, `#foreach`, `#break`, `#stop`, `#include`, `#parse`, `#define`, `#macro`, `#evaluate`), expressions, ranges, and literals.
  - Complete immutable AST model (`VtlNode`, `VtlExpression`, `VtlDirectiveNode`).
- **Reference Interpreter (`viet-template-vtl-interpreter`)**:
  - Reference interpreter (`VtlInterpreter`) implementing standard 3-state evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`).
  - Strict Velocity 2.4.1 compatibility modes: truthiness empty checks, configurable `#set` null-RHS overwriting, alternate-value fallback (`${var|'default'}`), and `LINES` mode space gobbling.
  - Loop metadata (`$foreach.index`, `$foreach.count`, `$foreach.first`, `$foreach.last`, `$foreach.hasNext`, `$foreach.parent`, `$foreach.stop()`).
  - Renderable blocks with `#define` and block macro support (`#@macroName ... #end`).
- **Tooling & Engineering Foundation**:
  - Dual build system supporting Gradle 8.12 (Kotlin DSL) and Apache Maven 3.9.9 with 100% build parity.
  - Automated release publication bundle validation (`scripts/validate-release-bundle.sh`).
  - Release metadata and workflow contract verification (`scripts/verify-release-metadata.py`).
  - Isolated release simulation tooling (`scripts/simulate-release.sh`).
  - Automated dual-build parity verification (`scripts/verify-build-parity.sh`).

### Security
- Default safe execution profile blocking reflection pivots (`Class`, `ClassLoader`) and sensitive system access.
- Execution resource limits (`ExecutionLimits`) guarding maximum loop iterations, recursion depth, range sizes, and output character limits.

### Compatibility
- Official Technology Compatibility Kit (`viet-template-tck`) baseline against Apache Velocity Engine 2.4.1.
- Evaluated across 301 differential test scenarios in 20 functional categories:
  - 295 `EXACT_MATCH` (98.01% exact behavioral parity)
  - 5 `EXPECTED_DIFFERENCE` (intentional differences for divide-by-zero, reflection denial, and legacy 1.x null-RHS preservation)
  - 1 `VIET_EXTENSION` (`$foreach.stop()` programmatic loop termination)
  - 0 `UNSUPPORTED`, 0 `BUG` (100.00% accounted behavior coverage)

[Unreleased]: https://github.com/minh124199/viet-template/compare/v0.2.2...HEAD
[0.2.2]: https://github.com/minh124199/viet-template/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/minh124199/viet-template/compare/v0.2.0...v0.2.1
