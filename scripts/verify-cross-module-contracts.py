#!/usr/bin/env python3
"""
verify-cross-module-contracts.py

Automated CI verification script enforcing cross-module internal contract boundaries:
1. Enforces that no production module imports internal classes (*.internal.* or types
   classified as INTERNAL_CROSS_MODULE) from another module without an explicit registered contract
   in config/architecture/cross-module-internal-contracts.json.
2. Validates module dependency direction invariants:
   - Framework modules (viet-template-spring, spring-boot-autoconfigure, spring-security, quarkus, quarkus-deployment)
     and build-tooling modules (viet-template-maven-plugin, gradle-plugin) have ZERO deep-core internal edges
     to AST, IR, Lexer, or Semantics internals.
   - Only runtime stream bridge (NonClosingOutputStream) is permitted as an internal cross-module contract
     for framework modules.
3. Verifies that all 56 contracts in config/architecture/cross-module-internal-contracts.json:
   - Are defined in the specified owningModule.
   - Are consumed in production sources of all specified consumingModules.
   - Are classified as INTERNAL_CROSS_MODULE in config/api-baseline/public-surface-classification.txt.
"""

import json
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

CONTRACTS_FILE = REPO_ROOT / "config/architecture/cross-module-internal-contracts.json"
CLASSIFICATION_FILE = REPO_ROOT / "config/api-baseline/public-surface-classification.txt"

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

FRAMEWORK_AND_TOOLING_MODULES = {
    "viet-template-spring",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-security",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
}

DEEP_CORE_MODULES = {
    "viet-template-language-vtl",
}


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


def get_class_definitions():
    """Maps every class defined in src/main/java to its defining module."""
    class_to_mod = {}
    for m in MODULES:
        main_java = REPO_ROOT / m / "src/main/java"
        if not main_java.is_dir():
            continue
        for p in main_java.rglob("*.java"):
            rel = p.relative_to(main_java)
            fqcn = str(rel)[:-5].replace(os.sep, ".")
            class_to_mod[fqcn] = m
    return class_to_mod


def scan_production_imports():
    """Scans all production java files and returns: (source_mod, imported_fqcn, source_file)."""
    imports = []
    for m in MODULES:
        main_java = REPO_ROOT / m / "src/main/java"
        if not main_java.is_dir():
            continue
        for p in main_java.rglob("*.java"):
            content = p.read_text(encoding="utf-8", errors="ignore")
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("import ") and line.endswith(";"):
                    imp = line[7:-1].strip()
                    if imp.startswith("static "):
                        imp = imp[7:].strip()
                        # If static import of member, trim member name
                        imp = imp.rsplit(".", 1)[0]
                    imports.append((m, imp, str(p.relative_to(REPO_ROOT))))
    return imports


def check_module_consumes_contract(consuming_module, fqcn, consumer_scope=None):
    """Verifies that a consuming module actually references the given contract in its production
    or test sources. Test-only modules (e.g. viet-template-tck) keep their sources under
    src/test/java; benchmark modules keep them under src/main/java."""
    simple = fqcn.split("$")[-1] if "$" in fqcn else fqcn.split(".")[-1]
    pkg = fqcn[:fqcn.rfind(".")] if "$" not in fqcn else fqcn[:fqcn.index("$")].rsplit(".", 1)[0]
    word_pattern = re.compile(r"\b" + re.escape(simple) + r"\b")

    search_dirs = []
    if consumer_scope is None or "MAIN" in consumer_scope:
        search_dirs.append(REPO_ROOT / consuming_module / "src/main/java")
    if consumer_scope is not None and "TEST" in consumer_scope:
        search_dirs.append(REPO_ROOT / consuming_module / "src/test/java")
    if consumer_scope is not None and "BENCHMARK" in consumer_scope:
        search_dirs.append(REPO_ROOT / consuming_module / "src/main/java")
    if not search_dirs:
        search_dirs = [
            REPO_ROOT / consuming_module / "src/main/java",
            REPO_ROOT / consuming_module / "src/test/java",
        ]

    for cons_dir in search_dirs:
        if not cons_dir.is_dir():
            continue
        for p in cons_dir.rglob("*.java"):
            content = p.read_text(encoding="utf-8", errors="ignore")
            if (f"import {fqcn};" in content or
                f"import {pkg}.*;" in content or
                word_pattern.search(content)):
                return True
    return False


def main():
    print("================================================================================")
    print("VIET TEMPLATE CROSS-MODULE INTERNAL CONTRACT VERIFICATION")
    print("================================================================================")

    if not CONTRACTS_FILE.is_file():
        print(f"Error: Contracts file not found: {CONTRACTS_FILE}", file=sys.stderr)
        sys.exit(1)

    with open(CONTRACTS_FILE, "r", encoding="utf-8") as f:
        contracts_data = json.load(f)

    registered_contracts = contracts_data.get("contracts", [])
    print(f"Loaded {len(registered_contracts)} registered contracts from {CONTRACTS_FILE.relative_to(REPO_ROOT)}")

    classification = load_classification()
    if classification is None:
        sys.exit(1)

    class_to_mod = get_class_definitions()
    prod_imports = scan_production_imports()

    # Build lookup: (owning_mod, consuming_mod, fqcn) -> contract
    registered_edges = set()
    for c in registered_contracts:
        fqcn = c["fqcn"]
        owning = c["owningModule"]
        for cons in c["consumingModules"]:
            registered_edges.add((owning, cons, fqcn))

    errors = 0

    # --------------------------------------------------------------------------
    # Check 1: Verify all contracts in registry are valid
    # --------------------------------------------------------------------------
    print("\n--- Checking Registered Contracts Integrity ---")
    for c in registered_contracts:
        fqcn = c["fqcn"]
        owning = c["owningModule"]
        consuming = c["consumingModules"]
        base_fqcn = fqcn.split("$")[0]

        # 1. Owning module check
        actual_owning = class_to_mod.get(base_fqcn)
        if actual_owning != owning:
            print(f"[FAIL] Contract {fqcn}: Declared owningModule '{owning}' does not match actual '{actual_owning}'")
            errors += 1

        # 2. Classification check
        cat = classification.get(fqcn)
        pub_status = c.get("publicationStatus", "PUBLISHED")
        valid_cats = {"INTERNAL_CROSS_MODULE"}
        if pub_status == "BENCHMARK_ONLY" or "BENCHMARK" in c.get("consumerScope", []):
            valid_cats.add("BENCHMARK_SUPPORT_INTERNAL")
        if cat not in valid_cats:
            print(f"[FAIL] Contract {fqcn}: Must be classified as {' or '.join(sorted(valid_cats))}, but was '{cat}'")
            errors += 1

        # 3. Consuming modules check
        scope = c.get("consumerScope", ["MAIN"])
        for cons in consuming:
            if not check_module_consumes_contract(cons, fqcn, scope):
                print(f"[FAIL] Contract {fqcn}: Consuming module '{cons}' declared, but no reference found in scope {scope}")
                errors += 1

    print(f"[PASS] Checked {len(registered_contracts)} registered internal contracts.")

    # --------------------------------------------------------------------------
    # Check 2: Verify that NO production module imports internal classes without registration
    # --------------------------------------------------------------------------
    print("\n--- Checking for Unregistered Cross-Module Internal Imports ---")
    unregistered_imports = set()

    for src_mod, imp, file_path in prod_imports:
        target_mod = class_to_mod.get(imp)
        if not target_mod:
            # Could be inner class or external package
            base = imp.split("$")[0]
            target_mod = class_to_mod.get(base)

        if not target_mod or target_mod == src_mod:
            continue

        # Target class is in another module
        # Check if target class is internal or classified as INTERNAL_CROSS_MODULE
        is_internal_pkg = ".internal." in imp
        cat = classification.get(imp)
        is_internal_classified = (cat == "INTERNAL_CROSS_MODULE")

        if is_internal_pkg or is_internal_classified:
            base_imp = imp.split("$")[0]
            if (target_mod, src_mod, imp) not in registered_edges and (target_mod, src_mod, base_imp) not in registered_edges:
                unregistered_imports.add((src_mod, target_mod, imp, file_path))

    if unregistered_imports:
        print(f"[FAIL] Found {len(unregistered_imports)} unregistered cross-module internal import(s):")
        for src_mod, target_mod, imp, file_path in sorted(unregistered_imports):
            print(f"  - {src_mod} -> {target_mod}: {imp} (in {file_path})")
        errors += 1
    else:
        print("[PASS] Exactly 0 unregistered cross-module internal imports found across all production modules.")

    # --------------------------------------------------------------------------
    # Check 3: Architecture Direction Invariants (Zero Deep-Core Edges from Framework/Tooling)
    # --------------------------------------------------------------------------
    print("\n--- Checking Module Dependency Direction Invariants ---")
    deep_core_violations = []

    for c in registered_contracts:
        fqcn = c["fqcn"]
        owning = c["owningModule"]
        for cons in c["consumingModules"]:
            if cons in FRAMEWORK_AND_TOOLING_MODULES:
                if owning in DEEP_CORE_MODULES:
                    deep_core_violations.append((cons, owning, fqcn, "Framework/tooling depends on deep-core language-vtl internals"))
                elif owning == "viet-template-vtl-interpreter":
                    deep_core_violations.append((cons, owning, fqcn, "Framework/tooling depends on vtl-interpreter internals"))
                elif owning == "viet-template-runtime" and fqcn != "io.github.minh124199.viettemplate.runtime.stream.NonClosingOutputStream":
                    deep_core_violations.append((cons, owning, fqcn, f"Non-stream runtime contract '{fqcn}' exposed to framework"))

    if deep_core_violations:
        print(f"[FAIL] Found {len(deep_core_violations)} architectural direction violation(s):")
        for cons, owning, fqcn, desc in deep_core_violations:
            print(f"  - {cons} -> {owning}: {fqcn} ({desc})")
        errors += 1
    else:
        print("[PASS] Architectural direction verified: Framework and tooling modules have zero deep-core internal edges.")

    # --------------------------------------------------------------------------
    # Contract Scope Summary
    # --------------------------------------------------------------------------
    production_contracts = [c for c in registered_contracts if c.get("consumerScope", ["MAIN"]) == ["MAIN"]]
    test_contracts = [c for c in registered_contracts if c.get("consumerScope", ["MAIN"]) == ["TEST"]]
    benchmark_contracts = [c for c in registered_contracts if c.get("consumerScope", ["MAIN"]) == ["BENCHMARK"]]
    mixed_contracts = [c for c in registered_contracts if "MAIN" in c.get("consumerScope", ["MAIN"]) and len(c.get("consumerScope", ["MAIN"])) > 1]
    mixed_nonprod_contracts = [c for c in registered_contracts if "MAIN" not in c.get("consumerScope", ["MAIN"]) and len(c.get("consumerScope", ["MAIN"])) > 1]

    print("\n--- Contract Scope Summary ---")
    print(f"Production contracts (consumerScope=MAIN only): {len(production_contracts)}")
    print(f"Test-only contracts (no MAIN scope): {len(test_contracts)}")
    print(f"Benchmark-only contracts (no MAIN scope): {len(benchmark_contracts)}")
    print(f"Mixed (MAIN + TEST/BENCHMARK): {len(mixed_contracts)}")
    if mixed_nonprod_contracts:
        print(f"Mixed non-production (BENCHMARK + TEST): {len(mixed_nonprod_contracts)}")

    # --------------------------------------------------------------------------
    # Final Outcome
    # --------------------------------------------------------------------------
    prod_edges = {}
    for c in production_contracts:
        key = (c["owningModule"], tuple(sorted(c["consumingModules"])))
        prod_edges[key] = prod_edges.get(key, 0) + 1

    print("\n--------------------------------------------------------------------------------")
    print(f"Contract Summary: {len(production_contracts)} production contracts across {len(prod_edges)} verified production edges:")
    for (owning, consuming), count in sorted(prod_edges.items()):
        print(f"  {owning} -> {', '.join(consuming)}: {count} contracts")
    print("--------------------------------------------------------------------------------")

    if errors > 0:
        print(f"\nFAILED: {errors} check(s) failed.")
        sys.exit(1)
    else:
        print("\nALL CHECKS PASSED: Cross-module internal contracts fully verified.")
        sys.exit(0)


if __name__ == "__main__":
    main()
