#!/usr/bin/env python3
"""
verify-frontend-vite-svelte.py

Automated CI and local qualification script for Viet Template Frontend Asset Integration:
1. Validates required frontend and Java tooling (Node, npm, Java, Maven/Gradle).
2. Supports qualification profiles:
   - 'vite5-svelte4' (Legacy lane: Vite 5 + Svelte 4, Rollup-era manifest)
   - 'vite8-svelte5' (Current lane: Vite 8 + Svelte 5, Rolldown-era manifest)
   - 'all' (Runs both profiles sequentially)
3. Performs reproducible clean installation using locked dependencies (npm ci).
4. Executes real TypeScript and Svelte compilation (npm run check && npm run build).
5. Locates and validates real Vite production manifest (.vite/manifest.json).
6. Verifies structural output invariants (entries, hashes, emitted CSS, shared chunks).
7. Executes Viet Template Java resolver compatibility suite against the live manifest.
8. Generates machine-readable multi-profile report to build/reports/frontend-compatibility.json.
9. Exits non-zero on any compatibility defect.
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

PROFILES: Dict[str, Dict[str, Any]] = {
    "vite5-svelte4": {
        "id": "vite5-svelte4",
        "aliases": ["legacy", "vite5"],
        "name": "Vite 5 + Svelte 4 (Legacy)",
        "project_dir": REPO_ROOT / "integration-tests" / "frontend" / "vite5-svelte4",
        "bundler_generation": "rollup",
        "logical_entry": "src/pages/employees/index.ts",
        "expected_framework_generation": "svelte4",
        "expected_vite_major": 5,
        "expected_svelte_major": 4,
        "manifest_discovery_strategy": "standard_vite_dist",
        "pinned_versions": {
            "vite": "5.4.2",
            "svelte": "4.2.19",
            "vitePluginSvelte": "3.1.2",
            "typescript": "5.5.4",
        },
        "required_entries": REQUIRED_ENTRIES,
    },
    "vite8-svelte5": {
        "id": "vite8-svelte5",
        "aliases": ["current", "vite8"],
        "name": "Vite 8 + Svelte 5 (Current)",
        "project_dir": REPO_ROOT / "examples" / "frontend-svelte-islands",
        "bundler_generation": "rolldown",
        "logical_entry": "src/pages/employees/index.ts",
        "expected_framework_generation": "svelte5",
        "expected_vite_major": 8,
        "expected_svelte_major": 5,
        "manifest_discovery_strategy": "standard_vite_dist",
        "pinned_versions": {
            "vite": "8.3.4",
            "svelte": "5.57.2",
            "vitePluginSvelte": "7.3.1",
            "typescript": "5.8.3",
        },
        "required_entries": REQUIRED_ENTRIES,
    },
}


def discover_manifest(project_dir: Path, strategy: str = "standard_vite_dist") -> Path:
    """Discovers the Vite production manifest based on the configured strategy."""
    if strategy == "standard_vite_dist":
        candidates = [
            project_dir / "dist" / ".vite" / "manifest.json",
            project_dir / "dist" / "manifest.json",
        ]
        for c in candidates:
            if c.is_file():
                return c
        return candidates[0]
    return project_dir / "dist" / ".vite" / "manifest.json"



def resolve_profiles(profile_name: Optional[str]) -> List[Dict[str, Any]]:
    """Resolves a profile name or alias to a list of configured profile definitions."""
    if not profile_name or profile_name.lower() in ("all", "*"):
        return [PROFILES["vite5-svelte4"], PROFILES["vite8-svelte5"]]

    name_lower = profile_name.lower().strip()
    for prof_id, prof in PROFILES.items():
        if prof_id.lower() == name_lower or name_lower in [a.lower() for a in prof.get("aliases", [])]:
            return [prof]

    raise ValueError(f"Unknown qualification profile '{profile_name}'. Available: {list(PROFILES.keys())} or 'all'")


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
    """Extracts pinned dependencies from package.json and installed node_modules."""
    pkg_json_path = example_dir / "package.json"
    if not pkg_json_path.exists():
        raise FileNotFoundError(f"package.json missing at: {pkg_json_path}")

    with open(pkg_json_path, "r", encoding="utf-8") as f:
        pkg_data = json.load(f)

    dev_deps = pkg_data.get("devDependencies", {})
    toolchain = {
        "vite": dev_deps.get("vite", "unknown"),
        "svelte": dev_deps.get("svelte", "unknown"),
        "vitePluginSvelte": dev_deps.get("@sveltejs/vite-plugin-svelte", "unknown"),
        "typescript": dev_deps.get("typescript", "unknown"),
    }

    # Inspect node_modules if present to verify actual installed versions
    nm = example_dir / "node_modules"
    pkg_map = {
        "vite": nm / "vite" / "package.json",
        "svelte": nm / "svelte" / "package.json",
        "vitePluginSvelte": nm / "@sveltejs" / "vite-plugin-svelte" / "package.json",
        "typescript": nm / "typescript" / "package.json",
    }
    for key, p in pkg_map.items():
        if p.is_file():
            try:
                with open(p, "r", encoding="utf-8") as f:
                    installed_pkg = json.load(f)
                    installed_ver = installed_pkg.get("version")
                    if installed_ver:
                        toolchain[key] = installed_ver
            except Exception:
                pass

    return toolchain


def validate_profile_versions(profile: Dict[str, Any], toolchain: Dict[str, str]) -> List[str]:
    """Validates that extracted versions conform to profile invariants."""
    errors = []
    expected_vite_major = profile.get("expected_vite_major")
    expected_svelte_major = profile.get("expected_svelte_major")
    pinned = profile.get("pinned_versions", {})

    actual_vite = toolchain.get("vite", "")
    actual_svelte = toolchain.get("svelte", "")

    if expected_vite_major is not None and actual_vite and actual_vite != "unknown":
        actual_major = actual_vite.split(".")[0]
        if actual_major != str(expected_vite_major):
            errors.append(
                f"Vite major version mismatch for profile '{profile['id']}': "
                f"expected major {expected_vite_major}, found {actual_vite}"
            )

    if expected_svelte_major is not None and actual_svelte and actual_svelte != "unknown":
        actual_major = actual_svelte.split(".")[0]
        if actual_major != str(expected_svelte_major):
            errors.append(
                f"Svelte major version mismatch for profile '{profile['id']}': "
                f"expected major {expected_svelte_major}, found {actual_svelte}"
            )

    expected_fw_gen = profile.get("expected_framework_generation")
    if expected_fw_gen == "svelte4" and actual_svelte and not actual_svelte.startswith("4."):
        errors.append(
            f"Framework generation mismatch for profile '{profile['id']}': "
            f"expected 'svelte4', found Svelte {actual_svelte}"
        )
    elif expected_fw_gen == "svelte5" and actual_svelte and not actual_svelte.startswith("5."):
        errors.append(
            f"Framework generation mismatch for profile '{profile['id']}': "
            f"expected 'svelte5', found Svelte {actual_svelte}"
        )


    for dep_key, expected_ver in pinned.items():
        actual_ver = toolchain.get(dep_key, "")
        if actual_ver and actual_ver != "unknown" and actual_ver != expected_ver:
            errors.append(
                f"Toolchain version mismatch for '{dep_key}' in profile '{profile['id']}': "
                f"expected pinned '{expected_ver}', found '{actual_ver}'"
            )

    return errors


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
    profile_id: Optional[str] = None,
    build_tool: str = "auto",
) -> None:
    """Executes RealFrontendViteSvelteCompatibilityTest via Maven or Gradle."""
    cmd = []
    env = os.environ.copy()
    env["VIET_TEMPLATE_FRONTEND_MANIFEST"] = str(manifest_path.resolve())
    if profile_id:
        env["VIET_TEMPLATE_FRONTEND_PROFILE"] = profile_id

    mvnw = repo_root / "mvnw"
    gradlew = repo_root / "gradlew"

    profile_prop = f"-Dviet-template.frontend.profile={profile_id}" if profile_id else "-Dviet-template.frontend.profile=auto"

    if build_tool == "maven" or (build_tool == "auto" and mvnw.exists()):
        cmd = [
            str(mvnw),
            "test",
            "-pl",
            "viet-template-runtime",
            "-am",
            "-Dtest=RealFrontendViteSvelteCompatibilityTest",
            "-DfailIfNoTests=false",
            f"-Dviet-template.frontend.manifest={manifest_path.resolve()}",
            profile_prop,
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
    manifest_data: Optional[Dict[str, Any]],
    report_path: Path,
    status: str = "PASS",
    errors: Optional[List[str]] = None,
    profiles: Optional[List[Dict[str, Any]]] = None,
    requested_profile: Optional[str] = None,
) -> Dict[str, Any]:
    """Creates machine-readable qualification report and writes it to report_path."""
    data = manifest_data if isinstance(manifest_data, dict) else {}
    script_count = sum(1 for e in data.values() if isinstance(e, dict) and e.get("file", "").endswith(".js"))
    css_count = sum(len(e.get("css", [])) for e in data.values() if isinstance(e, dict))
    preload_count = sum(len(e.get("imports", [])) for e in data.values() if isinstance(e, dict))

    resolved_req_prof = requested_profile or ("all" if profiles and len(profiles) > 1 else (profiles[0]["id"] if profiles else "single"))

    report: Dict[str, Any] = {
        "status": status,
        "requestedProfile": resolved_req_prof,
        "nodeVersion": node_version,
        "npmVersion": npm_version,
    }


    if profiles:
        report["profiles"] = profiles
        # Backwards compatibility: if single profile, populate top-level convenience fields
        if len(profiles) == 1:
            p = profiles[0]
            report.update({
                "viteVersion": p.get("viteVersion", ""),
                "svelteVersion": p.get("svelteVersion", ""),
                "vitePluginSvelteVersion": p.get("vitePluginSvelteVersion", ""),
                "typescriptVersion": p.get("typescriptVersion", ""),
                "entry": p.get("entry", ""),
                "entries": p.get("entries", []),
                "manifest": p.get("manifest", str(manifest_path)),
                "resolvedScriptCount": p.get("resolvedScriptCount", 0),
                "resolvedStylesheetCount": p.get("resolvedStylesheetCount", 0),
                "resolvedPreloadCount": p.get("resolvedPreloadCount", 0),
            })
    else:
        # Build single profile record
        single_profile = {
            "id": "single",
            "nodeVersion": node_version,
            "npmVersion": npm_version,
            "viteVersion": toolchain.get("vite", ""),
            "svelteVersion": toolchain.get("svelte", ""),
            "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
            "typescriptVersion": toolchain.get("typescript", ""),
            "entry": REQUIRED_ENTRIES[0] if REQUIRED_ENTRIES else "",
            "entries": [k for k, v in data.items() if isinstance(v, dict) and v.get("isEntry")],
            "manifest": str(manifest_path),
            "resolvedScriptCount": script_count,
            "resolvedStylesheetCount": css_count,
            "resolvedPreloadCount": preload_count,
            "status": status,
        }
        if errors:
            single_profile["errors"] = errors
        report["profiles"] = [single_profile]
        report.update({
            "viteVersion": toolchain.get("vite", ""),
            "svelteVersion": toolchain.get("svelte", ""),
            "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
            "typescriptVersion": toolchain.get("typescript", ""),
            "entry": REQUIRED_ENTRIES[0] if REQUIRED_ENTRIES else "",
            "entries": [k for k, v in data.items() if isinstance(v, dict) and v.get("isEntry")],
            "manifest": str(manifest_path),
            "resolvedScriptCount": script_count,
            "resolvedStylesheetCount": css_count,
            "resolvedPreloadCount": preload_count,
        })

    if errors:
        report["errors"] = errors

    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    return report


def qualify_single_profile(
    profile: Dict[str, Any],
    node_version: str,
    npm_version: str,
    repo_root: Path,
    manifest_override: Optional[Path] = None,
    build_tool: str = "auto",
    skip_install: bool = False,
    skip_build: bool = False,
    skip_java: bool = False,
) -> Tuple[str, Dict[str, Any], List[str]]:
    """Runs full qualification for a single profile."""
    prof_id = profile["id"]
    prof_name = profile["name"]
    project_dir = Path(profile["project_dir"]).resolve()
    required_entries = profile.get("required_entries", REQUIRED_ENTRIES)
    bundler_gen = profile.get("bundler_generation", "rollup")

    print("\n-----------------------------------------------------------------")
    print(f" Qualifying Profile: {prof_name} ({prof_id})")
    print(f" Directory:          {project_dir}")
    print(f" Bundler Gen:        {bundler_gen}")
    print("-----------------------------------------------------------------")

    errors: List[str] = []
    toolchain: Dict[str, str] = {}

    # 1. Extract toolchain versions
    try:
        toolchain = extract_toolchain_versions(project_dir)
        print(f"[INFO] Toolchain: Vite {toolchain['vite']} | Svelte {toolchain['svelte']} | Plugin {toolchain['vitePluginSvelte']} | TS {toolchain['typescript']}")
    except Exception as e:
        err_msg = f"Failed inspecting toolchain versions: {e}"
        print(f"[FAIL] {err_msg}", file=sys.stderr)
        errors.append(err_msg)
        return "FAIL", {
            "id": prof_id,
            "name": prof_name,
            "bundlerGeneration": bundler_gen,
            "status": "FAIL",
            "errors": errors,
        }, errors

    # 2. Validate versions against profile constraints
    ver_errors = validate_profile_versions(profile, toolchain)
    if ver_errors:
        for err in ver_errors:
            print(f"[FAIL] {err}", file=sys.stderr)
        errors.extend(ver_errors)
        return "FAIL", {
            "id": prof_id,
            "name": prof_name,
            "bundlerGeneration": bundler_gen,
            "status": "FAIL",
            "nodeVersion": node_version,
            "npmVersion": npm_version,
            "viteVersion": toolchain.get("vite", ""),
            "svelteVersion": toolchain.get("svelte", ""),
            "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
            "typescriptVersion": toolchain.get("typescript", ""),
            "errors": errors,
        }, errors

    # 3. Build frontend
    if not skip_build:
        try:
            run_frontend_build(project_dir, skip_install=skip_install)
        except Exception as e:
            err_msg = f"Frontend compilation failed: {e}"
            print(f"[FAIL] {err_msg}", file=sys.stderr)
            errors.append(err_msg)
            return "FAIL", {
                "id": prof_id,
                "name": prof_name,
                "bundlerGeneration": bundler_gen,
                "status": "FAIL",
                "nodeVersion": node_version,
                "npmVersion": npm_version,
                "viteVersion": toolchain.get("vite", ""),
                "svelteVersion": toolchain.get("svelte", ""),
                "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
                "typescriptVersion": toolchain.get("typescript", ""),
                "errors": errors,
            }, errors
    else:
        print("[INFO] Skipping frontend build as requested.")

    # 4. Locate manifest
    manifest_path = manifest_override
    if manifest_path is None:
        strategy = profile.get("manifest_discovery_strategy", "standard_vite_dist")
        manifest_path = discover_manifest(project_dir, strategy)


    if not manifest_path.is_file():
        err_msg = f"Vite manifest not found at expected location: {manifest_path}"
        print(f"[FAIL] {err_msg}", file=sys.stderr)
        errors.append(err_msg)
        return "FAIL", {
            "id": prof_id,
            "name": prof_name,
            "bundlerGeneration": bundler_gen,
            "status": "FAIL",
            "nodeVersion": node_version,
            "npmVersion": npm_version,
            "viteVersion": toolchain.get("vite", ""),
            "svelteVersion": toolchain.get("svelte", ""),
            "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
            "typescriptVersion": toolchain.get("typescript", ""),
            "manifest": str(manifest_path),
            "errors": errors,
        }, errors

    dist_dir = manifest_path.parent.parent if manifest_path.parent.name == ".vite" else manifest_path.parent

    # 5. Validate manifest contents & disk artifacts
    manifest_data: Dict[str, Any] = {}
    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)
    except Exception as e:
        err_msg = f"Failed parsing manifest JSON at {manifest_path}: {e}"
        print(f"[FAIL] {err_msg}", file=sys.stderr)
        errors.append(err_msg)
        return "FAIL", {
            "id": prof_id,
            "name": prof_name,
            "bundlerGeneration": bundler_gen,
            "status": "FAIL",
            "nodeVersion": node_version,
            "npmVersion": npm_version,
            "manifest": str(manifest_path),
            "errors": errors,
        }, errors

    struct_errors = validate_manifest_structure(manifest_data, dist_dir, required_entries)
    if struct_errors:
        print(f"[FAIL] Manifest structural validation failed with {len(struct_errors)} error(s):", file=sys.stderr)
        for err in struct_errors:
            print(f"  - {err}", file=sys.stderr)
        errors.extend(struct_errors)
        return "FAIL", {
            "id": prof_id,
            "name": prof_name,
            "bundlerGeneration": bundler_gen,
            "status": "FAIL",
            "nodeVersion": node_version,
            "npmVersion": npm_version,
            "viteVersion": toolchain.get("vite", ""),
            "svelteVersion": toolchain.get("svelte", ""),
            "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
            "typescriptVersion": toolchain.get("typescript", ""),
            "manifest": str(manifest_path),
            "errors": errors,
        }, errors

    print(f"[PASS] Real manifest structural invariants verified ({len(manifest_data)} records, all required entries present).")

    # 6. Execute Java resolver qualification
    if not skip_java:
        try:
            run_java_qualification(repo_root, manifest_path, profile_id=prof_id, build_tool=build_tool)
        except Exception as e:
            err_msg = f"Java compatibility qualification failed: {e}"
            print(f"[FAIL] {err_msg}", file=sys.stderr)
            errors.append(err_msg)
            return "FAIL", {
                "id": prof_id,
                "name": prof_name,
                "bundlerGeneration": bundler_gen,
                "status": "FAIL",
                "nodeVersion": node_version,
                "npmVersion": npm_version,
                "viteVersion": toolchain.get("vite", ""),
                "svelteVersion": toolchain.get("svelte", ""),
                "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
                "typescriptVersion": toolchain.get("typescript", ""),
                "manifest": str(manifest_path),
                "errors": errors,
            }, errors
    else:
        print("[INFO] Skipping Java qualification as requested.")

    script_count = sum(1 for e in manifest_data.values() if isinstance(e, dict) and e.get("file", "").endswith(".js"))
    css_count = sum(len(e.get("css", [])) for e in manifest_data.values() if isinstance(e, dict))
    preload_count = sum(len(e.get("imports", [])) for e in manifest_data.values() if isinstance(e, dict))

    profile_report = {
        "id": prof_id,
        "name": prof_name,
        "requestedProfile": prof_id,
        "bundlerGeneration": bundler_gen,
        "frameworkGeneration": profile.get("expected_framework_generation", "svelte4" if "vite5" in prof_id else "svelte5"),
        "logicalEntry": profile.get("logical_entry", required_entries[0] if required_entries else ""),
        "manifestDiscoveryStrategy": profile.get("manifest_discovery_strategy", "standard_vite_dist"),
        "nodeVersion": node_version,
        "npmVersion": npm_version,
        "viteVersion": toolchain.get("vite", ""),
        "svelteVersion": toolchain.get("svelte", ""),
        "vitePluginSvelteVersion": toolchain.get("vitePluginSvelte", ""),
        "typescriptVersion": toolchain.get("typescript", ""),
        "entry": profile.get("logical_entry", required_entries[0] if required_entries else ""),
        "entries": [k for k, v in manifest_data.items() if isinstance(v, dict) and v.get("isEntry")],
        "manifest": str(manifest_path),
        "resolvedScriptCount": script_count,
        "resolvedStylesheetCount": css_count,
        "resolvedPreloadCount": preload_count,
        "status": "PASS",
    }
    return "PASS", profile_report, []



def check_upstream_freshness() -> None:
    """Audits upstream npm registry for latest versions and reports drift."""
    print("\n=================================================================")
    print(" Upstream Frontend Package Freshness Audit                       ")
    print("=================================================================")
    packages = ["vite", "svelte", "@sveltejs/vite-plugin-svelte", "typescript"]
    current_pinned = PROFILES["vite8-svelte5"]["pinned_versions"]
    for pkg in packages:
        try:
            res = subprocess.run(["npm", "view", pkg, "version"], capture_output=True, text=True)
            latest = res.stdout.strip() if res.returncode == 0 else "unknown"
        except Exception:
            latest = "unknown"
        pinned_key = "vitePluginSvelte" if pkg == "@sveltejs/vite-plugin-svelte" else pkg
        pinned_val = current_pinned.get(pinned_key, "unknown")
        status_flag = "[CURRENT]" if pinned_val == latest else "[DRIFT]"
        print(f" {status_flag:9} {pkg:30}: pinned {pinned_val:<10} | latest {latest}")
    print("=================================================================\n")


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Viet Template Real Vite/Svelte Compatibility Qualification")
    parser.add_argument("profile_arg", nargs="?", default=None, help="Qualification profile ('vite5-svelte4', 'vite8-svelte5', 'legacy', 'current', 'all')")
    parser.add_argument("--profile", default=None, help="Named qualification profile")
    parser.add_argument("--example-dir", type=Path, default=None, help="Custom frontend project path (overrides profile directory)")
    parser.add_argument("--manifest-path", type=Path, default=None, help="Explicit manifest path")
    parser.add_argument("--report-path", type=Path, default=DEFAULT_REPORT_PATH, help="Path for JSON report")
    parser.add_argument("--build-tool", choices=["auto", "maven", "gradle"], default="auto", help="Build tool for Java test")
    parser.add_argument("--skip-install", action="store_true", help="Skip npm ci")
    parser.add_argument("--skip-build", action="store_true", help="Skip frontend build (use existing dist)")
    parser.add_argument("--skip-java", action="store_true", help="Skip Java resolver test")
    parser.add_argument("--check-freshness", action="store_true", help="Check upstream package freshness on npm registry")

    args = parser.parse_args(argv)

    if args.check_freshness:
        check_upstream_freshness()

    print("=================================================================")
    print(" Viet Template: Real Vite + Svelte Compatibility Matrix          ")
    print("=================================================================")

    # 1. Check environment
    try:
        node_version = check_tool_available("node")
        npm_version = check_tool_available("npm")
        print(f"[INFO] Node environment: {node_version} (npm {npm_version})")
    except Exception as e:
        print(f"[FAIL] Environment check failed: {e}", file=sys.stderr)
        return 1

    # 2. Determine profiles to execute
    profile_req = args.profile or args.profile_arg
    if args.example_dir is not None:
        # Custom directory specified: execute single custom profile
        resolved_profiles = [{
            "id": "custom",
            "name": f"Custom ({args.example_dir.name})",
            "project_dir": args.example_dir,
            "bundler_generation": "custom",
            "pinned_versions": {},
            "required_entries": REQUIRED_ENTRIES,
        }]
    else:
        try:
            resolved_profiles = resolve_profiles(profile_req)
        except ValueError as e:
            print(f"[FAIL] {e}", file=sys.stderr)
            generate_compatibility_report(
                node_version=node_version,
                npm_version=npm_version,
                toolchain={},
                manifest_path=args.manifest_path or (DEFAULT_EXAMPLE_DIR / "dist" / ".vite" / "manifest.json"),
                manifest_data=None,
                report_path=args.report_path.resolve(),
                status="FAIL",
                errors=[str(e)],
                requested_profile=profile_req,
            )
            return 1

    overall_status = "PASS"
    profile_reports: List[Dict[str, Any]] = []
    all_errors: List[str] = []

    for prof in resolved_profiles:
        status, p_report, errors = qualify_single_profile(
            profile=prof,
            node_version=node_version,
            npm_version=npm_version,
            repo_root=REPO_ROOT,
            manifest_override=args.manifest_path,
            build_tool=args.build_tool,
            skip_install=args.skip_install,
            skip_build=args.skip_build,
            skip_java=args.skip_java,
        )
        profile_reports.append(p_report)
        if status != "PASS":
            overall_status = "FAIL"
            all_errors.extend(errors)

    # Generate combined report
    first_toolchain = extract_toolchain_versions(Path(resolved_profiles[0]["project_dir"]).resolve()) if resolved_profiles and (Path(resolved_profiles[0]["project_dir"]) / "package.json").exists() else {}
    report = generate_compatibility_report(
        node_version=node_version,
        npm_version=npm_version,
        toolchain=first_toolchain,
        manifest_path=args.manifest_path or Path(resolved_profiles[0]["project_dir"]) / "dist" / ".vite" / "manifest.json",
        manifest_data=None,
        report_path=args.report_path.resolve(),
        status=overall_status,
        errors=all_errors if all_errors else None,
        profiles=profile_reports,
        requested_profile=profile_req or "all",
    )

    print(f"\n[INFO] Qualification report written to: {args.report_path}")

    print("=================================================================")
    if overall_status == "PASS":
        print(" [PASS] Frontend Asset Integration Matrix Qualification Passed!  ")
    else:
        print(" [FAIL] Frontend Asset Integration Matrix Qualification Failed!  ", file=sys.stderr)
    print("=================================================================")

    return 0 if overall_status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
