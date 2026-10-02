#!/usr/bin/env python3
"""Run the release-preparation qualification suite for the 1.1 development line.

This is deliberately separate from verify-ga-readiness.py and
verify-1.0-readiness.py: those scripts audit immutable 1.0 release contracts.
This verifier reports unavailable checks as UNAVAILABLE and only returns a
ready verdict when every mandatory command, candidate invariant, and clean-tree
check succeeds. It performs no release or publication action.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
TARGET_VERSION = "1.1.0"
DEVELOPMENT_VERSION = "1.1.0-SNAPSHOT"
BASELINE_RELEASE = "v1.0.1"
READY = "READY_FOR_1_1_RELEASE_PREPARATION"


def run(name: str, argv: list[str], cwd: Path = ROOT) -> dict[str, Any]:
    """Run one mandatory check and retain compact, reproducible evidence."""
    try:
        result = subprocess.run(argv, cwd=cwd, text=True, capture_output=True, check=False)
    except FileNotFoundError as exc:
        return {"status": "UNAVAILABLE", "command": shlex.join(argv), "exitCode": None,
                "detail": f"required executable is unavailable: {exc.filename}"}
    output = (result.stdout + result.stderr).strip()
    markers = [line.strip() for line in output.splitlines()
               if re.search(r"\b(FAIL|FAILED|ERROR|BUILD FAILURE|FAILED TESTS)\b", line, re.IGNORECASE)]
    native_tool_missing = re.search(
        r"(?:native-image|GraalVM Native Image).{0,100}(?:not found|No such file|unavailable|not installed|could not be found)|"
        r"(?:not found|No such file|unavailable|not installed).{0,100}(?:native-image|GraalVM Native Image)",
        output, re.IGNORECASE | re.DOTALL,
    ) is not None
    status = ("PASS" if result.returncode == 0 else
              "UNAVAILABLE" if native_tool_missing else "FAIL")
    result = {
        "status": status,
        "command": shlex.join(argv),
        "exitCode": result.returncode,
        "diagnosticMarkers": markers[-20:],
    }
    if native_tool_missing:
        result["detail"] = "GraalVM native-image is unavailable in this environment"
    if name in ("maven", "tck"):
        if name == "tck":
            report_dir = cwd / "viet-template-tck/build/test-results/test"
            reports = sorted(report_dir.glob("TEST-*.xml")) if report_dir.is_dir() else []
        else:
            reports = sorted(cwd.glob("*/target/surefire-reports/TEST-*.xml"))
        counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        for report_path in reports:
            try:
                root = ET.parse(report_path).getroot()
            except ET.ParseError:
                continue
            for key in counts:
                counts[key] += int(root.attrib.get(key, 0))
        result["testSummary"] = {**counts, "reportFiles": len(reports)}
        velocity_reports = [p for p in reports if "VelocityDifferentialTckTest" in p.name]
        if velocity_reports:
            result["velocityScenarios"] = sum(int(ET.parse(p).getroot().attrib.get("tests", 0))
                                               for p in velocity_reports)
    return result


def validate_native_ci_payload(payload: dict[str, Any], candidate_sha: str) -> dict[str, Any]:
    """Accept native CI only when the complete workflow passed on this exact SHA."""
    jobs = payload.get("jobs", [])
    matched = {}
    for job in jobs:
        name = str(job.get("name", ""))
        if "Spring AOT" in name and "boot3" in name.lower():
            matched["springNativeBoot3"] = job
        elif "Spring AOT" in name and "boot4" in name.lower():
            matched["springNativeBoot4"] = job
        elif "Quarkus Security & REST CSRF Native Image" in name:
            matched["quarkusNative"] = job
    expected = {"springNativeBoot3", "springNativeBoot4", "quarkusNative"}
    valid = (
        payload.get("workflowName") == "Native Image Verification"
        and payload.get("status") == "completed"
        and payload.get("conclusion") == "success"
        and payload.get("headSha") == candidate_sha
        and expected.issubset(matched)
        and all(matched[name].get("conclusion") == "success" for name in expected)
    )
    return {
        "status": "PASS" if valid else "FAIL",
        "headSha": payload.get("headSha"),
        "workflowName": payload.get("workflowName"),
        "url": payload.get("url"),
        "jobs": {name: matched[name].get("conclusion") for name in sorted(matched)},
        "detail": [] if valid else ["native CI evidence must be complete, successful, and match the candidate SHA"],
    }


def load_native_ci_evidence(run_ids: list[str], candidate_sha: str) -> dict[str, Any]:
    gh = shutil.which("gh")
    if not run_ids:
        return {"status": "UNAVAILABLE", "detail": "no native CI run id supplied"}
    if not gh:
        return {"status": "UNAVAILABLE", "detail": "GitHub CLI is unavailable"}
    for run_id in run_ids:
        proc = subprocess.run(
            [gh, "run", "view", run_id, "--json", "workflowName,status,conclusion,headSha,url,jobs"],
            cwd=ROOT, text=True, capture_output=True, check=False,
        )
        if proc.returncode != 0:
            continue
        try:
            payload = json.loads(proc.stdout)
        except json.JSONDecodeError:
            continue
        evidence = validate_native_ci_payload(payload, candidate_sha)
        if evidence["status"] == "PASS":
            evidence["runId"] = run_id
            return evidence
    return {"status": "FAIL", "detail": ["no supplied native CI run fully passed for this candidate SHA"]}


def git_value(*args: str) -> str:
    proc = subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True, check=False)
    return proc.stdout.strip() if proc.returncode == 0 else ""


def version_invariants() -> dict[str, Any]:
    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    match = re.search(r"<version>\s*([^<]+)\s*</version>", pom)
    version = match.group(1).strip() if match else None
    tag = git_value("rev-parse", "--verify", BASELINE_RELEASE)
    return {
        "status": "PASS" if version == DEVELOPMENT_VERSION and tag else "FAIL",
        "developmentVersion": version,
        "expectedDevelopmentVersion": DEVELOPMENT_VERSION,
        "baselineTag": BASELINE_RELEASE,
        "baselineTagObject": tag or None,
        "detail": [] if version == DEVELOPMENT_VERSION and tag else [
            *([] if version == DEVELOPMENT_VERSION else [f"root POM version is {version!r}" ]),
            *([] if tag else [f"baseline tag {BASELINE_RELEASE} is unavailable"]),
        ],
    }


def checks() -> list[tuple[Any, ...]]:
    py = sys.executable
    bash = "bash"
    return [
        ("apiCompatibility", [py, "scripts/verify-api-compatibility.py"]),
        ("publicSurface", [py, "scripts/verify-public-surface-classification.py"]),
        ("generatedAbi", [py, "scripts/verify-generated-abi.py"]),
        ("historicalCompatibilityBaseline", [py, "scripts/verify-compatibility-baseline.py"]),
        ("crossModuleContracts", [py, "scripts/verify-cross-module-contracts.py"]),
        ("frameworkEntrypoints", [py, "scripts/verify-framework-entrypoints.py"]),
        ("exceptionSemantics", [py, "scripts/verify-exception-semantics.py"]),
        ("diagnosticCodes", [py, "scripts/verify-diagnostic-codes.py"]),
        ("buildParity", [py, "scripts/verify-build-parity.py"]),
        ("documentation", [py, "scripts/verify-documentation.py"]),
        ("scriptTests", [py, "-m", "unittest", "discover", "scripts/tests/"]),
        ("tck", ["./gradlew", ":viet-template-tck:test", "--no-daemon"]),
        ("maven", ["./mvnw", "clean", "verify"]),
        ("mavenFormatting", ["./mvnw", "spotless:check"]),
        ("gradle", ["./gradlew", "clean", "check", "--no-daemon"]),
        ("gradleFormatting", ["./gradlew", "spotlessCheck", "--no-daemon"]),
        ("spring", [bash, "scripts/verify-spring-integration-parity.sh"]),
        ("springSecurity", [bash, "scripts/verify-spring-security-parity.sh"]),
        ("springSecurity7", [bash, "scripts/verify-spring-security7-integration.sh"]),
        ("springSecurityCompatibility", [py, "scripts/verify-spring-security-compatibility.py"]),
        ("devtoolsLifecycle", [bash, "scripts/verify-devtools-restart-integration.sh"]),
        ("quarkus", [bash, "scripts/verify-quarkus-integration.sh"]),
        ("quarkusDevMode", [bash, "scripts/verify-quarkus-dev-mode.sh"]),
        ("springNativeBoot4", [bash, "scripts/verify-native-image-integration.sh", "boot4"]),
        ("springNativeBoot3", [bash, "scripts/verify-native-image-integration.sh", "boot3"]),
        ("vscode", [py, "scripts/verify-vscode-extension.py"]),
        ("vscodeInstall", ["npm", "ci"], ROOT / "editors" / "vscode"),
        ("vscodeTestsAndServerBundle", ["npm", "test"], ROOT / "editors" / "vscode"),
        ("vscodePackage", ["npm", "run", "package"], ROOT / "editors" / "vscode"),
        ("intellij", [py, "scripts/verify-intellij-plugin.py"]),
        ("intellijTests", ["./gradlew", "test", "--no-daemon"], ROOT / "editors" / "intellij"),
        ("intellijDistribution", ["./gradlew", "buildPlugin", "--no-daemon"], ROOT / "editors" / "intellij"),
        ("gradleSigningLifecycle", [py, "scripts/verify-gradle-signing-lifecycle.py"]),
        ("releaseMetadata", [py, "scripts/verify-release-metadata.py", "--check-workflow-contract",
                              "--check-publication-metadata", "--check-effective-pom"]),
        ("publicationTopology", [py, "scripts/verify-release-metadata.py", "--check-publication-metadata"]),
        ("releaseSimulation", [py, "scripts/simulate-release.py", "--version", "1.1.0", "--tag", "v1.1.0",
                                "--build-tool", "both"]),
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=Path("build/reports/1.1-readiness.json"),
                        help="JSON report path (default: build/reports/1.1-readiness.json)")
    parser.add_argument("--native-ci-run-id", action="append", default=[],
                        help="GitHub Native Image Verification run id for exact-SHA evidence; may be repeated")
    args = parser.parse_args()

    candidate = git_value("rev-parse", "HEAD")
    cleanliness = git_value("status", "--porcelain", "--untracked-files=all")
    report: dict[str, Any] = {
        "targetVersion": TARGET_VERSION,
        "developmentVersion": DEVELOPMENT_VERSION,
        "baselineRelease": BASELINE_RELEASE,
        "candidateSha": candidate or None,
        "workingTreeClean": not bool(cleanliness),
        "checks": {"versionConsistency": version_invariants()},
        "blockers": [],
        "warnings": [],
        "verdict": "BLOCKED",
        "publicationPerformed": False,
    }
    for name, command, *path in checks():
        report["checks"][name] = run(name, command, path[0] if path else ROOT)
    native_ci = load_native_ci_evidence(args.native_ci_run_id, candidate)
    report["checks"]["nativeCiEvidence"] = native_ci
    if native_ci.get("status") == "PASS":
        for check_name in ("springNativeBoot3", "springNativeBoot4", "quarkus"):
            local = report["checks"].get(check_name, {})
            if local.get("status") == "UNAVAILABLE":
                local["localStatus"] = "UNAVAILABLE"
                local["status"] = "PASS"
                local["evidenceSource"] = "nativeCiEvidence"
    # These subsystem conclusions are backed by the broader real executions
    # above; keep each required 1.1 surface explicit in the report.
    for name, evidence in {
        "spiCompatibility": "apiCompatibility",
        "velocityCompatibility": "tck",
        "astIrAotParity": "tck",
        "typedContracts": "maven",
        "schemaGeneration": "maven",
        "typescriptProjection": "maven",
        "lsp": "maven",
        "quarkusSecurity": "quarkus",
        "quarkusRestCsrf": "quarkus",
    }.items():
        report["checks"][name] = {
            "status": report["checks"][evidence]["status"],
            "evidenceCheck": evidence,
        }
    native_inputs = ("springNativeBoot3", "springNativeBoot4", "quarkus")
    native_input_statuses = [report["checks"][item]["status"] for item in native_inputs]
    native_status = ("PASS" if all(status == "PASS" for status in native_input_statuses)
                     else "UNAVAILABLE" if "UNAVAILABLE" in native_input_statuses else "FAIL")
    report["checks"]["nativeImage"] = {"status": native_status, "evidenceChecks": list(native_inputs)}
    report["checks"]["workingTree"] = {
        "status": "PASS" if report["workingTreeClean"] else "FAIL",
    }

    for name, result in report["checks"].items():
        if result.get("status") != "PASS":
            report["blockers"].append({"check": name, "status": result.get("status"),
                                       "detail": result.get("detail", result.get("diagnosticMarkers", []))})
    if not report["blockers"]:
        report["verdict"] = READY

    report_path = args.report if args.report.is_absolute() else ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["verdict"] == READY else 1


if __name__ == "__main__":
    raise SystemExit(main())
