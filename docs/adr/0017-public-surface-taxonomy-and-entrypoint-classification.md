# ADR-0017: Public Surface Taxonomy and Entrypoint Classification

**Status**: Accepted
**Date**: 2026-09-22
**Milestone**: 0.3.0-M3.3

---

## Context

Prior to 0.3.0-M3.3, Viet Template's `config/api-baseline/public-surface-classification.txt`
used a binary classification: `STABLE_API`, `STABLE_SPI`, `EXPERIMENTAL`, or the catch-all
`PUBLIC_BUT_INTERNAL_ACCIDENT`. This made 207 public types appear equivalently "accidental"
when they were actually public for materially different reasons:

- A Spring AOT hints class must be public so Spring's AOT processor can instantiate it via
  reflection at compile time. It is not intended for application programmers to call.
- A Gradle plugin class must be public so the Gradle plugin system can load it. It is not
  a library API.
- 56 types are public because they cross internal module boundaries within the Viet Template
  project itself (e.g. `language-vtl → vtl-interpreter`). They are not public Java APIs.
- `BytecodeRuntimeBridge` is public because generated AOT template classfiles reference it
  by exact name in their constant pools. Changing its FQCN is an ABI break.

Conflating all of these with genuinely accidental public classes (those that could be
package-private or relocated tomorrow) obscures what work remains and makes automated
enforcement impossible.

The canonical principle established by this ADR is:

> **Public because a framework must discover it is not the same as public because users
> should program against it.**

---

## Decision

Viet Template adopts a **nine-category public surface taxonomy**. Every compiled public or
protected type in every published production module must be classified in exactly one
of these categories:

### Category 1: `STABLE_API`
**Definition**: Supported application-developer-facing contracts under the long-term 1.x
backward compatibility guarantee.

**Consumers**: Any Java application developer who uses Viet Template as a library.

**Compatibility guarantee**: Binary + source backward compatible across all 1.x releases.
Breaking changes require a 2.0.0 major version increment.

**Dependency direction allowed**: External consumer → Viet Template stable API.

**Signature leak rule**: STABLE_API signatures MUST NOT reference any non-stable type.

**CI enforcement**: `verify-public-surface-classification.py` Check 3 (baseline bijection),
Check 4 (signature leak).

---

### Category 2: `STABLE_SPI`
**Definition**: Supported extension and service-provider interfaces intended for third-party
implementors (e.g. `TemplateEngineProvider`, `MemberAccessPolicy`, `TemplateOutput`).

**Consumers**: Library integrators and plugin authors who implement the interface.

**Compatibility guarantee**: Binary + source backward compatible across all 1.x releases.
New default methods allowed; abstract method additions are breaking.

**Signature leak rule**: Same as STABLE_API.

**CI enforcement**: Same as STABLE_API.

---

### Category 3: `EXPERIMENTAL`
**Definition**: Emerging language or compiler APIs that are expected to evolve before 1.0.
May break across minor versions. Annotated or documented as experimental.

**Examples**: `VtlParser`, `VtlParseResult`, `VtlParserOptions`, `VtlFeatureState`, `VtlFrontend`.

**Compatibility guarantee**: None. May break across any pre-1.0 release.

**CI enforcement**: Classified in `public-surface-classification.txt`; excluded from baseline
bijection check.

---

### Category 4: `GENERATED_RUNTIME_ABI`
**Definition**: Classes and members that are directly referenced by name in the constant
pools of AOT-generated template classfiles. The FQCN and member signatures are part of
a binary contract guaranteed by ADR-0016 (Model D pre-1.0 generated template ABI policy).

**Examples**: `BytecodeRuntimeBridge` (and its static methods).

**Why public**: Generated `.class` files resolve these references at class-load time. If
the FQCN or any referenced method is removed or renamed, all previously compiled templates
fail with `NoSuchMethodError` or `ClassNotFoundException`.

**Compatibility guarantee**: FQCN and members listed in `generated-template-runtime-abi.txt`
are frozen. New overloads allowed; no removals or signature changes without a model version bump.

**CI enforcement**: `verify-generated-abi.py` (constant-pool inspection of compiled templates
vs. `config/api-baseline/generated-template-runtime-abi.txt`).

---

### Category 5: `FRAMEWORK_ENTRYPOINT`
**Definition**: Classes that must be public because an external framework (Spring, Quarkus,
CDI) discovers and instantiates them via metadata files, annotations, or reflection. They
are NOT general-purpose library APIs.

**Examples**:
- `VietTemplateRuntimeHints` — Spring AOT discovers it via `META-INF/spring/aot.factories`.
- `VietTemplateSecurityRuntimeHints` — same mechanism in `viet-template-spring-security`.
- `VietTemplateProducer` — Quarkus Arc discovers `@ApplicationScoped @Produces` beans.
- `VietTemplateProcessor` — Quarkus build system discovers `@BuildStep` methods.

**Why public**: Frameworks instantiate these classes with `Class.forName()` or equivalent
reflection using the fully-qualified class name recorded in metadata. A non-public class
or constructor causes instantiation failure at application startup.

**Compatibility guarantee**: FQCN and metadata pointer are stable while the framework
integration module is published. NOT a library Java API; no public-method-level stability
guarantee beyond what the framework contract requires.

**Signature leak rule**: Entrypoint signatures may reference framework types (`BuildItem`,
`@BuildStep`, etc.) without violating the stable API signature leak policy.

**CI enforcement**: `verify-framework-entrypoints.py` (metadata ↔ compiled class ↔
classification parity check; generates `build/reports/public-framework-entrypoints.json`).

---

### Category 6: `BUILD_TOOL_ENTRYPOINT`
**Definition**: Classes that must be public because Maven or Gradle tooling discovers and
instantiates them via plugin descriptors or class naming conventions.

**Examples**:
- `VietTemplateCompileMojo` — Maven Plugin instantiates Mojo classes by reflection; FQCN
  declared in `META-INF/maven/plugin.xml`.
- `VietTemplatePlugin` — Gradle Plugin instantiates plugin implementations by FQCN from
  `META-INF/gradle-plugins/*.properties`.
- `VietTemplateCompileTask` — Gradle Task instantiates abstract task types by reflection.
- `VietTemplateExtension` — Gradle DSL instantiates extension classes by reflection.

**Why public**: Build tool infrastructure resolves these classes by FQCN at build time.
A package-private class causes `IllegalAccessException` or plugin resolution failure.

**Compatibility guarantee**: FQCN and the user-facing DSL properties / task actions are
stable within a build-tooling-compatible release. NOT a general library API.

**CI enforcement**: `verify-framework-entrypoints.py` (same gate covers build entrypoints).

---

### Category 7: `SERVICE_ENTRYPOINT`
**Definition**: Classes discovered via `java.util.ServiceLoader` through `META-INF/services/`
files.

**Examples**: `VtlTemplateEngineProvider` implements `TemplateEngineProvider` (a `STABLE_SPI`).
As the implementing provider it is classified `STABLE_API` in this repository because it is
the canonical implementation that application developers reference by service lookup.

**Why public**: `ServiceLoader` requires public no-arg constructor. FQCN must match the
entry in `META-INF/services/`.

**Compatibility guarantee**: Provider FQCN is stable. Members beyond those required by the
SPI interface carry no additional guarantee.

**CI enforcement**: `verify-framework-entrypoints.py` (SERVICE_ENTRYPOINT kind verified
against `META-INF/services/` entries).

---

### Category 8: `INTERNAL_CROSS_MODULE`
**Definition**: Production contracts shared between internal Viet Template modules where
Java package visibility cannot enforce the boundary without JPMS qualified exports.

**Verified module edges (56 contracts)**:
| Edge | Contract Count | Examples |
|---|---|---|
| `language-vtl → vtl-interpreter` | 48 | AST nodes, IR types, Lexer, Semantics, Optimizer |
| `runtime → vtl-interpreter` | 7 | `DynamicLinker`, `CallSiteRegistry`, `MemberKey`, `MemberOperation` |
| `runtime → spring` | 1 | `NonClosingOutputStream` |
| `runtime → quarkus` | 1 | `NonClosingOutputStream` |

**Why public**: Without JPMS qualified exports, Java requires `public` visibility for any
class accessed across a module boundary, even if that boundary is purely internal to the
Viet Template project.

**Compatibility guarantee**: No external compatibility promise before 1.0. Subject to
package restructuring or JPMS encapsulation.

**Dependency direction allowed**: Owning module → consuming module only (no reverse edge).
Framework and build-tool modules MUST NOT import deep-core internal types.

**CI enforcement**: `verify-cross-module-contracts.py` (zero unregistered cross-module
internal imports, direction invariant check; registry at
`config/architecture/cross-module-internal-contracts.json`).

---

### Category 9: `PUBLIC_BUT_INTERNAL_ACCIDENT`
**Definition**: Classes that are public solely because Java package visibility cannot enforce
the boundary, and which do not fit any of the explicit categories above. These are candidates
for future encapsulation via JPMS, package relocation, or architecture refactoring.

**Compatibility guarantee**: None. Subject to reduction in any 0.x minor release.

**CI enforcement**: All accidental types are inventoried in
`config/api-baseline/accidental-public-types-inventory.json` with a Category-C subcategory
annotation. The count is tracked and must not increase without a documented reason.

---

## Enforcement Invariants

1. **Zero unclassified types**: Every compiled public/protected type in every published module
   must have exactly one classification entry.

2. **Zero stale entries**: Every classified type must exist as a compiled public/protected type.

3. **Baseline bijection**: Every `STABLE_API` and `STABLE_SPI` type must be covered by one
   of the 5 API baseline files. No baseline type may be classified as anything other than
   `STABLE_API` or `STABLE_SPI`.

4. **Zero signature leaks**: No `STABLE_API` or `STABLE_SPI` signature may reference any
   non-stable type (any category other than `STABLE_API` or `STABLE_SPI`).

5. **Zero unregistered cross-module internal imports**: No production source file may import
   a type from another module's package unless that type is registered in
   `config/architecture/cross-module-internal-contracts.json`.

6. **Directional invariant**: Framework and build-tool modules MUST have zero
   `INTERNAL_CROSS_MODULE` imports from deep-core modules (language, runtime, interpreter).

---

## Consequences

**Positive**:
- The public surface is now fully explained. Every public type has a documented reason for
  being public.
- Automated CI gates prevent regressions in each category independently.
- The stable contract count (STABLE_API 94 + STABLE_SPI 27 = 121 as of M5/M6; originally 94 + 25 = 119 prior to freshness SPI additions) is enforced mechanically.
- Framework/build-tool entrypoints are documented with their discovery mechanism, enabling
  future authors to assess FQCN rename risk before making changes.
- `PUBLIC_BUT_INTERNAL_ACCIDENT` count reduced from 213 (pre-M3.x) to 146.

**Negative / Trade-offs**:
- `INTERNAL_CROSS_MODULE` types remain public Java types; the boundary is informational
  only until JPMS qualified exports are adopted (target: M22.4, currently DEFERRED).
- `VTypes` (C7) and the 82 C4 sealed-hierarchy types cannot be package-private under JLS
  without language or JPMS changes.
- Framework and build-tool entrypoints have FQCN stability implied by their metadata files.
  Renaming requires coordinated metadata update, not just a source rename.

---

## Related Decisions

- [ADR-0016](0016-generated-template-abi-compatibility.md): Generated template ABI
  compatibility (Model D), which governs `GENERATED_RUNTIME_ABI` types.

---

## Machine-Readable Resources

- `config/api-baseline/public-surface-classification.txt` — authoritative classification
- `config/architecture/framework-and-tooling-entrypoints.json` — entrypoint registry
- `config/architecture/cross-module-internal-contracts.json` — cross-module contract registry
- `scripts/verify-public-surface-classification.py` — primary enforcement gate
- `scripts/verify-framework-entrypoints.py` — entrypoint verification gate
- `scripts/verify-cross-module-contracts.py` — cross-module contract gate
