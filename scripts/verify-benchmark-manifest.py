#!/usr/bin/env python3
"""Semantically validate the M18 benchmark and comparison contract."""

import argparse
import json
import re
import sys
from pathlib import Path

VALID_CATEGORIES = {"QUALIFICATION", "CHARACTERIZATION", "DIAGNOSTIC"}
VALID_ESCAPING = {"RAW", "HTML_ESCAPED"}
VALID_TRACKS = {"TRACK_A_DYNAMIC", "TRACK_B_COMPILED", "INTERNAL_VIET"}
REQUIRED_WORKLOAD_FIELDS = {
    "id", "name", "suite", "category", "tracks", "escaping",
    "claimEligible", "description",
}
ID_PATTERN = re.compile(r"^[BC][0-9]{2}$")


def _positive_int(value):
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def validate_manifest(data: object, repo_root: Path) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["manifest root must be an object"]
    for key in ("version", "title", "participatingEngines", "comparisonTracks", "protocols", "workloads"):
        if key not in data:
            errors.append(f"missing top-level field: {key}")

    engines = data.get("participatingEngines", [])
    if not isinstance(engines, list) or not engines:
        errors.append("participatingEngines must be a non-empty array")
        engines = []
    engine_ids: set[str] = set()
    for index, engine in enumerate(engines):
        if not isinstance(engine, dict):
            errors.append(f"engine at index {index} must be an object")
            continue
        missing = {"id", "name", "track", "version", "mode", "coordinates"} - set(engine)
        if missing:
            errors.append(f"engine at index {index} missing fields: {', '.join(sorted(missing))}")
            continue
        engine_id = engine["id"]
        if engine_id in engine_ids:
            errors.append(f"duplicate engine ID: {engine_id}")
        engine_ids.add(engine_id)
        if engine["track"] not in VALID_TRACKS - {"INTERNAL_VIET"}:
            errors.append(f"engine {engine_id} has invalid track: {engine['track']}")
        if not isinstance(engine["version"], str) or not engine["version"] or any(
            token in engine["version"].lower() for token in ("latest", "snapshot", "[")
        ) and not engine_id.startswith("viet-"):
            errors.append(f"competitor {engine_id} must use a pinned stable version")

    tracks = data.get("comparisonTracks", {})
    if not isinstance(tracks, dict):
        errors.append("comparisonTracks must be an object")
        tracks = {}
    for track_id, track in tracks.items():
        if track_id not in VALID_TRACKS - {"INTERNAL_VIET"}:
            errors.append(f"unknown comparison track: {track_id}")
        if not isinstance(track, dict) or not isinstance(track.get("engines"), list):
            errors.append(f"track {track_id} must declare an engines array")
            continue
        for engine_id in track["engines"]:
            if engine_id not in engine_ids:
                errors.append(f"track {track_id} references unknown engine: {engine_id}")

    protocols = data.get("protocols", {})
    if not isinstance(protocols, dict):
        errors.append("protocols must be an object")
        protocols = {}
    for required in ("CHARACTERIZATION", "RELEASE_QUALIFICATION"):
        protocol = protocols.get(required)
        if not isinstance(protocol, dict):
            errors.append(f"missing protocol: {required}")
            continue
        for field in ("forks", "warmupIterations", "measurementIterations", "iterationSeconds"):
            if not _positive_int(protocol.get(field)):
                errors.append(f"protocol {required} has invalid {field}")
    qualification = protocols.get("RELEASE_QUALIFICATION", {})
    if isinstance(qualification, dict) and (
        qualification.get("forks", 0) < 3
        or qualification.get("warmupIterations", 0) < 5
        or qualification.get("measurementIterations", 0) < 10
    ):
        errors.append("RELEASE_QUALIFICATION protocol must be at least 3 forks / 5 warmups / 10 measurements")

    workloads = data.get("workloads", [])
    if not isinstance(workloads, list) or not workloads:
        errors.append("workloads must be a non-empty array")
        return errors
    seen: set[str] = set()
    benchmark_sources = list((repo_root / "viet-template-benchmarks" / "src" / "main" / "java").rglob("*.java"))
    source_by_class = {path.stem: path for path in benchmark_sources}
    for index, workload in enumerate(workloads):
        if not isinstance(workload, dict):
            errors.append(f"workload at index {index} must be an object")
            continue
        missing = REQUIRED_WORKLOAD_FIELDS - set(workload)
        if missing:
            errors.append(f"workload at index {index} missing fields: {', '.join(sorted(missing))}")
            continue
        workload_id = workload["id"]
        if not isinstance(workload_id, str) or not ID_PATTERN.fullmatch(workload_id):
            errors.append(f"invalid workload ID: {workload_id!r}")
        if workload_id in seen:
            errors.append(f"duplicate workload ID: {workload_id}")
        seen.add(workload_id)
        if workload["category"] not in VALID_CATEGORIES:
            errors.append(f"workload {workload_id} has unknown category: {workload['category']}")
        if workload["escaping"] not in VALID_ESCAPING:
            errors.append(f"workload {workload_id} has invalid escaping mode: {workload['escaping']}")
        workload_tracks = workload["tracks"]
        if not isinstance(workload_tracks, list) or not workload_tracks:
            errors.append(f"workload {workload_id} must declare tracks")
        else:
            for track in workload_tracks:
                if track not in VALID_TRACKS:
                    errors.append(f"workload {workload_id} references unknown track: {track}")
        if workload["claimEligible"] and workload["category"] != "QUALIFICATION":
            errors.append(f"workload {workload_id} is claim-eligible but not RELEASE_QUALIFICATION")
        suite = workload["suite"]
        source = source_by_class.get(suite)
        if source is None:
            errors.append(f"workload {workload_id} references missing benchmark class: {suite}")
        elif workload_id.startswith("C"):
            method_prefix = workload_id.lower() + "_"
            if method_prefix not in source.read_text(encoding="utf-8"):
                errors.append(f"workload {workload_id} has no matching method in {suite}")
        if workload_id.startswith("C") and workload["category"] == "QUALIFICATION":
            fixture = data.get("comparativeCorrectnessFixture", {})
            if workload_id not in fixture.get("workloads", []) or not fixture.get("testClass"):
                errors.append(f"comparative workload {workload_id} lacks correctness-fixture linkage")
        if workload["claimEligible"] and data.get("allocationEvidencePolicy") != "MATCHED_REQUIRED":
            errors.append(f"claim-eligible workload {workload_id} requires MATCHED_REQUIRED allocation evidence")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("config/benchmark-manifest.json"))
    args = parser.parse_args()
    repo_root = Path(__file__).resolve().parent.parent
    path = args.manifest if args.manifest.is_absolute() else repo_root / args.manifest
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"[FAIL] benchmark manifest cannot be read: {exc}", file=sys.stderr)
        return 1
    errors = validate_manifest(data, repo_root)
    if errors:
        print(f"[FAIL] benchmark manifest has {len(errors)} error(s):", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"[PASS] benchmark manifest {data['version']}: {len(data['workloads'])} workloads semantically valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
