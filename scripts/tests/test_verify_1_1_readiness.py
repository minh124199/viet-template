import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("verify_1_1_readiness", ROOT / "scripts" / "verify-1.1-readiness.py")
readiness = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(readiness)
SIM_SPEC = importlib.util.spec_from_file_location("simulate_release", ROOT / "scripts" / "simulate-release.py")
simulator = importlib.util.module_from_spec(SIM_SPEC)
SIM_SPEC.loader.exec_module(simulator)


class ReadinessVerifierTests(unittest.TestCase):
    def test_candidate_version_and_historical_baseline_are_present(self):
        # The readiness verifier intentionally describes the earlier
        # 1.1.0-SNAPSHOT state. Exercise that state in isolation so this test
        # remains meaningful on the later 1.1.0 release-preparation branch.
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "pom.xml").write_text(
                "<project><groupId>io.github.minh124199</groupId>"
                "<artifactId>viet-template-parent</artifactId>"
                "<version>1.1.0-SNAPSHOT</version></project>",
                encoding="utf-8",
            )
            with patch.object(readiness, "ROOT", root), patch.object(
                readiness, "git_value", return_value="immutable-v1.0.1-tag-object"
            ):
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

    def test_native_ci_evidence_requires_all_jobs_and_exact_candidate_sha(self):
        payload = {
            "workflowName": "Native Image Verification",
            "status": "completed",
            "conclusion": "success",
            "headSha": "candidate-sha",
            "url": "https://example.invalid/run/1",
            "jobs": [
                {"name": "GraalVM Native Image & Spring AOT (boot3, JDK 25)", "conclusion": "success"},
                {"name": "GraalVM Native Image & Spring AOT (boot4, JDK 25)", "conclusion": "success"},
                {"name": "Quarkus Security & REST CSRF Native Image (JDK 25)", "conclusion": "success"},
            ],
        }
        self.assertEqual("PASS", readiness.validate_native_ci_payload(payload, "candidate-sha")["status"])
        self.assertEqual("FAIL", readiness.validate_native_ci_payload(payload, "other-sha")["status"])
        payload["jobs"].pop()
        self.assertEqual("FAIL", readiness.validate_native_ci_payload(payload, "candidate-sha")["status"])

    def test_mandatory_checks_do_not_invoke_release_or_publication_commands(self):
        forbidden = ("mvnw deploy", "gradlew publish", "gh release create", "gh workflow run")
        commands = [" ".join(item[1]) for item in readiness.checks()]
        for command in commands:
            for token in forbidden:
                self.assertNotIn(token, command)
        self.assertIn("releaseSimulation", {item[0] for item in readiness.checks()})

    def test_builds_precede_compiled_surface_audits_and_intellij_uses_root_wrapper(self):
        entries = readiness.checks()
        positions = {entry[0]: index for index, entry in enumerate(entries)}
        self.assertLess(positions["maven"], positions["apiCompatibility"])
        self.assertLess(positions["gradle"], positions["publicSurface"])
        self.assertLess(positions["gradle"], positions["frameworkEntrypoints"])
        by_name = {entry[0]: entry for entry in entries}
        self.assertEqual(
            ["./gradlew", "-p", "editors/intellij", "test", "--no-daemon"],
            by_name["intellijTests"][1],
        )
        self.assertEqual(
            ["./gradlew", "-p", "editors/intellij", "buildPlugin", "--no-daemon"],
            by_name["intellijDistribution"][1],
        )

    def test_exact_sha_native_ci_can_supply_expensive_native_qualification(self):
        local_checks = {entry[0] for entry in readiness.checks()}
        ci_backed_checks = {entry[0] for entry in readiness.checks(skip_native_ci_backed=True)}
        self.assertTrue({"quarkus", "springNativeBoot3", "springNativeBoot4"}.issubset(local_checks))
        self.assertTrue({"quarkus", "springNativeBoot3", "springNativeBoot4"}.isdisjoint(ci_backed_checks))
        self.assertIn("quarkusDevMode", ci_backed_checks)

    def test_release_simulation_derives_next_patch_snapshot(self):
        self.assertEqual("1.1.1-SNAPSHOT", simulator.next_development_version("1.1.0"))
        with self.assertRaises(ValueError):
            simulator.next_development_version("1.1.0-RC1")


if __name__ == "__main__":
    unittest.main()
