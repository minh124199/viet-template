# Maven Adoption & Setup Guide

## 1. Overview & Coordinates

Viet Template provides complete build-time Ahead-Of-Time (AOT) compilation and Spring Boot integration for Apache Maven projects.

The current published release is **`0.2.2`**. All consumer examples below use `0.2.2`.

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
    <version>0.2.2</version>
</dependency>
```

To include the standard VTL interpreter engine:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-vtl-interpreter</artifactId>
    <version>0.2.2</version>
</dependency>
```

### 2.2 Spring Boot Starter
For Spring Boot applications (Spring MVC, auto-configuration, and view resolution):

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.2</version>
</dependency>
```

### 2.3 Spring Security Integration
For authenticated user and CSRF view facades:

```xml
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-security</artifactId>
    <version>0.2.2</version>
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
    <version>0.2.2</version>
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
    </configuration>
</plugin>
```

### 3.2 Compilation Phase & Execution
The `compile` goal binds by default to `process-classes`. It runs after Java source compilation, ensuring compiled domain classes and DTOs are available on the compilation classpath for typed model inspection.

```bash
mvn compile
```

### 3.3 Incremental Build Support
When `<incremental>true</incremental>` is enabled, the plugin tracks template modification timestamps and SHA-256 content hashes. Unmodified templates are skipped during subsequent builds, providing sub-second incremental build times.

---

## 4. Production Deployment Recommendation

In production, disable dynamic runtime compilation in `application.properties`:

```properties
# Reject uncompiled templates at runtime
viet-template.runtime-compilation-enabled=false
```

When runtime compilation is disabled, the engine solely executes precompiled bytecode discovered from `META-INF/viet-template/templates.idx`.
