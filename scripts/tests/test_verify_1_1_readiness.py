import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("verify_1_1_readiness", ROOT / "scripts" / "verify-1.1-readiness.py")
readiness = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(readiness)
SIM_SPEC = importlib.util.spec_from_file_location("simulate_release", ROOT / "scripts" / "simulate-release.py")
simulator = importlib.util.module_from_spec(SIM_SPEC)
SIM_SPEC.loader.exec_module(simulator)


class ReadinessVerifierTests(unittest.TestCase):
    def test_candidate_version_and_historical_baseline_are_present(self):
        result = readiness.version_invariants()
        self.assertEqual("PASS", result["status"])
        self.assertEqual("1.1.0-SNAPSHOT", result["developmentVersion"])
        self.assertTrue(result["baselineTagObject"])

    def test_check_runner_distinguishes_pass_and_unavailable(self):
        passed = readiness.run("probe", [sys.executable, "-c", "print('ok')"])
        missing = readiness.run("missing", ["no-such-viet-template-command"])
        self.assertEqual("PASS", passed["status"])
        self.assertEqual(0, passed["exitCode"])
        self.assertEqual("UNAVAILABLE", missing["status"])
        self.assertIsNone(missing["exitCode"])

    def test_missing_native_image_tool_is_unavailable_not_a_pass(self):
        missing = readiness.run("native", ["bash", "-c", "native-image --version"])
        if missing["exitCode"] == 127:
            self.assertEqual("UNAVAILABLE", missing["status"])

    def test_mandatory_checks_do_not_invoke_release_or_publication_commands(self):
        forbidden = ("mvnw deploy", "gradlew publish", "gh release create", "gh workflow run")
        commands = [" ".join(item[1]) for item in readiness.checks()]
        for command in commands:
            for token in forbidden:
                self.assertNotIn(token, command)
        self.assertIn("releaseSimulation", {item[0] for item in readiness.checks()})

    def test_release_simulation_derives_next_patch_snapshot(self):
        self.assertEqual("1.1.1-SNAPSHOT", simulator.next_development_version("1.1.0"))
        with self.assertRaises(ValueError):
            simulator.next_development_version("1.1.0-RC1")


if __name__ == "__main__":
    unittest.main()
