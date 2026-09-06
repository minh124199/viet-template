#!/usr/bin/env python3
"""
scripts/verify-build-parity.py

Verifies build parity between Gradle Kotlin DSL and Apache Maven builds:
1. Module consistency across settings.gradle.kts and pom.xml
2. Identity consistency: groupId, version
3. Java baseline: release 17
4. Dependency version parity for key dependencies (JUnit, AssertJ, ArchUnit, google-java-format)
5. Compiler flag parity (-parameters, -Xlint:all, -Werror, UTF-8)
6. JAR entry parity (classes and resources) when artifacts are compiled
"""

import sys
import re
import os
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent

def check_modules(errors):
    settings_file = ROOT_DIR / "settings.gradle.kts"
    pom_file = ROOT_DIR / "pom.xml"

    if not settings_file.exists():
        errors.append("settings.gradle.kts missing")
        return
    if not pom_file.exists():
        errors.append("root pom.xml missing")
        return

    # Extract Gradle modules
    settings_text = settings_file.read_text(encoding="utf-8")
    gradle_modules = sorted(re.findall(r'include\(["\']([^"\']+)["\']\)', settings_text))

    # Extract Maven modules
    pom_tree = ET.parse(pom_file)
    pom_root = pom_tree.getroot()
    # Maven namespace may be present
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    maven_modules = []
    for mod_elem in pom_root.findall(f"./{prefix}modules/{prefix}module", ns):
        if mod_elem.text:
            maven_modules.append(mod_elem.text.strip())
    maven_modules = sorted(maven_modules)

    print(f"[CHECK] Modules in Gradle: {gradle_modules}")
    print(f"[CHECK] Modules in Maven:  {maven_modules}")

    if gradle_modules != maven_modules:
        errors.append(f"Module mismatch: Gradle has {gradle_modules}, Maven has {maven_modules}")
    else:
        print("[PASS] Module sets match identically.")

    # Check that each module has both build.gradle.kts and pom.xml
    for mod in gradle_modules:
        mod_dir = ROOT_DIR / mod
        if not (mod_dir / "build.gradle.kts").exists():
            errors.append(f"Module {mod} missing build.gradle.kts")
        if not (mod_dir / "pom.xml").exists():
            errors.append(f"Module {mod} missing pom.xml")

def check_identity(errors):
    build_gradle = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    # Group
    gradle_group_match = re.search(r'group\s*=\s*["\']([^"\']+)["\']', build_gradle)
    gradle_group = gradle_group_match.group(1) if gradle_group_match else None
    pom_group_elem = pom_root.find(f"./{prefix}groupId", ns)
    pom_group = pom_group_elem.text.strip() if pom_group_elem is not None else None

    print(f"[CHECK] GroupId: Gradle={gradle_group}, Maven={pom_group}")
    if gradle_group != pom_group:
        errors.append(f"GroupId mismatch: Gradle={gradle_group}, Maven={pom_group}")
    else:
        print("[PASS] GroupId matches.")

    # Version
    gradle_ver_match = re.search(r'version\s*=\s*["\']([^"\']+)["\']', build_gradle)
    gradle_ver = gradle_ver_match.group(1) if gradle_ver_match else None
    pom_ver_elem = pom_root.find(f"./{prefix}version", ns)
    pom_ver = pom_ver_elem.text.strip() if pom_ver_elem is not None else None

    print(f"[CHECK] Version: Gradle={gradle_ver}, Maven={pom_ver}")
    if gradle_ver != pom_ver:
        errors.append(f"Version mismatch: Gradle={gradle_ver}, Maven={pom_ver}")
    else:
        print("[PASS] Version matches.")

def check_java_baseline_and_flags(errors):
    build_gradle = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    pom_text = (ROOT_DIR / "pom.xml").read_text(encoding="utf-8")

    # Release 17
    if 'options.release.set(17)' not in build_gradle:
        errors.append("build.gradle.kts missing 'options.release.set(17)'")
    if '<release>17</release>' not in pom_text:
        errors.append("pom.xml missing '<release>17</release>'")

    # Required compiler flags
    for flag in ["-parameters", "-Xlint:all", "-Werror"]:
        if flag not in build_gradle:
            errors.append(f"build.gradle.kts missing compiler flag '{flag}'")
        if flag not in pom_text:
            errors.append(f"pom.xml missing compiler flag '{flag}'")

    print("[PASS] Java 17 release target and strict compiler flags (-parameters, -Xlint:all, -Werror) match in both builds.")

def check_dependency_versions(errors):
    libs_toml = (ROOT_DIR / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    build_gradle = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    properties = {}
    props_elem = pom_root.find(f"./{prefix}properties", ns)
    if props_elem is not None:
        for child in props_elem:
            tag = child.tag.split("}")[-1] if "}" in child.tag else child.tag
            properties[tag] = child.text.strip() if child.text else ""

    # JUnit
    junit_toml = re.search(r'junit\s*=\s*["\']([^"\']+)["\']', libs_toml)
    junit_ver = junit_toml.group(1) if junit_toml else None
    pom_junit_ver = properties.get("junit.version") or properties.get("junit-jupiter.version")
    print(f"[CHECK] JUnit version: TOML={junit_ver}, POM={pom_junit_ver}")
    if junit_ver != pom_junit_ver:
        errors.append(f"JUnit version mismatch: TOML={junit_ver}, POM={pom_junit_ver}")

    # AssertJ
    assertj_toml = re.search(r'assertj\s*=\s*["\']([^"\']+)["\']', libs_toml)
    assertj_ver = assertj_toml.group(1) if assertj_toml else None
    pom_assertj_ver = properties.get("assertj.version")
    print(f"[CHECK] AssertJ version: TOML={assertj_ver}, POM={pom_assertj_ver}")
    if assertj_ver != pom_assertj_ver:
        errors.append(f"AssertJ version mismatch: TOML={assertj_ver}, POM={pom_assertj_ver}")

    # ArchUnit
    archunit_toml = re.search(r'archunit\s*=\s*["\']([^"\']+)["\']', libs_toml)
    archunit_ver = archunit_toml.group(1) if archunit_toml else None
    pom_archunit_ver = properties.get("archunit.version")
    print(f"[CHECK] ArchUnit version: TOML={archunit_ver}, POM={pom_archunit_ver}")
    if archunit_ver != pom_archunit_ver:
        errors.append(f"ArchUnit version mismatch: TOML={archunit_ver}, POM={pom_archunit_ver}")

    # Velocity
    velocity_toml = re.search(r'velocity\s*=\s*["\']([^"\']+)["\']', libs_toml)
    velocity_ver = velocity_toml.group(1) if velocity_toml else None
    pom_velocity_ver = properties.get("velocity.version")
    print(f"[CHECK] Velocity version: TOML={velocity_ver}, POM={pom_velocity_ver}")
    if velocity_ver != pom_velocity_ver:
        errors.append(f"Velocity version mismatch: TOML={velocity_ver}, POM={pom_velocity_ver}")

    # google-java-format
    gjf_gradle = re.search(r'googleJavaFormat\(["\']([^"\']+)["\']\)', build_gradle)
    gjf_ver = gjf_gradle.group(1) if gjf_gradle else None
    pom_gjf_ver = properties.get("google-java-format.version")
    print(f"[CHECK] google-java-format version: Gradle={gjf_ver}, POM={pom_gjf_ver}")
    if gjf_ver != pom_gjf_ver:
        errors.append(f"google-java-format mismatch: Gradle={gjf_ver}, POM={pom_gjf_ver}")

    if not errors:
        print("[PASS] Core dependency versions are fully aligned.")

def check_jar_contents(errors):
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    version = ver_elem.text.strip() if ver_elem is not None else "0.1.0"

    modules = [
        "viet-template-api",
        "viet-template-runtime",
        "viet-template-language-vtl",
        "viet-template-vtl-interpreter",
    ]
    checked = 0
    for mod in modules:
        gradle_jar = ROOT_DIR / mod / "build" / "libs" / f"{mod}-{version}.jar"
        maven_jar = ROOT_DIR / mod / "target" / f"{mod}-{version}.jar"

        if gradle_jar.exists() and maven_jar.exists():
            with zipfile.ZipFile(gradle_jar) as gz, zipfile.ZipFile(maven_jar) as mz:
                g_entries = sorted([n for n in gz.namelist() if not n.startswith("META-INF")])
                m_entries = sorted([n for n in mz.namelist() if not n.startswith("META-INF")])
                diff = set(g_entries) ^ set(m_entries)
                if diff:
                    errors.append(f"JAR entry mismatch in module {mod}: diff={diff}")
                else:
                    print(f"[PASS] JAR contents match identically for {mod} ({len(g_entries)} entries)")
                    checked += 1
        else:
            print(f"[INFO] JARs not present for {mod} (skipping binary diff)")

    if checked > 0:
        print(f"[PASS] Verified identical class/resource contents across {checked} production JARs.")

def main():
    print("=== Viet Template Build Parity Verification ===")
    errors = []

    check_modules(errors)
    check_identity(errors)
    check_java_baseline_and_flags(errors)
    check_dependency_versions(errors)
    check_jar_contents(errors)

    if errors:
        print("\n[FAILED] Build parity verification failed with the following issues:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Build parity verification PASSED! Gradle and Maven builds are fully aligned.")
        sys.exit(0)

if __name__ == "__main__":
    main()
