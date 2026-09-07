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
- [x] Align Maven dependency versions with `gradle/libs.versions.toml` (JUnit 5.11.4, AssertJ 3.27.2, ArchUnit 1.3.0, google-java-format 1.28.0).
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

- [ ] Define `TemplateOutput` with minimal operations.
- [ ] Writer-backed implementation.
- [ ] UTF-8 OutputStream-backed implementation.
- [ ] String-building implementation for convenience APIs/tests.
- [ ] Static UTF-8 byte chunk support.
- [ ] Escaper SPI.
- [ ] HTML text escaping.
- [ ] HTML attribute escaping policy.
- [ ] URI/JavaScript/CSS contextual escaping decision explicitly documented; do not pretend generic HTML escaping is sufficient in every context.
- [ ] Efficient primitive-number output.
- [ ] CharSequence output without forced `String` creation.
- [ ] Back-pressure story documented for synchronous servlet output; reactive support is separate/non-goal until designed.

### Allocation target

For a simple typed template with an externally supplied output buffer, compiler/runtime overhead should approach zero allocations beyond allocations required by the user's own getters/data conversions.

---

## 9. Dynamic linker

- [ ] Define `MemberKey` by operation/name/arity.
- [ ] Define access policy as an input to linkage.
- [ ] Implement monomorphic inline cache.
- [ ] Extend to small polymorphic inline cache only after benchmarks.
- [ ] Use `MethodHandle`-based targets where safe and measurable.
- [ ] Implement megamorphic fallback.
- [ ] Bound every cache.
- [ ] Make ClassLoader references weak/collectable where necessary.
- [ ] Add cache statistics.
- [ ] Verify denied members never become linkable through cache reuse.
- [ ] Stress test concurrent linkage.
- [ ] Stress test redeploy/classloader churn.

### `invokedynamic` gate

Do **not** add `invokedynamic` only because it is sophisticated. Add it only if a benchmark demonstrates a material benefit over the simpler explicit PIC design on supported JVMs.

---

## 10. Optimizer

Implement in this order and benchmark each pass independently:

- [ ] remove unreachable IR.
- [ ] constant folding.
- [ ] boolean simplification.
- [ ] merge adjacent static output.
- [ ] pre-encode static UTF-8 chunks.
- [ ] eliminate redundant local loads/conversions.
- [ ] bind statically known accessors.
- [ ] specialize primitive operations.
- [ ] specialize common loops where semantics remain identical.
- [ ] inline small static macros/includes.
- [ ] split large generated render methods.
- [ ] optional escape-hoisting only when proven safe.

### Optimization invariant

Every optimization must preserve:

1. observable output,
2. evaluation order,
3. side effects permitted by the profile,
4. exception classification/source location as far as specified,
5. security capability boundaries.

---

## 11. AOT bytecode backend

- [ ] Define generated class ABI.
- [ ] Define deterministic generated class naming.
- [ ] Generate constructor only when required.
- [ ] Generate `render(...)` with direct output calls.
- [ ] Map primitive VTypes to JVM primitive descriptors.
- [ ] Emit direct calls for statically resolved access plans.
- [ ] Emit dynamic linker calls only for explicitly dynamic sites.
- [ ] Emit source-map metadata in a sidecar index or class metadata.
- [ ] Verify generated classes with JVM verification plus project-specific tests.
- [ ] Handle method-size limits with deterministic splitting.
- [ ] Generate no Java source in the direct bytecode backend.

### JDK strategy

- [ ] Runtime remains Java 17-compatible.
- [ ] `compiler-jdk25` can use `java.lang.classfile` on JDK 25.
- [ ] Prototype the exact class-file target versions produced by that module and test on Java 17/21/25; do not assume cross-target behavior.
- [ ] Keep compiler SPI isolated so another backend (e.g. ASM) can exist if needed.

---

## 12. Template repository/cache/hot reload

- [ ] Template IDs are normalized and traversal-safe.
- [ ] Classpath repository.
- [ ] Filesystem repository only where explicitly configured.
- [ ] Composite repository with deterministic precedence.
- [ ] Compile cache keyed by source fingerprint + compiler config + model signature + compiler version.
- [ ] Negative caching policy.
- [ ] Development watcher/debounce.
- [ ] Atomic replacement of compiled template handles.
- [ ] No global ClassLoader leaks.
- [ ] Production mode can reject runtime compilation entirely.

---

## 13. Security hardening

- [ ] Deny class loading/reflection/system/runtime/process by default.
- [ ] Deny Spring `ApplicationContext`, bean factory, raw request/response/session exposure by default.
- [ ] Safe-profile allowlist rules.
- [ ] Resource root confinement.
- [ ] Include/parse traversal protection.
- [ ] Maximum source size.
- [ ] Maximum AST nodes.
- [ ] Maximum expression depth.
- [ ] Maximum macro/include recursion.
- [ ] Configurable loop/output/time budgets.
- [ ] Disable `#evaluate` by default.
- [ ] Raw output is explicit and auditable.
- [ ] Security regression corpus.
- [ ] Fuzz parser/linker/path resolution.

---

## 14. Public API

- [ ] `TemplateEngine`.
- [ ] `Template`.
- [ ] `CompiledTemplate`.
- [ ] `TemplateRepository`.
- [ ] `TemplateCompiler`.
- [ ] `TemplateModelDescriptor`.
- [ ] `RenderContext`.
- [ ] `TemplateOutput`.
- [ ] `Escaper`.
- [ ] `MemberAccessPolicy`.
- [ ] `TemplateDiagnostic`.
- [ ] `TemplateException` taxonomy.
- [ ] Extension API separated from internal compiler API.
- [ ] Thread-safety guarantees in Javadoc.
- [ ] Compatibility/versioning policy.

---

## 15. Maven/Gradle AOT tooling

### Maven

- [ ] `compileTemplates` goal.
- [ ] incremental cache.
- [ ] generated resources/class output integration.
- [ ] model signature input.
- [ ] fail-on-warning option.
- [ ] compatibility profile option.
- [ ] machine-readable compile report.

### Gradle

- [ ] cacheable task.
- [ ] incremental inputs/outputs.
- [ ] worker isolation if compilation benefits from it.
- [ ] configuration-cache compatibility.
- [ ] toolchain selection.

### Reproducibility

- [ ] same input produces byte-identical output where practical.
- [ ] no absolute build path in generated artifacts.
- [ ] stable constant ordering.
- [ ] stable class naming.

---

## 16. Spring Framework 7 integration

- [ ] `VelocityLikeView` / final product naming.
- [ ] `ViewResolver`.
- [ ] stream directly to response output where safe.
- [ ] content type/charset behavior.
- [ ] locale-aware template resolution where enabled.
- [ ] model binding without copying when possible.
- [ ] explicit policy for request/session attributes.
- [ ] no automatic application-context exposure.
- [ ] exception mapping with template source location.
- [ ] MVC integration tests using MockMvc and real server tests.

---

## 17. Spring Boot 4 integration

- [ ] dedicated `*-spring-boot-autoconfigure` module.
- [ ] dedicated starter.
- [ ] configuration properties namespace owned by the project.
- [ ] `@AutoConfiguration`.
- [ ] classpath/web-application/property conditions.
- [ ] auto-config imports metadata.
- [ ] user bean backs off auto-configuration.
- [ ] devtools/hot reload integration decision.
- [ ] AOT runtime hints only where genuinely required.
- [ ] native-image smoke test.
- [ ] Boot 4.1 stable CI lane.
- [ ] preview lane for the next Spring/Boot generation without making preview APIs public dependencies.

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

## 19. Benchmark release gate

- [ ] JMH harness checked into repository.
- [ ] exact JDK flags recorded.
- [ ] CPU/JVM/OS metadata recorded.
- [ ] warmup/measurement/fork counts recorded.
- [ ] template compilation excluded from render-only benchmark.
- [ ] separate cold compile/startup benchmark.
- [ ] compare allocation/op as a first-class metric.
- [ ] p50/p95/p99 where end-to-end harness is used.
- [ ] publish source templates used for every engine.
- [ ] inspect generated assembly/JIT compilation only for diagnosed hotspots, not as marketing evidence by itself.

### Initial success gates

Do not advertise ratios before measurement. Internal stretch targets:

- [ ] typed simple render within 15% of handwritten renderer throughput after warmup.
- [ ] very low engine overhead allocation in typed path.
- [ ] meaningful throughput/allocation win over Velocity on dynamic migration workload.
- [ ] competitive with Qute typed mode and jte on typed workload.
- [ ] no performance regression >10% without an accepted benchmark note/ADR.

---

## 20. Documentation and developer experience

- [ ] language reference.
- [ ] migration guide from Velocity.
- [ ] compatibility matrix.
- [ ] secure-template guide.
- [ ] typed-model guide.
- [ ] Maven/Gradle setup.
- [ ] Spring MVC/Boot setup.
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
6. Spring Framework 7 and Spring Boot 4 integration are production-tested.
7. Java 17/21/25 runtime compatibility is CI-verified for modules claiming it.
8. Build-time compilation is incremental and reproducible.
9. Generated-code/source diagnostics are actionable.
10. No benchmark or compatibility marketing claim exceeds the evidence produced by the checked-in suites.
