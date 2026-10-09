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
PROFILES = verify_frontend.PROFILES
extract_toolchain_versions = verify_frontend.extract_toolchain_versions
generate_compatibility_report = verify_frontend.generate_compatibility_report
main = verify_frontend.main
qualify_single_profile = verify_frontend.qualify_single_profile
resolve_profiles = verify_frontend.resolve_profiles
run_java_qualification = verify_frontend.run_java_qualification
validate_manifest_structure = verify_frontend.validate_manifest_structure
validate_profile_versions = verify_frontend.validate_profile_versions
discover_manifest = verify_frontend.discover_manifest




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

    def test_resolve_valid_legacy_profile(self):
        profs = resolve_profiles("vite5-svelte4")
        self.assertEqual(1, len(profs))
        p = profs[0]
        self.assertEqual("vite5-svelte4", p["id"])
        self.assertEqual(5, p["expected_vite_major"])
        self.assertEqual(4, p["expected_svelte_major"])
        self.assertEqual("rollup", p["bundler_generation"])

        # Test alias 'legacy'
        profs_alias = resolve_profiles("legacy")
        self.assertEqual("vite5-svelte4", profs_alias[0]["id"])

    def test_resolve_valid_current_profile(self):
        profs = resolve_profiles("vite8-svelte5")
        self.assertEqual(1, len(profs))
        p = profs[0]
        self.assertEqual("vite8-svelte5", p["id"])
        self.assertEqual(8, p["expected_vite_major"])
        self.assertEqual(5, p["expected_svelte_major"])
        self.assertEqual("rolldown", p["bundler_generation"])

        # Test alias 'current'
        profs_alias = resolve_profiles("current")
        self.assertEqual("vite8-svelte5", profs_alias[0]["id"])

    def test_resolve_all_profiles(self):
        profs = resolve_profiles("all")
        self.assertEqual(2, len(profs))
        ids = [p["id"] for p in profs]
        self.assertIn("vite5-svelte4", ids)
        self.assertIn("vite8-svelte5", ids)

    def test_resolve_unknown_profile_raises(self):
        with self.assertRaises(ValueError) as ctx:
            resolve_profiles("unknown-matrix-lane")
        self.assertIn("Unknown qualification profile 'unknown-matrix-lane'", str(ctx.exception))

    def test_main_unknown_profile_returns_failure(self):
        report_file = self.test_root / "unknown-report.json"
        code = main([
            "--profile", "unknown-profile-name",
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

    def test_wrong_pinned_major_detected(self):
        legacy_prof = PROFILES["vite5-svelte4"]
        mismatched_toolchain = {
            "vite": "6.0.0",
            "svelte": "4.2.19",
            "vitePluginSvelte": "3.1.2",
            "typescript": "5.5.4",
        }
        errors = validate_profile_versions(legacy_prof, mismatched_toolchain)
        self.assertTrue(any("Vite major version mismatch" in e for e in errors))

        current_prof = PROFILES["vite8-svelte5"]
        mismatched_svelte = {
            "vite": "8.3.4",
            "svelte": "4.2.19",
            "vitePluginSvelte": "7.3.1",
            "typescript": "5.8.3",
        }
        errors_svelte = validate_profile_versions(current_prof, mismatched_svelte)
        self.assertTrue(any("Svelte major version mismatch" in e for e in errors_svelte))

    def test_version_metadata_mismatch_detected(self):
        current_prof = PROFILES["vite8-svelte5"]
        mismatched_version = {
            "vite": "8.1.0",
            "svelte": "5.57.2",
            "vitePluginSvelte": "7.3.1",
            "typescript": "5.8.3",
        }
        errors = validate_profile_versions(current_prof, mismatched_version)
        self.assertTrue(any("Toolchain version mismatch for 'vite'" in e for e in errors))

    @patch("subprocess.run")
    def test_package_install_failure_handled(self, mock_run):
        mock_res = MagicMock()
        mock_res.returncode = 1
        mock_res.stderr = "npm ERR! 404 Not Found"
        mock_run.return_value = mock_res

        status, rep, errors = qualify_single_profile(
            profile=PROFILES["vite5-svelte4"],
            node_version="v22.23.3",
            npm_version="10.9.9",
            repo_root=self.test_root,
            skip_install=False,
            skip_build=False,
            skip_java=True,
        )
        self.assertEqual("FAIL", status)
        self.assertTrue(any("npm ci" in e for e in errors))

    @patch("subprocess.run")
    def test_package_build_failure_handled(self, mock_run):
        # Allow npm ci, fail npm run check
        def side_effect(cmd, **kwargs):
            m = MagicMock()
            if "ci" in cmd:
                m.returncode = 0
            elif "check" in cmd:
                m.returncode = 2
                m.stderr = "Type error: TS2304"
            else:
                m.returncode = 0
            return m

        mock_run.side_effect = side_effect

        status, rep, errors = qualify_single_profile(
            profile=PROFILES["vite8-svelte5"],
            node_version="v22.23.3",
            npm_version="10.9.9",
            repo_root=self.test_root,
            skip_install=False,
            skip_build=False,
            skip_java=True,
        )
        self.assertEqual("FAIL", status)
        self.assertTrue(any("npm run check" in e for e in errors))

    def test_java_qualification_failure_handled(self):
        with patch.object(verify_frontend, "run_java_qualification", side_effect=RuntimeError("Java qualification test failed with code 1")):
            manifest = self.create_valid_manifest_and_files()
            manifest_file = self.dist_dir / ".vite" / "manifest.json"
            manifest_file.parent.mkdir(parents=True, exist_ok=True)
            manifest_file.write_text(json.dumps(manifest), encoding="utf-8")

            prof = {
                "id": "mock-prof",
                "name": "Mock Profile",
                "project_dir": self.test_root,
                "bundler_generation": "rolldown",
                "pinned_versions": {},
                "required_entries": REQUIRED_ENTRIES,
            }
            pkg_json = self.test_root / "package.json"
            pkg_json.write_text("{}", encoding="utf-8")

            status, rep, errors = qualify_single_profile(
                profile=prof,
                node_version="v22.23.3",
                npm_version="10.9.9",
                repo_root=self.test_root,
                manifest_override=manifest_file,
                skip_install=True,
                skip_build=True,
                skip_java=False,
            )
            self.assertEqual("FAIL", status)
            self.assertTrue(any("Java compatibility qualification failed" in e for e in errors))


    def test_report_generation_multi_profile(self):
        report_file = self.test_root / "multi-report.json"
        profiles = [
            {
                "id": "vite5-svelte4",
                "name": "Legacy",
                "bundlerGeneration": "rollup",
                "status": "PASS",
                "nodeVersion": "v22.0.0",
                "viteVersion": "5.4.2",
            },
            {
                "id": "vite8-svelte5",
                "name": "Current",
                "bundlerGeneration": "rolldown",
                "status": "PASS",
                "nodeVersion": "v24.0.0",
                "viteVersion": "8.3.4",
            },
        ]
        rep = generate_compatibility_report(
            node_version="v22.23.3",
            npm_version="10.9.9",
            toolchain={},
            manifest_path=self.dist_dir / "manifest.json",
            manifest_data=None,
            report_path=report_file,
            status="PASS",
            profiles=profiles,
        )
        self.assertEqual("PASS", rep["status"])
        self.assertEqual(2, len(rep["profiles"]))
        self.assertEqual("vite5-svelte4", rep["profiles"][0]["id"])
        self.assertEqual("vite8-svelte5", rep["profiles"][1]["id"])

    def test_partial_profile_failure_marks_overall_failure(self):
        report_file = self.test_root / "partial-fail.json"
        profiles = [
            {"id": "vite5-svelte4", "status": "PASS"},
            {"id": "vite8-svelte5", "status": "FAIL", "errors": ["Build failed"]},
        ]
        rep = generate_compatibility_report(
            node_version="v22.23.3",
            npm_version="10.9.9",
            toolchain={},
            manifest_path=self.dist_dir / "manifest.json",
            manifest_data=None,
            report_path=report_file,
            status="FAIL",
            errors=["Build failed"],
            profiles=profiles,
        )
        self.assertEqual("FAIL", rep["status"])
        self.assertEqual(2, len(rep["profiles"]))
        self.assertEqual("FAIL", rep["profiles"][1]["status"])

    def test_node_modules_installed_version_extraction(self):
        nm = self.test_root / "node_modules"
        vite_nm = nm / "vite"
        vite_nm.mkdir(parents=True, exist_ok=True)
        (vite_nm / "package.json").write_text(json.dumps({"version": "8.3.4"}), encoding="utf-8")

        pkg_json = self.test_root / "package.json"
        pkg_json.write_text(
            json.dumps({"devDependencies": {"vite": "8.3.4"}}),
            encoding="utf-8",
        )
        v = extract_toolchain_versions(self.test_root)
        self.assertEqual("8.3.4", v["vite"])

    def test_discover_manifest_prefers_vite_dir(self):
        vite_dir = self.dist_dir / ".vite"
        vite_dir.mkdir(parents=True, exist_ok=True)
        manifest_a = vite_dir / "manifest.json"
        manifest_a.write_text("{}", encoding="utf-8")

        manifest_b = self.dist_dir / "manifest.json"
        manifest_b.write_text("{}", encoding="utf-8")

        found = discover_manifest(self.test_root)
        self.assertEqual(manifest_a, found)

    def test_discover_manifest_fallback_to_dist_manifest(self):
        manifest_b = self.dist_dir / "manifest.json"
        manifest_b.write_text("{}", encoding="utf-8")

        found = discover_manifest(self.test_root)
        self.assertEqual(manifest_b, found)

    def test_profile_metadata_coverage(self):
        for prof_id, prof in PROFILES.items():
            self.assertIn("id", prof)
            self.assertIn("name", prof)
            self.assertIn("project_dir", prof)
            self.assertIn("bundler_generation", prof)
            self.assertIn("logical_entry", prof)
            self.assertIn("expected_framework_generation", prof)
            self.assertIn("expected_vite_major", prof)
            self.assertIn("expected_svelte_major", prof)
            self.assertIn("manifest_discovery_strategy", prof)
            self.assertIn("pinned_versions", prof)
            self.assertIn("required_entries", prof)
            self.assertEqual("src/pages/employees/index.ts", prof["logical_entry"])

    def test_framework_generation_mismatch_detected(self):
        legacy_prof = PROFILES["vite5-svelte4"]
        mismatched = {
            "vite": "5.4.2",
            "svelte": "5.0.0",
            "vitePluginSvelte": "3.1.2",
            "typescript": "5.5.4",
        }
        errors = validate_profile_versions(legacy_prof, mismatched)
        self.assertTrue(any("Framework generation mismatch" in e for e in errors))

    def test_generate_compatibility_report_requested_profile(self):
        report_file = self.test_root / "req-profile-report.json"
        rep = generate_compatibility_report(
            node_version="v22.23.3",
            npm_version="10.9.9",
            toolchain={"vite": "8.3.4", "svelte": "5.57.2", "vitePluginSvelte": "7.3.1", "typescript": "5.8.3"},
            manifest_path=self.dist_dir / "manifest.json",
            manifest_data=None,
            report_path=report_file,
            status="PASS",
            requested_profile="vite8-svelte5",
        )
        self.assertEqual("vite8-svelte5", rep["requestedProfile"])


if __name__ == "__main__":
    unittest.main()
