# M18 Benchmark Evidence

This directory stores raw JMH JSON output from qualifying M18 benchmark runs.

## File Naming Convention

```
<benchmark-suite>-<profile>-<date>.json
```

Examples:
- `comparative-J25-G1-2026-09-20.json` — ComparativeEngineBenchmark on JDK 25 / G1
- `full-J25-G1-2026-09-20.json` — Full benchmark suite on JDK 25 / G1

## What Goes Here

- Raw JMH JSON result files (`-rf json -rff <file>`)
- These files are **NOT committed to git** (`.gitignore` excludes `*.json`)
- Only `.gitkeep` and `README.md` are version-controlled

## Generating a Report

After running benchmarks:

```bash
python3 scripts/perf/generate-benchmark-report.py
# Output: benchmark-evidence/m18/report.md
```

Or with explicit paths:

```bash
python3 scripts/perf/generate-benchmark-report.py \
  --input-dir benchmark-evidence/m18/ \
  --output benchmark-evidence/m18/report.md
```

## Running the Comparative Benchmarks

```bash
./mvnw package -pl viet-template-benchmarks -DskipTests
java -jar viet-template-benchmarks/target/benchmarks.jar ComparativeEngineBenchmark \
  -rf json -rff benchmark-evidence/m18/comparative-J25-G1-$(date +%Y-%m-%d).json
```

## Running the Full Internal Suite

```bash
java -jar viet-template-benchmarks/target/benchmarks.jar \
  -rf json -rff benchmark-evidence/m18/full-J25-G1-$(date +%Y-%m-%d).json
```

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
