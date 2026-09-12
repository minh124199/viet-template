#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

PROFILE="J17-G1"
BENCHMARK_PATTERN=".*"
FORKS=""
THREADS=""
WARMUP_ITERS=""
MEASURE_ITERS=""
WARMUP_TIME=""
MEASURE_TIME=""
PROFILER=""
OUTPUT_DIR=""
BENCHMARKS_JAR=""
DRY_RUN=false
JAVA_BIN="${JAVA_CMD:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile|-p)
      PROFILE="$2"
      shift 2
      ;;
    --benchmark|-b)
      BENCHMARK_PATTERN="$2"
      shift 2
      ;;
    --forks|-f)
      FORKS="$2"
      shift 2
      ;;
    --threads|-t)
      THREADS="$2"
      shift 2
      ;;
    --warmup|-wi)
      WARMUP_ITERS="$2"
      shift 2
      ;;
    --iterations|-i)
      MEASURE_ITERS="$2"
      shift 2
      ;;
    --warmup-time|-w)
      WARMUP_TIME="$2"
      shift 2
      ;;
    --measurement-time|-r)
      MEASURE_TIME="$2"
      shift 2
      ;;
    --prof)
      PROFILER="$2"
      shift 2
      ;;
    --output-dir|-o)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --benchmarks-jar|-j)
      BENCHMARKS_JAR="$2"
      shift 2
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --profile <ID>            Runtime profile (default: J17-G1)"
      echo "  --benchmark <PATTERN>     Benchmark regex or class (default: .*)"
      echo "  --forks <N>               Number of forks (-f)"
      echo "  --threads <N>             Worker threads (-t)"
      echo "  --warmup <N>              Warmup iterations (-wi)"
      echo "  --iterations <N>          Measurement iterations (-i)"
      echo "  --warmup-time <TIME>      Warmup iteration time (e.g. 1s)"
      echo "  --measurement-time <TIME> Measurement iteration time (e.g. 1s)"
      echo "  --prof <PROFILER>         JMH profiler (e.g. gc, jfr)"
      echo "  --output-dir <DIR>        Destination directory"
      echo "  --benchmarks-jar <JAR>    Path to benchmarks.jar"
      echo "  --java <BIN>              Override java executable path"
      echo "  --dry-run                 Print planned commands without running"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

# Query target Java version and JVM flags for profile
REQUIRED_VERSION=$(python3 "$SCRIPT_DIR/runtime_profiles.py" --get-version "$PROFILE")
read -r -a JVM_FLAGS <<< "$(python3 "$SCRIPT_DIR/runtime_profiles.py" --get-flags "$PROFILE")"

# Resolve java binary if not explicitly given
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
  echo "Error: Could not locate a Java $REQUIRED_VERSION binary for profile $PROFILE. Specify --java <path>" >&2
  exit 1
fi

# Validate runtime
"$SCRIPT_DIR/check-java-runtime.sh" "$PROFILE" --java "$JAVA_BIN"

# Locate or package benchmarks.jar
if [[ -z "$BENCHMARKS_JAR" ]]; then
  BENCHMARKS_JAR="$REPO_ROOT/viet-template-benchmarks/build/libs/benchmarks.jar"
  if [[ ! -f "$BENCHMARKS_JAR" ]]; then
    M_JAR="$REPO_ROOT/viet-template-benchmarks/target/benchmarks.jar"
    if [[ -f "$M_JAR" ]]; then
      BENCHMARKS_JAR="$M_JAR"
    elif [[ "$DRY_RUN" == "false" ]]; then
      echo "Packaging benchmarks.jar via Gradle..."
      (cd "$REPO_ROOT" && ./gradlew :viet-template-benchmarks:benchmarkJar --no-daemon)
    fi
  fi
fi

if [[ "$DRY_RUN" == "false" && ! -f "$BENCHMARKS_JAR" ]]; then
  echo "Error: benchmarks.jar not found at '$BENCHMARKS_JAR'" >&2
  exit 1
fi

# Standardized layout: build/performance/<timestamp>-<sha>/<profile>/
GIT_SHORT_SHA=$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD 2>/dev/null || echo "unknown")
TIMESTAMP_DIR=$(date -u +"%Y%m%d-%H%M%SZ")

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$REPO_ROOT/build/performance/${TIMESTAMP_DIR}-${GIT_SHORT_SHA}/${PROFILE}"
fi

mkdir -p "$OUTPUT_DIR"

ENV_JSON="$OUTPUT_DIR/environment.json"
RESULT_JSON="$OUTPUT_DIR/result.json"
STDOUT_LOG="$OUTPUT_DIR/stdout.log"

# Build JMH command arguments
JMH_CMD=("$JAVA_BIN" "${JVM_FLAGS[@]}" -jar "$BENCHMARKS_JAR" "$BENCHMARK_PATTERN" -rf json -rff "$RESULT_JSON")

if [[ -n "$FORKS" ]]; then JMH_CMD+=(-f "$FORKS"); fi
if [[ -n "$THREADS" ]]; then JMH_CMD+=(-t "$THREADS"); fi
if [[ -n "$WARMUP_ITERS" ]]; then JMH_CMD+=(-wi "$WARMUP_ITERS"); fi
if [[ -n "$MEASURE_ITERS" ]]; then JMH_CMD+=(-i "$MEASURE_ITERS"); fi
if [[ -n "$WARMUP_TIME" ]]; then JMH_CMD+=(-w "$WARMUP_TIME"); fi
if [[ -n "$MEASURE_TIME" ]]; then JMH_CMD+=(-r "$MEASURE_TIME"); fi
if [[ -n "$PROFILER" ]]; then JMH_CMD+=(-prof "$PROFILER"); fi

echo "============================================================"
echo "Performance Benchmark Runner"
echo "============================================================"
echo "  Profile      : $PROFILE (Java $REQUIRED_VERSION)"
echo "  Benchmark    : $BENCHMARK_PATTERN"
echo "  Output Dir   : $OUTPUT_DIR"
echo "  Benchmark JAR: $BENCHMARKS_JAR"
echo "  Command      : ${JMH_CMD[*]}"
echo "============================================================"

# Record environment metadata
"$REPO_ROOT/scripts/record-benchmark-env.sh" --output "$ENV_JSON" --profile "$PROFILE" --java "$JAVA_BIN"
python3 - "$ENV_JSON" "$BENCHMARK_PATTERN" "$FORKS" "$THREADS" "$WARMUP_ITERS" "$MEASURE_ITERS" "$WARMUP_TIME" "$MEASURE_TIME" <<'PY'
import json
import sys

path, pattern, forks, threads, warmup, measurement, warmup_time, measurement_time = sys.argv[1:]
with open(path, encoding="utf-8") as handle:
    data = json.load(handle)
data["jmh"].update({
    "benchmarkPattern": pattern,
    "forks": int(forks) if forks else None,
    "threads": int(threads) if threads else None,
    "warmupIterations": int(warmup) if warmup else None,
    "measurementIterations": int(measurement) if measurement else None,
    "warmupTime": warmup_time or None,
    "measurementTime": measurement_time or None,
})
with open(path, "w", encoding="utf-8") as handle:
    json.dump(data, handle, indent=2)
    handle.write("\n")
PY

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[dry-run] Planned JMH execution skipped."
  exit 0
fi

echo "Executing JMH benchmarks (logging to $STDOUT_LOG)..."
set +e
"${JMH_CMD[@]}" 2>&1 | tee "$STDOUT_LOG"
EXIT_CODE=${PIPESTATUS[0]}
set -e

if [[ $EXIT_CODE -eq 0 ]]; then
  echo "Benchmark execution completed successfully."
  echo "Artifacts written to:"
  echo "  - Environment: $ENV_JSON"
  echo "  - Result JSON: $RESULT_JSON"
  echo "  - Stdout Log : $STDOUT_LOG"
else
  echo "Benchmark execution failed with exit code $EXIT_CODE" >&2
fi

exit $EXIT_CODE
