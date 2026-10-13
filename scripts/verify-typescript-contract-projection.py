#!/usr/bin/env python3
"""
verify-typescript-contract-projection.py

Automated qualification verification script for Viet Template 1.5 M46A:
Schema-to-Client TypeScript Contract Projection Architecture Qualification.

Mission:
1. Verifies availability of Node.js and TypeScript compiler (tsc).
2. Verifies execution and PASS status of all 14 bounded qualification prototypes:
   - Prototype 01: Real tsc --noEmit validation of generated declarations.
   - Prototype 02: Negative test proving intentional typo 'employee.nmae' is rejected by tsc.
   - Prototype 03: Nullability and optionality mapping (T | null).
   - Prototype 04: Map type projection (Record<string, V>).
   - Prototype 05: Recursive types projection without cycle or stack overflow.
   - Prototype 06: Package collision disambiguation.
   - Prototype 07: 100 consecutive generations byte-for-byte deterministic.
   - Prototype 08: Incremental write avoidance (mtime preserved).
   - Prototype 09: Stale output cleanup safety (owned vs unowned).
   - Prototype 10: Windows backslash normalization and path traversal guards.
   - Prototype 11: MemberAccessPolicy security boundary (getClass, ClassLoader excluded).
   - Prototype 12: ClientData SimpleJsonSerializer runtime JSON shape parity 1:1.
   - Prototype 13: Performance scaling benchmark.
   - Prototype 14: Frontend framework neutrality (zero framework imports).
3. Writes machine-readable JSON report to build/reports/typescript-contract-projection-qualification.json.
4. Exits 0 on PASS, non-zero on failure.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_REPORT_PATH = REPO_ROOT / "build/reports/typescript-contract-projection-qualification.json"
TEST_XML_PATH = (
    REPO_ROOT
    / "viet-template-vtl-interpreter/build/test-results/test"
    / "TEST-io.github.minh124199.viettemplate.qualification.m46a.M46ATypeScriptProjectionQualificationTest.xml"
)

EXPECTED_PROTOTYPES = [
    "Prototype 01: Real tsc --noEmit validates generated .d.ts",
    "Prototype 02: Negative test — consumer typo 'employee.nmae' is rejected by tsc",
    "Prototype 03: Nullable template parameters map to T | null and enforce TS checks",
    "Prototype 04: Map types project to Record<string, V>",
    "Prototype 05: Recursive types project safely without cycle or overflow",
    "Prototype 06: Same simple name in different packages is disambiguated deterministically",
    "Prototype 07: 100 consecutive generations produce identical byte output and SHA-256",
    "Prototype 08: writeIfChanged skips rewrite and preserves mtime when content is unchanged",
    "Prototype 09: cleanStaleOutputs deletes only owned stale files, leaving user files untouched",
    "Prototype 10: Windows backslashes normalize and path traversals are blocked",
    "Prototype 11: Sensitive methods (getClass, ClassLoader, Thread) are never projected",
    "Prototype 12: Generated declarations match SimpleJsonSerializer runtime JSON shape 1:1",
    "Prototype 13: Generator scales well (< 500ms for 10 contracts, < 2000ms for 100 contracts)",
    "Prototype 14: Generated .d.ts has exactly zero framework imports",
]


def find_node_executable() -> str | None:
    """Finds node executable on PATH."""
    return shutil.which("node")


def find_tsc_executable(repo_root: Path) -> Path | None:
    """Finds tsc script or executable in repository or on PATH."""
    candidates = [
        repo_root / "examples/frontend-svelte-islands/node_modules/typescript/bin/tsc",
        repo_root / "editors/vscode/node_modules/typescript/bin/tsc",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    system_tsc = shutil.which("tsc")
    if system_tsc:
        return Path(system_tsc)
    return None


def run_gradle_qualification_tests(repo_root: Path) -> int:
    """Runs the Gradle qualification test suite."""
    gradlew = repo_root / "gradlew"
    cmd = [
        str(gradlew),
        ":viet-template-vtl-interpreter:test",
        "--tests",
        "io.github.minh124199.viettemplate.qualification.m46a.*",
        "--no-daemon",
    ]
    print(f"[INFO] Executing Gradle qualification tests: {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=repo_root)
    return res.returncode


def parse_qualification_test_results(xml_path: Path) -> dict[str, Any]:
    """Parses JUnit XML report of M46A qualification tests."""
    if not xml_path.is_file():
        return {
            "status": "FAIL",
            "error": f"Test results XML not found at {xml_path}. Run tests first.",
            "passed": 0,
            "failed": 0,
            "total": 0,
            "prototypes": {},
        }

    tree = ET.parse(xml_path)
    root = tree.getroot()

    total_tests = int(root.attrib.get("tests", 0))
    failures = int(root.attrib.get("failures", 0))
    errors = int(root.attrib.get("errors", 0))
    skipped = int(root.attrib.get("skipped", 0))

    prototypes_status: dict[str, dict[str, Any]] = {}
    for tc in root.findall("testcase"):
        name = tc.attrib.get("name", "")
        time_sec = float(tc.attrib.get("time", 0.0))
        failure_elem = tc.find("failure")
        error_elem = tc.find("error")
        skipped_elem = tc.find("skipped")

        if failure_elem is not None:
            status = "FAILED"
            msg = failure_elem.attrib.get("message", "")
        elif error_elem is not None:
            status = "ERROR"
            msg = error_elem.attrib.get("message", "")
        elif skipped_elem is not None:
            status = "SKIPPED"
            msg = "skipped"
        else:
            status = "PASSED"
            msg = ""

        prototypes_status[name] = {
            "status": status,
            "time_seconds": time_sec,
            "message": msg,
        }

    passed_count = sum(1 for p in prototypes_status.values() if p["status"] == "PASSED")
    all_expected_passed = True
    missing_prototypes = []

    for expected in EXPECTED_PROTOTYPES:
        if expected not in prototypes_status:
            all_expected_passed = False
            missing_prototypes.append(expected)
        elif prototypes_status[expected]["status"] != "PASSED":
            all_expected_passed = False

    overall_status = "PASS" if (all_expected_passed and failures == 0 and errors == 0) else "FAIL"

    return {
        "status": overall_status,
        "total": total_tests,
        "passed": passed_count,
        "failed": failures + errors,
        "skipped": skipped,
        "missing_prototypes": missing_prototypes,
        "prototypes": prototypes_status,
    }


def generate_report(
    results: dict[str, Any],
    node_exe: str | None,
    tsc_path: Path | None,
    report_file: Path,
) -> None:
    """Writes JSON verification report."""
    report_file.parent.mkdir(parents=True, exist_ok=True)
    report_data = {
        "milestone": "M46A",
        "description": "Schema-to-Client TypeScript Contract Projection Architecture Qualification",
        "verdict": "GO" if results.get("status") == "PASS" else "NO_GO",
        "status": results.get("status"),
        "node_executable": node_exe,
        "tsc_path": str(tsc_path) if tsc_path else None,
        "total_prototypes": len(EXPECTED_PROTOTYPES),
        "passed_prototypes": results.get("passed", 0),
        "failed_prototypes": results.get("failed", 0),
        "prototype_results": results.get("prototypes", {}),
    }
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(report_data, f, indent=2, sort_keys=True)
    print(f"[INFO] Report written to: {report_file}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify TypeScript Contract Projection Qualification (M46A)"
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT_PATH,
        help="Path to write the verification JSON report",
    )
    parser.add_argument(
        "--run-tests",
        action="store_true",
        help="Execute Gradle qualification tests before parsing",
    )
    parser.add_argument(
        "--check-only",
        action="store_true",
        help="Check existing test results without running Gradle test suite",
    )
    args = parser.parse_args()

    print("=" * 80)
    print("Viet Template 1.5 M46A TypeScript Projection Qualification Verifier")
    print("=" * 80)

    # 1. Check Toolchain
    node_exe = find_node_executable()
    tsc_path = find_tsc_executable(REPO_ROOT)
    print(f"[CHECK] Node.js executable: {node_exe or 'NOT FOUND'}")
    print(f"[CHECK] TypeScript tsc:     {tsc_path or 'NOT FOUND'}")

    if not node_exe:
        print("[WARN] Node.js is not on PATH. Real tsc execution may be skipped in tests.")
    if not tsc_path:
        print("[WARN] TypeScript compiler (tsc) not found in repo node_modules or system.")

    # 2. Run tests if requested or if XML missing
    if args.run_tests or (not args.check_only and not TEST_XML_PATH.is_file()):
        rc = run_gradle_qualification_tests(REPO_ROOT)
        if rc != 0:
            print("[FAIL] Gradle qualification test execution failed.")
            # Still proceed to parse XML for diagnostic report

    # 3. Parse XML
    results = parse_qualification_test_results(TEST_XML_PATH)

    print("-" * 80)
    print(f"Total Prototypes:  {results.get('total', 0)}")
    print(f"Passed:            {results.get('passed', 0)}")
    print(f"Failed:            {results.get('failed', 0)}")
    print(f"Skipped:           {results.get('skipped', 0)}")
    print("-" * 80)

    for proto in EXPECTED_PROTOTYPES:
        proto_data = results.get("prototypes", {}).get(proto)
        if proto_data:
            st = proto_data.get("status")
            t = proto_data.get("time_seconds", 0.0)
            mark = "[PASS]" if st == "PASSED" else "[FAIL]"
            print(f"{mark} {proto} ({t:.3f}s)")
        else:
            print(f"[FAIL] {proto} (MISSING)")

    # 4. Generate JSON Report
    generate_report(results, node_exe, tsc_path, args.report)

    if results.get("status") == "PASS":
        print("=" * 80)
        print("[SUCCESS] All 14 M46A TypeScript Contract Projection Prototypes QUALIFIED (GO).")
        print("=" * 80)
        return 0
    else:
        print("=" * 80)
        print("[FAILURE] One or more M46A qualification prototypes failed.")
        print("=" * 80)
        return 1


if __name__ == "__main__":
    sys.exit(main())
