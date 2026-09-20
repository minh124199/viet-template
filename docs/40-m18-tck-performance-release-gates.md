# M18 — TCK & Performance Release Gates

## Status

**COMPLETE & FROZEN.** Qualified on branch `feature/m18-tck-performance-release-gates`.

The feature matrix, TCK, backend parity, comparative harness, clean-room gate, mandatory release
workflow dependency, and durable SHA-bound J21/J25 evidence contract are implemented and validated.
The historical M19.3c performance baseline is
`af8142c8e5b8155a79a7379a39a0dc009336815e`; the formal M18 measurement source is recorded in
`benchmark-evidence/m18/manifest.json`.

## Objective

Make all Viet Template language-compatibility and performance claims:

- **Independently executable** — any external developer can clone and run
- **Machine-verifiable** — release gates exit 0 / non-zero; no human judgment required
- **Reproducible** — canonical environment metadata, raw JMH JSON evidence, pinned competitor versions
- **Backend-neutral where applicable** — IR/AOT_BYTECODE parity verified for all eligible features

M18 adds **no runtime optimizations** and does **not modify frozen M19.3c internals**.
If benchmarks reveal gaps, the result is recorded as evidence only.

---

## Deliverable A — Language Conformance TCK

### A1. Feature Claim Matrix

| Property | Value |
|---|---|
| File | [`config/tck/vtl-feature-matrix.json`](../config/tck/vtl-feature-matrix.json) |
| Features | 80 claimed across 20 categories |
| Categories | `LEX`, `REF`, `PROP`, `IDX`, `METH`, `EXPR`, `TRUTH`, `SET`, `IF`, `FOREACH`, `MACRO`, `DYN`, `STATE3`, `STRICT`, `SEC`, `ERR`, `UNICODE`, `APP`, `DIFF`, `EXT` |
| Coverage gate | `python3 scripts/verify-tck-coverage.py` → exits 0, 100% (80/80) |
| Schema | `{ "version", "generatedAt", "features": [{ "id", "category", "name", "status", "requiredBackends", "profile", "rationale", "docRef" }] }` |

Feature IDs use the format `{CATEGORY}-{NNN}` (e.g. `LEX-001`, `FOREACH-006`). IDs are durable and must not be renumbered.

### A2. Conformance Test Suite

| Property | Value |
|---|---|
| Module | `viet-template-tck` |
| Scenarios | 80 executable scenarios, each mapped 1:1 to a matrix feature ID |
| Backends | IR and/or AOT_BYTECODE per feature declaration |
| Registry | [`TckSuiteRegistry.java`](../viet-template-tck/src/main/java/io/github/minh124199/viettemplate/tck/conformance/suite/TckSuiteRegistry.java) |
| Test class | [`TckConformanceTest.java`](../viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/conformance/TckConformanceTest.java) |
| Test expansion | Derived by JUnit from scenarios, profiles, backends, parity, architecture, and CLI tests; no fixed invocation count is a release contract |
| API boundary | **Zero internal imports.** Enforced by [`ArchitectureRulesTest.java`](../viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/architecture/ArchitectureRulesTest.java) |

Public APIs used: `VtlTemplateEngine`, `ExecutionTier`, `RenderContext`, `InMemoryTemplateRepository`, `StringTemplateOutput`.

**CLI runner** — the TCK JAR is independently executable:

```bash
java -jar viet-template-tck/target/viet-template-tck-*-cli.jar \
  --backend ALL --profile VTL_CORE --json-output /tmp/tck-summary.json
```

### A3. Backend Parity

| Property | Value |
|---|---|
| Test class | [`BackendParityTest.java`](../viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/conformance/BackendParityTest.java) |
| Dual-backend features | 75 (all produce bit-identical IR/AOT output) |
| IR-only features | 5: `LEX-003`, `LEX-004`, `FOREACH-006`, `SEC-003`, `EXT-001` |
| IR-only rationale | These features exercise IR-specific mechanics (interpreter syntax error recovery, source span tracking, dynamic template inclusion at runtime, classloader-scoped class generation). AOT parity is intentionally not required and classified in the matrix. |

### A4. Independent Consumer Fixture (M18.2)

External developers can consume the TCK independently — no knowledge of the reactor required.

| Consumer | Location | Tests |
|---|---|---|
| Maven standalone | [`integration-tests/tck-consumer/maven/`](../integration-tests/tck-consumer/maven/) | 3 |
| Gradle standalone | [`integration-tests/tck-consumer/gradle/`](../integration-tests/tck-consumer/gradle/) | 3 |

Run:

```bash
./scripts/verify-tck-consumer.sh
```

### A5. Velocity Differential Classification

| Classification | Count |
|---|---|
| `SUPPORTED_EXACT` | 295 |
| `SUPPORTED_WITH_DECLARED_DIFFERENCE` | 5 |
| `EXTENSION` | 1 |
| **Total** | **301** |

See [`docs/06-velocity-2.4.1-compatibility.md`](06-velocity-2.4.1-compatibility.md) for the complete differential specification.

---

## Deliverable B — Performance Release Gates

### B1. Comparative JMH Benchmarks (C01–C08)

| Property | Value |
|---|---|
| Suite | `ComparativeEngineBenchmark` |
| Benchmark class | [`ComparativeEngineBenchmark.java`](../viet-template-benchmarks/src/main/java/io/github/minh124199/viettemplate/benchmarks/comparative/ComparativeEngineBenchmark.java) |
| Mode | Throughput (ops/s) |
| Characterization protocol | 1 fork / 3 warmup / 5 measurement × 1 s |
| Release qualification protocol | 3 forks / 5 warmup / 10 measurement × 1 s, matched `gc.alloc.rate.norm` |
| Correctness gate | [`CrossEngineFixtureCorrectnessTest.java`](../viet-template-benchmarks/src/test/java/io/github/minh124199/viettemplate/benchmarks/comparative/CrossEngineFixtureCorrectnessTest.java) — 48 tests, 0 failures |

**Track A — Dynamic / Interpreted:**

| Engine | Version | Adapter |
|---|---|---|
| Viet Template (IR) | 0.2.2-SNAPSHOT | `VietIrAdapter` |
| Apache Velocity | 2.4.1 | `VelocityAdapter` |
| Thymeleaf | 3.1.5.RELEASE | `ThymeleafAdapter` |

**Track B — Compiled / Bytecode:**

| Engine | Version | Adapter |
|---|---|---|
| Viet Template (AOT) | 0.2.2-SNAPSHOT | `VietAotAdapter` |
| Quarkus Qute | 3.39.4 | `QuteAdapter` |
| jte | 3.2.4 | `JteAdapter` |

**Workloads:**

| ID | Name | Description |
|---|---|---|
| C01 | Hello World | 5-variable scalar rendering |
| C02 | Scalar Substitution | 20 mixed-type variables |
| C03 | Deep Property Chains | order.customer.address.city.name chain |
| C04 | Conditionals & Branching | Complex boolean/truthiness/multi-branch |
| C05 | Small Table Foreach | 5 items × 3 cols |
| C06 | Large Table Foreach | 100 items × 3 cols |
| C07 | Nested Foreach | Semantically shared hierarchical nested iteration (no engine-specific metadata) |
| C08 | HTML Contextual Escaping | High-density `&`, `<`, `>`, `"`, `'` |

### B2. Internal Benchmarks (B01–B15)

See [`config/benchmark-manifest.json`](../config/benchmark-manifest.json) for the full registry.

Canonical qualification suites (3 forks / 5 warmup / 10 measurement):
- `EngineSteadyStateBenchmark`
- `ForeachObservabilityBenchmark`

End-to-end characterization (2 forks / 3 warmup / 5 measurement):
- `RenderingEndToEndBenchmark` (B01–B12)

### B3. Raw Evidence Directory

```
benchmark-evidence/m18/
  manifest.json                 ← SHA/tree/protocol/competitor contract
  environment-J21-G1.json      ← measured host and JVM metadata
  environment-J25-G1.json
  comparative-J21-G1.json      ← tracked raw JMH + matched allocation evidence
  comparative-J25-G1.json
  report.md                     ← deterministic generated report
  SHA256SUMS                    ← verified package checksums
```

**Report generator:**

```bash
python3 scripts/perf/generate-benchmark-report.py
# Output: benchmark-evidence/m18/report.md
```

---

## Release Gate Commands

### Master gate (all deterministic checks):

```bash
./scripts/verify-m18-release-gates.sh
```

### Individual gates:

```bash
# Gate 1: 100% TCK coverage
python3 scripts/verify-tck-coverage.py

# Gate 2: Public surface classification
python3 scripts/verify-public-surface-classification.py

# Gate 3: API compatibility (0 breaking changes)
python3 scripts/verify-api-compatibility.py

# Gate 4: TCK conformance suite
./mvnw test -pl viet-template-tck -am

# Gate 5: Cross-engine fixture correctness (8 workloads × 6 engines)
./mvnw test -pl viet-template-benchmarks -am -Dtest=CrossEngineFixtureCorrectnessTest

# Gate 6: Independent consumer fixture
./scripts/verify-tck-consumer.sh
```

---

## Running Benchmarks

### Comparative benchmarks (C01–C08):

```bash
JAVA21_HOME=/path/to/jdk21 JAVA25_HOME=/path/to/jdk25 \
  ./scripts/perf/run-m18-comparative-qualification.sh
```

### Full internal suite (B01–B15):

```bash
java -jar viet-template-benchmarks/target/benchmarks.jar \
  -rf json -rff benchmark-evidence/m18/full-J25-G1-$(date +%Y-%m-%d).json
python3 scripts/perf/generate-benchmark-report.py
```

---

## Environment

| Property | Value |
|---|---|
| Java baseline | Java 21 (`--release 21`) |
| Primary runtime | GraalVM JDK 25 / OpenJDK 25 |
| Canonical baseline SHA | `af8142c8e5b8155a79a7379a39a0dc009336815e` |
| Benchmark profiles | `config/benchmark-runtime-profiles.json` |

---

## Constraints

- M18 adds **no runtime optimizations**.
- Frozen M19.3c internals (`TemplateCompileCache`, output buffer/escaping, `LoopPlan`, `SmallLocalScope`, tagged/raw `ExecutionFrame`, dense function IDs, `RandomAccess` loop specialization, virtual-thread scheduling, pooling, `ThreadLocal` request caches) are **not modified**.
- If benchmarks reveal performance gaps, results are recorded as evidence only.
- Competitor dependencies are scoped to `viet-template-benchmarks` only — never published to production artifacts.

## Formal qualification evidence

The durable package was measured from commit
`6838a3227d8010d74dbd94aea93411208e43438b` (tree
`88af25ac2c6060e06be3caaa55b61d6acc91cbb1`) on one x86_64 Intel i5-8350U host. Both J21-G1
(OpenJDK 21.0.12.1) and J25-G1 (GraalVM 25.0.4) used `-Xms2g -Xmx2g`, `AlwaysPreTouch`, and the
matched 3/5/10 protocol. The raw data contains all C01–C08 pairings for six engines, including JMH
error and normalized allocation. The generated report deliberately separates the dynamic and compiled
tracks and makes no overall-winner claim. Synthetic steady-state results do not characterize startup,
compilation, every application model, or every host.

The release workflow's `m18-release-qualification` job runs after verified builds and before the
release bundle. `package` requires that job, `central-publication-guard` requires `package`, and live
sign/upload therefore cannot bypass M18. Dry runs exercise the same dependency. Future release
candidates must supply evidence whose source SHA matches the release commit, or whose strict
qualification-input digest proves that only documentation/evidence packaging changed.
