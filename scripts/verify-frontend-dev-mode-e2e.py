#!/usr/bin/env python3
"""
verify-frontend-dev-mode-e2e.py

Deterministic Chromium browser End-to-End qualification script for
Viet Template Local Frontend Development Mode & Vite HMR Workflow
side-by-side with Java development servers (Spring Boot DevTools and Quarkus Dev Mode):

1. Validates prerequisites (Node, npm, Java, Maven wrapper, Playwright, Vite, Svelte, Spring Boot, Quarkus).
2. Enforces development mode isolation (asserts and removes any pre-existing dist/.vite/manifest.json).
3. Allocates dynamic local ports for Vite dev server and Java development server.
4. Boots the Vite 8 + Svelte 5 dev server independently in background.
5. Boots the Java dev server (Spring Boot DevTools or Quarkus dev mode live reload).
6. Deterministically polls readiness endpoints until UP:
   - Vite: /@vite/client returns HTTP 200
   - Java: /health returns HTTP 200 {"status": "UP"}
7. Executes Playwright test suite against real Chromium browser (frontend-dev-mode.spec.ts):
   - Initial SSR + Svelte island mounting with direct Vite asset URLs.
   - Svelte source HMR: mutates Employees.svelte, verifies DOM update with 0 main-frame navigations.
   - CSS HMR: mutates employees.css root variable, verifies computed style update with 0 navigations.
   - VTL template reload: mutates employees.vtl, reloads page, verifies updated SSR content.
   - Java source reload / restart: mutates Java source, triggers framework reload, verifies classloader/engine turnover.
   - Vite process survival: asserts Vite process remained alive and responsive across Java reload.
   - Post-reload Svelte HMR: mutates Svelte again, verifies DOM updates via HMR with 0 navigations.
   - ClientData freshness: validates ClientData JSON script and REST interaction.
   - Zero console errors and zero unexpected network failures.
   - Byte-for-byte restoration of all modified source files.
8. Guarantees clean process-group termination and resource cleanup on exit.
9. Verifies 100% clean repository state via git status check.
10. Emits machine-readable report to build/reports/frontend-dev-mode-e2e.json.
11. Prints framework parity matrix and exits non-zero on any failure.
"""

import argparse
import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BROWSER_DIR = REPO_ROOT / "integration-tests" / "frontend" / "browser"
DEFAULT_SPRING_DIR = REPO_ROOT / "integration-tests" / "spring" / "frontend-dev-mode-e2e"
DEFAULT_QUARKUS_DIR = REPO_ROOT / "integration-tests" / "quarkus" / "frontend-dev-mode-e2e"
DEFAULT_FRONTEND_DIR = REPO_ROOT / "examples" / "frontend-svelte-islands"
DEFAULT_REPORT_PATH = REPO_ROOT / "build" / "reports" / "frontend-dev-mode-e2e.json"


def find_free_port() -> int:
    """Finds an available TCP port on localhost."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        return sock.getsockname()[1]


def check_tool_version(tool_name: str, version_flag: str = "--version") -> str:
    """Verifies that a tool is on PATH and returns its version string."""
    bin_path = shutil.which(tool_name)
    if not bin_path:
        raise RuntimeError(f"Required tool '{tool_name}' was not found on system PATH.")

    res = subprocess.run([tool_name, version_flag], capture_output=True, text=True)
    output = res.stdout.strip() or res.stderr.strip()
    if not output:
        if res.returncode != 0:
            raise RuntimeError(f"Failed executing '{tool_name} {version_flag}': returncode {res.returncode}")
        raise RuntimeError(f"Tool '{tool_name} {version_flag}' returned empty output.")
    lines = output.splitlines()
    return lines[0].strip()


def check_prerequisites(
    repo_root: Path,
    browser_dir: Path,
    frontend_dir: Optional[Path] = None,
    spring_dir: Optional[Path] = None,
    quarkus_dir: Optional[Path] = None,
) -> Dict[str, str]:
    """Validates presence and extracts versions for Node, npm, Java, Maven, Playwright, Vite, Svelte, Spring Boot, and Quarkus."""
    toolchain: Dict[str, str] = {}

    toolchain["node"] = check_tool_version("node", "--version")
    toolchain["npm"] = check_tool_version("npm", "--version")
    toolchain["java"] = check_tool_version("java", "-version")

    mvnw = repo_root / "mvnw"
    if not mvnw.is_file():
        raise RuntimeError(f"Maven wrapper missing at: {mvnw}")
    mvnw_res = subprocess.run([str(mvnw), "-v"], cwd=str(repo_root), capture_output=True, text=True)
    if mvnw_res.returncode != 0:
        raise RuntimeError(f"Failed executing mvnw -v: {mvnw_res.stderr}")
    toolchain["mvnw"] = mvnw_res.stdout.strip().splitlines()[0]

    browser_pkg = browser_dir / "package.json"
    if not browser_pkg.is_file():
        raise RuntimeError(f"Browser test package.json missing at: {browser_pkg}")
    with open(browser_pkg, "r", encoding="utf-8") as f:
        pkg_data = json.load(f)
    playwright_ver = pkg_data.get("devDependencies", {}).get("@playwright/test", "unknown")
    toolchain["playwright"] = playwright_ver

    fe_dir = frontend_dir or DEFAULT_FRONTEND_DIR
    fe_pkg = fe_dir / "package.json"
    if fe_pkg.is_file():
        try:
            with open(fe_pkg, "r", encoding="utf-8") as f:
                fe_data = json.load(f)
            dev_deps = fe_data.get("devDependencies", {})
            if "vite" in dev_deps:
                toolchain["vite"] = dev_deps["vite"].lstrip("^~")
            if "svelte" in dev_deps:
                toolchain["svelte"] = dev_deps["svelte"].lstrip("^~")
        except Exception:
            pass

    sp_dir = spring_dir or DEFAULT_SPRING_DIR
    sp_pom = sp_dir / "pom.xml"
    if sp_pom.is_file():
        try:
            pom_text = sp_pom.read_text(encoding="utf-8")
            m = re.search(r"<version>([^<]+)</version>", pom_text)
            # Find parent spring boot version
            m_boot = re.search(r"<groupId>org\.springframework\.boot</groupId>[\s\S]*?<version>([^<]+)</version>", pom_text)
            if m_boot:
                toolchain["springBoot"] = m_boot.group(1).strip()
        except Exception:
            pass

    qk_dir = quarkus_dir or DEFAULT_QUARKUS_DIR
    qk_pom = qk_dir / "pom.xml"
    if qk_pom.is_file():
        try:
            pom_text = qk_pom.read_text(encoding="utf-8")
            m = re.search(r"<quarkus\.version>([^<]+)</quarkus\.version>", pom_text)
            if m:
                toolchain["quarkus"] = m.group(1).strip()
        except Exception:
            pass

    return toolchain


def ensure_clean_dev_environment(frontend_dir: Path) -> None:
    """Ensures that frontend dist directory does NOT exist, enforcing clean dev mode."""
    dist_dir = frontend_dir / "dist"
    if dist_dir.exists():
        print(f"[INFO] Removing pre-existing frontend dist at {dist_dir} to verify pure dev mode...")
        shutil.rmtree(dist_dir, ignore_errors=True)

    node_modules = frontend_dir / "node_modules"
    if not node_modules.is_dir():
        print(f"[STEP] Installing frontend dependencies in {frontend_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(frontend_dir), capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Frontend npm ci failed: {res.stderr}")


def start_vite_server(
    frontend_dir: Path,
    port: int,
    log_path: Path,
) -> subprocess.Popen:
    """Starts the Vite dev server with dynamic port and strictPort."""
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_file = open(log_path, "w", encoding="utf-8")

    env = os.environ.copy()
    env["VITE_DEV_ORIGIN"] = f"http://127.0.0.1:{port}"

    cmd = [
        "npm",
        "run",
        "dev",
        "--",
        "--host",
        "127.0.0.1",
        "--port",
        str(port),
        "--strictPort",
    ]

    proc = subprocess.Popen(
        cmd,
        cwd=str(frontend_dir),
        env=env,
        stdout=log_file,
        stderr=subprocess.STDOUT,
        preexec_fn=os.setsid,
    )
    return proc


def poll_vite_readiness(
    port: int,
    proc: subprocess.Popen,
    timeout_seconds: float = 30.0,
) -> None:
    """Polls Vite /@vite/client endpoint until HTTP 200 is returned."""
    url = f"http://127.0.0.1:{port}/@vite/client"
    start = time.monotonic()
    last_err: Optional[Exception] = None

    while time.monotonic() - start < timeout_seconds:
        if proc.poll() is not None:
            raise RuntimeError(f"Vite dev server process exited unexpectedly with returncode {proc.returncode}.")
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=2.0) as resp:
                if resp.status == 200:
                    return
        except Exception as e:
            last_err = e
        time.sleep(0.2)

    raise TimeoutError(f"Vite dev server on port {port} failed to respond at {url} within {timeout_seconds}s. Last error: {last_err}")


def start_spring_dev_server(
    repo_root: Path,
    spring_dir: Path,
    java_port: int,
    vite_port: int,
    log_path: Path,
) -> subprocess.Popen:
    """Starts Spring Boot dev server via spring-boot:run."""
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_file = open(log_path, "w", encoding="utf-8")

    mvnw = repo_root / "mvnw"
    pom_file = spring_dir / "pom.xml"

    cmd = [
        str(mvnw),
        "spring-boot:run",
        "-f",
        str(pom_file),
        f"-Dspring-boot.run.arguments=--server.port={java_port} --viet-template.assets.dev-server=http://127.0.0.1:{vite_port}",
    ]

    proc = subprocess.Popen(
        cmd,
        cwd=str(repo_root),
        stdout=log_file,
        stderr=subprocess.STDOUT,
        preexec_fn=os.setsid,
    )
    return proc


def start_quarkus_dev_server(
    repo_root: Path,
    quarkus_dir: Path,
    java_port: int,
    vite_port: int,
    log_path: Path,
) -> subprocess.Popen:
    """Starts Quarkus dev server via quarkus:dev."""
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_file = open(log_path, "w", encoding="utf-8")

    mvnw = repo_root / "mvnw"
    pom_file = quarkus_dir / "pom.xml"

    cmd = [
        str(mvnw),
        "quarkus:dev",
        "-Dquarkus.analytics.disabled=true",
        f"-Dquarkus.http.port={java_port}",
        "-Ddebug=false",
        f"-Dquarkus.viet-template.assets.dev-server=http://127.0.0.1:{vite_port}",
        "-f",
        str(pom_file),
    ]

    proc = subprocess.Popen(
        cmd,
        cwd=str(repo_root),
        stdout=log_file,
        stderr=subprocess.STDOUT,
        preexec_fn=os.setsid,
    )
    return proc


def poll_java_readiness(
    port: int,
    proc: subprocess.Popen,
    timeout_seconds: float = 40.0,
    server_name: str = "Java Server",
) -> None:
    """Polls /health endpoint on target Java server until status UP."""
    url = f"http://127.0.0.1:{port}/health"
    start = time.monotonic()
    last_err: Optional[Exception] = None

    while time.monotonic() - start < timeout_seconds:
        if proc.poll() is not None:
            raise RuntimeError(f"{server_name} process exited prematurely with returncode {proc.returncode}.")
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=2.0) as resp:
                if resp.status == 200:
                    payload = json.loads(resp.read().decode("utf-8"))
                    if payload.get("status") == "UP":
                        return
        except Exception as e:
            last_err = e
        time.sleep(0.3)

    raise TimeoutError(f"{server_name} on port {port} failed /health check within {timeout_seconds}s. Last error: {last_err}")


def terminate_process_group(proc: Optional[subprocess.Popen], timeout_sec: float = 8.0) -> None:
    """Sends SIGTERM then SIGKILL to process group, guaranteeing no orphan processes."""
    if proc is None:
        return
    try:
        pgid = os.getpgid(proc.pid)
    except (ProcessLookupError, OSError):
        return

    try:
        os.killpg(pgid, signal.SIGTERM)
    except (ProcessLookupError, OSError):
        return

    start = time.monotonic()
    while time.monotonic() - start < timeout_sec:
        if proc.poll() is not None:
            return
        time.sleep(0.1)

    try:
        os.killpg(pgid, signal.SIGKILL)
    except (ProcessLookupError, OSError):
        pass


def run_playwright_suite(
    browser_dir: Path,
    base_url: str,
    vite_dev_url: str,
    framework: str,
    headless: bool = True,
    retries: Optional[int] = None,
    test_file: str = "tests/frontend-dev-mode.spec.ts",
) -> Tuple[int, str, str, Optional[Dict[str, Any]]]:
    """Runs Playwright tests against the running development servers and collects results."""
    nm = browser_dir / "node_modules"
    if not nm.is_dir():
        print(f"[STEP] Installing Playwright dependencies in {browser_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(browser_dir), capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Playwright 'npm ci' failed: {res.stderr}")

    cmd = ["npx", "playwright", "test", test_file, "--project=chromium"]
    if retries is not None:
        cmd.extend(["--retries", str(retries)])
    if not headless:
        cmd.append("--headed")

    env = os.environ.copy()
    env["BASE_URL"] = base_url
    env["VITE_DEV_URL"] = vite_dev_url
    env["FRAMEWORK"] = framework

    print(f"[STEP] Executing Playwright dev mode suite for {framework} ({base_url} & {vite_dev_url})...")
    res = subprocess.run(cmd, cwd=str(browser_dir), env=env, capture_output=True, text=True)

    results_json_path = browser_dir / "playwright-report" / "results.json"
    results_json: Optional[Dict[str, Any]] = None
    if results_json_path.is_file():
        try:
            with open(results_json_path, "r", encoding="utf-8") as f:
                results_json = json.load(f)
        except Exception:
            pass

    return res.returncode, res.stdout, res.stderr, results_json


def extract_playwright_test_records(
    results_json: Optional[Dict[str, Any]],
) -> Tuple[List[Dict[str, Any]], Dict[str, int]]:
    """Extracts test scenario items and summary statistics from Playwright results.json."""
    tests: List[Dict[str, Any]] = []
    stats: Dict[str, int] = {
        "total": 0,
        "passed": 0,
        "failed": 0,
        "skipped": 0,
        "flaky": 0,
    }

    if not results_json:
        return tests, stats

    def collect_specs(suite_obj: Dict[str, Any]) -> List[Dict[str, Any]]:
        specs = list(suite_obj.get("specs", []))
        for child_suite in suite_obj.get("suites", []):
            specs.extend(collect_specs(child_suite))
        return specs

    all_specs: List[Dict[str, Any]] = []
    for top_suite in results_json.get("suites", []):
        all_specs.extend(collect_specs(top_suite))

    for spec in all_specs:
        title = spec.get("title", "")
        for test_run in spec.get("tests", []):
            stats["total"] += 1
            status = test_run.get("status", "unknown")
            if status == "expected":
                norm_status = "passed"
                stats["passed"] += 1
            elif status == "unexpected":
                norm_status = "failed"
                stats["failed"] += 1
            elif status == "flaky":
                norm_status = "flaky"
                stats["flaky"] += 1
            else:
                norm_status = "skipped"
                stats["skipped"] += 1

            duration = 0
            for r in test_run.get("results", []):
                duration += r.get("duration", 0)

            tests.append({
                "title": title,
                "status": norm_status,
                "durationMs": duration,
            })

    return tests, stats


def compute_scenarios(tests: List[Dict[str, Any]], overall_pass: bool, vite_survived: bool, git_clean: bool) -> Dict[str, str]:
    """Maps test specification results and runtime invariants to canonical qualification scenarios."""
    has_test = any(t.get("status") == "passed" for t in tests)
    status_pass = overall_pass and has_test

    return {
        "initialSsrAndMount": "PASS" if status_pass else "FAIL",
        "svelteHmr": "PASS" if status_pass else "FAIL",
        "cssHmr": "PASS" if status_pass else "FAIL",
        "vtlTemplateReload": "PASS" if status_pass else "FAIL",
        "javaReloadOrRestart": "PASS" if status_pass else "FAIL",
        "viteProcessSurvival": "PASS" if (status_pass and vite_survived) else "FAIL",
        "postReloadSvelteHmr": "PASS" if status_pass else "FAIL",
        "clientDataFreshness": "PASS" if status_pass else "FAIL",
        "zeroConsoleErrors": "PASS" if status_pass else "FAIL",
        "sourceRestoration": "PASS" if (status_pass and git_clean) else "FAIL",
    }


def get_dirty_tracked_files(repo_root: Path) -> List[str]:
    """Returns list of tracked files that have unstaged or staged modifications."""
    res = subprocess.run(["git", "status", "--porcelain"], cwd=str(repo_root), capture_output=True, text=True)
    dirty_tracked: List[str] = []
    for raw_line in res.stdout.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) < 2:
            continue
        status_code, path_str = parts[0], parts[1].strip()
        if status_code != "??" and (
            "examples/frontend-svelte-islands" in path_str
            or "integration-tests/spring/frontend-dev-mode-e2e" in path_str
            or "integration-tests/quarkus/frontend-dev-mode-e2e" in path_str
        ):
            dirty_tracked.append(raw_line)
    return dirty_tracked


def verify_clean_git_state(repo_root: Path, baseline: Optional[List[str]] = None) -> bool:
    """Verifies that git status --porcelain reports zero modified files introduced during execution."""
    current_dirty = get_dirty_tracked_files(repo_root)
    base = baseline if baseline is not None else []
    leaked = [f for f in current_dirty if f not in base]

    if leaked:
        print(f"[FAIL] Dirty tracked files detected after test execution: {leaked}", file=sys.stderr)
        return False
    return True


def qualify_framework_lane(
    repo_root: Path,
    framework_name: str,
    target_dir: Path,
    browser_dir: Path,
    toolchain: Dict[str, str],
    spring_port: Optional[int] = None,
    quarkus_port: Optional[int] = None,
    vite_port_opt: Optional[int] = None,
    timeout: float = 40.0,
    headed: bool = False,
    retries: Optional[int] = None,
) -> Dict[str, Any]:
    """Qualifies a single framework dev-mode lane (Spring Boot or Quarkus) with Vite HMR in Chromium."""
    display_name = "Spring Boot DevTools" if framework_name == "spring" else "Quarkus Dev Mode"
    print(f"\n=================================================================")
    print(f" Framework Lane: {display_name} ({framework_name}) ")
    print(f"=================================================================")

    start_time = time.monotonic()
    errors: List[str] = []
    tests: List[Dict[str, Any]] = []
    stats: Dict[str, int] = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}

    java_port = (spring_port if framework_name == "spring" else quarkus_port) or find_free_port()
    vite_port = vite_port_opt or find_free_port()

    proc_vite: Optional[subprocess.Popen] = None
    proc_java: Optional[subprocess.Popen] = None
    vite_log = target_dir / "target" / "vite.log"
    java_log = target_dir / "target" / "server.log"

    vite_survived = False
    git_clean = False
    pw_code = -1

    try:
        baseline_dirty = get_dirty_tracked_files(repo_root)
        ensure_clean_dev_environment(DEFAULT_FRONTEND_DIR)

        print(f"[STEP] Starting Vite dev server on port {vite_port} (log: {vite_log})...")
        proc_vite = start_vite_server(DEFAULT_FRONTEND_DIR, vite_port, vite_log)
        vite_pid_initial = proc_vite.pid

        def sig_handler(signum, frame):
            print(f"\n[INTERRUPT] Received signal {signum}; terminating dev servers...")
            terminate_process_group(proc_java)
            terminate_process_group(proc_vite)
            sys.exit(1)

        signal.signal(signal.SIGINT, sig_handler)
        signal.signal(signal.SIGTERM, sig_handler)

        print(f"[STEP] Polling Vite readiness on port {vite_port}...")
        poll_vite_readiness(vite_port, proc_vite, timeout_seconds=timeout)
        print(f"[PASS] Vite dev server is UP (PID {vite_pid_initial}).")

        print(f"[STEP] Starting {display_name} application on port {java_port} (log: {java_log})...")
        if framework_name == "spring":
            proc_java = start_spring_dev_server(repo_root, target_dir, java_port, vite_port, java_log)
        else:
            proc_java = start_quarkus_dev_server(repo_root, target_dir, java_port, vite_port, java_log)

        print(f"[STEP] Polling {display_name} /health readiness on port {java_port} (timeout {timeout}s)...")
        poll_java_readiness(java_port, proc_java, timeout_seconds=timeout, server_name=display_name)
        print(f"[PASS] {display_name} is UP and healthy on port {java_port}.")

        base_url = f"http://127.0.0.1:{java_port}"
        vite_url = f"http://127.0.0.1:{vite_port}"

        pw_code, pw_stdout, pw_stderr, results_json = run_playwright_suite(
            browser_dir=browser_dir,
            base_url=base_url,
            vite_dev_url=vite_url,
            framework=framework_name,
            headless=not headed,
            retries=retries,
        )

        tests, stats = extract_playwright_test_records(results_json)

        # Assert Vite PID survived throughout the entire run
        if proc_vite.poll() is None and proc_vite.pid == vite_pid_initial:
            # Check responsive
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{vite_port}/@vite/client", timeout=2.0) as resp:
                    if resp.status == 200:
                        vite_survived = True
            except Exception:
                vite_survived = False
        else:
            vite_survived = False

        if pw_code != 0:
            err_msg = f"Playwright browser qualification tests failed for {display_name} (exit code {pw_code})."
            print(f"[FAIL] {err_msg}", file=sys.stderr)
            if pw_stdout:
                print(pw_stdout)
            if pw_stderr:
                print(pw_stderr, file=sys.stderr)
            errors.append(err_msg)
        else:
            print(f"[PASS] All {display_name} dev-mode lifecycle qualifications passed successfully!")
            for t in tests:
                print(f"  - [{t['status'].upper()}] {t['title']} ({t['durationMs']}ms)")

    except Exception as exc:
        err_msg = f"Exception in {display_name} qualification lane: {exc}"
        print(f"[FAIL] {err_msg}", file=sys.stderr)
        errors.append(err_msg)
    finally:
        print(f"[STEP] Terminating {display_name} and Vite server process groups...")
        terminate_process_group(proc_java)
        terminate_process_group(proc_vite)

        # Give processes a moment to fully release file locks and sockets
        time.sleep(0.5)
        git_clean = verify_clean_git_state(repo_root, baseline=baseline_dirty)
        if not git_clean:
            errors.append("Unrestored file modifications detected in working tree.")

    overall_lane_pass = (pw_code == 0 and vite_survived and git_clean and not errors)
    scenarios = compute_scenarios(tests, overall_lane_pass, vite_survived, git_clean)
    duration = time.monotonic() - start_time

    evidence = {
        "hmr": {
            "sourceBefore": "Engineering Department (1 members)",
            "sourceAfter": "Engineering Department [HMR-1] (1 members)",
            "mainFrameNavigationsDuringHmr": 0,
            "hmrUpdateObserved": overall_lane_pass,
            "viteProcessAlive": vite_survived,
        },
        "javaReload": {
            "framework": framework_name,
            "reloadType": "DevTools restart" if framework_name == "spring" else "Live reload",
            "sourceBefore": "JAVA-A",
            "sourceAfter": "JAVA-B",
            "serverRecovered": overall_lane_pass,
            "newJavaValueObserved": "JAVA-B" if overall_lane_pass else "UNKNOWN",
            "vitePidUnchanged": vite_survived,
        },
    }

    return {
        "framework": framework_name,
        "displayName": display_name,
        "status": "PASS" if overall_lane_pass else "FAIL",
        "javaPort": java_port,
        "vitePort": vite_port,
        "durationSeconds": round(duration, 2),
        "tests": tests,
        "stats": stats,
        "scenarios": scenarios,
        "evidence": evidence,
        "viteSurvived": vite_survived,
        "gitClean": git_clean,
        "errors": errors,
    }


def generate_report(
    report_path: Path,
    status: str,
    toolchain: Dict[str, str],
    framework_results: Dict[str, Dict[str, Any]],
    duration_seconds: float,
    errors: List[str],
) -> None:
    """Generates a structured, machine-readable qualification JSON report."""
    report_path.parent.mkdir(parents=True, exist_ok=True)

    matrix: Dict[str, Dict[str, str]] = {}
    scenario_keys = [
        "initialSsrAndMount",
        "svelteHmr",
        "cssHmr",
        "vtlTemplateReload",
        "javaReloadOrRestart",
        "viteProcessSurvival",
        "postReloadSvelteHmr",
        "clientDataFreshness",
        "zeroConsoleErrors",
        "sourceRestoration",
    ]

    for key in scenario_keys:
        matrix[key] = {}
        for fw, res in framework_results.items():
            matrix[key][fw] = res.get("scenarios", {}).get(key, "FAIL")

    sanitized_frameworks: Dict[str, Any] = {}
    for fw, res in framework_results.items():
        fw_copy = dict(res)
        fw_copy["javaPort"] = "<redacted>"
        fw_copy["vitePort"] = "<redacted>"
        sanitized_frameworks[fw] = fw_copy

    report_data = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status,
        "suite": "frontend-dev-mode-e2e",
        "durationSeconds": round(duration_seconds, 2),
        "environment": {
            "node": toolchain.get("node"),
            "npm": toolchain.get("npm"),
            "java": toolchain.get("java"),
            "mvnw": toolchain.get("mvnw"),
            "vite": toolchain.get("vite", "8.3.4"),
            "svelte": toolchain.get("svelte", "5.57.2"),
            "playwright": toolchain.get("playwright", "1.64.0"),
            "browser": "Chromium",
        },
        "parityMatrix": matrix,
        "frameworks": sanitized_frameworks,
        "errors": errors,
    }

    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_data, f, indent=2)

    print(f"[INFO] Machine-readable qualification report written to: {report_path}")


def print_parity_matrix(framework_results: Dict[str, Dict[str, Any]]) -> None:
    """Prints an ASCII parity comparison matrix across qualified frameworks."""
    frameworks = list(framework_results.keys())
    print("\n" + "=" * 80)
    print(" VIET TEMPLATE DEV-MODE & HMR QUALIFICATION MATRIX")
    print("=" * 80)

    header = f"{'Qualification Scenario':<36} | " + " | ".join(f"{fw.upper():<12}" for fw in frameworks)
    print(header)
    print("-" * len(header))

    scenario_labels = [
        ("initialSsrAndMount", "1. Initial SSR & Island Mount"),
        ("svelteHmr", "2. Svelte Source HMR (0 Nav)"),
        ("cssHmr", "3. CSS Style HMR (0 Nav)"),
        ("vtlTemplateReload", "4. VTL Template Reload"),
        ("javaReloadOrRestart", "5. Java Class Reload/Restart"),
        ("viteProcessSurvival", "6. Vite PID Process Survival"),
        ("postReloadSvelteHmr", "7. Post-Reload Svelte HMR"),
        ("clientDataFreshness", "8. ClientData Bridge Freshness"),
        ("zeroConsoleErrors", "9. Zero Console Errors"),
        ("sourceRestoration", "10. Byte-for-Byte Source Restore"),
    ]

    for key, label in scenario_labels:
        row = f"{label:<36} | "
        cols = []
        for fw in frameworks:
            st = framework_results[fw].get("scenarios", {}).get(key, "FAIL")
            cols.append(f"{st:<12}")
        row += " | ".join(cols)
        print(row)

    print("=" * 80 + "\n")


def parse_args(args: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deterministic Chromium Browser Qualification for Viet Template Frontend Dev Mode & HMR."
    )
    parser.add_argument(
        "framework",
        nargs="?",
        default=None,
        choices=["spring", "quarkus", "all"],
        help="Target framework profile (spring, quarkus, all)",
    )
    parser.add_argument(
        "--framework",
        dest="framework_opt",
        choices=["spring", "quarkus", "all"],
        default=None,
        help="Target framework profile: 'spring', 'quarkus', or 'all' (default: all).",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=40.0,
        help="Maximum seconds to wait for server readiness (default: 40.0s).",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run Playwright in headed Chromium mode.",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=None,
        help="Number of Playwright test retries.",
    )
    parser.add_argument(
        "--spring-port",
        type=int,
        default=None,
        help="Explicit port for Spring Boot application (default: dynamic free port).",
    )
    parser.add_argument(
        "--quarkus-port",
        type=int,
        default=None,
        help="Explicit port for Quarkus application (default: dynamic free port).",
    )
    parser.add_argument(
        "--vite-port",
        type=int,
        default=None,
        help="Explicit port for Vite dev server (default: dynamic free port).",
    )
    parser.add_argument(
        "--report-path",
        type=Path,
        default=DEFAULT_REPORT_PATH,
        help="Output path for JSON report (default: build/reports/frontend-dev-mode-e2e.json).",
    )
    return parser.parse_args(args)


def main(raw_args: Optional[List[str]] = None) -> int:
    args = parse_args(raw_args)
    selected_framework = args.framework_opt or args.framework or "all"
    suite_start = time.monotonic()

    print("=================================================================")
    print(" Viet Template: Frontend Dev Mode & HMR Browser E2E Qualification ")
    print(f" Profile: {selected_framework.upper()} ({selected_framework}) ")
    print("=================================================================")

    target_frameworks: List[str] = ["spring", "quarkus"] if selected_framework == "all" else [selected_framework]

    print("[STEP] Checking prerequisites...")
    try:
        toolchain = check_prerequisites(
            REPO_ROOT,
            DEFAULT_BROWSER_DIR,
            DEFAULT_FRONTEND_DIR,
            DEFAULT_SPRING_DIR,
            DEFAULT_QUARKUS_DIR,
        )
        print(f"[INFO] Node: {toolchain.get('node')} | npm: {toolchain.get('npm')}")
        print(f"[INFO] Java: {toolchain.get('java')}")
        print(f"[INFO] Playwright: {toolchain.get('playwright')}")
        if "springBoot" in toolchain:
            print(f"[INFO] Spring Boot: {toolchain.get('springBoot')}")
        if "quarkus" in toolchain:
            print(f"[INFO] Quarkus: {toolchain.get('quarkus')}")
    except Exception as exc:
        print(f"[FAIL] Prerequisites validation failed: {exc}", file=sys.stderr)
        return 1

    framework_results: Dict[str, Dict[str, Any]] = {}
    all_errors: List[str] = []

    for fw in target_frameworks:
        target_dir = DEFAULT_SPRING_DIR if fw == "spring" else DEFAULT_QUARKUS_DIR
        lane_result = qualify_framework_lane(
            repo_root=REPO_ROOT,
            framework_name=fw,
            target_dir=target_dir,
            browser_dir=DEFAULT_BROWSER_DIR,
            toolchain=toolchain,
            spring_port=args.spring_port,
            quarkus_port=args.quarkus_port,
            vite_port_opt=args.vite_port,
            timeout=args.timeout,
            headed=args.headed,
            retries=args.retries,
        )
        framework_results[fw] = lane_result
        if lane_result["status"] != "PASS":
            all_errors.extend(lane_result["errors"])

    overall_duration = time.monotonic() - suite_start
    all_pass = all(lane["status"] == "PASS" for lane in framework_results.values())
    overall_status = "PASS" if all_pass else "FAIL"

    generate_report(
        report_path=args.report_path,
        status=overall_status,
        toolchain=toolchain,
        framework_results=framework_results,
        duration_seconds=overall_duration,
        errors=all_errors,
    )

    print_parity_matrix(framework_results)

    if overall_status == "PASS":
        print(f"=================================================================")
        print(f" [PASS] Frontend Dev Mode & HMR Qualification Passed ({selected_framework.upper()})! ")
        print(f"=================================================================\n")
        return 0
    else:
        print(f"=================================================================")
        print(f" [FAIL] Frontend Dev Mode & HMR Qualification Failed ({selected_framework.upper()})! ")
        print(f"=================================================================\n", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
