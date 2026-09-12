# Sources and External Baselines

This file records external specifications and implementation references used to define the initial engineering baseline. It is **not** a license to copy implementation code. The project should use a clean-room compatibility approach: public language behavior and public documentation may be used as behavioral references; implementation must be independently written and all dependencies must comply with their licenses.

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

At the time this design pack was written (September 2026), target the current stable Spring Framework 7.0.x and Spring Boot 4.1.x lines, while treating later preview lines as CI-only compatibility targets until stable.

## Java/JVM

- Java SE 25 Class-File API (`java.lang.classfile`): <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/classfile/package-summary.html>
- JVM Specification: <https://docs.oracle.com/javase/specs/>
- MethodHandle API: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/MethodHandle.html>
- CallSite API: <https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/invoke/CallSite.html>
- JEP 444: Virtual Threads (Java 21): <https://openjdk.org/jeps/444>
- JEP 439: Generational ZGC (Java 21): <https://openjdk.org/jeps/439>
- JEP 450: Compact Object Headers (Java 25): <https://openjdk.org/jeps/450>
- JEP 483: Ahead-of-Time Class Loading & Compilation (Java 25): <https://openjdk.org/jeps/483>
- JEP 514: Ahead-of-Time Method Compilation (Java 25): <https://openjdk.org/jeps/514>
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
