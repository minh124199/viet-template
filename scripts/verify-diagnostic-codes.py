#!/usr/bin/env python3
"""
verify-diagnostic-codes.py

Automated CI verification script enforcing Diagnostic-Code 1.0 Candidate Baseline:
1. Discovers all DiagnosticCode definitions and usages across production Java sources (**/src/main/java/**/*.java).
2. Accounts for direct invocations DiagnosticCode.of("CATEGORY", "ID") and references
   to constants such as RenderBudget.CODE_LIMIT_EXCEEDED.
3. Compares discovered codes against config/api-baseline/diagnostic-codes-1.0.txt.
4. Asserts exact match (0 extra codes, 0 missing codes, exactly 31 total).
5. Writes machine-readable report to build/reports/diagnostic-codes.json.
6. Exits 0 on success, non-zero on violation.
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASELINE = REPO_ROOT / "config/api-baseline/diagnostic-codes-1.0.txt"
DEFAULT_REPORT = REPO_ROOT / "build/reports/diagnostic-codes.json"
EXPECTED_DIAGNOSTIC_CODE_COUNT = 31

# Direct DiagnosticCode.of("CATEGORY", "ID") invocation pattern
DIAG_OF_PATTERN = re.compile(
    r'(?:[a-zA-Z0-9_.]+\.)?DiagnosticCode\.of\(\s*"([A-Za-z0-9_]+)"\s*,\s*"([A-Za-z0-9_]+)"\s*\)'
)

# Constant declaration pattern: DiagnosticCode CONST_NAME = DiagnosticCode.of(...) or CONST_NAME = ALIAS
CONST_DECL_PATTERN = re.compile(
    r'(?:public|protected|private)?\s*(?:static\s+final|final\s+static)\s+DiagnosticCode\s+([A-Za-z0-9_]+)\s*=\s*(?:(?:[a-zA-Z0-9_.]+\.)?DiagnosticCode\.of\(\s*"([A-Za-z0-9_]+)"\s*,\s*"([A-Za-z0-9_]+)"\s*\)|([A-Za-z0-9_.]+))\s*;'
)

# Known static constants mapped to their canonical diagnostic code strings
KNOWN_STATIC_CONSTANTS = {
    "RenderBudget.CODE_LIMIT_EXCEEDED": "LIMIT:LIMIT_EXCEEDED",
    "RenderBudget.CODE_TIME_LIMIT": "LIMIT:TIME_LIMIT_EXCEEDED",
}


def parse_baseline(baseline_path: Path) -> set[str]:
    """Parses a diagnostic codes baseline file, ignoring comments and blank lines."""
    codes: set[str] = set()
    if not baseline_path.exists():
        raise FileNotFoundError(f"Baseline file does not exist: {baseline_path}")

    for line in baseline_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        codes.add(line)
    return codes


def find_production_java_files(repo_root: Path) -> list[Path]:
    """Finds all production Java source files under the repository root."""
    files: list[Path] = []
    # Primary: scan all src/main/java/**/*.java ignoring build and dot directories
    for p in repo_root.glob("**/src/main/java/**/*.java"):
        if not any(part.startswith(".") or part == "build" for part in p.relative_to(repo_root).parts):
            files.append(p)

    # Fallback for synthetic unit test structures or non-standard directory layouts
    if not files:
        for p in repo_root.glob("**/*.java"):
            if not any(part.startswith(".") or part == "build" for part in p.relative_to(repo_root).parts):
                files.append(p)

    return sorted(files)


def discover_diagnostic_codes(
    repo_root: Path,
    java_files: list[Path] | None = None,
) -> dict[str, list[dict[str, Any]]]:
    """
    Scans Java source files to discover all DiagnosticCode definitions and references.
    Returns a dictionary mapping diagnostic code (e.g. 'LIMIT:LIMIT_EXCEEDED') to a list
    of occurrences with file, line, match type, and expression.
    """
    if java_files is None:
        java_files = find_production_java_files(repo_root)

    # Copy base known constants
    constants_map: dict[str, str] = dict(KNOWN_STATIC_CONSTANTS)

    # Pass 1: Discover all constant declarations of type DiagnosticCode
    for jf in java_files:
        try:
            content = jf.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        class_name = jf.stem
        for m in CONST_DECL_PATTERN.finditer(content):
            var_name = m.group(1)
            cat = m.group(2)
            cid = m.group(3)
            alias = m.group(4)
            if cat and cid:
                code = f"{cat}:{cid}"
                constants_map[f"{class_name}.{var_name}"] = code
            elif alias:
                if alias in constants_map:
                    constants_map[f"{class_name}.{var_name}"] = constants_map[alias]

    discovered: dict[str, list[dict[str, Any]]] = defaultdict(list)

    # Pass 2: Discover all direct DiagnosticCode.of(...) and constant references
    for jf in java_files:
        try:
            rel = str(jf.relative_to(repo_root))
        except ValueError:
            rel = str(jf)

        try:
            content = jf.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        # 2a: Direct DiagnosticCode.of(...) matches
        for m in DIAG_OF_PATTERN.finditer(content):
            start = m.start()
            line = content.count("\n", 0, start) + 1
            cat, cid = m.group(1), m.group(2)
            code = f"{cat}:{cid}"
            discovered[code].append({
                "file": rel,
                "line": line,
                "type": "DIRECT_INVOCATION",
                "expression": m.group(0),
            })

        # 2b: Constant references (e.g. RenderBudget.CODE_LIMIT_EXCEEDED)
        for const_expr, code in constants_map.items():
            escaped = re.escape(const_expr)
            pattern = re.compile(rf"\b{escaped}\b")
            simple_name = const_expr.split(".")[-1]
            decl_check = "DiagnosticCode " + simple_name

            for m in pattern.finditer(content):
                start = m.start()
                line = content.count("\n", 0, start) + 1

                # Skip the declaration line of the constant itself to avoid duplicate counting
                line_start = content.rfind("\n", 0, start) + 1
                line_end = content.find("\n", start)
                if line_end == -1:
                    line_end = len(content)
                line_text = content[line_start:line_end]
                if decl_check in line_text:
                    continue

                discovered[code].append({
                    "file": rel,
                    "line": line,
                    "type": "CONSTANT_REFERENCE",
                    "expression": const_expr,
                })

    return discovered


def verify_diagnostic_codes(
    repo_root: Path,
    baseline_path: Path,
    report_path: Path | None = None,
    check_exact: bool = False,
    expected_count: int = EXPECTED_DIAGNOSTIC_CODE_COUNT,
) -> tuple[bool, dict[str, Any], list[str]]:
    """
    Verifies discovered diagnostic codes against baseline and generates verification report.
    Returns (passed, report_dict, errors).
    """
    errors: list[str] = []
    baseline_codes = parse_baseline(baseline_path)
    discovered_map = discover_diagnostic_codes(repo_root)
    discovered_codes = set(discovered_map.keys())

    missing_codes = sorted(baseline_codes - discovered_codes)
    extra_codes = sorted(discovered_codes - baseline_codes)

    for code in missing_codes:
        errors.append(f"Missing diagnostic code from production sources: {code}")
    for code in extra_codes:
        errors.append(f"Unexpected diagnostic code not in baseline: {code}")

    if check_exact:
        if len(discovered_codes) != expected_count:
            errors.append(
                f"Expected exactly {expected_count} diagnostic codes, but discovered {len(discovered_codes)}"
            )
        if len(baseline_codes) != expected_count:
            errors.append(
                f"Expected baseline to contain exactly {expected_count} codes, but found {len(baseline_codes)}"
            )

    passed = len(errors) == 0

    report = {
        "version": "1.0",
        "status": "PASSED" if passed else "FAILED",
        "expectedCount": expected_count,
        "totalDiscovered": len(discovered_codes),
        "totalBaseline": len(baseline_codes),
        "exactMatch": len(missing_codes) == 0 and len(extra_codes) == 0 and len(discovered_codes) == expected_count,
        "missingCodes": missing_codes,
        "extraCodes": extra_codes,
        "codes": sorted(list(discovered_codes)),
        "baselineCodes": sorted(list(baseline_codes)),
        "errors": errors,
        "details": {
            code: {
                "category": code.split(":", 1)[0],
                "id": code.split(":", 1)[1] if ":" in code else code,
                "occurrenceCount": len(occurrences),
                "occurrences": occurrences,
            }
            for code, occurrences in sorted(discovered_map.items())
        },
    }

    if report_path is not None:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)

    return passed, report, errors


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Verify production diagnostic codes against 1.0 candidate baseline."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=REPO_ROOT,
        help="Path to repository root (default: repo root containing scripts/)",
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help="Path to baseline file (default: config/api-baseline/diagnostic-codes-1.0.txt)",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help="Path to report file (default: build/reports/diagnostic-codes.json)",
    )
    parser.add_argument(
        "--check-exact",
        action="store_true",
        help=f"Assert exact match and target count (exactly {EXPECTED_DIAGNOSTIC_CODE_COUNT} codes)",
    )
    args = parser.parse_args()

    print("================================================================================")
    print("Viet Template Diagnostic Codes Baseline Verifier (1.0)")
    print("================================================================================")
    print(f"Repository Root : {args.repo_root.resolve()}")
    print(f"Baseline File   : {args.baseline.resolve()}")
    print(f"Report File     : {args.report.resolve()}")
    print(f"Check Exact     : {args.check_exact}")
    print("--------------------------------------------------------------------------------")

    passed, report, errors = verify_diagnostic_codes(
        repo_root=args.repo_root,
        baseline_path=args.baseline,
        report_path=args.report,
        check_exact=args.check_exact,
    )

    print(f"Discovered codes : {report['totalDiscovered']}")
    print(f"Baseline codes   : {report['totalBaseline']}")
    print(f"Missing codes    : {len(report['missingCodes'])}")
    print(f"Extra codes      : {len(report['extraCodes'])}")
    print("--------------------------------------------------------------------------------")

    for code in report["codes"]:
        detail = report["details"].get(code, {})
        count = detail.get("occurrenceCount", 0)
        print(f"  [OK] {code:<35} ({count} occurrences)")

    print("--------------------------------------------------------------------------------")
    if passed:
        print(f"[SUCCESS] Diagnostic codes verification PASSED ({report['totalDiscovered']} stable tooling codes).")
        sys.exit(0)
    else:
        print("[FAILED] Diagnostic codes verification FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)


if __name__ == "__main__":
    main()
