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
import shutil

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
    java_home = os.environ.get("JAVA_HOME")
    candidates = []
    if java_home:
        candidates.append(os.path.join(java_home, "bin/javap"))
    which_javap = shutil.which("javap")
    if which_javap:
        candidates.append(which_javap)
    candidates.extend([
        "/usr/lib/jvm/java-25-openjdk/bin/javap",
        "/usr/lib/jvm/java-21-openjdk/bin/javap",
        "/usr/bin/javap",
        "javap",
    ])
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
                        current_class = parts[idx + 1].split("<")[0].split("(")[0]
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


def parse_header(header):
    if not header:
        return {
            "raw": "",
            "kind": "class",
            "modifiers": set(),
            "is_sealed": False,
            "is_non_sealed": False,
            "is_final": False,
            "is_record": False,
            "is_interface": False,
            "permits": set(),
        }
    h = header.strip().rstrip(" {")
    permits = set()
    if " permits " in h:
        prefix, permits_part = h.split(" permits ", 1)
        h = prefix.strip()
        permits = {p.strip().rstrip(";") for p in permits_part.split(",") if p.strip().rstrip(";")}

    decl_part = h
    if " implements " in decl_part:
        decl_part = decl_part.split(" implements ", 1)[0].strip()
    if " extends " in decl_part:
        decl_part = decl_part.split(" extends ", 1)[0].strip()

    before_name = decl_part
    if "(" in before_name:
        before_name = before_name.split("(", 1)[0].strip()

    tokens = before_name.split()
    kind = None
    kind_idx = -1
    for idx, t in enumerate(tokens):
        if t in ("class", "interface", "record", "enum", "@interface"):
            kind = t
            kind_idx = idx
            break

    modifiers = set(tokens[:kind_idx]) if kind_idx != -1 else set()

    is_sealed = "sealed" in modifiers and "non-sealed" not in modifiers
    is_non_sealed = "non-sealed" in modifiers
    is_final = "final" in modifiers or kind == "record" or kind == "enum"
    is_record = (kind == "record")
    is_interface = (kind == "interface")

    return {
        "raw": header,
        "kind": kind or "class",
        "modifiers": modifiers,
        "is_sealed": is_sealed,
        "is_non_sealed": is_non_sealed,
        "is_final": is_final,
        "is_record": is_record,
        "is_interface": is_interface,
        "permits": permits,
    }


def parse_member(m):
    clean_m = m.strip().rstrip(";")
    is_method = "(" in clean_m
    clean_without_throws = clean_m
    if " throws " in clean_without_throws:
        clean_without_throws = clean_without_throws.split(" throws ", 1)[0].strip()

    parts = clean_without_throws.split()
    mods = []
    rest_idx = 0
    standard_mods = {"public", "protected", "private", "static", "final", "abstract", "default", "synchronized", "native"}
    for idx, p in enumerate(parts):
        if p in standard_mods:
            mods.append(p)
            rest_idx = idx + 1
        else:
            break

    modifiers = set(mods)
    remainder = " ".join(parts[rest_idx:])
    return {
        "raw": m,
        "is_method": is_method,
        "modifiers": modifiers,
        "remainder": remainder,
    }


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
            b_hdr = parse_header(b_info.get("header", ""))
            c_hdr = parse_header(c_info.get("header", ""))

            # 1. Kind check (e.g. record vs class, interface vs class)
            if b_hdr["kind"] != c_hdr["kind"]:
                errors.append(f"INCOMPATIBLE_TYPE_KIND [{name}]: in '{cls_name}': type kind changed from '{b_hdr['kind']}' to '{c_hdr['kind']}'")

            # 2. Final modifier check (non-final becoming final breaks subclassing)
            if not b_hdr["is_final"] and c_hdr["is_final"]:
                errors.append(f"INCOMPATIBLE_TYPE_MODIFIER [{name}]: in '{cls_name}': non-final type became final (breaks subclassing)")

            # 3. Sealed modifier check (non-sealed becoming sealed restricts external implementors)
            if not b_hdr["is_sealed"] and c_hdr["is_sealed"]:
                errors.append(f"INCOMPATIBLE_TYPE_MODIFIER [{name}]: in '{cls_name}': non-sealed type became sealed (restricts external implementations)")

            # 4. Permits list check
            if b_hdr["is_sealed"] and c_hdr["is_sealed"]:
                if b_hdr["permits"] != c_hdr["permits"]:
                    removed_permits = b_hdr["permits"] - c_hdr["permits"]
                    added_permits = c_hdr["permits"] - b_hdr["permits"]
                    if removed_permits:
                        errors.append(f"INCOMPATIBLE_PERMITS_LIST [{name}]: in '{cls_name}': permitted subclass(es) removed: {sorted(removed_permits)}")
                    if added_permits:
                        errors.append(f"INCOMPATIBLE_PERMITS_LIST [{name}]: in '{cls_name}': permitted subclass(es) added: {sorted(added_permits)}")

            # Check members
            b_members = set(b_info["members"])
            c_members = set(c_info["members"])

            b_methods = {parse_member(m)["remainder"]: parse_member(m) for m in b_members if "(" in m}
            c_methods = {parse_member(m)["remainder"]: parse_member(m) for m in c_members if "(" in m}

            # Check method modifier compatibility for matching method signatures
            for sig, b_m in b_methods.items():
                if sig in c_methods:
                    c_m = c_methods[sig]
                    # Static modifier changes
                    if ("static" in b_m["modifiers"]) != ("static" in c_m["modifiers"]):
                        errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER [{name}]: in '{cls_name}': method static modifier changed: '{b_m['raw']}' -> '{c_m['raw']}'")
                    # Default becoming abstract
                    if "default" in b_m["modifiers"] and "abstract" in c_m["modifiers"]:
                        errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER [{name}]: in '{cls_name}': default method became abstract: '{b_m['raw']}'")
                    # Concrete becoming abstract
                    elif "abstract" not in b_m["modifiers"] and "abstract" in c_m["modifiers"]:
                        errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER [{name}]: in '{cls_name}': concrete method became abstract: '{b_m['raw']}'")
                    # Non-final method in non-final class becoming final
                    if not b_hdr["is_final"] and not c_hdr["is_final"] and ("final" not in b_m["modifiers"]) and ("final" in c_m["modifiers"]):
                        errors.append(f"INCOMPATIBLE_MEMBER_MODIFIER [{name}]: in '{cls_name}': non-final method became final: '{b_m['raw']}'")

            # Check for removed members
            removed = b_members - c_members
            for m in sorted(removed):
                if "(" in m:
                    m_parsed = parse_member(m)
                    if m_parsed["remainder"] in c_methods:
                        continue

                    words = m.split("(")[0].split()
                    is_constructor = len(words) >= 1 and (words[-1] == cls_name.split(".")[-1] or words[-1] == cls_name)
                    if is_constructor:
                        errors.append(f"REMOVED_CONSTRUCTOR [{name}]: in '{cls_name}': '{m}'")
                    else:
                        errors.append(f"REMOVED_METHOD [{name}]: in '{cls_name}': '{m}'")
                else:
                    errors.append(f"REMOVED_FIELD [{name}]: in '{cls_name}': '{m}'")

            # Check for added abstract methods in interfaces (source/binary breaking for implementors)
            if b_hdr["is_interface"] or "interface " in b_info["header"]:
                added = c_members - b_members
                for m in sorted(added):
                    if "public abstract " in m:
                        m_parsed = parse_member(m)
                        if m_parsed["remainder"] not in b_methods:
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
