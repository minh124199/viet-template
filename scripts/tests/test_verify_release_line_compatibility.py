import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gen = load_script("generate-release-baseline.py")
ver = load_script("verify-release-line-compatibility.py")


class ReleaseLineCompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.path = Path(self.temp_dir.name)

        self.sample_baseline = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "version": "1.4.0",
            "baselineName": "Viet Template 1.4.0 Test Baseline",
            "provenance": {
                "releaseVersion": "1.4.0",
                "tag": "v1.4.0",
                "tagObjectSha": "615ffa9ea291fbd9226e040fd321f7ed018bd01a",
                "sourceCommitSha": "1ee485beee4b7588dd154881ede1e76985e339dc",
                "treeSha": "8ddc255797a8bfce88ee30e1815d8f56e809b61e",
                "releaseDate": "2026-10-11",
                "maintenanceLine": "1.4.x",
            },
            "publicSurface": {
                "totalCompiledPublicTypes": 2,
                "stableApiTypesCount": 1,
                "stableSpiTypesCount": 1,
                "totalStableCount": 2,
                "stableApiTypes": ["com.example.StableApiClass"],
                "stableSpiTypes": ["com.example.StableSpiInterface"],
                "stableTypes": ["com.example.StableApiClass", "com.example.StableSpiInterface"],
                "classifications": {
                    "com.example.StableApiClass": "STABLE_API",
                    "com.example.StableSpiInterface": "STABLE_SPI",
                },
            },
            "members": {
                "com.example.StableApiClass": {
                    "header": "public class com.example.StableApiClass",
                    "members": [
                        "public com.example.StableApiClass()",
                        "public java.lang.String doSomething(int)",
                        "public static final java.lang.String CONSTANT",
                    ],
                },
                "com.example.StableSpiInterface": {
                    "header": "public interface com.example.StableSpiInterface",
                    "members": [
                        "public abstract void onEvent(java.lang.String)",
                        "public default boolean isEnabled()",
                    ],
                },
            },
            "frameworkEntrypoints": {
                "frameworkEntrypointsCount": 1,
                "frameworkEntrypointTypes": ["com.example.TestAutoConfiguration"],
            },
            "frameworkConfigurationKeys": {
                "totalPropertiesCount": 2,
                "keys": ["viet-template.enabled", "viet-template.prefix"],
            },
            "publicationTopology": {
                "publishedCoordinatesCount": 2,
                "coordinates": [
                    "io.github.minh124199:viet-template-parent",
                    "io.github.minh124199:viet-template-api",
                ],
            },
        }

        self.baseline_file = self.path / "1.4.0-test-baseline.json"
        self.baseline_file.write_text(json.dumps(self.sample_baseline, indent=2), encoding="utf-8")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_identical_candidate_satisfies_patch_policy(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    "public com.example.StableApiClass()",
                    "public java.lang.String doSomething(int)",
                    "public static final java.lang.String CONSTANT",
                ],
            },
            "com.example.StableSpiInterface": {
                "header": "public interface com.example.StableSpiInterface",
                "members": [
                    "public abstract void onEvent(java.lang.String)",
                    "public default boolean isEnabled()",
                ],
            },
        }
        cand_keys = {"allProperties": ["viet-template.enabled", "viet-template.prefix"]}
        cand_topo = {
            "coordinates": [
                "io.github.minh124199:viet-template-parent",
                "io.github.minh124199:viet-template-api",
            ]
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            candidate_config_keys_override=cand_keys,
            candidate_topology_override=cand_topo,
            skip_provenance=True,
        )

        self.assertEqual(report["classification"], "PATCH_ALLOWED")
        self.assertTrue(report["valid"])
        self.assertEqual(report["stableApiAdditions"], 0)
        self.assertEqual(report["stableSpiAdditions"], 0)
        self.assertEqual(report["memberAdditions"], 0)
        self.assertEqual(report["signatureChanges"], 0)

    def test_internal_change_preserves_patch_eligibility(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
            "com.example.InternalClass": "INTERNAL_CROSS_PACKAGE",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    "public com.example.StableApiClass()",
                    "public java.lang.String doSomething(int)",
                    "public static final java.lang.String CONSTANT",
                ],
            },
            "com.example.StableSpiInterface": {
                "header": "public interface com.example.StableSpiInterface",
                "members": [
                    "public abstract void onEvent(java.lang.String)",
                    "public default boolean isEnabled()",
                ],
            },
        }
        cand_keys = {"allProperties": ["viet-template.enabled", "viet-template.prefix"]}
        cand_topo = {
            "coordinates": [
                "io.github.minh124199:viet-template-parent",
                "io.github.minh124199:viet-template-api",
            ]
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            candidate_config_keys_override=cand_keys,
            candidate_topology_override=cand_topo,
            skip_provenance=True,
        )

        self.assertEqual(report["classification"], "PATCH_ALLOWED")
        self.assertTrue(report["valid"])

    def test_new_stable_api_type_requires_minor_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.NewFeatureClass": "STABLE_API",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    "public com.example.StableApiClass()",
                    "public java.lang.String doSomething(int)",
                    "public static final java.lang.String CONSTANT",
                ],
            },
            "com.example.StableSpiInterface": {
                "header": "public interface com.example.StableSpiInterface",
                "members": [
                    "public abstract void onEvent(java.lang.String)",
                    "public default boolean isEnabled()",
                ],
            },
            "com.example.NewFeatureClass": {
                "header": "public class com.example.NewFeatureClass",
                "members": ["public void execute()"],
            },
        }

        report_patch = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_patch["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report_patch["valid"])
        self.assertEqual(report_patch["stableApiAdditions"], 1)

        report_minor = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="minor",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_minor["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertTrue(report_minor["valid"])

    def test_new_stable_spi_type_requires_minor_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.NewSpiExtension": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": self.sample_baseline["members"]["com.example.StableApiClass"],
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
            "com.example.NewSpiExtension": {
                "header": "public interface com.example.NewSpiExtension",
                "members": ["public abstract void hook()"],
            },
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["stableSpiAdditions"], 1)

    def test_removed_stable_api_type_requires_major_release(self):
        cand_classif = {
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["stableApiRemovals"], 1)

    def test_new_public_method_on_existing_stable_class_requires_minor(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    "public com.example.StableApiClass()",
                    "public java.lang.String doSomething(int)",
                    "public static final java.lang.String CONSTANT",
                    "public void newAddedMethod()",  # Added method!
                ],
            },
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["memberAdditions"], 1)

    def test_removed_public_method_requires_major_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    "public com.example.StableApiClass()",
                    # Removed doSomething(int)
                    "public static final java.lang.String CONSTANT",
                ],
            },
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])

    def test_new_abstract_method_in_spi_requires_major_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": self.sample_baseline["members"]["com.example.StableApiClass"],
            "com.example.StableSpiInterface": {
                "header": "public interface com.example.StableSpiInterface",
                "members": [
                    "public abstract void onEvent(java.lang.String)",
                    "public default boolean isEnabled()",
                    "public abstract void newMandatorySpiMethod()",  # New abstract method breaks third party implementors!
                ],
            },
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])

    def test_new_framework_entrypoint_requires_minor_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
            "com.example.NewAutoConfiguration": "FRAMEWORK_ENTRYPOINT",  # Added entrypoint
        }
        cand_surface = {
            "com.example.StableApiClass": self.sample_baseline["members"]["com.example.StableApiClass"],
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["frameworkEntryAdditions"], 1)

    def test_new_configuration_key_requires_minor_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": self.sample_baseline["members"]["com.example.StableApiClass"],
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }
        cand_keys = {
            "allProperties": [
                "viet-template.enabled",
                "viet-template.prefix",
                "viet-template.new-feature-key",  # Added key!
            ]
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            candidate_config_keys_override=cand_keys,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["configKeyAdditions"], 1)

    def test_new_published_coordinate_requires_minor_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": self.sample_baseline["members"]["com.example.StableApiClass"],
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }
        cand_topo = {
            "coordinates": [
                "io.github.minh124199:viet-template-parent",
                "io.github.minh124199:viet-template-api",
                "io.github.minh124199:viet-template-new-module",  # Added coordinate!
            ]
        }

        report = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            candidate_topology_override=cand_topo,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["artifactTopologyChanges"], 1)

    def test_provenance_validation_detects_wrong_sha(self):
        bad_baseline = dict(self.sample_baseline)
        bad_baseline["provenance"] = dict(self.sample_baseline["provenance"])
        bad_baseline["provenance"]["sourceCommitSha"] = "0000000000000000000000000000000000000000"

        bad_file = self.path / "bad-sha-baseline.json"
        bad_file.write_text(json.dumps(bad_baseline, indent=2), encoding="utf-8")

        errors = ver.verify_baseline_provenance(bad_baseline, ROOT)
        self.assertTrue(any("Commit SHA mismatch" in e for e in errors))

    def test_provenance_validation_detects_wrong_tag(self):
        bad_baseline = dict(self.sample_baseline)
        bad_baseline["provenance"] = dict(self.sample_baseline["provenance"])
        bad_baseline["provenance"]["tag"] = "v999.999.999-nonexistent"

        errors = ver.verify_baseline_provenance(bad_baseline, ROOT)
        self.assertTrue(any("does not exist" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
