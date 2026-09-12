#!/usr/bin/env bash
set -euo pipefail

OUTPUT_FILE="${1:-benchmark-env.json}"

GIT_SHA=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

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
  CPU_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo "unknown")
else
  CPU_CORES="unknown"
fi

if [[ -f /proc/cpuinfo ]]; then
  CPU_MODEL=$(grep -m1 "model name" /proc/cpuinfo | cut -d: -f2- | sed -e 's/^[[:space:]]*//' || echo "unknown")
elif command -v sysctl >/dev/null 2>&1; then
  CPU_MODEL=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "unknown")
else
  CPU_MODEL="unknown"
fi

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
  JVM_FULL_VERSION=$(java -version 2>&1 | head -n 2 | tail -n 1 | sed -e 's/^[[:space:]]*//')
  JVM_VENDOR=$(java -XshowSettings:properties -version 2>&1 | awk -F '=' '/java.vendor =/ {print $2}' | sed -e 's/^[[:space:]]*//' || echo "unknown")
else
  JAVA_VERSION="unknown"
  JVM_FULL_VERSION="unknown"
  JVM_VENDOR="unknown"
fi

JVM_FLAGS="-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC"

cat <<EOF > "$OUTPUT_FILE"
{
  "timestamp": "$TIMESTAMP",
  "git": {
    "commit": "$GIT_SHA",
    "branch": "$GIT_BRANCH"
  },
  "environment": {
    "os": "$OS_NAME",
    "osRelease": "$OS_RELEASE",
    "osDistro": "$OS_DISTRO",
    "arch": "$OS_ARCH",
    "cpuModel": "$CPU_MODEL",
    "processorCount": "$CPU_CORES"
  },
  "java": {
    "version": "$JAVA_VERSION",
    "vm": "$JVM_FULL_VERSION",
    "vendor": "$JVM_VENDOR",
    "standardJvmFlags": "$JVM_FLAGS"
  }
}
EOF

echo "Benchmark environment recorded to: $OUTPUT_FILE"
cat "$OUTPUT_FILE"
