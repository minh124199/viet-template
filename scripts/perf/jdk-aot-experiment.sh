#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ITERATIONS=30
TIER="IR"
JAVA_BIN="${JAVA_CMD:-}"
OUTPUT_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --iterations|-n)
      ITERATIONS="$2"
      shift 2
      ;;
    --tier|-t)
      TIER="$2"
      shift 2
      ;;
    --output-dir|-o)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  --iterations <N>    Launches per condition (minimum 20, default 30)"
      echo "  --tier <TIER>       Execution tier (IR or AOT_BYTECODE, default: IR)"
      echo "  --output-dir <DIR>  Output directory for AOT artifacts and reports"
      echo "  --java <PATH>       Path to JDK 25 java binary"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! "$ITERATIONS" =~ ^[0-9]+$ || "$ITERATIONS" -lt 20 ]]; then
  echo "Error: AOT measurement requires at least 20 launches per condition." >&2
  exit 2
fi

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
  echo "Error: JDK 25 java binary not found. Specify with --java <PATH>" >&2
  exit 1
fi

"$SCRIPT_DIR/check-java-runtime.sh" 25 --java "$JAVA_BIN"

# Locate benchmarks.jar
BENCHMARKS_JAR="$REPO_ROOT/viet-template-benchmarks/build/libs/benchmarks.jar"
if [[ ! -f "$BENCHMARKS_JAR" ]]; then
  echo "Packaging benchmarks.jar..."
  (cd "$REPO_ROOT" && ./gradlew :viet-template-benchmarks:benchmarkJar --no-daemon)
fi

TIMESTAMP=$(date -u +"%Y%m%d-%H%M%SZ")
if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$REPO_ROOT/build/performance/aot/${TIMESTAMP}"
fi
mkdir -p "$OUTPUT_DIR"

AOT_CONFIG="$OUTPUT_DIR/template-startup.aotconfig"
AOT_CACHE="$OUTPUT_DIR/template-startup.aot"
NORMAL_JSON="$OUTPUT_DIR/startup-normal.json"
AOT_JSON="$OUTPUT_DIR/startup-aot.json"
REPORT_MD="$OUTPUT_DIR/aot-comparison-report.md"

echo "============================================================"
echo "Java 25 Ahead-of-Time (AOT) Cache Experiment (JEP 483 / 514)"
echo "============================================================"
echo "  JDK 25 Java : $JAVA_BIN"
echo "  Artifact Jar: $BENCHMARKS_JAR"
echo "  Output Dir  : $OUTPUT_DIR"
echo "  Iterations  : $ITERATIONS"
echo "  Tier        : $TIER"
echo "============================================================"

# Step 1: Record AOT Configuration
echo ""
echo "[Step 1/4] Recording AOT Configuration with -XX:AOTMode=record..."
"$JAVA_BIN" -XX:AOTMode=record -XX:AOTConfiguration="$AOT_CONFIG" \
  -cp "$BENCHMARKS_JAR" \
  io.github.minh124199.viettemplate.benchmarks.startup.StartupBenchmarkEntrypoint \
  --tier "$TIER" > /dev/null

echo "AOT configuration recorded to: $AOT_CONFIG ($(du -h "$AOT_CONFIG" | cut -f1))"

# Step 2: Create AOT Cache
echo ""
echo "[Step 2/4] Creating AOT Cache Archive with -XX:AOTMode=create..."
"$JAVA_BIN" -XX:AOTMode=create -XX:AOTConfiguration="$AOT_CONFIG" -XX:AOTCache="$AOT_CACHE" \
  -cp "$BENCHMARKS_JAR" > /dev/null

echo "AOT cache archive created: $AOT_CACHE ($(du -h "$AOT_CACHE" | cut -f1))"

# Step 3: Measure both conditions in alternating order
echo ""
echo "[Step 3/4] Alternating normal and AOT-cache launches ($ITERATIONS per condition)..."
python3 "$SCRIPT_DIR/measure-startup-pair.py" \
  --java "$JAVA_BIN" \
  --classpath "$BENCHMARKS_JAR" \
  --iterations "$ITERATIONS" \
  --tier "$TIER" \
  --first-label normal \
  --first-flags "-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC" \
  --first-output "$NORMAL_JSON" \
  --second-label aot \
  --second-flags "-server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:AOTCache=$AOT_CACHE" \
  --second-output "$AOT_JSON"

# Step 5: Comparative Analysis and Markdown Report Generation
echo ""
echo "============================================================"
echo "AOT Startup Condition Comparison (descriptive, not a release claim)"
echo "============================================================"

python3 - << PY
import json

with open("$NORMAL_JSON") as f: normal = json.load(f)
with open("$AOT_JSON") as f: aot = json.load(f)

norm_sum = normal.get("summary", {})
aot_sum = aot.get("summary", {})

keys = [
    ("jvmUptimeAtEntry", "JVM Bootstrap to Entry"),
    ("repoReady", "Repository Population"),
    ("engineInit", "Engine Initialization"),
    ("compilation", "Template Compilation"),
    ("firstRender", "First Render"),
    ("allFirstRenders", "All First Renders"),
    ("smallBatch50", "Small Batch (50 renders)"),
    ("totalStartup", "Total Startup & Execution"),
]

print(f"{'CHECKPOINT':<28} | {'NORMAL (ms)':<12} | {'AOT (ms)':<12} | {'DIRECTION':<10} | {'DELTA (ms)'}")
print("-" * 75)

lines = []
lines.append("### Java 25 JVM AOT Cache Startup Condition Report\n")
lines.append("Descriptive medians from alternating independent launches. Interpret JVM bootstrap, engine initialization, compilation/first render, and small-batch checkpoints separately; this is not a steady-state rendering claim.\n")
lines.append("| Checkpoint | Normal J25 (ms) | AOT J25 (ms) | Directional delta (%) | Delta (ms) |")
lines.append("|---|---|---|---|---|")

for k, label in keys:
    n_med = norm_sum.get(k, {}).get("stats", {}).get("median", 0.0)
    a_med = aot_sum.get(k, {}).get("stats", {}).get("median", 0.0)
    delta = a_med - n_med
    if n_med > 0:
        pct = ((n_med - a_med) / n_med) * 100.0
        pct_str = f"+{pct:.1f}%" if pct > 0 else f"{pct:.1f}%"
    else:
        pct_str = "0.0%"
    print(f"{label:<28} | {n_med:<12.2f} | {a_med:<12.2f} | {pct_str:<10} | {delta:+.2f}")
    lines.append(f"| {label} | {n_med:.2f} | {a_med:.2f} | {pct_str} | {delta:+.2f} |")

print("=" * 75)

with open("$REPORT_MD", "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"Comparison report saved to: $REPORT_MD")
PY

echo ""
echo "AOT experiment completed successfully."
