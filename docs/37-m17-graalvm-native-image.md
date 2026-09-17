# Milestone M17 — GraalVM Native Image & Spring AOT Compatibility (Phase A)

## 1. Executive Summary

- **Milestone**: M17 — GraalVM Native Image & Spring AOT Compatibility (Phase A)
- **Status**: **COMPLETE**
- **Decision**: **`A. M17 NATIVE IMAGE PHASE COMPLETE — READY FOR DEVTOOLS RESTART/REFRESH HARDENING`**
- **Technical Baseline**:
  - Production bytecode: Java 17 (`--release 17`, classfile major version 61)
  - Native image builder JDK: GraalVM CE / JDK 21+ (`native-image 21.0.2+13.1`)
  - Spring Framework 6.1.14 & Spring Boot 3.3.5 baseline
  - Spring Security 6.3.4 baseline
  - Generation 2 compatibility: Spring Framework 7.0 / Spring Boot 4.0 / Spring Security 7.0
  - Native Build Tools: 0.10.3
- **Core Proof**:
  ```text
  VTL source
  → Viet Template build-time AOT compilation
  → Spring AOT processing (processAot / process-aot)
  → GraalVM Native Image compilation (native-image)
  → standalone native executable
  → embedded HTTP server
  → Viet Template rendering (Scalar, Record, JavaBean, Map, #if, #foreach, HTML escaping)
  → Spring Security rendering ($security, $csrf)
  with runtime template compilation disabled (rejectRuntimeCompilation = true).
  ```

---

## 2. Closed-World Constraints & Reachability Architecture

In a GraalVM closed-world native image, all reflection, resource loading, dynamic proxies, and classloading must be declared ahead of time or discovered through static analysis.

### 2.1 Dynamic Mechanisms Audit & Solutions

| Dynamic Mechanism | Closed-World Constraint | Solution | Reachability Registration |
|---|---|---|---|
| `templates.idx` Resource Discovery | `ClassLoader.getResources(...)` only discovers files included in `resource-config.json`. | Registered resource pattern in `VietTemplateRuntimeHints`. | `hints.resources().registerPattern("META-INF/viet-template/templates.idx")`, `hints.resources().registerPattern("META-INF/viet-template/*")` |
| Generated Compiled Template Classes | Generated classes (e.g. `T_hello_vtl_*`) are referenced by string in `templates.idx` and instantiated via `aotClass.getDeclaredConstructor().newInstance()`. | Discovered deterministically from `templates.idx` on the AOT classpath during Spring AOT processing and registered for reflection. | `hints.reflection().registerType(TypeReference.of(fqcn), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)` |
| Spring Security Facades (`SecurityView`, `CsrfView`) | VTL templates evaluate `$security.name`, `$security.authenticated`, `$security.hasAuthority(...)`, `$csrf.token`, `$csrf.headerName` via `DynamicLinker` reflection. | Registered reflection for interfaces and internal default implementations in `VietTemplateSecurityRuntimeHints`. | `hints.reflection().registerType(SecurityView.class, MemberCategory.INVOKE_PUBLIC_METHODS)`, `hints.reflection().registerType(TypeReference.of("...DefaultSecurityView"), ...)` |
| `VietTemplateProperties` | Spring Boot configuration property binding at AOT/runtime. | Registered constructor and method reflection in `VietTemplateRuntimeHints`. | `hints.reflection().registerType(VietTemplateProperties.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)` |
| `TemplateEngineProvider` | ServiceLoader discovery and fallback reflection in `TemplateEngine.builder()`. | Registered reflection for `VtlTemplateEngineProvider`. | `hints.reflection().registerType(TypeReference.of("...VtlTemplateEngineProvider"), ...)` |
| Application Model Classes | Dynamic member access on domain objects (`Account`, `Profile`) via `DynamicLinker`. | **Model Reachability Contract**: Built-in JDK collections and basic types are supported natively. Consumer domain model classes must be registered by the consumer application (e.g., `@RegisterReflectionForBinding` or application `RuntimeHintsRegistrar`). | Documented consumer responsibility. |
| Runtime Template Compilation | Dynamic bytecode generation (`TemplateClassLoader`) is unsupported in native image. | `VtlTemplateEngine` enforces `rejectRuntimeCompilation=true`, giving clear, early `TemplateSecurityException` diagnostics instead of VM crashes. | Precompiled templates required in native mode. |

---

## 3. Implementation Details

### 3.1 `VietTemplateRuntimeHints` (`viet-template-spring-boot-autoconfigure`)

Implements Spring's `RuntimeHintsRegistrar`:
1. Discovers all `META-INF/viet-template/templates.idx` resources available on the AOT `ClassLoader`.
2. Parses canonical `id=fqcn` entries, ignoring comments and whitespace.
3. Registers every generated compiled template class for reflection (`INVOKE_DECLARED_CONSTRUCTORS`, `INVOKE_PUBLIC_METHODS`).
4. Registers `META-INF/viet-template/templates.idx` and `META-INF/viet-template/*` as resource patterns.
5. Registers `VietTemplateProperties` and `VietTemplateProperties.Security` for reflection.
6. Registered via `@ImportRuntimeHints(VietTemplateRuntimeHints.class)` on `VietTemplateAutoConfiguration` and `META-INF/spring/aot.factories`.

### 3.2 `VietTemplateSecurityRuntimeHints` (`viet-template-spring-security`)

Implements `RuntimeHintsRegistrar`:
1. Registers `SecurityView` and `DefaultSecurityView` for reflection.
2. Registers `CsrfView` and `DefaultCsrfView` for reflection.
3. Registered via `@ImportRuntimeHints(VietTemplateSecurityRuntimeHints.class)` on `VietTemplateSecurityAutoConfiguration` and `META-INF/spring/aot.factories`.

---

## 4. Model Reachability Contract

Viet Template enforces a clear contract separating framework-owned reachability from consumer domain model reachability:

- **Framework-Owned Reachability**:
  Viet Template automatically provides reachability for all internal components, view resolvers, engine builders, auto-configurations, security view facades, and generated template classes discovered from `templates.idx`.
- **Consumer Domain Model Reachability**:
  Because VTL templates access properties dynamically (e.g., `$account.loginCount`, `$profile.displayName`), any custom application domain class passed into `Model` / `ModelAndView` must be registered for reflection by the consumer application.
  In Spring Boot 3 / 4 applications, this is achieved using:
  ```java
  @SpringBootApplication
  @RegisterReflectionForBinding({Account.class, UserProfile.class})
  public class Application { ... }
  ```
  or by implementing a custom Spring `RuntimeHintsRegistrar`.

---

## 5. Parity & Dual-Build Verification

Native image support is verified across both Maven and Gradle:
- **Maven**: `viet-template-maven-plugin` (compile goal) + `spring-boot-maven-plugin` (`process-aot`) + `native-maven-plugin` (`native:compile`).
- **Gradle**: `io.github.minh124199.viet-template` + `org.springframework.boot` (`processAot`) + `org.graalvm.buildtools.native` (`nativeCompile`).
- Byte-for-byte equivalence of `templates.idx` and generated `.class` files is strictly verified between Maven and Gradle.
- Bytecode major version 61 (Java 17) is verified for all generated template classes.

---

## 6. Live Native HTTP Testing Results

The compiled standalone native executable was launched on an embedded HTTP port and tested with live HTTP requests:

1. **`GET /public` (200 OK)**:
   - Scalar property: `$user.name` -> `Welcome, Alice!`
   - Record access: `$account.name (Logins: $account.loginCount)` -> `Account: AliceAccount (Logins: 10)`
   - JavaBean getter: `$profile.displayName` -> `Profile: Alice Wonderland`
   - Map lookup: `$settings.theme` -> `Theme: dark`
   - Conditional `#if`: `$account.loginCount > 5` -> `Status: Veteran`
   - Foreach loop `#foreach`: `<li>java</li>`, `<li>native</li>`, `<li>viet-template</li>`
   - Hostile input rendering: `<div id="escaped"><script>alert('x')</script></div>`
2. **`GET /dashboard` unauthenticated**: Returns `401 Unauthorized`.
3. **`GET /dashboard` authenticated (`user:user123`) (200 OK)**:
   - `$security.name` -> `user`
   - `$security.authenticated` -> `true`
   - `$security.hasAuthority('ROLE_ADMIN')` -> `false`
   - `$csrf.token` and `$csrf.headerName` rendered.
4. **`GET /admin` authenticated as non-admin (`user:user123`)**: Returns `403 Forbidden`.
5. **`GET /admin` authenticated as admin (`admin:admin123`) (200 OK)**:
   - `$security.name` -> `admin`
   - `$security.hasAuthority('ROLE_ADMIN')` -> `true`

---

## 7. Public API & Invariant Stability

- **Public Surface**: Zero new stable public APIs introduced.
- **Classification**: `VietTemplateRuntimeHints` and `VietTemplateSecurityRuntimeHints` classified as `PUBLIC_BUT_INTERNAL_ACCIDENT`.
- **API Compatibility**: 0 breaking changes across all 4 stable baselines.
- **Security Invariants**: Closed-world execution preserves all runtime security boundaries and prevents unauthorized access to internal framework/security context structures.
