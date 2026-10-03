# Controlled Same-Runtime v1.0.0 OpenJDK 25 Benchmark Evidence

## Purpose

This evidence package contains the empirical same-runtime re-evaluation of **Viet Template v1.0.0** (`b951021e9975b8e8103b2402dc244b32a96afaa8`) on **genuine OpenJDK 25 HotSpot C2** (`25.0.4.1`).

### Context & Methodology
- The historical 1.0.0 regression baseline in `config/performance/1.0.0-baseline.json` was captured using Oracle GraalVM 25 with the JVMCI compiler enabled (`UseJVMCICompiler=true`).
- The canonical 1.1.0 release qualification profile `J25-G1` in `benchmark-evidence/1.1.0/` was executed on genuine OpenJDK 25 HotSpot C2 (`UseJVMCICompiler=false`).
- Because cross-runtime comparison between GraalVM/JVMCI and HotSpot/C2 causes apparent regressions on C01, C02, and C08 due to differing JIT compiler architectures, this controlled re-evaluation measures exact `v1.0.0` product code on the **identical genuine OpenJDK 25 HotSpot runtime** with the **identical JMH protocol** (3 forks, 5 warmup, 10 measurement iterations, `-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC`, `-prof gc`).

### Invariants
1. **Historical Baseline Immutability**: This run does **NOT** alter or replace `config/performance/1.0.0-baseline.json`. The historical baseline remains immutable.
2. **Controlled Comparison**: This dataset provides durable proof that under an apples-to-apples genuine OpenJDK 25 HotSpot execution, Viet Template 1.1.0 passes configured same-runtime regression thresholds across all eight workloads (all 8/8 PASS within regression threshold; no policy-significant performance regressions detected across C01-C08; workloads C02 (-1.7%), C04 (-4.5%), C06 (-1.0%), and C08 (-6.8%) measured small negative throughput deltas, but all remained within the configured 10%/15% regression gates).

## Results Summary (Viet-IR on genuine OpenJDK 25 HotSpot C2)

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

## Provenance
- **Product Commit**: `b951021e9975b8e8103b2402dc244b32a96afaa8` (tag `v1.0.0`)
- **Tooling Commit**: `5725f6b529c69faa4031d8af66941d99cc538074`
- **Java Runtime**: OpenJDK 25.0.4.1 (build 25.0.4.1, mixed mode, sharing), HotSpot C2, Arch Linux
- **Host**: Linux x86_64, kernel 7.2.8-2-cachyos, Intel(R) Core(TM) i5-8350U CPU @ 1.70GHz (4 physical / 8 logical cores)
