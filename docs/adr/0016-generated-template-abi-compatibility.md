# ADR-0016 — Generated Template Runtime ABI and Version Compatibility Policy

- Status: Accepted
- Date: 2026-09-22

## Context

Viet Template Ahead-Of-Time (AOT) compilation translates Velocity Template Language (VTL) templates directly into Java 21 bytecode (`.class` files) during application build time (via `TemplateAotCompiler`, the Maven plugin, the Gradle plugin, or the Quarkus deployment extension).

Generated template classfiles implement `io.github.minh124199.viettemplate.api.CompiledTemplate` and execute by invoking runtime helper methods on `io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge`, referencing dynamic polymorphic inline cache (PIC) sites via `io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite`, and querying loop metadata via `io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata`.

As Viet Template transitions from `0.2.x` through `0.3.x` toward `1.0.0 GA`, we must define the exact binary compatibility contract between generated template bytecode and the engine runtime across releases.

Specifically, we evaluated four potential compatibility models:
- **Model A (Full Immutability)**: Complete cross-version forward/backward ABI stability across all minor and patch releases starting immediately.
- **Model B (Ephemeral / No Guarantee)**: Templates must be recompiled on every release, even patch releases.
- **Model C (N-1 Backward Only)**: New engines can run old bytecode, but old engines cannot run new bytecode.
- **Model D (Model-D Pragmatic Evolution)**: Forward compatibility starting at 1.0 GA; patch-line compatibility within minor series (`0.2.x`, `0.3.x`); recompilation across minor versions allowed prior to 1.0.

## Decision

Adopt **Model D**:

1. **Patch-Line Binary Compatibility (0.2.x, 0.3.x)**:
   - Templates compiled by any `0.2.x` release (e.g. `0.2.0`, `0.2.1`, `0.2.2`, `0.2.3`) are binary compatible with any `0.2.x` runtime without requiring template recompilation.
   - Similarly, templates compiled by `0.3.0` will run on any subsequent `0.3.x` patch release.
   - `BytecodeRuntimeBridge` helper signatures, `DynamicCallSite` layouts, and runtime fields (`SITES`, `SECURITY_POLICY`, `UTF8_CHUNKS`) remain stable across patch releases.

2. **Pre-1.0 Minor Evolution & Recompilation (0.2.x -> 0.3.x)**:
   - AOT templates are compiled during the consumer build alongside source code (via Maven, Gradle, or Quarkus augmentation). Template artifacts are not distributed as standalone third-party libraries independently of application dependencies.
   - To enable aggressive optimization of bytecode generation, slot lifetime management, and encapsulation of internal packages throughout the `0.3.x` milestones, cross-minor runtime ABI compatibility is not guaranteed prior to `1.0.0 GA`.
   - Upgrading between minor versions before 1.0 (e.g. from `0.2.3` to `0.3.0`) may require recompilation of templates (`mvn clean compile` or `./gradlew clean build`).

3. **Strict ABI Freeze at 1.0.0-RC1 and 1.0.0 GA**:
   - Beginning with `1.0.0-RC1`, the candidate generated template runtime ABI is frozen at exactly 7 types and 22 methods (0 fields) and locked for candidate qualification.
   - At `1.0.0 GA`, the generated template runtime ABI becomes permanently frozen and forward-compatible across all `1.x` releases.
   - The runtime ABI baseline (`config/api-baseline/generated-template-runtime-abi.txt`) and automated mechanical audit script (`scripts/verify-generated-abi.py`) enforce that any breaking signature change to runtime bridge methods will fail continuous integration.

## Consequences

### Positive

- Avoids prematurely freezing internal compiler bridges while the engine undergoes active optimization and encapsulation in `0.3.x`.
- Reflects the practical reality of modern Java build tools where build plugins recompile templates whenever project dependencies change.
- Establishes a verifiable mechanical baseline (`generated-template-runtime-abi.txt`) and automated gate (`verify-generated-abi.py`) to prevent accidental signature regressions.

### Negative

- Consumers upgrading between pre-1.0 minor versions (e.g. `0.2.3` to `0.3.0`) cannot drop in a new engine JAR against pre-existing compiled template binaries without rebuilding the project.

## References

- [ADR-0002 — Compile-First Architecture & Execution Tiers](0002-compile-first-three-execution-tiers.md)
- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- `config/api-baseline/generated-template-runtime-abi.txt`
- `scripts/verify-generated-abi.py`
