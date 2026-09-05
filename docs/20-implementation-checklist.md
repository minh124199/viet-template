# 20 — Implementation Checklist

Status legend:

- `[ ]` not started
- `[-]` in progress
- `[x]` complete
- `[!]` blocked / decision required

The checklist is intentionally ordered so that correctness is established before optimization and framework integration.

## 0. Repository bootstrap

- [ ] Create multi-module build.
- [ ] Configure Java toolchains for 17, 21, and 25.
- [ ] Enable reproducible archives and deterministic generated-source paths.
- [ ] Configure formatter, static analysis, license checks, forbidden APIs, and dependency locking.
- [ ] Configure unit-test, integration-test, TCK, fuzz, and JMH source sets.
- [ ] Add `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, release process, and compatibility policy.
- [ ] Add CI jobs for Linux, Windows, and macOS.
- [ ] Add CI matrix for JDK 17/21/25.
- [ ] Add binary/API compatibility checking after the first public release.

### Exit criteria

A clean checkout can run one command and execute formatting checks, unit tests, integration tests, and a smoke benchmark.

---

## 1. Source model and diagnostics

- [ ] Implement `SourceId`.
- [ ] Implement immutable `SourceText`.
- [ ] Implement UTF-16 offset ↔ line/column mapping without rescanning the whole file.
- [ ] Implement `SourceSpan(startOffset, endOffset)`.
- [ ] Implement diagnostic severity: INFO/WARNING/ERROR.
- [ ] Implement stable diagnostic codes.
- [ ] Implement related locations / notes.
- [ ] Implement source excerpt rendering.
- [ ] Verify CRLF, LF, tabs, supplementary Unicode code points, and empty files.

### Required tests

- [ ] Span at file start/end.
- [ ] Span crossing lines.
- [ ] Malformed UTF-16 input handling policy.
- [ ] Exact line/column diagnostics for all parser fixtures.

---

## 2. Lexer

### Token families

- [ ] Raw text.
- [ ] `$identifier`.
- [ ] `${identifier}`.
- [ ] `$!identifier`.
- [ ] `$!{identifier}`.
- [ ] Member access `.`.
- [ ] Index brackets.
- [ ] Parentheses.
- [ ] Comma.
- [ ] String literals.
- [ ] Integer/floating literals.
- [ ] Boolean/null literals where supported by the selected compatibility profile.
- [ ] Operators.
- [ ] Directive prefix `#`.
- [ ] Comments.
- [ ] Escapes.

### Lexer modes

- [ ] TEXT mode.
- [ ] REFERENCE mode.
- [ ] DIRECTIVE/EXPRESSION mode.
- [ ] STRING mode.
- [ ] COMMENT mode.

### Correctness cases

- [ ] `Price: $price`.
- [ ] `Price: ${price}USD`.
- [ ] Quiet references.
- [ ] Escaped `$` and `#`.
- [ ] Directive text adjacent to literal text.
- [ ] Nested parentheses in directives.
- [ ] Quoted strings containing `$`/`#`.
- [ ] Unicode identifiers according to the documented identifier policy.
- [ ] Unterminated strings/comments/references.

### Performance cases

- [ ] Lexer performs one linear scan in normal cases.
- [ ] Avoid substring allocation for every token; represent tokens by source slices.
- [ ] No regex in the hot lexer loop unless benchmarked and justified.

---

## 3. Parser and AST

### Expressions

- [ ] Literals.
- [ ] Variable reference.
- [ ] Quiet reference.
- [ ] Formal reference.
- [ ] Property chain.
- [ ] Method call where profile permits it.
- [ ] Index access.
- [ ] Unary expressions.
- [ ] Arithmetic expressions.
- [ ] Comparisons.
- [ ] Boolean operations with short-circuiting.
- [ ] Parenthesized expressions.
- [ ] Ranges if supported.

### Statements/directives

- [ ] Text node.
- [ ] Output expression.
- [ ] `#set`.
- [ ] `#if/#elseif/#else/#end`.
- [ ] `#foreach/#end`.
- [ ] `#break`.
- [ ] `#stop` policy.
- [ ] `#include`.
- [ ] `#parse`.
- [ ] `#macro` declaration/invocation.
- [ ] `#define` if included in migration profile.
- [ ] `#evaluate` only in the explicit dynamic/migration capability.

### Parser architecture

- [ ] Handwritten recursive-descent or Pratt parser for expressions.
- [ ] Explicit precedence table.
- [ ] Bounded recursion/depth checks.
- [ ] Error recovery around directive boundaries.
- [ ] AST nodes are immutable.
- [ ] AST nodes always carry source spans.
- [ ] AST contains syntax, not runtime reflection objects.

### Exit criteria

All phase-1 syntax fixtures parse to golden ASTs and malformed fixtures produce stable diagnostics rather than exceptions escaping the compiler.

---

## 4. Compatibility oracle and differential runner

- [ ] Create a test harness that renders a fixture with Apache Velocity and Viet Template.
- [ ] Normalize only explicitly documented non-semantic differences.
- [ ] Capture output, exception type/classification, and relevant side effects.
- [ ] Version fixtures by Velocity baseline.
- [ ] Categorize each fixture: compatible, intentionally different, unsupported, extension.
- [ ] Require an issue/ADR for every intentional incompatibility.

### Fixture categories

- [ ] References/property access.
- [ ] Maps/lists/arrays.
- [ ] Null/missing values.
- [ ] Truthiness.
- [ ] Arithmetic/coercion.
- [ ] Method overloads.
- [ ] `#set`.
- [ ] `#if`.
- [ ] `#foreach` metadata.
- [ ] Macro scope/arguments.
- [ ] Includes/parsing.
- [ ] Escapes/comments.
- [ ] Dynamic evaluation.
- [ ] Error behavior.

### Exit criteria

The project can generate a machine-readable compatibility report such as:

```text
Velocity baseline: 2.4.x
Fixtures: 1,250
Exact pass: 1,181
Intentional difference: 43
Unsupported: 26
Unexpected regression: 0
```

---

## 5. Semantic analyzer and model typing

- [ ] Implement symbol scopes.
- [ ] Implement local variables introduced by `#set`.
- [ ] Implement loop variable scopes and loop metadata.
- [ ] Implement macro parameter scopes.
- [ ] Implement `VType` hierarchy.
- [ ] Represent primitive numeric types separately from boxed/reference types where useful.
- [ ] Represent nullable/unknown/dynamic states explicitly.
- [ ] Resolve declared template model parameters.
- [ ] Resolve properties according to selected member-access policy.
- [ ] Perform overload resolution only where method calls are enabled.
- [ ] Emit compile diagnostics for missing members in typed mode.
- [ ] Track escaping context/capability where possible.
- [ ] Calculate template capability flags.

### Capability flags to compute

- [ ] requires dynamic member resolution.
- [ ] requires arbitrary method calls.
- [ ] requires dynamic include/parse.
- [ ] requires runtime evaluation.
- [ ] accesses raw/unescaped output.
- [ ] uses unknown model types.
- [ ] eligible for fully static AOT lowering.

---

## 6. Template IR

- [ ] Define stable compiler-internal IR package.
- [ ] Lower AST to control-flow-aware IR.
- [ ] Preserve source mapping on every effectful instruction.
- [ ] Separate values from output effects.
- [ ] Represent static output chunks by constant-pool IDs.
- [ ] Represent resolved member access by explicit `AccessPlan`.
- [ ] Represent dynamic member access by explicit `DynamicAccessSite`.
- [ ] Represent escaping strategy explicitly.
- [ ] Preserve primitive types.
- [ ] Verify IR invariants before and after every optimization phase in debug builds.

### Required IR operations

- [ ] `WriteStatic`.
- [ ] `WriteValue`.
- [ ] `LoadParam`.
- [ ] `LoadLocal` / `StoreLocal`.
- [ ] `GetProperty`.
- [ ] `InvokeAllowedMethod`.
- [ ] `IndexGet`.
- [ ] arithmetic/comparison/boolean operations.
- [ ] conditional branch.
- [ ] loop setup/next/end.
- [ ] include/static-call.
- [ ] dynamic dispatch callsite.
- [ ] macro call or lowered equivalent.
- [ ] return/stop.

---

## 7. Reference interpreter

The interpreter is a correctness oracle and development backend, not the final performance story.

- [ ] Execute the same semantic/IR representation used by compiled backends.
- [ ] Implement streaming output.
- [ ] Implement all VTL_CORE semantics first.
- [ ] Implement compatibility-only features behind capability checks.
- [ ] Match source-position error reporting.
- [ ] Support deterministic execution limits.
- [ ] Support development hot reload.

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
