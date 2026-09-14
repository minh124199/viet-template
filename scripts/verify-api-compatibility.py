#!/usr/bin/env python3
"""
verify-api-compatibility.py

Verifies binary and source compatibility of Viet Template's public API against
the recorded public baseline (config/api-baseline/1.0-public-api.txt).

Checks:
- Removed public/protected types
- Removed public/protected methods
- Removed public/protected constructors
- Removed public/protected fields
- Changed return or parameter types
- Interface methods becoming abstract incompatibly
"""

import os
import sys
import subprocess
import argparse
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
BASELINE_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-public-api.txt")

JAVAP = os.environ.get("JAVAP_BIN")
if not JAVAP:
    candidates = [
        "/home/lynguyen/opt/usr/lib/jvm/java-17-openjdk/bin/javap",
        "/usr/lib/jvm/java-17-openjdk/bin/javap",
        "javap",
    ]
    for c in candidates:
        if os.path.exists(c) or c == "javap":
            JAVAP = c
            break

MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
]

FULL_CP = ":".join([os.path.join(REPO_ROOT, m, "build/classes/java/main") for m in MODULES])

STABLE_PACKAGES = [
    "io.github.minh124199.viettemplate.api",
    "io.github.minh124199.viettemplate.runtime",
]

STABLE_EXPLICIT_CLASSES = [
    "io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine",
    "io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineBuilder",
    "io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineProvider",
    "io.github.minh124199.viettemplate.vtl.engine.VtlTemplate",
    "io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter",
    "io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions",
    "io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions$Builder",
    "io.github.minh124199.viettemplate.language.vtl.source.SourceText",
]

EXCLUDED_PACKAGES = [
    "io.github.minh124199.viettemplate.runtime.linker",
]


def is_stable_class(cls_name):
    for exc in EXCLUDED_PACKAGES:
        if cls_name.startswith(exc + "."):
            return False
    for pkg in STABLE_PACKAGES:
        parent_pkg = ".".join(cls_name.split(".")[:-1])
        if parent_pkg == pkg or parent_pkg.startswith(pkg + "$"):
            return True
    if cls_name in STABLE_EXPLICIT_CLASSES:
        return True
    return False


def get_class_files():
    classes = set()
    for mod in MODULES:
        classes_dir = os.path.join(REPO_ROOT, mod, "build/classes/java/main")
        if not os.path.exists(classes_dir):
            continue
        for root, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class") and not f.endswith("package-info.class"):
                    rel = os.path.relpath(os.path.join(root, f), classes_dir)
                    cls_name = rel[:-6].replace(os.sep, ".")
                    if is_stable_class(cls_name):
                        classes.add(cls_name)
    return sorted(classes)


def inspect_class(cls_name):
    cmd = [JAVAP, "-protected", "-cp", FULL_CP, cls_name]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        return None
    raw = proc.stdout.strip()
    lines = raw.splitlines()
    header = ""
    members = []
    for line in lines:
        l = line.strip()
        if not l or l.startswith("Compiled from"):
            continue
        if l == "}" or l == "{":
            continue
        if ("class " in l or "interface " in l or "enum " in l or "record " in l or "@interface " in l) and not header:
            header = l.rstrip(" {")
        elif l.startswith("public ") or l.startswith("protected "):
            members.append(l.rstrip(";"))
    if not header or not (header.startswith("public ") or header.startswith("protected ")):
        return None
    members.sort()
    return {
        "header": header,
        "members": members
    }


def parse_baseline(baseline_path):
    if not os.path.exists(baseline_path):
        print(f"Error: Baseline file not found: {baseline_path}", file=sys.stderr)
        return None

    baseline = {}
    current_class = None
    current_header = None
    current_members = []

    with open(baseline_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\r\n")
            if not line or line.startswith("#"):
                continue
            if line.startswith("TYPE "):
                if current_class:
                    baseline[current_class] = {
                        "header": current_header,
                        "members": sorted(current_members)
                    }
                current_header = line[5:].strip()
                parts = current_header.split()
                for idx, p in enumerate(parts):
                    if p in ("class", "interface", "enum", "record", "@interface") and idx + 1 < len(parts):
                        current_class = parts[idx + 1].split("<")[0]
                        break
                current_members = []
            elif line.startswith("  MEMBER "):
                current_members.append(line[9:].strip())

    if current_class:
        baseline[current_class] = {
            "header": current_header,
            "members": sorted(current_members)
        }

    return baseline


def generate_baseline():
    classes = get_class_files()
    lines = [
        "# Viet Template 1.0 Public API and SPI Baseline",
        "# Machine-readable public signatures for source and binary compatibility enforcement",
        "# Format: TYPE <class-header>",
        "#         MEMBER <member-signature>",
        ""
    ]
    count = 0
    for cls_name in sorted(classes):
        info = inspect_class(cls_name)
        if info:
            count += 1
            lines.append(f"TYPE {info['header']}")
            for m in info["members"]:
                lines.append(f"  MEMBER {m}")
            lines.append("")

    os.makedirs(os.path.dirname(BASELINE_FILE), exist_ok=True)
    with open(BASELINE_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Generated API baseline with {count} types at: {BASELINE_FILE}")


def check_compatibility():
    baseline = parse_baseline(BASELINE_FILE)
    if baseline is None:
        return 1

    current_classes = get_class_files()
    current_surface = {}
    for cls in current_classes:
        info = inspect_class(cls)
        if info:
            current_surface[cls] = info

    errors = []

    print(f"Verifying API compatibility against baseline ({len(baseline)} types in baseline, {len(current_surface)} types in current build)...")

    # Check for removed types
    for cls_name, b_info in baseline.items():
        if cls_name not in current_surface:
            errors.append(f"REMOVED_TYPE: Public type '{cls_name}' recorded in baseline is missing.")
            continue

        c_info = current_surface[cls_name]

        # Check members
        b_members = set(b_info["members"])
        c_members = set(c_info["members"])

        # Check for removed members
        removed = b_members - c_members
        for m in sorted(removed):
            # Check if this is an interface method becoming default or vice versa
            abstract_form = m.replace("public default ", "public abstract ")
            default_form = m.replace("public abstract ", "public default ")
            if abstract_form in c_members or default_form in c_members:
                if "public default " in m and abstract_form in c_members:
                    errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER: in '{cls_name}': default method became abstract: '{m}'")
                continue

            if "(" in m:
                if cls_name.split(".")[-1] in m.split("(")[0]:
                    errors.append(f"REMOVED_CONSTRUCTOR: in '{cls_name}': '{m}'")
                else:
                    errors.append(f"REMOVED_METHOD: in '{cls_name}': '{m}'")
            else:
                errors.append(f"REMOVED_FIELD: in '{cls_name}': '{m}'")

        # Check for added abstract methods in interfaces (source/binary breaking for implementors)
        if "interface " in b_info["header"]:
            added = c_members - b_members
            for m in sorted(added):
                if "public abstract " in m:
                    if m.replace("public abstract ", "public default ") not in b_members:
                        errors.append(f"ADDED_ABSTRACT_INTERFACE_METHOD: in '{cls_name}': '{m}' (breaks external SPI implementors without default implementation)")

    if errors:
        print("\n" + "=" * 70, file=sys.stderr)
        print(f"API COMPATIBILITY CHECK FAILED ({len(errors)} violations found):", file=sys.stderr)
        print("=" * 70, file=sys.stderr)
        for err in errors:
            print(f"  [ERROR] {err}", file=sys.stderr)
        print("\nTo update the baseline after intentional pre-1.0 changes, run:", file=sys.stderr)
        print("  python3 scripts/verify-api-compatibility.py --update-baseline", file=sys.stderr)
        return 1

    print("\nAPI COMPATIBILITY CHECK PASSED: 0 breaking changes detected.")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Verify public API compatibility")
    parser.add_argument("--update-baseline", action="store_true", help="Update the recorded public API baseline")
    args = parser.parse_args()

    if args.update_baseline:
        generate_baseline()
        return 0
    else:
        return check_compatibility()


if __name__ == "__main__":
    sys.exit(main())
