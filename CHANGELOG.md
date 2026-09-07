# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
  - Complete implementation of all 12 canonical optimization passes:
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
