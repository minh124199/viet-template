#!/usr/bin/env python3
"""
scripts/tests/test_ga_product_freeze.py

Unit and regression tests for GA product-freeze verifier.
Verifies that:
1. All forbidden product files are correctly classified into forbidden categories.
2. Allowed GA transition files are correctly classified into allowed categories.
3. Unknown or arbitrary paths are categorized as UNCLASSIFIED_DRIFT.
4. Any forbidden product drift causes evaluation to FAIL with requires_rc4=True and verdict=RC4_REQUIRED.
5. Any unclassified file causes evaluation to FAIL.
"""

from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SCRIPT_PATH = REPO_ROOT / "scripts" / "verify-ga-product-freeze.py"

spec = importlib.util.spec_from_file_location("verify_ga_product_freeze", SCRIPT_PATH)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

classify_file = mod.classify_file
evaluate_product_freeze = mod.evaluate_product_freeze

CAT_PRODUCT_RUNTIME = mod.CAT_PRODUCT_RUNTIME
CAT_PUBLIC_API_SPI = mod.CAT_PUBLIC_API_SPI
CAT_LANGUAGE_PARSER = mod.CAT_LANGUAGE_PARSER
CAT_INTERPRETER = mod.CAT_INTERPRETER
CAT_AOT_COMPILER = mod.CAT_AOT_COMPILER
CAT_DYNAMIC_LINKER = mod.CAT_DYNAMIC_LINKER
CAT_SPRING_INTEGRATION = mod.CAT_SPRING_INTEGRATION
CAT_SPRING_SECURITY_INTEGRATION = mod.CAT_SPRING_SECURITY_INTEGRATION
CAT_QUARKUS_INTEGRATION = mod.CAT_QUARKUS_INTEGRATION
CAT_MAVEN_PLUGIN = mod.CAT_MAVEN_PLUGIN
CAT_GRADLE_PLUGIN = mod.CAT_GRADLE_PLUGIN
CAT_GENERATED_ABI_CONTRACT = mod.CAT_GENERATED_ABI_CONTRACT
CAT_SECURITY_POLICY = mod.CAT_SECURITY_POLICY

CAT_VERSION_METADATA = mod.CAT_VERSION_METADATA
CAT_RELEASE_INFRASTRUCTURE = mod.CAT_RELEASE_INFRASTRUCTURE
CAT_VERIFICATION_TOOLING = mod.CAT_VERIFICATION_TOOLING
CAT_DOCUMENTATION = mod.CAT_DOCUMENTATION
CAT_TEST_ONLY = mod.CAT_TEST_ONLY
CAT_BENCHMARK_ONLY = mod.CAT_BENCHMARK_ONLY
CAT_UNCLASSIFIED = mod.CAT_UNCLASSIFIED


class TestGAProductFreeze(unittest.TestCase):

    def test_classify_forbidden_product_categories(self):
        # Product Runtime
        self.assertEqual(
            classify_file("viet-template-runtime/src/main/java/io/github/minh124199/viettemplate/runtime/Engine.java"),
            CAT_PRODUCT_RUNTIME
        )
        # Public API
        self.assertEqual(
            classify_file("viet-template-api/src/main/java/io/github/minh124199/viettemplate/api/Template.java"),
            CAT_PUBLIC_API_SPI
        )
        # Parser
        self.assertEqual(
            classify_file("viet-template-language-vtl/src/main/java/io/github/minh124199/viettemplate/parser/VtlParser.java"),
            CAT_LANGUAGE_PARSER
        )
        # Interpreter
        self.assertEqual(
            classify_file("viet-template-vtl-interpreter/src/main/java/io/github/minh124199/viettemplate/vtl/VtlInterpreter.java"),
            CAT_INTERPRETER
        )
        # AOT Compiler
        self.assertEqual(
            classify_file("viet-template-vtl-interpreter/src/main/java/io/github/minh124199/viettemplate/vtl/compiler/BytecodeTemplateCompiler.java"),
            CAT_AOT_COMPILER
        )
        # Dynamic Linker
        self.assertEqual(
            classify_file("viet-template-runtime/src/main/java/io/github/minh124199/viettemplate/runtime/linker/StandardLinker.java"),
            CAT_DYNAMIC_LINKER
        )
        # Security Policy
        self.assertEqual(
            classify_file("viet-template-runtime/src/main/java/io/github/minh124199/viettemplate/runtime/linker/LinkerAccessPolicy.java"),
            CAT_SECURITY_POLICY
        )
        self.assertEqual(
            classify_file("viet-template-runtime/src/main/java/io/github/minh124199/viettemplate/runtime/linker/SafeLinkerAccessPolicy.java"),
            CAT_SECURITY_POLICY
        )
        # Framework Integrations
        self.assertEqual(
            classify_file("viet-template-spring/src/main/java/io/github/minh124199/viettemplate/spring/VietTemplateView.java"),
            CAT_SPRING_INTEGRATION
        )
        self.assertEqual(
            classify_file("viet-template-spring-security/src/main/java/io/github/minh124199/viettemplate/spring/security/SecurityContextEvaluator.java"),
            CAT_SPRING_SECURITY_INTEGRATION
        )
        self.assertEqual(
            classify_file("viet-template-quarkus/src/main/java/io/github/minh124199/viettemplate/quarkus/runtime/VietTemplateRecorder.java"),
            CAT_QUARKUS_INTEGRATION
        )
        # Build Plugins
        self.assertEqual(
            classify_file("viet-template-maven-plugin/src/main/java/io/github/minh124199/viettemplate/maven/CompileMojo.java"),
            CAT_MAVEN_PLUGIN
        )
        self.assertEqual(
            classify_file("viet-template-gradle-plugin/src/main/java/io/github/minh124199/viettemplate/gradle/VietTemplatePlugin.java"),
            CAT_GRADLE_PLUGIN
        )
        # Generated ABI Contract
        self.assertEqual(
            classify_file("config/api-baseline/generated-template-runtime-abi.txt"),
            CAT_GENERATED_ABI_CONTRACT
        )

    def test_classify_allowed_categories(self):
        # Version Metadata
        self.assertEqual(classify_file("pom.xml"), CAT_VERSION_METADATA)
        self.assertEqual(classify_file("build.gradle.kts"), CAT_VERSION_METADATA)
        self.assertEqual(classify_file("viet-template-api/pom.xml"), CAT_VERSION_METADATA)
        self.assertEqual(
            classify_file("viet-template-quarkus/src/main/resources/META-INF/quarkus-extension.properties"),
            CAT_VERSION_METADATA
        )
        self.assertEqual(classify_file("config/compatibility/1.0-candidate-contract.json"), CAT_VERSION_METADATA)
        self.assertEqual(classify_file("benchmark-evidence/m18/manifest.json"), CAT_VERSION_METADATA)

        # Release Infrastructure
        self.assertEqual(classify_file(".github/workflows/release.yml"), CAT_RELEASE_INFRASTRUCTURE)
        self.assertEqual(classify_file("viet-template-gradle-plugin/build.gradle.kts"), CAT_RELEASE_INFRASTRUCTURE)

        # Verification Tooling
        self.assertEqual(classify_file("scripts/verify-ga-readiness.py"), CAT_VERIFICATION_TOOLING)
        self.assertEqual(classify_file("scripts/verify-ga-product-freeze.py"), CAT_VERIFICATION_TOOLING)

        # Documentation
        self.assertEqual(classify_file("README.md"), CAT_DOCUMENTATION)
        self.assertEqual(classify_file("CHANGELOG.md"), CAT_DOCUMENTATION)
        self.assertEqual(classify_file("docs/17-release-process.md"), CAT_DOCUMENTATION)

        # Test Only
        self.assertEqual(
            classify_file("viet-template-runtime/src/test/java/io/github/minh124199/viettemplate/runtime/EngineTest.java"),
            CAT_TEST_ONLY
        )
        self.assertEqual(
            classify_file("integration-tests/spring/maven-mvc-aot/pom.xml"),
            CAT_TEST_ONLY
        )
        self.assertEqual(
            classify_file("scripts/tests/test_release_infrastructure.py"),
            CAT_TEST_ONLY
        )

        # Benchmark Only
        self.assertEqual(
            classify_file("viet-template-benchmarks/src/main/java/io/github/minh124199/viettemplate/benchmarks/BenchmarkRunner.java"),
            CAT_BENCHMARK_ONLY
        )

    def test_unclassified_file_fails_closed(self):
        self.assertEqual(classify_file("mystery_dir/unrecognized_file.xyz"), CAT_UNCLASSIFIED)

    def test_evaluation_passes_with_allowed_drift(self):
        with patch.object(mod, "get_diff_files") as mock_diff:
            mock_diff.return_value = [
                "pom.xml",
                "build.gradle.kts",
                "README.md",
                "scripts/verify-ga-readiness.py",
                "viet-template-runtime/src/test/java/io/github/minh124199/viettemplate/runtime/FooTest.java",
            ]
            res = evaluate_product_freeze(baseline_tag="v1.0.0-RC3", candidate="HEAD")
            self.assertTrue(res["passed"])
            self.assertFalse(res["requires_rc4"])
            self.assertEqual(res["product_drift_total"], 0)
            self.assertEqual(res["unclassified_drift_total"], 0)
            self.assertEqual(res["verdict"], "PASS")

    def test_evaluation_fails_on_forbidden_product_drift(self):
        with patch.object(mod, "get_diff_files") as mock_diff:
            mock_diff.return_value = [
                "pom.xml",
                "viet-template-runtime/src/main/java/io/github/minh124199/viettemplate/runtime/Engine.java",
            ]
            res = evaluate_product_freeze(baseline_tag="v1.0.0-RC3", candidate="HEAD")
            self.assertFalse(res["passed"])
            self.assertTrue(res["requires_rc4"])
            self.assertEqual(res["product_drift_total"], 1)
            self.assertEqual(res["verdict"], "RC4_REQUIRED")

    def test_evaluation_fails_on_unclassified_drift(self):
        with patch.object(mod, "get_diff_files") as mock_diff:
            mock_diff.return_value = [
                "pom.xml",
                "arbitrary_unexpected_file.xyz",
            ]
            res = evaluate_product_freeze(baseline_tag="v1.0.0-RC3", candidate="HEAD")
            self.assertFalse(res["passed"])
            self.assertFalse(res["requires_rc4"])
            self.assertEqual(res["unclassified_drift_total"], 1)
            self.assertEqual(res["verdict"], "FAIL")


if __name__ == "__main__":
    unittest.main()
