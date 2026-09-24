#!/usr/bin/env python3
"""
verify-framework-entrypoints.py

Automated CI verification script validating framework, build-tool, and metadata entrypoints:
1. Validates that every registered entrypoint in config/architecture/framework-and-tooling-entrypoints.json
   exists in compiled classes, has the required public visibility, and has the required constructor.
2. Validates that framework metadata files on disk reference the exact compiled entrypoints:
   - Spring AOT (aot.factories)
   - Spring Boot AutoConfiguration (AutoConfiguration.imports)
   - Gradle plugin descriptor (gradle-plugins properties)
   - Java ServiceLoader (META-INF/services/ TemplateEngineProvider)
3. Validates that every type classified as FRAMEWORK_ENTRYPOINT, BUILD_TOOL_ENTRYPOINT, or SERVICE_ENTRYPOINT
   in config/api-baseline/public-surface-classification.txt is explicitly registered.
4. Validates that classifications between the registry and public-surface-classification.txt match.
5. Emits build/reports/public-framework-entrypoints.json report.
"""

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

REGISTRY_FILE = REPO_ROOT / "config/architecture/framework-and-tooling-entrypoints.json"
CLASSIFICATION_FILE = REPO_ROOT / "config/api-baseline/public-surface-classification.txt"
REPORT_FILE = REPO_ROOT / "build/reports/public-framework-entrypoints.json"

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
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
]

FULL_CP = os.pathsep.join(
    str(REPO_ROOT / m / "build/classes/java/main")
    for m in MODULES
    if (REPO_ROOT / m / "build/classes/java/main").is_dir()
)


def load_classification():
    if not CLASSIFICATION_FILE.is_file():
        print(f"Error: Classification file not found: {CLASSIFICATION_FILE}", file=sys.stderr)
        return None
    mapping = {}
    with open(CLASSIFICATION_FILE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) >= 2:
                mapping[parts[0]] = parts[1]
    return mapping


def verify_class_and_constructor(fqcn, must_be_public, must_have_public_ctor):
    cmd = [JAVAP, "-protected", "-cp", FULL_CP, fqcn]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        return False, False, f"Class {fqcn} failed javap inspection: {proc.stderr.strip()}"

    lines = [l.strip() for l in proc.stdout.splitlines() if l.strip()]
    is_public = False
    has_public_ctor = False
    simple_name = fqcn.split("$")[-1] if "$" in fqcn else fqcn.split(".")[-1]

    for line in lines:
        if line.startswith("Compiled from"):
            continue
        if ("class " in line or "interface " in line or "enum " in line or "record " in line) and simple_name in line:
            if line.startswith("public ") or line.startswith("protected "):
                is_public = True
        if f"public {simple_name}(" in line or f"public {fqcn}(" in line:
            has_public_ctor = True

    errors = []
    if must_be_public and not is_public:
        errors.append(f"{fqcn} must be public, but javap indicated non-public")
    if must_have_public_ctor and not has_public_ctor:
        errors.append(f"{fqcn} must have a public constructor, but no public constructor found in javap")

    return is_public, has_public_ctor, "; ".join(errors)


def verify_metadata_file(rel_path, expected_fqcn):
    p = REPO_ROOT / rel_path
    if not p.is_file():
        # Fall back to build/resources/main if src file has another name
        return False, f"Metadata file not found: {rel_path}"
    content = p.read_text(encoding="utf-8", errors="ignore")
    if expected_fqcn not in content:
        return False, f"Metadata file {rel_path} does not reference expected FQCN {expected_fqcn}"
    return True, f"Found reference in {rel_path}"


def main():
    print("================================================================================")
    print("VIET TEMPLATE FRAMEWORK & TOOLING ENTRYPOINT VERIFICATION")
    print("================================================================================")

    if not REGISTRY_FILE.is_file():
        print(f"Error: Registry file not found: {REGISTRY_FILE}", file=sys.stderr)
        sys.exit(1)

    with open(REGISTRY_FILE, "r", encoding="utf-8") as f:
        registry_data = json.load(f)

    entrypoints = registry_data.get("entrypoints", [])
    print(f"Loaded {len(entrypoints)} entrypoint definitions from {REGISTRY_FILE.relative_to(REPO_ROOT)}")

    classification = load_classification()
    if classification is None:
        sys.exit(1)

    errors = 0

    # --------------------------------------------------------------------------
    # Check 1: Verify each entrypoint in registry
    # --------------------------------------------------------------------------
    print("\n--- Checking Entrypoint Classes & Constructors ---")
    verified_records = []
    registered_fqcns = set()

    for ep in entrypoints:
        fqcn = ep["fqcn"]
        registered_fqcns.add(fqcn)
        mod = ep["module"]
        must_public = ep.get("mustBePublic", True)
        must_ctor = ep.get("mustHavePublicConstructor", True)
        expected_cat = ep.get("stableApiClassification")

        # Verify class exists in module build output
        class_rel = fqcn.replace(".", "/") + ".class"
        class_file = REPO_ROOT / mod / "build/classes/java/main" / class_rel
        if not class_file.is_file():
            print(f"[FAIL] {fqcn}: Compiled class not found at {class_file.relative_to(REPO_ROOT)}")
            errors += 1
            continue

        # Inspect class and constructor via javap
        is_pub, has_ctor, err_msg = verify_class_and_constructor(fqcn, must_public, must_ctor)
        if err_msg:
            print(f"[FAIL] {fqcn}: {err_msg}")
            errors += 1
            continue
        else:
            print(f"[PASS] {fqcn} (public={is_pub}, public_ctor={has_ctor})")

        # Verify classification in public-surface-classification.txt
        actual_cat = classification.get(fqcn)
        if actual_cat != expected_cat:
            print(f"[FAIL] {fqcn}: Classification mismatch! Expected {expected_cat}, got {actual_cat}")
            errors += 1
        else:
            print(f"       Classification matches: {actual_cat}")

        record = dict(ep)
        record["verifiedCompiledClass"] = str(class_file.relative_to(REPO_ROOT))
        record["isPublic"] = is_pub
        record["hasPublicConstructor"] = has_ctor
        record["activeClassification"] = actual_cat
        verified_records.append(record)

    # --------------------------------------------------------------------------
    # Check 2: Verify metadata files on disk
    # --------------------------------------------------------------------------
    print("\n--- Checking Framework & Build-Tool Metadata Files ---")
    metadata_checks = [
        (
            "viet-template-spring-boot-autoconfigure/src/main/resources/META-INF/spring/aot.factories",
            "io.github.minh124199.viettemplate.spring.boot.autoconfigure.aot.VietTemplateRuntimeHints",
        ),
        (
            "viet-template-spring-security/src/main/resources/META-INF/spring/aot.factories",
            "io.github.minh124199.viettemplate.spring.security.aot.VietTemplateSecurityRuntimeHints",
        ),
        (
            "viet-template-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
            "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateAutoConfiguration",
        ),
        (
            "viet-template-gradle-plugin/src/main/resources/META-INF/gradle-plugins/io.github.minh124199.viet-template.properties",
            "io.github.minh124199.viettemplate.tooling.gradle.VietTemplatePlugin",
        ),
        (
            "viet-template-vtl-interpreter/src/main/resources/META-INF/services/io.github.minh124199.viettemplate.api.TemplateEngineProvider",
            "io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineProvider",
        ),
    ]

    for rel_path, expected_fqcn in metadata_checks:
        ok, msg = verify_metadata_file(rel_path, expected_fqcn)
        if ok:
            print(f"[PASS] {rel_path} -> {expected_fqcn}")
        else:
            print(f"[FAIL] {msg}")
            errors += 1

    # --------------------------------------------------------------------------
    # Check 3: Ensure all FRAMEWORK_ENTRYPOINT, BUILD_TOOL_ENTRYPOINT, and SERVICE_ENTRYPOINT
    #          in classification are registered in framework-and-tooling-entrypoints.json
    # --------------------------------------------------------------------------
    print("\n--- Checking Classification Coverage ---")
    entrypoint_categories = {"FRAMEWORK_ENTRYPOINT", "BUILD_TOOL_ENTRYPOINT", "SERVICE_ENTRYPOINT"}
    unregistered_entrypoints = [
        cls for cls, cat in classification.items()
        if cat in entrypoint_categories and cls not in registered_fqcns
    ]

    if unregistered_entrypoints:
        print(f"[FAIL] Found {len(unregistered_entrypoints)} unregistered entrypoints in classification:")
        for t in sorted(unregistered_entrypoints):
            print(f"  - {t} ({classification[t]})")
        errors += 1
    else:
        print(f"[PASS] All entrypoints classified in {entrypoint_categories} are registered.")

    # --------------------------------------------------------------------------
    # Write report
    # --------------------------------------------------------------------------
    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    report_data = {
        "version": registry_data.get("version", "0.3.0"),
        "totalEntrypoints": len(verified_records),
        "entrypoints": verified_records,
    }
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        json.dump(report_data, f, indent=2)
        f.write("\n")
    print(f"\nWrote verification report to: {REPORT_FILE.relative_to(REPO_ROOT)}")

    if errors > 0:
        print(f"\nFAILED: {errors} check(s) failed.")
        sys.exit(1)
    else:
        print("\nALL CHECKS PASSED: Framework and tooling entrypoints verified successfully.")
        sys.exit(0)


if __name__ == "__main__":
    main()
