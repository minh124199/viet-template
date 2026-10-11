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

    def test_baseline_integrity_detects_tampered_baseline_file(self):
        tampered_baseline = dict(self.sample_baseline)
        tampered_file = self.path / "config/compatibility/1.4.0-release-baseline.json"
        tampered_file.parent.mkdir(parents=True, exist_ok=True)
        tampered_file.write_text("tampered bytes", encoding="utf-8")

        errors = ver.verify_baseline_provenance(tampered_baseline, ROOT, baseline_path=tampered_file)
        self.assertTrue(any("BASELINE_INTEGRITY_VIOLATION" in e for e in errors))

    def test_static_method_added_to_interface_is_minor_not_major(self):
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
                    "public static com.example.StableSpiInterface create()",  # Static method on interface: MINOR, not MAJOR!
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
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["memberAdditions"], 1)

    def test_reduced_member_visibility_requires_major_release(self):
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
                    "protected java.lang.String doSomething(int)",  # Reduced visibility from public to protected!
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
        self.assertTrue(any("REDUCED_MEMBER_VISIBILITY" in r for r in report["majorReasons"]))

    def test_non_final_method_becoming_final_requires_major_release(self):
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
                    "public final java.lang.String doSomething(int)",  # Became final!
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
        self.assertTrue(any("non-final method became final" in r for r in report["majorReasons"]))

    def test_non_abstract_class_becoming_abstract_requires_major_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public abstract class com.example.StableApiClass",  # Became abstract!
                "members": self.sample_baseline["members"]["com.example.StableApiClass"]["members"],
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
        self.assertTrue(any("non-abstract class became abstract" in r for r in report["majorReasons"]))

    def test_removed_implemented_interface_requires_major_release(self):
        baseline_with_impl = dict(self.sample_baseline)
        baseline_with_impl["members"] = dict(self.sample_baseline["members"])
        baseline_with_impl["members"]["com.example.StableApiClass"] = {
            "header": "public class com.example.StableApiClass implements java.io.Serializable, java.lang.Cloneable",
            "members": self.sample_baseline["members"]["com.example.StableApiClass"]["members"],
        }
        baseline_file = self.path / "impl-baseline.json"
        baseline_file.write_text(json.dumps(baseline_with_impl, indent=2), encoding="utf-8")

        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass implements java.io.Serializable",  # Removed Cloneable!
                "members": self.sample_baseline["members"]["com.example.StableApiClass"]["members"],
            },
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertTrue(any("REMOVED_IMPLEMENTED_INTERFACE" in r for r in report["majorReasons"]))

    def test_changed_superclass_requires_major_release(self):
        baseline_with_super = dict(self.sample_baseline)
        baseline_with_super["members"] = dict(self.sample_baseline["members"])
        baseline_with_super["members"]["com.example.StableApiClass"] = {
            "header": "public class com.example.StableApiClass extends com.example.BaseClass",
            "members": self.sample_baseline["members"]["com.example.StableApiClass"]["members"],
        }
        baseline_file = self.path / "super-baseline.json"
        baseline_file.write_text(json.dumps(baseline_with_super, indent=2), encoding="utf-8")

        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass extends com.example.DifferentBase",
                "members": self.sample_baseline["members"]["com.example.StableApiClass"]["members"],
            },
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        report = ver.check_candidate_compatibility(
            baseline_path=baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertTrue(any("INCOMPATIBLE_SUPERCLASS" in r for r in report["majorReasons"]))

    def test_enum_constant_addition_requires_minor_release(self):
        cand_classif = {
            "com.example.StableEnum": "STABLE_API",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        enum_baseline = {
            "version": "1.4.0",
            "publicSurface": {
                "stableApiTypes": ["com.example.StableEnum"],
                "stableSpiTypes": [],
                "stableTypes": ["com.example.StableEnum"],
            },
            "members": {
                "com.example.StableEnum": {
                    "header": "public final class com.example.StableEnum extends java.lang.Enum<com.example.StableEnum>",
                    "members": [
                        "public static final com.example.StableEnum ALPHA",
                        "public static final com.example.StableEnum BETA",
                    ],
                }
            },
            "frameworkEntrypoints": {"frameworkEntrypointTypes": ["com.example.TestAutoConfiguration"]},
            "frameworkConfigurationKeys": {"keys": []},
            "publicationTopology": {"coordinates": []},
        }
        b_file = self.path / "enum-baseline.json"
        b_file.write_text(json.dumps(enum_baseline, indent=2), encoding="utf-8")

        cand_surface = {
            "com.example.StableEnum": {
                "header": "public final class com.example.StableEnum extends java.lang.Enum<com.example.StableEnum>",
                "members": [
                    "public static final com.example.StableEnum ALPHA",
                    "public static final com.example.StableEnum BETA",
                    "public static final com.example.StableEnum GAMMA",  # New enum constant!
                ],
            }
        }

        report = ver.check_candidate_compatibility(
            baseline_path=b_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])
        self.assertEqual(report["memberAdditions"], 1)

    def test_enum_constant_removal_requires_major_release(self):
        cand_classif = {
            "com.example.StableEnum": "STABLE_API",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        enum_baseline = {
            "version": "1.4.0",
            "publicSurface": {
                "stableApiTypes": ["com.example.StableEnum"],
                "stableSpiTypes": [],
                "stableTypes": ["com.example.StableEnum"],
            },
            "members": {
                "com.example.StableEnum": {
                    "header": "public final class com.example.StableEnum extends java.lang.Enum<com.example.StableEnum>",
                    "members": [
                        "public static final com.example.StableEnum ALPHA",
                        "public static final com.example.StableEnum BETA",
                    ],
                }
            },
            "frameworkEntrypoints": {"frameworkEntrypointTypes": ["com.example.TestAutoConfiguration"]},
            "frameworkConfigurationKeys": {"keys": []},
            "publicationTopology": {"coordinates": []},
        }
        b_file = self.path / "enum-baseline.json"
        b_file.write_text(json.dumps(enum_baseline, indent=2), encoding="utf-8")

        cand_surface = {
            "com.example.StableEnum": {
                "header": "public final class com.example.StableEnum extends java.lang.Enum<com.example.StableEnum>",
                "members": [
                    "public static final com.example.StableEnum ALPHA",
                    # Removed BETA!
                ],
            }
        }

        report = ver.check_candidate_compatibility(
            baseline_path=b_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report["valid"])

    def test_removed_constructor_requires_major_release(self):
        cand_classif = {
            "com.example.StableApiClass": "STABLE_API",
            "com.example.StableSpiInterface": "STABLE_SPI",
            "com.example.TestAutoConfiguration": "FRAMEWORK_ENTRYPOINT",
        }
        cand_surface = {
            "com.example.StableApiClass": {
                "header": "public class com.example.StableApiClass",
                "members": [
                    # Removed constructor com.example.StableApiClass()
                    "public java.lang.String doSomething(int)",
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
        self.assertTrue(any("REMOVED_CONSTRUCTOR" in r for r in report["majorReasons"]))

    def test_patch_policy_rejects_new_public_method_while_minor_policy_allows_it(self):
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
                    "public java.lang.String brandNewFeatureMethod(java.lang.String)",  # Additive method
                ],
            },
            "com.example.StableSpiInterface": self.sample_baseline["members"]["com.example.StableSpiInterface"],
        }

        # Under patch policy: FAILS closed
        report_patch = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="patch",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_patch["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertFalse(report_patch["valid"])
        self.assertEqual(report_patch["memberAdditions"], 1)

        # Under minor policy: PASSES because backwards-compatible addition is allowed
        report_minor = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="minor",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_minor["classification"], "MINOR_RELEASE_REQUIRED")
        self.assertTrue(report_minor["valid"])
        self.assertEqual(report_minor["memberAdditions"], 1)

    def test_minor_policy_rejects_removed_stable_method(self):
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

        # Under minor policy: FAILS closed because breaking changes are rejected
        report_minor = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="minor",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_minor["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report_minor["valid"])
        self.assertTrue(any("REMOVED_METHOD" in r for r in report_minor["majorReasons"]))

    def test_minor_policy_rejects_new_abstract_method_on_spi_interface(self):
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
                    "public abstract void breakingNewSpiMethod()",  # New abstract method breaks SPI implementors
                ],
            },
        }

        # Under minor policy: FAILS closed because SPI breaking method is rejected
        report_minor = ver.check_candidate_compatibility(
            baseline_path=self.baseline_file,
            policy="minor",
            candidate_surface_override=cand_surface,
            candidate_classif_override=cand_classif,
            skip_provenance=True,
        )
        self.assertEqual(report_minor["classification"], "MAJOR_RELEASE_REQUIRED")
        self.assertFalse(report_minor["valid"])
        self.assertTrue(any("ADDED_ABSTRACT_INTERFACE_METHOD" in r for r in report_minor["majorReasons"]))

    def test_resolve_policy_routing(self):
        # Explicit policies
        self.assertEqual(ver.resolve_policy("patch")[0], "patch")
        self.assertEqual(ver.resolve_policy("minor")[0], "minor")
        self.assertEqual(ver.resolve_policy("major")[0], "major")

        # release/1.4 maintenance line -> patch
        p, r = ver.resolve_policy("auto", branch_override="release/1.4", version_override="1.4.1-SNAPSHOT")
        self.assertEqual(p, "patch")
        self.assertIn("release/1.4", r)

        # release/1.4 with higher version still -> patch
        p, _ = ver.resolve_policy("auto", branch_override="release/1.4", version_override="1.5.0-SNAPSHOT")
        self.assertEqual(p, "patch")

        # main with 1.4.1-SNAPSHOT -> patch
        p, r = ver.resolve_policy("auto", branch_override="main", version_override="1.4.1-SNAPSHOT")
        self.assertEqual(p, "patch")
        self.assertIn("1.4.", r)

        # main with 1.5.0-SNAPSHOT -> minor
        p, r = ver.resolve_policy("auto", branch_override="main", version_override="1.5.0-SNAPSHOT")
        self.assertEqual(p, "minor")
        self.assertIn("1.5.0-SNAPSHOT", r)

        # chore/begin-1.5.0-development with 1.5.0-SNAPSHOT -> minor
        p, r = ver.resolve_policy("auto", branch_override="chore/begin-1.5.0-development", version_override="1.5.0-SNAPSHOT")
        self.assertEqual(p, "minor")
        self.assertIn("1.5.0-SNAPSHOT", r)


if __name__ == "__main__":
    unittest.main()
