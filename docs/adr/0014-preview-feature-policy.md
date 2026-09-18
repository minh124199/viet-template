# ADR-0014 — Java Preview Feature Policy

- Status: Accepted
- Date: 2026-09-17

## Context

The OpenJDK release cadence introduces preview language features and incubating APIs via `--enable-preview` (JEP 12). While preview features offer early access to prospective JVM capabilities (e.g. string templates, foreign function APIs, structured concurrency, or early class-file APIs), utilizing them in library code presents severe hazards for downstream consumers:
1. **Viral Runtime Requirement**: Classes compiled with `--enable-preview` receive classfile minor version `65535` (`0xFFFF`). The JVM refuses to load such classes unless the `--enable-preview` flag is explicitly passed to the runtime launcher.
2. **API Churn**: Preview APIs frequently change signatures or get redesigned between intermediate JDK releases, causing breaking binary and source incompatibilities.
3. **Production Hostility**: Enterprise production environments strictly forbid running with `--enable-preview` due to lack of backward compatibility guarantees.

## Decision

Enforce a **zero-preview-feature policy for all published production artifacts**:

1. **Strict Production Prohibition**: No production module (`viet-template-api`, `runtime`, `language-vtl`, `interpreter`, `spring`, `spring-security`, `spring-boot-starter`, etc.) may use Java preview features or be compiled with `--enable-preview`.
2. **Compilation Verification**: The build scripts enforce `-Werror` and `-Xlint:all` with standard `-release 21`. Any accidental use of preview constructs will fail the build immediately.
3. **Restricted Research Scope**: Preview or experimental JVM features may only be explored in dedicated benchmark subprojects (e.g., within isolated suites in `viet-template-benchmarks`) or internal spike modules that are never published to Maven Central.
4. **Artifact Cleanliness**: Published JARs must always have classfile minor version `0` and major version `65`, ensuring clean execution on standard JVMs without special flags.

## Consequences

### Positive

- Downstream applications can consume Viet Template without needing `--enable-preview` JVM flags.
- Maximum stability and binary compatibility across all supported JDK releases.
- Clean compilation and compliance with enterprise security and deployment standards.

### Negative

- Exciting prospective Java language or library features cannot be incorporated into public APIs until they achieve permanent final status in an LTS release.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0011 — Rejection of Multi-Release JARs (MRJARs)](0011-no-multi-release-jars.md)
