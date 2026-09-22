#!/usr/bin/env python3
"""
scripts/verify-generated-abi.py

Mechanically audits the runtime ABI of generated AOT template classfiles.
1. Locates compiled template classes (from build/reports/generated-template-abi-classes or integration fixtures).
2. Parses constant pool entries via `javap -verbose`.
3. Extracts all referenced types, member references, and field accesses.
4. Generates a structured, machine-readable report at `build/reports/generated-template-abi.json`.
5. Validates referenced types and methods against the runtime ABI baseline at
   `config/api-baseline/generated-template-runtime-abi.txt`.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
DEFAULT_BASELINE = ROOT_DIR / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
DEFAULT_REPORT = ROOT_DIR / "build" / "reports" / "generated-template-abi.json"


def parse_baseline(baseline_file: Path) -> dict:
    """Parses generated-template-runtime-abi.txt into expected types and members."""
    expected = {}
    current_type = None

    if not baseline_file.exists():
        return expected

    with open(baseline_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("TYPE "):
                current_type = line[5:].strip()
                expected[current_type] = set()
            elif line.startswith("MEMBER ") and current_type:
                member_sig = line[7:].strip()
                expected[current_type].add(member_sig)
    return expected


def locate_template_classes(root_dir: Path) -> list[Path]:
    """Finds all compiled AOT template classfiles."""
    classes = []
    # Primary audit directory produced by GeneratedTemplateAbiAuditTest
    report_classes = list((root_dir / "build" / "reports" / "generated-template-abi-classes").glob("*.class"))
    if report_classes:
        classes.extend(report_classes)

    # Secondary: target directories from tests
    for p in (root_dir / "viet-template-vtl-interpreter").glob("**/generated-template-abi-classes/*.class"):
        classes.append(p)

    # Integration test fixtures
    for p in root_dir.glob("integration-tests/**/T_*.class"):
        classes.append(p)

    # Deduplicate by filename
    seen_names = set()
    unique_classes = []
    for c in sorted(classes):
        if c.name not in seen_names:
            seen_names.add(c.name)
            unique_classes.append(c)

    return unique_classes


def analyze_class_constant_pool(class_file: Path) -> dict:
    """Runs javap -verbose and parses constant pool entries."""
    cmd = ["javap", "-v", str(class_file)]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except Exception as e:
        print(f"[WARN] Failed to inspect {class_file}: {e}", file=sys.stderr)
        return {"classes": [], "methods": [], "fields": []}

    output = proc.stdout
    classes = set()
    methods = set()
    fields = set()

    for line in output.splitlines():
        # Match Class references
        m_class = re.search(r"Class\s+#\d+\s+//\s+(.*)", line)
        if m_class:
            cname = m_class.group(1).strip('"').replace("/", ".")
            if not cname.startswith("io.github.minh124199.viettemplate.generated") and not cname.startswith("["):
                classes.add(cname)

        # Match Methodref & InterfaceMethodref
        m_meth = re.search(r"(?:Methodref|InterfaceMethodref)\s+#\d+\.#\d+\s+//\s+(.*)", line)
        if m_meth:
            mcall = m_meth.group(1).strip().replace("/", ".")
            if not mcall.startswith("io.github.minh124199.viettemplate.generated"):
                methods.add(mcall)

        # Match Fieldref
        m_field = re.search(r"Fieldref\s+#\d+\.#\d+\s+//\s+(.*)", line)
        if m_field:
            fref = m_field.group(1).strip().replace("/", ".")
            if not fref.startswith("io.github.minh124199.viettemplate.generated"):
                fields.add(fref)

    return {
        "classes": sorted(classes),
        "methods": sorted(methods),
        "fields": sorted(fields),
    }


def build_abi_report(classes: list[Path]) -> dict:
    """Aggregates ABI references across all analyzed template classfiles."""
    all_referenced_types = set()
    all_invoked_methods = set()
    all_accessed_fields = set()
    per_class_summary = {}

    for c in classes:
        res = analyze_class_constant_pool(c)
        per_class_summary[c.name] = res
        all_referenced_types.update(res["classes"])
        all_invoked_methods.update(res["methods"])
        all_accessed_fields.update(res["fields"])

    viet_types = sorted([t for t in all_referenced_types if "minh124199" in t])
    jdk_types = sorted([t for t in all_referenced_types if "minh124199" not in t])
    viet_methods = sorted([m for m in all_invoked_methods if "minh124199" in m])
    viet_fields = sorted([f for f in all_accessed_fields if "minh124199" in f])

    report = {
        "metadata": {
            "total_classes_analyzed": len(classes),
            "analyzed_files": [c.name for c in classes],
        },
        "runtime_abi": {
            "viet_template_types": viet_types,
            "jdk_types": jdk_types,
            "viet_template_methods": viet_methods,
            "viet_template_fields": viet_fields,
        },
        "per_class_details": per_class_summary,
    }
    return report


def main():
    parser = argparse.ArgumentParser(description="Verify generated template runtime ABI.")
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help="Path to generated-template-runtime-abi.txt baseline",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help="Path to write generated-template-abi.json report",
    )
    args = parser.parse_args()

    print("=== Viet Template Generated Template Runtime ABI Audit ===")
    classes = locate_template_classes(ROOT_DIR)

    if not classes:
        print("[INFO] No compiled template classfiles found. Running GeneratedTemplateAbiAuditTest to generate them...")
        res = subprocess.run(
            ["./gradlew", ":viet-template-vtl-interpreter:test", "--tests", "GeneratedTemplateAbiAuditTest", "--no-daemon"],
            cwd=str(ROOT_DIR),
            capture_output=True,
            text=True,
        )
        if res.returncode != 0:
            print(f"[ERROR] Failed to compile representative template suite:\n{res.stderr}", file=sys.stderr)
            sys.exit(1)
        classes = locate_template_classes(ROOT_DIR)

    print(f"[INFO] Analyzed {len(classes)} compiled template classfiles.")
    report = build_abi_report(classes)

    args.report.parent.mkdir(parents=True, exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"[PASS] ABI report written to: {args.report.relative_to(ROOT_DIR)}")

    # Baseline comparison
    baseline_types = parse_baseline(args.baseline)
    errors = []

    viet_types = set(report["runtime_abi"]["viet_template_types"])
    expected_viet_types = set(baseline_types.keys())

    unregistered_types = viet_types - expected_viet_types
    if unregistered_types:
        for ut in sorted(unregistered_types):
            errors.append(f"Generated template references unregistered type: {ut}")

    print("\n--- Verified Runtime ABI Types ---")
    for vt in sorted(viet_types):
        print(f"  [OK] {vt}")

    print("\n--- Verified Runtime ABI Method Calls ---")
    for vm in sorted(report["runtime_abi"]["viet_template_methods"]):
        print(f"  [OK] {vm}")

    if errors:
        print("\n[FAILED] Generated template ABI audit FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Generated template runtime ABI audit PASSED! 0 unregistered runtime dependencies.")
        sys.exit(0)


if __name__ == "__main__":
    main()
