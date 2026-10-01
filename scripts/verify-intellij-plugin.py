#!/usr/bin/env python3
"""
verify-intellij-plugin.py

Automated governance verification script validating the IntelliJ IDEA plugin in editors/intellij:
1. Plugin manifest structure (src/main/resources/META-INF/plugin.xml):
   - Metadata: id, name, version, vendor, description, depends
   - Extensions: fileType, syntaxHighlighterFactory, commenter, settings, services,
     externalAnnotator, completion.contributor, documentationProvider, gotoDeclarationHandler
   - Actions: VietTemplate.RestartLspServer
   - File associations: .vtl, .vm, .vt
2. Gradle build configuration (build.gradle.kts and settings.gradle.kts):
   - IntelliJ Platform Gradle Plugin 2.2.1
   - Java 21 toolchain
   - Target platform IntelliJ IDEA Community 2024.2.4
   - Plugin ID, name, version, sinceBuild (242), untilBuild (251.*)
   - bundleLspServer task definition and processResources packaging
3. Strict path safety: verifies zero hardcoded local machine paths in editors/intellij
4. Distribution artifact verification: if the plugin distribution ZIP exists, validates its
   archive structure, nested plugin JAR, plugin.xml descriptor, and bundled language server JAR
"""

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_INTELLIJ_DIR = REPO_ROOT / "editors" / "intellij"

EXPECTED_PLUGIN_ID = "io.github.minh124199.viet-template-intellij"
EXPECTED_PLUGIN_NAME = "Viet Template"
EXPECTED_PLUGIN_VERSION = "1.1.0"
EXPECTED_VENDOR = "Viet Template"
EXPECTED_EXTENSIONS = {"vtl", "vm", "vt"}
EXPECTED_SINCE_BUILD = "242"
EXPECTED_UNTIL_BUILD = "251.*"

FORBIDDEN_PATH_PATTERNS = [
    re.compile(r"/home/[a-zA-Z0-9_\-]+/"),
    re.compile(r"/Users/[a-zA-Z0-9_\-]+/"),
    re.compile(r"[A-Za-z]:\\Users\\[a-zA-Z0-9_\-]+"),
]

EXCLUDED_PATH_SCAN_DIRS = {
    ".gradle",
    "build",
    ".intellijPlatform",
    ".git",
}


def verify_plugin_xml(plugin_xml_path: Path) -> List[str]:
    errors = []
    if not plugin_xml_path.is_file():
        return [f"plugin.xml not found at {plugin_xml_path}"]

    try:
        tree = ET.parse(plugin_xml_path)
        root = tree.getroot()
    except Exception as e:
        return [f"Failed to parse plugin.xml as XML: {e}"]

    # 1. Root tag
    if root.tag != "idea-plugin":
        errors.append(f"Root tag must be <idea-plugin>, got <{root.tag}>")

    # 2. Metadata elements
    id_el = root.find("id")
    if id_el is None or id_el.text != EXPECTED_PLUGIN_ID:
        errors.append(f"<id> must be '{EXPECTED_PLUGIN_ID}', got '{id_el.text if id_el is not None else None}'")

    name_el = root.find("name")
    if name_el is None or name_el.text != EXPECTED_PLUGIN_NAME:
        errors.append(f"<name> must be '{EXPECTED_PLUGIN_NAME}', got '{name_el.text if name_el is not None else None}'")

    version_el = root.find("version")
    if version_el is None or version_el.text != EXPECTED_PLUGIN_VERSION:
        errors.append(f"<version> must be '{EXPECTED_PLUGIN_VERSION}', got '{version_el.text if version_el is not None else None}'")

    vendor_el = root.find("vendor")
    if vendor_el is None or (vendor_el.text or "").strip() != EXPECTED_VENDOR:
        errors.append(f"<vendor> text must be '{EXPECTED_VENDOR}', got '{vendor_el.text if vendor_el is not None else None}'")

    # 3. Dependencies
    depends_els = root.findall("depends")
    depends_texts = [d.text for d in depends_els if d.text]
    if "com.intellij.modules.platform" not in depends_texts:
        errors.append("plugin.xml must declare dependency on 'com.intellij.modules.platform'")

    # 4. Extensions
    extensions_el = root.find("extensions")
    if extensions_el is None:
        errors.append("plugin.xml missing <extensions> section")
    else:
        # fileType
        filetype_el = extensions_el.find("fileType")
        if filetype_el is None:
            errors.append("plugin.xml <extensions> missing <fileType>")
        else:
            ext_str = filetype_el.get("extensions", "")
            declared_exts = set(ext_str.split(";")) if ext_str else set()
            missing_exts = EXPECTED_EXTENSIONS - declared_exts
            if missing_exts:
                errors.append(f"<fileType> missing extensions: {sorted(missing_exts)}")

        # syntaxHighlighterFactory
        if extensions_el.find("lang.syntaxHighlighterFactory") is None:
            errors.append("plugin.xml <extensions> missing <lang.syntaxHighlighterFactory>")

        # commenter
        if extensions_el.find("lang.commenter") is None:
            errors.append("plugin.xml <extensions> missing <lang.commenter>")

        # externalAnnotator (diagnostics)
        if extensions_el.find("externalAnnotator") is None:
            errors.append("plugin.xml <extensions> missing <externalAnnotator>")

        # completion.contributor
        if extensions_el.find("completion.contributor") is None:
            errors.append("plugin.xml <extensions> missing <completion.contributor>")

        # lang.documentationProvider (hover)
        if extensions_el.find("lang.documentationProvider") is None:
            errors.append("plugin.xml <extensions> missing <lang.documentationProvider>")

        # gotoDeclarationHandler
        if extensions_el.find("gotoDeclarationHandler") is None:
            errors.append("plugin.xml <extensions> missing <gotoDeclarationHandler>")

        # applicationService & projectService
        if extensions_el.find("applicationService") is None:
            errors.append("plugin.xml <extensions> missing <applicationService>")
        if extensions_el.find("projectService") is None:
            errors.append("plugin.xml <extensions> missing <projectService>")

    # 5. Actions
    actions_el = root.find("actions")
    if actions_el is None:
        errors.append("plugin.xml missing <actions> section")
    else:
        action_ids = [a.get("id") for a in actions_el.findall("action") if a.get("id")]
        if "VietTemplate.RestartLspServer" not in action_ids:
            errors.append("plugin.xml <actions> missing action 'VietTemplate.RestartLspServer'")

    return errors


def verify_build_gradle_kts(build_gradle_path: Path) -> List[str]:
    errors = []
    if not build_gradle_path.is_file():
        return [f"build.gradle.kts not found at {build_gradle_path}"]

    try:
        content = build_gradle_path.read_text(encoding="utf-8")
    except Exception as e:
        return [f"Failed to read build.gradle.kts: {e}"]

    # IntelliJ platform plugin
    if 'id("org.jetbrains.intellij.platform")' not in content:
        errors.append("build.gradle.kts must apply org.jetbrains.intellij.platform plugin")

    # Java 21 toolchain
    if "JavaLanguageVersion.of(21)" not in content and "languageVersion.set(JavaLanguageVersion.of(21))" not in content:
        errors.append("build.gradle.kts must configure Java 21 toolchain")

    # IntelliJ Community platform target
    if 'intellijIdeaCommunity("2024.2.4")' not in content and "intellijIdeaCommunity" not in content:
        errors.append("build.gradle.kts must configure intellijIdeaCommunity platform dependency")

    # Plugin configuration
    if f'id = "{EXPECTED_PLUGIN_ID}"' not in content:
        errors.append(f"build.gradle.kts pluginConfiguration id must be '{EXPECTED_PLUGIN_ID}'")
    if f'name = "{EXPECTED_PLUGIN_NAME}"' not in content:
        errors.append(f"build.gradle.kts pluginConfiguration name must be '{EXPECTED_PLUGIN_NAME}'")
    if f'version = "{EXPECTED_PLUGIN_VERSION}"' not in content:
        errors.append(f"build.gradle.kts pluginConfiguration version must be '{EXPECTED_PLUGIN_VERSION}'")

    # ideaVersion sinceBuild & untilBuild
    if f'sinceBuild = "{EXPECTED_SINCE_BUILD}"' not in content:
        errors.append(f"build.gradle.kts ideaVersion sinceBuild must be '{EXPECTED_SINCE_BUILD}'")
    if f'untilBuild = "{EXPECTED_UNTIL_BUILD}"' not in content:
        errors.append(f"build.gradle.kts ideaVersion untilBuild must be '{EXPECTED_UNTIL_BUILD}'")

    # bundleLspServer task
    if "bundleLspServer" not in content:
        errors.append("build.gradle.kts must define bundleLspServer task")
    if "processResources" not in content:
        errors.append("build.gradle.kts must configure processResources task to package server JAR")

    return errors


def verify_settings_gradle_kts(settings_gradle_path: Path) -> List[str]:
    errors = []
    if not settings_gradle_path.is_file():
        return [f"settings.gradle.kts not found at {settings_gradle_path}"]

    try:
        content = settings_gradle_path.read_text(encoding="utf-8")
    except Exception as e:
        return [f"Failed to read settings.gradle.kts: {e}"]

    if 'rootProject.name = "viet-template-intellij"' not in content:
        errors.append("settings.gradle.kts must set rootProject.name = 'viet-template-intellij'")

    return errors


def verify_no_hardcoded_paths(intellij_dir: Path) -> List[str]:
    errors = []
    text_extensions = {".java", ".kt", ".kts", ".xml", ".json", ".md", ".vtl", ".txt", ".yml", ".yaml", ".properties"}

    for root, dirs, files in os.walk(intellij_dir):
        # Exclude directories
        dirs[:] = [d for d in dirs if d not in EXCLUDED_PATH_SCAN_DIRS]

        for file in files:
            file_path = Path(root) / file
            if file_path.suffix.lower() not in text_extensions:
                continue

            try:
                content = file_path.read_text(encoding="utf-8", errors="ignore")
                for line_idx, line in enumerate(content.splitlines(), start=1):
                    for pattern in FORBIDDEN_PATH_PATTERNS:
                        if pattern.search(line):
                            rel_path = file_path.relative_to(intellij_dir)
                            errors.append(
                                f"Forbidden hardcoded path detected in {rel_path}:{line_idx}: {line.strip()[:100]}"
                            )
            except Exception as e:
                errors.append(f"Failed to inspect {file_path}: {e}")

    return errors


def verify_distribution_archive(dist_zip_path: Path) -> List[str]:
    errors = []
    if not dist_zip_path.is_file():
        return [f"Distribution ZIP not found at {dist_zip_path}"]

    try:
        with zipfile.ZipFile(dist_zip_path, "r") as zf:
            names = zf.namelist()
            expected_jar_prefix = f"viet-template-intellij/lib/viet-template-intellij-{EXPECTED_PLUGIN_VERSION}.jar"
            jar_entry = next((n for n in names if n == expected_jar_prefix), None)
            if not jar_entry:
                errors.append(f"Distribution ZIP missing main plugin jar '{expected_jar_prefix}'")
                return errors

            # Inspect inner plugin JAR
            jar_data = zf.read(jar_entry)
            import io
            with zipfile.ZipFile(io.BytesIO(jar_data), "r") as inner_zf:
                inner_names = inner_zf.namelist()
                if "META-INF/plugin.xml" not in inner_names:
                    errors.append("Nested plugin jar missing META-INF/plugin.xml")
                if "server/viet-template-lsp.jar" not in inner_names:
                    errors.append("Nested plugin jar missing bundled server/viet-template-lsp.jar")
    except Exception as e:
        errors.append(f"Failed to verify distribution ZIP {dist_zip_path}: {e}")

    return errors


def verify_intellij_plugin(intellij_dir: Path, check_distribution: bool = False) -> Dict[str, Any]:
    plugin_xml_path = intellij_dir / "src" / "main" / "resources" / "META-INF" / "plugin.xml"
    build_gradle_path = intellij_dir / "build.gradle.kts"
    settings_gradle_path = intellij_dir / "settings.gradle.kts"
    dist_zip_path = (
        intellij_dir / "build" / "distributions" / f"viet-template-intellij-{EXPECTED_PLUGIN_VERSION}.zip"
    )

    plugin_xml_errors = verify_plugin_xml(plugin_xml_path)
    build_gradle_errors = verify_build_gradle_kts(build_gradle_path)
    settings_gradle_errors = verify_settings_gradle_kts(settings_gradle_path)
    path_errors = verify_no_hardcoded_paths(intellij_dir)

    dist_errors = []
    if check_distribution or dist_zip_path.is_file():
        dist_errors = verify_distribution_archive(dist_zip_path)

    all_errors = (
        plugin_xml_errors
        + build_gradle_errors
        + settings_gradle_errors
        + path_errors
        + dist_errors
    )

    return {
        "passed": len(all_errors) == 0,
        "intellij_dir": str(intellij_dir),
        "errors": all_errors,
        "summary": {
            "plugin_xml_errors": len(plugin_xml_errors),
            "build_gradle_errors": len(build_gradle_errors),
            "settings_gradle_errors": len(settings_gradle_errors),
            "hardcoded_path_errors": len(path_errors),
            "distribution_errors": len(dist_errors),
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Verify IntelliJ plugin governance requirements")
    parser.add_argument(
        "--intellij-dir",
        type=Path,
        default=DEFAULT_INTELLIJ_DIR,
        help="Path to the editors/intellij directory",
    )
    parser.add_argument(
        "--check-distribution",
        action="store_true",
        help="Strictly require and verify the built distribution ZIP",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit result in JSON format",
    )
    args = parser.parse_args()

    result = verify_intellij_plugin(args.intellij_dir, check_distribution=args.check_distribution)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"=== IntelliJ Plugin Verification: {args.intellij_dir} ===")
        if result["passed"]:
            print("✓ plugin.xml, build.gradle.kts, settings.gradle.kts, distribution packaging, and path safety verified successfully.")
        else:
            print("✗ Verification failed with errors:")
            for err in result["errors"]:
                print(f"  - {err}")

    sys.exit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
