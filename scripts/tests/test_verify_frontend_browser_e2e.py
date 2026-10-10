"""
scripts/tests/test_verify_frontend_browser_e2e.py

Unit tests for scripts/verify-frontend-browser-e2e.py enforcing orchestration invariants:
- Prerequisites validation (Node, npm, Java, Maven wrapper, Playwright)
- Port allocation
- Frontend build steps and failure handling
- Spring Boot build steps and failure handling
- Process-group management and robust cleanup (SIGTERM -> SIGKILL)
- Server readiness polling (success, early crash, timeout)
- Playwright execution and results parsing
- Report generation schema
- Full CLI orchestration flows (success and failure modes)
"""

import importlib.util
import io
import json
import signal
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import MagicMock, call, mock_open, patch

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_browser_e2e = load_script("verify-frontend-browser-e2e.py")
find_free_port = verify_browser_e2e.find_free_port
check_tool_version = verify_browser_e2e.check_tool_version
check_prerequisites = verify_browser_e2e.check_prerequisites
build_frontend = verify_browser_e2e.build_frontend
build_spring_app = verify_browser_e2e.build_spring_app
build_quarkus_app = verify_browser_e2e.build_quarkus_app
start_server = verify_browser_e2e.start_server
start_spring_server = verify_browser_e2e.start_spring_server
start_quarkus_server = verify_browser_e2e.start_quarkus_server
poll_server_readiness = verify_browser_e2e.poll_server_readiness
terminate_process_group = verify_browser_e2e.terminate_process_group
run_playwright_suite = verify_browser_e2e.run_playwright_suite
extract_playwright_test_records = verify_browser_e2e.extract_playwright_test_records
extract_browser_version = verify_browser_e2e.extract_browser_version
compute_scenarios = verify_browser_e2e.compute_scenarios
generate_report = verify_browser_e2e.generate_report
print_parity_matrix = verify_browser_e2e.print_parity_matrix
main = verify_browser_e2e.main


class VerifyFrontendBrowserE2ETests(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.test_root = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    # -------------------------------------------------------------------------
    # 1. Port Allocation
    # -------------------------------------------------------------------------

    def test_find_free_port_allocates_valid_port(self):
        port = find_free_port()
        self.assertIsInstance(port, int)
        self.assertGreater(port, 1024)
        self.assertLess(port, 65536)

    # -------------------------------------------------------------------------
    # 2. Tool & Prerequisites Validation
    # -------------------------------------------------------------------------

    @patch("shutil.which", return_value=None)
    def test_check_tool_version_missing_tool(self, mock_which):
        with self.assertRaises(RuntimeError) as ctx:
            check_tool_version("nonexistent-tool")
        self.assertIn("was not found on system PATH", str(ctx.exception))

    @patch("shutil.which", return_value="/bin/tool")
    @patch("subprocess.run")
    def test_check_tool_version_success_stdout(self, mock_run, mock_which):
        mock_run.return_value = MagicMock(returncode=0, stdout="Tool v1.2.3\nExtra", stderr="")
        res = check_tool_version("tool")
        self.assertEqual("Tool v1.2.3", res)

    @patch("shutil.which", return_value="/bin/tool")
    @patch("subprocess.run")
    def test_check_tool_version_success_stderr(self, mock_run, mock_which):
        # Like java -version which writes to stderr
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="openjdk version 21.0.1\n")
        res = check_tool_version("tool")
        self.assertEqual("openjdk version 21.0.1", res)

    @patch("shutil.which", return_value="/bin/tool")
    @patch("subprocess.run")
    def test_check_tool_version_failure(self, mock_run, mock_which):
        mock_run.return_value = MagicMock(returncode=1, stdout="", stderr="")
        with self.assertRaises(RuntimeError) as ctx:
            check_tool_version("tool")
        self.assertIn("Failed executing", str(ctx.exception))

    def test_check_prerequisites_missing_mvnw(self):
        fake_browser_dir = self.test_root / "browser"
        fake_browser_dir.mkdir(parents=True)
        (fake_browser_dir / "package.json").write_text('{"devDependencies":{"@playwright/test":"1.0.0"}}', encoding="utf-8")

        with patch.object(verify_browser_e2e, "check_tool_version", return_value="v1.0"):
            with self.assertRaises(RuntimeError) as ctx:
                check_prerequisites(self.test_root, fake_browser_dir)
            self.assertIn("Maven wrapper missing", str(ctx.exception))

    def test_check_prerequisites_missing_browser_package_json(self):
        fake_mvnw = self.test_root / "mvnw"
        fake_mvnw.write_text("#!/bin/sh\n", encoding="utf-8")
        fake_browser_dir = self.test_root / "browser"
        fake_browser_dir.mkdir(parents=True)

        with patch.object(verify_browser_e2e, "check_tool_version", return_value="v1.0"):
            with patch("subprocess.run", return_value=MagicMock(returncode=0, stdout="Apache Maven 3.9.9\n")):
                with self.assertRaises(RuntimeError) as ctx:
                    check_prerequisites(self.test_root, fake_browser_dir)
                self.assertIn("package.json missing", str(ctx.exception))

    def test_check_prerequisites_success(self):
        fake_mvnw = self.test_root / "mvnw"
        fake_mvnw.write_text("#!/bin/sh\n", encoding="utf-8")
        fake_browser_dir = self.test_root / "browser"
        fake_browser_dir.mkdir(parents=True)
        (fake_browser_dir / "package.json").write_text('{"devDependencies":{"@playwright/test":"1.64.0"}}', encoding="utf-8")

        with patch.object(verify_browser_e2e, "check_tool_version", side_effect=["v22.0.0", "10.0.0", "Java 21"]):
            with patch("subprocess.run", return_value=MagicMock(returncode=0, stdout="Apache Maven 3.9.9\n")):
                tools = check_prerequisites(self.test_root, fake_browser_dir)
                self.assertEqual("v22.0.0", tools["node"])
                self.assertEqual("10.0.0", tools["npm"])
                self.assertEqual("Java 21", tools["java"])
                self.assertEqual("Apache Maven 3.9.9", tools["mvnw"])
                self.assertEqual("1.64.0", tools["playwright"])

    # -------------------------------------------------------------------------
    # 3. Frontend Build Steps
    # -------------------------------------------------------------------------

    def test_build_frontend_missing_package_json(self):
        with self.assertRaises(FileNotFoundError):
            build_frontend(self.test_root)

    @patch("subprocess.run")
    def test_build_frontend_npm_ci_failure(self, mock_run):
        (self.test_root / "package.json").write_text("{}", encoding="utf-8")
        mock_run.return_value = MagicMock(returncode=1, stderr="npm ci network error")

        with self.assertRaises(RuntimeError) as ctx:
            build_frontend(self.test_root, skip_install=False)
        self.assertIn("'npm ci' failed", str(ctx.exception))

    @patch("subprocess.run")
    def test_build_frontend_build_failure(self, mock_run):
        (self.test_root / "package.json").write_text("{}", encoding="utf-8")
        # First call is npm ci (success), second call is npm run build (failure)
        mock_run.side_effect = [
            MagicMock(returncode=0),
            MagicMock(returncode=1, stderr="Vite compile error"),
        ]

        with self.assertRaises(RuntimeError) as ctx:
            build_frontend(self.test_root, skip_install=False)
        self.assertIn("'npm run build' failed", str(ctx.exception))

    @patch("subprocess.run")
    def test_build_frontend_manifest_missing_after_build(self, mock_run):
        (self.test_root / "package.json").write_text("{}", encoding="utf-8")
        mock_run.side_effect = [MagicMock(returncode=0), MagicMock(returncode=0)]

        with self.assertRaises(FileNotFoundError) as ctx:
            build_frontend(self.test_root, skip_install=False)
        self.assertIn("Expected Vite manifest not found", str(ctx.exception))

    @patch("subprocess.run")
    def test_build_frontend_success(self, mock_run):
        (self.test_root / "package.json").write_text("{}", encoding="utf-8")
        dist_vite = self.test_root / "dist" / ".vite"
        dist_vite.mkdir(parents=True)
        (dist_vite / "manifest.json").write_text("{}", encoding="utf-8")
        mock_run.return_value = MagicMock(returncode=0)

        # Should complete without error when skip_build=True
        build_frontend(self.test_root, skip_install=True, skip_build=True)

    # -------------------------------------------------------------------------
    # 4. Spring Boot Build Steps
    # -------------------------------------------------------------------------

    def test_build_spring_app_missing_pom(self):
        with self.assertRaises(FileNotFoundError):
            build_spring_app(self.test_root, self.test_root / "spring")

    @patch("subprocess.run")
    def test_build_spring_app_mvn_failure(self, mock_run):
        spring_dir = self.test_root / "spring"
        spring_dir.mkdir(parents=True)
        (spring_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        (self.test_root / "mvnw").write_text("#!/bin/sh\n", encoding="utf-8")

        mock_run.return_value = MagicMock(returncode=1, stdout="BUILD FAILURE", stderr="")

        with self.assertRaises(RuntimeError) as ctx:
            build_spring_app(self.test_root, spring_dir, skip_build=False)
        self.assertIn("Maven packaging failed", str(ctx.exception))

    def test_build_spring_app_missing_jar_artifact(self):
        spring_dir = self.test_root / "spring"
        spring_dir.mkdir(parents=True)
        (spring_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        target_dir = spring_dir / "target"
        target_dir.mkdir(parents=True)

        with self.assertRaises(FileNotFoundError) as ctx:
            build_spring_app(self.test_root, spring_dir, skip_build=True)
        self.assertIn("No packaged jar artifact found", str(ctx.exception))

    def test_build_spring_app_filters_original_and_sources_jar(self):
        spring_dir = self.test_root / "spring"
        spring_dir.mkdir(parents=True)
        (spring_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        target_dir = spring_dir / "target"
        target_dir.mkdir(parents=True)

        (target_dir / "app-1.0.jar.original").write_text("orig", encoding="utf-8")
        (target_dir / "app-1.0-sources.jar").write_text("sources", encoding="utf-8")
        real_jar = target_dir / "app-1.0.jar"
        real_jar.write_text("jar", encoding="utf-8")

        jar = build_spring_app(self.test_root, spring_dir, skip_build=True)
        self.assertEqual(real_jar, jar)

    # -------------------------------------------------------------------------
    # 4b. Quarkus Build & Start Steps
    # -------------------------------------------------------------------------

    def test_build_quarkus_app_missing_pom(self):
        with self.assertRaises(FileNotFoundError):
            build_quarkus_app(self.test_root, self.test_root / "quarkus")

    @patch("subprocess.run")
    def test_build_quarkus_app_mvn_failure(self, mock_run):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        (self.test_root / "mvnw").write_text("#!/bin/sh\n", encoding="utf-8")

        mock_run.return_value = MagicMock(returncode=1, stdout="BUILD FAILURE", stderr="")

        with self.assertRaises(RuntimeError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=False)
        self.assertIn("Maven Quarkus packaging failed", str(ctx.exception))

    def test_build_quarkus_app_missing_target_dir(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")

        with self.assertRaises(FileNotFoundError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertIn("Target directory missing", str(ctx.exception))

    def test_build_quarkus_app_missing_runner_jar(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        target_dir = quarkus_dir / "target"
        target_dir.mkdir(parents=True)

        with self.assertRaises(FileNotFoundError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertIn("Expected Quarkus runner JAR not found", str(ctx.exception))

    def test_build_quarkus_app_missing_app_dir(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        runner_jar = quarkus_dir / "target" / "quarkus-app" / "quarkus-run.jar"
        runner_jar.parent.mkdir(parents=True)
        runner_jar.write_text("fast-jar", encoding="utf-8")

        with self.assertRaises(FileNotFoundError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertIn("Expected Quarkus fast-jar app directory not found", str(ctx.exception))

    def test_build_quarkus_app_missing_lib_dir(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        runner_jar = quarkus_dir / "target" / "quarkus-app" / "quarkus-run.jar"
        runner_jar.parent.mkdir(parents=True)
        runner_jar.write_text("fast-jar", encoding="utf-8")
        (runner_jar.parent / "app").mkdir(parents=True)

        with self.assertRaises(FileNotFoundError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertIn("Expected Quarkus fast-jar lib directory not found", str(ctx.exception))

    def test_build_quarkus_app_manifest_verification_failure(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        runner_jar = quarkus_dir / "target" / "quarkus-app" / "quarkus-run.jar"
        runner_jar.parent.mkdir(parents=True)
        runner_jar.write_text("fast-jar", encoding="utf-8")
        app_dir = runner_jar.parent / "app"
        app_dir.mkdir(parents=True)
        (runner_jar.parent / "lib").mkdir(parents=True)

        # Create real zipfile in app_dir without manifest
        app_jar = app_dir / "quarkus-app-1.0.jar"
        with zipfile.ZipFile(app_jar, "w") as zf:
            zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")

        with self.assertRaises(FileNotFoundError) as ctx:
            build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertIn("Expected packaged Vite manifest not found in Quarkus application artifact", str(ctx.exception))

    def test_build_quarkus_app_success(self):
        quarkus_dir = self.test_root / "quarkus"
        quarkus_dir.mkdir(parents=True)
        (quarkus_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        runner_jar = quarkus_dir / "target" / "quarkus-app" / "quarkus-run.jar"
        runner_jar.parent.mkdir(parents=True)
        runner_jar.write_text("fast-jar", encoding="utf-8")
        app_dir = runner_jar.parent / "app"
        app_dir.mkdir(parents=True)
        (runner_jar.parent / "lib").mkdir(parents=True)

        app_jar = app_dir / "quarkus-app-1.0.jar"
        with zipfile.ZipFile(app_jar, "w") as zf:
            zf.writestr("META-INF/resources/.vite/manifest.json", "{}")

        jar = build_quarkus_app(self.test_root, quarkus_dir, skip_build=True)
        self.assertEqual(runner_jar, jar)

    def test_build_spring_app_manifest_verification_failure(self):
        spring_dir = self.test_root / "spring"
        spring_dir.mkdir(parents=True)
        (spring_dir / "pom.xml").write_text("<project/>", encoding="utf-8")
        target_dir = spring_dir / "target"
        target_dir.mkdir(parents=True)
        app_jar = target_dir / "app-1.0.jar"
        with zipfile.ZipFile(app_jar, "w") as zf:
            zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")

        with self.assertRaises(FileNotFoundError) as ctx:
            build_spring_app(self.test_root, spring_dir, skip_build=True)
        self.assertIn("Expected packaged Vite manifest not found in Spring Boot artifact", str(ctx.exception))

    @patch("subprocess.Popen")
    def test_start_quarkus_server_command(self, mock_popen):
        fake_jar = self.test_root / "quarkus-run.jar"
        fake_jar.write_text("jar", encoding="utf-8")
        log_path = self.test_root / "target" / "server.log"

        start_quarkus_server(fake_jar, 8081, log_path)

        mock_popen.assert_called_once()
        cmd = mock_popen.call_args[0][0]
        self.assertEqual("java", cmd[0])
        self.assertEqual("-Dquarkus.http.port=8081", cmd[1])
        self.assertEqual("-jar", cmd[2])
        self.assertEqual(str(fake_jar.resolve()), cmd[3])

    # -------------------------------------------------------------------------
    # 5. Server Startup & Readiness Polling
    # -------------------------------------------------------------------------

    @patch("urllib.request.urlopen")
    def test_poll_server_readiness_success(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 200
        mock_resp.read.return_value = b'{"status":"UP"}'
        mock_urlopen.return_value.__enter__.return_value = mock_resp

        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        ready = poll_server_readiness(8080, mock_proc, timeout_seconds=5.0)
        self.assertTrue(ready)

    def test_poll_server_readiness_early_exit(self):
        mock_proc = MagicMock()
        mock_proc.poll.return_value = 1  # Exited with code 1
        mock_proc.returncode = 1

        with self.assertRaises(RuntimeError) as ctx:
            poll_server_readiness(8080, mock_proc, timeout_seconds=5.0)
        self.assertIn("terminated unexpectedly with code 1", str(ctx.exception))

    @patch("urllib.request.urlopen", side_effect=Exception("Connection refused"))
    def test_poll_server_readiness_timeout(self, mock_urlopen):
        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        with self.assertRaises(TimeoutError) as ctx:
            poll_server_readiness(8080, mock_proc, timeout_seconds=0.3, poll_interval=0.1)
        self.assertIn("failed to become ready", str(ctx.exception))

    # -------------------------------------------------------------------------
    # 6. Process Cleanup
    # -------------------------------------------------------------------------

    @patch("os.getpgid", return_value=12345)
    @patch("os.killpg")
    def test_terminate_process_group_graceful(self, mock_killpg, mock_getpgid):
        mock_proc = MagicMock(pid=12345)
        mock_proc.poll.return_value = None
        mock_proc.wait.return_value = 0

        terminate_process_group(mock_proc, timeout=1.0)
        mock_killpg.assert_called_once_with(12345, signal.SIGTERM)

    @patch("os.getpgid", return_value=12345)
    @patch("os.killpg")
    def test_terminate_process_group_force_kill_on_timeout(self, mock_killpg, mock_getpgid):
        mock_proc = MagicMock(pid=12345)
        mock_proc.poll.return_value = None
        mock_proc.wait.side_effect = [subprocess.TimeoutExpired(cmd="java", timeout=1.0), 0]

        terminate_process_group(mock_proc, timeout=1.0)
        self.assertEqual(2, mock_killpg.call_count)
        mock_killpg.assert_has_calls([
            call(12345, signal.SIGTERM),
            call(12345, signal.SIGKILL),
        ])

    def test_terminate_process_group_already_stopped(self):
        mock_proc = MagicMock(pid=12345)
        mock_proc.poll.return_value = 0
        with patch("os.killpg") as mock_killpg:
            terminate_process_group(mock_proc)
            mock_killpg.assert_not_called()

    # -------------------------------------------------------------------------
    # 7. Playwright Test Suite & Results Extraction
    # -------------------------------------------------------------------------

    def test_extract_playwright_test_records_none_or_empty(self):
        tests, stats = extract_playwright_test_records(None)
        self.assertEqual([], tests)
        self.assertEqual(0, stats["total"])

        tests, stats = extract_playwright_test_records({})
        self.assertEqual([], tests)
        self.assertEqual(0, stats["total"])

    def test_extract_playwright_test_records_nested_suites(self):
        sample_results = {
            "stats": {
                "expected": 2,
                "unexpected": 1,
                "skipped": 0,
                "flaky": 0,
            },
            "suites": [
                {
                    "title": "root",
                    "suites": [
                        {
                            "title": "employee-page.spec.ts",
                            "specs": [
                                {
                                    "title": "mounts island",
                                    "ok": True,
                                    "tests": [
                                        {"results": [{"duration": 150}]}
                                    ]
                                },
                                {
                                    "title": "handles failure",
                                    "ok": False,
                                    "tests": [
                                        {"results": [{"duration": 200}]}
                                    ]
                                }
                            ]
                        }
                    ]
                }
            ]
        }
        tests, stats = extract_playwright_test_records(sample_results)
        self.assertEqual(3, stats["total"])
        self.assertEqual(2, stats["passed"])
        self.assertEqual(1, stats["failed"])
        self.assertEqual(2, len(tests))
        self.assertEqual("mounts island", tests[0]["title"])
        self.assertEqual("passed", tests[0]["status"])
        self.assertEqual(150, tests[0]["durationMs"])
        self.assertEqual("handles failure", tests[1]["title"])
        self.assertEqual("failed", tests[1]["status"])
        self.assertEqual(200, tests[1]["durationMs"])

    @patch("subprocess.run")
    def test_run_playwright_suite_installs_deps_if_missing(self, mock_run):
        browser_dir = self.test_root / "browser"
        browser_dir.mkdir(parents=True)
        # node_modules not present, should trigger npm ci
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")

        code, out, err, results = run_playwright_suite(browser_dir, "http://localhost:8080")
        self.assertEqual(0, code)
        self.assertEqual(2, mock_run.call_count)
        self.assertEqual(["npm", "ci"], mock_run.call_args_list[0][0][0])
        self.assertEqual(["npx", "playwright", "test"], mock_run.call_args_list[1][0][0])

    # -------------------------------------------------------------------------
    # 8. Report Generation
    # -------------------------------------------------------------------------

    def test_generate_report_pass(self):
        report_file = self.test_root / "report.json"
        toolchain = {
            "node": "v22.23.3",
            "npm": "10.9.9",
            "vite": "8.3.4",
            "svelte": "5.57.2",
            "springBoot": "4.1.1",
        }
        tests = [
            {"title": "renders SSR content, mounts Svelte island, and updates from Java REST", "status": "passed", "durationMs": 100},
            {"title": "preserves useful server-rendered page when JavaScript is disabled", "status": "passed", "durationMs": 50},
            {"title": "keeps hostile client data inert while preserving its value", "status": "passed", "durationMs": 30},
        ]
        stats = {"total": 3, "passed": 3, "failed": 0, "skipped": 0, "flaky": 0}

        report = generate_report(
            report_file,
            status="PASS",
            toolchain=toolchain,
            port=8080,
            duration_seconds=5.25,
            tests=tests,
            stats=stats,
            errors=[],
            browser="chromium",
            browser_version="156.0.8078.4",
            base_url="http://127.0.0.1:8080",
        )

        self.assertEqual("PASS", report["status"])
        self.assertEqual("chromium", report["browser"])
        self.assertEqual("156.0.8078.4", report["browserVersion"])
        self.assertEqual("v22.23.3", report["nodeVersion"])
        self.assertEqual("8.3.4", report["viteVersion"])
        self.assertEqual("5.57.2", report["svelteVersion"])
        self.assertEqual("4.1.1", report["springBootVersion"])
        self.assertEqual("http://127.0.0.1:8080", report["baseUrl"])
        self.assertEqual("Spring Boot 4.1.1", report["framework"])
        self.assertEqual("Vite 8.3.4 + Svelte 5.57.2", report["frontend"])
        self.assertIn("scenarios", report)
        self.assertEqual("PASS", report["scenarios"]["ssr"])
        self.assertEqual("PASS", report["scenarios"]["assetLoading"])
        self.assertEqual("PASS", report["scenarios"]["clientData"])
        self.assertEqual("PASS", report["scenarios"]["islandMount"])
        self.assertEqual("PASS", report["scenarios"]["restInteraction"])
        self.assertEqual("PASS", report["scenarios"]["domUpdate"])
        self.assertEqual("PASS", report["scenarios"]["noJsFallback"])
        self.assertEqual("PASS", report["scenarios"]["scriptBreakoutProtection"])
        self.assertEqual(8080, report["port"])
        self.assertEqual(5.25, report["durationSeconds"])
        self.assertEqual([], report["errors"])
        self.assertTrue(report_file.is_file())

        with open(report_file, "r", encoding="utf-8") as f:
            loaded = json.load(f)
        self.assertEqual("PASS", loaded["status"])
        self.assertEqual("chromium", loaded["browser"])
        self.assertEqual("PASS", loaded["scenarios"]["ssr"])

    def test_compute_scenarios_failure_mapping(self):
        tests = [
            {"title": "mounts Svelte island", "status": "failed"},
            {"title": "JavaScript is disabled", "status": "passed"},
            {"title": "hostile client data", "status": "passed"},
        ]
        scenarios = compute_scenarios(tests, False)
        self.assertEqual("FAIL", scenarios["islandMount"])
        self.assertEqual("FAIL", scenarios["ssr"])
        self.assertEqual("PASS", scenarios["noJsFallback"])
        self.assertEqual("PASS", scenarios["scriptBreakoutProtection"])

    def test_generate_report_fail(self):
        report_file = self.test_root / "report.json"
        report = generate_report(
            report_file,
            status="FAIL",
            toolchain={},
            port=0,
            duration_seconds=1.0,
            tests=[],
            stats={"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0},
            errors=["Network timeout"],
        )
        self.assertEqual("FAIL", report["status"])
        self.assertEqual(["Network timeout"], report["errors"])
        self.assertEqual("FAIL", report["scenarios"]["ssr"])

    # -------------------------------------------------------------------------
    # 9. Main Orchestration Flow
    # -------------------------------------------------------------------------

    def test_main_success_flow(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_spring_app") as mock_build_spring, \
             patch.object(verify_browser_e2e, "start_server") as mock_start, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22", "playwright": "1.64.0"}
            mock_build_spring.return_value = Path("/tmp/app.jar")
            fake_proc = MagicMock()
            mock_start.return_value = fake_proc
            mock_poll.return_value = True
            mock_run_pw.return_value = (0, "All passed", "", {
                "stats": {"expected": 1, "unexpected": 0, "skipped": 0, "flaky": 0},
                "suites": [{"specs": [{"title": "island test", "ok": True, "tests": []}]}],
            })

            report_path = self.test_root / "report.json"
            exit_code = main([
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
                "--port", "8099",
            ])

            self.assertEqual(0, exit_code)
            mock_terminate.assert_called_once_with(fake_proc)
            self.assertTrue(report_path.is_file())
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("PASS", data["status"])

    def test_main_playwright_failure_flow(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_spring_app") as mock_build_spring, \
             patch.object(verify_browser_e2e, "start_server") as mock_start, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22"}
            mock_build_spring.return_value = Path("/tmp/app.jar")
            fake_proc = MagicMock()
            mock_start.return_value = fake_proc
            mock_poll.return_value = True
            mock_run_pw.return_value = (1, "Test failed", "Assertion error", None)

            report_path = self.test_root / "report.json"
            exit_code = main([
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
                "--port", "8099",
            ])

            self.assertEqual(1, exit_code)
            mock_terminate.assert_called_once_with(fake_proc)
            self.assertTrue(report_path.is_file())
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("FAIL", data["status"])
            self.assertTrue(any("failed with exit code 1" in e for e in data["errors"]))

    def test_main_readiness_failure_dumps_server_log(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_spring_app") as mock_build_spring, \
             patch.object(verify_browser_e2e, "start_server") as mock_start, \
             patch.object(verify_browser_e2e, "poll_server_readiness", side_effect=TimeoutError("Timed out")), \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22"}
            mock_build_spring.return_value = Path("/tmp/app.jar")
            fake_proc = MagicMock()
            mock_start.return_value = fake_proc

            spring_dir = self.test_root / "spring"
            spring_dir.mkdir(parents=True)
            log_file = spring_dir / "target" / "server.log"
            log_file.parent.mkdir(parents=True)
            log_file.write_text("Exception in thread main: BeanCreationException\n", encoding="utf-8")

            report_path = self.test_root / "report.json"
            stderr_capture = io.StringIO()
            with patch("sys.stderr", stderr_capture):
                exit_code = main([
                    "--report-path", str(report_path),
                    "--spring-dir", str(spring_dir),
                    "--skip-install",
                    "--skip-build",
                    "--port", "8099",
                ])

            self.assertEqual(1, exit_code)
            self.assertIn("Captured Server Logs (server.log)", stderr_capture.getvalue())
            self.assertIn("BeanCreationException", stderr_capture.getvalue())

    def test_print_parity_matrix(self):
        results = [
            {
                "framework": "spring",
                "displayName": "Spring Boot",
                "status": "PASS",
                "scenarios": {k: "PASS" for k in [
                    "ssr", "assetLoading", "clientData", "islandMount",
                    "restInteraction", "domUpdate", "noJsFallback",
                    "scriptBreakoutProtection", "consoleHealth", "networkHealth"
                ]}
            },
            {
                "framework": "quarkus",
                "displayName": "Quarkus",
                "status": "PASS",
                "scenarios": {k: "PASS" for k in [
                    "ssr", "assetLoading", "clientData", "islandMount",
                    "restInteraction", "domUpdate", "noJsFallback",
                    "scriptBreakoutProtection", "consoleHealth", "networkHealth"
                ]}
            }
        ]
        capture = io.StringIO()
        with patch("sys.stdout", capture):
            print_parity_matrix(results)
        output = capture.getvalue()
        self.assertIn("Scenario Parity Qualification Matrix", output)
        self.assertIn("Spring", output)
        self.assertIn("Quarkus", output)
        self.assertIn("SSR", output)
        self.assertIn("Island mount", output)

    def test_main_unknown_framework_choice(self):
        with self.assertRaises(SystemExit) as ctx:
            with patch("sys.stderr", io.StringIO()):
                main(["--framework", "invalid-fw"])
        self.assertNotEqual(0, ctx.exception.code)

    def test_main_quarkus_success_flow(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_quarkus_app") as mock_build_quarkus, \
             patch.object(verify_browser_e2e, "start_quarkus_server") as mock_start_quarkus, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22", "quarkus": "3.39.4"}
            mock_build_quarkus.return_value = Path("/tmp/quarkus-run.jar")
            fake_proc = MagicMock()
            mock_start_quarkus.return_value = fake_proc
            mock_poll.return_value = True
            mock_run_pw.return_value = (0, "All passed", "", {
                "stats": {"expected": 1, "unexpected": 0, "skipped": 0, "flaky": 0},
                "suites": [{"specs": [{"title": "island test", "ok": True, "tests": []}]}],
            })

            report_path = self.test_root / "report.json"
            exit_code = main([
                "--framework", "quarkus",
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
                "--port", "8099",
            ])

            self.assertEqual(0, exit_code)
            mock_terminate.assert_called_once_with(fake_proc)
            self.assertTrue(report_path.is_file())
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("PASS", data["status"])
            self.assertEqual("quarkus", data["frameworks"][0]["framework"])

    def test_main_quarkus_playwright_failure(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_quarkus_app") as mock_build_quarkus, \
             patch.object(verify_browser_e2e, "start_quarkus_server") as mock_start_quarkus, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22"}
            mock_build_quarkus.return_value = Path("/tmp/quarkus-run.jar")
            fake_proc = MagicMock()
            mock_start_quarkus.return_value = fake_proc
            mock_poll.return_value = True
            mock_run_pw.return_value = (1, "Playwright failure", "AssertionError", None)

            report_path = self.test_root / "report.json"
            exit_code = main([
                "quarkus",
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
                "--port", "8099",
            ])

            self.assertEqual(1, exit_code)
            mock_terminate.assert_called_once_with(fake_proc)
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("FAIL", data["status"])
            self.assertTrue(any("Playwright browser tests failed" in e for e in data["errors"]))

    def test_main_quarkus_readiness_failure_dumps_server_log(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_quarkus_app") as mock_build_quarkus, \
             patch.object(verify_browser_e2e, "start_quarkus_server") as mock_start, \
             patch.object(verify_browser_e2e, "poll_server_readiness", side_effect=TimeoutError("Quarkus timed out")), \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22"}
            mock_build_quarkus.return_value = Path("/tmp/quarkus-run.jar")
            fake_proc = MagicMock()
            mock_start.return_value = fake_proc

            quarkus_dir = self.test_root / "quarkus"
            quarkus_dir.mkdir(parents=True)
            log_file = quarkus_dir / "target" / "server.log"
            log_file.parent.mkdir(parents=True)
            log_file.write_text("Quarkus initialization failed: Port in use\n", encoding="utf-8")

            report_path = self.test_root / "report.json"
            stderr_capture = io.StringIO()
            with patch("sys.stderr", stderr_capture):
                exit_code = main([
                    "quarkus",
                    "--report-path", str(report_path),
                    "--quarkus-dir", str(quarkus_dir),
                    "--skip-install",
                    "--skip-build",
                    "--port", "8099",
                ])

            self.assertEqual(1, exit_code)
            self.assertIn("Captured Server Logs (server.log)", stderr_capture.getvalue())
            self.assertIn("Quarkus initialization failed", stderr_capture.getvalue())

    def test_main_all_success_flow(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_spring_app") as mock_build_spring, \
             patch.object(verify_browser_e2e, "build_quarkus_app") as mock_build_quarkus, \
             patch.object(verify_browser_e2e, "start_spring_server") as mock_start_spring, \
             patch.object(verify_browser_e2e, "start_quarkus_server") as mock_start_quarkus, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22", "springBoot": "4.1.1", "quarkus": "3.39.4"}
            mock_build_spring.return_value = Path("/tmp/spring.jar")
            mock_build_quarkus.return_value = Path("/tmp/quarkus-run.jar")
            fake_spring_proc = MagicMock()
            fake_quarkus_proc = MagicMock()
            mock_start_spring.return_value = fake_spring_proc
            mock_start_quarkus.return_value = fake_quarkus_proc
            mock_poll.return_value = True
            mock_run_pw.return_value = (0, "All passed", "", {
                "stats": {"expected": 1, "unexpected": 0, "skipped": 0, "flaky": 0},
                "suites": [{"specs": [{"title": "island test", "ok": True, "tests": []}]}],
            })

            report_path = self.test_root / "report.json"
            exit_code = main([
                "all",
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
            ])

            self.assertEqual(0, exit_code)
            self.assertEqual(2, mock_terminate.call_count)
            mock_terminate.assert_has_calls([call(fake_spring_proc), call(fake_quarkus_proc)])
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("PASS", data["status"])
            self.assertEqual(2, len(data["frameworks"]))
            self.assertEqual("spring", data["frameworks"][0]["framework"])
            self.assertEqual("quarkus", data["frameworks"][1]["framework"])

    def test_main_all_partial_failure_flow(self):
        with patch.object(verify_browser_e2e, "check_prerequisites") as mock_check_prereqs, \
             patch.object(verify_browser_e2e, "build_frontend") as mock_build_fe, \
             patch.object(verify_browser_e2e, "build_spring_app") as mock_build_spring, \
             patch.object(verify_browser_e2e, "build_quarkus_app") as mock_build_quarkus, \
             patch.object(verify_browser_e2e, "start_spring_server") as mock_start_spring, \
             patch.object(verify_browser_e2e, "start_quarkus_server") as mock_start_quarkus, \
             patch.object(verify_browser_e2e, "poll_server_readiness") as mock_poll, \
             patch.object(verify_browser_e2e, "run_playwright_suite") as mock_run_pw, \
             patch.object(verify_browser_e2e, "terminate_process_group") as mock_terminate:

            mock_check_prereqs.return_value = {"node": "22"}
            mock_build_spring.return_value = Path("/tmp/spring.jar")
            mock_build_quarkus.return_value = Path("/tmp/quarkus-run.jar")
            fake_spring_proc = MagicMock()
            fake_quarkus_proc = MagicMock()
            mock_start_spring.return_value = fake_spring_proc
            mock_start_quarkus.return_value = fake_quarkus_proc
            mock_poll.return_value = True
            # Spring passes (code 0), Quarkus fails (code 1)
            mock_run_pw.side_effect = [
                (0, "Passed", "", {"stats": {"expected": 1}, "suites": []}),
                (1, "Failed", "Error", None),
            ]

            report_path = self.test_root / "report.json"
            exit_code = main([
                "--framework", "all",
                "--report-path", str(report_path),
                "--skip-install",
                "--skip-build",
            ])

            self.assertEqual(1, exit_code)
            with open(report_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual("FAIL", data["status"])
            self.assertEqual("PASS", data["frameworks"][0]["status"])
            self.assertEqual("FAIL", data["frameworks"][1]["status"])


if __name__ == "__main__":
    unittest.main()
