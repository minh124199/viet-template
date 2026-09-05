# 15 — Benchmark and Performance Engineering Plan

## 1. Principle

Benchmark representative rendering, not only hello-world. Track throughput **and allocation**.

## 2. Tooling

Primary JMH. Supplement with JFR, async-profiler, GC logs and separate end-to-end HTTP benchmarks.

## 3. Comparators

Current compatible versions of:

- Apache Velocity;
- Thymeleaf;
- Quarkus Qute;
- jte;
- Rocker where practical;
- Mustache.java/lightweight baseline;
- handwritten Java renderer.

## 4. Workloads

| ID | Workload |
|---|---|
| B01 | 20 KB static HTML |
| B02 | 50 scalar substitutions |
| B03 | repeated deep property chain |
| B04 | 100 mixed conditionals |
| B05 | 10-row loop × 5 properties |
| B06 | 1,000-row loop × 5 properties |
| B07 | nested 100 × 10 loop |
| B08 | escaping-heavy strings |
| B09 | dynamic monomorphic property |
| B10 | dynamic 4-type polymorphic property |
| B11 | 20 partial/template calls |
| B12 | macro-heavy render |
| B13 | 1–5 MiB report output |
| B14 | missing/denied property failure path |
| B15 | parse/analyze/compile 1,000 templates |

## 5. Viet Template modes

```text
Interpreter
Dynamic optimized cold
Dynamic optimized warm
Typed/AOT Writer
Typed/AOT UTF-8
Typed/AOT auto-escape
```

## 6. Metrics

```text
ops/s
time/op
sampling p50/p95/p99 where meaningful
bytes allocated/op
gc.alloc.rate
GC count/time
compile time/template
compiled class bytes/template
startup initialization time
```

## 7. Handwritten baseline

Every major workload has equivalent direct Java output implementation. Report Viet Template as a percentage of handwritten performance/allocations. This is the real optimization ceiling.

## 8. Hygiene

Same model data and escaping requirements, same output sink/charset where possible, JMH forks/warmup, consume output checksum/Blackhole, record CPU/OS/JDK/JVM args, preserve raw results and confidence intervals.

## 9. Allocation targets

Typed rendering should strive for zero engine-created temporary allocation for static literals, primitive access/output, direct property reads and internal-only loop metadata.

## 10. Regression gates

After baseline stabilizes, introduce statistical gates such as ~5% throughput/allocation regression and ~10% compile/class-size regression, with noise-aware thresholds.

## 11. Qute fairness

Qute already performs build-time type validation and generates optimized value resolvers. Compare:

```text
Qute dynamic/reflection vs Viet Template dynamic
Qute type-safe/generated-resolver vs Viet Template typed/AOT
```

with equivalent escaping/features. Never compare only Viet Template best mode to Qute worst mode.

## 12. HTTP benchmark

Separate renderer JMH from framework benchmark:

```text
Spring MVC + Viet Template
Spring MVC + Thymeleaf
Quarkus REST + Qute
```

Label this application-level because frameworks differ. Measure requests/s, p50/p95/p99, CPU, heap allocation, GC and RSS.

## 13. Publication

Every published result includes git SHA, templates/model code, dependency versions, JDK/JVM flags, hardware, raw JMH output, analysis scripts and caveats.
