#!/usr/bin/env python3
"""
compare-benchmark-baseline.py — Compare a JMH result set against the 1.0.0 baseline.

Usage:
    python3 scripts/compare-benchmark-baseline.py \\
        --results path/to/jmh-results.json \\
        --profile J21-G1 \\
        [--baseline config/performance/1.0.0-baseline.json] \\
        [--threshold 10.0]

Exit codes:
    0  — All benchmarks within threshold of baseline
    1  — One or more regressions detected, or comparison failed
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Optional


DEFAULT_BASELINE = Path(__file__).parent.parent / "config" / "performance" / "1.0.0-baseline.json"
DEFAULT_THRESHOLD_PERCENT = 10.0


def load_json(path: Path) -> object:
    """Load and parse JSON from *path*, exit 1 on any error (fail-closed)."""
    if not path.exists():
        print(f"[ERROR] File not found: {path}", file=sys.stderr)
        sys.exit(1)
    try:
        with path.open(encoding="utf-8") as fh:
            return json.load(fh)
    except json.JSONDecodeError as exc:
        print(f"[ERROR] Malformed JSON in {path}: {exc}", file=sys.stderr)
        sys.exit(1)


def extract_viet_ir_results(jmh_results: list) -> dict[str, dict]:
    """
    Extract Viet-IR engine throughput results from a JMH JSON array.

    Returns a mapping of workload short-name → {score, scoreError, scoreUnit}.
    Exits 1 if *jmh_results* is not a list or contains no Viet-IR entries.
    """
    if not isinstance(jmh_results, list):
        print(
            "[ERROR] JMH results file must contain a JSON array at the top level.",
            file=sys.stderr,
        )
        sys.exit(1)

    extracted: dict[str, dict] = {}
    for entry in jmh_results:
        params = entry.get("params", {})
        engine = params.get("engine", "")
        if engine != "Viet-IR":
            continue
        benchmark_fqn: str = entry.get("benchmark", "")
        short_name = benchmark_fqn.split(".")[-1]
        extracted[short_name] = {
            "score": entry["primaryMetric"]["score"],
            "scoreError": entry["primaryMetric"].get("scoreError", 0.0),
            "scoreUnit": entry["primaryMetric"].get("scoreUnit", "ops/s"),
        }

    if not extracted:
        print(
            "[ERROR] No Viet-IR engine entries found in results file. "
            "Ensure the JMH run used the comparative benchmark and the engine "
            "parameter 'Viet-IR' was included.",
            file=sys.stderr,
        )
        sys.exit(1)

    return extracted


def compare(
    baseline_path: Path,
    results_path: Path,
    profile: str,
    global_threshold: float,
) -> bool:
    """
    Compare *results_path* against *baseline_path* for the given *profile*.

    Returns True if all benchmarks pass, False if any regression is detected.
    Exits 1 on structural / file errors (fail-closed).
    """
    baseline = load_json(baseline_path)
    jmh_results = load_json(results_path)

    # Validate baseline schema
    if not isinstance(baseline, dict):
        print("[ERROR] Baseline file must be a JSON object.", file=sys.stderr)
        sys.exit(1)
    profiles = baseline.get("profiles", {})
    if profile not in profiles:
        available = ", ".join(sorted(profiles.keys())) or "(none)"
        print(
            f"[ERROR] Profile '{profile}' not found in baseline. "
            f"Available: {available}",
            file=sys.stderr,
        )
        sys.exit(1)

    profile_data = profiles[profile]
    baseline_results: dict[str, dict] = profile_data.get("results", {})
    if not baseline_results:
        print(
            f"[ERROR] Profile '{profile}' has no results in baseline.",
            file=sys.stderr,
        )
        sys.exit(1)

    regression_policy = baseline.get("regressionPolicy", {})
    per_workload_overrides: dict[str, dict] = regression_policy.get(
        "perWorkloadOverrides", {}
    )

    # Extract new results
    new_results = extract_viet_ir_results(jmh_results)

    print(f"  Baseline : {baseline_path}")
    print(f"  Results  : {results_path}")
    print(f"  Profile  : {profile}")
    print(f"  Version  : {baseline.get('version', 'unknown')}")
    print(f"  Commit   : {baseline.get('commit', 'unknown')}")
    print()
    print(
        f"  {'Workload':<30} {'Baseline (ops/s)':>18} {'Current (ops/s)':>18} "
        f"{'Delta %':>10} {'Threshold %':>12} {'Status':>8}"
    )
    print("  " + "-" * 100)

    all_pass = True
    compared = 0

    for workload_name, baseline_entry in baseline_results.items():
        if workload_name not in new_results:
            print(
                f"  [WARN] Workload '{workload_name}' missing from new results — skipped.",
            )
            continue

        new_entry = new_results[workload_name]
        baseline_score: float = baseline_entry["score"]
        new_score: float = new_entry["score"]

        if baseline_score <= 0:
            print(
                f"  [WARN] Baseline score for '{workload_name}' is ≤ 0 — skipped.",
            )
            continue

        delta_pct = ((new_score - baseline_score) / baseline_score) * 100.0

        # Per-workload threshold override
        override = per_workload_overrides.get(workload_name, {})
        threshold = override.get("thresholdPercent", global_threshold)

        is_regression = delta_pct < -threshold
        status = "FAIL" if is_regression else "PASS"
        if is_regression:
            all_pass = False

        print(
            f"  {workload_name:<30} {baseline_score:>18,.0f} {new_score:>18,.0f} "
            f"{delta_pct:>+10.1f} {threshold:>12.1f} {status:>8}"
        )
        compared += 1

    print()
    print(f"  Compared {compared} workload(s).")
    return all_pass


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare JMH benchmark results against the 1.0.0 performance baseline.",
    )
    parser.add_argument(
        "--results",
        type=Path,
        required=True,
        help="Path to a JMH JSON results file (e.g. comparative-J21-G1.json).",
    )
    parser.add_argument(
        "--profile",
        choices=["J21-G1", "J25-G1"],
        required=True,
        help="Runtime profile to compare against (J21-G1 or J25-G1).",
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help=f"Path to baseline JSON (default: {DEFAULT_BASELINE}).",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=DEFAULT_THRESHOLD_PERCENT,
        help=f"Global regression threshold in percent (default: {DEFAULT_THRESHOLD_PERCENT}).",
    )
    args = parser.parse_args()

    print("=" * 80)
    print("Viet Template Performance Baseline Comparison")
    print("=" * 80)

    passed = compare(
        baseline_path=args.baseline,
        results_path=args.results,
        profile=args.profile,
        global_threshold=args.threshold,
    )

    if passed:
        print("[PASS] All benchmarks within regression threshold.")
        sys.exit(0)
    else:
        print("[FAIL] One or more benchmarks regressed beyond threshold.")
        sys.exit(1)


if __name__ == "__main__":
    main()
