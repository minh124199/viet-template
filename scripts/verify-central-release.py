#!/usr/bin/env python3
"""Guard or verify Viet Template artifacts on the public Maven Central repository."""

from __future__ import annotations

import argparse
import os
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

BASE_URL = "https://repo1.maven.org/maven2"
GROUP_PATH = "io/github/minh124199"
PARENT = "viet-template-parent"
PRODUCTION_MODULES = (
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
)
EXCLUDED_MODULES = ("viet-template-tck", "viet-template-benchmarks")


def artifact_urls(version: str) -> dict[str, tuple[str, ...]]:
    result = {PARENT: (f"{PARENT}-{version}.pom",)}
    for module in PRODUCTION_MODULES:
        result[module] = (
            f"{module}-{version}.pom",
            f"{module}-{version}.jar",
            f"{module}-{version}-sources.jar",
            f"{module}-{version}-javadoc.jar",
        )
    return result


def url_for(module: str, version: str, filename: str) -> str:
    return f"{BASE_URL}/{GROUP_PATH}/{module}/{version}/{filename}"


def exists(url: str, request_timeout: float) -> bool:
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "viet-template-release-audit/1"})
    try:
        with urllib.request.urlopen(request, timeout=request_timeout) as response:
            return getattr(response, "status", 200) == 200
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return False
        raise RuntimeError(f"HTTP {exc.code} for {url}") from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"request failed for {url}: {exc}") from exc


def fetch(url: str, request_timeout: float) -> bytes:
    request = urllib.request.Request(url, method="GET", headers={"User-Agent": "viet-template-release-audit/1"})
    try:
        with urllib.request.urlopen(request, timeout=request_timeout) as response:
            return response.read()
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"could not read {url}: {exc}") from exc


def audit_pom(module: str, version: str, content: bytes) -> list[str]:
    try:
        root = ET.fromstring(content)
    except ET.ParseError:
        return [f"malformed public POM: {module}"]
    ns_uri = root.tag.split("}")[0].strip("{") if "}" in root.tag else ""
    ns = {"m": ns_uri} if ns_uri else {}
    prefix = "m:" if ns else ""
    version_node = root.find(f"./{prefix}version", ns)
    if version_node is None:
        version_node = root.find(f"./{prefix}parent/{prefix}version", ns)
    actual = version_node.text.strip() if version_node is not None and version_node.text else None
    errors = []
    if actual != version:
        errors.append(f"public POM version mismatch for {module}: {actual!r} != {version!r}")
    if b"SNAPSHOT" in content.upper():
        errors.append(f"SNAPSHOT leakage in public POM: {module}")
    return errors


def write_outputs(path: Path | None, **values: str) -> None:
    for key, value in values.items():
        print(f"{key}={value}")
    if path:
        with path.open("a", encoding="utf-8") as stream:
            for key, value in values.items():
                stream.write(f"{key}={value}\n")


def guard(version: str, request_timeout: float, output: Path | None) -> int:
    checks = [(module, filename) for module, files in artifact_urls(version).items() for filename in files]
    found = [(module, filename) for module, filename in checks if exists(url_for(module, version, filename), request_timeout)]
    if not found:
        write_outputs(output, publication_state="absent", already_published="false")
        return 0
    if len(found) == len(checks):
        write_outputs(output, publication_state="published", already_published="true")
        return 0
    print("PUBLIC_ARTIFACT_MISMATCH: target version is partially present; refusing deployment", file=sys.stderr)
    for module, filename in found:
        print(f"present={module}/{filename}", file=sys.stderr)
    write_outputs(output, publication_state="partial", already_published="true")
    return 1


def verify_once(version: str, request_timeout: float) -> list[str]:
    errors = []
    for module, files in artifact_urls(version).items():
        for filename in files:
            url = url_for(module, version, filename)
            if not exists(url, request_timeout):
                errors.append(f"missing public artifact: {module}/{filename}")
            elif filename.endswith(".pom"):
                errors.extend(audit_pom(module, version, fetch(url, request_timeout)))
    for module in EXCLUDED_MODULES:
        for suffix in (".pom", ".jar", "-sources.jar", "-javadoc.jar"):
            filename = f"{module}-{version}{suffix}"
            if exists(url_for(module, version, filename), request_timeout):
                errors.append(f"internal module is publicly available: {module}/{filename}")
    return errors


def verify(version: str, timeout: float, interval: float, request_timeout: float, output: Path | None) -> int:
    deadline = time.monotonic() + timeout
    while True:
        try:
            errors = verify_once(version, request_timeout)
        except RuntimeError as exc:
            errors = [str(exc)]
        if not errors:
            write_outputs(output, verification_state="verified", version=version)
            return 0
        if time.monotonic() >= deadline:
            print("PUBLIC_ARTIFACT_MISMATCH:", file=sys.stderr)
            for error in errors:
                print(f"- {error}", file=sys.stderr)
            write_outputs(output, verification_state="failed", version=version)
            return 1
        time.sleep(min(interval, max(0, deadline - time.monotonic())))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("--mode", choices=("guard", "verify"), default="verify")
    parser.add_argument("--timeout", type=float, default=600)
    parser.add_argument("--poll-interval", type=float, default=15)
    parser.add_argument("--request-timeout", type=float, default=30)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    if args.version.upper().endswith("-SNAPSHOT"):
        parser.error("Maven Central release verification requires a non-SNAPSHOT version")
    if args.mode == "guard":
        return guard(args.version, args.request_timeout, args.github_output)
    return verify(args.version, args.timeout, args.poll_interval, args.request_timeout, args.github_output)


if __name__ == "__main__":
    raise SystemExit(main())
