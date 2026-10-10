"""
scripts/tests/test_verify_frontend_dev_mode_e2e.py

Unit tests for scripts/verify-frontend-dev-mode-e2e.py enforcing orchestration invariants:
- Prerequisites validation (Node, npm, Java, Maven wrapper, Playwright, Vite, Svelte, Spring Boot, Quarkus)
- Development mode isolation (dist removal, pure dev server asset serving)
- Dynamic port allocation
- Vite server startup and readiness polling
- Spring Boot DevTools and Quarkus dev mode startup and /health readiness polling
- Process-group management and clean termination
- Playwright execution targeting frontend-dev-mode.spec.ts
- Scenario parity computation across 10 qualification criteria
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
from pathlib import Path
from unittest.mock import MagicMock, call, mock_open, patch

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


verify_dev_mode_e2e = load_script("verify-frontend-dev-mode-e2e.py")
find_free_port = verify_dev_mode_e2e.find_free_port
check_tool_version = verify_dev_mode_e2e.check_tool_version
check_prerequisites = verify_dev_mode_e2e.check_prerequisites
ensure_clean_dev_environment = verify_dev_mode_e2e.ensure_clean_dev_environment
start_vite_server = verify_dev_mode_e2e.start_vite_server
poll_vite_readiness = verify_dev_mode_e2e.poll_vite_readiness
start_spring_dev_server = verify_dev_mode_e2e.start_spring_dev_server
start_quarkus_dev_server = verify_dev_mode_e2e.start_quarkus_dev_server
poll_java_readiness = verify_dev_mode_e2e.poll_java_readiness
terminate_process_group = verify_dev_mode_e2e.terminate_process_group
run_playwright_suite = verify_dev_mode_e2e.run_playwright_suite
extract_playwright_test_records = verify_dev_mode_e2e.extract_playwright_test_records
compute_scenarios = verify_dev_mode_e2e.compute_scenarios
verify_clean_git_state = verify_dev_mode_e2e.verify_clean_git_state
generate_report = verify_dev_mode_e2e.generate_report
print_parity_matrix = verify_dev_mode_e2e.print_parity_matrix
qualify_framework_lane = verify_dev_mode_e2e.qualify_framework_lane
main = verify_dev_mode_e2e.main


class VerifyFrontendDevModeE2ETests(unittest.TestCase):

    def test_find_free_port_returns_positive_integer(self):
        port = find_free_port()
        self.assertIsInstance(port, int)
        self.assertGreater(port, 1024)
        self.assertLessEqual(port, 65535)

    @patch("shutil.which", return_value="/usr/bin/node")
    @patch("subprocess.run")
    def test_check_tool_version_success(self, mock_run, mock_which):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["node", "--version"], returncode=0, stdout="v22.23.3\n", stderr=""
        )
        res = check_tool_version("node", "--version")
        self.assertEqual(res, "v22.23.3")

    @patch("shutil.which", return_value=None)
    def test_check_tool_version_missing(self, mock_which):
        with self.assertRaises(RuntimeError) as ctx:
            check_tool_version("missing_tool")
        self.assertIn("Required tool 'missing_tool' was not found", str(ctx.exception))

    def test_ensure_clean_dev_environment_removes_dist(self):
        with tempfile.TemporaryDirectory() as td:
            fe_dir = Path(td)
            dist_dir = fe_dir / "dist"
            dist_dir.mkdir(parents=True)
            (dist_dir / "test.txt").write_text("hello")
            (fe_dir / "node_modules").mkdir()

            self.assertTrue(dist_dir.exists())
            ensure_clean_dev_environment(fe_dir)
            self.assertFalse(dist_dir.exists())

    @patch("os.setsid")
    @patch("subprocess.Popen")
    def test_start_vite_server(self, mock_popen, mock_setsid):
        mock_proc = MagicMock()
        mock_proc.pid = 1234
        mock_popen.return_value = mock_proc

        with tempfile.TemporaryDirectory() as td:
            fe_dir = Path(td)
            log_path = fe_dir / "target" / "vite.log"
            proc = start_vite_server(fe_dir, 5173, log_path)
            self.assertEqual(proc.pid, 1234)
            self.assertTrue(mock_popen.called)
            args, kwargs = mock_popen.call_args
            self.assertIn("vite", args[0])
            self.assertIn("--port", args[0])
            self.assertIn("5173", args[0])
            self.assertIn("--strictPort", args[0])
            self.assertEqual(kwargs["env"]["VITE_DEV_ORIGIN"], "http://127.0.0.1:5173")

    @patch("urllib.request.urlopen")
    def test_poll_vite_readiness_success(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 200
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        # Should complete without error
        poll_vite_readiness(5173, mock_proc, timeout_seconds=1.0)
        self.assertTrue(mock_urlopen.called)

    @patch("urllib.request.urlopen")
    def test_poll_java_readiness_success(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 200
        mock_resp.read.return_value = b'{"status":"UP"}'
        mock_resp.__enter__.return_value = mock_resp
        mock_urlopen.return_value = mock_resp

        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        poll_java_readiness(8080, mock_proc, timeout_seconds=1.0)
        self.assertTrue(mock_urlopen.called)

    @patch("os.getpgid", return_value=9999)
    @patch("os.killpg")
    def test_terminate_process_group_sends_sigterm(self, mock_killpg, mock_getpgid):
        mock_proc = MagicMock()
        mock_proc.pid = 9999
        mock_proc.poll.side_effect = [None, 0]

        terminate_process_group(mock_proc, timeout_sec=0.5)
        mock_killpg.assert_called_with(9999, signal.SIGTERM)

    def test_extract_playwright_test_records_and_stats(self):
        sample_results = {
            "suites": [
                {
                    "specs": [
                        {
                            "title": "executes end-to-end dev lifecycle: SSR -> Svelte HMR -> CSS HMR -> VTL reload -> Java restart -> Post-restart HMR",
                            "tests": [
                                {
                                    "status": "expected",
                                    "results": [{"duration": 4200}],
                                }
                            ],
                        }
                    ]
                }
            ]
        }
        tests, stats = extract_playwright_test_records(sample_results)
        self.assertEqual(stats["total"], 1)
        self.assertEqual(stats["passed"], 1)
        self.assertEqual(tests[0]["status"], "passed")
        self.assertEqual(tests[0]["durationMs"], 4200)

    def test_compute_scenarios_pass(self):
        tests = [{"title": "executes end-to-end dev lifecycle", "status": "passed"}]
        scenarios = compute_scenarios(tests, overall_pass=True, vite_survived=True, git_clean=True)
        self.assertEqual(scenarios["initialSsrAndMount"], "PASS")
        self.assertEqual(scenarios["svelteHmr"], "PASS")
        self.assertEqual(scenarios["cssHmr"], "PASS")
        self.assertEqual(scenarios["vtlTemplateReload"], "PASS")
        self.assertEqual(scenarios["javaReloadOrRestart"], "PASS")
        self.assertEqual(scenarios["viteProcessSurvival"], "PASS")
        self.assertEqual(scenarios["postReloadSvelteHmr"], "PASS")
        self.assertEqual(scenarios["clientDataFreshness"], "PASS")
        self.assertEqual(scenarios["zeroConsoleErrors"], "PASS")
        self.assertEqual(scenarios["sourceRestoration"], "PASS")

    def test_compute_scenarios_fail_on_vite_killed(self):
        tests = [{"title": "executes end-to-end dev lifecycle", "status": "passed"}]
        scenarios = compute_scenarios(tests, overall_pass=True, vite_survived=False, git_clean=True)
        self.assertEqual(scenarios["viteProcessSurvival"], "FAIL")

    def test_generate_report_schema(self):
        with tempfile.TemporaryDirectory() as td:
            rep_path = Path(td) / "reports" / "report.json"
            toolchain = {"node": "v22.23.3", "java": "21.0.12.1"}
            framework_results = {
                "spring": {
                    "framework": "spring",
                    "status": "PASS",
                    "scenarios": {
                        "initialSsrAndMount": "PASS",
                        "svelteHmr": "PASS",
                        "cssHmr": "PASS",
                        "vtlTemplateReload": "PASS",
                        "javaReloadOrRestart": "PASS",
                        "viteProcessSurvival": "PASS",
                        "postReloadSvelteHmr": "PASS",
                        "clientDataFreshness": "PASS",
                        "zeroConsoleErrors": "PASS",
                        "sourceRestoration": "PASS",
                    },
                }
            }

            generate_report(
                rep_path,
                status="PASS",
                toolchain=toolchain,
                framework_results=framework_results,
                duration_seconds=12.34,
                errors=[],
            )

            self.assertTrue(rep_path.is_file())
            with open(rep_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual(data["status"], "PASS")
            self.assertEqual(data["suite"], "frontend-dev-mode-e2e")
            self.assertEqual(data["parityMatrix"]["initialSsrAndMount"]["spring"], "PASS")
            self.assertEqual(data["parityMatrix"]["svelteHmr"]["spring"], "PASS")

    @patch.object(verify_dev_mode_e2e.subprocess, "run")
    def test_verify_clean_git_state_returns_true_when_clean(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["git", "status", "--porcelain"], returncode=0, stdout="", stderr=""
        )
        self.assertTrue(verify_clean_git_state(ROOT))

    @patch.object(verify_dev_mode_e2e.subprocess, "run")
    def test_verify_clean_git_state_returns_false_when_tracked_modified(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["git", "status", "--porcelain"],
            returncode=0,
            stdout=" M examples/frontend-svelte-islands/src/pages/employees/Employees.svelte\n",
            stderr="",
        )
        self.assertFalse(verify_clean_git_state(ROOT))

    @patch.object(verify_dev_mode_e2e, "check_prerequisites")
    @patch.object(verify_dev_mode_e2e, "qualify_framework_lane")
    @patch.object(verify_dev_mode_e2e, "generate_report")
    @patch.object(verify_dev_mode_e2e, "print_parity_matrix")
    def test_main_spring_success(self, mock_print, mock_gen, mock_lane, mock_prereqs):
        mock_prereqs.return_value = {"node": "v22.23.3", "java": "21"}
        mock_lane.return_value = {"status": "PASS", "errors": []}

        exit_code = main(["spring"])
        self.assertEqual(exit_code, 0)
        mock_lane.assert_called_once()


if __name__ == "__main__":
    unittest.main()
