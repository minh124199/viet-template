#!/usr/bin/env python3
"""Measure two startup conditions in alternating order and retain every observation."""

import argparse
import importlib.util
import json
import sys
from pathlib import Path

CHECKPOINTS = ("jvmUptimeAtEntry", "repoReady", "engineInit", "compilation", "firstRender", "allFirstRenders", "smallBatch50", "totalStartup")


def load_measure_module():
    path = Path(__file__).with_name("measure-startup.py")
    spec = importlib.util.spec_from_file_location("measure_startup", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def summarize(module, runs):
    return {key: {"stats": module.compute_statistics([run["milliseconds"][key] for run in runs])} for key in CHECKPOINTS}


def write_result(path, label, java, flags, runs, summary, order):
    with path.open("w", encoding="utf-8") as handle:
        json.dump({"mode": "measurement", "condition": label, "java": {"bin": java, "flags": flags}, "launchOrder": order, "summary": summary, "rawRuns": runs}, handle, indent=2)
        handle.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True)
    parser.add_argument("--classpath", required=True)
    parser.add_argument("--tier", choices=["IR", "AOT_BYTECODE"], default="IR")
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--first-label", default="normal")
    parser.add_argument("--first-flags", default="")
    parser.add_argument("--first-output", type=Path, required=True)
    parser.add_argument("--second-label", default="aot")
    parser.add_argument("--second-flags", default="")
    parser.add_argument("--second-output", type=Path, required=True)
    args = parser.parse_args()
    if args.iterations < 20:
        parser.error("paired measurement requires at least 20 launches per condition")

    import shlex
    module = load_measure_module()
    conditions = {
        args.first_label: (shlex.split(args.first_flags), []),
        args.second_label: (shlex.split(args.second_flags), []),
    }
    order = []
    labels = [args.first_label, args.second_label]
    for iteration in range(args.iterations):
        iteration_order = labels if iteration % 2 == 0 else list(reversed(labels))
        for label in iteration_order:
            flags, runs = conditions[label]
            print(f"{iteration + 1}/{args.iterations} {label}", flush=True)
            runs.append(module.run_single_startup(args.java, flags, args.classpath, args.tier))
            order.append(label)

    for label, output in ((args.first_label, args.first_output), (args.second_label, args.second_output)):
        flags, runs = conditions[label]
        output.parent.mkdir(parents=True, exist_ok=True)
        write_result(output, label, args.java, flags, runs, summarize(module, runs), order)
    return 0


if __name__ == "__main__":
    sys.exit(main())
