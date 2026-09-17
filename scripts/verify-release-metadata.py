#!/usr/bin/env python3
"""
scripts/verify-release-metadata.py

Validates release metadata across Gradle Kotlin DSL and Apache Maven builds:
1. Extract and compare project versions between pom.xml and build.gradle.kts.
2. Verify release tag format (e.g. v0.1.0) and verify that tag matches project version exactly.
3. Verify that live releases do NOT use -SNAPSHOT versions.
4. Output status variables for CI workflows when requested.
"""

import sys
import re
import os
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

import subprocess
import tempfile
import urllib.error
import urllib.request
import yaml

ROOT_DIR = Path(__file__).resolve().parent.parent

PUBLISHED_MODULES = [
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
]

NON_PUBLISHED_MODULES = [
    "viet-template-tck",
    "viet-template-benchmarks",
]

RE_INVALID_URL = re.compile(r"^https://github\.com/minh124199/viet-template/viet-template-.*")
RE_INVALID_SCM = re.compile(r"(/viet-template-)|(viet-template\.git/viet-template-.*)|(^scm:git:git://github\.com/)")


def get_reactor_modules(root_dir=ROOT_DIR):
    """Derives published production modules and non-published modules dynamically from root pom.xml."""
    pom_file = root_dir / "pom.xml"
    if not pom_file.exists():
        return list(PUBLISHED_MODULES), list(NON_PUBLISHED_MODULES)
    try:
        pom_tree = ET.parse(pom_file)
        pom_root = pom_tree.getroot()
        ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
        prefix = "m:" if ns else ""
        modules_elem = pom_root.find(f"./{prefix}modules", ns)
        if modules_elem is None:
            return list(PUBLISHED_MODULES), list(NON_PUBLISHED_MODULES)

        published = []
        non_published = []
        for mod_elem in modules_elem.findall(f"./{prefix}module", ns):
            mod_name = mod_elem.text.strip() if mod_elem.text else ""
            if not mod_name:
                continue
            child_pom = root_dir / mod_name / "pom.xml"
            if not child_pom.exists():
                continue
            child_tree = ET.parse(child_pom)
            child_root = child_tree.getroot()
            c_ns = {"m": child_root.tag.split("}")[0].strip("{")} if "}" in child_root.tag else {}
            c_prefix = "m:" if c_ns else ""
            props = child_root.find(f"./{c_prefix}properties", c_ns)
            skip = False
            if props is not None:
                for skip_prop in ("maven.deploy.skip", "skipPublishing", "central.publishing.skip"):
                    val = props.findtext(f"./{c_prefix}{skip_prop}", namespaces=c_ns)
                    if val and val.strip() == "true":
                        skip = True
                        break
            if skip:
                non_published.append(mod_name)
            else:
                published.append(mod_name)
        return published, non_published
    except Exception:
        return list(PUBLISHED_MODULES), list(NON_PUBLISHED_MODULES)


def validate_effective_pom(errors, effective_pom_path=None):
    tmp_path = None
    try:
        if effective_pom_path is not None:
            eff_path = Path(effective_pom_path)
        else:
            mvnw_cmd = ROOT_DIR / "mvnw"
            if not mvnw_cmd.exists():
                errors.append(f"mvnw wrapper missing at {mvnw_cmd}")
                return
            with tempfile.NamedTemporaryFile(suffix="-effective-pom.xml", delete=False) as tmp:
                tmp_path = Path(tmp.name)
            cmd = [str(mvnw_cmd), "help:effective-pom", f"-Doutput={tmp_path}", "-q"]
            result = subprocess.run(cmd, cwd=ROOT_DIR, capture_output=True, text=True)
            if result.returncode != 0:
                errors.append(f"mvnw help:effective-pom failed (exit code {result.returncode}): {result.stderr.strip()}")
                return
            eff_path = tmp_path

        eff_tree = ET.parse(eff_path)
        eff_root = eff_tree.getroot()
        eff_ns = {"m": "http://maven.apache.org/POM/4.0.0"}

        projects = eff_root.findall(".//m:project", eff_ns)
        if not projects:
            # Fallback without namespace if default namespace stripping occurred
            projects = eff_root.findall(".//project")
        if not projects and eff_root.tag.endswith("project"):
            projects = [eff_root]

        found_artifacts = set()
        for p in projects:
            art_elem = p.find("m:artifactId", eff_ns)
            if art_elem is None:
                art_elem = p.find("artifactId")
            if art_elem is None or not art_elem.text:
                continue
            art_id = art_elem.text.strip()
            found_artifacts.add(art_id)

            url_elem = p.find("m:url", eff_ns)
            if url_elem is None:
                url_elem = p.find("url")
            url_val = url_elem.text.strip() if url_elem is not None and url_elem.text else ""

            scm_p = p.find("m:scm", eff_ns)
            if scm_p is None:
                scm_p = p.find("scm")

            scm_conn = ""
            scm_dev = ""
            scm_url = ""
            if scm_p is not None:
                scm_conn = (scm_p.findtext("m:connection", namespaces=eff_ns) or scm_p.findtext("connection") or "").strip()
                scm_dev = (scm_p.findtext("m:developerConnection", namespaces=eff_ns) or scm_p.findtext("developerConnection") or "").strip()
                scm_url = (scm_p.findtext("m:url", namespaces=eff_ns) or scm_p.findtext("url") or "").strip()

            # SCM assertions apply to all projects in reactor
            for label, val in [("scm.connection", scm_conn), ("scm.developerConnection", scm_dev), ("scm.url", scm_url)]:
                if val and RE_INVALID_SCM.search(val):
                    errors.append(f"Effective POM for {art_id} has invalid {label} (appended module or rejected protocol): '{val}'")
            if scm_url and RE_INVALID_URL.match(scm_url):
                errors.append(f"Effective POM for {art_id} has invalid scm.url: '{scm_url}'")

            if scm_conn.startswith("scm:git:git://github.com/"):
                errors.append(f"Effective POM for {art_id} scm.connection must not use obsolete git:// protocol: '{scm_conn}'")
            elif scm_conn != "scm:git:https://github.com/minh124199/viet-template.git":
                errors.append(f"Effective POM for {art_id} scm.connection must be 'scm:git:https://github.com/minh124199/viet-template.git', found '{scm_conn}'")

            if scm_dev != "scm:git:ssh://git@github.com/minh124199/viet-template.git":
                errors.append(f"Effective POM for {art_id} scm.developerConnection must not append module path: '{scm_dev}'")
            if scm_url != "https://github.com/minh124199/viet-template":
                errors.append(f"Effective POM for {art_id} scm.url must not append module path: '{scm_url}'")

            # Project URL assertions apply to parent and published modules
            published_modules, _ = get_reactor_modules(ROOT_DIR)
            if art_id == "viet-template-parent":
                if url_val != "https://github.com/minh124199/viet-template":
                    errors.append(f"Effective POM for parent url must be 'https://github.com/minh124199/viet-template', found '{url_val}'")
                if RE_INVALID_URL.match(url_val):
                    errors.append(f"Effective POM for parent url has invalid pattern: '{url_val}'")
            elif art_id in published_modules:
                expected_url = f"https://github.com/minh124199/viet-template/tree/main/{art_id}"
                if url_val == "https://github.com/minh124199/viet-template":
                    errors.append(
                        f"Effective POM for published module {art_id} merely inherited repository-root url; must explicitly declare '{expected_url}'"
                    )
                elif url_val != expected_url:
                    errors.append(f"Effective POM for published module {art_id} url must be '{expected_url}', found '{url_val}'")
                if RE_INVALID_URL.match(url_val):
                    errors.append(f"Effective POM for published module {art_id} has invalid url: '{url_val}'")

        published_modules, _ = get_reactor_modules(ROOT_DIR)
        missing = set(published_modules) - found_artifacts
        if missing:
            errors.append(f"Effective POM missing published modules: {sorted(missing)}")
    except Exception as exc:
        errors.append(f"Effective POM validation failed: {exc}")
    finally:
        if tmp_path is not None and tmp_path.exists():
            tmp_path.unlink()


def validate_urls_online(errors, strict=False):
    root_url = "https://github.com/minh124199/viet-template"
    published_modules, _ = get_reactor_modules(ROOT_DIR)
    check_items = [("viet-template", root_url)]
    for mod in published_modules:
        check_items.append((mod, f"https://github.com/minh124199/viet-template/tree/main/{mod}"))

    for name, u in check_items:
        req = urllib.request.Request(u, headers={"User-Agent": "viet-template-release-audit/1"})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                status = getattr(resp, "status", 200)
                if status != 200:
                    errors.append(f"Online URL check returned status {status} for {u}")
                else:
                    print(f"  [PASS] URL online check: {u} (HTTP 200)")
        except urllib.error.HTTPError as exc:
            if exc.code == 404 and not strict:
                # Check if this is a newly created module not yet merged to origin/main
                res = subprocess.run(["git", "cat-file", "-e", f"origin/main:{name}"], cwd=ROOT_DIR, capture_output=True)
                if res.returncode != 0:
                    print(f"  [WARN] URL online check: {u} (HTTP 404, expected for new unmerged module not yet on origin/main)")
                    continue
            errors.append(f"Online URL check failed for {u}: HTTP {exc.code}")
        except Exception as exc:
            errors.append(f"Online URL check failed for {u}: {exc}")


def validate_publication_metadata(errors, check_effective=False, check_online=False, effective_pom_path=None):
    pom_file = ROOT_DIR / "pom.xml"
    if not pom_file.exists():
        errors.append("Root pom.xml missing")
        return

    try:
        pom_tree = ET.parse(pom_file)
    except Exception as exc:
        errors.append(f"Failed to parse root pom.xml: {exc}")
        return

    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""

    # 1. Root POM URL and inheritance controls
    root_url_elem = pom_root.find(f"./{prefix}url", ns)
    root_url = root_url_elem.text.strip() if root_url_elem is not None and root_url_elem.text else ""
    if root_url != "https://github.com/minh124199/viet-template":
        errors.append(f"Root pom.xml url must be 'https://github.com/minh124199/viet-template', found '{root_url}'")
    if RE_INVALID_URL.match(root_url):
        errors.append(f"Root pom.xml url matches invalid appended pattern: '{root_url}'")

    proj_url_inherit = pom_root.attrib.get("child.project.url.inherit.append.path")
    if proj_url_inherit != "false":
        errors.append(
            f"Root pom.xml <project> element must declare child.project.url.inherit.append.path=\"false\", found '{proj_url_inherit}'"
        )

    # Root properties github.repository.url
    props_elem = pom_root.find(f"./{prefix}properties", ns)
    github_repo_url = None
    if props_elem is not None:
        prop = props_elem.find(f"./{prefix}github.repository.url", ns)
        if prop is not None and prop.text:
            github_repo_url = prop.text.strip()
    if github_repo_url != "https://github.com/minh124199/viet-template":
        errors.append(
            f"Root pom.xml must define property <github.repository.url>https://github.com/minh124199/viet-template</github.repository.url>, found '{github_repo_url}'"
        )

    # 2. Root POM SCM attributes & values
    scm_elem = pom_root.find(f"./{prefix}scm", ns)
    if scm_elem is None:
        errors.append("Root pom.xml missing <scm> element")
    else:
        for attr in (
            "child.scm.connection.inherit.append.path",
            "child.scm.developerConnection.inherit.append.path",
            "child.scm.url.inherit.append.path",
        ):
            val = scm_elem.attrib.get(attr)
            if val != "false":
                errors.append(f"Root pom.xml <scm> must declare {attr}=\"false\", found '{val}'")

        conn_elem = scm_elem.find(f"./{prefix}connection", ns)
        conn = conn_elem.text.strip() if conn_elem is not None and conn_elem.text else ""
        if conn.startswith("scm:git:git://github.com/"):
            errors.append(f"Root pom.xml scm connection must not use obsolete git:// protocol: '{conn}'")
        elif conn != "scm:git:https://github.com/minh124199/viet-template.git":
            errors.append(
                f"Root pom.xml scm connection must be 'scm:git:https://github.com/minh124199/viet-template.git', found '{conn}'"
            )
        if RE_INVALID_SCM.search(conn):
            errors.append(f"Root pom.xml scm connection matches invalid pattern or rejected protocol: '{conn}'")

        dev_conn_elem = scm_elem.find(f"./{prefix}developerConnection", ns)
        dev_conn = dev_conn_elem.text.strip() if dev_conn_elem is not None and dev_conn_elem.text else ""
        if dev_conn != "scm:git:ssh://git@github.com/minh124199/viet-template.git":
            errors.append(
                f"Root pom.xml scm developerConnection must be 'scm:git:ssh://git@github.com/minh124199/viet-template.git', found '{dev_conn}'"
            )
        if RE_INVALID_SCM.search(dev_conn):
            errors.append(f"Root pom.xml scm developerConnection matches invalid appended pattern: '{dev_conn}'")

        scm_url_elem = scm_elem.find(f"./{prefix}url", ns)
        s_url = scm_url_elem.text.strip() if scm_url_elem is not None and scm_url_elem.text else ""
        if s_url != "https://github.com/minh124199/viet-template":
            errors.append(f"Root pom.xml scm url must be 'https://github.com/minh124199/viet-template', found '{s_url}'")
        if RE_INVALID_SCM.search(s_url) or RE_INVALID_URL.match(s_url):
            errors.append(f"Root pom.xml scm url matches invalid appended pattern: '{s_url}'")

    # 3. Published child module POMs (derived dynamically from reactor)
    published_modules, non_published_modules = get_reactor_modules(ROOT_DIR)
    for mod in published_modules:
        mod_pom = ROOT_DIR / mod / "pom.xml"
        if not mod_pom.exists():
            errors.append(f"Published module {mod} missing pom.xml")
            continue
        try:
            m_tree = ET.parse(mod_pom)
            m_root = m_tree.getroot()
            m_ns = {"m": m_root.tag.split("}")[0].strip("{")} if "}" in m_root.tag else {}
            m_prefix = "m:" if m_ns else ""

            url_elem = m_root.find(f"./{m_prefix}url", m_ns)
            if url_elem is None or not url_elem.text:
                errors.append(f"Published module {mod} must have explicit <url> declared in pom.xml")
            else:
                mod_url = url_elem.text.strip()
                expected_raw_1 = f"https://github.com/minh124199/viet-template/tree/main/{mod}"
                expected_raw_2 = f"${{github.repository.url}}/tree/main/{mod}"
                if mod_url == "https://github.com/minh124199/viet-template":
                    errors.append(
                        f"Published module {mod} merely inherited repository-root url; must explicitly declare '{expected_raw_1}' or '{expected_raw_2}'"
                    )
                elif mod_url not in (expected_raw_1, expected_raw_2):
                    errors.append(
                        f"Published module {mod} url must be '{expected_raw_1}' or '{expected_raw_2}', found '{mod_url}'"
                    )
                if RE_INVALID_URL.match(mod_url):
                    errors.append(f"Published module {mod} url matches invalid appended pattern: '{mod_url}'")

            mod_scm = m_root.find(f"./{m_prefix}scm", m_ns)
            if mod_scm is not None:
                for child_tag in ("connection", "developerConnection", "url"):
                    elem = mod_scm.find(f"./{m_prefix}{child_tag}", m_ns)
                    if elem is not None and elem.text:
                        val = elem.text.strip()
                        if RE_INVALID_SCM.search(val) or (child_tag == "url" and RE_INVALID_URL.match(val)):
                            errors.append(f"Published module {mod} scm.{child_tag} matches invalid pattern: '{val}'")
        except Exception as exc:
            errors.append(f"Failed to parse {mod}/pom.xml: {exc}")

    # 4. Non-published modules
    for mod in non_published_modules:
        mod_pom = ROOT_DIR / mod / "pom.xml"
        if not mod_pom.exists():
            errors.append(f"Non-published module {mod} missing pom.xml")
            continue
        try:
            m_tree = ET.parse(mod_pom)
            m_root = m_tree.getroot()
            m_ns = {"m": m_root.tag.split("}")[0].strip("{")} if "}" in m_root.tag else {}
            m_prefix = "m:" if m_ns else ""

            url_elem = m_root.find(f"./{m_prefix}url", m_ns)
            if url_elem is not None and url_elem.text and url_elem.text.strip():
                errors.append(f"Non-published module {mod} must NOT declare <url> in pom.xml, found '{url_elem.text.strip()}'")

            props = {}
            props_elem = m_root.find(f"./{m_prefix}properties", m_ns)
            if props_elem is not None:
                for child in props_elem:
                    tag = child.tag.split("}")[-1] if "}" in child.tag else child.tag
                    props[tag] = child.text.strip() if child.text else ""

            for required_prop in ("maven.deploy.skip", "skipPublishing", "central.publishing.skip"):
                if props.get(required_prop) != "true":
                    errors.append(f"Non-published module {mod} must have property <{required_prop}>true</{required_prop}>")
        except Exception as exc:
            errors.append(f"Failed to parse {mod}/pom.xml: {exc}")

    # 5. Effective POM check (when requested)
    if check_effective:
        validate_effective_pom(errors, effective_pom_path=effective_pom_path)

    # 6. Online URL check (when requested)
    if check_online:
        validate_urls_online(errors)


def as_needs(job):
    needs = job.get("needs", [])
    return {needs} if isinstance(needs, str) else set(needs)


def combined_run(job):
    return "\n".join(step.get("run", "") for step in job.get("steps", []) if isinstance(step, dict))


def validate_workflow_contract(errors):
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")}
    plugins = pom_root.findall(".//m:plugin", ns)
    central = next(
        (p for p in plugins if p.findtext("m:artifactId", namespaces=ns) == "central-publishing-maven-plugin"),
        None,
    )
    if central is None:
        errors.append("pom.xml is missing central-publishing-maven-plugin")
    else:
        config = central.find("m:configuration", ns)
        values = {
            child.tag.split("}")[-1]: (child.text or "").strip()
            for child in (list(config) if config is not None else [])
        }
        if values.get("publishingServerId") != "central":
            errors.append("Central publisher must use publishingServerId=central")
        if values.get("autoPublish") != "true":
            errors.append("Central publisher must keep autoPublish=true")
        if values.get("waitUntil") != "validated":
            errors.append("Central publisher must use waitUntil=validated; publication polling belongs to CI")

    workflow_path = ROOT_DIR / ".github" / "workflows" / "release.yml"
    try:
        workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        errors.append(f"release workflow cannot be parsed as YAML: {exc}")
        return
    jobs = workflow.get("jobs", {}) if isinstance(workflow, dict) else {}
    required = {
        "validate-metadata",
        "verify-builds",
        "package-and-validate-bundle",
        "guard-publication",
        "publish-to-central",
        "wait-for-central-publication",
        "verify-public-artifacts",
        "central-consumer-smoke",
        "create-github-release",
    }
    missing = required - set(jobs)
    if missing:
        errors.append(f"release workflow missing jobs: {', '.join(sorted(missing))}")
        return
    if "publish-to-central" not in as_needs(jobs["wait-for-central-publication"]):
        errors.append("publication monitor must depend on publish-to-central")
    if "wait-for-central-publication" not in as_needs(jobs["verify-public-artifacts"]):
        errors.append("public artifact verification must depend on publication monitor")
    if "verify-public-artifacts" not in as_needs(jobs["central-consumer-smoke"]):
        errors.append("Central-only consumer smoke must depend on public artifact verification")
    release_needs = as_needs(jobs["create-github-release"])
    if "central-consumer-smoke" not in release_needs or "publish-to-central" in release_needs:
        errors.append("GitHub Release must depend on verified Central consumers, not directly on Maven deploy")
    release_run = combined_run(jobs["create-github-release"])
    if "gh release view" not in release_run or "gh release create" not in release_run:
        errors.append("GitHub Release finalization must be idempotent (verify existing or create absent)")
    for job_name in ("verify-public-artifacts", "central-consumer-smoke", "create-github-release"):
        condition = str(jobs[job_name].get("if", ""))
        if "always()" not in condition or ".result == 'success'" not in condition:
            errors.append(
                f"{job_name} must explicitly tolerate skipped upload ancestors while requiring its predecessor to succeed"
            )

    publish_run = combined_run(jobs["publish-to-central"])
    if "./mvnw clean deploy -P release" not in publish_run:
        errors.append("publish-to-central must invoke the Maven release deploy")
    if "gpg.passphrase" in publish_run:
        errors.append("release workflow must not pass deprecated gpg.passphrase on the command line")
    if "MAVEN_GPG_PASSPHRASE" not in str(jobs["publish-to-central"]):
        errors.append("publish-to-central must supply the supported secret-safe MAVEN_GPG_PASSPHRASE environment variable")
    if "extract-central-deployment-id.py" not in publish_run:
        errors.append("publish-to-central must strictly capture the deployment ID")
    monitor_run = combined_run(jobs["wait-for-central-publication"])
    if "check-central-deployment.py" not in monitor_run or "--timeout 7200" not in monitor_run:
        errors.append("publication monitor must use the shared checker with the 120-minute policy")
    if jobs["publish-to-central"].get("environment") != "release":
        errors.append("publish-to-central must retain the protected release environment")
    if jobs["wait-for-central-publication"].get("environment") != "release":
        errors.append("authenticated Central monitoring must retain the protected release environment")
    if workflow.get("concurrency", {}).get("cancel-in-progress") is not False:
        errors.append("release concurrency must set cancel-in-progress=false")
    if "github.run_attempt" not in workflow_path.read_text(encoding="utf-8"):
        errors.append("release workflow must prevent tag workflow reruns from redeploying")
    if "github.run_attempt" not in str(jobs["publish-to-central"]):
        errors.append("publish-to-central must independently block rerun attempts")
    if "GH_REPO" not in str(jobs["create-github-release"]):
        errors.append("GitHub Release finalization must explicitly identify the repository")
    cleanup_run = "\n".join(
        step.get("run", "")
        for step in jobs["publish-to-central"].get("steps", [])
        if isinstance(step, dict) and "Clean up" in step.get("name", "")
    )
    if "fpr:" not in cleanup_run or "--delete-secret-keys \"$fingerprint\"" not in cleanup_run:
        errors.append("GPG cleanup must delete imported secret keys by full fingerprint")

    for module in ("viet-template-tck", "viet-template-benchmarks"):
        module_text = (ROOT_DIR / module / "pom.xml").read_text(encoding="utf-8")
        for setting in ("<maven.deploy.skip>true</maven.deploy.skip>", "<skipPublishing>true</skipPublishing>"):
            if setting not in module_text:
                errors.append(f"{module} publication exclusion missing {setting}")
    gradle_text = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    for module in ("viet-template-tck", "viet-template-benchmarks"):
        if f'project.name != "{module}"' not in gradle_text:
            errors.append(f"Gradle publication exclusion missing for {module}")

def get_gradle_version():
    build_gradle = (ROOT_DIR / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'version\s*=\s*["\']([^"\']+)["\']', build_gradle)
    if not match:
        raise ValueError("Could not find 'version' in build.gradle.kts")
    return match.group(1).strip()

def get_maven_version():
    pom_tree = ET.parse(ROOT_DIR / "pom.xml")
    pom_root = pom_tree.getroot()
    ns = {"m": pom_root.tag.split("}")[0].strip("{")} if "}" in pom_root.tag else {}
    prefix = "m:" if ns else ""
    ver_elem = pom_root.find(f"./{prefix}version", ns)
    if ver_elem is None or not ver_elem.text:
        raise ValueError("Could not find '<version>' in root pom.xml")
    return ver_elem.text.strip()

def main():
    parser = argparse.ArgumentParser(description="Verify release version and tag consistency.")
    parser.add_argument("--tag", help="Release tag being evaluated (e.g. v0.1.0)")
    parser.add_argument("--require-non-snapshot", action="store_true", help="Fail if version is a SNAPSHOT")
    parser.add_argument("--require-release", action="store_true", help="Fail if version is a SNAPSHOT (enforces release version)")
    parser.add_argument("--require-match-tag", action="store_true", help="Fail if tag is not provided or does not match version")
    parser.add_argument("--check-workflow-contract", action="store_true", help="Statically validate release workflow and pom configuration contract")
    parser.add_argument("--check-publication-metadata", action="store_true", help="Validate publication metadata in POMs and SCM configuration")
    parser.add_argument("--check-effective-pom", action="store_true", help="Generate and validate effective POM metadata across modules")
    parser.add_argument("--check-urls-online", action="store_true", help="Perform online HTTP checks on repository and module URLs")
    args = parser.parse_args()

    print("=== Viet Template Release Metadata Verification ===")
    errors = []

    gradle_version = get_gradle_version()
    maven_version = get_maven_version()

    print(f"[CHECK] Gradle version: {gradle_version}")
    print(f"[CHECK] Maven version:  {maven_version}")

    if gradle_version != maven_version:
        errors.append(f"Version mismatch: Gradle has '{gradle_version}' while Maven has '{maven_version}'")
    else:
        print(f"[PASS] Both build systems agree on version '{maven_version}'.")

    version = maven_version
    is_snapshot = "SNAPSHOT" in version.upper()
    print(f"[CHECK] Is SNAPSHOT version: {is_snapshot}")

    if (args.require_non_snapshot or args.require_release) and is_snapshot:
        errors.append(f"Release requirement failed: version '{version}' is a SNAPSHOT version. Live releases require a release version (e.g. 0.1.0).")

    if args.tag is not None:
        tag = args.tag
    elif "RELEASE_TAG" in os.environ:
        # An explicit empty value means a branch-based dry run, not "fall back to branch name".
        tag = os.environ["RELEASE_TAG"]
    elif os.environ.get("GITHUB_REF_TYPE") == "tag":
        tag = os.environ.get("GITHUB_REF_NAME")
    else:
        tag = None
    if tag and tag.startswith("refs/tags/"):
        tag = tag.replace("refs/tags/", "")

    if tag:
        print(f"[CHECK] Evaluating tag: '{tag}'")
        tag_pattern = r"^v(\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?)$"
        tag_match = re.match(tag_pattern, tag)
        if not tag_match:
            errors.append(f"Malformed release tag '{tag}'. Tags must conform to 'vX.Y.Z' format (e.g. 'v0.1.0').")
        else:
            expected_version = tag_match.group(1)
            print(f"[CHECK] Expected version from tag: '{expected_version}'")
            if version != expected_version:
                errors.append(
                    f"Tag/version mismatch: tag '{tag}' implies version '{expected_version}', "
                    f"but repository version is '{version}'."
                )
            else:
                print(f"[PASS] Tag '{tag}' exactly matches repository version '{version}'.")
    elif args.require_match_tag:
        errors.append("Release requirement failed: release tag was required but none was provided.")

    if args.check_workflow_contract:
        print("\n[CHECK] Validating release workflow contract and publishing configuration...")
        before = len(errors)
        validate_workflow_contract(errors)
        if len(errors) == before:
            print("  [PASS] Release workflow contract verified successfully.")

    if args.check_publication_metadata or args.check_effective_pom or args.check_urls_online:
        print("\n[CHECK] Validating publication metadata...")
        before = len(errors)
        validate_publication_metadata(
            errors,
            check_effective=args.check_effective_pom,
            check_online=args.check_urls_online,
        )
        if len(errors) == before:
            print("  [PASS] Publication metadata verified successfully.")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as f:
            f.write(f"project_version={version}\n")
            f.write(f"is_snapshot={'true' if is_snapshot else 'false'}\n")
            if tag:
                f.write(f"tag_name={tag}\n")

    if errors:
        print("\n[FAILED] Release metadata verification FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Release metadata verification PASSED.")
        sys.exit(0)

if __name__ == "__main__":
    main()
