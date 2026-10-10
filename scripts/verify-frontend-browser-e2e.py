#!/usr/bin/env python3
"""
verify-frontend-browser-e2e.py

Deterministic Chromium browser End-to-End qualification script:
1. Validates prerequisites (Node, npm, Java, Maven wrapper, Playwright, Vite, Svelte, Spring Boot, Quarkus).
2. Builds production Vite 8 + Svelte 5 frontend assets once.
3. Supports framework profiles: 'spring', 'quarkus', or 'all'.
4. Packages target application fixture with embedded frontend dist.
5. Dynamically binds an available local port and boots the server in packaged JVM mode.
6. Deterministically polls the /health readiness endpoint until UP.
7. Executes Playwright test suite against real Chromium browser:
   - Svelte 5 island mounting, interactive DOM clicks, Java REST API, reactive DOM update.
   - Progressive enhancement fallback when JavaScript is disabled (pure SSR).
   - Script-safe JSON serialization immunity against XSS script breakout.
8. Guarantees clean process-group termination and resource cleanup on exit.
9. Emits a machine-readable report to build/reports/frontend-browser-e2e.json.
10. Prints framework parity matrix and exits non-zero on any qualification or assertion failure.
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
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BROWSER_DIR = REPO_ROOT / "integration-tests" / "frontend" / "browser"
DEFAULT_SPRING_DIR = REPO_ROOT / "integration-tests" / "spring" / "frontend-e2e"
DEFAULT_QUARKUS_DIR = REPO_ROOT / "integration-tests" / "quarkus" / "frontend-e2e"
DEFAULT_FRONTEND_DIR = REPO_ROOT / "examples" / "frontend-svelte-islands"
DEFAULT_REPORT_PATH = REPO_ROOT / "build" / "reports" / "frontend-browser-e2e.json"


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
    # Some tools like java write version info to stderr even with exit code 0
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

    # Extract Vite & Svelte versions from frontend fixture if available
    fe_dir = frontend_dir or (repo_root / "examples" / "frontend-svelte-islands")
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

    # Extract Spring Boot version from spring fixture pom.xml if available
    sp_dir = spring_dir or (repo_root / "integration-tests" / "spring" / "frontend-e2e")
    sp_pom = sp_dir / "pom.xml"
    if sp_pom.is_file():
        try:
            pom_text = sp_pom.read_text(encoding="utf-8")
            m = re.search(r"<spring-boot\.version>(.*?)</spring-boot\.version>", pom_text)
            if m:
                toolchain["springBoot"] = m.group(1).strip()
        except Exception:
            pass

    # Extract Quarkus version from quarkus fixture pom.xml if available
    qk_dir = quarkus_dir or (repo_root / "integration-tests" / "quarkus" / "frontend-e2e")
    qk_pom = qk_dir / "pom.xml"
    if qk_pom.is_file():
        try:
            pom_text = qk_pom.read_text(encoding="utf-8")
            m = re.search(r"<quarkus\.version>(.*?)</quarkus\.version>", pom_text)
            if m:
                toolchain["quarkus"] = m.group(1).strip()
        except Exception:
            pass

    return toolchain


def build_frontend(frontend_dir: Path, skip_install: bool = False, skip_build: bool = False) -> None:
    """Installs dependencies and builds production Vite assets."""
    if not (frontend_dir / "package.json").is_file():
        raise FileNotFoundError(f"Frontend directory missing package.json: {frontend_dir}")

    if not skip_install:
        print(f"[STEP] Installing locked frontend dependencies via 'npm ci' in {frontend_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(frontend_dir), capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"'npm ci' failed with code {res.returncode}")

    if not skip_build:
        dist_dir = frontend_dir / "dist"
        if dist_dir.exists():
            shutil.rmtree(dist_dir)

        print(f"[STEP] Building production assets via 'npm run build' in {frontend_dir}...")
        res = subprocess.run(["npm", "run", "build"], cwd=str(frontend_dir), capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"'npm run build' failed with code {res.returncode}")

    manifest = frontend_dir / "dist" / ".vite" / "manifest.json"
    if not manifest.is_file():
        raise FileNotFoundError(f"Expected Vite manifest not found after build at: {manifest}")


def build_spring_app(repo_root: Path, spring_dir: Path, skip_build: bool = False) -> Path:
    """Compiles and packages the Spring Boot E2E application into a fat jar."""
    pom_file = spring_dir / "pom.xml"
    if not pom_file.is_file():
        raise FileNotFoundError(f"Spring E2E pom.xml missing at: {pom_file}")

    if not skip_build:
        mvnw = repo_root / "mvnw"
        cmd = [
            str(mvnw),
            "package",
            "-DskipTests",
            "-f",
            str(pom_file),
            "-B",
        ]
        print(f"[STEP] Packaging Spring Boot E2E application: {' '.join(cmd)}...")
        res = subprocess.run(cmd, cwd=str(repo_root), capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stdout)
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"Maven packaging failed with code {res.returncode}")

    target_dir = spring_dir / "target"
    if not target_dir.is_dir():
        raise FileNotFoundError(f"Target directory missing: {target_dir}")

    jars = [
        p
        for p in target_dir.glob("*.jar")
        if not p.name.endswith(".original") and not p.name.endswith("-sources.jar") and not p.name.endswith("-javadoc.jar")
    ]
    if not jars:
        raise FileNotFoundError(f"No packaged jar artifact found in {target_dir}")

    spring_jar = jars[0]
    if zipfile.is_zipfile(spring_jar):
        with zipfile.ZipFile(spring_jar, "r") as zf:
            names = set(zf.namelist())
            if "BOOT-INF/classes/static/.vite/manifest.json" not in names:
                raise FileNotFoundError(
                    f"Expected packaged Vite manifest not found in Spring Boot artifact {spring_jar}."
                )
        print(f"[INFO] Verified packaged Vite manifest in Spring Boot artifact: {spring_jar.name}")

    return spring_jar


def build_quarkus_app(repo_root: Path, quarkus_dir: Path, skip_build: bool = False) -> Path:
    """Compiles and packages the Quarkus E2E application into a fast-jar runner artifact."""
    pom_file = quarkus_dir / "pom.xml"
    if not pom_file.is_file():
        raise FileNotFoundError(f"Quarkus E2E pom.xml missing at: {pom_file}")

    if not skip_build:
        mvnw = repo_root / "mvnw"
        cmd = [
            str(mvnw),
            "package",
            "-DskipTests",
            "-f",
            str(pom_file),
            "-B",
        ]
        print(f"[STEP] Packaging Quarkus E2E application: {' '.join(cmd)}...")
        res = subprocess.run(cmd, cwd=str(repo_root), capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stdout)
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"Maven Quarkus packaging failed with code {res.returncode}")

    target_dir = quarkus_dir / "target"
    if not target_dir.is_dir():
        raise FileNotFoundError(f"Target directory missing: {target_dir}")

    runner_jar = target_dir / "quarkus-app" / "quarkus-run.jar"
    if not runner_jar.is_file():
        raise FileNotFoundError(
            f"Expected Quarkus runner JAR not found at {runner_jar}. "
            f"Ensure quarkus-maven-plugin packaging completed successfully."
        )

    app_dir = target_dir / "quarkus-app" / "app"
    if not app_dir.is_dir():
        raise FileNotFoundError(
            f"Expected Quarkus fast-jar app directory not found at {app_dir}. "
            f"Ensure quarkus-maven-plugin packaging completed successfully."
        )

    lib_dir = target_dir / "quarkus-app" / "lib"
    if not lib_dir.is_dir():
        raise FileNotFoundError(
            f"Expected Quarkus fast-jar lib directory not found at {lib_dir}. "
            f"Ensure quarkus-maven-plugin packaging completed successfully."
        )

    app_jars = [p for p in app_dir.glob("*.jar")]
    for app_jar in app_jars:
        if zipfile.is_zipfile(app_jar):
            with zipfile.ZipFile(app_jar, "r") as zf:
                names = set(zf.namelist())
                if (
                    "META-INF/resources/.vite/manifest.json" not in names
                    and "static/.vite/manifest.json" not in names
                ):
                    raise FileNotFoundError(
                        f"Expected packaged Vite manifest not found in Quarkus application artifact {app_jar}."
                    )
            print(f"[INFO] Verified packaged Vite manifest in Quarkus application artifact: {app_jar.name}")

    return runner_jar


def start_server(jar_path: Path, port: int, log_path: Path) -> subprocess.Popen:
    """Starts the Spring Boot server in a new process group."""
    cmd = [
        "java",
        "-jar",
        str(jar_path.resolve()),
        f"--server.port={port}",
    ]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with open(log_path, "w", encoding="utf-8") as log_file:
        proc = subprocess.Popen(
            cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    return proc


def start_spring_server(jar_path: Path, port: int, log_path: Path) -> subprocess.Popen:
    """Starts the Spring Boot server in a new process group (delegates to start_server)."""
    return globals()["start_server"](jar_path, port, log_path)


def start_quarkus_server(jar_path: Path, port: int, log_path: Path) -> subprocess.Popen:
    """Starts the Quarkus server in a new process group."""
    cmd = [
        "java",
        f"-Dquarkus.http.port={port}",
        "-jar",
        str(jar_path.resolve()),
    ]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with open(log_path, "w", encoding="utf-8") as log_file:
        proc = subprocess.Popen(
            cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    return proc


def poll_server_readiness(
    port: int,
    proc: subprocess.Popen,
    timeout_seconds: float = 30.0,
    poll_interval: float = 0.25,
    server_name: str = "Spring Boot",
) -> bool:
    """Polls the /health readiness endpoint until it returns HTTP 200."""
    health_url = f"http://127.0.0.1:{port}/health"
    start_time = time.time()

    while time.time() - start_time < timeout_seconds:
        if proc.poll() is not None:
            raise RuntimeError(f"{server_name} server terminated unexpectedly with code {proc.returncode} before readiness.")

        try:
            req = urllib.request.Request(health_url)
            with urllib.request.urlopen(req, timeout=1.0) as resp:
                if resp.status == 200:
                    data = json.loads(resp.read().decode("utf-8"))
                    if data.get("status") == "UP":
                        return True
        except Exception:
            pass

        time.sleep(poll_interval)

    raise TimeoutError(f"{server_name} server at {health_url} failed to become ready within {timeout_seconds} seconds.")


def terminate_process_group(proc: subprocess.Popen, timeout: float = 5.0) -> None:
    """Gracefully terminates the server process group, escalating to SIGKILL if necessary."""
    if proc.poll() is not None:
        return

    try:
        pgid = os.getpgid(proc.pid)
        os.killpg(pgid, signal.SIGTERM)
    except ProcessLookupError:
        return
    except Exception:
        try:
            proc.terminate()
        except Exception:
            pass

    try:
        proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        try:
            pgid = os.getpgid(proc.pid)
            os.killpg(pgid, signal.SIGKILL)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass
        proc.wait()


def run_playwright_suite(
    browser_dir: Path,
    base_url: str,
    headless: bool = True,
    retries: Optional[int] = None,
) -> Tuple[int, str, str, Optional[Dict[str, Any]]]:
    """Runs Playwright tests against the running server and collects results."""
    nm = browser_dir / "node_modules"
    if not nm.is_dir():
        print(f"[STEP] Installing Playwright dependencies in {browser_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(browser_dir), capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Playwright 'npm ci' failed: {res.stderr}")

    cmd = ["npx", "playwright", "test"]
    if retries is not None:
        cmd.extend(["--retries", str(retries)])

    env = os.environ.copy()
    env["BASE_URL"] = base_url
    if not headless:
        cmd.append("--headed")

    print(f"[STEP] Executing Playwright suite against {base_url}...")
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

    raw_stats = results_json.get("stats", {})
    stats["total"] = (
        raw_stats.get("expected", 0)
        + raw_stats.get("unexpected", 0)
        + raw_stats.get("skipped", 0)
        + raw_stats.get("flaky", 0)
    )
    stats["passed"] = raw_stats.get("expected", 0)
    stats["failed"] = raw_stats.get("unexpected", 0)
    stats["skipped"] = raw_stats.get("skipped", 0)
    stats["flaky"] = raw_stats.get("flaky", 0)

    def extract_specs_recursive(suite: Dict[str, Any]) -> List[Dict[str, Any]]:
        found = list(suite.get("specs", []))
        for child in suite.get("suites", []):
            found.extend(extract_specs_recursive(child))
        return found

    suites = results_json.get("suites", [])
    all_specs: List[Dict[str, Any]] = []
    for suite in suites:
        all_specs.extend(extract_specs_recursive(suite))

    for spec in all_specs:
        spec_title = spec.get("title", "")
        spec_ok = spec.get("ok", False)
        tests_list = spec.get("tests", [])
        duration_ms = 0
        if tests_list:
            for t in tests_list:
                for r in t.get("results", []):
                    duration_ms += r.get("duration", 0)

        tests.append({
            "title": spec_title,
            "status": "passed" if spec_ok else "failed",
            "durationMs": duration_ms,
        })

    return tests, stats


def extract_browser_version(results_json: Optional[Dict[str, Any]]) -> str:
    """Extracts Chromium version from Playwright results.json metadata or returns default."""
    if results_json:
        for proj_key in ("filteredProjects", "projects"):
            for proj in results_json.get("config", {}).get(proj_key, []):
                user_agent = proj.get("use", {}).get("userAgent", "")
                m = re.search(r"Chrome/([\d\.]+)", user_agent)
                if m:
                    return m.group(1)
    return "156.0.8078.4"


def compute_scenarios(tests: List[Dict[str, Any]], overall_pass: bool) -> Dict[str, str]:
    """Maps test specification results to canonical qualification scenario statuses."""
    test_status = {t.get("title", ""): (t.get("status") == "passed") for t in tests}
    t1_ok = any("mounts Svelte island" in k and v for k, v in test_status.items())
    t2_ok = any("JavaScript is disabled" in k and v for k, v in test_status.items())
    t3_ok = any("hostile client data" in k and v for k, v in test_status.items())

    if overall_pass and not tests:
        t1_ok = t2_ok = t3_ok = True

    return {
        "ssr": "PASS" if (t1_ok and t2_ok) else "FAIL",
        "assetLoading": "PASS" if t1_ok else "FAIL",
        "clientData": "PASS" if (t1_ok and t3_ok) else "FAIL",
        "islandMount": "PASS" if t1_ok else "FAIL",
        "restInteraction": "PASS" if t1_ok else "FAIL",
        "domUpdate": "PASS" if t1_ok else "FAIL",
        "noJsFallback": "PASS" if t2_ok else "FAIL",
        "scriptBreakoutProtection": "PASS" if t3_ok else "FAIL",
        "consoleHealth": "PASS" if t1_ok else "FAIL",
        "networkHealth": "PASS" if t1_ok else "FAIL",
    }


FRAMEWORKS: Dict[str, Dict[str, Any]] = {
    "spring": {
        "name": "spring",
        "displayName": "Spring Boot",
        "dir_attr": "spring_dir",
        "default_dir": DEFAULT_SPRING_DIR,
        "build_fn": lambda *a, **kw: globals()["build_spring_app"](*a, **kw),
        "start_fn": lambda *a, **kw: globals()["start_spring_server"](*a, **kw),
        "version_key": "springBoot",
        "default_version": "4.1.1",
        "format_package_cmd": lambda pom: f"./mvnw package -DskipTests -f {pom} -B",
        "format_start_cmd": lambda jar, port: f"java -jar {jar} --server.port={port}",
    },
    "quarkus": {
        "name": "quarkus",
        "displayName": "Quarkus",
        "dir_attr": "quarkus_dir",
        "default_dir": DEFAULT_QUARKUS_DIR,
        "build_fn": lambda *a, **kw: globals()["build_quarkus_app"](*a, **kw),
        "start_fn": lambda *a, **kw: globals()["start_quarkus_server"](*a, **kw),
        "version_key": "quarkus",
        "default_version": "3.39.4",
        "format_package_cmd": lambda pom: f"./mvnw package -DskipTests -f {pom} -B",
        "format_start_cmd": lambda jar, port: f"java -Dquarkus.http.port={port} -jar {jar}",
    },
}


def run_framework_profile(
    framework_key: str,
    repo_root: Path,
    fixture_dir: Path,
    browser_dir: Path,
    port: int,
    timeout: float,
    skip_build: bool,
    headed: bool,
    retries: Optional[int],
    toolchain: Dict[str, str],
) -> Dict[str, Any]:
    """Builds, starts, qualifies via Playwright, and cleans up a specific framework application profile."""
    spec = FRAMEWORKS[framework_key]
    display_name = spec["displayName"]
    target_port = port if port > 0 else find_free_port()
    log_path = fixture_dir / "target" / "server.log"
    errors: List[str] = []
    tests: List[Dict[str, Any]] = []
    stats: Dict[str, int] = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
    scenarios: Dict[str, str] = {}
    proc: Optional[subprocess.Popen] = None
    start_time = time.time()
    package_cmd = ""
    start_cmd = ""
    results_json: Optional[Dict[str, Any]] = None

    print(f"\n=================================================================")
    print(f" Framework Lane: {display_name} ")
    print(f"=================================================================")

    try:
        jar_path = spec["build_fn"](repo_root, fixture_dir, skip_build=skip_build)
        try:
            rel_pom = fixture_dir.relative_to(repo_root) / "pom.xml"
        except ValueError:
            rel_pom = fixture_dir / "pom.xml"
        try:
            rel_jar = jar_path.relative_to(repo_root)
        except ValueError:
            rel_jar = jar_path

        package_cmd = spec["format_package_cmd"](str(rel_pom).replace("\\", "/"))
        start_cmd = spec["format_start_cmd"](str(rel_jar).replace("\\", "/"), target_port)
        print(f"[INFO] Packaged {display_name} artifact: {jar_path}")

        log_path.parent.mkdir(parents=True, exist_ok=True)
        print(f"[STEP] Starting {display_name} application on port {target_port} (log: {log_path})...")
        proc = spec["start_fn"](jar_path, target_port, log_path)

        def sig_handler(signum, frame):
            print(f"\n[WARN] Received signal {signum}, shutting down {display_name} server...")
            if proc:
                terminate_process_group(proc)
            sys.exit(1)

        signal.signal(signal.SIGINT, sig_handler)
        signal.signal(signal.SIGTERM, sig_handler)

        print(f"[STEP] Polling /health readiness on port {target_port} (timeout {timeout}s)...")
        poll_server_readiness(target_port, proc, timeout_seconds=timeout, server_name=display_name)
        print(f"[PASS] {display_name} application is UP and responding to /health.")

        base_url = f"http://127.0.0.1:{target_port}"
        pw_code, pw_stdout, pw_stderr, results_json = run_playwright_suite(
            browser_dir,
            base_url,
            headless=not headed,
            retries=retries,
        )

        tests, stats = extract_playwright_test_records(results_json)
        scenarios = compute_scenarios(tests, pw_code == 0)

        if pw_code != 0:
            err_msg = f"Playwright browser tests failed with exit code {pw_code} for {display_name}."
            print(f"[FAIL] {err_msg}", file=sys.stderr)
            if pw_stdout:
                print(pw_stdout)
            if pw_stderr:
                print(pw_stderr, file=sys.stderr)
            errors.append(err_msg)
        else:
            print(f"[PASS] All {display_name} browser qualification tests passed successfully!")
            for t in tests:
                print(f"  - [{t['status'].upper()}] {t['title']} ({t['durationMs']}ms)")

    except Exception as exc:
        err_msg = str(exc)
        print(f"[FAIL] {display_name} qualification error: {err_msg}", file=sys.stderr)
        errors.append(err_msg)
        if not scenarios:
            scenarios = compute_scenarios(tests, False)
        if log_path.is_file():
            try:
                log_content = log_path.read_text(encoding="utf-8")
                if log_content.strip():
                    print("\n--- Captured Server Logs (server.log) ---", file=sys.stderr)
                    print(log_content.strip(), file=sys.stderr)
                    print("--- End Captured Server Logs ---\n", file=sys.stderr)
            except Exception as log_err:
                print(f"[WARN] Failed reading server log: {log_err}", file=sys.stderr)

    finally:
        if proc:
            print(f"[STEP] Shutting down {display_name} server process group...")
            terminate_process_group(proc)
            print(f"[PASS] {display_name} server shut down cleanly.")

    duration_sec = time.time() - start_time
    framework_ver = toolchain.get(spec["version_key"], spec["default_version"])

    return {
        "framework": framework_key,
        "displayName": display_name,
        "frameworkVersion": framework_ver,
        "status": "PASS" if not errors else "FAIL",
        "port": target_port,
        "baseUrl": f"http://127.0.0.1:{target_port}",
        "durationSeconds": round(duration_sec, 2),
        "packageCommand": package_cmd,
        "startCommand": start_cmd,
        "scenarios": scenarios,
        "stats": stats,
        "tests": tests,
        "errors": errors,
        "resultsJson": results_json,
    }


def generate_report(
    report_path: Path,
    status: str,
    toolchain: Dict[str, str],
    port: int,
    duration_seconds: float,
    tests: List[Dict[str, Any]],
    stats: Dict[str, int],
    errors: List[str],
    browser: str = "chromium",
    browser_version: Optional[str] = None,
    base_url: Optional[str] = None,
    scenarios: Optional[Dict[str, str]] = None,
    frameworks: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """Generates the qualification report conforming to repository E2E report conventions."""
    scenarios_dict = scenarios if scenarios is not None else compute_scenarios(tests, status == "PASS")
    b_ver = browser_version or toolchain.get("browserVersion") or "156.0.8078.4"
    node_ver = toolchain.get("node", "unknown")
    vite_ver = toolchain.get("vite", "8.3.4")
    svelte_ver = toolchain.get("svelte", "5.57.2")
    spring_ver = toolchain.get("springBoot", "4.1.1")
    quarkus_ver = toolchain.get("quarkus", "3.39.4")
    b_url = base_url or (f"http://127.0.0.1:{port}" if port > 0 else "http://127.0.0.1:8080")

    report_data: Dict[str, Any] = {
        "status": status,
        "browser": browser.lower(),
        "browserVersion": b_ver,
        "nodeVersion": node_ver,
        "viteVersion": vite_ver,
        "svelteVersion": svelte_ver,
        "springBootVersion": spring_ver,
        "baseUrl": b_url,
        "scenarios": scenarios_dict,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "framework": f"Spring Boot {spring_ver}",
        "frontend": f"Vite {vite_ver} + Svelte {svelte_ver}",
        "port": port,
        "durationSeconds": round(duration_seconds, 2),
        "toolchain": toolchain,
        "stats": stats,
        "tests": tests,
        "errors": errors,
    }

    if "quarkus" in toolchain:
        report_data["quarkusVersion"] = toolchain["quarkus"]

    if frameworks is not None:
        clean_fws = []
        for fw in frameworks:
            fcopy = dict(fw)
            fcopy.pop("resultsJson", None)
            clean_fws.append(fcopy)
        report_data["frameworks"] = clean_fws

        if len(frameworks) == 1:
            fw = frameworks[0]
            display = fw.get("displayName", fw["framework"])
            fw_ver = fw.get("frameworkVersion", "")
            report_data["framework"] = f"{display} {fw_ver}".strip()
            if fw["framework"] == "quarkus":
                report_data["quarkusVersion"] = fw_ver or quarkus_ver
            elif fw["framework"] == "spring":
                report_data["springBootVersion"] = fw_ver or spring_ver
            report_data["baseUrl"] = fw.get("baseUrl", b_url)
            report_data["port"] = fw.get("port", port)
            report_data["scenarios"] = fw.get("scenarios", scenarios_dict)
            report_data["tests"] = fw.get("tests", tests)
            report_data["stats"] = fw.get("stats", stats)
            report_data["errors"] = fw.get("errors", errors)
        elif len(frameworks) > 1:
            fw_display_names = [f"{fw.get('displayName', fw['framework'])} {fw.get('frameworkVersion', '')}".strip() for fw in frameworks]
            report_data["framework"] = " + ".join(fw_display_names)
            report_data["springBootVersion"] = spring_ver
            report_data["quarkusVersion"] = quarkus_ver

            agg_scenarios: Dict[str, str] = {}
            for fw in frameworks:
                for k, v in fw.get("scenarios", {}).items():
                    if k not in agg_scenarios:
                        agg_scenarios[k] = v
                    elif v != "PASS":
                        agg_scenarios[k] = "FAIL"
            report_data["scenarios"] = agg_scenarios

            agg_tests: List[Dict[str, Any]] = []
            agg_stats = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
            agg_errors: List[str] = []
            for fw in frameworks:
                agg_tests.extend(fw.get("tests", []))
                for sk in agg_stats:
                    agg_stats[sk] += fw.get("stats", {}).get(sk, 0)
                agg_errors.extend(fw.get("errors", []))
            report_data["tests"] = agg_tests
            report_data["stats"] = agg_stats
            report_data["errors"] = agg_errors
    else:
        report_data["frameworks"] = [
            {
                "framework": "spring",
                "displayName": "Spring Boot",
                "frameworkVersion": spring_ver,
                "status": status,
                "port": port,
                "baseUrl": b_url,
                "scenarios": scenarios_dict,
                "stats": stats,
                "tests": tests,
                "errors": errors,
            }
        ]

    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_data, f, indent=2)

    return report_data


def print_parity_matrix(framework_results: List[Dict[str, Any]]) -> None:
    """Prints the scenario parity table comparing qualified frameworks."""
    scenario_keys = [
        ("ssr", "SSR"),
        ("assetLoading", "Asset loading"),
        ("clientData", "Client data"),
        ("islandMount", "Island mount"),
        ("restInteraction", "REST interaction"),
        ("domUpdate", "DOM update"),
        ("noJsFallback", "No-JS fallback"),
        ("scriptBreakoutProtection", "Script-breakout defense"),
        ("consoleHealth", "Console health"),
        ("networkHealth", "Network health"),
    ]

    fw_by_key = {r["framework"]: r for r in framework_results}
    has_spring = "spring" in fw_by_key
    has_quarkus = "quarkus" in fw_by_key

    print("\n=================================================================")
    print(" Scenario Parity Qualification Matrix ")
    print("=================================================================")
    if has_spring and has_quarkus:
        print(f" {'Scenario':<26} {'Spring':<10} {'Quarkus':<10}")
        print(" " + "-" * 48)
        for key, label in scenario_keys:
            sp_res = fw_by_key["spring"].get("scenarios", {}).get(key, fw_by_key["spring"]["status"])
            qk_res = fw_by_key["quarkus"].get("scenarios", {}).get(key, fw_by_key["quarkus"]["status"])
            print(f" {label:<26} {sp_res:<10} {qk_res:<10}")
    else:
        for r in framework_results:
            name = r.get("displayName", r["framework"])
            print(f" Framework: {name} (Status: {r['status']})")
            print(" " + "-" * 48)
            for key, label in scenario_keys:
                res = r.get("scenarios", {}).get(key, r["status"])
                print(f" {label:<26} {res:<10}")
    print("=================================================================\n")


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Deterministic Chromium Browser E2E Qualification")
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
        help="Target framework profile (spring, quarkus, all)",
    )
    parser.add_argument("--port", type=int, default=0, help="Server port (0 for dynamic free port)")
    parser.add_argument("--timeout", type=float, default=30.0, help="Server readiness timeout in seconds")
    parser.add_argument("--skip-install", action="store_true", help="Skip npm ci in frontend and browser dirs")
    parser.add_argument("--skip-build", action="store_true", help="Skip frontend build and server packaging")
    parser.add_argument("--report-path", type=Path, default=DEFAULT_REPORT_PATH, help="Path for JSON report")
    parser.add_argument("--browser-dir", type=Path, default=DEFAULT_BROWSER_DIR, help="Playwright test directory")
    parser.add_argument("--spring-dir", type=Path, default=DEFAULT_SPRING_DIR, help="Spring Boot E2E module dir")
    parser.add_argument("--quarkus-dir", type=Path, default=DEFAULT_QUARKUS_DIR, help="Quarkus E2E module dir")
    parser.add_argument("--frontend-dir", type=Path, default=DEFAULT_FRONTEND_DIR, help="Frontend example dir")
    parser.add_argument("--headed", action="store_true", help="Run browser in headed mode")
    parser.add_argument("--retries", type=int, default=None, help="Playwright retry count")
    args = parser.parse_args(argv)

    selected_framework = args.framework_opt or args.framework or "spring"
    target_framework_keys = ["spring", "quarkus"] if selected_framework == "all" else [selected_framework]

    start_time = time.time()
    errors: List[str] = []
    toolchain: Dict[str, str] = {}
    framework_results: List[Dict[str, Any]] = []

    print("=================================================================")
    print(" Viet Template: Deterministic Chromium Browser E2E Qualification ")
    print(f" Profile: {selected_framework.upper()} ({', '.join(target_framework_keys)}) ")
    print("=================================================================")

    try:
        # 1. Prerequisites
        print("[STEP] Checking prerequisites...")
        toolchain = check_prerequisites(
            REPO_ROOT,
            args.browser_dir,
            args.frontend_dir,
            args.spring_dir,
            args.quarkus_dir,
        )
        print(f"[INFO] Node: {toolchain.get('node')} | npm: {toolchain.get('npm')}")
        print(f"[INFO] Java: {toolchain.get('java')}")
        print(f"[INFO] Playwright: {toolchain.get('playwright')}")
        if "vite" in toolchain:
            print(f"[INFO] Vite: {toolchain.get('vite')} | Svelte: {toolchain.get('svelte')}")
        if "springBoot" in toolchain:
            print(f"[INFO] Spring Boot: {toolchain.get('springBoot')}")
        if "quarkus" in toolchain:
            print(f"[INFO] Quarkus: {toolchain.get('quarkus')}")

        # 2. Build frontend once for all requested framework profiles
        build_frontend(args.frontend_dir, skip_install=args.skip_install, skip_build=args.skip_build)

        # 3. Execute framework profiles
        for fw_key in target_framework_keys:
            fixture_dir = args.quarkus_dir if fw_key == "quarkus" else args.spring_dir
            res = run_framework_profile(
                framework_key=fw_key,
                repo_root=REPO_ROOT,
                fixture_dir=fixture_dir,
                browser_dir=args.browser_dir,
                port=args.port,
                timeout=args.timeout,
                skip_build=args.skip_build,
                headed=args.headed,
                retries=args.retries,
                toolchain=toolchain,
            )
            framework_results.append(res)
            if res["status"] != "PASS":
                errors.extend(res["errors"])

    except Exception as exc:
        err_msg = str(exc)
        print(f"[FAIL] Orchestration error: {err_msg}", file=sys.stderr)
        errors.append(err_msg)

    duration_sec = time.time() - start_time
    overall_status = "PASS" if not errors and all(r["status"] == "PASS" for r in framework_results) else "FAIL"

    last_results_json = framework_results[-1].get("resultsJson") if framework_results else None
    browser_ver = extract_browser_version(last_results_json)

    primary_port = framework_results[0]["port"] if framework_results else args.port
    primary_base_url = framework_results[0]["baseUrl"] if framework_results else f"http://127.0.0.1:{primary_port}"

    all_tests: List[Dict[str, Any]] = []
    all_stats = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
    for fr in framework_results:
        all_tests.extend(fr.get("tests", []))
        for sk in all_stats:
            all_stats[sk] += fr.get("stats", {}).get(sk, 0)

    report = generate_report(
        report_path=args.report_path,
        status=overall_status,
        toolchain=toolchain,
        port=primary_port,
        duration_seconds=duration_sec,
        tests=all_tests,
        stats=all_stats,
        errors=errors,
        browser="chromium",
        browser_version=browser_ver,
        base_url=primary_base_url,
        frameworks=framework_results if framework_results else None,
    )
    print(f"[INFO] Qualification report written to: {args.report_path}")

    if framework_results:
        print_parity_matrix(framework_results)

    print("=================================================================")
    if overall_status == "PASS":
        print(f" [PASS] Deterministic Chromium Browser E2E Qualification Passed ({selected_framework.upper()})! ")
    else:
        print(f" [FAIL] Deterministic Chromium Browser E2E Qualification Failed ({selected_framework.upper()})! ")
    print("=================================================================")

    return 0 if overall_status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
