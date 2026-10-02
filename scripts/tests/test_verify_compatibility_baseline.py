#!/usr/bin/env python3
"""
scripts/tests/test_verify_compatibility_baseline.py

Unit tests for scripts/verify-compatibility-baseline.py:
1. Baseline parsing and validation against real repository state (PASS).
2. Missing baseline file handling (FAIL-CLOSED, exit 1).
3. Malformed JSON structure handling (FAIL-CLOSED, exit 1).
4. Schema validation and required sections (FAIL-CLOSED on drift).
5. Removed/modified API types or classifications (FAIL-CLOSED).
6. Removed/modified Runtime ABI types or methods (FAIL-CLOSED).
7. Removed/modified diagnostic codes (FAIL-CLOSED).
8. Removed/modified framework entrypoints, cross-module contracts, or TCK features (FAIL-CLOSED).
9. CLI execution verification.
"""

from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT_PATH = REPO_ROOT / "scripts" / "verify-compatibility-baseline.py"
BASELINE_PATH = REPO_ROOT / "config" / "compatibility" / "1.0.0-compatibility-baseline.json"

spec = importlib.util.spec_from_file_location("verify_compatibility_baseline", SCRIPT_PATH)
vcb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vcb)


class BaselineCleanRepoTests(unittest.TestCase):
    """Verifies that clean repository state passes all baseline checks."""

    def test_canonical_baseline_file_exists(self):
        self.assertTrue(BASELINE_PATH.is_file(), f"Canonical baseline missing at {BASELINE_PATH}")

    def test_validate_schema_passes_on_canonical_baseline(self):
        with open(BASELINE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        errors = vcb.validate_baseline_schema(data)
        self.assertEqual(errors, [], f"Schema validation failed: {errors}")

    def test_verify_compatibility_baseline_passes_on_repository(self):
        passed, section_errors, report = vcb.verify_compatibility_baseline(
            baseline_path=BASELINE_PATH,
            repo_root=REPO_ROOT,
            check_git=True,
            verbose=False,
        )
        self.assertTrue(passed, f"Baseline verification failed with errors: {section_errors}")
        self.assertEqual(section_errors, {})
        self.assertEqual(report.get("verdict"), "PASSED")
        self.assertTrue(report.get("passed"))

    def test_cli_execution_succeeds_on_clean_repository(self):
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), "--baseline", str(BASELINE_PATH)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            proc.returncode,
            0,
            f"CLI exited with {proc.returncode}.\nSTDOUT: {proc.stdout}\nSTDERR: {proc.stderr}",
        )
        self.assertIn("[SUCCESS]", proc.stdout)


class BaselineMissingAndMalformedTests(unittest.TestCase):
    """Verifies fail-closed behavior on missing or malformed baseline files."""

    def test_missing_baseline_file_fails(self):
        non_existent = REPO_ROOT / "config" / "compatibility" / "non-existent-baseline.json"
        passed, section_errors, report = vcb.verify_compatibility_baseline(
            baseline_path=non_existent,
            repo_root=REPO_ROOT,
            check_git=False,
        )
        self.assertFalse(passed)
        self.assertIn("baselineFile", section_errors)
        self.assertEqual(report.get("verdict"), "FAILED")
        self.assertFalse(report.get("passed"))

    def test_cli_missing_baseline_file_exits_one(self):
        non_existent = REPO_ROOT / "config" / "compatibility" / "non-existent-baseline.json"
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), "--baseline", str(non_existent)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(proc.returncode, 1)
        self.assertIn("[FAIL]", proc.stdout)
        self.assertIn("baselineFile", proc.stdout)

    def test_malformed_json_syntax_fails(self):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            f.write("{\n  \"version\": \"1.0.0\",\n  INVALID_JSON_HERE\n")
            temp_path = Path(f.name)

        try:
            passed, section_errors, report = vcb.verify_compatibility_baseline(
                baseline_path=temp_path,
                repo_root=REPO_ROOT,
                check_git=False,
            )
            self.assertFalse(passed)
            self.assertIn("baselineJson", section_errors)
            self.assertEqual(report.get("verdict"), "FAILED")
        finally:
            temp_path.unlink(missing_ok=True)

    def test_root_not_dict_fails_schema_and_verification(self):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            f.write(json.dumps(["not", "an", "object"]))
            temp_path = Path(f.name)

        try:
            passed, section_errors, report = vcb.verify_compatibility_baseline(
                baseline_path=temp_path,
                repo_root=REPO_ROOT,
                check_git=False,
            )
            self.assertFalse(passed)
            self.assertIn("schema", section_errors)
            self.assertEqual(report.get("verdict"), "FAILED")
        finally:
            temp_path.unlink(missing_ok=True)


class BaselineSchemaDriftTests(unittest.TestCase):
    """Verifies schema drift detection."""

    def setUp(self):
        with open(BASELINE_PATH, "r", encoding="utf-8") as f:
            self.base_data = json.load(f)

    def test_missing_required_sections_fail(self):
        required_sections = [
            "provenance",
            "publicSurface",
            "generatedRuntimeAbi",
            "diagnosticCodes",
            "frameworkEntrypoints",
            "crossModuleContracts",
            "tckLanguageFeatures",
        ]
        for sec in required_sections:
            corrupted = copy.deepcopy(self.base_data)
            del corrupted[sec]
            errors = vcb.validate_baseline_schema(corrupted)
            self.assertTrue(
                any(f"Baseline missing required section object: '{sec}'" in e for e in errors),
                f"Did not detect missing section: {sec}",
            )

    def test_version_mismatch_fails_schema(self):
        corrupted = copy.deepcopy(self.base_data)
        corrupted["version"] = "2.0.0"
        errors = vcb.validate_baseline_schema(corrupted)
        self.assertTrue(any("Root 'version' must be '1.0.0'" in e for e in errors))

    def test_provenance_mismatch_fails_schema(self):
        corrupted = copy.deepcopy(self.base_data)
        corrupted["provenance"]["commit"] = "0000000000000000000000000000000000000000"
        errors = vcb.validate_baseline_schema(corrupted)
        self.assertTrue(any("Provenance field 'commit' mismatch" in e for e in errors))


class BaselineComponentMutationTests(unittest.TestCase):
    """Verifies fail-closed detection when individual components are mutated."""

    def setUp(self):
        with open(BASELINE_PATH, "r", encoding="utf-8") as f:
            self.base_data = json.load(f)

    def test_removed_stable_api_type_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed = mutated["publicSurface"]["stableTypes"].pop()
        mutated["publicSurface"]["allPublicSurfaceTypes"].pop(removed, None)
        errors = vcb.verify_public_surface(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any(removed in e for e in errors))

    def test_extra_stable_api_type_fails(self):
        mutated = copy.deepcopy(self.base_data)
        phantom = "io.github.minh124199.viettemplate.api.PhantomType"
        mutated["publicSurface"]["stableTypes"].append(phantom)
        mutated["publicSurface"]["allPublicSurfaceTypes"][phantom] = "STABLE_API"
        errors = vcb.verify_public_surface(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any(phantom in e for e in errors))

    def test_category_drift_fails(self):
        mutated = copy.deepcopy(self.base_data)
        target = "io.github.minh124199.viettemplate.api.CompiledTemplate"
        mutated["publicSurface"]["allPublicSurfaceTypes"][target] = "EXPERIMENTAL"
        errors = vcb.verify_public_surface(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Category mismatches" in e for e in errors))

    def test_removed_abi_method_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed_method = mutated["generatedRuntimeAbi"]["methods"].pop()
        errors = vcb.verify_runtime_abi(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Unexpected Runtime ABI methods" in e and removed_method in e for e in errors))

    def test_extra_abi_method_fails(self):
        mutated = copy.deepcopy(self.base_data)
        fake_method = "io.github.minh124199.viettemplate.api.TemplateId.fakeMethod()"
        mutated["generatedRuntimeAbi"]["methods"].append(fake_method)
        errors = vcb.verify_runtime_abi(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Runtime ABI methods missing" in e and fake_method in e for e in errors))

    def test_removed_abi_type_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed_type = mutated["generatedRuntimeAbi"]["types"].pop()
        errors = vcb.verify_runtime_abi(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Unexpected Runtime ABI types" in e and removed_type in e for e in errors))

    def test_removed_diagnostic_code_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed_code = mutated["diagnosticCodes"]["codes"].pop()
        errors = vcb.verify_diagnostic_codes(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Unexpected diagnostic codes" in e and removed_code in e for e in errors))

    def test_extra_diagnostic_code_fails(self):
        mutated = copy.deepcopy(self.base_data)
        fake_code = "SECURITY:FAKE_VIOLATION_CODE"
        mutated["diagnosticCodes"]["codes"].append(fake_code)
        errors = vcb.verify_diagnostic_codes(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Diagnostic codes missing" in e and fake_code in e for e in errors))

    def test_removed_framework_entrypoint_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed = mutated["frameworkEntrypoints"]["entrypoints"].pop()
        errors = vcb.verify_framework_entrypoints(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Unexpected framework entrypoints" in e and removed["fqcn"] in e for e in errors))

    def test_removed_cross_module_contract_fails(self):
        mutated = copy.deepcopy(self.base_data)
        removed = mutated["crossModuleContracts"]["contracts"].pop()
        errors = vcb.verify_cross_module_contracts(mutated, REPO_ROOT)
        self.assertTrue(len(errors) > 0)
        self.assertTrue(any("Unexpected cross-module internal contracts" in e and removed["fqcn"] in e for e in errors))

    def test_historical_baseline_immutability(self):
        import hashlib
        import subprocess

        # 1. Verify 1.0.0-compatibility-baseline.json SHA-256 matches canonical frozen hash
        expected_manifest_hash = "c3a0d7d3ac00302263a4ccfc32f1302331f37a99a8af4655f7ecea05f424a99c"
        actual_manifest_hash = hashlib.sha256(BASELINE_PATH.read_bytes()).hexdigest()
        self.assertEqual(
            actual_manifest_hash,
            expected_manifest_hash,
            "1.0.0-compatibility-baseline.json was mutated! Historical baseline is immutable.",
        )

        # 2. Verify config/api-baseline/1.0/generated-template-runtime-abi.txt matches v1.0.0 tag
        hist_abi_file = REPO_ROOT / "config" / "api-baseline" / "1.0" / "generated-template-runtime-abi.txt"
        self.assertTrue(hist_abi_file.exists(), "Frozen 1.0 ABI baseline file must exist")
        expected_abi_hash = "a33eec726f8bce60ef37f7cef2384fb0ba2d99054d60fe648e22118f34de510a"
        actual_abi_hash = hashlib.sha256(hist_abi_file.read_bytes()).hexdigest()
        self.assertEqual(
            actual_abi_hash,
            expected_abi_hash,
            "1.0/generated-template-runtime-abi.txt was mutated! Must match v1.0.0 GA exactly.",
        )

        # 3. Direct comparison against git tag v1.0.0 if git available
        res = subprocess.run(
            ["git", "show", "v1.0.0:config/api-baseline/generated-template-runtime-abi.txt"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
        )
        if res.returncode == 0:
            self.assertEqual(
                hist_abi_file.read_text(encoding="utf-8"),
                res.stdout,
                "Frozen 1.0 ABI file must match tag v1.0.0 byte-for-byte",
            )

    def test_frozen_1_0_runtime_abi_has_exact_22_methods_and_7_types(self):
        abi = self.base_data.get("generatedRuntimeAbi", {})
        self.assertEqual(abi["typesCount"], 7)
        self.assertEqual(abi["methodsCount"], 22)
        self.assertEqual(abi["fieldsCount"], 0)
        self.assertEqual(len(abi["types"]), 7)
        self.assertEqual(len(abi["methods"]), 22)


if __name__ == "__main__":
    unittest.main()
