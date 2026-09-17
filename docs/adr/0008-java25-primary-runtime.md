# ADR-0008 — Java 25 Primary Runtime and Build Toolchain

- Status: Accepted
- Date: 2026-09-17

## Context

While Java 21 provides the minimum bytecode and API baseline ([ADR-0007](0007-java21-minimum-baseline.md)), Java 25 represents the latest Long-Term Support (LTS) release of the Java platform. Java 25 introduces critical performance enhancements for high-concurrency, low-latency template rendering:
1. **Generational ZGC**: Near-zero pause times (<1ms) even under heavy allocation rates typical of template string materialization.
2. **Compact Object Headers (COH)**: Reduced object memory footprint, improving L1/L2 cache locality during AST/IR traversal and buffer pooling.
3. **Advanced JIT & Native Image (GraalVM 25)**: Superior escape analysis, loop unrolling, and profile-guided optimizations (PGO) for AOT bytecode execution.

To maximize developer ergonomics and ensure optimal production deployments, the project needs an explicitly designated primary runtime and build environment.

## Decision

Designate **Java 25** as the primary build toolchain, benchmark environment, and recommended production deployment runtime:

1. **Build Toolchain**: All official releases, CI Tier A matrix suites, and local developer builds prioritize Java 25 as the execution JVM, configured with `-release 21` compilation to ensure strict Java 21 bytecode compatibility.
2. **Performance Baselines**: Official performance benchmarks (JMH) and latency regression tests run primarily on Java 25 utilizing Generational ZGC and Compact Object Headers.
3. **Native Image Target**: GraalVM for JDK 25 is the canonical target for ahead-of-time native image compilation.
4. **Cross-Compilation Verification**: Strict bytecode verification (`-release 21`, major version 65) ensures that artifacts built on JDK 25 execute identically on Java 21 runtimes without relying on JDK 25-specific standard library methods.

## Consequences

### Positive

- Ensures maximum throughput and minimal tail latency on modern cloud infrastructure running Java 25.
- Developers benefit from the fastest compiler passes, latest diagnostic tools, and modernized GC implementations.
- Guaranteed compatibility with cutting-edge GraalVM 25 native compilation toolchains.

### Negative

- Developers must have JDK 21 minimum and preferably JDK 25 installed locally for full benchmark and toolchain parity.
- Requires dual-tier CI testing (Tier A on Java 25, Tier B on Java 21) to safeguard against accidental leakage of Java 22-25 APIs into Java 21 codebases.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0014 — Preview Feature Policy](0014-preview-feature-policy.md)
