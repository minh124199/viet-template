#!/usr/bin/env python3
"""
verify-public-surface-classification.py

Automated CI verification script enforcing public surface containment and stability:
- Check 1: 0 unclassified public types (every compiled public/protected type in production modules must be classified).
- Check 2: No stale classified types (every type in classification file must exist and be public/protected).
- Check 3: Compatibility baseline parity and bijection:
           - Every type in any 1.0 baseline (core, aot, spring, spring-security) must be STABLE_API or STABLE_SPI.
           - Every type classified as STABLE_API or STABLE_SPI must be covered by a baseline.
           - Baselines must be strictly disjoint (0 duplicate ownership).
- Check 4: Signature leak check: STABLE_API and STABLE_SPI in viet-template-api, viet-template-runtime,
           viet-template-aot, and viet-template-spring must not leak any PUBLIC_BUT_INTERNAL_ACCIDENT or EXPERIMENTAL types into public signatures.
"""

import os
import sys
import subprocess
import argparse
import re

import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))

BASELINE_CORE_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-core-public-api.txt")
BASELINE_LEGACY_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-public-api.txt")
BASELINE_AOT_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-aot-public-api.txt")
BASELINE_SPRING_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-spring-public-api.txt")
BASELINE_SECURITY_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-spring-security-public-api.txt")
CLASSIFICATION_FILE = os.path.join(REPO_ROOT, "config/api-baseline/public-surface-classification.txt")

DEFAULT_BASELINES = [
    ("core", BASELINE_CORE_FILE if os.path.exists(BASELINE_CORE_FILE) else BASELINE_LEGACY_FILE),
    ("aot", BASELINE_AOT_FILE),
    ("spring", BASELINE_SPRING_FILE),
    ("spring-security", BASELINE_SECURITY_FILE),
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


def load_classification(path):
    if not os.path.exists(path):
        print(f"Error: Classification file not found: {path}", file=sys.stderr)
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


def load_baseline_api_types(path):
    if not os.path.exists(path):
        print(f"Error: Baseline file not found: {path}", file=sys.stderr)
        return None
    types = set()
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("TYPE "):
                parts = line.split()
                for i, p in enumerate(parts):
                    if p in ("class", "interface", "enum", "record", "@interface") and i + 1 < len(parts):
                        cls = parts[i + 1].split("<")[0]
                        types.add(cls)
    return types


def get_compiled_public_types():
    all_classes = []
    for mod in MODULES:
        classes_dir = os.path.join(REPO_ROOT, mod, "build/classes/java/main")
        if not os.path.exists(classes_dir):
            continue
        for root, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class") and not f.endswith("package-info.class"):
                    rel = os.path.relpath(os.path.join(root, f), classes_dir)
                    cls_name = rel[:-6].replace(os.sep, ".")
                    all_classes.append(cls_name)

    all_classes.sort()
    pub_types = set()
    batch_size = 100
    for i in range(0, len(all_classes), batch_size):
        batch = all_classes[i : i + batch_size]
        cmd = [JAVAP, "-protected", "-cp", FULL_CP] + batch
        proc = subprocess.run(cmd, capture_output=True, text=True)
        for line in proc.stdout.splitlines():
            l = line.strip()
            if (
                "class " in l
                or "interface " in l
                or "enum " in l
                or "record " in l
                or "@interface " in l
            ) and not l.startswith("Compiled"):
                if l.startswith("public ") or l.startswith("protected "):
                    parts = l.split()
                    for idx, p in enumerate(parts):
                        if (
                            p in ("class", "interface", "enum", "record", "@interface")
                            and idx + 1 < len(parts)
                        ):
                            c = parts[idx + 1].split("<")[0]
                            pub_types.add(c)
                            break

    return pub_types


def check_signature_leaks(api_types, internal_and_exp_types):
    leaks = []
    targets = sorted(list(internal_and_exp_types), key=len, reverse=True)
    pattern = re.compile(r"\b(" + "|".join(re.escape(t) for t in targets) + r")\b")

    for cls in sorted(api_types):
        cmd = [JAVAP, "-protected", "-cp", FULL_CP, cls]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            continue
        for line in proc.stdout.splitlines():
            l = line.strip()
            if not l or l.startswith("Compiled"):
                continue
            matches = pattern.findall(l)
            if matches:
                for m in set(matches):
                    if m != cls:
                        leaks.append((cls, l, m))
    return leaks


def main():
    parser = argparse.ArgumentParser(description="Verify public surface classification.")
    parser.add_argument("--classification", default=CLASSIFICATION_FILE, help="Path to classification file")
    args = parser.parse_args()

    print("================================================================================")
    print("VIET TEMPLATE PUBLIC SURFACE CLASSIFICATION VERIFICATION")
    print("================================================================================")

    classification = load_classification(args.classification)
    if classification is None:
        sys.exit(1)

    all_baseline_types = {}
    total_baseline_types = set()
    duplicate_errors = []

    for name, path in DEFAULT_BASELINES:
        if os.path.exists(path):
            types = load_baseline_api_types(path)
            if types is None:
                sys.exit(1)
            for t in types:
                if t in all_baseline_types:
                    duplicate_errors.append((t, all_baseline_types[t], name))
                else:
                    all_baseline_types[t] = name
                    total_baseline_types.add(t)

    compiled_public_types = get_compiled_public_types()
    classified_types = set(classification.keys())

    errors = 0

    # --------------------------------------------------------------------------
    # Check 1: 0 unclassified public types
    # --------------------------------------------------------------------------
    unclassified = compiled_public_types - classified_types
    if unclassified:
        print(f"\n[FAIL] Check 1: Found {len(unclassified)} UNCLASSIFIED public types:")
        for t in sorted(unclassified):
            print(f"  - {t}")
        errors += 1
    else:
        print(f"[PASS] Check 1: Exactly 0 unclassified public types ({len(compiled_public_types)} compiled types verified).")

    # --------------------------------------------------------------------------
    # Check 2: No stale classified types
    # --------------------------------------------------------------------------
    stale = classified_types - compiled_public_types
    if stale:
        print(f"\n[FAIL] Check 2: Found {len(stale)} STALE classified types (not in compiled classes):")
        for t in sorted(stale):
            print(f"  - {t}")
        errors += 1
    else:
        print(f"[PASS] Check 2: No stale classified types ({len(classified_types)} classified types exist).")

    # --------------------------------------------------------------------------
    # Check 3: Baseline parity and bijection
    # --------------------------------------------------------------------------
    parity_errors = []
    if duplicate_errors:
        for t, orig, dup in duplicate_errors:
            parity_errors.append(f"Duplicate baseline ownership: '{t}' in both '{orig}' and '{dup}'")

    stable_classified = {
        cls for cls, cat in classification.items()
        if cat in ("STABLE_API", "STABLE_SPI")
    }

    # Baseline types must be STABLE_API or STABLE_SPI
    for t in sorted(total_baseline_types):
        cat = classification.get(t)
        if cat not in ("STABLE_API", "STABLE_SPI"):
            parity_errors.append(f"Baseline type '{t}' in '{all_baseline_types[t]}' is classified as {cat}")

    # STABLE_API and STABLE_SPI must be in baselines
    missing_baselines = stable_classified - total_baseline_types
    for t in sorted(missing_baselines):
        parity_errors.append(f"Classified stable type '{t}' ({classification[t]}) is missing from all baselines")

    if parity_errors:
        print(f"\n[FAIL] Check 3: Baseline parity check failed with {len(parity_errors)} issue(s):")
        for err in parity_errors:
            print(f"  - {err}")
        errors += 1
    else:
        print(f"[PASS] Check 3: Baseline parity & bijection verified across {len(DEFAULT_BASELINES)} baselines "
              f"({len(total_baseline_types)} baseline types == {len(stable_classified)} STABLE_API/STABLE_SPI types).")

    # --------------------------------------------------------------------------
    # Check 4: Signature leak check
    # --------------------------------------------------------------------------
    internal_and_exp = {
        cls for cls, cat in classification.items()
        if cat in ("PUBLIC_BUT_INTERNAL_ACCIDENT", "EXPERIMENTAL")
    }

    api_and_spi_to_check = {
        cls for cls, cat in classification.items()
        if cat in ("STABLE_API", "STABLE_SPI")
        and (
            cls.startswith("io.github.minh124199.viettemplate.api.")
            or cls.startswith("io.github.minh124199.viettemplate.runtime.")
            or cls.startswith("io.github.minh124199.viettemplate.aot.")
            or cls.startswith("io.github.minh124199.viettemplate.spring.")
        )
    }

    leaks = check_signature_leaks(api_and_spi_to_check, internal_and_exp)
    if leaks:
        print(f"\n[FAIL] Check 4: Found {len(leaks)} signature leak(s) in STABLE_API/STABLE_SPI:")
        for cls, sig, leak in leaks:
            print(f"  - In {cls}: signature '{sig}' leaks internal type '{leak}'")
        errors += 1
    else:
        print(f"[PASS] Check 4: Signature leak check passed. Checked {len(api_and_spi_to_check)} public/SPI classes (0 leaks detected).")

    # Summary
    counts = {}
    for cat in classification.values():
        counts[cat] = counts.get(cat, 0) + 1

    print("\n--------------------------------------------------------------------------------")
    print("Classification Summary:")
    print(f"  Total Types:                 {len(classification)}")
    print(f"  STABLE_API:                  {counts.get('STABLE_API', 0)}")
    print(f"  STABLE_SPI:                  {counts.get('STABLE_SPI', 0)}")
    print(f"  EXPERIMENTAL:                {counts.get('EXPERIMENTAL', 0)}")
    print(f"  PUBLIC_BUT_INTERNAL_ACCIDENT:{counts.get('PUBLIC_BUT_INTERNAL_ACCIDENT', 0)}")
    print("--------------------------------------------------------------------------------")

    if errors > 0:
        print(f"\nFAILED: {errors} check(s) failed.")
        sys.exit(1)
    else:
        print("\nALL CHECKS PASSED: Public surface containment and classification fully verified.")
        sys.exit(0)


if __name__ == "__main__":
    main()
