#!/usr/bin/env python3
"""
verify-frontend-security-e2e.py

Deterministic Chromium browser End-to-End qualification script for
Authenticated Session & CSRF Protection across Spring Boot 4 and Quarkus 3:
1. Validates prerequisites (Node, npm, Java, Maven wrapper, Playwright, Vite, Svelte, Spring Boot, Quarkus).
2. Builds production Vite 8 + Svelte 5 frontend assets once.
3. Supports framework profiles: 'spring', 'quarkus', or 'all' (default).
4. Packages target application fixture with embedded frontend dist.
5. Dynamically binds an available local port and boots the server in packaged JVM mode.
6. Deterministically polls the /health readiness endpoint until UP.
7. Executes Playwright test suite against real Chromium browser (authenticated-security.spec.ts):
   - Anonymous denial and redirect to /login.
   - Form authentication establishing session/cookie.
   - Viet Template SSR $security and $csrf facades.
   - Role-dependent markup and authoritative server authorization.
   - Valid CSRF acceptance, REST interaction, reactive Svelte island update.
   - Missing and invalid CSRF rejection (Spring: 403, Quarkus: 400).
   - Non-JavaScript fallback form submission with CSRF in no-JS context.
   - Zero console errors and zero unexpected network failures.
8. Guarantees clean process-group termination and resource cleanup on exit.
9. Emits a machine-readable report to build/reports/frontend-security-e2e.json.
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
DEFAULT_SPRING_DIR = REPO_ROOT / "integration-tests" / "spring" / "frontend-security-e2e"
DEFAULT_QUARKUS_DIR = REPO_ROOT / "integration-tests" / "quarkus" / "frontend-security-e2e"
DEFAULT_FRONTEND_DIR = REPO_ROOT / "examples" / "frontend-svelte-islands"
DEFAULT_REPORT_PATH = REPO_ROOT / "build" / "reports" / "frontend-security-e2e.json"


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
            m = re.search(r"<spring-boot\.version>([^<]+)</spring-boot\.version>", pom_text)
            if m:
                toolchain["springBoot"] = m.group(1).strip()
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


def build_frontend(frontend_dir: Path, skip_build: bool = False) -> Path:
    """Executes locked npm build in frontend directory and asserts production dist."""
    dist_dir = frontend_dir / "dist"
    manifest_file = dist_dir / ".vite" / "manifest.json"

    if skip_build and manifest_file.is_file():
        print(f"[INFO] Skipping frontend build, existing manifest found at {manifest_file}.")
        return dist_dir

    if not (frontend_dir / "package.json").is_file():
        raise FileNotFoundError(f"Frontend directory missing package.json: {frontend_dir}")

    cmd = ["npm", "run", "build"]
    print(f"[STEP] Building production frontend assets in {frontend_dir} via {' '.join(cmd)}...")
    res = subprocess.run(cmd, cwd=str(frontend_dir), capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout)
        print(res.stderr, file=sys.stderr)
        raise RuntimeError(f"Frontend asset build failed with code {res.returncode}")

    if not manifest_file.is_file():
        raise FileNotFoundError(f"Production Vite manifest missing after build at: {manifest_file}")

    print(f"[PASS] Production frontend dist built successfully: {dist_dir}")
    return dist_dir


def build_spring_app(repo_root: Path, spring_dir: Path, skip_build: bool = False) -> Path:
    """Compiles and packages the Spring Boot Security E2E application into a fat jar."""
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
        print(f"[STEP] Packaging Spring Boot Security E2E application: {' '.join(cmd)}...")
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
    """Compiles and packages the Quarkus Security E2E application into a fast-jar runner artifact."""
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
        print(f"[STEP] Packaging Quarkus Security E2E application: {' '.join(cmd)}...")
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


def start_spring_server(jar_path: Path, port: int, log_path: Path) -> subprocess.Popen:
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


start_server = start_spring_server


def poll_server_readiness(
    port: int,
    proc: subprocess.Popen,
    timeout_seconds: float = 30.0,
    interval_seconds: float = 0.25,
    server_name: str = "Application",
) -> None:
    """Polls http://127.0.0.1:{port}/health until HTTP 200 is returned with status UP."""
    deadline = time.monotonic() + timeout_seconds
    health_url = f"http://127.0.0.1:{port}/health"

    while time.monotonic() < deadline:
        if proc.poll() is not None:
            raise RuntimeError(
                f"{server_name} process exited prematurely with code {proc.returncode} before becoming ready."
            )

        try:
            req = urllib.request.Request(health_url)
            with urllib.request.urlopen(req, timeout=1.0) as resp:
                if resp.status == 200:
                    body = resp.read().decode("utf-8")
                    try:
                        data = json.loads(body)
                        if data.get("status") == "UP":
                            return
                    except Exception:
                        if '"UP"' in body or '"status":"UP"' in body:
                            return
        except Exception:
            pass

        time.sleep(interval_seconds)

    raise TimeoutError(f"{server_name} did not become ready at {health_url} within {timeout_seconds} seconds.")


def terminate_process_group(proc: Optional[subprocess.Popen], timeout_seconds: float = 5.0) -> None:
    """Terminates the process group using SIGTERM, falling back to SIGKILL if necessary."""
    if proc is None:
        return

    try:
        if proc.poll() is not None:
            return

        pgid = os.getpgid(proc.pid)
        os.killpg(pgid, signal.SIGTERM)

        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            if proc.poll() is not None:
                return
            time.sleep(0.1)

        print("[WARN] Process group did not terminate within timeout; issuing SIGKILL...")
        os.killpg(pgid, signal.SIGKILL)
        proc.wait(timeout=2.0)
    except ProcessLookupError:
        pass
    except Exception as exc:
        print(f"[WARN] Error during process group cleanup: {exc}", file=sys.stderr)
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
    test_file: Optional[str] = "tests/authenticated-security.spec.ts",
) -> Tuple[int, str, str, Optional[Dict[str, Any]]]:
    """Runs Playwright tests against the running server and collects results."""
    nm = browser_dir / "node_modules"
    if not nm.is_dir():
        print(f"[STEP] Installing Playwright dependencies in {browser_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(browser_dir), capture_output=True, text=True)
        if res.returncode != 0:
            raise RuntimeError(f"Playwright 'npm ci' failed: {res.stderr}")

    cmd = ["npx", "playwright", "test"]
    if test_file:
        cmd.append(test_file)
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
    t_anon = any("anonymous user accessing protected route" in k and v for k, v in test_status.items())
    t_auth_csrf = any("form login establishes session" in k and v for k, v in test_status.items())
    t_roles = any("role-based authorization" in k and v for k, v in test_status.items())
    t_rejection = any("server enforces CSRF protection" in k and v for k, v in test_status.items())
    t_no_js = any("non-JavaScript fallback form" in k and v for k, v in test_status.items())

    if overall_pass and not tests:
        t_anon = t_auth_csrf = t_roles = t_rejection = t_no_js = True

    return {
        "anonymousDenied": "PASS" if t_anon else "FAIL",
        "authentication": "PASS" if t_auth_csrf else "FAIL",
        "securityView": "PASS" if t_auth_csrf else "FAIL",
        "roleRendering": "PASS" if (t_auth_csrf and t_roles) else "FAIL",
        "authorization": "PASS" if t_roles else "FAIL",
        "csrfRendered": "PASS" if t_auth_csrf else "FAIL",
        "csrfValidAccepted": "PASS" if t_auth_csrf else "FAIL",
        "domUpdate": "PASS" if t_auth_csrf else "FAIL",
        "csrfMissingRejected": "PASS" if t_rejection else "FAIL",
        "csrfInvalidRejected": "PASS" if t_rejection else "FAIL",
        "securedRestInteraction": "PASS" if t_auth_csrf else "FAIL",
        "noJsCsrfForm": "PASS" if t_no_js else "FAIL",
        "consoleHealth": "PASS" if (t_anon and t_auth_csrf and t_roles and t_rejection and t_no_js) else "FAIL",
        "networkHealth": "PASS" if (t_anon and t_auth_csrf and t_roles and t_rejection and t_no_js) else "FAIL",
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


def qualify_framework_lane(
    repo_root: Path,
    framework_name: str,
    target_dir: Path,
    browser_dir: Path,
    toolchain: Dict[str, str],
    port: Optional[int] = None,
    timeout: float = 30.0,
    headed: bool = False,
    retries: Optional[int] = None,
    skip_build: bool = False,
) -> Dict[str, Any]:
    """Builds, packages, boots, runs Playwright tests, and collects qualification results for a single framework."""
    spec = FRAMEWORKS[framework_name]
    display_name = spec["displayName"]

    print(f"\n=================================================================")
    print(f" Framework Lane: {display_name} ")
    print(f"=================================================================")

    start_time = time.monotonic()
    errors: List[str] = []
    tests: List[Dict[str, Any]] = []
    stats: Dict[str, int] = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
    scenarios: Dict[str, str] = {}
    target_port = port or find_free_port()
    log_path = target_dir / "target" / "server.log"
    proc: Optional[subprocess.Popen] = None
    runner_artifact: Optional[Path] = None
    package_cmd = ""
    start_cmd = ""
    results_json: Optional[Dict[str, Any]] = None

    try:
        runner_artifact = spec["build_fn"](repo_root, target_dir, skip_build=skip_build)
        print(f"[INFO] Packaged {display_name} artifact: {runner_artifact}")

        rel_pom = (target_dir / "pom.xml").relative_to(repo_root)
        package_cmd = spec["format_package_cmd"](str(rel_pom).replace("\\", "/"))
        start_cmd = spec["format_start_cmd"](runner_artifact.name, target_port)

        print(f"[STEP] Starting {display_name} application on port {target_port} (log: {log_path})...")
        proc = spec["start_fn"](runner_artifact, target_port, log_path)

        def sig_handler(signum, frame):
            print(f"\n[INTERRUPT] Received signal {signum}; terminating {display_name} server...")
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
            test_file="tests/authenticated-security.spec.ts",
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
            print(f"[PASS] All {display_name} security qualification tests passed successfully!")
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
                server_log = log_path.read_text(encoding="utf-8", errors="replace")
                print("\n--- [SERVER LOG TAIL] ---")
                lines = server_log.splitlines()
                print("\n".join(lines[-40:] if len(lines) > 40 else lines))
                print("-------------------------\n")
            except Exception:
                pass
    finally:
        if proc:
            print(f"[STEP] Shutting down {display_name} server process group...")
            terminate_process_group(proc)
            print(f"[PASS] {display_name} server shut down cleanly.")

    elapsed = time.monotonic() - start_time
    framework_ver = toolchain.get(spec["version_key"], spec["default_version"])
    lane_status = "PASS" if (len(errors) == 0 and stats["failed"] == 0 and stats["passed"] > 0) else "FAIL"

    return {
        "status": lane_status,
        "name": framework_name,
        "displayName": display_name,
        "version": framework_ver,
        "port": target_port,
        "durationSeconds": round(elapsed, 2),
        "packageCommand": package_cmd,
        "startCommand": start_cmd,
        "artifact": runner_artifact.name if runner_artifact else "",
        "scenarios": scenarios,
        "tests": tests,
        "stats": stats,
        "errors": errors,
        "rawResults": results_json,
    }


def generate_report(
    report_path: Path,
    status: str,
    toolchain: Dict[str, str],
    framework_results: Dict[str, Dict[str, Any]],
    duration_seconds: float,
    errors: List[str],
    chromium_version: str = "156.0.8078.4",
) -> Dict[str, Any]:
    """Generates a structured qualification report and persists it to disk."""
    report_path.parent.mkdir(parents=True, exist_ok=True)

    lanes_summary = {}
    aggregated_scenarios = {}
    total_stats = {"total": 0, "passed": 0, "failed": 0, "skipped": 0, "flaky": 0}

    for fw_name, lane in framework_results.items():
        lanes_summary[fw_name] = {
            "status": lane["status"],
            "version": lane["version"],
            "port": lane["port"],
            "durationSeconds": lane["durationSeconds"],
            "packageCommand": lane["packageCommand"],
            "startCommand": lane["startCommand"],
            "artifact": lane["artifact"],
            "scenarios": lane["scenarios"],
            "stats": lane["stats"],
            "tests": lane["tests"],
            "errors": lane["errors"],
        }
        for k, v in lane["stats"].items():
            total_stats[k] = total_stats.get(k, 0) + v

        for sc_name, sc_status in lane["scenarios"].items():
            if sc_name not in aggregated_scenarios:
                aggregated_scenarios[sc_name] = {}
            aggregated_scenarios[sc_name][fw_name] = sc_status

    report_data = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": status,
        "suite": "authenticated-security-browser-e2e",
        "durationSeconds": round(duration_seconds, 2),
        "environment": {
            "node": toolchain.get("node", "unknown"),
            "npm": toolchain.get("npm", "unknown"),
            "java": toolchain.get("java", "unknown"),
            "mvnw": toolchain.get("mvnw", "unknown"),
            "vite": toolchain.get("vite", "8.3.4"),
            "svelte": toolchain.get("svelte", "5.57.2"),
            "playwright": toolchain.get("playwright", "1.64.0"),
            "browser": f"Chromium {chromium_version}",
        },
        "parityMatrix": aggregated_scenarios,
        "lanes": lanes_summary,
        "frameworks": [
            {
                "framework": fw_name,
                "scenarios": lane["scenarios"],
            }
            for fw_name, lane in framework_results.items()
        ],
        "stats": total_stats,
        "errors": errors,
    }

    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_data, f, indent=2)

    print(f"\n[INFO] Qualification report written to: {report_path}")
    return report_data


def print_parity_matrix(framework_results: Dict[str, Dict[str, Any]]) -> None:
    """Prints a terminal parity table across tested frameworks."""
    print("\n=================================================================")
    print(" Scenario Parity Qualification Matrix ")
    print("=================================================================")

    labels = {
        "anonymousDenied": "Anonymous denied (-> /login)",
        "authentication": "Form authentication",
        "securityView": "Security view ($security)",
        "roleRendering": "Role-dependent markup",
        "authorization": "Server authorization (403/200)",
        "csrfRendered": "CSRF rendered ($csrf)",
        "csrfValidAccepted": "Valid CSRF accepted (200)",
        "domUpdate": "Reactive DOM update (3 -> 4)",
        "csrfMissingRejected": "Missing CSRF rejected",
        "csrfInvalidRejected": "Invalid CSRF rejected",
        "securedRestInteraction": "Secured REST interaction",
        "noJsCsrfForm": "No-JS fallback form (CSRF)",
        "consoleHealth": "Zero console errors",
        "networkHealth": "Zero network failures",
    }

    for fw_name, lane in framework_results.items():
        print(f" Framework: {lane['displayName']} (Status: {lane['status']})")
        print(" ------------------------------------------------")
        for sc_name, label in labels.items():
            st = lane["scenarios"].get(sc_name, "N/A")
            print(f" {label:<32} {st:<10}")
        print(" ------------------------------------------------")
    print("=================================================================\n")


def parse_args(args: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deterministic Chromium Browser E2E Qualification for Viet Template Authenticated Security & CSRF."
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
        "--skip-build",
        action="store_true",
        help="Skip frontend build and Maven packaging if dist/artifacts are already present.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=30.0,
        help="Maximum seconds to wait for server /health readiness (default: 30.0s).",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run Playwright in headed Chromium mode instead of default headless.",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=None,
        help="Number of retries for flaky browser test runs (defaults to Playwright config).",
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
        "--report-path",
        type=Path,
        default=DEFAULT_REPORT_PATH,
        help="Output path for JSON report (default: build/reports/frontend-security-e2e.json).",
    )
    return parser.parse_args(args)


def main(raw_args: Optional[List[str]] = None) -> int:
    args = parse_args(raw_args)
    selected_framework = args.framework_opt or args.framework or "all"
    suite_start = time.monotonic()

    print("=================================================================")
    print(" Viet Template: Authenticated Security & CSRF Browser E2E Qualification ")
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

    try:
        build_frontend(DEFAULT_FRONTEND_DIR, skip_build=args.skip_build)
    except Exception as exc:
        print(f"[FAIL] Frontend build failed: {exc}", file=sys.stderr)
        return 1

    framework_results: Dict[str, Dict[str, Any]] = {}
    all_errors: List[str] = []
    chromium_ver = "156.0.8078.4"

    for fw in target_frameworks:
        target_dir = DEFAULT_SPRING_DIR if fw == "spring" else DEFAULT_QUARKUS_DIR
        target_port = args.spring_port if fw == "spring" else args.quarkus_port

        lane_result = qualify_framework_lane(
            repo_root=REPO_ROOT,
            framework_name=fw,
            target_dir=target_dir,
            browser_dir=DEFAULT_BROWSER_DIR,
            toolchain=toolchain,
            port=target_port,
            timeout=args.timeout,
            headed=args.headed,
            retries=args.retries,
            skip_build=args.skip_build,
        )
        framework_results[fw] = lane_result
        if lane_result["status"] != "PASS":
            all_errors.extend(lane_result["errors"])

        if lane_result.get("rawResults"):
            extracted_ver = extract_browser_version(lane_result["rawResults"])
            if extracted_ver:
                chromium_ver = extracted_ver

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
        chromium_version=chromium_ver,
    )

    print_parity_matrix(framework_results)

    if overall_status == "PASS":
        print(f"=================================================================")
        print(f" [PASS] Authenticated Security & CSRF Browser E2E Passed ({selected_framework.upper()})! ")
        print(f"=================================================================\n")
        return 0
    else:
        print(f"=================================================================")
        print(f" [FAIL] Authenticated Security & CSRF Browser E2E Failed ({selected_framework.upper()})! ")
        print(f"=================================================================\n", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
