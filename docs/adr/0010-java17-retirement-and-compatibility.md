# ADR-0010 — Java 17 Retirement and Migration Compatibility

- Status: Accepted
- Date: 2026-09-17

## Context

Viet Template 0.1.x was developed with a Java 17 baseline. While Java 17 served as a long-standing LTS platform, maintaining compatibility with it imposed substantial architectural friction on Viet Template 0.2.x:
1. **Language Limitations**: Inability to use sealed hierarchies with pattern-matching switch expressions in public IR APIs without awkward compiler flags or visitor fallbacks.
2. **Bytecode Generation**: The internal AOT compiler had to maintain compatibility with JVM classfile version 61 (Java 17), preventing clean code generation of modern JVM idioms.
3. **Ecosystem Evolution**: Spring Boot 4, Tomcat 11, and Jakarta EE 11 require modernized environments, making Java 17 an obsolete target for the primary enterprise use cases of Viet Template.

## Decision

Formally **retire Java 17 support** starting in Viet Template 0.2.0:

1. **Hard Baseline**: All code, build scripts, tests, and maven/gradle plugins require Java 21 or higher. No binary artifacts targeting Java 17 (classfile major version 61) will be published for 0.2.0+.
2. **Maintenance of 0.1.x**: The `0.1.x` branch will be maintained for critical security vulnerabilities and severe bug fixes for users unable to upgrade from Java 17, following a defined end-of-life maintenance window.
3. **Migration Guidance**: Applications migrating from 0.1.x to 0.2.0 must upgrade their JVM execution environment to Java 21 LTS or Java 25 LTS. Since the public template API (`TemplateEngine`, `Template`, `RenderContext`) remains semantically compatible, upgrading only requires updating the runtime JVM and dependency versions.

## Consequences

### Positive

- Complete removal of legacy Java 17 compilation hacks, build splits, and test workarounds.
- Streamlined CI pipelines without the need to maintain redundant Java 17 build agents.
- Cohesive architectural foundation built on sealed records, modern switch patterns, and virtual threads.

### Negative

- Strict barrier to entry for organizations still operating legacy Java 17 runtimes.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0008 — Java 25 Primary Runtime and Build Toolchain](0008-java25-primary-runtime.md)
