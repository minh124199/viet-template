import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_").replace(".", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_ts = load_script("verify-typescript-contract-projection.py")


class VerifyTypeScriptContractProjectionTests(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_find_node_executable(self):
        node = verify_ts.find_node_executable()
        # On this environment, node is available
        self.assertTrue(node is None or isinstance(node, str))

    def test_find_tsc_executable(self):
        tsc = verify_ts.find_tsc_executable(ROOT)
        self.assertTrue(tsc is None or isinstance(tsc, Path))

    def test_parse_valid_xml_all_pass(self):
        import xml.sax.saxutils
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="M46ATest" tests="14" skipped="0" failures="0" errors="0">
"""
        for proto in verify_ts.EXPECTED_PROTOTYPES:
            xml_content += f'  <testcase name={xml.sax.saxutils.quoteattr(proto)} time="0.05"/>\n'
        xml_content += "</testsuite>\n"

        xml_file = self.temp_path / "valid.xml"
        xml_file.write_text(xml_content, encoding="utf-8")

        res = verify_ts.parse_qualification_test_results(xml_file)
        self.assertEqual(res["status"], "PASS")
        self.assertEqual(res["total"], 14)
        self.assertEqual(res["passed"], 14)
        self.assertEqual(res["failed"], 0)
        self.assertEqual(len(res["missing_prototypes"]), 0)

    def test_parse_xml_with_failure(self):
        import xml.sax.saxutils
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="M46ATest" tests="14" skipped="0" failures="1" errors="0">
"""
        for i, proto in enumerate(verify_ts.EXPECTED_PROTOTYPES):
            attr = xml.sax.saxutils.quoteattr(proto)
            if i == 0:
                xml_content += f'  <testcase name={attr} time="0.05"><failure message="error">fail</failure></testcase>\n'
            else:
                xml_content += f'  <testcase name={attr} time="0.05"/>\n'
        xml_content += "</testsuite>\n"

        xml_file = self.temp_path / "failure.xml"
        xml_file.write_text(xml_content, encoding="utf-8")

        res = verify_ts.parse_qualification_test_results(xml_file)
        self.assertEqual(res["status"], "FAIL")
        self.assertEqual(res["failed"], 1)
        self.assertEqual(res["passed"], 13)

    def test_parse_missing_xml(self):
        missing = self.temp_path / "nonexistent.xml"
        res = verify_ts.parse_qualification_test_results(missing)
        self.assertEqual(res["status"], "FAIL")
        self.assertIn("error", res)

    def test_generate_report(self):
        results = {
            "status": "PASS",
            "passed": 14,
            "failed": 0,
            "prototypes": {"proto1": {"status": "PASSED", "time_seconds": 0.05}},
        }
        report_file = self.temp_path / "report.json"
        verify_ts.generate_report(results, "node", Path("/path/to/tsc"), report_file)

        self.assertTrue(report_file.is_file())
        data = json.loads(report_file.read_text(encoding="utf-8"))
        self.assertEqual(data["milestone"], "M46A")
        self.assertEqual(data["verdict"], "GO")
        self.assertEqual(data["status"], "PASS")
        self.assertEqual(data["passed_prototypes"], 14)


if __name__ == "__main__":
    unittest.main()
