# Milestone M14.1: Public Surface Containment + Lifecycle Finalization Report

- **Document Version**: `1.0.0`
- **Protocol**: `VT-API-M14.1-PUBLIC-CONTAINMENT-1`
- **Date**: 2026-09-14
- **Authoritative Baseline Commit**: `a55b603180b5a2e35f0b09c1032e123966f89553`
- **Branch**: `release/m14-1-public-surface-containment`
- **Final Milestone Decision**: **`B. M14.1 COMPLETE — M15 REQUIRES A NARROW BUILD-TIME FACADE`**
- **Impacted Production Modules**:
  - `viet-template-api` (Javadoc lifecycle and AutoCloseable contract hardening)
  - `viet-template-runtime` (visibility reductions in linker; Javadoc lifecycle and thread-confinement hardening)
  - `viet-template-language-vtl` (visibility reductions in parser and semantics)
  - `viet-template-vtl-interpreter` (public inventory categorization)
- **Impacted Verification Modules**:
  - `viet-template-runtime` (new contract suite `ConcreteOutputLifecycleContractTest`)
  - `viet-template-tck` (new ArchRule `integration_and_tooling_boundary_must_not_access_internal_packages`)
- **Containment & Compatibility Tooling**:
  - `config/api-baseline/public-surface-classification.txt` (exact 341-type deterministic classification baseline)
  - `scripts/generate-public-surface.py` (fast batch javap public surface generator)
  - `scripts/verify-public-surface-classification.py` (4-check automated CI verification tool)
  - `.github/workflows/ci.yml` (automated CI execution of surface containment verification)
- **Java Target & Bytecode Baseline**: Java 17 strictly frozen (`--release 17`, classfile major version 61).

---

## 1. STARTING STATE & GOVERNING QUESTION

Milestone M14 established the initial public API and SPI baseline (`1.0-public-api.txt`) encompassing 80 core types and validated them against automated regression tooling. However, an exhaustive audit across all four production modules revealed that an additional 265 types were declared with `public` or `protected` visibility solely due to historical cross-package visibility requirements within flat JARs prior to Java Platform Module System (JPMS) modularization.

Milestone **M14.1: Public Surface Containment + Lifecycle Finalization** was chartered to address the governing question:
> *"How do we guarantee that every single compiled public and protected type in the production modules is strictly classified, that no accidental internal implementation classes leak into consumer-facing signatures, that concrete output stream lifecycles are unambiguous, and that downstream build tooling in M15 has a safe architectural boundary?"*

To resolve this comprehensively, Milestone M14.1 executed a six-part containment plan:
1. **Safe Visibility Reductions**: Lowered visibility on public internal types that were never referenced outside their defining package or class.
2. **Complete 4-Category Inventory & Classification**: Categorized every compiled public/protected type (exactly 341 types) into `STABLE_API`, `STABLE_SPI`, `EXPERIMENTAL`, or `PUBLIC_BUT_INTERNAL_ACCIDENT`. Exactly zero types remain unclassified.
3. **Automated CI Enforcement**: Implemented `scripts/verify-public-surface-classification.py` executing 4 automated verification checks on CI:
   - Check 1: Zero unclassified public types.
   - Check 2: Zero stale classified types.
   - Check 3: Strict baseline parity with `1.0-public-api.txt`.
   - Check 4: Automated signature leak scanning ensuring no `STABLE_API` or `STABLE_SPI` signature leaks internal or experimental types.
4. **Integration & Tooling Boundary Rule**: Added ArchUnit rule `integration_and_tooling_boundary_must_not_access_internal_packages` in `viet-template-tck`.
5. **Output Stream Lifecycle & Resource Hardening**: Added comprehensive class-level and method-level contracts to `Utf8OutputStreamTemplateOutput`, `WriterTemplateOutput`, `StringTemplateOutput`, and `TemplateEngine`, backed by dedicated test suite `ConcreteOutputLifecycleContractTest`.
6. **M15 AOT Facade Readiness Audit**: Evaluated build-time template compilation requirements, identifying why internal compiler classes cannot be directly consumed by Maven and Gradle plugins and mandating a narrow build-time compiler facade in M15.

---

## 2. PRODUCTION MODULE PUBLIC INVENTORY & 4-CATEGORY CLASSIFICATION

A full AST and bytecode scan across all four production modules identified **341 public/protected types** (post-visibility reductions):

| Production Module | Total Public Types | `STABLE_API` | `STABLE_SPI` | `EXPERIMENTAL` | `PUBLIC_BUT_INTERNAL_ACCIDENT` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `viet-template-api` | 51 | 42 | 9 | 0 | 0 |
| `viet-template-runtime` | 32 | 16 | 5 | 0 | 11 |
| `viet-template-language-vtl` | 185 | 1 | 0 | 6 | 178 |
| `viet-template-vtl-interpreter` | 73 | 7 | 0 | 0 | 66 |
| **Total** | **341** | **66** | **14** | **6** | **255** |

### Category Definitions

1. **`STABLE_API` (66 types)**:
   - Long-term consumer-facing contracts guaranteed for binary and source compatibility throughout 1.x.
   - Includes engine interfaces (`TemplateEngine`, `Template`, `CompiledTemplate`), evaluation context (`RenderContext`, `RenderRequest`, `ContributorContext`, `RenderBudget`), descriptors, IDs, exceptions, and concrete output implementations (`StringTemplateOutput`, `WriterTemplateOutput`, `Utf8OutputStreamTemplateOutput`).
2. **`STABLE_SPI` (14 types)**:
   - Extensibility interfaces and service-provider contracts intended for third-party integrators and framework authors.
   - Includes:
     - `TemplateRepository`, `TemplateOutput`, `TemplateEngineProvider`, `RenderContextContributor`, `ContributorContext`, `LayoutResolver`, `LayoutRenderPlan`, `MemberAccessPolicy`, `MemberAccessPolicy.Builder` (in `api`).
     - `Escaper`, `EscapeMode`, `EscaperRegistry`, `SafeContent`, `StandardEscapers` (in `runtime`).
3. **`EXPERIMENTAL` (6 types)**:
   - Advanced language parsing and AST frontend APIs under active evaluation that may evolve prior to 1.0 finalization:
     - `VtlFeatureState`, `VtlFrontend`, `VtlProfile`, `VtlParseResult`, `VtlParser`, `VtlParserOptions`.
4. **`PUBLIC_BUT_INTERNAL_ACCIDENT` (255 types)**:
   - Internal execution machinery declared `public` solely for cross-package accessibility prior to JPMS modularization.
   - Includes runtime dynamic linker internals (`runtime.linker.*`), AST node definitions (`language.vtl.ast.*`), IR statements and passes (`language.vtl.ir.*`), compiler bytecode generators (`vtl.compiler.*`), and interpreter runtime structures (`vtl.interpreter.*`).
   - *Compatibility Contract*: These types are explicitly excluded from 1.0 public compatibility. Downstream application and framework consumers must never depend on them directly.

---

## 3. SAFE VISIBILITY REDUCTIONS

Milestone M14.1 reviewed candidate classes for immediate visibility reduction without breaking public baseline contracts or cross-package runtime dependencies:

| Class / Member | Former Visibility | Reduced Visibility | Module | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| `CallSiteRegistry.CallSiteKey` | `public record` | `private record` | `viet-template-runtime` | Internal hash map cache key used exclusively inside `CallSiteRegistry`. Zero external callers. |
| `AccessLink.Status` | `public enum` | `package-private enum` | `viet-template-runtime` | Resolution status enum inspected only via package methods; callers interact through boolean predicates (`isOk()`, `isDenied()`, `isMissing()`). |
| `AccessLink.status()` | `public Status` | `package-private Status` | `viet-template-runtime` | Accessor for package-private enum status; no external call sites. |
| `VtlAstDumper` | `public final class` | `package-private final class` | `viet-template-language-vtl` | Debugging AST tree formatter called exclusively from `VtlParseResult.dumpAst()` in the same package. |
| `VtlSemanticDiagnosticCodes` | `public final class` | `package-private final class` | `viet-template-language-vtl` | Semantic error code constants referenced only by `VtlSemanticAnalyzer` within the `semantics` package. |

These reductions safely eliminated 4 types from accidental public exposure, reducing the unmodularized internal surface from 259 to 255 types.

---

## 4. SIGNATURE LEAK AUDIT RESULTS

To ensure that internal or experimental types do not contaminate stable contracts, an automated signature leak analyzer was developed in `scripts/verify-public-surface-classification.py`.

### Audit Methodology
- Extracted all public and protected signatures (class headers, superclasses, interfaces, constructors, methods, parameters, return types, field types, and thrown exceptions) across all 72 `STABLE_API` and `STABLE_SPI` types in `viet-template-api` and `viet-template-runtime`.
- Evaluated every signature token against the full set of 261 types classified as `PUBLIC_BUT_INTERNAL_ACCIDENT` (255) or `EXPERIMENTAL` (6).

### Audit Result
- **Classes Audited**: 72
- **Signatures Evaluated**: 584 public/protected constructors and methods
- **Detected Signature Leaks**: **0**
- **Conclusion**: The public API and SPI surfaces of `viet-template-api` and `viet-template-runtime` are strictly sealed at the type signature level. No internal compiler classes, AST representations, IR structures, or linker internals leak into consumer-facing signatures.

---

## 5. INTEGRATION BOUNDARY ARCHITECTURE RULE

To permanently guard integration modules (such as future Spring Framework integration in M16/M17) and build tooling modules (such as Maven and Gradle plugins in M15) against coupling to internal implementation details, a new ArchUnit rule was added to `viet-template-tck`:

```java
@ArchTest
public static final ArchRule integration_and_tooling_boundary_must_not_access_internal_packages =
    noClasses()
        .that()
        .resideInAnyPackage("..viettemplate.integration..", "..viettemplate.tooling..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..viettemplate.runtime.linker..",
            "..viettemplate.language.vtl.ast..",
            "..viettemplate.language.vtl.ir..",
            "..viettemplate.vtl.compiler..",
            "..viettemplate.vtl.interpreter..")
        .allowEmptyShould(true);
```

### Verification
- Executed via `./gradlew :viet-template-tck:test`.
- Passed with 100% success.
- Configured with `allowEmptyShould(true)` so that the architectural invariant is enforced immediately upon creation of any class residing in `integration` or `tooling` packages.

---

## 6. M15 AOT FACADE READINESS AUDIT

Milestone M15 will introduce build-time Ahead-Of-Time (AOT) template compilation plugins for Apache Maven (`viet-template-maven-plugin`) and Gradle (`viet-template-gradle-plugin`). An architectural readiness audit was conducted during M14.1 to determine how build tooling should interact with the template compiler.

### The Problem: Internal Compiler Coupling
Currently, template bytecode generation is handled by `BytecodeTemplateCompiler` in package `io.github.minh124199.viettemplate.vtl.compiler`. This compiler relies heavily on internal structures:
- Directly accepts `IrTemplate` from the VTL language IR pipeline (`viet-template-language-vtl`).
- Directly constructs `BytecodeCompilationPlan` and configures ASM `ClassWriter` flags.
- Returns internal `BytecodeCompilationResult` objects exposing raw bytecode arrays and internal diagnostics.

All classes in `io.github.minh124199.viettemplate.vtl.compiler` are classified as `PUBLIC_BUT_INTERNAL_ACCIDENT`. If M15 plugins were implemented by directly importing these classes, the build tooling would violate the integration boundary architecture rule and become tightly coupled to volatile compiler internals.

### The Requirement: Narrow Build-Time Compiler Facade in M15
To ensure clean public surface containment, Milestone M15 must introduce a **narrow build-time compiler facade** (e.g. `BuildTemplateCompiler` or `TemplateAotCompiler` within a dedicated tooling API):
1. **Inputs**: Source template paths/repositories, model type metadata (for typed templates), target output directory, class naming strategy, and compiler options (e.g., target bytecode version, profile, fail-on-warning).
2. **Outputs**: A structured compilation report providing generated class names, output `.class` files, source-to-bytecode mappings, and public `Diagnostic` objects.
3. **Encapsulation**: The facade completely encapsulates IR generation, optimization passes, and ASM byte manipulation. Build plugins only interact with stable facade parameters and standard Java file/path abstractions.

---

## 7. CONCRETE OUTPUT LIFECYCLE CONTRACTS & MATRICES

Milestone M14.1 fully hardened and documented the lifecycle, thread-confinement, and resource ownership semantics of all concrete output streams:

### 7.1. Lifecycle & Concurrency Matrix

| Component | Thread Safety | Scope | `close()` Ownership Semantics | `flush()` Semantics | Double Close | Post-Close Writes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`Utf8OutputStreamTemplateOutput`** | Single-threaded (Not thread-safe) | Render-scoped | Flushes buffer, returns pooled buffer to `Utf8BufferPool`, **closes wrapped `OutputStream`** | Flushes buffer and calls `out.flush()`; keeps stream open and buffer held | Idempotent safe no-op | Throws `IOException("Output is closed")` |
| **`WriterTemplateOutput`** | Single-threaded (Not thread-safe) | Render-scoped | Not `AutoCloseable`; **caller retains full ownership of wrapped `Writer`** | Calls `writer.flush()` | N/A (Not AutoCloseable) | Controlled by underlying `Writer` |
| **`StringTemplateOutput`** | Single-threaded (Not thread-safe) | Render-scoped | Not `AutoCloseable`; purely in-memory (`StringBuilder`) | Safe no-op | N/A (Not AutoCloseable) | Permitted (or reusable via `reset()`) |
| **`TemplateEngine`** | Fully thread-safe (Multi-threaded) | Long-lived Singleton | Default no-op; shuts down background watchers, executor pools, and shared buffer pools | N/A | Idempotent safe no-op | Implementation-defined (throws `IllegalStateException`) |

### 7.2. Container-Managed / Servlet Response Streams Pattern
When rendering templates directly to container-managed output streams (such as `HttpServletResponse.getOutputStream()`), container lifecycle rules require that the stream not be prematurely closed by template rendering. Callers must wrap the container stream in a non-closing delegate:

```java
OutputStream nonClosingStream = new FilterOutputStream(response.getOutputStream()) {
    @Override
    public void close() throws IOException {
        flush(); // Flush buffered bytes but leave container stream open
    }
};

try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(nonClosingStream)) {
    engine.render(request, out);
}
```

### 7.3. Dedicated Verification Suite
Created `viet-template-runtime/src/test/java/io/github/minh124199/viettemplate/runtime/ConcreteOutputLifecycleContractTest.java` covering 9 comprehensive scenarios:
- `utf8Output_close_flushesAndClosesUnderlyingStream`: Proves pending buffer is flushed and stream closed.
- `utf8Output_close_releasesPooledBuffer`: Proves checkout from and return to `Utf8BufferPool`.
- `utf8Output_flush_flushesWithoutClosingStreamOrReleasingBuffer`: Proves partial flush retains stream and buffer.
- `utf8Output_doubleClose_isIdempotent`: Proves second close does not re-close stream or double-release buffer.
- `utf8Output_writeAfterClose_throwsIOException`: Proves writes and flushes after close throw `IOException("Output is closed")`.
- `utf8Output_nonClosingOutputStreamPattern_leavesUnderlyingOpen`: Proves container stream preservation pattern.
- `writerOutput_callerRetainsOwnershipOfWriter`: Proves non-closing lifecycle for writers.
- `writerOutput_flush_delegatesToWriter`: Proves flush delegation.
- `stringOutput_inMemoryLifecycleAndReset`: Proves memory reuse and reset semantics.

---

## 8. AUTOMATED COMPATIBILITY & CONTAINMENT TOOLING

Milestone M14.1 introduced a second automated baseline and verification script to complement `verify-api-compatibility.py`:

### 8.1. Baseline Classification Snapshot (`config/api-baseline/public-surface-classification.txt`)
Deterministic, alphabetically sorted classification of all 341 public/protected types across four production modules:
- 66 `STABLE_API`
- 14 `STABLE_SPI`
- 6 `EXPERIMENTAL`
- 255 `PUBLIC_BUT_INTERNAL_ACCIDENT`

### 8.2. Verification Tool (`scripts/verify-public-surface-classification.py`)
Executes 4 automated verification checks:
1. `Check 1`: 0 unclassified public types (compares compiled classes against classification baseline).
2. `Check 2`: 0 stale classified types (verifies all entries correspond to existing compiled classes).
3. `Check 3`: Baseline parity with `config/api-baseline/1.0-public-api.txt` (all 80 baseline types must be `STABLE_API` or `STABLE_SPI`).
4. `Check 4`: Signature leak detection (ensures no stable public or SPI signatures reference internal or experimental types).

### 8.3. CI Integration (`.github/workflows/ci.yml`)
Integrated into the `api-compatibility` job:
```yaml
      - name: Run Public API Compatibility Verification
        run: python3 scripts/verify-api-compatibility.py

      - name: Run Public Surface Classification Verification
        run: python3 scripts/verify-public-surface-classification.py
```

---

## 9. VERIFICATION RESULTS ACROSS TOOLCHAINS & GATES

| Gate / Check | Command | Status | Result |
| :--- | :--- | :---: | :--- |
| **Public Surface Classification** | `python3 scripts/verify-public-surface-classification.py` | **PASS** | 4/4 checks passed; 341 types verified (0 unclassified, 0 stale, 0 signature leaks). |
| **API Compatibility Baseline** | `python3 scripts/verify-api-compatibility.py` | **PASS** | 80 types verified; 0 breaking changes detected. |
| **Release Metadata Contract** | `python3 scripts/verify-release-metadata.py --check-workflow-contract` | **PASS** | Workflow and publishing contracts fully valid. |
| **Release Infrastructure Tests** | `python3 -m unittest discover -s scripts/tests -v` | **PASS** | 20/20 unit tests passed (0.67s). |
| **Gradle Test Suite** | `JAVA_HOME=.../java-17-openjdk ./gradlew test --no-daemon` | **PASS** | 100% test pass rate across all modules including new lifecycle and ArchUnit tests. |
| **Spotless Formatting** | `JAVA_HOME=.../jdk-21 ./gradlew spotlessCheck --no-daemon` | **PASS** | Formatted cleanly with google-java-format. |
| **Gradle Full Check** | `JAVA_HOME=.../java-17-openjdk ./gradlew clean check --no-daemon` | **PASS** | Clean build and check passed. |
| **Maven Full Verify** | `JAVA_HOME=.../java-17-openjdk ./mvnw clean verify -B` | **PASS** | Clean build and verification across all 7 POMs passed. |
| **Build Parity** | `./scripts/verify-build-parity.sh` | **PASS** | Gradle and Maven outputs match with 100% parity. |

---

## 10. FINAL DECISION

```text
================================================================================
FINAL MILESTONE DECISION:
B. M14.1 COMPLETE — M15 REQUIRES A NARROW BUILD-TIME FACADE
================================================================================
```

### Justification
1. **Public Surface Strictly Contained**: All 341 public and protected types in production modules are classified into a machine-readable baseline, with 0 unclassified types, 0 stale types, and 0 signature leaks.
2. **Safe Visibility Reduced**: 4 unneeded public classes/members were reduced to private or package-private visibility.
3. **Stream Lifecycle Finalized**: Explicit thread-confinement, ownership on close, flush semantics, and double-close idempotency contracts are fully specified in Javadoc and enforced by 9 dedicated contract tests.
4. **Architectural Guard Established**: The integration and tooling boundary ArchUnit rule prohibits downstream integration modules from accessing internal compiler, AST, IR, or linker packages.
5. **M15 Facade Requirement Defined**: The AOT readiness audit proves that build plugins cannot directly import current compiler internals without violating architectural boundaries; Milestone M15 must introduce a narrow build-time compiler facade (`TemplateAotCompiler`) to decouple build tools from internal compiler machinery.
