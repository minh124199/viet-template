#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

MODE="full"
JAVA_BIN="${JAVA_CMD:-}"
FILTER="io.github.minh124199.viettemplate.benchmarks.stress.*"
BUILD_TOOL="gradle"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode|-m)
      MODE="$2"
      shift 2
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    --filter|-f)
      FILTER="$2"
      shift 2
      ;;
    --maven)
      BUILD_TOOL="maven"
      shift
      ;;
    --gradle)
      BUILD_TOOL="gradle"
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --mode <smoke|full>     Execution mode (default: full)"
      echo "  --java <PATH>           Path to java executable (e.g. Java 21 or Java 25)"
      echo "  --filter <PATTERN>      Class filter (default: io.github.minh124199.viettemplate.benchmarks.stress.*)"
      echo "  --maven                 Run via Maven instead of Gradle"
      echo "  --gradle                Run via Gradle (default)"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ "$MODE" != "smoke" && "$MODE" != "full" ]]; then
  echo "Error: --mode must be 'smoke' or 'full'" >&2
  exit 2
fi

# If JAVA_BIN provided, export JAVA_HOME
if [[ -n "$JAVA_BIN" ]]; then
  RESOLVED_BIN=$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")
  EXPORTED_HOME="$(dirname "$(dirname "$RESOLVED_BIN")")"
  export JAVA_HOME="$EXPORTED_HOME"
  export PATH="$EXPORTED_HOME/bin:$PATH"
fi

echo "============================================================"
echo "Virtual-Thread & Concurrency Stress Test Suite"
echo "============================================================"
echo "  Mode       : $MODE"
echo "  Build Tool : $BUILD_TOOL"
echo "  Filter     : $FILTER"
if [[ -n "${JAVA_HOME:-}" ]]; then
  echo "  JAVA_HOME  : $JAVA_HOME"
fi
echo "  Active Java: $(command -v java)"
java -version 2>&1 | head -n 2 | sed -e 's/^/  /'
echo "============================================================"

ACTIVE_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | sed -E 's/^1\.//' | sed -E 's/^([0-9]+).*/\1/')
if [[ ! "$ACTIVE_VERSION" =~ ^[0-9]+$ || "$ACTIVE_VERSION" -lt 21 ]]; then
  echo "Error: virtual-thread stress requires a Java 21+ runtime; found Java $ACTIVE_VERSION." >&2
  exit 1
fi

cd "$REPO_ROOT"

if [[ "$BUILD_TOOL" == "gradle" ]]; then
  GRADLE_ARGS=(:viet-template-benchmarks:test --tests "$FILTER" --no-daemon -Dspotless.check.skip=true)
  if [[ "$MODE" == "smoke" ]]; then
    GRADLE_ARGS+=("-Dviet.stress.smoke=true")
  fi
  ./gradlew "${GRADLE_ARGS[@]}"
else
  MAVEN_ARGS=(test -pl viet-template-benchmarks -am -Dtest="$FILTER" -B -Dspotless.check.skip=true)
  if [[ "$MODE" == "smoke" ]]; then
    MAVEN_ARGS+=("-Dviet.stress.smoke=true")
  fi
  ./mvnw "${MAVEN_ARGS[@]}"
fi

echo "Virtual thread stress test suite passed."
