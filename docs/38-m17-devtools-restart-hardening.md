# Milestone M17 — Spring Boot DevTools Restart & ClassLoader Lifecycle Hardening (Phase B)

## 1. Executive Summary

- **Milestone**: M17 — Spring Boot DevTools Restart & ClassLoader Lifecycle Hardening (Phase B)
- **Status**: **COMPLETE**
- **Decision**: **`A. M17 DEVTOOLS RESTART HARDENING COMPLETE — READY FOR M18 TCK & BENCHMARKS`**
- **Technical Baseline**:
  - Bytecode target: Java 17 (`--release 17`, classfile major version 61)
  - Runtime support: Java 17 and Java 21+
  - Spring Boot 3 Baseline: Spring Boot 3.3.5 / Spring Framework 6.1.14 / Spring Security 6.3.4
  - Spring Boot 4 Baseline: Spring Boot 4.0.0 / Spring Framework 7.0 / Spring Security 7.0
  - DevTools: `org.springframework.boot:spring-boot-devtools`
  - Strict Public API bijection: 99 stable types preserved across all 4 baselines (80 core + 6 AOT + 8 Spring + 5 Security)
  - Zero DevTools compile/runtime dependency leaks in production modules (`viet-template-*`)

---

## 2. Architecture & Dual Reload Modes

Viet Template provides dual-mode developer experience when paired with Spring Boot DevTools:

```text
                                +---------------------------+
                                |  Developer Edits Template |
                                +---------------------------+
                                              |
                     +------------------------+------------------------+
                     |                                                 |
                     v                                                 v
           [Mode A: Dynamic Mode]                           [Mode B: Precompiled AOT]
       Uncompiled / Interpreted VTL                       Precompiled Bytecode Class
                     |                                                 |
        File modified on disk                             AOT recompile (process-classes / classes)
                     |                                                 |
        TemplateCompileCache invalidation                 Update trigger file (.restart-trigger)
                     |                                                 |
        Instant render on next request                    DevTools detects changed class + trigger
                     |                                                 |
        RestartClassLoader: UNCHANGED                     RestartClassLoader: TURNOVER (New Gen)
        TemplateEngine: PRESERVED                         Old Context: CLOSED, Engine CLOSED
        Downtime: 0ms                                     New Context: CREATED, Engine RE-INITIALIZED
```

### 2.1 Mode A: Dynamic Hot Reload Without Restart
- **Use Case**: Rapid front-end template iteration, rapid UX design changes.
- **Mechanism**:
  - `VtlTemplateEngine` utilizes `TemplateCompileCache` with fingerprint-based cache keys (`source.fingerprint()`).
  - When `viet-template.cache=false` (development setting), view instances are resolved per request and updated templates are re-parsed/re-compiled immediately.
  - ClassLoader identity (`RestartClassLoader`) and `TemplateEngine` identity remain constant.
  - Zero JVM restart overhead, sub-millisecond turnarounds.

### 2.2 Mode B: Precompiled AOT Bytecode + Restart Trigger
- **Use Case**: Production parity testing during development, verification of compiled template bytecode and Spring AOT hints.
- **Mechanism**:
  - Templates are compiled ahead-of-time into `.class` files by the Maven (`viet-template-maven-plugin`) or Gradle (`viet-template-gradle-plugin`) plugin.
  - A trigger file (`spring.devtools.restart.trigger-file=.restart-trigger`) gates restarts.
  - When classes are recompiled and `.restart-trigger` is touched, Spring Boot DevTools creates a new `RestartClassLoader`, closes the previous `ApplicationContext`, and initializes a fresh application state.
  - Previous `RestartClassLoader` instances and closed `TemplateEngine` instances are cleanly released and garbage collected.

---

## 3. ClassLoader Lifecycle & Leak Prevention

A critical challenge with dynamic classloading frameworks under Spring Boot DevTools is memory leakage caused by static references, Thread Context ClassLoaders (TCCL), or reflection caches retaining instances of `RestartClassLoader`.

### 3.1 Hardened Engine Lifecycle (`destroyMethod = "close"`)
- `TemplateEngine` implements `AutoCloseable`.
- `VietTemplateAutoConfiguration` registers `@Bean(destroyMethod = "close")`.
- Upon context shutdown / DevTools restart:
  1. `VtlTemplateEngine.close()` shuts down `DevelopmentFileWatcher` and its executor thread pool.
  2. Clears `TemplateCompileCache` (positive and negative caches).
  3. Clears dynamic bytecode class caches and method dispatch tables.
  4. Subsequent calls to `close()` are idempotent.

### 3.2 Dynamic CallSite & Reflection Cache Turnover
- `BoundedWeakClassCache` and `DynamicCallSite` hold class references using weak/soft mechanisms.
- Under ClassLoader turnover, old class references do not prevent ClassLoader garbage collection.
- Spring Framework reflection caches (`ConcurrentReferenceHashMap`) using `SoftReference` are purged under memory pressure or via DevTools `forceReferenceCleanup()`.

### 3.3 Thread Context ClassLoader (TCCL) Containment
- Lingering worker threads (e.g. Tomcat worker threads or JVM threads) whose TCCL points to an inactive `RestartClassLoader` are reset to `ClassLoader.getPlatformClassLoader()`.
- Verified 100% GC reclamation across 10x consecutive restart cycles in automated stress tests (`leakFree = true`).

---

## 4. Integration Test Fixture Matrix

Four independent dual-build consumer fixtures validate DevTools integration:

| Fixture | Build Tool | Spring Boot | Spring Framework | Spring Security | Port |
|---|---|---|---|---|---|
| `integration-tests/devtools/maven-boot3-devtools` | Maven (3.3.5 starter parent) | 3.3.5 | 6.1.14 | 6.3.4 | 19081 |
| `integration-tests/devtools/gradle-boot3-devtools` | Gradle (Kotlin DSL) | 3.3.5 | 6.1.14 | 6.3.4 | 19082 |
| `integration-tests/devtools/maven-boot4-devtools` | Maven (4.0.0 starter parent) | 4.0.0 | 7.0.0 | 7.0.0 | 19083 |
| `integration-tests/devtools/gradle-boot4-devtools` | Gradle (Kotlin DSL) | 4.0.0 | 7.0.0 | 7.0.0 | 19084 |

Each fixture verifies:
1. **Full MockMvc test suite**: 7 slice tests per fixture verifying template rendering, security boundaries, and DevTools endpoints.
2. **DevTools live execution**: Applications boot with `RestartClassLoader` active on runtime classpath.
3. **Spring Security & CSRF isolation**: Anonymous, user, and admin roles correctly enforced with CSRF tokens resolved from `$csrf`.
4. **Mode A dynamic hot reload**: In-place template modifications without ClassLoader turnover.
5. **Mode B AOT recompile + restart**: ClassLoader turnover and `TemplateEngine` bean recreation upon AOT recompile.
6. **Stale template deletion**: Recompiled index automatically purges deleted templates, returning HTTP 500/404.
7. **Restart stress & leak check**: Consecutive restarts verify all intermediate `RestartClassLoader` generations are garbage collected (`leakFree = true`).

---

## 5. Verification Tools & CI Automation

### 5.1 Verification Script (`scripts/verify-devtools-restart-integration.sh`)
Executes an end-to-end audit across all 4 fixtures:
```bash
./scripts/verify-devtools-restart-integration.sh
```
Covers:
- Fixture test suites (`mvn test` / `./gradlew test`)
- Server daemon lifecycle management with trap cleanup
- HTTP endpoint validation (public, admin, dashboard, dynamic-page, stale)
- JSON metadata inspection (`/__test/restart-generation` and `/__test/classloader-leak-check`)
- Multi-generation ClassLoader garbage collection proof

### 5.2 GitHub Actions Workflow (`.github/workflows/devtools-restart.yml`)
- Executes on pull requests and pushes to `main`.
- Matrix test across JDK 17 and JDK 21.
- Uploads failure logs and HTML artifacts on error.

---

## 6. Dependency & Surface Containment Audit

- **Zero DevTools Dependency Leak**:
  - `spring-boot-devtools` is strictly scoped to `optional` (Maven) or `developmentOnly` (Gradle) in consumer fixtures.
  - Production modules (`viet-template-*`) contain 0 compile or runtime references to DevTools classes.
  - Verified by `DevToolsContainmentTest` across all 8 production modules.
- **Strict Public API Preservation**:
  - 99 public types across 4 API baselines verified with 0 breaking changes via `verify-api-compatibility.py`.
