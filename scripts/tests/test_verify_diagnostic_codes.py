import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_diag = load_script("verify-diagnostic-codes.py")


class DiagnosticCodesBaselineTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def create_file(self, rel_path: str, content: str) -> Path:
        file_path = self.temp_path / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content, encoding="utf-8")
        return file_path

    # =========================================================================
    # 1. Baseline Parsing Tests
    # =========================================================================

    def test_parse_valid_baseline(self):
        content = """# Header comment
# Another comment
CATEGORY_A:CODE_1

CATEGORY_B:CODE_2
# Trailing comment
CATEGORY_C:CODE_3
"""
        baseline_file = self.create_file("baseline.txt", content)
        codes = verify_diag.parse_baseline(baseline_file)
        self.assertEqual(codes, {"CATEGORY_A:CODE_1", "CATEGORY_B:CODE_2", "CATEGORY_C:CODE_3"})

    def test_parse_nonexistent_baseline_raises_error(self):
        nonexistent = self.temp_path / "nonexistent.txt"
        with self.assertRaises(FileNotFoundError):
            verify_diag.parse_baseline(nonexistent)

    def test_default_baseline_exists_and_contains_exact_31_codes(self):
        default_baseline = ROOT / "config/api-baseline/diagnostic-codes-1.0.txt"
        self.assertTrue(default_baseline.exists(), f"Baseline file missing: {default_baseline}")
        codes = verify_diag.parse_baseline(default_baseline)
        self.assertEqual(len(codes), 31)
        self.assertIn("LIMIT:LIMIT_EXCEEDED", codes)
        self.assertIn("SECURITY:ACCESS_DENIED", codes)
        self.assertIn("VTLSEC:2401", codes)
        self.assertNotIn("LIMIT:EXCEEDED", codes)

    # =========================================================================
    # 2. Code Discovery Tests
    # =========================================================================

    def test_discover_direct_diagnostic_code_of(self):
        java_code = """
package com.example;
import io.github.minh124199.viettemplate.api.DiagnosticCode;

public class Sample {
    void run() {
        DiagnosticCode c1 = DiagnosticCode.of("SECURITY", "ACCESS_DENIED");
        DiagnosticCode c2 = DiagnosticCode.of(
            "LAYOUT",
            "CYCLE_DETECTED"
        );
    }
}
"""
        self.create_file("src/main/java/com/example/Sample.java", java_code)
        discovered = verify_diag.discover_diagnostic_codes(self.temp_path)

        self.assertIn("SECURITY:ACCESS_DENIED", discovered)
        self.assertIn("LAYOUT:CYCLE_DETECTED", discovered)
        self.assertEqual(len(discovered["SECURITY:ACCESS_DENIED"]), 1)
        self.assertEqual(discovered["SECURITY:ACCESS_DENIED"][0]["line"], 7)
        self.assertEqual(discovered["LAYOUT:CYCLE_DETECTED"][0]["line"], 8)

    def test_discover_constant_declaration_and_usage(self):
        render_budget_code = """
package com.example;
import io.github.minh124199.viettemplate.api.DiagnosticCode;

public final class RenderBudget {
    public static final DiagnosticCode CODE_LIMIT_EXCEEDED =
        DiagnosticCode.of("LIMIT", "LIMIT_EXCEEDED");
}
"""
        plan_code = """
package com.example;

public class Plan {
    void execute() {
        throw new RuntimeException("error", RenderBudget.CODE_LIMIT_EXCEEDED);
    }
}
"""
        self.create_file("src/main/java/com/example/RenderBudget.java", render_budget_code)
        self.create_file("src/main/java/com/example/Plan.java", plan_code)

        discovered = verify_diag.discover_diagnostic_codes(self.temp_path)
        self.assertIn("LIMIT:LIMIT_EXCEEDED", discovered)

        occurrences = discovered["LIMIT:LIMIT_EXCEEDED"]
        files = [occ["file"] for occ in occurrences]
        self.assertTrue(any("RenderBudget.java" in f for f in files))
        self.assertTrue(any("Plan.java" in f for f in files))

        types = [occ["type"] for occ in occurrences]
        self.assertIn("DIRECT_INVOCATION", types)
        self.assertIn("CONSTANT_REFERENCE", types)

    def test_ignore_build_and_dot_directories(self):
        valid_java = 'class A { DiagnosticCode c = DiagnosticCode.of("REAL", "CODE"); }'
        ignored_build = 'class B { DiagnosticCode c = DiagnosticCode.of("IGNORE", "BUILD"); }'
        ignored_dot = 'class C { DiagnosticCode c = DiagnosticCode.of("IGNORE", "DOT"); }'

        self.create_file("src/main/java/A.java", valid_java)
        self.create_file("build/src/main/java/B.java", ignored_build)
        self.create_file(".gradle/src/main/java/C.java", ignored_dot)

        discovered = verify_diag.discover_diagnostic_codes(self.temp_path)
        self.assertIn("REAL:CODE", discovered)
        self.assertNotIn("IGNORE:BUILD", discovered)
        self.assertNotIn("IGNORE:DOT", discovered)

    # =========================================================================
    # 3. Difference Detection and Verification Tests
    # =========================================================================

    def test_exact_match_passes(self):
        java_code = """
class Test {
    void foo() {
        DiagnosticCode.of("CAT", "A");
        DiagnosticCode.of("CAT", "B");
    }
}
"""
        self.create_file("src/main/java/Test.java", java_code)
        baseline = self.create_file("baseline.txt", "CAT:A\nCAT:B\n")
        report_file = self.temp_path / "report.json"

        passed, report, errors = verify_diag.verify_diagnostic_codes(
            repo_root=self.temp_path,
            baseline_path=baseline,
            report_path=report_file,
            check_exact=False,
        )

        self.assertTrue(passed)
        self.assertEqual(len(errors), 0)
        self.assertEqual(report["status"], "PASSED")
        self.assertEqual(report["missingCodes"], [])
        self.assertEqual(report["extraCodes"], [])
        self.assertTrue(report_file.exists())

    def test_detects_missing_codes(self):
        java_code = """
class Test {
    void foo() {
        DiagnosticCode.of("CAT", "A");
    }
}
"""
        self.create_file("src/main/java/Test.java", java_code)
        baseline = self.create_file("baseline.txt", "CAT:A\nCAT:B\n")

        passed, report, errors = verify_diag.verify_diagnostic_codes(
            repo_root=self.temp_path,
            baseline_path=baseline,
            check_exact=False,
        )

        self.assertFalse(passed)
        self.assertEqual(report["status"], "FAILED")
        self.assertIn("CAT:B", report["missingCodes"])
        self.assertTrue(any("Missing diagnostic code" in e for e in errors))

    def test_detects_extra_codes(self):
        java_code = """
class Test {
    void foo() {
        DiagnosticCode.of("CAT", "A");
        DiagnosticCode.of("SURPRISE", "EXTRA");
    }
}
"""
        self.create_file("src/main/java/Test.java", java_code)
        baseline = self.create_file("baseline.txt", "CAT:A\n")

        passed, report, errors = verify_diag.verify_diagnostic_codes(
            repo_root=self.temp_path,
            baseline_path=baseline,
            check_exact=False,
        )

        self.assertFalse(passed)
        self.assertEqual(report["status"], "FAILED")
        self.assertIn("SURPRISE:EXTRA", report["extraCodes"])
        self.assertTrue(any("Unexpected diagnostic code" in e for e in errors))

    def test_check_exact_flag_enforces_count(self):
        java_code = """
class Test {
    void foo() {
        DiagnosticCode.of("CAT", "A");
    }
}
"""
        self.create_file("src/main/java/Test.java", java_code)
        baseline = self.create_file("baseline.txt", "CAT:A\n")

        # Without check_exact: passes because discovered == baseline
        passed_lenient, _, errors_lenient = verify_diag.verify_diagnostic_codes(
            repo_root=self.temp_path,
            baseline_path=baseline,
            check_exact=False,
        )
        self.assertTrue(passed_lenient)
        self.assertEqual(errors_lenient, [])

        # With check_exact: fails because count != 31
        passed_exact, report_exact, errors_exact = verify_diag.verify_diagnostic_codes(
            repo_root=self.temp_path,
            baseline_path=baseline,
            check_exact=True,
        )
        self.assertFalse(passed_exact)
        self.assertEqual(report_exact["status"], "FAILED")
        self.assertTrue(any("Expected exactly 31" in e for e in errors_exact))

    # =========================================================================
    # 4. Current Repository Verification Tests
    # =========================================================================

    def test_current_repository_passes_baseline_verification(self):
        baseline_path = ROOT / "config/api-baseline/diagnostic-codes-1.0.txt"
        report_path = ROOT / "build/reports/diagnostic-codes.json"

        passed, report, errors = verify_diag.verify_diagnostic_codes(
            repo_root=ROOT,
            baseline_path=baseline_path,
            report_path=report_path,
            check_exact=True,
        )

        self.assertTrue(passed, f"Verification failed with errors: {errors}")
        self.assertEqual(report["status"], "PASSED")
        self.assertEqual(report["totalDiscovered"], 31)
        self.assertEqual(report["totalBaseline"], 31)
        self.assertEqual(report["missingCodes"], [])
        self.assertEqual(report["extraCodes"], [])
        self.assertTrue(report["exactMatch"])

    def test_orphan_limit_exceeded_eliminated(self):
        baseline_path = ROOT / "config/api-baseline/diagnostic-codes-1.0.txt"
        _, report, _ = verify_diag.verify_diagnostic_codes(
            repo_root=ROOT,
            baseline_path=baseline_path,
            check_exact=True,
        )

        self.assertNotIn("LIMIT:EXCEEDED", report["codes"])
        self.assertIn("LIMIT:LIMIT_EXCEEDED", report["codes"])

    def test_default_layout_render_plan_uses_render_budget_constant(self):
        discovered = verify_diag.discover_diagnostic_codes(ROOT)
        self.assertIn("LIMIT:LIMIT_EXCEEDED", discovered)

        occurrences = discovered["LIMIT:LIMIT_EXCEEDED"]
        layout_occurrences = [
            occ for occ in occurrences
            if "DefaultLayoutRenderPlan.java" in occ["file"]
        ]
        self.assertTrue(
            len(layout_occurrences) > 0,
            "Expected DefaultLayoutRenderPlan.java to be recorded under LIMIT:LIMIT_EXCEEDED occurrences",
        )
        self.assertEqual(layout_occurrences[0]["type"], "CONSTANT_REFERENCE")
        self.assertEqual(layout_occurrences[0]["expression"], "RenderBudget.CODE_LIMIT_EXCEEDED")


if __name__ == "__main__":
    unittest.main()
