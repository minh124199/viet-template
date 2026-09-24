# ADR-0019: Runtime Exception Semantics, Failure Boundaries, and Diagnostic Hardening

**Status**: Accepted  
**Date**: 2026-09-24  
**Milestone**: 0.3.0-M6  

---

## Context

Prior to Milestone 0.3.0-M6, an audit of production catch blocks across Viet Template revealed 56 occurrences of broad `catch (Exception)` or `catch (Throwable)` statements. Detailed architectural investigation identified several failure boundary defects:

1. **Fatal JVM Error Swallowing (P0)**:
   - In framework adapters (such as `QuarkusSecurityView` and `QuarkusSecurityRenderContextContributor`), catching `Throwable` swallowed `VirtualMachineError` (including `OutOfMemoryError` and `StackOverflowError`) and `ThreadDeath`, returning `ANONYMOUS` or `null`.
   - In `BytecodeRuntimeBridge` PIC dispatch, catching `Throwable` wrapped fatal JVM errors into generic `new RuntimeException(t)`.
2. **Security Denial Masking (P0/P1)**:
   - In `FilesystemTemplateRepository`, `AccessDeniedException` was caught by a broad block and converted into `Optional.empty()`, treating a security authorization denial as template absence and poisoning the negative cache.
   - In `VtlTemplateEngine.engineResolver`, security violations during `#parse` were caught and converted into `Optional.empty()`, causing template execution to proceed with empty output rather than failing closed.
   - In `VietTemplateViewResolver` and `VietTemplateRenderer`, probing `templateExists` caught `Exception` and treated security violations as either false or true, failing to report authorization violations to the application container.
3. **Application Cause Masking & Diagnostic Degradation (P1)**:
   - When reflective invocation threw `InvocationTargetException`, the true application cause was frequently swallowed, obscured, or mapped into generic syntax errors (`SYNTAX_ERROR`) rather than `INVALID_METHOD`.
4. **AOT Bytecode Corruption vs. Absence Confusion (P1)**:
   - `AotTemplateRegistry` caught `Throwable` when attempting to load AOT classes, treating corrupt bytecode or incompatible class version errors (`LinkageError`) as template absence rather than compilation failures.

---

## Governing Invariants

Viet Template establishes the following non-negotiable failure-boundary invariants:

> **"Catch only what you understand, recover only from what is actually recoverable, and preserve enough structured context that users and tools can determine what failed. Never silently transform a compiler bug, security denial, I/O failure, thread interruption, or fatal JVM failure into template not found, optional feature unavailable, or an empty result."**

---

## Decision

To establish deterministic failure boundaries and eliminate bug-masking risks, Viet Template adopts the following 8-part architectural decision:

### 1. No Speculative Hierarchy Rewrite
A complete redesign of the exception hierarchy is rejected. The existing standard hierarchy is sufficient, stable, and expressive when applied consistently:
- `TemplateException` (base checked/unchecked root)
  - `TemplateResourceException` (template not found, I/O failure during stream read)
  - `TemplateSyntaxException` (lexical or syntactic grammar violations)
  - `TemplateCompilationException` (semantic analysis, IR lowering, AOT compilation, or linkage errors)
  - `TemplateRenderException` (runtime evaluation, invocation errors, stream I/O)
  - `TemplateSecurityException` (access policy violations, forbidden members, path traversal)
  - `TemplateLimitException` (iteration budget, output character limit exceeded)

### 2. Sixteen Canonical Failure Classifications
Every failure condition in the repository is mapped to one of 16 formal categories:
`EXPECTED_MISS`, `OPTIONAL_CAPABILITY_PROBE`, `USER_INPUT_ERROR`, `RESOLUTION`, `PARSE`, `SEMANTIC`, `COMPILATION`, `EVALUATION`, `IO`, `SECURITY`, `LINKAGE`, `FRAMEWORK`, `INTERRUPTION`, `LIFECYCLE`, `INTERNAL`, `FATAL`.

### 3. Fatal JVM Error and Thread Death Propagation Invariant
All catch blocks that intercept broad types (`Throwable` or `Exception`) must explicitly rethrow `VirtualMachineError` and `ThreadDeath` before performing fallback logic. Fatal JVM states must escape unmasked to allow container-level remediation.

### 4. Security Fails Closed Invariant
Security denials (`TemplateSecurityException`) must never be caught and converted to `Optional.empty()`, fallback values, or suppressed. In `VtlTemplateEngine`, `VietTemplateViewResolver`, `VietTemplateRenderer`, and repository layers, security denials immediately bubble up to abort template processing.

### 5. Dynamic Linker and Reflection Target Unmasking
In `BytecodeRuntimeBridge`, `LinkedReferenceAccess`, and `IrInterpreter`:
- `ControlSignal` (`#stop`, `#return`) is rethrown directly.
- `InvocationTargetException` is unwrapped to extract the true target cause. If the target is fatal or a `TemplateException`, it is rethrown directly. User application exceptions are wrapped into `TemplateRenderException` with diagnostic code `INVALID_METHOD` and the original cause intact.

### 6. Repository Failure Boundaries and Negative-Cache Integrity
- Expected resource absence returns `Optional.empty()` without throwing or allocating exception stack traces.
- Access denials throw `TemplateSecurityException`.
- Stream read errors throw `TemplateResourceException`.
- Negative cache is only populated on verified resource absence; I/O failures and security denials never poison the negative cache.

### 7. Framework and Build Tool Boundary Hardening
- View resolvers in Spring MVC and Quarkus rethrow security denials immediately.
- Build plugins (Maven Mojo, Gradle Task) and runtime hint generators narrow parameter parsing catches to `IllegalArgumentException` and `IOException`, avoiding catching unrelated runtime exceptions.

### 8. Machine-Readable Allowlist and Automated CI Verification
- Every allowable broad catch block in production code is cataloged in `config/architecture/exception-boundary-allowlist.json` with its enclosing method, caught type, classification category, subsystem owner, and rationale.
- The CI verifier `scripts/verify-exception-semantics.py` enforces zero unallowlisted broad catches and zero stale allowlist entries across all 13 physical build submodules (including 11 published production modules and 2 unpublished verification modules).

---

## Consequences

### Positive
- **Zero Bug Masking**: Corrupted bytecode, missing classes, and internal compiler failures can never be mistaken for ordinary template absence.
- **Fail-Closed Security**: Security policy violations reliably abort request processing across all framework integrations.
- **Full Backend Parity**: Verified identical failure semantics across AST interpreter, IR interpreter, and AOT bytecode backends.
- **Permanent CI Gate**: The allowlist verifier ensures that future development cannot introduce accidental broad catch blocks.

### Neutral / Trade-offs
- Refactored catch blocks require explicit multi-catch or rethrow blocks in performance-critical reflection bridges.
- Allowlist maintenance is required when adding intentional system boundaries in the future.
