#!/usr/bin/env python3
"""
Category C Boundary & Reason Analysis Script for Viet Template (0.3.0-M3).

Analyzes all Category-C accidental public types to determine their exact usage,
producers, consumers, sealed hierarchy constraints, metadata entrypoints,
and stable API signature exposures.

Outputs: build/reports/category-c-boundary-analysis.json
"""

import json
import os
import re
import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-security",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-boot-starter",
    "viet-template-tck",
    "viet-template-benchmarks",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
]

def load_classification():
    class_file = REPO_ROOT / "config/api-baseline/public-surface-classification.txt"
    classification = {}
    for line in class_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) >= 2:
            classification[parts[0]] = parts[1]
    return classification

def load_inventory():
    inv_file = REPO_ROOT / "config/api-baseline/accidental-public-types-inventory.json"
    return json.loads(inv_file.read_text(encoding="utf-8"))

def load_contracts():
    contracts_file = REPO_ROOT / "config/architecture/cross-module-internal-contracts.json"
    if not contracts_file.is_file():
        return {}
    data = json.loads(contracts_file.read_text(encoding="utf-8"))
    return {c["fqcn"]: c for c in data.get("contracts", [])}

def build_classpath():
    cp_parts = []
    for m in MODULES:
        for p in ["build/classes/java/main", "build/classes/java/test", "target/classes", "target/test-classes"]:
            d = REPO_ROOT / m / p
            if d.is_dir():
                cp_parts.append(str(d))
    return os.pathsep.join(cp_parts)

def get_stable_signature_leaks(stable_types, candidate_types, cp):
    """Inspects all stable types using javap to find exact member signature leaks."""
    targets = sorted(list(candidate_types), key=len, reverse=True)
    pattern = re.compile(r"\b(" + "|".join(re.escape(t) for t in targets) + r")\b")
    leaks_by_type = {}

    for cls in sorted(stable_types):
        cmd = ["javap", "-protected", "-cp", cp, cls]
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
                        leaks_by_type.setdefault(m, []).append({
                            "exposing_class": cls,
                            "signature": l
                        })
    return leaks_by_type

def scan_file_usages(candidate_fqcns):
    """Scans all source, test, benchmark, and resource files for occurrences of candidate FQCNs or simple names."""
    # Maps FQCN -> usage dictionaries
    usages = {fqcn: {
        "production_users": set(),
        "test_users": set(),
        "benchmark_users": False,
        "framework_users": set(),
        "build_tooling_users": set(),
        "metadata_users": set(),
        "reflection_users": set(),
        "is_sealed_permit": False,
        "sealed_parent": None
    } for fqcn in candidate_fqcns}

    # Precompile regexes for each candidate
    # Notice: a file may import FQCN, or use simple name if in same package or wildcard import
    # We check full FQCN references as well as specific package usages
    fqcn_to_parts = {}
    for fqcn in candidate_fqcns:
        simple = fqcn.split("$")[-1] if "$" in fqcn else fqcn.split(".")[-1]
        base_fqcn = fqcn.split("$")[0]
        pkg = fqcn[:fqcn.rfind(".")] if "$" not in fqcn else fqcn[:fqcn.index("$")].rfind(".")
        if "$" in fqcn:
            pkg = fqcn[:fqcn.index("$")]
            pkg = pkg[:pkg.rfind(".")]
        else:
            pkg = fqcn[:fqcn.rfind(".")]
        fqcn_to_parts[fqcn] = (simple, base_fqcn, pkg)

    # 1. Scan metadata files
    for root, _, files in os.walk(REPO_ROOT):
        if "/.git" in root or "/build/" in root or "/target/" in root:
            continue
        for f in files:
            path = Path(root) / f
            rel = path.relative_to(REPO_ROOT)
            rel_str = str(rel)
            if "META-INF" in rel_str or rel_str.endswith(".factories") or rel_str.endswith(".imports"):
                content = path.read_text(encoding="utf-8", errors="ignore")
                for fqcn in candidate_fqcns:
                    if fqcn in content:
                        usages[fqcn]["metadata_users"].add(rel_str)

    # 2. Scan Java sources across modules
    for mod in MODULES:
        mod_dir = REPO_ROOT / mod
        if not mod_dir.is_dir():
            continue
        for root, _, files in os.walk(mod_dir):
            if "/build/" in root or "/target/" in root:
                continue
            for f in files:
                if not f.endswith(".java"):
                    continue
                file_path = Path(root) / f
                content = file_path.read_text(encoding="utf-8", errors="ignore")
                is_test = "/src/test/" in str(file_path)
                is_jmh = "/src/jmh/" in str(file_path)
                is_prod = "/src/main/" in str(file_path)

                # Check sealed hierarchy in content
                match = re.search(r"sealed\s+(?:public\s+|protected\s+)?(?:interface|class)\s+([A-Za-z0-9_]+)\b[^{]*?\bpermits\s+([^;{]+)", content, re.DOTALL)
                if match:
                    parent_name = match.group(1)
                    permits_str = match.group(2)
                    for fqcn in candidate_fqcns:
                        simple, base_fqcn, pkg = fqcn_to_parts[fqcn]
                        if re.search(r"\b" + re.escape(simple) + r"\b", permits_str):
                            usages[fqcn]["is_sealed_permit"] = True
                            parent_pkg = ""
                            for line in content.splitlines():
                                if line.strip().startswith("package "):
                                    parent_pkg = line.strip().split()[1].rstrip(";")
                            usages[fqcn]["sealed_parent"] = f"{parent_pkg}.{parent_name}"

                # Check usage
                for fqcn in candidate_fqcns:
                    simple, base_fqcn, pkg = fqcn_to_parts[fqcn]
                    uses_candidate = False
                    file_path_str = str(file_path).replace(os.sep, "/")
                    def_suffix = pkg.replace(".", "/") + "/" + base_fqcn.split(".")[-1] + ".java"
                    is_definition = file_path_str.endswith(def_suffix)
                    has_simple_word = bool(re.search(r"\b" + re.escape(simple) + r"\b", content))

                    if fqcn in content or f"import {base_fqcn};" in content or f"import {fqcn};" in content:
                        if not is_definition:
                            uses_candidate = True
                    elif f"import {pkg}.*;" in content and has_simple_word:
                        if not is_definition:
                            uses_candidate = True
                    elif f"package {pkg};" in content and has_simple_word:
                        if not is_definition:
                            uses_candidate = True

                    if uses_candidate:
                        if mod == "viet-template-benchmarks" or is_jmh:
                            usages[fqcn]["benchmark_users"] = True
                        elif is_prod:
                            usages[fqcn]["production_users"].add(mod)
                        elif is_test:
                            usages[fqcn]["test_users"].add(mod)

                        if "spring" in mod:
                            usages[fqcn]["framework_users"].add(mod)
                        elif "quarkus" in mod:
                            usages[fqcn]["framework_users"].add(mod)
                        elif "plugin" in mod:
                            usages[fqcn]["build_tooling_users"].add(mod)

    return usages

def main():
    classification = load_classification()
    inventory = load_inventory()
    cp = build_classpath()

    stable_types = {cls for cls, cat in classification.items() if cat in ("STABLE_API", "STABLE_SPI")}
    cat_c_entries = [
        e for e in inventory
        if e.get("category", "").startswith("C")
        and classification.get(e["fqcn"]) in ("PUBLIC_BUT_INTERNAL_ACCIDENT", "INTERNAL_CROSS_MODULE", "FRAMEWORK_ENTRYPOINT")
    ]
    candidate_fqcns = {e["fqcn"] for e in cat_c_entries}

    print(f"Auditing signature leaks for {len(candidate_fqcns)} Category-C types across {len(stable_types)} stable types...")
    leaks_by_type = get_stable_signature_leaks(stable_types, candidate_fqcns, cp)
    print(f"Found {len(leaks_by_type)} Category-C types exposed in stable signatures.")

    print(f"Scanning usage across all modules, tests, benchmarks, and metadata...")
    usages = scan_file_usages(candidate_fqcns)

    # Perform Reason Subcategorization
    # C1 — TRUE_PRODUCTION_CROSS_MODULE_CONTRACT
    # C2 — BENCHMARK_OR_TEST_INDUCED_VISIBILITY
    # C3 — STABLE_SIGNATURE_EXPOSURE
    # C4 — SEALED_HIERARCHY_OR_LANGUAGE_TOPOLOGY
    # C5 — FRAMEWORK_METADATA_ENTRYPOINT
    # C6 — BUILD_TOOLING_CROSS_MODULE_USE
    # C7 — MODULE_BOUNDARY_ARTIFACT
    # C8 — GENERATED_OR_RUNTIME_SPECIAL_CASE
    # C9 — TEMPORARY_UNRESOLVED

    analysis_records = []
    subcat_counts = {}

    for entry in cat_c_entries:
        fqcn = entry["fqcn"]
        mod = entry["module"]
        pkg = entry["package"]
        u = usages.get(fqcn, {})
        leaks = leaks_by_type.get(fqcn, [])

        prod_users = sorted(list(u.get("production_users", set()) - {mod}))
        test_users = sorted(list(u.get("test_users", set()) - {mod}))
        bench_user = u.get("benchmark_users", False)
        fw_users = sorted(list(u.get("framework_users", set())))
        tooling_users = sorted(list(u.get("build_tooling_users", set())))
        meta_users = sorted(list(u.get("metadata_users", set())))
        is_sealed = u.get("is_sealed_permit", False)
        sealed_parent = u.get("sealed_parent", None)

        # Decide primary reason subcategory
        subcat = None
        reason = ""

        if leaks:
            subcat = "C3 — STABLE_SIGNATURE_EXPOSURE"
            exposing = sorted(list({leak["exposing_class"] for leak in leaks}))
            reason = f"Directly exposed in public/protected signature of stable API(s): {', '.join(exposing)}"
        elif meta_users or fqcn in (
            "io.github.minh124199.viettemplate.spring.boot.autoconfigure.aot.VietTemplateRuntimeHints",
            "io.github.minh124199.viettemplate.spring.security.aot.VietTemplateSecurityRuntimeHints",
            "io.github.minh124199.viettemplate.quarkus.VietTemplateProducer"
        ):
            subcat = "C5 — FRAMEWORK_METADATA_ENTRYPOINT"
            reason = f"Referenced in framework metadata / CDI entrypoints: {', '.join(meta_users) if meta_users else 'CDI / AOT entrypoint'}"
        elif is_sealed or fqcn == "io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability":
            subcat = "C4 — SEALED_HIERARCHY_OR_LANGUAGE_TOPOLOGY"
            reason = (
                "Enum exposed in public sealed hierarchy VType (nullability(), withNullability()); constrained by Java language visibility rules"
                if "Nullability" in fqcn
                else f"Permitted subclass of sealed hierarchy {sealed_parent}; constrained by JLS 8.1.6 co-location rule"
            )
        elif not prod_users and (bench_user or test_users):
            subcat = "C2 — BENCHMARK_OR_TEST_INDUCED_VISIBILITY"
            reasons = []
            if bench_user:
                reasons.append("benchmarks")
            if test_users:
                reasons.append(f"cross-module tests ({', '.join(test_users)})")
            reason = f"No other production module consumes this; visibility forced only by {' and '.join(reasons)}"
        elif tooling_users and not (set(prod_users) - set(tooling_users)):
            subcat = "C6 — BUILD_TOOLING_CROSS_MODULE_USE"
            reason = f"Consumed across module boundary by build plugins: {', '.join(tooling_users)}"
        elif prod_users:
            subcat = "C1 — TRUE_PRODUCTION_CROSS_MODULE_CONTRACT"
            reason = f"Cross-module production dependency consumed by: {', '.join(prod_users)}"
        elif not prod_users and not test_users and not bench_user:
            subcat = "C7 — MODULE_BOUNDARY_ARTIFACT"
            reason = "No cross-module consumers detected; internal to owning module (potential candidate for intra-module internal package)"
        else:
            subcat = "C9 — TEMPORARY_UNRESOLVED"
            reason = "Complex or unassigned dependency pattern"

        subcat_counts[subcat] = subcat_counts.get(subcat, 0) + 1

        record = {
            "fqcn": fqcn,
            "module": mod,
            "package": pkg,
            "visibility": "public",
            "classification": classification.get(fqcn, "PUBLIC_BUT_INTERNAL_ACCIDENT"),
            "subcategory": subcat,
            "reason": reason,
            "production_users": prod_users,
            "test_users": test_users,
            "benchmark_users": bench_user,
            "framework_users": fw_users,
            "build_tooling_users": tooling_users,
            "metadata_users": meta_users,
            "stable_signature_users": [leak["exposing_class"] for leak in leaks],
            "stable_signature_details": leaks,
            "is_sealed_permit": is_sealed,
            "sealed_parent": sealed_parent
        }
        analysis_records.append(record)

    # Sort records by FQCN
    analysis_records.sort(key=lambda r: r["fqcn"])

    out_dir = REPO_ROOT / "build/reports"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "category-c-boundary-analysis.json"
    out_file.write_text(json.dumps(analysis_records, indent=2) + "\n", encoding="utf-8")
    print(f"\nReport written to: {out_file} ({len(analysis_records)} records)")

    print("\n================================================================================")
    print("CATEGORY C REASON BREAKDOWN SUMMARY")
    print("================================================================================")
    for subcat in sorted(subcat_counts.keys()):
        print(f"  {subcat}: {subcat_counts[subcat]} types")
    print(f"  TOTAL: {len(analysis_records)} types")
    print("================================================================================\n")

if __name__ == "__main__":
    main()
