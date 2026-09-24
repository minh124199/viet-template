# Support Matrix & Compatibility

This document outlines the official environment support matrix for **Viet Template 1.0.0-RC1**, including JDK runtimes, build tooling, framework integrations, and execution environments.

---

## 1. Java Runtimes (JDK)

Viet Template enforces a hard baseline of **Java 21**. All production classes and Maven/Gradle plugins are compiled to Java 21 bytecode target (`--release 21`).

| Java Version | Support Status | Notes |
|---|---|---|
| **Java 25** | **Primary Target** | Primary development, profiling, and performance qualification runtime. Leverages JEP 450 compact object headers and optimized memory operations. |
| **Java 21 LTS** | **Supported (Baseline)** | Minimum supported Java runtime and compilation baseline. Fully verified across all modules, tests, and TCK suites. |
| **Java 22, 23, 24** | **Supported** | Fully compatible with interim non-LTS releases. |
| **Java 17 and earlier** | **Unsupported** | Viet Template requires language and runtime capabilities of Java 21+ (records, pattern matching, sequenced collections). Applications running on Java 17 must upgrade to Java 21+. |

---

## 2. Build Tooling

Both Apache Maven and Gradle are supported as first-class build tools with verified build parity.

| Build Tool | Declared Minimum | RC-Tested Versions | Canonical RC Version | Plugin Coordinates |
|---|---|---|---|---|
| **Apache Maven** | `3.8.0` | `3.9.9` | `3.9.9` (wrapper) | `io.github.minh124199:viet-template-maven-plugin:1.0.0-RC1` |
| **Gradle** | `8.5` | `9.7.1` | `9.7.1` (wrapper) | `io.github.minh124199.viet-template:1.0.0-RC1` |

---

## 3. Framework Integrations & Compatibility Matrix

Viet Template specifies exact compatibility boundaries with explicit Declared Minimum, RC-Tested, and Canonical RC versions:

| Framework / Tool | Declared Minimum | RC-Tested Versions | Canonical RC Version | Native Image Status | Integration Modules |
|---|---|---|---|---|---|
| **Spring Boot** | `3.3.0` | `3.3.5`, `4.1.1` | `4.1.1` | Supported (Oracle GraalVM 25.0.4+7.1) | `viet-template-spring-boot-starter`, `viet-template-spring-boot-autoconfigure` |
| **Spring Framework** | `6.1.0` | `6.1.14`, `7.0.9` | `7.0.9` | Supported (Oracle GraalVM 25.0.4+7.1) | `viet-template-spring` |
| **Spring Security** | `6.3.0` | `6.3.4`, `6.5.11`, `7.0.7`, `7.1.1` | `7.1.1` | Supported (Oracle GraalVM 25.0.4+7.1) | `viet-template-spring-security` |
| **Quarkus** | `3.33.0` | `3.33.3` (LTS), `3.39.4` | `3.39.4` | Supported (Mandrel 25.0.4.1-Final) | `viet-template-quarkus`, `viet-template-quarkus-deployment` |

### 3.1 Quarkus Ecosystem Details
- **Canonical Modern**: Quarkus `3.39.4` tested across Maven and Gradle fixtures.
- **LTS Tested**: Quarkus `3.33.3` LTS line verified in continuous integration.
- **Declared Minimum**: Quarkus `3.33.0` (requires Jakarta EE 10, CDI 4.0, SmallRye Config). Versions `< 3.33.0` are unsupported.

### 3.2 Spring Ecosystem Details
- **Canonical Modern**: Spring Boot `4.1.1` / Spring Framework `7.0.9` / Spring Security `7.1.1` (Jakarta Servlet 6.1.0, Tomcat 11.0.24).
- **LTS Generation 1**: Spring Boot `3.3.5` / Spring Framework `6.1.14` / Spring Security `6.3.4` verified by dedicated multi-generation consumer fixtures.
- **Additional Security Tested**: Spring Security `6.5.11` and `7.0.7` verified by single-artifact compatibility suite.

*Note: The canonical starter targets Spring Framework 7 / Spring Boot 4. For Spring Boot 3.x applications, multi-generation consumer fixtures verify compatibility against Spring Boot 3.3.5 and Spring Security 6.3.4.*

### 3.3 Standalone Jakarta EE & CDI Integration

- **Status**: `NO_STANDALONE_JAKARTA_INTEGRATION`
- **Scope**: Viet Template does not provide standalone Jakarta Servlet or CDI consumer modules. Jakarta Servlet (`6.1.0` canonical / `6.0.0` minimum) and CDI (`4.1.0`) are utilized strictly as transitive runtime dependencies of the Spring MVC (`viet-template-spring`) and Quarkus (`viet-template-quarkus`) integrations. Support claims for Jakarta EE and CDI apply solely within those framework contexts.

---

## 4. Execution Environments & JVM Features

| Feature / Environment | Support Status | Architectural Characteristics |
|---|---|---|
| **HotSpot JVM** | **Supported** | Validated on OpenJDK, Eclipse Temurin, Azul Zulu, Amazon Corretto, and GraalVM JDK. |
| **Virtual Threads (Loom)** | **Supported** | Completely thread-safe and non-pinning. Viet Template avoids `synchronized` blocks on hot rendering paths, using `ReentrantLock` and fine-grained concurrent data structures to eliminate carrier-thread pinning. |
| **GraalVM Native Image** | **Supported** | Ahead-of-Time native image compilation supported via `VietTemplateRuntimeHints` (Spring) and `VietTemplateProcessor` (Quarkus). In AOT mode (`runtime-compilation-enabled=false`), rendering is completely reflection-free. Quarkus native binary executables empirically qualified on Linux x86_64 (Mandrel 25.0.4.1-Final, Java 25). |
| **Spring AOT Processing** | **JVM_AOT_QUALIFIED** | Automatic contribution of GraalVM reflection and resource hints during `processAot` build phase. Packaged executable JARs verified under JVM AOT execution (`scripts/verify-spring-integration-parity.sh`); Spring native-image binary compilation remains separate. |
| **Spring Boot DevTools** | **Supported** | Restart-safe classloader handling. Compile cache isolates and tracks restart classloaders to prevent memory leaks or stale template definitions. |
| **Quarkus Dev Mode** | **Supported** | Live reload with transitive `#parse` dependency invalidation and zero static ClassLoader leakage. |

---

## 5. Operating Systems & Native Image Hosts

Viet Template differentiates JVM runtime execution from GraalVM native binary compilation:

### 5.1 Standard JVM Execution
| Operating System | Architecture | Support Status | Evidence |
|---|---|---|---|
| **Linux** | `x86_64`, `aarch64` | **Qualified** | Tested continuously in CI (Ubuntu 22.04 / 24.04). |
| **macOS** | `aarch64` (Apple Silicon), `x86_64` | **Qualified** | Tested in CI (`macos-latest`). |
| **Windows** | `x86_64` | **Qualified** | Tested in CI (`windows-latest`). |

### 5.2 GraalVM Native Executable Compilation & Execution
| Host Platform | Native Build Status | Native Execution Status | Evidence & Toolchain |
|---|---|---|---|
| **Linux x86_64** | **Empirically Qualified** | **PASS** | Spring Boot 3/4: Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS). Quarkus: Mandrel 25.0.4.1-Final (Java 25), GCC 16.2.1; verified HTTP 200 on ports 18095/18097. |
| **macOS aarch64** | **Experimental** | **Unverified** | JVM qualified; host native compilation not verified in CI. |
| **Windows x86_64** | **Experimental** | **Unverified** | JVM qualified; host native compilation not verified in CI. |

---

## 6. Compatibility Qualification Gates

Every Viet Template release must pass the following continuous qualification gates before publication:

1. **VTL TCK (Technology Compatibility Kit)**: 100% pass rate across 80 VTL language specification tests.
2. **Dual-Build Parity (`verify-build-parity.py`)**: Strict parity between Maven and Gradle builds.
3. **Public API & Surface Classification (`verify-public-surface-classification.py`)**: Explicit verification of all exported types against baseline.
4. **Code Quality & Formatting**: Zero spotless errors (`spotlessJavaCheck`).
5. **Virtual-Thread Concurrency**: Stress-tested under multi-threaded concurrency without deadlocks or thread starvation.
