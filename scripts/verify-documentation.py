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
"""

import argparse
import os
import re
import sys
from pathlib import Path

DEFAULT_RELEASED_VERSION = "0.2.2"
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


# =============================================================================
# CHECK 2: Consumer Installation Snippets
# =============================================================================

def check_consumer_snippets_in_text(
    text: str,
    filename: str = "<text>",
    released_version: str = DEFAULT_RELEASED_VERSION,
) -> list[str]:
    """Checks that consumer installation snippets reference the released version."""
    errors = []

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
                    for kw in ("snapshot", "development", "dev build", "nightly", "main branch", "pre-release")
                )
                if not is_labeled:
                    errors.append(
                        f"Consumer installation snippet in {filename} references snapshot "
                        f"version '{version}' for {artifact} without explicit snapshot/development label."
                    )
            elif version != released_version:
                errors.append(
                    f"Consumer installation snippet in {filename} references version "
                    f"'{version}' for {artifact} (expected released version '{released_version}')."
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
                    for kw in ("snapshot", "development", "dev build", "nightly", "main branch", "pre-release")
                )
                if not is_labeled:
                    errors.append(
                        f"Consumer installation snippet in {filename} references snapshot "
                        f"version '{version}' for {artifact} without explicit snapshot/development label."
                    )
            elif version != released_version:
                errors.append(
                    f"Consumer installation snippet in {filename} references version "
                    f"'{version}' for {artifact} (expected released version '{released_version}')."
                )

    return errors


def check_all_consumer_snippets(
    repo_root: Path,
    released_version: str = DEFAULT_RELEASED_VERSION,
) -> list[str]:
    """Verifies consumer installation snippets in README.md and active docs."""
    errors = []
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
        field_re = re.compile(r"private\s+(?:boolean|int|long|String|Charset)\s+([a-zA-Z0-9_]+)\b")
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
# TOP-LEVEL VERIFICATION ORCHESTRATION
# =============================================================================

def verify_all(repo_root: Path, verbose: bool = False) -> list[str]:
    """Runs all documentation verification checks and aggregates any errors."""
    all_errors: list[str] = []

    checks = [
        ("Release Date Integrity", check_release_dates),
        ("Consumer Installation Snippets", check_all_consumer_snippets),
        ("Maven Coordinates & Gradle Plugin ID", check_all_coordinates),
        ("Compiler Baseline Documentation", check_compiler_baseline_documented),
        ("Spring Configuration Properties", check_all_spring_properties),
        ("Internal Markdown Link Resolution", check_all_markdown_links),
        ("Public Type Classification Alignment", check_all_public_types),
        ("Diagnostic Code Consistency", check_all_diagnostic_codes),
        ("Compatibility Matrix Synchronization", check_compatibility_matrix_synced),
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
