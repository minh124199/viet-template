# Gradle Adoption & Setup Guide

## 1. Overview & Plugin ID

Viet Template provides a dedicated Gradle plugin (`io.github.minh124199.viet-template`) for build-time Ahead-Of-Time (AOT) template compilation, compatible with Gradle 8.x and Gradle 9.x.

The latest published stable release is **`1.1.0`** (published 2026-10-03; following 1.0.1 and 1.0.0 GA). Consumer examples below show the available Gradle Plugin DSL resolution (`plugins { id("io.github.minh124199.viet-template") version "1.1.0" }`).

### Minimum Requirements
- **Java**: Java 21 (`jvmToolchain(21)`) or newer.
- **Gradle**: Gradle 8.5+ or Gradle 9.x.

---

## 2. Plugin & Dependency Setup

### 2.1 Applying the Plugin

In `build.gradle.kts` (Kotlin DSL):

```kotlin
plugins {
    java
    id("io.github.minh124199.viet-template") version "1.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

Or in `build.gradle` (Groovy DSL):

```groovy
plugins {
    id 'java'
    id 'io.github.minh124199.viet-template' version '1.1.0'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### 2.2 Adding Dependencies

For Spring Boot applications:

```kotlin
dependencies {
    implementation("io.github.minh124199:viet-template-spring-boot-starter:1.1.0")
    implementation("io.github.minh124199:viet-template-spring-security:1.1.0")
}
```

For plain Java applications without Spring:

```kotlin
dependencies {
    implementation("io.github.minh124199:viet-template-runtime:1.1.0")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:1.1.0")
}
```

---

## 3. Extension Configuration

The plugin registers the `vietTemplate` extension to customize compilation options:

```kotlin
vietTemplate {
    // Template source directory (default: src/main/viet-template)
    sourceDirectory.set(file("src/main/resources/templates"))

    // Output directory for compiled classes (default: build/generated/viet-template/classes)
    outputDirectory.set(layout.buildDirectory.dir("generated/viet-template/classes"))

    // Output directory for templates.idx (default: build/generated/viet-template/resources)
    resourceOutputDirectory.set(layout.buildDirectory.dir("generated/viet-template/resources"))

    // Package prefix for generated classes
    packagePrefix.set("io.github.minh124199.viettemplate.generated")

    // Incremental compilation using fingerprint tracking
    incremental.set(true)

    // Character encoding
    encoding.set("UTF-8")

    // Fail build if template warnings occur
    failOnWarning.set(false)

    // Static Contract Validation: OFF (default), WARN, or ERROR
    typeChecking.set("OFF")
}
```

---

## 4. Gradle Plugin Tasks & Cacheability

The plugin registers six tasks for build-time operations:

| Task Name | Task Class | Description |
|---|---|---|
| `validateVietTemplates` | `VietTemplateValidateTask` | Validates templates in-memory for syntax, contract conformity, and static `#parse`/`#include` dependencies during `check`. |
| `explainVietTemplates` | `VietTemplateExplainTask` | Explains compiler decisions (types, access planning, output dispatch specialization, AOT eligibility) in structured text/JSON without rendering (`help` group). |
| `compileVietTemplates` | `VietTemplateCompileTask` | Compiles VTL templates Ahead-Of-Time into JVM bytecode and generates `META-INF/viet-template/templates.idx`. |
| `generateVietTemplateFacades` | `VietTemplateGenerateFacadesTask` | Generates strongly-typed Java facade classes from declared template contracts. |
| `generateVietTemplateSchemas` | `VietTemplateGenerateSchemasTask` | Generates canonical contract schemas (`*.vt-schema.json`) for templates with contracts. |
| `generateVietTemplateTypeScript` | `VietTemplateGenerateTypeScriptTask` | Projects canonical schemas into TypeScript declarations (`*.d.ts`). |

### 4.1 Compilation Task Features
- **Task Type**: `@CacheableTask` `VietTemplateCompileTask`.
- **Automatic Wiring**: When the `java` plugin is present, `compileVietTemplates` automatically wires into `classes`, and its output directories are registered as production outputs of `sourceSets.main`.
- **Configuration Cache**: Fully compatible with the Gradle Configuration Cache (`--configuration-cache`).
- **Build Cache**: Generated classes and `templates.idx` are cacheable across developer machines and CI agents.

Execute template compilation:

```bash
./gradlew compileVietTemplates
```

Or build the complete project:

```bash
./gradlew build
```

### 4.2 Explaining Compiler Decisions (`explainVietTemplates`)
The `explainVietTemplates` task exposes structured compiler truth regarding types, member resolution, output dispatch specialization, and AOT eligibility:

```bash
# Human-readable explanation of templates
./gradlew explainVietTemplates

# With contract type checking
./gradlew explainVietTemplates -PvietTemplate.typeChecking=WARN

# Filter single template and expression position
./gradlew explainVietTemplates -PvietTemplate.template=order.vtl -PvietTemplate.line=10 -PvietTemplate.column=3

# Output structured JSON report
./gradlew explainVietTemplates -PvietTemplate.format=json -PvietTemplate.outputFile=build/reports/explain.json

# Fail if dynamic fallback paths are required
./gradlew explainVietTemplates -PvietTemplate.failOnDynamicFallback=true
```

---

## 5. Production Deployment Configuration

In `application.properties`:

```properties
# Enforce pure precompiled execution in production
viet-template.runtime-compilation-enabled=false
```
