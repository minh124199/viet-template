# Support Matrix & Compatibility

This document outlines the official environment support matrix for **Viet Template 0.2.x**, including JDK runtimes, build tooling, framework integrations, and execution environments.

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

| Build Tool | Supported Versions | Plugin Artifact ID / Gradle Plugin ID |
|---|---|---|
| **Apache Maven** | `3.9.0` and newer | `io.github.minh124199:viet-template-maven-plugin` |
| **Gradle** | `8.5` and newer (including `8.10+`) | `io.github.minh124199.viet-template` |

---

## 3. Spring Ecosystem

Viet Template is built natively for next-generation Spring Framework and Spring Boot releases:

| Framework | Supported Versions | Integration Module |
|---|---|---|
| **Spring Boot** | `4.0.0` and newer | `viet-template-spring-boot-starter`, `viet-template-spring-boot-autoconfigure` |
| **Spring Framework** | `7.0.0` and newer | `viet-template-spring-web-servlet`, `viet-template-spring-web-reactive` |
| **Spring Security** | `7.0.0` and newer | `viet-template-spring-security` |

*Note: For Spring Boot 3.x / Spring Framework 6.x applications, contact the maintainers or evaluate migration prerequisites, as the default starter targets the Spring 7 baseline.*

---

## 4. Execution Environments & JVM Features

| Feature / Environment | Support Status | Architectural Characteristics |
|---|---|---|
| **HotSpot JVM** | **Supported** | Validated on OpenJDK, Eclipse Temurin, Azul Zulu, Amazon Corretto, and GraalVM JDK. |
| **Virtual Threads (Loom)** | **Supported** | Completely thread-safe and non-pinning. Viet Template avoids `synchronized` blocks on hot rendering paths, using `ReentrantLock` and fine-grained concurrent data structures to eliminate carrier-thread pinning. |
| **GraalVM Native Image** | **Supported** | Ahead-of-Time native image compilation supported via `VietTemplateRuntimeHints`. In AOT mode (`viet-template.runtime-compilation-enabled=false`), rendering is completely reflection-free. |
| **Spring AOT Processing** | **Supported** | Automatic contribution of GraalVM reflection and resource hints during `processAot` build phase. |
| **Spring Boot DevTools** | **Supported** | Restart-safe classloader handling. Compile cache isolates and tracks restart classloaders to prevent memory leaks or stale template definitions. |

---

## 5. Operating Systems & Architectures

Viet Template is 100% pure Java and contains no JNI or OS-specific native bindings:

| Operating System | Architecture | Verification Status |
|---|---|---|
| **Linux** | `x86_64` (AMD64), `aarch64` (ARM64) | Tested continuously in CI (Ubuntu 22.04 / 24.04). |
| **macOS** | `aarch64` (Apple Silicon M1/M2/M3/M4), `x86_64` (Intel) | Verified. |
| **Windows** | `x86_64` (Windows Server 2022, Windows 11) | Verified. |

---

## 6. Compatibility Qualification Gates

Every Viet Template release must pass the following continuous qualification gates before publication:

1. **VTL TCK (Technology Compatibility Kit)**: 100% pass rate across 80 VTL language specification tests.
2. **Dual-Build Parity (`verify-build-parity.py`)**: Strict parity between Maven and Gradle builds.
3. **Public API & Surface Classification (`verify-public-surface-classification.py`)**: Explicit verification of all exported types against baseline.
4. **Code Quality & Formatting**: Zero spotless errors (`spotlessJavaCheck`).
5. **Virtual-Thread Concurrency**: Stress-tested under multi-threaded concurrency without deadlocks or thread starvation.
