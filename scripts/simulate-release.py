#!/usr/bin/env python3
"""
scripts/simulate-release.py

Simulates the complete release validation pipeline for a release version (e.g. 0.1.0)
and release tag (e.g. v0.1.0) in an isolated temporary workspace.

Guarantees:
1. Validates version agreement, tag matching, and non-SNAPSHOT enforcement.
2. Assembles release bundles for all 4 production modules in Maven and Gradle.
3. Validates binary JARs, sources JARs, Javadoc JARs, and publication POMs for version 0.1.0.
4. Performs leak scans (0 test classes, 0 TCK classes, 0 Apache Velocity classes).
5. Confirms TCK publication defense-in-depth.
6. Leaves the real repository working tree 100% untouched.
7. Zero remote network publication.
"""

import sys
import os
import re
import shutil
import tempfile
import subprocess
import argparse
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent


def next_development_version(release_version):
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", release_version)
    if not match:
        raise ValueError(f"Release version must be stable numeric SemVer: {release_version}")
    major, minor, patch = (int(part) for part in match.groups())
    return f"{major}.{minor}.{patch + 1}-SNAPSHOT"


def replace_project_version(workspace, version):
    pom_file = workspace / "pom.xml"
    pom_text = pom_file.read_text(encoding="utf-8")
    updated, count = re.subn(
        r'(<groupId>io\.github\.minh124199</groupId>\s*<artifactId>viet-template-parent</artifactId>\s*<version>)[^<]+(</version>)',
        rf'\g<1>{version}\g<2>', pom_text, count=1
    )
    if count != 1:
        raise ValueError("Could not uniquely update the root Maven project version")
    pom_file.write_text(updated, encoding="utf-8")
    for child_pom in workspace.glob("*/pom.xml"):
        child_text = child_pom.read_text(encoding="utf-8")
        child_text = re.sub(
            r'(<parent>[\s\S]*?<artifactId>viet-template-parent</artifactId>\s*<version>)[^<]+(</version>)',
            rf'\g<1>{version}\g<2>', child_text
        )
        child_pom.write_text(child_text, encoding="utf-8")
    gradle_file = workspace / "build.gradle.kts"
    gradle_text, count = re.subn(r'version\s*=\s*["\'][^"\']+["\']', f'version = "{version}"',
                                 gradle_file.read_text(encoding="utf-8"), count=1)
    if count != 1:
        raise ValueError("Could not uniquely update the root Gradle project version")
    gradle_file.write_text(gradle_text, encoding="utf-8")

def ignore_patterns(path, names):
    ignored = set()
    for name in names:
        if name in [".git", "target", "build", ".gradle", ".idea", ".vscode"]:
            ignored.add(name)
        elif name.endswith((".log", ".tmp", ".swp")):
            ignored.add(name)
    return ignored

def simulate_release(release_version=None, release_tag=None, build_tool="both", next_version=None):
    source_pom = (ROOT_DIR / "pom.xml").read_text(encoding="utf-8")
    source_match = re.search(r'<version>([0-9A-Za-z.-]+)</version>', source_pom)
    if not source_match:
        raise ValueError("Could not find the source development version in root pom.xml")
    source_version = source_match.group(1)
    if release_version is None:
        if not source_version.endswith("-SNAPSHOT"):
            raise ValueError(f"Specify a release version for non-snapshot source {source_version}")
        release_version = source_version.removesuffix("-SNAPSHOT")
    release_tag = release_tag or f"v{release_version}"
    next_version = next_version or next_development_version(release_version)
    print(f"=== Viet Template Simulated Release Validation ({release_version} / {release_tag}) ===")
    print(f"[INFO] Source repository: {ROOT_DIR}")
    print(f"[INFO] Target version:    {release_version}")
    print(f"[INFO] Target tag:        {release_tag}")
    print(f"[INFO] Build tool:        {build_tool}")
    print(f"[INFO] Next development:  {next_version}")

    initial_real_pom = (ROOT_DIR / "pom.xml").read_text(encoding="utf-8")
    initial_real_bg = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")

    with tempfile.TemporaryDirectory(prefix="viet-template-release-sim-") as temp_dir:
        temp_path = Path(temp_dir)
        print(f"\n[STEP 1] Creating isolated temporary workspace at {temp_path}...")

        # Copy source files excluding build artifacts and git repo
        for item in ROOT_DIR.iterdir():
            if item.name in [".git", "target", "build", ".gradle", ".idea", ".vscode", ".system_generated"]:
                continue
            dest = temp_path / item.name
            if item.is_dir():
                shutil.copytree(item, dest, ignore=ignore_patterns)
            else:
                shutil.copy2(item, dest)

        # Make gradlew and mvnw executable
        gradlew = temp_path / "gradlew"
        mvnw = temp_path / "mvnw"
        if gradlew.exists():
            gradlew.chmod(0o755)
        if mvnw.exists():
            mvnw.chmod(0o755)

        print("[PASS] Temporary workspace assembled.")

        print(f"\n[STEP 2] Substituting version '{release_version}' in temporary workspace...")
        # Update pom.xml
        pom_file = temp_path / "pom.xml"
        pom_text = pom_file.read_text(encoding="utf-8")
        current_version_match = re.search(r'<version>([0-9A-Za-z.-]+)</version>', pom_text)
        if not current_version_match:
            raise ValueError("Could not find <version> in root pom.xml")
        old_version = current_version_match.group(1)
        print(f"[INFO] Replacing '{old_version}' with '{release_version}' in pom.xml and child modules...")

        replace_project_version(temp_path, release_version)

        print("[PASS] Versions updated to release version.")

        print(f"\n[STEP 3] Executing release metadata validation...")
        meta_cmd = [
            sys.executable,
            str(temp_path / "scripts" / "verify-release-metadata.py"),
            "--tag", release_tag,
            "--require-match-tag",
            "--require-non-snapshot",
            "--check-workflow-contract",
        ]
        result = subprocess.run(meta_cmd, cwd=temp_path, capture_output=True, text=True)
        print(result.stdout)
        if result.returncode != 0:
            print(result.stderr, file=sys.stderr)
            raise RuntimeError(f"Release metadata validation failed with exit code {result.returncode}")
        print("[PASS] Release metadata verification passed in simulated workspace.")

        print(f"\n[STEP 4] Assembling release bundles in simulated workspace...")
        validate_script = temp_path / "scripts" / "validate-release-bundle.py"

        if build_tool in ["maven", "both"]:
            print("  [MAVEN] Building release packages (-P release -Dgpg.skip=true -DskipTests)...")
            mvn_cmd = ["./mvnw", "clean", "package", "-P", "release", "-Dgpg.skip=true", "-DskipTests", "-B"]
            res = subprocess.run(mvn_cmd, cwd=temp_path, capture_output=True, text=True)
            if res.returncode != 0:
                print(res.stdout)
                print(res.stderr, file=sys.stderr)
                raise RuntimeError(f"Maven package assembly failed with exit code {res.returncode}")
            print("  [MAVEN] Build SUCCESS.")

        if build_tool in ["gradle", "both"]:
            print("  [GRADLE] Building assemble and publication POMs...")
            gradle_cmd = ["./gradlew", "assemble", "generatePomFileForMavenJavaPublication",
                          "generatePomFileForVietTemplatePluginMarkerMavenPublication", "--no-daemon"]
            res = subprocess.run(gradle_cmd, cwd=temp_path, capture_output=True, text=True)
            if res.returncode != 0:
                print(res.stdout)
                print(res.stderr, file=sys.stderr)
                raise RuntimeError(f"Gradle assemble failed with exit code {res.returncode}")
            print("  [GRADLE] Build SUCCESS.")

        print(f"\n[STEP 5] Validating publication bundle contents for version '{release_version}'...")
        val_cmd = [
            sys.executable,
            str(validate_script),
            "--build-tool", build_tool,
            "--version", release_version,
            "--target-dir", str(temp_path),
        ]
        res = subprocess.run(val_cmd, cwd=temp_path, capture_output=True, text=True)
        print(res.stdout)
        if res.returncode != 0:
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"Publication bundle validation failed with exit code {res.returncode}")

        print(f"\n[STEP 6] Simulating next development version '{next_version}' in the temporary workspace...")
        replace_project_version(temp_path, next_version)
        next_meta_cmd = [sys.executable, str(temp_path / "scripts" / "verify-release-metadata.py"),
                         "--check-workflow-contract"]
        next_meta = subprocess.run(next_meta_cmd, cwd=temp_path, capture_output=True, text=True)
        print(next_meta.stdout)
        if next_meta.returncode != 0:
            print(next_meta.stderr, file=sys.stderr)
            raise RuntimeError(f"Next development metadata validation failed with exit code {next_meta.returncode}")
        print(f"[PASS] Release version {release_version} advances consistently to {next_version} in the isolated simulation.")

        print("\n[STEP 7] Confirming real working tree hygiene...")
        current_real_pom = (ROOT_DIR / "pom.xml").read_text(encoding="utf-8")
        current_real_bg = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
        if current_real_pom != initial_real_pom or current_real_bg != initial_real_bg:
            raise RuntimeError("CRITICAL ERROR: Real repository working tree was modified!")
        if old_version != release_version and f"<version>{release_version}</version>" in current_real_pom:
            raise RuntimeError("CRITICAL ERROR: Real repository working tree was modified!")
        print(f"[PASS] Real repository remains at {old_version} with zero temporary contamination.")

    print("\n[SUCCESS] Simulated release validation completed successfully!")
    print(f"Release preparation simulation completed for {release_version} / {release_tag}; no publication was performed.")

def main():
    parser = argparse.ArgumentParser(description="Simulate release validation for a release version.")
    parser.add_argument("--version", help="Simulated release version (defaults from the current SNAPSHOT version)")
    parser.add_argument("--tag", help="Simulated release tag (defaults to v<version>)")
    parser.add_argument("--next-version", help="Next development version (defaults to next patch SNAPSHOT)")
    parser.add_argument("--build-tool", choices=["maven", "gradle", "both"], default="both",
                        help="Build tool artifacts to inspect (default: both)")
    args = parser.parse_args()

    try:
        simulate_release(release_version=args.version, release_tag=args.tag, build_tool=args.build_tool,
                         next_version=args.next_version)
    except Exception as e:
        print(f"\n[FAILED] Simulated release validation failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
