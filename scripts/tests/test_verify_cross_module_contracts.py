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


cross_module = load_script("verify-cross-module-contracts.py")


class CrossModuleContractsTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.orig_repo_root = cross_module.REPO_ROOT
        cross_module.REPO_ROOT = Path(self.temp_dir)

    def tearDown(self):
        cross_module.REPO_ROOT = self.orig_repo_root
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def _create_source(self, module, source_type, file_name, content):
        # source_type: "main" or "test"
        dir_path = Path(self.temp_dir) / module / "src" / source_type / "java" / "com" / "example"
        dir_path.mkdir(parents=True, exist_ok=True)
        file_path = dir_path / file_name
        file_path.write_text(content, encoding="utf-8")
        return file_path

    def test_main_consumer_detected(self):
        self._create_source(
            "mod-consumer",
            "main",
            "Service.java",
            "package com.example;\nimport com.owner.InternalService;\npublic class Service {}",
        )
        self.assertTrue(
            cross_module.check_module_consumes_contract(
                "mod-consumer", "com.owner.InternalService", consumer_scope=["MAIN"]
            )
        )

    def test_test_consumer_detected(self):
        self._create_source(
            "mod-tck",
            "test",
            "ServiceTest.java",
            "package com.example;\nimport com.owner.TestBridge;\npublic class ServiceTest {}",
        )
        self.assertTrue(
            cross_module.check_module_consumes_contract(
                "mod-tck", "com.owner.TestBridge", consumer_scope=["TEST"]
            )
        )

    def test_benchmark_consumer_detected(self):
        self._create_source(
            "mod-benchmarks",
            "main",
            "MicroBench.java",
            "package com.example;\nimport com.owner.BenchState;\npublic class MicroBench {}",
        )
        self.assertTrue(
            cross_module.check_module_consumes_contract(
                "mod-benchmarks", "com.owner.BenchState", consumer_scope=["BENCHMARK"]
            )
        )

    def test_nested_class_consumer_detected(self):
        self._create_source(
            "mod-consumer",
            "main",
            "Consumer.java",
            "package com.example;\nimport com.owner.Outer.NestedState;\npublic class Consumer { NestedState s; }",
        )
        self.assertTrue(
            cross_module.check_module_consumes_contract(
                "mod-consumer", "com.owner.Outer$NestedState", consumer_scope=["MAIN"]
            )
        )

    def test_production_contract_cannot_be_justified_only_by_test_source(self):
        # Reference exists only in src/test/java
        self._create_source(
            "mod-consumer",
            "test",
            "ConsumerTest.java",
            "package com.example;\nimport com.owner.InternalContract;\npublic class ConsumerTest {}",
        )
        # Searching with MAIN scope must NOT find it
        self.assertFalse(
            cross_module.check_module_consumes_contract(
                "mod-consumer", "com.owner.InternalContract", consumer_scope=["MAIN"]
            )
        )

    def test_no_actual_consumer_returns_false(self):
        self._create_source(
            "mod-consumer",
            "main",
            "Other.java",
            "package com.example;\npublic class Other {}",
        )
        self.assertFalse(
            cross_module.check_module_consumes_contract(
                "mod-consumer", "com.owner.UnusedContract", consumer_scope=["MAIN"]
            )
        )

    def test_contract_scope_categorization_and_separation(self):
        contracts = [
            {"fqcn": "com.owner.Prod1", "owningModule": "mod-a", "consumingModules": ["mod-b"], "consumerScope": ["MAIN"]},
            {"fqcn": "com.owner.Prod2", "owningModule": "mod-a", "consumingModules": ["mod-b"], "consumerScope": ["MAIN"]},
            {"fqcn": "com.owner.TestOnly", "owningModule": "mod-a", "consumingModules": ["mod-tck"], "consumerScope": ["TEST"]},
            {"fqcn": "com.owner.BenchOnly", "owningModule": "mod-a", "consumingModules": ["mod-bench"], "consumerScope": ["BENCHMARK"]},
            {"fqcn": "com.owner.MixedNonProd", "owningModule": "mod-a", "consumingModules": ["mod-bench", "mod-tck"], "consumerScope": ["BENCHMARK", "TEST"]},
        ]

        production_contracts = [c for c in contracts if c.get("consumerScope", ["MAIN"]) == ["MAIN"]]
        test_contracts = [c for c in contracts if c.get("consumerScope", ["MAIN"]) == ["TEST"]]
        benchmark_contracts = [c for c in contracts if c.get("consumerScope", ["MAIN"]) == ["BENCHMARK"]]
        mixed_nonprod = [c for c in contracts if "MAIN" not in c.get("consumerScope", ["MAIN"]) and len(c.get("consumerScope", ["MAIN"])) > 1]

        self.assertEqual(len(production_contracts), 2)
        self.assertEqual(len(test_contracts), 1)
        self.assertEqual(len(benchmark_contracts), 1)
        self.assertEqual(len(mixed_nonprod), 1)

        # Test-only and benchmark-only contracts are NOT counted in production edges
        prod_edges = {}
        for c in production_contracts:
            key = (c["owningModule"], tuple(sorted(c["consumingModules"])))
            prod_edges[key] = prod_edges.get(key, 0) + 1

        self.assertEqual(len(prod_edges), 1)
        self.assertEqual(prod_edges[("mod-a", ("mod-b",))], 2)
        self.assertNotIn(("mod-a", ("mod-tck",)), prod_edges)
        self.assertNotIn(("mod-a", ("mod-bench",)), prod_edges)


if __name__ == "__main__":
    unittest.main()
