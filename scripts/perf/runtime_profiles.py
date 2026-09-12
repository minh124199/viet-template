#!/usr/bin/env python3
"""Authoritative benchmark runtime profiles CLI.

Provides unified querying and validation of benchmark runtime profiles
defined in config/benchmark-runtime-profiles.json.
Standard library only (Python 3.8+ compatible).
"""

import argparse
import json
import sys
from pathlib import Path


def find_repo_root() -> Path:
    current = Path(__file__).resolve().parent
    while current != current.parent:
        if (current / "config" / "benchmark-runtime-profiles.json").is_file():
            return current
        if (current / ".git").exists():
            return current
        current = current.parent
    return Path.cwd()


def load_profiles(config_path: Path) -> dict:
    if not config_path.is_file():
        raise FileNotFoundError(f"Runtime profile configuration file not found: {config_path}")
    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def validate_config(data: dict) -> None:
    if not isinstance(data, dict):
        raise ValueError("Profile configuration root must be an object.")
    if "profiles" not in data or not isinstance(data["profiles"], dict):
        raise ValueError("Configuration must contain a 'profiles' object.")
    default_profile = data.get("defaultProfile")
    if default_profile and default_profile not in data["profiles"]:
        raise ValueError(f"defaultProfile '{default_profile}' not found in profiles dictionary.")

    required_fields = [
        "description",
        "javaVersion",
        "historicallyComparable",
        "heap",
        "alwaysPreTouch",
        "garbageCollector",
        "compactObjectHeaders",
        "virtualThreads",
        "aot",
        "jvmFlags",
    ]
    for pid, pdata in data["profiles"].items():
        for field in required_fields:
            if field not in pdata:
                raise ValueError(f"Profile '{pid}' missing required field: '{field}'")
        if not isinstance(pdata["jvmFlags"], list) or not all(
            isinstance(flag, str) and flag for flag in pdata["jvmFlags"]
        ):
            raise ValueError(f"Profile '{pid}' jvmFlags must be a list of strings.")

    canonical = [
        "-server",
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC",
    ]
    if data["profiles"].get("J17-G1", {}).get("jvmFlags") != canonical:
        raise ValueError("J17-G1 must preserve the canonical historical JVM flags and order.")
    if data["profiles"].get("J17-G1", {}).get("historicallyComparable") is not True:
        raise ValueError("J17-G1 must remain marked historically comparable.")
    for pid, pdata in data["profiles"].items():
        flags = pdata["jvmFlags"]
        expected_gc_flag = "-XX:+UseG1GC" if pdata["garbageCollector"] == "G1" else "-XX:+UseZGC"
        if expected_gc_flag not in flags:
            raise ValueError(f"Profile '{pid}' is missing collector flag {expected_gc_flag}.")
        if pdata["alwaysPreTouch"] != ("-XX:+AlwaysPreTouch" in flags):
            raise ValueError(f"Profile '{pid}' AlwaysPreTouch metadata does not match jvmFlags.")
        if pdata["javaVersion"] >= 25 and "-XX:+ZGenerational" in flags:
            raise ValueError(f"Profile '{pid}' uses obsolete Java 25 ZGenerational selection.")


def main():
    parser = argparse.ArgumentParser(
        description="Query and validate benchmark runtime profiles."
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=None,
        help="Path to benchmark-runtime-profiles.json",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List all defined runtime profiles in a formatted table.",
    )
    parser.add_argument(
        "--default",
        action="store_true",
        help="Print the default profile ID.",
    )
    parser.add_argument(
        "--get-flags",
        metavar="PROFILE",
        help="Print space-delimited JVM flags for the specified profile.",
    )
    parser.add_argument(
        "--get-version",
        metavar="PROFILE",
        help="Print the required Java major version for the specified profile.",
    )
    parser.add_argument(
        "--get-property",
        nargs=2,
        metavar=("PROFILE", "KEY"),
        help="Print a specific property (e.g. garbageCollector, compactObjectHeaders, virtualThreads, aot).",
    )
    parser.add_argument(
        "--get-json",
        metavar="PROFILE",
        help="Print the JSON representation of the specified profile.",
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        help="Validate the profile configuration file.",
    )

    args = parser.parse_args()

    repo_root = find_repo_root()
    config_file = args.config if args.config else repo_root / "config" / "benchmark-runtime-profiles.json"

    try:
        data = load_profiles(config_file)
        validate_config(data)
    except Exception as e:
        sys.stderr.write(f"Error loading profile configuration: {e}\n")
        sys.exit(1)

    profiles = data.get("profiles", {})
    default_profile = data.get("defaultProfile", "J17-G1")

    if args.validate:
        print(f"Configuration valid: {config_file} ({len(profiles)} profiles loaded)")
        sys.exit(0)

    if args.default:
        print(default_profile)
        sys.exit(0)

    if args.list:
        print(f"{'PROFILE ID':<14} | {'JDK':<4} | {'GC':<6} | {'COH':<5} | {'VTHREADS':<8} | {'AOT':<5} | {'DESCRIPTION'}")
        print("-" * 90)
        for pid, p in profiles.items():
            jdk = str(p.get("javaVersion", "?"))
            gc = str(p.get("garbageCollector", "?"))
            coh = "Yes" if p.get("compactObjectHeaders", False) else "No"
            vt = "Yes" if p.get("virtualThreads", False) else "No"
            aot = "Yes" if p.get("aot", False) else "No"
            desc = p.get("description", "")
            marker = "*" if pid == default_profile else " "
            print(f"{pid:<13}{marker}| {jdk:<4} | {gc:<6} | {coh:<5} | {vt:<8} | {aot:<5} | {desc}")
        sys.exit(0)

    if args.get_flags:
        pid = args.get_flags
        if pid not in profiles:
            sys.stderr.write(f"Unknown profile: {pid}\n")
            sys.exit(2)
        print(" ".join(profiles[pid].get("jvmFlags", [])))
        sys.exit(0)

    if args.get_version:
        pid = args.get_version
        if pid not in profiles:
            sys.stderr.write(f"Unknown profile: {pid}\n")
            sys.exit(2)
        print(profiles[pid].get("javaVersion", 17))
        sys.exit(0)

    if args.get_property:
        pid, key = args.get_property
        if pid not in profiles:
            sys.stderr.write(f"Unknown profile: {pid}\n")
            sys.exit(2)
        val = profiles[pid].get(key)
        if val is None:
            sys.stderr.write(f"Key '{key}' not found in profile '{pid}'\n")
            sys.exit(3)
        if isinstance(val, bool):
            print("true" if val else "false")
        elif isinstance(val, list):
            print(" ".join(str(v) for v in val))
        else:
            print(val)
        sys.exit(0)

    if args.get_json:
        pid = args.get_json
        if pid not in profiles:
            sys.stderr.write(f"Unknown profile: {pid}\n")
            sys.exit(2)
        print(json.dumps(profiles[pid], indent=2))
        sys.exit(0)

    parser.print_help()


if __name__ == "__main__":
    main()
