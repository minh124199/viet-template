# Cross-Language Schema Interoperability Specification (Milestone M35)

## 1. Overview and Core Architectural Invariant

Viet Template Milestone M35 introduces cross-language schema interoperability across the Java, JSON Schema, and TypeScript ecosystems.

The central architectural invariant governing M35 is:

> **Every supported external schema format must normalize into one canonical Viet Template type model before validation (M32), explanation (M33), migration reporting (M34), LSP analysis, or compilation uses it.**

Downstream tools must never interpret external schema formats independently or maintain format-specific semantic analysis.

```
Java Domain Models ──────────────┐
JSON Schema (Draft 7/2020-12) ───┼────► Canonical Schema Model (`CanonicalSchema`)
TypeScript Declarations (.d.ts) ─┘               │
                                                 ├── Build-Time Validation (M32)
                                                 ├── Compiler Explanation (M33)
                                                 ├── Migration Reporting (M34)
                                                 ├── Language Server Protocol (LSP)
                                                 └── Semantic Analysis / Type Propagation
Canonical Schema Model
          │
          └────► TypeScript Declaration Projection (`*.d.ts`)
```

### Strict Non-Goals & Invariants
- **Zero JavaScript Execution**: Viet Template does NOT execute JavaScript, TypeScript, or Node.js runtime code.
- **Zero Node/npm Dependencies**: The parser and importers are implemented entirely in standard Java 21 with zero third-party runtime dependencies.
- **Single Type System**: External schemas are normalized into `CanonicalSchemaModel` and projected into the internal VTL `ModelSchema`; there is no secondary semantic engine.
- **No Silent Degeneracy**: Unsupported or lossy schema constructs never silently degrade to `Object`; structured diagnostics are emitted with actionable remedies.
- **Offline & Reproducible**: Remote network `$ref` imports (HTTP/HTTPS) and external npm package imports are rejected offline.
- **AOT Member Access Safety**: Shape-only schemas (JSON Schema, TypeScript) that lack physical JVM bytecodes automatically configure dynamic member resolution, ensuring `IrVerifier` and the AOT bytecode compiler never attempt direct JVM getter bytecodes on synthetic shapes.

---

## 2. Canonical Schema Model Architecture

The canonical schema representation is defined in package `io.github.minh124199.viettemplate.schema`:

### 2.1 Sealed Type Hierarchy (`TypeRef`)
All canonical types implement the sealed `CanonicalSchemaModel.TypeRef` interface:

| Type Ref | Representation | Description |
|---|---|---|
| `PrimitiveTypeRef` | `boolean`, `int`, `long`, `string`, etc. | Fundamental scalar primitives. |
| `ClassTypeRef` | Fully-qualified class name | Java classes (`java.lang.String`, etc.). |
| `NamedTypeRef` | Simple or qualified name + type arguments | Reference to sibling or local declared types. |
| `ArrayTypeRef` | Component `TypeRef` | Sequential lists, collections, and arrays. |
| `MapTypeRef` | Key `TypeRef`, Value `TypeRef` | Key-value dictionaries and index signatures. |
| `EnumTypeRef` | Name + symbol list | Enumeration constant sets and literal string unions. |
| `UnionTypeRef` | List of `TypeRef` options | Disjunctive union of multiple permitted types. |
| `WildcardTypeRef`| Bound kind (`EXTENDS`/`SUPER`) + bound | Generic wildcards. |
| `ParameterizedTypeRef` | Raw type + argument list | Generic types (`List<T>`, `Optional<T>`). |
| `DynamicTypeRef`| Dynamic / untyped | Fallback dynamic reference. |

### 2.2 Four Distinct Optionality and Nullability States
Viet Template strictly distinguishes nullability (whether a value may be `null`) from optionality (whether a property may be omitted from context):

1. **Required + Non-Null**: Property must exist and must not be null (e.g. `user.id: number`).
2. **Required + Nullable**: Property must exist but may be null (e.g. `user.bio: string | null`).
3. **Optional + Non-Null**: Property may be omitted, but if present must not be null (e.g. `user.nickname?: string`).
4. **Optional + Nullable**: Property may be omitted, and if present may be null (e.g. `user.middleName?: string | null`).

---

## 3. Supported Schema Formats & Importers

### 3.1 JSON Schema Importer (`JsonSchemaImporter`)
- **Supported Dialects**: JSON Schema Draft 7 and Draft 2020-12 compatible subsets.
- **Offline Guarantee**: Remote references (`http://`, `https://`) emit `JSON_SCHEMA_UNSUPPORTED_REMOTE_REF` and fail closed.
- **Position Tracking**: 1-based line and column positions tracked for precise diagnostics without external JSON libraries.
- **Supported Keywords**:
  - `type`: Scalar strings or arrays (e.g. `["string", "null"]` mapping to nullable string).
  - `properties`: Object property definitions.
  - `required`: Delineates required vs optional properties.
  - `items`: Array item element types.
  - `additionalProperties`: Map value types.
  - `enum` / `const`: Discrete constant enumerations.
  - `$defs` / `definitions`: Reusable local definitions with recursion bounding.
  - `oneOf` / `anyOf`: Canonical union types.
  - `nullable`: Explicit boolean nullability flag.

### 3.2 TypeScript Declaration Importer (`TypeScriptSchemaImporter`)
- **Engine**: Handcrafted zero-dependency lexer and recursive descent parser.
- **Supported Declarations**:
  - `interface Name { ... }` with optional `extends`.
  - `type Name = ...;` (object types, unions, aliases).
  - `enum Name { ... }` (standard numeric and string enums).
- **Supported Constructs**:
  - Primitives: `string`, `number`, `boolean`, `bigint`, `any`, `unknown`.
  - Arrays: `T[]`, `Array<T>`, `ReadonlyArray<T>`.
  - Maps: `Record<string, V>`, index signatures `{ [key: string]: V }`.
  - Unions: `A | B` (including literal unions `"OPEN" | "CLOSED"` as enums).
  - Nested inline object literals: `{ sub: type }`.
- **Structured Diagnostics**:
  - `TS_SCHEMA_UNSUPPORTED_CONDITIONAL_TYPE`: Conditional types (`T extends U ? X : Y`).
  - `TS_SCHEMA_UNSUPPORTED_MAPPED_TYPE`: Mapped types (`{ [K in Keys]: ... }`).
  - `TS_SCHEMA_UNSUPPORTED_FUNCTION_TYPE`: Callable function signatures.
  - `TS_SCHEMA_UNSUPPORTED_NPM_IMPORT`: External npm package imports.

### 3.3 Java Domain Model Normalizer (`JavaModelSchemaImporter`)
- Introspects Java records, JavaBeans, interfaces, enums, collections, and maps.
- Extracts `TemplateContract` annotations (`@TemplateContract`, `TemplateParameter`).
- Enforces `MemberAccessPolicy` safely without invoking constructors or static initializers.

---

## 4. Multi-Format Resolution & Build Tooling

### 4.1 Schema Discovery Convention
`CanonicalSchemaResolver` automatically resolves companion schema files alongside template sources in order of preference:
1. `<template-name>.vt-schema.json` (Viet Template Contract Schema)
2. `<template-name>.schema.json` (JSON Schema)
3. `<template-name>.d.ts` (TypeScript Declaration)
4. `<template-name>.contract` (Viet Template Companion Contract)

### 4.2 Maven Plugin Integration
The `viet-template-maven-plugin` provides unified schema discovery:
- **`viet-template:validate`**: Automatically discovers and validates templates against companion schemas.
- **`viet-template:explain`**: Explains inferred types and member access derived from schemas.
- **`viet-template:generate-typescript`**: Projects TypeScript declaration files from both `*.vt-schema.json` and `*.schema.json`.

```xml
<plugin>
  <groupId>io.github.minh124199</groupId>
  <artifactId>viet-template-maven-plugin</artifactId>
  <version>1.2.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals>
        <goal>validate</goal>
        <goal>generate-typescript</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### 4.3 Gradle Plugin Integration
The `viet-template-gradle-plugin` offers 100% parity with Maven:
- **`validateVietTemplates`**: Wired into Gradle's standard lifecycle `check` task.
- **`explainVietTemplates`**: Provides compiler explanation under the `help` task group.
- **`generateVietTemplateTypeScript`**: Generates `.d.ts` declarations from discovered contract and JSON schemas.

```kotlin
plugins {
    id("io.github.minh124199.viet-template") version "1.2.0-SNAPSHOT"
}

vietTemplate {
    schemaDirectory.set(file("src/main/resources/schemas"))
    typeScriptOutputDirectory.set(layout.buildDirectory.dir("generated/typescript"))
}
```
