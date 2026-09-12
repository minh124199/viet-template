#!/usr/bin/env python3
"""Process Startup Measurement CLI.

Executes N independent child JVM processes running StartupBenchmarkEntrypoint
and calculates statistical distributions (min, median, mean, p95, max, stddev)
for every startup checkpoint. Standard library only.
"""

import argparse
import json
import math
import shlex
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List


def find_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / ".git").exists():
            return current
        current = current.parent
    return Path.cwd()


def run_single_startup(
    java_bin: str,
    jvm_flags: List[str],
    classpath: str,
    tier: str,
) -> Dict[str, Any]:
    cmd = [
        java_bin,
        *jvm_flags,
        "-cp",
        classpath,
        "io.github.minh124199.viettemplate.benchmarks.startup.StartupBenchmarkEntrypoint",
        "--tier",
        tier,
    ]
    proc = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=True,
    )
    output = proc.stdout.strip()
    try:
        return json.loads(output)
    except json.JSONDecodeError as e:
        sys.stderr.write(f"Failed to parse child JVM output as JSON:\n{output}\nStderr:\n{proc.stderr}\n")
        raise e


def compute_statistics(values: List[float]) -> Dict[str, float]:
    n = len(values)
    if n == 0:
        return {"min": 0.0, "median": 0.0, "mean": 0.0, "p95": 0.0, "max": 0.0, "stddev": 0.0}

    s_vals = sorted(values)
    mean = sum(s_vals) / n
    min_v = s_vals[0]
    max_v = s_vals[-1]

    # Median
    if n % 2 == 1:
        median = s_vals[n // 2]
    else:
        median = (s_vals[(n // 2) - 1] + s_vals[n // 2]) / 2.0

    # 95th percentile
    p95_idx = min(max(0, int(math.ceil(0.95 * n)) - 1), n - 1)
    p95 = s_vals[p95_idx]

    # Standard deviation
    if n > 1:
        variance = sum((x - mean) ** 2 for x in s_vals) / (n - 1)
        stddev = math.sqrt(variance)
    else:
        stddev = 0.0

    return {
        "min": min_v,
        "median": median,
        "mean": mean,
        "p95": p95,
        "max": max_v,
        "stddev": stddev,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Measure process startup latency across independent JVM launches."
    )
    parser.add_argument("--java", default="java", help="Path to java executable")
    parser.add_argument(
        "--jvm-flags", default="", help="Space-separated JVM flags to pass to child JVMs"
    )
    parser.add_argument("--classpath", "-cp", default=None, help="Classpath")
    parser.add_argument(
        "--iterations", "-n", type=int, default=None, help="Number of independent child JVM launches"
    )
    parser.add_argument(
        "--mode", choices=["smoke", "measurement"], default="smoke",
        help="smoke uses 2-3 launches for plumbing only; measurement requires at least 20",
    )
    parser.add_argument(
        "--tier", default="IR", choices=["IR", "AOT_BYTECODE"], help="Execution tier (IR or AOT_BYTECODE)"
    )
    parser.add_argument(
        "--output-json", type=Path, default=None, help="Save summary and raw runs to JSON file"
    )
    parser.add_argument(
        "--markdown", "-m", action="store_true", help="Output summary in Markdown format"
    )

    args = parser.parse_args()

    repo_root = find_repo_root()
    classpath = args.classpath
    if not classpath:
        jar = repo_root / "viet-template-benchmarks" / "build" / "libs" / "benchmarks.jar"
        if not jar.is_file():
            jar = repo_root / "viet-template-benchmarks" / "target" / "benchmarks.jar"
        if not jar.is_file():
            sys.stderr.write(f"Error: benchmarks.jar not found at {jar}. Build it first.\n")
            sys.exit(1)
        classpath = str(jar)

    if args.iterations is None:
        args.iterations = 3 if args.mode == "smoke" else 30
    if args.iterations <= 0:
        parser.error("--iterations must be positive")
    if args.mode == "smoke" and not 2 <= args.iterations <= 3:
        parser.error("smoke mode requires 2 or 3 launches")
    if args.mode == "measurement" and args.iterations < 20:
        parser.error("measurement mode requires at least 20 independent launches")

    jvm_flags = shlex.split(args.jvm_flags) if args.jvm_flags.strip() else []

    print(f"Executing {args.iterations} independent JVM launches (mode: {args.mode}, tier: {args.tier})...")
    if args.mode == "smoke":
        print("SMOKE ONLY: validates harness plumbing; these observations must not support performance claims.")
    runs = []
    for i in range(args.iterations):
        sys.stdout.write(f"  Iteration {i+1}/{args.iterations}... ")
        sys.stdout.flush()
        data = run_single_startup(args.java, jvm_flags, classpath, args.tier)
        runs.append(data)
        total_ms = data.get("milliseconds", {}).get("totalStartup", 0.0)
        print(f"done ({total_ms:.1f} ms)")

    # Aggregate metric series in milliseconds
    metrics = [
        ("jvmUptimeAtEntry", "JVM Bootstrap to Entry"),
        ("repoReady", "Repository Population"),
        ("engineInit", "Engine Initialization"),
        ("compilation", "Template Compilation"),
        ("firstRender", "First Render"),
        ("allFirstRenders", "All First Renders"),
        ("smallBatch50", "Small Batch (50 renders)"),
        ("totalStartup", "Total Startup & Execution"),
    ]

    summary = {}
    for key, label in metrics:
        values = [r["milliseconds"][key] for r in runs if "milliseconds" in r and key in r["milliseconds"]]
        summary[key] = {
            "label": label,
            "stats": compute_statistics(values),
        }

    if args.markdown:
        print("\n### Process Startup Latency Summary (N = %d, Tier: %s)\n" % (args.iterations, args.tier))
        print("| Checkpoint | Min (ms) | Median (ms) | Mean (ms) | p95 (ms) | Max (ms) | StdDev (ms) |")
        print("|---|---|---|---|---|---|---|")
        for key, s in summary.items():
            st = s["stats"]
            print(f"| {s['label']} | {st['min']:.2f} | {st['median']:.2f} | {st['mean']:.2f} | {st['p95']:.2f} | {st['max']:.2f} | {st['stddev']:.2f} |")
    else:
        print("\n" + "=" * 92)
        print(f"{'CHECKPOINT':<28} | {'MIN':<7} | {'MEDIAN':<7} | {'MEAN':<7} | {'P95':<7} | {'MAX':<7} | {'STDDEV':<7}")
        print("-" * 92)
        for key, s in summary.items():
            st = s["stats"]
            print(f"{s['label']:<28} | {st['min']:<7.2f} | {st['median']:<7.2f} | {st['mean']:<7.2f} | {st['p95']:<7.2f} | {st['max']:<7.2f} | {st['stddev']:<7.2f}")
        print("=" * 92)

    if args.output_json:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        out_data = {
            "iterations": args.iterations,
            "mode": args.mode,
            "tier": args.tier,
            "java": {
                "bin": args.java,
                "flags": jvm_flags,
            },
            "summary": summary,
            "rawRuns": runs,
        }
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(out_data, f, indent=2)
        print(f"Detailed startup results saved to: {args.output_json}")


if __name__ == "__main__":
    main()
