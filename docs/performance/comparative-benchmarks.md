# Comparative Benchmarks

## Current evidence status

The comparative report establishes the complete two-profile release qualification for **Viet Template 1.1.0** (commit `621a98fc419bf3af37cc55aac5379a68ab02986e`, tag `v1.1.0`). Evaluation was performed across both canonical runtime profiles — **genuine OpenJDK 21** (`21.0.12.1+1`) and **genuine OpenJDK 25** (`25.0.4.1`) on Arch Linux — using benchmark tooling commit `9154489474e7285bd3667d2e7142adc638031f9e`.

All eight workloads (C01–C08) were qualified across all six template engines. The published 1.0.0 regression baseline remains immutable at [`config/performance/1.0.0-baseline.json`](../../config/performance/1.0.0-baseline.json). Durable raw JMH evidence, environment descriptors, runtime identity records, and checksums are archived in [`benchmark-evidence/1.1.0/`](../../benchmark-evidence/1.1.0/). The earlier single-profile artifact [`benchmark-evidence/1.1.0-j21-partial/`](../../benchmark-evidence/1.1.0-j21-partial/) is preserved as the initial post-release qualification record.

## Method and environment

The repository's canonical comparative suite and correctness fixtures were used. Before timing,
`CrossEngineFixtureCorrectnessTest` passed for all eight workloads across all six engines. The
fixtures compare equivalent models and rendered semantics; templates are prepared outside the
timed operation and rendered output is consumed. C01–C07 are raw output; C08 uses HTML escaping.

| Setting | J21-G1 specification | J25-G1 specification |
|---|---|---|
| Measured commit | `621a98fc419bf3af37cc55aac5379a68ab02986e` (tag `v1.1.0`) | `621a98fc419bf3af37cc55aac5379a68ab02986e` (tag `v1.1.0`) |
| Tooling commit | `9154489474e7285bd3667d2e7142adc638031f9e` | `9154489474e7285bd3667d2e7142adc638031f9e` |
| Qualification timestamp | 2026-10-03 08:24:23 UTC | 2026-10-03 08:24:23 UTC |
| Java runtime | OpenJDK 21.0.12.1+1 (Arch Linux build) | OpenJDK 25.0.4.1 (Arch Linux build) |
| JVM / JIT | OpenJDK 64-Bit Server VM, HotSpot C2 | OpenJDK 64-Bit Server VM, HotSpot C2 |
| Garbage collector | G1 GC | G1 GC |
| JMH configuration | 1.37; throughput mode; 3 forks; 5 x 1s warmup; 10 x 1s measurement; 1 thread | 1.37; throughput mode; 3 forks; 5 x 1s warmup; 10 x 1s measurement; 1 thread |
| JVM arguments | `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC` | `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC` |
| Host environment | Linux x86_64, kernel `7.2.8-2-cachyos`; Intel Core i5-8350U @ 1.70GHz (4 physical / 8 logical cores); 11.4 GiB RAM | Linux x86_64, kernel `7.2.8-2-cachyos`; Intel Core i5-8350U @ 1.70GHz (4 physical / 8 logical cores); 11.4 GiB RAM |
| Evaluated engines | Viet-IR, Viet-AOT, Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, Thymeleaf 3.1.5.RELEASE | Viet-IR, Viet-AOT, Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, Thymeleaf 3.1.5.RELEASE |

The results below give JMH throughput with its reported 99.9% error and matched `gc.alloc.rate.norm`
allocation. Throughput is workload-specific; differences between engines do not establish a general
ranking outside these fixtures and this environment.

## J21-G1 comparative results

| Workload | Viet-IR | Viet-AOT | Velocity 2.4.1 | Qute 3.39.4 | jte 3.2.4 | Thymeleaf 3.1.5.RELEASE |
|---|---|---|---|---|---|---|
| C01 static HTML | 4,566,289 ± 132,868 ops/s; 1,512.0 B/op | 11,560,167 ± 44,976 ops/s; 816.0 B/op | 5,602,205 ± 89,392 ops/s; 1,128.0 B/op | 10,884,119 ± 166,320 ops/s; 576.0 B/op | 7,417,632 ± 162,431 ops/s; 872.0 B/op | 1,120,304 ± 29,356 ops/s; 2,400.0 B/op |
| C02 scalar variables | 1,215,859 ± 14,989 ops/s; 1,880.0 B/op | 2,527,359 ± 87,601 ops/s; 1,184.0 B/op | 870,093 ± 17,663 ops/s; 1,344.0 B/op | 1,699,603 ± 40,393 ops/s; 1,376.0 B/op | 4,486,490 ± 84,843 ops/s; 832.0 B/op | 258,471 ± 3,394 ops/s; 6,104.0 B/op |
| C03 deep property chains | 220,685 ± 4,984 ops/s; 6,888.1 B/op | 1,613,164 ± 29,218 ops/s; 1,088.0 B/op | 288,152 ± 6,509 ops/s; 5,552.0 B/op | 908,944 ± 14,245 ops/s; 2,648.0 B/op | 4,523,519 ± 73,986 ops/s; 864.0 B/op | 61,936 ± 842 ops/s; 11,392.1 B/op |
| C04 conditionals | 1,405,541 ± 29,297 ops/s; 1,816.0 B/op | 3,711,737 ± 371,431 ops/s; 1,080.0 B/op | 1,136,062 ± 20,153 ops/s; 1,256.0 B/op | 2,338,619 ± 27,477 ops/s; 1,194.7 B/op | 5,661,748 ± 71,745 ops/s; 824.0 B/op | 269,173 ± 5,535 ops/s; 4,280.0 B/op |
| C05 small table (5 rows) | 119,777 ± 2,625 ops/s; 10,352.1 B/op | 443,357 ± 8,947 ops/s; 2,440.0 B/op | 222,709 ± 3,775 ops/s; 3,400.0 B/op | 374,247 ± 6,852 ops/s; 6,352.0 B/op | 1,227,970 ± 23,841 ops/s; 1,608.0 B/op | 34,301 ± 495 ops/s; 23,104.2 B/op |
| C06 large table (100 rows) | 6,697 ± 173 ops/s; 185,121.7 B/op | 23,521 ± 398 ops/s; 43,776.5 B/op | 14,147 ± 190 ops/s; 37,072.5 B/op | 21,446 ± 234 ops/s; 99,448.4 B/op | 75,513 ± 1,666 ops/s; 30,696.2 B/op | 1,963 ± 43 ops/s; 394,900.9 B/op |
| C07 nested foreach | 107,716 ± 2,437 ops/s; 9,880.1 B/op | 710,398 ± 7,785 ops/s; 1,288.0 B/op | 222,255 ± 5,032 ops/s; 2,856.0 B/op | 423,886 ± 8,778 ops/s; 8,336.0 B/op | 2,823,454 ± 57,091 ops/s; 968.0 B/op | 46,618 ± 808 ops/s; 18,800.1 B/op |
| C08 HTML escaping | 257,350 ± 7,319 ops/s; 6,817.7 B/op | 759,903 ± 10,724 ops/s; 2,384.0 B/op | 580,591 ± 7,914 ops/s; 2,848.0 B/op | 733,755 ± 12,014 ops/s; 2,749.3 B/op | 1,530,608 ± 32,713 ops/s; 1,512.0 B/op | 186,197 ± 8,655 ops/s; 6,560.0 B/op |

Full precision is retained in the raw JMH JSON. The derived table in the evidence directory rounds
throughput to whole operations per second and allocation to 0.1 B/op.

## J25-G1 comparative results

| Workload | Viet-IR | Viet-AOT | Velocity 2.4.1 | Qute 3.39.4 | jte 3.2.4 | Thymeleaf 3.1.5.RELEASE |
|---|---|---|---|---|---|---|
| C01 static HTML | 8,323,468 ± 86,928 ops/s; 1,008.0 B/op | 11,336,730 ± 31,646 ops/s; 816.0 B/op | 5,871,265 ± 181,526 ops/s; 1,120.0 B/op | 12,134,163 ± 214,688 ops/s; 576.0 B/op | 7,383,865 ± 111,227 ops/s; 872.0 B/op | 1,216,521 ± 23,558 ops/s; 2,400.0 B/op |
| C02 scalar variables | 1,278,306 ± 21,720 ops/s; 1,880.0 B/op | 2,706,028 ± 32,869 ops/s; 1,152.0 B/op | 991,081 ± 17,251 ops/s; 1,344.0 B/op | 1,739,113 ± 113,039 ops/s; 1,322.7 B/op | 4,239,397 ± 74,101 ops/s; 832.0 B/op | 287,802 ± 4,866 ops/s; 6,104.0 B/op |
| C03 deep property chains | 244,895 ± 5,820 ops/s; 6,216.1 B/op | 1,682,773 ± 16,214 ops/s; 1,088.0 B/op | 356,658 ± 6,027 ops/s; 5,216.0 B/op | 1,005,672 ± 18,880 ops/s; 2,152.0 B/op | 4,238,833 ± 71,624 ops/s; 864.0 B/op | 73,982 ± 1,592 ops/s; 11,392.1 B/op |
| C04 conditionals | 1,440,485 ± 24,697 ops/s; 1,816.0 B/op | 5,298,508 ± 93,323 ops/s; 920.0 B/op | 1,325,208 ± 23,451 ops/s; 1,256.0 B/op | 2,564,748 ± 55,169 ops/s; 1,176.0 B/op | 5,487,708 ± 101,416 ops/s; 824.0 B/op | 291,259 ± 5,071 ops/s; 4,280.0 B/op |
| C05 small table (5 rows) | 131,982 ± 2,351 ops/s; 9,152.1 B/op | 481,171 ± 11,170 ops/s; 1,944.0 B/op | 247,777 ± 4,169 ops/s; 3,208.0 B/op | 387,497 ± 7,563 ops/s; 6,288.0 B/op | 1,382,967 ± 33,952 ops/s; 1,128.0 B/op | 38,494 ± 1,052 ops/s; 22,984.2 B/op |
| C06 large table (100 rows) | 6,965 ± 104 ops/s; 161,922.0 B/op | 25,359 ± 476 ops/s; 34,960.5 B/op | 15,367 ± 253 ops/s; 34,672.5 B/op | 22,600 ± 392 ops/s; 97,040.3 B/op | 80,334 ± 1,096 ops/s; 21,896.3 B/op | 2,150 ± 35 ops/s; 392,475.3 B/op |
| C07 nested foreach | 119,390 ± 1,913 ops/s; 9,304.1 B/op | 750,506 ± 14,027 ops/s; 1,352.0 B/op | 228,852 ± 18,547 ops/s; 2,856.0 B/op | 381,179 ± 16,936 ops/s; 8,392.0 B/op | 1,967,798 ± 100,142 ops/s; 968.0 B/op | 41,506 ± 2,436 ops/s; 18,800.2 B/op |
| C08 HTML escaping | 259,869 ± 5,611 ops/s; 6,793.0 B/op | 666,833 ± 7,244 ops/s; 2,512.0 B/op | 481,442 ± 58,878 ops/s; 2,848.0 B/op | 560,764 ± 53,051 ops/s; 2,685.3 B/op | 959,477 ± 74,634 ops/s; 1,592.0 B/op | 140,161 ± 12,990 ops/s; 6,336.1 B/op |

Full precision is retained in the raw JMH JSON. The derived table in the evidence directory rounds
throughput to whole operations per second and allocation to 0.1 B/op.

## Baseline comparison against immutable 1.0.0 Viet-IR

The published 1.0.0 regression baseline in `config/performance/1.0.0-baseline.json` establishes regression prevention limits: 10% maximum regression generally, and 15% on C03 deep property chains.

### J21-G1 baseline comparison

`scripts/compare-benchmark-baseline.py` compared all eight Viet-IR workloads on OpenJDK 21 against the 1.0.0 baseline. All eight workloads passed within policy threshold:

| Workload | 1.0.0 ops/s | Current ops/s | Delta % | Threshold % | Status |
|---|---:|---:|---:|---:|---|
| C01 static HTML | 4,835,233 | 4,566,289 | -5.6% | 10.0% | PASS |
| C02 scalar variables | 1,177,241 | 1,215,859 | +3.3% | 10.0% | PASS |
| C03 deep property chains | 194,230 | 220,685 | +13.6% | 15.0% | PASS |
| C04 conditionals | 1,334,333 | 1,405,541 | +5.3% | 10.0% | PASS |
| C05 small table foreach | 114,466 | 119,777 | +4.6% | 10.0% | PASS |
| C06 large table foreach | 6,282 | 6,697 | +6.6% | 10.0% | PASS |
| C07 nested foreach | 102,524 | 107,716 | +5.1% | 10.0% | PASS |
| C08 HTML escaping | 257,200 | 257,350 | +0.1% | 10.0% | PASS |

### J25-G1 baseline comparison and runtime analysis

Comparison of the genuine OpenJDK 25 run against `config/performance/1.0.0-baseline.json` yields the following deltas:

| Workload | Baseline ops/s | Current ops/s | Delta % | Threshold % | Status |
|---|---:|---:|---:|---:|---|
| C01 static HTML | 10,763,964 | 8,323,468 | -22.7% | 10.0% | FAIL |
| C02 scalar variables | 1,852,108 | 1,278,306 | -31.0% | 10.0% | FAIL |
| C03 deep property chains | 192,684 | 244,895 | +27.1% | 15.0% | PASS |
| C04 conditionals | 1,499,496 | 1,440,485 | -3.9% | 10.0% | PASS |
| C05 small table foreach | 111,521 | 131,982 | +18.3% | 10.0% | PASS |
| C06 large table foreach | 6,511 | 6,965 | +7.0% | 10.0% | PASS |
| C07 nested foreach | 99,946 | 119,390 | +19.5% | 10.0% | PASS |
| C08 HTML escaping | 292,997 | 259,869 | -11.3% | 10.0% | FAIL |

Five workloads passed within policy (C03 +27.1%, C04 -3.9%, C05 +18.3%, C06 +7.0%, C07 +19.5%), while three workloads (C01 -22.7%, C02 -31.0%, C08 -11.3%) failed against the historical baseline file.

#### Root cause analysis

The root cause of the J25-G1 baseline failure is an environment mismatch in the historical baseline recording:
1. **Historical recording environment:** The 1.0.0 baseline recorded in `config/performance/1.0.0-baseline.json` for J25-G1 was captured on Oracle GraalVM 25.0.4+7.1 with the JVMCI compiler enabled (`UseJVMCICompiler=true`), not genuine OpenJDK HotSpot (C2).
2. **Current qualification environment:** The 1.1.0 release qualification strictly requires genuine OpenJDK 25 HotSpot (`25.0.4.1`) utilizing the standard C2 compiler (`UseJVMCICompiler=false`). The historical and current measurements were produced by materially different JIT and runtime configurations, so the raw delta is not a valid same-runtime regression comparison.
3. **Controlled same-runtime empirical re-evaluation:** When Viet Template 1.0.0 is measured on the identical genuine OpenJDK 25 HotSpot C2 runtime under the canonical JMH protocol (3 forks, 5 warmup, 10 measurement iterations, identical JVM flags), 1.1.0 passes within regression thresholds across all eight workloads:

| Workload | v1.0.0 ops/s | v1.1.0 ops/s | Delta % | Threshold % | Status |
|---|---:|---:|---:|---:|---|
| c01_staticHtml | 5,237,983 ± 79,883 | 8,323,468 ± 86,928 | +58.9% | 10.0% | PASS |
| c02_scalarVariables | 1,299,877 ± 19,818 | 1,278,306 ± 21,720 | -1.7% | 10.0% | PASS |
| c03_deepPropertyChains | 211,057 ± 17,577 | 244,895 ± 5,820 | +16.0% | 15.0% | PASS |
| c04_conditionals | 1,508,151 ± 27,293 | 1,440,485 ± 24,697 | -4.5% | 10.0% | PASS |
| c05_smallTableForeach | 114,832 ± 2,229 | 131,982 ± 2,351 | +14.9% | 10.0% | PASS |
| c06_largeTableForeach | 7,039 ± 90 | 6,965 ± 104 | -1.0% | 10.0% | PASS |
| c07_nestedForeach | 113,960 ± 4,880 | 119,390 ± 1,913 | +4.8% | 10.0% | PASS |
| c08_htmlEscaping | 278,885 ± 17,128 | 259,869 ± 5,611 | -6.8% | 10.0% | PASS |

The historical baseline FAIL results remain visible and immutable in `config/performance/1.0.0-baseline.json`. The controlled same-runtime comparison in [`benchmark-evidence/1.0.0-openjdk25-rerun/`](../../benchmark-evidence/1.0.0-openjdk25-rerun/) proves that zero code regressions occurred when run on the same runtime.

## Cross-engine interpretation

- **Viet-AOT vs Apache Velocity:** Across all eight workloads (C01–C08) on both OpenJDK 21 and OpenJDK 25, Viet-AOT exceeded Apache Velocity throughput.
- **Static HTML throughput (C01):** On OpenJDK 21, Viet-AOT led all tested engines on static HTML rendering (11.56M ops/s vs Qute 10.88M ops/s, jte 7.42M ops/s). On OpenJDK 25, Quarkus Qute led static HTML rendering (12.13M ops/s vs Viet-AOT 11.34M ops/s, jte 7.38M ops/s).
- **Deep property chains (C03) and large collection iterations (C05–C07):** jte achieved the highest throughput on deep property chains (C03) and large collection iterations (C05–C07) across both JDKs. This performance profile stems from jte's un-sandboxed direct Java bytecode generation, which compiles template expressions directly into raw Java bytecode without runtime sandboxing or dynamic dispatch indirection.
- **Viet-IR role:** Viet-IR operates as an interpreted execution engine with full runtime sandboxing (`MemberAccessPolicy`). Its primary performance benchmark is intra-project regression tracking against the 1.0.0 baseline rather than raw throughput ranking against compiled bytecode engines.
- **Microbenchmarks:** Internal specialization, linker, output, escaping, and cache microbenchmarks represent engineering evidence characterizing isolated subcomponents. They do not substantiate cross-engine performance claims; refer to the [M25 specialization report](1.1-m25-specialization-benchmarks.md).
- **Historical data:** Historical 1.0 cross-engine measurements remain archived in [`benchmark-evidence/m18/`](../../benchmark-evidence/m18/) and are not substituted for 1.1 release measurements.

## Reproduction

The canonical qualification harness is executed via `scripts/perf/run-m18-comparative-qualification.sh`. The harness requires clean worktree inputs and valid installations of genuine OpenJDK 21 and OpenJDK 25. The runner strictly validates the runtime identity of both JDKs and rejects non-OpenJDK binaries.

Full raw JMH JSON results, environment captures, runtime identity records, baseline comparison outputs, and SHA-256 manifests are preserved in [`benchmark-evidence/1.1.0/`](../../benchmark-evidence/1.1.0/). The initial single-profile qualification artifact remains preserved in [`benchmark-evidence/1.1.0-j21-partial/`](../../benchmark-evidence/1.1.0-j21-partial/).
