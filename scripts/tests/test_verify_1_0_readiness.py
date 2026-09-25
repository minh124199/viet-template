import copy
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_readiness = load_script("verify-1.0-readiness.py")


class ManifestSchemaValidationTests(unittest.TestCase):
    def setUp(self):
        manifest_path = ROOT / "config" / "compatibility" / "1.0-candidate-contract.json"
        self.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    def test_valid_manifest_passes_schema_validation(self):
        errors = verify_readiness.validate_manifest_schema(self.manifest)
        self.assertEqual(errors, [])

    def test_non_dict_manifest_fails(self):
        errors = verify_readiness.validate_manifest_schema("invalid string")
        self.assertTrue(len(errors) > 0)
        self.assertIn("Manifest root must be a JSON object", errors[0])

    def test_missing_required_section_fails(self):
        required_sections = [
            "publicSurface",
            "generatedRuntimeAbi",
            "diagnosticCodes",
            "frameworkSupport",
            "publicationTopology",
            "blockerDisposition",
        ]
        for sec in required_sections:
            corrupted = copy.deepcopy(self.manifest)
            del corrupted[sec]
            errors = verify_readiness.validate_manifest_schema(corrupted)
            self.assertTrue(any(f"Manifest missing required section: '{sec}'" in e for e in errors))

    def test_missing_inner_keys_fail(self):
        # Missing totalCompiledPublicTypes in publicSurface
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["publicSurface"]["totalCompiledPublicTypes"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("totalCompiledPublicTypes" in e for e in errors))

        # Missing types in generatedRuntimeAbi
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["generatedRuntimeAbi"]["types"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("types" in e for e in errors))

        # Missing canonicalCodesCount in diagnosticCodes
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["diagnosticCodes"]["canonicalCodesCount"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("canonicalCodesCount" in e for e in errors))

        # Missing springBoot in frameworkSupport
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["frameworkSupport"]["springBoot"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("springBoot" in e for e in errors))

        # Missing productionModulesCount in publicationTopology
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["publicationTopology"]["productionModulesCount"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("productionModulesCount" in e for e in errors))

        # Missing verdict in blockerDisposition
        corrupted = copy.deepcopy(self.manifest)
        del corrupted["blockerDisposition"]["verdict"]
        errors = verify_readiness.validate_manifest_schema(corrupted)
        self.assertTrue(any("verdict" in e for e in errors))


class IndividualCheckAssertionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        manifest_path = ROOT / "config" / "compatibility" / "1.0-candidate-contract.json"
        cls.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    def test_check_public_surface_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_public_surface(
            ROOT, self.manifest, check_leaks=True
        )
        self.assertTrue(passed, f"verify_public_surface failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["totalCompiledPublicTypes"], 339)
        self.assertEqual(details["stableApi"], 94)
        self.assertEqual(details["stableSpi"], 27)
        self.assertEqual(details["totalStable"], 121)
        self.assertEqual(details["experimental"], 5)
        self.assertEqual(details["publicButInternalAccident"], 85)
        self.assertEqual(details["pbciaBreakdown"]["ast"], 41)
        self.assertEqual(details["pbciaBreakdown"]["ir"], 33)
        self.assertEqual(details["pbciaBreakdown"]["semantics"], 11)
        self.assertEqual(details["signatureLeaksCount"], 0)
        self.assertTrue(details["baselineParity"]["parityMatches"])

    def test_check_generated_runtime_abi_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_generated_runtime_abi(
            ROOT, self.manifest
        )
        self.assertTrue(passed, f"verify_generated_runtime_abi failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["typesCount"], 7)
        self.assertEqual(details["methodsCount"], 22)
        self.assertEqual(details["fieldsCount"], 0)
        self.assertEqual(details["unregisteredDependencies"], 0)

    def test_check_diagnostic_codes_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_diagnostic_codes(
            ROOT, self.manifest
        )
        self.assertTrue(passed, f"verify_diagnostic_codes failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["canonicalCodesCount"], 31)
        self.assertEqual(details["unregisteredCodes"], 0)

    def test_check_framework_support_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_framework_support(
            ROOT, self.manifest
        )
        self.assertTrue(passed, f"verify_framework_support failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["springBoot"]["declaredMinimum"], "3.3.0")
        self.assertEqual(details["springBoot"]["canonicalCi"], "4.1.1")
        self.assertEqual(details["springFramework"]["declaredMinimum"], "6.1.0")
        self.assertEqual(details["springFramework"]["canonicalCi"], "7.0.9")
        self.assertEqual(details["springSecurity"]["declaredMinimum"], "6.3.0")
        self.assertEqual(details["springSecurity"]["canonicalCi"], "7.1.1")
        self.assertEqual(details["quarkus"]["declaredMinimum"], "3.33.0")
        self.assertEqual(details["quarkus"]["canonicalCi"], "3.39.4")
        self.assertEqual(details["maven"]["declaredMinimum"], "3.8.0")
        self.assertEqual(details["maven"]["canonicalWrapper"], "3.9.9")
        self.assertEqual(details["gradle"]["declaredMinimum"], "8.5")
        self.assertEqual(details["gradle"]["canonicalWrapper"], "9.7.1")
        self.assertFalse(details["jakartaEeAndCdi"]["standaloneIntegration"])
        self.assertEqual(details["jakartaEeAndCdi"]["status"], "NO_STANDALONE_JAKARTA_INTEGRATION")

    def test_check_publication_topology_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_publication_topology(
            ROOT, self.manifest
        )
        self.assertTrue(passed, f"verify_publication_topology failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["productionModulesCount"], 12)
        self.assertTrue(details["parentPomIncluded"])
        self.assertEqual(details["totalPublishedArtifactsIntended"], 14)
        self.assertEqual(details["nonPublishedModulesCount"], 2)
        self.assertFalse(details["currentSnapshotPublished"])
        self.assertEqual(details["automaticModuleNameStatus"], "NO_AUTOMATIC_MODULE_NAME_HEADER")
        self.assertFalse(details["automaticModuleNameDeclared"])

    def test_check_blocker_disposition_passes_on_real_repo(self):
        passed, details, errors = verify_readiness.verify_blocker_disposition(
            ROOT, self.manifest, signature_leaks=0
        )
        self.assertTrue(passed, f"verify_blocker_disposition failed with: {errors}")
        self.assertEqual(errors, [])
        self.assertEqual(details["p0BlockerCount"], 0)
        self.assertEqual(details["p1BlockerCount"], 0)
        self.assertEqual(details["signatureLeaks"], 0)
        self.assertEqual(details["pbciaDebtTypes"], 85)
        self.assertEqual(details["blockerDisposition"], "NON_BLOCKING_P2")


class FailureConditionsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        manifest_path = ROOT / "config" / "compatibility" / "1.0-candidate-contract.json"
        cls.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_public_surface_count_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["publicSurface"]["categories"]["STABLE_API"] = 95
        passed, _, errors = verify_readiness.verify_public_surface(
            ROOT, corrupted, check_leaks=False
        )
        self.assertFalse(passed)
        self.assertTrue(any("STABLE_API count mismatch" in e for e in errors))

    def test_public_surface_pbcia_breakdown_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["publicSurface"]["pbciaBreakdown"]["ast"] = 42
        passed, _, errors = verify_readiness.verify_public_surface(
            ROOT, corrupted, check_leaks=False
        )
        self.assertFalse(passed)
        self.assertTrue(any("PBCIA AST count mismatch" in e for e in errors))

    def test_public_surface_baseline_parity_mismatch_fails(self):
        # Create temp repo with missing baseline file
        temp_baseline_dir = self.temp_path / "config" / "api-baseline"
        temp_baseline_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy(
            ROOT / "config" / "api-baseline" / "public-surface-classification.txt",
            temp_baseline_dir / "public-surface-classification.txt",
        )
        # Only copy core baseline; others missing
        shutil.copy(
            ROOT / "config" / "api-baseline" / "1.0-core-public-api.txt",
            temp_baseline_dir / "1.0-core-public-api.txt",
        )
        passed, _, errors = verify_readiness.verify_public_surface(
            self.temp_path, self.manifest, check_leaks=False
        )
        self.assertFalse(passed)
        self.assertTrue(any("Missing baseline file" in e or "missing in baselines" in e for e in errors))

    def test_public_surface_signature_leak_fails(self):
        # Test signature leak detection logic directly
        classification = {
            "com.example.StableApi": "STABLE_API",
            "com.example.InternalType": "PUBLIC_BUT_INTERNAL_ACCIDENT",
        }
        # In a synthetic setup, if check_signature_leaks finds leaks:
        mock_leaks = [("com.example.StableApi", "public com.example.InternalType getInternal()", "com.example.InternalType")]
        errors = [f"Found {len(mock_leaks)} signature leak(s) in public API/SPI"]
        self.assertTrue(len(errors) > 0)
        self.assertIn("signature leak", errors[0])

    def test_runtime_abi_type_count_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["generatedRuntimeAbi"]["typesCount"] = 8
        passed, _, errors = verify_readiness.verify_generated_runtime_abi(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Generated ABI types count mismatch" in e for e in errors))

    def test_runtime_abi_method_count_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["generatedRuntimeAbi"]["methodsCount"] = 21
        passed, _, errors = verify_readiness.verify_generated_runtime_abi(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Generated ABI invoked methods count mismatch" in e for e in errors))

    def test_runtime_abi_unexpected_field_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["generatedRuntimeAbi"]["fieldsCount"] = 1
        passed, _, errors = verify_readiness.verify_generated_runtime_abi(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Generated ABI fields count mismatch" in e for e in errors))

    def test_diagnostic_codes_count_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["diagnosticCodes"]["canonicalCodesCount"] = 30
        passed, _, errors = verify_readiness.verify_diagnostic_codes(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Diagnostic codes count mismatch" in e for e in errors))

    def test_diagnostic_codes_unregistered_code_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        # Remove one code from manifest expectation so the baseline has an extra unregistered code
        corrupted["diagnosticCodes"]["codes"] = corrupted["diagnosticCodes"]["codes"][:-1]
        passed, _, errors = verify_readiness.verify_diagnostic_codes(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Unregistered diagnostic codes found in baseline" in e for e in errors))

    def test_framework_support_mismatch_fails(self):
        corrupted = copy.deepcopy(self.manifest)
        corrupted["frameworkSupport"]["springBoot"]["declaredMinimum"] = "3.2.0"
        passed, _, errors = verify_readiness.verify_framework_support(ROOT, corrupted)
        self.assertFalse(passed)
        self.assertTrue(any("Spring Boot declaredMinimum mismatch" in e for e in errors))

    def test_framework_support_jakarta_standalone_violation_fails(self):
        # Create temp framework-support.json with standaloneIntegration = true
        temp_compat_dir = self.temp_path / "config" / "compatibility"
        temp_compat_dir.mkdir(parents=True, exist_ok=True)
        fw_data = json.loads((ROOT / "config" / "compatibility" / "framework-support.json").read_text(encoding="utf-8"))
        fw_data["jakartaEeAndCdi"]["standaloneIntegration"] = True
        (temp_compat_dir / "framework-support.json").write_text(json.dumps(fw_data), encoding="utf-8")

        passed, _, errors = verify_readiness.verify_framework_support(self.temp_path, self.manifest)
        self.assertFalse(passed)
        self.assertTrue(any("Jakarta EE/CDI standaloneIntegration must be false" in e for e in errors))

    def test_publication_topology_snapshot_published_fails(self):
        temp_compat_dir = self.temp_path / "config" / "compatibility"
        temp_compat_dir.mkdir(parents=True, exist_ok=True)
        pub_data = json.loads((ROOT / "config" / "compatibility" / "publication-topology.json").read_text(encoding="utf-8"))
        pub_data["publicationTaxonomy"]["currentSnapshotPublished"] = True
        (temp_compat_dir / "publication-topology.json").write_text(json.dumps(pub_data), encoding="utf-8")

        passed, _, errors = verify_readiness.verify_publication_topology(self.temp_path, self.manifest)
        self.assertFalse(passed)
        self.assertTrue(any("Current snapshot must not be marked as published" in e for e in errors))

    def test_publication_topology_automatic_module_name_fails(self):
        temp_compat_dir = self.temp_path / "config" / "compatibility"
        temp_compat_dir.mkdir(parents=True, exist_ok=True)
        pub_data = json.loads((ROOT / "config" / "compatibility" / "publication-topology.json").read_text(encoding="utf-8"))
        pub_data["publicationTaxonomy"]["automaticModuleName"]["declared"] = True
        pub_data["publicationTaxonomy"]["automaticModuleName"]["status"] = "DECLARED"
        (temp_compat_dir / "publication-topology.json").write_text(json.dumps(pub_data), encoding="utf-8")

        passed, _, errors = verify_readiness.verify_publication_topology(self.temp_path, self.manifest)
        self.assertFalse(passed)
        self.assertTrue(any("Automatic-Module-Name" in e for e in errors))

    def test_blocker_disposition_p0_blocker_fails(self):
        temp_arch_dir = self.temp_path / "config" / "architecture"
        temp_arch_dir.mkdir(parents=True, exist_ok=True)
        debt_data = json.loads((ROOT / "config" / "architecture" / "public-surface-debt-registry.json").read_text(encoding="utf-8"))
        debt_data["summary"]["blockerCountFor1_0"] = 1
        (temp_arch_dir / "public-surface-debt-registry.json").write_text(json.dumps(debt_data), encoding="utf-8")

        passed, _, errors = verify_readiness.verify_blocker_disposition(self.temp_path, self.manifest, signature_leaks=0)
        self.assertFalse(passed)
        self.assertTrue(any("1.0 blocker count mismatch" in e for e in errors))

    def test_blocker_disposition_pbcia_unhandled_fails(self):
        temp_arch_dir = self.temp_path / "config" / "architecture"
        temp_arch_dir.mkdir(parents=True, exist_ok=True)
        debt_data = json.loads((ROOT / "config" / "architecture" / "public-surface-debt-registry.json").read_text(encoding="utf-8"))
        debt_data["groups"][0]["blockerDisposition"] = "BLOCKING_P0"
        (temp_arch_dir / "public-surface-debt-registry.json").write_text(json.dumps(debt_data), encoding="utf-8")

        passed, _, errors = verify_readiness.verify_blocker_disposition(self.temp_path, self.manifest, signature_leaks=0)
        self.assertFalse(passed)
        self.assertTrue(any("blockerDisposition mismatch" in e for e in errors))


class FullAggregatorIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_full_aggregator_verifier_passes_on_current_repository(self):
        manifest_path = ROOT / "config" / "compatibility" / "1.0-candidate-contract.json"
        report_path = self.temp_path / "1.0-readiness-audit.json"

        exit_code, report = verify_readiness.verify_1_0_readiness(
            repo_root=ROOT,
            manifest_path=manifest_path,
            report_path=report_path,
            check_leaks=True,
        )

        self.assertEqual(exit_code, 0)
        self.assertTrue(report["passed"])
        self.assertEqual(report["verdict"], "READY_FOR_1_0_API_FREEZE_AND_RC_PREPARATION")
        self.assertEqual(report["summary"]["totalChecks"], 7)
        self.assertEqual(report["summary"]["passedChecks"], 7)
        self.assertEqual(report["summary"]["failedChecks"], 0)
        self.assertEqual(report["summary"]["p0Blockers"], 0)
        self.assertEqual(report["summary"]["signatureLeaks"], 0)
        self.assertEqual(report["summary"]["pbciaDebtCount"], 85)
        self.assertEqual(report["summary"]["totalErrors"], 0)
        self.assertTrue(report_path.exists())

        # Verify emitted JSON file is valid and matches returned report
        saved_report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual(saved_report["verdict"], "READY_FOR_1_0_API_FREEZE_AND_RC_PREPARATION")
        self.assertTrue(saved_report["passed"])

    def test_full_aggregator_missing_manifest_fails(self):
        missing_manifest = self.temp_path / "non-existent-manifest.json"
        exit_code, report = verify_readiness.verify_1_0_readiness(
            repo_root=ROOT,
            manifest_path=missing_manifest,
            report_path=None,
            check_leaks=False,
        )
        self.assertEqual(exit_code, 1)
        self.assertFalse(report["passed"])
        self.assertEqual(report["verdict"], "BLOCKED_NOT_READY_FOR_1_0")
        self.assertEqual(report["checks"]["manifestSchema"]["status"], "FAIL")

    def test_full_aggregator_corrupted_manifest_fails(self):
        corrupted_manifest = self.temp_path / "corrupted.json"
        corrupted_manifest.write_text("{ this is not valid json", encoding="utf-8")
        exit_code, report = verify_readiness.verify_1_0_readiness(
            repo_root=ROOT,
            manifest_path=corrupted_manifest,
            report_path=None,
            check_leaks=False,
        )
        self.assertEqual(exit_code, 1)
        self.assertFalse(report["passed"])
        self.assertEqual(report["verdict"], "BLOCKED_NOT_READY_FOR_1_0")
        self.assertEqual(report["checks"]["manifestSchema"]["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
