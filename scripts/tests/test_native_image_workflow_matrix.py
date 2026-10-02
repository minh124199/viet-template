from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NativeImageWorkflowMatrixTests(unittest.TestCase):
    def test_native_ci_runs_both_supported_spring_boot_generations(self):
        workflow = (ROOT / ".github" / "workflows" / "native-image.yml").read_text(encoding="utf-8")
        self.assertIn("bootGeneration: [ 'boot3', 'boot4' ]", workflow)
        self.assertIn('bash scripts/verify-native-image-integration.sh "${{ matrix.bootGeneration }}"', workflow)
        self.assertIn("Quarkus Security & REST CSRF Native Image", workflow)


if __name__ == "__main__":
    unittest.main()
