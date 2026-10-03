from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
FUZZ_WORKFLOW = ROOT / ".github" / "workflows" / "fuzz.yml"


class FuzzWorkflowTests(unittest.TestCase):
    def test_fuzz_workflow_exists_and_is_valid_yaml(self):
        self.assertTrue(FUZZ_WORKFLOW.is_file(), f"Workflow file {FUZZ_WORKFLOW} does not exist")
        content = FUZZ_WORKFLOW.read_text(encoding="utf-8")
        data = yaml.safe_load(content)
        self.assertIsInstance(data, dict)
        self.assertEqual(data.get("name"), "Robustness and Differential Fuzzing")

    def test_fuzz_workflow_defines_java_matrix(self):
        content = FUZZ_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("java: [ '25', '21' ]", content)
        data = yaml.safe_load(content)
        matrix = data["jobs"]["deep-fuzz"]["strategy"]["matrix"]
        self.assertEqual(matrix["java"], ["25", "21"])

    def test_fuzz_workflow_contains_bootstrap_step_before_gradle_fuzzing(self):
        content = FUZZ_WORKFLOW.read_text(encoding="utf-8")
        bootstrap_name = "Bootstrap reactor artifacts for clean-room verification"
        bootstrap_cmd = "./mvnw install -DskipTests -Dspotless.check.skip=true -B"
        gradle_name = "Run Deep Fuzzing via Gradle"

        self.assertIn(bootstrap_name, content)
        self.assertIn(bootstrap_cmd, content)

        data = yaml.safe_load(content)
        steps = data["jobs"]["deep-fuzz"]["steps"]
        step_names = [s.get("name") for s in steps]
        self.assertIn(bootstrap_name, step_names)
        self.assertIn(gradle_name, step_names)

        bootstrap_idx = step_names.index(bootstrap_name)
        gradle_idx = step_names.index(gradle_name)
        self.assertLess(bootstrap_idx, gradle_idx, "Bootstrap step must execute before Gradle fuzzing")

        bootstrap_step = steps[bootstrap_idx]
        self.assertEqual(bootstrap_step.get("run"), bootstrap_cmd)

    def test_fuzz_workflow_runs_gradle_and_maven_fuzzing_with_fuzz_mode(self):
        content = FUZZ_WORKFLOW.read_text(encoding="utf-8")
        fuzz_flag = "-DvietTemplate.fuzz.mode=${{ inputs.fuzz_mode || 'deep' }}"
        self.assertIn(fuzz_flag, content)

        data = yaml.safe_load(content)
        steps = data["jobs"]["deep-fuzz"]["steps"]
        step_runs = {s.get("name"): s.get("run", "") for s in steps}

        gradle_step = step_runs.get("Run Deep Fuzzing via Gradle")
        maven_step = step_runs.get("Run Deep Fuzzing via Maven")

        self.assertIsNotNone(gradle_step)
        self.assertIn("./gradlew test", gradle_step)
        self.assertIn(fuzz_flag, gradle_step)

        self.assertIsNotNone(maven_step)
        self.assertIn("./mvnw test", maven_step)
        self.assertIn(fuzz_flag, maven_step)


if __name__ == "__main__":
    unittest.main()
