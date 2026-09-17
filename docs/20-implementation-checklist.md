# 20 — Implementation Checklist

Status legend:

- `[ ]` not started
- `[-]` in progress
- `[x]` complete
- `[!]` blocked / decision required

The checklist is intentionally ordered so that correctness is established before optimization and framework integration.

## 0. Repository bootstrap

- [x] Create multi-module build.
- [x] Configure Java toolchains for 17, 21, and 25.
- [x] Enable reproducible archives and deterministic generated-source paths.
- [x] Configure formatter, static analysis, license checks, forbidden APIs, and dependency rules.
- [x] Configure unit-test, integration-test, and TCK foundation.
- [x] Add `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, release process, and compatibility policy.
- [x] Add CI jobs for Linux, Windows, and macOS.
- [x] Add CI matrix for JDK 17/21/25.
- [-] Add binary/API compatibility checking after the first public release (deferred to 1.0).

### Exit criteria

A clean checkout can run one command and execute formatting checks, unit tests, integration tests, and a smoke benchmark.

---

## 1. Source model and diagnostics

- [x] Implement `SourceId` (`TemplateId`).
- [x] Implement immutable `SourceText`.
- [x] Implement UTF-16 offset ↔ line/column mapping without rescanning the whole file.
- [x] Implement `SourceSpan(startOffset, endOffset)`.
- [x] Implement diagnostic severity: INFO/WARNING/ERROR.
- [x] Implement stable diagnostic codes.
- [x] Implement related locations / notes.
- [x] Implement source excerpt rendering.
- [x] Verify CRLF, LF, tabs, supplementary Unicode code points, and empty files.

### Required tests

- [x] Span at file start/end.
- [x] Span crossing lines.
- [x] Malformed UTF-16 input handling policy.
- [x] Exact line/column diagnostics for all parser fixtures.

---

## 2. Lexer

### Token families

- [x] Raw text.
- [x] `$identifier`.
- [x] `${identifier}`.
- [x] `$!identifier`.
- [x] `$!{identifier}`.
- [x] Member access `.`.
- [x] Index brackets.
- [x] Parentheses.
- [x] Comma.
- [x] String literals.
- [x] Integer/floating literals.
- [x] Boolean/null literals where supported by the selected compatibility profile.
- [x] Operators.
- [x] Directive prefix `#`.
- [x] Comments.
- [x] Escapes.

### Lexer modes

- [x] TEXT mode.
- [x] REFERENCE mode.
- [x] DIRECTIVE/EXPRESSION mode.
- [x] STRING mode.
- [x] COMMENT mode.

### Correctness cases

- [x] `Price: $price`.
- [x] `Price: ${price}USD`.
- [x] Quiet references.
- [x] Escaped `$` and `#`.
- [x] Directive text adjacent to literal text.
- [x] Nested parentheses in directives.
- [x] Quoted strings containing `$`/`#`.
- [x] Unicode identifiers according to the documented identifier policy.
- [x] Unterminated strings/comments/references.

### Performance cases

- [x] Lexer performs one linear scan in normal cases.
- [x] Avoid substring allocation for every token; represent tokens by source slices.
- [x] No regex in the hot lexer loop unless benchmarked and justified.

---

## 3. Parser and AST

### Expressions

- [x] Literals.
- [x] Variable reference.
- [x] Quiet reference.
- [x] Formal reference.
- [x] Property chain.
- [x] Method call where profile permits it.
- [x] Index access.
- [x] Unary expressions.
- [x] Arithmetic expressions.
- [x] Comparisons.
- [x] Boolean operations with short-circuiting.
- [x] Parenthesized expressions.
- [x] Ranges if supported.

### Statements/directives

- [x] Text node.
- [x] Output expression.
- [x] `#set`.
- [x] `#if/#elseif/#else/#end`.
- [x] `#foreach/#end`.
- [x] `#break`.
- [x] `#stop` policy.
- [x] `#include`.
- [x] `#parse`.
- [x] `#macro` declaration/invocation.
- [x] `#define` if included in migration profile.
- [x] `#evaluate` only in the explicit dynamic/migration capability.

### Parser architecture

- [x] Handwritten recursive-descent or Pratt parser for expressions.
- [x] Explicit precedence table.
- [x] Bounded recursion/depth checks.
- [x] Error recovery around directive boundaries.
- [x] AST nodes are immutable.
- [x] AST nodes always carry source spans.
- [x] AST contains syntax, not runtime reflection objects.

### Exit criteria

All phase-1 syntax fixtures parse to golden ASTs and malformed fixtures produce stable diagnostics rather than exceptions escaping the compiler.

---

## 3.1 Build parity (Milestone M2.1)

- [x] Provide official Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` pinned to Maven 3.9.9).
- [x] Create root aggregator/parent `pom.xml` and module POMs for all 4 submodules (`viet-template-api`, `viet-template-runtime`, `viet-template-language-vtl`, `viet-template-tck`).
- [x] Align Maven dependency versions with `gradle/libs.versions.toml` (JUnit 5.11.4, AssertJ 3.27.2, ArchUnit 1.3.0, google-java-format 1.30.0).
- [x] Configure Maven plugins matching Gradle setup: `maven-compiler-plugin` (release 17, `-parameters`, `-Xlint:all`, `-Werror`, UTF-8), `maven-surefire-plugin`, `maven-jar-plugin`, `maven-source-plugin`, `maven-javadoc-plugin`, `spotless-maven-plugin`, and `maven-enforcer-plugin`.
- [x] Verify Java 17 bytecode baseline (classfile 61) and test suite execution across both Java 17 and Java 21 under both `./gradlew clean build` and `./mvnw clean verify`.
- [x] Confirm zero dependency leakage (no Spring, Velocity, or unapproved runtime dependencies).
- [x] Implement automated build parity verification script (`scripts/verify-build-parity.py` / `scripts/verify-build-parity.sh`).
- [x] Update GitHub Actions CI matrix (`.github/workflows/ci.yml`) to test both Gradle and Maven across JDK 17, 21, and 25, and run the parity verification job.

### Exit criteria

`./gradlew clean build` and `./mvnw clean verify` independently succeed with identical test passes and byte-for-byte classfile parity without either tool invoking the other.

---

## 3.2 Reference Interpreter and Core VTL Runtime Semantics (Milestone M3)

- [x] Create dedicated module `viet-template-vtl-interpreter` depending only on `api`, `runtime`, and `language-vtl`.
- [x] Configure dual build parity (Gradle Kotlin DSL + Maven `pom.xml`) with Java 17 release baseline and strict compiler flags.
- [x] Implement 3-state evaluation model (`EvaluationValue`: `UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`).
- [x] Implement nested lexical scopes and execution context (`ExecutionContext`) with read-through to immutable `RenderContext`.
- [x] Implement member resolution policy: JavaBean getters, record components, map keys, fields, and index navigation (`ReferenceAccess` / `DefaultReferenceAccess`).
- [x] Implement method overload scoring and invocation with parameter coercion and `ControlSignal` preservation.
- [x] Implement Centralized Truthiness Semantics (`VtlTruthiness`) compliant with Velocity 2.4.x (numbers are truthy, configurable `emptyCheck` for empty CharSequence, Collection, Map, and arrays).
- [x] Implement arithmetic operations (`BigDecimal`/`BigInteger` exact coercion) and comparison operations (`VtlNumericOperations`, `VtlComparisonOperations`).
- [x] Implement short-circuit evaluation for `&&` and `||`.
- [x] Implement `#set` directive with LHS reference targeting and configurable null-RHS behavior (`ignoreSetNullRhs`).
- [x] Implement `#if / #elseif / #else / #end` control flow.
- [x] Implement `#foreach / #end` over collections, maps, arrays, and ranges with `$foreach` metadata (`index`, `count`, `first`, `last`, `hasNext`, `parent`, `stop()`).
- [x] Implement `#break` and `#stop` stackless control signals (`BreakSignal`, `StopSignal`).
- [x] Implement `#include` and `#parse` resource directives with `TemplateResourceResolver`.
- [x] Implement `#macro` and `#define` with top-level pre-pass discovery, default parameter evaluation, and `$bodyContent` for `#@blockMacro`.
- [x] Implement `#evaluate` gated under `VTL_DYNAMIC` profile with strict source length and recursion depth guards.
- [x] Implement Velocity 2.x `LINES` mode space gobbling (`SpaceGobbler`) using character-precise `BitSet` suppression.
- [x] Implement defensive limits (`ExecutionLimits`) for max output characters, loop iterations, range sizes, macro recursion, parse depth, evaluate depth, and dynamic source length.
- [x] Implement security policy (`VtlSecurityPolicy`) denying dangerous pivots (`Class`, `ClassLoader`, `Runtime`, `ProcessBuilder`, `Thread`, `System`, reflection).
- [x] Comprehensive test suite (59 unit and integration tests covering concurrency, fuzzing, strict mode, limits, truthiness, escaping, directives).
- [x] Verify ArchUnit architecture rules: zero cyclic dependencies, zero bytecode generator dependencies (ASM/ByteBuddy), zero Velocity engine dependencies.

### Exit criteria

`./gradlew clean build` and `./mvnw clean verify` succeed with 100% test pass rate across both JDK 17 and JDK 21; all production JARs match identically between Gradle and Maven; architectural constraints verified by ArchUnit.

---

## 3.3 Semantic Compatibility Corrections (Milestone M3.1)

- [x] Audit and correct Area 1: Numeric truthiness under `directive.if.empty_check=true` (zero numbers: `0`, `0.0`, `BigDecimal.ZERO`, `BigInteger.ZERO` evaluate to falsy, matching Velocity 2.4.1 `DuckType.asBoolean`).
- [x] Audit and correct Area 1: Numeric truthiness under `directive.if.empty_check=false` (all numbers evaluate to truthy).
- [x] Audit and correct Area 1: `getAsBoolean()` zero-argument method precedence (evaluated before emptiness checking).
- [x] Audit and correct Area 2: `#set` null-RHS behavior (default `setNullAllowed = true` in Velocity 2.x overwriting variable with `DEFINED_NULL`; configurable legacy 1.x mode preserving existing variable).
- [x] Audit and correct Area 3: Bare `null` literal syntax rejection by default (`BARE_NULL_DISALLOWED`), with opt-in via `VtlParserOptions.allowBareNullLiteral`.
- [x] Audit and correct Area 4: Defined-null vs undefined root reference rendering (both render literal reference text in non-strict mode).
- [x] Audit and correct Area 4: Odd/even backslash reference escaping semantics (odd slashes before undefined/null reduce pairs and preserve trailing slash).
- [x] Audit and correct Area 5: `directive.if.empty_check` configuration interaction across strings, collections, maps, arrays, and numbers.
- [x] Audit and correct Area 6: Strict-reference interactions (undefined root reference throws even if quiet; defined null throws in normal mode but quiet `$!foo` renders `""`; `#if` and alternate-value fallback never throw on null/undefined).
- [x] Audit and correct Area 7: Alternate-value fallback (`${x|'fallback'}`) evaluation (always performs empty checking via `DuckType.asBoolean(val, true)`, falling back on `0`, `""`, and empty collections regardless of `directive.if.empty_check`).
- [x] Keep `org.apache.velocity:velocity-engine-core:2.4.1` test-only in `viet-template-tck` (verified by ArchUnit; zero runtime leakage).
- [x] Implement comprehensive side-by-side differential tests (`SemanticCompatibilityDifferentialTest`) and probe suites (`SemanticCompatibilityProbeTest`, `AdditionalSemanticProbeTest`).
- [x] Validate 100% build parity across Gradle 9.7.1 and Maven 3.9.9 on both Java 17 and Java 21.

### Exit criteria

All 7 semantic compatibility areas validated directly against Velocity 2.4.1 runtime probe and differential test suite; `./gradlew clean build` and `./mvnw clean verify` pass cleanly on JDK 17 and JDK 21.

---

## 4. Compatibility oracle and differential runner (Milestone M4)

- [x] Create a test harness that renders a fixture with Apache Velocity and Viet Template (`VelocityDifferentialTckTest`, `Velocity241EngineAdapter`, `VietReferenceEngineAdapter`).
- [x] Normalize only explicitly documented non-semantic differences (`DifferentialComparator`).
- [x] Capture output, exception type/classification, context mutations, and relevant side effects (`EngineResult`, `ExceptionObservation`).
- [x] Version fixtures by Velocity baseline (`Apache Velocity 2.4.1`).
- [x] Categorize each fixture: `EXACT_MATCH`, `EXPECTED_DIFFERENCE`, `UNSUPPORTED`, `VIET_EXTENSION`, `BUG`.
- [x] Require an architectural rationale / ADR for every intentional incompatibility (`ExpectationRegistry`, `StandardExpectations`).
- [x] Enforce strict mode by default (`viet.tck.mode=STRICT`) failing CI on any unclassified differences or bugs.

### Fixture categories (301 scenarios across 20 active categories)

- [x] References / property access (`ReferenceCorpus`, `IntrospectionCorpus`).
- [x] Maps / lists / arrays / index access (`ReferenceCorpus`, `LiteralCorpus`).
- [x] Null / missing values / quiet references (`ReferenceCorpus`, `StrictModeCorpus`).
- [x] Truthiness / empty checking (`TruthinessCorpus`).
- [x] Arithmetic / coercion / comparisons (`ArithmeticCorpus`, `ComparisonCorpus`, `RandomDifferentialCorpus`).
- [x] Method overloads and parameter type scoring (`MethodOverloadCorpus`).
- [x] `#set` and context mutation (`SetCorpus`).
- [x] `#if / #elseif / #else / #end` (`ConditionalCorpus`, `ShortCircuitCorpus`).
- [x] `#foreach` metadata, nesting, and control flow (`ForeachCorpus`).
- [x] Macro scope, default arguments, and block macros (`MacroCorpus`, `BlockMacroCorpus`).
- [x] `#define` renderable blocks (`DefineCorpus`).
- [x] Includes / parsing / sub-template scopes (`ResourceCorpus`).
- [x] Escapes / backslash matrices / comments (`EscapingCorpus`, `LexicalCorpus`).
- [x] Dynamic evaluation and execution limits (`EvaluateCorpus`).
- [x] Error behavior and fail-fast diagnostics (`ErrorCorpus`).
- [x] Security sandboxing and denial (`SecurityCorpus`).
- [x] Whitespace and space gobbling (`SpaceGobblingCorpus`).
- [x] Programmatic invariant enforcement verifying report totals, ID uniqueness, category coverage, and classification sums (`ReportGenerator.validateInvariants`).

### Exit criteria

The project generates machine-readable compatibility reports (`velocity-compatibility-report.md` and `.json`):

```text
Velocity baseline: 2.4.1
Total differential scenarios: 301 (100.00%)
Exact parity: 295 (98.01%)
Intentional difference: 5 (1.66%)
Viet extension: 1 (0.33%)
Unsupported: 0 (0.00%)
Unexpected regression (BUG): 0 (0.00%)
Accounted behavior coverage: 301 / 301 (100.00%)
```

Both `./gradlew check` and `./mvnw clean verify` execute all 338 TCK tests (301 differential dynamic tests + 37 supporting unit/baseline/architecture tests) in strict mode with 100% pass rate across JDK 17 and JDK 21.

---

## 5. Semantic analyzer and model typing (Milestone M5)

- [x] Implement symbol scopes.
- [x] Implement local variables introduced by `#set`.
- [x] Implement loop variable scopes and loop metadata.
- [x] Implement macro parameter scopes.
- [x] Implement `VType` hierarchy.
- [x] Represent primitive numeric types separately from boxed/reference types where useful.
- [x] Represent nullable/unknown/dynamic states explicitly.
- [x] Resolve declared template model parameters.
- [x] Resolve properties according to selected member-access policy.
- [x] Perform overload resolution only where method calls are enabled.
- [x] Emit compile diagnostics for missing members in typed mode.
- [x] Track escaping context/capability where possible.
- [x] Calculate template capability flags.

### Capability flags to compute

- [x] requires dynamic member resolution.
- [x] requires arbitrary method calls.
- [x] requires dynamic include/parse.
- [x] requires runtime evaluation.
- [x] accesses raw/unescaped output.
- [x] uses unknown model types.
- [x] eligible for fully static AOT lowering.

### Exit criteria

Typed templates analyze with zero diagnostics and compute `eligibleForStaticAot = true`; typos in model parameters and properties fail with stable diagnostics (`VTLS2101`, `VTLS2104`) and Levenshtein suggestions; ArchUnit verifies strict module boundaries with zero cyclic dependencies; 100% build parity and test passes maintained across Maven and Gradle.

---

## 6. Template IR

- [x] Define stable compiler-internal IR package.
- [x] Lower AST to control-flow-aware IR.
- [x] Preserve source mapping on every effectful instruction.
- [x] Separate values from output effects.
- [x] Represent static output chunks by constant-pool IDs.
- [x] Represent resolved member access by explicit `AccessPlan`.
- [x] Represent dynamic member access by explicit `DynamicAccessSite`.
- [x] Represent escaping strategy explicitly.
- [x] Preserve primitive types.
- [x] Verify IR invariants before and after every optimization phase in debug builds.

### Required IR operations

- [x] `WriteStatic`.
- [x] `WriteValue`.
- [x] `LoadParam`.
- [x] `LoadLocal` / `StoreLocal`.
- [x] `GetProperty`.
- [x] `InvokeAllowedMethod`.
- [x] `IndexGet`.
- [x] arithmetic/comparison/boolean operations.
- [x] conditional branch.
- [x] loop setup/next/end.
- [x] include/static-call.
- [x] dynamic dispatch callsite.
- [x] macro call or lowered equivalent.
- [x] return/stop.

### Exit criteria

Typed and untyped ASTs lower into verifiable, control-flow-aware `IrTemplate` structures with deduplicated constant text pools, explicit access plans, explicit escaping modes, primitive operations, and complete source span mapping; `IrVerifier` enforces static AOT and scope constraints; full build parity, TCK compatibility, and ArchUnit rules pass across Maven and Gradle.

---

## 7. Reference interpreter

The interpreter is a correctness oracle and development backend, not the final performance story.

- [x] Execute the same semantic/IR representation used by compiled backends.
- [x] Implement streaming output.
- [x] Implement all VTL_CORE semantics first.
- [x] Implement compatibility-only features behind capability checks.
- [x] Match source-position error reporting.
- [x] Support deterministic execution limits.
- [x] Support development hot reload.

### Exit criteria

The interpreter passes the TCK before the AOT backend is allowed to claim compatibility for the same features.

---

## 8. Output runtime

- [x] Define `TemplateOutput` with minimal operations.
- [x] Writer-backed implementation.
- [x] UTF-8 OutputStream-backed implementation.
- [x] String-building implementation for convenience APIs/tests.
- [x] Static UTF-8 byte chunk support.
- [x] Escaper SPI.
- [x] HTML text escaping.
- [x] HTML attribute escaping policy.
- [x] URI/JavaScript/CSS contextual escaping decision explicitly documented; do not pretend generic HTML escaping is sufficient in every context.
- [x] Efficient primitive-number output.
- [x] CharSequence output without forced `String` creation.
- [x] Back-pressure story documented for synchronous servlet output; reactive support is separate/non-goal until designed.

### Allocation target

For a simple typed template with an externally supplied output buffer, compiler/runtime overhead should approach zero allocations beyond allocations required by the user's own getters/data conversions.

---

## 9. Dynamic linker

- [x] Define `MemberKey` by operation/name/arity.
- [x] Define access policy as an input to linkage.
- [x] Implement monomorphic inline cache.
- [x] Extend to small polymorphic inline cache only after benchmarks.
- [x] Use `MethodHandle`-based targets where safe and measurable.
- [x] Implement megamorphic fallback.
- [x] Bound every cache.
- [x] Make ClassLoader references weak/collectable where necessary.
- [x] Add cache statistics.
- [x] Verify denied members never become linkable through cache reuse.
- [x] Stress test concurrent linkage.
- [x] Stress test redeploy/classloader churn.

### `invokedynamic` gate

Do **not** add `invokedynamic` only because it is sophisticated. Add it only if a benchmark demonstrates a material benefit over the simpler explicit PIC design on supported JVMs.

---

## 10. Optimizer

Implement in this order and benchmark each pass independently:

- [x] remove unreachable IR.
- [x] constant folding.
- [x] boolean simplification.
- [x] merge adjacent static output.
- [x] pre-encode static UTF-8 chunks.
- [x] eliminate redundant local loads/conversions.
- [x] bind statically known accessors.
- [x] specialize primitive operations.
- [x] specialize common loops where semantics remain identical.
- [x] inline small static macros/includes.
- [x] split large generated render methods.
- [x] optional escape-hoisting only when proven safe.

### Optimization invariant

Every optimization must preserve:

1. observable output,
2. evaluation order,
3. side effects permitted by the profile,
4. exception classification/source location as far as specified,
5. security capability boundaries.

---

## 11. AOT bytecode backend

- [x] Define generated class ABI.
- [x] Define deterministic generated class naming.
- [x] Generate constructor only when required.
- [x] Generate `render(...)` with direct output calls.
- [x] Map primitive VTypes to JVM primitive descriptors.
- [x] Emit direct calls for statically resolved access plans.
- [x] Emit dynamic linker calls only for explicitly dynamic sites.
- [x] Emit source-map metadata in a sidecar index or class metadata.
- [x] Verify generated classes with JVM verification plus project-specific tests.
- [x] Handle method-size limits with deterministic splitting.
- [x] Generate no Java source in the direct bytecode backend.

### JDK strategy

- [x] Runtime remains Java 17-compatible.
- [ ] `compiler-jdk25` can use `java.lang.classfile` on JDK 25.
- [x] Prototype the exact class-file target versions produced by that module and test on Java 17/21/25; do not assume cross-target behavior.
- [x] Keep compiler SPI isolated so another backend (e.g. ASM) can exist if needed.

---

## 12. Template repository/cache/hot reload

- [x] Template IDs are normalized and traversal-safe.
- [x] Classpath repository.
- [x] Filesystem repository only where explicitly configured.
- [x] Composite repository with deterministic precedence.
- [x] Compile cache keyed by source fingerprint + compiler config + model signature + compiler version.
- [x] Negative caching policy.
- [x] Development watcher/debounce.
- [x] Atomic replacement of compiled template handles.
- [x] No global ClassLoader leaks.
- [x] Production mode can reject runtime compilation entirely.

---

## 12.5. Velocity application compatibility architecture

- [x] Directed template dependency graph (`TemplateDependencyGraph`, `TemplateDependencyKind`).
- [x] Static dependency extraction from IR (`#parse`, `#include`, global macros, layout).
- [x] Transitive dependent cache invalidation (`invalidateWithDependents`).
- [x] Cycle-safe graph traversal protecting against recursive `#parse` cycles.
- [x] Multi-source context composition (`RenderRequest`, `ContributorContext`, `RenderContextContributor`).
- [x] Configurable context collision policies (`FAIL`, `MODEL_WINS`, `CONTRIBUTOR_WINS`).
- [x] Protection of engine-reserved variables against contributor overwrite.
- [x] Global Velocimacro library caching and fingerprinting (`velocimacro.library`).
- [x] Global macro library precedence (`FIRST_WINS`, `LAST_WINS`).
- [x] Invariant that local template macros unconditionally shadow global macros.
- [x] Constant pool merging and statement ID remapping for global macros.
- [x] Two-stage layout rendering plan (`LayoutRenderPlan`, `LayoutResolver`, `LayoutConfiguration`).
- [x] Output character limit enforcement during screen template capture.
- [x] Post-screen layout resolution supporting in-template `#set($layout = ...)` override/bypass.
- [x] Layout recursion cycle detection and maximum depth limits (`maxLayoutDepth`).
- [x] Shared vs. isolated layout context scope (`LayoutContextScope`).
- [x] Zero production dependencies on Spring Framework or Apache Velocity.
- [x] ArchUnit architectural rules ensuring zero forbidden dependency leakage.

---

## 13. Security hardening

- [x] Deny class loading/reflection/system/runtime/process by default.
- [x] Deny Spring `ApplicationContext`, bean factory, raw request/response/session exposure by default.
- [x] Safe-profile allowlist rules.
- [x] Resource root confinement.
- [x] Include/parse traversal protection.
- [x] Maximum source size.
- [x] Maximum AST nodes.
- [x] Maximum expression depth.
- [x] Maximum macro/include recursion.
- [x] Configurable loop/output/time budgets.
- [x] Disable `#evaluate` by default.
- [x] Raw output is explicit and auditable.
- [x] Security regression corpus.
- [x] Fuzz parser/linker/path resolution.

---

## 14. Public API

- [x] `TemplateEngine`.
- [x] `Template`.
- [x] `CompiledTemplate`.
- [x] `TemplateRepository`.
- [x] `TemplateCompiler`.
- [x] `TemplateModelDescriptor`.
- [x] `RenderContext`.
- [x] `TemplateOutput`.
- [x] `Escaper`.
- [x] `MemberAccessPolicy`.
- [x] `TemplateDiagnostic`.
- [x] `TemplateException` taxonomy.
- [x] Extension API separated from internal compiler API.
- [x] Thread-safety guarantees in Javadoc.
- [x] Compatibility/versioning policy.

### 14.1 Public Surface Containment + Lifecycle Finalization (Milestone M14.1)

- [x] Safe visibility reductions: `CallSiteRegistry.CallSiteKey` (private), `AccessLink.Status` and `status()` (package-private), `VtlAstDumper` (package-private), `VtlSemanticDiagnosticCodes` (package-private).
- [x] Deterministic 4-category classification of all 341 public/protected production types in `config/api-baseline/public-surface-classification.txt`:
  - `STABLE_API`: 66 types.
  - `STABLE_SPI`: 14 types.
  - `EXPERIMENTAL`: 6 types.
  - `PUBLIC_BUT_INTERNAL_ACCIDENT`: 255 types.
  - 0 unclassified types.
- [x] Automated CI classification verification (`scripts/verify-public-surface-classification.py`):
  - Check 1: 0 unclassified public types.
  - Check 2: No stale classified types.
  - Check 3: Baseline parity with `config/api-baseline/1.0-public-api.txt` (80/80 types).
  - Check 4: Zero signature leaks from internal/experimental types into `STABLE_API` or `STABLE_SPI`.
- [x] Concrete output stream lifecycle and resource ownership contracts:
  - `Utf8OutputStreamTemplateOutput`: render-scoped, flushes and closes wrapped `OutputStream` on `close()`, releases buffer to `Utf8BufferPool`, idempotent double close, managed response non-closing delegate pattern.
  - `WriterTemplateOutput`: render-scoped, caller retains ownership of `Writer`, flush delegation.
  - `StringTemplateOutput`: render-scoped, in-memory, `reset()` contract.
  - `TemplateEngine`: long-lived singleton, thread-safe, `AutoCloseable` with idempotent `close()`.
- [x] Dedicated lifecycle contract test suite: `ConcreteOutputLifecycleContractTest` (9 tests passing).
- [x] Integration boundary ArchUnit rule: `integration_and_tooling_boundary_must_not_access_internal_packages` in `ArchitectureRulesTest`.
- [x] M15 AOT facade readiness audit: documented requirement for narrow build-time compiler facade (`TemplateAotCompiler`) in M15.

---

## 15. Maven/Gradle AOT tooling

### Narrow Public Facade & Runtime Discovery
- [x] Narrow public AOT facade: `TemplateAotCompiler`, `TemplateAotRequest`, `TemplateAotResult`, `TemplateAotDiagnostic`, `TemplateAotArtifact` in `io.github.minh124199.viettemplate.aot` (classified as `STABLE_API`).
- [x] Internal compiler decoupling: ArchUnit rules ensure plugins never import internal compiler, parser, IR, AST, or linker packages.
- [x] Self-contained bytecode: `<clinit>` emits `UTF8_CHUNKS` and `SITES` arrays via `BytecodeRuntimeBridge`, enabling isolated ClassLoader execution.
- [x] Runtime AOT discovery: `VtlTemplateEngine` discovers `META-INF/viet-template/templates.idx` from ClassLoaders with `rejectRuntimeCompilation(true)` support.

### Maven
- [x] `viet-template-maven-plugin`: `compile` goal (`VietTemplateCompileMojo`) bound by default to `process-classes`.
- [x] Incremental cache: SHA-256 fingerprint tracking via `META-INF/viet-template/aot-state`.
- [x] Generated resources/class output integration: outputs directly to `${project.build.outputDirectory}`.
- [x] Stale file removal: deleted `.vtl` sources trigger deletion of corresponding `.class` files.
- [x] Fail-on-warning option: `failOnWarning` parameter supported.
- [x] Line:column diagnostic logging: structured error formatting matching IDE standards.

### Gradle
- [x] `viet-template-gradle-plugin`: Plugin ID `io.github.minh124199.viet-template`, task `compileVietTemplates`.
- [x] Cacheable task: `VietTemplateCompileTask` annotated with `@CacheableTask` and Gradle lazy properties.
- [x] Configuration-cache compatibility: task action captures zero `Project` references.
- [x] Automatic source set wiring: registers output classes and resources with `sourceSets.main`.
- [x] Lifecycle binding: automatic wiring `compileJava` -> `compileVietTemplates` -> `classes`.

### Reproducibility & Parity
- [x] Byte-for-byte identical output: Maven and Gradle produce bit-identical `.class` files (verified by SHA-256).
- [x] No absolute build path in generated artifacts.
- [x] Alphabetically sorted template discovery and deterministic `templates.idx` generation.
- [x] Deterministic class naming based on sanitized template path and truncated SHA-256 hash.
- [x] Automated parity verification script: `scripts/verify-aot-tooling-parity.sh` testing all consumer fixtures.

---

## 16. Spring Framework & Spring Boot integration (Milestone M16)

- [x] `VietTemplateView` (Spring MVC `View` implementation).
- [x] `VietTemplateViewResolver` with caching and AOT index discovery fallback.
- [x] Stream directly to response output where safe (`Utf8OutputStreamTemplateOutput` + `NonClosingOutputStream`).
- [x] Content type/charset behavior (`text/html;charset=UTF-8`, UTF-8 validated).
- [x] Locale-aware template resolution / view caching (`ConcurrentHashMap`).
- [x] Model binding without copying when possible (`RenderContext.of(model)`).
- [x] Explicit policy for request/session attributes (default denied/isolated).
- [x] No automatic application-context exposure.
- [x] Path traversal defense and exception mapping with template location.
- [x] MVC integration tests using MockMvc and real server tests across Maven and Gradle fixtures.
- [x] Dedicated `viet-template-spring-boot-autoconfigure` module.
- [x] Dedicated starter `viet-template-spring-boot-starter`.
- [x] Configuration properties namespace owned by the project (`viet-template.*`).
- [x] `@AutoConfiguration` (`VietTemplateAutoConfiguration`).
- [x] Classpath, web-application, and property conditions.
- [x] Auto-config imports metadata (`AutoConfiguration.imports`).
- [x] User beans back off auto-configuration (`@ConditionalOnMissingBean`).
- [x] Engine customization SPI (`VietTemplateEngineCustomizer`).
- [x] Dual-build parity verification script: `scripts/verify-spring-integration-parity.sh`.

---

## 16.1. Spring Security Integration (Milestone M16.1)

- [x] Dedicated optional `viet-template-spring-security` module.
- [x] Unidirectional dependency rule enforced: core engine never depends on Spring Security.
- [x] Starter classpath isolation: `viet-template-spring-boot-starter` does not pull in Spring Security.
- [x] Generic request metadata bridge (`SpringRenderAttributes`) in `viet-template-spring`.
- [x] Strict security boundary: raw `Authentication`, `SecurityContext`, request/session, and credentials never exposed to template.
- [x] Read-only immutable projections: `SecurityView` and `CsrfView` with minimized JavaBean accessors (`getName()`, `isAuthenticated()`, `isAnonymous()`, `getAuthorities()`, `hasAuthority()`, `hasAnyAuthority()`, `getToken()`, `getParameterName()`, `getHeaderName()`).
- [x] Full `VTL_SAFE` compatibility: properties resolve without core engine modifications.
- [x] Sensitive token redaction: `CsrfView.toString()` strictly redacts token secret.
- [x] Contextual auto-escaping: plain String returns for principal name and authorities to prevent XSS.
- [x] SPI contributor: `SpringSecurityRenderContextContributor` implementing `RenderContextContributor`.
- [x] Factory SPIs: `SecurityViewFactory` and `CsrfViewFactory` with `@ConditionalOnMissingBean`.
- [x] Spring Boot auto-configuration: `VietTemplateSecurityAutoConfiguration` (`viet-template.security.enabled=true`).
- [x] Comprehensive test coverage: unit tests, multithreaded concurrency tests, 3-party tests, Virtual Thread tests, ArchUnit boundary tests, auto-configuration tests, starter isolation tests.
- [x] Multi-generation dual-build AOT consumer fixtures:
  - Spring Boot 3.3.5 / Spring Security 6.3.4: `maven-security-aot` & `gradle-security-aot`
  - Spring Boot 4.0.0 / Spring Framework 7.0.1 / Spring Security 7.0.0: `maven-security7-aot` & `gradle-security7-aot`
- [x] 10-step dual-build parity verification suite (`scripts/verify-spring-security-parity.sh` & `scripts/verify-spring-security7-integration.sh`) verifying 100% byte-for-byte bytecode and index parity and live HTTP execution.
- [x] Multi-version compatibility verification: single artifact verified against Spring Security 6.3.4, 6.5.11, 7.0.7, 7.1.1 via `scripts/verify-spring-security-compatibility.py`.
- [x] Layered public API baselines (`1.0-core-public-api.txt` [80 types], `1.0-aot-public-api.txt` [6 types], `1.0-spring-public-api.txt` [8 types], `1.0-spring-security-public-api.txt` [5 types]) protecting 99 types mechanically with full bijection invariant via `scripts/verify-api-compatibility.py`.
- [x] Formally classified in public surface baseline (`config/api-baseline/public-surface-classification.txt`) with 99 stable types (80 `STABLE_API`, 19 `STABLE_SPI`).
- [x] Formally documented in [`docs/36-spring-security-integration.md`](36-spring-security-integration.md).
- [x] Milestones M15, M16, and M16.1 released as 0.2.1 on 2026-09-17 (published to Maven Central and GitHub Releases, public consumer smoke tests passing).

---

## 17. Spring Boot Advanced & Native Image Integration (Milestone M17)

### Phase A: GraalVM Native Image & Spring AOT (COMPLETE)
- [x] Spring AOT runtime hints for precompiled template discovery (`VietTemplateRuntimeHints`).
- [x] Runtime resource hints for `META-INF/viet-template/templates.idx` and precompiled class discovery.
- [x] Reflection hints for template engine provider and configuration properties.
- [x] Spring Security AOT runtime hints (`VietTemplateSecurityRuntimeHints`) with `TypeReference` reflection registration for security views.
- [x] Dual-build native test fixtures for Maven (`maven-boot3-native`, `maven-boot4-native`) and Gradle (`gradle-boot3-native`, `gradle-boot4-native`).
- [x] Automated native image integration test runner script (`scripts/verify-native-image-integration.sh`).
- [x] GitHub Actions native-image CI workflow (`.github/workflows/native-image.yml`).
- [x] Live HTTP server verification for standalone native executables with runtime compilation disabled (`viet-template.runtime-compilation-enabled=false`).
- [x] Full architecture documentation in `docs/37-m17-graalvm-native-image.md`.

### Phase B: Spring Boot DevTools & Advanced Lifecycle (COMPLETE)
- [x] Spring Boot DevTools live reload lifecycle hook hardening and ClassLoader boundary isolation.
- [x] Hardened engine lifecycle (`destroyMethod = "close"`) terminating `DevelopmentFileWatcher` and executor threads.
- [x] ClassLoader turnover safety under `URLClassLoader` and `RestartClassLoader` with weak cache eviction.
- [x] DevTools containment audit ensuring 0 compile/runtime DevTools leaks in all 8 production modules (`DevToolsContainmentTest`).
- [x] Dual-build DevTools test fixtures (`maven-boot3-devtools`, `gradle-boot3-devtools`, `maven-boot4-devtools`, `gradle-boot4-devtools`).
- [x] Automated DevTools restart integration verification script (`scripts/verify-devtools-restart-integration.sh`).
- [x] GitHub Actions DevTools CI workflow (`.github/workflows/devtools-restart.yml`).
- [x] Live DevTools HTTP verification for Mode A (dynamic hot reload), Mode B (AOT recompile + trigger restart), stale template deletion, and 10x restart stress test with zero ClassLoader leaks (`leakFree = true`).
- [x] Full architecture documentation in `docs/38-m17-devtools-restart-hardening.md`.

---

## 18. TCK release gate

- [ ] TCK is independently runnable.
- [ ] Every syntax feature maps to TCK IDs.
- [ ] Every compatibility profile has its own expected feature set.
- [ ] Compiler and interpreter execute the same TCK corpus.
- [ ] AOT and dynamic backend outputs are differential-tested against the interpreter.
- [ ] VTL migration fixtures are additionally compared with Apache Velocity.
- [ ] Release blocks on unexpected semantic differences.

---

## 19. Benchmark release gate & performance engineering milestones

### 19.1 Milestone M19.1 — 0.1.x Benchmark Infrastructure & Baseline Measurement

- [x] Create dedicated submodule `viet-template-benchmarks` with dual build parity (Gradle `build.gradle.kts` + Maven `pom.xml`).
- [x] Configure JMH framework with `jmh-generator-annprocess`, shadow/uber JAR packaging (`benchmarks.jar`), and `-prof gc`.
- [x] Implement Workload B01: `StaticHtmlBenchmark` (20 KB static HTML streaming, zero-allocation literal writes).
- [x] Implement Workload B02: `ScalarVariableBenchmark` (50 scalar variable substitutions, context lookup overhead).
- [x] Implement Workload B03: `DeepPropertyChainBenchmark` (4-level getter chains on records and POJOs).
- [x] Implement Workload B04: `ConditionalBranchBenchmark` (100 mixed conditionals with truthiness evaluation).
- [x] Implement Workloads B05, B06, B07: `ForeachLoopBenchmark` (10-row, 1,000-row, and nested 100x10 loops over collections, arrays, ranges).
- [x] Implement Workload B08: `EscapingBenchmark` (escaping-heavy HTML text and attribute streams).
- [x] Implement Workloads B09, B10: `DynamicCallSitePicBenchmark` (monomorphic, 2-to-4 polymorphic PIC, megamorphic property resolution).
- [x] Implement Workload B15: `TemplateCompilationCacheBenchmark` (concurrent parse, analyze, compile, and invalidation of 1,000 templates).
- [x] Implement Workloads B11, B12: `MacroAndLayoutBenchmark` (macro parameter passing, block macros, and two-stage layout rendering).
- [x] Milestone M19.1a: Internal Java 17 benchmark infrastructure & baseline measurement completed on clean commit `aad9d35` (`baseline-java17.json`).
- [ ] Milestone M19.1b: Set up comparator baselines in benchmark suite: handwritten Java, Apache Velocity 2.4.1, Quarkus Qute (dynamic and typed), jte, and Thymeleaf.
- [x] Milestone M19.1c: Cross-JDK validation across Java 17, 21, and 25 completed with identical methodology on clean commit `26567ca` (`baseline-java17.json`, `baseline-java21.json`, `baseline-java25.json`).
- [x] Document preservation of 0.1.x simple high-performance data structures: contiguous `AccessLink[]` array scan for PIC depth <= 4 (low constant factors, good locality, avoiding unnecessary hashing or node overhead), dependency graph reverse index `Map<TemplateId, Set<TemplateId>>` + cycle-safe BFS traversal using standard `ArrayDeque` and visited `HashSet`, `ExecutionContext` with `ArrayDeque<LocalScope>` and `HashMap`.

### 19.2 Milestone M19.2 — 0.2.0 High-Performance Runtime Architecture (RELEASED)

#### M19.2a — Indexed compile-cache invalidation

- [x] Add the `TemplateId -> Set<CompileCacheKey>` reverse index using JDK concurrent collections.
- [x] Replace targeted full-cache scanning with average O(1) index lookup plus O(K) removal.
- [x] Keep entry, active-key, reverse-index, and LRU state consistent on put, replacement,
  invalidation, eviction, and full reset.
- [x] Preserve negative-cache and dependency-graph invalidation semantics.
- [x] Define and test concurrent put/invalidate ordering with per-template lock stripes.
- [x] Add N=10,000 invalidation coverage and explicit eviction bookkeeping measurement to JMH.

#### M19.2b — Variable slots and ExecutionFrame (complete)

- [x] Implement IR and optimizer variable slot assignment pass (`O45 AssignVariableSlots`) to assign stable compiler-assigned integer slot IDs without slot reuse (slot reuse deferred in 0.2.0).
- [x] Implement `ExecutionFrame` backed by `EvaluationValue[] slots` for fast indexed variable access.
- [x] `ExecutionFrame` explicitly initializes each slot to `EvaluationValue.undefined()`, ensuring Java reference arrays (which initialize to `null`) never expose `null` or conflate it with `DEFINED_NULL`.
- [x] A name-based fallback is retained for variable accesses whose identity cannot safely be resolved to a static slot while preserving Velocity-compatible semantics.
- [x] Preserve 3-state evaluation semantics (`UNDEFINED`, `DEFINED_NULL`, `DEFINED_VALUE`) and full Velocity compatibility across all slot read/write operations.
- [x] Update VTL reference interpreter to execute variable accesses via `ExecutionFrame` slots.
- [x] Update AOT bytecode backend to emit direct slot-indexed bytecode instructions (`ALOAD`, `ASTORE`, `AALOAD`, `AASTORE`).
- [x] Replace $O(N)$ linear key scan `entries.keySet().removeIf(...)` in `TemplateCompileCache.invalidate(TemplateId)` with secondary reverse index (`ConcurrentMap<TemplateId, Set<CompileCacheKey>>`), performing average $O(1)$ key set lookup + $O(K)$ removal of the $K$ associated entries, reducing overall invalidation work to $O(K)$ (M19.2a).
- [x] Verify throughput improvement and allocation reduction on `ScalarVariableBenchmark` and `ForeachLoopBenchmark` over 0.1.x baseline under balanced tradeoff evaluation.
- [x] Verify $O(K)$ indexed invalidation under high concurrency in `TemplateCompilationCacheBenchmark`.
- [x] Milestone M19.2 released as 0.2.0 on 2026-09-12 (published to Maven Central and GitHub Releases, consumer tests passing).

### 19.2.1 Post-0.2.0 Release Infrastructure Hardening (complete)

- [x] Preserve `v0.2.0` and all published `0.2.0` bytes unchanged.
- [x] Separate validated Central upload from long-running publication observation.
- [x] Capture the Central deployment UUID and monitor explicit deployment states for up to 120 minutes.
- [x] Guard immutable versions against first-run duplicates and prohibit tag-workflow redeployment on rerun.
- [x] Verify parent and production artifacts from Maven Central, assert TCK/benchmarks remain absent, and run fresh Maven/Gradle Central-only consumer checks.
- [x] Make GitHub Release finalization publication-gated, idempotent, and resumable by deployment UUID.
- [x] Add deterministic state/API/timeout/deployment-ID/publication-contract tests and a no-publish dry run.
- [x] Record the 0.2.0 asynchronous-publication incident and recovery procedure.

This follow-up was release infrastructure work only; it did not start M19.3 (M15 was subsequently implemented under Section 15).

### 19.2.1a Maven Central Publication Metadata Audit & Correction (COMPLETE)

- [x] Configure SCM and project URL inheritance controls on root POM (`child.project.url.inherit.append.path="false"`, `child.scm.connection.inherit.append.path="false"`, `child.scm.developerConnection.inherit.append.path="false"`, `child.scm.url.inherit.append.path="false"` supported since Maven 3.6.1).
- [x] Update SCM connection to HTTPS read-only `scm:git:https://github.com/minh124199/viet-template.git` and developerConnection to standard `scm:git:ssh://git@github.com/minh124199/viet-template.git`.
- [x] Add explicit canonical `<url>${github.repository.url}/tree/main/<module></url>` across published production child modules; strictly fail verification if a published child merely inherits the repository-root URL.
- [x] Verify non-published modules (`viet-template-tck`, `viet-template-benchmarks`) omit `<url>` and enforce triple publication skipping (`maven.deploy.skip`, `skipPublishing`, `central.publishing.skip`).
- [x] Ensure dual-build parity in `build.gradle.kts` and `viet-template-gradle-plugin/build.gradle.kts` with matching `/tree/main/${project.name}` URL and HTTPS/SSH SCM settings.
- [x] Implement automated publication metadata validation in `scripts/verify-release-metadata.py` (`--check-publication-metadata`, `--check-effective-pom`, `--check-urls-online`) rejecting obsolete `git://` and malformed appended paths.
- [x] Enhance `scripts/validate-release-bundle.py` to dynamically derive reactor published modules, validate POM metadata across all published coordinates, and reject malformed SCM/URL patterns.
- [x] Add comprehensive release infrastructure unit tests in `scripts/tests/test_release_infrastructure.py`.
- [x] Integrate `--check-publication-metadata` and `--check-effective-pom` into release and CI workflows.

### 19.2.2 Milestone M19.2c — Performance Engineering Infrastructure & Cross-JDK Analysis (COMPLETE)

- [x] Define authoritative runtime profiles (`config/benchmark-runtime-profiles.json`) and CLI query tool (`scripts/perf/runtime_profiles.py`) for `J17-G1`, `J21-G1`, `J21-ZGC`, `J25-G1`, `J25-G1-COH`, `J25-ZGC`, `J25-AOT`.
- [x] Implement JDK runtime validation tool (`scripts/perf/check-java-runtime.sh`) verifying target version, VM, vendor, architecture, and executable paths.
- [x] Extend environment metadata recorder (`scripts/record-benchmark-env.sh`) to support `--profile`, capturing Git dirty status, CPU cores, RAM, GC collector, Compact Object Headers, and AOT capabilities.
- [x] Implement profile-aware benchmark runner (`scripts/perf/run-benchmarks.sh`) with standardized directory layout (`build/performance/<timestamp>-<sha>/<profile>/`).
- [x] Implement JMH benchmark comparison tool (`scripts/perf/compare-jmh.py`) with parameter/configuration compatibility checks, metric direction awareness, conservative interval interpretation, regression thresholds, and Markdown reporting.
- [x] Implement Java 21 Virtual-Thread support (`VirtualThreadSupport.java`) maintaining `--release 17` binary compatibility across production and benchmark sources via `MethodHandle` dynamic resolution.
- [x] Implement comprehensive concurrency stress suite (`SharedEngineVirtualThreadStressTest`, `DynamicCallSiteConcurrencyStressTest`, `CompileCacheConcurrencyStressTest`, `HotReloadConcurrencyStressTest`, `DependencyGraphConcurrencyStressTest`, `RenderBudgetIsolationStressTest`, `PlatformThreadComparisonHarness`).
- [x] Implement virtual-thread stress runner script (`scripts/perf/run-virtual-thread-stress.sh`).
- [x] Implement Java 25 JFR profiling tools (`scripts/perf/jfr-profile.sh`, `scripts/perf/jfr-summary.sh`) extracting views: `hot-methods`, `allocation-by-class`, `contention-by-site`, `gc-pauses`, `thread-allocation`, and `pinned-threads`.
- [x] Implement process startup measurement harness (`StartupBenchmarkEntrypoint.java`, `scripts/perf/measure-startup.py`, `scripts/perf/measure-startup.sh`) capturing nanosecond checkpoints from JVM bootstrap to batch rendering.
- [x] Implement Java 25 JVM AOT cache experiment script (`scripts/perf/jdk-aot-experiment.sh`) demonstrating class-loading and startup acceleration.
- [x] Implement dedicated scheduled and manual performance CI pipeline (`.github/workflows/performance.yml`).
- [x] Update documentation inventory with the 10 canonical benchmark classes and complete profiling methodology (`docs/15-benchmark-plan.md`, `docs/18-roadmap.md`, `docs/20-implementation-checklist.md`, `docs/SOURCES.md`, `.gitignore`).

### 19.3 Milestone M19.3 — 0.3.x+ Evidence-Driven Optimizations (Gated on Empirical Evidence)

Post-0.2.0 baseline performance characterization, multi-JDK comparisons, and empirical candidate evaluations are complete and documented in [`docs/21-performance-characterization.md`](21-performance-characterization.md). Production implementation of M19.3a is documented in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md), memory-model hardening in [`docs/24-m19.3a1-recency-memory-model-hardening.md`](24-m19.3a1-recency-memory-model-hardening.md), and low-concurrency fast-path tuning in [`docs/25-m19.3a2-low-concurrency-fast-path.md`](25-m19.3a2-low-concurrency-fast-path.md). Subsequent M19.3 optimizations remain GATED.

- [x] **Milestone M19.3a (Qualification)**: Complete qualification and design study for cache contention mitigation (documented in [`docs/22-m19.3a-cache-contention-qualification.md`](22-m19.3a-cache-contention-qualification.md); Prototype B qualified for implementation).
- [x] **Milestone M19.3a (Production Implementation — COMPLETE)**: Implement batched deferred maintenance compile cache in `TemplateCompileCache` with per-thread striped circular recency buffers, lock-free read hits, opportunistic maintenance draining, and strict two-level lock hierarchy; verified 6.85x speedup at 8 threads, 0 JFR monitor contention events, 0.000 B/op steady-state cache allocation, and 100% test pass rate across JDK 17, 21, and 25 (documented in [`docs/23-m19.3a-cache-production-implementation.md`](23-m19.3a-cache-production-implementation.md); merged in PR #8).
- [x] **Milestone M19.3a.1 (Memory-Model Hardening — COMPLETE)**: Harden deferred-recency publication and consumption with 16 shared atomic slot sampler stripes (`AtomicReferenceArray`), `setRelease` publication, `getAndSet` acquire-release draining, `lastDrainedCursor` stripe-skip fast path, intentional overwrite semantics, and stale-slot invalidation immunity; verified 6.82x speedup (52.57M ops/s at 8 threads), 0.000 B/op allocation overhead, 0 monitor contention events, and full adversarial test pass (documented in [`docs/24-m19.3a1-recency-memory-model-hardening.md`](24-m19.3a1-recency-memory-model-hardening.md)).
- [x] **Milestone M19.3a.2 (Low-Concurrency Fast-Path Tuning — COMPLETE)**: Qualify and tune low-concurrency approximate-recency maintenance by increasing opportunistic drain threshold (`READ_DRAIN_THRESHOLD = 256`) while preserving 100% full sampling; verified complete 1T throughput recovery (+145.0% to 20.11M ops/s, surpassing legacy locked cache by +10.9%), 1T concurrent rendering recovery (+42.6% to 7.10M ops/s, surpassing legacy by +10.4%), preservation of multi-thread scaling (up to 56.73M ops/s at 8T, 7.27x on hot-key skew, 5.11x on 8T render), 100% eviction quality retention under continuous write churn, 0.0000 B/op allocation, and 0 JFR monitor contention events (documented in [`docs/25-m19.3a2-low-concurrency-fast-path.md`](25-m19.3a2-low-concurrency-fast-path.md)).
- [x] **Milestone M19.3b (Streaming Output & UTF-8 Qualification — COMPLETE & FROZEN)**: Complete qualification study for streaming output allocation and UTF-8 formatting across 14 taxonomy workloads and 5 output strategies. Attributed 99.6% of streaming `byte[]` allocations to unpooled 8KB setup buffers and `ByteArrayOutputStream` growth. Identified severe primitive formatting allocation leak in `NumberFormatting` (32-byte temp array per int, 40-byte temp array per long; direct formatting boosts throughput by +72.3% and eliminates 100% of temporary allocations) and substring slicing leak in `HtmlTextEscaper` (67.6% allocation penalty). Promoted Candidate 1 (Zero-Allocation Direct Primitive Number Formatting) for production implementation; qualified HTML escaping for follow-up and bounded buffer pooling conditionally; verified 12/12 correctness tests across JDK 17, 21, and 25 (documented in [`docs/26-m19.3b-output-allocation-qualification.md`](26-m19.3b-output-allocation-qualification.md)).
- [x] **Milestone M19.3b.1 (Zero-Allocation Direct Primitive Number Formatting — COMPLETE)**: Implement direct backward ASCII decimal encoding in `NumberFormatting.formatInt` and `formatLong` directly into caller-provided byte buffers; verified 100% elimination of intermediate `byte[]` arrays (32 B/int -> 0 B/op, 40 B/long -> 0 B/op), up to +145.9% (J17) / +180.8% (J25) single-digit and +57.9% (J17) / +62.0% (J25) negative int speedup, 0.000 B/op formatter allocation across all value classes, 100% elimination of `NumberFormatting` allocation stacks in JFR, and 200,000 deterministic property tests passing identically across JDK 17, 21, and 25 (documented in [`docs/27-m19.3b1-direct-number-formatting.md`](27-m19.3b1-direct-number-formatting.md)).
- [x] **Milestone M19.3b.2 (Zero-Allocation HTML Escaping — COMPLETE)**: Implement direct character range streaming via `TemplateOutput.write(CharSequence, int, int)` and eliminate `subSequence()`/`substring()` allocations in `HtmlTextEscaper`; verified 100% elimination of intermediate string allocations (184-880 B/op -> 0.000 B/op, 100% allocation reduction), up to +53.4% (J17) / +66.0% (J21) / +58.0% (J25) throughput speedup on escaping workloads, 0.000 B/op across all workloads with escapes, 100% elimination of `HtmlTextEscaper` allocation stacks in JFR, hostile CharSequence harness verifying zero calls to `subSequence()` or `toString()`, and 100,000 deterministic property tests passing identically across JDK 17, 21, and 25 (documented in [`docs/28-m19.3b2-zero-allocation-html-escaping.md`](28-m19.3b2-zero-allocation-html-escaping.md)).
- [x] **Milestone M19.3b.2.1 (Range-Write SPI Hardening & Writer Allocation Cleanup — COMPLETE)**: Harden public `TemplateOutput.write(CharSequence, int, int)` SPI contract across all edge cases (`null`, empty range, bounds checking via `Objects.checkFromToIndex`, surrogate pairs, backward compatibility); eliminate temporary `char[]` allocations on non-String range writes in `WriterTemplateOutput` via lazy instance-owned buffer batching ($0.000\text{ B/op}$, up to $+16.7\%$ speedup on small slices) while avoiding per-character `Writer.write(int)` synchronization bottlenecks ($80\text{--}94\%$ throughput collapse of direct loop); verified 100,000 deterministic property tests and hostile CharSequence tests passing across JDK 17, 21, and 25 (documented in [`docs/29-m19.3b2-1-range-write-spi-hardening.md`](29-m19.3b2-1-range-write-spi-hardening.md)).
- [x] **Milestone M19.3b.3 (Bounded UTF-8 Stream-Buffer Reuse — COMPLETE)**: Productionize bounded UTF-8 stream-buffer reuse in `Utf8BufferPool` (package-private, capacity 16 = 128 KiB retained payload, backed by lock-free `AtomicReferenceArray<byte[]>`); integrate into `Utf8OutputStreamTemplateOutput` with strict single-owner confinement, non-blocking fallback on pool exhaustion, guaranteed release on flush exceptions, idempotent double close, and post-close write protection; verified 100% elimination of 8 KiB buffer setup allocation ($8,208.0\text{ B/op}$ saved across all 8 taxonomy workloads), 0 JFR monitor contention events, 0 pinned virtual threads, and 10,000 virtual thread tasks passing with 100% fidelity across JDK 17, 21, and 25 (documented in [`docs/30-m19.3b3-bounded-utf8-buffer-reuse.md`](30-m19.3b3-bounded-utf8-buffer-reuse.md)).
- [x] **Milestone M19.3b.3.1 (Post-Merge Performance + Lifecycle Validation — COMPLETE & FROZEN)**: Execute authoritative benchmark validation (protocol `VT-PERF-M19.3B3.1-POST-MERGE-VALIDATION-1`: 3 forks, 5 warmups, 10 measurements, $N=30$) across JDK 17, 21, and 25; verified +142.6% speedup on tiny ASCII templates, +21.1% on medium templates, and 8,208 B/op eliminated across all 8 taxonomy workloads with 0 regressions; verified concurrency scaling from 1 to 32 threads maintaining +63% to +72% gains under saturation; proved 100% data fidelity and 0 pinned threads across 10,000 virtual-thread tasks; audited `close()` vs `flush()` lifecycle and stream ownership boundaries; permanently frozen the streaming output subsystem (documented in [`docs/31-m19.3b3-1-post-merge-validation.md`](31-m19.3b3-1-post-merge-validation.md)).
- [ ] *(Deferred)*: Lexer and parser token allocation reductions (disqualified: token allocations <0.1% in steady state).
- [ ] *(Deferred)*: Concurrent read-path optimizations in `TemplateDependencyGraph` (disqualified: 0 contention events observed).
- [ ] *(Deferred)*: `invokedynamic` dynamic property resolution prototype (disqualified: contiguous `AccessLink[]` PIC scan achieves 51-85M ops/s, <1.5% rendering CPU time).

### 19.4 Performance PR Review Checklist

Every PR modifying runtime execution paths, data structures, or caching algorithms must verify:

- [ ] 1. **JMH Benchmark Evidence**: Includes before/after JMH results on relevant benchmarks.
- [ ] 2. **Balanced Allocation & Performance Tradeoff**: Measures allocation rate (`bytes/op`) using `-prof gc`; evaluates throughput, latency, allocation rate, retained memory, and contention together on representative workloads.
- [ ] 3. **Scalability & Contention**: Validates concurrent scaling under $\ge 8$ threads without lock convoying.
- [ ] 4. **Memory Footprint**: Analyzes memory footprint per template and per execution context.
- [ ] 5. **Java 17 Baseline Idioms**: Adheres to Java 17 idiomatic practices, compact flat arrays, and standard collections without third-party dependencies.
- [ ] 6. **Avoidance of Premature Hacks**: Avoids unsafe tricks, undocumented JVM internals, and unmaintainable micro-optimizations.
- [ ] 7. **Thread-Safety & Invariants**: Formally proves all concurrency invariants and immutability guarantees.
- [ ] 8. **Tail Latency & Branch Predictability**: Avoids branch mispredictions and unbounded worst-case latencies.
- [ ] 9. **Architectural Simplicity & DSA Acceptance**: Satisfies all 7 parts of the DSA Acceptance Rule and the Four-Tier Preference Hierarchy; reverts to simple JDK structures if gains are marginal ($< 5\text{--}10\%$).

### Initial success gates

Do not advertise ratios before measurement. Internal engineering targets:

- [x] Viet Template aims to reduce rendering overhead relative to reflection-heavy interpreted template execution while approaching generated or compiled Java performance where its semantics permit, with baseline measured under M19.1.
- [ ] Low engine overhead allocation in typed path.
- [ ] Meaningful throughput and allocation profile improvements over Velocity on dynamic migration workloads.
- [ ] Competitive with Qute typed mode and jte on typed workloads under reproducible benchmark methodology.
- [ ] No performance regression $> 5\%$ without an accepted benchmark note/ADR.

---

## 20. Documentation and developer experience

- [ ] language reference.
- [ ] migration guide from Velocity.
- [ ] compatibility matrix.
- [ ] secure-template guide.
- [ ] typed-model guide.
- [ ] Maven/Gradle setup.
- [x] Spring MVC/Boot setup.
- [ ] production/AOT setup.
- [ ] native-image guide.
- [ ] optimization/explain guide.
- [ ] benchmark methodology.
- [ ] error-code catalog.
- [ ] extension author guide.

---

## Definition of done for 1.0

1. VTL_CORE compatibility is fully specified and TCK-covered.
2. Migration profile has an explicit feature-by-feature Velocity compatibility table.
3. Interpreter, optimized dynamic backend, and AOT backend agree on shared semantics.
4. Typed/AOT rendering is competitive with modern compiled JVM template engines in reproducible benchmarks.
5. Security-sensitive dynamic features are disabled unless explicitly enabled.
6. Spring integration verified baseline: Spring Framework 6.1.14 and Spring Boot 3.3.5 tested on Java 17 / Jakarta Servlet 6.0 (intended compatibility line: Spring Framework 6.1.x / Spring Boot 3.3.x; untested versions are not independently guaranteed; future major lines such as Spring 7 / Boot 4 are planned future targets).
7. Java 17/21/25 runtime compatibility is CI-verified for modules claiming it.
8. Build-time compilation is incremental and reproducible.
9. Generated-code/source diagnostics are actionable.
10. No benchmark or compatibility marketing claim exceeds the evidence produced by the checked-in suites.
11. Stable public surface convergence: reconcile `config/api-baseline/public-surface-classification.txt` with layered baselines (`1.0-core-public-api.txt` [80 types], `1.0-aot-public-api.txt` [6 types], `1.0-spring-public-api.txt` [8 types], `1.0-spring-security-public-api.txt` [5 types]) so every public type classified as `STABLE_API` or `STABLE_SPI` (99 total types) is mechanically verified and protected by automated compatibility verification with full bijection invariant before 1.0 freeze.
