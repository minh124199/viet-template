# ADR-0011 — Rejection of Multi-Release JARs (MRJARs)

- Status: Accepted
- Date: 2026-09-17

## Context

Java 9 introduced Multi-Release JARs (JEP 238), allowing a single archive to contain version-specific class files under `META-INF/versions/<N>/`. While MRJARs permit selective adoption of newer JDK APIs while retaining a lower runtime baseline, in practice they present severe operational and engineering challenges:
1. **Tooling and Packaging Fragility**: Shading plugins (e.g., `maven-shade-plugin`, Gradle `shadow`), fat-jar creators (Spring Boot repackager, Quarkus fast-jar), and OSGi bundles frequently fail to correctly merge, relocate, or inspect classes within `META-INF/versions/`.
2. **Classloader and Debugging Opacity**: Stack traces, debugger breakpoints, and IDE navigations become non-deterministic when classes are dynamically loaded from different version subdirectories depending on JVM launch flags.
3. **Build Parity Complexity**: Achieving byte-for-byte build parity between Gradle and Maven across multi-release structures requires complex and error-prone custom plugin tasks.
4. **Testing Incompleteness**: Tests running against the root classes frequently fail to exercise the multi-release overrides unless explicitly executed in matrix permutations.

## Decision

Viet Template **explicitly rejects the use of Multi-Release JARs (MRJARs)**:

1. **Single Uniform Target**: All released artifacts contain exactly one uniform class hierarchy compiled strictly to Java 21 (`-release 21`, classfile major version 65).
2. **No `META-INF/versions/`**: The build configuration forbids the generation or packaging of version-specific directories in production JARs.
3. **Runtime Adaptability via Polymorphism/SPI**: When JVM-specific optimizations or runtime detections are necessary (e.g., detecting virtual threads or platform capabilities), they are handled via standard runtime feature probes, dynamic method handles, or ServiceLoader SPI abstractions rather than divergent bytecode packages.

## Consequences

### Positive

- Clean, simple, deterministic JAR artifacts that work identically across standard classloaders, module paths, shaded fat JARs, and GraalVM native images.
- 100% build parity between Maven and Gradle with trivial verification via standard diff tools.
- Straightforward debugging experience in all IDEs with zero version confusion.

### Negative

- Features that strictly require Java 22+ compile-time types cannot be included in core modules until the minimum baseline is raised.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0014 — Preview Feature Policy](0014-preview-feature-policy.md)
