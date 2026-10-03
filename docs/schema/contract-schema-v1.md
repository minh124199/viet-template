# Viet Template Contract Schema Specification (v1)

## 1. Overview

Viet Template Milestone M27 establishes a canonical, deterministic, language-neutral serialized contract schema (`*.vt-schema.json`) derived from the public `TemplateContract` and `TemplateType` models.

The schema enables external developer tools, language servers (LSP), IDE plugins, code generators, and static analysis tooling to inspect template parameter bindings, type constraints, and complex domain object shapes without requiring a live JVM runtime or executing arbitrary bytecode.

- **Format Identifier**: `viet-template-contract-schema/1`
- **Schema Version**: `1`
- **Schema URI**: `https://viet-template.github.io/schemas/contract-v1.json`
- **Artifact Suffix**: `*.vt-schema.json`
- **Encoding**: UTF-8 without BOM, LF (`\n`) line endings, 2-space indentation.

---

## 2. Canonical JSON Structure

Every `*.vt-schema.json` document consists of an envelope object containing metadata, sorted template parameters, and expanded domain type definitions.

```json
{
  "$schema": "https://viet-template.github.io/schemas/contract-v1.json",
  "format": "viet-template-contract-schema/1",
  "schemaVersion": 1,
  "templateId": "users/user-card.vtl",
  "contractFingerprint": "sha256:d8a9f...",
  "parameters": [
    {
      "name": "active",
      "type": {
        "kind": "primitive",
        "name": "boolean"
      },
      "nullable": false,
      "optional": false
    },
    {
      "name": "user",
      "type": {
        "kind": "class",
        "name": "com.example.UserDto"
      },
      "nullable": false,
      "optional": false
    }
  ],
  "types": {
    "com.example.UserDto": {
      "kind": "record",
      "properties": [
        {
          "name": "email",
          "type": {
            "kind": "class",
            "name": "java.lang.String"
          },
          "nullable": false
        },
        {
          "name": "id",
          "type": {
            "kind": "primitive",
            "name": "long"
          },
          "nullable": false
        }
      ]
    }
  }
}
```

### 2.1 Envelope Fields

| Field | Type | Description |
|---|---|---|
| `$schema` | String | URI pointing to the canonical JSON Schema definition. |
| `format` | String | Format identifier, must be `viet-template-contract-schema/1`. |
| `schemaVersion` | Integer | Integer version number, must be `1`. |
| `templateId` | String | Relative template identifier path (e.g. `users/user-card.vtl`). |
| `contractFingerprint` | String | Canonical SHA-256 fingerprint computed via `TemplateContract.computeCanonicalFingerprint()`. |
| `parameters` | Array | Alphabetically sorted list of input parameters accepted by the template. |
| `types` | Object | Map of referenced complex types keyed by FQCN, sorted alphabetically. |

---

## 3. Type System Representation

`TemplateType` instances are serialized into structured JSON objects with a discriminating `kind` property:

### 3.1 Primitive Types (`primitive`)

Represents JVM primitives (`boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`, `void`).

```json
{
  "kind": "primitive",
  "name": "long"
}
```

### 3.2 Reference Class Types (`class`)

Represents a reference class or interface by its fully-qualified class name.

```json
{
  "kind": "class",
  "name": "java.lang.String"
}
```

### 3.3 Parameterized Generic Types (`parameterized`)

Represents generic types with explicit type arguments (e.g. `java.util.List<java.lang.String>`).

```json
{
  "kind": "parameterized",
  "rawType": "java.util.List",
  "arguments": [
    {
      "kind": "class",
      "name": "java.lang.String"
    }
  ]
}
```

### 3.4 Array Types (`array`)

Represents single- or multi-dimensional arrays.

```json
{
  "kind": "array",
  "componentType": {
    "kind": "primitive",
    "name": "int"
  }
}
```

### 3.5 Wildcard Types (`wildcard`)

Represents bounded or unbounded wildcard generic arguments (`?`, `? extends T`, `? super T`).

```json
{
  "kind": "wildcard",
  "boundKind": "extends",
  "bound": {
    "kind": "class",
    "name": "java.lang.Number"
  }
}
```

### 3.6 Named / Symbolic Types (`named`)

Represents symbolic type variables or named contract declarations.

```json
{
  "kind": "named",
  "name": "T",
  "arguments": []
}
```

---

## 4. Bounded Member Discovery & Security Invariants

### 4.1 Discovery Scope

- **Java Records**: Components are extracted via `Class.getRecordComponents()`. Component types and nullability annotations (`@Nullable`) are preserved.
- **JavaBeans / Classes**: Public getter methods matching `get[A-Z].*` (arity 0, non-void return) and `is[A-Z].*` (arity 0, boolean return) are extracted and converted to property names following JavaBean naming conventions.
- **JDK Exclusions**: Standard library types (`java.*`, `javax.*`, `jakarta.*`, `sun.*`, `jdk.*`) are not recursively expanded into the `types` catalog to avoid dumping hundreds of JVM internal methods.
- **Cycle Detection**: Recursive models (e.g. `Node -> Node` or `Parent -> Child -> Parent`) are tracked via a visited set. The traversal terminates deterministically and models are emitted once without infinite expansion. Maximum expansion depth is bounded at 32.

### 4.2 Security Filtering

Member discovery strictly applies `MemberAccessPolicy.standard()` at discovery time, prior to serialization:
- Blocked Classes: `java.lang.ClassLoader`, `java.lang.Runtime`, `java.lang.Process`, `java.lang.reflect.*`, and engine internals.
- Blocked Methods: `getClass()`, `wait()`, `notify()`, `notifyAll()`.
- Legitimate business properties that contain "Class" in their name (e.g. `getClassLikeBusinessProperty()`) remain accessible, matching the runtime sandbox policy.

---

## 5. Deterministic Serialization Guarantees

To ensure 100% reproducible builds and byte-for-byte parity across build tools:
1. **Zero-Dependency Serializer**: Serialized using a dedicated, high-performance writer with zero third-party JSON dependencies.
2. **Key Ordering**: All JSON object keys are emitted in strict canonical order:
   - Root: `$schema`, `format`, `schemaVersion`, `templateId`, `contractFingerprint`, `parameters`, `types`.
   - Parameter: `name`, `type`, `nullable`, `optional`.
   - Type definition: `kind`, `properties`.
   - Property: `name`, `type`, `nullable`.
3. **Array Ordering**: Parameters are sorted by `name` ascending; types are keyed by FQCN ascending; properties are sorted by `name` ascending.
4. **Formatting**: Exactly 2 spaces per indentation level, LF line endings, UTF-8 charset without BOM.

---

## 6. Build Tool Integration

### 6.1 Maven Plugin (`viet-template-maven-plugin`)

This example uses the `1.1.0` release-preparation coordinate, which is not published yet. The latest published stable version remains `1.0.1` until the release is completed.

The `generate-schemas` goal executes during the `process-classes` lifecycle phase:

```xml
<plugin>
  <groupId>io.github.minh124199</groupId>
  <artifactId>viet-template-maven-plugin</artifactId>
  <version>1.1.0</version>
  <executions>
    <execution>
      <goals>
        <goal>generate-schemas</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

- Default source directory: `${project.basedir}/src/main/viet-template`
- Default output directory: `${project.build.directory}/generated-resources/viet-template/schemas`

### 6.2 Gradle Plugin (`viet-template-gradle-plugin`)

The `generateVietTemplateSchemas` task runs after `compileJava` and wires generated schemas into the project resources:

```kotlin
plugins {
    id("io.github.minh124199.viet-template")
}

vietTemplate {
    // optional custom schema output directory
    schemaOutputDirectory.set(layout.buildDirectory.dir("generated/viet-template/schemas"))
}
```

- Default task name: `generateVietTemplateSchemas`
- Default output directory: `layout.buildDirectory.dir("generated/viet-template/schemas")`

### 6.3 Tooling Parity

Given identical input template and contract files, Maven and Gradle plugins produce identical `*.vt-schema.json` files with identical SHA-256 hashes.
