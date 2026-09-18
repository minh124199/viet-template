#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Viet Template JDK 21 vs JDK 25 Benchmark Matrix Runner ==="

# 1. Discover JDK 21
JDK21_BIN="${JAVA21_HOME:-}/bin/java"
if [ ! -x "${JDK21_BIN}" ]; then
    for candidate in \
        "${HOME:-}/opt"/jdk21* \
        "${HOME:-}/opt"/*/usr/lib/jvm/java-21* \
        "/usr/lib/jvm"/java-21* \
        "/opt"/jdk-21*; do
        if [ -x "${candidate}/bin/java" ]; then
            JDK21_BIN="${candidate}/bin/java"
            break
        fi
    done
fi
if [ ! -x "${JDK21_BIN}" ]; then
    if command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q 'version "21'; then
        JDK21_BIN="$(command -v java)"
    else
        echo "ERROR: JDK 21 not found. Set JAVA21_HOME."
        exit 1
    fi
fi

# 2. Discover JDK 25
JDK25_BIN="${JAVA25_HOME:-}/bin/java"
if [ ! -x "${JDK25_BIN}" ]; then
    for candidate in \
        "${HOME:-}/opt"/graalvm-jdk-25* \
        "${HOME:-}/opt"/jdk25* \
        "${HOME:-}/opt"/*/usr/lib/jvm/java-25* \
        "/usr/lib/jvm"/java-25* \
        "/opt"/jdk-25*; do
        if [ -x "${candidate}/bin/java" ]; then
            JDK25_BIN="${candidate}/bin/java"
            break
        fi
    done
fi
if [ ! -x "${JDK25_BIN}" ]; then
    echo "ERROR: JDK 25 not found. Set JAVA25_HOME."
    exit 1
fi

BENCHMARK_JAR="${ROOT_DIR}/viet-template-benchmarks/target/benchmarks.jar"
if [ ! -f "${BENCHMARK_JAR}" ]; then
    echo "Building benchmark JAR..."
    "${ROOT_DIR}/mvnw" package -DskipTests -pl viet-template-benchmarks -am -B
fi

OUTPUT_DIR="${ROOT_DIR}/viet-template-benchmarks/results"
mkdir -p "${OUTPUT_DIR}"

CPU_INFO="$(lscpu 2>/dev/null | grep -E "Model name|Tên mô hình" | sed -e 's/.*: *//' || uname -m)"
OS_INFO="$(uname -s) $(uname -r) $(uname -m)"

echo "================================================================================"
echo "BENCHMARK ENVIRONMENT"
echo "================================================================================"
echo "OS:         ${OS_INFO}"
echo "CPU:        ${CPU_INFO}"
echo "JDK 21:     $("${JDK21_BIN}" -version 2>&1 | head -n 1)"
echo "JDK 25:     $("${JDK25_BIN}" -version 2>&1 | head -n 1)"
echo "Heap:       -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC"
echo "Output Dir: ${OUTPUT_DIR}"
echo "================================================================================"

BENCHMARK_FILTER="${1:-VariableLookupBenchmark}"
FORKS="${FORKS:-2}"
WARMUPS="${WARMUPS:-3}"
ITERATIONS="${ITERATIONS:-5}"

echo "Executing JDK 21 benchmark: ${BENCHMARK_FILTER} (-f ${FORKS} -wi ${WARMUPS} -i ${ITERATIONS})..."
"${JDK21_BIN}" -jar "${BENCHMARK_JAR}" "${BENCHMARK_FILTER}" \
    -f "${FORKS}" -wi "${WARMUPS}" -i "${ITERATIONS}" -rf json -rff "${OUTPUT_DIR}/results-jdk21.json" \
    | tee "${OUTPUT_DIR}/results-jdk21.txt"

echo "Executing JDK 25 benchmark: ${BENCHMARK_FILTER} (-f ${FORKS} -wi ${WARMUPS} -i ${ITERATIONS})..."
"${JDK25_BIN}" -jar "${BENCHMARK_JAR}" "${BENCHMARK_FILTER}" \
    -f "${FORKS}" -wi "${WARMUPS}" -i "${ITERATIONS}" -rf json -rff "${OUTPUT_DIR}/results-jdk25.json" \
    | tee "${OUTPUT_DIR}/results-jdk25.txt"

echo "================================================================================"
echo "BENCHMARK COMPARISON SUMMARY"
echo "================================================================================"
python3 -c "
import json, os

jdk21_file = '${OUTPUT_DIR}/results-jdk21.json'
jdk25_file = '${OUTPUT_DIR}/results-jdk25.json'

if not os.path.exists(jdk21_file) or not os.path.exists(jdk25_file):
    print('Benchmark results missing.')
    exit(0)

with open(jdk21_file) as f:
    d21 = {f\"{r['benchmark']}|{r.get('params', {})}\": r for r in json.load(f)}

with open(jdk25_file) as f:
    d25 = {f\"{r['benchmark']}|{r.get('params', {})}\": r for r in json.load(f)}

print(f\"{'Benchmark':<45} | {'Params':<12} | {'JDK21 Score ± Error':<24} | {'JDK25 Score ± Error':<24} | {'Delta':<10} | {'Interpretation'}\")
print('-' * 140)

def to_float(v):
    try:
        val = float(v)
        return 0.0 if (val != val) else val
    except (ValueError, TypeError):
        return 0.0

for k in sorted(d21.keys()):
    r21 = d21[k]
    r25 = d25.get(k)
    if not r25:
        continue
    bm_name = r21['benchmark'].split('.')[-1]
    params = str(r21.get('params', {}))
    s21 = float(r21['primaryMetric']['score'])
    e21 = to_float(r21['primaryMetric'].get('scoreError'))
    s25 = float(r25['primaryMetric']['score'])
    e25 = to_float(r25['primaryMetric'].get('scoreError'))

    ratio = (s25 - s21) / s21 * 100 if s21 > 0 else 0.0

    # Uncertainty check
    diff = abs(s25 - s21)
    combined_err = e21 + e25
    if combined_err > 0 and diff < combined_err:
        interp = 'inconclusive / overlapping uncertainty'
    elif ratio > 10:
        interp = 'clear improvement'
    elif ratio < -10:
        interp = 'clear regression'
    else:
        interp = 'approximately equivalent'

    print(f\"{bm_name:<45} | {params:<12} | {s21:>12.0f} ± {e21:<8.0f} | {s25:>12.0f} ± {e25:<8.0f} | {ratio:>+7.1f}% | {interp}\")
"

echo "Benchmark run completed successfully."
