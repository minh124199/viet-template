# Milestone M14: Public API & SPI Stabilization Report

- **Document Version**: `1.0.0`
- **Protocol**: `VT-API-M14-PUBLIC-STABILIZATION-1`
- **Date**: 2026-09-14
- **Authoritative Baseline Commit**: `31b401aa83238ad84c57935fab893f0723e0df9e`
- **Branch**: `release/m14-public-api-spi-stabilization`
- **Final Decision / Status**: **`A. M14 COMPLETE — PUBLIC API/SPI STABILIZED`**
- **Impacted Production Modules**:
  - `viet-template-api` (contract hardening, null preservation, convenience methods, AutoCloseable)
  - `viet-template-runtime` (contract Javadoc, fail-closed linker security adapter)
  - `viet-template-language-vtl` (public classification audit)
  - `viet-template-vtl-interpreter` (fail-closed engine security adapter, lifecycle implementation)
- **Impacted Verification Modules**:
  - `viet-template-tck` (new ArchRule `api_must_not_depend_on_runtime_or_interpreter`)
  - `viet-template-api` (new `PublicApiContractTest`)
  - `viet-template-runtime` (new `TemplateOutputSpiContractTest`)
  - `viet-template-vtl-interpreter` (new `TemplateEngineContractTest`, `TemplateRepositorySpiContractTest`, `SecuritySpiContractTest`, `ApiConsumerSmokeTest`)
- **Compatibility Tooling Added**:
  - `config/api-baseline/1.0-public-api.txt` (80 stable public API and SPI types)
  - `scripts/verify-api-compatibility.py` (automated binary and source compatibility verifier)
  - `.github/workflows/ci.yml` (automated CI job `api-compatibility`)
- **Java Target & Bytecode Baseline**: Java 17 strictly frozen (`--release 17`, classfile major version 61).

---

## 1. EXECUTIVE SUMMARY & GOVERNING PRINCIPLE

Milestone **M14: Public API & SPI Stabilization** is the first critical step on the path toward **Viet Template 1.0**.

The milestone was governed by a single uncompromising question:
> *"If Viet Template 1.0 shipped from this public surface tomorrow, could we maintain source and binary compatibility throughout 1.x without regretting accidental API decisions?"*

To answer this question affirmatively, M14 executed an exhaustive audit, classification, contract hardening, and automated verification process across the entire repository:
1. **Public Inventory Audited**: All 345 `public` and `protected` types across four production modules were inventoried and categorized into `STABLE_API`, `STABLE_SPI`, `PUBLIC_BUT_INTERNAL_ACCIDENT`, and `EXPERIMENTAL`.
2. **8 Core Abstractions Hardened**: `TemplateEngine`, `Template`, `CompiledTemplate`, `TemplateRepository`, `RenderContext`, `TemplateOutput`, `Escaper`, and `MemberAccessPolicy` were formally specified with explicit lifecycle, thread-safety, resource ownership, nullability, and security contracts.
3. **Evaluation Semantics Preserved**: Fixed a critical defect in `RenderContext` and `DefaultMutableRenderContext` where `null` values collapsed into `UNDEFINED` (via `Map.copyOf` or `variables.remove`), restoring strict Velocity 3-state evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`).
4. **Lifecycle Formalized**: `TemplateEngine` now extends `AutoCloseable` with a no-op default `close()`, enabling safe management of long-lived engine instances and asynchronous background watchers without breaking backward compatibility.
5. **Fail-Closed Security Enforced**: Hardened security adapters (`MemberAccessPolicyVtlAdapter` and `MemberAccessPolicyLinkerAdapter`) to reject core dangerous reflection, classloading, process, and thread management types regardless of overly permissive third-party policies.
6. **Automated Compatibility Enforcement**: Established a machine-readable baseline (`config/api-baseline/1.0-public-api.txt` covering 80 stable types), an automated verification CLI (`scripts/verify-api-compatibility.py`), and CI workflow gating.
7. **Third-Party SPI & Consumer Smoke Testing**: Created dedicated SPI implementor fixtures and external consumer applications to verify usability, error handling, and boundary isolation.

---

## 2. STARTING STATE & M19.3b FREEZE CONFIRMATION

Prior to M14, the **M19.3b Streaming Output Allocation & UTF-8 Formatting** initiative completed four production improvements and an authoritative post-merge validation milestone (`docs/31-m19.3b3-1-post-merge-validation.md`), resulting in the permanent freeze of the performance optimization line:

| Sub-Milestone | Description | Status |
| :--- | :--- | :--- |
| **M19.3b.1** | Primitive decimal formatting (`NumberFormatting`) | **COMPLETE / FROZEN** |
| **M19.3b.2** | HTML escaping via range forwarding (`HtmlTextEscaper`) | **COMPLETE / FROZEN** |
| **M19.3b.2.1** | Range-write SPI hardening (`WriterTemplateOutput`) | **COMPLETE / FROZEN** |
| **M19.3b.3** | Bounded UTF-8 stream-buffer reuse (`Utf8BufferPool`) | **COMPLETE / FROZEN** |
| **M19.3b.3.1** | Authoritative post-merge validation | **COMPLETE / FROZEN** |

### M19.3b Freeze Invariant Preserved in M14
In accordance with user rules, Milestone M14 maintained the strict freeze on all M19.3b performance internals:
- Zero modifications to `TemplateCompileCache` recency sampling, lock striping, or drain thresholds.
- Zero modifications to `NumberFormatting.formatInt` and `formatLong`.
- Zero modifications to `HtmlTextEscaper` lookup algorithms or bitmask classifications.
- Zero modifications to `Utf8BufferPool` capacity (16), buffer sizes (8192 bytes), or lock-free array scanning.
- Zero modifications to `WriterTemplateOutput` range buffering logic.

---

## 3. PRODUCTION MODULE PUBLIC INVENTORY & CLASSIFICATION

A complete AST-level scan of all production modules identified **345 public/protected types**:

| Production Module | Total Types | `STABLE_API` | `STABLE_SPI` | `PUBLIC_BUT_INTERNAL_ACCIDENT` | `EXPERIMENTAL` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `viet-template-api` | 51 | 42 | 9 | 0 | 0 |
| `viet-template-runtime` | 34 | 16 | 5 | 13 | 0 |
| `viet-template-language-vtl` | 187 | 1 | 0 | 180 | 6 |
| `viet-template-vtl-interpreter` | 73 | 7 | 0 | 66 | 0 |
| **Total** | **345** | **66** | **14** | **259** | **6** |

### Classification Criteria

1. **`STABLE_API` (66 types)**:
   - Primary consumer-facing contracts designed for long-term binary and source compatibility throughout 1.x.
   - Includes `TemplateEngine`, `Template`, `CompiledTemplate`, `RenderContext`, `RenderRequest`, `ContributorContext`, `RenderBudget`, `TemplateId`, `SourceSpan`, `Diagnostic`, `DiagnosticCode`, `TemplateException` taxonomy, and runtime output buffers (`StringTemplateOutput`, `WriterTemplateOutput`, `Utf8OutputStreamTemplateOutput`).
2. **`STABLE_SPI` (14 types)**:
   - Extension contracts designed for third-party implementors and library integrators.
   - Includes `TemplateRepository`, `TemplateOutput`, `Escaper`, `MemberAccessPolicy`, `TemplateEngineProvider`, `RenderContextContributor`, and `LayoutResolver`.
3. **`PUBLIC_BUT_INTERNAL_ACCIDENT` (259 types)**:
   - Classes declared `public` solely because Java package boundaries required cross-package visibility across modules prior to JPMS modularization.
   - Includes `runtime.linker.*` (`DynamicLinker`, `DynamicCallSite`, `AccessLink`, `BoundedWeakClassCache`), `language.vtl.ast.*`, `ir.*`, `compiler.*`, and `interpreter.vtl.*` execution engine internals.
   - *Policy*: These classes are explicitly excluded from the 1.0 public compatibility contract. Consumers must not rely on them. In 1.0, they will be encapsulated via Java Platform Module System (`module-info.java`) exports or internal packages.
4. **`EXPERIMENTAL` (6 types)**:
   - Advanced APIs subject to evolution before 1.0 (e.g. `VtlParser`, `VtlParserOptions`, `VtlParserResult`, and experimental hot-reload watchers).

---

## 4. CORE ABSTRACTION STABILIZATION (THE 8 CORE INTERFACES)

### 4.1. `TemplateEngine`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_API` (Primary Facade)
- **Hardening Applied**:
  - Declared `public interface TemplateEngine extends AutoCloseable`.
  - Added default `void close() {}` method with no checked exceptions, enabling clean resource shutdown (such as stopping background file watchers) in try-with-resources blocks.
  - Added default `void invalidate(TemplateId id)` delegating to `invalidateWithDependents(id)`.
  - Added default `void invalidateAll() {}`.
  - Added high-level string convenience methods:
    - `default String render(String screenName, RenderContext context) throws IOException`
    - `default String render(TemplateId screenId, RenderContext context) throws IOException`
  - Fully documented concurrency and lifecycle guarantees in Javadoc: instances are thread-safe, immutable or internally synchronized, and intended to be shared as application singletons across thousands of concurrent threads.

### 4.2. `Template`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_API` (Executable Template Handle)
- **Hardening Applied**:
  - Added high-level convenience render method:
    - `default String render(RenderContext context) throws IOException`
  - Fully documented thread-safety: compiled `Template` instances are completely immutable and thread-safe. Multiple threads may execute `render(context, output)` concurrently on the same `Template` instance without synchronization.

### 4.3. `CompiledTemplate`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_API` (Compiled Representation Metadata)
- **Hardening Applied**:
  - Verified stability of metadata accessors: `id()`, `descriptor()`, `executionTier()`.
  - Enforced that compilation artifacts never leak classloaders or compiler internals into public signatures.

### 4.4. `TemplateRepository`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_SPI` (Resource Provider)
- **Hardening Applied**:
  - Fully documented resource confinement and exception semantics:
    - Implementations must throw `TemplateResourceException` (with code `TEMPLATE_NOT_FOUND`) when a requested template does not exist.
    - Implementations must reject path traversal attacks (`../`, null bytes) either via `TemplateId.normalize` or native filesystem confinement.
  - Validated static factories: `classpath()`, `filesystem()`, `composite()`, `inMemory()`.

### 4.5. `RenderContext`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_API` (Evaluation Context)
- **Hardening Applied**:
  - **Critical Bug Fix (Null Preservation)**: In `MapBackedRenderContext`, replaced `Map.copyOf(map)` with `Collections.unmodifiableMap(new LinkedHashMap<>(map))`. Previously, `Map.copyOf` threw an unconditional `NullPointerException` if any entry value was `null`, completely breaking templates that set or pass null variables.
  - **Factory Hardening**: Updated `RenderContext.of(String key, Object value)` to allow `value == null`, returning a map backed by `Collections.singletonMap(key, null)` instead of crashing in `Map.of()`.
  - **Builder Hardening**: Updated `RenderContext.Builder.put(key, value)` to allow `value == null`.
  - **Mutable Context Hardening**: In `DefaultMutableRenderContext`, introduced a `NULL_SENTINEL = new Object()` internal placeholder. When `put(key, null)` is called, the sentinel is stored instead of calling `variables.remove(key)`. This guarantees that `contains(key)` returns `true` and `get(key)` returns `null`, preserving Velocity 3-state evaluation semantics.

### 4.6. `TemplateOutput`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_SPI` & `STABLE_API` (Streaming Target)
- **Hardening Applied**:
  - Documented strict **thread-confinement**: `TemplateOutput` instances are render-scoped and **not** thread-safe for concurrent writes. Each render execution must use its own `TemplateOutput` instance.
  - Documented range-write indexing: `write(CharSequence csq, int start, int end)` uses standard Java half-open intervals `[start, end)`. Bounds must satisfy `0 <= start <= end <= csq.length()`.
  - Documented stream ownership: `TemplateOutput` does **not** close underlying `Writer` or `OutputStream` targets upon completion; resource ownership remains strictly with the caller.

### 4.7. `Escaper`
- **Package**: `io.github.minh124199.viettemplate.runtime`
- **Role**: `STABLE_SPI` (Contextual Output Encoding)
- **Hardening Applied**:
  - Documented SPI contract: implementations must be stateless and thread-safe.
  - Documented null handling: passing a `null` CharSequence or null object must result in a safe no-op without writing characters or throwing NPE.
  - Documented exception propagation: `IOException` from downstream `TemplateOutput` must be propagated immediately without suppression.

### 4.8. `MemberAccessPolicy`
- **Package**: `io.github.minh124199.viettemplate.api`
- **Role**: `STABLE_SPI` (Introspection Security Sandbox)
- **Hardening Applied**:
  - **Removed Accidental Interface Inheritance**: Removed `extends Serializable` from `MemberAccessPolicy`. Requiring serialization on an SPI interface burdened third-party implementors and introduced unnecessary attack surfaces.
  - **Fail-Closed Security Enforcement**: Hardened runtime adapters (`MemberAccessPolicyVtlAdapter` and `MemberAccessPolicyLinkerAdapter`) to unconditionally deny access to dangerous classes (`Class`, `ClassLoader`, `Runtime`, `Process`, `ProcessBuilder`, `Thread`, `ThreadGroup`, `MethodHandle`, `MethodHandles`, and packages `java.lang.reflect.*`, `java.lang.invoke.*`, `java.security.*`, `sun.*`, `jdk.internal.*`), even if a custom third-party policy returns `true`.

---

## 5. EVALUATION SEMANTICS & 3-STATE MATRIX

Velocity and Viet Template rely on a fundamental 3-state evaluation distinction:
1. `UNDEFINED`: The variable was never declared or placed in the context.
2. `DEFINED_NULL`: The variable was explicitly placed in the context with a value of `null`, or was assigned `null` via `#set($x = $nullVal)`.
3. `DEFINED_VALUE`: The variable exists and has a non-null Java object reference.

### 3-State Evaluation Matrix in Public APIs

| State | Context `contains(key)` | Context `get(key)` | Non-Strict Render | Strict Normal Render | Strict Quiet `$!ref` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`UNDEFINED`** | `false` | `null` | `"$key"` (verbatim) | `TemplateEvaluationException` | `TemplateEvaluationException` |
| **`DEFINED_NULL`** | `true` | `null` | `""` (empty string) | `TemplateEvaluationException` | `""` (empty string) |
| **`DEFINED_VALUE`** | `true` | `<value>` | `value.toString()` | `value.toString()` | `value.toString()` |

### The Defect and The Resolution
- **Defect Prior to M14**: Calling `RenderContext.of(Map.of("key", null))` failed immediately with NPE. Calling `MutableRenderContext.put("key", null)` executed `variables.remove("key")`, collapsing `DEFINED_NULL` into `UNDEFINED`.
- **Resolution in M14**:
  - `MapBackedRenderContext` stores nulls safely in an unmodifiable `LinkedHashMap`.
  - `DefaultMutableRenderContext` stores `NULL_SENTINEL` internally, so `containsKey` remains `true` and `asMap().get(key)` returns `null`.
  - Verified by `PublicApiContractTest`.

---

## 6. THREAD-SAFETY & MEMORY MODEL MATRIX

| Public / SPI Abstraction | Thread-Safety Guarantee | Confinement Model | Concurrency Mechanism |
| :--- | :--- | :--- | :--- |
| `TemplateEngine` | **Fully Thread-Safe** | Unconfined (Global Singleton) | Internal lock striping, immutable state, concurrent caches |
| `Template` | **Fully Thread-Safe** | Unconfined (Shared Handle) | Completely immutable compiled IR / bytecode |
| `CompiledTemplate` | **Fully Thread-Safe** | Unconfined (Shared Handle) | Immutable record / state |
| `TemplateRepository` | **Fully Thread-Safe** | Unconfined (Shared SPI) | Read-only classpath/filesystem or concurrent map |
| `RenderContext` | **Fully Thread-Safe** | Unconfined (Read-Only) | Unmodifiable copy of caller state |
| `MutableRenderContext` | **Single-Threaded** | Thread-Confined (Per-Render) | Thread-confined to single execution stack |
| `TemplateOutput` | **Single-Threaded** | Thread-Confined (Per-Render) | Render-scoped buffer; concurrent writes forbidden |
| `Escaper` | **Fully Thread-Safe** | Unconfined (Stateless) | Pure functions, zero mutable state |
| `MemberAccessPolicy` | **Fully Thread-Safe** | Unconfined (Shared SPI) | Stateless rule matching or immutable allowlist |
| `RenderBudget` | **Fully Thread-Safe** | Render-Scoped (Shared In Tree) | Atomic longs for monotonic limit enforcement |

---

## 7. AUTOMATED COMPATIBILITY ENFORCEMENT TOOLING

To ensure that no breaking change is accidentally introduced between M14 and 1.0+, an automated binary and source compatibility verification system was implemented:

### 7.1. Public Baseline Snapshot (`config/api-baseline/1.0-public-api.txt`)
- Machine-readable snapshot of all `public` and `protected` classes, interfaces, records, enums, methods, constructors, and fields across stable public packages.
- Captures **80 stable types** in production modules (`viet-template-api`, `viet-template-runtime`, and stable engine entrypoints).

### 7.2. Verification Script (`scripts/verify-api-compatibility.py`)
- Python 3 automated verification tool executing `javap -protected` against compiled classes.
- Validates:
  1. No stable types have been removed.
  2. No public or protected methods have been removed.
  3. No public or protected fields or constructors have been removed.
  4. Parameter types, return types, and generic signatures have not changed incompatibly.
  5. No new abstract methods have been added to existing interfaces (preventing source breakage for third-party implementors).

### 7.3. CI Workflow Integration (`.github/workflows/ci.yml`)
- Added dedicated `api-compatibility` job running on every pull request and push to `main`.
- Compiles classes and executes `scripts/verify-api-compatibility.py`, failing CI immediately if any binary or source incompatibility is detected.

---

## 8. DEDICATED CONTRACT TEST SUITES & TEST FIXTURES

Milestone M14 introduced 6 dedicated contract test suites verifying external consumer and third-party implementor experiences:

1. **`PublicApiContractTest` (`viet-template-api`)**:
   - Tests `RenderContext` null preservation and 3-state evaluation.
   - Tests `MutableRenderContext` null preservation and protected key security.
   - Tests `TemplateId` normalization, validation, and traversal rejection.
   - Tests `SourceSpan`, `Diagnostic`, `DiagnosticCode`, and `TemplateException` taxonomy.
2. **`TemplateOutputSpiContractTest` (`viet-template-runtime`)**:
   - Implements a minimal custom `TemplateOutput` implementing only fundamental methods.
   - Verifies default range-write implementations, half-open interval indexing `[start, end)`, bounds checking, and null handling.
3. **`TemplateEngineContractTest` (`viet-template-vtl-interpreter`)**:
   - Verifies concurrent multi-threaded execution across 16 worker threads sharing a single `TemplateEngine`.
   - Tests `engine.render(...)` and `template.render(...)` string conveniences.
   - Tests `engine.close()`, `engine.invalidate(...)`, and `engine.invalidateAll()`.
4. **`TemplateRepositorySpiContractTest` (`viet-template-vtl-interpreter`)**:
   - Implements a custom third-party `TemplateRepository` using only public API interfaces.
   - Verifies custom template loading, engine compilation, and error handling (`TemplateResourceException`).
5. **`SecuritySpiContractTest` (`viet-template-vtl-interpreter`)**:
   - Implements an insecure third-party `MemberAccessPolicy` that attempts to allow access to `java.lang.Class` and `java.lang.Runtime`.
   - Verifies that the engine fails closed, completely blocking access to reflection and runtime operations regardless of the custom policy.
6. **`ApiConsumerSmokeTest` (`viet-template-vtl-interpreter`)**:
   - Simulates a downstream consumer application importing only public API and runtime classes.
   - Builds engine, registers repository, populates context with complex records, renders to string and streaming output, and exercises custom escapers.
7. **`ArchitectureRulesTest` (`viet-template-tck`)**:
   - Added ArchRule `api_must_not_depend_on_runtime_or_interpreter` ensuring `viet-template-api` has zero compile-time dependencies on runtime, parser, or interpreter modules.

---

## 9. VERIFICATION RESULTS ACROSS TOOLCHAINS & ENVIRONMENTS

### 9.1. Java Bytecode Baseline Verification
- Command: `javap -v viet-template-api/build/classes/java/main/io/github/minh124199/viettemplate/api/TemplateEngine.class | grep "major version"`
- Result: **`major version: 61`** (Java 17 bytecode compliance confirmed).

### 9.2. Gradle Build Verification
- Command: `JAVA_HOME=.../java-17-openjdk ./gradlew clean check --no-daemon -Dspotless.check.skip=true`
- Result: **`BUILD SUCCESSFUL in 2m 26s`** (29 actionable tasks executed, 100% test pass rate across all modules).

### 9.3. Maven Build Verification
- Command: `JAVA_HOME=.../java-17-openjdk ./mvnw clean verify -B -Dspotless.check.skip=true`
- Result: **`BUILD SUCCESS in 02:01 min`** (All 7 modules compiled and verified cleanly).

### 9.4. Spotless Code Formatting Verification
- Commands:
  - `JAVA_HOME=.../jdk-21 ./gradlew spotlessCheck --no-daemon` -> **`BUILD SUCCESSFUL`**
  - `JAVA_HOME=.../jdk-21 ./mvnw spotless:check -B` -> **`BUILD SUCCESS`**

### 9.5. Build Parity Verification
- Command: `./scripts/verify-build-parity.sh`
- Result: **`[SUCCESS] Build parity verification PASSED! Gradle and Maven builds are fully aligned.`**
  - Verified identical module sets, group ID, version, compilation flags, dependencies, and identical class/resource contents across all 4 production JARs.

### 9.6. Public API Compatibility Verification
- Command: `python3 scripts/verify-api-compatibility.py`
- Result: **`API COMPATIBILITY CHECK PASSED: 0 breaking changes detected.`** (80 types verified).

### 9.7. Release Infrastructure Verification
- Command: `python3 scripts/verify-release-metadata.py --check-workflow-contract && python3 -m unittest discover -s scripts/tests -v`
- Result: **`20 tests executed in 0.670s, OK.`**

---

## 10. IMPACT ON DOWNSTREAM ROADMAP (MILESTONE M15 NEXT)

With Milestone M14 complete, the public API and SPI surfaces of Viet Template are stable, documented, and protected by automated regression tooling.

### Status of the 1.0 Critical Path
1. **M14 (Public API & SPI Stabilization)**: **COMPLETE**
2. **M15 (Maven & Gradle AOT Tooling)**: **NEXT / UNBLOCKED**
   - Development of `viet-template-maven-plugin` and `viet-template-gradle-plugin` can now proceed against stable, finalized public compiler and repository interfaces.
3. **M16 & M17 (Spring Framework 6 & Spring Boot 3 Integration)**: **UNBLOCKED**
   - Spring integration modules can safely implement `View` and `ViewResolver` against `TemplateEngine`, `Template`, and `RenderContext`.
4. **M18 (Independent Public TCK & Benchmark Release Gates)**: **PLANNED**

---

## 11. FINAL DECISION

```text
================================================================================
FINAL MILESTONE DECISION:
A. M14 COMPLETE — PUBLIC API/SPI STABILIZED
================================================================================
```
The public API and SPI of Viet Template are formally stabilized for the 1.0 release series. Source and binary compatibility are backed by machine-readable baselines and automated CI enforcement. All verification gates have passed with 100% success across dual Gradle and Maven toolchains.

---

## 12. PUBLIC SURFACE TAXONOMY (Updated M3.5)

The following taxonomy defines all recognized classifications in `config/api-baseline/public-surface-classification.txt`. Updated in M3.5 to add `INTERNAL_CROSS_PACKAGE` and `BENCHMARK_SUPPORT_INTERNAL`, and to precisely distinguish Java visibility from JPMS export control.

### 12.1 Complete Category Table

| Category | Intended Consumer | Binary Compat | Source Compat | Why Public |
|---|---|---|---|---|
| `STABLE_API` | All users | Guaranteed pre-1.0 | Guaranteed pre-1.0 | Intentional user-facing API |
| `STABLE_SPI` | Extension implementors | Guaranteed pre-1.0 | Best-effort | Intentional extension contract |
| `EXPERIMENTAL` | Early adopters | Not guaranteed | Not guaranteed | Feature preview |
| `GENERATED_RUNTIME_ABI` | Generated bytecode only | Not guaranteed | N/A | Bytecode `invokestatic` — see ADR-0016 |
| `FRAMEWORK_ENTRYPOINT` | Framework infrastructure | Not guaranteed | Not guaranteed | Framework reflection/CDI discovery |
| `BUILD_TOOL_ENTRYPOINT` | Maven/Gradle tooling | Not guaranteed | Not guaranteed | Build tool execution/plugin loading |
| `SERVICE_ENTRYPOINT` | ServiceLoader | Not guaranteed | FQCN stable | `ServiceLoader.load()` |
| `INTERNAL_CROSS_MODULE` | Viet Template production modules only | Not guaranteed pre-1.0 | Not guaranteed | Cross-artifact Java visibility |
| `INTERNAL_CROSS_PACKAGE` | Same published artifact only | Not guaranteed pre-1.0 | Not guaranteed | Cross-package intra-artifact Java visibility |
| `BENCHMARK_SUPPORT_INTERNAL` | JMH benchmark suite only | Not guaranteed | Not guaranteed | Benchmark slot-level measurement access |
| `PUBLIC_BUT_INTERNAL_ACCIDENT` | None (tracked debt) | Not guaranteed | Not guaranteed | Historical or topology constraint |

### 12.2 Java Visibility vs JPMS Export Control

These are four distinct concepts that must not be conflated:

| Concept | What It Means |
|---|---|
| `JAVA_VISIBILITY` | The `public`/`package-private`/`protected`/`private` modifier on a Java type |
| `PACKAGE_NAMING` | Whether a package name communicates internal status (`*.internal.*`) — convention only |
| `JPMS_EXPORT_VISIBILITY` | Which named modules can `requires` a public package in a named module |
| `SUPPORTED_API_STATUS` | Whether a type is intentionally part of the documented, supported public API |

**Key precision**: JPMS `qualified exports` control which *named modules* can access a public
package. They do **not** turn `public class Foo` into `class Foo`. A `public` type in an
unexported JPMS package remains `public` in the Java bytecode and remains accessible on the
classpath (unnamed module). For all code running on the classpath (i.e., all non-JPMS users),
`public` visibility is the only access control mechanism that matters.

### 12.3 JPMS Capability Matrix

| Problem | Package-private | Internal package naming | JPMS qualified export | Module redesign |
|---|---|---|---|---|
| Prevent external source import (classpath) | **Strong** | Convention only | No | Depends |
| Prevent classpath reflection access | **Yes** | No | No | Depends |
| Permit cross-package access within artifact | No | Yes (requires `public`) | No | No |
| Control which named modules can see a public package | No | No | **Yes** | Depends |
| Reduce public bytecode type count | **Yes** | No | No | Sometimes |
| Encapsulate cross-module internals on module path | No | No | **Yes** | Yes |

### 12.4 JPMS Decision: DEFER

**Decision: DEFERRED** — do not introduce `module-info.java` at this stage.

**Rationale:**
- The 55 `INTERNAL_CROSS_PACKAGE` types need cross-package intra-artifact visibility. JPMS is
  irrelevant — they require `public` due to Java package boundaries, not module boundaries.
- The 85 `PUBLIC_BUT_INTERNAL_ACCIDENT` types (AST/IR concrete nodes, semantics types) are
  public because their sealed root interfaces are cross-module contracts. JPMS without a module
  merge does not help.
- Framework compat (Spring, Quarkus) on named module path has not been validated.
- ServiceLoader, GraalVM, and dual classpath/module-path deployment have not been verified.

**Future evaluation**: Re-evaluate JPMS as part of M22 when the `language-vtl` / `vtl-interpreter`
module boundary question is resolved. At that point, JPMS could provide real encapsulation benefit.

### 12.5 INTERNAL_CROSS_PACKAGE vs PUBLIC_BUT_INTERNAL_ACCIDENT

The distinction made in M3.5:

| Criterion | `INTERNAL_CROSS_PACKAGE` | `PUBLIC_BUT_INTERNAL_ACCIDENT` |
|---|---|---|
| Package structure intentional? | Yes — deliberate subsystem split | No or Unknown |
| Cross-package callers same artifact? | Yes | May have cross-module issue |
| Remediation requires module merge? | No — package consolidation can help | Often Yes |
| Tracked in debt registry? | No | Yes |
| Example | `vtl.internal.compiler.BackendId` | `language.vtl.ast.VtlTemplate` |
