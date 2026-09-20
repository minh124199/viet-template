import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("benchmark_manifest", ROOT / "scripts" / "verify-benchmark-manifest.py")
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class BenchmarkManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.valid = json.loads((ROOT / "config" / "benchmark-manifest.json").read_text())

    def validate(self, data):
        return validator.validate_manifest(data, ROOT)

    def test_current_manifest_is_valid(self):
        self.assertEqual([], self.validate(self.valid))

    def test_rejects_duplicate_workload(self):
        data = copy.deepcopy(self.valid)
        data["workloads"].append(copy.deepcopy(data["workloads"][0]))
        self.assertTrue(any("duplicate workload ID" in error for error in self.validate(data)))

    def test_rejects_unknown_classification(self):
        data = copy.deepcopy(self.valid)
        data["workloads"][0]["category"] = "MARKETING"
        self.assertTrue(any("unknown category" in error for error in self.validate(data)))

    def test_rejects_weak_or_missing_protocol(self):
        data = copy.deepcopy(self.valid)
        data["protocols"]["RELEASE_QUALIFICATION"]["forks"] = 1
        self.assertTrue(any("at least 3 forks" in error for error in self.validate(data)))

    def test_rejects_missing_benchmark_class(self):
        data = copy.deepcopy(self.valid)
        data["workloads"][0]["suite"] = "MissingBenchmark"
        self.assertTrue(any("missing benchmark class" in error for error in self.validate(data)))

    def test_rejects_bad_track_and_marketing_eligibility(self):
        data = copy.deepcopy(self.valid)
        data["workloads"][8]["tracks"] = ["UNKNOWN"]
        data["workloads"][8]["category"] = "CHARACTERIZATION"
        self.assertTrue(any("unknown track" in error for error in self.validate(data)))
        self.assertTrue(any("claim-eligible" in error for error in self.validate(data)))

    def test_malformed_json_is_not_accepted_by_cli_parser(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text("{")
            with self.assertRaises(json.JSONDecodeError):
                json.loads(path.read_text())


if __name__ == "__main__":
    unittest.main()
