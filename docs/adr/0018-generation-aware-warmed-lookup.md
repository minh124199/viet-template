# ADR-0018: Generation-Aware Warmed-Lookup Simplification

**Status**: Accepted  
**Date**: 2026-09-23  
**Milestone**: 0.3.0-M5  

---

## Context

Prior to Milestone 0.3.0-M5, `VtlTemplateEngine.get(TemplateId)` exhibited measurable per-lookup overhead during warmed template retrieval, even when the requested template was already fully compiled, optimized, and cached in memory. Profiling during Milestone M4.2 revealed that retrieving a warmed template incurred ~1120–1255 ns/op and 1320–1504 B/op of GC allocation on Java 21, and ~1045–1155 ns/op and 1248–1416 B/op on Java 25.

Investigation traced this residual latency and garbage generation to three repeated invariant operations performed unconditionally on every cache lookup:

1. **Repeated Repository Source Polling and Fingerprinting**:
   Even on warm cache hits, the lookup path invoked `TemplateRepository.find(id)`. This caused resource stream resolution, byte reading into `SourceText`, and SHA-256 fingerprinting (~75% of lookup allocation, ~990 B/op), despite classpath resources and production templates never changing during application runtime.
2. **Repeated Global Macro Digest Traversal**:
   On every lookup, `GlobalMacroManager` traversed all registered global macros to compute an aggregate fingerprint (MD5/SHA digest concatenation, ~15% of lookup allocation, ~200 B/op) to ensure that the template compile cache key matched current macro definitions.
3. **Deep Cache Key Reconstruction**:
   `CompileCacheKey.of(...)` allocated composite records combining `TemplateId`, macro fingerprints, and engine options (~10% of lookup allocation, ~130 B/op) before probing `TemplateCompileCache`.

While these checks guaranteed freshness and correct invalidation, repeating them unconditionally on every request imposed unnecessary CPU and allocation tax on high-throughput serving paths.

---

## Governing Principle

Viet Template adopts the following design invariant for caching and runtime lifecycle optimization:

> **"Remove repeated invariant work before trying to make repeated work cheaper. Correctness of source freshness, invalidation, macro generation, security isolation, and ClassLoader lifecycle is more important than a cache-hit benchmark."**

Optimizations must never sacrifice live reload in development environments, dynamic macro invalidation semantics, path traversal security barriers, or dynamic AOT ClassLoader isolation.

---

## Decision

To eliminate invariant overhead while preserving strict correctness, Viet Template implements a six-part generation-aware warmed-lookup architecture:

### 1. FreshnessToken SPI and TemplateFreshnessProvider Capability

We introduce a first-class, lightweight SPI in `viet-template-api`:

- **`FreshnessToken`** (`io.github.minh124199.viettemplate.api.FreshnessToken`):
  An immutable, allocation-free value object representing the currency state of a template resource. It defines static factory methods:
  - `FreshnessToken.immutable()`: for static, immutable resources (e.g. classpath resources) whose state cannot change during the ClassLoader lifecycle.
  - `FreshnessToken.ofVersion(long version)`: for programmatically versioned repositories (e.g. in-memory stores) backed by monotonic counters.
  - `FreshnessToken.ofFile(long lastModifiedMillis, long sizeBytes)`: for filesystem resources, capturing file modification timestamp and size without reading file content.
- **`TemplateFreshnessProvider`** (`io.github.minh124199.viettemplate.api.TemplateFreshnessProvider`):
  An optional repository capability interface exposing `Optional<FreshnessToken> freshnessToken(TemplateId id)`.
- **`TemplateRepository` Non-Breaking Extension**:
  Added a default method `default Optional<FreshnessToken> freshnessToken(TemplateId id) { return Optional.empty(); }` to `TemplateRepository`, ensuring 100% binary and source compatibility for external custom repositories.
- **Core Repository Implementations**:
  - `ClasspathTemplateRepository`: Implements `TemplateFreshnessProvider`. Returns `FreshnessToken.immutable()` when the resource is present on the classpath, or `Optional.empty()` when absent.
  - `InMemoryTemplateRepository`: Implements `TemplateFreshnessProvider`. Maintains an `AtomicLong` monotonic revision counter incremented on every `put` or `remove`, returning `FreshnessToken.ofVersion(version)`.
  - `FilesystemTemplateRepository`: Implements `TemplateFreshnessProvider`. Safely validates against directory traversal (`normalize().startsWith(baseDir)`), checks existence via `Files.isRegularFile()`, and returns `FreshnessToken.ofFile(mtime, size)`. If the file is missing or unreadable, returns `Optional.empty()`.
  - `CompositeTemplateRepository`: Implements `TemplateFreshnessProvider`. Hierarchically evaluates delegate repositories in configured tier order. If an authoritative tier implements freshness tokens, its token is returned; if a delegate does not implement the capability, it gracefully falls back to empty, triggering the full source-based path.

### 2. Generation-Aware Fast Path in `VtlTemplateEngine.get(id)`

`VtlTemplateEngine.get(id)` evaluates an elision fast path before entering source loading or key compilation:

1. The engine queries `repository.freshnessToken(id)`.
2. If a token is present, the engine queries `TemplateCompileCache.getPreparedEntry(id)`.
3. If a cached `PreparedTemplateEntry` exists, the fast path validates two invariant guards:
   - `entry.freshnessToken().equals(currentToken)`: Confirms the underlying source has not changed.
   - `entry.macroGeneration() == globalMacroManager.macroGeneration()`: Confirms no global macros have been registered, unregistered, or modified since compilation.
4. When both guards match, the canonical, pre-wrapped `entry.template()` is returned immediately.
5. This completely bypasses `repository.find(id)`, `SourceText` instantiation, SHA-256 source hashing, macro registry traversal, and `CompileCacheKey` allocation.
6. When tokens differ or no token provider exists, the engine seamlessly falls back to the full compilation and validation coordinator path.

### 3. Global Macro Generation Counter in `GlobalMacroManager`

Instead of hashing all registered macros on every template lookup:
- `GlobalMacroManager` maintains an `AtomicLong macroGeneration` counter.
- Any mutation (`registerMacro`, `registerMacros`, `unregisterMacro`, `clear`) increments `macroGeneration`.
- `PreparedTemplateEntry` records the `macroGeneration` value at time of compilation. Fast-path lookup performs a single atomic long comparison (`==`) rather than string or digest operations.
- The composite macro digest string required by `CompileCacheKey` is cached with double-checked locking and recomputed only when `macroGeneration` changes during actual compilation.

### 4. `EngineFingerprint` Promotion and Composition in `CompileCacheKey`

Engine configuration (options, backend configurations, security policies) is invariant across the lifetime of a `VtlTemplateEngine`:
- `EngineFingerprint` (`io.github.minh124199.viettemplate.vtl.engine.EngineFingerprint`) is computed once during engine initialization.
- `CompileCacheKey` now composes `EngineFingerprint` by reference rather than duplicating configuration state or re-computing hashes on key construction.
- Avoids redundant object graphs and isolates cache invalidation concerns between engine configuration and template revisions.

### 5. Negative Cache Invalidation on Freshness Token Appearance

`TemplateCompileCache` maintains a negative lookup cache to avoid repeated expensive repository lookups for non-existent templates:
- When a client queries a template ID that is currently negative-cached, but `repository.freshnessToken(id)` returns a newly appeared token (e.g. a template created on disk during dev mode), `VtlTemplateEngine` immediately invalidates the negative cache entry via `compileCache.invalidateNegative(id)`.
- Eliminates stale negative cache windows and guarantees instant discovery of newly created template files without requiring engine restarts or TTL expiration.

### 6. Cache Schema and Serialization Versioning Invariants

To guarantee long-term integrity and prevent classpath contamination:
- All serializable cache structures enforce explicit `serialVersionUID = 1L`.
- `PreparedTemplateEntry` and `CompileCacheKey` are implemented as immutable records with defensive boundary copies.
- Thread safety is guaranteed via `ConcurrentHashMap` with atomic compute-if-absent semantics and zero lock contention during warm reads.

---

## Consequences

### Performance Benefits & Allocation Reductions

Empirical matched qualification on identical hardware (Intel i5-8350U, 12 GB RAM, Linux 7.2.6 x86_64, G1GC) demonstrated substantial throughput and allocation improvements:

#### Java 21 Improvements:
- **`warmedEngineGet` IR**: Latency reduced from **1255.46 ns/op to 1078.15 ns/op (-14.12%)**. GC allocation dropped from **1504 B/op to 1272 B/op (-232 B/op, -15.4%)**.
- **`engineRenderRequest` REALISTIC IR**: Latency reduced from **2346.28 ns/op to 2006.98 ns/op (-14.46%)**. GC allocation reduced from **3424 B/op to 3216 B/op (-208 B/op)**.
- **`retainedTemplateRender` IR**: Zero regression (**460.46 ns/op baseline vs 454.42 ns/op M5**, -1.31%, neutral within noise).

#### Java 25 Improvements:
- **`warmedEngineGet` IR**: Latency reduced from **1142.00 ns/op to 1029.87 ns/op (-9.82%)**. GC allocation dropped from **1400 B/op to 1200 B/op (-200 B/op, -14.3%)**.
- **`engineRenderRequest` REALISTIC IR**: Latency reduced from **2181.90 ns/op to 1921.31 ns/op (-11.94%)**. GC allocation reduced from **1416 B/op to 1248 B/op (-168 B/op)**.
- **`retainedTemplateRender` IR**: Latency improved from **470.69 ns/op baseline to 428.30 ns/op M5 (-8.99%)**.

### Preserved Invariants & Behavioral Guarantees

1. **Development-Mode Live Reload**: Modifying a `.vtl` file on disk alters `FreshnessToken.ofFile(mtime, size)`, causing immediate cache invalidation and transparent recompilation.
2. **Dynamic Macro Lifecycle**: Registering or unregistering a macro increments `macroGeneration`, instantly invalidating fast-path hits without stale output.
3. **Security Boundaries**: Path traversal validation (`..` checks, directory jail checks) remains enforced inside `FilesystemTemplateRepository.freshnessToken()` prior to checking file metadata.
4. **Backward Compatibility**: Existing third-party `TemplateRepository` implementations continue to function unmodified via default fallback methods.
5. **Virtual Thread Cleanliness**: Zero thread pinning or carrier starvation across all concurrency stress tests.

### Trade-offs & Limitations

- Repositories that do not implement `TemplateFreshnessProvider` continue to use the standard validation path (which still benefits from macro generation counter and engine fingerprint caching, but performs stream reads for source fingerprinting).
- Extremely rapid file modifications within the same millisecond having identical byte size could theoretically produce equal file tokens on filesystems with low timestamp resolution; production deployments using classpath or immutable stores are immune.

---

## Validation Matrix

The generation-aware lookup architecture was validated across the complete test and compliance matrix:

| Verification Scope | Gate / Test Suite | Result | Details |
| :--- | :--- | :---: | :--- |
| **Freshness Capability** | `TemplateFreshnessProviderTest` | **PASS** | Immutable, ofVersion, ofFile, traversal security, and composite fallback |
| **Generation Lookup** | `GenerationAwareCacheLookupTest` | **PASS** | Fast-path hit, file invalidation, macro invalidation, negative cache eviction |
| **Interpreter Tests** | `./gradlew :viet-template-vtl-interpreter:test` | **PASS** | 100% pass across runtime and coordinator tests |
| **Full Gradle Suite** | `./gradlew test --no-daemon` | **PASS** | 59 tasks, 0 failures across all modules |
| **Full Maven Suite** | `./mvnw test -B` | **PASS** | 15 reactor modules, 772+ tests, 0 failures |
| **Surface Classification** | `python3 scripts/verify-public-surface-classification.py` | **PASS** | 336 types checked, 0 unclassified, 0 leaks |
| **API Compatibility** | `python3 scripts/verify-api-compatibility.py` | **PASS** | 5 baselines checked, 0 breaking changes |
| **Generated ABI** | `python3 scripts/verify-generated-abi.py` | **PASS** | Constant pool verified, 0 unregistered dependencies |
| **Cross-Module Contracts** | `python3 scripts/verify-cross-module-contracts.py` | **PASS** | 62 contracts verified across production/test/benchmarks |
| **Framework Entrypoints** | `python3 scripts/verify-framework-entrypoints.py` | **PASS** | 12 entrypoints verified |
| **Documentation & Links** | `python3 scripts/verify-documentation.py` | **PASS** | Clean documentation, link resolution, and metadata |
| **Spring MVC Parity** | `./scripts/verify-spring-integration-parity.sh` | **PASS** | 9/9 steps passed, executable JAR tested |
| **Spring Security Parity** | `./scripts/verify-spring-security-parity.sh` | **PASS** | 10/10 steps passed, Sec 6 & 7 verified |
| **Quarkus Integration** | `./scripts/verify-quarkus-integration.sh` | **PASS** | JVM + Native Image runners verified |
| **Quarkus Dev Mode** | `./scripts/verify-quarkus-dev-mode.sh` | **PASS** | Dev-mode live reload and template replacement verified |

---

## Related Decisions

- [ADR-0002](0002-compile-first-three-execution-tiers.md): Compile-First and Three-Tier Execution Model.
- [ADR-0016](0016-generated-template-abi-compatibility.md): Generated Template Runtime ABI Compatibility.
- [ADR-0017](0017-public-surface-taxonomy-and-entrypoint-classification.md): Public Surface Taxonomy and Entrypoint Classification.

---

## Machine-Readable Resources

- `config/api-baseline/1.0-public-api.txt` — authoritative public API baseline.
- `config/api-baseline/1.0-core-public-api.txt` — core engine API/SPI baseline.
- `config/api-baseline/public-surface-classification.txt` — complete public surface classification.
- `scripts/verify-documentation.py` — documentation link and type parity verification gate.
