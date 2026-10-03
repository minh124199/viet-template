import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "benchmark_definition_equivalence",
    ROOT / "scripts" / "perf" / "verify-benchmark-definition-equivalence.py",
)
equiv_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(equiv_module)


class BenchmarkDefinitionEquivalenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo_root = ROOT
        cls.v110_dir = ROOT / "benchmark-evidence" / "1.1.0"
        cls.rerun_dir = ROOT / "benchmark-evidence" / "1.0.0-openjdk25-rerun"
        cls.v110_manifest = json.loads((cls.v110_dir / "benchmark-definition.json").read_text(encoding="utf-8"))
        cls.rerun_manifest = json.loads((cls.rerun_dir / "benchmark-definition.json").read_text(encoding="utf-8"))

    def test_real_repo_benchmark_definitions_are_equivalent(self):
        errors = equiv_module.verify_benchmark_equivalence(
            self.v110_dir,
            self.rerun_dir,
            self.repo_root,
        )
        self.assertEqual([], errors, f"Unexpected equivalence errors: {errors}")

    def test_detects_missing_manifest(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "dir1"
            dir2 = tmp_path / "dir2"
            dir1.mkdir()
            dir2.mkdir()
            (dir1 / "benchmark-definition.json").write_text("{}", encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(any("Missing benchmark definition manifest" in err for err in errors))

    def test_detects_benchmark_file_sha_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            m2["benchmarkFiles"]["ComparativeEngineBenchmark.java"]["sha256"] = "0" * 64

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(
                any("Semantic difference detected in benchmark file ComparativeEngineBenchmark.java" in err for err in errors),
                f"Expected semantic difference error not found: {errors}",
            )

    def test_detects_missing_required_benchmark_file(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            del m1["benchmarkFiles"]["VietIrAdapter.java"]

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(
                any("missing required file: VietIrAdapter.java" in err for err in errors),
                f"Expected missing file error not found: {errors}",
            )

    def test_detects_protocol_parameter_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            m1["protocol"]["forks"] = 1

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(
                any("protocol mismatch for 'forks'" in err for err in errors),
                f"Expected protocol mismatch error not found: {errors}",
            )

    def test_detects_runtime_identity_jvmci_enabled(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            m2["runtimeIdentity"]["useJvmciCompiler"] = True

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(
                any("must disable JVMCI" in err for err in errors),
                f"Expected JVMCI disabled error not found: {errors}",
            )

    def test_detects_runtime_identity_version_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            m1["runtimeIdentity"]["javaVersion"] = "21.0.12.1"

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertTrue(
                any("javaVersion must be '25.0.4.1'" in err for err in errors),
                f"Expected javaVersion error not found: {errors}",
            )

    def test_permits_differing_tooling_sha(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            dir1 = tmp_path / "v110"
            dir2 = tmp_path / "rerun"
            dir1.mkdir()
            dir2.mkdir()

            m1 = copy.deepcopy(self.v110_manifest)
            m2 = copy.deepcopy(self.rerun_manifest)
            m1["toolingSha"] = "1" * 40
            m2["toolingSha"] = "2" * 40

            (dir1 / "benchmark-definition.json").write_text(json.dumps(m1), encoding="utf-8")
            (dir2 / "benchmark-definition.json").write_text(json.dumps(m2), encoding="utf-8")

            errors = equiv_module.verify_benchmark_equivalence(dir1, dir2)
            self.assertEqual([], errors, f"Differing tooling commit SHA should be permitted: {errors}")


if __name__ == "__main__":
    unittest.main()
