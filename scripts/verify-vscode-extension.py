#!/usr/bin/env python3
"""
verify-vscode-extension.py

Automated governance verification script validating the VS Code extension in editors/vscode:
1. Extension manifest structure (package.json):
   - Metadata: name, displayName, version, publisher, engines, categories, activationEvents, main
   - Contributions: languages, grammars, commands, configuration settings
   - File associations: .vtl, .vm, .vt
2. Lockfile existence and validity (package-lock.json)
3. Language configuration validity (language-configuration.json)
4. TextMate grammar validity (syntaxes/viet-template.tmLanguage.json)
5. Strict path safety: verifies zero hardcoded local machine paths in editors/vscode source files
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_VSCODE_DIR = REPO_ROOT / "editors" / "vscode"

EXPECTED_EXTENSIONS = {".vtl", ".vm", ".vt"}
EXPECTED_CONFIG_PROPERTIES = {
    "vietTemplate.java.home",
    "vietTemplate.languageServer.jarPath",
    "vietTemplate.languageServer.trace",
    "vietTemplate.languageServer.vmArgs",
}

FORBIDDEN_PATH_PATTERNS = [
    re.compile(r"/home/[a-zA-Z0-9_\-]+/"),
    re.compile(r"/Users/[a-zA-Z0-9_\-]+/"),
    re.compile(r"[A-Za-z]:\\Users\\[a-zA-Z0-9_\-]+"),
]

EXCLUDED_PATH_SCAN_DIRS = {
    "node_modules",
    "out",
    ".git",
    "server",
    ".vscode-test",
}


def verify_package_json(package_json_path: Path) -> List[str]:
    errors = []
    if not package_json_path.is_file():
        return [f"package.json not found at {package_json_path}"]

    try:
        with open(package_json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [f"Failed to parse package.json as JSON: {e}"]

    # Basic metadata
    if data.get("name") != "viet-template":
        errors.append(f"package.json 'name' must be 'viet-template', got '{data.get('name')}'")
    if data.get("displayName") != "Viet Template Language Support":
        errors.append(
            f"package.json 'displayName' must be 'Viet Template Language Support', got '{data.get('displayName')}'"
        )
    if data.get("version") != "1.1.0":
        errors.append(f"package.json 'version' must be '1.1.0', got '{data.get('version')}'")
    if data.get("publisher") != "minh124199":
        errors.append(f"package.json 'publisher' must be 'minh124199', got '{data.get('publisher')}'")

    engines = data.get("engines", {})
    if engines.get("vscode") != "^1.85.0":
        errors.append(f"package.json engines.vscode must be '^1.85.0', got '{engines.get('vscode')}'")

    categories = data.get("categories", [])
    if "Programming Languages" not in categories:
        errors.append("package.json categories must include 'Programming Languages'")

    activation_events = data.get("activationEvents", [])
    if "onLanguage:viet-template" not in activation_events:
        errors.append("package.json activationEvents must include 'onLanguage:viet-template'")

    if data.get("main") != "./out/src/extension.js":
        errors.append(f"package.json 'main' must be './out/src/extension.js', got '{data.get('main')}'")

    contributes = data.get("contributes", {})
    if not isinstance(contributes, dict):
        errors.append("package.json contributes must be an object")
        return errors

    # Languages contribution
    languages = contributes.get("languages", [])
    vtl_lang = next((l for l in languages if l.get("id") == "viet-template"), None)
    if not vtl_lang:
        errors.append("package.json contributes.languages must define language 'viet-template'")
    else:
        lang_exts = set(vtl_lang.get("extensions", []))
        missing_exts = EXPECTED_EXTENSIONS - lang_exts
        if missing_exts:
            errors.append(f"package.json language 'viet-template' missing extensions: {sorted(missing_exts)}")
        if vtl_lang.get("configuration") != "./language-configuration.json":
            errors.append(
                f"package.json language configuration must be './language-configuration.json', got '{vtl_lang.get('configuration')}'"
            )

    # Grammars contribution
    grammars = contributes.get("grammars", [])
    vtl_grammar = next((g for g in grammars if g.get("language") == "viet-template"), None)
    if not vtl_grammar:
        errors.append("package.json contributes.grammars must define grammar for 'viet-template'")
    else:
        if vtl_grammar.get("scopeName") != "source.viet-template":
            errors.append(
                f"Grammar scopeName must be 'source.viet-template', got '{vtl_grammar.get('scopeName')}'"
            )
        if vtl_grammar.get("path") != "./syntaxes/viet-template.tmLanguage.json":
            errors.append(
                f"Grammar path must be './syntaxes/viet-template.tmLanguage.json', got '{vtl_grammar.get('path')}'"
            )

    # Commands contribution
    commands = contributes.get("commands", [])
    restart_cmd = next((c for c in commands if c.get("command") == "vietTemplate.restartServer"), None)
    if not restart_cmd:
        errors.append("package.json contributes.commands must define 'vietTemplate.restartServer'")
    else:
        if restart_cmd.get("category") != "Viet Template":
            errors.append(
                f"Command category must be 'Viet Template', got '{restart_cmd.get('category')}'"
            )

    # Configuration properties
    config_section = contributes.get("configuration", {})
    props = config_section.get("properties", {})
    missing_props = EXPECTED_CONFIG_PROPERTIES - set(props.keys())
    if missing_props:
        errors.append(f"package.json contributes.configuration.properties missing: {sorted(missing_props)}")

    return errors


def verify_lockfile(lockfile_path: Path) -> List[str]:
    errors = []
    if not lockfile_path.is_file():
        return [f"package-lock.json not found at {lockfile_path}"]

    try:
        with open(lockfile_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [f"Failed to parse package-lock.json as JSON: {e}"]

    if data.get("name") != "viet-template":
        errors.append(f"package-lock.json 'name' must be 'viet-template', got '{data.get('name')}'")

    if not data.get("lockfileVersion") or data.get("lockfileVersion") < 2:
        errors.append(f"package-lock.json lockfileVersion must be >= 2, got {data.get('lockfileVersion')}")

    return errors


def verify_language_configuration(config_path: Path) -> List[str]:
    errors = []
    if not config_path.is_file():
        return [f"language-configuration.json not found at {config_path}"]

    try:
        with open(config_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [f"Failed to parse language-configuration.json as JSON: {e}"]

    comments = data.get("comments", {})
    if comments.get("lineComment") != "##":
        errors.append(f"Comments lineComment must be '##', got '{comments.get('lineComment')}'")
    if comments.get("blockComment") != ["#*", "*#"]:
        errors.append(f"Comments blockComment must be ['#*', '*#'], got '{comments.get('blockComment')}'")

    brackets = data.get("brackets")
    if not isinstance(brackets, list) or len(brackets) < 3:
        errors.append("language-configuration.json must define bracket pairs for {}, [], ()")

    auto_closing = data.get("autoClosingPairs")
    if not isinstance(auto_closing, list) or len(auto_closing) < 5:
        errors.append("language-configuration.json must define auto-closing pairs")

    surrounding = data.get("surroundingPairs")
    if not isinstance(surrounding, list) or len(surrounding) < 5:
        errors.append("language-configuration.json must define surrounding pairs")

    indent = data.get("indentationRules", {})
    if not indent.get("increaseIndentPattern") or not indent.get("decreaseIndentPattern"):
        errors.append("language-configuration.json indentationRules must define increaseIndentPattern and decreaseIndentPattern")

    return errors


def verify_textmate_grammar(grammar_path: Path) -> List[str]:
    errors = []
    if not grammar_path.is_file():
        return [f"TextMate grammar not found at {grammar_path}"]

    try:
        with open(grammar_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [f"Failed to parse TextMate grammar as JSON: {e}"]

    if data.get("scopeName") != "source.viet-template":
        errors.append(f"Grammar scopeName must be 'source.viet-template', got '{data.get('scopeName')}'")

    patterns = data.get("patterns", [])
    if not patterns:
        errors.append("TextMate grammar patterns array must not be empty")

    repo = data.get("repository", {})
    required_repo_keys = {"comments", "directives", "references", "strings", "numbers", "operators"}
    missing_repo_keys = required_repo_keys - set(repo.keys())
    if missing_repo_keys:
        errors.append(f"TextMate grammar repository missing rules: {sorted(missing_repo_keys)}")

    return errors


def verify_no_hardcoded_paths(vscode_dir: Path) -> List[str]:
    errors = []
    text_extensions = {".ts", ".js", ".json", ".md", ".vtl", ".txt", ".yml", ".yaml"}

    for root, dirs, files in os.walk(vscode_dir):
        # Exclude directories
        dirs[:] = [d for d in dirs if d not in EXCLUDED_PATH_SCAN_DIRS]

        for file in files:
            file_path = Path(root) / file
            if file_path.suffix.lower() not in text_extensions:
                continue
            if file == "package-lock.json":
                continue  # package-lock may contain integrity hashes or npm internal urls

            try:
                content = file_path.read_text(encoding="utf-8", errors="ignore")
                for line_idx, line in enumerate(content.splitlines(), start=1):
                    for pattern in FORBIDDEN_PATH_PATTERNS:
                        if pattern.search(line):
                            rel_path = file_path.relative_to(vscode_dir)
                            errors.append(
                                f"Forbidden hardcoded path detected in {rel_path}:{line_idx}: {line.strip()[:100]}"
                            )
            except Exception as e:
                errors.append(f"Failed to inspect {file_path}: {e}")

    return errors


def verify_vscode_extension(vscode_dir: Path) -> Dict[str, Any]:
    manifest_path = vscode_dir / "package.json"
    lockfile_path = vscode_dir / "package-lock.json"
    lang_config_path = vscode_dir / "language-configuration.json"
    grammar_path = vscode_dir / "syntaxes" / "viet-template.tmLanguage.json"

    manifest_errors = verify_package_json(manifest_path)
    lockfile_errors = verify_lockfile(lockfile_path)
    lang_config_errors = verify_language_configuration(lang_config_path)
    grammar_errors = verify_textmate_grammar(grammar_path)
    path_errors = verify_no_hardcoded_paths(vscode_dir)

    all_errors = (
        manifest_errors
        + lockfile_errors
        + lang_config_errors
        + grammar_errors
        + path_errors
    )

    return {
        "passed": len(all_errors) == 0,
        "vscode_dir": str(vscode_dir),
        "errors": all_errors,
        "summary": {
            "manifest_errors": len(manifest_errors),
            "lockfile_errors": len(lockfile_errors),
            "language_configuration_errors": len(lang_config_errors),
            "grammar_errors": len(grammar_errors),
            "hardcoded_path_errors": len(path_errors),
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Verify VS Code extension governance requirements")
    parser.add_argument(
        "--vscode-dir",
        type=Path,
        default=DEFAULT_VSCODE_DIR,
        help="Path to the editors/vscode directory",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit result in JSON format",
    )
    args = parser.parse_args()

    result = verify_vscode_extension(args.vscode_dir)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"=== VS Code Extension Verification: {args.vscode_dir} ===")
        if result["passed"]:
            print("✓ Manifest, lockfile, grammar, language config, and path safety verified successfully.")
        else:
            print("✗ Verification failed with errors:")
            for err in result["errors"]:
                print(f"  - {err}")

    sys.exit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
