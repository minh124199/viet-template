import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("m18_evidence", ROOT / "scripts" / "perf" / "verify-m18-evidence.py")
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class M18EvidenceTests(unittest.TestCase):
    def fixture(self, root: Path):
        evidence = root / "benchmark-evidence" / "m18"
        config = root / "config"
        evidence.mkdir(parents=True)
        config.mkdir()
        benchmark_manifest = config / "benchmark-manifest.json"
        benchmark_manifest.write_text("{}\n")
        files = []
        for name, kind in (
            ("environment-J21-G1.json", "environment"),
            ("comparative-J21-G1.json", "jmh-comparative-with-gc"),
            ("report.md", "report"),
        ):
            path = evidence / name
            path.write_text("{}\n")
            files.append({"path": name, "kind": kind, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        manifest = {
            "schemaVersion": "1.0.0", "classification": "RELEASE_QUALIFICATION",
            "gitSha": "a" * 40, "gitTree": "b" * 40, "createdAt": "2026-09-20T00:00:00Z",
            "qualificationInputSha256": "d" * 64,
            "qualificationProtocol": {"forks": 3, "warmupIterations": 5, "measurementIterations": 10},
            "profiles": [{"id": "J21-G1"}, {"id": "J25-G1"}], "competitors": [],
            "benchmarkManifestSha256": hashlib.sha256(benchmark_manifest.read_bytes()).hexdigest(),
            "correctness": {"passed": True, "workloads": 8, "engines": 6}, "files": files,
        }
        (evidence / "manifest.json").write_text(json.dumps(manifest))
        (evidence / "SHA256SUMS").write_text("".join(f"{entry['sha256']}  {entry['path']}\n" for entry in files))
        return evidence, manifest

    def test_valid_package(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence, _ = self.fixture(Path(directory))
            self.assertEqual([], validator.validate_evidence(evidence, "a" * 40))

    def test_rejects_sha_mismatch_and_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence, _ = self.fixture(Path(directory))
            (evidence / "report.md").write_text("tampered")
            with mock.patch.object(validator, "qualification_input_digest", return_value="e" * 64):
                errors = validator.validate_evidence(evidence, "c" * 40)
            self.assertTrue(any("does not match release source" in error for error in errors))
            self.assertTrue(any("checksum mismatch" in error for error in errors))

    def test_rejects_weak_protocol_and_missing_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence, manifest = self.fixture(Path(directory))
            manifest["qualificationProtocol"]["forks"] = 1
            manifest["profiles"] = [{"id": "J25-G1"}]
            (evidence / "manifest.json").write_text(json.dumps(manifest))
            errors = validator.validate_evidence(evidence)
            self.assertTrue(any("3 forks" in error for error in errors))
            self.assertTrue(any("J21-G1" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
