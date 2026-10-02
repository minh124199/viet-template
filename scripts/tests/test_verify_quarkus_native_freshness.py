from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "verify-quarkus-integration.sh"


class QuarkusNativeFreshnessTests(unittest.TestCase):
    def test_quarkus_native_verifier_uses_fixture_version_and_fresh_binaries(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("MAVEN_FIXTURE_VERSION=", source)
        self.assertIn("GRADLE_FIXTURE_VERSION=", source)
        self.assertIn('rm -f "${MAVEN_NATIVE_RUNNER}" "${GRADLE_NATIVE_RUNNER}"', source)
        self.assertIn('"${ROOT_DIR}/mvnw" clean package -Dquarkus.package.type=native', source)
        self.assertIn('"${ROOT_DIR}/gradlew" clean build -Dquarkus.package.type=native', source)
        self.assertNotIn("viet-template-native-cache", source)
        self.assertNotIn("1.0.0-runner", source)

    def test_quarkus_native_verifier_has_valid_shell_syntax(self):
        result = subprocess.run(["bash", "-n", str(SCRIPT)], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
