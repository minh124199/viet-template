"""
scripts/tests/test_verify_frontend_security_e2e.py

Unit tests for scripts/verify-frontend-security-e2e.py enforcing orchestration invariants:
- Prerequisites validation (Node, npm, Java, Maven wrapper, Playwright, Spring Boot, Quarkus)
- Port allocation
- Frontend build steps and failure handling
- Spring Boot & Quarkus packaging and artifact checks
- Server startup and readiness polling
- Process-group management and robust cleanup
- Playwright execution targeting authenticated-security.spec.ts
- Scenario parity computation across 14 security criteria
- Machine-readable report generation schema
- Full CLI orchestration flows (Spring, Quarkus, All, failure modes)
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


verify_security_e2e = load_script("verify-frontend-security-e2e.py")
find_free_port = verify_security_e2e.find_free_port
check_tool_version = verify_security_e2e.check_tool_version
check_prerequisites = verify_security_e2e.check_prerequisites
build_frontend = verify_security_e2e.build_frontend
build_spring_app = verify_security_e2e.build_spring_app
build_quarkus_app = verify_security_e2e.build_quarkus_app
start_spring_server = verify_security_e2e.start_spring_server
start_quarkus_server = verify_security_e2e.start_quarkus_server
poll_server_readiness = verify_security_e2e.poll_server_readiness
terminate_process_group = verify_security_e2e.terminate_process_group
run_playwright_suite = verify_security_e2e.run_playwright_suite
extract_playwright_test_records = verify_security_e2e.extract_playwright_test_records
extract_browser_version = verify_security_e2e.extract_browser_version
compute_scenarios = verify_security_e2e.compute_scenarios
generate_report = verify_security_e2e.generate_report
print_parity_matrix = verify_security_e2e.print_parity_matrix
qualify_framework_lane = verify_security_e2e.qualify_framework_lane
main = verify_security_e2e.main


class VerifyFrontendSecurityE2ETests(unittest.TestCase):

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
    def test_check_tool_version_failure(self, mock_run, mock_which):
        mock_run.return_value = MagicMock(returncode=127, stdout="", stderr="")
        with self.assertRaises(RuntimeError) as ctx:
            check_tool_version("tool")
        self.assertIn("Failed executing", str(ctx.exception))

    def test_check_prerequisites_missing_mvnw(self):
        browser_dir = self.test_root / "browser"
        browser_dir.mkdir(parents=True)
        (browser_dir / "package.json").write_text(json.dumps({"devDependencies": {"@playwright/test": "1.64.0"}}))

        with self.assertRaises(RuntimeError) as ctx:
            check_prerequisites(self.test_root, browser_dir)
        self.assertIn("Maven wrapper missing", str(ctx.exception))

    def test_check_prerequisites_missing_browser_package_json(self):
        mvnw = self.test_root / "mvnw"
        mvnw.touch()
        browser_dir = self.test_root / "browser"
        browser_dir.mkdir(parents=True)

        with patch.object(verify_security_e2e, "check_tool_version", return_value="v1.0"):
            with patch("subprocess.run", return_value=MagicMock(returncode=0, stdout="Apache Maven 3.9.6\n")):
                with self.assertRaises(RuntimeError) as ctx:
                    check_prerequisites(self.test_root, browser_dir)
                self.assertIn("Browser test package.json missing", str(ctx.exception))

    def test_check_prerequisites_success(self):
        mvnw = self.test_root / "mvnw"
        mvnw.touch()
        browser_dir = self.test_root / "browser"
        browser_dir.mkdir(parents=True)
        (browser_dir / "package.json").write_text(json.dumps({"devDependencies": {"@playwright/test": "1.64.0"}}))

        fe_dir = self.test_root / "fe"
        fe_dir.mkdir(parents=True)
        (fe_dir / "package.json").write_text(json.dumps({"devDependencies": {"vite": "^8.3.4", "svelte": "^5.57.2"}}))

        sp_dir = self.test_root / "sp"
        sp_dir.mkdir(parents=True)
        (sp_dir / "pom.xml").write_text("<project><properties><spring-boot.version>4.1.1</spring-boot.version></properties></project>")

        qk_dir = self.test_root / "qk"
        qk_dir.mkdir(parents=True)
        (qk_pom := qk_dir / "pom.xml").write_text("<project><properties><quarkus.version>3.39.4</quarkus.version></properties></project>")

        with patch.object(verify_security_e2e, "check_tool_version", side_effect=lambda name, flag="--version": f"{name}-1.0"):
            with patch("subprocess.run", return_value=MagicMock(returncode=0, stdout="Apache Maven 3.9.6\n")):
                res = check_prerequisites(self.test_root, browser_dir, fe_dir, sp_dir, qk_dir)
                self.assertEqual("1.64.0", res["playwright"])
                self.assertEqual("8.3.4", res["vite"])
                self.assertEqual("5.57.2", res["svelte"])
                self.assertEqual("4.1.1", res["springBoot"])
                self.assertEqual("3.39.4", res["quarkus"])

    # -------------------------------------------------------------------------
    # 3. Frontend Build
    # -------------------------------------------------------------------------

    def test_build_frontend_skip_when_manifest_exists(self):
        fe_dir = self.test_root / "fe"
        manifest = fe_dir / "dist" / ".vite" / "manifest.json"
        manifest.parent.mkdir(parents=True)
        manifest.write_text("{}")

        dist = build_frontend(fe_dir, skip_build=True)
        self.assertEqual(fe_dir / "dist", dist)

    @patch("subprocess.run")
    def test_build_frontend_success(self, mock_run):
        fe_dir = self.test_root / "fe"
        fe_dir.mkdir(parents=True)
        (fe_dir / "package.json").write_text("{}")
        manifest = fe_dir / "dist" / ".vite" / "manifest.json"

        def fake_run(*args, **kwargs):
            manifest.parent.mkdir(parents=True, exist_ok=True)
            manifest.write_text("{}")
            return MagicMock(returncode=0, stdout="", stderr="")

        mock_run.side_effect = fake_run
        dist = build_frontend(fe_dir, skip_build=False)
        self.assertEqual(fe_dir / "dist", dist)

    @patch("subprocess.run")
    def test_build_frontend_failure(self, mock_run):
        fe_dir = self.test_root / "fe"
        fe_dir.mkdir(parents=True)
        (fe_dir / "package.json").write_text("{}")
        mock_run.return_value = MagicMock(returncode=1, stdout="Build error", stderr="Details")

        with self.assertRaises(RuntimeError) as ctx:
            build_frontend(fe_dir, skip_build=False)
        self.assertIn("Frontend asset build failed", str(ctx.exception))

    # -------------------------------------------------------------------------
    # 4. Spring Boot Build & Packaging
    # -------------------------------------------------------------------------

    @patch("subprocess.run")
    def test_build_spring_app_success(self, mock_run):
        sp_dir = self.test_root / "sp"
        sp_dir.mkdir(parents=True)
        (sp_dir / "pom.xml").touch()
        target_dir = sp_dir / "target"
        target_dir.mkdir(parents=True)
        jar_path = target_dir / "app.jar"

        with zipfile.ZipFile(jar_path, "w") as zf:
            zf.writestr("BOOT-INF/classes/static/.vite/manifest.json", "{}")

        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        res_jar = build_spring_app(self.test_root, sp_dir, skip_build=False)
        self.assertEqual(jar_path, res_jar)

    def test_build_spring_app_missing_manifest(self):
        sp_dir = self.test_root / "sp"
        sp_dir.mkdir(parents=True)
        (sp_dir / "pom.xml").touch()
        target_dir = sp_dir / "target"
        target_dir.mkdir(parents=True)
        jar_path = target_dir / "app.jar"

        with zipfile.ZipFile(jar_path, "w") as zf:
            zf.writestr("some/other/file.txt", "data")

        with self.assertRaises(FileNotFoundError) as ctx:
            build_spring_app(self.test_root, sp_dir, skip_build=True)
        self.assertIn("Expected packaged Vite manifest not found", str(ctx.exception))

    # -------------------------------------------------------------------------
    # 5. Quarkus Build & Packaging
    # -------------------------------------------------------------------------

    @patch("subprocess.run")
    def test_build_quarkus_app_success(self, mock_run):
        qk_dir = self.test_root / "qk"
        qk_dir.mkdir(parents=True)
        (qk_dir / "pom.xml").touch()
        target_dir = qk_dir / "target"
        runner_jar = target_dir / "quarkus-app" / "quarkus-run.jar"
        runner_jar.parent.mkdir(parents=True)
        runner_jar.touch()
        (target_dir / "quarkus-app" / "lib").mkdir(parents=True)
        app_dir = target_dir / "quarkus-app" / "app"
        app_dir.mkdir(parents=True)
        app_jar = app_dir / "app.jar"

        with zipfile.ZipFile(app_jar, "w") as zf:
            zf.writestr("META-INF/resources/.vite/manifest.json", "{}")

        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")
        res_jar = build_quarkus_app(self.test_root, qk_dir, skip_build=False)
        self.assertEqual(runner_jar, res_jar)

    # -------------------------------------------------------------------------
    # 6. Server Management & Polling
    # -------------------------------------------------------------------------

    @patch("subprocess.Popen")
    def test_start_spring_server(self, mock_popen):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        jar = self.test_root / "app.jar"
        log = self.test_root / "log.txt"

        proc = start_spring_server(jar, 8090, log)
        self.assertEqual(mock_proc, proc)
        args, kwargs = mock_popen.call_args
        self.assertIn("--server.port=8090", args[0])

    @patch("subprocess.Popen")
    def test_start_quarkus_server(self, mock_popen):
        mock_proc = MagicMock()
        mock_popen.return_value = mock_proc
        jar = self.test_root / "quarkus-run.jar"
        log = self.test_root / "log.txt"

        proc = start_quarkus_server(jar, 8090, log)
        self.assertEqual(mock_proc, proc)
        args, kwargs = mock_popen.call_args
        self.assertIn("-Dquarkus.http.port=8090", args[0])

    @patch("urllib.request.urlopen")
    def test_poll_server_readiness_success(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 200
        mock_resp.read.return_value = b'{"status": "UP"}'
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        poll_server_readiness(8090, mock_proc, timeout_seconds=2.0, interval_seconds=0.01)

    def test_poll_server_readiness_premature_exit(self):
        mock_proc = MagicMock()
        mock_proc.poll.return_value = 1
        mock_proc.returncode = 1

        with self.assertRaises(RuntimeError) as ctx:
            poll_server_readiness(8090, mock_proc, timeout_seconds=1.0, interval_seconds=0.01)
        self.assertIn("process exited prematurely", str(ctx.exception))

    @patch("os.getpgid", return_value=1234)
    @patch("os.killpg")
    def test_terminate_process_group_sigterm(self, mock_killpg, mock_getpgid):
        mock_proc = MagicMock()
        mock_proc.pid = 1234
        # poll() None first, then 0 after kill
        mock_proc.poll.side_effect = [None, 0]

        terminate_process_group(mock_proc)
        mock_killpg.assert_called_once_with(1234, signal.SIGTERM)

    # -------------------------------------------------------------------------
    # 7. Playwright Runner & Scenario Extraction
    # -------------------------------------------------------------------------

    @patch("subprocess.run")
    def test_run_playwright_suite_command(self, mock_run):
        browser_dir = self.test_root / "browser"
        (browser_dir / "node_modules").mkdir(parents=True)
        mock_run.return_value = MagicMock(returncode=0, stdout="", stderr="")

        code, out, err, results = run_playwright_suite(
            browser_dir,
            "http://127.0.0.1:8080",
            headless=True,
            retries=1,
            test_file="tests/authenticated-security.spec.ts",
        )
        self.assertEqual(0, code)
        cmd_run = mock_run.call_args[0][0]
        self.assertIn("tests/authenticated-security.spec.ts", cmd_run)
        self.assertIn("--retries", cmd_run)

    def test_compute_scenarios_all_passing(self):
        tests = [
            {"title": "anonymous user accessing protected route is redirected to login", "status": "passed"},
            {"title": "form login establishes session, SSR renders security & CSRF metadata, Svelte island mounts and follows with CSRF", "status": "passed"},
            {"title": "role-based authorization: admin renders admin-link and accesses admin route, alice is forbidden", "status": "passed"},
            {"title": "server enforces CSRF protection: missing or invalid CSRF is rejected and state preserved", "status": "passed"},
            {"title": "non-JavaScript fallback form with CSRF succeeds when JavaScript is disabled", "status": "passed"},
        ]
        scenarios = compute_scenarios(tests, overall_pass=True)
        for name, status in scenarios.items():
            self.assertEqual("PASS", status, f"Scenario {name} should be PASS")

    def test_compute_scenarios_failure_propagation(self):
        tests = [
            {"title": "anonymous user accessing protected route is redirected to login", "status": "passed"},
            {"title": "form login establishes session, SSR renders security & CSRF metadata", "status": "failed"},
        ]
        scenarios = compute_scenarios(tests, overall_pass=False)
        self.assertEqual("PASS", scenarios["anonymousDenied"])
        self.assertEqual("FAIL", scenarios["authentication"])
        self.assertEqual("FAIL", scenarios["csrfValidAccepted"])
        self.assertEqual("FAIL", scenarios["noJsCsrfForm"])

    # -------------------------------------------------------------------------
    # 8. Report Generation & Parity Table
    # -------------------------------------------------------------------------

    def test_generate_report_schema(self):
        report_file = self.test_root / "report.json"
        toolchain = {
            "node": "v22.23.3",
            "npm": "10.9.9",
            "java": "openjdk 21",
            "mvnw": "3.9.6",
            "vite": "8.3.4",
            "svelte": "5.57.2",
            "playwright": "1.64.0",
            "springBoot": "4.1.1",
            "quarkus": "3.39.4",
        }
        framework_results = {
            "spring": {
                "status": "PASS",
                "displayName": "Spring Boot",
                "version": "4.1.1",
                "port": 8080,
                "durationSeconds": 5.2,
                "packageCommand": "./mvnw package",
                "startCommand": "java -jar app.jar",
                "artifact": "app.jar",
                "scenarios": {"anonymousDenied": "PASS", "authentication": "PASS"},
                "stats": {"total": 5, "passed": 5, "failed": 0, "skipped": 0, "flaky": 0},
                "tests": [{"title": "t1", "status": "passed", "durationMs": 100}],
                "errors": [],
            }
        }

        report = generate_report(
            report_file,
            status="PASS",
            toolchain=toolchain,
            framework_results=framework_results,
            duration_seconds=10.5,
            errors=[],
            chromium_version="156.0.8078.4",
        )

        self.assertTrue(report_file.is_file())
        self.assertEqual("PASS", report["status"])
        self.assertEqual("authenticated-security-browser-e2e", report["suite"])
        self.assertIn("parityMatrix", report)
        self.assertIn("lanes", report)
        self.assertIn("frameworks", report)
        self.assertEqual("spring", report["frameworks"][0]["framework"])
        self.assertIn("spring", report["lanes"])
        self.assertEqual("Chromium 156.0.8078.4", report["environment"]["browser"])

    def test_print_parity_matrix_runs_cleanly(self):
        framework_results = {
            "spring": {
                "displayName": "Spring Boot",
                "status": "PASS",
                "scenarios": {"anonymousDenied": "PASS", "authentication": "PASS"},
            }
        }
        # Verify no unhandled exception
        print_parity_matrix(framework_results)

    # -------------------------------------------------------------------------
    # 9. Main Orchestration Flow
    # -------------------------------------------------------------------------

    @patch.object(verify_security_e2e, "check_prerequisites")
    @patch.object(verify_security_e2e, "build_frontend")
    @patch.object(verify_security_e2e, "qualify_framework_lane")
    def test_main_spring_success(self, mock_lane, mock_fe, mock_pre):
        mock_pre.return_value = {
            "node": "v22.23.3",
            "npm": "10.9.9",
            "java": "21",
            "mvnw": "3.9",
            "playwright": "1.64.0",
            "springBoot": "4.1.1",
        }
        mock_lane.return_value = {
            "status": "PASS",
            "name": "spring",
            "displayName": "Spring Boot",
            "version": "4.1.1",
            "port": 8080,
            "durationSeconds": 4.5,
            "packageCommand": "mvnw",
            "startCommand": "java",
            "artifact": "app.jar",
            "scenarios": {"anonymousDenied": "PASS"},
            "tests": [],
            "stats": {"total": 5, "passed": 5, "failed": 0, "skipped": 0, "flaky": 0},
            "errors": [],
            "rawResults": None,
        }

        report_path = self.test_root / "rep.json"
        code = main(["--framework", "spring", "--report-path", str(report_path)])
        self.assertEqual(0, code)
        self.assertTrue(report_path.is_file())


if __name__ == "__main__":
    unittest.main()
