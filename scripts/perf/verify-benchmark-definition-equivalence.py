#!/usr/bin/env python3
"""Audit and verify benchmark definition equivalence across release and rerun evidence packages.

Verifies that the canonical C01-C08 comparative benchmark sources, execution protocol,
and runtime identity are byte-for-byte identical between v1.1.0 qualification tooling
and v1.0.0 OpenJDK 25 rerun tooling.
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")

REQUIRED_BENCHMARK_FILES = [
    "ComparativeEngineBenchmark.java",
    "ComparativeWorkloads.java",
    "BenchmarkEngineAdapter.java",
    "VietIrAdapter.java",
    "VietAotAdapter.java",
    "VelocityAdapter.java",
    "QuteAdapter.java",
    "JteAdapter.java",
    "ThymeleafAdapter.java",
    "CrossEngineFixtureCorrectnessTest.java",
    "scripts/perf/run-m18-comparative-qualification.sh",
    "scripts/perf/jdk_identity.py",
]

EXPECTED_PROTOCOL = {
    "forks": 3,
    "warmupIterations": 5,
    "measurementIterations": 10,
    "iterationSeconds": 1,
    "jvmArgs": [
        "-server",
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC",
    ],
    "profilerGc": True,
    "benchmarkRegex": ".*ComparativeEngineBenchmark.*",
}


def compute_sha256(path: Path) -> str:
    """Computes SHA-256 hex digest for a file."""
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def extract_file_entry(files_dict: dict, file_name: str) -> tuple[str, str] | None:
    """Extracts (relative_path, sha256) for a given benchmark file identifier."""
    # Check direct filename key
    if file_name in files_dict:
        val = files_dict[file_name]
        if isinstance(val, dict):
            return val.get("path", file_name), val.get("sha256", "")
        if isinstance(val, str):
            return file_name, val

    # Check match by suffix in path
    for key, val in files_dict.items():
        if key.endswith(file_name):
            if isinstance(val, dict):
                return val.get("path", key), val.get("sha256", "")
            if isinstance(val, str):
                return key, val
        elif isinstance(val, dict) and val.get("path", "").endswith(file_name):
            return val.get("path", ""), val.get("sha256", "")

    return None


def verify_benchmark_equivalence(
    v110_dir: Path,
    rerun_dir: Path,
    repo_root: Path | None = None,
) -> list[str]:
    """Audits benchmark definition manifests and verifies semantic equivalence."""
    errors: list[str] = []

    v110_def_path = v110_dir / "benchmark-definition.json"
    rerun_def_path = rerun_dir / "benchmark-definition.json"

    if not v110_def_path.is_file():
        return [f"Missing benchmark definition manifest: {v110_def_path}"]
    if not rerun_def_path.is_file():
        return [f"Missing benchmark definition manifest: {rerun_def_path}"]

    try:
        v110_data = json.loads(v110_def_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return [f"Cannot parse JSON in {v110_def_path}: {exc}"]

    try:
        rerun_data = json.loads(rerun_def_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return [f"Cannot parse JSON in {rerun_def_path}: {exc}"]

    # 1. Structural fields
    for name, data in [("v1.1.0", v110_data), ("v1.0.0 rerun", rerun_data)]:
        for req in ("schemaVersion", "benchmarkSuite", "toolingSha", "equivalenceStatus", "protocol", "runtimeIdentity", "benchmarkFiles"):
            if req not in data:
                errors.append(f"{name} benchmark definition missing required field: {req}")

    if errors:
        return errors

    if v110_data.get("benchmarkSuite") != "ComparativeEngineBenchmark":
        errors.append(f"v1.1.0 benchmarkSuite must be 'ComparativeEngineBenchmark', got: {v110_data.get('benchmarkSuite')}")
    if rerun_data.get("benchmarkSuite") != "ComparativeEngineBenchmark":
        errors.append(f"v1.0.0 rerun benchmarkSuite must be 'ComparativeEngineBenchmark', got: {rerun_data.get('benchmarkSuite')}")

    if v110_data.get("equivalenceStatus") != "IDENTICAL":
        errors.append(f"v1.1.0 equivalenceStatus must be 'IDENTICAL', got: {v110_data.get('equivalenceStatus')}")
    if rerun_data.get("equivalenceStatus") != "IDENTICAL":
        errors.append(f"v1.0.0 rerun equivalenceStatus must be 'IDENTICAL', got: {rerun_data.get('equivalenceStatus')}")

    for name, data in [("v1.1.0", v110_data), ("v1.0.0 rerun", rerun_data)]:
        tooling_sha = data.get("toolingSha", "")
        if not GIT_SHA_RE.fullmatch(tooling_sha):
            errors.append(f"{name} toolingSha must be a 40-character git commit SHA, got: {tooling_sha}")

    # Tooling SHA difference is permitted as metadata, but must be documented
    # Benchmark source and protocol semantics must be identical.

    # 2. Benchmark files byte-for-byte SHA256 equivalence
    v110_files = v110_data.get("benchmarkFiles", {})
    rerun_files = rerun_data.get("benchmarkFiles", {})

    for fname in REQUIRED_BENCHMARK_FILES:
        entry_110 = extract_file_entry(v110_files, fname)
        entry_rerun = extract_file_entry(rerun_files, fname)

        if not entry_110:
            errors.append(f"v1.1.0 benchmark definition missing required file: {fname}")
            continue
        if not entry_rerun:
            errors.append(f"v1.0.0 rerun benchmark definition missing required file: {fname}")
            continue

        rel_path_110, sha_110 = entry_110
        _, sha_rerun = entry_rerun

        if not SHA256_RE.fullmatch(sha_110):
            errors.append(f"Invalid SHA-256 hash for {fname} in v1.1.0 manifest: {sha_110}")
        if not SHA256_RE.fullmatch(sha_rerun):
            errors.append(f"Invalid SHA-256 hash for {fname} in v1.0.0 rerun manifest: {sha_rerun}")

        if sha_110 != sha_rerun:
            errors.append(
                f"Semantic difference detected in benchmark file {fname}: "
                f"v1.1.0 SHA {sha_110} != rerun SHA {sha_rerun}"
            )

        if repo_root:
            disk_path = repo_root / rel_path_110
            if not disk_path.is_file():
                errors.append(f"Benchmark-critical file not found on disk: {disk_path}")
            else:
                disk_sha = compute_sha256(disk_path)
                if disk_sha != sha_110:
                    errors.append(
                        f"Checksum mismatch on disk for {rel_path_110}: expected {sha_110}, got {disk_sha}"
                    )

    # 3. Protocol parameters equivalence
    v110_proto = v110_data.get("protocol", {})
    rerun_proto = rerun_data.get("protocol", {})

    for key, expected_val in EXPECTED_PROTOCOL.items():
        # jvmArgs may be keyed as jvmArgs or jvmFlags
        actual_110 = v110_proto.get(key)
        if actual_110 is None and key == "jvmArgs":
            actual_110 = v110_proto.get("jvmFlags")

        actual_rerun = rerun_proto.get(key)
        if actual_rerun is None and key == "jvmArgs":
            actual_rerun = rerun_proto.get("jvmFlags")

        if actual_110 != expected_val:
            errors.append(f"v1.1.0 protocol mismatch for '{key}': expected {expected_val}, got {actual_110}")
        if actual_rerun != expected_val:
            errors.append(f"v1.0.0 rerun protocol mismatch for '{key}': expected {expected_val}, got {actual_rerun}")

    # 4. Runtime identity equivalence
    v110_rt = v110_data.get("runtimeIdentity", {})
    rerun_rt = rerun_data.get("runtimeIdentity", {})

    # Java major/runtime version
    if v110_rt.get("javaVersion") != "25.0.4.1":
        errors.append(f"v1.1.0 runtime javaVersion must be '25.0.4.1', got: {v110_rt.get('javaVersion')}")
    if rerun_rt.get("javaVersion") != "25.0.4.1":
        errors.append(f"v1.0.0 rerun runtime javaVersion must be '25.0.4.1', got: {rerun_rt.get('javaVersion')}")

    # JVMCI disabled (genuine HotSpot C2)
    if v110_rt.get("useJvmciCompiler") is not False or v110_rt.get("enableJvmci") is not False:
        errors.append("v1.1.0 runtime must disable JVMCI (HotSpot C2)")
    if rerun_rt.get("useJvmciCompiler") is not False or rerun_rt.get("enableJvmci") is not False:
        errors.append("v1.0.0 rerun runtime must disable JVMCI (HotSpot C2)")

    # VM Name
    for name, rt in [("v1.1.0", v110_rt), ("v1.0.0 rerun", rerun_rt)]:
        vm_name = rt.get("vmName", "")
        if "OpenJDK" not in vm_name:
            errors.append(f"{name} runtime vmName must indicate OpenJDK, got: {vm_name}")

    # javaExecutableSha256 must match
    exe_sha_110 = v110_rt.get("javaExecutableSha256", "")
    exe_sha_rerun = rerun_rt.get("javaExecutableSha256", "")
    if not SHA256_RE.fullmatch(exe_sha_110):
        errors.append(f"v1.1.0 missing or invalid javaExecutableSha256: {exe_sha_110}")
    if not SHA256_RE.fullmatch(exe_sha_rerun):
        errors.append(f"v1.0.0 rerun missing or invalid javaExecutableSha256: {exe_sha_rerun}")
    if exe_sha_110 and exe_sha_rerun and exe_sha_110 != exe_sha_rerun:
        errors.append(
            f"Runtime identity mismatch: javaExecutableSha256 differs ({exe_sha_110} != {exe_sha_rerun})"
        )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--v110-dir", type=Path, default=Path("benchmark-evidence/1.1.0"))
    parser.add_argument("--rerun-dir", type=Path, default=Path("benchmark-evidence/1.0.0-openjdk25-rerun"))
    parser.add_argument("--repo-root", type=Path, default=None)
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    repo_root = args.repo_root or Path(__file__).resolve().parents[2]
    v110_dir = args.v110_dir if args.v110_dir.is_absolute() else repo_root / args.v110_dir
    rerun_dir = args.rerun_dir if args.rerun_dir.is_absolute() else repo_root / args.rerun_dir

    if args.verbose:
        print(f"Auditing benchmark definition equivalence:")
        print(f"  v1.1.0: {v110_dir}")
        print(f"  Rerun:  {rerun_dir}")
        print(f"  Root:   {repo_root}")

    errors = verify_benchmark_equivalence(v110_dir, rerun_dir, repo_root)
    if errors:
        print(f"[FAIL] Benchmark definition equivalence audit failed with {len(errors)} error(s):", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1

    print("[PASS] Benchmark definitions are byte-for-byte equivalent across canonical files, protocol, and runtime identity.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
