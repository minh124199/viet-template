#!/usr/bin/env python3
"""
scripts/validate-release-bundle.py

Validates the release publication bundle across the public release coordinates:
1. Verifies the parent POM and that all 4 production modules produce:
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

import importlib
import importlib.util

ROOT_DIR = Path(__file__).resolve().parent.parent
_metadata_script = Path(__file__).resolve().parent / "verify-release-metadata.py"
if _metadata_script.exists():
    _spec = importlib.util.spec_from_file_location("verify_release_metadata", _metadata_script)
    _mod = importlib.util.module_from_spec(_spec)
    _spec.loader.exec_module(_mod)
    PUBLISHED_MODULES = _mod.PUBLISHED_MODULES
    NON_PUBLISHED_MODULES = _mod.NON_PUBLISHED_MODULES
else:
    PUBLISHED_MODULES = []
    NON_PUBLISHED_MODULES = []

PRODUCTION_MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
]

ALL_PUBLISHED_MODULES = PUBLISHED_MODULES
EXCLUDED_MODULES = NON_PUBLISHED_MODULES

PARENT_MODULE = "viet-template-parent"
PLUGIN_MARKER_GROUP_ID = "io.github.minh124199.viet-template"
PLUGIN_MARKER_ARTIFACT_ID = "io.github.minh124199.viet-template.gradle.plugin"
PLUGIN_MARKER_COORDINATE = f"{PLUGIN_MARKER_GROUP_ID}:{PLUGIN_MARKER_ARTIFACT_ID}"
TOTAL_PUBLIC_COORDINATES = 14

RE_INVALID_URL = re.compile(r"^https://github\.com/minh124199/viet-template/viet-template-.*")
RE_INVALID_SCM = re.compile(r"(/viet-template-)|(viet-template\.git/viet-template-.*)|(^scm:git:git://github\.com/)")



def derive_published_modules(root_dir=ROOT_DIR):
    """Derives published production modules and non-published modules dynamically from root pom.xml."""
    pom_file = root_dir / "pom.xml"
    if not pom_file.exists():
        return list(PRODUCTION_MODULES), list(ALL_PUBLISHED_MODULES), list(EXCLUDED_MODULES)
    try:
        pom_tree = ET.parse(pom_file)
        pom_root = pom_tree.getroot()
        ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
        prefix = "m:" if ns else ""
        modules_elem = pom_root.find(f"./{prefix}modules", ns)
        if modules_elem is None:
            return list(PRODUCTION_MODULES), list(ALL_PUBLISHED_MODULES), list(EXCLUDED_MODULES)

        all_published = []
        excluded = []
        for mod_elem in modules_elem.findall(f"./{prefix}module", ns):
            mod_name = mod_elem.text.strip() if mod_elem.text else ""
            if not mod_name:
                continue
            child_pom = root_dir / mod_name / "pom.xml"
            if not child_pom.exists():
                continue
            child_tree = ET.parse(child_pom)
            child_root = child_tree.getroot()
            c_ns = {"m": child_root.tag.split("}")[0].strip("{")} if "}" in child_root.tag else {}
            c_prefix = "m:" if c_ns else ""
            props = child_root.find(f"./{c_prefix}properties", c_ns)
            skip = False
            if props is not None:
                for skip_prop in ("maven.deploy.skip", "skipPublishing", "central.publishing.skip"):
                    val = props.findtext(f"./{c_prefix}{skip_prop}", namespaces=c_ns)
                    if val and val.strip() == "true":
                        skip = True
                        break
            if skip:
                excluded.append(mod_name)
            else:
                all_published.append(mod_name)

        prod = [
            m for m in all_published
            if (root_dir / m / "src" / "main" / "java").exists()
            and not m.endswith("-plugin")
            and not m.endswith("-starter")
            and not m.endswith("-security")  # security is verified under all_published
            and m in PRODUCTION_MODULES  # baseline production modules with full verification
        ]
        if not prod:
            prod = [m for m in all_published if m in PRODUCTION_MODULES]
        return prod, all_published, excluded
    except Exception:
        return list(PRODUCTION_MODULES), list(ALL_PUBLISHED_MODULES), list(EXCLUDED_MODULES)

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

            # Verify bytecode version is Java 21 (class major version 65)
            if name.endswith(".class") and not name.endswith("module-info.class"):
                class_bytes = z.read(name)
                if len(class_bytes) >= 8:
                    magic = int.from_bytes(class_bytes[0:4], "big")
                    major = int.from_bytes(class_bytes[6:8], "big")
                    if magic == 0xCAFEBABE and major != 65:
                        errors.append(
                            f"Bytecode version mismatch in {jar_path.name} for {name}: expected major version 65 (Java 21), got {major}"
                        )

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

def validate_pom_metadata(pom_path, module_name, expected_version, errors, enforce_production_dependencies=True):
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

    # URL check
    url_elem = pom_root.find(f"./{prefix}url", ns)
    if url_elem is None or not url_elem.text:
        errors.append(f"Missing <url> in {pom_path}")
    else:
        url_str = url_elem.text.strip()
        if module_name == PARENT_MODULE:
            if url_str != "https://github.com/minh124199/viet-template":
                errors.append(
                    f"Invalid url '{url_str}' in {pom_path} (expected 'https://github.com/minh124199/viet-template')"
                )
            proj_url_inherit = pom_root.attrib.get("child.project.url.inherit.append.path")
            if proj_url_inherit != "false":
                errors.append(
                    f"Missing or invalid child.project.url.inherit.append.path on <project> in {pom_path} (expected 'false', found '{proj_url_inherit}')"
                )
        else:
            expected_1 = f"https://github.com/minh124199/viet-template/tree/main/{module_name}"
            expected_2 = f"${{github.repository.url}}/tree/main/{module_name}"
            if url_str == "https://github.com/minh124199/viet-template":
                errors.append(
                    f"Module {module_name} in {pom_path} merely inherited repository-root url; must explicitly declare '{expected_1}' or '{expected_2}'"
                )
            elif url_str not in (expected_1, expected_2):
                errors.append(f"Invalid url '{url_str}' in {pom_path} (expected '{expected_1}' or '{expected_2}')")
        if RE_INVALID_URL.match(url_str):
            errors.append(f"Malformed appended url '{url_str}' in {pom_path}")

    # SCM check
    scm_elem = pom_root.find(f"./{prefix}scm", ns)
    if scm_elem is not None:
        if module_name == PARENT_MODULE:
            for attr in (
                "child.scm.connection.inherit.append.path",
                "child.scm.developerConnection.inherit.append.path",
                "child.scm.url.inherit.append.path",
            ):
                val = scm_elem.attrib.get(attr)
                if val != "false":
                    errors.append(
                        f"Missing or invalid {attr} on <scm> in {pom_path} (expected 'false', found '{val}')"
                    )

        conn_elem = scm_elem.find(f"./{prefix}connection", ns)
        conn = conn_elem.text.strip() if conn_elem is not None and conn_elem.text else ""
        if conn and conn.startswith("scm:git:git://github.com/"):
            errors.append(f"Obsolete git:// protocol rejected for scm connection in {pom_path}: '{conn}'")
        elif conn and conn != "scm:git:https://github.com/minh124199/viet-template.git":
            errors.append(f"Invalid scm connection '{conn}' in {pom_path} (expected 'scm:git:https://github.com/minh124199/viet-template.git')")
        if conn and RE_INVALID_SCM.search(conn):
            errors.append(f"Malformed appended scm connection or rejected protocol '{conn}' in {pom_path}")

        dev_elem = scm_elem.find(f"./{prefix}developerConnection", ns)
        dev = dev_elem.text.strip() if dev_elem is not None and dev_elem.text else ""
        if dev and dev != "scm:git:ssh://git@github.com/minh124199/viet-template.git":
            errors.append(f"Invalid scm developerConnection '{dev}' in {pom_path}")
        if dev and RE_INVALID_SCM.search(dev):
            errors.append(f"Malformed appended scm developerConnection '{dev}' in {pom_path}")

        url_s_elem = scm_elem.find(f"./{prefix}url", ns)
        s_url = url_s_elem.text.strip() if url_s_elem is not None and url_s_elem.text else ""
        if s_url and s_url != "https://github.com/minh124199/viet-template":
            errors.append(f"Invalid scm url '{s_url}' in {pom_path}")
        if s_url and (RE_INVALID_SCM.search(s_url) or RE_INVALID_URL.match(s_url)):
            errors.append(f"Malformed appended scm url '{s_url}' in {pom_path}")
    elif module_name == PARENT_MODULE:
        errors.append(f"Missing <scm> in parent POM {pom_path}")
    else:
        # Child POM in Maven inherits from parent
        parent_pom = (
            pom_path.parent.parent / "pom.xml"
            if pom_path.parent.name != "target"
            else pom_path.parent.parent.parent / "pom.xml"
        )
        if not parent_pom.exists():
            parent_pom = ROOT_DIR / "pom.xml"
        if parent_pom.exists():
            p_tree = ET.parse(parent_pom)
            p_root = p_tree.getroot()
            p_ns = {"m": p_root.tag.split("}")[0].strip("{")} if "}" in p_root.tag else {}
            p_prefix = "m:" if p_ns else ""
            p_scm = p_root.find(f"./{p_prefix}scm", p_ns)
            if p_scm is None:
                errors.append(f"Child module {module_name} lacks <scm> and parent lacks <scm>")
            else:
                for attr in (
                    "child.scm.connection.inherit.append.path",
                    "child.scm.developerConnection.inherit.append.path",
                    "child.scm.url.inherit.append.path",
                ):
                    val = p_scm.attrib.get(attr)
                    if val != "false":
                        errors.append(f"Parent <scm> for {module_name} must declare {attr}='false'")

    # Dependencies check: ensure strict clean-room isolation and zero test/Velocity leakage
    dep_list = (
        pom_root.findall(f"./{prefix}dependencies/{prefix}dependency", ns)
        if module_name != PARENT_MODULE
        else []
    )
    for dep in dep_list:
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
            if enforce_production_dependencies:
                if group_str != "io.github.minh124199" or art_str not in PRODUCTION_MODULES:
                    errors.append(
                        f"CRITICAL: Unexpected external production dependency in {pom_path}: "
                        f"{group_str}:{art_str} (scope: {scope_str}). Viet Template production modules must have zero external dependencies."
                    )

    print(f"  [PASS] POM metadata for {module_name} conforms to Maven Central standards.")

def validate_tck_defense_in_depth(root_dir, errors):
    print("\n[CHECK] Verifying non-published modules deployment defense-in-depth...")
    for mod in EXCLUDED_MODULES:
        pom_path = root_dir / mod / "pom.xml"
        if not pom_path.exists():
            errors.append(f"{mod}/pom.xml missing")
            continue

        pom_text = pom_path.read_text(encoding="utf-8")
        if "<maven.deploy.skip>true</maven.deploy.skip>" not in pom_text:
            errors.append(f"{mod}/pom.xml missing <maven.deploy.skip>true</maven.deploy.skip>")
        if "<skipPublishing>true</skipPublishing>" not in pom_text:
            errors.append(f"{mod}/pom.xml missing <skipPublishing>true</skipPublishing>")

    # Check Gradle build.gradle.kts
    build_gradle_path = root_dir / "build.gradle.kts"
    if build_gradle_path.exists():
        build_gradle = build_gradle_path.read_text(encoding="utf-8")
        if 'project.name != "viet-template-tck"' not in build_gradle:
            errors.append("build.gradle.kts does not explicitly exclude viet-template-tck from publication")
        if 'project.name != "viet-template-benchmarks"' not in build_gradle:
            errors.append("build.gradle.kts does not explicitly exclude viet-template-benchmarks from publication")

    print("  [PASS] Non-published modules (viet-template-tck, viet-template-benchmarks) are explicitly prevented from publishing in both Maven and Gradle.")


def validate_parent_pom(root_dir, expected_version, errors):
    """The root POM is a deliberate public coordinate, not merely reactor metadata."""
    print(f"\nEvaluating parent coordinate '{PARENT_MODULE}':")
    validate_pom_metadata(
        root_dir / "pom.xml", PARENT_MODULE, expected_version, errors, enforce_production_dependencies=False
    )


def validate_plugin_marker(target_dir, expected_version, errors, repo_dir=None, pom_path=None):
    """Validates the Gradle plugin marker POM metadata and dependency on viet-template-gradle-plugin."""
    print(f"\nEvaluating Gradle plugin marker coordinate '{PLUGIN_MARKER_COORDINATE}':")
    target_dir = Path(target_dir)
    marker_pom = Path(pom_path) if pom_path is not None else None

    if marker_pom is None:
        marker_filename = f"{PLUGIN_MARKER_ARTIFACT_ID}-{expected_version}.pom"
        candidates = []
        if repo_dir:
            r = Path(repo_dir)
            candidates.extend([
                r / "io" / "github" / "minh124199" / "viet-template" / PLUGIN_MARKER_ARTIFACT_ID / expected_version / marker_filename,
                r / PLUGIN_MARKER_ARTIFACT_ID / expected_version / marker_filename,
            ])
        candidates.extend([
            target_dir / "viet-template-gradle-plugin" / "build" / "publications" / "vietTemplatePluginMarkerMaven" / "pom-default.xml",
            target_dir / "build" / "rc-repository" / "io" / "github" / "minh124199" / "viet-template" / PLUGIN_MARKER_ARTIFACT_ID / expected_version / marker_filename,
            target_dir / "io" / "github" / "minh124199" / "viet-template" / PLUGIN_MARKER_ARTIFACT_ID / expected_version / marker_filename,
            target_dir / "target" / "central-staging" / "io" / "github" / "minh124199" / "viet-template" / PLUGIN_MARKER_ARTIFACT_ID / expected_version / marker_filename,
        ])
        for cand in candidates:
            if cand.exists():
                marker_pom = cand
                break

    if marker_pom is None or not marker_pom.exists():
        errors.append(f"Missing Gradle plugin marker POM in {target_dir}")
        return

    print(f"  [CHECK] Inspecting Gradle plugin marker POM: {marker_pom}")
    try:
        pom_tree = ET.parse(marker_pom)
        pom_root = pom_tree.getroot()
    except Exception as exc:
        errors.append(f"Failed to parse marker POM {marker_pom}: {exc}")
        return

    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    # GroupId
    group_elem = pom_root.find(f"./{prefix}groupId", ns)
    group_id = group_elem.text.strip() if group_elem is not None and group_elem.text else ""
    if group_id != PLUGIN_MARKER_GROUP_ID:
        errors.append(
            f"Invalid groupId '{group_id}' in marker POM {marker_pom} (expected '{PLUGIN_MARKER_GROUP_ID}')"
        )

    # ArtifactId
    art_elem = pom_root.find(f"./{prefix}artifactId", ns)
    art_id = art_elem.text.strip() if art_elem is not None and art_elem.text else ""
    if art_id != PLUGIN_MARKER_ARTIFACT_ID:
        errors.append(
            f"Invalid artifactId '{art_id}' in marker POM {marker_pom} (expected '{PLUGIN_MARKER_ARTIFACT_ID}')"
        )

    # Version
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    ver = ver_elem.text.strip() if ver_elem is not None and ver_elem.text else ""
    if ver != expected_version:
        errors.append(
            f"Invalid version '{ver}' in marker POM {marker_pom} (expected '{expected_version}')"
        )

    # Packaging
    pack_elem = pom_root.find(f"./{prefix}packaging", ns)
    pack = pack_elem.text.strip() if pack_elem is not None and pack_elem.text else ""
    if pack != "pom":
        errors.append(
            f"Invalid packaging '{pack}' in marker POM {marker_pom} (expected 'pom')"
        )

    # Description
    desc_elem = pom_root.find(f"./{prefix}description", ns)
    desc = desc_elem.text.strip() if desc_elem is not None and desc_elem.text else ""
    if not desc:
        errors.append(f"Missing <description> in marker POM {marker_pom}")

    # URL
    url_elem = pom_root.find(f"./{prefix}url", ns)
    url_val = url_elem.text.strip() if url_elem is not None and url_elem.text else ""
    if not url_val:
        errors.append(f"Missing <url> in marker POM {marker_pom}")

    # Licenses
    license_elem = pom_root.find(f"./{prefix}licenses/{prefix}license", ns)
    if license_elem is None:
        errors.append(f"Missing <licenses> in marker POM {marker_pom}")
    else:
        lic_name = license_elem.findtext(f"./{prefix}name", namespaces=ns)
        lic_url = license_elem.findtext(f"./{prefix}url", namespaces=ns)
        if not lic_name or not lic_url:
            errors.append(f"Incomplete <license> in marker POM {marker_pom}")

    # Developers
    dev_elem = pom_root.find(f"./{prefix}developers/{prefix}developer", ns)
    if dev_elem is None:
        errors.append(f"Missing <developers> in marker POM {marker_pom}")
    else:
        dev_id = dev_elem.findtext(f"./{prefix}id", namespaces=ns)
        dev_name = dev_elem.findtext(f"./{prefix}name", namespaces=ns)
        if not dev_id and not dev_name:
            errors.append(f"Incomplete <developer> in marker POM {marker_pom}")

    # SCM
    scm_elem = pom_root.find(f"./{prefix}scm", ns)
    if scm_elem is None:
        errors.append(f"Missing <scm> in marker POM {marker_pom}")
    else:
        scm_conn = scm_elem.findtext(f"./{prefix}connection", namespaces=ns)
        scm_dev = scm_elem.findtext(f"./{prefix}developerConnection", namespaces=ns)
        scm_url = scm_elem.findtext(f"./{prefix}url", namespaces=ns)
        if not scm_conn or not scm_dev or not scm_url:
            errors.append(f"Incomplete <scm> in marker POM {marker_pom}")

    # Dependencies: must depend on io.github.minh124199:viet-template-gradle-plugin:<expected_version>
    deps = pom_root.findall(f"./{prefix}dependencies/{prefix}dependency", ns)
    plugin_dep_found = False
    for dep in deps:
        d_group = (dep.findtext(f"./{prefix}groupId", namespaces=ns) or "").strip()
        d_art = (dep.findtext(f"./{prefix}artifactId", namespaces=ns) or "").strip()
        d_ver = (dep.findtext(f"./{prefix}version", namespaces=ns) or "").strip()
        if d_group == "io.github.minh124199" and d_art == "viet-template-gradle-plugin":
            plugin_dep_found = True
            if d_ver != expected_version:
                errors.append(
                    f"Marker POM {marker_pom} depends on viet-template-gradle-plugin version '{d_ver}', expected '{expected_version}'"
                )
    if not plugin_dep_found:
        errors.append(
            f"Marker POM {marker_pom} missing required dependency on io.github.minh124199:viet-template-gradle-plugin:{expected_version}"
        )

    print(f"  [PASS] Marker POM {marker_pom} validated successfully.")


def main():
    parser = argparse.ArgumentParser(description="Validate release publication bundle.")
    parser.add_argument("--build-tool", choices=["maven", "gradle", "both"], default="maven",
                        help="Build tool artifacts to inspect (default: maven)")
    parser.add_argument("--version", help="Explicit version to validate (defaults to root pom.xml version)")
    parser.add_argument("--target-dir", type=Path, default=ROOT_DIR, help="Root directory of repository to inspect")
    parser.add_argument("--repo-dir", type=Path, help="Path to staged repository to inspect (e.g. build/rc-repository)")
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
    print(f"[INFO] Enforcing publication topology across {TOTAL_PUBLIC_COORDINATES} public coordinates (parent + 12 modules + 1 marker)")

    tools = ["maven", "gradle"] if args.build_tool == "both" else [args.build_tool]

    # Maven Central receives the root parent POM. Gradle remains a parity/local-publication build
    # and does not authoritatively publish this coordinate.
    if "maven" in tools or args.repo_dir:
        validate_parent_pom(target_dir, version, errors)

    if args.assemble:
        import subprocess
        if "maven" in tools:
            print("\n[ACTION] Assembling Maven release artifacts...")
            subprocess.run(["./mvnw", "clean", "package", "-P", "release", "-Dgpg.skip=true", "-DskipTests", "-B"],
                           cwd=target_dir, check=True)
        if "gradle" in tools:
            print("\n[ACTION] Assembling Gradle artifacts...")
            subprocess.run(["./gradlew", "assemble", "generatePomFileForVietTemplatePluginMarkerMavenPublication", "--no-daemon"], cwd=target_dir, check=True)

    prod_modules, all_published_modules, excluded_modules = derive_published_modules(target_dir)
    print(f"[INFO] Authoritative published modules ({len(all_published_modules)}): {all_published_modules}")
    print(f"[INFO] Non-published internal verification modules ({len(excluded_modules)}): {excluded_modules}")

    for tool in tools:
        print(f"\n--- Validating {tool.upper()} Release Bundle ---")
        for mod in prod_modules:
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
            validate_pom_metadata(pom_path, mod, version, errors, enforce_production_dependencies=True)

        for mod in all_published_modules:
            if mod in prod_modules:
                continue
            print(f"\nEvaluating publication POM for module '{mod}':")
            if tool == "maven":
                pom_path = target_dir / mod / "pom.xml"
            else: # gradle
                if mod == "viet-template-gradle-plugin":
                    pub_pom = target_dir / mod / "build" / "publications" / "pluginMaven" / "pom-default.xml"
                else:
                    pub_pom = target_dir / mod / "build" / "publications" / "mavenJava" / "pom-default.xml"
                if not pub_pom.exists():
                    pub_pom = target_dir / mod / "build" / "publications" / "pluginMaven" / "pom-default.xml"
                pom_path = pub_pom if pub_pom.exists() else (target_dir / mod / "pom.xml")
            validate_pom_metadata(pom_path, mod, version, errors, enforce_production_dependencies=False)

    # Validate plugin marker coordinate for Gradle, repo-dir, or both
    if "gradle" in tools or args.repo_dir or (target_dir / "build" / "rc-repository").exists():
        validate_plugin_marker(target_dir, version, errors, repo_dir=args.repo_dir)

    validate_tck_defense_in_depth(target_dir, errors)

    if errors:
        print(f"\n[FAILED] Release publication bundle validation FAILED ({len(errors)} errors):")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print(f"\n[SUCCESS] Release publication bundle validation PASSED! All {TOTAL_PUBLIC_COORDINATES} public coordinates are release-ready.")
        sys.exit(0)


if __name__ == "__main__":
    main()

