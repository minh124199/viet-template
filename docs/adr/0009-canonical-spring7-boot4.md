# ADR-0009 — Canonical Spring Framework 7 and Spring Boot 4 Integration

- Status: Accepted
- Date: 2026-09-17

## Context

Spring Framework 7 and Spring Boot 4 represent a generational evolution in enterprise Java web development. Key architectural advancements include:
- Baseline shift to Jakarta EE 11 (Jakarta Servlet 6.1, Tomcat 11, Undertow 2.4).
- Native first-class virtual thread execution model for web request handling and asynchronous dispatching.
- Spring Security 7 with streamlined authorization managers and unified request-matching architectures.
- Enhanced Ahead-Of-Time (AOT) engine integration and GraalVM native image optimization.

Template engines built for legacy Spring Boot 2/3 and Servlet 4/5 architectures suffer from blocking I/O assumptions, deprecated filter hierarchies, and thread-local state binding that can compromise virtual thread scaling.

## Decision

Establish **Spring Framework 7.0+ (canonical 7.0.9)**, **Spring Boot 4.0+ (canonical 4.1.1)**, and **Spring Security 7.0+ (canonical 7.1.1)** (with JUnit Jupiter 6.0.3, Tomcat 11.0.24, and Jakarta Servlet 6.1.0) as the canonical supported enterprise stack for Viet Template:

1. **Jakarta EE 11 Alignment**: The servlet view layer targets Jakarta Servlet 6.1.0 directly on Tomcat 11.0.24. Stream handling utilizes non-closing streaming output abstractions (`NonClosingOutputStream`) to integrate safely with container-managed response lifecycles.
2. **Virtual Thread Native Dispatch**: View resolution, template compilation caching, and stream rendering fully support `spring.threads.virtual.enabled=true` on Tomcat 11 without thread-pinning or synchronization bottlenecks.
3. **Spring Security 7 Integration**: Context contributors (`SpringSecurityRenderContextContributor`) adapt Spring Security 7 `SecurityContextHolderStrategy` and `CsrfToken` attributes without coupling to deprecated security filters.
4. **Boot 4 Auto-Configuration**: Auto-configuration is implemented in `viet-template-spring-boot-autoconfigure` using Spring Boot 4 `@AutoConfiguration` mechanics and `@ConditionalOnClass` guards.
5. **GraalVM Native AOT**: Runtime hints are registered via `VietTemplateRuntimeHints` implementing `RuntimeHintsRegistrar`, ensuring instantaneous native image startup and reflection-free view resolution.

## Consequences

### Positive

- Zero deprecation warnings against Spring 7 and Boot 4 APIs.
- Full compatibility with Tomcat 11 and virtual-thread-based high-concurrency request servicing.
- Flawless compilation to GraalVM native executables with automated AOT hint registration.
- Enterprise-grade security integration with CSRF token generation and role-based view customization.

### Negative

- Direct usage on Spring Boot 3.x / Spring 6.x requires either remaining on Viet Template 0.1.x or using isolated compatibility bridges per [ADR-0015](0015-legacy-spring6-compatibility-policy.md).

## References

- [ADR-0013 — Virtual Thread Invariants](0013-virtual-thread-invariants.md)
- [ADR-0015 — Legacy Spring 6 Compatibility Policy](0015-legacy-spring6-compatibility-policy.md)
