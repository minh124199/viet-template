#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

PROFILE="J17-G1"
ITERATIONS=""
MODE="smoke"
TIER="IR"
OUTPUT_FILE=""
MARKDOWN=false
JAVA_BIN="${JAVA_CMD:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile|-p)
      PROFILE="$2"
      shift 2
      ;;
    --iterations|-n)
      ITERATIONS="$2"
      shift 2
      ;;
    --mode)
      MODE="$2"
      shift 2
      ;;
    --tier|-t)
      TIER="$2"
      shift 2
      ;;
    --output|-o)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    --markdown|-m)
      MARKDOWN=true
      shift
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --profile <ID>          Runtime profile (default: J17-G1)"
      echo "  --mode <smoke|measurement> Smoke: 2-3 runs; measurement: >=20 runs"
      echo "  --iterations <N>        Override launches (defaults: smoke=3, measurement=30)"
      echo "  --tier <IR|AOT_BYTECODE> Execution tier (default: IR)"
      echo "  --output <FILE>         Save summary JSON to file"
      echo "  --markdown              Print Markdown table output"
      echo "  --java <PATH>           Path to java executable"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

REQUIRED_VERSION=$(python3 "$SCRIPT_DIR/runtime_profiles.py" --get-version "$PROFILE")
JVM_FLAGS=$(python3 "$SCRIPT_DIR/runtime_profiles.py" --get-flags "$PROFILE")

if [[ -z "$JAVA_BIN" ]]; then
  CANDIDATES=(
    "${JAVA_HOME:-}/bin/java"
    "${HOME}/opt/jdk${REQUIRED_VERSION}-pkg/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/java"
    "${HOME}/opt/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/java"
    "/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/java"
    "/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk-amd64/bin/java"
    "/usr/lib/jvm/temurin-${REQUIRED_VERSION}-jdk/bin/java"
    "$(command -v java || echo "")"
  )
  for cand in "${CANDIDATES[@]}"; do
    if [[ -n "$cand" && -x "$cand" ]]; then
      VER=$("$cand" -version 2>&1 | awk -F '"' '/version/ {print $2}' | sed -E 's/^1\.//' | sed -E 's/^([0-9]+).*/\1/')
      if [[ "$VER" == "$REQUIRED_VERSION" ]]; then
        JAVA_BIN="$cand"
        break
      fi
    fi
  done
fi

if [[ -z "$JAVA_BIN" ]]; then
  echo "Error: Java $REQUIRED_VERSION binary not found. Specify with --java <PATH>" >&2
  exit 1
fi

"$SCRIPT_DIR/check-java-runtime.sh" "$PROFILE" --java "$JAVA_BIN"

# Ensure benchmarks.jar exists
BENCHMARKS_JAR="$REPO_ROOT/viet-template-benchmarks/build/libs/benchmarks.jar"
if [[ ! -f "$BENCHMARKS_JAR" ]]; then
  echo "Building benchmarks.jar..."
  (cd "$REPO_ROOT" && ./gradlew :viet-template-benchmarks:benchmarkJar --no-daemon)
fi

PY_ARGS=(
  python3 "$SCRIPT_DIR/measure-startup.py"
  --java "$JAVA_BIN"
  --jvm-flags "$JVM_FLAGS"
  --classpath "$BENCHMARKS_JAR"
  --mode "$MODE"
  --tier "$TIER"
)

if [[ -n "$ITERATIONS" ]]; then
  PY_ARGS+=(--iterations "$ITERATIONS")
fi

if [[ -n "$OUTPUT_FILE" ]]; then
  PY_ARGS+=(--output-json "$OUTPUT_FILE")
fi

if [[ "$MARKDOWN" == "true" ]]; then
  PY_ARGS+=(--markdown)
fi

"${PY_ARGS[@]}"
