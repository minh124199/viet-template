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

    def test_fresh_native_cleans_happen_after_jvm_parity_and_execution(self):
        source = SCRIPT.read_text(encoding="utf-8")
        native_build = source.index('"${ROOT_DIR}/mvnw" clean package -Dquarkus.package.type=native')
        parity = source.index("Comparing templates.idx byte-for-byte")
        maven_jvm_execution = source.index("Verifying executable Maven Quarkus runner execution")
        gradle_jvm_execution = source.index("Verifying executable Gradle Quarkus runner execution")
        self.assertLess(parity, native_build)
        self.assertLess(maven_jvm_execution, native_build)
        self.assertLess(gradle_jvm_execution, native_build)

    def test_quarkus_native_verifier_has_valid_shell_syntax(self):
        result = subprocess.run(["bash", "-n", str(SCRIPT)], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
