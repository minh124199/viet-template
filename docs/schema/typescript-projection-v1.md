# Viet Template TypeScript Declaration Projection Specification (v1)

## 1. Overview & Architectural Objective

Viet Template Milestone M28 establishes a deterministic, safe, versioned projection from canonical contract schemas (`*.vt-schema.json`, defined in M27) into TypeScript declaration files (`*.d.ts`).

The projection architecture adheres to a strict unidirectional boundary:

```
Java / Template Contract
        │
        ▼
M27 Canonical Schema Generator (TemplateContractSchemaGenerator)
        │
        ▼
*.vt-schema.json (Schema Specification v1)
        │
        ▼
M28 TypeScript Declaration Projector (TypeScriptDeclarationProjector)
        │
        ▼
*.d.ts
```

### 1.1 Architectural Invariants
1. **Schema-First Boundary**: The TypeScript projector consumes **only** the serialized M27 schema (`*.vt-schema.json`).
2. **Zero JVM Reflection**: The projector never accesses Java reflection metadata, runtime class loaders, JVM classes, compiler internals, `VType`, or template AST structures.
3. **Runtime Isolation**: The core runtime (`viet-template-api` and `viet-template-runtime`) remains completely free of TypeScript-specific dependencies or code.
4. **Zero Third-Party Dependency**: Schema parsing, validation, semantic projection, and code generation require zero external JSON libraries (no Jackson, Gson, etc.), preserving the lightweight zero-dependency architecture.
5. **Byte-for-Byte Determinism**: Identical schema input produces identical `.d.ts` bytes across Linux, macOS, and Windows.

---

## 2. Input Schema Contract

The projector consumes schemas adhering to format `viet-template-contract-schema/1` with `schemaVersion = 1`:

```json
{
  "$schema": "https://viet-template.github.io/schemas/contract-v1.json",
  "format": "viet-template-contract-schema/1",
  "schemaVersion": 1,
  "templateId": "users/user-card.vtl",
  "contractFingerprint": "vt-contract:v1:7e046f289b...",
  "parameters": [ ... ],
  "types": { ... }
}
```

### 2.1 Envelope Validation
- `format`: Must be exactly `"viet-template-contract-schema/1"`. Any unsupported format is rejected with an explicit diagnostic error.
- `schemaVersion`: Must be integer `1`. Unsupported versions are rejected rather than silently guessed.
- `templateId`: Must be a non-empty string identifier.
- `contractFingerprint`: Must be a non-empty canonical fingerprint string.
- `parameters`: Array of parameter definitions (`name`, `type`, `nullable`, `optional`).
- `types`: Map of fully qualified class names (FQCN) to type definitions (`kind`, `properties`).

---

## 3. TypeScript Type Mapping Semantics

### 3.1 Primitive Types (`primitive`)

JVM primitive types are projected to TypeScript equivalents:

| JVM Primitive | TypeScript Type | Semantic Notes |
|---|---|---|
| `boolean` | `boolean` | Direct equivalence |
| `byte` | `number` | Deliberate lossy range mapping (64-bit IEEE 754 float) |
| `short` | `number` | Deliberate lossy range mapping |
| `int` | `number` | Deliberate lossy range mapping |
| `long` | `number` | Mapped to `number` for standard JSON/JS interop compatibility |
| `float` | `number` | Direct equivalence |
| `double` | `number` | Direct equivalence |
| `char` | `string` | Single-character string in TypeScript |
| `void` | `void` | Direct equivalence |

### 3.2 Boxed Primitives & String (`class`)

Standard Java wrapper types and strings are mapped directly:

| Java Reference Class | TypeScript Type |
|---|---|
| `java.lang.Boolean` | `boolean` |
| `java.lang.Byte` | `number` |
| `java.lang.Short` | `number` |
| `java.lang.Integer` | `number` |
| `java.lang.Long` | `number` |
| `java.lang.Float` | `number` |
| `java.lang.Double` | `number` |
| `java.lang.Character` | `string` |
| `java.lang.Void` | `void` |
| `java.lang.String` | `string` |
| `java.lang.CharSequence` | `string` |
| `java.lang.Object` | `unknown` |

### 3.3 Dynamic Types (`dynamic`)

The M27 `dynamic` schema kind represents dynamically typed template values:
```json
{ "kind": "dynamic" }
```
- **TypeScript Representation**: `unknown`
- **Rationale**: `unknown` is TypeScript's type-safe top type. Callers can supply any value into template inputs, while downstream code requires explicit narrowing before unchecked operations. `unknown` is preferred over `any` because `any` disables all compile-time type verification.

### 3.4 Arrays (`array`)

Array types are projected to `T[]`:
- `int[]` -> `number[]`
- `String[]` -> `string[]`
- `User[]` -> `User[]`
- `User[][]` -> `User[][]`
- Union elements are parenthesized: `(string | null)[]`

### 3.5 Collections & Parameterized Types (`parameterized`)

Common Java collection and map types are projected to their canonical TypeScript representations:

| Java Type | TypeScript Representation | Rationale |
|---|---|---|
| `java.util.List<T>` | `T[]` | JSON arrays are the standard serialization of Java lists |
| `java.util.Collection<T>` | `T[]` | Array is the standard ordered iterable collection |
| `java.util.Iterable<T>` | `T[]` | Iteration target in templates |
| `java.util.Set<T>` | `Set<T>` | Preserves Set semantics |
| `java.util.Map<K, V>` (K is `string`) | `Record<string, V>` | Standard JSON object key-value dictionary |
| `java.util.Map<K, V>` (K is `number`) | `Record<number, V>` | Numeric-indexed dictionary |
| `java.util.Map<K, V>` (other K) | `Map<K, V>` | Built-in ES6 Map interface |
| Raw `Map` (no args) | `Record<string, unknown>` | Safe fallback for untyped map |
| Raw `List` (no args) | `unknown[]` | Safe fallback for untyped list |

For custom parameterized types (`Foo<T>`):
- If `rawType` is defined in the schema's `types` catalog: projected as `Foo<T>`.
- If `rawType` is unknown / not defined in `types`: fallback to `unknown`.

### 3.6 Wildcards (`wildcard`)

Java wildcards are projected according to variance semantics:

| Java Wildcard | Schema Form | TypeScript Projection | Rationale |
|---|---|---|---|
| `? extends T` | `boundKind: "extends", bound: T` | `T` | Covariant read: list elements are at least `T` |
| `? super T` | `boundKind: "super", bound: T` | `unknown` | Contravariant write: supertype up to Object |
| `?` (unbounded) | `boundKind: "unbounded"` | `unknown` | Top type: element can be any Object |

### 3.7 Named Types & Type Variables (`named`)

- Symbolic type variables (e.g. `T`, `E`, `K`, `V` not defined in `types`) project to TypeScript type parameters: `<T = unknown>` on `TemplateParameters` and `<T>` on generic interfaces.
- References to named types with arguments project to `Name<Arg1, Arg2>`.

---

## 4. Nullability and Optionality Contract

M27 contract schemas explicitly distinguish between **nullable** (can the value be `null`?) and **optional** (can the property be omitted from the object?).

### 4.1 Truth Table

| `nullable` | `optional` | TypeScript Syntax | Semantic Meaning |
|:---:|:---:|---|---|
| `false` | `false` | `prop: T;` | Value is strictly required and non-null |
| `true` | `false` | `prop: T \| null;` | Value must be passed, but can be `null` |
| `false` | `true` | `prop?: T;` | Property may be omitted; if passed, non-null |
| `true` | `true` | `prop?: T \| null;` | Property may be omitted; if passed, can be `null` |

---

## 5. Type Definitions & Declarations

### 5.1 Interface Representation

Each complex type in `types` is projected as an `export interface`:

```typescript
export interface User {
  active: boolean;
  age: number;
  name: string | null;
}
```

**Design Decision**: `interface` is chosen over `type` aliases because:
1. `interface` naturally supports self-referential recursive types without recursion limit errors.
2. `interface` produces clear, clean hover displays in language servers (M29).
3. `interface` allows ambient declaration merging if needed by consumer builds.

### 5.2 Recursive Models

Self-referential models (such as `RecursiveNode -> next: RecursiveNode`) project to named interface references without infinite expansion:

```typescript
export interface RecursiveNode {
  next: RecursiveNode | null;
  value: string | null;
}
```

### 5.3 Root Template Parameters Contract

The root template input parameters are exported as `export interface TemplateParameters`:

```typescript
export interface TemplateParameters {
  order: SimpleRecord;
}
```

---

## 6. Identifier Normalization & Reserved Words

### 6.1 Type Name Normalization & Collision Resolution
1. **Candidate Simple Name**: By default, package prefixes are removed (e.g. `com.example.User` -> `User`).
2. **Inner Class Handling**: Nested class identifiers with `$` extract the simple name (e.g. `Container$Item` -> `Item`).
3. **Collision Disambiguation**: If two or more FQCNs share the same candidate simple name (e.g. `com.a.User` and `com.b.User`), all colliding types are disambiguated deterministically using their package-qualified sanitized identifier:
   - `com.a.User` -> `com_a_User`
   - `com.b.User` -> `com_b_User`
   All references in parameters and property types are updated consistently.
4. **Reserved Type Names**: If a candidate type name matches a TypeScript reserved keyword (`class`, `interface`, `type`, `function`, etc.), it is prefixed with `_` (e.g. `_Class`).

### 6.2 Property Name Quoting
- Valid TypeScript identifiers (`^[a-zA-Z_$][a-zA-Z0-9_$]*$`) that are not reserved keywords are emitted unquoted: `active: boolean;`.
- Reserved keywords (`class`, `function`, `default`, `import`, etc.) and non-identifier property names (`foo-bar`, `user name`) are quoted with double quotes: `"class": string;`, `"default"?: boolean;`.

---

## 7. Deterministic Output Guarantees

1. **Header Metadata**:
   ```typescript
   // Generated by Viet Template M28.
   // Source schema format: viet-template-contract-schema/1
   // Schema version: 1
   // Contract fingerprint: <canonical-fingerprint>
   // DO NOT EDIT.
   ```
2. **Encoding**: UTF-8 without BOM.
3. **Line Endings**: LF (`\n`) on all platforms.
4. **Indentation**: Exactly 2 spaces.
5. **Ordering**:
   - Complex types in `types` are emitted in ascending alphabetical order by their TypeScript interface name.
   - Properties inside each interface are emitted in ascending alphabetical order by property name.
   - Root `TemplateParameters` properties are emitted in ascending alphabetical order by parameter name.
6. **No Environmental Variance**: No timestamps, absolute file paths, or random identifiers are ever emitted.

---

## 8. Build Tool Integration

### 8.1 Maven Plugin (`viet-template-maven-plugin`)
- **Goal**: `generate-typescript`
- **Phase**: `process-classes` (runs after `generate-schemas`)
- **Default Schema Directory**: `${project.build.directory}/generated-resources/viet-template/schemas`
- **Default Output Directory**: `${project.build.directory}/generated-sources/viet-template/typescript`

### 8.2 Gradle Plugin (`viet-template-gradle-plugin`)
- **Task**: `generateVietTemplateTypeScript`
- **Wiring**: Runs after `generateVietTemplateSchemas`
- **Default Output Directory**: `layout.buildDirectory.dir("generated/viet-template/typescript")`
- **Extension Property**: `vietTemplate.typeScriptOutputDirectory`

### 8.3 Tooling Parity
Given identical input schemas, Maven and Gradle produce byte-for-byte identical `.d.ts` files with identical SHA-256 hashes.
