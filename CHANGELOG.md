# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
