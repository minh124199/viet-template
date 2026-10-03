#!/usr/bin/env python3
"""Deterministic OpenJDK HotSpot runtime validator and provenance extractor."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path


DISALLOWED_PATTERN = re.compile(r"graal|mandrel|jvmci", re.IGNORECASE)
CHECK_PROPERTIES = (
    "java.vendor",
    "java.vendor.version",
    "java.vm.vendor",
    "java.vm.version",
    "java.runtime.version",
    "java.vm.name",
    "java.runtime.name",
    "java.vendor.url",
)


def parse_properties(text: str) -> dict[str, str]:
    """Parse output of `java -XshowSettings:properties -version` into key->value dict.

    Property lines start with 4 spaces (`    key = value`). Continuation lines
    indented further without ` = ` are ignored (first value line is kept).
    Comments (#), header lines, and trailing version banner lines are ignored.
    """
    props: dict[str, str] = {}
    pattern = re.compile(r"^ {4}([^=\s]+)\s*=\s*(.*)$")
    for line in text.splitlines():
        line_stripped = line.strip()
        if not line_stripped or line_stripped.startswith("#"):
            continue
        m = pattern.match(line)
        if m:
            key = m.group(1).strip()
            val = m.group(2).strip()
            props[key] = val
    return props


def parse_flags(text: str) -> dict[str, str]:
    """Parse output of `java -XX:+PrintFlagsFinal` into flag_name->flag_value dict."""
    flags: dict[str, str] = {}
    pattern = re.compile(r"^\s*([a-zA-Z0-9_]+)\s+([a-zA-Z0-9_]+)\s*[:]?=\s*(.*?)\s*(?:\{.*\}|\s*)$")
    for line in text.splitlines():
        line_stripped = line.strip()
        if not line_stripped or line_stripped.startswith("#") or line_stripped.startswith("["):
            continue
        m = pattern.match(line)
        if m:
            name = m.group(2).strip()
            val = m.group(3).strip()
            flags[name] = val
    return flags


def evaluate(
    props: dict[str, str],
    required_major: int,
    flags: dict[str, str] | None = None,
) -> tuple[bool, list[str]]:
    """Evaluate whether JVM properties and flags satisfy genuine OpenJDK HotSpot requirements."""
    reasons: list[str] = []

    # 1. java.specification.version == str(required_major)
    spec_ver = props.get("java.specification.version", "")
    if spec_ver != str(required_major):
        reasons.append(
            f"java.specification.version '{spec_ver}' does not match required major version {required_major}"
        )

    # 2. java.runtime.name == 'OpenJDK Runtime Environment'
    rt_name = props.get("java.runtime.name", "")
    if rt_name != "OpenJDK Runtime Environment":
        reasons.append(f"java.runtime.name '{rt_name}' is not 'OpenJDK Runtime Environment'")

    # 3. java.vm.name starts with 'OpenJDK' and contains 'Server VM'
    vm_name = props.get("java.vm.name", "")
    if not (vm_name.startswith("OpenJDK") and "Server VM" in vm_name):
        reasons.append(f"java.vm.name '{vm_name}' does not start with 'OpenJDK' and contain 'Server VM'")

    # 4. None of the specified properties match graal|mandrel|jvmci
    for key in CHECK_PROPERTIES:
        val = props.get(key)
        if val and DISALLOWED_PATTERN.search(val):
            reasons.append(f"property '{key}' with value '{val}' matches disallowed pattern (graal|mandrel|jvmci)")

    # 5. Flags check (if provided): UseJVMCICompiler and EnableJVMCI must be 'false'
    if flags is not None:
        use_jvmci = flags.get("UseJVMCICompiler")
        if use_jvmci is not None and use_jvmci.lower() != "false":
            reasons.append(f"flag UseJVMCICompiler is '{use_jvmci}' (expected 'false')")
        enable_jvmci = flags.get("EnableJVMCI")
        if enable_jvmci is not None and enable_jvmci.lower() != "false":
            reasons.append(f"flag EnableJVMCI is '{enable_jvmci}' (expected 'false')")

    accepted = len(reasons) == 0
    return accepted, reasons


def parse_release_file(text: str | None) -> dict[str, str] | None:
    """Parse $JAVA_HOME/release key=value file, stripping quotes and excluding MODULES."""
    if text is None:
        return None
    release: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, val = line.split("=", 1)
            key = key.strip()
            val = val.strip()
            if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                val = val[1:-1]
            if key != "MODULES":
                release[key] = val
    return release


def identity_record(
    props: dict[str, str],
    flags: dict[str, str] | None,
    java_path: str | Path,
    release_text: str | None = None,
) -> dict:
    """Build deterministic runtime identity provenance dictionary."""
    java_file = Path(java_path)
    java_sha256 = None
    if java_file.is_file():
        java_sha256 = hashlib.sha256(java_file.read_bytes()).hexdigest()

    use_jvmci = None
    enable_jvmci = None
    if flags is not None:
        if "UseJVMCICompiler" in flags:
            use_jvmci = flags["UseJVMCICompiler"].lower() == "true"
        if "EnableJVMCI" in flags:
            enable_jvmci = flags["EnableJVMCI"].lower() == "true"

    vendor_version = props.get("java.vendor.version")
    if vendor_version == "":
        vendor_version = None

    return {
        "javaExecutable": str(java_path),
        "javaHome": props.get("java.home"),
        "javaVersion": props.get("java.version"),
        "javaVersionDate": props.get("java.version.date"),
        "specificationVersion": props.get("java.specification.version"),
        "vendor": props.get("java.vendor"),
        "vendorVersion": vendor_version,
        "vendorUrl": props.get("java.vendor.url"),
        "runtimeName": props.get("java.runtime.name"),
        "runtimeVersion": props.get("java.runtime.version"),
        "vmName": props.get("java.vm.name"),
        "vmVendor": props.get("java.vm.vendor"),
        "vmVersion": props.get("java.vm.version"),
        "useJvmciCompiler": use_jvmci,
        "enableJvmci": enable_jvmci,
        "releaseFile": parse_release_file(release_text),
        "javaExecutableSha256": java_sha256,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Deterministic OpenJDK HotSpot runtime validator")
    parser.add_argument("--java", required=True, help="Path to java executable")
    parser.add_argument("--major", type=int, required=True, help="Required Java major version")
    parser.add_argument("--profile", required=True, help="Profile ID (e.g. J21-G1, J25-G1)")
    parser.add_argument("--json-out", type=Path, help="Optional output path for identity JSON")

    try:
        args = parser.parse_args(argv)
    except SystemExit as exc:
        code = exc.code if exc.code is not None else 0
        return 2 if code != 0 else 0

    java_path = Path(args.java)
    if not java_path.is_file() or not os.access(java_path, os.X_OK):
        print(f"Error: java binary not found or not executable: {args.java}", file=sys.stderr)
        return 2

    try:
        props_res = subprocess.run(
            [str(java_path), "-XshowSettings:properties", "-version"],
            capture_output=True,
            text=True,
            check=True,
        )
        flags_res = subprocess.run(
            [str(java_path), "-XX:+UnlockExperimentalVMOptions", "-XX:+PrintFlagsFinal", "-version"],
            capture_output=True,
            text=True,
            check=True,
        )
    except (subprocess.CalledProcessError, OSError) as exc:
        print(f"Error executing java commands on {args.java}: {exc}", file=sys.stderr)
        return 2

    props = parse_properties(props_res.stdout + "\n" + props_res.stderr)
    flags = parse_flags(flags_res.stdout + "\n" + flags_res.stderr)

    release_text = None
    java_home = props.get("java.home")
    if java_home:
        release_file = Path(java_home) / "release"
        if release_file.is_file():
            try:
                release_text = release_file.read_text(encoding="utf-8")
            except OSError:
                pass

    accepted, reasons = evaluate(props, args.major, flags)
    identity = identity_record(props, flags, str(java_path), release_text)

    result = {
        "profile": args.profile,
        "requiredMajor": args.major,
        "accepted": accepted,
        "reasons": reasons,
        "identity": identity,
    }

    if args.json_out:
        try:
            args.json_out.parent.mkdir(parents=True, exist_ok=True)
            args.json_out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        except OSError as exc:
            print(f"Error writing --json-out to {args.json_out}: {exc}", file=sys.stderr)
            return 2

    if accepted:
        vendor = identity.get("vendor") or "Unknown"
        runtime_version = identity.get("runtimeVersion") or "Unknown"
        home = identity.get("javaHome") or "Unknown"
        print(f"[ACCEPT] {args.profile}: {vendor} {runtime_version} ({home})", file=sys.stderr)
        return 0
    else:
        reasons_msg = "; ".join(reasons)
        print(f"[REJECT] {args.profile}: {reasons_msg}", file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main())
