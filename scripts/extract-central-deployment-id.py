#!/usr/bin/env python3
"""Strictly extract the single Sonatype deployment ID from Maven publisher output."""

import argparse
import re
import sys
from pathlib import Path

PATTERN = re.compile(r"\bdeploymentId:\s*([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\b")


def extract(text: str) -> str:
    matches = PATTERN.findall(text)
    unique = list(dict.fromkeys(value.lower() for value in matches))
    if len(unique) != 1:
        raise ValueError(f"expected exactly one unique deployment ID, found {len(unique)}")
    return unique[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()
    try:
        deployment_id = extract(args.log.read_text(encoding="utf-8", errors="replace"))
    except (OSError, ValueError) as exc:
        print(f"DEPLOYMENT_ID_ERROR: {exc}", file=sys.stderr)
        return 1
    print(f"deployment_id={deployment_id}")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as stream:
            stream.write(f"deployment_id={deployment_id}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
