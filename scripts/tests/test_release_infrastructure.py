import importlib.util
import io
import json
import re
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
bundle = load_script("validate-release-bundle.py")
parity = load_script("verify-build-parity.py")


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

    def test_detects_m18_release_gate_bypass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            text = text.replace(
                "needs: [validate-metadata, verify-builds, m18-release-qualification]",
                "needs: [validate-metadata, verify-builds]",
            )
            text = text.replace(
                "./scripts/verify-m18-release-gates.sh --clean-room --require-evidence",
                "./scripts/verify-m18-release-gates.sh --clean-room",
            )
            workflow.write_text(text)
            errors = self.validate(root)
            self.assertTrue(any("formal evidence" in error for error in errors))
            self.assertTrue(any("publication bundle must depend" in error for error in errors))

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

    def test_detects_missing_reactor_bootstrap_before_gradle_check(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            bootstrap_step = (
                "      - name: Bootstrap reactor artifacts for clean-room verification\n"
                "        run: ./mvnw install -DskipTests -Dspotless.check.skip=true -B\n"
            )
            self.assertIn(bootstrap_step, text)
            workflow.write_text(text.replace(bootstrap_step, ""))
            errors = self.validate(root)
            self.assertTrue(
                any(
                    "verify-builds must bootstrap reactor artifacts with './mvnw install -DskipTests' before running Gradle check"
                    in error
                    for error in errors
                )
            )

            # Also verify that placing bootstrap step after Gradle check is detected as out-of-order
            reordered = text.replace(bootstrap_step, "")
            gradle_step = (
                "      - name: Run Gradle check\n"
                "        run: ./gradlew check --no-daemon -Dspotless.check.skip=true\n"
            )
            self.assertIn(gradle_step, reordered)
            reordered = reordered.replace(gradle_step, gradle_step + bootstrap_step)
            workflow.write_text(reordered)
            reordered_errors = self.validate(root)
            self.assertTrue(
                any(
                    "verify-builds must bootstrap reactor artifacts with './mvnw install -DskipTests' before running Gradle check"
                    in error
                    for error in reordered_errors
                )
            )

    def test_detects_missing_workflow_dispatch_release_tag_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            self.assertIn("release_tag:", text)
            text = re.sub(
                r"      release_tag:\n        description:.*?\n        type:.*?\n        required:.*?\n        default:.*?\n",
                "",
                text,
            )
            workflow.write_text(text)
            errors = self.validate(root)
            self.assertTrue(
                any("release workflow must define release_tag input under workflow_dispatch" in error for error in errors)
            )

    def test_detects_missing_validate_metadata_outputs(self):
        for out in ("artifact_source_tag", "artifact_source_sha", "orchestration_sha"):
            with self.subTest(output=out), tempfile.TemporaryDirectory() as directory:
                root = self.fixture(directory)
                workflow = root / ".github/workflows/release.yml"
                text = workflow.read_text()
                self.assertIn(f"{out}:", text)
                text = re.sub(rf"^\s+{out}:.*?\n", "", text, flags=re.MULTILINE)
                workflow.write_text(text)
                errors = self.validate(root)
                self.assertTrue(
                    any(f"validate-metadata job must export {out}" in error for error in errors)
                )

    def test_detects_checkout_ref_missing_artifact_source_sha(self):
        for job_name in (
            "verify-builds",
            "m18-release-qualification",
            "package-and-validate-bundle",
            "publish-to-central",
        ):
            with self.subTest(job=job_name), tempfile.TemporaryDirectory() as directory:
                root = self.fixture(directory)
                workflow = root / ".github/workflows/release.yml"
                text = workflow.read_text()
                job_idx = text.index(f"  {job_name}:")
                next_job_match = re.search(r"\n  [a-z0-9-]+:", text[job_idx + 10:])
                job_end = job_idx + 10 + next_job_match.start() if next_job_match else len(text)
                job_section = text[job_idx:job_end]
                bad_job_section = job_section.replace(
                    "ref: ${{ needs.validate-metadata.outputs.artifact_source_sha || github.ref }}",
                    "ref: ${{ github.ref }}",
                )
                self.assertNotEqual(job_section, bad_job_section)
                modified_text = text[:job_idx] + bad_job_section + text[job_end:]
                workflow.write_text(modified_text)
                errors = self.validate(root)
                self.assertTrue(
                    any(f"{job_name} must use artifact_source_sha in checkout ref" in error for error in errors)
                )

    def test_detects_package_and_validate_bundle_missing_release_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            workflow = root / ".github/workflows/release.yml"
            text = workflow.read_text()
            job_idx = text.index("  package-and-validate-bundle:")
            next_job_match = re.search(r"\n  [a-z0-9-]+:", text[job_idx + 10:])
            job_end = job_idx + 10 + next_job_match.start() if next_job_match else len(text)
            job_section = text[job_idx:job_end]
            target_step = (
                "      - name: Install release test dependencies\n"
                "        run: python3 -m pip install --disable-pip-version-check -r scripts/requirements-release.txt\n"
            )
            self.assertIn(target_step, job_section)
            bad_job_section = job_section.replace(target_step, "")
            modified_text = text[:job_idx] + bad_job_section + text[job_end:]
            workflow.write_text(modified_text)
            errors = self.validate(root)
            self.assertTrue(
                any(
                    "package-and-validate-bundle must install release dependencies from scripts/requirements-release.txt"
                    in error
                    for error in errors
                )
            )


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

    def test_dispatch_release_tag_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            gh_output = Path(directory) / "gh_output"
            with mock.patch.dict(
                metadata.os.environ,
                {"RELEASE_TAG": "v1.0.0-RC1", "GITHUB_OUTPUT": str(gh_output)},
                clear=True,
            ), mock.patch.object(metadata.sys, "argv", ["verify-release-metadata.py"]):
                with self.assertRaises(SystemExit) as result:
                    metadata.main()
                self.assertEqual(0, result.exception.code)
            self.assertIn("tag_name=v1.0.0-RC1\n", gh_output.read_text())


class PublicationMetadataTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory)
        shutil.copy2(ROOT / "pom.xml", root / "pom.xml")
        shutil.copy2(ROOT / "build.gradle.kts", root / "build.gradle.kts")
        for mod in metadata.PUBLISHED_MODULES + metadata.NON_PUBLISHED_MODULES:
            mod_dir = root / mod
            mod_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / mod / "pom.xml", mod_dir / "pom.xml")
        return root

    def test_current_publication_metadata(self):
        errors = []
        metadata.validate_publication_metadata(errors, check_effective=False, check_online=False)
        self.assertEqual([], errors)

    def test_root_project_url_stays_repo_root(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            pom = root / "pom.xml"
            pom.write_text(
                pom.read_text().replace(
                    "<url>https://github.com/minh124199/viet-template</url>",
                    "<url>https://github.com/minh124199/viet-template/viet-template-parent</url>",
                )
            )
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(
                    any("Root pom.xml url must be 'https://github.com/minh124199/viet-template'" in e for e in errors)
                )

    def test_child_module_urls_are_tree_main(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            api_pom = root / "viet-template-api/pom.xml"
            # Test missing URL
            api_pom.write_text(re.sub(r"\s*<url>.*?</url>", "", api_pom.read_text()))
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(any("viet-template-api must have explicit <url>" in e for e in errors))

            # Test child merely inheriting repository-root URL
            api_pom.write_text(
                api_pom.read_text().replace(
                    "<description>Viet Template Public API and Core Contracts</description>",
                    "<description>Viet Template Public API and Core Contracts</description>\n    <url>https://github.com/minh124199/viet-template</url>",
                )
            )
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(any("merely inherited repository-root url" in e for e in errors))

            # Test malformed / invalid URL (without /tree/main)
            api_pom.write_text(
                api_pom.read_text().replace(
                    "<url>https://github.com/minh124199/viet-template</url>",
                    "<url>https://github.com/minh124199/viet-template/viet-template-api</url>",
                )
            )
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(any("viet-template-api url matches invalid appended pattern" in e for e in errors))

    def test_detection_of_missing_project_url_inheritance_attribute_on_parent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            pom = root / "pom.xml"
            # Remove child.project.url.inherit.append.path attribute
            pom.write_text(pom.read_text().replace('child.project.url.inherit.append.path="false"', ""))
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(any("child.project.url.inherit.append.path" in e for e in errors))

    def test_detection_of_missing_scm_inheritance_attributes_on_parent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            pom = root / "pom.xml"
            # Remove inheritance attributes
            pom.write_text(pom.read_text().replace('child.scm.connection.inherit.append.path="false"', ""))
            with mock.patch.object(metadata, "ROOT_DIR", root):
                errors = []
                metadata.validate_publication_metadata(errors)
                self.assertTrue(any("child.scm.connection.inherit.append.path" in e for e in errors))

    def test_generic_rejection_of_malformed_urls_and_scm(self):
        self.assertTrue(metadata.RE_INVALID_URL.match("https://github.com/minh124199/viet-template/viet-template-api"))
        self.assertFalse(metadata.RE_INVALID_URL.match("https://github.com/minh124199/viet-template/tree/main/viet-template-api"))
        self.assertFalse(metadata.RE_INVALID_URL.match("https://github.com/minh124199/viet-template"))

        # Reject obsolete git:// protocol for Viet Template
        self.assertTrue(metadata.RE_INVALID_SCM.search("scm:git:git://github.com/minh124199/viet-template.git"))
        self.assertTrue(metadata.RE_INVALID_SCM.search("scm:git:git://github.com/minh124199/viet-template.git/viet-template-api"))

        # Accept HTTPS read-only and SSH developer connections
        self.assertFalse(metadata.RE_INVALID_SCM.search("scm:git:https://github.com/minh124199/viet-template.git"))
        self.assertFalse(metadata.RE_INVALID_SCM.search("scm:git:ssh://git@github.com/minh124199/viet-template.git"))
        self.assertFalse(metadata.RE_INVALID_SCM.search("https://github.com/minh124199/viet-template"))

        # Reject appended module paths in HTTPS connection
        self.assertTrue(
            metadata.RE_INVALID_SCM.search("scm:git:https://github.com/minh124199/viet-template.git/viet-template-api")
        )
        self.assertTrue(metadata.RE_INVALID_SCM.search("https://github.com/minh124199/viet-template/viet-template-api"))

    def test_child_effective_pom_does_not_append_artifact_id_to_scm(self):
        # 1. Clean effective POM should produce 0 errors
        projects_xml = [
            f"""  <project xmlns="http://maven.apache.org/POM/4.0.0">
    <artifactId>{m}</artifactId>
    <url>https://github.com/minh124199/viet-template/tree/main/{m}</url>
    <scm>
      <connection>scm:git:https://github.com/minh124199/viet-template.git</connection>
      <developerConnection>scm:git:ssh://git@github.com/minh124199/viet-template.git</developerConnection>
      <url>https://github.com/minh124199/viet-template</url>
    </scm>
  </project>"""
            for m in metadata.PUBLISHED_MODULES
        ]
        sample_clean_effective = """<?xml version="1.0" encoding="UTF-8"?>
<projects>
  <project xmlns="http://maven.apache.org/POM/4.0.0">
    <artifactId>viet-template-parent</artifactId>
    <url>https://github.com/minh124199/viet-template</url>
    <scm>
      <connection>scm:git:https://github.com/minh124199/viet-template.git</connection>
      <developerConnection>scm:git:ssh://git@github.com/minh124199/viet-template.git</developerConnection>
      <url>https://github.com/minh124199/viet-template</url>
    </scm>
  </project>
""" + "\n".join(projects_xml) + "\n</projects>"

        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
            f.write(sample_clean_effective)
            clean_file = f.name
        try:
            errors = []
            metadata.validate_effective_pom(errors, effective_pom_path=clean_file)
            self.assertEqual([], errors)
        finally:
            Path(clean_file).unlink()

        # 2. Malformed effective POM with appended artifactId in SCM
        sample_malformed_effective = sample_clean_effective.replace(
            "<connection>scm:git:https://github.com/minh124199/viet-template.git</connection>",
            "<connection>scm:git:https://github.com/minh124199/viet-template.git/viet-template-api</connection>",
            1,
        )
        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
            f.write(sample_malformed_effective)
            bad_file = f.name
        try:
            errors = []
            metadata.validate_effective_pom(errors, effective_pom_path=bad_file)
            self.assertTrue(any("invalid scm.connection (appended module" in e for e in errors))
        finally:
            Path(bad_file).unlink()

        # 3. Malformed effective POM with obsolete git:// protocol
        sample_git_proto_effective = sample_clean_effective.replace(
            "<connection>scm:git:https://github.com/minh124199/viet-template.git</connection>",
            "<connection>scm:git:git://github.com/minh124199/viet-template.git</connection>",
            1,
        )
        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
            f.write(sample_git_proto_effective)
            git_file = f.name
        try:
            errors = []
            metadata.validate_effective_pom(errors, effective_pom_path=git_file)
            self.assertTrue(any("obsolete git://" in e for e in errors))
        finally:
            Path(git_file).unlink()

    def test_publication_bundle_pom_metadata_validation(self):
        errors = []
        curr_version = bundle.get_project_version()
        bundle.validate_parent_pom(ROOT, curr_version, errors)
        self.assertEqual([], errors)

        for mod in bundle.ALL_PUBLISHED_MODULES:
            bundle.validate_pom_metadata(
                ROOT / mod / "pom.xml",
                mod,
                curr_version,
                errors,
                enforce_production_dependencies=(mod in bundle.PRODUCTION_MODULES),
            )
        self.assertEqual([], errors)

        # Test failure on corrupted POM
        with tempfile.TemporaryDirectory() as directory:
            fake_pom = Path(directory) / "bad-pom.xml"
            fake_pom.write_text(f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>io.github.minh124199</groupId>
  <artifactId>viet-template-api</artifactId>
  <version>{curr_version}</version>
  <url>https://github.com/minh124199/viet-template/viet-template-api</url>
  <scm>
    <connection>scm:git:https://github.com/minh124199/viet-template.git/viet-template-api</connection>
  </scm>
</project>""")
            bad_errors = []
            bundle.validate_pom_metadata(fake_pom, "viet-template-api", curr_version, bad_errors)
            self.assertTrue(any("Malformed appended url" in e for e in bad_errors))
            self.assertTrue(any("Malformed appended scm connection" in e for e in bad_errors))


def __getattr__(name):
    if name == "ReleaseWorkflowContractTests":
        return WorkflowContractTests
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")


if __name__ == "__main__":
    unittest.main()
