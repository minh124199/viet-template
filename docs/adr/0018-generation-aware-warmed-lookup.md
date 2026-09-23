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

### 1. FreshnessToken SPI and Optional TemplateFreshnessProvider Capability

We introduce a first-class, lightweight SPI in `viet-template-api`:

- **`FreshnessToken`** (`io.github.minh124199.viettemplate.api.FreshnessToken`):
  An immutable, allocation-free value object representing the currency state of a template resource. It defines static factory methods:
  - `FreshnessToken.immutable()`: for static, immutable resources (e.g. classpath resources) whose state cannot change during the ClassLoader lifecycle.
  - `FreshnessToken.ofVersion(long version)`: for programmatically versioned repositories (e.g. in-memory stores) backed by monotonic counters.
  - `FreshnessToken.ofFile(long lastModifiedMillis, long sizeBytes)`: captures file metadata when explicitly configured by custom implementations.
- **`TemplateFreshnessProvider`** (`io.github.minh124199.viettemplate.api.TemplateFreshnessProvider`):
  An optional repository capability interface exposing `Optional<FreshnessToken> freshnessToken(TemplateId id)`.
- **API Surface Minimization (Decoupled `TemplateRepository`)**:
  `TemplateRepository` is preserved in its pure SPI form (`find(id)` and factory methods) without being polluted by caching or token concerns. Repositories that support freshness opt in by implementing `TemplateFreshnessProvider`. The runtime engine tests `repository instanceof TemplateFreshnessProvider provider`. Arbitrary third-party repositories remain completely decoupled and operate via the safe legacy source check.
- **Core Repository Implementations & Policies**:
  - `ClasspathTemplateRepository`: Implements `TemplateFreshnessProvider`. Returns `FreshnessToken.immutable()` when the resource is present on the classpath, or `Optional.empty()` when absent. Scoped to ClassLoader lifecycle with zero classloader retention.
  - `InMemoryTemplateRepository`: Implements `TemplateFreshnessProvider`. Maintains an `AtomicLong` monotonic revision counter incremented on every `put`, `remove`, or `clear`, returning `FreshnessToken.ofVersion(version)`.
  - `FilesystemTemplateRepository` (`FILESYSTEM_USES_SAFE_FALLBACK`): Under adversarial testing (same-size file rewrites within filesystem timestamp resolution and forced mtime preservation), metadata alone cannot guarantee source content equality. To guarantee 100% correctness and prevent serving stale code, filesystem repositories do not implement `TemplateFreshnessProvider` and safely fall back to `find(id)` with cryptographic SHA-256 fingerprinting. Proactive live reload is driven by `DevelopmentFileWatcher` (`hotReload(true)`).
  - `CompositeTemplateRepository`: Implements `TemplateFreshnessProvider`. Composes delegate tokens into `CompositeFreshnessToken(repositoryIndex, delegateToken)`. Incorporating `repositoryIndex` guarantees that shadowing changes (a higher-precedence repository gaining or losing a template) immediately invalidate previous cache tokens. If any delegate lacks `TemplateFreshnessProvider`, the composite safely returns `Optional.empty()`, triggering the source-based fallback.

### 2. Generation-Aware Fast Path in `VtlTemplateEngine.get(id)`

`VtlTemplateEngine.get(id)` evaluates an elision fast path before entering source loading or key compilation:

1. The engine queries `repository instanceof TemplateFreshnessProvider p ? p.freshnessToken(id) : Optional.empty()`.
2. If a token is present, the engine queries `TemplateCompileCache.getActiveEntry(id)`.
3. If a cached `PreparedTemplateEntry` exists, the fast path validates two invariant guards:
   - `active.freshnessToken() != null && currentToken.get().equals(active.freshnessToken())`: Confirms the underlying source has not changed.
   - `active.macroGeneration() == globalMacroManager.generation()`: Confirms no global macros have been registered, unregistered, or modified since compilation.
4. When both guards match, the canonical, pre-wrapped `active.templateInstance()` is returned immediately.
5. This completely bypasses `repository.find(id)`, `SourceText` instantiation, SHA-256 source hashing, macro registry traversal, and `CompileCacheKey` allocation.
6. When tokens differ or no token provider exists, the engine seamlessly falls back to the full compilation and validation coordinator path.

### 3. Global Macro Generation Counter in `GlobalMacroManager`

Instead of hashing all registered macros on every template lookup:
- `GlobalMacroManager` maintains an `AtomicLong macroGeneration` counter.
- Any mutation (`invalidate`, `invalidateAll`, macro reload) increments `macroGeneration`.
- `PreparedTemplateEntry` records the `macroGeneration` value at time of compilation. Fast-path lookup performs a single atomic long comparison (`==`) rather than string or digest operations.
- The composite macro digest string required by `CompileCacheKey` is cached and recomputed only when `macroGeneration` changes during actual compilation. Generation serves runtime fast-path checks; deterministic SHA-256 fingerprint serves persistent key identity.

### 4. `EngineFingerprint` Promotion and Composition in `CompileCacheKey`

Engine configuration (compiler version, optimization level, execution tier, security policy, model schema parameters, profile hashes) is immutable across the lifetime of a `VtlTemplateEngine`:
- `EngineFingerprint` (`io.github.minh124199.viettemplate.vtl.engine.EngineFingerprint`) is computed once during engine initialization. All 6 composed fields are classified `ENGINE_IMMUTABLE`.
- `CompileCacheKey` composes `EngineFingerprint` by reference rather than duplicating configuration state or re-computing hashes on key construction.
- Avoids redundant object graphs and isolates cache invalidation concerns between engine configuration and template revisions.

### 5. Negative Cache Invalidation on Freshness Token Appearance

`TemplateCompileCache` maintains a negative lookup cache to avoid repeated expensive repository lookups for non-existent templates:
- When a client queries a template ID that is currently negative-cached, but `freshnessProvider.freshnessToken(id)` returns a newly appeared token (e.g. a template added to an in-memory or dynamic store), `VtlTemplateEngine` immediately invalidates the negative cache entry via `cache.clearNegative(id)`.
- For repositories without freshness capability, the existing negative cache TTL (`negativeCacheTtlMillis`) expires entries safely.

### 6. In-Memory Cache Lifecycle and Persistence Architecture (`NO_PERSISTENT_CACHE_SCHEMA`)

To guarantee architectural integrity:
- **Verdict**: **`NO_PERSISTENT_CACHE_SCHEMA`**. The template compilation cache is strictly in-memory (`TemplateCompileCache`). Neither `CompileCacheKey` nor `EngineFingerprint` is ever serialized to disk, network, or persistent storage. Unused `Serializable` and `serialVersionUID` declarations have been eliminated.
- **Actual Persisted Formats**:
  1. *Generated Template ABI*: Enforced by ADR-0016 and verified by `scripts/verify-generated-abi.py`.
  2. *Template Registration Index (`META-INF/viet-template/templates.idx`)*: UTF-8 newline-delimited index mapping template paths to generated class names, verified byte-for-byte in build parity.
- Thread safety is guaranteed via `ConcurrentHashMap` with atomic stripe locking and zero lock contention during warm reads.

---

## Consequences

### Performance Attribution: Cumulative Program vs. Incremental M5

Matched qualification on identical hardware (Intel i5-8350U, 12 GB RAM, Linux 7.2.6 x86_64, G1GC) isolates the performance gains into two distinct views:

#### 1. Incremental M5 Improvement (M4.2 Candidate `2d31923` -> M5 Final `da35f77`)

| Benchmark | JDK | M4.2 Score | M5 Score | Error | Latency Delta | M4.2 Alloc | M5 Alloc | Alloc Delta | Classification |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `warmedEngineGet` IR | J21 | 1119.97 ns | 1078.15 ns | ±41.00 ns | **-3.73%** | 1320 B/op | 1272 B/op | **-48 B/op (-3.6%)** | `M5_INCREMENTAL` |
| `warmedEngineGet` AOT | J21 | 1167.84 ns | 1109.12 ns | ±100.89 ns | **-5.03%** | 1320 B/op | 1272 B/op | **-48 B/op (-3.6%)** | `M5_INCREMENTAL` |
| `warmedEngineGet` AST | J21 | 1154.31 ns | 1109.57 ns | ±67.36 ns | **-3.88%** | 1320 B/op | 1272 B/op | **-48 B/op (-3.6%)** | `M5_INCREMENTAL` |
| `engineRenderRequest` REALISTIC IR | J21 | 2085.06 ns | 2006.98 ns | ±39.12 ns | **-3.74%** | 3216 B/op | 3152 B/op | **-64 B/op (-2.0%)** | `M5_INCREMENTAL` |
| `engineGetThenRender` REALISTIC IR | J21 | 1705.38 ns | 1490.30 ns | ±72.83 ns | **-12.61%** | 2152 B/op | 2088 B/op | **-64 B/op (-3.0%)** | `M5_INCREMENTAL` |
| `retainedTemplateRender` IR | J21 | 477.38 ns | 456.78 ns | ±34.33 ns | **-4.32%** | 816 B/op | 816 B/op | **0 B/op (0.0%)** | `NEUTRAL` |
| `pureAotWarmedEngineGet` | J21 | 20.50 ns | 19.20 ns | ±1.37 ns | **-6.37%** | 0 B/op | 0 B/op | **0 B/op (0.0%)** | `NEUTRAL` |
| `warmedEngineGet` IR | J25 | 1030.75 ns | 1029.87 ns | ±48.89 ns | **-0.09%** | 1248 B/op | 1200 B/op | **-48 B/op (-3.8%)** | `M5_INCREMENTAL` |
| `warmedEngineGet` AOT | J25 | 1035.12 ns | 996.09 ns | ±45.93 ns | **-3.77%** | 1248 B/op | 1200 B/op | **-48 B/op (-3.8%)** | `M5_INCREMENTAL` |
| `warmedEngineGet` AST | J25 | 1068.76 ns | 977.62 ns | ±23.21 ns | **-8.53%** | 1248 B/op | 1200 B/op | **-48 B/op (-3.8%)** | `M5_INCREMENTAL` |
| `engineRenderRequest` REALISTIC IR | J25 | 2021.90 ns | 1921.31 ns | ±75.39 ns | **-4.97%** | 3128 B/op | 3080 B/op | **-48 B/op (-1.5%)** | `M5_INCREMENTAL` |
| `engineGetThenRender` REALISTIC IR | J25 | 1629.55 ns | 1594.78 ns | ±114.55 ns | **-2.13%** | 2064 B/op | 2016 B/op | **-48 B/op (-2.3%)** | `M5_INCREMENTAL` |
| `retainedTemplateRender` IR | J25 | 457.06 ns | 444.57 ns | ±27.52 ns | **-2.73%** | 816 B/op | 816 B/op | **0 B/op (0.0%)** | `NEUTRAL` |
| `pureAotWarmedEngineGet` | J25 | 17.96 ns | 17.61 ns | ±1.35 ns | **-1.90%** | 0 B/op | 0 B/op | **0 B/op (0.0%)** | `NEUTRAL` |

#### 2. Cumulative Program Improvement (M4.0 Baseline `869d4ab` -> M4.2 -> M5 Final `da35f77`)

| Benchmark | JDK | M4.0 Baseline | M5 Score | Program Latency Delta | M4.0 Alloc | M5 Alloc | Program Alloc Delta | Primary Attribution |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `warmedEngineGet` IR | J21 | 1255.46 ns | 1078.15 ns | **-14.12%** | 1504 B/op | 1272 B/op | **-232 B/op (-15.4%)** | M4.1/M4.2 (-184 B) + M5 (-48 B) |
| `warmedEngineGet` AOT | J21 | 1272.04 ns | 1109.12 ns | **-12.81%** | 1504 B/op | 1272 B/op | **-232 B/op (-15.4%)** | M4.1/M4.2 (-184 B) + M5 (-48 B) |
| `warmedEngineGet` AST | J21 | 1290.42 ns | 1109.57 ns | **-14.01%** | 1504 B/op | 1272 B/op | **-232 B/op (-15.4%)** | M4.1/M4.2 (-184 B) + M5 (-48 B) |
| `engineRenderRequest` REALISTIC IR | J21 | 2346.28 ns | 2006.98 ns | **-14.46%** | 3424 B/op | 3152 B/op | **-272 B/op (-7.9%)** | M4.1/M4.2 (-208 B) + M5 (-64 B) |
| `retainedTemplateRender` IR | J21 | 475.78 ns | 456.78 ns | **-3.99%** | 816 B/op | 816 B/op | **0 B/op (0.0%)** | Neutral across M4 and M5 |
| `pureAotWarmedEngineGet` | J21 | 16.53 ns | 19.20 ns | **+16.15% (noise)** | 0 B/op | 0 B/op | **0 B/op (0.0%)** | Neutral across M4 and M5 |

### Preserved Invariants & Behavioral Guarantees

1. **Development-Mode Live Reload**: Driven by `DevelopmentFileWatcher` (`hotReload(true)`). Proactive watch events invalidate cached templates and transitive dependents.
2. **Filesystem Correctness Under Adversaries**: `FilesystemTemplateRepository` adheres to `FILESYSTEM_USES_SAFE_FALLBACK`. Timestamp granularity issues or forced mtime preservations never cause stale template execution.
3. **Dynamic Macro Lifecycle**: Mutating macro state increments `macroGeneration`, immediately invalidating fast-path hits without stale output.
4. **Composite Shadowing Detection**: `CompositeFreshnessToken(repositoryIndex, delegateToken)` captures delegate tier index, invalidating entries whenever higher-precedence repositories gain or lose templates.
5. **Security Boundaries**: Path traversal validation (`..` checks, directory jail checks) remains strictly enforced across all repository operations.
6. **Backward Compatibility**: Third-party `TemplateRepository` implementations continue to function unmodified without implementing `TemplateFreshnessProvider`.
7. **Virtual Thread Cleanliness**: Zero thread pinning or carrier starvation across all concurrency stress tests.
8. **ClassLoader Lifecycle**: Classpath tokens hold no references to `ClassLoader` or `Class`, guaranteeing leak-free ClassLoader turnover.

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
