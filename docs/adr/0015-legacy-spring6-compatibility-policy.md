# ADR-0015 — Legacy Spring Framework 6 and Spring Boot 3 Compatibility Policy

- Status: Accepted
- Date: 2026-09-17

## Context

Viet Template 0.2.0 establishes Spring Framework 7, Spring Boot 4, and Spring Security 7 as the canonical enterprise framework baseline ([ADR-0009](0009-canonical-spring7-boot4.md)). However, enterprise projects frequently operate on extended upgrade cycles and may remain on Spring Framework 6.x and Spring Boot 3.x for years.

Because Spring Boot 4 incorporates breaking API changes (e.g. `TestRestTemplate` package moves, revised auto-configuration metadata, Jakarta EE 11 servlet lifecycle adjustments, and updated Spring Security 7 authorization manager signatures), maintaining simultaneous dual-framework support within a single module artifact would require fragile reflective shims or complex shading.

## Decision

Establish a clear branch-based **compatibility policy for legacy Spring 6 and Spring Boot 3**:

1. **Mainline Focus (0.2.x+)**: Starting with Viet Template 0.2.0, all primary development on the `main` branch targets Spring Framework 7.0+, Spring Boot 4.0+, and Spring Security 7.0+.
2. **Maintenance Branch for Spring 6 (0.1.x)**: The `0.1.x` release series is designated as the Long-Term Support (LTS) maintenance line for Spring Framework 6.x and Spring Boot 3.x.
   - Critical bug fixes and security vulnerabilities will be backported to `0.1.x`.
   - The `0.1.x` branch maintains Java 17 and Spring Boot 3.3+ compatibility.
3. **Clear Dependency Boundaries**: Production artifacts under group `io.github.minh124199` will clearly indicate baseline expectations in release notes, and documentation provides explicit upgrade steps for moving from 0.1.x to 0.2.x alongside Spring Boot 4 migrations.

## Consequences

### Positive

- Keeps the Viet Template 0.2.x codebase clean, fast, and unencumbered by legacy backwards-compatibility hacks.
- Fully exploits the latest Spring 7 and Boot 4 features (native AOT, virtual threads, Jakarta Servlet 6.1).
- Provides existing Spring Boot 3 users with a stable, reliable 0.1.x branch for their current production deployments.

### Negative

- Users on Spring Boot 3 cannot use new language or engine features introduced exclusively in 0.2.0 without upgrading to Spring Boot 4.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0009 — Canonical Spring Framework 7 and Spring Boot 4 Integration](0009-canonical-spring7-boot4.md)
- [ADR-0010 — Java 17 Retirement and Migration Compatibility](0010-java17-retirement-and-compatibility.md)
