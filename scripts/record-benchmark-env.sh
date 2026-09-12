#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

OUTPUT_FILE="benchmark-env.json"
PROFILE_ID=""
JAVA_BIN="${JAVA_CMD:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output|-o)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    --profile|-p)
      PROFILE_ID="$2"
      shift 2
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [OUTPUT_FILE] [--output <FILE>] [--profile <PROFILE>] [--java <JAVA_BIN>]"
      echo "Example: $0 benchmark-env.json"
      echo "         $0 --profile J21-G1 --output build/performance/env.json"
      exit 0
      ;;
    *)
      if [[ ! "$1" =~ ^- ]]; then
        OUTPUT_FILE="$1"
        shift
      else
        echo "Unknown argument: $1" >&2
        exit 1
      fi
      ;;
  esac
done

GIT_SHA=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
GIT_SHORT_SHA=$(git rev-parse --short=7 HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
  GIT_DIRTY=true
else
  GIT_DIRTY=false
fi

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

OS_NAME=$(uname -s 2>/dev/null || echo "unknown")
OS_RELEASE=$(uname -r 2>/dev/null || echo "unknown")
OS_ARCH=$(uname -m 2>/dev/null || echo "unknown")

if [[ -f /etc/os-release ]]; then
  OS_DISTRO=$(grep -E '^PRETTY_NAME=' /etc/os-release | cut -d= -f2- | tr -d '"' || echo "$OS_NAME")
else
  OS_DISTRO="$OS_NAME"
fi

if command -v nproc >/dev/null 2>&1; then
  CPU_CORES=$(nproc)
elif command -v sysctl >/dev/null 2>&1; then
  CPU_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo "0")
else
  CPU_CORES="0"
fi

if [[ -f /proc/cpuinfo ]]; then
  CPU_MODEL=$(grep -m1 "model name" /proc/cpuinfo | cut -d: -f2- | sed -e 's/^[[:space:]]*//' || echo "unknown")
elif command -v sysctl >/dev/null 2>&1; then
  CPU_MODEL=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "unknown")
else
  CPU_MODEL="unknown"
fi

TOTAL_RAM_BYTES=0
if [[ -f /proc/meminfo ]]; then
  TOTAL_RAM_BYTES=$(awk '/MemTotal:/ {print $2 * 1024}' /proc/meminfo 2>/dev/null || echo "0")
elif command -v sysctl >/dev/null 2>&1; then
  TOTAL_RAM_BYTES=$(sysctl -n hw.memsize 2>/dev/null || echo "0")
fi

if [[ -z "$JAVA_BIN" ]]; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
  else
    JAVA_BIN="unknown"
  fi
fi

if [[ "$JAVA_BIN" != "unknown" && -x "$JAVA_BIN" ]]; then
  JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | awk -F '"' '/version/ {print $2}')
  DETECTED_MAJOR=$(echo "$JAVA_VERSION" | sed -E 's/^1\.//' | sed -E 's/^([0-9]+).*/\1/')
  JVM_FULL_VERSION=$("$JAVA_BIN" -version 2>&1 | head -n 3 | grep -E "VM|Runtime" | head -n 1 | sed -e 's/^[[:space:]]*//')
  PROPS_OUTPUT=$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 || true)
  JVM_VENDOR=$(echo "$PROPS_OUTPUT" | awk -F '=' '/java.vendor =/ {print $2}' | sed -e 's/^[[:space:]]*//' | head -n 1)
  if [[ -z "$JVM_VENDOR" ]]; then JVM_VENDOR="unknown"; fi
  JAVA_PATH=$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")
else
  JAVA_VERSION="unknown"
  DETECTED_MAJOR=0
  JVM_FULL_VERSION="unknown"
  JVM_VENDOR="unknown"
  JAVA_PATH="unknown"
fi

# Profile-based properties
if [[ -n "$PROFILE_ID" && -f "$REPO_ROOT/scripts/perf/runtime_profiles.py" ]]; then
  JVM_FLAGS=$(python3 "$REPO_ROOT/scripts/perf/runtime_profiles.py" --get-flags "$PROFILE_ID" 2>/dev/null || echo "-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC")
  COLLECTOR=$(python3 "$REPO_ROOT/scripts/perf/runtime_profiles.py" --get-property "$PROFILE_ID" garbageCollector 2>/dev/null || echo "G1")
  COH=$(python3 "$REPO_ROOT/scripts/perf/runtime_profiles.py" --get-property "$PROFILE_ID" compactObjectHeaders 2>/dev/null || echo "false")
  VTHREADS=$(python3 "$REPO_ROOT/scripts/perf/runtime_profiles.py" --get-property "$PROFILE_ID" virtualThreads 2>/dev/null || echo "false")
  AOT=$(python3 "$REPO_ROOT/scripts/perf/runtime_profiles.py" --get-property "$PROFILE_ID" aot 2>/dev/null || echo "false")
else
  PROFILE_ID="custom"
  JVM_FLAGS="-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC"
  COLLECTOR="G1"
  COH="false"
  if [[ "$DETECTED_MAJOR" -ge 21 ]]; then VTHREADS="true"; else VTHREADS="false"; fi
  AOT="false"
fi

# Determine JMH version from libs.versions.toml if available
JMH_VERSION="1.37"
if [[ -f "$REPO_ROOT/gradle/libs.versions.toml" ]]; then
  JMH_VERSION=$(grep -E '^jmh = ' "$REPO_ROOT/gradle/libs.versions.toml" | cut -d= -f2- | tr -d ' "' || echo "1.37")
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

python3 - "$OUTPUT_FILE" "$TIMESTAMP" "$GIT_SHA" "$GIT_SHORT_SHA" "$GIT_BRANCH" \
  "$GIT_DIRTY" "$OS_NAME" "$OS_RELEASE" "$OS_DISTRO" "$OS_ARCH" "$CPU_MODEL" \
  "$CPU_CORES" "$TOTAL_RAM_BYTES" "$PROFILE_ID" "$JAVA_VERSION" "$DETECTED_MAJOR" \
  "$JVM_FULL_VERSION" "$JVM_VENDOR" "$JAVA_PATH" "$COLLECTOR" "$COH" "$VTHREADS" \
  "$AOT" "$JVM_FLAGS" "$JMH_VERSION" <<'PY'
import json
import sys

(output, timestamp, commit, short_commit, branch, dirty, os_name, os_release, distro,
 arch, cpu, cores, memory, profile, version, major, vm, vendor, java_path, collector,
 coh, virtual_threads, aot, flags, jmh_version) = sys.argv[1:]
document = {
    "timestamp": timestamp,
    "git": {"commit": commit, "shortCommit": short_commit, "branch": branch,
            "dirty": dirty == "true"},
    "environment": {"os": os_name, "kernel": os_release, "osDistro": distro,
                    "arch": arch, "cpuModel": cpu, "processorCount": int(cores),
                    "totalMemoryBytes": int(float(memory))},
    "java": {"profile": profile, "version": version, "majorVersion": int(major),
             "vm": vm, "vendor": vendor, "executable": java_path, "collector": collector,
             "compactObjectHeaders": coh == "true",
             "virtualThreadsSupported": virtual_threads == "true", "aotSupported": aot == "true",
             "standardJvmFlags": flags.split()},
    "jmh": {"version": jmh_version, "forks": None, "threads": None,
            "warmupIterations": None, "measurementIterations": None},
}
with open(output, "w", encoding="utf-8") as handle:
    json.dump(document, handle, indent=2)
    handle.write("\n")
PY

echo "Benchmark environment recorded to: $OUTPUT_FILE"
