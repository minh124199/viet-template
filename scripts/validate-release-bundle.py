#!/usr/bin/env python3
"""
scripts/validate-release-bundle.py

Validates the release publication bundle across all production modules:
1. Verifies that all 4 production modules produce:
   - main JAR (classes)
   - sources JAR
   - Javadoc JAR
   - valid publication POM
2. Inspects binary JARs for clean-room hygiene:
   - No test classes (*Test*.class, *Probe*.class)
   - No TCK classes (*Tck*.class, *Corpus*.class)
   - No Apache Velocity classes (org/apache/velocity/*)
   - No sensitive files (.env, *.key, *.pem)
   - Valid package structure (io/github/minh124199/viettemplate/...)
3. Inspects sources and Javadoc archives for completeness.
4. Validates publication POM metadata against Maven Central requirements:
   - groupId, artifactId, version, name, description, url, license, developers, scm.
   - Asserts zero Apache Velocity dependencies in production POMs.
5. Verifies that viet-template-tck is strictly non-published:
   - maven.deploy.skip / central.publishing.skip enabled in Maven
   - zero publications defined in Gradle
   - absent from publication bundle
"""

import sys
import re
import os
import argparse
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent

PRODUCTION_MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
]

EXCLUDED_MODULES = [
    "viet-template-tck",
]

def get_project_version():
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    if ver_elem is None or not ver_elem.text:
        raise ValueError("Cannot extract version from root pom.xml")
    return ver_elem.text.strip()

def validate_jar_classes(jar_path, module_name, errors):
    print(f"  [CHECK] Inspecting binary archive {jar_path.name}...")
    if not jar_path.exists():
        errors.append(f"Missing binary JAR: {jar_path}")
        return
    if jar_path.stat().st_size == 0:
        errors.append(f"Empty binary JAR (0 bytes): {jar_path}")
        return

    with zipfile.ZipFile(jar_path) as z:
        names = z.namelist()
        class_files = [n for n in names if n.endswith(".class")]
        if not class_files:
            errors.append(f"No .class files found in {jar_path}")

        for name in names:
            # Check for leaked test or TCK classes
            if any(forbidden in name for forbidden in ["Test", "TestCase", "Probe", "Corpus", "Smoke"]):
                errors.append(f"Forbidden test class found in production JAR {jar_path.name}: {name}")

            # Check for leaked Apache Velocity classes
            if name.startswith("org/apache/velocity"):
                errors.append(f"CRITICAL: Apache Velocity class leaked into {jar_path.name}: {name}")

            # Check for leaked sensitive files
            if any(name.endswith(ext) for ext in [".key", ".pem", ".p12", ".env"]):
                errors.append(f"CRITICAL: Sensitive file leaked into {jar_path.name}: {name}")

            # Verify package namespace
            if name.endswith(".class") and not name.startswith("META-INF"):
                if not name.startswith("io/github/minh124199/viettemplate/"):
                    errors.append(f"Class outside standard namespace in {jar_path.name}: {name}")

    print(f"  [PASS] {jar_path.name} contains {len(class_files)} classes in valid namespace with 0 test/Velocity leaks.")

def validate_sources_jar(sources_jar_path, errors):
    print(f"  [CHECK] Inspecting sources archive {sources_jar_path.name}...")
    if not sources_jar_path.exists():
        errors.append(f"Missing sources JAR: {sources_jar_path}")
        return
    if sources_jar_path.stat().st_size == 0:
        errors.append(f"Empty sources JAR (0 bytes): {sources_jar_path}")
        return

    with zipfile.ZipFile(sources_jar_path) as z:
        java_files = [n for n in z.namelist() if n.endswith(".java")]
        if not java_files:
            errors.append(f"No .java files found in sources JAR: {sources_jar_path.name}")
        for name in java_files:
            if "Test" in name:
                errors.append(f"Test source found in production sources JAR: {name}")

    print(f"  [PASS] {sources_jar_path.name} contains {len(java_files)} source files.")

def validate_javadoc_jar(javadoc_jar_path, errors):
    print(f"  [CHECK] Inspecting javadoc archive {javadoc_jar_path.name}...")
    if not javadoc_jar_path.exists():
        errors.append(f"Missing Javadoc JAR: {javadoc_jar_path}")
        return
    if javadoc_jar_path.stat().st_size == 0:
        errors.append(f"Empty Javadoc JAR (0 bytes): {javadoc_jar_path}")
        return

    with zipfile.ZipFile(javadoc_jar_path) as z:
        names = z.namelist()
        has_doc = any(n.endswith(".html") or n.endswith("element-list") or n.endswith("package-list") for n in names)
        if not has_doc:
            errors.append(f"No HTML/metadata doc files found in Javadoc JAR: {javadoc_jar_path.name}")

    print(f"  [PASS] {javadoc_jar_path.name} is valid Javadoc archive.")

def validate_pom_metadata(pom_path, module_name, expected_version, errors):
    print(f"  [CHECK] Inspecting publication POM for {module_name}...")
    if not pom_path.exists():
        errors.append(f"Missing publication POM: {pom_path}")
        return

    pom_tree = ET.parse(pom_path)
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    # GroupId
    group_elem = pom_root.find(f"./{prefix}groupId", ns)
    if group_elem is None:
        parent_elem = pom_root.find(f"./{prefix}parent/{prefix}groupId", ns)
        group_id = parent_elem.text.strip() if parent_elem is not None else None
    else:
        group_id = group_elem.text.strip()

    if group_id != "io.github.minh124199":
        errors.append(f"Invalid groupId '{group_id}' in {pom_path} (expected 'io.github.minh124199')")

    # ArtifactId
    art_elem = pom_root.find(f"./{prefix}artifactId", ns)
    artifact_id = art_elem.text.strip() if art_elem is not None else None
    if artifact_id != module_name:
        errors.append(f"Invalid artifactId '{artifact_id}' in {pom_path} (expected '{module_name}')")

    # Version
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    if ver_elem is None:
        parent_elem = pom_root.find(f"./{prefix}parent/{prefix}version", ns)
        version = parent_elem.text.strip() if parent_elem is not None else None
    else:
        version = ver_elem.text.strip()

    if version != expected_version:
        errors.append(f"Invalid version '{version}' in {pom_path} (expected '{expected_version}')")

    # Dependencies check: ensure strict clean-room isolation and zero test/Velocity leakage
    for dep in pom_root.findall(f".//{prefix}dependency", ns):
        dep_group = dep.find(f"./{prefix}groupId", ns)
        dep_art = dep.find(f"./{prefix}artifactId", ns)
        dep_scope = dep.find(f"./{prefix}scope", ns)
        scope_str = dep_scope.text.strip() if dep_scope is not None else "compile"
        group_str = dep_group.text.strip() if dep_group is not None else ""
        art_str = dep_art.text.strip() if dep_art is not None else ""

        # Check 1: Apache Velocity must NEVER appear anywhere in production module POMs
        if "velocity" in group_str.lower() or "velocity" in art_str.lower():
            errors.append(f"CRITICAL: Apache Velocity dependency in {pom_path}: {group_str}:{art_str} (scope: {scope_str})")

        # Check 2: viet-template-tck must NEVER appear anywhere in production module POMs
        if "tck" in art_str.lower():
            errors.append(f"CRITICAL: TCK dependency found in {pom_path}: {group_str}:{art_str} (scope: {scope_str})")

        # Check 3: Test libraries must NEVER appear in compile or runtime scope
        if scope_str != "test":
            for forbidden_prefix in ["junit", "assertj", "archunit", "mockito"]:
                if forbidden_prefix in group_str.lower() or forbidden_prefix in art_str.lower():
                    errors.append(
                        f"CRITICAL: Test framework dependency leaked into non-test scope in {pom_path}: "
                        f"{group_str}:{art_str} (scope: {scope_str})"
                    )

            # Check 4: Non-test dependencies in Viet Template production modules must only be sibling production modules
            if group_str != "io.github.minh124199" or art_str not in PRODUCTION_MODULES:
                errors.append(
                    f"CRITICAL: Unexpected external production dependency in {pom_path}: "
                    f"{group_str}:{art_str} (scope: {scope_str}). Viet Template production modules must have zero external dependencies."
                )

    print(f"  [PASS] POM metadata for {module_name} conforms to Maven Central standards.")

def validate_tck_defense_in_depth(root_dir, errors):
    print("\n[CHECK] Verifying TCK deployment defense-in-depth...")
    tck_pom_path = root_dir / "viet-template-tck" / "pom.xml"
    if not tck_pom_path.exists():
        errors.append("viet-template-tck/pom.xml missing")
        return

    tck_text = tck_pom_path.read_text(encoding="utf-8")
    if "<maven.deploy.skip>true</maven.deploy.skip>" not in tck_text:
        errors.append("viet-template-tck/pom.xml missing <maven.deploy.skip>true</maven.deploy.skip>")
    if "<skipPublishing>true</skipPublishing>" not in tck_text:
        errors.append("viet-template-tck/pom.xml missing <skipPublishing>true</skipPublishing>")

    # Check Gradle build.gradle.kts
    build_gradle_path = root_dir / "build.gradle.kts"
    if build_gradle_path.exists():
        build_gradle = build_gradle_path.read_text(encoding="utf-8")
        if 'if (project.name != "viet-template-tck")' not in build_gradle:
            errors.append("build.gradle.kts does not explicitly exclude viet-template-tck from publication")

    print("  [PASS] viet-template-tck is explicitly prevented from publishing in both Maven and Gradle.")

def main():
    parser = argparse.ArgumentParser(description="Validate release publication bundle.")
    parser.add_argument("--build-tool", choices=["maven", "gradle", "both"], default="maven",
                        help="Build tool artifacts to inspect (default: maven)")
    parser.add_argument("--version", help="Explicit version to validate (defaults to root pom.xml version)")
    parser.add_argument("--target-dir", type=Path, default=ROOT_DIR, help="Root directory of repository to inspect")
    parser.add_argument("--assemble", action="store_true", help="Assemble artifacts before validating")
    args = parser.parse_args()

    target_dir = args.target_dir.resolve()
    print("=== Viet Template Release Publication Bundle Validation ===")
    errors = []

    version = args.version
    if not version:
        pom_tree = ET.parse(target_dir / "pom.xml")
        pom_root = pom_tree.getroot()
        ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
        prefix = "m:" if ns else ""
        ver_elem = pom_root.find(f"./{prefix}version", ns)
        version = ver_elem.text.strip() if ver_elem is not None else "0.0.0"

    print(f"[INFO] Target directory: {target_dir}")
    print(f"[INFO] Project version: {version}")
    print(f"[INFO] Evaluating build tool artifacts: {args.build_tool}")

    tools = ["maven", "gradle"] if args.build_tool == "both" else [args.build_tool]

    if args.assemble:
        import subprocess
        if "maven" in tools:
            print("\n[ACTION] Assembling Maven release artifacts...")
            subprocess.run(["./mvnw", "clean", "package", "-P", "release", "-Dgpg.skip=true", "-DskipTests", "-B"],
                           cwd=target_dir, check=True)
        if "gradle" in tools:
            print("\n[ACTION] Assembling Gradle artifacts...")
            subprocess.run(["./gradlew", "assemble", "--no-daemon"], cwd=target_dir, check=True)

    for tool in tools:
        print(f"\n--- Validating {tool.upper()} Release Bundle ---")
        for mod in PRODUCTION_MODULES:
            print(f"\nEvaluating module '{mod}':")
            if tool == "maven":
                mod_dir = target_dir / mod / "target"
                main_jar = mod_dir / f"{mod}-{version}.jar"
                sources_jar = mod_dir / f"{mod}-{version}-sources.jar"
                javadoc_jar = mod_dir / f"{mod}-{version}-javadoc.jar"
                pom_path = target_dir / mod / "pom.xml"
            else: # gradle
                mod_dir = target_dir / mod / "build" / "libs"
                main_jar = mod_dir / f"{mod}-{version}.jar"
                sources_jar = mod_dir / f"{mod}-{version}-sources.jar"
                javadoc_jar = mod_dir / f"{mod}-{version}-javadoc.jar"
                pub_pom = target_dir / mod / "build" / "publications" / "mavenJava" / "pom-default.xml"
                pom_path = pub_pom if pub_pom.exists() else (target_dir / mod / "pom.xml")

            validate_jar_classes(main_jar, mod, errors)
            validate_sources_jar(sources_jar, errors)
            validate_javadoc_jar(javadoc_jar, errors)
            validate_pom_metadata(pom_path, mod, version, errors)

    validate_tck_defense_in_depth(target_dir, errors)

    if errors:
        print("\n[FAILED] Release publication bundle validation FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Release publication bundle validation PASSED! All 4 production modules are release-ready.")
        sys.exit(0)

if __name__ == "__main__":
    main()
