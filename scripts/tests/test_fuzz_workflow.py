from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
FUZZ_WORKFLOW = ROOT / ".github" / "workflows" / "fuzz.yml"


def discover_fuzz_test_classes(repo_root: Path) -> dict[str, str]:
    """
    Dynamically scans repository source for Java test classes referencing
    fuzz mode properties ('vietTemplate.fuzz.mode' or 'VIET_FUZZ_MODE').

    Returns a mapping of class_name -> subproject_dir, e.g.:
      {'TemplateIdPropertyTest': 'viet-template-api', ...}
    """
    fuzz_classes: dict[str, str] = {}
    for java_file in repo_root.rglob("*.java"):
        parts = java_file.parts
        # Ignore build outputs, compiler targets, and version control metadata
        if any(part in parts for part in ("target", "build", ".gradle", ".git")):
            continue
        try:
            rel = java_file.relative_to(repo_root)
        except ValueError:
            continue
        rel_parts = rel.parts
        # Only inspect test code under src/test/java
        if "src" not in rel_parts or "test" not in rel_parts:
            continue
        try:
            content = java_file.read_text(encoding="utf-8")
        except Exception:
            continue
        if "vietTemplate.fuzz.mode" in content or "VIET_FUZZ_MODE" in content:
            subproject = rel_parts[0]
            fuzz_classes[java_file.stem] = subproject
    return fuzz_classes


class FuzzWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(FUZZ_WORKFLOW.is_file(), f"Workflow file {FUZZ_WORKFLOW} does not exist")
        self.content = FUZZ_WORKFLOW.read_text(encoding="utf-8")
        self.data = yaml.safe_load(self.content)
        self.assertIsInstance(self.data, dict, "Workflow must parse as a valid YAML dictionary")
        self.job = self.data["jobs"]["deep-fuzz"]
        self.steps = self.job["steps"]
        self.step_map = {s.get("name"): s for s in self.steps if "name" in s}

    def test_fuzz_workflow_exists_and_is_valid_yaml(self):
        self.assertEqual(self.data.get("name"), "Robustness and Differential Fuzzing")
        self.assertEqual(self.data.get("permissions"), {"contents": "read"})

        # Verify triggers (PyYAML parses unquoted 'on:' as boolean True)
        triggers = self.data.get("on") or self.data.get(True, {})
        self.assertIn("schedule", triggers)
        self.assertEqual(triggers["schedule"], [{"cron": "0 2 * * *"}])
        self.assertIn("workflow_dispatch", triggers)

        dispatch_inputs = triggers["workflow_dispatch"].get("inputs", {})
        self.assertIn("fuzz_mode", dispatch_inputs)
        fuzz_mode_spec = dispatch_inputs["fuzz_mode"]
        self.assertEqual(fuzz_mode_spec.get("type"), "choice")
        self.assertEqual(fuzz_mode_spec.get("default"), "deep")
        self.assertEqual(fuzz_mode_spec.get("options"), ["deep", "normal"])

        self.assertIn("seed", dispatch_inputs)
        seed_spec = dispatch_inputs["seed"]
        self.assertEqual(seed_spec.get("type"), "string")
        self.assertEqual(seed_spec.get("default"), "")

    def test_fuzz_workflow_defines_java_matrix(self):
        self.assertIn("java: [ '25', '21' ]", self.content)
        matrix = self.job["strategy"]["matrix"]
        self.assertEqual(matrix["java"], ["25", "21"])
        self.assertFalse(self.job["strategy"].get("fail-fast", True))

    def test_fuzz_workflow_contains_bootstrap_step_before_gradle_fuzzing(self):
        bootstrap_name = "Bootstrap reactor artifacts for clean-room verification"
        bootstrap_cmd = "./mvnw install -DskipTests -Dspotless.check.skip=true -B"
        gradle_name = "Run Targeted Deep Fuzzing via Gradle"

        self.assertIn(bootstrap_name, self.content)
        self.assertIn(bootstrap_cmd, self.content)

        step_names = [s.get("name") for s in self.steps]
        self.assertIn(bootstrap_name, step_names)
        self.assertIn(gradle_name, step_names)

        bootstrap_idx = step_names.index(bootstrap_name)
        gradle_idx = step_names.index(gradle_name)
        self.assertLess(bootstrap_idx, gradle_idx, "Bootstrap step must execute before Gradle fuzzing")

        bootstrap_step = self.steps[bootstrap_idx]
        self.assertEqual(bootstrap_step.get("run"), bootstrap_cmd)

    def test_fuzz_workflow_configures_reproducible_seed_and_environment(self):
        seed_step_name = "Configure fuzzing seed and environment"
        self.assertIn(seed_step_name, self.step_map)

        seed_step = self.step_map[seed_step_name]
        seed_cmd = seed_step.get("run", "")

        # Verify seed derivation logic
        self.assertIn("inputs.seed", seed_cmd)
        self.assertIn("github.run_id", seed_cmd)
        self.assertIn("FUZZ_SEED", seed_cmd)
        self.assertIn("GITHUB_ENV", seed_cmd)

        # Verify reproduction output logging
        self.assertIn("gradlew", seed_cmd)
        self.assertIn("mvnw", seed_cmd)

        # Verify execution order: seed step before test steps
        step_names = [s.get("name") for s in self.steps]
        seed_idx = step_names.index(seed_step_name)
        gradle_idx = step_names.index("Run Targeted Deep Fuzzing via Gradle")
        maven_idx = step_names.index("Run Targeted Deep Fuzzing via Maven")
        self.assertLess(seed_idx, gradle_idx, "Seed configuration must run before Gradle fuzzing")
        self.assertLess(seed_idx, maven_idx, "Seed configuration must run before Maven fuzzing")

    def test_dynamic_discovery_all_fuzz_test_classes_targeted_in_gradle_and_maven(self):
        discovered = discover_fuzz_test_classes(ROOT)

        # Sanity check: Ensure known baseline fuzz/property tests are detected
        expected_baseline = {
            "TemplateIdPropertyTest",
            "VtlMutationParserFuzzTest",
            "VtlParserPropertyFuzzTest",
            "EscaperPropertyTest",
            "SafeUrlValidatorPropertyTest",
            "AstIrAotDifferentialFuzzTest",
            "VelocityDifferentialFuzzTest",
        }
        for cls_name in expected_baseline:
            self.assertIn(
                cls_name,
                discovered,
                f"Expected fuzz test class '{cls_name}' was not discovered from repository source",
            )

        gradle_step = self.step_map.get("Run Targeted Deep Fuzzing via Gradle")
        self.assertIsNotNone(gradle_step, "Targeted Gradle fuzzing step is missing")
        gradle_cmd = gradle_step.get("run", "")

        maven_step = self.step_map.get("Run Targeted Deep Fuzzing via Maven")
        self.assertIsNotNone(maven_step, "Targeted Maven fuzzing step is missing")
        maven_cmd = maven_step.get("run", "")

        # Verify that EVERY discovered test class is explicitly targeted in both Gradle and Maven
        missing_in_gradle = []
        missing_in_maven = []

        for cls_name, subproject in discovered.items():
            expected_gradle_target = f":{subproject}:test"
            expected_gradle_test = f"--tests {cls_name}"
            if expected_gradle_target not in gradle_cmd or expected_gradle_test not in gradle_cmd:
                missing_in_gradle.append(f"{cls_name} ({expected_gradle_target})")

            if subproject not in maven_cmd or cls_name not in maven_cmd:
                missing_in_maven.append(f"{cls_name} (module: {subproject})")

        self.assertEqual(
            [],
            missing_in_gradle,
            f"Fuzz test classes missing from Gradle targeted test execution: {missing_in_gradle}",
        )
        self.assertEqual(
            [],
            missing_in_maven,
            f"Fuzz test classes missing from Maven targeted test execution: {missing_in_maven}",
        )

    def test_fuzz_workflow_passes_fuzz_mode_and_seed_properties(self):
        mode_flag = "-DvietTemplate.fuzz.mode=${{ inputs.fuzz_mode || 'deep' }}"
        seed_flag = "-DvietTemplate.fuzz.seed=${FUZZ_SEED}"

        gradle_step = self.step_map.get("Run Targeted Deep Fuzzing via Gradle")
        self.assertIsNotNone(gradle_step)
        gradle_cmd = gradle_step.get("run", "")
        self.assertIn(mode_flag, gradle_cmd)
        self.assertIn(seed_flag, gradle_cmd)

        maven_step = self.step_map.get("Run Targeted Deep Fuzzing via Maven")
        self.assertIsNotNone(maven_step)
        maven_cmd = maven_step.get("run", "")
        self.assertIn(mode_flag, maven_cmd)
        self.assertIn(seed_flag, maven_cmd)

    def test_fuzz_workflow_does_not_execute_untargeted_unit_tests(self):
        gradle_step = self.step_map.get("Run Targeted Deep Fuzzing via Gradle", {})
        maven_step = self.step_map.get("Run Targeted Deep Fuzzing via Maven", {})

        gradle_cmd = gradle_step.get("run", "")
        maven_cmd = maven_step.get("run", "")

        # Untargeted root gradle test execution must NOT exist
        for line in gradle_cmd.splitlines():
            stripped = line.strip()
            if stripped.startswith("./gradlew"):
                self.assertNotIn(
                    stripped,
                    ("./gradlew test", "./gradlew check"),
                    "Untargeted root Gradle test execution is forbidden in fuzz workflow",
                )
                self.assertIn(
                    "--tests",
                    line,
                    f"Gradle command line '{line}' must explicitly specify targeted test filter '--tests'",
                )

        # Untargeted root maven test execution must NOT exist
        self.assertIn("-pl", maven_cmd, "Maven execution must specify targeted project list (-pl)")
        self.assertIn("-Dtest=", maven_cmd, "Maven execution must specify targeted test filter (-Dtest=)")

    def test_fuzz_workflow_uploads_test_reports_on_failure(self):
        upload_name = "Upload test reports on failure"
        self.assertIn(upload_name, self.step_map)

        upload_step = self.step_map[upload_name]
        self.assertEqual(upload_step.get("if"), "failure()")
        self.assertIn(
            "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1",
            self.content,
        )

        with_block = upload_step.get("with", {})
        paths = with_block.get("path", "")
        self.assertIn("**/build/reports/tests/", paths)
        self.assertIn("**/target/surefire-reports/", paths)


if __name__ == "__main__":
    unittest.main()
