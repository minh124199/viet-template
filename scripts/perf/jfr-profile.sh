#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

OUTPUT_FILE=""
TARGET_CLASS=""
TARGET_ARGS=""
BENCHMARK_PATTERN=""
JAVA_BIN="${JAVA_CMD:-}"
SUMMARY=false
SUMMARY_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output|-o)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    --class|-c)
      TARGET_CLASS="$2"
      shift 2
      ;;
    --args|-a)
      TARGET_ARGS="$2"
      shift 2
      ;;
    --benchmark|-b)
      BENCHMARK_PATTERN="$2"
      shift 2
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    --summary|-s)
      SUMMARY=true
      shift
      ;;
    --summary-dir)
      SUMMARY=true
      SUMMARY_DIR="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --output <FILE.jfr>     Path to output JFR recording"
      echo "  --class <CLASS>         Target Java main class to execute and profile"
      echo "  --args <ARGS>           Arguments to pass to target class"
      echo "  --benchmark <PATTERN>   Run JMH benchmark with JFR enabled"
      echo "  --java <PATH>           Path to JDK 25 java executable"
      echo "  --summary               Automatically generate JFR summary and views after profiling"
      echo "  --summary-dir <DIR>     Directory to store JFR view reports"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# Locate JDK 25 java binary
REQUIRED_VERSION="25"
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
  echo "Error: JDK 25 java binary could not be found. Specify with --java <PATH>" >&2
  exit 1
fi

"$SCRIPT_DIR/check-java-runtime.sh" 25 --java "$JAVA_BIN"

TIMESTAMP=$(date -u +"%Y%m%d-%H%M%SZ")
if [[ -z "$OUTPUT_FILE" ]]; then
  OUTPUT_FILE="$REPO_ROOT/build/performance/jfr/profile-${TIMESTAMP}.jfr"
fi
mkdir -p "$(dirname "$OUTPUT_FILE")"

JFR_FLAGS=(
  "-XX:StartFlightRecording=filename=${OUTPUT_FILE},dumponexit=true,settings=profile"
)

# If benchmark pattern was provided, execute JMH with JFR
if [[ -n "$BENCHMARK_PATTERN" ]]; then
  echo "Executing JMH benchmark with JFR profiling..."
  "$SCRIPT_DIR/run-benchmarks.sh" \
    --profile J25-G1 \
    --benchmark "$BENCHMARK_PATTERN" \
    --prof "jfr:dir=$(dirname "$OUTPUT_FILE")" \
    --java "$JAVA_BIN"
else
  # Default to running PlatformThreadComparisonHarness if no class specified
  if [[ -z "$TARGET_CLASS" ]]; then
    TARGET_CLASS="io.github.minh124199.viettemplate.benchmarks.stress.PlatformThreadComparisonHarness"
    TARGET_ARGS="1000"
  fi

  # Build classpath
  BENCHMARKS_JAR="$REPO_ROOT/viet-template-benchmarks/build/libs/benchmarks.jar"
  if [[ ! -f "$BENCHMARKS_JAR" ]]; then
    (cd "$REPO_ROOT" && ./gradlew :viet-template-benchmarks:benchmarkJar --no-daemon)
  fi

  CP="$REPO_ROOT/viet-template-benchmarks/build/classes/java/test:$BENCHMARKS_JAR"
  TARGET_ARGS_ARRAY=()
  if [[ -n "$TARGET_ARGS" ]]; then
    read -r -a TARGET_ARGS_ARRAY <<< "$TARGET_ARGS"
  fi
  echo "Executing target class '$TARGET_CLASS' with JFR..."
  "$JAVA_BIN" "${JFR_FLAGS[@]}" -cp "$CP" "$TARGET_CLASS" "${TARGET_ARGS_ARRAY[@]}"
fi

echo "JFR profiling recording created at: $OUTPUT_FILE"

if [[ "$SUMMARY" == "true" ]]; then
  if [[ -z "$SUMMARY_DIR" ]]; then
    SUMMARY_DIR="$(dirname "$OUTPUT_FILE")/views-${TIMESTAMP}"
  fi
  echo "Generating JFR summary and views in: $SUMMARY_DIR"
  "$SCRIPT_DIR/jfr-summary.sh" "$OUTPUT_FILE" --output-dir "$SUMMARY_DIR"
fi
