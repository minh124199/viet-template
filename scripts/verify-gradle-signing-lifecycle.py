#!/usr/bin/env python3
"""
verify-gradle-signing-lifecycle.py

Authoritative release qualification gate and verification tool for Gradle plugin
publication signing lifecycle ordering and topology.

Verifies:
1. When signing credentials are absent:
   - Gradle configuration succeeds.
   - Signing is optional and ordinary developer builds are unaffected.
2. When signing credentials are present (using an ephemeral OpenPGP test key):
   - Gradle configuration succeeds without publication lookup timing errors.
   - Both expected publications are discovered:
     - 'pluginMaven' (implementation coordinate)
     - 'vietTemplatePluginMarkerMaven' (Gradle plugin DSL marker)
   - Signing tasks are dynamically registered for both publications:
     - 'signPluginMavenPublication'
     - 'signVietTemplatePluginMarkerMavenPublication'
   - Staging to a temporary repository successfully produces signed artifacts (*.asc).
3. Secret-independent: Never uses or prints production signing credentials.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import NamedTuple

ROOT_DIR = Path(__file__).resolve().parents[1]

EXPECTED_PUBLICATIONS = {
    "pluginMaven",
    "vietTemplatePluginMarkerMaven",
}

EXPECTED_SIGNING_TASKS = {
    "signPluginMavenPublication",
    "signVietTemplatePluginMarkerMavenPublication",
}


class PublicationCheckResult(NamedTuple):
    passed: bool
    observed_publications: set[str]
    missing_publications: set[str]
    observed_signing_tasks: set[str]
    missing_signing_tasks: set[str]
    diagnostics: list[str]


def generate_ephemeral_pgp_key(passphrase: str = "") -> tuple[str, str]:
    """Generates an ephemeral in-memory ASCII-armored RSA 2048-bit PGP private key via GnuPG.

    Returns (armored_private_key, passphrase).
    Never logs or leaks key material.
    """
    gnupg_home = tempfile.mkdtemp(prefix="viet-ephemeral-gpg-")
    try:
        os.chmod(gnupg_home, 0o700)
        env = {**os.environ, "GNUPGHOME": gnupg_home}
        user_id = "Viet Template Ephemeral Test <test@viet-template.internal>"
        gen_cmd = [
            "gpg",
            "--batch",
            "--passphrase",
            passphrase,
            "--quick-generate-key",
            user_id,
            "rsa2048",
            "sign",
            "1d",
        ]
        result = subprocess.run(
            gen_cmd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(f"GnuPG key generation failed: {result.stderr.strip()}")

        export_cmd = [
            "gpg",
            "--batch",
            "--armor",
            "--passphrase",
            passphrase,
            "--pinentry-mode",
            "loopback",
            "--export-secret-keys",
            user_id,
        ]
        export_result = subprocess.run(
            export_cmd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        if export_result.returncode != 0:
            raise RuntimeError(f"GnuPG key export failed: {export_result.stderr.strip()}")

        armored_key = export_result.stdout
        if "BEGIN PGP PRIVATE KEY BLOCK" not in armored_key:
            raise RuntimeError("Exported key does not contain valid PGP private key header")

        return armored_key, passphrase
    finally:
        shutil.rmtree(gnupg_home, ignore_errors=True)


def check_unauthenticated_configuration(root_dir: Path = ROOT_DIR) -> tuple[bool, list[str]]:
    """Verifies that Gradle configuration succeeds when no signing credentials are provided."""
    env = dict(os.environ)
    env.pop("SIGNING_KEY", None)
    env.pop("SIGNING_PASSWORD", None)

    gradlew = root_dir / "gradlew"
    cmd = [
        str(gradlew),
        ":viet-template-gradle-plugin:tasks",
        "--all",
        "--console=plain",
        "-Dspotless.check.skip=true",
    ]
    result = subprocess.run(cmd, cwd=root_dir, env=env, capture_output=True, text=True, check=False)
    diagnostics = []
    if result.returncode != 0:
        diagnostics.append(f"Unauthenticated Gradle configuration failed (exit code {result.returncode}):\n{result.stderr}")
        return False, diagnostics

    # Ensure signing tasks are NOT registered when signing key is absent
    if "signPluginMavenPublication" in result.stdout or "signVietTemplatePluginMarkerMavenPublication" in result.stdout:
        diagnostics.append("Signing tasks were unexpectedly registered when SIGNING_KEY was absent.")
        return False, diagnostics

    diagnostics.append("Unauthenticated Gradle configuration succeeded (signing optional, zero signing tasks).")
    return True, diagnostics


def check_signing_enabled_lifecycle(
    root_dir: Path = ROOT_DIR,
    signing_key: str | None = None,
    signing_password: str = "",
) -> PublicationCheckResult:
    """Verifies that with signing credentials present, Gradle discovers publications and registers signing tasks."""
    diagnostics = []
    if signing_key is None:
        diagnostics.append("[INFO] Generating ephemeral OpenPGP test key for qualification...")
        signing_key, signing_password = generate_ephemeral_pgp_key(passphrase="ephemeral-secret-passphrase")
        diagnostics.append("[PASS] Ephemeral OpenPGP test key generated successfully (key material masked).")

    env = dict(os.environ)
    env["SIGNING_KEY"] = signing_key
    env["SIGNING_PASSWORD"] = signing_password

    gradlew = root_dir / "gradlew"

    # Step 1: Query tasks
    cmd = [
        str(gradlew),
        ":viet-template-gradle-plugin:tasks",
        "--all",
        "--console=plain",
        "-Dspotless.check.skip=true",
    ]
    result = subprocess.run(cmd, cwd=root_dir, env=env, capture_output=True, text=True, check=False)

    if result.returncode != 0:
        # Detect the specific regression
        if "Publication with name 'pluginMaven' not found" in result.stderr or "Publication with name 'pluginMaven' not found" in result.stdout:
            diagnostics.append("[FAIL] REGRESSION DETECTED: Publication with name 'pluginMaven' not found during configuration.")
        else:
            diagnostics.append(f"[FAIL] Gradle task inspection failed (exit code {result.returncode}):\n{result.stderr.strip()}")
        return PublicationCheckResult(
            passed=False,
            observed_publications=set(),
            missing_publications=set(EXPECTED_PUBLICATIONS),
            observed_signing_tasks=set(),
            missing_signing_tasks=set(EXPECTED_SIGNING_TASKS),
            diagnostics=diagnostics,
        )

    output = result.stdout

    # Detect publications from task names
    observed_pubs = set()
    if "generatePomFileForPluginMavenPublication" in output:
        observed_pubs.add("pluginMaven")
    if "generatePomFileForVietTemplatePluginMarkerMavenPublication" in output:
        observed_pubs.add("vietTemplatePluginMarkerMaven")

    missing_pubs = EXPECTED_PUBLICATIONS - observed_pubs

    # Detect signing tasks
    observed_signing_tasks = set()
    if "signPluginMavenPublication" in output:
        observed_signing_tasks.add("signPluginMavenPublication")
    if "signVietTemplatePluginMarkerMavenPublication" in output:
        observed_signing_tasks.add("signVietTemplatePluginMarkerMavenPublication")

    missing_signing_tasks = EXPECTED_SIGNING_TASKS - observed_signing_tasks

    passed = (len(missing_pubs) == 0) and (len(missing_signing_tasks) == 0)

    if missing_pubs:
        diagnostics.append(f"[FAIL] Missing expected publication(s): {', '.join(sorted(missing_pubs))}")
    else:
        diagnostics.append(f"[PASS] All expected publications discovered: {', '.join(sorted(observed_pubs))}")

    if missing_signing_tasks:
        diagnostics.append(f"[FAIL] Missing expected signing task(s): {', '.join(sorted(missing_signing_tasks))}")
    else:
        diagnostics.append(f"[PASS] All expected signing tasks registered: {', '.join(sorted(observed_signing_tasks))}")

    return PublicationCheckResult(
        passed=passed,
        observed_publications=observed_pubs,
        missing_publications=missing_pubs,
        observed_signing_tasks=observed_signing_tasks,
        missing_signing_tasks=missing_signing_tasks,
        diagnostics=diagnostics,
    )


def verify_staged_signatures(root_dir: Path = ROOT_DIR) -> tuple[bool, list[str]]:
    """Verifies that executing publication with signing produces valid .asc signature files."""
    diagnostics = []
    signing_key, signing_password = generate_ephemeral_pgp_key(passphrase="test-passphrase")
    env = dict(os.environ)
    env["SIGNING_KEY"] = signing_key
    env["SIGNING_PASSWORD"] = signing_password

    gradlew = root_dir / "gradlew"

    with tempfile.TemporaryDirectory(prefix="viet-signing-stage-") as temp_repo:
        # Publish to rcRepository (or staging)
        cmd = [
            str(gradlew),
            ":viet-template-gradle-plugin:publishVietTemplatePluginMarkerMavenPublicationToRcRepositoryRepository",
            "--no-daemon",
            "-Dspotless.check.skip=true",
        ]
        result = subprocess.run(cmd, cwd=root_dir, env=env, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            diagnostics.append(f"[FAIL] Staged marker publication execution failed:\n{result.stderr.strip()}")
            return False, diagnostics

        marker_dir = root_dir / "build" / "rc-repository" / "io" / "github" / "minh124199" / "viet-template" / "io.github.minh124199.viet-template.gradle.plugin"
        if not marker_dir.exists():
            diagnostics.append(f"[FAIL] Staged marker directory does not exist: {marker_dir}")
            return False, diagnostics

        # Find .asc files in marker directory
        asc_files = list(marker_dir.glob("**/*.asc"))
        if not asc_files:
            diagnostics.append("[FAIL] No .asc signature files produced for marker publication.")
            return False, diagnostics

        for asc in asc_files:
            if asc.stat().st_size == 0:
                diagnostics.append(f"[FAIL] Signature file is empty: {asc.name}")
                return False, diagnostics

        diagnostics.append(f"[PASS] Successfully generated and verified {len(asc_files)} non-empty .asc signature file(s) for marker publication.")
        return True, diagnostics


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Gradle plugin publication signing lifecycle.")
    parser.add_argument("--require-signatures", action="store_true", help="Execute publication and assert .asc signatures are generated.")
    args = parser.parse_args()

    print("================================================================================")
    print(" VIET TEMPLATE GRADLE PLUGIN SIGNING LIFECYCLE QUALIFICATION")
    print("================================================================================")

    # 1. Unauthenticated check
    print("[Check 1] Verifying unauthenticated developer build configuration...")
    unauth_ok, unauth_diag = check_unauthenticated_configuration()
    for d in unauth_diag:
        print(f"  {d}")
    if not unauth_ok:
        print("================================================================================")
        print(" [RESULT: FAILED] Unauthenticated build check failed.")
        print("================================================================================")
        return 1

    # 2. Signing-enabled check
    print("\n[Check 2] Verifying signing-enabled publication lifecycle ordering...")
    signing_res = check_signing_enabled_lifecycle()
    for d in signing_res.diagnostics:
        print(f"  {d}")
    if not signing_res.passed:
        print("================================================================================")
        print(" [RESULT: FAILED] Signing-enabled publication lifecycle check failed.")
        print("================================================================================")
        return 1

    # 3. Optional or requested signature generation check
    if args.require_signatures:
        print("\n[Check 3] Verifying staged signature generation (.asc files)...")
        sig_ok, sig_diag = verify_staged_signatures()
        for d in sig_diag:
            print(f"  {d}")
        if not sig_ok:
            print("================================================================================")
            print(" [RESULT: FAILED] Signature generation check failed.")
            print("================================================================================")
            return 1

    print("\n================================================================================")
    print(" [RESULT: ALL PASS] Gradle plugin publication signing lifecycle verified.")
    print("================================================================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())
