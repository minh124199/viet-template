"""
scripts/tests/test_ci_topology.py

Automated validation of continuous integration topology contracts and workflow invariants:
1. ci.yml topology:
   - Crucial jobs exist: formatting, gradle-build-tier-a, maven-build-tier-a,
     parity-check-tier-a, api-compatibility-tier-a, tck-gates-tier-a,
     release-infrastructure-tier-a, tier-b-jdk21-compatibility.
   - Explicit Tier A (JDK 25 LTS) and Tier B (JDK 21 baseline) definitions.
   - Multi-OS coverage: ubuntu-latest, macos-latest, windows-latest are all represented.
   - Build tool coverage: both Gradle and Maven are exercised across Tier A and Tier B.
2. devtools-restart.yml pairwise topology:
   - Pairwise matrix covers JDK 21 and JDK 25.
   - Pairwise matrix covers Maven and Gradle.
   - Pairwise matrix covers Spring Boot 3 and Spring Boot 4.
   - Pairwise combinations cover all pairs across dimensions.
3. native-image.yml topology:
   - Matrix covers boot3 and boot4 generations.
   - Matrix covers both Maven and Gradle native compilation.
   - quarkus-native-security job is present and executes native verification.
4. fuzz.yml topology:
   - Matrix covers both JDK 21 and JDK 25.
   - Explicitly targets the 7 deep fuzz classes.
   - Reactor bootstrap step executes before Gradle fuzzing.
"""

from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
CI_WORKFLOW = WORKFLOWS_DIR / "ci.yml"
DEVTOOLS_WORKFLOW = WORKFLOWS_DIR / "devtools-restart.yml"
NATIVE_IMAGE_WORKFLOW = WORKFLOWS_DIR / "native-image.yml"
FUZZ_WORKFLOW = WORKFLOWS_DIR / "fuzz.yml"

CRUCIAL_CI_JOBS = [
    "formatting",
    "gradle-build-tier-a",
    "maven-build-tier-a",
    "parity-check-tier-a",
    "api-compatibility-tier-a",
    "tck-gates-tier-a",
    "release-infrastructure-tier-a",
    "tier-b-jdk21-compatibility",
]

SEVEN_DEEP_FUZZ_CLASSES = [
    "TemplateIdPropertyTest",
    "VtlMutationParserFuzzTest",
    "VtlParserPropertyFuzzTest",
    "EscaperPropertyTest",
    "SafeUrlValidatorPropertyTest",
    "AstIrAotDifferentialFuzzTest",
    "VelocityDifferentialFuzzTest",
]


def load_yaml_workflow(path: Path) -> dict:
    content = path.read_text(encoding="utf-8")
    data = yaml.safe_load(content)
    if not isinstance(data, dict):
        raise ValueError(f"Workflow at {path} did not parse as a mapping")
    return data


def extract_setup_java_versions(job: dict) -> list[str]:
    """Returns list of java-version values configured in actions/setup-java steps."""
    versions = []
    for step in job.get("steps", []):
        uses = step.get("uses", "")
        if "actions/setup-java" in uses:
            with_block = step.get("with", {})
            v = str(with_block.get("java-version", "")).strip()
            if v:
                versions.append(v)
    return versions


def extract_job_operating_systems(job: dict) -> set[str]:
    """Extracts all operating systems targeted by a job, including matrix variations."""
    oses = set()
    runs_on = job.get("runs-on")
    if isinstance(runs_on, str) and not runs_on.startswith("${{"):
        oses.add(runs_on)

    strategy = job.get("strategy", {})
    matrix = strategy.get("matrix", {})
    if isinstance(matrix, dict):
        if "os" in matrix:
            os_val = matrix["os"]
            if isinstance(os_val, list):
                oses.update(os_val)
            elif isinstance(os_val, str):
                oses.add(os_val)
        for inc in matrix.get("include", []):
            if isinstance(inc, dict) and "os" in inc:
                oses.add(inc["os"])
    return oses


def job_uses_tool(job: dict, tool_indicator: str) -> bool:
    """Checks whether any step in the job executes commands with the given tool indicator."""
    for step in job.get("steps", []):
        run_cmd = step.get("run", "")
        if tool_indicator in run_cmd:
            return True
        uses_cmd = step.get("uses", "")
        if tool_indicator in uses_cmd:
            return True
    return False


class CiWorkflowTopologyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow_data = load_yaml_workflow(CI_WORKFLOW)
        cls.jobs = cls.workflow_data.get("jobs", {})

    def test_ci_workflow_crucial_jobs_exist(self):
        for job_id in CRUCIAL_CI_JOBS:
            self.assertIn(
                job_id,
                self.jobs,
                f"Crucial CI job '{job_id}' is missing from .github/workflows/ci.yml",
            )

    def test_ci_workflow_defines_tier_a_jdk25_and_tier_b_jdk21(self):
        # Tier A Java jobs must configure Java 25
        tier_a_java_jobs = [
            "formatting",
            "gradle-build-tier-a",
            "maven-build-tier-a",
            "parity-check-tier-a",
            "api-compatibility-tier-a",
            "tck-gates-tier-a",
        ]
        for job_id in tier_a_java_jobs:
            job = self.jobs[job_id]
            java_versions = extract_setup_java_versions(job)
            self.assertIn(
                "25",
                java_versions,
                f"Tier A job '{job_id}' must configure JDK 25 via actions/setup-java",
            )
            job_name = job.get("name", "")
            self.assertTrue(
                "Tier A" in job_name and ("25" in job_name or "JDK 25" in job_name),
                f"Tier A job '{job_id}' name '{job_name}' must explicitly declare Tier A and JDK 25",
            )

        # Tier B compatibility job must configure Java 21 baseline
        tier_b_job = self.jobs["tier-b-jdk21-compatibility"]
        tier_b_java = extract_setup_java_versions(tier_b_job)
        self.assertIn(
            "21",
            tier_b_java,
            "Tier B job 'tier-b-jdk21-compatibility' must configure JDK 21 baseline via actions/setup-java",
        )
        self.assertNotIn(
            "25",
            tier_b_java,
            "Tier B job 'tier-b-jdk21-compatibility' must strictly target JDK 21 baseline",
        )
        tier_b_name = tier_b_job.get("name", "")
        self.assertIn("Tier B", tier_b_name)
        self.assertIn("JDK 21", tier_b_name)

    def test_ci_workflow_multi_os_coverage(self):
        all_oses = set()
        for job_id, job in self.jobs.items():
            all_oses.update(extract_job_operating_systems(job))

        expected_oses = {"ubuntu-latest", "macos-latest", "windows-latest"}
        self.assertTrue(
            expected_oses.issubset(all_oses),
            f"CI workflow must represent all target OSes {expected_oses}, found: {all_oses}",
        )

        # Confirm targeted distribution: Gradle on Windows and Maven on macOS
        gradle_job = self.jobs["gradle-build-tier-a"]
        gradle_oses = extract_job_operating_systems(gradle_job)
        self.assertIn("windows-latest", gradle_oses, "Gradle Tier A build must cover windows-latest")
        self.assertIn("ubuntu-latest", gradle_oses, "Gradle Tier A build must cover ubuntu-latest")

        maven_job = self.jobs["maven-build-tier-a"]
        maven_oses = extract_job_operating_systems(maven_job)
        self.assertIn("macos-latest", maven_oses, "Maven Tier A build must cover macos-latest")
        self.assertIn("ubuntu-latest", maven_oses, "Maven Tier A build must cover ubuntu-latest")

    def test_ci_workflow_build_tool_coverage_across_tier_a_and_tier_b(self):
        # Tier A must cover both Gradle and Maven
        tier_a_jobs = [j for j_id, j in self.jobs.items() if "tier-a" in j_id or j_id == "formatting"]
        tier_a_uses_gradle = any(job_uses_tool(j, "gradle") for j in tier_a_jobs)
        tier_a_uses_maven = any(job_uses_tool(j, "mvnw") for j in tier_a_jobs)
        self.assertTrue(tier_a_uses_gradle, "Tier A jobs must exercise Gradle")
        self.assertTrue(tier_a_uses_maven, "Tier A jobs must exercise Maven")

        # Tier B must cover both Gradle and Maven
        tier_b_job = self.jobs["tier-b-jdk21-compatibility"]
        tier_b_uses_gradle = job_uses_tool(tier_b_job, "gradlew")
        tier_b_uses_maven = job_uses_tool(tier_b_job, "mvnw")
        self.assertTrue(tier_b_uses_gradle, "Tier B job must exercise Gradle on JDK 21")
        self.assertTrue(tier_b_uses_maven, "Tier B job must exercise Maven on JDK 21")


class DevToolsRestartTopologyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow_data = load_yaml_workflow(DEVTOOLS_WORKFLOW)
        cls.job = cls.workflow_data.get("jobs", {}).get("devtools-restart", {})
        cls.matrix_include = cls.job.get("strategy", {}).get("matrix", {}).get("include", [])

    def test_devtools_restart_pairwise_matrix_covers_all_dimensions(self):
        self.assertTrue(len(self.matrix_include) >= 4, "Pairwise matrix must have at least 4 entries")

        jdk_versions = {entry.get("java-version") for entry in self.matrix_include}
        build_tools = {entry.get("build-tool") for entry in self.matrix_include}
        boot_generations = {entry.get("boot-generation") for entry in self.matrix_include}

        # Pairwise matrix covers JDK 21 and JDK 25
        self.assertEqual(
            {"21", "25"},
            jdk_versions,
            f"Pairwise matrix must cover JDK 21 and JDK 25, got: {jdk_versions}",
        )

        # Pairwise matrix covers maven and gradle
        self.assertEqual(
            {"maven", "gradle"},
            build_tools,
            f"Pairwise matrix must cover maven and gradle, got: {build_tools}",
        )

        # Pairwise matrix covers boot3 and boot4
        self.assertEqual(
            {"boot3", "boot4"},
            boot_generations,
            f"Pairwise matrix must cover boot3 and boot4, got: {boot_generations}",
        )

    def test_devtools_restart_pairwise_orthogonal_combinations(self):
        # Verify 2-way projection coverage across dimensions
        java_tool_pairs = {(e["java-version"], e["build-tool"]) for e in self.matrix_include}
        java_boot_pairs = {(e["java-version"], e["boot-generation"]) for e in self.matrix_include}
        tool_boot_pairs = {(e["build-tool"], e["boot-generation"]) for e in self.matrix_include}

        expected_java_tool = {("21", "maven"), ("21", "gradle"), ("25", "maven"), ("25", "gradle")}
        expected_java_boot = {("21", "boot3"), ("21", "boot4"), ("25", "boot3"), ("25", "boot4")}
        expected_tool_boot = {("maven", "boot3"), ("maven", "boot4"), ("gradle", "boot3"), ("gradle", "boot4")}

        self.assertEqual(expected_java_tool, java_tool_pairs, "Pairwise matrix must cover all (java, tool) combinations")
        self.assertEqual(expected_java_boot, java_boot_pairs, "Pairwise matrix must cover all (java, boot) combinations")
        self.assertEqual(expected_tool_boot, tool_boot_pairs, "Pairwise matrix must cover all (tool, boot) combinations")

    def test_devtools_restart_executes_verification_script(self):
        steps = self.job.get("steps", [])
        expected_cmd = 'bash scripts/verify-devtools-restart-integration.sh "${{ matrix.build-tool }}" "${{ matrix.boot-generation }}"'
        matching = [s for s in steps if expected_cmd in s.get("run", "")]
        self.assertTrue(len(matching) > 0, f"Expected step running '{expected_cmd}' not found")


class NativeImageTopologyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow_data = load_yaml_workflow(NATIVE_IMAGE_WORKFLOW)
        cls.jobs = cls.workflow_data.get("jobs", {})

    def test_native_image_matrix_covers_boot3_and_boot4(self):
        job = self.jobs.get("native-image", {})
        matrix_include = job.get("strategy", {}).get("matrix", {}).get("include", [])
        boot_gens = {entry.get("bootGeneration") for entry in matrix_include}
        self.assertEqual(
            {"boot3", "boot4"},
            boot_gens,
            f"Native Image matrix must cover boot3 and boot4, got: {boot_gens}",
        )

    def test_native_image_matrix_covers_maven_and_gradle(self):
        job = self.jobs.get("native-image", {})
        matrix_include = job.get("strategy", {}).get("matrix", {}).get("include", [])
        tools = {entry.get("buildTool") for entry in matrix_include}
        self.assertEqual(
            {"maven", "gradle"},
            tools,
            f"Native Image matrix must cover maven and gradle, got: {tools}",
        )

    def test_native_image_quarkus_native_security_job_is_present(self):
        self.assertIn(
            "quarkus-native-security",
            self.jobs,
            "Native Image workflow must include the 'quarkus-native-security' job",
        )
        quarkus_job = self.jobs["quarkus-native-security"]
        steps = quarkus_job.get("steps", [])
        expected_cmd = "bash scripts/verify-quarkus-integration.sh --force-native"
        matching = [s for s in steps if expected_cmd in s.get("run", "")]
        self.assertTrue(
            len(matching) > 0,
            f"Job 'quarkus-native-security' must execute '{expected_cmd}'",
        )


class FuzzWorkflowTopologyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow_data = load_yaml_workflow(FUZZ_WORKFLOW)
        cls.job = cls.workflow_data.get("jobs", {}).get("deep-fuzz", {})
        cls.steps = cls.job.get("steps", [])

    def test_fuzz_workflow_matrix_covers_jdk21_and_jdk25(self):
        matrix_java = self.job.get("strategy", {}).get("matrix", {}).get("java", [])
        java_set = {str(v) for v in matrix_java}
        self.assertEqual(
            {"21", "25"},
            java_set,
            f"Fuzz workflow matrix must cover JDK 21 and JDK 25, got: {java_set}",
        )

    def test_fuzz_workflow_targets_seven_deep_fuzz_classes(self):
        self.assertEqual(7, len(SEVEN_DEEP_FUZZ_CLASSES))

        # Check Gradle targeted step
        gradle_steps = [s for s in self.steps if "Run Targeted Deep Fuzzing via Gradle" in s.get("name", "")]
        self.assertEqual(1, len(gradle_steps), "Targeted Gradle fuzz step must exist")
        gradle_cmd = gradle_steps[0].get("run", "")

        for cls_name in SEVEN_DEEP_FUZZ_CLASSES:
            self.assertIn(
                cls_name,
                gradle_cmd,
                f"Gradle fuzz step must explicitly target deep fuzz class '{cls_name}'",
            )

        # Check Maven targeted step
        maven_steps = [s for s in self.steps if "Run Targeted Deep Fuzzing via Maven" in s.get("name", "")]
        self.assertEqual(1, len(maven_steps), "Targeted Maven fuzz step must exist")
        maven_cmd = maven_steps[0].get("run", "")

        for cls_name in SEVEN_DEEP_FUZZ_CLASSES:
            self.assertIn(
                cls_name,
                maven_cmd,
                f"Maven fuzz step must explicitly target deep fuzz class '{cls_name}'",
            )

    def test_fuzz_workflow_reactor_bootstrap_before_gradle(self):
        bootstrap_name = "Bootstrap reactor artifacts for clean-room verification"
        gradle_name = "Run Targeted Deep Fuzzing via Gradle"

        step_names = [s.get("name", "") for s in self.steps]
        self.assertIn(bootstrap_name, step_names, "Reactor bootstrap step must be present")
        self.assertIn(gradle_name, step_names, "Targeted Gradle fuzz step must be present")

        bootstrap_idx = step_names.index(bootstrap_name)
        gradle_idx = step_names.index(gradle_name)
        self.assertLess(
            bootstrap_idx,
            gradle_idx,
            "Reactor bootstrap step must precede Gradle fuzz test execution",
        )


if __name__ == "__main__":
    unittest.main()
