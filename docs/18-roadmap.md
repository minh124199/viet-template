# 18 — Implementation Roadmap

## Phase 0 — Foundation

Repository/build, Java 17/21/25 CI, diagnostics/source model, architecture boundaries, ADR process.

**Exit:** clean build + tests from fresh clone.

## Phase 1 — Lexer/parser

Text/comments/references/expressions/`#set`/`#if`/`#foreach`/include/parse syntax, source spans, recovery.

**Exit:** >=500 parser fixtures and fuzz smoke.

## Phase 2 — Interpreter/core semantics

Context, property/index, strict/quiet/null, truthiness, control flow, loop metadata, resource repository.

**Exit:** end-to-end core examples with no framework dependency.

## Phase 3 — Velocity differential compatibility

Reference runner, property lookup/truthiness/undefined behavior corpus, compatibility manifest.

**Exit:** every claimed core feature differential-tested.

## Phase 4 — Types/model schema

`VType`, model declaration, direct property binding, nullability, compile-time capability checks, rich diagnostics.

**Exit:** model typos fail build with suggestions.

## Phase 5 — IR

Structured typed IR, verifier, source mapping, IR interpreter.

**Exit:** migrate semantic oracle to IR interpreter with parity.

## Phase 6 — Baseline AOT compiler

Static writes, scalar/direct getter, if, loops, locals, template calls, class loading/verification.

**Exit:** compiler equals IR interpreter on core TCK.

## Phase 7 — Optimization

Text merge, constant fold, truthiness/loop/primitive specialization, UTF-8 constants, method splitting.

**Exit:** credible performance on B01–B08 without correctness regression.

## Phase 8 — Dynamic backend

MethodHandle linker, monomorphic/PIC cache, Velocity property compatibility, policy-aware dynamic methods.

**Exit:** warm dynamic path materially beats repeated reflection and passes security cache tests.

## Phase 9 — Advanced migration

Macros, full break/stop, ranges/collections, dynamic parse, opt-in evaluate, migration scanner.

## Phase 10 — Spring 7 / Boot 4

MVC View/Resolver, auto-config/starter, properties, hot reload, AOT hints.

**Exit:** Boot 4.1 sample works with starter + templates; MockMvc E2E passes; no container exposure by default.

## Phase 11 — Native/hardening

Native profile, GraalVM smoke, fuzz campaigns, security review, classloader leak tests, reproducible builds, SBOM.

## Phase 12 — 1.0

Stable API/language profile, public TCK, reproducible benchmark report, migration guide, final naming/trademark review and publication automation.
