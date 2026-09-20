#!/usr/bin/env python3
"""
scripts/verify-tck-coverage.py

Verifies completeness, schema validity, and 100% test coverage of the
authoritative VTL language feature claim matrix (config/tck/vtl-feature-matrix.json)
against registered TCK conformance test scenarios.

Enforces:
1. Matrix JSON schema validity (structure, field types, required properties)
2. Presence of all 20 durable ID categories:
   LEX, REF, PROP, IDX, METH, EXPR, TRUTH, SET, IF, FOREACH, MACRO, DYN,
   STATE3, STRICT, SEC, ERR, UNICODE, APP, DIFF, EXT
3. Valid required execution backends (IR, AOT_BYTECODE)
4. Classification and rationale enforcement for intentional differences and extensions
5. 100% test coverage of all claimed features in TckSuiteRegistry
"""

import sys
import os
import re
import json
from pathlib import Path

REQUIRED_PREFIXES = [
    "LEX", "REF", "PROP", "IDX", "METH", "EXPR", "TRUTH", "SET",
    "IF", "FOREACH", "MACRO", "DYN", "STATE3", "STRICT", "SEC",
    "ERR", "UNICODE", "APP", "DIFF", "EXT"
]

VALID_STATUSES = {"SUPPORTED", "INTENTIONAL_DIFFERENCE", "EXTENSION"}
VALID_CLASSIFICATIONS = {"EXACT_MATCH", "EXPECTED_DIFFERENCE", "VIET_EXTENSION"}
VALID_BACKENDS = {"IR", "AOT_BYTECODE"}

ID_PATTERN = re.compile(
    r"^(LEX|REF|PROP|IDX|METH|EXPR|TRUTH|SET|IF|FOREACH|MACRO|DYN|STATE3|STRICT|SEC|ERR|UNICODE|APP|DIFF|EXT)-[0-9]{3}$"
)


def validate_matrix(matrix_data):
    errors = []

    if not isinstance(matrix_data, dict):
        return ["Matrix root must be a JSON object"]

    version = matrix_data.get("version")
    if not version or not isinstance(version, str):
        errors.append("Matrix must declare string 'version'")

    title = matrix_data.get("title")
    if not title or not isinstance(title, str):
        errors.append("Matrix must declare string 'title'")

    features = matrix_data.get("features")
    if not isinstance(features, list) or len(features) == 0:
        errors.append("Matrix must declare non-empty 'features' array")
        return errors

    seen_ids = set()
    found_prefixes = set()

    for idx, feat in enumerate(features):
        if not isinstance(feat, dict):
            errors.append(f"Feature at index {idx} must be a JSON object")
            continue

        feat_id = feat.get("id")
        if not feat_id or not isinstance(feat_id, str):
            errors.append(f"Feature at index {idx} missing valid 'id'")
            continue

        if not ID_PATTERN.match(feat_id):
            errors.append(f"Feature '{feat_id}' does not match durable ID pattern (PREFIX-NNN)")

        prefix = feat_id.split("-")[0]
        found_prefixes.add(prefix)

        if feat_id in seen_ids:
            errors.append(f"Duplicate feature ID detected: '{feat_id}'")
        seen_ids.add(feat_id)

        category = feat.get("category")
        if not category or not isinstance(category, str):
            errors.append(f"Feature '{feat_id}' missing 'category'")

        name = feat.get("name")
        if not name or not isinstance(name, str):
            errors.append(f"Feature '{feat_id}' missing 'name'")

        description = feat.get("description")
        if not description or not isinstance(description, str):
            errors.append(f"Feature '{feat_id}' missing 'description'")

        status = feat.get("status")
        if status not in VALID_STATUSES:
            errors.append(f"Feature '{feat_id}' has invalid status '{status}'; must be one of {VALID_STATUSES}")

        classification = feat.get("classification")
        if classification not in VALID_CLASSIFICATIONS:
            errors.append(f"Feature '{feat_id}' has invalid classification '{classification}'; must be one of {VALID_CLASSIFICATIONS}")

        required_backends = feat.get("requiredBackends")
        if not isinstance(required_backends, list) or len(required_backends) == 0:
            errors.append(f"Feature '{feat_id}' must declare non-empty 'requiredBackends' array")
        else:
            for b in required_backends:
                if b not in VALID_BACKENDS:
                    errors.append(f"Feature '{feat_id}' has invalid backend '{b}'; must be in {VALID_BACKENDS}")

        spec_ref = feat.get("specificationReference")
        if not spec_ref or not isinstance(spec_ref, str):
            errors.append(f"Feature '{feat_id}' missing 'specificationReference'")

        # Validate difference and extension rules
        if status == "INTENTIONAL_DIFFERENCE":
            if not feat_id.startswith("DIFF-"):
                errors.append(f"Feature '{feat_id}' with INTENTIONAL_DIFFERENCE must use DIFF- prefix")
            if classification != "EXPECTED_DIFFERENCE":
                errors.append(f"Feature '{feat_id}' must have classification EXPECTED_DIFFERENCE")
            rationale = feat.get("rationale")
            if not rationale or not isinstance(rationale, str) or len(rationale.strip()) == 0:
                errors.append(f"Feature '{feat_id}' (INTENTIONAL_DIFFERENCE) requires non-empty 'rationale'")

        if status == "EXTENSION":
            if not feat_id.startswith("EXT-"):
                errors.append(f"Feature '{feat_id}' with EXTENSION must use EXT- prefix")
            if classification != "VIET_EXTENSION":
                errors.append(f"Feature '{feat_id}' must have classification VIET_EXTENSION")
            rationale = feat.get("rationale")
            if not rationale or not isinstance(rationale, str) or len(rationale.strip()) == 0:
                errors.append(f"Feature '{feat_id}' (EXTENSION) requires non-empty 'rationale'")

    missing_prefixes = set(REQUIRED_PREFIXES) - found_prefixes
    if missing_prefixes:
        errors.append(f"Matrix missing required categories: {sorted(missing_prefixes)}")

    return errors


def extract_covered_features(registry_file):
    if not registry_file.exists():
        return set(), [f"Suite registry file not found: {registry_file}"]

    content = registry_file.read_text(encoding="utf-8")

    covered = set()
    for m in re.finditer(r'builder\(\s*["\'][^"\']+["\']\s*,\s*["\']([^"\']+)["\']', content):
        covered.add(m.group(1))
    for m in re.finditer(r'featureId\(["\']([^"\']+)["\']\)', content):
        covered.add(m.group(1))
    return covered, []


def main():
    repo_root = Path(__file__).resolve().parent.parent
    matrix_file = repo_root / "config" / "tck" / "vtl-feature-matrix.json"
    registry_file = (
        repo_root
        / "viet-template-tck"
        / "src"
        / "main"
        / "java"
        / "io"
        / "github"
        / "minh124199"
        / "viettemplate"
        / "tck"
        / "conformance"
        / "suite"
        / "TckSuiteRegistry.java"
    )

    print("================================================================================")
    print("VIET TEMPLATE LANGUAGE (VTL) TCK COVERAGE VERIFICATION")
    print("================================================================================")
    print(f"Matrix file:   {matrix_file}")
    print(f"Registry file: {registry_file}")

    if not matrix_file.exists():
        print(f"[FAIL] Feature matrix not found at: {matrix_file}", file=sys.stderr)
        sys.exit(1)

    try:
        matrix_data = json.loads(matrix_file.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"[FAIL] Invalid JSON in feature matrix: {e}", file=sys.stderr)
        sys.exit(1)

    matrix_errors = validate_matrix(matrix_data)
    if matrix_errors:
        print(f"[FAIL] Feature matrix schema validation failed with {len(matrix_errors)} error(s):")
        for err in matrix_errors:
            print(f"  - {err}")
        sys.exit(1)

    features = matrix_data["features"]
    print(f"[PASS] Matrix schema valid: {len(features)} features declared across {len(REQUIRED_PREFIXES)} categories.")

    covered_features, registry_errors = extract_covered_features(registry_file)
    if registry_errors:
        print(f"[FAIL] Registry extraction failed: {registry_errors}", file=sys.stderr)
        sys.exit(1)

    print(f"[INFO] Discovered {len(covered_features)} covered feature IDs in TckSuiteRegistry.")

    # Check coverage for each feature
    all_feature_ids = [f["id"] for f in features]
    uncovered = [fid for fid in all_feature_ids if fid not in covered_features]

    # Category breakdown
    categories = {}
    for f in features:
        cat = f["category"]
        if cat not in categories:
            categories[cat] = {"total": 0, "covered": 0}
        categories[cat]["total"] += 1
        if f["id"] in covered_features:
            categories[cat]["covered"] += 1

    print("\nFeature Category Coverage Summary:")
    print("--------------------------------------------------------------------------------")
    print(f"{'Category':<25} | {'Total':<8} | {'Covered':<8} | {'Coverage':<8}")
    print("--------------------------------------------------------------------------------")
    for cat in sorted(categories.keys()):
        tot = categories[cat]["total"]
        cov = categories[cat]["covered"]
        pct = (cov / tot * 100.0) if tot > 0 else 0.0
        print(f"{cat:<25} | {tot:<8} | {cov:<8} | {pct:>6.1f}%")
    print("--------------------------------------------------------------------------------")

    total_count = len(all_feature_ids)
    covered_count = total_count - len(uncovered)
    total_pct = (covered_count / total_count * 100.0) if total_count > 0 else 0.0

    print(f"Total Features:   {total_count}")
    print(f"Covered Features: {covered_count}")
    print(f"Coverage Rate:    {total_pct:.2f}%")

    if uncovered:
        print(f"\n[FAIL] 100% coverage requirement not met! Missing test coverage for {len(uncovered)} feature(s):")
        for fid in uncovered:
            print(f"  - {fid}")
        sys.exit(1)

    print("\n[SUCCESS] 100% TCK coverage verified! All claimed features have active conformance tests.")
    sys.exit(0)


if __name__ == "__main__":
    main()
