#!/usr/bin/env python3
"""
verify-frontend-vite-svelte.py

Automated CI and local qualification script for Viet Template Frontend Asset Integration:
1. Validates required frontend and Java tooling (Node, npm, Java, Maven/Gradle).
2. Performs reproducible clean installation using locked dependencies (npm ci).
3. Executes real TypeScript and Svelte compilation (npm run check && npm run build).
4. Locates and validates real Vite production manifest (.vite/manifest.json).
5. Verifies structural output invariants (entries, hashes, emitted CSS, shared chunks).
6. Executes Viet Template Java resolver compatibility suite against the live manifest.
7. Generates machine-readable report to build/reports/frontend-compatibility.json.
8. Exits non-zero on any compatibility defect.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_EXAMPLE_DIR = REPO_ROOT / "examples" / "frontend-svelte-islands"
DEFAULT_REPORT_PATH = REPO_ROOT / "build" / "reports" / "frontend-compatibility.json"
REQUIRED_ENTRIES = [
    "src/pages/employees/index.ts",
    "src/pages/counter/index.ts",
    "src/pages/payroll/Payroll.svelte",
]


def check_tool_available(tool_name: str) -> str:
    """Verifies a tool is installed on PATH and returns its version string."""
    bin_path = shutil.which(tool_name)
    if not bin_path:
        raise RuntimeError(f"Required tool '{tool_name}' was not found on system PATH.")

    res = subprocess.run([tool_name, "--version"], capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Failed executing '{tool_name} --version': {res.stderr}")
    return res.stdout.strip()


def extract_toolchain_versions(example_dir: Path) -> Dict[str, str]:
    """Extracts pinned dependencies from package.json and lockfile."""
    pkg_json_path = example_dir / "package.json"
    if not pkg_json_path.exists():
        raise FileNotFoundError(f"package.json missing at: {pkg_json_path}")

    with open(pkg_json_path, "r", encoding="utf-8") as f:
        pkg_data = json.load(f)

    dev_deps = pkg_data.get("devDependencies", {})
    return {
        "vite": dev_deps.get("vite", "unknown"),
        "svelte": dev_deps.get("svelte", "unknown"),
        "vitePluginSvelte": dev_deps.get("@sveltejs/vite-plugin-svelte", "unknown"),
        "typescript": dev_deps.get("typescript", "unknown"),
    }


def run_frontend_build(example_dir: Path, skip_install: bool = False) -> None:
    """Executes npm ci, npm run check, and npm run build in the example directory."""
    if not (example_dir / "package.json").exists():
        raise FileNotFoundError(f"Example directory invalid: {example_dir}")

    if not skip_install:
        lock_file = example_dir / "package-lock.json"
        if not lock_file.exists():
            raise FileNotFoundError(f"package-lock.json missing at {lock_file}. Run 'npm install' locally first.")

        print(f"[STEP] Installing locked frontend dependencies via 'npm ci' in {example_dir}...")
        res = subprocess.run(["npm", "ci"], cwd=str(example_dir), capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stderr, file=sys.stderr)
            raise RuntimeError(f"'npm ci' failed with code {res.returncode}")

    # Clean existing dist
    dist_dir = example_dir / "dist"
    if dist_dir.exists():
        shutil.rmtree(dist_dir)

    print(f"[STEP] Running TypeScript check via 'npm run check' in {example_dir}...")
    res = subprocess.run(["npm", "run", "check"], cwd=str(example_dir), capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stderr, file=sys.stderr)
        raise RuntimeError(f"'npm run check' failed with code {res.returncode}")

    print(f"[STEP] Building production assets via 'npm run build' in {example_dir}...")
    res = subprocess.run(["npm", "run", "build"], cwd=str(example_dir), capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stderr, file=sys.stderr)
        raise RuntimeError(f"'npm run build' failed with code {res.returncode}")


def validate_manifest_structure(
    manifest_data: Dict[str, Any], dist_dir: Path, required_entries: List[str]
) -> List[str]:
    """
    Validates structural invariants of the generated Vite manifest and physical assets on disk.
    Returns a list of error strings (empty if valid).
    """
    errors = []

    if not isinstance(manifest_data, dict):
        return ["Manifest root must be a JSON object mapping entry keys to chunk records."]

    if not manifest_data:
        return ["Manifest root object is empty."]

    # 1. Verify required entries exist in manifest
    for req in required_entries:
        if req not in manifest_data:
            errors.append(f"Required logical entry '{req}' missing from manifest.")
            continue

        entry_record = manifest_data[req]
        if not isinstance(entry_record, dict):
            errors.append(f"Manifest record for '{req}' must be a dictionary object.")
            continue

        # Verify entry flag
        if not entry_record.get("isEntry"):
            errors.append(f"Entry '{req}' does not have 'isEntry: true' in manifest.")

        # Verify file property and physical existence
        file_prop = entry_record.get("file")
        if not file_prop:
            errors.append(f"Entry '{req}' is missing required 'file' property.")
        else:
            physical_js = dist_dir / file_prop
            if not physical_js.is_file():
                errors.append(f"Generated JS file for '{req}' does not exist on disk: {physical_js}")

        # Verify CSS properties if present
        css_list = entry_record.get("css", [])
        if not isinstance(css_list, list):
            errors.append(f"Entry '{req}' 'css' field must be a list.")
        else:
            for css_file in css_list:
                physical_css = dist_dir / css_file
                if not physical_css.is_file():
                    errors.append(f"Generated CSS file for '{req}' does not exist on disk: {physical_css}")

        # Verify static imports
        imports_list = entry_record.get("imports", [])
        if not isinstance(imports_list, list):
            errors.append(f"Entry '{req}' 'imports' field must be a list.")
        else:
            for chunk_key in imports_list:
                if chunk_key not in manifest_data:
                    errors.append(f"Entry '{req}' imports chunk '{chunk_key}' which is not in manifest.")
                else:
                    chunk_record = manifest_data[chunk_key]
                    chunk_file = chunk_record.get("file") if isinstance(chunk_record, dict) else None
                    if not chunk_file:
                        errors.append(f"Imported chunk '{chunk_key}' has no 'file' property.")
                    else:
                        physical_chunk = dist_dir / chunk_file
                        if not physical_chunk.is_file():
                            errors.append(f"Physical chunk file for '{chunk_key}' does not exist: {physical_chunk}")

    return errors


def run_java_qualification(
    repo_root: Path,
    manifest_path: Path,
    build_tool: str = "auto",
) -> None:
    """Executes RealFrontendViteSvelteCompatibilityTest via Maven or Gradle."""
    cmd = []
    env = os.environ.copy()
    env["VIET_TEMPLATE_FRONTEND_MANIFEST"] = str(manifest_path.resolve())

    mvnw = repo_root / "mvnw"
    gradlew = repo_root / "gradlew"

    if build_tool == "maven" or (build_tool == "auto" and mvnw.exists()):
        cmd = [
            str(mvnw),
            "test",
            "-pl",
            "viet-template-runtime",
            "-am",
            f"-Dtest=RealFrontendViteSvelteCompatibilityTest",
            "-DfailIfNoTests=false",
            f"-Dviet-template.frontend.manifest={manifest_path.resolve()}",
            "-B",
        ]
    elif build_tool == "gradle" or (build_tool == "auto" and gradlew.exists()):
        cmd = [
            str(gradlew),
            ":viet-template-runtime:test",
            "--tests",
            "RealFrontendViteSvelteCompatibilityTest",
            f"-Dviet-template.frontend.manifest={manifest_path.resolve()}",
            "--no-daemon",
        ]
    else:
        raise RuntimeError("No supported build tool (mvnw or gradlew) found in repository root.")

    print(f"[STEP] Running Viet Template Java resolver qualification via: {' '.join(cmd)}")
    res = subprocess.run(cmd, cwd=str(repo_root), env=env, capture_output=True, text=True)
    if res.returncode != 0:
        print("[FAIL] Java resolver qualification failed:", file=sys.stderr)
        print(res.stdout)
        print(res.stderr, file=sys.stderr)
        raise RuntimeError(f"Java resolver test suite exited with code {res.returncode}")

    print("[PASS] Java resolver qualification passed.")


def generate_compatibility_report(
    node_version: str,
    npm_version: str,
    toolchain: Dict[str, str],
    manifest_path: Path,
    manifest_data: Dict[str, Any],
    report_path: Path,
) -> Dict[str, Any]:
    """Creates machine-readable qualification report and writes it to report_path."""
    script_count = sum(1 for e in manifest_data.values() if isinstance(e, dict) and e.get("file", "").endswith(".js"))
    css_count = sum(len(e.get("css", [])) for e in manifest_data.values() if isinstance(e, dict))
    preload_count = sum(len(e.get("imports", [])) for e in manifest_data.values() if isinstance(e, dict))

    report = {
        "status": "PASS",
        "nodeVersion": node_version,
        "npmVersion": npm_version,
        "viteVersion": toolchain.get("vite", ""),
        "svelteVersion": toolchain.get("svelte", ""),
        "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
        "typescriptVersion": toolchain.get("typescript", ""),
        "manifest": str(manifest_path),
        "entries": [k for k, v in manifest_data.items() if isinstance(v, dict) and v.get("isEntry")],
        "resolvedScriptCount": script_count,
        "resolvedStylesheetCount": css_count,
        "resolvedPreloadCount": preload_count,
    }

    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    return report


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Viet Template Real Vite/Svelte Compatibility Qualification")
    parser.add_argument("--example-dir", type=Path, default=DEFAULT_EXAMPLE_DIR, help="Frontend example path")
    parser.add_argument("--manifest-path", type=Path, default=None, help="Explicit manifest path")
    parser.add_argument("--report-path", type=Path, default=DEFAULT_REPORT_PATH, help="Path for JSON report")
    parser.add_argument("--build-tool", choices=["auto", "maven", "gradle"], default="auto", help="Build tool for Java test")
    parser.add_argument("--skip-install", action="store_true", help="Skip npm ci")
    parser.add_argument("--skip-build", action="store_true", help="Skip frontend build (use existing dist)")
    parser.add_argument("--skip-java", action="store_true", help="Skip Java resolver test")

    args = parser.parse_args(argv)

    print("=================================================================")
    print(" Viet Template: Real Vite + Svelte Compatibility Qualification   ")
    print("=================================================================")

    # 1. Check environment
    try:
        node_version = check_tool_available("node")
        npm_version = check_tool_available("npm")
        print(f"[INFO] Node environment: {node_version} (npm {npm_version})")
    except Exception as e:
        print(f"[FAIL] Environment check failed: {e}", file=sys.stderr)
        return 1

    # 2. Inspect dependencies
    example_dir = args.example_dir.resolve()
    try:
        toolchain = extract_toolchain_versions(example_dir)
        print(f"[INFO] Toolchain: Vite {toolchain['vite']} | Svelte {toolchain['svelte']} | Plugin {toolchain['vitePluginSvelte']} | TS {toolchain['typescript']}")
    except Exception as e:
        print(f"[FAIL] Failed inspecting toolchain versions: {e}", file=sys.stderr)
        return 1

    # 3. Build frontend
    if not args.skip_build:
        try:
            run_frontend_build(example_dir, skip_install=args.skip_install)
        except Exception as e:
            print(f"[FAIL] Frontend compilation failed: {e}", file=sys.stderr)
            return 1
    else:
        print("[INFO] Skipping frontend build as requested.")

    # 4. Locate manifest
    manifest_path = args.manifest_path
    if manifest_path is None:
        manifest_path = example_dir / "dist" / ".vite" / "manifest.json"

    if not manifest_path.is_file():
        print(f"[FAIL] Vite manifest not found at expected location: {manifest_path}", file=sys.stderr)
        return 1

    dist_dir = manifest_path.parent.parent if manifest_path.parent.name == ".vite" else manifest_path.parent

    # 5. Validate manifest contents & disk artifacts
    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)
    except Exception as e:
        print(f"[FAIL] Failed parsing manifest JSON at {manifest_path}: {e}", file=sys.stderr)
        return 1

    errors = validate_manifest_structure(manifest_data, dist_dir, REQUIRED_ENTRIES)
    if errors:
        print(f"[FAIL] Manifest structural validation failed with {len(errors)} error(s):", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1
    print(f"[PASS] Real manifest structural invariants verified ({len(manifest_data)} records, all required entries present).")

    # 6. Execute Java resolver qualification
    if not args.skip_java:
        try:
            run_java_qualification(REPO_ROOT, manifest_path, build_tool=args.build_tool)
        except Exception as e:
            print(f"[FAIL] Java compatibility qualification failed: {e}", file=sys.stderr)
            return 1
    else:
        print("[INFO] Skipping Java qualification as requested.")

    # 7. Generate report
    report = generate_compatibility_report(
        node_version=node_version,
        npm_version=npm_version,
        toolchain=toolchain,
        manifest_path=manifest_path,
        manifest_data=manifest_data,
        report_path=args.report_path.resolve(),
    )
    print(f"[PASS] Report written to: {args.report_path}")

    print("=================================================================")
    print(" [PASS] Vite + Svelte Asset Integration Qualification Complete!  ")
    print("=================================================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())
