# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
  - Upgraded Google Java Format from 1.24.0 to 1.28.0 across Gradle and Maven to fix JDK 25 internal javac API compatibility (addressing NoSuchMethodError in DeferredDiagnosticHandler.getDiagnostics()), keeping formatting validation fully active and enforced across the Java 17, 21, and 25 CI matrix.

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
