"""Unit tests for verify-vscode-extension.py governance script."""

import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "verify_vscode_extension",
    ROOT / "scripts" / "verify-vscode-extension.py",
)
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

verify_package_json = mod.verify_package_json
verify_lockfile = mod.verify_lockfile
verify_language_configuration = mod.verify_language_configuration
verify_textmate_grammar = mod.verify_textmate_grammar
verify_no_hardcoded_paths = mod.verify_no_hardcoded_paths
verify_vscode_extension = mod.verify_vscode_extension
DEFAULT_VSCODE_DIR = mod.DEFAULT_VSCODE_DIR


class VerifyVsCodeExtensionTests(unittest.TestCase):
    def test_actual_repository_extension_passes(self):
        result = verify_vscode_extension(DEFAULT_VSCODE_DIR)
        self.assertTrue(
            result["passed"],
            f"Expected verify_vscode_extension to pass on repository directory, but got errors:\n{result['errors']}",
        )
        self.assertEqual(len(result["errors"]), 0)

    def test_manifest_validation_detects_missing_fields(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            pkg_path = Path(tmp_dir) / "package.json"
            pkg_path.write_text(json.dumps({"name": "wrong-name"}), encoding="utf-8")
            errors = verify_package_json(pkg_path)
            self.assertTrue(any("wrong-name" in e for e in errors))
            self.assertTrue(any("displayName" in e for e in errors))

    def test_manifest_validation_detects_missing_file_associations(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            pkg_path = Path(tmp_dir) / "package.json"
            valid_manifest = json.loads(
                (DEFAULT_VSCODE_DIR / "package.json").read_text(encoding="utf-8")
            )
            # Remove .vt extension
            valid_manifest["contributes"]["languages"][0]["extensions"] = [".vtl", ".vm"]
            pkg_path.write_text(json.dumps(valid_manifest), encoding="utf-8")

            errors = verify_package_json(pkg_path)
            self.assertTrue(any("missing extensions" in e and ".vt" in e for e in errors))

    def test_lockfile_validation(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            lock_path = Path(tmp_dir) / "package-lock.json"
            # Non-existent
            errors = verify_lockfile(lock_path)
            self.assertTrue(any("not found" in e for e in errors))

            # Invalid version
            lock_path.write_text(
                json.dumps({"name": "viet-template", "lockfileVersion": 1}),
                encoding="utf-8",
            )
            errors = verify_lockfile(lock_path)
            self.assertTrue(any("lockfileVersion must be >= 2" in e for e in errors))

    def test_language_configuration_validation(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            cfg_path = Path(tmp_dir) / "language-configuration.json"
            cfg_path.write_text(json.dumps({"comments": {}}), encoding="utf-8")
            errors = verify_language_configuration(cfg_path)
            self.assertTrue(any("lineComment" in e for e in errors))

    def test_textmate_grammar_validation(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            grammar_path = Path(tmp_dir) / "viet-template.tmLanguage.json"
            grammar_path.write_text(
                json.dumps({"scopeName": "wrong.scope"}), encoding="utf-8"
            )
            errors = verify_textmate_grammar(grammar_path)
            self.assertTrue(any("scopeName" in e for e in errors))

    def test_hardcoded_path_detection(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_dir = Path(tmp_dir)
            clean_file = test_dir / "clean.ts"
            clean_file.write_text("const x = 'relative/path';", encoding="utf-8")

            bad_file = test_dir / "bad.ts"
            bad_file.write_text("const path = '/home/username/secret';", encoding="utf-8")

            errors = verify_no_hardcoded_paths(test_dir)
            self.assertEqual(len(errors), 1)
            self.assertIn("Forbidden hardcoded path detected", errors[0])
            self.assertIn("bad.ts", errors[0])


if __name__ == "__main__":
    unittest.main()
