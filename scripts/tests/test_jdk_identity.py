import importlib.util
import json
import os
import stat
import subprocess
import tempfile
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "jdk_identity", ROOT / "scripts" / "perf" / "jdk_identity.py"
)
jdk_identity = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(jdk_identity)

FIXTURES_DIR = ROOT / "scripts" / "tests" / "fixtures" / "jdk-identity"
RUNNER_SCRIPT = ROOT / "scripts" / "perf" / "run-m18-comparative-qualification.sh"

LOCAL_JDK_ARCH_25 = Path("/home/lynguyen/opt/jdk25-pkg/usr/lib/jvm/java-25-openjdk/bin/java")
LOCAL_JDK_ARCH_21 = Path("/home/lynguyen/opt/jdk21-pkg/usr/lib/jvm/java-21-openjdk/bin/java")
LOCAL_JDK_TEMURIN_21 = Path("/home/lynguyen/.jdks/jdk-21.0.12.1+1/bin/java")
LOCAL_JDK_GRAAL_25 = Path("/home/lynguyen/opt/graalvm-jdk-25/bin/java")
LOCAL_JDK_GRAAL_CE_21 = Path("/home/lynguyen/opt/graalvm-community-openjdk-21.0.2+13.1/bin/java")


class JdkIdentityParserTests(unittest.TestCase):
    def test_parse_properties_basic_and_continuation_lines(self):
        sample = """Property settings:
    file.encoding = UTF-8
    java.class.path =
    java.library.path = /usr/java/packages/lib
        /usr/lib64
        /lib64
    java.runtime.name = OpenJDK Runtime Environment
    java.specification.version = 25

openjdk version "25.0.4.1" 2026-08-18
OpenJDK Runtime Environment (build 25.0.4.1)
"""
        props = jdk_identity.parse_properties(sample)
        self.assertEqual(props.get("file.encoding"), "UTF-8")
        self.assertEqual(props.get("java.class.path"), "")
        self.assertEqual(props.get("java.library.path"), "/usr/java/packages/lib")
        self.assertEqual(props.get("java.runtime.name"), "OpenJDK Runtime Environment")
        self.assertEqual(props.get("java.specification.version"), "25")
        self.assertNotIn("openjdk version", props)
        self.assertNotIn("Property settings:", props)

    def test_parse_properties_ignores_comments(self):
        sample = """# This is a comment
# Another comment
    java.vendor = Red Hat, Inc.
"""
        props = jdk_identity.parse_properties(sample)
        self.assertEqual(props.get("java.vendor"), "Red Hat, Inc.")
        self.assertEqual(len(props), 1)

    def test_parse_flags_handles_standard_and_modified_flags(self):
        sample = """[Global flags]
     bool EnableJVMCI                              = false                          {JVMCI experimental} {default}
     bool UseG1GC                                 := true                                     {product} {command line}
    ccstr AOTCache                                 =                                           {product} {default}
     bool UseJVMCICompiler                         = false                          {JVMCI experimental} {default}
"""
        flags = jdk_identity.parse_flags(sample)
        self.assertEqual(flags.get("EnableJVMCI"), "false")
        self.assertEqual(flags.get("UseG1GC"), "true")
        self.assertEqual(flags.get("AOTCache"), "")
        self.assertEqual(flags.get("UseJVMCICompiler"), "false")

    def test_parse_release_file_strips_quotes_and_excludes_modules(self):
        sample = """IMPLEMENTOR="Arch Linux"
JAVA_VERSION="25.0.4.1"
MODULES="java.base java.compiler jdk.graal.compiler"
OS_ARCH="x86_64"
# Comment line
"""
        release = jdk_identity.parse_release_file(sample)
        self.assertIsNotNone(release)
        self.assertEqual(release.get("IMPLEMENTOR"), "Arch Linux")
        self.assertEqual(release.get("JAVA_VERSION"), "25.0.4.1")
        self.assertEqual(release.get("OS_ARCH"), "x86_64")
        self.assertNotIn("MODULES", release)

    def test_parse_release_file_none_returns_none(self):
        self.assertIsNone(jdk_identity.parse_release_file(None))


class JdkIdentityEvaluationTests(unittest.TestCase):
    def load_fixture(self, name: str):
        props_text = (FIXTURES_DIR / f"{name}-properties.txt").read_text(encoding="utf-8")
        flags_text = (FIXTURES_DIR / f"{name}-flags.txt").read_text(encoding="utf-8")
        rel_file = FIXTURES_DIR / f"{name}-release.txt"
        rel_text = rel_file.read_text(encoding="utf-8") if rel_file.is_file() else None
        props = jdk_identity.parse_properties(props_text)
        flags = jdk_identity.parse_flags(flags_text)
        return props, flags, rel_text

    def test_arch_openjdk_25_accepted_for_25_rejected_for_21(self):
        props, flags, rel_text = self.load_fixture("arch-openjdk-25")
        accepted_25, reasons_25 = jdk_identity.evaluate(props, 25, flags)
        self.assertTrue(accepted_25, f"Expected accept for major 25, got reasons: {reasons_25}")
        self.assertEqual([], reasons_25)

        accepted_21, reasons_21 = jdk_identity.evaluate(props, 21, flags)
        self.assertFalse(accepted_21)
        self.assertTrue(any("java.specification.version" in r for r in reasons_21))

    def test_arch_openjdk_21_accepted_for_21(self):
        props, flags, rel_text = self.load_fixture("arch-openjdk-21")
        accepted_21, reasons_21 = jdk_identity.evaluate(props, 21, flags)
        self.assertTrue(accepted_21, f"Expected accept for major 21, got reasons: {reasons_21}")
        self.assertEqual([], reasons_21)

    def test_temurin_21_accepted_for_21(self):
        props, flags, rel_text = self.load_fixture("temurin-21")
        accepted_21, reasons_21 = jdk_identity.evaluate(props, 21, flags)
        self.assertTrue(accepted_21, f"Expected accept for major 21, got reasons: {reasons_21}")
        self.assertEqual([], reasons_21)

    def test_oracle_graalvm_25_rejected_for_25(self):
        props, flags, rel_text = self.load_fixture("oracle-graalvm-25")
        accepted, reasons = jdk_identity.evaluate(props, 25, flags)
        self.assertFalse(accepted)
        reasons_text = " ".join(reasons)
        self.assertIn("OpenJDK Runtime Environment", reasons_text)
        self.assertIn("Server VM", reasons_text)
        self.assertTrue(any("graal" in r.lower() or "jvmci" in r.lower() for r in reasons))
        self.assertTrue(any("UseJVMCICompiler" in r for r in reasons))

    def test_graalvm_ce_21_rejected_for_21(self):
        props, flags, rel_text = self.load_fixture("graalvm-ce-21")
        accepted, reasons = jdk_identity.evaluate(props, 21, flags)
        self.assertFalse(accepted)
        # GraalVM CE has java.runtime.name = OpenJDK Runtime Environment,
        # but vendor and version contain graal / jvmci
        reasons_text = " ".join(reasons)
        self.assertTrue("graal" in reasons_text.lower() or "jvmci" in reasons_text.lower())
        self.assertTrue(any("UseJVMCICompiler" in r for r in reasons))

    def test_synthetic_mandrel_25_rejected_for_25(self):
        props, flags, rel_text = self.load_fixture("synthetic-mandrel-25")
        accepted, reasons = jdk_identity.evaluate(props, 25, flags)
        self.assertFalse(accepted)
        self.assertTrue(any("mandrel" in r.lower() for r in reasons))

    def test_flags_only_use_jvmci_compiler_rejected(self):
        props, flags, _ = self.load_fixture("arch-openjdk-25")
        flags_modified = dict(flags)
        flags_modified["UseJVMCICompiler"] = "true"
        accepted, reasons = jdk_identity.evaluate(props, 25, flags_modified)
        self.assertFalse(accepted)
        self.assertTrue(any("UseJVMCICompiler" in r for r in reasons))

    def test_flags_only_enable_jvmci_rejected(self):
        props, flags, _ = self.load_fixture("arch-openjdk-25")
        flags_modified = dict(flags)
        flags_modified["EnableJVMCI"] = "true"
        accepted, reasons = jdk_identity.evaluate(props, 25, flags_modified)
        self.assertFalse(accepted)
        self.assertTrue(any("EnableJVMCI" in r for r in reasons))

    def test_missing_and_invalid_properties(self):
        # Empty properties
        accepted, reasons = jdk_identity.evaluate({}, 25)
        self.assertFalse(accepted)
        self.assertTrue(len(reasons) >= 3)

        # Non-Server VM
        props, flags, _ = self.load_fixture("arch-openjdk-25")
        client_props = dict(props)
        client_props["java.vm.name"] = "OpenJDK 64-Bit Client VM"
        accepted, reasons = jdk_identity.evaluate(client_props, 25, flags)
        self.assertFalse(accepted)
        self.assertTrue(any("Server VM" in r for r in reasons))


class JdkIdentityRecordTests(unittest.TestCase):
    def test_identity_record_structure_and_keys(self):
        props, flags, rel_text = JdkIdentityEvaluationTests().load_fixture("arch-openjdk-25")
        with tempfile.NamedTemporaryFile() as fake_java:
            fake_java.write(b"fake-binary")
            fake_java.flush()
            record = jdk_identity.identity_record(props, flags, fake_java.name, rel_text)

        expected_keys = {
            "javaExecutable",
            "javaHome",
            "javaVersion",
            "javaVersionDate",
            "specificationVersion",
            "vendor",
            "vendorVersion",
            "vendorUrl",
            "runtimeName",
            "runtimeVersion",
            "vmName",
            "vmVendor",
            "vmVersion",
            "useJvmciCompiler",
            "enableJvmci",
            "releaseFile",
            "javaExecutableSha256",
        }
        self.assertEqual(expected_keys, set(record.keys()))
        self.assertIsNone(record["vendorVersion"])
        self.assertFalse(record["useJvmciCompiler"])
        self.assertFalse(record["enableJvmci"])
        self.assertIsNotNone(record["javaExecutableSha256"])
        self.assertIsInstance(record["releaseFile"], dict)
        self.assertNotIn("MODULES", record["releaseFile"])


class JdkIdentityCliTests(unittest.TestCase):
    def test_cli_help(self):
        res = subprocess.run(
            ["python3", str(ROOT / "scripts" / "perf" / "jdk_identity.py"), "--help"],
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, res.returncode)
        self.assertIn("--java", res.stdout)
        self.assertIn("--major", res.stdout)
        self.assertIn("--profile", res.stdout)

    def test_cli_invalid_args_exits_2(self):
        res = subprocess.run(
            ["python3", str(ROOT / "scripts" / "perf" / "jdk_identity.py"), "--invalid-flag"],
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, res.returncode)

    def test_cli_nonexistent_java_exits_2(self):
        res = subprocess.run(
            [
                "python3",
                str(ROOT / "scripts" / "perf" / "jdk_identity.py"),
                "--java",
                "/nonexistent/path/java",
                "--major",
                "25",
                "--profile",
                "J25-G1",
            ],
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, res.returncode)

    def test_cli_real_jdks_acceptance_and_rejection(self):
        cases = [
            (LOCAL_JDK_ARCH_25, 25, "J25-G1", 0, "[ACCEPT]"),
            (LOCAL_JDK_GRAAL_25, 25, "J25-G1", 3, "[REJECT]"),
            (LOCAL_JDK_ARCH_21, 21, "J21-G1", 0, "[ACCEPT]"),
            (LOCAL_JDK_GRAAL_CE_21, 21, "J21-G1", 3, "[REJECT]"),
            (LOCAL_JDK_ARCH_25, 21, "J21-G1", 3, "[REJECT]"),
        ]
        for java_bin, major, profile, expected_code, expected_marker in cases:
            if not java_bin.is_file():
                continue
            with tempfile.NamedTemporaryFile() as json_out:
                res = subprocess.run(
                    [
                        "python3",
                        str(ROOT / "scripts" / "perf" / "jdk_identity.py"),
                        "--java",
                        str(java_bin),
                        "--major",
                        str(major),
                        "--profile",
                        profile,
                        "--json-out",
                        json_out.name,
                    ],
                    capture_output=True,
                    text=True,
                )
                self.assertEqual(
                    expected_code,
                    res.returncode,
                    f"Failed for {java_bin} major {major}: {res.stderr}",
                )
                self.assertIn(expected_marker, res.stderr)
                payload = json.loads(Path(json_out.name).read_text(encoding="utf-8"))
                self.assertEqual(profile, payload["profile"])
                self.assertEqual(major, payload["requiredMajor"])
                self.assertEqual(expected_code == 0, payload["accepted"])


class JdkIdentityRunnerDeterministicTests(unittest.TestCase):
    @staticmethod
    def _create_fake_jdk(parent_dir: Path, name: str, props_file: Path, flags_file: Path, banner: str) -> Path:
        jdk_dir = parent_dir / name
        bin_dir = jdk_dir / "bin"
        bin_dir.mkdir(parents=True)
        java_bin = bin_dir / "java"
        script = f"""#!/usr/bin/env bash
if [[ "$*" == *"-XshowSettings:properties"* ]]; then
  cat "{props_file}" >&2
  echo "{banner}" >&2
elif [[ "$*" == *"-XX:+PrintFlagsFinal"* ]]; then
  cat "{flags_file}"
  echo "{banner}" >&2
elif [[ "$*" == *"-version"* ]]; then
  echo "{banner}" >&2
fi
exit 0
"""
        java_bin.write_text(script, encoding="utf-8")
        java_bin.chmod(java_bin.stat().st_mode | stat.S_IEXEC)
        return jdk_dir

    def test_runner_rejects_graalvm_25_before_dirty_check_and_maven(self):
        with tempfile.TemporaryDirectory() as tmp_str:
            tmp = Path(tmp_str)
            fake_arch21 = self._create_fake_jdk(
                tmp,
                "arch21",
                FIXTURES_DIR / "arch-openjdk-21-properties.txt",
                FIXTURES_DIR / "arch-openjdk-21-flags.txt",
                'openjdk version "21.0.12.1" 2026-08-18\nOpenJDK Runtime Environment (build 21.0.12.1+1)\nOpenJDK 64-Bit Server VM (build 21.0.12.1+1, mixed mode, sharing)',
            )
            fake_graal25 = self._create_fake_jdk(
                tmp,
                "graal25",
                FIXTURES_DIR / "oracle-graalvm-25-properties.txt",
                FIXTURES_DIR / "oracle-graalvm-25-flags.txt",
                'java version "25.0.4" 2026-07-21 LTS\nJava(TM) SE Runtime Environment Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01)\nJava HotSpot(TM) 64-Bit Server VM Oracle GraalVM 25.0.4+7.1 (build 25.0.4+7-LTS-jvmci-b01, mixed mode, sharing)',
            )
            evidence_dir = tmp / "evidence"
            evidence_dir.mkdir()

            env = os.environ.copy()
            env["JAVA21_HOME"] = str(fake_arch21)
            env["JAVA25_HOME"] = str(fake_graal25)
            env["M18_EVIDENCE_DIR"] = str(evidence_dir)

            t0 = time.time()
            res = subprocess.run(
                ["bash", str(RUNNER_SCRIPT)],
                env=env,
                capture_output=True,
                text=True,
            )
            elapsed = time.time() - t0

            self.assertEqual(2, res.returncode)
            self.assertIn("J25-G1", res.stderr)
            self.assertIn("[REJECT]", res.stderr)
            self.assertIn("J25-G1 runtime identity validation failed", res.stderr)
            self.assertFalse(list(evidence_dir.glob("comparative-*.json")))
            self.assertLess(elapsed, 5.0)

    def test_runner_rejects_graalvm_ce_21_mirrored_before_dirty_check_and_maven(self):
        with tempfile.TemporaryDirectory() as tmp_str:
            tmp = Path(tmp_str)
            fake_graal21 = self._create_fake_jdk(
                tmp,
                "graal21",
                FIXTURES_DIR / "graalvm-ce-21-properties.txt",
                FIXTURES_DIR / "graalvm-ce-21-flags.txt",
                'openjdk version "21.0.2" 2024-01-16\nOpenJDK Runtime Environment GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)\nOpenJDK 64-Bit Server VM GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30, mixed mode, sharing)',
            )
            fake_arch25 = self._create_fake_jdk(
                tmp,
                "arch25",
                FIXTURES_DIR / "arch-openjdk-25-properties.txt",
                FIXTURES_DIR / "arch-openjdk-25-flags.txt",
                'openjdk version "25.0.4.1" 2026-08-18\nOpenJDK Runtime Environment (build 25.0.4.1)\nOpenJDK 64-Bit Server VM (build 25.0.4.1, mixed mode, sharing)',
            )
            evidence_dir = tmp / "evidence"
            evidence_dir.mkdir()

            env = os.environ.copy()
            env["JAVA21_HOME"] = str(fake_graal21)
            env["JAVA25_HOME"] = str(fake_arch25)
            env["M18_EVIDENCE_DIR"] = str(evidence_dir)

            t0 = time.time()
            res = subprocess.run(
                ["bash", str(RUNNER_SCRIPT)],
                env=env,
                capture_output=True,
                text=True,
            )
            elapsed = time.time() - t0

            self.assertEqual(2, res.returncode)
            self.assertIn("J21-G1", res.stderr)
            self.assertIn("[REJECT]", res.stderr)
            self.assertIn("J21-G1 runtime identity validation failed", res.stderr)
            self.assertFalse(list(evidence_dir.glob("comparative-*.json")))
            self.assertLess(elapsed, 5.0)


if __name__ == "__main__":
    unittest.main()
