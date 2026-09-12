#!/usr/bin/env python3
"""Poll one Sonatype Central deployment and emit normalized machine-readable state.

Exit codes: 0=PUBLISHED, 1=FAILED, 2=non-terminal/timeout, 3=API/auth/parse error.
Credentials are read only from MAVEN_CENTRAL_USERNAME and
MAVEN_CENTRAL_PASSWORD; they are never printed.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API_URL = "https://central.sonatype.com/api/v1/publisher/status"
TERMINAL_STATES = {"PUBLISHED", "FAILED"}
NON_TERMINAL_STATES = {"PENDING", "VALIDATING", "VALIDATED", "PUBLISHING"}
KNOWN_STATES = TERMINAL_STATES | NON_TERMINAL_STATES


class CentralStatusError(RuntimeError):
    """The Central response could not be safely interpreted."""


def query_status(deployment_id: str, username: str, password: str, request_timeout: float) -> str:
    token = base64.b64encode(f"{username}:{password}".encode()).decode("ascii")
    url = f"{API_URL}?{urllib.parse.urlencode({'id': deployment_id})}"
    request = urllib.request.Request(
        url,
        method="POST",
        headers={"Authorization": f"UserToken {token}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=request_timeout) as response:
            status = getattr(response, "status", 200)
            body = response.read()
    except urllib.error.HTTPError as exc:
        raise CentralStatusError(f"Central API returned HTTP {exc.code}") from exc
    except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
        raise CentralStatusError(f"Central API request failed: {exc.reason if hasattr(exc, 'reason') else exc}") from exc

    if status != 200:
        raise CentralStatusError(f"Central API returned HTTP {status}")
    try:
        payload = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise CentralStatusError("Central API returned malformed JSON") from exc
    if not isinstance(payload, dict) or set(payload).isdisjoint({"deploymentState"}):
        raise CentralStatusError("Central API response is missing deploymentState")
    state = payload["deploymentState"]
    if not isinstance(state, str) or state.upper() not in KNOWN_STATES:
        raise CentralStatusError(f"Central API returned unknown deploymentState: {state!r}")
    return state.upper()


def emit(state: str, deployment_id: str, output_file: Path | None = None) -> None:
    lines = (f"state={state}", f"deployment_id={deployment_id}")
    print("\n".join(lines), flush=True)
    if output_file:
        with output_file.open("a", encoding="utf-8") as stream:
            stream.write("\n".join(lines) + "\n")


def monitor(
    deployment_id: str,
    username: str,
    password: str,
    timeout: float,
    interval: float,
    request_timeout: float,
    one_shot: bool,
    output_file: Path | None,
) -> int:
    deadline = time.monotonic() + timeout
    last_state = "UNKNOWN"
    while True:
        try:
            last_state = query_status(deployment_id, username, password, request_timeout)
        except CentralStatusError as exc:
            print(f"CENTRAL_API_ERROR: {exc}", file=sys.stderr)
            emit("API_ERROR", deployment_id, output_file)
            return 3

        emit(last_state, deployment_id, output_file if last_state in TERMINAL_STATES or one_shot else None)
        if last_state == "PUBLISHED":
            return 0
        if last_state == "FAILED":
            return 1
        if one_shot:
            return 2

        remaining = deadline - time.monotonic()
        if remaining <= 0:
            print(
                f"PUBLICATION_TIMEOUT: deployment {deployment_id} remained {last_state} after {timeout:g} seconds",
                file=sys.stderr,
            )
            emit(last_state, deployment_id, output_file)
            return 2
        time.sleep(min(interval, remaining))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("deployment_id")
    parser.add_argument("--timeout", type=float, default=7200, help="total observation timeout in seconds (default: 7200)")
    parser.add_argument("--poll-interval", type=float, default=30, help="seconds between status requests (default: 30)")
    parser.add_argument("--request-timeout", type=float, default=30, help="per-request timeout in seconds (default: 30)")
    parser.add_argument("--one-shot", action="store_true")
    parser.add_argument("--github-output", type=Path, help="append final state and deployment_id to this GitHub output file")
    args = parser.parse_args()

    if args.timeout < 0 or args.poll_interval <= 0 or args.request_timeout <= 0:
        parser.error("timeouts must be non-negative and intervals must be positive")
    username = os.environ.get("MAVEN_CENTRAL_USERNAME", "")
    password = os.environ.get("MAVEN_CENTRAL_PASSWORD", "")
    if not username or not password:
        print("CENTRAL_API_ERROR: MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD are required", file=sys.stderr)
        emit("API_ERROR", args.deployment_id, args.github_output)
        return 3
    return monitor(
        args.deployment_id,
        username,
        password,
        args.timeout,
        args.poll_interval,
        args.request_timeout,
        args.one_shot,
        args.github_output,
    )


if __name__ == "__main__":
    raise SystemExit(main())
