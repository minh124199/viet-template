# Sources and External Baselines

This file records external specifications and implementation references used to define the initial engineering baseline. It is **not** a license to copy implementation code. The project uses an independent implementation approach: public language behavior and public documentation may be used as behavioral references; implementation must be independently written and all production artifacts must maintain strict dependency isolation from Apache Velocity classes.

## Apache Velocity

Primary behavioral reference for migration compatibility:

- Apache Velocity Engine project: <https://velocity.apache.org/engine/>
- VTL Reference: <https://velocity.apache.org/engine/devel/vtl-reference.html>
- Velocity User Guide: <https://velocity.apache.org/engine/devel/user-guide.html>
- Velocity changes: <https://velocity.apache.org/engine/devel/changes.html>
- Apache Velocity repository: <https://github.com/apache/velocity-engine>

Important behaviors to freeze into the compatibility TCK rather than relying on memory:

- reference, quiet-reference, and formal-reference syntax;
- property lookup and method invocation semantics;
- truthiness/coercion;
- `#set`, `#if`, `#foreach`, `#include`, `#parse`, `#macro`, `#define`, `#break`, `#stop`, and `#evaluate` behavior;
- escaping/comments;
- macro and scope semantics;
- collection/map/array/indexing behavior.

## Quarkus Qute

Primary modern architecture/performance comparator:

- Qute guide: <https://quarkus.io/guides/qute>
- Qute reference: <https://quarkus.io/guides/qute-reference>

Areas worth comparing rather than copying:

- build-time template validation;
- type-safe templates;
- generated value resolvers;
- reflection minimization;
- native-image behavior;
- developer diagnostics.

## Spring Framework / Spring Boot

Integration baselines:

- Spring Framework reference: <https://docs.spring.io/spring-framework/reference/>
- Spring MVC view technologies: <https://docs.spring.io/spring-framework/reference/web/webmvc-view.html>
- Spring Boot reference: <https://docs.spring.io/spring-boot/reference/>
- Spring Boot auto-configuration authoring: <https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html>

**Historical design baseline:** This source inventory was written during the 1.0/1.1 development cycle. Current framework minima and independently tested versions are maintained in [`config/compatibility/framework-support.json`](../config/compatibility/framework-support.json) and summarized in the [support matrix](getting-started/support-matrix.md); do not use this initial design note as a current compatibility guarantee.

> [!NOTE]
> **Historical 1.0 implementation baseline:** Milestone M16 established an earlier Spring integration baseline of Spring Framework 6.1.14 / Spring Boot 3.3.5. Java 17 in the original design note predates the current Java 21 minimum. Framework 7 / Boot 4 are now tested; the current declared minima and exact tested versions are in [`config/compatibility/framework-support.json`](../config/compatibility/framework-support.json). This historical note does not define current support.

## Java/JVM

- Java SE 25 Class-File API (`java.lang.classfile`): <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/package-summary.html>
- JVM Specification: <https://docs.oracle.com/javase/specs/>
- MethodHandle API: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandle.html>
- CallSite API: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/CallSite.html>
- JEP 444: Virtual Threads (Java 21): <https://openjdk.org/jeps/444>
- JEP 439: Generational ZGC (Java 21): <https://openjdk.org/jeps/439>
- JEP 519: Compact Object Headers (Java 25): <https://openjdk.org/jeps/519> (Note: JEP 450 was experimental in JDK 24)
- JEP 483: Ahead-of-Time Class Loading & Linking (Java 24 foundation): <https://openjdk.org/jeps/483>
- JEP 514: Ahead-of-Time Command-Line Ergonomics (Java 25): <https://openjdk.org/jeps/514>
- JEP 515: Ahead-of-Time Method Profiling (Java 25): <https://openjdk.org/jeps/515>
- JEP 520: JFR Method Timing & Tracing (Java 25): <https://openjdk.org/jeps/520>
- JEP 484: Class-File API (Java 24): <https://openjdk.org/jeps/484>
- Java Flight Recorder (JFR) & `jfr view` CLI diagnostic tool: <https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html>

Design rule: runtime API compatibility and compiler implementation JDK are separate concerns. The optional JDK-25 compiler module may use the standard Class-File API, while runtime modules retain the declared Java 17 baseline. Exact generated class-file targets must be validated empirically in CI.

## Benchmark comparators

Use official/current versions when the benchmark suite is executed, and record exact versions in the result artifact:

- Apache Velocity
- Thymeleaf
- Quarkus Qute
- jte
- Rocker
- Handwritten Java renderer baseline

Never reuse third-party benchmark numbers as product claims. They are useful only for forming hypotheses; publish results from the project's reproducible harness.

## Source-freezing policy

For every release that claims Velocity compatibility:

1. record the exact Velocity version used as the oracle;
2. snapshot the project's own input fixtures and expected outcomes;
3. record intentional differences in the compatibility manifest;
4. do not silently change semantics when the upstream oracle changes;
5. add a new compatibility baseline/version when necessary.
