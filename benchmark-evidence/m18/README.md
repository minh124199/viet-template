# M18 Benchmark Evidence

This directory stores the durable example M18 `RELEASE_QUALIFICATION` package. Future release
candidates must generate or retrieve their own package; this snapshot is not permanent proof for
later source changes.

## File Naming Convention

```
<benchmark-suite>-<profile>-<date>.json
```

Examples:
- `comparative-J25-G1-2026-09-20.json` — ComparativeEngineBenchmark on JDK 25 / G1
- `full-J25-G1-2026-09-20.json` — Full benchmark suite on JDK 25 / G1

## Evidence Contract

A qualifying package contains `manifest.json`, J21-G1 and J25-G1 environment records, matched JMH
JSON with GC allocation metrics, generated `report.md`, and `SHA256SUMS`. The manifest records the
measured commit, tree, comparator coordinates, correctness result, and a digest of qualification
inputs. Raw JSON is retained either in git when its size is reasonable or as a permanent GitHub
Release asset. Expiring workflow artifacts are not the durable source of record.

## Generating a Report

After running the qualification runner:

```bash
JAVA21_HOME=/path/to/jdk-21 JAVA25_HOME=/path/to/jdk-25 \
  ./scripts/perf/run-m18-comparative-qualification.sh
```

Or with explicit paths:

```bash
python3 scripts/perf/generate-benchmark-report.py \
  --input-dir benchmark-evidence/m18/ \
  --output benchmark-evidence/m18/report.md
```

The runner refuses a dirty tree, runs the 8 × 6 correctness matrix before JMH, applies the matched
3-fork/5-warmup/10-measurement protocol on both required JDKs, records allocation metrics with the
GC profiler, and verifies the completed package.

Release validation normally requires an exact source SHA. The checked-in example may be followed
only by evidence/report documentation changes; in that case the qualification-input digest (which
includes production, harness, scripts, build, and workflow inputs while excluding evidence and docs)
must remain identical. Any executable-input change invalidates the package.

## Environment Reference

Canonical runtime profiles (`config/benchmark-runtime-profiles.json`):

| Profile ID | JDK | GC |
|---|---|---|
| J21-G1 | Java 21 | G1 |
| J21-ZGC | Java 21 | ZGC |
| J25-G1 | Java 25 | G1 |
| J25-G1-COH | Java 25 | G1 + COH |
| J25-ZGC | Java 25 | ZGC |

See `docs/40-m18-tck-performance-release-gates.md` for full M18 documentation.
