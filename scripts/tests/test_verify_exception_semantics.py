import importlib.util
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_script(name):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


exception_verifier = load_script("verify-exception-semantics.py")


class VerifyExceptionSemanticsTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def _create_source(self, module, rel_path, content):
        file_path = self.temp_path / module / "src/main/java" / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content, encoding="utf-8")
        return file_path

    def _create_allowlist(self, entries):
        allowlist_path = self.temp_path / "allowlist.json"
        allowlist_path.write_text(json.dumps(entries, indent=2), encoding="utf-8")
        return allowlist_path

    def test_current_repository_passes(self):
        """Verifies that the actual repository code and allowlist pass with 0 errors."""
        report_path = self.temp_path / "report.json"
        allowlist_path = ROOT / "config/architecture/exception-boundary-allowlist.json"
        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=ROOT,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )
        self.assertTrue(passed, f"Repository verification failed with violations: {violations}")
        self.assertEqual(len(violations), 0)
        self.assertEqual(report["summary"]["totalBroadCatches"], 31)
        self.assertEqual(report["summary"]["totalAllowlistEntries"], 31)
        self.assertEqual(report["summary"]["violationsCount"], 0)
        self.assertEqual(report["summary"]["staleEntriesCount"], 0)
        self.assertTrue(report_path.is_file())

    def test_unallowlisted_catch_fails(self):
        """Verifies that an unallowlisted catch block is detected and reported as a violation."""
        self._create_source(
            "viet-template-api",
            "com/example/MyService.java",
            """package com.example;
public class MyService {
    public void execute() {
        try {
            System.out.println("Executing");
        } catch (Exception e) {
            System.err.println("Failed");
        }
    }
}
""",
        )
        allowlist_path = self._create_allowlist([])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertGreaterEqual(len(violations), 1)
        self.assertTrue(
            any("Unallowlisted broad catch in com.example.MyService#execute catching Exception" in v for v in violations),
            f"Expected unallowlisted catch violation, got: {violations}",
        )

    def test_stale_allowlist_entry_fails(self):
        """Verifies that an allowlist entry for a non-existent method/class is reported as stale."""
        stale_entry = {
            "class": "com.example.NonExistentService",
            "method": "missingMethod",
            "caughtType": "Exception",
            "reason": "Test non-existent entry",
            "category": "INTERNAL",
            "owner": "api",
        }
        allowlist_path = self._create_allowlist([stale_entry])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertTrue(
            any("Stale allowlist entry: com.example.NonExistentService#missingMethod" in v for v in violations),
            f"Expected stale allowlist entry violation, got: {violations}",
        )

    def test_invalid_category_fails(self):
        """Verifies that an allowlist entry with an invalid category is rejected."""
        invalid_entry = {
            "class": "com.example.MyService",
            "method": "execute",
            "caughtType": "Exception",
            "reason": "Invalid category test",
            "category": "INVALID_CATEGORY_NAME",
            "owner": "engine",
        }
        allowlist_path = self._create_allowlist([invalid_entry])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertTrue(
            any("invalid category 'INVALID_CATEGORY_NAME'" in v for v in violations),
            f"Expected invalid category violation, got: {violations}",
        )

    def test_missing_fields_fails(self):
        """Verifies that an allowlist entry missing required fields like 'reason' is rejected."""
        missing_reason_entry = {
            "class": "com.example.MyService",
            "method": "execute",
            "caughtType": "Exception",
            "category": "EVALUATION",
            "owner": "engine",
            # missing "reason"
        }
        allowlist_path = self._create_allowlist([missing_reason_entry])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertTrue(
            any("Missing or empty required field 'reason'" in v for v in violations),
            f"Expected missing reason violation, got: {violations}",
        )

    def test_invalid_owner_fails(self):
        """Verifies that an allowlist entry with an invalid subsystem owner is rejected."""
        invalid_owner_entry = {
            "class": "com.example.MyService",
            "method": "execute",
            "caughtType": "Exception",
            "reason": "Test owner validation",
            "category": "INTERNAL",
            "owner": "unknown_subsystem",
        }
        allowlist_path = self._create_allowlist([invalid_owner_entry])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertTrue(
            any("invalid owner 'unknown_subsystem'" in v for v in violations),
            f"Expected invalid owner violation, got: {violations}",
        )

    def test_count_mismatch_unallowlisted_extra_catch(self):
        """Verifies that if code has 2 broad catches in a method but allowlist only has 1, the extra catch fails."""
        self._create_source(
            "viet-template-api",
            "com/example/MyService.java",
            """package com.example;
public class MyService {
    public void execute() {
        try {
            System.out.println("First");
        } catch (Exception e) {
            // First catch
        }
        try {
            System.out.println("Second");
        } catch (Exception e) {
            // Second catch
        }
    }
}
""",
        )
        single_entry = {
            "class": "com.example.MyService",
            "method": "execute",
            "caughtType": "Exception",
            "reason": "Only first catch allowlisted",
            "category": "EVALUATION",
            "owner": "api",
        }
        allowlist_path = self._create_allowlist([single_entry])
        report_path = self.temp_path / "report.json"

        passed, report, violations = exception_verifier.verify_exception_semantics(
            repo_root=self.temp_path,
            allowlist_path=allowlist_path,
            report_path=report_path,
        )

        self.assertFalse(passed)
        self.assertTrue(
            any("found 2 in code, but allowlist only registers 1" in v for v in violations),
            f"Expected count mismatch violation, got: {violations}",
        )

    def test_enclosing_method_resolution(self):
        """Verifies multi-line method, multi-line constructor, and lambda enclosing method resolution."""
        code = """package com.example;
public class ComplexClass {
    ComplexClass(
        String arg1,
        int arg2) {
        try {
            init();
        } catch (Exception e) {}
    }

    public <T> T multiLineMethod(
        String first,
        int second) throws Exception {
        Runnable r = () -> {
            try {
                run();
            } catch (Throwable t) {}
        };
        return null;
    }
}
"""
        cleaned = exception_verifier.strip_comments_and_strings(code)
        
        # Test constructor catch method name
        ctor_catch_pos = code.index("catch (Exception e)")
        ctor_method = exception_verifier.find_enclosing_method_at(cleaned, ctor_catch_pos, "ComplexClass")
        self.assertEqual(ctor_method, "ComplexClass")

        # Test multi-line method catch method name (inside lambda)
        method_catch_pos = code.index("catch (Throwable t)")
        method_name = exception_verifier.find_enclosing_method_at(cleaned, method_catch_pos, "ComplexClass")
        self.assertEqual(method_name, "multiLineMethod")


if __name__ == "__main__":
    unittest.main()
