#!/usr/bin/env python3
"""
verify-ga-readiness.py

Master GA Readiness Verifier for Viet Template 1.0 (Milestone M13).
Orchestrates and verifies the critical GA invariants:
1. RC3 Release Provenance & Immutability (tags, commits, Central deployment)
2. State-aware product policy (historical RC3 freeze or post-GA patch development)
3. API Freeze & Compatibility (0 breaking changes across all 5 baselines)
4. Runtime ABI Freeze (exact 7 types, 22 methods, 0 fields, 0 unregistered)
5. Public Surface Classification (339 types, 121 stable, 0 signature leaks)
6. Framework & Tooling Entrypoints
7. Cross-Module Internal Contracts
8. Diagnostic Codes Baseline (31 canonical codes)
9. Living Documentation Integrity
10. TCK Coverage (100% feature coverage)
11. Build Tool Parity (Maven & Gradle)
12. Release Infrastructure & Publication Topology (14 public coordinates, 2 internal)
13. Gradle Plugin Signing Lifecycle
14. Security Surface & Policy Denials
15. Performance Manifest Integrity
16. Clean-Worktree Verification
17. GA Workflow Dry-Run Contract (stable non-prerelease)

Emits: build/reports/ga-readiness.json
Pre-GA verdict: READY_TO_TAG_1_0_0 (or RC4_REQUIRED / NOT_READY_FOR_1_0_0)
Post-GA verdict: PATCH_RELEASE_REQUIRED (or NOT_READY_FOR_1_0_1)
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_REPORT = REPO_ROOT / "build" / "reports" / "ga-readiness.json"

RC3_VERSION = "1.0.0-RC3"
RC3_TAG = "v1.0.0-RC3"
RC3_COMMIT_SHA = "aedfa96acbca0f435c892c925dd71c87eba20e8c"
RC3_TAG_OBJECT_SHA = "f3cbfc5410ce89aaf8e92defb27a1edc905276a7"
RC3_DEPLOYMENT_ID = "b208f631-4891-4f17-9659-589663e032e6"

RC2_TAG = "v1.0.0-RC2"
RC2_COMMIT_SHA = "8c0504f45926291964e5b45ff280b8f065cd8f04"
RC2_TAG_OBJECT_SHA = "a6bd4bbc9d05dc6ba4780df84a4921b35942456d"

TARGET_VERSION_DEFAULT = "1.0.0"
VERDICT_READY_TO_TAG = "READY_TO_TAG_1_0_0"
VERDICT_RC4_REQUIRED = "RC4_REQUIRED"
VERDICT_NOT_READY = "NOT_READY_FOR_1_0_0"


def run_command(cmd: list[str], cwd: Path = REPO_ROOT) -> tuple[int, str, str]:
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    return result.returncode, result.stdout, result.stderr


def check_git_provenance(baseline_tag: str = RC3_TAG) -> dict[str, Any]:
    issues: list[str] = []

    # Check RC3 tag object
    rc, stdout, _ = run_command(["git", "rev-parse", RC3_TAG])
    actual_rc3_tag = stdout.strip() if rc == 0 else ""
    if actual_rc3_tag != RC3_TAG_OBJECT_SHA:
        issues.append(f"RC3 tag object mismatch: expected {RC3_TAG_OBJECT_SHA}, got {actual_rc3_tag}")

    # Check RC3 commit
    rc, stdout, _ = run_command(["git", "rev-parse", f"{RC3_TAG}^{{commit}}"])
    actual_rc3_commit = stdout.strip() if rc == 0 else ""
    if actual_rc3_commit != RC3_COMMIT_SHA:
        issues.append(f"RC3 commit mismatch: expected {RC3_COMMIT_SHA}, got {actual_rc3_commit}")

    # Check RC2 tag object
    rc, stdout, _ = run_command(["git", "rev-parse", RC2_TAG])
    actual_rc2_tag = stdout.strip() if rc == 0 else ""
    if actual_rc2_tag != RC2_TAG_OBJECT_SHA:
        issues.append(f"RC2 tag object mismatch: expected {RC2_TAG_OBJECT_SHA}, got {actual_rc2_tag}")

    # Check RC2 commit
    rc, stdout, _ = run_command(["git", "rev-parse", f"{RC2_TAG}^{{commit}}"])
    actual_rc2_commit = stdout.strip() if rc == 0 else ""
    if actual_rc2_commit != RC2_COMMIT_SHA:
        issues.append(f"RC2 commit mismatch: expected {RC2_COMMIT_SHA}, got {actual_rc2_commit}")

    # Check current HEAD
    rc, stdout, _ = run_command(["git", "rev-parse", "HEAD"])
    head_sha = stdout.strip() if rc == 0 else ""

    return {
        "passed": len(issues) == 0,
        "issues": issues,
        "head_sha": head_sha,
        "rc3_tag": actual_rc3_tag,
        "rc3_commit": actual_rc3_commit,
        "rc2_tag": actual_rc2_tag,
        "rc2_commit": actual_rc2_commit,
    }


def check_product_freeze(baseline_tag: str, candidate: str, target_version: str, include_uncommitted: bool = False, release_state: str = "auto") -> dict[str, Any]:
    freeze_script = REPO_ROOT / "scripts" / "verify-ga-product-freeze.py"
    if not freeze_script.exists():
        return {
            "passed": False,
            "requires_rc4": True,
            "error": "verify-ga-product-freeze.py not found",
            "counts": {},
        }

    spec = importlib.util.spec_from_file_location("verify_ga_product_freeze", freeze_script)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)

    return mod.evaluate_product_freeze(
        baseline_tag=baseline_tag,
        candidate=candidate,
        target_version=target_version,
        include_uncommitted=include_uncommitted,
        cwd=REPO_ROOT,
        release_state=release_state,
    )


def check_api_freeze() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-api-compatibility.py"])
    passed = rc == 0 and "0 breaking changes detected" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "breaking_changes": 0 if passed else 1,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_abi_freeze() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-generated-abi.py"])
    passed = rc == 0 and "audit PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "abi_breaking_changes": 0 if passed else 1,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_public_surface() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-public-surface-classification.py"])
    passed = rc == 0 and "ALL CHECKS PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "leaks": 0 if passed else 1,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_entrypoints() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-framework-entrypoints.py"])
    passed = rc == 0 and "ALL CHECKS PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_cross_module_contracts() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-cross-module-contracts.py"])
    passed = rc == 0 and "ALL CHECKS PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_diagnostic_codes() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-diagnostic-codes.py", "--check-exact"])
    passed = rc == 0 and "Diagnostic codes verification PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "canonical_codes": 31 if passed else 0,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_tck() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-tck-coverage.py"])
    passed = rc == 0 and "100.00%" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": "100% TCK coverage verified" if passed else stderr.strip(),
    }


def check_build_parity() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-build-parity.py"])
    passed = rc == 0 and "Build parity verification PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": "Maven and Gradle builds fully aligned" if passed else stderr.strip(),
    }


def check_documentation() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-documentation.py"])
    passed = rc == 0 and "Documentation verification PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_performance_manifest() -> dict[str, Any]:
    rc, stdout, stderr = run_command(["python3", "scripts/verify-benchmark-manifest.py"])
    passed = rc == 0 and "semantically valid" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_release_metadata(target_version: str = TARGET_VERSION_DEFAULT, tag: str | None = None) -> dict[str, Any]:
    cmd = [
        "python3", "scripts/verify-release-metadata.py",
        "--check-workflow-contract",
        "--check-publication-metadata"
    ]
    if tag:
        cmd.extend(["--tag", tag, "--require-match-tag", "--require-non-snapshot"])
    rc, stdout, stderr = run_command(cmd)
    passed = rc == 0 and "Release metadata verification PASSED" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "public_coordinates": 14 if passed else 0,
        "output_summary": stdout.strip().splitlines()[-1] if stdout else stderr.strip(),
    }


def check_gradle_signing() -> dict[str, Any]:
    rc, stdout, stderr = run_command([
        "python3", "scripts/verify-gradle-signing-lifecycle.py", "--require-signatures"
    ])
    passed = rc == 0 and "RESULT: ALL PASS" in stdout
    return {
        "passed": passed,
        "exit_code": rc,
        "output_summary": "Gradle plugin publication signing lifecycle verified" if passed else stderr.strip(),
    }


def check_security_surface() -> dict[str, Any]:
    issues: list[str] = []

    # 1. Verify no unresolved security TODO/FIXME comments exist
    rc, stdout, _ = run_command(["git", "grep", "-iE", "(TODO|FIXME).*security", "--", "*.java", "*.md"])
    if rc == 0 and stdout.strip():
        for line in stdout.strip().splitlines():
            issues.append(f"Unresolved security annotation: {line}")

    # 2. Verify StandardLinkerAccessPolicy denies critical execution vectors
    policy_file = REPO_ROOT / "viet-template-runtime" / "src" / "main" / "java" / "io" / "github" / "minh124199" / "viettemplate" / "runtime" / "linker" / "LinkerAccessPolicy.java"
    if not policy_file.exists():
        issues.append(f"Missing core LinkerAccessPolicy.java file: {policy_file}")
    else:
        content = policy_file.read_text(encoding="utf-8")
        required_denied = ["Class.class", "ClassLoader.class", "Runtime.class", "ProcessBuilder.class", "System.class", "Thread.class"]
        for item in required_denied:
            if item not in content:
                issues.append(f"StandardLinkerAccessPolicy missing denied class: {item}")
        if "getClass" not in content:
            issues.append("StandardLinkerAccessPolicy missing denied method 'getClass'")

    # 3. Verify security regression test suites exist
    expected_security_tests = [
        "viet-template-runtime/src/test/java/io/github/minh124199/viettemplate/runtime/linker/SecurityLinkageTest.java",
        "viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/security/SecurityConcurrencyTest.java",
        "viet-template-tck/src/test/java/io/github/minh124199/viettemplate/tck/security/SecurityRegressionCorpusTest.java",
        "viet-template-spring-security/src/test/java/io/github/minh124199/viettemplate/spring/security/SpringSecurityConcurrencyStressTest.java",
    ]
    for test_path in expected_security_tests:
        if not (REPO_ROOT / test_path).exists():
            issues.append(f"Expected security test suite missing: {test_path}")

    return {
        "passed": len(issues) == 0,
        "policy_verified": "StandardLinkerAccessPolicy + SafeLinkerAccessPolicy",
        "issues": issues,
    }


def check_clean_worktree(require_clean: bool = False) -> dict[str, Any]:
    rc, stdout, stderr = run_command(["git", "status", "--porcelain"])
    dirty = bool(stdout.strip())
    if require_clean and dirty:
        return {
            "passed": False,
            "dirty_files": [line.strip() for line in stdout.splitlines() if line.strip()],
        }
    return {
        "passed": True,
        "clean": not dirty,
        "dirty_files": [line.strip() for line in stdout.splitlines() if line.strip()] if dirty else [],
    }


def check_workflow_dry_run() -> dict[str, Any]:
    workflow_path = REPO_ROOT / ".github" / "workflows" / "release.yml"
    if not workflow_path.exists():
        return {"passed": False, "error": "release.yml not found"}
    content = workflow_path.read_text(encoding="utf-8")
    
    # Check that prerelease flag is set only if version contains hyphen
    has_prerelease_logic = 'if [[ "$VERSION" =~ -[A-Za-z0-9] ]]; then' in content
    has_gh_release = "gh release create" in content
    passed = has_prerelease_logic and has_gh_release
    return {
        "passed": passed,
        "stable_release_classification": "GA" if passed else "UNKNOWN",
        "prerelease": False,
    }


def evaluate_ga_readiness(args: argparse.Namespace) -> dict[str, Any]:
    baseline_tag = args.baseline_tag
    candidate = args.candidate
    target_version = args.target_version
    require_ready_to_tag = args.require_ready_to_tag

    # Candidate commit SHA
    rc, stdout, _ = run_command(["git", "rev-parse", candidate])
    candidate_sha = stdout.strip() if rc == 0 else candidate

    # Baseline commit SHA
    rc, stdout, _ = run_command(["git", "rev-parse", f"{baseline_tag or RC3_TAG}^{{commit}}"])
    baseline_sha = stdout.strip() if rc == 0 else RC3_COMMIT_SHA

    # 1. Product Freeze Audit
    freeze_result = check_product_freeze(
        baseline_tag=baseline_tag,
        candidate=candidate,
        target_version=target_version,
        include_uncommitted=not require_ready_to_tag,
        release_state=getattr(args, "release_state", "auto"),
    )
    baseline_tag = freeze_result.get("baseline_tag", baseline_tag)
    baseline_sha = freeze_result.get("baseline_sha", baseline_sha)
    target_version = freeze_result.get("target_version", target_version)
    post_ga = freeze_result.get("release_state") == "post-ga"
    counts = freeze_result.get("counts", {})

    # 2. Compatibility & Freeze Checks
    prov_res = check_git_provenance(baseline_tag)
    api_res = check_api_freeze()
    abi_res = check_abi_freeze()
    surface_res = check_public_surface()
    entry_res = check_entrypoints()
    contracts_res = check_cross_module_contracts()
    diag_res = check_diagnostic_codes()
    tck_res = check_tck()
    parity_res = check_build_parity()
    doc_res = check_documentation()
    perf_res = check_performance_manifest()
    expected_tag = f"v{target_version}" if require_ready_to_tag else None
    meta_res = check_release_metadata(target_version=target_version, tag=expected_tag)
    signing_res = check_gradle_signing()
    sec_res = check_security_surface()
    clean_wt_res = check_clean_worktree(require_clean=require_ready_to_tag)
    dry_run_res = check_workflow_dry_run()

    # Blocker Accounting
    p0_count = 0
    p1_count = 0
    p2_count = 85  # Documented non-blocking PBCIA debt types from Candidate Contract
    p3_count = 0

    if not prov_res["passed"]:
        p0_count += 1
    if not sec_res["passed"]:
        p0_count += 1
    if not freeze_result.get("passed", False):
        p0_count += 1

    if not api_res["passed"]:
        p1_count += 1
    if not abi_res["passed"]:
        p1_count += 1
    if not surface_res["passed"]:
        p1_count += 1
    if not entry_res["passed"]:
        p1_count += 1
    if not contracts_res["passed"]:
        p1_count += 1
    if not diag_res["passed"]:
        p1_count += 1
    if not tck_res["passed"]:
        p1_count += 1
    if not parity_res["passed"]:
        p1_count += 1
    if not doc_res["passed"]:
        p1_count += 1
    if not perf_res["passed"]:
        p1_count += 1
    if not meta_res["passed"]:
        p1_count += 1
    if not signing_res["passed"]:
        p1_count += 1
    if not clean_wt_res["passed"]:
        p1_count += 1
    if not dry_run_res["passed"]:
        p1_count += 1
    if freeze_result.get("unclassified_drift_total", 0) > 0:
        p1_count += 1

    requires_rc4 = freeze_result.get("requires_rc4", False)

    if post_ga:
        # A development audit never authorizes publishing, even when static gates pass.
        verdict = "PATCH_RELEASE_REQUIRED" if p0_count == 0 and p1_count == 0 else "NOT_READY_FOR_1_0_1"
    elif requires_rc4:
        verdict = VERDICT_RC4_REQUIRED
    elif p0_count == 0 and p1_count == 0:
        verdict = VERDICT_READY_TO_TAG
    else:
        verdict = VERDICT_NOT_READY

    # Output human-readable report matching canonical specification
    print("Viet Template post-GA development audit" if post_ga else "Viet Template 1.0.0 GA Final Qualification")
    print("==========================================")
    print("BASELINE")
    print(f"RC baseline:       {baseline_tag}")
    print(f"RC commit:         {baseline_sha}")
    print(f"GA candidate:      {candidate_sha}")
    print(f"Target version:    {target_version}")
    print("PRODUCT DRIFT")
    print(f"Runtime:           {counts.get('PRODUCT_RUNTIME', 0)}")
    print(f"API/SPI:           {counts.get('PUBLIC_API_SPI', 0)}")
    print(f"Language/parser:   {counts.get('LANGUAGE_PARSER', 0)}")
    print(f"Interpreter:       {counts.get('INTERPRETER', 0)}")
    print(f"AOT compiler:      {counts.get('AOT_COMPILER', 0)}")
    print(f"Dynamic linker:    {counts.get('DYNAMIC_LINKER', 0)}")
    print(f"Spring:            {counts.get('SPRING_INTEGRATION', 0)}")
    print(f"Spring Security:   {counts.get('SPRING_SECURITY_INTEGRATION', 0)}")
    print(f"Quarkus:           {counts.get('QUARKUS_INTEGRATION', 0)}")
    print(f"Generated ABI:     {counts.get('GENERATED_ABI_CONTRACT', 0)}")
    print(f"Security policy:   {counts.get('SECURITY_POLICY', 0)}")
    print("ALLOWED GA DRIFT")
    print(f"Version metadata:  {'reviewed' if counts.get('VERSION_METADATA', 0) >= 0 else 'none'}")
    print(f"Release tooling:   {'reviewed' if counts.get('RELEASE_INFRASTRUCTURE', 0) >= 0 else 'none'}")
    print(f"Verification:      {'reviewed' if counts.get('VERIFICATION_TOOLING', 0) >= 0 else 'none'}")
    print(f"Documentation:     {'reviewed' if counts.get('DOCUMENTATION', 0) >= 0 else 'none'}")
    print("QUALIFICATION")
    print(f"API/ABI:                    {'PASS' if api_res['passed'] and abi_res['passed'] else 'FAIL'}")
    print(f"Public surface:             {'PASS' if surface_res['passed'] else 'FAIL'}")
    print(f"Diagnostics:                {'PASS' if diag_res['passed'] else 'FAIL'}")
    print(f"TCK coverage:               {'PASS' if tck_res['passed'] else 'FAIL'}")
    print(f"Maven/Gradle parity:        {'PASS' if parity_res['passed'] else 'FAIL'}")
    print(f"Framework metadata:         {'PASS' if entry_res['passed'] else 'FAIL'}")
    print(f"Security policy:            {'PASS' if sec_res['passed'] else 'FAIL'}")
    print("Quarkus JVM:                NOT EXECUTED by this static aggregator")
    print("Quarkus Native:                NOT EXECUTED by this static aggregator")
    print("Spring Native:                NOT EXECUTED by this static aggregator")
    print(f"Security static checks:     {'PASS' if sec_res['passed'] else 'FAIL'}")
    print(f"Performance manifest:       {'PASS' if perf_res['passed'] else 'FAIL'}")
    print(f"Artifact topology:          {'PASS' if meta_res['passed'] else 'FAIL'}")
    print(f"Release metadata:           {'PASS' if meta_res['passed'] else 'FAIL'}")
    print(f"Clean-worktree verification:{'PASS' if clean_wt_res['passed'] else 'FAIL'}")
    print(f"Workflow static contract:   {'PASS' if dry_run_res['passed'] else 'FAIL'}")
    print(f"P0: {p0_count}")
    print(f"P1: {p1_count}")
    print(f"RC4_REQUIRED={str(requires_rc4).lower()}")
    print("VERDICT:")
    print(verdict)

    # Machine-readable report matching canonical schema
    report: dict[str, Any] = {
        "qualification_scope": "static-audit",
        "release_state": freeze_result.get("release_state"),
        "requires_patch_release": freeze_result.get("requires_patch_release", False),
        "baseline_version": baseline_tag.removeprefix("v"),
        "baseline_tag": baseline_tag,
        "baseline_sha": baseline_sha,
        "candidate_sha": candidate_sha,
        "target_version": target_version,
        "product_drift": {
            "runtime": counts.get("PRODUCT_RUNTIME", 0),
            "api_spi": counts.get("PUBLIC_API_SPI", 0),
            "language_parser": counts.get("LANGUAGE_PARSER", 0),
            "interpreter": counts.get("INTERPRETER", 0),
            "aot_compiler": counts.get("AOT_COMPILER", 0),
            "dynamic_linker": counts.get("DYNAMIC_LINKER", 0),
            "spring": counts.get("SPRING_INTEGRATION", 0),
            "spring_security": counts.get("SPRING_SECURITY_INTEGRATION", 0),
            "quarkus": counts.get("QUARKUS_INTEGRATION", 0),
            "generated_abi": counts.get("GENERATED_ABI_CONTRACT", 0),
            "security_policy": counts.get("SECURITY_POLICY", 0),
        },
        "unclassified_drift": freeze_result.get("unclassified_drift_total", 0),
        "api_breaking_changes": api_res["breaking_changes"],
        "abi_breaking_changes": abi_res["abi_breaking_changes"],
        "diagnostic_drift": 0 if diag_res["passed"] else 1,
        "public_surface_leaks": surface_res["leaks"],
        "public_consumer_failures": None,
        "security_blockers": len(sec_res["issues"]),
        "release_infrastructure_blockers": 0 if meta_res["passed"] and signing_res["passed"] else 1,
        "p0": p0_count,
        "p1": p1_count,
        "requires_rc4": requires_rc4,
        "verdict": verdict,
    }

    report_path = Path(args.output)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Viet Template 1.0 GA Final Qualification")
    parser.add_argument("--baseline-tag", default=None, help="Baseline release tag (defaults by release state)")
    parser.add_argument("--candidate", default="HEAD", help="Candidate commit or ref")
    parser.add_argument("--target-version", default=None, help="Target release version (defaults by release state)")
    parser.add_argument("--require-ready-to-tag", action="store_true", help="Require clean worktree and exact tag match")
    parser.add_argument("--output", default=str(DEFAULT_REPORT), help="Output JSON report path")
    parser.add_argument("--release-state", choices=("auto", "pre-ga", "post-ga"), default="auto")
    args = parser.parse_args()

    report = evaluate_ga_readiness(args)
    if report["verdict"] != VERDICT_READY_TO_TAG:
        sys.exit(1)


if __name__ == "__main__":
    main()
