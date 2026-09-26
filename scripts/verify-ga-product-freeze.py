#!/usr/bin/env python3
"""
scripts/verify-ga-product-freeze.py

Formal GA Product-Freeze Verifier for Viet Template 1.0 (Milestone M13).
Validates that NO forbidden product source code or contracts have changed
between the qualified baseline (v1.0.0-RC3) and the GA candidate.

Accepts:
  --baseline-tag v1.0.0-RC3
  --candidate HEAD (or commit SHA)
  --target-version 1.0.0
  --include-uncommitted (include dirty working tree in audit)

Classification Categories:
  FORBIDDEN PRODUCT DRIFT:
    - PRODUCT_RUNTIME
    - PUBLIC_API_SPI
    - LANGUAGE_PARSER
    - INTERPRETER
    - AOT_COMPILER
    - DYNAMIC_LINKER
    - SPRING_INTEGRATION
    - SPRING_SECURITY_INTEGRATION
    - QUARKUS_INTEGRATION
    - MAVEN_PLUGIN
    - GRADLE_PLUGIN
    - GENERATED_ABI_CONTRACT
    - SECURITY_POLICY

  ALLOWED GA DRIFT:
    - VERSION_METADATA
    - RELEASE_INFRASTRUCTURE
    - VERIFICATION_TOOLING
    - DOCUMENTATION
    - TEST_ONLY
    - BENCHMARK_ONLY

  FAIL-CLOSED:
    - UNCLASSIFIED_DRIFT (any unrecognized file)
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent

# Category Constants
CAT_PRODUCT_RUNTIME = "PRODUCT_RUNTIME"
CAT_PUBLIC_API_SPI = "PUBLIC_API_SPI"
CAT_LANGUAGE_PARSER = "LANGUAGE_PARSER"
CAT_INTERPRETER = "INTERPRETER"
CAT_AOT_COMPILER = "AOT_COMPILER"
CAT_DYNAMIC_LINKER = "DYNAMIC_LINKER"
CAT_SPRING_INTEGRATION = "SPRING_INTEGRATION"
CAT_SPRING_SECURITY_INTEGRATION = "SPRING_SECURITY_INTEGRATION"
CAT_QUARKUS_INTEGRATION = "QUARKUS_INTEGRATION"
CAT_MAVEN_PLUGIN = "MAVEN_PLUGIN"
CAT_GRADLE_PLUGIN = "GRADLE_PLUGIN"
CAT_GENERATED_ABI_CONTRACT = "GENERATED_ABI_CONTRACT"
CAT_SECURITY_POLICY = "SECURITY_POLICY"

CAT_VERSION_METADATA = "VERSION_METADATA"
CAT_RELEASE_INFRASTRUCTURE = "RELEASE_INFRASTRUCTURE"
CAT_VERIFICATION_TOOLING = "VERIFICATION_TOOLING"
CAT_DOCUMENTATION = "DOCUMENTATION"
CAT_TEST_ONLY = "TEST_ONLY"
CAT_BENCHMARK_ONLY = "BENCHMARK_ONLY"
CAT_UNCLASSIFIED = "UNCLASSIFIED_DRIFT"

FORBIDDEN_PRODUCT_CATEGORIES = {
    CAT_PRODUCT_RUNTIME,
    CAT_PUBLIC_API_SPI,
    CAT_LANGUAGE_PARSER,
    CAT_INTERPRETER,
    CAT_AOT_COMPILER,
    CAT_DYNAMIC_LINKER,
    CAT_SPRING_INTEGRATION,
    CAT_SPRING_SECURITY_INTEGRATION,
    CAT_QUARKUS_INTEGRATION,
    CAT_MAVEN_PLUGIN,
    CAT_GRADLE_PLUGIN,
    CAT_GENERATED_ABI_CONTRACT,
    CAT_SECURITY_POLICY,
}

ALLOWED_GA_CATEGORIES = {
    CAT_VERSION_METADATA,
    CAT_RELEASE_INFRASTRUCTURE,
    CAT_VERIFICATION_TOOLING,
    CAT_DOCUMENTATION,
    CAT_TEST_ONLY,
    CAT_BENCHMARK_ONLY,
}


def classify_file(path_str: str) -> str:
    """Explicitly classifies a relative repository file path into a strict category."""
    p = path_str.replace("\\", "/")

    # 1. Tests & Fixtures
    if "/src/test/" in p or p.startswith("integration-tests/") or p.startswith("viet-template-tck/"):
        return CAT_TEST_ONLY
    if p.startswith("scripts/tests/") or p.startswith("scripts/public-consumers/"):
        return CAT_TEST_ONLY

    # 2. Benchmarks
    if p.startswith("viet-template-benchmarks/"):
        if p.endswith("/pom.xml"):
            return CAT_VERSION_METADATA
        return CAT_BENCHMARK_ONLY
    if p.startswith("benchmark-evidence/"):
        if p.endswith("manifest.json"):
            return CAT_VERSION_METADATA
        return CAT_BENCHMARK_ONLY

    # 3. Version & Packaging Metadata
    if p == "pom.xml" or p == "build.gradle.kts" or p.endswith("/pom.xml"):
        return CAT_VERSION_METADATA
    if p == "viet-template-quarkus/src/main/resources/META-INF/quarkus-extension.properties":
        return CAT_VERSION_METADATA
    if p == "config/compatibility/1.0-candidate-contract.json":
        return CAT_VERSION_METADATA

    # 4. Documentation & Repository Meta
    if p.startswith("docs/") or p.endswith(".md") or p.endswith(".txt") and not p.startswith("config/"):
        return CAT_DOCUMENTATION
    if p in ("LICENSE", "NOTICE", ".gitignore", ".gitattributes", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd"):
        return CAT_DOCUMENTATION
    if p.startswith("gradle/") or p.startswith(".mvn/") or p.startswith("spotless/"):
        return CAT_RELEASE_INFRASTRUCTURE

    # 5. Release Infrastructure & Tooling
    if p.startswith(".github/"):
        return CAT_RELEASE_INFRASTRUCTURE
    if p == "viet-template-gradle-plugin/build.gradle.kts":
        return CAT_RELEASE_INFRASTRUCTURE
    if p.startswith("scripts/"):
        return CAT_VERIFICATION_TOOLING

    # 6. Specific Contracts & API Baselines
    if p == "config/api-baseline/generated-template-runtime-abi.txt":
        return CAT_GENERATED_ABI_CONTRACT
    if p.startswith("config/api-baseline/") or p.startswith("config/architecture/") or p.startswith("config/compatibility/"):
        return CAT_PUBLIC_API_SPI
    if p.startswith("config/tck/") or p.startswith("config/benchmark-"):
        return CAT_VERIFICATION_TOOLING

    # 7. Shipped Product Source Tree Classification
    # Linker Access & Security Policy
    if "LinkerAccessPolicy.java" in p or "SafeLinkerAccessPolicy.java" in p:
        return CAT_SECURITY_POLICY
    if p.startswith("viet-template-runtime/src/main/java/") and "/linker/" in p:
        return CAT_DYNAMIC_LINKER
    if p.startswith("viet-template-runtime/src/main/"):
        return CAT_PRODUCT_RUNTIME

    # Public API module
    if p.startswith("viet-template-api/src/main/"):
        return CAT_PUBLIC_API_SPI

    # Parser module
    if p.startswith("viet-template-language-vtl/src/main/"):
        return CAT_LANGUAGE_PARSER

    # Interpreter & AOT Compiler module
    if p.startswith("viet-template-vtl-interpreter/src/main/"):
        if "/compiler/" in p or "/aot/" in p:
            return CAT_AOT_COMPILER
        return CAT_INTERPRETER

    # Spring & Spring Boot Integration
    if p.startswith("viet-template-spring/src/main/") or p.startswith("viet-template-spring-boot-autoconfigure/src/main/"):
        return CAT_SPRING_INTEGRATION
    if p.startswith("viet-template-spring-boot-starter/src/main/"):
        return CAT_SPRING_INTEGRATION

    # Spring Security Integration
    if p.startswith("viet-template-spring-security/src/main/"):
        return CAT_SPRING_SECURITY_INTEGRATION

    # Quarkus Integration
    if p.startswith("viet-template-quarkus/src/main/") or p.startswith("viet-template-quarkus-deployment/src/main/"):
        return CAT_QUARKUS_INTEGRATION

    # Build Plugins
    if p.startswith("viet-template-maven-plugin/src/main/"):
        return CAT_MAVEN_PLUGIN
    if p.startswith("viet-template-gradle-plugin/src/main/"):
        return CAT_GRADLE_PLUGIN

    return CAT_UNCLASSIFIED


def get_diff_files(baseline_tag: str, candidate_sha: str, include_uncommitted: bool = False, cwd: Path = REPO_ROOT) -> list[str]:
    """Retrieves changed files between baseline and candidate (plus uncommitted if requested)."""
    # 1. Committed diff
    cmd = ["git", "diff", "--name-only", f"{baseline_tag}..{candidate_sha}"]
    res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=True)
    files = set(line.strip() for line in res.stdout.splitlines() if line.strip())

    if include_uncommitted:
        # Working tree diffs (tracked modified)
        res_wt = subprocess.run(["git", "diff", "--name-only", candidate_sha], cwd=cwd, capture_output=True, text=True, check=True)
        for line in res_wt.stdout.splitlines():
            if line.strip():
                files.add(line.strip())

        # Untracked files
        res_untracked = subprocess.run(
            ["git", "status", "--porcelain=v1", "-uall"],
            cwd=cwd, capture_output=True, text=True, check=True
        )
        for line in res_untracked.stdout.splitlines():
            if line.startswith("?? "):
                files.add(line[3:].strip())

    return sorted(files)


GA_TAG = "v1.0.0"
GA_TAG_OBJECT = "ac0dc481da40a718688ab800f1750d7498515c08"
GA_COMMIT = "b951021e9975b8e8103b2402dc244b32a96afaa8"


def resolve_release_state(candidate: str, release_state: str, cwd: Path) -> str:
    """Use candidate ancestry, never today's date or merely the presence of a tag."""
    if release_state not in ("auto", "pre-ga", "post-ga"):
        raise ValueError(f"Unknown release state: {release_state}")
    if release_state != "auto":
        return release_state
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", GA_COMMIT, candidate],
        cwd=cwd, capture_output=True, text=True,
    )
    if result.returncode not in (0, 1):
        raise ValueError("Cannot establish release state; fetch complete release history")
    return "post-ga" if result.returncode == 0 else "pre-ga"


def evaluate_product_freeze(
    baseline_tag: str | None = None,
    candidate: str = "HEAD",
    target_version: str | None = None,
    include_uncommitted: bool = False,
    cwd: Path = REPO_ROOT,
    release_state: str = "auto",
) -> dict[str, Any]:
    state = resolve_release_state(candidate, release_state, cwd)
    baseline_tag = baseline_tag or (GA_TAG if state == "post-ga" else "v1.0.0-RC3")
    target_version = target_version or ("1.0.1-SNAPSHOT" if state == "post-ga" else "1.0.0")
    issues: list[str] = []
    if state == "post-ga":
        for ref, expected in ((GA_TAG, GA_TAG_OBJECT), (f"{GA_TAG}^{{commit}}", GA_COMMIT)):
            result = subprocess.run(["git", "rev-parse", ref], cwd=cwd, capture_output=True, text=True)
            if result.returncode or result.stdout.strip() != expected:
                issues.append(f"Immutable GA provenance mismatch: {ref}")
        if baseline_tag != GA_TAG:
            issues.append("Post-GA baseline must be v1.0.0")
        if target_version not in ("1.0.1", "1.0.1-SNAPSHOT"):
            issues.append("Post-GA development requires the 1.0.1 patch line; 1.0.0 is immutable")
    # Resolve SHAs
    res_base = subprocess.run(["git", "rev-parse", f"{baseline_tag}^{{commit}}"], cwd=cwd, capture_output=True, text=True)
    baseline_sha = res_base.stdout.strip() if res_base.returncode == 0 else baseline_tag

    res_cand = subprocess.run(["git", "rev-parse", candidate], cwd=cwd, capture_output=True, text=True)
    candidate_sha = res_cand.stdout.strip() if res_cand.returncode == 0 else candidate

    diff_files = get_diff_files(baseline_tag, candidate_sha, include_uncommitted=include_uncommitted, cwd=cwd)

    counts: dict[str, int] = {cat: 0 for cat in (FORBIDDEN_PRODUCT_CATEGORIES | ALLOWED_GA_CATEGORIES | {CAT_UNCLASSIFIED})}
    categorized_files: dict[str, list[str]] = {cat: [] for cat in counts}

    for f in diff_files:
        cat = classify_file(f)
        counts[cat] += 1
        categorized_files[cat].append(f)

    # Compute totals
    product_drift_total = sum(counts[cat] for cat in FORBIDDEN_PRODUCT_CATEGORIES)
    unclassified_total = counts[CAT_UNCLASSIFIED]
    allowed_total = sum(counts[cat] for cat in ALLOWED_GA_CATEGORIES)

    requires_rc4 = state == "pre-ga" and product_drift_total > 0
    requires_patch = state == "post-ga" and product_drift_total > 0
    passed = not requires_rc4 and unclassified_total == 0 and not issues
    verdict = "FAIL" if issues or unclassified_total else (
        "RC4_REQUIRED" if requires_rc4 else ("PATCH_RELEASE_REQUIRED" if requires_patch else "PASS")
    )

    return {
        "passed": passed,
        "release_state": state,
        "issues": issues,
        "requires_patch_release": requires_patch,
        "baseline_tag": baseline_tag,
        "baseline_sha": baseline_sha,
        "candidate": candidate,
        "candidate_sha": candidate_sha,
        "target_version": target_version,
        "total_files_changed": len(diff_files),
        "product_drift_total": product_drift_total,
        "unclassified_drift_total": unclassified_total,
        "allowed_drift_total": allowed_total,
        "requires_rc4": requires_rc4,
        "counts": counts,
        "categorized_files": categorized_files,
        "verdict": verdict,
    }


def print_report(res: dict[str, Any]) -> None:
    c = res["counts"]
    print(f"Release policy ({res['release_state']})")
    print("=================")
    print(f"Baseline:  {res['baseline_tag']} ({res['baseline_sha'][:12]})")
    print(f"Candidate: {res['candidate_sha'][:12]}")
    print(f"Product runtime drift:             {c[CAT_PRODUCT_RUNTIME]}")
    print(f"Public API/SPI drift:              {c[CAT_PUBLIC_API_SPI]}")
    print(f"Language/parser drift:             {c[CAT_LANGUAGE_PARSER]}")
    print(f"Interpreter drift:                 {c[CAT_INTERPRETER]}")
    print(f"AOT compiler drift:                {c[CAT_AOT_COMPILER]}")
    print(f"Dynamic linker drift:              {c[CAT_DYNAMIC_LINKER]}")
    print(f"Spring integration drift:          {c[CAT_SPRING_INTEGRATION]}")
    print(f"Spring Security integration drift: {c[CAT_SPRING_SECURITY_INTEGRATION]}")
    print(f"Quarkus integration drift:         {c[CAT_QUARKUS_INTEGRATION]}")
    print(f"Maven plugin drift:                {c[CAT_MAVEN_PLUGIN]}")
    print(f"Gradle plugin drift:               {c[CAT_GRADLE_PLUGIN]}")
    print(f"Generated ABI drift:               {c[CAT_GENERATED_ABI_CONTRACT]}")
    print(f"Security policy drift:             {c[CAT_SECURITY_POLICY]}")
    print(f"Allowed version metadata drift:     {c[CAT_VERSION_METADATA]}")
    print(f"Allowed release tooling drift:      {c[CAT_RELEASE_INFRASTRUCTURE]}")
    print(f"Allowed documentation drift:        {c[CAT_DOCUMENTATION]}")
    print(f"Allowed verifier drift:             {c[CAT_VERIFICATION_TOOLING]}")
    print(f"Allowed test-only drift:            {c[CAT_TEST_ONLY]}")
    print(f"Allowed benchmark-only drift:       {c[CAT_BENCHMARK_ONLY]}")
    print(f"Unclassified drift:                 {res['unclassified_drift_total']}")
    print(f"RESULT: {res['verdict']}")
    for issue in res["issues"]:
        print(f"ERROR: {issue}")
    if not res["passed"] or res["requires_patch_release"]:
        if res["product_drift_total"]:
            print("\nPRODUCT CHANGES DETECTED:")
            for cat in FORBIDDEN_PRODUCT_CATEGORIES:
                if c[cat] > 0:
                    for f in res["categorized_files"][cat]:
                        print(f"  [{cat}] {f}")
        if res["unclassified_drift_total"] > 0:
            print("\nUNCLASSIFIED DRIFT DETECTED:")
            for f in res["categorized_files"][CAT_UNCLASSIFIED]:
                print(f"  [{CAT_UNCLASSIFIED}] {f}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify GA product freeze against RC baseline")
    parser.add_argument("--baseline-tag", default=None, help="Baseline tag (defaults by release state)")
    parser.add_argument("--candidate", default="HEAD", help="Candidate commit or ref to verify")
    parser.add_argument("--target-version", default=None, help="Target version (defaults by release state)")
    parser.add_argument("--include-uncommitted", action="store_true", help="Include working tree uncommitted changes")
    parser.add_argument("--json", action="store_true", help="Output JSON report")
    parser.add_argument("--release-state", choices=("auto", "pre-ga", "post-ga"), default="auto")
    args = parser.parse_args()

    res = evaluate_product_freeze(
        release_state=args.release_state,
        baseline_tag=args.baseline_tag,
        candidate=args.candidate,
        target_version=args.target_version,
        include_uncommitted=args.include_uncommitted,
    )

    if args.json:
        print(json.dumps(res, indent=2))
    else:
        print_report(res)

    sys.exit(0 if res["passed"] else 1)


if __name__ == "__main__":
    main()
