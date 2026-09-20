#!/usr/bin/env python3
"""Validate the durable, SHA-bound M18 comparative benchmark evidence package."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SHA256 = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_PROFILES = {"J21-G1", "J25-G1"}


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def qualification_input_digest(root: Path) -> str:
    import subprocess

    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).split(b"\0")
    root_build_files = {b"pom.xml", b"build.gradle.kts", b"settings.gradle.kts", b"gradle.properties"}
    exact_inputs = {
        b"config/benchmark-manifest.json",
        b"config/benchmark-runtime-profiles.json",
        b"scripts/perf/run-m18-comparative-qualification.sh",
        b"scripts/record-benchmark-env.sh",
    }
    value = hashlib.sha256()
    for raw_path in sorted(path for path in tracked if path):
        if not (
            raw_path.startswith(b"viet-template-")
            or raw_path.startswith(b"gradle/")
            or raw_path in root_build_files
            or raw_path in exact_inputs
        ):
            continue
        path = root / raw_path.decode()
        value.update(raw_path + b"\0")
        value.update(hashlib.sha256(path.read_bytes()).digest())
    return value.hexdigest()


def validate_evidence(directory: Path, expected_sha: str | None = None) -> list[str]:
    errors: list[str] = []
    manifest_path = directory / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"evidence manifest cannot be read: {exc}"]
    for field in (
        "schemaVersion", "classification", "gitSha", "gitTree", "qualificationInputSha256", "createdAt",
        "qualificationProtocol", "profiles", "competitors", "benchmarkManifestSha256",
        "correctness", "files",
    ):
        if field not in manifest:
            errors.append(f"manifest missing field: {field}")
    if manifest.get("classification") != "RELEASE_QUALIFICATION":
        errors.append("formal evidence classification must be RELEASE_QUALIFICATION")
    git_sha = manifest.get("gitSha", "")
    if not GIT_SHA.fullmatch(git_sha):
        errors.append("gitSha must be a full 40-character lowercase commit SHA")
    if expected_sha and git_sha != expected_sha:
        expected_input = manifest.get("qualificationInputSha256")
        actual_input = qualification_input_digest(directory.parents[1])
        if not expected_input or expected_input != actual_input:
            errors.append(
                f"evidence SHA {git_sha} does not match release source SHA {expected_sha}, "
                "and the qualification-input digest also differs"
            )
    protocol = manifest.get("qualificationProtocol", {})
    if not isinstance(protocol, dict) or (
        protocol.get("forks", 0) < 3
        or protocol.get("warmupIterations", 0) < 5
        or protocol.get("measurementIterations", 0) < 10
    ):
        errors.append("qualification protocol must be at least 3 forks / 5 warmups / 10 measurements")
    profiles = manifest.get("profiles", [])
    profile_ids = {profile.get("id") for profile in profiles if isinstance(profile, dict)}
    missing_profiles = REQUIRED_PROFILES - profile_ids
    if missing_profiles:
        errors.append(f"missing required profiles: {', '.join(sorted(missing_profiles))}")
    correctness = manifest.get("correctness", {})
    if correctness.get("passed") is not True or correctness.get("workloads") != 8 or correctness.get("engines") != 6:
        errors.append("correctness evidence must record all 8 workloads across all 6 engines as passed")
    benchmark_manifest = directory.parents[1] / "config" / "benchmark-manifest.json"
    if benchmark_manifest.exists() and manifest.get("benchmarkManifestSha256") != digest(benchmark_manifest):
        errors.append("benchmark manifest checksum does not match config/benchmark-manifest.json")
    files = manifest.get("files", [])
    if not isinstance(files, list) or not files:
        errors.append("manifest files must be a non-empty array")
        files = []
    seen: set[str] = set()
    for entry in files:
        if not isinstance(entry, dict) or not {"path", "sha256", "kind"} <= set(entry):
            errors.append("every evidence file entry requires path, sha256, and kind")
            continue
        relative = entry["path"]
        if relative in seen:
            errors.append(f"duplicate evidence file: {relative}")
        seen.add(relative)
        if Path(relative).is_absolute() or ".." in Path(relative).parts:
            errors.append(f"unsafe evidence path: {relative}")
            continue
        path = directory / relative
        if not path.is_file():
            errors.append(f"missing evidence file: {relative}")
        elif not SHA256.fullmatch(entry["sha256"]) or digest(path) != entry["sha256"]:
            errors.append(f"checksum mismatch: {relative}")
    required_kinds = {"environment", "jmh-comparative-with-gc", "report"}
    found_kinds = {entry.get("kind") for entry in files if isinstance(entry, dict)}
    if missing := required_kinds - found_kinds:
        errors.append(f"missing evidence file kinds: {', '.join(sorted(missing))}")
    checksums_path = directory / "SHA256SUMS"
    if not checksums_path.is_file():
        errors.append("missing SHA256SUMS")
    else:
        listed = {}
        for line in checksums_path.read_text(encoding="utf-8").splitlines():
            parts = line.split("  ", 1)
            if len(parts) == 2:
                listed[parts[1]] = parts[0]
        for entry in files:
            if isinstance(entry, dict) and listed.get(entry.get("path")) != entry.get("sha256"):
                errors.append(f"SHA256SUMS does not match manifest for {entry.get('path')}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, default=Path("benchmark-evidence/m18"))
    parser.add_argument("--expected-sha")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    directory = args.evidence_dir if args.evidence_dir.is_absolute() else root / args.evidence_dir
    errors = validate_evidence(directory, args.expected_sha)
    if errors:
        print(f"[FAIL] M18 evidence contract has {len(errors)} error(s):", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("[PASS] M18 formal evidence manifest, profiles, checksums, correctness, and SHA are valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
