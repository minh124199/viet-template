#!/usr/bin/env python3
"""
generate-benchmark-report.py — M18 JMH Benchmark Report Generator

Reads raw JMH JSON output files from benchmark-evidence/m18/ (or --input-dir)
and generates a Markdown report with comparative and internal throughput tables.

Usage:
    python3 scripts/perf/generate-benchmark-report.py
    python3 scripts/perf/generate-benchmark-report.py --input-dir benchmark-evidence/m18/ --output benchmark-evidence/m18/report.md
"""
import argparse
import json
import math
import sys
import tempfile
from collections import defaultdict
from pathlib import Path


def geometric_mean(values: list[float]) -> float:
    """Compute geometric mean of a list of positive values."""
    if not values:
        return 0.0
    log_sum = sum(math.log(v) for v in values if v > 0)
    return math.exp(log_sum / len(values))


def parse_jmh_json(path: Path) -> list[dict]:
    """Parse a JMH JSON results file and return list of benchmark result dicts."""
    with open(path) as f:
        payload = json.load(f)
    if not isinstance(payload, list):
        raise ValueError("JMH JSON root must be an array")
    return payload


def score_from_result(result: dict) -> float:
    """Extract the primary score from a JMH result dict."""
    return result.get("primaryMetric", {}).get("score", 0.0)


def engine_from_params(params: dict) -> str:
    """Extract engine name from JMH @Param map."""
    return params.get("engine", params.get("Engine", "unknown"))


def workload_from_benchmark(benchmark_name: str) -> str:
    """Extract workload ID from JMH benchmark method name."""
    # e.g. ".ComparativeEngineBenchmark.c01HelloWorld" -> "c01HelloWorld"
    return benchmark_name.split(".")[-1]


def classify_benchmark(benchmark_name: str) -> str:
    """Classify benchmark as COMPARATIVE or INTERNAL based on class name."""
    if "ComparativeEngineBenchmark" in benchmark_name:
        return "COMPARATIVE"
    return "INTERNAL"


def format_throughput(ops_per_sec: float) -> str:
    """Format ops/sec value for table display."""
    if ops_per_sec >= 1_000_000:
        return f"{ops_per_sec / 1_000_000:.2f}M"
    elif ops_per_sec >= 1_000:
        return f"{ops_per_sec / 1_000:.1f}K"
    return f"{ops_per_sec:.1f}"


def generate_report(input_dir: Path, output_path: Path, generated_at: str | None = None) -> bool:
    json_files = sorted(
        path for path in input_dir.glob("*.json")
        if path.name != "manifest.json" and not path.name.startswith("environment-")
    )

    if not json_files:
        print(
            f"No raw evidence files found in {input_dir}.\n"
            "Run benchmarks first (see docs/40-m18-tck-performance-release-gates.md)."
        )
        # Write a placeholder report so the output file always exists
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, "w") as f:
            f.write("# M18 Benchmark Report\n\n")
            f.write(
                "> **No evidence files found.** Run benchmarks first;\n"
                "> see `docs/40-m18-tck-performance-release-gates.md` for instructions.\n"
            )
        return False

    # Collect all benchmark results
    all_results: list[dict] = []
    for jf in json_files:
        try:
            all_results.extend(parse_jmh_json(jf))
        except Exception as e:
            print(f"Warning: could not parse {jf}: {e}", file=sys.stderr)

    if not all_results:
        print("Warning: JSON files found but no results parsed.", file=sys.stderr)
        return False

    # Separate comparative vs internal
    comparative = [r for r in all_results if classify_benchmark(r.get("benchmark", "")) == "COMPARATIVE"]
    internal = [r for r in all_results if classify_benchmark(r.get("benchmark", "")) == "INTERNAL"]

    lines: list[str] = []
    lines.append("# M18 Benchmark Report")
    lines.append("")
    if generated_at:
        lines.append(f"**Evidence generated:** {generated_at}")
    lines.append(f"**Evidence files:** {len(json_files)}")
    lines.append(f"**Total results:** {len(all_results)}")
    lines.append("")

    # --- Comparative section ---
    if comparative:
        lines.append("## Comparative Engine Benchmarks (C01–C08)")
        lines.append("")
        lines.append("| Workload | Viet-IR | Viet-AOT | Velocity 2.4.1 | Qute 3.39.4 | jte 3.2.4 | Thymeleaf 3.1.5 |")
        lines.append("|---|---|---|---|---|---|---|")

        # Group by workload
        by_workload: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
        for r in comparative:
            workload = workload_from_benchmark(r.get("benchmark", "unknown"))
            params = r.get("params", {})
            engine = engine_from_params(params)
            score = score_from_result(r)
            if score > 0:
                by_workload[workload][engine].append(score)

        engine_order = ["Viet-IR", "Viet-AOT", "Velocity", "Qute", "jte", "Thymeleaf"]
        for workload in sorted(by_workload.keys()):
            row = [workload]
            for engine in engine_order:
                scores = by_workload[workload].get(engine, [])
                if scores:
                    gm = geometric_mean(scores)
                    row.append(format_throughput(gm))
                else:
                    row.append("—")
            lines.append("| " + " | ".join(row) + " |")

        lines.append("")
        lines.append("> **Units:** ops/sec (throughput mode). Values are geometric means across measurement iterations.")
        lines.append("> **Track A (Dynamic/Interpreted):** Viet-IR, Velocity, Thymeleaf.")
        lines.append("> **Track B (Compiled/Bytecode):** Viet-AOT, Qute, jte.")
        lines.append("")

    # --- Internal section ---
    if internal:
        lines.append("## Internal Benchmark Results (B01–B15)")
        lines.append("")
        lines.append("| Benchmark | Suite | Score (ops/s) |")
        lines.append("|---|---|---|")

        by_bench: dict[str, list[float]] = defaultdict(list)
        bench_suite: dict[str, str] = {}
        for r in internal:
            bname = r.get("benchmark", "unknown")
            short = workload_from_benchmark(bname)
            suite = bname.split(".")[-2] if "." in bname else "unknown"
            score = score_from_result(r)
            if score > 0:
                by_bench[short].append(score)
                bench_suite[short] = suite

        for bench in sorted(by_bench.keys()):
            gm = geometric_mean(by_bench[bench])
            suite = bench_suite.get(bench, "")
            lines.append(f"| {bench} | {suite} | {format_throughput(gm)} |")

        lines.append("")

    lines.append("---")
    lines.append("_Report generated by `scripts/perf/generate-benchmark-report.py`._")
    lines.append(f"_Baseline SHA: `af8142c8e5b8155a79a7379a39a0dc009336815e`._")
    lines.append("")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        f.write("\n".join(lines) + "\n")

    print(f"Report written to {output_path}")
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate M18 benchmark report from JMH JSON files.")
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("benchmark-evidence/m18"),
        help="Directory containing JMH JSON output files (default: benchmark-evidence/m18/)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("benchmark-evidence/m18/report.md"),
        help="Output Markdown file path (default: benchmark-evidence/m18/report.md)",
    )
    parser.add_argument("--generated-at", help="Stable evidence timestamp to include in the report")
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Fail unless evidence exists and regenerating the report produces identical bytes",
    )
    args = parser.parse_args()
    if args.verify:
        if not args.output.is_file():
            print(f"Report does not exist: {args.output}", file=sys.stderr)
            raise SystemExit(1)
        with tempfile.TemporaryDirectory() as directory:
            regenerated = Path(directory) / "report.md"
            if not generate_report(args.input_dir, regenerated, args.generated_at):
                raise SystemExit(1)
            if regenerated.read_bytes() != args.output.read_bytes():
                print("Report is stale or non-deterministic; regenerate it from the raw evidence.", file=sys.stderr)
                raise SystemExit(1)
        print("Report reproducibility verified.")
    elif not generate_report(args.input_dir, args.output, args.generated_at):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
