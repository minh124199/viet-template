#!/usr/bin/env python3
"""
generate-release-baseline.py

Authoritative generator for Viet Template maintenance-line release baselines:
- Inspects the official, immutable release tag (e.g. v1.4.0)
- Validates release tag object SHA, commit SHA, and tree SHA
- Extracts all classified stable types (STABLE_API and STABLE_SPI)
- Runs javap on compiled classes from the release checkout to extract
  exact member-level signatures (constructors, methods, fields)
- Freezes framework entrypoints, configuration keys, publication coordinates,
  generated ABI, and diagnostic codes
- Generates:
  * config/compatibility/<release>-release-baseline.json
  * config/api-baseline/<release>-core-public-api.txt
  * config/api-baseline/<release>-aot-public-api.txt
  * config/api-baseline/<release>-spring-public-api.txt
  * config/api-baseline/<release>-spring-security-public-api.txt
  * config/api-baseline/<release>-quarkus-public-api.txt
  * config/compatibility/framework-configuration-keys-<major.minor>.json
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
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

KNOWN_RELEASE_PROVENANCE = {
    "1.4.0": {
        "tag": "v1.4.0",
        "tagObjectSha": "615ffa9ea291fbd9226e040fd321f7ed018bd01a",
        "sourceCommitSha": "1ee485beee4b7588dd154881ede1e76985e339dc",
        "treeSha": "8ddc255797a8bfce88ee30e1815d8f56e809b61e",
        "releaseDate": "2026-10-11",
        "maintenanceLine": "1.4.x",
    }
}


def run_git(args: list[str], cwd: Path = REPO_ROOT) -> str:
    res = subprocess.run(["git"] + args, cwd=str(cwd), capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"git command failed: git {' '.join(args)}\n{res.stderr}")
    return res.stdout.strip()


def verify_release_tag_provenance(release: str, tag: str, repo_root: Path) -> dict[str, str]:
    tags = run_git(["tag", "-l", tag], cwd=repo_root).split()
    if tag not in tags:
        raise ValueError(f"Release tag '{tag}' does not exist in repository.")

    tag_object_sha = run_git(["rev-parse", tag], cwd=repo_root)
    commit_sha = run_git(["rev-parse", f"{tag}^{{commit}}"], cwd=repo_root)
    tree_sha = run_git(["rev-parse", f"{tag}^{{tree}}"], cwd=repo_root)

    expected = KNOWN_RELEASE_PROVENANCE.get(release)
    if expected:
        if tag != expected["tag"]:
            raise ValueError(f"Tag mismatch for {release}: expected {expected['tag']}, got {tag}")
        if tag_object_sha != expected["tagObjectSha"]:
            raise ValueError(
                f"Tag object SHA mismatch for {tag}: expected {expected['tagObjectSha']}, got {tag_object_sha}"
            )
        if commit_sha != expected["sourceCommitSha"]:
            raise ValueError(
                f"Commit SHA mismatch for {tag}: expected {expected['sourceCommitSha']}, got {commit_sha}"
            )
        if tree_sha != expected["treeSha"]:
            raise ValueError(
                f"Tree SHA mismatch for {tag}: expected {expected['treeSha']}, got {tree_sha}"
            )

    return {
        "releaseVersion": release,
        "tag": tag,
        "tagObjectSha": tag_object_sha,
        "sourceCommitSha": commit_sha,
        "treeSha": tree_sha,
        "releaseDate": expected["releaseDate"] if expected else datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "maintenanceLine": f"{release.rsplit('.', 1)[0]}.x",
    }


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


def inspect_class_from_cp(cls_name: str, classpath: str) -> dict[str, Any] | None:
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


def categorize_types_into_baselines(stable_types: list[str]) -> dict[str, list[str]]:
    """Partitions the 205 stable types into the 5 standard baseline categories."""
    groups: dict[str, list[str]] = {
        "core": [],
        "aot": [],
        "spring": [],
        "spring-security": [],
        "quarkus": [],
    }

    for fqcn in sorted(stable_types):
        if fqcn.startswith("io.github.minh124199.viettemplate.quarkus"):
            groups["quarkus"].append(fqcn)
        elif fqcn.startswith("io.github.minh124199.viettemplate.spring.security"):
            groups["spring-security"].append(fqcn)
        elif fqcn.startswith("io.github.minh124199.viettemplate.spring"):
            groups["spring"].append(fqcn)
        elif (
            fqcn.startswith("io.github.minh124199.viettemplate.aot")
            or fqcn.startswith("io.github.minh124199.viettemplate.schema")
            or fqcn.startswith("io.github.minh124199.viettemplate.validation")
            or fqcn.startswith("io.github.minh124199.viettemplate.explanation")
            or fqcn.startswith("io.github.minh124199.viettemplate.migration")
        ):
            groups["aot"].append(fqcn)
        else:
            groups["core"].append(fqcn)

    return groups


def format_baseline_text(group_name: str, release: str, types: list[str], member_map: dict[str, Any]) -> str:
    lines = [
        f"# Viet Template {release} Public API and SPI Baseline ({group_name})",
        "# Machine-readable public signatures for source and binary compatibility enforcement",
        "# Format: TYPE <class-header>",
        "#         MEMBER <member-signature>",
        "",
    ]
    for cls in sorted(types):
        info = member_map.get(cls)
        if not info:
            continue
        lines.append(f"TYPE {info['header']}")
        for m in sorted(info["members"]):
            lines.append(f"  MEMBER {m}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def generate_baseline(
    release: str = "1.4.0",
    tag: str = "v1.4.0",
    worktree_path: Path | None = None,
    output_root: Path = REPO_ROOT,
    check_only: bool = False,
) -> dict[str, Any]:
    provenance = verify_release_tag_provenance(release, tag, REPO_ROOT)

    cleanup_worktree = False
    if worktree_path is None:
        worktree_path = Path(f"/tmp/viet-template-{release}-gen-baseline")
        if worktree_path.exists():
            subprocess.run(["git", "worktree", "remove", "--force", str(worktree_path)], cwd=str(REPO_ROOT), check=False)
        run_git(["worktree", "add", "--detach", str(worktree_path), tag], cwd=REPO_ROOT)
        cleanup_worktree = True
        try:
            gradlew = worktree_path / "gradlew"
            subprocess.run([str(gradlew), "classes", "--no-daemon"], cwd=str(worktree_path), check=True)
        except Exception:
            if cleanup_worktree and worktree_path.exists():
                subprocess.run(["git", "worktree", "remove", "--force", str(worktree_path)], cwd=str(REPO_ROOT), check=False)
            raise

    try:
        class_dirs = [worktree_path / m / "build/classes/java/main" for m in MODULES]
        full_cp = ":".join(str(d) for d in class_dirs if d.is_dir())

        classif_file = worktree_path / "config/api-baseline/public-surface-classification.txt"
        classifications = parse_classification_file(classif_file)

        stable_api_types = sorted([c for c, cat in classifications.items() if cat == "STABLE_API"])
        stable_spi_types = sorted([c for c, cat in classifications.items() if cat == "STABLE_SPI"])
        all_stable_types = sorted(stable_api_types + stable_spi_types)

        member_map: dict[str, Any] = {}
        for cls in all_stable_types:
            info = inspect_class_from_cp(cls, full_cp)
            if not info:
                raise RuntimeError(f"Failed to inspect compiled class via javap: {cls}")
            member_map[cls] = info

        category_counts: dict[str, int] = {}
        for cat in classifications.values():
            category_counts[cat] = category_counts.get(cat, 0) + 1
        category_counts["TOTAL_STABLE"] = len(all_stable_types)

        entrypoints_json_path = worktree_path / "config/architecture/framework-and-tooling-entrypoints.json"
        entrypoints_data = json.loads(entrypoints_json_path.read_text(encoding="utf-8")) if entrypoints_json_path.is_file() else {}
        registered_entrypoints = entrypoints_data.get("entrypoints", [])
        framework_entrypoint_types = sorted([
            c for c, cat in classifications.items() if cat == "FRAMEWORK_ENTRYPOINT"
        ])

        topology_json_path = worktree_path / "config/compatibility/publication-topology.json"
        pub_topology = json.loads(topology_json_path.read_text(encoding="utf-8")) if topology_json_path.is_file() else {}
        published_modules = [
            f"{m['groupId']}:{m['artifactId']}"
            for m in pub_topology.get("modules", [])
            if m.get("configuredForPublication")
        ]
        if pub_topology.get("publicationTaxonomy", {}).get("gradlePluginPublishing", {}).get("mavenCentral"):
            published_modules.append("io.github.minh124199.viet-template:io.github.minh124199.viet-template.gradle.plugin")
        published_modules.sort()

        spring_props = [
            "viet-template.enabled",
            "viet-template.prefix",
            "viet-template.suffix",
            "viet-template.suffixes",
            "viet-template.content-type",
            "viet-template.charset",
            "viet-template.cache",
            "viet-template.check-template-location",
            "viet-template.runtime-compilation-enabled",
            "viet-template.max-cache-entries",
            "viet-template.negative-cache-ttl-millis",
            "viet-template.hot-reload",
            "viet-template.watch-debounce-millis",
            "viet-template.order",
            "viet-template.auto-escape",
            "viet-template.security.enabled",
            "viet-template.assets.enabled",
            "viet-template.assets.provider",
            "viet-template.assets.mode",
            "viet-template.assets.public-base",
            "viet-template.assets.module-preload",
            "viet-template.assets.fail-fast",
            "viet-template.assets.manifest-location",
            "viet-template.assets.dev-server",
            "viet-template.assets.vite.manifest",
            "viet-template.assets.vite.dev-server",
        ]
        quarkus_props = [
            "quarkus.viet-template.path",
            "quarkus.viet-template.suffix",
            "quarkus.viet-template.additional-suffixes",
            "quarkus.viet-template.runtime-compilation-enabled",
            "quarkus.viet-template.cache-max-entries",
            "quarkus.viet-template.negative-cache-ttl-millis",
            "quarkus.viet-template.undefined-reference-policy",
            "quarkus.viet-template.encoding",
            "quarkus.viet-template.profile",
            "quarkus.viet-template.assets.enabled",
            "quarkus.viet-template.assets.provider",
            "quarkus.viet-template.assets.mode",
            "quarkus.viet-template.assets.public-base",
            "quarkus.viet-template.assets.module-preload",
            "quarkus.viet-template.assets.fail-fast",
            "quarkus.viet-template.assets.manifest-location",
            "quarkus.viet-template.assets.dev-server",
        ]
        all_config_keys = sorted(spring_props + quarkus_props)

        frontend_types = sorted([
            cls for cls in classifications.keys()
            if cls.startswith("io.github.minh124199.viettemplate.assets.")
            or "Asset" in cls
        ])

        baseline_groups = categorize_types_into_baselines(all_stable_types)

        config_keys_payload = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "version": "1.4",
            "contractName": f"Viet Template {release} Framework Configuration Keys Contract",
            "description": f"Authoritative machine-readable inventory of frozen public configuration keys for Spring Boot and Quarkus framework integrations for Viet Template {release}.",
            "status": "FROZEN_1_4_CONFIG_KEYS",
            "provenance": provenance,
            "springBoot": {
                "sourceClasses": [
                    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties",
                    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateAssetProperties",
                ],
                "prefixes": ["viet-template", "viet-template.assets"],
                "propertiesCount": len(spring_props),
                "propertyNames": sorted(spring_props),
            },
            "quarkus": {
                "sourceClass": "io.github.minh124199.viettemplate.quarkus.VietTemplateConfig",
                "prefix": "quarkus.viet-template",
                "propertiesCount": len(quarkus_props),
                "propertyNames": sorted(quarkus_props),
            },
            "totalPropertiesCount": len(all_config_keys),
            "allProperties": all_config_keys,
            "historicalBaseline": {
                "file": "framework-configuration-keys-1.1.json",
                "version": "1.1",
                "status": "CURRENT_1_1_CONFIG_KEYS",
            },
        }

        release_baseline_payload = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "version": release,
            "baselineName": f"Viet Template {release} Canonical Maintenance Release Baseline",
            "description": (
                f"Permanent canonical maintenance-line compatibility baseline for the Viet Template "
                f"{provenance['maintenanceLine']} line defining frozen public API/SPI surface, runtime ABI, "
                f"diagnostic codes, framework entrypoints, configuration keys, and publication topology with "
                f"complete {release} release provenance."
            ),
            "provenance": provenance,
            "publicSurface": {
                "totalCompiledPublicTypes": len(classifications),
                "stableApiTypesCount": len(stable_api_types),
                "stableSpiTypesCount": len(stable_spi_types),
                "totalStableCount": len(all_stable_types),
                "categories": category_counts,
                "signatureLeaksAllowed": 0,
                "stableApiTypes": stable_api_types,
                "stableSpiTypes": stable_spi_types,
                "stableTypes": all_stable_types,
                "classifications": {k: classifications[k] for k in sorted(classifications.keys())},
                "frontendTypes": {
                    "total": len(frontend_types),
                    "stableCount": len([t for t in frontend_types if classifications.get(t) in ("STABLE_API", "STABLE_SPI")]),
                    "types": frontend_types,
                },
                "apiBaselines": [
                    f"config/api-baseline/{release}-core-public-api.txt",
                    f"config/api-baseline/{release}-aot-public-api.txt",
                    f"config/api-baseline/{release}-spring-public-api.txt",
                    f"config/api-baseline/{release}-spring-security-public-api.txt",
                    f"config/api-baseline/{release}-quarkus-public-api.txt",
                ],
            },
            "members": {k: member_map[k] for k in sorted(member_map.keys())},
            "frameworkEntrypoints": {
                "frameworkEntrypointsCount": len(framework_entrypoint_types),
                "buildToolEntrypointsCount": category_counts.get("BUILD_TOOL_ENTRYPOINT", 18),
                "totalRegisteredEntrypoints": len(registered_entrypoints),
                "frameworkEntrypointTypes": framework_entrypoint_types,
                "registryFile": "config/architecture/framework-and-tooling-entrypoints.json",
            },
            "frameworkConfigurationKeys": {
                "totalPropertiesCount": len(all_config_keys),
                "springBootPropertiesCount": len(spring_props),
                "quarkusPropertiesCount": len(quarkus_props),
                "keys": all_config_keys,
                "baselineFile": f"config/compatibility/framework-configuration-keys-{release.rsplit('.', 1)[0]}.json",
            },
            "publicationTopology": {
                "publishedCoordinatesCount": len(published_modules),
                "groupId": "io.github.minh124199",
                "coordinates": published_modules,
                "baselineFile": "config/compatibility/publication-topology.json",
            },
            "javaBaseline": {
                "minimumJavaVersion": "21",
                "classfileMajorVersion": 65,
            },
            "frameworkSupport": {
                "springBootMinimum": "3.2.0",
                "springFrameworkMinimum": "6.1.0",
                "quarkusMinimum": "3.8.0",
                "mavenMinimum": "3.9.0",
                "gradleMinimum": "8.5",
                "baselineFile": "config/compatibility/framework-support.json",
            },
            "generatedRuntimeAbi": {
                "typesCount": 8,
                "methodsCount": 24,
                "baselineFile": "config/api-baseline/generated-template-runtime-abi.txt",
            },
            "diagnosticCodes": {
                "canonicalCodesCount": 32,
                "baselineFile": "config/api-baseline/diagnostic-codes-1.0.txt",
            },
            "patchPolicy": {
                "policyName": f"Viet Template {provenance['maintenanceLine']} Maintenance Patch Policy",
                "classifications": [
                    "PATCH_ALLOWED",
                    "PATCH_ALLOWED_SECURITY",
                    "PATCH_ALLOWED_PERFORMANCE",
                    "MINOR_RELEASE_REQUIRED",
                    "MAJOR_RELEASE_REQUIRED",
                    "HISTORICAL_ONLY",
                ],
                "rules": {
                    "stableApiAdditions": "MINOR_RELEASE_REQUIRED",
                    "stableSpiAdditions": "MINOR_RELEASE_REQUIRED",
                    "memberAdditions": "MINOR_RELEASE_REQUIRED",
                    "frameworkEntrypointAdditions": "MINOR_RELEASE_REQUIRED",
                    "configKeyAdditions": "MINOR_RELEASE_REQUIRED",
                    "publicationTopologyAdditions": "MINOR_RELEASE_REQUIRED",
                    "breakingChanges": "MAJOR_RELEASE_REQUIRED",
                    "bugFixesPreservingSignatures": "PATCH_ALLOWED",
                    "securityTightening": "PATCH_ALLOWED_SECURITY",
                    "semanticsPreservingOptimization": "PATCH_ALLOWED_PERFORMANCE",
                },
            },
        }

        generated_files: dict[Path, str] = {}
        for group_name, types in baseline_groups.items():
            out_file = output_root / f"config/api-baseline/{release}-{group_name}-public-api.txt"
            content = format_baseline_text(group_name, release, types, member_map)
            generated_files[out_file] = content

        config_keys_file = output_root / f"config/compatibility/framework-configuration-keys-{release.rsplit('.', 1)[0]}.json"
        generated_files[config_keys_file] = json.dumps(config_keys_payload, indent=2, sort_keys=False) + "\n"

        baseline_file = output_root / f"config/compatibility/{release}-release-baseline.json"
        generated_files[baseline_file] = json.dumps(release_baseline_payload, indent=2, sort_keys=False) + "\n"

        if check_only:
            mismatches = []
            for path, expected_content in generated_files.items():
                if not path.is_file():
                    mismatches.append(f"Missing file: {path}")
                else:
                    actual = path.read_text(encoding="utf-8")
                    if actual != expected_content:
                        mismatches.append(f"Content mismatch in {path}")
            if mismatches:
                raise ValueError("Check-only mode found mismatches:\n" + "\n".join(mismatches))
            print(f"[CHECK] All {len(generated_files)} baseline files match generated release baseline exactly.")
        else:
            for path, content in generated_files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
                print(f"[WROTE] {path.relative_to(output_root)} ({len(content.splitlines())} lines)")

        return release_baseline_payload

    finally:
        if cleanup_worktree and worktree_path.exists():
            subprocess.run(["git", "worktree", "remove", "--force", str(worktree_path)], cwd=str(REPO_ROOT), check=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Viet Template release maintenance baseline")
    parser.add_argument("--release", default="1.4.0", help="Release version (default: 1.4.0)")
    parser.add_argument("--tag", default="v1.4.0", help="Release tag (default: v1.4.0)")
    parser.add_argument("--worktree", type=Path, default=None, help="Path to pre-existing compiled release worktree")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help="Repository root path")
    parser.add_argument("--check-only", action="store_true", help="Only verify baseline files match without modifying")
    args = parser.parse_args()

    try:
        generate_baseline(
            release=args.release,
            tag=args.tag,
            worktree_path=args.worktree,
            output_root=args.repo_root,
            check_only=args.check_only,
        )
        return 0
    except Exception as e:
        print(f"Error generating release baseline: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
