import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "verify_1_1_release_candidate", ROOT / "scripts" / "verify-1.1-release-candidate.py"
)
candidate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(candidate)


class NativeEvidenceTests(unittest.TestCase):
    def payload(self, **overrides):
        data = {
            "workflowName": "Native Image Verification",
            "headSha": "candidate-sha",
            "status": "completed",
            "conclusion": "success",
            "jobs": [
                {"name": name, "status": "completed", "conclusion": "success"}
                for name in candidate.NATIVE_JOBS
            ],
        }
        data.update(overrides)
        return data

    def call_with(self, payload):
        with patch.object(
            candidate,
            "run",
            return_value=subprocess.CompletedProcess(
                ["gh"], 0, json.dumps(payload), ""
            ),
        ):
            return candidate.verify_native("1234", "candidate-sha")

    def test_exact_sha_and_all_three_jobs_pass(self):
        evidence = self.call_with(self.payload())
        self.assertEqual("1234", evidence["runId"])
        self.assertEqual("candidate-sha", evidence["sha"])
        self.assertEqual("PASS", evidence["status"])
        self.assertEqual(3, len(evidence["jobs"]))

    def test_different_sha_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "not a successful completed run"):
            self.call_with(self.payload(headSha="different-sha"))

    def test_missing_or_failed_required_job_fails_closed(self):
        data = self.payload()
        data["jobs"].pop()
        with self.assertRaisesRegex(RuntimeError, "Native required jobs"):
            self.call_with(data)


if __name__ == "__main__":
    unittest.main()
