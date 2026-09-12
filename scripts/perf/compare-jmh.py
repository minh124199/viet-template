#!/usr/bin/env python3
"""Conservatively compare compatible entries from two JMH JSON files."""

import argparse
import json
import math
import sys
from pathlib import Path

CONFIG_FIELDS = ("mode", "threads", "forks", "warmupIterations", "warmupTime", "warmupBatchSize", "measurementIterations", "measurementTime", "measurementBatchSize")
HIGHER_IS_BETTER = {"thrpt", "Throughput"}
LOWER_IS_BETTER = {"avgt", "AverageTime", "sample", "SampleTime", "ss", "SingleShotTime"}


def load_jmh_json(path):
    with path.open(encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, list) or not all(isinstance(entry, dict) for entry in data):
        raise ValueError(f"{path}: expected an array of JMH result objects")
    return data


def identity(entry):
    params = entry.get("params") or {}
    if not isinstance(params, dict):
        raise ValueError(f"{entry.get('benchmark', '<unknown>')}: params must be an object")
    return entry.get("benchmark", ""), tuple(sorted((str(k), str(v)) for k, v in params.items()))


def display_key(key):
    benchmark, params = key
    short = ".".join(benchmark.split(".")[-2:])
    suffix = ",".join(f"{k}={v}" for k, v in params)
    return f"{short}[{suffix}]" if suffix else short


def index(entries, label):
    result = {}
    for entry in entries:
        key = identity(entry)
        if not key[0]:
            raise ValueError(f"{label}: benchmark name is missing")
        if key in result:
            raise ValueError(f"{label}: duplicate benchmark identity {display_key(key)}")
        result[key] = entry
    return result


def finite_number(value, field):
    number = float(value)
    if not math.isfinite(number):
        raise ValueError(f"{field} must be finite, got {value!r}")
    return number


def metric(entry):
    primary = entry.get("primaryMetric")
    if not isinstance(primary, dict):
        raise ValueError(f"{entry['benchmark']}: primaryMetric is missing")
    score = finite_number(primary.get("score"), "primaryMetric.score")
    raw_error = primary.get("scoreError")
    error = None if raw_error is None or (isinstance(raw_error, float) and math.isnan(raw_error)) else finite_number(raw_error, "primaryMetric.scoreError")
    confidence = primary.get("scoreConfidence")
    if confidence is not None:
        if not isinstance(confidence, list) or len(confidence) != 2:
            raise ValueError(f"{entry['benchmark']}: scoreConfidence must have two bounds")
        confidence = tuple(finite_number(v, "primaryMetric.scoreConfidence") for v in confidence)
    return score, error, confidence, str(primary.get("scoreUnit", ""))


def incompatibilities(baseline, candidate):
    reasons = [f"{field}: {baseline.get(field)!r} != {candidate.get(field)!r}" for field in CONFIG_FIELDS if baseline.get(field) != candidate.get(field)]
    b_unit = (baseline.get("primaryMetric") or {}).get("scoreUnit")
    c_unit = (candidate.get("primaryMetric") or {}).get("scoreUnit")
    if b_unit != c_unit:
        reasons.append(f"scoreUnit: {b_unit!r} != {c_unit!r}")
    return reasons


def interval(score, error, confidence):
    if confidence is not None:
        return confidence
    return None if error is None else (score - error, score + error)


def classify(base_score, cand_score, mode, threshold, overlap):
    if base_score == 0:
        return 0.0, "overlapping/noisy"
    delta = ((cand_score - base_score) / abs(base_score)) * 100.0
    if mode in HIGHER_IS_BETTER:
        improvement = delta
    elif mode in LOWER_IS_BETTER:
        improvement = -delta
    else:
        raise ValueError(f"unsupported or ambiguous JMH mode: {mode!r}")
    if overlap is not False or abs(delta) < threshold:
        return delta, "overlapping/noisy"
    if improvement >= threshold:
        return delta, "clear directional signal (likely improvement)"
    return delta, "large likely regression"


def compare_runs(baseline_data, candidate_data, threshold=5.0):
    baseline, candidate = index(baseline_data, "baseline"), index(candidate_data, "candidate")
    rows = []
    for key in sorted(set(baseline) | set(candidate)):
        b_entry, c_entry = baseline.get(key), candidate.get(key)
        if b_entry is None or c_entry is None:
            rows.append({"key": display_key(key), "status": "candidate only" if b_entry is None else "baseline only"})
            continue
        reasons = incompatibilities(b_entry, c_entry)
        if reasons:
            rows.append({"key": display_key(key), "status": "incompatible", "reasons": reasons})
            continue
        b_score, b_error, b_conf, unit = metric(b_entry)
        c_score, c_error, c_conf, _ = metric(c_entry)
        b_interval, c_interval = interval(b_score, b_error, b_conf), interval(c_score, c_error, c_conf)
        overlap = None if b_interval is None or c_interval is None else not (b_interval[1] < c_interval[0] or c_interval[1] < b_interval[0])
        delta, status = classify(b_score, c_score, b_entry.get("mode"), threshold, overlap)
        rows.append({"key": display_key(key), "status": status, "mode": b_entry.get("mode"), "unit": unit, "base": b_score, "candidate": c_score, "delta": delta})
    return rows


def render(rows, markdown):
    regressions = sum(row["status"] == "large likely regression" for row in rows)
    if markdown:
        print("| Benchmark | Mode | Baseline | Candidate | Delta | Classification |")
        print("|---|---|---:|---:|---:|---|")
    for row in rows:
        if "base" not in row:
            detail = "; ".join(row.get("reasons", []))
            message = row["status"] + ((": " + detail) if detail else "")
            print(f"| `{row['key']}` | — | — | — | — | {message} |" if markdown else f"{row['key']}: {message}")
        else:
            if markdown:
                print(f"| `{row['key']}` | {row['mode']} | {row['base']:.6g} {row['unit']} | {row['candidate']:.6g} {row['unit']} | {row['delta']:+.2f}% | {row['status']} |")
            else:
                print(f"{row['key']}: {row['base']:.6g} -> {row['candidate']:.6g} {row['unit']} ({row['delta']:+.2f}%); {row['status']}")
    return regressions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--threshold", type=float, default=5.0)
    parser.add_argument("--markdown", "-m", action="store_true")
    parser.add_argument("--fail-on-regression", action="store_true")
    args = parser.parse_args()
    try:
        regressions = render(compare_runs(load_jmh_json(args.baseline), load_jmh_json(args.candidate), args.threshold), args.markdown)
    except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 2
    return 1 if args.fail_on_regression and regressions else 0


if __name__ == "__main__":
    sys.exit(main())
