"""The aggregator must never authorize retagging GA during patch development."""
import argparse
from contextlib import ExitStack, redirect_stdout
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("ga_readiness", ROOT / "scripts/verify-ga-readiness.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class ReadinessStateTests(unittest.TestCase):
    def evaluate(self, state, product_drift=False, failed_gate=None):
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            for name in dir(mod):
                if name.startswith("check_") and name != "check_product_freeze":
                    stack.enter_context(patch.object(mod, name, return_value={
                        "passed": name != failed_gate, "breaking_changes": 0,
                        "abi_breaking_changes": 0, "leaks": 0, "issues": [],
                    }))
            stack.enter_context(patch.object(mod, "check_product_freeze", return_value={
                "passed": not (state == "pre-ga" and product_drift),
                "release_state": state, "baseline_tag": "v1.0.0" if state == "post-ga" else "v1.0.0-RC3",
                "baseline_sha": "baseline", "target_version": "1.0.1-SNAPSHOT" if state == "post-ga" else "1.0.0",
                "requires_rc4": state == "pre-ga" and product_drift,
                "requires_patch_release": state == "post-ga" and product_drift,
            }))
            args = argparse.Namespace(baseline_tag=None, candidate="HEAD", target_version=None,
                                      require_ready_to_tag=False, release_state=state,
                                      output=str(Path(directory) / "report.json"))
            with redirect_stdout(io.StringIO()):
                return mod.evaluate_ga_readiness(args)

    def test_post_ga_is_patch_candidate_even_when_static_gates_pass(self):
        report = self.evaluate("post-ga", product_drift=True)
        self.assertEqual(report["verdict"], "PATCH_RELEASE_REQUIRED")
        self.assertFalse(report["requires_rc4"])
        self.assertIsNone(report["public_consumer_failures"])

    def test_failed_compatibility_blocks_patch(self):
        self.assertEqual(self.evaluate("post-ga", failed_gate="check_api_freeze")["verdict"], "NOT_READY_FOR_1_0_1")

    def test_pre_ga_freeze_remains_strict(self):
        self.assertEqual(self.evaluate("pre-ga", product_drift=True)["verdict"], "RC4_REQUIRED")
        self.assertEqual(self.evaluate("pre-ga")["verdict"], "READY_TO_TAG_1_0_0")
