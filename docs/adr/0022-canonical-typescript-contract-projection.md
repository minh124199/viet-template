# ADR-0022: Canonical TypeScript Contract Projection from Typed Template Models

## Status
**ACCEPTED / QUALIFIED** (Milestone M46A)

## Date
2026-10-13

## Context

Viet Template 1.4 introduced `$clientData` and full-stack hydration capabilities for frontend islands (e.g. Svelte islands). Viet Template 1.5 (Milestone M45) established optional typed template parameters (`#param(...)`), compile-time expression type propagation, and canonical `TemplateContract` / `ModelSchema` representations.

However, client-side UI consumers (e.g., Svelte islands, React components, Vue widgets) lacked automated, end-to-end compile-time type safety against the server-rendered contracts. Developers were forced to maintain hand-written TypeScript interfaces, creating several critical risks:
1. **Contract Drift**: Changes to server-side Java records or template parameters did not break client TypeScript builds until runtime.
2. **Typos in Island Props**: Typos such as `employee.nmae` went undetected by frontend tooling.
3. **Serialization Discrepancies**: Mismatches between Java collection/temporal semantics and JSON runtime payloads (e.g. Java `Set<T>` serialized as array `T[]`, or `Instant` serialized as ISO-8601 string) resulted in inaccurate TypeScript types.
4. **Build Chain Bloat**: Introducing Node.js or npm into standard Java builds (Maven/Gradle) would violate Viet Template's core operational invariants.

Milestone M46A was chartered to investigate, prototype, and qualify the smallest, deterministic, frontend-framework-neutral architecture capable of projecting canonical server-side template contracts into high-quality TypeScript declaration files (`.d.ts`).

### Strict Architectural Invariants
- **Zero Node/npm in Production Builds**: Generation must run in pure Java. Node/tsc is strictly isolated to opt-in developer/CI tests and frontend tooling.
- **Byte-for-Byte Cross-Run Determinism**: Consecutive runs against unchanged sources must produce byte-for-byte identical output with zero timestamp or platform variance.
- **Incremental Write Avoidance (`writeIfChanged`)**: Existing files must not have their modification timestamps (`mtime`) updated if the content is identical, preventing spurious Vite/Rollup/Webpack rebuild loops.
- **Safe Output Ownership (`cleanStaleOutputs`)**: Cleanup must only remove known generated declaration files, never touching hand-crafted user files.
- **Fail-Closed Security**: Member introspection must strictly honor `MemberAccessPolicy`. Internal and security-sensitive members (`getClass()`, `ClassLoader`, `Thread`) must never appear in generated client contracts.
- **Frontend Neutrality**: Generated contracts must be pure standard TypeScript declarations (`.d.ts`) without hardcoding Svelte, React, or Vue dependencies.

---

## Decision

The project formally adopts the following architecture for TypeScript contract projection:

### 1. Canonical Pipeline: Candidate D (Hybrid Normalization via Canonical Schema Model)

The projection architecture adopts Candidate D:
```text
Java Models / Template Parameters
               │
               ▼
   JavaModelSchemaImporter
               │
               ▼
     CanonicalSchemaModel
               │
               ▼
 Pure-Java TypeScript Emitter
               │
               ▼
Deterministic .d.ts Declarations
```
- Reuses the existing schema AST (`CanonicalSchemaModel`, `SchemaType`, `SchemaProperty`) without introducing duplicate type models.
- Normalizes both inline `#param(...)` template contracts and Java bean/record models into a unified schema intermediate representation.

### 2. Output and File Organization Strategy: Per-Template Declarations

Declarations are generated per template file:
- For a template `templates/employees/detail.vtl`, the generator produces `templates/employees/detail.vtl.d.ts` (or in a configured target output directory maintaining relative path structure).
- Includes top-level interface `DetailContract` matching the template parameter bag, alongside referenced domain interfaces (`Employee`, `Department`).
- Shared domain types are disambiguated deterministically to avoid name collisions across packages.

### 3. Type Mapping System & Runtime Serialization Parity

Generated declarations strictly mirror runtime JSON shapes produced by `ClientData` and `SimpleJsonSerializer`:
- **Primitives**: `String` -> `string`, numbers (`int`, `long`, `double`, `BigDecimal`, etc.) -> `number`, `boolean` -> `boolean`.
- **Temporal Types**: `Instant`, `LocalDate`, `Date`, etc. serialize as ISO-8601 strings and are projected as `string`.
- **Collections & Arrays**: `List<T>`, arrays, and `Set<T>` serialize as JSON arrays and are projected as `T[]`. (Corrects legacy M28 `Set<T>` discrepancy).
- **Maps**: `Map<String, V>` projects to `Record<string, V>`.
- **Enums**: Project to string literal unions matching enum constant names (e.g., `"ACTIVE" | "INACTIVE"`).
- **Nullability**: Reference types and nullable template parameters project to `T | null`. Under TypeScript `--strict`, consumers are required to handle nullability.
- **Recursive Types**: Handled cleanly via named interface declarations without circular resolution or stack overflow.

### 4. Deterministic Collision Disambiguation

When distinct classes in different packages share the same simple name (e.g. `com.example.hr.Employee` and `com.example.payroll.Employee`):
- Types are sorted lexicographically by fully-qualified class name.
- Non-colliding types use their simple names.
- Colliding types are disambiguated by prepending sanitized package path components (e.g., `Hr_Employee` and `Payroll_Employee`).
- Aliased simple names pointing to the same underlying FQCN within a single template schema are deduplicated without generating artificial collision prefixes.

### 5. File System & Tooling Invariants

- **Write Avoidance**: The generator compares prospective content against existing disk content; if identical, write is skipped and file modification time (`mtime`) is preserved.
- **Path Normalization**: Windows path backslashes (`\`) are normalized to standard forward slashes (`/`), and path traversal attempts (`..`) are strictly rejected.
- **Safe Output Ownership**: A generated file manifest or set is maintained. When a template is deleted or renamed, only its owned `.d.ts` output is deleted; foreign user files are never touched.

---

## Consequences

### Positive
- **End-to-End Type Safety**: Server template parameters and client-side island components share a single, verifiable contract.
- **Developer Experience**: Real `tsc` type checking prevents property typos (`employee.nmae`), enforces null handling, and provides IDE autocompletion in frontend editors.
- **Vite / HMR Integration**: Preserving `mtime` prevents hot-module-replacement cascades in Vite/Rollup watchers when server builds re-run without contract changes.
- **Zero Node/npm Footprint in Java Builds**: Pure Java generator runs cleanly in Maven and Gradle plugins without requiring Node.js to be installed on developer machines or CI build agents.

### Negative / Trade-offs
- Pure Java AST-to-TypeScript projection requires maintaining the emitter in Java rather than leveraging TypeScript compiler API tools directly.
- Package collision disambiguation produces compound interface names (e.g., `Hr_Employee`) when conflicting models are combined in the same scope.

### Neutral
- Public runtime API/SPI contracts are completely unaffected (`STABLE_API +0`, `STABLE_SPI +0`, `FRAMEWORK_ENTRYPOINT +0`).
