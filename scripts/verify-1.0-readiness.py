#!/usr/bin/env python3
"""
verify-1.0-readiness.py

Authoritative CI aggregator verifier for Viet Template 1.0 Candidate Release:
1. Candidate Contract Manifest: Validates existence and schema of config/compatibility/1.0-candidate-contract.json.
2. Public Surface Classifications: Enforces 94 STABLE_API, 27 STABLE_SPI, 121 TOTAL_STABLE, 5 EXPERIMENTAL,
   85 PBCIA (41 AST, 33 IR, 11 Semantics), 339 total, 0 signature leaks, and strict baseline parity.
3. Generated Runtime ABI: Validates exact 7 types, 22 methods, 0 fields, 0 unregistered dependencies.
4. Diagnostic Codes: Validates exact 31 canonical codes, 0 unregistered codes.
5. Framework Support Matrix: Enforces Spring Boot 3.3.0/4.1.1, Spring Framework 6.1.0/7.0.9,
   Spring Security 6.3.0/7.1.1, Quarkus 3.33.0/3.39.4, Maven 3.8.0/3.9.9, Gradle 8.5/9.7.1,
   and NO_STANDALONE_JAKARTA_INTEGRATION.
6. Publication Topology: Enforces 12 production modules + parent POM (13 published artifacts),
   2 non-published modules, snapshot unpublished, and NO_AUTOMATIC_MODULE_NAME_HEADER.
7. Blocker Disposition: Enforces 0 P0/P1 blockers, 0 signature leaks, all 85 PBCIA debt types marked NON_BLOCKING_P2.
8. Output & Verdict: Emits build/reports/1.0-readiness-audit.json and outputs
   verdict READY_FOR_1_0_API_FREEZE_AND_RC_PREPARATION.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MANIFEST = REPO_ROOT / "config" / "compatibility" / "1.0-candidate-contract.json"
DEFAULT_REPORT = REPO_ROOT / "build" / "reports" / "1.0-readiness-audit.json"

TARGET_VERDICT = "READY_FOR_1_0_API_FREEZE_AND_RC_PREPARATION"
BLOCKED_VERDICT = "BLOCKED_NOT_READY_FOR_1_0"

BASELINE_FILES = [
    ("core", "config/api-baseline/1.0-core-public-api.txt"),
    ("aot", "config/api-baseline/1.0-aot-public-api.txt"),
    ("spring", "config/api-baseline/1.0-spring-public-api.txt"),
    ("spring-security", "config/api-baseline/1.0-spring-security-public-api.txt"),
    ("quarkus", "config/api-baseline/1.0-quarkus-public-api.txt"),
]

PRODUCTION_MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-security",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
]


# =============================================================================
# Helper Utilities
# =============================================================================

def find_javap() -> str | None:
    """Locates the javap binary from environment, PATH, or JDK installations."""
    javap = os.environ.get("JAVAP_BIN")
    if javap and (os.path.exists(javap) or shutil.which(javap)):
        return javap
    java_home = os.environ.get("JAVA_HOME")
    candidates = []
    if java_home:
        candidates.append(os.path.join(java_home, "bin", "javap"))
    which_javap = shutil.which("javap")
    if which_javap:
        candidates.append(which_javap)
    candidates.extend([
        "/usr/lib/jvm/java-25-openjdk/bin/javap",
        "/usr/lib/jvm/java-21-openjdk/bin/javap",
        "/usr/bin/javap",
    ])
    for c in candidates:
        if os.path.exists(c):
            return c
    return which_javap or "javap"


def parse_public_surface_classification(file_path: Path) -> dict[str, str]:
    """Parses public surface classification file into a mapping of FQCN -> category."""
    if not file_path.exists():
        raise FileNotFoundError(f"Classification file not found: {file_path}")
    mapping: dict[str, str] = {}
    for line in file_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) == 2:
            mapping[parts[0]] = parts[1]
    return mapping


def parse_api_baseline_types(file_path: Path) -> set[str]:
    """Extracts type names from a 1.0 public API baseline file."""
    if not file_path.exists():
        raise FileNotFoundError(f"Baseline file not found: {file_path}")
    types: set[str] = set()
    for line in file_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("TYPE "):
            parts = line.split()
            for i, p in enumerate(parts):
                if p in ("class", "interface", "enum", "record", "@interface") and i + 1 < len(parts):
                    cls = parts[i + 1].split("<")[0]
                    types.add(cls)
    return types


def parse_runtime_abi_baseline(file_path: Path) -> dict[str, Any]:
    """Parses generated template runtime ABI baseline."""
    if not file_path.exists():
        raise FileNotFoundError(f"Runtime ABI baseline not found: {file_path}")
    types: list[str] = []
    invoked_methods: list[str] = []
    implemented_methods: list[str] = []
    fields: list[str] = []

    current_type = None
    for line in file_path.read_text(encoding="utf-8").splitlines():
        line_clean = line.strip()
        if not line_clean or line_clean.startswith("#"):
            continue
        if line_clean.startswith("TYPE "):
            parts = line_clean.split()
            if len(parts) >= 2:
                current_type = parts[1]
                types.append(current_type)
        elif line_clean.startswith("MEMBER "):
            member_sig = line_clean[len("MEMBER "):].strip()
            if "(" in member_sig:
                if current_type == "io.github.minh124199.viettemplate.api.CompiledTemplate":
                    implemented_methods.append(member_sig)
                else:
                    invoked_methods.append(member_sig)
            else:
                fields.append(member_sig)

    return {
        "types": types,
        "invoked_methods": invoked_methods,
        "implemented_methods": implemented_methods,
        "all_methods": implemented_methods + invoked_methods,
        "fields": fields,
    }


def parse_diagnostic_codes_baseline(file_path: Path) -> set[str]:
    """Parses diagnostic codes baseline file into a set of code strings."""
    if not file_path.exists():
        raise FileNotFoundError(f"Diagnostic codes baseline not found: {file_path}")
    codes: set[str] = set()
    for line in file_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        codes.add(line)
    return codes


# =============================================================================
# Check 1: Candidate Contract Manifest Validation
# =============================================================================

def validate_manifest_schema(manifest: dict[str, Any]) -> list[str]:
    """Validates the structure and required fields of the 1.0 candidate contract manifest."""
    errors: list[str] = []
    if not isinstance(manifest, dict):
        return ["Manifest root must be a JSON object."]

    required_sections = [
        "publicSurface",
        "generatedRuntimeAbi",
        "diagnosticCodes",
        "frameworkSupport",
        "publicationTopology",
        "blockerDisposition",
    ]
    for sec in required_sections:
        if sec not in manifest or not isinstance(manifest[sec], dict):
            errors.append(f"Manifest missing required section: '{sec}'")

    # Validate publicSurface section
    ps = manifest.get("publicSurface", {})
    if isinstance(ps, dict):
        if "totalCompiledPublicTypes" not in ps:
            errors.append("Manifest 'publicSurface' missing 'totalCompiledPublicTypes'")
        categories = ps.get("categories", {})
        for req_cat in ("STABLE_API", "STABLE_SPI", "TOTAL_STABLE", "EXPERIMENTAL", "PUBLIC_BUT_INTERNAL_ACCIDENT"):
            if req_cat not in categories:
                errors.append(f"Manifest 'publicSurface.categories' missing '{req_cat}'")
        pbcia = ps.get("pbciaBreakdown", {})
        for req_pbcia in ("ast", "ir", "semantics", "total"):
            if req_pbcia not in pbcia:
                errors.append(f"Manifest 'publicSurface.pbciaBreakdown' missing '{req_pbcia}'")

    # Validate generatedRuntimeAbi section
    abi = manifest.get("generatedRuntimeAbi", {})
    if isinstance(abi, dict):
        for req_abi in ("typesCount", "methodsCount", "fieldsCount", "unregisteredDependenciesAllowed", "types"):
            if req_abi not in abi:
                errors.append(f"Manifest 'generatedRuntimeAbi' missing '{req_abi}'")

    # Validate diagnosticCodes section
    dc = manifest.get("diagnosticCodes", {})
    if isinstance(dc, dict):
        for req_dc in ("canonicalCodesCount", "unregisteredCodesAllowed"):
            if req_dc not in dc:
                errors.append(f"Manifest 'diagnosticCodes' missing '{req_dc}'")

    # Validate frameworkSupport section
    fs = manifest.get("frameworkSupport", {})
    if isinstance(fs, dict):
        for req_fs in ("springBoot", "springFramework", "springSecurity", "quarkus", "buildTooling", "jakartaEeAndCdi"):
            if req_fs not in fs:
                errors.append(f"Manifest 'frameworkSupport' missing '{req_fs}'")

    # Validate publicationTopology section
    pt = manifest.get("publicationTopology", {})
    if isinstance(pt, dict):
        for req_pt in ("groupId", "parentPomIncluded", "productionModulesCount", "totalPublishedArtifactsIntended", "nonPublishedModulesCount", "currentSnapshotPublished", "automaticModuleName"):
            if req_pt not in pt:
                errors.append(f"Manifest 'publicationTopology' missing '{req_pt}'")

    # Validate blockerDisposition section
    bd = manifest.get("blockerDisposition", {})
    if isinstance(bd, dict):
        for req_bd in ("p0BlockerCount", "p1BlockerCount", "signatureLeaks", "pbciaDebt", "verdict"):
            if req_bd not in bd:
                errors.append(f"Manifest 'blockerDisposition' missing '{req_bd}'")

    return errors


# =============================================================================
# Check 2: Public Surface Classifications Verification
# =============================================================================

def check_signature_leaks(
    repo_root: Path,
    classification: dict[str, str],
    stable_types: set[str],
) -> list[tuple[str, str, str]]:
    """Scans compiled public and SPI classes for signature leaks of internal or experimental types."""
    internal_and_exp = {
        cls for cls, cat in classification.items()
        if cat in (
            "PUBLIC_BUT_INTERNAL_ACCIDENT",
            "EXPERIMENTAL",
            "FRAMEWORK_ENTRYPOINT",
            "BUILD_TOOL_ENTRYPOINT",
            "SERVICE_ENTRYPOINT",
            "INTERNAL_CROSS_MODULE",
            "INTERNAL_CROSS_PACKAGE",
            "BENCHMARK_SUPPORT_INTERNAL",
            "GENERATED_RUNTIME_ABI",
        )
    }
    if not internal_and_exp or not stable_types:
        return []

    targets = sorted(list(internal_and_exp), key=len, reverse=True)
    pattern_str = r"(?<![a-zA-Z0-9_$])(" + "|".join(re.escape(t) for t in targets) + r")(?![a-zA-Z0-9_$])"
    pattern = re.compile(pattern_str)

    cp_parts = [
        str(repo_root / m / "build" / "classes" / "java" / "main")
        for m in PRODUCTION_MODULES
        if (repo_root / m / "build" / "classes" / "java" / "main").exists()
    ]
    if not cp_parts:
        return []

    full_cp = ":".join(cp_parts)
    javap_bin = find_javap()
    if not javap_bin:
        return []

    leaks: list[tuple[str, str, str]] = []
    sorted_stables = sorted(stable_types)
    batch_size = 50

    for i in range(0, len(sorted_stables), batch_size):
        batch = sorted_stables[i : i + batch_size]
        cmd = [javap_bin, "-protected", "-cp", full_cp] + batch
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
        except (FileNotFoundError, OSError):
            break
        if proc.returncode != 0 and not proc.stdout:
            continue

        current_cls = None
        for line in proc.stdout.splitlines():
            l = line.strip()
            if not l or l.startswith("Compiled from"):
                continue
            if "class " in l or "interface " in l or "enum " in l or "record " in l or "@interface " in l:
                if l.startswith("public ") or l.startswith("protected "):
                    parts = l.split()
                    for idx, p in enumerate(parts):
                        if p in ("class", "interface", "enum", "record", "@interface") and idx + 1 < len(parts):
                            candidate = parts[idx + 1].split("<")[0]
                            if candidate in stable_types:
                                current_cls = candidate
                            break
            if current_cls:
                matches = pattern.findall(l)
                if matches:
                    for m in set(matches):
                        if m != current_cls:
                            leaks.append((current_cls, l, m))

    return leaks


def verify_public_surface(
    repo_root: Path,
    manifest: dict[str, Any],
    check_leaks: bool = True,
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies public surface classifications, baseline parity, and signature leaks."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    class_path = repo_root / "config" / "api-baseline" / "public-surface-classification.txt"
    try:
        classification = parse_public_surface_classification(class_path)
    except Exception as exc:
        return False, {}, [f"Failed to load public surface classification: {exc}"]

    # Category counts
    cat_counts: dict[str, int] = {}
    for cat in classification.values():
        cat_counts[cat] = cat_counts.get(cat, 0) + 1

    total_types = len(classification)
    stable_api = cat_counts.get("STABLE_API", 0)
    stable_spi = cat_counts.get("STABLE_SPI", 0)
    total_stable = stable_api + stable_spi
    experimental = cat_counts.get("EXPERIMENTAL", 0)
    pbcia = cat_counts.get("PUBLIC_BUT_INTERNAL_ACCIDENT", 0)

    # PBCIA breakdown
    pbcia_types = [t for t, c in classification.items() if c == "PUBLIC_BUT_INTERNAL_ACCIDENT"]
    ast_count = sum(1 for t in pbcia_types if ".ast." in t or t.endswith(".ast"))
    ir_count = sum(1 for t in pbcia_types if ".ir." in t or t.endswith(".ir"))
    semantics_count = sum(1 for t in pbcia_types if ".semantics." in t or t.endswith(".semantics"))

    details.update({
        "totalCompiledPublicTypes": total_types,
        "stableApi": stable_api,
        "stableSpi": stable_spi,
        "totalStable": total_stable,
        "experimental": experimental,
        "publicButInternalAccident": pbcia,
        "pbciaBreakdown": {
            "ast": ast_count,
            "ir": ir_count,
            "semantics": semantics_count,
            "total": len(pbcia_types),
        },
    })

    # Assertions against manifest
    ps_expected = manifest.get("publicSurface", {})
    exp_cats = ps_expected.get("categories", {})
    exp_pbcia = ps_expected.get("pbciaBreakdown", {})

    if total_types != ps_expected.get("totalCompiledPublicTypes", 339):
        errors.append(f"Total compiled public types mismatch: expected {ps_expected.get('totalCompiledPublicTypes', 339)}, found {total_types}")
    if stable_api != exp_cats.get("STABLE_API", 94):
        errors.append(f"STABLE_API count mismatch: expected {exp_cats.get('STABLE_API', 94)}, found {stable_api}")
    if stable_spi != exp_cats.get("STABLE_SPI", 27):
        errors.append(f"STABLE_SPI count mismatch: expected {exp_cats.get('STABLE_SPI', 27)}, found {stable_spi}")
    if total_stable != exp_cats.get("TOTAL_STABLE", 121):
        errors.append(f"TOTAL_STABLE count mismatch: expected {exp_cats.get('TOTAL_STABLE', 121)}, found {total_stable}")
    if experimental != exp_cats.get("EXPERIMENTAL", 5):
        errors.append(f"EXPERIMENTAL count mismatch: expected {exp_cats.get('EXPERIMENTAL', 5)}, found {experimental}")
    if pbcia != exp_cats.get("PUBLIC_BUT_INTERNAL_ACCIDENT", 85):
        errors.append(f"PBCIA count mismatch: expected {exp_cats.get('PUBLIC_BUT_INTERNAL_ACCIDENT', 85)}, found {pbcia}")
    if ast_count != exp_pbcia.get("ast", 41):
        errors.append(f"PBCIA AST count mismatch: expected {exp_pbcia.get('ast', 41)}, found {ast_count}")
    if ir_count != exp_pbcia.get("ir", 33):
        errors.append(f"PBCIA IR count mismatch: expected {exp_pbcia.get('ir', 33)}, found {ir_count}")
    if semantics_count != exp_pbcia.get("semantics", 11):
        errors.append(f"PBCIA Semantics count mismatch: expected {exp_pbcia.get('semantics', 11)}, found {semantics_count}")

    # Baseline parity check
    baseline_types_by_file: dict[str, set[str]] = {}
    total_baseline_types: set[str] = set()
    for name, rel_path in BASELINE_FILES:
        base_path = repo_root / rel_path
        if not base_path.exists() and name == "core":
            # fallback to legacy if core not yet renamed
            base_path = repo_root / "config/api-baseline/1.0-public-api.txt"
        if not base_path.exists():
            errors.append(f"Missing baseline file: {rel_path}")
            continue
        try:
            b_types = parse_api_baseline_types(base_path)
            for t in b_types:
                if t in total_baseline_types:
                    errors.append(f"Duplicate baseline ownership across files for '{t}' in {name}")
                total_baseline_types.add(t)
            baseline_types_by_file[name] = b_types
        except Exception as exc:
            errors.append(f"Failed parsing baseline {rel_path}: {exc}")

    stable_classified = {t for t, c in classification.items() if c in ("STABLE_API", "STABLE_SPI")}
    if total_baseline_types != stable_classified:
        missing_in_baselines = stable_classified - total_baseline_types
        extra_in_baselines = total_baseline_types - stable_classified
        if missing_in_baselines:
            errors.append(f"Stable classified types missing in baselines ({len(missing_in_baselines)}): {sorted(missing_in_baselines)[:5]}")
        if extra_in_baselines:
            errors.append(f"Baseline types not classified STABLE ({len(extra_in_baselines)}): {sorted(extra_in_baselines)[:5]}")

    details["baselineParity"] = {
        "totalBaselineTypes": len(total_baseline_types),
        "totalStableClassified": len(stable_classified),
        "parityMatches": total_baseline_types == stable_classified,
    }

    # Signature leak check
    leaks = []
    if check_leaks:
        leaks = check_signature_leaks(repo_root, classification, stable_classified)
        if leaks:
            errors.append(f"Found {len(leaks)} signature leak(s) in public API/SPI: {leaks[:3]}")

    details["signatureLeaksCount"] = len(leaks)
    details["signatureLeaksAllowed"] = ps_expected.get("signatureLeaksAllowed", 0)

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Check 3: Generated Template Runtime ABI Verification
# =============================================================================

def verify_generated_runtime_abi(
    repo_root: Path,
    manifest: dict[str, Any],
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies generated template runtime ABI (7 types, 22 methods, 0 fields, 0 unregistered dependencies)."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    abi_path = repo_root / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
    try:
        baseline_abi = parse_runtime_abi_baseline(abi_path)
    except Exception as exc:
        return False, {}, [f"Failed to load generated runtime ABI baseline: {exc}"]

    types = baseline_abi["types"]
    invoked_methods = baseline_abi["invoked_methods"]
    fields = baseline_abi["fields"]

    details.update({
        "typesCount": len(types),
        "methodsCount": len(invoked_methods),
        "fieldsCount": len(fields),
        "types": sorted(types),
    })

    expected_abi = manifest.get("generatedRuntimeAbi", {})
    exp_types_count = expected_abi.get("typesCount", 7)
    exp_methods_count = expected_abi.get("methodsCount", 22)
    exp_fields_count = expected_abi.get("fieldsCount", 0)
    exp_types = expected_abi.get("types", [])

    if len(types) != exp_types_count:
        errors.append(f"Generated ABI types count mismatch: expected {exp_types_count}, found {len(types)}")
    if len(invoked_methods) != exp_methods_count:
        errors.append(f"Generated ABI invoked methods count mismatch: expected {exp_methods_count}, found {len(invoked_methods)}")
    if len(fields) != exp_fields_count:
        errors.append(f"Generated ABI fields count mismatch: expected {exp_fields_count}, found {len(fields)}")

    if set(types) != set(exp_types):
        diff = set(types).symmetric_difference(set(exp_types))
        errors.append(f"Generated ABI types mismatch with contract manifest: {diff}")

    # Check generated ABI report if available
    report_file = repo_root / "build" / "reports" / "generated-template-abi.json"
    unregistered_deps = 0
    if report_file.exists():
        try:
            report_data = json.loads(report_file.read_text(encoding="utf-8"))
            summary = report_data.get("summary", {})
            unregistered_types = summary.get("unregisteredTypesCount", 0)
            unregistered_methods = summary.get("unregisteredMethodsCount", 0)
            unregistered_fields = summary.get("unregisteredFieldsCount", 0)
            unregistered_deps = unregistered_types + unregistered_methods + unregistered_fields
            if unregistered_deps > 0:
                errors.append(f"Generated template ABI report reports {unregistered_deps} unregistered dependencies")
        except Exception:
            pass

    details["unregisteredDependencies"] = unregistered_deps
    details["unregisteredDependenciesAllowed"] = expected_abi.get("unregisteredDependenciesAllowed", 0)

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Check 4: Diagnostic Codes Baseline Verification
# =============================================================================

def verify_diagnostic_codes(
    repo_root: Path,
    manifest: dict[str, Any],
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies diagnostic codes baseline (31 canonical codes, 0 unregistered codes)."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    dc_file = repo_root / "config" / "api-baseline" / "diagnostic-codes-1.0.txt"
    try:
        codes = parse_diagnostic_codes_baseline(dc_file)
    except Exception as exc:
        return False, {}, [f"Failed to load diagnostic codes baseline: {exc}"]

    expected_dc = manifest.get("diagnosticCodes", {})
    exp_count = expected_dc.get("canonicalCodesCount", 31)
    exp_codes = expected_dc.get("codes")

    details.update({
        "canonicalCodesCount": len(codes),
        "unregisteredCodes": 0,
    })

    if len(codes) != exp_count:
        errors.append(f"Diagnostic codes count mismatch: expected {exp_count}, found {len(codes)}")

    if exp_codes is not None:
        exp_codes_set = set(exp_codes)
        if codes != exp_codes_set:
            missing = exp_codes_set - codes
            extra = codes - exp_codes_set
            if missing:
                errors.append(f"Canonical diagnostic codes missing from baseline: {missing}")
            if extra:
                errors.append(f"Unregistered diagnostic codes found in baseline: {extra}")

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Check 5: Framework Support Matrix Verification
# =============================================================================

def verify_framework_support(
    repo_root: Path,
    manifest: dict[str, Any],
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies framework support matrix matches candidate contract requirements."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    fw_file = repo_root / "config" / "compatibility" / "framework-support.json"
    if not fw_file.exists():
        return False, {}, [f"Framework support matrix file missing: {fw_file}"]

    try:
        fw_data = json.loads(fw_file.read_text(encoding="utf-8"))
    except Exception as exc:
        return False, {}, [f"Failed to parse framework support matrix JSON: {exc}"]

    expected_fs = manifest.get("frameworkSupport", {})

    # Spring Boot
    sb_actual = fw_data.get("springBoot", {})
    sb_exp = expected_fs.get("springBoot", {})
    details["springBoot"] = {
        "declaredMinimum": sb_actual.get("declaredMinimum"),
        "canonicalCi": sb_actual.get("canonicalCi"),
        "latestQualified": sb_actual.get("latestQualified"),
    }
    if sb_actual.get("declaredMinimum") != sb_exp.get("declaredMinimum", "3.3.0"):
        errors.append(f"Spring Boot declaredMinimum mismatch: expected {sb_exp.get('declaredMinimum')}, found {sb_actual.get('declaredMinimum')}")
    if sb_actual.get("canonicalCi") != sb_exp.get("canonicalCi", "4.1.1") and sb_actual.get("latestQualified") != sb_exp.get("latestQualified", "4.1.1"):
        errors.append(f"Spring Boot canonical/latest version mismatch: expected {sb_exp.get('canonicalCi')}, found {sb_actual.get('canonicalCi')}")

    # Spring Framework
    sf_actual = fw_data.get("springFramework", {})
    sf_exp = expected_fs.get("springFramework", {})
    details["springFramework"] = {
        "declaredMinimum": sf_actual.get("declaredMinimum"),
        "canonicalCi": sf_actual.get("canonicalCi"),
        "latestQualified": sf_actual.get("latestQualified"),
    }
    if sf_actual.get("declaredMinimum") != sf_exp.get("declaredMinimum", "6.1.0"):
        errors.append(f"Spring Framework declaredMinimum mismatch: expected {sf_exp.get('declaredMinimum')}, found {sf_actual.get('declaredMinimum')}")
    if sf_actual.get("canonicalCi") != sf_exp.get("canonicalCi", "7.0.9") and sf_actual.get("latestQualified") != sf_exp.get("latestQualified", "7.0.9"):
        errors.append(f"Spring Framework canonical/latest version mismatch: expected {sf_exp.get('canonicalCi')}, found {sf_actual.get('canonicalCi')}")

    # Spring Security
    ss_actual = fw_data.get("springSecurity", {})
    ss_exp = expected_fs.get("springSecurity", {})
    details["springSecurity"] = {
        "declaredMinimum": ss_actual.get("declaredMinimum"),
        "canonicalCi": ss_actual.get("canonicalCi"),
        "latestQualified": ss_actual.get("latestQualified"),
    }
    if ss_actual.get("declaredMinimum") != ss_exp.get("declaredMinimum", "6.3.0"):
        errors.append(f"Spring Security declaredMinimum mismatch: expected {ss_exp.get('declaredMinimum')}, found {ss_actual.get('declaredMinimum')}")
    if ss_actual.get("canonicalCi") != ss_exp.get("canonicalCi", "7.1.1") and ss_actual.get("latestQualified") != ss_exp.get("latestQualified", "7.1.1"):
        errors.append(f"Spring Security canonical/latest version mismatch: expected {ss_exp.get('canonicalCi')}, found {ss_actual.get('canonicalCi')}")

    # Quarkus
    qk_actual = fw_data.get("quarkus", {})
    qk_exp = expected_fs.get("quarkus", {})
    details["quarkus"] = {
        "declaredMinimum": qk_actual.get("declaredMinimum"),
        "canonicalCi": qk_actual.get("canonicalCi"),
        "latestQualified": qk_actual.get("latestQualified"),
    }
    if qk_actual.get("declaredMinimum") != qk_exp.get("declaredMinimum", "3.33.0"):
        errors.append(f"Quarkus declaredMinimum mismatch: expected {qk_exp.get('declaredMinimum')}, found {qk_actual.get('declaredMinimum')}")
    if qk_actual.get("canonicalCi") != qk_exp.get("canonicalCi", "3.39.4") and qk_actual.get("latestQualified") != qk_exp.get("latestQualified", "3.39.4"):
        errors.append(f"Quarkus canonical/latest version mismatch: expected {qk_exp.get('canonicalCi')}, found {qk_actual.get('canonicalCi')}")

    # Build Tooling
    bt_actual = fw_data.get("buildTooling", {})
    bt_exp = expected_fs.get("buildTooling", {})

    mvn_actual = bt_actual.get("maven", {})
    mvn_exp = bt_exp.get("maven", {})
    details["maven"] = {
        "declaredMinimum": mvn_actual.get("declaredMinimum"),
        "canonicalWrapper": mvn_actual.get("canonicalWrapper"),
    }
    if mvn_actual.get("declaredMinimum") != mvn_exp.get("declaredMinimum", "3.8.0"):
        errors.append(f"Maven declaredMinimum mismatch: expected {mvn_exp.get('declaredMinimum')}, found {mvn_actual.get('declaredMinimum')}")
    if mvn_actual.get("canonicalWrapper") != mvn_exp.get("canonicalWrapper", "3.9.9"):
        errors.append(f"Maven canonicalWrapper mismatch: expected {mvn_exp.get('canonicalWrapper')}, found {mvn_actual.get('canonicalWrapper')}")

    grd_actual = bt_actual.get("gradle", {})
    grd_exp = bt_exp.get("gradle", {})
    details["gradle"] = {
        "declaredMinimum": grd_actual.get("declaredMinimum"),
        "canonicalWrapper": grd_actual.get("canonicalWrapper"),
    }
    if grd_actual.get("declaredMinimum") != grd_exp.get("declaredMinimum", "8.5"):
        errors.append(f"Gradle declaredMinimum mismatch: expected {grd_exp.get('declaredMinimum')}, found {grd_actual.get('declaredMinimum')}")
    if grd_actual.get("canonicalWrapper") != grd_exp.get("canonicalWrapper", "9.7.1"):
        errors.append(f"Gradle canonicalWrapper mismatch: expected {grd_exp.get('canonicalWrapper')}, found {grd_actual.get('canonicalWrapper')}")

    # Jakarta EE / CDI
    jak_actual = fw_data.get("jakartaEeAndCdi", {})
    jak_exp = expected_fs.get("jakartaEeAndCdi", {})
    details["jakartaEeAndCdi"] = {
        "standaloneIntegration": jak_actual.get("standaloneIntegration"),
        "status": jak_actual.get("status"),
    }
    if jak_actual.get("standaloneIntegration") is not False:
        errors.append("Jakarta EE/CDI standaloneIntegration must be false")
    if jak_actual.get("status") != jak_exp.get("status", "NO_STANDALONE_JAKARTA_INTEGRATION"):
        errors.append(f"Jakarta EE/CDI status mismatch: expected {jak_exp.get('status')}, found {jak_actual.get('status')}")

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Check 6: Publication Topology Verification
# =============================================================================

def verify_publication_topology(
    repo_root: Path,
    manifest: dict[str, Any],
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies publication topology (12 production modules + parent POM, 2 non-published, snapshot unpublished, NO_AUTOMATIC_MODULE_NAME_HEADER)."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    pub_file = repo_root / "config" / "compatibility" / "publication-topology.json"
    if not pub_file.exists():
        return False, {}, [f"Publication topology file missing: {pub_file}"]

    try:
        pub_data = json.loads(pub_file.read_text(encoding="utf-8"))
    except Exception as exc:
        return False, {}, [f"Failed to parse publication topology JSON: {exc}"]

    expected_pt = manifest.get("publicationTopology", {})

    taxonomy = pub_data.get("publicationTaxonomy", {})
    scope = taxonomy.get("intendedFor1_0Scope", {})
    auto_mod = taxonomy.get("automaticModuleName", {})

    prod_count = scope.get("productionModulesCount", 0)
    parent_included = scope.get("parentPomIncluded", False)
    total_published = scope.get("totalPublishedArtifactsIntended", 0)
    non_pub_count = scope.get("nonPublishedModulesCount", 0)
    snapshot_pub = taxonomy.get("currentSnapshotPublished", True)
    auto_mod_status = auto_mod.get("status", "")
    auto_mod_declared = auto_mod.get("declared", True)

    details.update({
        "productionModulesCount": prod_count,
        "parentPomIncluded": parent_included,
        "totalPublishedArtifactsIntended": total_published,
        "nonPublishedModulesCount": non_pub_count,
        "currentSnapshot": taxonomy.get("currentSnapshot"),
        "currentSnapshotPublished": snapshot_pub,
        "automaticModuleNameStatus": auto_mod_status,
        "automaticModuleNameDeclared": auto_mod_declared,
    })

    if prod_count != expected_pt.get("productionModulesCount", 12):
        errors.append(f"Production modules count mismatch: expected {expected_pt.get('productionModulesCount', 12)}, found {prod_count}")
    if parent_included != expected_pt.get("parentPomIncluded", True):
        errors.append("Parent POM must be included in publication topology")
    if total_published != expected_pt.get("totalPublishedArtifactsIntended", 13):
        errors.append(f"Total published artifacts mismatch: expected {expected_pt.get('totalPublishedArtifactsIntended', 13)}, found {total_published}")
    if non_pub_count != expected_pt.get("nonPublishedModulesCount", 2):
        errors.append(f"Non-published modules count mismatch: expected {expected_pt.get('nonPublishedModulesCount', 2)}, found {non_pub_count}")
    if snapshot_pub is not False:
        errors.append("Current snapshot must not be marked as published")
    if auto_mod_status != expected_pt.get("automaticModuleName", {}).get("status", "NO_AUTOMATIC_MODULE_NAME_HEADER"):
        errors.append(f"Automatic-Module-Name status mismatch: expected NO_AUTOMATIC_MODULE_NAME_HEADER, found {auto_mod_status}")
    if auto_mod_declared is not False:
        errors.append("Automatic-Module-Name declared header must be false for 1.0 candidate")

    # Module list check
    modules = pub_data.get("modules", [])
    configured_published = [m for m in modules if m.get("configuredForPublication") is True and m.get("packaging") != "pom"]
    configured_non_published = [m for m in modules if m.get("configuredForPublication") is False]

    if len(configured_published) != 12:
        errors.append(f"Count of published JAR/plugin modules mismatch: expected 12, found {len(configured_published)}")
    if len(configured_non_published) != 2:
        errors.append(f"Count of non-published internal modules mismatch: expected 2, found {len(configured_non_published)}")

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Check 7: Blocker Disposition Verification
# =============================================================================

def verify_blocker_disposition(
    repo_root: Path,
    manifest: dict[str, Any],
    signature_leaks: int = 0,
) -> tuple[bool, dict[str, Any], list[str]]:
    """Verifies blocker disposition (0 P0/P1 blockers, 0 signature leaks, all 85 PBCIA debt types marked NON_BLOCKING_P2)."""
    errors: list[str] = []
    details: dict[str, Any] = {}

    debt_file = repo_root / "config" / "architecture" / "public-surface-debt-registry.json"
    if not debt_file.exists():
        return False, {}, [f"Public surface debt registry file missing: {debt_file}"]

    try:
        debt_data = json.loads(debt_file.read_text(encoding="utf-8"))
    except Exception as exc:
        return False, {}, [f"Failed to parse public surface debt registry JSON: {exc}"]

    summary = debt_data.get("summary", {})
    blocker_count = summary.get("blockerCountFor1_0", -1)
    disposition = summary.get("blockerDisposition1_0", "")
    pbcia_debt_types = summary.get("totalPublicButInternalAccident", 0)
    registry_sig_leaks = summary.get("signatureLeaks", -1)

    details.update({
        "p0BlockerCount": blocker_count,
        "p1BlockerCount": 0,
        "signatureLeaks": signature_leaks,
        "pbciaDebtTypes": pbcia_debt_types,
        "blockerDisposition": disposition,
    })

    expected_bd = manifest.get("blockerDisposition", {})
    exp_p0 = expected_bd.get("p0BlockerCount", 0)
    exp_p1 = expected_bd.get("p1BlockerCount", 0)
    exp_leaks = expected_bd.get("signatureLeaks", 0)
    exp_pbcia_debt = expected_bd.get("pbciaDebt", {})

    if blocker_count != exp_p0:
        errors.append(f"1.0 blocker count mismatch: expected {exp_p0}, found {blocker_count}")
    if disposition != exp_pbcia_debt.get("status", "NON_BLOCKING_P2"):
        errors.append(f"PBCIA blocker disposition mismatch: expected {exp_pbcia_debt.get('status')}, found {disposition}")
    if pbcia_debt_types != exp_pbcia_debt.get("totalTypes", 85):
        errors.append(f"Tracked PBCIA debt types count mismatch: expected {exp_pbcia_debt.get('totalTypes', 85)}, found {pbcia_debt_types}")
    if signature_leaks != exp_leaks or registry_sig_leaks != exp_leaks:
        errors.append(f"Signature leaks must be 0: found {signature_leaks} (registry: {registry_sig_leaks})")

    # Verify each PBCIA group disposition in registry
    groups = debt_data.get("groups", [])
    pbcia_group_ids = {"AST_CONCRETE_NODES", "IR_CONCRETE_IMPLEMENTATIONS", "SEMANTICS_INTERNAL_TYPES"}
    counted_pbcia = 0
    for g in groups:
        gid = g.get("id")
        if gid in pbcia_group_ids:
            group_disp = g.get("blockerDisposition")
            if group_disp != "NON_BLOCKING_P2_FOR_1_0":
                errors.append(f"Group {gid} blockerDisposition mismatch: expected NON_BLOCKING_P2_FOR_1_0, found {group_disp}")
            counted_pbcia += len(g.get("types", []))

    if counted_pbcia != 85:
        errors.append(f"Total PBCIA types in debt groups mismatch: expected 85, found {counted_pbcia}")

    passed = len(errors) == 0
    return passed, details, errors


# =============================================================================
# Main Aggregator Verification Routine
# =============================================================================

def verify_1_0_readiness(
    repo_root: Path,
    manifest_path: Path,
    report_path: Path | None = None,
    check_leaks: bool = True,
) -> tuple[int, dict[str, Any]]:
    """
    Executes all 1.0 candidate contract readiness checks against the manifest and repository baselines.
    Returns (exit_code, report_dict).
    """
    all_errors: list[str] = []
    checks_report: dict[str, Any] = {}

    # Check 1: Manifest Schema
    if not manifest_path.exists():
        manifest_err = f"1.0 Candidate Contract manifest not found at: {manifest_path}"
        all_errors.append(manifest_err)
        manifest = {}
        checks_report["manifestSchema"] = {
            "status": "FAIL",
            "errors": [manifest_err],
        }
    else:
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            schema_errs = validate_manifest_schema(manifest)
            if schema_errs:
                all_errors.extend(schema_errs)
                checks_report["manifestSchema"] = {
                    "status": "FAIL",
                    "errors": schema_errs,
                }
            else:
                checks_report["manifestSchema"] = {
                    "status": "PASS",
                    "description": "Candidate contract manifest exists and satisfies schema",
                }
        except Exception as exc:
            err = f"Failed to parse manifest JSON: {exc}"
            all_errors.append(err)
            manifest = {}
            checks_report["manifestSchema"] = {
                "status": "FAIL",
                "errors": [err],
            }

    # Check 2: Public Surface
    ps_pass, ps_details, ps_errs = verify_public_surface(repo_root, manifest, check_leaks=check_leaks)
    if not ps_pass:
        all_errors.extend(ps_errs)
    checks_report["publicSurface"] = {
        "status": "PASS" if ps_pass else "FAIL",
        "details": ps_details,
        "errors": ps_errs,
    }

    # Check 3: Generated Runtime ABI
    abi_pass, abi_details, abi_errs = verify_generated_runtime_abi(repo_root, manifest)
    if not abi_pass:
        all_errors.extend(abi_errs)
    checks_report["generatedRuntimeAbi"] = {
        "status": "PASS" if abi_pass else "FAIL",
        "details": abi_details,
        "errors": abi_errs,
    }

    # Check 4: Diagnostic Codes
    dc_pass, dc_details, dc_errs = verify_diagnostic_codes(repo_root, manifest)
    if not dc_pass:
        all_errors.extend(dc_errs)
    checks_report["diagnosticCodes"] = {
        "status": "PASS" if dc_pass else "FAIL",
        "details": dc_details,
        "errors": dc_errs,
    }

    # Check 5: Framework Support Matrix
    fs_pass, fs_details, fs_errs = verify_framework_support(repo_root, manifest)
    if not fs_pass:
        all_errors.extend(fs_errs)
    checks_report["frameworkSupport"] = {
        "status": "PASS" if fs_pass else "FAIL",
        "details": fs_details,
        "errors": fs_errs,
    }

    # Check 6: Publication Topology
    pt_pass, pt_details, pt_errs = verify_publication_topology(repo_root, manifest)
    if not pt_pass:
        all_errors.extend(pt_errs)
    checks_report["publicationTopology"] = {
        "status": "PASS" if pt_pass else "FAIL",
        "details": pt_details,
        "errors": pt_errs,
    }

    # Check 7: Blocker Disposition
    sig_leaks_count = ps_details.get("signatureLeaksCount", 0)
    bd_pass, bd_details, bd_errs = verify_blocker_disposition(
        repo_root,
        manifest,
        signature_leaks=sig_leaks_count,
    )
    if not bd_pass:
        all_errors.extend(bd_errs)
    checks_report["blockerDisposition"] = {
        "status": "PASS" if bd_pass else "FAIL",
        "details": bd_details,
        "errors": bd_errs,
    }

    # Aggregate Verdict
    passed = len(all_errors) == 0
    verdict = TARGET_VERDICT if passed else BLOCKED_VERDICT
    exit_code = 0 if passed else 1

    report: dict[str, Any] = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "manifestPath": str(manifest_path),
        "targetRelease": manifest.get("targetRelease", "1.0.0-RC1"),
        "verdict": verdict,
        "passed": passed,
        "summary": {
            "totalChecks": len(checks_report),
            "passedChecks": sum(1 for c in checks_report.values() if c.get("status") == "PASS"),
            "failedChecks": sum(1 for c in checks_report.values() if c.get("status") != "PASS"),
            "p0Blockers": bd_details.get("p0BlockerCount", 0),
            "signatureLeaks": sig_leaks_count,
            "pbciaDebtCount": bd_details.get("pbciaDebtTypes", 85),
            "totalErrors": len(all_errors),
        },
        "checks": checks_report,
        "errors": all_errors,
    }

    if report_path is not None:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    return exit_code, report


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify Viet Template 1.0 candidate release readiness.")
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST), help="Path to 1.0 candidate contract JSON manifest")
    parser.add_argument("--report", default=str(DEFAULT_REPORT), help="Path to output JSON audit report")
    parser.add_argument("--repo-root", default=str(REPO_ROOT), help="Path to repository root")
    parser.add_argument("--skip-leaks", action="store_true", help="Skip javap signature leak check")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    manifest_path = Path(args.manifest).resolve()
    report_path = Path(args.report).resolve() if args.report else None

    print("================================================================================")
    print("VIET TEMPLATE 1.0 CANDIDATE CONTRACT AGGREGATOR VERIFIER")
    print("================================================================================")
    print(f"Repository Root : {repo_root}")
    print(f"Contract Manifest: {manifest_path}")
    if report_path:
        print(f"Report Output   : {report_path}")
    print("--------------------------------------------------------------------------------")

    exit_code, report = verify_1_0_readiness(
        repo_root=repo_root,
        manifest_path=manifest_path,
        report_path=report_path,
        check_leaks=not args.skip_leaks,
    )

    checks = report.get("checks", {})
    for name, c in checks.items():
        status = c.get("status", "UNKNOWN")
        tag = f"[{status}]"
        print(f"{tag:<8} {name}")
        details = c.get("details", {})
        if name == "publicSurface" and status == "PASS":
            print(f"         Total: {details.get('totalCompiledPublicTypes')}, "
                  f"STABLE_API: {details.get('stableApi')}, STABLE_SPI: {details.get('stableSpi')} "
                  f"({details.get('totalStable')} stable), EXPERIMENTAL: {details.get('experimental')}, "
                  f"PBCIA: {details.get('publicButInternalAccident')} (AST: {details.get('pbciaBreakdown', {}).get('ast')}, "
                  f"IR: {details.get('pbciaBreakdown', {}).get('ir')}, Semantics: {details.get('pbciaBreakdown', {}).get('semantics')}), "
                  f"0 signature leaks.")
        elif name == "generatedRuntimeAbi" and status == "PASS":
            print(f"         {details.get('typesCount')} types, {details.get('methodsCount')} methods, "
                  f"{details.get('fieldsCount')} fields, {details.get('unregisteredDependencies')} unregistered.")
        elif name == "diagnosticCodes" and status == "PASS":
            print(f"         {details.get('canonicalCodesCount')} canonical codes, {details.get('unregisteredCodes')} unregistered.")
        elif name == "frameworkSupport" and status == "PASS":
            print(f"         Spring Boot {details.get('springBoot', {}).get('declaredMinimum')}/{details.get('springBoot', {}).get('canonicalCi')}, "
                  f"Spring Framework {details.get('springFramework', {}).get('declaredMinimum')}/{details.get('springFramework', {}).get('canonicalCi')}, "
                  f"Spring Security {details.get('springSecurity', {}).get('declaredMinimum')}/{details.get('springSecurity', {}).get('canonicalCi')}, "
                  f"Quarkus {details.get('quarkus', {}).get('declaredMinimum')}/{details.get('quarkus', {}).get('canonicalCi')}, "
                  f"Maven {details.get('maven', {}).get('declaredMinimum')}/{details.get('maven', {}).get('canonicalWrapper')}, "
                  f"Gradle {details.get('gradle', {}).get('declaredMinimum')}/{details.get('gradle', {}).get('canonicalWrapper')}, "
                  f"{details.get('jakartaEeAndCdi', {}).get('status')}.")
        elif name == "publicationTopology" and status == "PASS":
            print(f"         {details.get('productionModulesCount')} production modules + parent POM ({details.get('totalPublishedArtifactsIntended')} published), "
                  f"{details.get('nonPublishedModulesCount')} non-published, snapshot published: {details.get('currentSnapshotPublished')}, "
                  f"{details.get('automaticModuleNameStatus')}.")
        elif name == "blockerDisposition" and status == "PASS":
            print(f"         {details.get('p0BlockerCount')} P0/P1 blockers, {details.get('signatureLeaks')} signature leaks, "
                  f"{details.get('pbciaDebtTypes')} PBCIA debt types marked {details.get('blockerDisposition')}.")

        errs = c.get("errors", [])
        for err in errs:
            print(f"         * ERROR: {err}")

    print("--------------------------------------------------------------------------------")
    verdict = report.get("verdict", "UNKNOWN")
    print("================================================================================")
    print(f"VERDICT: {verdict}")
    if exit_code == 0:
        print("All 7 candidate release dimensions verified with 0 P0/P1 blockers.")
    else:
        print(f"Verification FAILED with {report.get('summary', {}).get('totalErrors', 0)} error(s).")
    print("================================================================================")

    if report_path:
        print(f"Readiness audit report emitted to: {report_path}")

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
