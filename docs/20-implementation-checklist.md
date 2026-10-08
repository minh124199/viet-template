# 20 — Implementation Checklist

> **Historical plan and review template (2026-10-03):** This checklist spans pre-1.0 planning through the released 1.1.0 milestones. Unchecked items inside review templates are prompts for applicable future changes, not open release work. Current release and milestone state is summarized in [`18-roadmap.md`](18-roadmap.md). Explicitly deferred/disqualified candidates remain deferred rather than planned work.

Status legend:

- `[ ]` not started
- `[-]` in progress
- `[x]` complete
- `[!]` blocked / decision required

The checklist is intentionally ordered so that correctness is established before optimization and framework integration.

## 0. Repository bootstrap

- [x] Create multi-module build.
- [x] Configure Java toolchains for 21 and 25 (Java 21 compiler baseline, Java 25 primary runtime).
- [x] Enable reproducible archives and deterministic generated-source paths.
- [x] Configure formatter, static analysis, license checks, forbidden APIs, and dependency rules.
- [x] Configure unit-test, integration-test, and TCK foundation.
- [x] Add `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, release process, and compatibility policy.
- [x] Add CI jobs for Linux, Windows, and macOS.
- [x] Add CI matrix for JDK 21/25 across Tier A-E suites.
- [x] Add binary/API compatibility checking (`verify-api-compatibility.py` and layered baselines).

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
- [x] Configure Maven plugins matching Gradle setup: `maven-compiler-plugin` (release 21, `-parameters`, `-Xlint:all`, `-Werror`, UTF-8), `maven-surefire-plugin`, `maven-jar-plugin`, `maven-source-plugin`, `maven-javadoc-plugin`, `spotless-maven-plugin`, and `maven-enforcer-plugin`.
- [x] Verify Java 21 bytecode baseline (classfile 65) and test suite execution across both Java 21 and Java 25 under both `./gradlew clean build` and `./mvnw clean verify`.
- [x] Confirm zero dependency leakage (no Spring, Velocity, or unapproved runtime dependencies).
- [x] Implement automated build parity verification script (`scripts/verify-build-parity.py` / `scripts/verify-build-parity.sh`).
- [x] Update GitHub Actions CI matrix (`.github/workflows/ci.yml`) to test both Gradle and Maven across JDK 21 and 25 (Tier A-E), and run the parity verification job.

### Exit criteria

`./gradlew clean build` and `./mvnw clean verify` independently succeed with identical test passes and byte-for-byte classfile parity without either tool invoking the other.

---

## 3.2 Reference Interpreter and Core VTL Runtime Semantics (Milestone M3)

- [x] Create dedicated module `viet-template-vtl-interpreter` depending only on `api`, `runtime`, and `language-vtl`.
- [x] Configure dual build parity (Gradle Kotlin DSL + Maven `pom.xml`) with Java 21 release baseline and strict compiler flags.
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
- [x] Validate 100% build parity across Gradle 9.7.1 and Maven 3.9.9 on both Java 21 and Java 25.

### Exit criteria

All 7 semantic compatibility areas validated directly against Velocity 2.4.1 runtime probe and differential test suite; `./gradlew clean build` and `./mvnw clean verify` pass cleanly on JDK 21 and JDK 25.

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

- [x] Runtime baseline is Java 21 (`--release 21`, classfile 65); runtime optimized for Java 25.
- [x] Evaluated ClassFile API (`java.lang.classfile`) vs zero-dependency `ClassFileWriter`; retained `ClassFileWriter` with zero external dependencies and zero preview flag requirements (ADR-0012).
- [x] Prototype the exact class-file target versions produced by that module and test on Java 21/25; do not assume cross-target behavior.
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
- [x] First-class multiple-suffix support (`suffixes`) with deterministic candidate evaluation, legacy `suffix` backward compatibility, cache invalidation, and Spring Boot binding.
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
  - Spring Boot 4.1.1 / Spring Framework 7.0.9 / Spring Security 7.1.1: `maven-security7-aot` & `gradle-security7-aot`
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

- [x] TCK is independently runnable.
- [x] Every syntax feature maps to TCK IDs.
- [x] Every compatibility profile has its own expected feature set.
- [x] Compiler and interpreter execute the same TCK corpus.
- [x] AOT and dynamic backend outputs are differential-tested against the interpreter.
- [x] VTL migration fixtures are additionally compared with Apache Velocity.
- [x] Release blocks on unexpected semantic differences.

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
- [x] Milestone M19.1b: Implemented comparative C01–C08 benchmarks for Viet-IR, Viet-AOT, Apache Velocity 2.4.1, Quarkus Qute, jte, and Thymeleaf. See `viet-template-benchmarks/src/main/java/io/github/minh124199/viettemplate/benchmarks/comparative/` and `docs/performance/comparative-benchmarks.md`.
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
- [x] **Milestone M19.3c (Steady-State Execution Preparation & DSA Specialization — COMPLETE & FROZEN)**: Complete steady-state execution preparation and DSA specialization across five evidence-backed phases, permanently freezing the milestone with zero M19.3c.6 selected (documented in [`docs/39-m19.3c-steady-state-dsa.md`](39-m19.3c-steady-state-dsa.md)):
  - [x] **M19.3c.1 (Engine-Scoped Precompiled AOT Reuse — COMPLETE & FROZEN)**: Precompiled generated template instances are constructed once per engine generation and safely shared concurrently, eliminating reflective construction and wrapper metadata on warmed lookup while preserving generation-scoped ownership and classloader collectability.
  - [x] **M19.3c.2 (Prepared IR Execution — COMPLETE & FROZEN)**: Final IR optimization, verification, root layout, and function dispatch maps are prepared once per compilation generation into immutable executables; warmed rendering executes with request-only mutable state.
  - [x] **M19.3c.3 (LoopPlan-Driven Iteration Specialization — COMPLETE & FROZEN)**: Loop execution consumes compiler-assigned `LoopPlan`s (`ARRAY`, `RANGE`, `ITERATOR`, `ITERABLE`, `DYNAMIC`), eliminating `ArrayList` copies for arrays, materialization for ranges, and eager draining of caller iterators while respecting `#break` immediately.
  - [x] **M19.3c.4 (Request-Scope Representation Cleanup — COMPLETE & FROZEN)**: Deleted redundant temporary `HashMap` allocations in foreach and macro invocations; removed duplicate slot/scope writes; implemented single-probe template-variable lookup; preserved defined-null vs. undefined 3-state semantics and public scope copy guarantees.
  - [x] **M19.3c.5 (Foreach Metadata Observability & Elision — COMPLETE & FROZEN)**: Implemented conservative compile-time analysis in `ForeachMetadataObservability` to elide metadata object construction, wrappers, slot writes, and scope synchronization when `$foreach` is unobservable (~60% IR allocation drop for $N=100$, ~5.65 KB/op eliminated, neutral on J25 AOT), while preserving immutable snapshots when observed or dynamic hazards exist.
  - [x] **Rejected / Deferred Decisions Tracked**: RandomAccess indexing (rejected), SmallLocalScope (rejected), tagged/raw ExecutionFrame (rejected), mutable ForeachMetadata reuse (rejected), internal virtual-thread rendering (rejected by architecture), scope/map pooling and ThreadLocal caches (rejected), dense function IDs (deferred).
  - [x] **Stopping Rule & Final Program Freeze**: Fresh post-M19.3c.5 profiling did not identify another production optimization with a sufficiently favorable performance-to-complexity ratio; no M19.3c.6 selected; program frozen.
- **Deferred/disqualified**: Lexer and parser token allocation reductions (token allocations <0.1% in the characterized steady state).
- **Deferred/disqualified**: Concurrent read-path optimizations in `TemplateDependencyGraph` (no contention events observed in the cited study).
- **Deferred/disqualified**: `invokedynamic` dynamic property resolution prototype (the measured contiguous `AccessLink[]` PIC scan was retained).

### 19.4 Performance PR Review Checklist

Every PR modifying runtime execution paths, data structures, or caching algorithms must verify:

The unchecked boxes below are a per-change review template. They do not mean a repository milestone or release gate is currently incomplete.

- [ ] 1. **JMH Benchmark Evidence**: Includes before/after JMH results on relevant benchmarks.
- [ ] 2. **Balanced Allocation & Performance Tradeoff**: Measures allocation rate (`bytes/op`) using `-prof gc`; evaluates throughput, latency, allocation rate, retained memory, and contention together on representative workloads.
- [ ] 3. **Scalability & Contention**: Validates concurrent scaling under $\ge 8$ threads without lock convoying.
- [ ] 4. **Memory Footprint**: Analyzes memory footprint per template and per execution context.
- [ ] 5. **Java 21 Baseline Idioms**: Adheres to Java 21 idiomatic practices, compact flat arrays, pattern matching, and standard collections without third-party dependencies.
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

## 20. Documentation and developer experience (Historical 1.0 checklist — complete)

- [x] language reference.
- [x] migration guide from Velocity.
- [x] compatibility matrix.
- [x] secure-template guide.
- [x] typed-model guide.
- [x] Maven/Gradle setup.
- [x] Spring MVC/Boot setup.
- [x] production/AOT setup.
- [x] native-image guide.
- [x] optimization/explain guide.
- [x] benchmark methodology.
- [x] error-code catalog.
- [x] extension author guide.

---

## Definition of done for 1.0

1. VTL_CORE compatibility is fully specified and TCK-covered.
2. Migration profile has an explicit feature-by-feature Velocity compatibility table.
3. Interpreter, optimized dynamic backend, and AOT backend agree on shared semantics.
4. Typed/AOT rendering is competitive with modern compiled JVM template engines in reproducible benchmarks.
5. Security-sensitive dynamic features are disabled unless explicitly enabled.
6. Spring integration canonical baseline: Spring Framework 7.0.9, Spring Boot 4.1.1, and Spring Security 7.1.1 tested on Java 21+ / Jakarta Servlet 6.1.0 (Tomcat 11.0.24) with full virtual thread execution support (ADR-0009).
7. Java 21/25 runtime compatibility is CI-verified across Tier A-E suites.
8. Build-time compilation is incremental and reproducible.
9. Generated-code/source diagnostics are actionable.
10. No benchmark or compatibility marketing claim exceeds the evidence produced by the checked-in suites.
11. Stable public surface convergence: reconcile `config/api-baseline/public-surface-classification.txt` with layered baselines (`1.0-core-public-api.txt` [80 types], `1.0-aot-public-api.txt` [6 types], `1.0-spring-public-api.txt` [8 types], `1.0-spring-security-public-api.txt` [5 types]) so every public type classified as `STABLE_API` or `STABLE_SPI` (99 total types) is mechanically verified and protected by automated compatibility verification with full bijection invariant before 1.0 freeze.

---

## M18 — TCK & Performance Release Gates (COMPLETE & FROZEN)

See [`docs/40-m18-tck-performance-release-gates.md`](40-m18-tck-performance-release-gates.md) for the full milestone document.

### Deliverable A: Language Conformance TCK

- [x] **A1.** `config/tck/vtl-feature-matrix.json` — 80 features across 20 categories, durable IDs
- [x] **A2.** `scripts/verify-tck-coverage.py` + `scripts/verify-tck-coverage.sh` — 100% coverage gate (80/80)
- [x] **A3.** `TckSuiteRegistry.java` — 80 executable public-API conformance scenarios
- [x] **A4.** `TckConformanceTest.java` — derived scenario/profile/backend expansion across IR and AOT_BYTECODE, 0 failures
- [x] **A5.** `FeatureMatrixValidationTest.java` — schema and coverage assertions
- [x] **A6.** `TckRunner.java` — CLI entrypoint (`--backend`, `--profile`, `--json-output`)
- [x] **A7.** `BackendParityTest.java` — 75 dual-backend bit-identical parity, 5 IR-only documented
- [x] **A8.** `integration-tests/tck-consumer/maven/` — Maven standalone consumer fixture (3 tests)
- [x] **A9.** `integration-tests/tck-consumer/gradle/` — Gradle standalone consumer fixture (3 tests)
- [x] **A10.** `scripts/verify-tck-consumer.sh` — end-to-end independent consumer gate

### Deliverable B: Performance Release Gates

- [x] **B1.** `ComparativeEngineBenchmark.java` — C01–C08 workloads, Velocity/Qute/jte/Thymeleaf
- [x] **B2.** `BenchmarkEngineAdapter` + 7 adapters (Viet-IR, Viet-AOT, Velocity, Qute, jte, Thymeleaf)
- [x] **B3.** `ComparativeWorkloads.java` — shared data models for fixture parity
- [x] **B4.** `CrossEngineFixtureCorrectnessTest.java` — 48 tests, 0 failures
- [x] **B5.** `config/benchmark-manifest.json` — machine-readable registry (C01–C08 + B01–B15)
- [x] **B6.** `benchmark-evidence/m18/` — durable raw J21/J25 JMH, environment, manifest, report, and checksums
- [x] **B7.** `scripts/perf/generate-benchmark-report.py` — JMH JSON → Markdown report generator
- [x] **B8.** `.gitignore` updated — exploratory evidence ignored while formal qualification JSON is tracked
- [x] **B9.** `scripts/verify-m18-release-gates.sh` — clean-room master release gate with mandatory evidence mode

### Documentation & CI

- [x] `docs/40-m18-tck-performance-release-gates.md` — formal M18 milestone document
- [x] `docs/18-roadmap.md` — M18 status updated to COMPLETE & FROZEN after qualification
- [x] `.github/workflows/ci.yml` — `tck-gates` job added (Tier A)
- [x] Milestones M17 and M18 released as 0.2.2 on 2026-09-20 (published to Maven Central and GitHub Releases, public consumer smoke tests passing; active development on main advanced to 0.2.3-SNAPSHOT).

---

## Milestone M20 — 1.0 Adoption Readiness, Migration & Documentation Suite

### Deliverable A: Release Consistency & Documentation Infrastructure
- [x] **A1.** Fix release date consistency (`2026-09-20`) across README, CHANGELOG, roadmap, and checklist.
- [x] **A2.** `scripts/verify-documentation.py` — comprehensive documentation verification engine (release dates, coordinates, Java 21 baseline, Spring properties, internal links, public types, diagnostic codes, matrix sync).
- [x] **A3.** `scripts/tests/test_verify_documentation.py` — unit tests (30 tests) for documentation verification engine.
- [x] **A4.** `.github/workflows/ci.yml` — documentation verification step integrated into CI quick-checks.

### Deliverable B: Language Reference & Compatibility
- [x] **B1.** `scripts/generate-compatibility-matrix.py` — sync generator from `config/tck/vtl-feature-matrix.json`.
- [x] **B2.** `docs/migration/compatibility-matrix.md` — 80 features, 20 categories, 100% TCK coverage.
- [x] **B3.** `docs/language/syntax-reference.md` — complete VTL syntax, directives, and operator precedence.
- [x] **B4.** `docs/language/undefined-null-semantics.md` — 3-state evaluation model conceptual guide.
- [x] **B5.** `docs/language/foreach-and-scopes.md` — `$foreach` metadata, frame lifecycle, and scope isolation.
- [x] **B6.** `docs/language/macros-and-layouts.md` — Velocimacros, global libraries, and layout rendering plans.
- [x] **B7.** `docs/language/typed-models.md` — `#* @vtlvariable *#` typed declarations and compile-time diagnostics.

### Deliverable C: Apache Velocity Migration Suite
- [x] **C1.** `docs/migration/velocity-migration-guide.md` — step-by-step replacement guide, context, layouts, tools.
- [x] **C2.** `docs/migration/velocity-differences.md` — intentional differences catalog (DIFF-001 through DIFF-003, sandboxing).
- [x] **C3.** `VelocityMigrationPatternsTest.java` — executable regression fixture validating migration patterns.

### Deliverable D: Security, Deployment & Spring Integration
- [x] **D1.** `docs/security/secure-templates.md` — `MemberAccessPolicy`, `RenderBudget`, path confinement, CSP nonces.
- [x] **D2.** `docs/deployment/production-aot.md` — Dev vs Prod profiles, AOT bytecode, `templates.idx`, cache sizing.
- [x] **D3.** `docs/native-image/graalvm-native-image.md` — GraalVM Native Image compilation, runtime hints, native builds.
- [x] **D4.** `docs/spring/spring-boot-integration.md` — Spring Boot 4 / Framework 7 starter, full property catalog.
- [x] **D5.** `docs/build-tooling/maven.md` & `docs/build-tooling/gradle.md` — Maven and Gradle plugin guides.
- [x] **D6.** `docs/extensions/extension-guide.md` — STABLE_SPI implementations, lifecycles, and error handling.
- [x] **D7.** `examples/plain-java/` — standalone executable plain Java example fixture.

### Deliverable E: Diagnostics, Performance Evidence & Readiness
- [x] **E1.** `docs/diagnostics/error-catalog.md` — comprehensive error codes catalog with examples and fixes.
- [x] **E2.** `docs/performance/comparative-benchmarks.md` — authoritative M18 C01–C08 comparative evidence across J21 and J25 with 4-gate qualification.
- [x] **E3.** `docs/1.0-readiness-gap-analysis.md` — 15-point readiness audit, 105 stable vs 259 accidental public types, encapsulation roadmap.
- [x] **E4.** `docs/getting-started/quickstart.md` — 5-minute quickstart guide.
- [x] **E5.** `docs/getting-started/support-matrix.md` — complete JDK, build tooling, Spring, Quarkus, and OS support matrix.
- [x] **E6.** `README.md` — restructured adoption-focused landing page.

### Deliverable F: Quarkus Extension & Multi-Framework Foundation (Milestone M21)
- [x] **F1.** `viet-template-quarkus` & `viet-template-quarkus-deployment` — separated runtime/deployment modules with Maven/Gradle dual-build parity.
- [x] **F2.** `VietTemplateProducer` & `VietTemplateRenderer` — CDI injection and streaming HTTP output.
- [x] **F3.** `TemplateSuffixConfiguration` & `UndefinedReferencePolicy` — canonical framework-neutral abstractions in `viet-template-api`.
- [x] **F4.** `VietTemplateProcessor` — build-time template AOT compilation and index generation (`templates.idx`).
- [x] **F5.** Dev-mode hot reload — `HotDeploymentWatchedFileBuildItem`, transitive `#parse` reload, and classloader leak safety.
- [x] **F6.** GraalVM native image execution — empirically verified on Linux x86_64 across Maven and Gradle native runners.
- [x] **F7.** Quarkus Security facade & Qute coexistence — `$security` context view and side-by-side execution with Qute.
- [x] **F8.** `docs/extensions/quarkus.md` — complete Quarkus extension guide.
- [x] Milestone M21 released as 0.2.3 on 2026-09-22 (active development advanced to 0.3.0-SNAPSHOT).

### Deliverable G: Public Surface Containment & Generated ABI Foundation (Milestone M22.1 / 0.3.0-M1)
- [x] **G1.** ADR-0016 (`docs/adr/0016-generated-template-abi-compatibility.md`) — formal generated-template ABI compatibility policy (Model D: pre-1.0 recompilation allowed; forward compatibility locked at 1.0).
- [x] **G2.** Mechanical generated runtime ABI audit (`scripts/verify-generated-abi.py`, `config/api-baseline/generated-template-runtime-abi.txt`) — verified 4 true runtime ABI types: `BytecodeRuntimeBridge`, `DynamicCallSite`, `LinkerAccessPolicy`, `ForeachMetadata`.
- [x] **G3.** Package-private containment completed for 34 validated Category-A types across `viet-template-language-vtl`, `viet-template-runtime`, and `viet-template-vtl-interpreter`.
- [x] **G4.** Accidental public surface reduced from 259 to 225 types (-34 types); stable surface frozen at 105 types with 0 signature leaks.
- [x] **G5.** Accidental public inventory regenerated (`config/api-baseline/accidental-public-types-inventory.json`): 62 Category B, 159 Category C, 4 Category F, 0 Category A remaining.

### Deliverable H: Internal Package Relocation (Milestone M22.2 / 0.3.0-M2)
- [x] **H1.** Relocated 49 accidental public implementation types across `viet-template-language-vtl` and `viet-template-vtl-interpreter` into explicit `*.internal.*` packages (`internal.ast`, `internal.ir.plan`, `internal.semantics`, `internal.compiler`, `internal.engine`, `internal.interpreter`).
- [x] **H2.** Reclassified 17 types from Category B to Category C to maintain JLS 8.1.6 sealed hierarchy permits, protect frozen stable API signatures (`VtlInterpreterOptions`), preserve Spring AOT reflection hints, benchmarks, and optimizer package constraints.
- [x] **H3.** Published migration and FQCN mapping reference at `docs/migration/0.3.0-internal-package-moves.md`.
- [x] **H4.** Accidental public inventory updated (`config/api-baseline/accidental-public-types-inventory.json`): 49 Category B (internal packages), 172 Category C, 4 Category F, 0 Category B un-internalized.
- [x] **H5.** Verified 100% backward compatibility across all 105 stable types, 0 signature leaks, 0 ABI drifts, and full dual-build parity.

### Deliverable K: Public Surface Role Classification & Framework/Build Entrypoint Containment (Milestone 0.3.0-M3.3)

- [x] **K1.** Established nine-category public surface taxonomy distinguishing true stable API/SPI from classes that must be public due to external framework discovery, build tooling, ServiceLoader, generated bytecode ABI, or internal cross-module contracts: `STABLE_API`, `STABLE_SPI`, `EXPERIMENTAL`, `GENERATED_RUNTIME_ABI`, `FRAMEWORK_ENTRYPOINT`, `BUILD_TOOL_ENTRYPOINT`, `SERVICE_ENTRYPOINT`, `INTERNAL_CROSS_MODULE`, `PUBLIC_BUT_INTERNAL_ACCIDENT`.
- [x] **K2.** Reclassified 4 framework entrypoints (`VietTemplateRuntimeHints`, `VietTemplateSecurityRuntimeHints`, `VietTemplateProducer`, `VietTemplateProcessor`) from `PUBLIC_BUT_INTERNAL_ACCIDENT` to `FRAMEWORK_ENTRYPOINT`.
- [x] **K3.** Reclassified 4 build-tool entrypoints (`VietTemplateCompileMojo`, `VietTemplatePlugin`, `VietTemplateCompileTask`, `VietTemplateExtension`) from `PUBLIC_BUT_INTERNAL_ACCIDENT` to `BUILD_TOOL_ENTRYPOINT`.
- [x] **K4.** Reclassified 56 production cross-module internal types from `PUBLIC_BUT_INTERNAL_ACCIDENT` to `INTERNAL_CROSS_MODULE` across 4 module edges (`language-vtl → vtl-interpreter`, `runtime → vtl-interpreter`, `runtime → spring`, `runtime → quarkus`).
- [x] **K5.** Reclassified `BytecodeRuntimeBridge` from `PUBLIC_BUT_INTERNAL_ACCIDENT` to `GENERATED_RUNTIME_ABI`.
- [x] **K6.** Resolved C7 (Module Boundary Artifact) for `ModelSchema$Builder`: contained to package-private, eliminating one compiled public type (336 → 335 total).
- [x] **K7.** Confirmed C7 resolution for `Nullability` (reclassified C4 — sealed hierarchy language topology constraint via `VType`); `VTypes` remains C7 pending JPMS.
- [x] **K8.** Created machine-readable entrypoint registry `config/architecture/framework-and-tooling-entrypoints.json` (12 entrypoints with 10-point schema: FQCN, module, kind, metadata source, mustBePublic, mustHavePublicConstructor, fqcnStabilityRequired, userJavaApi, stableApiClassification, description).
- [x] **K9.** Created machine-readable cross-module contract registry `config/architecture/cross-module-internal-contracts.json` (56 registered contracts across 4 production module edges).
- [x] **K10.** Expanded `scripts/verify-public-surface-classification.py` to cover all published modules (including `quarkus-deployment`, `maven-plugin`, `gradle-plugin`), support all new categories, and enforce zero-leak policy for ALL non-stable types in Check 4.
- [x] **K11.** Created `scripts/verify-framework-entrypoints.py`: automated gate verifying metadata files, compiled class existence, constructor requirements, and classification parity; generates `build/reports/public-framework-entrypoints.json`.
- [x] **K12.** Created `scripts/verify-cross-module-contracts.py`: automated gate enforcing zero unregistered cross-module internal imports and correct architectural dependency direction.
- [x] **K13.** Recorded ADR-0017 (`docs/adr/0017-public-surface-taxonomy-and-entrypoint-classification.md`) establishing the nine-category taxonomy and the canonical principle: *Public because a framework must discover it is not the same as public because users should program against it.*
- [x] **K14.** All 12 M3.3 verification gates pass: `test_verify_public_surface.py`, `verify-public-surface-classification.py`, `verify-framework-entrypoints.py`, `verify-cross-module-contracts.py`, `verify-api-compatibility.py`, `verify-generated-abi.py`, `analyze-category-c-boundaries.py`, `verify-build-parity.py`, `verify-documentation.py`, `verify-release-metadata.py`, dual spotless checks, and `git diff --check`.
### Deliverable L: vtl-interpreter Internal Contract Formalization (Milestone 0.3.0-M3.4)

- [x] **L1.** Reclassified 6 `vtl.interpreter.*` types from `PUBLIC_BUT_INTERNAL_ACCIDENT` to `INTERNAL_CROSS_MODULE`: `EvaluationValue`, `EvaluationValue$State`, `ExecutionContext`, `ExecutionFrame`, `EngineInterpreterBridge`, `ForeachMetadata` (concrete loop-metadata impl). `PUBLIC_BUT_INTERNAL_ACCIDENT`: 146 → 140.
- [x] **L2.** Registered 6 new cross-module contracts in `config/architecture/cross-module-internal-contracts.json` (56 → 62 total) across edges `vtl-interpreter → viet-template-benchmarks` and `vtl-interpreter → viet-template-tck`.
- [x] **L3.** Extended `scripts/verify-cross-module-contracts.py` to scan `src/test/java` alongside `src/main/java` for consumer-reference verification in test-only modules (e.g. `viet-template-tck`).
- [x] **L4.** Audited all 34 intra-module `vtl.internal.*` and `vtl.engine.cache.*` types: all have cross-subpackage callers within `vtl-interpreter` (e.g. `IrInterpreter`, `VtlTemplateEngine`, `BytecodeRuntimeBridge`). All currently require public Java visibility because of cross-package callers; potential remedies include ownership/package redesign, while JPMS could add module-path export control but would not itself reduce Java visibility.
- [x] **L5.** All verification gates pass. Stable contract unchanged: `STABLE_API` (94) + `STABLE_SPI` (25) = 119. `INTERNAL_CROSS_MODULE`: 62. `PUBLIC_BUT_INTERNAL_ACCIDENT`: 140. Zero signature leaks.

### Deliverable M: Final Public/Internal Architecture Convergence (Milestone 0.3.0-M3.5)

- [x] **M1.** Introduced `INTERNAL_CROSS_PACKAGE` category (55 types): types intentionally public within their artifact due to cross-package callers in the same JAR (`vtl.internal.compiler.*`, `vtl.internal.engine.*`, `vtl.internal.interpreter.*`, `vtl.engine.cache.*`, `vtl.engine.dependency.*`, `language.vtl.internal.*`, `runtime.linker.*`). Not architectural debt.
- [x] **M2.** Introduced `BENCHMARK_SUPPORT_INTERNAL` category (3 types): `EvaluationValue$State`, `ExecutionFrame`, `ForeachMetadata` (vtl.interpreter) — consumed only by the unpublished JMH benchmark suite.
- [x] **M3.** Updated `config/architecture/cross-module-internal-contracts.json` to version `0.3.0-M3.5`: added `consumerScope` (`MAIN`/`TEST`/`BENCHMARK`) and `publicationStatus` fields across all 62 contracts. Verified 56 production contracts.
- [x] **M4.** Updated `scripts/verify-cross-module-contracts.py` to distinguish production vs test vs benchmark contract counts.
- [x] **M5.** Added `scripts/tests/test_verify_cross_module_contracts.py` unit tests covering MAIN/TEST/BENCHMARK consumers, nested class consumers, scope separation, and unconsumed detection.
- [x] **M6.** Updated `scripts/test_verify_public_surface.py` with `TestNewCategories` leak detection tests (15 unit tests passing).
- [x] **M7.** Created `config/architecture/public-surface-debt-registry.json` tracking all 85 remaining `PUBLIC_BUT_INTERNAL_ACCIDENT` types across 3 groups (AST concrete nodes: 41, IR concrete nodes: 33, Semantics: 11) for M22, plus 3 non-production `INTERNAL_CROSS_MODULE` contracts for POST_1_0_MAINTENANCE (88 total tracked debt types) with remediation plans.
- [x] **M8.** Corrected JPMS documentation: JPMS `qualified exports` control named-module access only; they do not reduce Java `public` visibility on the classpath. Evaluated and confirmed JPMS decision: **DEFERRED**.
- [x] **M9.** Evaluated physical module boundary between `viet-template-language-vtl` and `viet-template-vtl-interpreter`: decision **KEEP**, re-evaluate at M22.
- [x] **M10.** All 10 architecture verifiers pass. Post-M3.5 metrics: `STABLE_API` 94, `STABLE_SPI` 25, `INTERNAL_CROSS_MODULE` 59 (56 production), `INTERNAL_CROSS_PACKAGE` 55, `BENCHMARK_SUPPORT_INTERNAL` 3, `PUBLIC_BUT_INTERNAL_ACCIDENT` 85, total 335 types, 0 signature leaks. Approved handoff: `BEGIN_RUNTIME_ARCHITECTURE`.

### Deliverable N: 1.0 Contract Evidence Reconciliation & RC Handoff (Milestone 0.3.0-M7.9)

- [x] **N1.** Framework Support Matrix Reconciliation (M7.9.1): Established authoritative machine-readable specification in `config/compatibility/framework-support.json` and updated `docs/getting-started/support-matrix.md`. Formalized tested versions and canonical CI for Spring Boot 3.3.0/4.1.1, Spring Framework 6.1.0/7.0.9, Spring Security 6.3.0/7.1.1, Quarkus 3.33.0/3.39.4, Maven 3.8.0/3.9.9, Gradle 8.5/9.7.1, and codified `NO_STANDALONE_JAKARTA_INTEGRATION`.
- [x] **N2.** Generated Runtime ABI Baseline & Verifier (M7.9.2): Reconciled exact generated template runtime dependency set in `config/api-baseline/generated-template-runtime-abi.txt` to exactly 7 types, 22 methods, and 0 fields. Enhanced `scripts/verify-generated-abi.py` with direct classfile constant pool parsing and javap disassembler fallback, backed by 32 unit tests in `scripts/tests/test_verify_generated_abi.py`.
- [x] **N3.** Diagnostic Codes Baseline & Verifier (M7.9.3): Unified limit diagnostic codes (`LIMIT:AST_NODE_LIMIT`, `LIMIT:LIMIT_EXCEEDED`, `LIMIT:SOURCE_TOO_LARGE`, `LIMIT:TIME_LIMIT_EXCEEDED`), locked exactly 31 canonical codes in `config/api-baseline/diagnostic-codes-1.0.txt`, and implemented automated AST/code scanning verifier `scripts/verify-diagnostic-codes.py` with 17 unit tests in `scripts/tests/test_verify_diagnostic_codes.py`.
- [x] **N4.** Public Surface PBCIA Debt Reconciliation (M7.9.4): Reconciled the 85 remaining accidental public types in `config/architecture/public-surface-debt-registry.json` across 3 groups (AST: 41, IR: 33, Semantics: 11). Documented formal disposition `NON_BLOCKING_P2_FOR_1_0` based on 0 signature leaks into `STABLE_API`/`STABLE_SPI`, zero reachability by public consumers, `ForeachMetadata` freeze under runtime ABI, and structural module realignment deferred to post-1.0 (M22).
- [x] **N5.** Publication Topology & Manifest Header Status (M7.9.5): Created machine-readable publication topology matrix in `config/compatibility/publication-topology.json` and documented release scope in `docs/17-release-process.md` and `docs/1.0-readiness-gap-analysis.md`. Reconciled 12 production modules + parent POM (13 published artifacts), 2 non-published modules (`viet-template-tck`, `viet-template-benchmarks`), 0.2.3 `PREPARED_HELD` historical status, 0.3.0-SNAPSHOT unpublished status, absence of `Automatic-Module-Name` (`NO_AUTOMATIC_MODULE_NAME_HEADER`), and Gradle plugin distribution exclusively to Maven Central.
- [x] **N6.** Candidate Contract Manifest & Aggregator Verifier (M7.9.6): Created machine-readable contract manifest in `config/compatibility/1.0-candidate-contract.json` and authoritative aggregator verifier `scripts/verify-1.0-readiness.py` with 21 unit tests in `scripts/tests/test_verify_1_0_readiness.py`. Audits all 7 candidate release dimensions, enforces 0 P0/P1 blockers, emits `build/reports/1.0-readiness-audit.json`, and outputs verdict `READY_FOR_1_0_API_FREEZE_AND_RC_PREPARATION`.
- [x] **N7.** Documentation Convergence & 1.0 Release Candidate Handoff (M7.9.7): Reconciled latest stable release in `README.md` to `0.2.2 (Published: 2026-09-20; 0.2.3 prepared/held)`, updated `docs/1.0-readiness-gap-analysis.md` (Section 1, Section 2 Area 11 marked complete with 0 blockers, Section 5, Section 6) to reflect readiness for 1.0 API freeze and Release Candidate (RC) preparation, verified documentation suite and unit tests, and satisfied all handoff criteria.

### Deliverable O: 1.0 Release Candidate API Freeze, ABI Lock, and Tooling Contracts (Milestone M8.1)

- [x] **O1.** Pre-Freeze Corrections (M8.0): Reconciled candidate baseline terminology to `1.0 Candidate Baseline` / `READY_TO_FREEZE`, refined native qualification scope to exact tested toolchain (`Linux x86_64 Mandrel 25.0.4.1-Final, Java 25`), reconciled Quarkus publication history to `Never published. First intended release: 0.3.0+ / 1.0 candidate line`, updated stale milestone references (`M4.5` -> `POST_1_0_MAINTENANCE`), and classified Spring AOT as `JVM_AOT_QUALIFIED` across `docs/`, `config/`, and `README.md`.
- [x] **O2.** 1.0 Candidate Contract Freeze (M8.1): Formally froze 121 stable types (94 `STABLE_API` + 27 `STABLE_SPI`), 7 generated runtime ABI types (22 invoked methods, 0 fields), 31 canonical diagnostic codes, and 23 framework configuration keys (15 Spring Boot, 8 Quarkus). Formulated ADR-0020 (`docs/adr/0020-1.0-api-abi-freeze-and-rc-contract.md`) and created machine-readable configuration keys contract `config/compatibility/framework-configuration-keys.json`.
- [x] **O3.** Build-Tool Contract Freeze (M8.1): Froze Maven plugin Mojo contract (`viet-template:compile`, phase `process-classes`, 10 parameters) and Gradle plugin user contract (`io.github.minh124199.viet-template`, task `compileVietTemplates`, extension `vietTemplate`, task graph wiring). Verified dual build parity.
- [x] **O4.** Differential Compatibility Freeze (M8.1): Evaluated canonical differential test suite against Apache Velocity 2.4.1: 301 total scenarios, 295 exact matches (98.01%), 5 documented expected differences, 1 extension, 0 unsupported, 0 regressions (100.00% accounted behavior coverage). Generated reports at `build/reports/velocity-compat/`.
- [x] **O5.** Version Preparation & Publication Gate (M8.2): Set candidate version to `1.0.0-RC1` across root and child POMs; enforced publication gate with remote publishing disabled (`REMOTE_PUBLICATION_NOT_EXECUTED`).
- [x] **O6.** Release Bundle Assembly & Packaging Validation (M8.3): Assembled and verified clean release bundles across all 13 published coordinates (12 production modules + parent POM) with source and javadoc JARs.
- [x] **O7.** Staged Maven Repository & Checksum Validation (M8.4): Staged candidate repository to `build/rc-repository` and verified sha256 checksums and POM metadata.
- [x] **O8.** External Standalone Consumer Validation (M8.5): Verified clean external Maven and Gradle consumer fixtures against staged repository.
- [x] **O9.** Spring Boot & Native Qualification (M8.6): Verified Spring Boot 3.3.5 / 4.1.1 and Spring Security integration fixtures against staged repository.
- [x] **O10.** Quarkus JVM & Native Qualification (M8.7): Verified Quarkus 3.33.3 / 3.39.4 JVM and Mandrel native compilation against staged repository.
- [x] **O11.** Local Release Candidate Gate Synthesis (M8.8): Synthesized M8 gates into local qualification report with verdict `RC1_LOCALLY_STAGED_AND_QUALIFIED`.

### Deliverable P: RC1 Artifact Provenance Rebuild, Contract Evidence Reconciliation, Documentation Convergence, and Public-RC Publication Readiness (Milestone M8.9)

- [x] **P1.** Enforce One-SHA Release Provenance Invariant: Resolved provenance divergence by mandating that final staged release artifacts, checksum manifests, and consumer qualification suites derive from the authoritative final qualification SHA (`FINAL_RC1_QUALIFICATION_SHA`).
- [x] **P2.** Fix GitHub Release Workflow Pre-Release Flag: Corrected `.github/workflows/release.yml` so that pre-release versions containing hyphens (e.g. `1.0.0-RC1`) pass `--prerelease` to `gh release create`.
- [x] **P3.** Reconcile Diagnostic Codes Baseline & Parser Detail Classification: Confirmed 31 canonical codes in `config/api-baseline/diagnostic-codes-1.0.txt` as `STABLE_TOOLING_CODE` and classified internal parser codes (such as `[PARSER:UNCLOSED_DIRECTIVE]`) as `INTERNAL_DETAIL` wrapped under `SYNTAX:PARSE_ERROR`.
- [x] **P4.** Reconcile Apache Velocity Differential Evidence: Verified 301 total scenarios against Velocity 2.4.1 (295 exact matches, 5 expected differences, 1 extension, 100% accounted behavior coverage) derived from `StandardExpectations.java`.
- [x] **P5.** Correct Exception Hierarchy Documentation: Aligned documentation with actual public classes in `viet-template-api` (corrected `TemplateParseException` to `TemplateSyntaxException`).
- [x] **P6.** Reconcile Framework Support Matrix: Updated `docs/getting-started/support-matrix.md` and `README.md` to explicit Declared Minimum, RC-Tested, and Canonical RC versions for Spring Boot (3.3.0 / 3.3.5, 4.1.1 / 4.1.1), Spring Security (6.3.0 / 6.3.4, 6.5.11, 7.0.7, 7.1.1 / 7.1.1), Quarkus (3.33.0 / 3.33.3, 3.39.4 / 3.39.4), Maven (3.8.0 / 3.9.9 / 3.9.9), and Gradle (8.5 / 9.7.1 / 9.7.1).
- [x] **P7.** Reconcile Native Toolchains: Clarified empirical qualification on Linux x86_64 using Oracle GraalVM 25.0.4+7.1 (Spring Boot 3/4 Native) and Mandrel 25.0.4.1-Final (Quarkus Native).
- [x] **P8.** Reconcile Publication Topology Coordinates: Documented 13 primary project publications (12 production modules + parent POM) plus 1 generated Gradle plugin marker publication (14 staged coordinates total).
- [x] **P9.** Complete Documentation Convergence: Reconciled `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `docs/17-release-process.md`, `docs/18-roadmap.md`, `docs/1.0-readiness-gap-analysis.md`, `docs/diagnostics/error-catalog.md`, `docs/migration/velocity-differences.md`, `docs/extensions/quarkus.md`, and ADRs.
- [x] **P10.** Authorized Publication Readiness Verdict: Verified all release gates and confirmed status `RC1_READY_FOR_AUTHORIZED_PUBLICATION` while preserving `REMOTE_PUBLICATION_NOT_EXECUTED`.

### Deliverable Q: Optional Typed Template Contracts, Static Specialization & Generated Typed Java APIs (Milestone M24 / 1.1.0)

- [x] **Q1.** Public Contract Domain Model: Implemented immutable, deterministic `TemplateContract` and `TemplateParameter` in `viet-template-api` with covariant return deduplication, synthetic/bridge filtering, primitive nullability normalization, and wildcard upper-bound resolution.
- [x] **Q2.** Zero-Allocation Context Fast-Path: Added `SingleVariableRenderContext` in `viet-template-api` for zero-allocation single-variable rendering.
- [x] **Q3.** Security-Hardened Member Resolution: Added deny-lists (`DENIED_METHODS`, `DENIED_CLASSES`) in `MemberResolver` to prevent compile-time reflection/access escapes.
- [x] **Q4.** Static Specialization & Bytecode Compilation: Overhauled `BytecodeTemplateCompiler` for direct `invokevirtual`/`invokeinterface` access plans with null guards and dynamic fallback (`dynamicInvokeMethod`).
- [x] **Q5.** Typed Facade Generator & Tooling Integration: Added `TypedTemplateFacadeGenerator` and configured contract discovery and facade generation across Maven (`VietTemplateCompileMojo`) and Gradle (`VietTemplateCompileTask`, `VietTemplatePlugin`).
- [x] **Q6.** Compatibility & Verification Gates: Verified 100% test pass across all 11 modules, verified 0 breaking changes against 1.0.0 API baselines, 0 unclassified types, and 0 unregistered contracts.

### Deliverable R: Multi-Argument Static Invocation, Runtime Guards, Single-Evaluation Semantics & Root-Slot ABI Evaluation (Milestone M25 / 1.1.0)

- [x] **R1.** Deterministic Multi-Argument Method Resolution & Dynamic Overload Parity: Extended `MethodResolver` with overload resolution scoring for exact matches, primitive boxing/unboxing, and strict primitive widening. When candidate sets contain multiple overloads for the same name and arity, compilation preserves dynamic dispatch (`IrDynamicDispatch`) to guarantee 100% semantic parity with `DynamicLinker`.
- [x] **R2.** Runtime Divergence Guards & Dynamic Fallback: Generated explicit runtime type guards for receiver and argument types in `BytecodeTemplateCompiler`. Divergence at runtime safely branches to polymorphic dynamic dispatch sites.
- [x] **R3.** Single-Evaluation Invariant Guarantee: Lowered expressions such that receiver and all arguments are evaluated exactly once into local slots before null checks and type guards. Proven with `MultiArgMethodSpecializationTest` that side effects never repeat.
- [x] **R4.** Scratch-Slot Register Allocator: Implemented slot allocation assigning 1 local variable slot per reference register (`astore`/`aload`), with category-2 values (`long`/`double`) processed on the operand stack during argument evaluation, preventing scratch local slot overlapping across complex expressions.
- [x] **R5.** Security Policy Integration & Cache Isolation: Integrated `OptimizationContext` with `LinkerAccessPolicy` in `DirectAccessorBindingPass`. Verified restricted methods fail closed at compile time and runtime, and confirmed policy fingerprints are isolated in compilation caches.
- [x] **R6.** Dedicated JMH Benchmark Suite: Implemented `TypedSpecializationBenchmark` covering workloads M25_01 through M25_10, measuring 2.6× to 4.1× throughput uplift and 68.8% allocation reduction (384.8 B/op to 120.0 B/op).
- [x] **R7.** Evidence-Driven Root-Slot ABI Evaluation: Profiled `RenderContext` overhead versus direct slots. Measured negligible cost for `RenderContext.of(...)` (8.52M ops/s), confirming carrier arrays (`Object[] roots`) would increase allocation overhead without performance benefit. Formally recorded decision: **REJECTED AFTER PROFILING**.
- [x] **R8.** Qualification & Baseline Invariants: Verified 100% test pass across all modules, zero breaking changes to 1.0.0 API/ABI surfaces, full Spring/Quarkus/Native Image compatibility, and zero regressions in the 301-scenario Apache Velocity differential TCK.

### Deliverable S: Strict Typed Mode, Nullable Navigation Analysis, Build-Time Contract Diagnostics & Parity (Milestone M26 / 1.1.0)

- [x] **S1.** Opt-In Strict Contract Validation: Introduced `TypeCheckingMode` (`OFF`, `WARN`, `ERROR`) in `viet-template-api` as a frozen `STABLE_API` enum governing static semantic validation and build-time failure semantics. Defaults to `OFF` to maintain complete backward-compatible dynamic execution for unannotated templates.
- [x] **S2.** Guiding Principle & Proven Invalid Distinction: Enforced the core invariant: *Reject what can be proven wrong. Do not reject merely because the compiler cannot prove something right.* Distinguishes `PROVEN_VALID`, `PROVEN_INVALID`, and `UNKNOWN / DYNAMIC`. Statically ambiguous expressions and dynamic values never fail compilation.
- [x] **S3.** Contract Completeness & Root-Parameter Validation: Emitted `VTLS:2101` (`UNRESOLVED_ROOT`) when an authoritative contract is present; empty contracts remain permissive (`DYNAMIC / UNKNOWN`). Verified template-scoped locals (`#set`), loop variables (`#foreach`), and macro parameters (`#macro`) are not flagged as undeclared roots.
- [x] **S4.** Member & Invocation Validation: Implemented member and method validation across Java beans, records, public fields, arrays, and pseudo-properties (`size`, `length`) with Levenshtein distance typo suggestions emitting `VTLS:2104` (`PROPERTY_NOT_FOUND`) and `VTLS:2105` (`METHOD_NOT_FOUND`).
- [x] **S5.** Method Overload Parity & Argument Compatibility: Ensured ambiguous method overloads preserve dynamic dispatch without compilation error; provably incompatible argument types and method arity mismatches emit `VTLS:2103` (`TYPE_MISMATCH`) and `VTLS:2105` (`METHOD_NOT_FOUND`).
- [x] **S6.** Conservative Nullable Navigation Analysis: Evaluated nullable references (`nullable=true`) dereferenced without quiet syntax, emitting advisory non-blocking `VTLS:2107` warnings under `WARN` and `ERROR`. Quiet references (`$!user.name`) suppress warnings.
- [x] **S7.** Flow-Sensitive Nullability Refinement: Refined variable nullability to non-null in branch bodies guarded by `#if($user)` or `#if($user != null)`.
- [x] **S8.** Security Precedence & Leak Protection: Ensured security denial (`VTLSEC:2401`) takes precedence over typo suggestions. Denied methods and classes are excluded from suggestion candidate sets.
- [x] **S9.** Build Tooling & Incremental Cache Parity: Exposed `typeChecking` configuration across Maven (`VietTemplateCompileMojo`, `VietTemplateGenerateFacadesMojo`) and Gradle (`VietTemplateCompileTask`, `VietTemplateGenerateFacadesTask`, `VietTemplateExtension`). Validated incremental cache invalidation when mode changes.
- [x] **S10.** Full Qualification & Parity Verification: Verified 100% test pass across all modules, zero breaking changes to 1.0.0 API/ABI baselines, 0 unclassified types, all 7 governance verifiers green, and zero regressions in the 301-scenario Apache Velocity differential TCK.

### Deliverable T: Canonical Tooling Schema Foundation, Deterministic JSON & Parity (Milestone M27 / 1.1.0)

- [x] **T1.** Canonical Serialized Schema Specification: Established `*.vt-schema.json` specification with `format = viet-template-contract-schema/1`, `schemaVersion = 1`, and schema URL `https://viet-template.github.io/schemas/contract-v1.json`.
- [x] **T2.** Complete Type System AST: Serialized primitive types, classes, parameterized generic collections, single- and multi-dimensional arrays, wildcard bounds (`extends`, `super`), and named types into a discriminating JSON model.
- [x] **T3.** Bounded Member Discovery: Extracted record components and JavaBean getters with lexicographical property sorting and JDK package exclusion (`java.*`, `javax.*`, `jakarta.*`, `sun.*`, `jdk.*`).
- [x] **T4.** Recursive Graph Cycle Detection: Implemented cycle detection for recursive graphs (`Node -> Node`, `Parent -> Child -> Parent`) bounded by maximum depth (32).
- [x] **T5.** Security Policy Invariant: Applied `MemberAccessPolicy.standard()` at discovery time prior to serialization, blocking reflection, `getClass()`, `ClassLoader`, `Runtime`, `Process`, and engine internals.
- [x] **T6.** Zero-Dependency Deterministic JSON Serializer: Implemented high-performance serializer with UTF-8 encoding, LF line endings, 2-space indentation, and fixed key order.
- [x] **T7.** Maven Schema Generation Mojo: Implemented `VietTemplateGenerateSchemasMojo` (`generate-schemas` goal) running in `process-classes` phase.
- [x] **T8.** Gradle Schema Generation Task: Implemented `VietTemplateGenerateSchemasTask` (`generateVietTemplateSchemas` task) and exposed `schemaOutputDirectory` on `VietTemplateExtension`.
- [x] **T9.** Build Tool Parity Verification: Verified byte-for-byte SHA-256 equivalence across Maven and Gradle outputs on identical inputs.
- [x] **T10.** Full Qualification & Baseline Preservation: Verified 100% test pass, zero breaking changes to 1.0.0 API/ABI surfaces, 0 unclassified types, all 8 governance verifiers green, and zero regressions.

### Deliverable U: TypeScript Declaration Projection (.d.ts) & Build Parity (Milestone M28 / 1.1.0)

- [x] **U1.** TypeScript Projection Specification: Established `docs/schema/typescript-projection-v1.md` documenting schema-to-declaration rules, identifier escaping, and deterministic formatting.
- [x] **U2.** Zero-Dependency Projector: Implemented `TypeScriptDeclarationProjector` in `viet-template-vtl-interpreter` with handcrafted JSON parsing and strict schema envelope validation.
- [x] **U3.** Type System Projection Fidelity: Projected primitives, records, Java beans, parameterized collections, arrays, and nullable parameters to idiomatic TypeScript declarations.
- [x] **U4.** Keyword & Identifier Collision Protection: Escaped TypeScript reserved words and prototype collisions (`constructor`, `prototype`, `toString`, `valueOf`) safely with quoted property names.
- [x] **U5.** Byte-for-Byte Determinism: Guaranteed deterministic ordering, UTF-8 encoding, LF line endings, 2-space indentation, and canonical header without machine-dependent metadata.
- [x] **U6.** Maven TypeScript Generation Mojo: Implemented `VietTemplateGenerateTypeScriptMojo` (`generate-typescript` goal) in `viet-template-maven-plugin`.
- [x] **U7.** Gradle TypeScript Generation Task: Implemented `VietTemplateGenerateTypeScriptTask` (`generateVietTemplateTypeScript` task) and extension property `typeScriptOutputDirectory` in `viet-template-gradle-plugin`.
- [x] **U8.** Build Tool Parity Verification: Proved byte-for-byte SHA-256 equivalence across Maven and Gradle outputs in `VietTemplateMavenGradleParityTest`.
- [x] **U9.** Full Qualification & Baseline Preservation: Verified 100% test pass across all modules, zero breaking changes to API/ABI baselines, 0 unclassified types, and all 10 governance verifiers green.

### Deliverable V: Language Server Protocol & Developer Tooling Foundation (Milestone M29 / 1.1.0)

- [x] **V1.** Language Server Protocol Foundation Specification: Established `docs/tooling/language-server-foundation.md` documenting architecture, transport framing, lifecycle, coordinate mapping, and security model.
- [x] **V2.** Server Lifecycle Management: Implemented standard LSP lifecycle (`initialize`, `initialized`, `shutdown`, `exit`) with fail-closed state validation in `LspProtocolAdapter`.
- [x] **V3.** Document Management & Position Mapping: Implemented `TemplateDocument` and `TemplateDocumentStore` providing UTF-16 code unit position/range mapping across LF, CRLF, and CR line endings, multi-byte Unicode, and surrogate pairs.
- [x] **V4.** Context-Sensitive Completion Provider: Implemented `CompletionProvider` for directives, root schema parameters, in-template local variables, and chained member properties with deterministic sorting.
- [x] **V5.** Hover & Type Information Provider: Implemented `HoverProvider` emitting Markdown-formatted type signatures, nullability indicators, and directive documentation.
- [x] **V6.** Navigation & Definition Provider: Implemented `DefinitionProvider` supporting in-template jumps to `#set` and `#foreach` bindings as well as jumps to `*.vt-schema.json` definition lines.
- [x] **V7.** Deterministic Diagnostic Provider: Implemented `DiagnosticProvider` emitting stable diagnostic codes (`SYNTAX:PARSE_ERROR`, `VTLS:2101`, `VTLS:2104`, `VTLS:2107`, `VTLSEC:2401`).
- [x] **V8.** Canonical Schema Integration: Implemented `CanonicalSchemaResolver` for discovering and parsing sibling and configured `*.vt-schema.json` files with support for recursive models.
- [x] **V9.** Security & Runtime Isolation: Preserved zero-diff in `viet-template-api` and `viet-template-runtime`, enforced `MemberAccessPolicy.standard()`, and utilized pure Java 21 standard library with zero external dependencies.
- [x] **V10.** Full Qualification & Governance Verification: Verified 100% test pass (45/45 LSP unit tests, full ArchUnit suite), passed all 10 governance scripts, and verified clean build parity.
- [x] **V11.** Version Ordering & Stale Update Rejection: Atomic monotonic document versioning via `TemplateDocumentStore.updateIfNewer()` with `ConcurrentHashMap.compute()`. Stale updates (`version < currentVersion`) are rejected without overwriting newer state or publishing stale diagnostics across the full lifecycle (`open v1 -> change v2 -> stale change v1 [rejected] -> change v3 -> close -> reopen v1`).
- [x] **V12.** Position Encoding & Coordinate Hardening: Verified bidirectional UTF-16 code unit position/range mapping across ASCII, Vietnamese diacritics, supplementary Unicode surrogate pairs (emojis), multiline templates, CRLF vs LF line endings, and safe boundary clamping.
- [x] **V13.** Diagnostics Lifecycle & Cleared Diagnostics: Verified instant emission of empty diagnostics array (`[]`) to clear problem markers in the client editor upon repair or document close.
- [x] **V14.** Resilient Error Recovery: Protected completion, hover, and definition providers against malformed templates and incomplete syntax without unhandled exceptions.
- [x] **V15.** Cancellation & Notification Protocol Handling: Handled standard `$/cancelRequest` and unknown notifications gracefully without protocol error.

### Deliverable W: Visual Studio Code Editor Integration & Language Client Foundation (Milestone M30 / 1.1.0)

- [x] **W1.** Extension Directory & Workspace Isolation: Established `editors/vscode` with independent `package.json` ensuring core Java reactor builds remain completely free of Node.js dependencies.
- [x] **W2.** File Association & Suffix Registration: Registered language identifier `viet-template` covering `.vtl`, `.vm`, and `.vt` file extensions.
- [x] **W3.** Language Configuration: Contributed `language-configuration.json` providing comment toggles (`##`, `#* *#`), auto-closing pairs, surrounding pairs, brackets, and block directive indentation rules.
- [x] **W4.** TextMate Lexical Grammar: Contributed `syntaxes/viet-template.tmLanguage.json` covering directives, formal/quiet references, strings, escape sequences, numbers, operators, and comments.
- [x] **W5.** Language Client Architecture: Integrated official `vscode-languageclient` over standard I/O (stdio) transport.
- [x] **W6.** Cross-Platform Java Discovery: Implemented deterministic discovery hierarchy (`vietTemplate.java.home` -> `JAVA_HOME` -> `PATH`) validating Java 21+ with actionable notifications.
- [x] **W7.** Server Launch Strategy & Bundling: Supported bundled server JAR (`server/viet-template-lsp.jar`), user-configured custom JAR path, and development classpath fallback.
- [x] **W8.** Extension Configuration & Commands: Exposed namespaced settings (`vietTemplate.java.home`, `vietTemplate.languageServer.jarPath`, `trace`, `vmArgs`) and interactive restart command `vietTemplate.restartServer`.
- [x] **W9.** Unit & End-to-End Test Suite: Implemented 21 automated tests spanning unit tests (Java runtime discovery, server launcher, configuration) and end-to-end LSP smoke tests exercising the real Java LSP process over stdio.
- [x] **W10.** Offline VSIX Packaging & Governance: Qualified offline `.vsix` packaging via `@vscode/vsce` and implemented governance verification in `scripts/verify-vscode-extension.py`.

### Deliverable X: IntelliJ IDEA Editor Integration & Language Client Foundation (Milestone M31 / 1.1.0)

- [x] **X1.** Plugin Directory & Workspace Isolation: Established `editors/intellij` with independent `build.gradle.kts` and `settings.gradle.kts` ensuring root Maven and Gradle reactor builds remain completely free of IntelliJ SDK dependencies.
- [x] **X2.** File Type Registration & Vector Icon: Registered `VietTemplateFileType` covering `.vtl`, `.vm`, and `.vt` file extensions with dedicated vector icons.
- [x] **X3.** Lexical Syntax Highlighting & Tokenizing: Implemented native IntelliJ lexer (`VietTemplateLexer`) and syntax highlighter (`VietTemplateSyntaxHighlighter`) covering directives, formal/quiet references, strings, numbers, operators, and comments.
- [x] **X4.** Commenter Integration: Contributed `VietTemplateCommenter` supporting line comments (`##`) and block comments (`#* *#`) with native IntelliJ comment shortcut actions.
- [x] **X5.** Settings & Java Runtime Discovery: Implemented persistent configuration (`VietTemplateSettings`, `VietTemplateConfigurable`) and deterministic Java 21+ discovery hierarchy (`Java Home Path` -> `JAVA_HOME` -> `PATH`).
- [x] **X6.** Stdio Language Client & Project Lifecycle: Built stdio JSON-RPC 2.0 language client with background stderr draining, project-level lifecycle service (`VietTemplateLspServerManager`), and leak-free process termination.
- [x] **X7.** Extension Point Adapters & Actions: Connected language server diagnostics to `ExternalAnnotator`, autocompletion to `CompletionContributor`, hover documentation to `DocumentationProvider`, definition navigation to `GotoDeclarationHandler`, and contributed `Restart Viet Template Language Server` menu action.
- [x] **X8.** Server Bundling & Offline Packaging: Configured `bundleLspServer` task packaging core engine classes into `server/viet-template-lsp.jar` and built installable plugin ZIP archive `viet-template-intellij-1.1.0.zip`.
- [x] **X9.** Comprehensive Test Suite: Implemented 29 automated tests covering unit tests (Java runtime discovery, server launcher, configuration), platform tests, and real LSP integration tests exercising `VietTemplateLanguageServer` over stdio with zero process leaks.
- [x] **X10.** Governance & Path Safety: Implemented `scripts/verify-intellij-plugin.py` and unit tests in `scripts/tests/test_verify_intellij_plugin.py` validating descriptor metadata, build configuration, packaging, and zero hardcoded machine paths.

### Deliverable Y: Build-Time Template Validation (Milestone M32 / 1.2.0)

- [x] **Y1.** Shared Validation Core Architecture: Introduced `TemplateValidator`, `TemplateValidationRequest`, and `TemplateValidationResult` in `io.github.minh124199.viettemplate.validation` (`viet-template-vtl-interpreter`).
- [x] **Y2.** In-Memory Syntax & Contract Analysis: Executes syntax parsing, `#*contract ... *#` semantic typing analysis, and static `#parse`/`#include` dependency validation purely in-memory without runtime rendering or template execution.
- [x] **Y3.** Maven Plugin Validation Goal: Implemented `viet-template:validate` (`VietTemplateValidateMojo`) bound by default to the Maven `validate` lifecycle phase with configurable `failOnWarning`, `validateDependencies`, contract schemas, includes/excludes, and VTL compilation profiles.
- [x] **Y4.** Gradle Plugin Validation Task: Implemented `validateVietTemplates` (`VietTemplateValidateTask`) registered under the `verification` task group and wired into Gradle's standard lifecycle `check` task.
- [x] **Y5.** Maven & Gradle Parity: Verified 100% feature and diagnostic parity across Maven and Gradle validation tooling via `VietTemplateMavenGradleParityTest`.
- [x] **Y6.** Governance & Public Surface Enforcement: Classified all validation public types as `STABLE_API` or `BUILD_TOOL_ENTRYPOINT` with zero non-stable signature leaks, passing all API baseline and framework entrypoint checks.

### Deliverable Z: Compiler Explanation Tooling (Milestone M33 / 1.2.0)

- [x] **Z1.** Shared Decision Logic Extraction: Extracted write output specialization decision logic (`OutputSpecializationDecider`, `WriteDispatchDecision`, `WriteDispatchKind`, `OutputSpecializationContext`) from `BytecodeTemplateCompiler` into shared immutable compiler models to guarantee `explained decision == compiled decision`.
- [x] **Z2.** Shared Explanation Core Engine: Implemented `TemplateExplainer`, `TemplateExplainRequest`, `SingleTemplateExplanation`, `ExpressionExplanation`, and deterministic JSON/text formatters in `io.github.minh124199.viettemplate.explanation` (`viet-template-vtl-interpreter`).
- [x] **Z3.** In-Memory Decision Inspection: Analyzes AST, semantic analysis results, IR lowering, member resolution, and bytecode specialization without running templates or creating a second analyzer.
- [x] **Z4.** Maven Plugin Explain Goal: Implemented `viet-template:explain` (`VietTemplateExplainMojo`) with CLI human-readable logging, file output, JSON/text formats, template/line/column filtering, and `failOnDynamicFallback`.
- [x] **Z5.** Gradle Plugin Explain Task: Implemented `explainVietTemplates` (`VietTemplateExplainTask`) registered under the `help` task group with configuration-cache compatibility and full parity with Maven options.
- [x] **Z6.** Parity Verification & Governance: Verified bit-for-bit and structured decision parity via `VietTemplateMavenGradleParityTest`, classified public surface types (`STABLE_API`, `BUILD_TOOL_ENTRYPOINT`), and qualified all governance gates with 0 signature leaks.

### Deliverable AA: Velocity Migration Report Tooling (Milestone M34 / 1.2.0)

- [x] **AA1.** Shared Migration Analysis Engine: Implemented `TemplateMigrationAnalyzer`, `TemplateMigrationRequest`, `MigrationReport`, `SingleTemplateMigration`, `MigrationFinding`, `MigrationSummary`, and package-private `MigrationRuleRegistry` in `io.github.minh124199.viettemplate.migration` (`viet-template-vtl-interpreter`).
- [x] **AA2.** Tested and Documented Invariant: Enforced `migration finding == documented and tested compatibility fact`. Every rule maps to concrete differential TCK scenarios and authoritative compatibility specification sections without heuristic speculation.
- [x] **AA3.** Taxonomy & Classifications: Implemented 4 severities (`INFO`, `WARNING`, `ERROR`, `BLOCKER`), 7 compatibility classifications (`EXACT_COMPATIBLE`, `COMPATIBLE_WITH_CONFIGURATION`, `KNOWN_BEHAVIOR_DIFFERENCE`, `DYNAMICALLY_UNVERIFIABLE`, `SECURITY_RESTRICTED`, `VIET_TEMPLATE_EXTENSION`, `UNSUPPORTED`), and 4 readiness states (`READY`, `READY_WITH_WARNINGS`, `ATTENTION_REQUIRED`, `BLOCKED`).
- [x] **AA4.** Deterministic Formatters: Implemented deterministic text and JSON (`formatVersion = 1`) reporting with sorted logical template IDs, source spans, and summary statistics.
- [x] **AA5.** Maven Plugin Migration Report Goal: Implemented `viet-template:migration-report` (`VietTemplateMigrationReportMojo`) supporting CLI invocation, JSON/text formats, file output, `failOnBlocker`, `failOnWarning`, and strict reference profiling.
- [x] **AA6.** Gradle Plugin Migration Report Task: Implemented `migrationReport` (`VietTemplateMigrationReportTask`) registered in the `help` group as an untracked, non-cacheable informational task.
- [x] **AA7.** Build Tool Parity & Differential Oracle Tests: Verified identical findings and SHA-256 JSON report parity between Maven and Gradle via `VietTemplateMavenGradleParityTest`. Verified differential oracle qualifications against live Apache Velocity 2.4.1 in `VelocityMigrationDifferentialTest`.
- [x] **AA8.** Governance & Public Surface Verification: Registered 14 stable migration types in `config/api-baseline/public-surface-classification.txt` and `config/api-baseline/1.0-aot-public-api.txt`, registered Maven/Gradle entrypoints, and passed all 11 governance verification scripts with zero signature leaks.

### Deliverable BB: Cross-Language Schema Interoperability (Milestone M35 / 1.2.0)

- [x] **BB1.** Canonical Schema Model Core: Implemented `CanonicalSchemaModel` in `io.github.minh124199.viettemplate.schema` (`viet-template-vtl-interpreter`) defining sealed `TypeRef` hierarchy, `PropertyDef`, `TypeDef`, `ParameterDef`, and `CanonicalSchema`. Enforced the core invariant: all external schema formats normalize into one canonical representation before downstream analysis.
- [x] **BB2.** Zero-Dependency JSON Schema Import: Implemented `JsonSchemaImporter` parsing Draft 7 / 2020-12 compatible schemas with 1-based position tracking, cycle-bounded `$defs` resolution, distinct handling of required/optional/nullable states, and offline rejection of remote HTTP/HTTPS references.
- [x] **BB3.** Handcrafted TypeScript Declaration Parser: Implemented `TypeScriptSchemaImporter` tokenizing and parsing TypeScript `interface`, `type`, `enum`, literal unions, arrays, maps, and nested object types without Node.js, npm, or JavaScript runtime execution.
- [x] **BB4.** Java Domain Model Normalization: Implemented `JavaModelSchemaImporter` introspecting records, JavaBeans, interfaces, enums, collections, and companion contracts using class metadata and member access policies safely.
- [x] **BB5.** Multi-Format Schema Resolution: Implemented `CanonicalSchemaResolver` auto-discovering companion schema formats (`*.vt-schema.json`, `*.schema.json`, `*.d.ts`, `*.contract`) and integrated resolution into M32 validation (`TemplateValidator`), M33 explanation (`TemplateExplainer`), and LSP tooling.
- [x] **BB6.** Shape-Only Semantic Typing & AOT Safety: Extended `ModelSchema` and `MemberResolver` to resolve shape-only properties with typo suggestions, configuring dynamic member resolution to prevent unsafe static bytecode getter generation while preserving strict compile-time verification.
- [x] **BB7.** Deterministic TypeScript Projection: Extended `TypeScriptDeclarationProjector` to project `CanonicalSchema` instances into deterministic `.d.ts` declaration files with 100% round-trip fidelity and golden fixture compatibility.
- [x] **BB8.** Build Tool Parity: Updated Maven `viet-template:generate-typescript` and Gradle `generateVietTemplateTypeScript` tasks to discover and project schemas uniformly, verified via `VietTemplateMavenGradleParityTest`.
- [x] **BB9.** Governance & Public Surface Enforcement: Registered 27 public schema types as `STABLE_API` in `public-surface-classification.txt` and `1.0-aot-public-api.txt`, registered internal bridges (`ModelSchema$Builder`, `CanonicalModelSchemaConverter`), and qualified all governance verification scripts with zero signature leaks.

### Deliverable CC: Java Source Navigation & Cross-Language Definition (Milestone M38 / 1.3.0)

- [x] **CC1.** Direct Cross-Language Definition Navigation: Extended `DefinitionProvider` in `viet-template-vtl-interpreter` to resolve template references ($user, $user.name) directly to exact Java declarations when backed by JVM models and Java source exists in the workspace.
- [x] **CC2.** Supported Java Member Patterns: Accurately navigates to record header components, explicit record accessor methods (with precedence over header components), JavaBean getters (`getXxx()`), boolean getters (`isXxx()`), public fields, and inherited members on declaring superclasses.
- [x] **CC3.** Nested & Inner Class Support: Seamlessly resolves member declarations across nested and inner classes (`Outer$Inner` and `Outer.Inner`).
- [x] **CC4.** JDK Compiler Tree AST Parser: Implemented package-private `JavaSourceDeclarationParser` using JDK Compiler Tree API (`com.sun.source.*`) for robust, zero-regex AST declaration extraction with exact UTF-16 line/column coordinates across CRLF and LF.
- [x] **CC5.** Deterministic Workspace Source Locator: Implemented package-private `WorkspaceJavaSourceLocator` discovering Java source roots across Maven multi-modules and Gradle subprojects with upward probing and strict path traversal defenses (`isSafeJavaTypeName`).
- [x] **CC6.** Cache Invalidation & File Watching: Integrated file watching (`workspace/didChangeWatchedFiles`) for `.java` files into `WorkspaceSchemaIndex` and `TemplateLanguageService`, evicting cached AST declarations on file change or deletion.
- [x] **CC7.** Graceful Fallbacks & Provenance Invariant: When Java source is missing, gracefully returns an empty list (`definition unavailable`) for pure Java models or falls back to companion `.contract` files, never emitting synthetic or false locations.
- [x] **CC8.** Preservation of Existing Definitions: 100% preserved in-template variable declarations (`#set`, `#foreach`) and non-JVM schemas (`.d.ts`, `.schema.json`, `.vt-schema.json`).
- [x] **CC9.** Zero Public API Leaks & Frozen Baseline: Added exactly 0 new `STABLE_API` or `STABLE_SPI` types; all locator and parser classes are package-private in `io.github.minh124199.viettemplate.lsp`, passing all 12 governance verification scripts cleanly.

### Deliverable DD: Cross-Language Find References & Workspace Symbol Graph (Milestone M39 / 1.3.0)

- [x] **DD1.** Semantic Cross-Language Find References: Implemented `textDocument/references` in `viet-template-vtl-interpreter` advertising `referencesProvider: true` across all template files (`.vtl`, `.vm`, `.vt`) in the workspace, indexed strictly on compiler-proven semantic binding truth with zero false-positive string collisions.
- [x] **DD2.** Canonical Internal Symbol Identity Model: Implemented package-private sealed `WorkspaceSymbolKey` hierarchy distinguishing `JvmMemberSymbolKey` (binary class name, member kind, name, descriptor, parameter count), `SchemaMemberSymbolKey` (schema source, type name, property name), `TemplateLocalSymbolKey` (URI, name, definition span), and `RootParameterSymbolKey` (source/URI, name).
- [x] **DD3.** Inherited vs Overridden Member Convergence: Inherited JVM members index under the declaring superclass (`BaseUser#getName()`), converging references across subclasses; overridden members index to the subclass override, keeping overrides isolated.
- [x] **DD4.** Member Kind Fidelity: Accurately distinguishes record components, explicit accessors, JavaBean getters, boolean getters, and public fields without merging symbols by superficial property name spelling.
- [x] **DD5.** Thread-Safe Incremental Reference Index: Implemented `WorkspaceReferenceIndex` with `ReentrantReadWriteLock` supporting atomic per-template eviction and replacement on document open/change/close, and automatic dependent template reindexing on companion schema changes.
- [x] **DD6.** Exact Identifier Ranges: Emits exact identifier ranges (e.g. `name` in `$customer.name`, omitting the leading `.`) for precise editor highlight coordinates.
- [x] **DD7.** Declaration Inclusion Support: Fully honors `ReferenceContext.includeDeclaration`, querying `DefinitionProvider` to dynamically prepend declarations when requested.
- [x] **DD8.** Editor Integration Verification: Updated VS Code smoke tests and IntelliJ IDEA real-server client integration tests to verify live `textDocument/references` execution against real server processes.
- [x] **DD9.** Zero Public API Leaks & Frozen Baseline: Added exactly 0 new `STABLE_API` or `STABLE_SPI` types; all symbol key and indexing classes remain internal package-private in `io.github.minh124199.viettemplate.lsp`, passing all 12 governance verification scripts cleanly.

### Deliverable EE: Safe Cross-Language Rename & Refactoring (Milestone M40 / 1.3.0)

- [x] **EE1.** Semantic Pre-Flight Check (`textDocument/prepareRename`): Implemented `prepareRename` advertising `renameProvider: { "prepareProvider": true }`, determining symbol eligibility, extracting exact token ranges, and returning `null` for non-renameable targets.
- [x] **EE2.** Semantic Symbol Resolution Alignment: Shared AST cursor resolution between `ReferenceProvider` and `RenameProvider` via package-private `WorkspaceSymbolResolver`, eliminating code duplication and guaranteeing identical symbol identity.
- [x] **EE3.** Conservative JVM Member Rejection: Enforced the conservative boundary rejecting rename of JVM-backed getters, fields, and record components with explicit user-facing diagnostic guidance.
- [x] **EE4.** Scope-Isolated Template-Local Variable Rename: Implemented rename for `#set` and `#foreach` loop variables, generating atomic edits for declarations and references with strict lexical scope isolation.
- [x] **EE5.** Cross-Language Schema Property Rename: Implemented rename for TypeScript `.d.ts` and `.contract` properties, emitting synchronized edits across declaration files and all workspace template references.
- [x] **EE6.** Deterministic Conflict & Collision Prevention: Implemented comprehensive conflict detection catching invalid VTL identifiers, reserved keywords, local variable collisions, loop shadowing, and schema property name duplicates before edit emission.
- [x] **EE7.** Atomic & Non-Overlapping `WorkspaceEdit` Generation: Implemented package-private `WorkspaceEdit` and `TextEdit` records with deterministic URI and coordinate sorting, deduplication, and overlapping edit detection.
- [x] **EE8.** Editor Client Verification: Verified `textDocument/prepareRename` and `textDocument/rename` in VS Code smoke tests and IntelliJ IDEA real-server client integration tests.
- [x] **EE9.** Zero Public API Leaks & Frozen Baseline: Added exactly 0 new `STABLE_API` or `STABLE_SPI` types; all rename types remain package-private in `io.github.minh124199.viettemplate.lsp`, passing all 12 governance verification scripts cleanly.

### Deliverable FF: Workspace Schema Intelligence & Symbol Search (Milestone M41 / 1.3.0)

- [x] **FF1.** Workspace Symbol Search Endpoint (`workspace/symbol`): Implemented LSP `workspace/symbol` handler advertising `workspaceSymbolProvider: true`, returning deterministic projections of workspace semantic symbols without query-time filesystem walking.
- [x] **FF2.** Canonical Semantic Symbol Categories: Indexes canonical schema types (`Class`, `Interface`, `Enum`, `Struct`), schema and model properties (`Property`, `Field`, `Method`), JVM-backed model types/members with exact Java coordinates via `WorkspaceJavaSourceLocator`, template root parameters, and template macros (`#macro` / `Function`).
- [x] **FF3.** Noise Suppression & Policy Enforcement: Template-local variables (`#set`, `#foreach`) are excluded from workspace symbol search by default; `MemberAccessPolicy` denies and untyped dynamic members are filtered out.
- [x] **FF4.** In-Memory Thread-Safe Symbol Index (`WorkspaceSymbolIndex`): Thread-safe index protected by `ReentrantReadWriteLock` with reference-counted deduplication, atomic incremental replacement on document/schema lifecycle events, and zero query-time disk I/O.
- [x] **FF5.** Multi-Tier Deterministic Matching: Implemented deterministic ranking across 9 tiers (exact case-sensitive, exact case-insensitive, qualified exact, simple prefix case-sensitive, simple prefix case-insensitive, qualified prefix, simple substring, qualified substring, camelCase) with stable tie-breaking and 500-result capping.
- [x] **FF6.** Exact Declaration Coordinate Guarantee: Every symbol result includes an exact, verified `LocationInfo` pointing to source or schema declaration; zero synthetic locations.
- [x] **FF7.** ClassLoader Leak Prevention: Long-lived symbol entries store string descriptors and binary class names; zero strong references to application `Class<?>` instances.
- [x] **FF8.** Editor Client Verification: Verified `workspace/symbol` in VS Code smoke tests and IntelliJ IDEA real-server client integration tests.
- [x] **FF9.** Zero Public API Leaks & Frozen Baseline: Added exactly 0 new `STABLE_API` or `STABLE_SPI` types; all symbol index, key, and protocol classes remain package-private in `io.github.minh124199.viettemplate.lsp`, passing all 12 governance verification scripts cleanly.



