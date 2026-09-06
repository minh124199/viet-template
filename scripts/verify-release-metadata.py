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

ROOT_DIR = Path(__file__).resolve().parent.parent

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

    tag = args.tag or os.environ.get("RELEASE_TAG") or os.environ.get("GITHUB_REF_NAME")
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
        pom_text = (ROOT_DIR / "pom.xml").read_text(encoding="utf-8")
        if "<central-publishing-maven-plugin.version>0.11.0</central-publishing-maven-plugin.version>" not in pom_text:
            errors.append("pom.xml does not use central-publishing-maven-plugin version 0.11.0")
        if "<autoPublish>true</autoPublish>" not in pom_text:
            errors.append("pom.xml missing <autoPublish>true</autoPublish> in central-publishing-maven-plugin")
        if "<waitUntil>published</waitUntil>" not in pom_text:
            errors.append("pom.xml missing <waitUntil>published</waitUntil> in central-publishing-maven-plugin")
        if "<publishingServerId>central</publishingServerId>" not in pom_text:
            errors.append("pom.xml missing <publishingServerId>central</publishingServerId>")

        workflow_path = ROOT_DIR / ".github" / "workflows" / "release.yml"
        if not workflow_path.exists():
            errors.append(".github/workflows/release.yml missing")
        else:
            wf_text = workflow_path.read_text(encoding="utf-8")
            if "publish-to-central" not in wf_text:
                errors.append("release.yml missing explicit 'publish-to-central' job")
            if "needs: [validate-metadata, publish-to-central]" not in wf_text and "needs: [validate-metadata, publish]" not in wf_text:
                errors.append("create-github-release job must depend on publish-to-central")
            if "cancel-in-progress: false" not in wf_text:
                errors.append("release.yml concurrency missing 'cancel-in-progress: false'")
            if "permissions:\n  contents: read" not in wf_text:
                errors.append("release.yml top-level missing 'permissions: contents: read'")
            if "permissions:\n      contents: write" not in wf_text:
                errors.append("create-github-release missing scoped 'permissions: contents: write'")
            if "./mvnw clean deploy -P release" not in wf_text:
                errors.append("publish-to-central must invoke './mvnw clean deploy -P release'")
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
