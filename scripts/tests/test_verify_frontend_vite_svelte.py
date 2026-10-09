"""
scripts/tests/test_verify_frontend_vite_svelte.py

Unit tests for scripts/verify-frontend-vite-svelte.py enforcing error detection and invariants:
- Manifest missing
- Build output missing
- Expected entry missing
- Generated script missing
- Generated CSS missing
- Chunk/import missing
- Resolver qualification failure handling
- Successful fixture validation
"""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_frontend = load_script("verify-frontend-vite-svelte.py")
REQUIRED_ENTRIES = verify_frontend.REQUIRED_ENTRIES
extract_toolchain_versions = verify_frontend.extract_toolchain_versions
generate_compatibility_report = verify_frontend.generate_compatibility_report
main = verify_frontend.main
run_java_qualification = verify_frontend.run_java_qualification
validate_manifest_structure = verify_frontend.validate_manifest_structure



class VerifyFrontendViteSvelteTests(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.test_root = Path(self.temp_dir.name)
        self.dist_dir = self.test_root / "dist"
        self.dist_dir.mkdir(parents=True, exist_ok=True)
        self.assets_dir = self.dist_dir / "assets"
        self.assets_dir.mkdir(parents=True, exist_ok=True)

    def tearDown(self):
        self.temp_dir.cleanup()

    def create_valid_manifest_and_files(self) -> dict:
        """Creates a valid manifest and matching files on disk."""
        (self.assets_dir / "employees-123.js").write_text("// js", encoding="utf-8")
        (self.assets_dir / "employees-123.css").write_text("/* css */", encoding="utf-8")
        (self.assets_dir / "counter-456.js").write_text("// js", encoding="utf-8")
        (self.assets_dir / "counter-456.css").write_text("/* css */", encoding="utf-8")
        (self.assets_dir / "payroll-789.js").write_text("// js", encoding="utf-8")
        (self.assets_dir / "payroll-789.css").write_text("/* css */", encoding="utf-8")
        (self.assets_dir / "shared-chunk.js").write_text("// chunk", encoding="utf-8")

        manifest = {
            "_shared-chunk.js": {
                "file": "assets/shared-chunk.js",
            },
            "src/pages/employees/index.ts": {
                "file": "assets/employees-123.js",
                "isEntry": True,
                "css": ["assets/employees-123.css"],
                "imports": ["_shared-chunk.js"],
            },
            "src/pages/counter/index.ts": {
                "file": "assets/counter-456.js",
                "isEntry": True,
                "css": ["assets/counter-456.css"],
                "imports": ["_shared-chunk.js"],
            },
            "src/pages/payroll/Payroll.svelte": {
                "file": "assets/payroll-789.js",
                "isEntry": True,
                "css": ["assets/payroll-789.css"],
            },
        }
        return manifest

    def test_successful_fixture_validation(self):
        manifest = self.create_valid_manifest_and_files()
        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertEqual([], errors, f"Expected 0 errors for valid fixture, got: {errors}")

    def test_missing_expected_entry(self):
        manifest = self.create_valid_manifest_and_files()
        del manifest["src/pages/employees/index.ts"]

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("src/pages/employees/index.ts' missing" in e for e in errors))

    def test_missing_generated_script_file(self):
        manifest = self.create_valid_manifest_and_files()
        (self.assets_dir / "employees-123.js").unlink()

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("Generated JS file for 'src/pages/employees/index.ts' does not exist" in e for e in errors))

    def test_missing_generated_css_file(self):
        manifest = self.create_valid_manifest_and_files()
        (self.assets_dir / "employees-123.css").unlink()

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("Generated CSS file for 'src/pages/employees/index.ts' does not exist" in e for e in errors))

    def test_missing_imported_chunk_in_manifest(self):
        manifest = self.create_valid_manifest_and_files()
        del manifest["_shared-chunk.js"]

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("imports chunk '_shared-chunk.js' which is not in manifest" in e for e in errors))

    def test_missing_imported_chunk_file_on_disk(self):
        manifest = self.create_valid_manifest_and_files()
        (self.assets_dir / "shared-chunk.js").unlink()

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("Physical chunk file for '_shared-chunk.js' does not exist" in e for e in errors))

    def test_empty_or_non_dict_manifest(self):
        errors_empty = validate_manifest_structure({}, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("empty" in e for e in errors_empty))

        errors_non_dict = validate_manifest_structure([], self.dist_dir, REQUIRED_ENTRIES)  # type: ignore
        self.assertTrue(any("must be a JSON object" in e for e in errors_non_dict))

    def test_entry_without_is_entry_flag(self):
        manifest = self.create_valid_manifest_and_files()
        manifest["src/pages/employees/index.ts"]["isEntry"] = False

        errors = validate_manifest_structure(manifest, self.dist_dir, REQUIRED_ENTRIES)
        self.assertTrue(any("does not have 'isEntry: true'" in e for e in errors))

    def test_extract_toolchain_versions(self):
        pkg_json = self.test_root / "package.json"
        pkg_json.write_text(
            json.dumps(
                {
                    "devDependencies": {
                        "vite": "5.4.2",
                        "svelte": "4.2.19",
                        "@sveltejs/vite-plugin-svelte": "3.1.2",
                        "typescript": "5.5.4",
                    }
                }
            ),
            encoding="utf-8",
        )
        versions = extract_toolchain_versions(self.test_root)
        self.assertEqual("5.4.2", versions["vite"])
        self.assertEqual("4.2.19", versions["svelte"])
        self.assertEqual("3.1.2", versions["vitePluginSvelte"])
        self.assertEqual("5.5.4", versions["typescript"])

    def test_generate_compatibility_report(self):
        manifest = self.create_valid_manifest_and_files()
        report_file = self.test_root / "report.json"
        report = generate_compatibility_report(
            node_version="v22.23.3",
            npm_version="10.9.9",
            toolchain={"vite": "5.4.2", "svelte": "4.2.19", "vitePluginSvelte": "3.1.2", "typescript": "5.5.4"},
            manifest_path=self.dist_dir / ".vite" / "manifest.json",
            manifest_data=manifest,
            report_path=report_file,
        )
        self.assertEqual("PASS", report["status"])
        self.assertEqual("v22.23.3", report["nodeVersion"])
        self.assertEqual(3, len(report["entries"]))
        self.assertEqual("src/pages/employees/index.ts", report["entry"])
        self.assertTrue(report_file.is_file())

    def test_generate_compatibility_report_failure(self):
        report_file = self.test_root / "failure-report.json"
        report = generate_compatibility_report(
            node_version="v22.23.3",
            npm_version="10.9.9",
            toolchain={"vite": "5.4.2", "svelte": "4.2.19", "vitePluginSvelte": "3.1.2", "typescript": "5.5.4"},
            manifest_path=self.dist_dir / ".vite" / "manifest.json",
            manifest_data=None,
            report_path=report_file,
            status="FAIL",
            errors=["Simulated failure"],
        )
        self.assertEqual("FAIL", report["status"])
        self.assertEqual(["Simulated failure"], report["errors"])
        self.assertTrue(report_file.is_file())
        with open(report_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertEqual("FAIL", data["status"])

    @patch("subprocess.run")
    def test_run_java_qualification_failure_raises(self, mock_run):
        mock_res = MagicMock()
        mock_res.returncode = 1
        mock_res.stdout = "Failed test"
        mock_res.stderr = "AssertionError"
        mock_run.return_value = mock_res

        with self.assertRaises(RuntimeError):
            run_java_qualification(self.test_root, self.test_root / "manifest.json")

    def test_main_missing_manifest_returns_error(self):
        non_existent_manifest = self.test_root / "non-existent-manifest.json"
        report_file = self.test_root / "report.json"
        code = main([
            "--example-dir", str(self.test_root),
            "--manifest-path", str(non_existent_manifest),
            "--report-path", str(report_file),
            "--skip-install",
            "--skip-build",
            "--skip-java",
        ])
        self.assertNotEqual(0, code)
        self.assertTrue(report_file.is_file())
        with open(report_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertEqual("FAIL", data["status"])

    def test_main_invalid_manifest_writes_failure_report(self):
        pkg_json = self.test_root / "package.json"
        pkg_json.write_text(
            json.dumps(
                {
                    "devDependencies": {
                        "vite": "5.4.2",
                        "svelte": "4.2.19",
                        "@sveltejs/vite-plugin-svelte": "3.1.2",
                        "typescript": "5.5.4",
                    }
                }
            ),
            encoding="utf-8",
        )
        manifest = self.create_valid_manifest_and_files()
        del manifest["src/pages/employees/index.ts"]
        manifest_file = self.dist_dir / ".vite" / "manifest.json"
        manifest_file.parent.mkdir(parents=True, exist_ok=True)
        manifest_file.write_text(json.dumps(manifest), encoding="utf-8")
        report_file = self.test_root / "report.json"
        code = main([
            "--example-dir", str(self.test_root),
            "--manifest-path", str(manifest_file),
            "--report-path", str(report_file),
            "--skip-install",
            "--skip-build",
            "--skip-java",
        ])
        self.assertNotEqual(0, code)
        self.assertTrue(report_file.is_file())
        with open(report_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertEqual("FAIL", data["status"])
        self.assertTrue(any("src/pages/employees/index.ts" in err for err in data.get("errors", [])))


if __name__ == "__main__":
    unittest.main()
