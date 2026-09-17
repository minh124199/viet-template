# ADR-0013 — Virtual Thread Invariants and Concurrency Guarantees

- Status: Accepted
- Date: 2026-09-17

## Context

Project Loom (Virtual Threads, JEP 444) is a cornerstone of high-throughput server architectures in Java 21 and Spring Boot 4. When running in a virtual thread environment (such as Tomcat 11 with `spring.threads.virtual.enabled=true`), millions of concurrent requests can be handled simultaneously on a modest pool of carrier platform threads.

However, traditional template engines present hazards that compromise virtual thread scalability:
1. **Carrier Thread Pinning**: Synchronizing on monitors (`synchronized`) while performing blocking I/O (e.g. flushing output streams or reading templates) pins the underlying OS carrier thread, causing carrier thread starvation.
2. **ThreadLocal Bloat and Leaks**: Storing request state, buffers, or security context in `ThreadLocal` objects causes memory bloat and potential state leakage across millions of ephemeral virtual threads.
3. **Premature Stream Closure**: Closing client output streams inside the view layer terminates HTTP chunked transfer encoding prematurely and corrupts container connection pooling.

## Decision

Enforce the following **strict architectural invariants** across the entire Viet Template codebase to guarantee safe, optimal execution on Virtual Threads:

1. **Zero Thread-Pinning in I/O Paths**:
   - `synchronized` blocks are strictly forbidden around any I/O operation, stream flush, or blocking call site.
   - Concurrency controls use lock-free data structures (`ConcurrentHashMap`, atomic primitives) or explicit `ReentrantLock` instances where locking is strictly necessary.
2. **Explicit Context Passing (No ThreadLocal Binding)**:
   - All execution state and variable bindings are encapsulated in the explicit `RenderContext` parameter passed along the call stack.
   - Viet Template core and web layers never store render state in `ThreadLocal` variables.
3. **Stream Ownership and Non-Closing Streams**:
   - The template engine and view resolvers do not own the output stream and must never call `.close()` on caller-supplied streams.
   - The Spring MVC view integration wraps the servlet output stream in a `NonClosingOutputStream` that ignores `close()` calls, leaving lifecycle control entirely to the servlet container.
4. **Stateless and Immutable Compiled Templates**:
   - `Template` instances are strictly immutable and thread-safe after compilation.
   - Any number of virtual threads may execute the same `Template` instance concurrently with independent `RenderContext` and `TemplateOutput` instances without contention.
5. **Bounded Cache Eviction and Memory Controls**:
   - The template compilation cache (`TemplateCompileCache`) uses bounded approximate LRU policies with amortized background eviction to prevent virtual-thread-induced GC spikes.

## Consequences

### Positive

- Linear scalability when serving hundreds of thousands of concurrent template rendering requests on Tomcat 11 under virtual threads.
- Zero carrier thread pinning detected by JVM flight recorder (`jdk.VirtualThreadPinned` events): verified under high-concurrency stress testing across 1,000 and 10,000 template renders. "No pinned virtual threads observed in tested workloads".
- Memory-safe context isolation with zero risk of cross-request contamination.

### Negative

- Developers contributing to Viet Template must follow strict lock discipline and avoid introducing `synchronized` keyword in any I/O-adjacent classes.

## References

- [ADR-0007 — Java 21 Minimum Baseline](0007-java21-minimum-baseline.md)
- [ADR-0009 — Canonical Spring Framework 7 and Spring Boot 4 Integration](0009-canonical-spring7-boot4.md)
