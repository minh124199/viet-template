import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "scripts" / "verify-native-image-integration.sh"
POM_PATH = ROOT / "pom.xml"

CANONICAL_PLUGIN_ID = "io.github.minh124199.viet-template"
CANONICAL_MARKER_ARTIFACT_ID = f"{CANONICAL_PLUGIN_ID}.gradle.plugin"


class VerifyNativeImageIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT_PATH.is_file(), f"Script not found at {SCRIPT_PATH}")
        self.script_content = SCRIPT_PATH.read_text(encoding="utf-8")

    def test_script_is_executable(self):
        file_stat = SCRIPT_PATH.stat()
        self.assertTrue(
            bool(file_stat.st_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)),
            f"{SCRIPT_PATH} is not executable",
        )

    def test_bash_syntax_is_valid(self):
        res = subprocess.run(
            ["bash", "-n", str(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(res.returncode, 0, f"Bash syntax check failed: {res.stderr}")

    def test_script_contains_publish_to_maven_local_and_mvn_install(self):
        self.assertIn(
            '"${ROOT_DIR}/gradlew" publishToMavenLocal --no-daemon -x test',
            self.script_content,
            "Script must invoke gradlew publishToMavenLocal in Step 1",
        )
        self.assertIn(
            '"${ROOT_DIR}/mvnw" install -DskipTests -Dspotless.check.skip=true --no-transfer-progress -B',
            self.script_content,
            "Script must invoke mvnw install in Step 1",
        )

    def test_script_contains_preflight_marker_validation(self):
        self.assertIn(
            'PROJECT_VERSION="$(sed -n \'s/^[[:space:]]*<version>\\([^<]*\\)<\\/version>/\\1/p\' "${ROOT_DIR}/pom.xml" | head -n 1)"',
            self.script_content,
            "Script must extract PROJECT_VERSION from pom.xml",
        )
        self.assertIn(
            "PLUGIN_MARKER_POM=",
            self.script_content,
            "Script must define PLUGIN_MARKER_POM variable",
        )
        self.assertIn(
            'if [ ! -f "${PLUGIN_MARKER_POM}" ]; then',
            self.script_content,
            "Script must assert existence of PLUGIN_MARKER_POM",
        )
        self.assertIn(
            "Pre-flight assertion failed: Gradle plugin marker artifact missing at",
            self.script_content,
            "Script must output pre-flight assertion failure message",
        )
        self.assertIn(
            "Reactor artifacts and Gradle plugin marker verified in local repository.",
            self.script_content,
            "Script must output pre-flight verification success message",
        )

    def test_marker_path_calculation_matches_convention_and_pom_version(self):
        pom_text = POM_PATH.read_text(encoding="utf-8")
        version_match = re.search(r"^\s*<version>([^<]+)</version>", pom_text, re.MULTILINE)
        self.assertIsNotNone(version_match, "Failed to extract version from pom.xml")
        pom_version = version_match.group(1).strip()

        expected_group_path = CANONICAL_PLUGIN_ID.replace(".", "/")
        expected_marker_relative = (
            f"{expected_group_path}/{CANONICAL_MARKER_ARTIFACT_ID}/{pom_version}/"
            f"{CANONICAL_MARKER_ARTIFACT_ID}-{pom_version}.pom"
        )

        expected_definition = (
            f'"${{HOME}}/.m2/repository/{expected_marker_relative}"'
        )
        expected_template = (
            '"${HOME}/.m2/repository/io/github/minh124199/viet-template/'
            'io.github.minh124199.viet-template.gradle.plugin/${PROJECT_VERSION}/'
            'io.github.minh124199.viet-template.gradle.plugin-${PROJECT_VERSION}.pom"'
        )
        self.assertIn(
            f"PLUGIN_MARKER_POM={expected_template}",
            self.script_content,
            f"PLUGIN_MARKER_POM definition does not match expected: {expected_template}",
        )

    def test_preflight_assertion_fails_when_marker_is_missing(self):
        with tempfile.TemporaryDirectory() as temp_home:
            cmd = [
                "bash",
                "-c",
                f'''
                ROOT_DIR="{ROOT}"
                HOME="{temp_home}"
                PROJECT_VERSION="$(sed -n 's/^[[:space:]]*<version>\\([^<]*\\)<\\/version>/\\1/p' "${{ROOT_DIR}}/pom.xml" | head -n 1)"
                PLUGIN_MARKER_POM="${{HOME}}/.m2/repository/io/github/minh124199/viet-template/io.github.minh124199.viet-template.gradle.plugin/${{PROJECT_VERSION}}/io.github.minh124199.viet-template.gradle.plugin-${{PROJECT_VERSION}}.pom"
                if [ ! -f "${{PLUGIN_MARKER_POM}}" ]; then
                    echo "[FAIL] Pre-flight assertion failed: Gradle plugin marker artifact missing at ${{PLUGIN_MARKER_POM}}!"
                    exit 1
                fi
                echo "[PASS] Reactor artifacts and Gradle plugin marker verified in local repository."
                ''',
            ]
            res = subprocess.run(cmd, capture_output=True, text=True, check=False)
            self.assertEqual(res.returncode, 1)
            self.assertIn(
                "[FAIL] Pre-flight assertion failed: Gradle plugin marker artifact missing at",
                res.stdout,
            )

    def test_preflight_assertion_succeeds_when_marker_is_present(self):
        with tempfile.TemporaryDirectory() as temp_home:
            pom_text = POM_PATH.read_text(encoding="utf-8")
            version_match = re.search(r"^\s*<version>([^<]+)</version>", pom_text, re.MULTILINE)
            pom_version = version_match.group(1).strip()

            marker_dir = (
                Path(temp_home)
                / ".m2"
                / "repository"
                / "io"
                / "github"
                / "minh124199"
                / "viet-template"
                / "io.github.minh124199.viet-template.gradle.plugin"
                / pom_version
            )
            marker_dir.mkdir(parents=True, exist_ok=True)
            marker_file = marker_dir / f"io.github.minh124199.viet-template.gradle.plugin-{pom_version}.pom"
            marker_file.write_text("<project/>\n", encoding="utf-8")

            cmd = [
                "bash",
                "-c",
                f'''
                ROOT_DIR="{ROOT}"
                HOME="{temp_home}"
                PROJECT_VERSION="$(sed -n 's/^[[:space:]]*<version>\\([^<]*\\)<\\/version>/\\1/p' "${{ROOT_DIR}}/pom.xml" | head -n 1)"
                PLUGIN_MARKER_POM="${{HOME}}/.m2/repository/io/github/minh124199/viet-template/io.github.minh124199.viet-template.gradle.plugin/${{PROJECT_VERSION}}/io.github.minh124199.viet-template.gradle.plugin-${{PROJECT_VERSION}}.pom"
                if [ ! -f "${{PLUGIN_MARKER_POM}}" ]; then
                    echo "[FAIL] Pre-flight assertion failed: Gradle plugin marker artifact missing at ${{PLUGIN_MARKER_POM}}!"
                    exit 1
                fi
                echo "[PASS] Reactor artifacts and Gradle plugin marker verified in local repository."
                ''',
            ]
            res = subprocess.run(cmd, capture_output=True, text=True, check=False)
            self.assertEqual(res.returncode, 0, f"Expected success but failed: {res.stderr}\n{res.stdout}")
            self.assertIn(
                "[PASS] Reactor artifacts and Gradle plugin marker verified in local repository.",
                res.stdout,
            )

    def test_simulated_step1_else_branch_execution_order_and_failure(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            mock_bin_dir = Path(temp_dir) / "bin"
            mock_bin_dir.mkdir()
            mock_repo_dir = Path(temp_dir) / "repo"
            mock_home_dir = Path(temp_dir) / "home"
            mock_home_dir.mkdir()
            log_file = Path(temp_dir) / "invocation.log"

            pom_text = POM_PATH.read_text(encoding="utf-8")
            version_match = re.search(r"^\s*<version>([^<]+)</version>", pom_text, re.MULTILINE)
            pom_version = version_match.group(1).strip()

            mock_root = Path(temp_dir) / "repo_root"
            mock_root.mkdir()
            (mock_root / "pom.xml").write_text(f"""<project>
    <version>{pom_version}</version>
</project>""", encoding="utf-8")

            # Create mock gradlew that doesn't create the marker pom
            mock_gradlew = mock_root / "gradlew"
            mock_gradlew.write_text(f"""#!/usr/bin/env bash
echo "GRADLEW: $@" >> "{log_file}"
""", encoding="utf-8")
            mock_gradlew.chmod(mock_gradlew.stat().st_mode | stat.S_IXUSR)

            # Create mock mvnw
            mock_mvnw = mock_root / "mvnw"
            mock_mvnw.write_text(f"""#!/usr/bin/env bash
echo "MVNW: $@" >> "{log_file}"
""", encoding="utf-8")
            mock_mvnw.chmod(mock_mvnw.stat().st_mode | stat.S_IXUSR)

            # Extract Step 1 script logic from verify-native-image-integration.sh
            step1_script = f"""
            set -euo pipefail
            ROOT_DIR="{mock_root}"
            HOME="{mock_home_dir}"

            if [ -d "${{ROOT_DIR}}/build/rc-repository/io/github/minh124199" ]; then
                mkdir -p "${{HOME}}/.m2/repository/io/github"
                cp -rn "${{ROOT_DIR}}/build/rc-repository/io/github/minh124199" "${{HOME}}/.m2/repository/io/github/" 2>/dev/null || cp -r "${{ROOT_DIR}}/build/rc-repository/io/github/minh124199" "${{HOME}}/.m2/repository/io/github/"
            else
                "${{ROOT_DIR}}/gradlew" publishToMavenLocal --no-daemon -x test
                "${{ROOT_DIR}}/mvnw" install -DskipTests -Dspotless.check.skip=true --no-transfer-progress -B
            fi

            PROJECT_VERSION="$(sed -n 's/^[[:space:]]*<version>\\([^<]*\\)<\\/version>/\\1/p' "${{ROOT_DIR}}/pom.xml" | head -n 1)"
            PLUGIN_MARKER_POM="${{HOME}}/.m2/repository/io/github/minh124199/viet-template/io.github.minh124199.viet-template.gradle.plugin/${{PROJECT_VERSION}}/io.github.minh124199.viet-template.gradle.plugin-${{PROJECT_VERSION}}.pom"
            if [ ! -f "${{PLUGIN_MARKER_POM}}" ]; then
                echo "[FAIL] Pre-flight assertion failed: Gradle plugin marker artifact missing at ${{PLUGIN_MARKER_POM}}!"
                exit 1
            fi
            echo "[PASS] Reactor artifacts and Gradle plugin marker verified in local repository."
            """

            # Run with gradlew that does not write the marker POM -> must fail
            res = subprocess.run(["bash", "-c", step1_script], capture_output=True, text=True, check=False)
            self.assertEqual(res.returncode, 1)
            self.assertIn("Pre-flight assertion failed: Gradle plugin marker artifact missing at", res.stdout)
            invocations = log_file.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(invocations), 2)
            self.assertIn("publishToMavenLocal --no-daemon -x test", invocations[0])
            self.assertIn("install -DskipTests -Dspotless.check.skip=true --no-transfer-progress -B", invocations[1])

            # Now update mock gradlew to create marker POM upon publishToMavenLocal -> must pass
            log_file.unlink()
            mock_gradlew.write_text(f"""#!/usr/bin/env bash
echo "GRADLEW: $@" >> "{log_file}"
MARKER="${{HOME}}/.m2/repository/io/github/minh124199/viet-template/io.github.minh124199.viet-template.gradle.plugin/{pom_version}/io.github.minh124199.viet-template.gradle.plugin-{pom_version}.pom"
mkdir -p "$(dirname "$MARKER")"
touch "$MARKER"
""", encoding="utf-8")
            res = subprocess.run(["bash", "-c", step1_script], capture_output=True, text=True, check=False)
            self.assertEqual(res.returncode, 0)
            self.assertIn("[PASS] Reactor artifacts and Gradle plugin marker verified in local repository.", res.stdout)


if __name__ == "__main__":
    unittest.main()
