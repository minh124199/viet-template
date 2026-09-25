# Gradle Adoption & Setup Guide

## 1. Overview & Plugin ID

Viet Template provides a dedicated Gradle plugin (`io.github.minh124199.viet-template`) for build-time Ahead-Of-Time (AOT) template compilation, compatible with Gradle 8.x and Gradle 9.x.

The latest stable release is **`0.2.2`** (published 2026-09-20). The latest preview release candidate is **`1.0.0-RC1`** (published 2026-09-24). Consumer examples below show the stable `0.2.2` coordinates, with `1.0.0-RC1` available for previewing candidate features.

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
    id("io.github.minh124199.viet-template") version "0.2.2"
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
    id 'io.github.minh124199.viet-template' version '0.2.2'
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
    implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.2")
    implementation("io.github.minh124199:viet-template-spring-security:0.2.2")
}
```

For plain Java applications without Spring:

```kotlin
dependencies {
    implementation("io.github.minh124199:viet-template-runtime:0.2.2")
    implementation("io.github.minh124199:viet-template-vtl-interpreter:0.2.2")
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
}
```

---

## 4. Compilation Task & Cacheability

The plugin registers a dedicated task named `compileVietTemplates`:

- **Task Type**: `@CacheableTask` `VietTemplateCompileTask`.
- **Automatic Wiring**: When the `java` plugin is present, the task automatically wires into `classes`, and its output directories are registered as production outputs of `sourceSets.main`.
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

---

## 5. Production Deployment Configuration

In `application.properties`:

```properties
# Enforce pure precompiled execution in production
viet-template.runtime-compilation-enabled=false
```
