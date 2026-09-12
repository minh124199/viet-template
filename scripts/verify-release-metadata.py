#!/usr/bin/env python3
"""
scripts/verify-release-metadata.py

Validates release metadata across Gradle Kotlin DSL and Apache Maven builds:
1. Extract and compare project versions between pom.xml and build.gradle.kts.
2. Verify release tag format (e.g. v0.1.0) and verify that tag matches project version exactly.
3. Verify that live releases do NOT use -SNAPSHOT versions.
4. Output status variables for CI workflows when requested.
"""

import sys
import re
import os
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

ROOT_DIR = Path(__file__).resolve().parent.parent


def as_needs(job):
    needs = job.get("needs", [])
    return {needs} if isinstance(needs, str) else set(needs)


def combined_run(job):
    return "\n".join(step.get("run", "") for step in job.get("steps", []) if isinstance(step, dict))


def validate_workflow_contract(errors):
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")}
    plugins = pom_root.findall(".//m:plugin", ns)
    central = next(
        (p for p in plugins if p.findtext("m:artifactId", namespaces=ns) == "central-publishing-maven-plugin"),
        None,
    )
    if central is None:
        errors.append("pom.xml is missing central-publishing-maven-plugin")
    else:
        config = central.find("m:configuration", ns)
        values = {
            child.tag.split("}")[-1]: (child.text or "").strip()
            for child in (list(config) if config is not None else [])
        }
        if values.get("publishingServerId") != "central":
            errors.append("Central publisher must use publishingServerId=central")
        if values.get("autoPublish") != "true":
            errors.append("Central publisher must keep autoPublish=true")
        if values.get("waitUntil") != "validated":
            errors.append("Central publisher must use waitUntil=validated; publication polling belongs to CI")

    workflow_path = ROOT_DIR / ".github" / "workflows" / "release.yml"
    try:
        workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        errors.append(f"release workflow cannot be parsed as YAML: {exc}")
        return
    jobs = workflow.get("jobs", {}) if isinstance(workflow, dict) else {}
    required = {
        "validate-metadata",
        "verify-builds",
        "package-and-validate-bundle",
        "guard-publication",
        "publish-to-central",
        "wait-for-central-publication",
        "verify-public-artifacts",
        "central-consumer-smoke",
        "create-github-release",
    }
    missing = required - set(jobs)
    if missing:
        errors.append(f"release workflow missing jobs: {', '.join(sorted(missing))}")
        return
    if "publish-to-central" not in as_needs(jobs["wait-for-central-publication"]):
        errors.append("publication monitor must depend on publish-to-central")
    if "wait-for-central-publication" not in as_needs(jobs["verify-public-artifacts"]):
        errors.append("public artifact verification must depend on publication monitor")
    if "verify-public-artifacts" not in as_needs(jobs["central-consumer-smoke"]):
        errors.append("Central-only consumer smoke must depend on public artifact verification")
    release_needs = as_needs(jobs["create-github-release"])
    if "central-consumer-smoke" not in release_needs or "publish-to-central" in release_needs:
        errors.append("GitHub Release must depend on verified Central consumers, not directly on Maven deploy")
    release_run = combined_run(jobs["create-github-release"])
    if "gh release view" not in release_run or "gh release create" not in release_run:
        errors.append("GitHub Release finalization must be idempotent (verify existing or create absent)")
    for job_name in ("verify-public-artifacts", "central-consumer-smoke", "create-github-release"):
        condition = str(jobs[job_name].get("if", ""))
        if "always()" not in condition or ".result == 'success'" not in condition:
            errors.append(
                f"{job_name} must explicitly tolerate skipped upload ancestors while requiring its predecessor to succeed"
            )

    publish_run = combined_run(jobs["publish-to-central"])
    if "./mvnw clean deploy -P release" not in publish_run:
        errors.append("publish-to-central must invoke the Maven release deploy")
    if "gpg.passphrase" in publish_run:
        errors.append("release workflow must not pass deprecated gpg.passphrase on the command line")
    if "MAVEN_GPG_PASSPHRASE" not in str(jobs["publish-to-central"]):
        errors.append("publish-to-central must supply the supported secret-safe MAVEN_GPG_PASSPHRASE environment variable")
    if "extract-central-deployment-id.py" not in publish_run:
        errors.append("publish-to-central must strictly capture the deployment ID")
    monitor_run = combined_run(jobs["wait-for-central-publication"])
    if "check-central-deployment.py" not in monitor_run or "--timeout 7200" not in monitor_run:
        errors.append("publication monitor must use the shared checker with the 120-minute policy")
    if jobs["publish-to-central"].get("environment") != "release":
        errors.append("publish-to-central must retain the protected release environment")
    if jobs["wait-for-central-publication"].get("environment") != "release":
        errors.append("authenticated Central monitoring must retain the protected release environment")
    if workflow.get("concurrency", {}).get("cancel-in-progress") is not False:
        errors.append("release concurrency must set cancel-in-progress=false")
    if "github.run_attempt" not in workflow_path.read_text(encoding="utf-8"):
        errors.append("release workflow must prevent tag workflow reruns from redeploying")
    if "github.run_attempt" not in str(jobs["publish-to-central"]):
        errors.append("publish-to-central must independently block rerun attempts")
    if "GH_REPO" not in str(jobs["create-github-release"]):
        errors.append("GitHub Release finalization must explicitly identify the repository")
    cleanup_run = "\n".join(
        step.get("run", "")
        for step in jobs["publish-to-central"].get("steps", [])
        if isinstance(step, dict) and "Clean up" in step.get("name", "")
    )
    if "fpr:" not in cleanup_run or "--delete-secret-keys \"$fingerprint\"" not in cleanup_run:
        errors.append("GPG cleanup must delete imported secret keys by full fingerprint")

    for module in ("viet-template-tck", "viet-template-benchmarks"):
        module_text = (ROOT_DIR / module / "pom.xml").read_text(encoding="utf-8")
        for setting in ("<maven.deploy.skip>true</maven.deploy.skip>", "<skipPublishing>true</skipPublishing>"):
            if setting not in module_text:
                errors.append(f"{module} publication exclusion missing {setting}")
    gradle_text = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    for module in ("viet-template-tck", "viet-template-benchmarks"):
        if f'project.name != "{module}"' not in gradle_text:
            errors.append(f"Gradle publication exclusion missing for {module}")

def get_gradle_version():
    build_gradle = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'version\s*=\s*["\']([^"\']+)["\']', build_gradle)
    if not match:
        raise ValueError("Could not find 'version' in build.gradle.kts")
    return match.group(1).strip()

def get_maven_version():
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    if ver_elem is None or not ver_elem.text:
        raise ValueError("Could not find '<version>' in root pom.xml")
    return ver_elem.text.strip()

def main():
    parser = argparse.ArgumentParser(description="Verify release version and tag consistency.")
    parser.add_argument("--tag", help="Release tag being evaluated (e.g. v0.1.0)")
    parser.add_argument("--require-non-snapshot", action="store_true", help="Fail if version is a SNAPSHOT")
    parser.add_argument("--require-release", action="store_true", help="Fail if version is a SNAPSHOT (enforces release version)")
    parser.add_argument("--require-match-tag", action="store_true", help="Fail if tag is not provided or does not match version")
    parser.add_argument("--check-workflow-contract", action="store_true", help="Statically validate release workflow and pom configuration contract")
    args = parser.parse_args()

    print("=== Viet Template Release Metadata Verification ===")
    errors = []

    gradle_version = get_gradle_version()
    maven_version = get_maven_version()

    print(f"[CHECK] Gradle version: {gradle_version}")
    print(f"[CHECK] Maven version:  {maven_version}")

    if gradle_version != maven_version:
        errors.append(f"Version mismatch: Gradle has '{gradle_version}' while Maven has '{maven_version}'")
    else:
        print(f"[PASS] Both build systems agree on version '{maven_version}'.")

    version = maven_version
    is_snapshot = "SNAPSHOT" in version.upper()
    print(f"[CHECK] Is SNAPSHOT version: {is_snapshot}")

    if (args.require_non_snapshot or args.require_release) and is_snapshot:
        errors.append(f"Release requirement failed: version '{version}' is a SNAPSHOT version. Live releases require a release version (e.g. 0.1.0).")

    if args.tag is not None:
        tag = args.tag
    elif "RELEASE_TAG" in os.environ:
        # An explicit empty value means a branch-based dry run, not "fall back to branch name".
        tag = os.environ["RELEASE_TAG"]
    elif os.environ.get("GITHUB_REF_TYPE") == "tag":
        tag = os.environ.get("GITHUB_REF_NAME")
    else:
        tag = None
    if tag and tag.startswith("refs/tags/"):
        tag = tag.replace("refs/tags/", "")

    if tag:
        print(f"[CHECK] Evaluating tag: '{tag}'")
        tag_pattern = r"^v(\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?)$"
        tag_match = re.match(tag_pattern, tag)
        if not tag_match:
            errors.append(f"Malformed release tag '{tag}'. Tags must conform to 'vX.Y.Z' format (e.g. 'v0.1.0').")
        else:
            expected_version = tag_match.group(1)
            print(f"[CHECK] Expected version from tag: '{expected_version}'")
            if version != expected_version:
                errors.append(
                    f"Tag/version mismatch: tag '{tag}' implies version '{expected_version}', "
                    f"but repository version is '{version}'."
                )
            else:
                print(f"[PASS] Tag '{tag}' exactly matches repository version '{version}'.")
    elif args.require_match_tag:
        errors.append("Release requirement failed: release tag was required but none was provided.")

    if args.check_workflow_contract:
        print("\n[CHECK] Validating release workflow contract and publishing configuration...")
        before = len(errors)
        validate_workflow_contract(errors)
        if len(errors) == before:
            print("  [PASS] Release workflow contract verified successfully.")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as f:
            f.write(f"project_version={version}\n")
            f.write(f"is_snapshot={'true' if is_snapshot else 'false'}\n")
            if tag:
                f.write(f"tag_name={tag}\n")

    if errors:
        print("\n[FAILED] Release metadata verification FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Release metadata verification PASSED.")
        sys.exit(0)

if __name__ == "__main__":
    main()
