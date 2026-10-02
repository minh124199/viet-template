#!/usr/bin/env python3
"""Fail-closed, non-publishing qualification for the exact Viet Template 1.1.0 candidate."""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION = "1.1.0"
BASELINE = "v1.0.1"
METADATA_SPEC = importlib.util.spec_from_file_location(
    "release_metadata", ROOT / "scripts" / "verify-release-metadata.py"
)
METADATA = importlib.util.module_from_spec(METADATA_SPEC)
METADATA_SPEC.loader.exec_module(METADATA)
NATIVE_JOBS = (
    "GraalVM Native Image & Spring AOT (boot3, JDK 25)",
    "GraalVM Native Image & Spring AOT (boot4, JDK 25)",
    "Quarkus Security & REST CSRF Native Image (JDK 25)",
)


def run(args: list[str], cwd: Path = ROOT, timeout: int = 7200) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout, check=False)


def git(*args: str) -> str:
    result = run(["git", *args])
    if result.returncode:
        raise RuntimeError(result.stdout.strip() or f"git {' '.join(args)} failed")
    return result.stdout.strip()


def verify_native(run_id: str, candidate_sha: str) -> dict:
    result = run(["gh", "run", "view", run_id, "--repo", "minh124199/viet-template",
                  "--json", "workflowName,headSha,status,conclusion,jobs"], timeout=180)
    if result.returncode:
        raise RuntimeError(f"Cannot inspect native workflow run {run_id}: {result.stdout[-3000:]}")
    data = json.loads(result.stdout)
    if (data.get("workflowName") != "Native Image Verification" or
            data.get("headSha") != candidate_sha or data.get("status") != "completed" or
            data.get("conclusion") != "success"):
        raise RuntimeError(f"Native workflow {run_id} is not a successful completed run on {candidate_sha}")
    jobs = {job.get("name"): job for job in data.get("jobs", [])}
    missing = [name for name in NATIVE_JOBS if name not in jobs]
    failed = [name for name in NATIVE_JOBS if name in jobs and
              (jobs[name].get("status") != "completed" or jobs[name].get("conclusion") != "success")]
    if missing or failed:
        raise RuntimeError(f"Native required jobs missing={missing}, unsuccessful={failed}")
    return {"runId": run_id, "sha": data["headSha"], "status": "PASS",
            "jobs": {name: "PASS" for name in NATIVE_JOBS}}


def test_counts(paths: list[Path]) -> dict[str, int]:
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    counts["reportFiles"] = 0
    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError):
            continue
        counts["reportFiles"] += 1
        for key in ("tests", "failures", "errors", "skipped"):
            counts[key] += int(root.attrib.get(key, 0))
    return counts


def snapshot_consumer_references() -> list[str]:
    matches = []
    candidates = list(ROOT.glob("**/pom.xml")) + list(ROOT.glob("**/*.gradle")) + list(ROOT.glob("**/*.gradle.kts"))
    for path in candidates:
        if any(part in {".git", "target", "build", "node_modules", ".gradle"} for part in path.parts):
            continue
        if "1.1.0-SNAPSHOT" in path.read_text(encoding="utf-8", errors="replace"):
            matches.append(str(path.relative_to(ROOT)))
    return sorted(matches)


def artifact_hygiene_errors() -> list[str]:
    errors = []
    forbidden = (b"1.1.0-SNAPSHOT", b"/home/", b"/tmp/", b"C:\\Users\\",
                 b"-----BEGIN PRIVATE KEY", b"-----BEGIN PGP PRIVATE KEY BLOCK",
                 b"SONATYPE_TOKEN", b"NPM_TOKEN", b"MARKETPLACE_TOKEN")
    for module in METADATA.PUBLISHED_MODULES:
        for jar in sorted((ROOT / module / "target").glob("*.jar")):
            try:
                with zipfile.ZipFile(jar) as archive:
                    names = archive.namelist()
                    for name in names:
                        content = archive.read(name)
                        if any(marker in content for marker in forbidden):
                            errors.append(f"Release hygiene marker found in {jar.relative_to(ROOT)}:{name}")
                    manifest_name = next((name for name in names if name.upper() == "META-INF/MANIFEST.MF"), None)
                    if manifest_name:
                        manifest = archive.read(manifest_name).decode("utf-8", errors="replace")
                        version_match = re.search(r"(?im)^Implementation-Version:\s*(\S+)", manifest)
                        if version_match and version_match.group(1) != VERSION:
                            errors.append(f"Unexpected Implementation-Version in {jar.relative_to(ROOT)}")
                        if re.search(r"(?im)^Automatic-Module-Name:", manifest):
                            errors.append(f"Unexpected Automatic-Module-Name policy change in {jar.relative_to(ROOT)}")
            except (zipfile.BadZipFile, OSError) as exc:
                errors.append(f"Cannot inspect release JAR {jar.relative_to(ROOT)}: {exc}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-ci-run-id", required=True, help="Successful exact-SHA Native Image Verification run ID")
    parser.add_argument("--report", type=Path, default=Path("build/reports/1.1-release-candidate.json"))
    args = parser.parse_args()

    errors: list[str] = []
    checks: list[dict] = []
    preflight_checks: list[dict] = []

    def preflight(name: str, passed: bool, detail: str) -> None:
        preflight_checks.append({"name": name, "status": "PASS" if passed else "FAIL", "detail": detail})
        if not passed:
            errors.append(detail)

    candidate_sha = git("rev-parse", "HEAD")
    baseline_sha = git("rev-parse", f"refs/tags/{BASELINE}^{{commit}}")
    branch = git("branch", "--show-current")
    initial_dirty = bool(git("status", "--porcelain", "--untracked-files=all"))
    preflight("branch", branch == "release/1.1.0", f"Expected release/1.1.0 worktree, found {branch or 'detached'}")
    preflight("initialCleanTree", not initial_dirty, "Candidate worktree must be clean before qualification")
    ancestor = run(["git", "merge-base", "--is-ancestor", baseline_sha, candidate_sha])
    preflight("baselineAncestry", ancestor.returncode == 0,
              f"Immutable compatibility baseline {BASELINE} is not an ancestor of the candidate")

    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
    gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    maven_version_ok = re.search(r"<artifactId>viet-template-parent</artifactId>\s*<version>1\.1\.0</version>", pom) is not None
    gradle_version_ok = 'version = "1.1.0"' in gradle
    preflight("mavenProjectVersion", maven_version_ok, "Root Maven version is not 1.1.0")
    preflight("gradleProjectVersion", gradle_version_ok, "Gradle project version is not 1.1.0")
    snapshot_consumers = snapshot_consumer_references()
    preflight("snapshotConsumerCoordinates", not snapshot_consumers,
              "Stale 1.1.0-SNAPSHOT release consumer coordinates remain: " + ", ".join(snapshot_consumers))
    native = None
    try:
        native = verify_native(args.native_ci_run_id, candidate_sha)
    except Exception as exc:  # fail closed and retain the explanation in the report
        errors.append(str(exc))
    preflight_checks.append({"name": "exactShaNative", "status": "PASS" if native else "FAIL",
                             "detail": "Native Image Verification must pass on candidate SHA" if not native else native})

    commands = [
        ("unitTests", ["python3", "-m", "unittest", "discover", "-s", "scripts/tests"]),
        ("documentation", ["python3", "scripts/verify-documentation.py"]),
        ("mavenVerify", ["./mvnw", "clean", "verify", "-B"]),
        ("mavenSpotless", ["./mvnw", "spotless:check", "-B"]),
        ("gradleCheck", ["./gradlew", "clean", "check", "--no-daemon"]),
        ("gradleSpotless", ["./gradlew", "spotlessCheck", "--no-daemon"]),
        ("gradleProjectVersion", ["./gradlew", "properties", "--no-daemon"]),
        ("apiCompatibility", ["python3", "scripts/verify-api-compatibility.py"]),
        ("publicSurface", ["python3", "scripts/verify-public-surface-classification.py"]),
        ("generatedAbi", ["python3", "scripts/verify-generated-abi.py"]),
        ("historicalCompatibility", ["python3", "scripts/verify-compatibility-baseline.py"]),
        ("crossModuleContracts", ["python3", "scripts/verify-cross-module-contracts.py"]),
        ("diagnosticCodes", ["python3", "scripts/verify-diagnostic-codes.py"]),
        ("frameworkEntrypoints", ["python3", "scripts/verify-framework-entrypoints.py"]),
        ("exceptionSemantics", ["python3", "scripts/verify-exception-semantics.py"]),
        ("tckCoverage", ["python3", "scripts/verify-tck-coverage.py"]),
        ("buildParity", ["python3", "scripts/verify-build-parity.py"]),
        ("tck", ["./gradlew", ":viet-template-tck:test", "--no-daemon"]),
        ("releaseGatesAndCleanRoomConsumers", ["bash", "scripts/verify-m18-release-gates.sh", "--clean-room"]),
        ("aotToolingParity", ["bash", "scripts/verify-aot-tooling-parity.sh"]),
        ("spring", ["bash", "scripts/verify-spring-integration-parity.sh"]),
        ("springSecurity", ["bash", "scripts/verify-spring-security-parity.sh"]),
        ("springSecurity7", ["bash", "scripts/verify-spring-security7-integration.sh"]),
        ("springSecurityCompatibility", ["python3", "scripts/verify-spring-security-compatibility.py"]),
        ("quarkusDevMode", ["bash", "scripts/verify-quarkus-dev-mode.sh"]),
        ("quarkusIntegration", ["bash", "scripts/verify-quarkus-integration.sh"]),
        ("devtools", ["bash", "scripts/verify-devtools-restart-integration.sh"]),
        ("vscodeGovernance", ["python3", "scripts/verify-vscode-extension.py"]),
        ("vscodeInstall", ["npm", "ci"], ROOT / "editors/vscode"),
        ("vscodeTests", ["npm", "test"], ROOT / "editors/vscode"),
        ("vscodePackage", ["npm", "run", "package"], ROOT / "editors/vscode"),
        ("intellijGovernance", ["python3", "scripts/verify-intellij-plugin.py"]),
        ("intellijTests", ["./gradlew", "-p", "editors/intellij", "test", "--no-daemon"]),
        ("intellijPackage", ["./gradlew", "-p", "editors/intellij", "buildPlugin", "--no-daemon"]),
        ("releaseMetadata", ["python3", "scripts/verify-release-metadata.py", "--tag", "v1.1.0",
                              "--require-release", "--require-match-tag", "--check-workflow-contract",
                              "--check-publication-metadata", "--check-effective-pom", "--check-signing-lifecycle"]),
        ("mavenBundleAssembly", ["./mvnw", "clean", "package", "-P", "release", "-Dgpg.skip=true", "-DskipTests", "-B"]),
        ("gradleBundleAssembly", ["./gradlew", "assemble", "generatePomFileForMavenJavaPublication",
                                   "generatePomFileForVietTemplatePluginMarkerMavenPublication", "--no-daemon"]),
        ("artifactHygiene", []),
        ("bundleValidation", ["python3", "scripts/validate-release-bundle.py", "--build-tool", "both", "--version", VERSION]),
        ("releaseSimulation", ["python3", "scripts/simulate-release.py", "--version", VERSION,
                                "--tag", "v1.1.0", "--build-tool", "both"]),
    ]

    test_summary: dict[str, object] = {}
    for item in commands:
        name, command, *cwd_arg = item
        cwd = cwd_arg[0] if cwd_arg else ROOT
        try:
            if name == "artifactHygiene":
                hygiene_errors = artifact_hygiene_errors()
                result = subprocess.CompletedProcess(command, int(bool(hygiene_errors)),
                                                      "\n".join(hygiene_errors) if hygiene_errors else
                                                      "Public Maven module JARs pass local path, snapshot, secret, and manifest checks.\n", "")
            else:
                result = run(command, cwd=cwd)
            checks.append({"name": name, "status": "PASS" if result.returncode == 0 else "FAIL",
                           "exitCode": result.returncode, "command": command,
                           "outputTail": result.stdout[-2500:]})
            if name == "mavenVerify":
                reports = sorted(ROOT.glob("*/target/surefire-reports/TEST-*.xml"))
                velocity_reports = [path for path in reports if "VelocityDifferentialTckTest" in path.name]
                test_summary["maven"] = test_counts(reports)
                test_summary["velocityScenarios"] = sum(test_counts([path])["tests"] for path in velocity_reports)
            elif name == "gradleCheck":
                reports = sorted(ROOT.glob("*/build/test-results/test/TEST-*.xml"))
                test_summary["gradle"] = test_counts(reports)
                tck_module = ROOT / "viet-template-tck"
                tck_reports = [path for path in reports if tck_module in path.parents]
                test_summary["gradleTck"] = test_counts(tck_reports)
            elif name == "tck":
                reports = sorted((ROOT / "viet-template-tck/build/test-results/test").glob("TEST-*.xml"))
                test_summary["gradleTck"] = test_counts(reports)
            if result.returncode:
                errors.append(f"{name} failed with exit code {result.returncode}")
        except Exception as exc:
            checks.append({"name": name, "status": "FAIL", "command": command, "error": str(exc)})
            errors.append(f"{name} could not run: {exc}")

    final_dirty = bool(git("status", "--porcelain", "--untracked-files=all"))
    preflight("finalCleanTree", not final_dirty,
              "Tracked or non-ignored untracked source changes appeared during candidate qualification")
    simulation = next((check for check in checks if check["name"] == "releaseSimulation"), {})
    next_version_match = re.search(r"Next development:\s*(\S+)", simulation.get("outputTail", ""))
    next_development_version = next_version_match.group(1) if next_version_match else None
    if next_development_version != "1.1.1-SNAPSHOT":
        errors.append("Release simulation did not prove next development version 1.1.1-SNAPSHOT")
    published_modules, internal_modules = METADATA.get_reactor_modules(ROOT)
    report = {
        "schemaVersion": 1, "targetVersion": VERSION, "baselineRelease": BASELINE, "baselineSha": baseline_sha,
        "candidateSha": candidate_sha, "branch": branch, "workingTreeClean": not initial_dirty and not final_dirty,
        "native": native, "checks": preflight_checks + checks, "checkCount": len(preflight_checks) + len(checks),
        "snapshotConsumerReferences": snapshot_consumers,
        "testSummary": test_summary,
        "publicationTopology": {"publicCoordinates": METADATA.get_public_coordinates(ROOT),
                                 "publicCount": len(METADATA.get_public_coordinates(ROOT)),
                                 "internalOnlyModules": internal_modules},
        "nextDevelopmentVersion": next_development_version,
        "blockers": errors, "warnings": [], "publicationPerformed": False,
        "verdict": "READY_TO_MERGE_1_1_0_RELEASE_PREPARATION" if not errors else "BLOCKED",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
    }
    report_path = args.report if args.report.is_absolute() else ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("targetVersion", "baselineRelease", "candidateSha",
                                              "workingTreeClean", "native", "checkCount", "blockers", "verdict",
                                              "publicationPerformed", "report")
                      if k != "report"}, indent=2))
    print(f"Report: {report_path}")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
