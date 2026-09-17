#!/usr/bin/env python3
"""
verify-api-compatibility.py

Verifies binary and source compatibility of Viet Template's public API against
the recorded public baselines:
- config/api-baseline/1.0-core-public-api.txt (or 1.0-public-api.txt)
- config/api-baseline/1.0-aot-public-api.txt
- config/api-baseline/1.0-spring-public-api.txt
- config/api-baseline/1.0-spring-security-public-api.txt

Enforces the baseline invariant:
  union(all baseline types) == set(STABLE_API + STABLE_SPI) in public-surface-classification.txt

Checks:
- Duplicate baseline type ownership (baselines must be disjoint)
- Missing stable types (STABLE_API / STABLE_SPI missing from all baselines)
- Declassified types (baseline types classified as PUBLIC_BUT_INTERNAL_ACCIDENT or EXPERIMENTAL)
- Removed public/protected types
- Removed public/protected methods
- Removed public/protected constructors
- Removed public/protected fields
- Incompatible interface modifiers (default method becoming abstract, added abstract methods)
"""

import os
import sys
import subprocess
import argparse
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))

BASELINE_CORE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-core-public-api.txt")
BASELINE_LEGACY = os.path.join(REPO_ROOT, "config/api-baseline/1.0-public-api.txt")
BASELINE_AOT = os.path.join(REPO_ROOT, "config/api-baseline/1.0-aot-public-api.txt")
BASELINE_SPRING = os.path.join(REPO_ROOT, "config/api-baseline/1.0-spring-public-api.txt")
BASELINE_SPRING_SECURITY = os.path.join(REPO_ROOT, "config/api-baseline/1.0-spring-security-public-api.txt")
CLASSIFICATION_FILE = os.path.join(REPO_ROOT, "config/api-baseline/public-surface-classification.txt")

DEFAULT_BASELINES = [
    ("core", BASELINE_CORE if os.path.exists(BASELINE_CORE) else BASELINE_LEGACY),
    ("aot", BASELINE_AOT),
    ("spring", BASELINE_SPRING),
    ("spring-security", BASELINE_SPRING_SECURITY),
]

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
    "viet-template-spring",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-security",
]

FULL_CP = ":".join([os.path.join(REPO_ROOT, m, "build/classes/java/main") for m in MODULES])

STABLE_PACKAGES = [
    "io.github.minh124199.viettemplate.api",
    "io.github.minh124199.viettemplate.runtime",
    "io.github.minh124199.viettemplate.aot",
    "io.github.minh124199.viettemplate.spring.web.servlet",
    "io.github.minh124199.viettemplate.spring.boot.autoconfigure",
    "io.github.minh124199.viettemplate.spring.security",
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


def is_stable_class(cls_name, known_baseline_classes=None):
    if known_baseline_classes and cls_name in known_baseline_classes:
        return True
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


def get_class_files(known_baseline_classes=None):
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
                    if is_stable_class(cls_name, known_baseline_classes):
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


def load_classification(path):
    if not os.path.exists(path):
        return None
    mapping = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) == 2:
                mapping[parts[0]] = parts[1]
    return mapping


def check_compatibility(baseline_specs=None, classification_path=CLASSIFICATION_FILE, surface_override=None):
    if baseline_specs is None:
        baseline_specs = DEFAULT_BASELINES

    all_baselines = {}
    total_baseline_types = 0
    known_classes = set()
    errors = []

    # 1. Parse and validate individual baselines
    for name, path in baseline_specs:
        b = parse_baseline(path)
        if b is None:
            return 1
        all_baselines[name] = (path, b)
        total_baseline_types += len(b)

    # 2. Check for duplicate baseline ownership (baselines must be disjoint)
    seen_types = {}
    for name, (path, b) in all_baselines.items():
        for cls_name in b:
            if cls_name in seen_types:
                errors.append(
                    f"DUPLICATE_BASELINE_OWNERSHIP: Public type '{cls_name}' is recorded in multiple baselines "
                    f"('{seen_types[cls_name]}' and '{name}')."
                )
            else:
                seen_types[cls_name] = name
                known_classes.add(cls_name)

    # 3. Check classification invariant if classification file is available
    if classification_path and os.path.exists(classification_path):
        classification = load_classification(classification_path)
        if classification:
            stable_classified = {
                cls for cls, cat in classification.items()
                if cat in ("STABLE_API", "STABLE_SPI")
            }
            # Missing from baselines
            missing_from_baselines = stable_classified - known_classes
            for m in sorted(missing_from_baselines):
                errors.append(
                    f"MISSING_BASELINE_COVERAGE: Type '{m}' is classified as {classification[m]} in "
                    f"{os.path.basename(classification_path)} but is missing from all compatibility baselines."
                )

            # Declassified types in baselines
            for cls_name in sorted(known_classes):
                cat = classification.get(cls_name)
                if cat and cat not in ("STABLE_API", "STABLE_SPI"):
                    errors.append(
                        f"DECLASSIFIED_BASELINE_TYPE: Type '{cls_name}' is recorded in baseline '{seen_types[cls_name]}' "
                        f"but classified as {cat} in {os.path.basename(classification_path)}."
                    )
                elif not cat:
                    errors.append(
                        f"UNCLASSIFIED_BASELINE_TYPE: Type '{cls_name}' is recorded in baseline '{seen_types[cls_name]}' "
                        f"but not listed in {os.path.basename(classification_path)}."
                    )

    # 4. Extract current surface or use override
    if surface_override is not None:
        current_surface = surface_override
    else:
        current_classes = get_class_files(known_classes)
        current_surface = {}
        for cls in current_classes:
            info = inspect_class(cls)
            if info:
                current_surface[cls] = info

    print(f"Verifying API compatibility against {len(all_baselines)} baseline(s) ({total_baseline_types} baseline types, {len(current_surface)} types in current surface)...")
    for name, (path, b) in all_baselines.items():
        print(f"  - [{name}] {os.path.relpath(path, REPO_ROOT)}: {len(b)} types")

    # 5. Check compatibility against baselines
    for name, (path, baseline) in all_baselines.items():
        for cls_name, b_info in baseline.items():
            if cls_name not in current_surface:
                errors.append(f"REMOVED_TYPE [{name}]: Public type '{cls_name}' recorded in {os.path.basename(path)} is missing.")
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
                        errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER [{name}]: in '{cls_name}': default method became abstract: '{m}'")
                    continue

                if "(" in m:
                    words = m.split("(")[0].split()
                    is_constructor = len(words) >= 1 and (words[-1] == cls_name.split(".")[-1] or words[-1] == cls_name)
                    if is_constructor:
                        errors.append(f"REMOVED_CONSTRUCTOR [{name}]: in '{cls_name}': '{m}'")
                    else:
                        errors.append(f"REMOVED_METHOD [{name}]: in '{cls_name}': '{m}'")
                else:
                    errors.append(f"REMOVED_FIELD [{name}]: in '{cls_name}': '{m}'")

            # Check for added abstract methods in interfaces (source/binary breaking for implementors)
            if "interface " in b_info["header"]:
                added = c_members - b_members
                for m in sorted(added):
                    if "public abstract " in m:
                        if m.replace("public abstract ", "public default ") not in b_members:
                            errors.append(f"ADDED_ABSTRACT_INTERFACE_METHOD [{name}]: in '{cls_name}': '{m}' (breaks external SPI implementors without default implementation)")

    if errors:
        print("\n" + "=" * 70, file=sys.stderr)
        print(f"API COMPATIBILITY CHECK FAILED ({len(errors)} violations found):", file=sys.stderr)
        print("=" * 70, file=sys.stderr)
        for err in errors:
            print(f"  [ERROR] {err}", file=sys.stderr)
        print("\nReview breaking changes before proceeding.", file=sys.stderr)
        return 1

    print("\nAPI COMPATIBILITY CHECK PASSED: 0 breaking changes detected across all baselines.")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Verify public API compatibility")
    parser.add_argument("--baseline", help="Verify against a single specific baseline file")
    parser.add_argument("--classification", default=CLASSIFICATION_FILE, help="Path to classification file")
    args = parser.parse_args()

    if args.baseline:
        baseline_specs = [("custom", args.baseline)]
        classification_path = None
    else:
        baseline_specs = DEFAULT_BASELINES
        classification_path = args.classification

    return check_compatibility(baseline_specs, classification_path)


if __name__ == "__main__":
    sys.exit(main())
