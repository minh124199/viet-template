import importlib.util
import io
import json
import shutil
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]


def load_script(name):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


status = load_script("check-central-deployment.py")
extractor = load_script("extract-central-deployment-id.py")
central = load_script("verify-central-release.py")
metadata = load_script("verify-release-metadata.py")


class FakeResponse:
    def __init__(self, payload, http_status=200):
        self.status = http_status
        self.payload = payload if isinstance(payload, bytes) else json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self.payload


class CentralDeploymentTests(unittest.TestCase):
    def query(self, response):
        with mock.patch.object(status.urllib.request, "urlopen", return_value=response):
            return status.query_status("00000000-0000-0000-0000-000000000000", "user", "pass", 1)

    def test_all_documented_states_are_normalized(self):
        for state_name in ("PENDING", "VALIDATING", "VALIDATED", "PUBLISHING", "PUBLISHED", "FAILED"):
            with self.subTest(state=state_name):
                self.assertEqual(state_name, self.query(FakeResponse({"deploymentState": state_name.lower()})))

    def test_published_and_failed_exit_semantics(self):
        for state_name, expected in (("PUBLISHED", 0), ("FAILED", 1), ("PUBLISHING", 2)):
            with self.subTest(state=state_name), mock.patch.object(status, "query_status", return_value=state_name):
                code = status.monitor("id", "u", "p", 0, 1, 1, True, None)
                self.assertEqual(expected, code)

    def test_timeout_is_distinct_from_failure(self):
        with mock.patch.object(status, "query_status", return_value="PUBLISHING"):
            self.assertEqual(2, status.monitor("id", "u", "p", 0, 1, 1, False, None))

    def test_state_progression_reaches_published(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            with mock.patch.object(
                status, "query_status", side_effect=["PENDING", "VALIDATING", "PUBLISHING", "PUBLISHED"]
            ), mock.patch.object(status.time, "sleep"):
                self.assertEqual(0, status.monitor("id", "u", "p", 10, 1, 1, False, output))
            self.assertEqual("state=PUBLISHED\ndeployment_id=id\n", output.read_text())

    def test_http_auth_not_found_and_server_errors(self):
        for code in (401, 403, 404, 500, 503):
            with self.subTest(code=code), mock.patch.object(
                status.urllib.request,
                "urlopen",
                side_effect=urllib.error.HTTPError("url", code, "error", {}, io.BytesIO()),
            ):
                with self.assertRaisesRegex(status.CentralStatusError, f"HTTP {code}"):
                    status.query_status("id", "u", "p", 1)

    def test_malformed_json_and_missing_or_unknown_state(self):
        for payload in (b"not-json", {}, {"deploymentState": "SURPRISE"}):
            with self.subTest(payload=payload), mock.patch.object(
                status.urllib.request, "urlopen", return_value=FakeResponse(payload)
            ):
                with self.assertRaises(status.CentralStatusError):
                    status.query_status("id", "u", "p", 1)

    def test_network_timeout(self):
        with mock.patch.object(status.urllib.request, "urlopen", side_effect=TimeoutError("timed out")):
            with self.assertRaisesRegex(status.CentralStatusError, "request failed"):
                status.query_status("id", "u", "p", 1)


class DeploymentIdTests(unittest.TestCase):
    ID = "7c8cd16e-b123-4422-81c9-b19a324570cb"

    def test_extracts_single_repeated_id(self):
        text = f"Uploaded bundle, deploymentId: {self.ID}\nstatus deploymentId: {self.ID}"
        self.assertEqual(self.ID, extractor.extract(text))

    def test_machine_readable_output_file(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            status.emit("PUBLISHED", self.ID, output)
            self.assertEqual(f"state=PUBLISHED\ndeployment_id={self.ID}\n", output.read_text())

    def test_rejects_zero_or_multiple_ids(self):
        with self.assertRaises(ValueError):
            extractor.extract("no deployment here")
        with self.assertRaises(ValueError):
            extractor.extract(
                f"deploymentId: {self.ID}\ndeploymentId: 00000000-0000-0000-0000-000000000000"
            )


class PublicReleaseTests(unittest.TestCase):
    def test_guard_reports_absent_or_complete(self):
        with mock.patch.object(central, "exists", return_value=False):
            self.assertEqual(0, central.guard("1.2.3", 1, None))
        with mock.patch.object(central, "exists", return_value=True):
            self.assertEqual(0, central.guard("1.2.3", 1, None))

    def test_guard_rejects_partial_publication(self):
        with mock.patch.object(central, "exists", side_effect=[True] + [False] * 16):
            self.assertEqual(1, central.guard("1.2.3", 1, None))

    def test_verify_requires_public_set_and_excluded_absence(self):
        expected_count = 1 + 4 * 4
        valid_pom = b"<project><version>1.2.3</version></project>"
        with mock.patch.object(central, "exists", side_effect=[True] * expected_count + [False] * 8), mock.patch.object(
            central, "fetch", return_value=valid_pom
        ):
            self.assertEqual([], central.verify_once("1.2.3", 1))
        with mock.patch.object(central, "exists", return_value=False):
            self.assertTrue(central.verify_once("1.2.3", 1))

    def test_pom_audit_rejects_version_mismatch_and_snapshot(self):
        errors = central.audit_pom("module", "1.2.3", b"<project><version>1.2.4-SNAPSHOT</version></project>")
        self.assertEqual(2, len(errors))


class WorkflowContractTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory)
        for relative in (
            "pom.xml",
            "build.gradle.kts",
            ".github/workflows/release.yml",
            "viet-template-tck/pom.xml",
            "viet-template-benchmarks/pom.xml",
        ):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        return root

    def validate(self, root):
        with mock.patch.object(metadata, "ROOT_DIR", root):
            errors = []
            metadata.validate_workflow_contract(errors)
            return errors

    def test_current_workflow_contract(self):
        self.assertEqual([], self.validate(ROOT))

    def test_detects_published_wait_and_command_line_passphrase(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            pom = root / "pom.xml"
            pom.write_text(pom.read_text().replace("<waitUntil>validated</waitUntil>", "<waitUntil>published</waitUntil>"))
            workflow = root / ".github/workflows/release.yml"
            workflow.write_text(
                workflow.read_text().replace("-DskipTests", '-Dgpg.passphrase="$SIGNING_PASSWORD" -DskipTests')
            )
            errors = self.validate(root)
            self.assertTrue(any("waitUntil=validated" in error for error in errors))
            self.assertTrue(any("gpg.passphrase" in error for error in errors))

    def test_detects_direct_release_dependency_and_lost_exclusion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            workflow.write_text(
                workflow.read_text().replace(
                    "needs: [validate-metadata, central-consumer-smoke]",
                    "needs: [validate-metadata, publish-to-central]",
                )
            )
            tck_pom = root / "viet-template-tck/pom.xml"
            tck_pom.write_text(tck_pom.read_text().replace("<maven.deploy.skip>true</maven.deploy.skip>", ""))
            errors = self.validate(root)
            self.assertTrue(any("not directly on Maven deploy" in error for error in errors))
            self.assertTrue(any("viet-template-tck publication exclusion" in error for error in errors))

    def test_detects_deploy_rerun_bypass_and_missing_release_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            publish_start = text.index("  publish-to-central:")
            monitor_start = text.index("  wait-for-central-publication:")
            publish = text[publish_start:monitor_start].replace("${{ github.run_attempt }}", "1")
            text = text[:publish_start] + publish + text[monitor_start:]
            text = text.replace("          GH_REPO: ${{ github.repository }}\n", "")
            workflow.write_text(text)
            errors = self.validate(root)
            self.assertTrue(any("independently block rerun" in error for error in errors))
            self.assertTrue(any("explicitly identify the repository" in error for error in errors))

    def test_detects_recovery_skip_gating_and_unsafe_key_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            text = text.replace(
                "always() &&\n      needs.wait-for-central-publication.result == 'success' &&",
                "needs.wait-for-central-publication.result == 'success' &&",
            )
            text = text.replace(
                "gpg --batch --yes --delete-secret-keys \"$fingerprint\"",
                "gpg --batch --yes --delete-secret-keys \"$key_id\"",
            )
            workflow.write_text(text)
            errors = self.validate(root)
            self.assertTrue(any("skipped upload ancestors" in error for error in errors))
            self.assertTrue(any("full fingerprint" in error for error in errors))


class MetadataTagSelectionTests(unittest.TestCase):
    def test_explicit_empty_release_tag_does_not_become_branch_name(self):
        with mock.patch.dict(
            metadata.os.environ,
            {"RELEASE_TAG": "", "GITHUB_REF_NAME": "main", "GITHUB_REF_TYPE": "branch"},
            clear=True,
        ), mock.patch.object(metadata.sys, "argv", ["verify-release-metadata.py"]):
            with self.assertRaises(SystemExit) as result:
                metadata.main()
            self.assertEqual(0, result.exception.code)


if __name__ == "__main__":
    unittest.main()
