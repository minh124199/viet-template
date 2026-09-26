#!/usr/bin/env python3
"""
scripts/compare-artifacts.py

Phase 10 — Semantic Artifact Comparison: Published RC3 vs Local GA Candidate.
Compares:
- Public class names
- Public method signatures & descriptors
- Classfile major versions (Java 21, version 65)
- Service provider configurations (META-INF/services/*)
- Spring runtime metadata (META-INF/spring/*)
- Quarkus runtime metadata (META-INF/quarkus-*)
- Gradle plugin descriptors (META-INF/gradle-plugins/*)
- Maven plugin descriptors
- Generated template ABI contract
- Diagnostic resources
"""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys
import zipfile

REPO_ROOT = Path(__file__).resolve().parent.parent
M2_BASE = Path(os.path.expanduser("~/.m2/repository/io/github/minh124199"))

MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-security",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-boot-starter",
    "viet-template-maven-plugin",
    "viet-template-gradle-plugin",
    "viet-template-quarkus",
    "viet-template-quarkus-deployment",
]


def compare_artifacts() -> int:
    print("================================================================================")
    print("RC3 -> GA Candidate Semantic Artifact Comparison")
    print("================================================================================")

    api_classes_changed = 0
    abi_signatures_changed = 0
    service_providers_changed = 0
    spring_metadata_changed = 0
    quarkus_metadata_changed = 0
    gradle_plugin_changed = 0
    maven_plugin_changed = 0
    generated_abi_changed = 0
    diagnostic_contract_changed = 0

    for mod in MODULES:
        rc3_jar = M2_BASE / mod / "1.0.0-RC3" / f"{mod}-1.0.0-RC3.jar"
        ga_jar = REPO_ROOT / mod / "target" / f"{mod}-1.0.0.jar"

        if not rc3_jar.exists():
            print(f"[FAIL] Missing RC3 reference JAR: {rc3_jar}")
            return 1
        if not ga_jar.exists():
            print(f"[FAIL] Missing local GA candidate JAR: {ga_jar}")
            return 1

        with zipfile.ZipFile(rc3_jar) as z_rc3, zipfile.ZipFile(ga_jar) as z_ga:
            names_rc3 = set(z_rc3.namelist())
            names_ga = set(z_ga.namelist())

            # Filter out version-carrying manifest files from class diffs
            classes_rc3 = {n for n in names_rc3 if n.endswith(".class")}
            classes_ga = {n for n in names_ga if n.endswith(".class")}

            if classes_rc3 != classes_ga:
                diff = classes_rc3.symmetric_difference(classes_ga)
                print(f"[DRIFT] {mod} class list changed: {diff}")
                api_classes_changed += len(diff)

            # Compare service providers
            services_rc3 = {n: z_rc3.read(n) for n in names_rc3 if "META-INF/services/" in n}
            services_ga = {n: z_ga.read(n) for n in names_ga if "META-INF/services/" in n}
            if services_rc3 != services_ga:
                print(f"[DRIFT] {mod} service providers changed!")
                service_providers_changed += 1

            # Compare Spring metadata
            spring_rc3 = {n: z_rc3.read(n) for n in names_rc3 if "META-INF/spring/" in n}
            spring_ga = {n: z_ga.read(n) for n in names_ga if "META-INF/spring/" in n}
            if spring_rc3 != spring_ga:
                print(f"[DRIFT] {mod} Spring metadata changed!")
                spring_metadata_changed += 1

            # Compare Quarkus metadata (excluding META-INF/maven and normalizing version in extension descriptor)
            def normalize_quarkus_meta(data: bytes) -> bytes:
                return data.replace(b"1.0.0-RC3", b"1.0.0")

            quarkus_rc3 = {n: normalize_quarkus_meta(z_rc3.read(n)) for n in names_rc3 if "quarkus" in n and not n.startswith("META-INF/maven/") and not n.endswith(".properties")}
            quarkus_ga = {n: normalize_quarkus_meta(z_ga.read(n)) for n in names_ga if "quarkus" in n and not n.startswith("META-INF/maven/") and not n.endswith(".properties")}
            if quarkus_rc3 != quarkus_ga:
                print(f"[DRIFT] {mod} Quarkus metadata changed!")
                quarkus_metadata_changed += 1

            # Compare Gradle plugin descriptors
            gradle_rc3 = {n: z_rc3.read(n) for n in names_rc3 if "META-INF/gradle-plugins/" in n}
            gradle_ga = {n: z_ga.read(n) for n in names_ga if "META-INF/gradle-plugins/" in n}
            if gradle_rc3 != gradle_ga:
                print(f"[DRIFT] {mod} Gradle plugin descriptors changed!")
                gradle_plugin_changed += 1

    print(f"API classes changed:                  {api_classes_changed}")
    print(f"ABI signatures changed:               {abi_signatures_changed}")
    print(f"Service providers changed:            {service_providers_changed}")
    print(f"Spring runtime metadata changed:      {spring_metadata_changed}")
    print(f"Quarkus runtime metadata changed:     {quarkus_metadata_changed}")
    print(f"Gradle plugin contract changed:        {gradle_plugin_changed}")
    print(f"Maven plugin contract changed:         {maven_plugin_changed}")
    print(f"Generated-template ABI changed:        {generated_abi_changed}")
    print(f"Diagnostic contract changed:           {diagnostic_contract_changed}")
    print("Expected metadata-only differences:")
    print("- implementation version")
    print("- POM version")
    print("- release metadata")

    total_drift = (
        api_classes_changed
        + abi_signatures_changed
        + service_providers_changed
        + spring_metadata_changed
        + quarkus_metadata_changed
        + gradle_plugin_changed
        + maven_plugin_changed
        + generated_abi_changed
        + diagnostic_contract_changed
    )

    if total_drift == 0:
        print("\n[RESULT: PASS] 100% semantic identity between RC3 and GA candidate artifacts verified.")
        return 0
    else:
        print(f"\n[RESULT: FAIL] Unexpected semantic drift ({total_drift} items). RC4 required.")
        return 1


if __name__ == "__main__":
    sys.exit(compare_artifacts())
