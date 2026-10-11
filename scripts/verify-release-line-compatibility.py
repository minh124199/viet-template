#!/usr/bin/env python3
"""
verify-release-line-compatibility.py

Authoritative CI verification script enforcing 1.4.x patch governance:
1. Validates the immutable v1.4.0 release baseline provenance and integrity.
2. Checks candidate changes against the frozen 1.4.0 public surface:
   - Rejects removal of any STABLE_API or STABLE_SPI type (MAJOR_RELEASE_REQUIRED)
   - Rejects breaking signature changes on stable types (MAJOR_RELEASE_REQUIRED)
   - Rejects adding abstract methods to SPI interfaces (MAJOR_RELEASE_REQUIRED)
   - Rejects new STABLE_API or STABLE_SPI types (MINOR_RELEASE_REQUIRED)
   - Rejects new public members on existing stable types (MINOR_RELEASE_REQUIRED)
   - Rejects new framework entrypoint types (MINOR_RELEASE_REQUIRED)
   - Rejects new framework configuration keys (MINOR_RELEASE_REQUIRED)
   - Rejects modifications to publication topology coordinates (MINOR/MAJOR)
3. Enforces policy gates:
   - --policy patch: Only PATCH_ALLOWED is permitted.
   - --policy minor: PATCH_ALLOWED and MINOR_RELEASE_REQUIRED are permitted.
   - --policy major: Allows breaking changes if explicitly requested.
4. Emits detailed report to build/reports/release-line-compatibility.json.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent

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
]

KNOWN_RELEASE_PROVENANCE: dict[str, dict[str, Any]] = {
    "1.4.0": {
        "tag": "v1.4.0",
        "tagObjectSha": "615ffa9ea291fbd9226e040fd321f7ed018bd01a",
        "sourceCommitSha": "1ee485beee4b7588dd154881ede1e76985e339dc",
        "treeSha": "8ddc255797a8bfce88ee30e1815d8f56e809b61e",
        "releaseDate": "2026-10-11",
        "maintenanceLine": "1.4.x",
        "baselineFilesSha256": {
            "config/compatibility/1.4.0-release-baseline.json": "51405237cf9ba8050172d2ad80515bfbe741e740ef4571ef29a5a49dc5cf9596",
            "config/compatibility/framework-configuration-keys-1.4.json": "8919f29e7a6f9a1879110182ee797dac7ba64cb7d738111cc19b3b7b02cccfd8",
            "config/api-baseline/1.4.0-core-public-api.txt": "0f2686e599848b65618055682374f2f8bfef51e18a690bdf5a3cb19725c35657",
            "config/api-baseline/1.4.0-aot-public-api.txt": "dc56406c9d76fb2817cf067a04e5b2e0d1651f22cbc841499dc8f6ed65c12fd7",
            "config/api-baseline/1.4.0-spring-public-api.txt": "ca162cf44546a7d65486583df2589978ed2ab106fee6cd33344e9dc6432a583d",
            "config/api-baseline/1.4.0-spring-security-public-api.txt": "6eeb4ae45c5471ed49904faf5036e0d20a8435b037a23e53df7518768283b46a",
            "config/api-baseline/1.4.0-quarkus-public-api.txt": "b34c7b47bf30b3d8f5845b78cabffbaab6ec854027b7d4dd9c05617e6af9f646",
        },
    }
}


def run_git(args: list[str], cwd: Path = REPO_ROOT) -> str:
    res = subprocess.run(["git"] + args, cwd=str(cwd), capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"git command failed: git {' '.join(args)}\n{res.stderr}")
    return res.stdout.strip()


def parse_classification_file(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"Classification file not found: {path}")
    mapping: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) >= 2:
            mapping[parts[0]] = parts[1]
    return mapping


def parse_header(header: str) -> dict[str, Any]:
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
            "extends": None,
            "implements": set(),
        }
    h = header.strip().rstrip(" {")
    permits = set()
    if " permits " in h:
        prefix, permits_part = h.split(" permits ", 1)
        h = prefix.strip()
        permits = {p.strip().rstrip(";") for p in permits_part.split(",") if p.strip().rstrip(";")}

    implements = set()
    if " implements " in h:
        prefix, impl_part = h.split(" implements ", 1)
        h = prefix.strip()
        implements = {i.strip().rstrip(";") for i in impl_part.split(",") if i.strip().rstrip(";")}

    extends = None
    if " extends " in h:
        prefix, ext_part = h.split(" extends ", 1)
        h = prefix.strip()
        extends = ext_part.strip().rstrip(";")

    before_name = h
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
        "extends": extends,
        "implements": implements,
    }


def parse_member(m: str) -> dict[str, Any]:
    clean_m = m.strip().rstrip(";")
    is_method = "(" in clean_m
    clean_without_throws = clean_m
    throws_exceptions = set()
    if " throws " in clean_without_throws:
        clean_without_throws, throws_part = clean_without_throws.split(" throws ", 1)
        throws_exceptions = {e.strip() for e in throws_part.split(",") if e.strip()}

    parts = clean_without_throws.split()
    mods = []
    rest_idx = 0
    standard_mods = {
        "public", "protected", "private", "static", "final",
        "abstract", "default", "synchronized", "native"
    }
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
        "throws": throws_exceptions,
    }


def inspect_class(cls_name: str, classpath: str) -> dict[str, Any] | None:
    cmd = [JAVAP, "-protected", "-cp", classpath, cls_name]
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
        "members": members,
    }


def verify_baseline_provenance(
    baseline_data: dict[str, Any],
    repo_root: Path,
    baseline_path: Path | None = None,
) -> list[str]:
    errors = []
    prov = baseline_data.get("provenance", {})
    tag = prov.get("tag")
    if not tag:
        return ["Baseline missing 'provenance.tag' field."]

    release_ver = baseline_data.get("version") or prov.get("releaseVersion")
    known = KNOWN_RELEASE_PROVENANCE.get(release_ver or "")

    try:
        tags = run_git(["tag", "-l", tag], cwd=repo_root).split()
        if tag not in tags:
            errors.append(f"Release tag '{tag}' does not exist in local git repository.")
        else:
            tag_object_sha = run_git(["rev-parse", tag], cwd=repo_root)
            commit_sha = run_git(["rev-parse", f"{tag}^{{commit}}"], cwd=repo_root)
            tree_sha = run_git(["rev-parse", f"{tag}^{{tree}}"], cwd=repo_root)

            if prov.get("tagObjectSha") and tag_object_sha != prov["tagObjectSha"]:
                errors.append(
                    f"Tag object SHA mismatch for {tag}: stored={prov['tagObjectSha']}, git={tag_object_sha}"
                )
            if prov.get("sourceCommitSha") and commit_sha != prov["sourceCommitSha"]:
                errors.append(
                    f"Commit SHA mismatch for {tag}: stored={prov['sourceCommitSha']}, git={commit_sha}"
                )
            if prov.get("treeSha") and tree_sha != prov["treeSha"]:
                errors.append(
                    f"Tree SHA mismatch for {tag}: stored={prov['treeSha']}, git={tree_sha}"
                )

            if known:
                if tag != known["tag"]:
                    errors.append(f"Release tag mismatch: expected '{known['tag']}', got '{tag}'")
                if commit_sha != known["sourceCommitSha"]:
                    errors.append(
                        f"Commit SHA mismatch for {tag}: expected {known['sourceCommitSha']}, got {commit_sha}"
                    )
                if tag_object_sha != known["tagObjectSha"]:
                    errors.append(
                        f"Tag object SHA mismatch for {tag}: expected {known['tagObjectSha']}, got {tag_object_sha}"
                    )
                if tree_sha != known["treeSha"]:
                    errors.append(
                        f"Tree SHA mismatch for {tag}: expected {known['treeSha']}, got {tree_sha}"
                    )

                expected_files = known.get("baselineFilesSha256", {})
                if baseline_path is not None and baseline_path.is_file():
                    matched_expected_sha = None
                    try:
                        rel = str(baseline_path.resolve().relative_to(repo_root.resolve()))
                        if rel in expected_files:
                            matched_expected_sha = (rel, expected_files[rel])
                    except ValueError:
                        pass
                    if not matched_expected_sha:
                        for k, v in expected_files.items():
                            if k.endswith("/" + baseline_path.name):
                                matched_expected_sha = (k, v)
                                break
                    if matched_expected_sha:
                        rel_name, exp_sha = matched_expected_sha
                        actual_sha = hashlib.sha256(baseline_path.read_bytes()).hexdigest()
                        if actual_sha != exp_sha:
                            errors.append(
                                f"BASELINE_INTEGRITY_VIOLATION: Baseline file '{rel_name}' sha256 mismatch: "
                                f"expected {exp_sha}, got {actual_sha}. Baseline files are immutable historical records."
                            )

                for rel_path_str, expected_sha in expected_files.items():
                    fpath = repo_root / rel_path_str
                    if fpath.is_file() and (baseline_path is None or fpath.resolve() != baseline_path.resolve()):
                        actual_sha = hashlib.sha256(fpath.read_bytes()).hexdigest()
                        if actual_sha != expected_sha:
                            errors.append(
                                f"BASELINE_INTEGRITY_VIOLATION: Baseline file '{rel_path_str}' sha256 mismatch: "
                                f"expected {expected_sha}, got {actual_sha}. Baseline files are immutable historical records."
                            )
    except Exception as e:
        errors.append(f"Failed to query git for provenance verification: {e}")

    return errors


def check_candidate_compatibility(
    baseline_path: Path,
    repo_root: Path = REPO_ROOT,
    policy: str = "patch",
    candidate_surface_override: dict[str, Any] | None = None,
    candidate_classif_override: dict[str, str] | None = None,
    candidate_entrypoints_override: dict[str, Any] | None = None,
    candidate_config_keys_override: dict[str, Any] | None = None,
    candidate_topology_override: dict[str, Any] | None = None,
    skip_provenance: bool = False,
) -> dict[str, Any]:
    if not baseline_path.is_file():
        raise FileNotFoundError(f"Baseline file not found: {baseline_path}")

    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))

    provenance_errors = []
    if not skip_provenance:
        provenance_errors = verify_baseline_provenance(baseline, repo_root, baseline_path=baseline_path)

    major_reasons: list[str] = list(provenance_errors)
    minor_reasons: list[str] = []
    patch_notes: list[str] = []

    # 1. Classification delta
    baseline_classif = baseline.get("publicSurface", {}).get("classifications", {})
    baseline_stable_api = set(baseline.get("publicSurface", {}).get("stableApiTypes", []))
    baseline_stable_spi = set(baseline.get("publicSurface", {}).get("stableSpiTypes", []))
    baseline_all_stable = set(baseline.get("publicSurface", {}).get("stableTypes", []))

    if candidate_classif_override is not None:
        cand_classif = candidate_classif_override
    else:
        cand_classif_path = repo_root / "config/api-baseline/public-surface-classification.txt"
        cand_classif = parse_classification_file(cand_classif_path)

    cand_stable_api = {c for c, cat in cand_classif.items() if cat == "STABLE_API"}
    cand_stable_spi = {c for c, cat in cand_classif.items() if cat == "STABLE_SPI"}
    cand_all_stable = cand_stable_api | cand_stable_spi

    # Check for removed stable types (MAJOR)
    removed_stable_api = sorted(baseline_stable_api - cand_stable_api)
    for r in removed_stable_api:
        cat = cand_classif.get(r, "NOT_PRESENT")
        major_reasons.append(f"REMOVED_STABLE_API: Type '{r}' was STABLE_API in baseline, now {cat}.")

    removed_stable_spi = sorted(baseline_stable_spi - cand_stable_spi)
    for r in removed_stable_spi:
        cat = cand_classif.get(r, "NOT_PRESENT")
        major_reasons.append(f"REMOVED_STABLE_SPI: Type '{r}' was STABLE_SPI in baseline, now {cat}.")

    # Check for added stable types (MINOR in semver, prohibited in patch)
    added_stable_api = sorted(cand_stable_api - baseline_stable_api)
    for a in added_stable_api:
        minor_reasons.append(f"NEW_STABLE_API_TYPE: Type '{a}' added to STABLE_API (requires minor release).")

    added_stable_spi = sorted(cand_stable_spi - baseline_stable_spi)
    for a in added_stable_spi:
        minor_reasons.append(f"NEW_STABLE_SPI_TYPE: Type '{a}' added to STABLE_SPI (requires minor release).")

    # 2. Framework Entrypoints delta
    baseline_entrypoint_types = set(
        baseline.get("frameworkEntrypoints", {}).get("frameworkEntrypointTypes", [])
    )
    cand_entrypoint_types = {c for c, cat in cand_classif.items() if cat == "FRAMEWORK_ENTRYPOINT"}

    added_entrypoints = sorted(cand_entrypoint_types - baseline_entrypoint_types)
    for ep in added_entrypoints:
        minor_reasons.append(
            f"NEW_FRAMEWORK_ENTRYPOINT: Entrypoint type '{ep}' added (requires minor release)."
        )

    removed_entrypoints = sorted(baseline_entrypoint_types - cand_entrypoint_types)
    for ep in removed_entrypoints:
        major_reasons.append(
            f"REMOVED_FRAMEWORK_ENTRYPOINT: Entrypoint type '{ep}' removed from baseline."
        )

    # 3. Framework Configuration Keys delta
    baseline_keys = set(baseline.get("frameworkConfigurationKeys", {}).get("keys", []))
    if candidate_config_keys_override is not None:
        cand_keys = set(candidate_config_keys_override.get("allProperties", []))
    else:
        # Load from candidate repo
        config_path = repo_root / "config/compatibility/framework-configuration-keys-1.4.json"
        if not config_path.is_file():
            config_path = repo_root / "config/compatibility/framework-configuration-keys-1.1.json"
        if config_path.is_file():
            c_data = json.loads(config_path.read_text(encoding="utf-8"))
            cand_keys = set(c_data.get("allProperties", []))
            if not cand_keys:
                spring_p = [p["name"] if isinstance(p, dict) else p for p in c_data.get("springBoot", {}).get("properties", [])]
                quarkus_p = [p["name"] if isinstance(p, dict) else p for p in c_data.get("quarkus", {}).get("properties", [])]
                cand_keys = set(spring_p + quarkus_p)
        else:
            cand_keys = baseline_keys

    added_keys = sorted(cand_keys - baseline_keys)
    for k in added_keys:
        minor_reasons.append(f"NEW_CONFIGURATION_KEY: Public property '{k}' added (requires minor release).")

    removed_keys = sorted(baseline_keys - cand_keys)
    for k in removed_keys:
        major_reasons.append(f"REMOVED_CONFIGURATION_KEY: Public property '{k}' removed from baseline.")

    # 4. Publication Topology delta
    baseline_coords = set(baseline.get("publicationTopology", {}).get("coordinates", []))
    if candidate_topology_override is not None:
        cand_coords = set(candidate_topology_override.get("coordinates", []))
    else:
        topo_path = repo_root / "config/compatibility/publication-topology.json"
        if topo_path.is_file():
            t_data = json.loads(topo_path.read_text(encoding="utf-8"))
            coords = [
                f"{m['groupId']}:{m['artifactId']}"
                for m in t_data.get("modules", [])
                if m.get("configuredForPublication")
            ]
            if t_data.get("publicationTaxonomy", {}).get("gradlePluginPublishing", {}).get("mavenCentral"):
                coords.append("io.github.minh124199.viet-template:io.github.minh124199.viet-template.gradle.plugin")
            cand_coords = set(coords)
        else:
            cand_coords = baseline_coords

    added_coords = sorted(cand_coords - baseline_coords)
    for c in added_coords:
        minor_reasons.append(f"NEW_PUBLISHED_COORDINATE: Artifact '{c}' added (requires minor release).")

    removed_coords = sorted(baseline_coords - cand_coords)
    for c in removed_coords:
        major_reasons.append(f"REMOVED_PUBLISHED_COORDINATE: Artifact '{c}' removed from publication.")

    # 5. Member-level Signature Inspection
    class_dirs = []
    for m in MODULES:
        gradle_dir = repo_root / m / "build/classes/java/main"
        maven_dir = repo_root / m / "target/classes"
        if gradle_dir.is_dir():
            class_dirs.append(str(gradle_dir))
        if maven_dir.is_dir():
            class_dirs.append(str(maven_dir))
    full_cp = ":".join(class_dirs)

    baseline_members = baseline.get("members", {})
    signature_changes_count = 0
    member_additions_count = 0

    types_to_inspect = sorted(baseline_all_stable & cand_all_stable)
    for cls in types_to_inspect:
        b_info = baseline_members.get(cls)
        if not b_info:
            continue

        if candidate_surface_override is not None:
            c_info = candidate_surface_override.get(cls)
        else:
            c_info = inspect_class(cls, full_cp)

        if not c_info:
            major_reasons.append(f"MISSING_COMPILED_CLASS: Could not inspect compiled class for '{cls}'.")
            continue

        b_hdr = parse_header(b_info.get("header", ""))
        c_hdr = parse_header(c_info.get("header", ""))

        # Type kind check
        if b_hdr["kind"] != c_hdr["kind"]:
            major_reasons.append(
                f"INCOMPATIBLE_TYPE_KIND in '{cls}': changed from '{b_hdr['kind']}' to '{c_hdr['kind']}'"
            )
            signature_changes_count += 1

        # Final check
        if not b_hdr["is_final"] and c_hdr["is_final"]:
            major_reasons.append(f"INCOMPATIBLE_TYPE_MODIFIER in '{cls}': non-final type became final.")
            signature_changes_count += 1

        # Sealed check
        if not b_hdr["is_sealed"] and c_hdr["is_sealed"]:
            major_reasons.append(f"INCOMPATIBLE_TYPE_MODIFIER in '{cls}': non-sealed type became sealed.")
            signature_changes_count += 1

        # Visibility reduction check
        if "public" in b_hdr["modifiers"] and "public" not in c_hdr["modifiers"]:
            major_reasons.append(f"REDUCED_TYPE_VISIBILITY in '{cls}': public type became protected.")
            signature_changes_count += 1

        # Abstractness check for classes
        if b_hdr["kind"] == "class" and "abstract" not in b_hdr["modifiers"] and "abstract" in c_hdr["modifiers"]:
            major_reasons.append(f"INCOMPATIBLE_TYPE_MODIFIER in '{cls}': non-abstract class became abstract.")
            signature_changes_count += 1

        # Superclass check for classes
        if b_hdr["kind"] == "class" and b_hdr["extends"] != c_hdr["extends"]:
            major_reasons.append(
                f"INCOMPATIBLE_SUPERCLASS in '{cls}': superclass changed from '{b_hdr['extends']}' to '{c_hdr['extends']}'"
            )
            signature_changes_count += 1

        # Implemented interfaces removal check
        removed_interfaces = b_hdr["implements"] - c_hdr["implements"]
        for ri in sorted(removed_interfaces):
            major_reasons.append(f"REMOVED_IMPLEMENTED_INTERFACE in '{cls}': removed interface '{ri}'")
            signature_changes_count += 1

        # Extended interface removal check for interfaces
        if b_hdr["is_interface"] and b_hdr["extends"]:
            b_ext_set = {x.strip() for x in b_hdr["extends"].split(",") if x.strip()}
            c_ext_set = {x.strip() for x in (c_hdr["extends"] or "").split(",") if x.strip()}
            removed_ext = b_ext_set - c_ext_set
            for re in sorted(removed_ext):
                major_reasons.append(f"REMOVED_EXTENDED_INTERFACE in '{cls}': removed extended interface '{re}'")
                signature_changes_count += 1

        b_mem_set = set(b_info.get("members", []))
        c_mem_set = set(c_info.get("members", []))

        b_methods = {parse_member(m)["remainder"]: parse_member(m) for m in b_mem_set if "(" in m}
        c_methods = {parse_member(m)["remainder"]: parse_member(m) for m in c_mem_set if "(" in m}

        # Check method modifier and signature changes on matching methods
        for sig, b_m in b_methods.items():
            if sig in c_methods:
                c_m = c_methods[sig]
                # Visibility reduction (public -> protected)
                if "public" in b_m["modifiers"] and "public" not in c_m["modifiers"]:
                    major_reasons.append(
                        f"REDUCED_MEMBER_VISIBILITY in '{cls}': public method became protected: '{b_m['raw']}'"
                    )
                    signature_changes_count += 1
                # Non-final method becoming final
                if "final" not in b_m["modifiers"] and "final" in c_m["modifiers"] and not b_hdr["is_final"]:
                    major_reasons.append(
                        f"INCOMPATIBLE_MEMBER_MODIFIER in '{cls}': non-final method became final: '{b_m['raw']}'"
                    )
                    signature_changes_count += 1
                if ("static" in b_m["modifiers"]) != ("static" in c_m["modifiers"]):
                    major_reasons.append(
                        f"INCOMPATIBLE_MEMBER_MODIFIER in '{cls}': static modifier changed for '{b_m['raw']}'"
                    )
                    signature_changes_count += 1
                if "default" in b_m["modifiers"] and "abstract" in c_m["modifiers"]:
                    major_reasons.append(
                        f"INCOMPATIBLE_MEMBER_MODIFIER in '{cls}': default method became abstract: '{b_m['raw']}'"
                    )
                    signature_changes_count += 1
                elif "abstract" not in b_m["modifiers"] and "abstract" in c_m["modifiers"]:
                    major_reasons.append(
                        f"INCOMPATIBLE_MEMBER_MODIFIER in '{cls}': concrete method became abstract: '{b_m['raw']}'"
                    )
                    signature_changes_count += 1
                # Checked exceptions for SPI methods
                if cand_classif.get(cls) == "STABLE_SPI":
                    added_throws = c_m["throws"] - b_m["throws"]
                    if added_throws:
                        major_reasons.append(
                            f"INCOMPATIBLE_THROWS in '{cls}': added checked exception(s) {sorted(added_throws)} to SPI method: '{c_m['raw']}'"
                        )
                        signature_changes_count += 1

        # Removed members (MAJOR)
        removed_members = b_mem_set - c_mem_set
        for rm in sorted(removed_members):
            if "(" in rm:
                m_p = parse_member(rm)
                if m_p["remainder"] in c_methods:
                    continue
                words = rm.split("(")[0].split()
                is_ctor = len(words) >= 1 and (words[-1] == cls.split(".")[-1] or words[-1] == cls)
                if is_ctor:
                    major_reasons.append(f"REMOVED_CONSTRUCTOR in '{cls}': '{rm}'")
                else:
                    major_reasons.append(f"REMOVED_METHOD in '{cls}': '{rm}'")
            else:
                major_reasons.append(f"REMOVED_FIELD in '{cls}': '{rm}'")
            signature_changes_count += 1

        # Added members
        added_members = c_mem_set - b_mem_set
        is_spi = (cand_classif.get(cls) == "STABLE_SPI")
        is_config_mapping = cls == "io.github.minh124199.viettemplate.quarkus.VietTemplateConfig"

        for am in sorted(added_members):
            member_additions_count += 1
            is_method = "(" in am
            is_abstract = is_method and (
                "abstract" in am
                or (b_hdr["is_interface"] and "default " not in am and "static " not in am)
            )

            if is_abstract and not is_config_mapping:
                # Abstract method added to interface / SPI breaks external implementors -> MAJOR
                major_reasons.append(
                    f"ADDED_ABSTRACT_INTERFACE_METHOD in '{cls}': '{am}' (breaks external SPI implementors)"
                )
                signature_changes_count += 1
            else:
                # Additive member to existing stable type -> MINOR
                minor_reasons.append(
                    f"NEW_STABLE_MEMBER in '{cls}': '{am}' (feature addition requires minor release)"
                )

    # Classification logic
    if major_reasons:
        classification = "MAJOR_RELEASE_REQUIRED"
    elif minor_reasons:
        classification = "MINOR_RELEASE_REQUIRED"
    else:
        classification = "PATCH_ALLOWED"

    # Policy gate
    if policy == "patch":
        valid = (classification == "PATCH_ALLOWED")
    elif policy == "minor":
        valid = (classification in ("PATCH_ALLOWED", "MINOR_RELEASE_REQUIRED"))
    elif policy == "major":
        valid = True
    else:
        raise ValueError(f"Unknown policy '{policy}'. Supported: patch, minor, major")

    report = {
        "baselineVersion": baseline.get("version", "1.4.0"),
        "baselineSha": baseline.get("provenance", {}).get("sourceCommitSha"),
        "baselineTagObjectSha": baseline.get("provenance", {}).get("tagObjectSha"),
        "candidate": "HEAD",
        "policy": policy.upper(),
        "stableApiRemovals": len(removed_stable_api),
        "stableSpiRemovals": len(removed_stable_spi),
        "signatureChanges": signature_changes_count,
        "stableApiAdditions": len(added_stable_api),
        "stableSpiAdditions": len(added_stable_spi),
        "memberAdditions": member_additions_count,
        "frameworkEntryAdditions": len(added_entrypoints),
        "configKeyAdditions": len(added_keys),
        "artifactTopologyChanges": len(added_coords) + len(removed_coords),
        "classification": classification,
        "valid": valid,
        "majorReasons": major_reasons,
        "minorReasons": minor_reasons,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    return report


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify Viet Template maintenance line patch compatibility")
    parser.add_argument("--baseline", default="1.4.0", help="Baseline version or path (default: 1.4.0)")
    parser.add_argument("--candidate", default="HEAD", help="Candidate revision (default: HEAD)")
    parser.add_argument("--policy", choices=["patch", "minor", "major"], default="patch", help="Enforcement policy (default: patch)")
    parser.add_argument("--report", type=Path, default=REPO_ROOT / "build/reports/release-line-compatibility.json", help="Path to write JSON report")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help="Repository root path")
    args = parser.parse_args()

    baseline_path = Path(args.baseline)
    if not baseline_path.is_file():
        baseline_path = args.repo_root / f"config/compatibility/{args.baseline}-release-baseline.json"
        if not baseline_path.is_file():
            print(f"Error: Baseline file not found: {baseline_path}", file=sys.stderr)
            return 1

    report = check_candidate_compatibility(
        baseline_path=baseline_path,
        repo_root=args.repo_root,
        policy=args.policy,
    )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print("=" * 70)
    print("VIET TEMPLATE RELEASE-LINE COMPATIBILITY VERIFICATION")
    print("=" * 70)
    print(f"Baseline:                  v{report['baselineVersion']}")
    print(f"Candidate:                 {args.candidate}")
    print(f"Policy:                    {report['policy']}")
    print("-" * 70)
    print(f"Stable API removals:       {report['stableApiRemovals']}")
    print(f"Stable SPI removals:       {report['stableSpiRemovals']}")
    print(f"Signature changes:         {report['signatureChanges']}")
    print(f"Stable API additions:      {report['stableApiAdditions']}")
    print(f"Stable SPI additions:      {report['stableSpiAdditions']}")
    print(f"Member additions:          {report['memberAdditions']}")
    print(f"Framework entry additions: {report['frameworkEntryAdditions']}")
    print(f"Config-key additions:      {report['configKeyAdditions']}")
    print(f"Artifact topology changes: {report['artifactTopologyChanges']}")
    print("-" * 70)
    print(f"Classification:            {report['classification']}")
    print("=" * 70)

    if not report["valid"]:
        print(f"\n[FAILURE] Candidate does not satisfy '{report['policy']}' policy gate.\n", file=sys.stderr)
        if report["majorReasons"]:
            print("Breaking Changes (Requires MAJOR release):", file=sys.stderr)
            for r in report["majorReasons"]:
                print(f"  - {r}", file=sys.stderr)
        if report["minorReasons"] and report["policy"] == "PATCH":
            print("\nFeature Additions Prohibited in Patch (Requires MINOR release):", file=sys.stderr)
            for r in report["minorReasons"]:
                print(f"  - {r}", file=sys.stderr)
            print("\n1.4.x patch policy violation:", file=sys.stderr)
            print("Move feature additions to a 1.5.0 feature line or remove them from this patch.", file=sys.stderr)
        return 1

    print(f"\n[SUCCESS] Candidate satisfies '{report['policy']}' policy gate: {report['classification']}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
