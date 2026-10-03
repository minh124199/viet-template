# Comparative Benchmarks

## Current evidence status

The current comparative report contains a fresh **J21-G1** measurement from commit
`e01046ade5abc9a108e91db2a92dbb3cc2ff00be`. It is a complete C01–C08, six-engine run, but it is
**not a complete release qualification** because this host did not have the OpenJDK 25 runtime
required by the canonical J25-G1 profile. The only installed Java 25 runtime was Oracle GraalVM
25.0.4+7.1; it was not accepted as OpenJDK evidence. No current J25 comparative values are claimed.

The measured commit is the post-release `1.1.1-SNAPSHOT` main build. Its production Java source
files are unchanged from the `v1.1.0` tag, but its Maven/Gradle version metadata is newer. The
published 1.0.0 regression baseline remains immutable at
[`config/performance/1.0.0-baseline.json`](../../config/performance/1.0.0-baseline.json).
Fresh partial-run provenance and checksums are in
[`benchmark-evidence/1.1.0-j21-partial/`](../../benchmark-evidence/1.1.0-j21-partial/).

## Method and environment

The repository's canonical comparative suite and correctness fixtures were used. Before timing,
`CrossEngineFixtureCorrectnessTest` passed for all eight workloads across all six engines. The
fixtures compare equivalent models and rendered semantics; templates are prepared outside the
timed operation and rendered output is consumed. C01–C07 are raw output; C08 uses HTML escaping.

| Setting | J21-G1 measurement |
|---|---|
| Benchmark commit | `e01046ade5abc9a108e91db2a92dbb3cc2ff00be` |
| Measurement time | 2026-10-03 04:04:57 UTC |
| Runtime | OpenJDK 21.0.12.1, OpenJDK 64-Bit Server VM, G1 |
| JMH | 1.37; throughput; 3 forks; 5 x 1-second warmups; 10 x 1-second measurements; 1 thread |
| JVM flags | `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC` |
| Host | CachyOS Linux x86_64, kernel `7.2.8-2-cachyos`; Intel Core i5-8350U, 4 physical / 8 logical cores; 11.4 GiB RAM |
| Engines | Viet-IR, Viet-AOT, Apache Velocity 2.4.1, Quarkus Qute 3.39.4, jte 3.2.4, Thymeleaf 3.1.5.RELEASE |

The results below give JMH throughput with its reported 99.9% error and matched `gc.alloc.rate.norm`
allocation. Throughput is workload-specific; differences between engines do not establish a general
ranking outside these fixtures and this environment.

## J21-G1 comparative results

| Workload | Viet-IR | Viet-AOT | Velocity 2.4.1 | Qute 3.39.4 | jte 3.2.4 | Thymeleaf 3.1.5.RELEASE |
|---|---|---|---|---|---|---|
| C01 static HTML | 4,721,163 ± 77,998 ops/s; 1,512 B/op | 12,288,174 ± 146,871 ops/s; 816 B/op | 5,633,139 ± 133,941 ops/s; 1,128 B/op | 10,408,407 ± 165,715 ops/s; 576 B/op | 7,316,579 ± 125,786 ops/s; 872 B/op | 1,133,390 ± 21,808 ops/s; 2,400 B/op |
| C02 scalar variables | 1,221,961 ± 30,388 ops/s; 1,880 B/op | 2,276,744 ± 63,654 ops/s; 1,184 B/op | 867,298 ± 23,032 ops/s; 1,344 B/op | 1,684,576 ± 33,344 ops/s; 1,376 B/op | 4,356,953 ± 93,941 ops/s; 832 B/op | 250,332 ± 5,033 ops/s; 6,104 B/op |
| C03 deep property chains | 192,722 ± 11,788 ops/s; 7,187 B/op | 1,586,897 ± 30,091 ops/s; 1,088 B/op | 288,063 ± 6,428 ops/s; 5,552 B/op | 888,470 ± 14,525 ops/s; 2,648 B/op | 4,395,828 ± 109,894 ops/s; 864 B/op | 62,413 ± 1,771 ops/s; 11,392 B/op |
| C04 conditionals | 1,371,820 ± 21,111 ops/s; 1,816 B/op | 3,193,318 ± 71,361 ops/s; 1,120 B/op | 1,114,887 ± 22,304 ops/s; 1,256 B/op | 2,366,100 ± 49,212 ops/s; 1,184 B/op | 5,466,283 ± 89,099 ops/s; 824 B/op | 260,425 ± 5,722 ops/s; 4,280 B/op |
| C05 small table (5 rows) | 113,899 ± 5,031 ops/s; 10,512 B/op | 444,089 ± 9,274 ops/s; 2,419 B/op | 225,508 ± 4,295 ops/s; 3,352 B/op | 362,120 ± 10,162 ops/s; 6,320 B/op | 1,227,032 ± 51,108 ops/s; 1,608 B/op | 34,430 ± 764 ops/s; 23,104 B/op |
| C06 large table (100 rows) | 6,650 ± 150 ops/s; 185,122 B/op | 22,807 ± 546 ops/s; 43,777 B/op | 13,822 ± 242 ops/s; 37,073 B/op | 21,335 ± 301 ops/s; 99,440 B/op | 74,650 ± 1,817 ops/s; 30,718 B/op | 1,902 ± 39 ops/s; 394,893 B/op |
| C07 nested foreach | 107,327 ± 2,396 ops/s; 9,880 B/op | 695,095 ± 26,633 ops/s; 1,288 B/op | 217,678 ± 1,593 ops/s; 2,856 B/op | 419,342 ± 10,936 ops/s; 8,325 B/op | 2,751,333 ± 45,273 ops/s; 968 B/op | 45,655 ± 690 ops/s; 18,800 B/op |
| C08 HTML escaping | 252,939 ± 11,118 ops/s; 6,835 B/op | 734,322 ± 14,123 ops/s; 2,384 B/op | 584,305 ± 11,008 ops/s; 2,821 B/op | 708,984 ± 16,777 ops/s; 2,824 B/op | 1,430,209 ± 43,963 ops/s; 1,523 B/op | 186,686 ± 3,616 ops/s; 6,496 B/op |

Full precision is retained in the raw JMH JSON. The derived table in the evidence directory rounds
throughput to whole operations per second and allocation to 0.1 B/op.

## J21-G1 versus the immutable 1.0.0 Viet-IR baseline

`scripts/compare-benchmark-baseline.py` compared all eight Viet-IR workloads against the unchanged
1.0.0 baseline. The policy permits a 10% regression generally and 15% on C03 deep property chains.
All measured deltas pass those limits:

| Workload | 1.0.0 ops/s | Current ops/s | Delta | Threshold | Result |
|---|---:|---:|---:|---:|---|
| C01 static HTML | 4,835,233 | 4,721,163 | -2.4% | 10% | PASS |
| C02 scalar variables | 1,177,241 | 1,221,961 | +3.8% | 10% | PASS |
| C03 deep property chains | 194,230 | 192,722 | -0.8% | 15% | PASS |
| C04 conditionals | 1,334,333 | 1,371,820 | +2.8% | 10% | PASS |
| C05 small table foreach | 114,466 | 113,899 | -0.5% | 10% | PASS |
| C06 large table foreach | 6,282 | 6,650 | +5.9% | 10% | PASS |
| C07 nested foreach | 102,524 | 107,327 | +4.7% | 10% | PASS |
| C08 HTML escaping | 257,200 | 252,939 | -1.7% | 10% | PASS |

This is a single J21-G1 profile comparison. It does not replace the missing J25-G1 comparison or
constitute a complete release qualification.

## Interpretation and evidence classes

- On this J21-G1 run, Viet-AOT exceeded the measured Velocity result on C01, C02, C03, C05, C06,
  C07, and C08; Velocity exceeded Viet-AOT on C04. jte led the measured engines on C02, C03, C04,
  C05, C06, C07, and C08. The table shows the exact workload-level scores and errors.
- Viet-IR is the engine measured by the regression-baseline comparison. That baseline is an
  intra-project regression reference, not a cross-engine ranking.
- Internal specialization, linker, output, escaping, and cache microbenchmarks are engineering
  evidence only. They do not support general cross-engine claims; see the
  [M25 specialization report](1.1-m25-specialization-benchmarks.md).
- Historical 1.0 cross-engine measurements remain historical and are not presented here as 1.1
  measurements.

## Reproduction

The canonical command is `scripts/perf/run-m18-comparative-qualification.sh`. It requires clean
worktree inputs and OpenJDK 21 and 25 installations. The script now rejects a non-OpenJDK runtime
for either OpenJDK profile. The current complete J21-G1 raw data, environment record, 1.0 baseline
comparison output, and checksums are retained in
[`benchmark-evidence/1.1.0-j21-partial/`](../../benchmark-evidence/1.1.0-j21-partial/). The J25
profile remains to be run on OpenJDK 25 before claiming a complete 1.1 qualification.
