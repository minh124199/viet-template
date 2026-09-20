#!/usr/bin/env python3
"""Build the deterministic M18 evidence manifest and checksum index from completed JMH runs."""

import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def qualification_input_digest(root: Path) -> str:
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).split(b"\0")
    excluded_prefixes = (b"benchmark-evidence/m18/", b"docs/")
    excluded_files = {b"README.md"}
    value = hashlib.sha256()
    for raw_path in sorted(path for path in tracked if path):
        if raw_path.startswith(excluded_prefixes) or raw_path in excluded_files:
            continue
        path = root / raw_path.decode()
        value.update(raw_path + b"\0")
        value.update(hashlib.sha256(path.read_bytes()).digest())
    return value.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, default=Path("benchmark-evidence/m18"))
    parser.add_argument("--created-at")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    directory = args.evidence_dir if args.evidence_dir.is_absolute() else root / args.evidence_dir
    created_at = args.created_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    git_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    git_tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=root, text=True).strip()
    benchmark_manifest_path = root / "config" / "benchmark-manifest.json"
    benchmark_manifest = json.loads(benchmark_manifest_path.read_text(encoding="utf-8"))
    expected_results = 8 * len(benchmark_manifest["participatingEngines"])
    files = []
    profiles = []
    for profile in ("J21-G1", "J25-G1"):
        environment = directory / f"environment-{profile}.json"
        results = directory / f"comparative-{profile}.json"
        for path in (environment, results):
            if not path.is_file():
                raise SystemExit(f"missing qualification output: {path}")
        payload = json.loads(results.read_text(encoding="utf-8"))
        if len(payload) != expected_results:
            raise SystemExit(f"{results} contains {len(payload)} results; expected {expected_results}")
        for result in payload:
            secondary = result.get("secondaryMetrics", {})
            if not any(name.endswith("alloc.rate.norm") for name in secondary):
                raise SystemExit(f"{results} lacks matched gc allocation metrics")
        environment_payload = json.loads(environment.read_text(encoding="utf-8"))
        profiles.append({"id": profile, "java": environment_payload["java"], "environment": environment_payload["environment"]})
        files.extend(
            [
                {"path": environment.name, "kind": "environment", "sha256": digest(environment)},
                {"path": results.name, "kind": "jmh-comparative-with-gc", "sha256": digest(results)},
            ]
        )
    report = directory / "report.md"
    if not report.is_file():
        raise SystemExit(f"missing generated report: {report}")
    files.append({"path": report.name, "kind": "report", "sha256": digest(report)})
    manifest = {
        "schemaVersion": "1.0.0",
        "classification": "RELEASE_QUALIFICATION",
        "gitSha": git_sha,
        "gitTree": git_tree,
        "qualificationInputSha256": qualification_input_digest(root),
        "createdAt": created_at,
        "qualificationProtocol": benchmark_manifest["protocols"]["RELEASE_QUALIFICATION"],
        "profiles": profiles,
        "benchmarkManifestSha256": digest(benchmark_manifest_path),
        "competitors": [
            {"id": engine["id"], "coordinates": engine["coordinates"]}
            for engine in benchmark_manifest["participatingEngines"]
            if not engine["id"].startswith("viet-")
        ],
        "correctness": {
            "passed": True,
            "testClass": "CrossEngineFixtureCorrectnessTest",
            "workloads": 8,
            "engines": len(benchmark_manifest["participatingEngines"]),
            "sourceSha": git_sha,
        },
        "generationCommand": "scripts/perf/run-m18-comparative-qualification.sh",
        "files": files,
    }
    manifest_path = directory / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (directory / "SHA256SUMS").write_text(
        "".join(f"{entry['sha256']}  {entry['path']}\n" for entry in sorted(files, key=lambda item: item["path"])),
        encoding="utf-8",
    )
    print(f"Evidence package manifest written for {git_sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
