# ADR-0012 — Class-File API Evaluation and Architecture Decision

- Status: Accepted
- Date: 2026-09-17

## Context

JDK 22 introduced JEP 457 (Class-File API) as a preview feature, followed by second preview in JDK 23 (JEP 466) and finalization in subsequent JDK releases. The standard `java.lang.classfile` API is intended to provide a standard JVM classfile parsing and generation facility, reducing the ecosystem's historic reliance on third-party libraries such as ASM or ByteBuddy.

Viet Template requires dynamic bytecode generation for its Ahead-Of-Time (AOT) and runtime compilation tiers. Currently, Viet Template utilizes an internal, zero-dependency classfile writer (`ClassFileWriter`) that directly emits binary classfiles conforming to JVM Specification SE 21 (major version 65).

We conducted an architectural evaluation to determine whether to migrate the template bytecode compiler to the JDK Class-File API.

## Decision

**Retain the zero-dependency `ClassFileWriter` as the canonical production compiler backend** and defer migrating the core engine to `java.lang.classfile`:

1. **Java 21 Baseline Compatibility**: The Java Class-File API is not finalized in Java 21 LTS; it was delivered as preview in Java 22/23. Adopting it in core modules would violate the strict Java 21 minimum baseline ([ADR-0007](0007-java21-minimum-baseline.md)) and the prohibition of preview features in production ([ADR-0014](0014-preview-feature-policy.md)).
2. **Zero-Dependency Guarantee**: The project's core principles mandate zero third-party dependencies for runtime bytecode generation. Viet Template's internal `ClassFileWriter` is compact (~900 LOC), highly tuned for template method emission, and has zero external dependencies.
3. **Performance and Memory**: `ClassFileWriter` pre-computes constant pool layouts and writes directly into pre-sized byte buffers, generating classes with minimal intermediate object allocations.
4. **Future Optional Prototype**: An optional compiler SPI implementation targeting `java.lang.classfile` may be explored in an isolated benchmark or experimental module under JDK 25 toolchains, but will not be part of the default runtime class path.

## Consequences

### Positive

- Retains 100% Java 21 LTS runtime compatibility without `--enable-preview` JVM flags.
- Preserves the zero-dependency footprint of `viet-template-vtl-interpreter`.
- Guarantees deterministic classfile generation across all Java 21 and Java 25 runtimes.

### Negative

- Viet Template must maintain its internal bytecode emission logic rather than relying on standard JDK library classes.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0014 — Preview Feature Policy](0014-preview-feature-policy.md)
