# Milestone M15 — Maven & Gradle Ahead-Of-Time (AOT) Tooling

## 1. Executive Summary

- **Milestone**: M15 — Maven & Gradle AOT Tooling
- **Status**: **COMPLETE**
- **Decision**: **`A. M15 COMPLETE — MAVEN & GRADLE AOT TOOLING READY`**
- **Target Java Baseline**: Java 17 (`--release 17`, classfile major version 61)
- **Primary Deliverables**:
  1. **Narrow Public AOT Facade**: `io.github.minh124199.viettemplate.aot` (`TemplateAotCompiler`, `TemplateAotRequest`, `TemplateAotResult`, `TemplateAotDiagnostic`, `TemplateAotArtifact`).
  2. **Bytecode Self-Containment**: Class initializer `<clinit>` bytecode emission for `UTF8_CHUNKS` and `SITES` in `BytecodeTemplateCompiler`, enabling zero-reflection, standalone ClassLoader execution.
  3. **Runtime AOT Discovery**: `VtlTemplateEngine` discovery of `META-INF/viet-template/templates.idx` resources from ClassLoaders, executing precompiled templates with zero runtime compilation (`rejectRuntimeCompilation(true)`).
  4. **Apache Maven Plugin**: `viet-template-maven-plugin` (`VietTemplateCompileMojo`, goal `compile`, lifecycle phase `process-classes`).
  5. **Gradle Plugin**: `viet-template-gradle-plugin` (`VietTemplatePlugin`, `VietTemplateCompileTask` `@CacheableTask`, `VietTemplateExtension`, id `io.github.minh124199.viet-template`).
  6. **Dual-Build Parity & Fixtures**: Black-box consumer fixtures (`maven-basic`, `gradle-basic`, `maven-failure`, `gradle-failure`) and verification script (`scripts/verify-aot-tooling-parity.sh`) proving 100% byte-for-byte identical bytecode and index parity.

---

## 2. Architectural Motivation & M14.1 Boundary Compliance

### 2.1 The Problem
In Milestone M14.1, strict architectural boundaries were established. ArchUnit rule `integration_and_tooling_boundary_must_not_access_internal_packages` forbids build tooling and integration modules from accessing internal compiler packages (`..runtime.linker..`, `..language.vtl.ast..`, `..language.vtl.ir..`, `..vtl.compiler..`, `..vtl.interpreter..`).

Furthermore, public surface verification requires zero unclassified types and zero signature leaks. Build plugins could not directly consume:
- `IrTemplate` or internal AST nodes
- Parser and compiler implementation classes
- ASM bytecode generators
- `BytecodeCompilationResult`
- Dynamic linker implementation classes

### 2.2 Solution: Narrow Public AOT Facade
To satisfy these invariants, Milestone M15 introduces a minimal, stable public facade in `viet-template-vtl-interpreter`:
- **`TemplateAotCompiler`**: Public interface with static factory `create()` and single execution method `compile(TemplateAotRequest)`.
- **`TemplateAotRequest`**: Immutable request with fluent Builder controlling source directories, output directory, resource directory, include/exclude globs, encoding, package prefix, incremental mode, and failure thresholds. Validates against path traversal.
- **`TemplateAotResult`**: Immutable compilation result exposing success status, counts (`compiledCount`, `skippedCount`, `deletedCount`), generated artifacts, and diagnostics.
- **`TemplateAotDiagnostic`**: Standardized diagnostic representation (`sourcePath`, line, column, severity, error code, formatted message).
- **`TemplateAotArtifact`**: Metadata record for compiled classfiles (`templateId`, FQCN, output path, SHA-256 fingerprint).
- **`DefaultTemplateAotCompiler`**: Package-private implementation encapsulating `VtlParser`, `VtlSemanticAnalyzer`, `AstToIrLowerer`, `BytecodeTemplateCompiler`, incremental SHA-256 caching, stale class deletion, and `templates.idx` generation.

Both `viet-template-maven-plugin` and `viet-template-gradle-plugin` act as thin adapters over this facade, importing zero internal classes.

---

## 3. Bytecode Self-Containment & Runtime Discovery

### 3.1 Self-Contained Bytecode Generation
Previously, `BytecodeTemplateCompiler` required in-memory reflection via `initializeClassFields(...)` to inject constant tables (`UTF8_CHUNKS`, `SITES`, `TEMPLATE_ID`) into generated classes before execution. In an AOT context, classes are loaded directly by standard JVM ClassLoaders where reflective injection is impossible.

In M15, `BytecodeTemplateCompiler` was enhanced to emit a complete JVM `<clinit>` method in bytecode:
- **UTF-8 Chunks**: Bytecode allocates `byte[][] UTF8_CHUNKS` array, emits string constants, and invokes `BytecodeRuntimeBridge.toUtf8Bytes(String)` to populate chunk slices.
- **Dynamic Call Sites**: Bytecode allocates `DynamicCallSite[] SITES` array and populates each call site via `BytecodeRuntimeBridge.createCallSite(int, String, int, int, LinkerAccessPolicy)` with default standard policy.
- **Template ID**: Sets `public static final String TEMPLATE_ID`.

The resulting `.class` files are 100% self-contained, valid Java 17 bytecode (classfile version 61), requiring no external setup before execution.

### 3.2 Runtime ClassLoader Discovery
`VtlTemplateEngine` now discovers precompiled templates at initialization:
1. Scans `META-INF/viet-template/templates.idx` across ClassLoaders (`ClassLoader.getResources(...)`).
2. Parses entries formatted as `<templateId>=<fqcn>`.
3. Stores mappings in an internal AOT registry: `Map<TemplateId, Class<? extends CompiledTemplate>>`.
4. When `engine.get(TemplateId id)` is invoked, it checks the AOT registry first. If found, it instantiates `CompiledTemplate` directly and wraps it in a descriptor with execution tier `AOT_BYTECODE`.
5. When configured with `rejectRuntimeCompilation(true)`, templates render without requiring template source files on disk or classpath.

---

## 4. Build Tooling Implementations

### 4.1 Apache Maven Plugin (`viet-template-maven-plugin`)
- **Mojo**: `VietTemplateCompileMojo` (`io.github.minh124199.viettemplate.tooling.maven`)
- **Goal**: `viet-template:compile`
- **Default Phase**: `LifecyclePhase.PROCESS_CLASSES`
- **Parameters**:
  - `sourceDirectory`: Defaults to `${project.basedir}/src/main/viet-template`.
  - `outputDirectory`: Defaults to `${project.build.outputDirectory}`.
  - `resourceOutputDirectory`: Defaults to `${project.build.outputDirectory}`.
  - `includes`: List of globs, defaults to `**/*.vtl, **/*.vm`.
  - `excludes`: List of globs to ignore.
  - `encoding`: Defaults to `UTF-8`.
  - `packagePrefix`: Defaults to `io.github.minh124199.viettemplate.generated`.
  - `failOnWarning`: Defaults to `false`.
  - `incremental`: Defaults to `true`.
  - `skip`: Defaults to `false`.
- **Diagnostic Logging**: Maps `TemplateAotDiagnostic` severities to Maven logger (`info`, `warn`, `error`). Formats line and column coordinates.

### 4.2 Gradle Plugin (`viet-template-gradle-plugin`)
- **Plugin ID**: `io.github.minh124199.viet-template`
- **Task**: `compileVietTemplates` (`VietTemplateCompileTask`)
- **Task Attributes**:
  - Annotated with `@CacheableTask` for Gradle build cache integration.
  - Fully compatible with Gradle Configuration Cache (captures zero `Project` references in task execution actions).
  - Uses Gradle lazy properties (`DirectoryProperty`, `ListProperty`, `Property<String>`, `Property<Boolean>`).
- **Convention & Lifecycle Wiring**:
  - Applies automatically when `java` plugin is present.
  - Adds generated classes directory to `sourceSets.main.output.dir(...)`.
  - Adds generated resources directory to `sourceSets.main.resources.srcDir(...)`.
  - Sets up task dependency: `compileJava` -> `compileVietTemplates` -> `classes`.

---

## 5. Dual-Build Parity & Determinism

### 5.1 Byte-for-Byte Parity
A primary architectural guarantee of Viet Template is absolute parity between Maven and Gradle builds. The build tooling guarantees:
1. **Sorted Template Discovery**: Discovered template files are sorted alphabetically by normalized relative path using forward slashes (`/`), guaranteeing identical processing order regardless of filesystem traversal order.
2. **Deterministic Class Naming**: Generated class names are derived from the normalized template ID and a truncated SHA-256 digest of the template ID (`T_<sanitized>_<hash>`).
3. **Byte-for-Byte Identical Bytecode**: Compiling the exact same template set with Maven and Gradle yields 100% byte-for-byte identical `.class` files (confirmed by SHA-256 comparison).
4. **Deterministic `templates.idx`**: The generated index file is sorted alphabetically by `templateId`, producing identical outputs across build systems.

### 5.2 Incremental Compilation & Stale Output Pruning
- State is preserved across builds in `META-INF/viet-template/aot-state`.
- Each template's content SHA-256 is tracked. Unchanged templates are skipped when `incremental` is true.
- When a template source is deleted, subsequent compilation detects the missing source, deletes the associated `.class` file, removes it from `templates.idx`, and increments `deletedCount`.
- Safe deletion guards prevent deleting files outside the configured output directory.

---

## 6. Verification & Test Matrix

### 6.1 Automated Verification Script
The verification script `scripts/verify-aot-tooling-parity.sh` runs:
1. **Maven Basic Consumer Fixture**: Compiles with `viet-template-maven-plugin`, verifies runtime execution with `rejectRuntimeCompilation(true)` and absent source files.
2. **Gradle Basic Consumer Fixture**: Compiles with `viet-template-gradle-plugin`, executes equivalent runtime tests.
3. **Index Parity**: Compares `templates.idx` byte-for-byte between Maven and Gradle (`cmp -s`).
4. **Bytecode Parity**: Compares generated `.class` files byte-for-byte between Maven and Gradle.
5. **Classfile Version Check**: Verifies all generated bytecode is valid Java 17 classfile format (major version 61).
6. **Maven Failure Reporting**: Tests `maven-failure` fixture, asserting non-zero exit code and structured `[PARSER:UNCLOSED_DIRECTIVE]` line:column diagnostics.
7. **Gradle Failure Reporting**: Tests `gradle-failure` fixture, asserting non-zero exit code and structured line:column diagnostics.

### 6.2 Architecture Rules & API Classification
- **ArchUnit**: Added `aot_public_api_must_not_depend_on_internal_packages` to `ArchitectureRulesTest`. Verified that `integration_and_tooling_boundary_must_not_access_internal_packages` passes with `..tooling.maven..` and `..tooling.gradle..`.
- **Public Surface Classification**:
  - Added all 6 new facade types (`TemplateAotCompiler`, `TemplateAotRequest`, `TemplateAotRequest$Builder`, `TemplateAotResult`, `TemplateAotDiagnostic`, `TemplateAotArtifact`) as `STABLE_API` in `config/api-baseline/public-surface-classification.txt` (total 347 types: 72 `STABLE_API`, 14 `STABLE_SPI`, 6 `EXPERIMENTAL`, 255 `PUBLIC_BUT_INTERNAL_ACCIDENT`).
  - `python3 scripts/verify-public-surface-classification.py` passed: 0 unclassified types, 0 stale types, 0 signature leaks.

---

## 7. Decision

**`A. M15 COMPLETE — MAVEN & GRADLE AOT TOOLING READY`**

All acceptance criteria for Milestone M15 are fully satisfied. The Ahead-Of-Time build tooling for both Apache Maven and Gradle is production-ready, fully tested, architecturally decoupled from compiler internals, and verified for 100% byte-for-byte dual-build parity.
