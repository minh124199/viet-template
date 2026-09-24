#!/usr/bin/env python3
"""
verify-exception-semantics.py

Automated CI verification script enforcing exception-boundary semantics:
1. Scans all src/main/java files in production modules.
2. Locates all broad catch blocks catching Exception or Throwable.
3. Matches every broad catch against config/architecture/exception-boundary-allowlist.json:
   - Validates that every production broad catch is registered in the allowlist.
   - Validates that every allowlist entry exists in the production codebase (zero stale entries).
   - Validates that allowlist entries contain non-empty class, method, caughtType, reason, category, owner.
   - Validates that category is one of the 16 canonical categories.
   - Validates that owner is one of the recognized subsystem owners.
4. Generates build/reports/exception-semantics-report.json.
5. Exits 0 on success, non-zero on violation.
"""

import argparse
import json
import os
import re
import sys
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_ALLOWLIST = REPO_ROOT / "config/architecture/exception-boundary-allowlist.json"
DEFAULT_REPORT = REPO_ROOT / "build/reports/exception-semantics-report.json"

PROD_MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-security",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-boot-starter",
    "viet-template-tck",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
]

CANONICAL_CATEGORIES = {
    "EXPECTED_MISS",
    "OPTIONAL_CAPABILITY_PROBE",
    "USER_INPUT_ERROR",
    "RESOLUTION",
    "PARSE",
    "SEMANTIC",
    "COMPILATION",
    "EVALUATION",
    "IO",
    "SECURITY",
    "LINKAGE",
    "FRAMEWORK",
    "INTERRUPTION",
    "LIFECYCLE",
    "INTERNAL",
    "FATAL",
}

CANONICAL_OWNERS = {
    "runtime",
    "engine",
    "compiler",
    "spring",
    "quarkus",
    "tck",
    "api",
}

ALLOWED_CAUGHT_TYPES = {"Exception", "Throwable"}

CONTROL_KEYWORDS = {
    "if",
    "for",
    "while",
    "switch",
    "catch",
    "synchronized",
    "new",
    "return",
    "throw",
}


def strip_comments_and_strings(src: str) -> str:
    """Strips comments and string/char literals while preserving byte offsets and newlines."""
    chars = []
    i = 0
    n = len(src)
    while i < n:
        if src[i : i + 2] == "/*":
            chars.append("  ")
            i += 2
            while i < n and src[i : i + 2] != "*/":
                chars.append("\n" if src[i] == "\n" else " ")
                i += 1
            if i < n:
                chars.append("  ")
                i += 2
        elif src[i : i + 2] == "//":
            chars.append("  ")
            i += 2
            while i < n and src[i] != "\n":
                chars.append(" ")
                i += 1
        elif src[i] == '"':
            chars.append(" ")
            i += 1
            while i < n:
                if src[i] == "\\":
                    chars.append("  ")
                    i += 2
                    continue
                if src[i] == '"':
                    chars.append(" ")
                    i += 1
                    break
                chars.append("\n" if src[i] == "\n" else " ")
                i += 1
        elif src[i] == "'":
            chars.append(" ")
            i += 1
            while i < n:
                if src[i] == "\\":
                    chars.append("  ")
                    i += 2
                    continue
                if src[i] == "'":
                    chars.append(" ")
                    i += 1
                    break
                chars.append("\n" if src[i] == "\n" else " ")
                i += 1
        else:
            chars.append(src[i])
            i += 1
    return "".join(chars)


def extract_fqcn(file_path: str, content: str) -> str:
    """Extracts fully qualified class name from Java source content and file name."""
    pkg = ""
    for line in content.splitlines():
        line = line.strip()
        if line.startswith("package "):
            pkg = line.split()[1].rstrip(";")
            break
    cls = Path(file_path).stem
    return f"{pkg}.{cls}" if pkg else cls


def find_enclosing_method_at(cleaned: str, target_idx: int, class_name: str) -> str:
    """
    Finds the enclosing method or constructor name for a position in cleaned Java source.
    Scans backward tracking brace depth across multi-line declarations.
    """
    depth = 0
    i = target_idx
    while i >= 0:
        ch = cleaned[i]
        if ch == "}":
            depth += 1
        elif ch == "{":
            if depth > 0:
                depth -= 1
            else:
                header_end = i
                j = header_end - 1
                while j >= 0 and cleaned[j] not in (";", "}", "{"):
                    j -= 1
                header = cleaned[j + 1 : header_end].strip()
                # Exclude class/interface/record/enum declarations and lambdas
                if not re.search(r"\b(class|interface|record|enum)\b", header) and "->" not in header:
                    m = re.search(r"([A-Za-z0-9_$]+)\s*\([^\)]*\)\s*(?:throws\s+[^{]+)?$", header)
                    if m:
                        name = m.group(1)
                        if name not in CONTROL_KEYWORDS:
                            return name
        i -= 1
    return class_name


def scan_production_broad_catches(repo_root: Path, modules=PROD_MODULES) -> list:
    """
    Scans src/main/java across specified modules for broad catches (Exception or Throwable).
    Returns list of catch descriptors.
    """
    catches = []
    root = Path(repo_root).resolve()
    for mod in modules:
        src_dir = root / mod / "src/main/java"
        if not src_dir.is_dir():
            continue
        for p in sorted(src_dir.rglob("*.java")):
            raw_content = p.read_text(encoding="utf-8", errors="ignore")
            cleaned = strip_comments_and_strings(raw_content)
            fqcn = extract_fqcn(str(p), raw_content)
            class_simple = p.stem

            for m in re.finditer(r"\bcatch\s*\(\s*([^\)]+)\s*\)", cleaned):
                clause = m.group(1).strip()
                parts = clause.rsplit(None, 1)
                if len(parts) >= 1:
                    type_part = parts[0] if len(parts) > 1 else clause
                    var_name = parts[1] if len(parts) > 1 else ""
                    types = [t.strip() for t in type_part.split("|")]
                    for t in types:
                        simple_type = t.split(".")[-1]
                        if simple_type in ALLOWED_CAUGHT_TYPES:
                            line_num = raw_content[: m.start()].count("\n") + 1
                            method = find_enclosing_method_at(cleaned, m.start(), class_simple)
                            catches.append({
                                "module": mod,
                                "file": str(p.relative_to(root)),
                                "fqcn": fqcn,
                                "method": method,
                                "line": line_num,
                                "caughtType": simple_type,
                                "var": var_name,
                            })
    return catches


def load_and_validate_allowlist(allowlist_path: Path) -> tuple:
    """
    Loads and validates the exception boundary allowlist schema and canonical values.
    Returns (entries, validation_errors).
    """
    path = Path(allowlist_path).resolve()
    if not path.is_file():
        return [], [f"Allowlist file not found: {path}"]

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as ex:
        return [], [f"Failed to parse allowlist JSON at {path}: {ex}"]

    if not isinstance(data, list):
        return [], [f"Allowlist root must be a JSON array, got {type(data).__name__}"]

    errors = []
    required_fields = ["class", "method", "caughtType", "reason", "category", "owner"]
    valid_entries = []

    for idx, entry in enumerate(data):
        if not isinstance(entry, dict):
            errors.append(f"Allowlist entry at index {idx} must be an object, got {type(entry).__name__}")
            continue

        entry_errors = []
        for field in required_fields:
            val = entry.get(field)
            if val is None or not isinstance(val, str) or not val.strip():
                entry_errors.append(f"Missing or empty required field '{field}'")

        if entry_errors:
            errors.append(f"Entry {idx} ({entry.get('class', 'unknown')}#{entry.get('method', 'unknown')}): " + "; ".join(entry_errors))
            continue

        category = entry["category"].strip()
        if category not in CANONICAL_CATEGORIES:
            errors.append(
                f"Entry {idx} ({entry['class']}#{entry['method']}): invalid category '{category}'. "
                f"Must be one of {sorted(CANONICAL_CATEGORIES)}"
            )

        owner = entry["owner"].strip()
        if owner not in CANONICAL_OWNERS:
            errors.append(
                f"Entry {idx} ({entry['class']}#{entry['method']}): invalid owner '{owner}'. "
                f"Must be one of {sorted(CANONICAL_OWNERS)}"
            )

        caught_type = entry["caughtType"].strip()
        if caught_type not in ALLOWED_CAUGHT_TYPES:
            errors.append(
                f"Entry {idx} ({entry['class']}#{entry['method']}): invalid caughtType '{caught_type}'. "
                f"Must be one of {sorted(ALLOWED_CAUGHT_TYPES)}"
            )

        valid_entries.append(entry)

    return valid_entries, errors


def verify_exception_semantics(
    repo_root: Path = None,
    allowlist_path: Path = None,
    report_path: Path = None,
) -> tuple:
    """
    Main verification entrypoint for programmatic and test usage.
    Returns (success: bool, report: dict, errors: list[str]).
    """
    root = Path(repo_root if repo_root is not None else REPO_ROOT).resolve()
    al_path = Path(allowlist_path if allowlist_path is not None else DEFAULT_ALLOWLIST).resolve()
    rep_path = Path(report_path if report_path is not None else DEFAULT_REPORT).resolve()

    allowlist_entries, schema_errors = load_and_validate_allowlist(al_path)
    catches = scan_production_broad_catches(root)

    violations = list(schema_errors)
    stale_entries = []

    # Group detected catches and allowlist entries by (class/fqcn, method, caughtType)
    code_groups = {}
    for c in catches:
        key = (c["fqcn"], c["method"], c["caughtType"])
        code_groups.setdefault(key, []).append(c)

    allowlist_groups = {}
    for a in allowlist_entries:
        key = (a["class"], a["method"], a["caughtType"])
        allowlist_groups.setdefault(key, []).append(a)

    matched_catches = []

    # 1. Check for unallowlisted catches in code
    for key, c_list in sorted(code_groups.items()):
        fqcn, method, c_type = key
        a_list = allowlist_groups.get(key, [])
        if not a_list:
            for c in c_list:
                msg = f"Unallowlisted broad catch in {fqcn}#{method} catching {c_type} at {c['file']}:{c['line']}"
                violations.append(msg)
        elif len(c_list) > len(a_list):
            diff = len(c_list) - len(a_list)
            for c in c_list[len(a_list) :]:
                msg = (
                    f"Unallowlisted broad catch in {fqcn}#{method} catching {c_type} at {c['file']}:{c['line']} "
                    f"(found {len(c_list)} in code, but allowlist only registers {len(a_list)})"
                )
                violations.append(msg)
            for i in range(len(a_list)):
                matched_catches.append({"code": c_list[i], "allowlist": a_list[i]})
        else:
            for i in range(len(c_list)):
                matched_catches.append({"code": c_list[i], "allowlist": a_list[i]})

    # 2. Check for stale allowlist entries
    for key, a_list in sorted(allowlist_groups.items()):
        fqcn, method, c_type = key
        c_list = code_groups.get(key, [])
        if not c_list:
            for a in a_list:
                msg = f"Stale allowlist entry: {fqcn}#{method} catching {c_type} does not exist in production code"
                stale_entries.append(msg)
                violations.append(msg)
        elif len(a_list) > len(c_list):
            diff = len(a_list) - len(c_list)
            msg = (
                f"Stale allowlist entry: {fqcn}#{method} catching {c_type} "
                f"(allowlist registers {len(a_list)}, but code only contains {len(c_list)})"
            )
            stale_entries.append(msg)
            violations.append(msg)

    # 3. Build summary report
    category_counts = Counter(a.get("category") for a in allowlist_entries if "category" in a)
    owner_counts = Counter(a.get("owner") for a in allowlist_entries if "owner" in a)
    caught_type_counts = Counter(c["caughtType"] for c in catches)

    passed = len(violations) == 0

    report = {
        "summary": {
            "totalBroadCatches": len(catches),
            "totalAllowlistEntries": len(allowlist_entries),
            "violationsCount": len(violations),
            "staleEntriesCount": len(stale_entries),
            "passed": passed,
        },
        "categoryBreakdown": dict(sorted(category_counts.items())),
        "ownerBreakdown": dict(sorted(owner_counts.items())),
        "caughtTypeBreakdown": dict(sorted(caught_type_counts.items())),
        "violations": violations,
        "staleEntries": stale_entries,
        "matchedCatches": matched_catches,
    }

    try:
        rep_path.parent.mkdir(parents=True, exist_ok=True)
        with open(rep_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
    except Exception as ex:
        violations.append(f"Failed to write report to {rep_path}: {ex}")
        passed = False

    return passed, report, violations


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify exception-boundary semantics and allowlist compliance across production modules."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=REPO_ROOT,
        help="Path to repository root (default: repository root containing this script)",
    )
    parser.add_argument(
        "--allowlist",
        type=Path,
        default=DEFAULT_ALLOWLIST,
        help="Path to exception boundary allowlist JSON (default: config/architecture/exception-boundary-allowlist.json)",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help="Path to write report JSON (default: build/reports/exception-semantics-report.json)",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose output",
    )
    args = parser.parse_args()

    print("================================================================================")
    print("Viet Template Exception Boundary CI Verifier (Milestone M6)")
    print("================================================================================")
    print(f"Repository Root : {args.repo_root.resolve()}")
    print(f"Allowlist File  : {args.allowlist.resolve()}")
    print(f"Report File     : {args.report.resolve()}")
    print("--------------------------------------------------------------------------------")

    passed, report, violations = verify_exception_semantics(
        repo_root=args.repo_root,
        allowlist_path=args.allowlist,
        report_path=args.report,
    )

    print(f"Total Broad Catches Scanned  : {report['summary']['totalBroadCatches']}")
    print(f"Total Allowlist Entries      : {report['summary']['totalAllowlistEntries']}")
    print(f"Caught Type Breakdown        : {report['caughtTypeBreakdown']}")
    print("\nCanonical Category Breakdown :")
    for cat, count in report["categoryBreakdown"].items():
        print(f"  {cat:<28}: {count}")
    print("\nSubsystem Owner Breakdown    :")
    for owner, count in report["ownerBreakdown"].items():
        print(f"  {owner:<28}: {count}")

    print("--------------------------------------------------------------------------------")

    if not passed:
        print(f"[FAIL] Found {len(violations)} exception semantics violation(s):")
        for v in violations:
            print(f"  - {v}")
        print("--------------------------------------------------------------------------------")
        print(f"Detailed report generated at: {args.report.resolve()}")
        return 1

    print("[PASS] Exception semantics verified successfully! Zero unallowlisted catches and zero stale entries.")
    print(f"Report written to: {args.report.resolve()}")
    print("================================================================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())
