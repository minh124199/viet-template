#!/usr/bin/env python3
"""
scripts/verify-documentation.py

Automated documentation verification infrastructure for Viet Template:
1. Release Date Integrity: Verifies that no stale release dates (e.g. 2026-09-21)
   remain in active documentation, ensuring v0.2.2 release date is 2026-09-20.
2. Consumer Installation Snippets: Validates that Maven and Gradle installation
   snippets reference the current released version (0.2.2) and reject
   unlabeled snapshot/development versions (e.g. 0.2.3-SNAPSHOT).
3. Maven Coordinates & Gradle Plugin ID: Enforces canonical group
   io.github.minh124199:viet-template-* and Gradle plugin ID io.github.minh124199.viet-template.
4. Compiler Baseline Documentation: Confirms that build tool guides and README
   document the compiler baseline as Java 21 (--release 21, bytecode major 65).
5. Spring Configuration Properties: Verifies that all documented viet-template.*
   properties exist in VietTemplateProperties.java or auto-configuration classes.
6. Internal Markdown Link Resolution: Verifies that all relative markdown links
   and heading anchors (#anchor) within docs/ and README.md resolve cleanly.
7. Public Type Classification Alignment: Validates that public types mentioned
   in documentation correspond to registered types in config/api-baseline/.
8. Diagnostic Code Consistency: Ensures diagnostic codes mentioned in documentation
   match known codes in the engine or adhere to defined diagnostic namespaces.
9. Compatibility Matrix Synchronization: Ensures docs/migration/compatibility-matrix.md
   is present and in sync with config/tck/vtl-feature-matrix.json.
10. Stale Pre-Publication Language in Living Docs: Confirms that living user-facing
    documentation does not contain outdated pre-publication wording or unreleased snapshots.
11. Velocity Compatibility Claims in Living Docs: Ensures living user-facing
    documentation does not assert unsubstantiated Velocity compatibility overclaims.
12. Benchmark Claims Consistency: Validates that README.md headline benchmark
    numbers match raw JMH evidence, rejects unevidenced allocation and throughput claims
    in living docs, rejects stale unevidenced numbers, and validates benchmark evidence SHA256SUMS integrity.
"""

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

DEFAULT_RELEASED_VERSION = "1.2.0"
STALE_RELEASE_DATES = ["2026-09-21"]
EXPECTED_RELEASE_DATE = "2026-09-20"

CANONICAL_MAVEN_GROUP = "io.github.minh124199"
CANONICAL_GRADLE_PLUGIN_ID = "io.github.minh124199.viet-template"

DIAGNOSTIC_NAMESPACES_RE = re.compile(r"^VTL(P|S|SEC|C|R|SPR|AOT)\d{2,4}$")


def strip_code_blocks(text: str) -> str:
    """Removes fenced code blocks from markdown text while preserving line count."""
    lines = text.split("\n")
    out = []
    in_fence = False
    for line in lines:
        if line.strip().startswith("```"):
            in_fence = not in_fence
            out.append("")
        elif in_fence:
            out.append("")
        else:
            out.append(line)
    return "\n".join(out)


def slugify_heading(heading: str) -> str:
    """Computes GitHub-compatible anchor slug for a markdown heading."""
    heading = re.sub(r"<[^>]+>", "", heading)
    slug = heading.strip().lower()
    slug = re.sub(r"[^\w\s-]", "", slug)
    slug = re.sub(r"\s+", "-", slug)
    return slug


# =============================================================================
# CHECK 1: Release Date Integrity
# =============================================================================

def check_release_dates(repo_root: Path) -> list[str]:
    """Verifies that no stale release dates remain in documentation."""
    errors = []
    candidates = [repo_root / "README.md", repo_root / "CHANGELOG.md"]
    candidates.extend(repo_root.glob("docs/**/*.md"))

    for file_path in candidates:
        if not file_path.is_file():
            continue
        rel_path = file_path.relative_to(repo_root)
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_no, line in enumerate(content.splitlines(), start=1):
            for stale in STALE_RELEASE_DATES:
                if stale in line:
                    errors.append(
                        f"Stale release date '{stale}' in {rel_path}:{line_no} "
                        f"(expected '{EXPECTED_RELEASE_DATE}'): {line.strip()}"
                    )
    return errors


def check_release_state_consistency(repo_root: Path) -> list[str]:
    """Keep the README and root build version aligned with publication metadata."""
    errors = []
    metadata_path = repo_root / "config" / "compatibility" / "publication-topology.json"
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        taxonomy = metadata["publicationTaxonomy"]
        stable = taxonomy["latestPublishedStableVersion"]
        snapshot = taxonomy["currentSnapshot"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        return [f"Cannot read current release metadata from {metadata_path}: {exc}"]

    readme_path = repo_root / "README.md"
    changelog_path = repo_root / "CHANGELOG.md"
    pom_path = repo_root / "pom.xml"
    gradle_path = repo_root / "build.gradle.kts"
    for path in (readme_path, changelog_path, pom_path):
        if not path.is_file():
            errors.append(f"Required release-state file is missing: {path.relative_to(repo_root)}")
    if errors:
        return errors

    readme = readme_path.read_text(encoding="utf-8")
    stable_line = next((line for line in readme.splitlines() if "Latest Published Stable Release" in line), "")
    development_line = next((line for line in readme.splitlines() if "Active Development" in line), "")
    if f"`{stable}`" not in stable_line or "published" not in stable_line.lower():
        errors.append(
            f"README latest published stable release does not match publication metadata ({stable})."
        )
    if f"`{snapshot}`" not in development_line:
        errors.append(
            f"README active development version does not match publication metadata ({snapshot})."
        )

    changelog = changelog_path.read_text(encoding="utf-8")
    if not re.search(rf"^## \[{re.escape(stable)}\] - \d{{4}}-\d{{2}}-\d{{2}}$", changelog, re.MULTILINE):
        errors.append(f"CHANGELOG.md has no dated release heading for current stable version {stable}.")

    pom = pom_path.read_text(encoding="utf-8")
    if not re.search(
        rf"<artifactId>viet-template-parent</artifactId>\s*<version>{re.escape(snapshot)}</version>",
        pom,
    ):
        errors.append(f"Root pom.xml version does not match current development snapshot {snapshot}.")
    if gradle_path.is_file():
        gradle = gradle_path.read_text(encoding="utf-8")
        if not re.search(rf'\bversion\s*=\s*"{re.escape(snapshot)}"', gradle):
            errors.append(
                f"Root build.gradle.kts version does not match current development snapshot {snapshot}."
            )
    if snapshot.endswith("-SNAPSHOT") and taxonomy.get("currentSnapshotPublished") is not False:
        errors.append("Publication metadata must mark the active SNAPSHOT as unpublished.")
    return errors


# =============================================================================
# CHECK 2: Consumer Installation Snippets
# =============================================================================

def check_consumer_snippets_in_text(
    text: str,
    filename: str = "<text>",
    released_version: str | set[str] | tuple[str, ...] | list[str] | None = None,
) -> list[str]:
    """Checks that consumer installation snippets reference the released version."""
    errors = []
    if released_version is None:
        allowed_versions = {DEFAULT_RELEASED_VERSION}
    elif isinstance(released_version, str):
        allowed_versions = {released_version}
    else:
        allowed_versions = set(released_version)

    # 1. Maven snippets: <groupId>io.github.minh124199</groupId> ... <artifactId>viet-template-*</artifactId> <version>...</version>
    mvn_dep_pattern = re.compile(
        r"<dependency>\s*"
        r"<groupId>([^<]+)</groupId>\s*"
        r"<artifactId>([^<]+)</artifactId>\s*"
        r"<version>([^<]+)</version>",
        re.DOTALL,
    )
    for m in mvn_dep_pattern.finditer(text):
        group, artifact, version = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
        if group == CANONICAL_MAVEN_GROUP and artifact.startswith("viet-template-"):
            if "SNAPSHOT" in version:
                before_text = text[max(0, m.start() - 200):m.start()].lower()
                after_text = text[m.end():min(len(text), m.end() + 200)].lower()
                surrounding = before_text + " " + after_text
                is_labeled = any(
                    kw in surrounding
                    for kw in ("snapshot", "development", "dev build", "nightly", "main branch", "pre-release", "prerelease")
                )
                if not is_labeled:
                    errors.append(
                        f"Consumer installation snippet in {filename} references snapshot "
                        f"version '{version}' for {artifact} without explicit snapshot/development label."
                    )
            elif version not in allowed_versions:
                exp_desc = f"'{next(iter(allowed_versions))}'" if len(allowed_versions) == 1 else f"one of {sorted(allowed_versions)}"
                errors.append(
                    f"Consumer installation snippet in {filename} references version "
                    f"'{version}' for {artifact} (expected released version {exp_desc})."
                )

    # 2. Gradle snippets: implementation("io.github.minh124199:viet-template-*:version")
    gradle_pattern = re.compile(
        r"""implementation\s*\(?\s*["']([^:"']+):([^:"']+):([^"']+)["']\s*\)?"""
    )
    for m in gradle_pattern.finditer(text):
        group, artifact, version = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
        if group == CANONICAL_MAVEN_GROUP and artifact.startswith("viet-template-"):
            if "SNAPSHOT" in version:
                before_text = text[max(0, m.start() - 200):m.start()].lower()
                after_text = text[m.end():min(len(text), m.end() + 200)].lower()
                surrounding = before_text + " " + after_text
                is_labeled = any(
                    kw in surrounding
                    for kw in ("snapshot", "development", "dev build", "nightly", "main branch", "pre-release", "prerelease")
                )
                if not is_labeled:
                    errors.append(
                        f"Consumer installation snippet in {filename} references snapshot "
                        f"version '{version}' for {artifact} without explicit snapshot/development label."
                    )
            elif version not in allowed_versions:
                exp_desc = f"'{next(iter(allowed_versions))}'" if len(allowed_versions) == 1 else f"one of {sorted(allowed_versions)}"
                errors.append(
                    f"Consumer installation snippet in {filename} references version "
                    f"'{version}' for {artifact} (expected released version {exp_desc})."
                )

    return errors


def check_all_consumer_snippets(
    repo_root: Path,
    released_version: str | set[str] | tuple[str, ...] | list[str] | None = None,
) -> list[str]:
    """Verifies consumer installation snippets in README.md and active docs."""
    errors = []
    if released_version is None:
        metadata_path = repo_root / "config" / "compatibility" / "publication-topology.json"
        try:
            released_version = json.loads(metadata_path.read_text(encoding="utf-8"))[
                "publicationTaxonomy"
            ]["latestPublishedStableVersion"]
        except (OSError, json.JSONDecodeError, KeyError, TypeError):
            released_version = DEFAULT_RELEASED_VERSION
    # Check README.md (primary consumer installation guide)
    readme_path = repo_root / "README.md"
    if readme_path.is_file():
        errors.extend(
            check_consumer_snippets_in_text(
                readme_path.read_text(encoding="utf-8"),
                "README.md",
                released_version,
            )
        )

    # Check any consumer installation guides under docs/ (skip architectural specs & milestone reports)
    for doc in repo_root.glob("docs/**/*.md"):
        rel_str = str(doc.relative_to(repo_root))
        if rel_str.startswith("docs/releases/"):
            continue
        try:
            content = doc.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if "## Installation" in content:
            errors.extend(check_consumer_snippets_in_text(content, rel_str, released_version))

    return errors


# =============================================================================
# CHECK 3: Maven Coordinates & Gradle Plugin ID
# =============================================================================

def check_coordinates_in_text(text: str, filename: str = "<text>") -> list[str]:
    """Validates that documentation uses canonical coordinates and plugin ID."""
    errors = []

    # Detect erroneous Maven groups
    bad_groups = ["com.github.minh124199", "org.viettemplate", "io.github.viet-template"]
    for bg in bad_groups:
        if bg in text:
            errors.append(f"Invalid Maven group '{bg}' found in {filename}.")

    # Validate Gradle plugin ID references:
    # 1. Plugin block syntax: id("...") or id '...'
    plugin_block_re = re.compile(r"""(?:id|plugin)\s*\(?\s*["']([^"']+)["']\s*\)?""")
    for m in plugin_block_re.finditer(text):
        candidate = m.group(1).strip()
        if "viet-template" in candidate and candidate != CANONICAL_GRADLE_PLUGIN_ID:
            # If it looks like a plugin declaration for this project
            if candidate.startswith("io.github.minh124199") or candidate.startswith("com.github.minh124199"):
                errors.append(
                    f"Invalid Gradle plugin ID '{candidate}' in {filename} "
                    f"(expected '{CANONICAL_GRADLE_PLUGIN_ID}')."
                )

    # 2. Textual mentions: "plugin id `...`" or "Plugin ID `...`"
    plugin_mention_re = re.compile(r"""[Pp]lugin\s+[Ii][Dd]\s+[`'"]([^`'"]+)[`'"]""")
    for m in plugin_mention_re.finditer(text):
        candidate = m.group(1).strip()
        if "viet-template" in candidate and candidate != CANONICAL_GRADLE_PLUGIN_ID:
            errors.append(
                f"Invalid Gradle plugin ID '{candidate}' in {filename} "
                f"(expected '{CANONICAL_GRADLE_PLUGIN_ID}')."
            )

    return errors


def check_all_coordinates(repo_root: Path) -> list[str]:
    """Verifies canonical Maven coordinates and Gradle plugin ID across docs."""
    errors = []
    candidates = [repo_root / "README.md"] + list(repo_root.glob("docs/**/*.md"))
    for file_path in candidates:
        if not file_path.is_file():
            continue
        rel_str = str(file_path.relative_to(repo_root))
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        errors.extend(check_coordinates_in_text(content, rel_str))
    return errors


# =============================================================================
# CHECK 4: Compiler Baseline Documentation
# =============================================================================

def check_compiler_baseline_documented(repo_root: Path) -> list[str]:
    """Ensures compiler baseline Java 21 is documented in build tool guides and README."""
    errors = []
    readme = repo_root / "README.md"
    if not readme.is_file():
        return ["README.md not found to verify compiler baseline."]

    content = readme.read_text(encoding="utf-8")
    has_java21 = "Java 21" in content
    has_release_or_classfile = any(
        kw in content
        for kw in ("--release 21", "major version 65", "classfile major 65", "classfile 65")
    )
    if not (has_java21 and has_release_or_classfile):
        errors.append(
            "README.md does not properly document compiler baseline Java 21 "
            "(--release 21, bytecode major version 65)."
        )

    # Check Build-Time Template AOT section in README.md
    if "Build-Time Template AOT" in content and "Java 21" not in content:
        errors.append(
            "README.md Build-Time Template AOT section does not specify Java 21 compiler baseline."
        )

    return errors


# =============================================================================
# CHECK 5: Spring Configuration Properties
# =============================================================================

def get_known_spring_properties(repo_root: Path) -> set[str]:
    """Extracts known Spring configuration properties from Java source definitions."""
    known = set()

    # 1. Parse VietTemplateProperties.java
    props_java = (
        repo_root
        / "viet-template-spring-boot-autoconfigure"
        / "src/main/java/io/github/minh124199/viettemplate/spring/boot/autoconfigure/VietTemplateProperties.java"
    )
    if props_java.is_file():
        content = props_java.read_text(encoding="utf-8")
        prefix = "viet-template"
        field_re = re.compile(r"private\s+(?:boolean|int|long|String|Charset|List(?:<String>)?)\s+([a-zA-Z0-9_]+)\b")
        for m in field_re.finditer(content):
            camel = m.group(1)
            kebab = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", camel).lower()
            known.add(f"{prefix}.{kebab}")

    # 2. Parse @ConditionalOnProperty definitions in autoconfigure module
    auto_dir = (
        repo_root
        / "viet-template-spring-boot-autoconfigure"
        / "src/main/java"
    )
    if auto_dir.is_dir():
        for jf in auto_dir.glob("**/*.java"):
            text = jf.read_text(encoding="utf-8")
            for m in re.finditer(r'@ConditionalOnProperty\([^)]*name\s*=\s*"([^"]+)"', text):
                known.add(m.group(1))

    return known


def check_spring_properties_in_text(
    text: str,
    known_properties: set[str],
    filename: str = "<text>",
) -> list[str]:
    """Validates that documented Spring properties exist in the codebase."""
    errors = []

    # 1. Properties table rows: | `viet-template.<name>` |
    table_prop_re = re.compile(r"\|\s*`(viet-template\.[a-z0-9-]+)`\s*\|")
    for m in table_prop_re.finditer(text):
        prop = m.group(1)
        if prop not in known_properties:
            errors.append(f"Unknown Spring property '{prop}' documented in table in {filename}.")

    # 2. Properties file blocks: viet-template.<name>=<val>
    prop_assign_re = re.compile(r"^([a-z0-9.-]+)\s*=", re.MULTILINE)
    for m in prop_assign_re.finditer(text):
        prop = m.group(1)
        if prop.startswith("viet-template.") and not prop.endswith(".git"):
            if prop not in known_properties:
                errors.append(
                    f"Unknown Spring property assignment '{prop}' documented in {filename}."
                )

    # 3. Inline backtick mentions: `viet-template.<name>=<val>` or `viet-template.<name>`
    # Skip non-property identifiers like .git or servlet request attribute keys
    inline_re = re.compile(r"`(viet-template\.[a-z0-9-]+)(?:=[^`]+)?`")
    for m in inline_re.finditer(text):
        prop = m.group(1)
        if prop.endswith(".git") or prop.startswith("viet-template.spring."):
            continue
        if prop in ("viet-template.security", "viet-template.prefix", "viet-template.suffix"):
            if prop == "viet-template.security":
                continue  # Fragment of viet-template.security.enabled
        if prop not in known_properties:
            errors.append(f"Unknown Spring property '{prop}' referenced in {filename}.")

    return errors


def check_all_spring_properties(repo_root: Path) -> list[str]:
    """Verifies all documented Spring properties across documentation."""
    known_properties = get_known_spring_properties(repo_root)
    if not known_properties:
        return ["Failed to discover known Spring properties from VietTemplateProperties.java."]

    errors = []
    candidates = [repo_root / "README.md"] + list(repo_root.glob("docs/**/*.md"))
    for file_path in candidates:
        if not file_path.is_file():
            continue
        rel_str = str(file_path.relative_to(repo_root))
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        errors.extend(check_spring_properties_in_text(content, known_properties, rel_str))
    return errors


# =============================================================================
# CHECK 6: Internal Markdown Link Resolution
# =============================================================================

def resolve_markdown_link_target(
    target: str,
    doc_file: Path,
    repo_root: Path,
) -> tuple[bool, Path | None, str | None, str]:
    """
    Resolves an internal markdown link target to a concrete markdown file and optional anchor.
    Returns (ok, resolved_file_or_None, anchor_or_None, failure_reason).
    Only validates internal markdown links (ending in .md or same-file #anchor).
    """
    if target.startswith(("http://", "https://", "mailto:")):
        return True, None, None, ""

    cleaned = target.strip()
    if cleaned.startswith("file://"):
        cleaned = cleaned[7:]

    target_path = cleaned.split("#")[0]
    anchor = cleaned.split("#")[1] if "#" in cleaned else None

    # Only process internal markdown links or anchor links
    is_md_link = target_path.endswith(".md") or (not target_path and anchor)
    if not is_md_link:
        return True, None, None, ""

    # Same-file anchor link: [text](#anchor)
    if not target_path:
        return True, doc_file, anchor, ""

    # Candidates for target file resolution
    candidates = [
        (doc_file.parent / target_path).resolve(),
        (repo_root / target_path).resolve(),
        Path(target_path).resolve(),
    ]

    # Handle absolute paths pointing into checkout
    if "/viet-template-repo/" in target_path:
        rel = target_path.split("/viet-template-repo/", 1)[1]
        candidates.append((repo_root / rel).resolve())

    # Handle module paths: /(viet-template-[a-z0-9-]+|docs)/(.*)$
    m_mod = re.search(r"/(viet-template-[a-z0-9-]+|docs)/(.*)$", target_path)
    if m_mod:
        candidates.append((repo_root / m_mod.group(1) / m_mod.group(2)).resolve())

    for c in candidates:
        if c.exists():
            return True, c, anchor, ""

    return False, None, anchor, f"Target file '{target_path}' does not exist"


def check_links_in_content(
    content: str,
    doc_file: Path,
    repo_root: Path,
) -> list[str]:
    """Validates markdown links and anchors within content."""
    errors = []
    link_pattern = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
    heading_cache: dict[Path, set[str]] = {}

    for m in link_pattern.finditer(content):
        label, target = m.group(1), m.group(2).strip()
        ok, resolved, anchor, reason = resolve_markdown_link_target(target, doc_file, repo_root)
        if not ok:
            errors.append(f"Broken link in {doc_file.relative_to(repo_root)}: '{target}' ({reason})")
            continue

        if anchor and resolved is not None and resolved.suffix == ".md":
            # Check line number anchor e.g. #L10-L20
            if re.match(r"^L\d+(-L\d+)?$", anchor):
                continue

            if resolved not in heading_cache:
                try:
                    res_content = resolved.read_text(encoding="utf-8")
                    headings = re.findall(r"^#{1,6}\s+(.+)$", res_content, flags=re.MULTILINE)
                    slugs = {slugify_heading(h) for h in headings}
                    # Also support HTML anchors
                    for anchor_tag in re.findall(r'<(?:a|div)\s+(?:name|id)=["\']([^"\']+)["\']', res_content):
                        slugs.add(anchor_tag.lower())
                    heading_cache[resolved] = slugs
                except Exception:
                    heading_cache[resolved] = set()

            known_slugs = heading_cache[resolved]
            if known_slugs and anchor.lower() not in known_slugs:
                errors.append(
                    f"Broken anchor '#{anchor}' in {doc_file.relative_to(repo_root)} -> "
                    f"{resolved.relative_to(repo_root)}"
                )

    return errors


def check_all_markdown_links(repo_root: Path) -> list[str]:
    """Verifies all internal markdown links and anchors in README.md and docs/."""
    errors = []
    candidates = [repo_root / "README.md"] + list(repo_root.glob("docs/**/*.md"))

    for file_path in candidates:
        if not file_path.is_file():
            continue
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        errors.extend(check_links_in_content(content, file_path, repo_root))
    return errors


# =============================================================================
# CHECK 7: Public Type Classification Alignment
# =============================================================================

def get_known_public_types(repo_root: Path) -> dict[str, str]:
    """Loads all known public types and classifications from config/api-baseline/."""
    known: dict[str, str] = {}

    class_file = repo_root / "config/api-baseline/public-surface-classification.txt"
    if class_file.is_file():
        for line in class_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) >= 2:
                known[parts[0]] = parts[1]

    # Include types from baseline .txt files
    for baseline in (repo_root / "config/api-baseline").glob("*.txt"):
        if baseline.name == "public-surface-classification.txt":
            continue
        for line in baseline.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("TYPE"):
                parts = line.split()
                fqcn = parts[-1]
                if fqcn not in known:
                    known[fqcn] = "STABLE_API"

    return known


def check_public_types_in_text(
    text: str,
    known_types: dict[str, str],
    filename: str = "<text>",
) -> list[str]:
    """Verifies that public type mentions in text correspond to registered types."""
    errors = []

    # 1. FQCN mentions for production packages (ignoring .benchmarks., .tck., and .generated.)
    fqcn_pattern = re.compile(
        r"\b(io\.github\.minh124199\.viettemplate\.(?!benchmarks\.|tck\.|generated\.)[A-Za-z0-9_.]+)\b"
    )
    for m in fqcn_pattern.finditer(text):
        fqcn = m.group(1).rstrip(".")
        last_seg = fqcn.split(".")[-1]
        if not last_seg or not last_seg[0].isupper():
            continue  # Package name or method call
        if fqcn.endswith(".java") or "(" in fqcn:
            continue
        base_class = fqcn.split("$")[0]
        if fqcn not in known_types and base_class not in known_types:
            errors.append(f"Unknown public type '{fqcn}' referenced in {filename}.")

    # 2. Public API/SPI classification tables: | `FQCN` | `CATEGORY` |
    table_re = re.compile(
        r"\|\s*`([a-zA-Z0-9_.]+)`\s*\|\s*`(STABLE_API|STABLE_SPI|EXPERIMENTAL|PUBLIC_BUT_INTERNAL_ACCIDENT)`\s*\|"
    )
    for m in table_re.finditer(text):
        fqcn, documented_cat = m.group(1), m.group(2)
        if fqcn in known_types:
            actual_cat = known_types[fqcn]
            if actual_cat != documented_cat:
                errors.append(
                    f"Type classification mismatch in {filename} for {fqcn}: "
                    f"documented '{documented_cat}', actual in baseline '{actual_cat}'."
                )

    return errors


def check_all_public_types(repo_root: Path) -> list[str]:
    """Verifies public type mentions across docs against config/api-baseline/."""
    known_types = get_known_public_types(repo_root)
    if not known_types:
        return ["No public surface types loaded from config/api-baseline/."]

    errors = []
    candidates = [repo_root / "README.md"] + list(repo_root.glob("docs/**/*.md"))
    for file_path in candidates:
        if not file_path.is_file():
            continue
        rel_str = str(file_path.relative_to(repo_root))
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        errors.extend(check_public_types_in_text(content, known_types, rel_str))
    return errors


# =============================================================================
# CHECK 8: Diagnostic Code Consistency
# =============================================================================

def get_known_diagnostic_codes(repo_root: Path) -> set[str]:
    """Collects known diagnostic codes from Java code definitions."""
    known = set()

    # Discover DiagnosticCode.of("CATEGORY", "ID") across Java sources
    diag_pattern = re.compile(r'DiagnosticCode\.of\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')
    for jf in repo_root.glob("**/src/main/java/**/*.java"):
        try:
            text = jf.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for m in diag_pattern.finditer(text):
            cat, code_id = m.group(1), m.group(2)
            known.add(f"{cat}:{code_id}")
            known.add(f"{cat}{code_id}")

    return known


def check_diagnostic_codes_in_text(
    text: str,
    known_codes: set[str],
    filename: str = "<text>",
) -> list[str]:
    """Verifies that diagnostic codes mentioned match known codes or valid namespaces."""
    errors = []

    # 1. Match codes like VTL[A-Z]{1,4}\d{2,4} (e.g. VTLS2101, VTLS2104, VTLSEC2401, VTLP1003)
    code_pattern = re.compile(r"\b(VTL[A-Z]{1,4}\d{2,4})\b")
    for m in code_pattern.finditer(text):
        code = m.group(1)
        if code in known_codes or DIAGNOSTIC_NAMESPACES_RE.match(code):
            continue
        errors.append(f"Unknown diagnostic code '{code}' mentioned in {filename}.")

    # 2. Match explicit qualified diagnostic codes: `CATEGORY:ID`
    qual_pattern = re.compile(r"`([A-Z]{3,}:[A-Z0-9_]+)`")
    for m in qual_pattern.finditer(text):
        code = m.group(1)
        # Filter out false positives like XML namespaces or schema attributes
        if code.startswith(("HTTP:", "HTTPS:", "SCM:", "SHA:", "JDK:")):
            continue
        if code not in known_codes:
            cat = code.split(":")[0]
            if cat not in ("SECURITY", "SYNTAX", "RESOURCE", "LIMIT", "LAYOUT", "INTERPRETER", "COMPILER", "CONTEXT", "VTLS", "VTLSEC", "VTLAOT"):
                continue
            errors.append(f"Unknown qualified diagnostic code '{code}' mentioned in {filename}.")

    return errors


def check_all_diagnostic_codes(repo_root: Path) -> list[str]:
    """Verifies all diagnostic code mentions across documentation."""
    known_codes = get_known_diagnostic_codes(repo_root)
    errors = []
    candidates = [repo_root / "README.md"] + list(repo_root.glob("docs/**/*.md"))

    for file_path in candidates:
        if not file_path.is_file():
            continue
        rel_str = str(file_path.relative_to(repo_root))
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        errors.extend(check_diagnostic_codes_in_text(content, known_codes, rel_str))
    return errors


# =============================================================================
# CHECK 9: Compatibility Matrix Synchronization
# =============================================================================

def check_compatibility_matrix_synced(repo_root: Path) -> list[str]:
    """Verifies that docs/migration/compatibility-matrix.md is synchronized with config/tck/vtl-feature-matrix.json."""
    errors = []
    matrix_file = repo_root / "config" / "tck" / "vtl-feature-matrix.json"
    doc_file = repo_root / "docs" / "migration" / "compatibility-matrix.md"

    if not matrix_file.is_file():
        errors.append(f"Feature matrix file not found: {matrix_file}")
        return errors

    if not doc_file.is_file():
        errors.append(
            f"Compatibility matrix documentation missing: {doc_file}. "
            "Run 'python3 scripts/generate-compatibility-matrix.py' to generate it."
        )
        return errors

    try:
        gen_script = repo_root / "scripts" / "generate-compatibility-matrix.py"
        if not gen_script.is_file():
            gen_script = Path(__file__).resolve().parent / "generate-compatibility-matrix.py"

        import importlib.util
        spec = importlib.util.spec_from_file_location("generate_compatibility_matrix_mod", gen_script)
        if spec and spec.loader:
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            expected = mod.generate_compatibility_matrix(matrix_file)
            actual = doc_file.read_text(encoding="utf-8")
            if actual != expected:
                errors.append(
                    f"Compatibility matrix '{doc_file}' is out of sync with '{matrix_file}'. "
                    "Run 'python3 scripts/generate-compatibility-matrix.py' to synchronize."
                )
        else:
            errors.append(f"Could not load generator script: {gen_script}")
    except Exception as e:
        errors.append(f"Failed to verify compatibility matrix synchronization: {e}")

    return errors


# =============================================================================
# LIVING DOCUMENT DEFINITION & HELPERS
# =============================================================================

def get_living_doc_files(repo_root: Path) -> list[Path]:
    """Returns a sorted list of unique living documentation files."""
    candidates: list[Path] = [
        repo_root / "README.md",
        repo_root / "SECURITY.md",
        repo_root / "docs" / "36-spring-security-integration.md",
        repo_root / "docs" / "extensions" / "quarkus.md",
    ]
    living_globs = [
        "docs/getting-started/**/*.md",
        "docs/security/**/*.md",
        "docs/spring/**/*.md",
        "docs/language/**/*.md",
        "docs/build-tooling/**/*.md",
        "docs/deployment/**/*.md",
        "docs/native-image/**/*.md",
        "docs/migration/**/*.md",
        "docs/diagnostics/**/*.md",
        "docs/performance/**/*.md",
    ]
    for pattern in living_globs:
        candidates.extend(repo_root.glob(pattern))

    seen: set[Path] = set()
    result: list[Path] = []
    for file_path in sorted(candidates):
        if not file_path.is_file() or file_path in seen:
            continue
        seen.add(file_path)
        result.append(file_path)
    return result


# =============================================================================
# CHECK 10: Stale Pre-Publication Language in Living Docs
# =============================================================================

STALE_PRE_PUBLICATION_PATTERNS = [
    "not yet remotely published",
    "remote publication pending",
    "locally staged and qualified; not yet",
    "0.2.1-SNAPSHOT",
]


def check_stale_release_language_in_living_docs(repo_root: Path) -> list[str]:
    """Confirms living docs do not contain outdated pre-publication wording or unreleased snapshots."""
    errors = []
    for file_path in get_living_doc_files(repo_root):
        rel_path = file_path.relative_to(repo_root)
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_no, line in enumerate(content.splitlines(), start=1):
            for phrase in STALE_PRE_PUBLICATION_PATTERNS:
                if phrase in line:
                    errors.append(
                        f"Stale pre-publication language '{phrase}' in {rel_path}:{line_no}: {line.strip()}"
                    )
    return errors


# =============================================================================
# CHECK 11: Velocity Compatibility Overclaims in Living Docs
# =============================================================================

VELOCITY_COMPATIBILITY_OVERCLAIM_PATTERNS = [
    re.compile(r"byte-for-byte\s+(?:behavioral\s+)?compatibility\s+with\s+Apache\s+Velocity", re.IGNORECASE),
    re.compile(r"100%\s+Velocity[- ]compatible", re.IGNORECASE),
    re.compile(r"100%\s+Velocity\s+Syntax\s+Compatibility", re.IGNORECASE),
    re.compile(r"perfect\s+compatibility\s+with\s+Apache\s+Velocity", re.IGNORECASE),
    re.compile(r"comprehensive,?\s*byte-for-byte\s+behavioral\s+compatibility\s+with\s+Apache\s+Velocity", re.IGNORECASE),
]


def check_velocity_compatibility_overclaims_in_living_docs(repo_root: Path) -> list[str]:
    """Confirms living docs do not contain unsubstantiated Velocity compatibility overclaims."""
    errors = []
    for file_path in get_living_doc_files(repo_root):
        rel_path = file_path.relative_to(repo_root)
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_no, line in enumerate(content.splitlines(), start=1):
            for pattern in VELOCITY_COMPATIBILITY_OVERCLAIM_PATTERNS:
                if pattern.search(line):
                    errors.append(
                        f"Velocity compatibility overclaim in {rel_path}:{line_no}: {line.strip()}"
                    )
                    break
    return errors


def check_developer_specific_file_links(repo_root: Path) -> list[str]:
    """Reject absolute local file links that only resolve on one developer's machine."""
    errors = []
    pattern = re.compile(r"(?:file:///(?:home|Users)/|file:///[A-Za-z]:/Users/)", re.IGNORECASE)
    candidates = [repo_root / "README.md", *repo_root.glob("docs/**/*.md")]
    for file_path in candidates:
        if not file_path.is_file():
            continue
        try:
            lines = file_path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for line_no, line in enumerate(lines, start=1):
            if pattern.search(line):
                errors.append(
                    f"Developer-specific absolute file link in {file_path.relative_to(repo_root)}:{line_no}: {line.strip()}"
                )
    return errors


# =============================================================================
# CHECK 12: Benchmark Claims Consistency
# =============================================================================

UNSUPPORTED_BENCHMARK_ALLOCATION_PATTERNS = [
    re.compile(r"<\s*8\s*B/op", re.IGNORECASE),
    re.compile(r"40\s*[-–]\s*80\s*B/op", re.IGNORECASE),
]

UNSUPPORTED_BENCHMARK_THROUGHPUT_PATTERNS = [
    re.compile(r"\bup\s+to\s+8x\b", re.IGNORECASE),
    re.compile(r"\b8x\b[^\n]*\bVelocity\b", re.IGNORECASE),
]

STALE_UNSUPPORTED_BENCHMARK_NUMBERS = [
    re.compile(r"\b3\.72M\b"),
    re.compile(r"\b1\.02M\b"),
    re.compile(r"\b252K\b"),
]

OVERSTATED_BENCHMARK_REGRESSION_PATTERNS = [
    re.compile(r"\bzero\s+(?:code|performance)\s+regressions?\b", re.IGNORECASE),
    re.compile(r"\bmatches\s+or\s+exceeds\b[^\n]*\b(?:all\s+eight|all\s+8|all\s+workloads)\b", re.IGNORECASE),
    re.compile(r"\bno\s+(?:code\s+|performance\s+)?regressions?\s+across\s+all\b", re.IGNORECASE),
]

README_BENCHMARK_WORKLOAD_MAP = {
    "C01 static HTML": "c01_staticHtml",
    "C03 deep property chains": "c03_deepPropertyChains",
    "C04 conditionals": "c04_conditionals",
    "C06 large table (100 rows)": "c06_largeTableForeach",
    "C08 HTML escaping": "c08_htmlEscaping",
}


def check_same_runtime_has_negative_deltas(repo_root: Path) -> bool:
    """Returns True if benchmark evidence records negative deltas for same-runtime comparison."""
    rerun_comparison = repo_root / "benchmark-evidence" / "1.0.0-openjdk25-rerun" / "baseline-comparison-J25-G1.txt"
    if rerun_comparison.is_file():
        try:
            text = rerun_comparison.read_text(encoding="utf-8")
            if re.search(r"\s+-\d+(?:\.\d+)?%", text):
                return True
            return False
        except Exception:
            pass

    qual_path = repo_root / "benchmark-evidence" / "1.1.0" / "qualification.json"
    if qual_path.is_file():
        try:
            qual = json.loads(qual_path.read_text(encoding="utf-8"))
            conclusion = qual.get("baseline", {}).get("j25SameRuntimeConclusion", "")
            if "negative" in conclusion.lower():
                return True
        except Exception:
            pass

    # Default to True when evidence is not explicitly non-negative
    return True


def check_benchmark_claims_consistency(repo_root: Path) -> list[str]:
    """Validates benchmark claims against raw JMH evidence and rejects unevidenced claims."""
    errors = []
    has_negative_deltas = check_same_runtime_has_negative_deltas(repo_root)

    # 1. Reject unevidenced claims, stale numbers, and overstated regression claims in living documentation
    for file_path in get_living_doc_files(repo_root):
        rel_path = file_path.relative_to(repo_root)
        try:
            content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_no, line in enumerate(content.splitlines(), start=1):
            for pat in UNSUPPORTED_BENCHMARK_ALLOCATION_PATTERNS:
                if pat.search(line):
                    errors.append(
                        f"Unevidenced allocation claim in {rel_path}:{line_no}: {line.strip()}"
                    )
            for pat in UNSUPPORTED_BENCHMARK_THROUGHPUT_PATTERNS:
                if pat.search(line):
                    errors.append(
                        f"Unevidenced throughput claim in {rel_path}:{line_no}: {line.strip()}"
                    )
            for pat in STALE_UNSUPPORTED_BENCHMARK_NUMBERS:
                if pat.search(line):
                    errors.append(
                        f"Stale unevidenced benchmark number in {rel_path}:{line_no}: {line.strip()}"
                    )
            if has_negative_deltas:
                for pat in OVERSTATED_BENCHMARK_REGRESSION_PATTERNS:
                    if pat.search(line):
                        errors.append(
                            f"Overstated benchmark regression claim in {rel_path}:{line_no}: {line.strip()}"
                        )

    # 2. Validate README headline benchmark numbers against raw JMH JSON
    readme_path = repo_root / "README.md"
    evidence_110_dir = repo_root / "benchmark-evidence" / "1.1.0"
    if readme_path.is_file() and evidence_110_dir.is_dir():
        readme_text = readme_path.read_text(encoding="utf-8")
        target_profiles = [
            ("J21-G1", "### OpenJDK 21 (J21-G1) Highlights"),
            ("J25-G1", "### OpenJDK 25 (J25-G1) Highlights"),
        ]
        target_engines = ["Viet-AOT", "Velocity", "Qute", "jte"]

        for profile_id, section_header in target_profiles:
            json_path = evidence_110_dir / f"comparative-{profile_id}.json"
            if not json_path.is_file():
                errors.append(f"Missing benchmark evidence file: {json_path.relative_to(repo_root)}")
                continue

            try:
                with open(json_path, "r", encoding="utf-8") as f:
                    raw_jmh = json.load(f)
            except Exception as e:
                errors.append(f"Cannot parse JMH JSON {json_path.relative_to(repo_root)}: {e}")
                continue

            raw_scores: dict[tuple[str, str], float] = {}
            for item in raw_jmh:
                bname = item.get("benchmark", "").split(".")[-1]
                engine = item.get("params", {}).get("engine", "")
                score = item.get("primaryMetric", {}).get("score", 0.0)
                raw_scores[(bname, engine)] = score

            if section_header not in readme_text:
                errors.append(f"Missing section '{section_header}' in README.md")
                continue

            section_part = readme_text.split(section_header, 1)[1]
            table_lines = []
            for line in section_part.splitlines():
                stripped = line.strip()
                if stripped.startswith("### ") or (stripped.startswith("## ") and not stripped.startswith(section_header)):
                    break
                if stripped.startswith("|"):
                    table_lines.append(stripped)

            parsed_rows: dict[str, dict[str, str]] = {}
            header_engines: list[str] = []
            for line in table_lines:
                cells = [c.strip() for c in line.split("|")[1:-1]]
                if not cells or cells[0].startswith("---"):
                    continue
                if cells[0] == "Workload":
                    header_engines = cells[1:]
                elif cells[0] in README_BENCHMARK_WORKLOAD_MAP:
                    parsed_rows[cells[0]] = dict(zip(header_engines, cells[1:]))

            for wl_label, jmh_name in README_BENCHMARK_WORKLOAD_MAP.items():
                if wl_label not in parsed_rows:
                    errors.append(f"Missing row '{wl_label}' under '{section_header}' in README.md")
                    continue
                row_data = parsed_rows[wl_label]
                for engine in target_engines:
                    claim = row_data.get(engine, "")
                    m = re.match(r"^([\d.]+)\s*([MK])\s*ops/s$", claim)
                    if not m:
                        errors.append(
                            f"Malformed benchmark cell '{claim}' for {wl_label} / {engine} under '{section_header}' in README.md"
                        )
                        continue
                    val = float(m.group(1)) * (1_000_000 if m.group(2) == "M" else 1_000)
                    raw_score = raw_scores.get((jmh_name, engine))
                    if raw_score is None:
                        errors.append(
                            f"No raw JMH benchmark score found for {jmh_name} / {engine} in {json_path.relative_to(repo_root)}"
                        )
                    else:
                        rel_diff = abs(val - raw_score) / raw_score
                        if rel_diff > 0.01:
                            errors.append(
                                f"README benchmark claim mismatch under '{section_header}': "
                                f"{wl_label} {engine} claim '{claim}' ({val:,.0f} ops/s) differs from "
                                f"raw score {raw_score:,.1f} ops/s (delta {rel_diff * 100:.2f}%)"
                            )

    # 3. Validate benchmark-evidence/1.0.0-openjdk25-rerun/ SHA256SUMS integrity
    rerun_dir = repo_root / "benchmark-evidence" / "1.0.0-openjdk25-rerun"
    if rerun_dir.is_dir():
        checksums_path = rerun_dir / "SHA256SUMS"
        if not checksums_path.is_file():
            errors.append(f"Missing SHA256SUMS in {rerun_dir.relative_to(repo_root)}")
        else:
            try:
                lines = checksums_path.read_text(encoding="utf-8").splitlines()
                checked_files = 0
                for line_no, line in enumerate(lines, start=1):
                    line = line.strip()
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split(None, 1)
                    if len(parts) != 2:
                        errors.append(
                            f"Malformed SHA256SUMS entry in {checksums_path.relative_to(repo_root)}:{line_no}: {line}"
                        )
                        continue
                    expected_sha, file_name = parts[0], parts[1].strip()
                    file_path = rerun_dir / file_name
                    if not file_path.is_file():
                        errors.append(
                            f"Missing evidence file listed in {checksums_path.relative_to(repo_root)}: {file_name}"
                        )
                        continue
                    actual_sha = hashlib.sha256(file_path.read_bytes()).hexdigest()
                    if actual_sha != expected_sha:
                        errors.append(
                            f"Checksum mismatch in {checksums_path.relative_to(repo_root)} for {file_name}: "
                            f"expected {expected_sha}, got {actual_sha}"
                        )
                    checked_files += 1
                if checked_files == 0:
                    errors.append(f"No checksum entries in {checksums_path.relative_to(repo_root)}")
            except Exception as exc:
                errors.append(f"Could not read {checksums_path.relative_to(repo_root)}: {exc}")
    elif (repo_root / "benchmark-evidence").is_dir():
        errors.append("Missing required directory benchmark-evidence/1.0.0-openjdk25-rerun")

    return errors


# =============================================================================
# TOP-LEVEL VERIFICATION ORCHESTRATION
# =============================================================================

def verify_all(repo_root: Path, verbose: bool = False) -> list[str]:
    """Runs all documentation verification checks and aggregates any errors."""
    all_errors: list[str] = []

    checks = [
        ("Release Date Integrity", check_release_dates),
        ("Release Metadata and Documentation", check_release_state_consistency),
        ("Consumer Installation Snippets", check_all_consumer_snippets),
        ("Maven Coordinates & Gradle Plugin ID", check_all_coordinates),
        ("Compiler Baseline Documentation", check_compiler_baseline_documented),
        ("Spring Configuration Properties", check_all_spring_properties),
        ("Internal Markdown Link Resolution", check_all_markdown_links),
        ("Public Type Classification Alignment", check_all_public_types),
        ("Diagnostic Code Consistency", check_all_diagnostic_codes),
        ("Compatibility Matrix Synchronization", check_compatibility_matrix_synced),
        ("Stale Pre-Publication Language in Living Docs", check_stale_release_language_in_living_docs),
        ("Velocity Compatibility Claims in Living Docs", check_velocity_compatibility_overclaims_in_living_docs),
        ("Developer-Specific File Links", check_developer_specific_file_links),
        ("Benchmark Claims Consistency", check_benchmark_claims_consistency),
    ]

    for name, check_fn in checks:
        if verbose:
            print(f"==> Running check: {name}...")
        errors = check_fn(repo_root)
        if errors:
            all_errors.extend(errors)
            if verbose:
                for err in errors:
                    print(f"    [FAIL] {err}")
        elif verbose:
            print("    [PASS]")

    return all_errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify documentation integrity, snippets, links, and baseline consistency."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root directory (defaults to parent of scripts/)",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose output during verification.",
    )
    args = parser.parse_args()
    repo_root = args.root.resolve()

    if not (repo_root / "README.md").exists():
        print(f"Error: Invalid repository root '{repo_root}'. README.md not found.", file=sys.stderr)
        return 1

    print(f"Verifying documentation in '{repo_root}'...")
    errors = verify_all(repo_root, verbose=args.verbose)

    if errors:
        print(f"\nDocumentation verification FAILED with {len(errors)} error(s):", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1

    print("Documentation verification PASSED: all checks clean.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
