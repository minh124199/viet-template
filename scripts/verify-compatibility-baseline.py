#!/usr/bin/env python3
"""
verify-compatibility-baseline.py

Authoritative CI verification script enforcing the permanent canonical
compatibility baseline for Viet Template 1.0.0 and the 1.x release line:

1. Canonical Baseline Schema: Validates existence and complete schema of
   config/compatibility/1.0.0-compatibility-baseline.json.
2. Release Provenance: Validates GA tag (v1.0.0), commit SHA, tree SHA, and 1.0.0 version.
3. Public Surface: Enforces exact match of 339 total public surface types,
   121 stable API/SPI types (94 STABLE_API, 27 STABLE_SPI), and bijection with the
   5 public API baselines (core, aot, spring, spring-security, quarkus).
4. Generated Runtime ABI: Enforces exact 7 types, 22 invoked methods, 0 fields.
5. Diagnostic Codes: Enforces exact 31 canonical codes.
6. Framework Entrypoints: Enforces exact 12 framework and build-tool entrypoints.
7. Cross-Module Internal Contracts: Enforces exact 62 registered internal contracts.
8. TCK Language Features: Enforces exact 80 language features.

Fail-Closed:
- Missing baseline, corrupt JSON, or any drift triggers exit code 1 with clear diagnostic.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASELINE = REPO_ROOT / "config" / "compatibility" / "1.0.0-compatibility-baseline.json"
DEFAULT_REPORT = REPO_ROOT / "build" / "reports" / "compatibility-baseline-verification.json"

EXPECTED_VERSION = "1.0.0"
EXPECTED_TAG = "v1.0.0"
EXPECTED_COMMIT = "b951021e9975b8e8103b2402dc244b32a96afaa8"
EXPECTED_TREE = "87425fa306fb3b44ac6f6d435d79dfb95cd0a52a"

EXPECTED_TOTAL_PUBLIC_TYPES = 339
EXPECTED_STABLE_API_COUNT = 94
EXPECTED_STABLE_SPI_COUNT = 27
EXPECTED_TOTAL_STABLE_COUNT = 121

EXPECTED_ABI_TYPES_COUNT = 7
EXPECTED_ABI_METHODS_COUNT = 22
EXPECTED_DIAGNOSTIC_CODES_COUNT = 31
EXPECTED_ENTRYPOINTS_COUNT = 12
EXPECTED_CONTRACTS_COUNT = 62
EXPECTED_TCK_FEATURES_COUNT = 80

API_BASELINE_FILES = [
    "config/api-baseline/1.0-core-public-api.txt",
    "config/api-baseline/1.0-aot-public-api.txt",
    "config/api-baseline/1.0-spring-public-api.txt",
    "config/api-baseline/1.0-spring-security-public-api.txt",
    "config/api-baseline/1.0-quarkus-public-api.txt",
]

# Permitted additive types introduced in post-1.0 minor releases (Viet Template 1.1.x),
# adhering to the 1.x Compatibility Policy in COMPATIBILITY.md (purely additive public APIs).
ALLOWED_ADDITIVE_1_X_TYPES: set[str] = {
    "io.github.minh124199.viettemplate.api.TemplateContract",
    "io.github.minh124199.viettemplate.api.TemplateContract$Builder",
    "io.github.minh124199.viettemplate.api.TemplateParameter",
    "io.github.minh124199.viettemplate.api.SlottedRenderContext",
    "io.github.minh124199.viettemplate.api.TemplateType",
    "io.github.minh124199.viettemplate.api.TemplateType$ArrayType",
    "io.github.minh124199.viettemplate.api.TemplateType$ClassType",
    "io.github.minh124199.viettemplate.api.TemplateType$NamedType",
    "io.github.minh124199.viettemplate.api.TemplateType$ParameterizedType",
    "io.github.minh124199.viettemplate.api.TemplateType$PrimitiveType",
    "io.github.minh124199.viettemplate.api.TemplateType$WildcardType",
    "io.github.minh124199.viettemplate.api.TypeCheckingMode",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateFacadesMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateFacadesTask",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateSchemasMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateSchemasTask",
    "io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateTypeScriptMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateTypeScriptTask",
    "io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer",
    "io.github.minh124199.viettemplate.quarkus.security.QuarkusCsrfView",
    "io.github.minh124199.viettemplate.quarkus.security.QuarkusSecurityViewFactory",
    "io.github.minh124199.viettemplate.validation.TemplateValidator",
    "io.github.minh124199.viettemplate.validation.TemplateValidationRequest",
    "io.github.minh124199.viettemplate.validation.TemplateValidationRequest$Builder",
    "io.github.minh124199.viettemplate.validation.TemplateValidationResult",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateValidateMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateValidateTask",
    "io.github.minh124199.viettemplate.explanation.ExpressionExplanation",
    "io.github.minh124199.viettemplate.explanation.SingleTemplateExplanation",
    "io.github.minh124199.viettemplate.explanation.TemplateExplainRequest",
    "io.github.minh124199.viettemplate.explanation.TemplateExplainRequest$Builder",
    "io.github.minh124199.viettemplate.explanation.TemplateExplainer",
    "io.github.minh124199.viettemplate.explanation.TemplateExplanation",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateExplainMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateExplainTask",
    "io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationContext",
    "io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.OutputSpecializationDecider",
    "io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchDecision",
    "io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.WriteDispatchKind",
    "io.github.minh124199.viettemplate.migration.MigrationCategory",
    "io.github.minh124199.viettemplate.migration.MigrationClassification",
    "io.github.minh124199.viettemplate.migration.MigrationConfidence",
    "io.github.minh124199.viettemplate.migration.MigrationFinding",
    "io.github.minh124199.viettemplate.migration.MigrationReadinessStatus",
    "io.github.minh124199.viettemplate.migration.MigrationReport",
    "io.github.minh124199.viettemplate.migration.MigrationRule",
    "io.github.minh124199.viettemplate.migration.MigrationRuleRegistry",
    "io.github.minh124199.viettemplate.migration.MigrationSeverity",
    "io.github.minh124199.viettemplate.migration.MigrationSummary",
    "io.github.minh124199.viettemplate.migration.SingleTemplateMigration",
    "io.github.minh124199.viettemplate.migration.TemplateMigrationAnalyzer",
    "io.github.minh124199.viettemplate.migration.TemplateMigrationRequest",
    "io.github.minh124199.viettemplate.migration.TemplateMigrationRequest$Builder",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateMigrationReportMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateMigrationReportTask",
}

ALLOWED_ADDITIVE_1_X_ENTRYPOINTS: set[str] = {
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateFacadesMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateFacadesTask",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateSchemasMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateSchemasTask",
    "io.github.minh124199.viettemplate.aot.TypeScriptDeclarationProjector",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateGenerateTypeScriptMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateGenerateTypeScriptTask",
    "io.github.minh124199.viettemplate.lsp.VietTemplateLanguageServer",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateValidateMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateValidateTask",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateExplainMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateExplainTask",
    "io.github.minh124199.viettemplate.tooling.maven.VietTemplateMigrationReportMojo",
    "io.github.minh124199.viettemplate.tooling.gradle.VietTemplateMigrationReportTask",
}

ALLOWED_ADDITIVE_1_X_DIAGNOSTIC_CODES: set[str] = {
    "VTLS:2107",
}

ALLOWED_ADDITIVE_1_X_ABI_METHODS: set[str] = {
    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.countMacroInvocation(io.github.minh124199.viettemplate.api.TemplateOutput)",
    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(java.lang.String, io.github.minh124199.viettemplate.api.TemplateOutput, int, int, java.lang.String)",
    "io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeInteger(java.lang.Integer, io.github.minh124199.viettemplate.api.TemplateOutput, int, java.lang.String)",
}


# =============================================================================
# Helper Parsers
# =============================================================================

def parse_classification_file(path: Path) -> dict[str, str]:
    """Parses public-surface-classification.txt into a mapping of FQCN -> category."""
    if not path.is_file():
        raise FileNotFoundError(f"Classification file not found: {path}")
    mapping: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) >= 2:
            mapping[parts[0]] = parts[1]
    return mapping


def parse_api_baseline_file(path: Path) -> set[str]:
    """Extracts type names from a public API baseline file."""
    if not path.is_file():
        raise FileNotFoundError(f"API baseline file not found: {path}")
    types: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("TYPE "):
            parts = line[5:].strip().split()
            for idx, p in enumerate(parts):
                if p in ("class", "interface", "enum", "record", "@interface") and idx + 1 < len(parts):
                    types.add(parts[idx + 1].split("<")[0].split("(")[0])
                    break
    return types


def parse_runtime_abi_file(path: Path) -> tuple[list[str], list[str]]:
    """Parses generated-template-runtime-abi.txt into (types, invoked_methods)."""
    if not path.is_file():
        raise FileNotFoundError(f"Runtime ABI baseline not found: {path}")
    types: list[str] = []
    invoked_methods: list[str] = []
    current_type = None

    for line in path.read_text(encoding="utf-8").splitlines():
        line_clean = line.strip()
        if not line_clean or line_clean.startswith("#"):
            continue
        if line_clean.startswith("TYPE "):
            parts = line_clean.split()
            if len(parts) >= 2:
                current_type = parts[1]
                types.append(current_type)
        elif line_clean.startswith("MEMBER "):
            sig = line_clean[len("MEMBER "):].strip()
            if "(" in sig:
                # CompiledTemplate methods are implemented by templates, not invoked
                if current_type != "io.github.minh124199.viettemplate.api.CompiledTemplate":
                    parts = sig.split("(")
                    mname = parts[0].split()[-1]
                    params = parts[1].rstrip(")")
                    invoked_methods.append(f"{current_type}.{mname}({params})")

    return sorted(types), sorted(invoked_methods)


def parse_diagnostic_codes_file(path: Path) -> list[str]:
    """Parses diagnostic-codes-1.0.txt into canonical code list."""
    if not path.is_file():
        raise FileNotFoundError(f"Diagnostic codes file not found: {path}")
    codes: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        codes.append(line)
    return codes


# =============================================================================
# Validation Functions
# =============================================================================

def validate_baseline_schema(manifest: Any) -> list[str]:
    """Validates the structure, types, and required sections of the baseline JSON."""
    errors: list[str] = []
    if not isinstance(manifest, dict):
        return ["Baseline root must be a JSON object."]

    if manifest.get("version") != EXPECTED_VERSION:
        errors.append(f"Root 'version' must be '{EXPECTED_VERSION}', got '{manifest.get('version')}'")

    required_sections = [
        "provenance",
        "publicSurface",
        "generatedRuntimeAbi",
        "diagnosticCodes",
        "frameworkEntrypoints",
        "crossModuleContracts",
        "tckLanguageFeatures",
    ]
    for sec in required_sections:
        if sec not in manifest or not isinstance(manifest[sec], dict):
            errors.append(f"Baseline missing required section object: '{sec}'")

    # Validate provenance
    prov = manifest.get("provenance")
    if isinstance(prov, dict):
        for req_field, exp_val in [
            ("version", EXPECTED_VERSION),
            ("tag", EXPECTED_TAG),
            ("commit", EXPECTED_COMMIT),
            ("tree", EXPECTED_TREE),
        ]:
            if prov.get(req_field) != exp_val:
                errors.append(f"Provenance field '{req_field}' mismatch: expected '{exp_val}', got '{prov.get(req_field)}'")

    # Validate publicSurface
    ps = manifest.get("publicSurface")
    if isinstance(ps, dict):
        if ps.get("totalCompiledPublicTypes") != EXPECTED_TOTAL_PUBLIC_TYPES:
            errors.append(f"Public surface 'totalCompiledPublicTypes' must be {EXPECTED_TOTAL_PUBLIC_TYPES}, got {ps.get('totalCompiledPublicTypes')}")
        if ps.get("totalStableCount") != EXPECTED_TOTAL_STABLE_COUNT:
            errors.append(f"Public surface 'totalStableCount' must be {EXPECTED_TOTAL_STABLE_COUNT}, got {ps.get('totalStableCount')}")
        if ps.get("stableApiTypesCount") != EXPECTED_STABLE_API_COUNT:
            errors.append(f"Public surface 'stableApiTypesCount' must be {EXPECTED_STABLE_API_COUNT}, got {ps.get('stableApiTypesCount')}")
        if ps.get("stableSpiTypesCount") != EXPECTED_STABLE_SPI_COUNT:
            errors.append(f"Public surface 'stableSpiTypesCount' must be {EXPECTED_STABLE_SPI_COUNT}, got {ps.get('stableSpiTypesCount')}")

        for list_field, exp_len in [
            ("stableApiTypes", EXPECTED_STABLE_API_COUNT),
            ("stableSpiTypes", EXPECTED_STABLE_SPI_COUNT),
            ("stableTypes", EXPECTED_TOTAL_STABLE_COUNT),
        ]:
            val = ps.get(list_field)
            if not isinstance(val, list) or len(val) != exp_len:
                errors.append(f"Public surface '{list_field}' must be a list of {exp_len} items, got {len(val) if isinstance(val, list) else type(val)}")

        all_types = ps.get("allPublicSurfaceTypes")
        if not isinstance(all_types, dict) or len(all_types) != EXPECTED_TOTAL_PUBLIC_TYPES:
            errors.append(f"Public surface 'allPublicSurfaceTypes' must be a dict of {EXPECTED_TOTAL_PUBLIC_TYPES} items, got {len(all_types) if isinstance(all_types, dict) else type(all_types)}")

    # Validate generatedRuntimeAbi
    abi = manifest.get("generatedRuntimeAbi")
    if isinstance(abi, dict):
        if abi.get("typesCount") != EXPECTED_ABI_TYPES_COUNT:
            errors.append(f"Runtime ABI 'typesCount' must be {EXPECTED_ABI_TYPES_COUNT}, got {abi.get('typesCount')}")
        if abi.get("methodsCount") != EXPECTED_ABI_METHODS_COUNT:
            errors.append(f"Runtime ABI 'methodsCount' must be {EXPECTED_ABI_METHODS_COUNT}, got {abi.get('methodsCount')}")
        types_val = abi.get("types")
        if not isinstance(types_val, list) or len(types_val) != EXPECTED_ABI_TYPES_COUNT:
            errors.append(f"Runtime ABI 'types' must be a list of {EXPECTED_ABI_TYPES_COUNT} items")
        methods_val = abi.get("methods")
        if not isinstance(methods_val, list) or len(methods_val) != EXPECTED_ABI_METHODS_COUNT:
            errors.append(f"Runtime ABI 'methods' must be a list of {EXPECTED_ABI_METHODS_COUNT} items")

    # Validate diagnosticCodes
    dc = manifest.get("diagnosticCodes")
    if isinstance(dc, dict):
        if dc.get("canonicalCodesCount") != EXPECTED_DIAGNOSTIC_CODES_COUNT:
            errors.append(f"Diagnostic codes 'canonicalCodesCount' must be {EXPECTED_DIAGNOSTIC_CODES_COUNT}, got {dc.get('canonicalCodesCount')}")
        codes_val = dc.get("codes")
        if not isinstance(codes_val, list) or len(codes_val) != EXPECTED_DIAGNOSTIC_CODES_COUNT:
            errors.append(f"Diagnostic codes 'codes' must be a list of {EXPECTED_DIAGNOSTIC_CODES_COUNT} items")

    # Validate frameworkEntrypoints
    fe = manifest.get("frameworkEntrypoints")
    if isinstance(fe, dict):
        if fe.get("entrypointsCount") != EXPECTED_ENTRYPOINTS_COUNT:
            errors.append(f"Framework entrypoints 'entrypointsCount' must be {EXPECTED_ENTRYPOINTS_COUNT}, got {fe.get('entrypointsCount')}")
        eps_val = fe.get("entrypoints")
        if not isinstance(eps_val, list) or len(eps_val) != EXPECTED_ENTRYPOINTS_COUNT:
            errors.append(f"Framework entrypoints 'entrypoints' must be a list of {EXPECTED_ENTRYPOINTS_COUNT} items")

    # Validate crossModuleContracts
    cm = manifest.get("crossModuleContracts")
    if isinstance(cm, dict):
        if cm.get("contractsCount") != EXPECTED_CONTRACTS_COUNT:
            errors.append(f"Cross-module contracts 'contractsCount' must be {EXPECTED_CONTRACTS_COUNT}, got {cm.get('contractsCount')}")
        contracts_val = cm.get("contracts")
        if not isinstance(contracts_val, list) or len(contracts_val) != EXPECTED_CONTRACTS_COUNT:
            errors.append(f"Cross-module contracts 'contracts' must be a list of {EXPECTED_CONTRACTS_COUNT} items")

    # Validate tckLanguageFeatures
    tck = manifest.get("tckLanguageFeatures")
    if isinstance(tck, dict):
        if tck.get("featuresCount") != EXPECTED_TCK_FEATURES_COUNT:
            errors.append(f"TCK features 'featuresCount' must be {EXPECTED_TCK_FEATURES_COUNT}, got {tck.get('featuresCount')}")
        feats_val = tck.get("features")
        if not isinstance(feats_val, list) or len(feats_val) != EXPECTED_TCK_FEATURES_COUNT:
            errors.append(f"TCK features 'features' must be a list of {EXPECTED_TCK_FEATURES_COUNT} items")

    return errors


def verify_git_provenance(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies that git repository tags and commit/tree SHAs match the baseline provenance."""
    errors: list[str] = []
    prov = manifest.get("provenance", {})

    exp_tag = prov.get("tag", EXPECTED_TAG)
    exp_commit = prov.get("commit", EXPECTED_COMMIT)
    exp_tree = prov.get("tree", EXPECTED_TREE)

    # Check commit from tag
    proc = subprocess.run(
        ["git", "rev-parse", f"{exp_tag}^{{commit}}"],
        cwd=repo_root,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        # Try fetching tags from origin if working tree is a shallow clone without tags
        subprocess.run(
            ["git", "fetch", "--tags", "origin"],
            cwd=repo_root,
            capture_output=True,
            text=True,
        )
        proc = subprocess.run(
            ["git", "rev-parse", f"{exp_tag}^{{commit}}"],
            cwd=repo_root,
            capture_output=True,
            text=True,
        )

    if proc.returncode != 0:
        errors.append(f"Failed to resolve git tag '{exp_tag}': {proc.stderr.strip()}")
    else:
        act_commit = proc.stdout.strip()
        if act_commit != exp_commit:
            errors.append(f"Git commit mismatch for tag '{exp_tag}': expected {exp_commit}, got {act_commit}")

    # Check tree from tag
    proc_tree = subprocess.run(
        ["git", "rev-parse", f"{exp_tag}^{{tree}}"],
        cwd=repo_root,
        capture_output=True,
        text=True,
    )
    if proc_tree.returncode != 0:
        errors.append(f"Failed to resolve git tree for '{exp_tag}': {proc_tree.stderr.strip()}")
    else:
        act_tree = proc_tree.stdout.strip()
        if act_tree != exp_tree:
            errors.append(f"Git tree mismatch for tag '{exp_tag}': expected {exp_tree}, got {act_tree}")

    return errors


def verify_public_surface(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies public surface classification file and the 5 API baseline files."""
    errors: list[str] = []
    ps = manifest.get("publicSurface", {})

    class_file = repo_root / "config" / "api-baseline" / "public-surface-classification.txt"
    try:
        current_classification = parse_classification_file(class_file)
    except Exception as e:
        return [f"Failed to load classification file: {e}"]

    # 1. Total count (1.0.0 baseline + explicitly registered 1.x additive types)
    expected_total = EXPECTED_TOTAL_PUBLIC_TYPES + len(ALLOWED_ADDITIVE_1_X_TYPES)
    if len(current_classification) != expected_total:
        errors.append(f"Public surface total types mismatch: expected {expected_total} ({EXPECTED_TOTAL_PUBLIC_TYPES} baseline + {len(ALLOWED_ADDITIVE_1_X_TYPES)} 1.x additive), got {len(current_classification)}")

    # 2. Match baseline classification mapping (allowing registered 1.x additive types)
    baseline_types = ps.get("allPublicSurfaceTypes", {})
    missing_in_current = set(baseline_types.keys()) - set(current_classification.keys())
    extra_in_current = (set(current_classification.keys()) - set(baseline_types.keys())) - ALLOWED_ADDITIVE_1_X_TYPES
    category_mismatches = [
        f"{t}: baseline={baseline_types[t]}, current={current_classification[t]}"
        for t in baseline_types
        if t in current_classification and baseline_types[t] != current_classification[t]
    ]
    if missing_in_current:
        errors.append(f"Public surface types missing from classification: {sorted(missing_in_current)[:5]} (total {len(missing_in_current)})")
    if extra_in_current:
        errors.append(f"Unexpected public surface types found in classification: {sorted(extra_in_current)[:5]} (total {len(extra_in_current)})")
    if category_mismatches:
        errors.append(f"Category mismatches between baseline and classification: {category_mismatches[:5]} (total {len(category_mismatches)})")

    # 3. Stable types parity (allowing registered 1.x additive types)
    stable_in_curr = {k for k, v in current_classification.items() if v in ("STABLE_API", "STABLE_SPI")}
    baseline_stable = set(ps.get("stableTypes", []))
    diff1 = baseline_stable - stable_in_curr
    diff2 = (stable_in_curr - baseline_stable) - ALLOWED_ADDITIVE_1_X_TYPES
    if diff1:
        errors.append(f"Stable types missing from current classification: {sorted(diff1)}")
    if diff2:
        errors.append(f"Unexpected stable types in current classification: {sorted(diff2)}")

    # 4. API baseline files parity (disjoint & bijection, allowing registered 1.x additive types)
    discovered_api_types: dict[str, str] = {}  # type -> baseline_name
    for rel_path in API_BASELINE_FILES:
        bf = repo_root / rel_path
        try:
            types_in_file = parse_api_baseline_file(bf)
        except Exception as e:
            errors.append(f"Failed to parse API baseline {rel_path}: {e}")
            continue

        for t in types_in_file:
            if t in discovered_api_types:
                errors.append(f"Duplicate type '{t}' in {rel_path}, already defined in {discovered_api_types[t]}")
            discovered_api_types[t] = rel_path

    all_api_types = set(discovered_api_types.keys())
    diff1 = baseline_stable - all_api_types
    diff2 = (all_api_types - baseline_stable) - ALLOWED_ADDITIVE_1_X_TYPES
    if diff1:
        errors.append(f"Stable types defined in baseline JSON but missing from API baseline files: {sorted(diff1)}")
    if diff2:
        errors.append(f"Types in API baseline files not recognized as stable in baseline JSON: {sorted(diff2)}")

    return errors


def verify_runtime_abi(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies generated-template-runtime-abi.txt and frozen 1.0 ABI baseline against manifest."""
    errors: list[str] = []
    abi = manifest.get("generatedRuntimeAbi", {})

    exp_types = sorted(abi.get("types", []))
    exp_methods = sorted(abi.get("methods", []))

    # 1. Verify frozen historical 1.0 ABI baseline file if present (must match 1.0 manifest with 0 drift)
    hist_file = repo_root / "config" / "api-baseline" / "1.0" / "generated-template-runtime-abi.txt"
    if hist_file.exists():
        try:
            hist_types, hist_methods = parse_runtime_abi_file(hist_file)
            if hist_types != exp_types:
                diff = set(exp_types).symmetric_difference(set(hist_types))
                errors.append(f"Frozen 1.0 Runtime ABI types drift from manifest: {sorted(diff)}")
            if hist_methods != exp_methods:
                diff = set(exp_methods).symmetric_difference(set(hist_methods))
                errors.append(f"Frozen 1.0 Runtime ABI methods drift from manifest: {sorted(diff)}")
        except Exception as e:
            errors.append(f"Failed to load frozen 1.0 Runtime ABI baseline: {e}")

    # 2. Verify active development generated-template-runtime-abi.txt (allows additive 1.x evolution)
    abi_file = repo_root / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
    try:
        curr_types, curr_methods = parse_runtime_abi_file(abi_file)
    except Exception as e:
        return errors + [f"Failed to load Runtime ABI baseline: {e}"]

    if curr_types != exp_types:
        missing_types = set(exp_types) - set(curr_types)
        extra_types = (set(curr_types) - set(exp_types)) - ALLOWED_ADDITIVE_1_X_TYPES
        if missing_types:
            errors.append(f"Runtime ABI types missing: {sorted(missing_types)}")
        if extra_types:
            errors.append(f"Unexpected Runtime ABI types: {sorted(extra_types)}")

    if curr_methods != exp_methods:
        missing_methods = set(exp_methods) - set(curr_methods)
        extra_methods = {
            m for m in (set(curr_methods) - set(exp_methods))
            if not any(m.startswith(t + ".") for t in ALLOWED_ADDITIVE_1_X_TYPES)
            and m not in ALLOWED_ADDITIVE_1_X_ABI_METHODS
        }
        if missing_methods:
            errors.append(f"Runtime ABI methods missing: {sorted(missing_methods)}")
        if extra_methods:
            errors.append(f"Unexpected Runtime ABI methods: {sorted(extra_methods)}")

    return errors


def verify_diagnostic_codes(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies diagnostic-codes-1.0.txt against baseline."""
    errors: list[str] = []
    dc = manifest.get("diagnosticCodes", {})

    diag_file = repo_root / "config" / "api-baseline" / "diagnostic-codes-1.0.txt"
    try:
        curr_codes = parse_diagnostic_codes_file(diag_file)
    except Exception as e:
        return [f"Failed to load diagnostic codes baseline: {e}"]

    exp_codes = dc.get("codes", [])

    if curr_codes != exp_codes:
        missing = set(exp_codes) - set(curr_codes)
        extra = (set(curr_codes) - set(exp_codes)) - ALLOWED_ADDITIVE_1_X_DIAGNOSTIC_CODES
        if missing:
            errors.append(f"Diagnostic codes missing from baseline file: {sorted(missing)}")
        if extra:
            errors.append(f"Unexpected diagnostic codes found in baseline file: {sorted(extra)}")

    return errors


def verify_framework_entrypoints(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies framework-and-tooling-entrypoints.json against baseline."""
    errors: list[str] = []
    fe = manifest.get("frameworkEntrypoints", {})

    ep_file = repo_root / "config" / "architecture" / "framework-and-tooling-entrypoints.json"
    if not ep_file.is_file():
        return [f"Framework entrypoints file not found: {ep_file}"]

    try:
        ep_data = json.loads(ep_file.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"Failed to parse framework entrypoints JSON: {e}"]

    curr_entrypoints = ep_data.get("entrypoints", [])
    exp_entrypoints = fe.get("entrypoints", [])

    curr_fqcns = [e.get("fqcn") for e in curr_entrypoints if isinstance(e, dict) and "fqcn" in e]
    exp_fqcns = [e.get("fqcn") for e in exp_entrypoints if isinstance(e, dict) and "fqcn" in e]

    if curr_fqcns != exp_fqcns:
        missing = set(exp_fqcns) - set(curr_fqcns)
        extra = (set(curr_fqcns) - set(exp_fqcns)) - ALLOWED_ADDITIVE_1_X_ENTRYPOINTS
        if missing:
            errors.append(f"Framework entrypoints missing: {sorted(missing)}")
        if extra:
            errors.append(f"Unexpected framework entrypoints: {sorted(extra)}")

    return errors


def verify_cross_module_contracts(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies cross-module-internal-contracts.json against baseline."""
    errors: list[str] = []
    cm = manifest.get("crossModuleContracts", {})

    cm_file = repo_root / "config" / "architecture" / "cross-module-internal-contracts.json"
    if not cm_file.is_file():
        return [f"Cross-module contracts file not found: {cm_file}"]

    try:
        cm_data = json.loads(cm_file.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"Failed to parse cross-module contracts JSON: {e}"]

    curr_contracts = cm_data.get("contracts", [])
    exp_contracts = cm.get("contracts", [])

    curr_fqcns = [c.get("fqcn") for c in curr_contracts if isinstance(c, dict) and "fqcn" in c]
    exp_fqcns = [c.get("fqcn") for c in exp_contracts if isinstance(c, dict) and "fqcn" in c]

    if curr_fqcns != exp_fqcns:
        missing = set(exp_fqcns) - set(curr_fqcns)
        extra = set(curr_fqcns) - set(exp_fqcns)
        if missing:
            errors.append(f"Cross-module internal contracts missing: {sorted(missing)}")
        if extra:
            errors.append(f"Unexpected cross-module internal contracts: {sorted(extra)}")

    return errors


def verify_tck_features(manifest: dict[str, Any], repo_root: Path) -> list[str]:
    """Verifies vtl-feature-matrix.json against baseline."""
    errors: list[str] = []
    tck = manifest.get("tckLanguageFeatures", {})

    tck_file = repo_root / "config" / "tck" / "vtl-feature-matrix.json"
    if not tck_file.is_file():
        return [f"TCK feature matrix file not found: {tck_file}"]

    try:
        tck_data = json.loads(tck_file.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"Failed to parse TCK feature matrix JSON: {e}"]

    curr_features = tck_data.get("features", [])
    exp_features = tck.get("features", [])

    curr_ids = [f.get("id") for f in curr_features if isinstance(f, dict) and "id" in f]
    exp_ids = [f.get("id") for f in exp_features if isinstance(f, dict) and "id" in f]

    if curr_ids != exp_ids:
        missing = set(exp_ids) - set(curr_ids)
        extra = set(curr_ids) - set(exp_ids)
        if missing:
            errors.append(f"TCK language features missing: {sorted(missing)}")
        if extra:
            errors.append(f"Unexpected TCK language features: {sorted(extra)}")

    return errors


# =============================================================================
# Aggregated Verification Runner
# =============================================================================

def verify_compatibility_baseline(
    baseline_path: Path,
    repo_root: Path = REPO_ROOT,
    check_git: bool = True,
    verbose: bool = False,
) -> tuple[bool, dict[str, list[str]], dict[str, Any]]:
    """Runs all baseline validations and verifications.

    Returns (passed, section_errors, report_data).
    """
    section_errors: dict[str, list[str]] = {}

    # Check baseline file existence
    if not baseline_path.is_file():
        section_errors["baselineFile"] = [f"Baseline file does not exist: {baseline_path}"]
        report_data = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "baselineFile": str(baseline_path),
            "verdict": "FAILED",
            "passed": False,
            "errors": section_errors,
        }
        return False, section_errors, report_data

    # Parse JSON
    try:
        content = baseline_path.read_text(encoding="utf-8")
        manifest = json.loads(content)
    except json.JSONDecodeError as jde:
        section_errors["baselineJson"] = [f"Malformed JSON syntax in baseline file: {jde}"]
        report_data = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "baselineFile": str(baseline_path),
            "verdict": "FAILED",
            "passed": False,
            "errors": section_errors,
        }
        return False, section_errors, report_data
    except Exception as e:
        section_errors["baselineJson"] = [f"Failed to read baseline file: {e}"]
        report_data = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "baselineFile": str(baseline_path),
            "verdict": "FAILED",
            "passed": False,
            "errors": section_errors,
        }
        return False, section_errors, report_data

    # 1. Schema Validation
    schema_errs = validate_baseline_schema(manifest)
    if schema_errs:
        section_errors["schema"] = schema_errs

    # If root is not a dict, stop immediately
    if not isinstance(manifest, dict):
        report_data = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "baselineFile": str(baseline_path),
            "verdict": "FAILED",
            "passed": False,
            "errors": section_errors,
        }
        return False, section_errors, report_data

    # 2. Git Provenance (optional / check if git repo available)
    if check_git:
        git_dir = repo_root / ".git"
        if git_dir.exists():
            prov_errs = verify_git_provenance(manifest, repo_root)
            if prov_errs:
                section_errors["gitProvenance"] = prov_errs

    # 3. Public Surface & API Baselines
    ps_errs = verify_public_surface(manifest, repo_root)
    if ps_errs:
        section_errors["publicSurface"] = ps_errs

    # 4. Generated Runtime ABI
    abi_errs = verify_runtime_abi(manifest, repo_root)
    if abi_errs:
        section_errors["generatedRuntimeAbi"] = abi_errs

    # 5. Diagnostic Codes
    dc_errs = verify_diagnostic_codes(manifest, repo_root)
    if dc_errs:
        section_errors["diagnosticCodes"] = dc_errs

    # 6. Framework Entrypoints
    fe_errs = verify_framework_entrypoints(manifest, repo_root)
    if fe_errs:
        section_errors["frameworkEntrypoints"] = fe_errs

    # 7. Cross-Module Internal Contracts
    cm_errs = verify_cross_module_contracts(manifest, repo_root)
    if cm_errs:
        section_errors["crossModuleContracts"] = cm_errs

    # 8. TCK Language Features
    tck_errs = verify_tck_features(manifest, repo_root)
    if tck_errs:
        section_errors["tckLanguageFeatures"] = tck_errs

    passed = len(section_errors) == 0
    report_data = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "baselineFile": str(baseline_path),
        "verdict": "PASSED" if passed else "FAILED",
        "passed": passed,
        "sections": {
            k: {"status": "FAIL", "errors": v} for k, v in section_errors.items()
        },
    }

    return passed, section_errors, report_data


# =============================================================================
# CLI Main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Enforces canonical 1.0.0 compatibility baseline for Viet Template 1.x line."
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help=f"Path to compatibility baseline JSON (default: {DEFAULT_BASELINE})",
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=REPO_ROOT,
        help=f"Path to repository root (default: {REPO_ROOT})",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help=f"Path to write output audit JSON (default: {DEFAULT_REPORT})",
    )
    parser.add_argument(
        "--skip-git",
        action="store_true",
        help="Skip git tag and commit/tree SHA provenance verification",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable detailed diagnostic logging",
    )

    args = parser.parse_args()

    print("=" * 80)
    print("VIET TEMPLATE 1.0.0 CANONICAL COMPATIBILITY BASELINE AUDIT")
    print("=" * 80)
    print(f"Target Baseline: {args.baseline}")
    print(f"Repository Root: {args.repo_root}")
    print(f"Check Git Provenance: {not args.skip_git}")

    passed, section_errors, report_data = verify_compatibility_baseline(
        baseline_path=args.baseline,
        repo_root=args.repo_root,
        check_git=not args.skip_git,
        verbose=args.verbose,
    )

    # Write report
    try:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2)
            f.write("\n")
        print(f"Report written to: {args.report}")
    except Exception as e:
        print(f"[WARN] Failed to write report to {args.report}: {e}", file=sys.stderr)

    if passed:
        print("\n" + "=" * 80)
        print("[SUCCESS] All 1.0.0 canonical compatibility baseline checks PASSED.")
        print("Provenances, public API/SPI surfaces, runtime ABI, diagnostic codes,")
        print("entrypoints, contracts, and TCK features match exact 1.0.0 baseline.")
        print("=" * 80)
        sys.exit(0)
    else:
        print("\n" + "=" * 80)
        print("[FAIL] Canonical compatibility baseline verification FAILED.")
        print("=" * 80)
        for sec, errs in section_errors.items():
            print(f"\n[SECTION: {sec}] ({len(errs)} error(s)):")
            for err in errs:
                print(f"  - {err}")
        print("\nExit code: 1 (fail-closed)")
        sys.exit(1)


if __name__ == "__main__":
    main()
