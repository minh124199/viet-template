# Maven Adoption & Setup Guide

## 1. Overview & Coordinates

Viet Template provides complete build-time Ahead-Of-Time (AOT) compilation and Spring Boot integration for Apache Maven projects.

The latest published stable release is **`1.0.1`** (published 2026-09-28; based on the 1.0.0 GA line). The `1.1.0` release candidate is under preparation and is not published yet. Consumer examples below use the available `1.0.1` coordinates unless they explicitly describe the release candidate.

### Minimum Requirements
- **Java**: Java 21 (`--release 21`) or newer.
- **Maven**: Apache Maven 3.9.0 or newer.

---

## 2. Dependencies

### 2.1 Basic Java Runtime
For standalone Java applications without Spring:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-runtime</artifactId>
    <version>1.0.1</version>
</dependency>
```

To include the standard VTL interpreter engine:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-vtl-interpreter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 2.2 Spring Boot Starter
For Spring Boot applications (Spring MVC, auto-configuration, and view resolution):

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 2.3 Spring Security Integration
For authenticated user and CSRF view facades:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-security</artifactId>
    <version>1.0.1</version>
</dependency>
```

---

## 3. Maven AOT Compiler Plugin

`viet-template-maven-plugin` compiles template files (`.vtl`, `.vm`) into Java bytecode `.class` files during the build, generating the `META-INF/viet-template/templates.idx` registration index.

### 3.1 Plugin Configuration

Add the plugin to the `<build><plugins>` section of your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-maven-plugin</artifactId>
    <version>1.0.1</version>
    <executions>
        <execution>
            <id>compile-templates</id>
            <phase>process-classes</phase>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Template source directory (default: src/main/viet-template) -->
        <sourceDirectory>${project.basedir}/src/main/resources/templates</sourceDirectory>
        
        <!-- Output directory for compiled classes (default: target/classes) -->
        <outputDirectory>${project.build.outputDirectory}</outputDirectory>
        
        <!-- Output directory for templates.idx (default: target/classes) -->
        <resourceOutputDirectory>${project.build.outputDirectory}</resourceOutputDirectory>
        
        <!-- Package prefix for generated classes -->
        <packagePrefix>io.github.minh124199.viettemplate.generated</packagePrefix>
        
        <!-- Incremental compilation based on template SHA-256 fingerprint -->
        <incremental>true</incremental>
        
        <!-- File patterns to include/exclude -->
        <includes>
            <include>**/*.vtl</include>
            <include>**/*.vm</include>
        </includes>
        
        <encoding>UTF-8</encoding>
        <failOnWarning>false</failOnWarning>

        <!-- Static Contract Validation: OFF (default), WARN, or ERROR -->
        <typeChecking>OFF</typeChecking>
    </configuration>
</plugin>
```

### 3.2 Plugin Goals

The `viet-template-maven-plugin` provides four goals for build-time operations:

| Goal | Default Phase | Description |
|---|---|---|
| `compile` | `process-classes` | Compiles VTL templates Ahead-Of-Time into JVM bytecode and generates the registration index `META-INF/viet-template/templates.idx`. |
| `generate-facades` | `generate-sources` | Generates strongly-typed Java facade classes from declared template contracts for compile-time safe model binding. |
| `generate-schemas` | `process-classes` | Extracts canonical JSON contract schemas (`*.vt-schema.json`) for templates with declared `#*contract ... *#` blocks. |
| `generate-typescript` | `process-classes` | Projects canonical contract schemas into TypeScript interface declarations (`*.d.ts`) for frontend/fullstack type safety. |

### 3.3 Compilation Phase & Execution
The `compile` goal binds by default to `process-classes`. It runs after Java source compilation, ensuring compiled domain classes and DTOs are available on the compilation classpath for typed model inspection.

```bash
mvn compile
```

### 3.4 Incremental Build Support
When `<incremental>true</incremental>` is enabled, the plugin tracks template modification timestamps and SHA-256 content hashes. Unmodified templates are skipped during subsequent builds, providing sub-second incremental build times.

---

## 4. Production Deployment Recommendation

In production, disable dynamic runtime compilation in `application.properties`:

```properties
# Reject uncompiled templates at runtime
viet-template.runtime-compilation-enabled=false
```

When runtime compilation is disabled, the engine solely executes precompiled bytecode discovered from `META-INF/viet-template/templates.idx`.
