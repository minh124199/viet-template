#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${M18_EVIDENCE_DIR:-${ROOT_DIR}/benchmark-evidence/m18}"
JAVA21_HOME="${JAVA21_HOME:-}"
JAVA25_HOME="${JAVA25_HOME:-}"

[[ -z "$(git -C "${ROOT_DIR}" status --porcelain)" ]] || {
  echo "Formal M18 qualification refuses a dirty working tree." >&2
  exit 2
}
for variable in JAVA21_HOME JAVA25_HOME; do
  value="${!variable}"
  [[ -x "${value}/bin/java" ]] || { echo "${variable} must point to a JDK containing bin/java" >&2; exit 2; }
done
[[ "$("${JAVA21_HOME}/bin/java" -version 2>&1 | head -1)" == *'"21.'* ]] || { echo "JAVA21_HOME is not Java 21" >&2; exit 2; }
[[ "$("${JAVA25_HOME}/bin/java" -version 2>&1 | head -1)" == *'"25'* ]] || { echo "JAVA25_HOME is not Java 25" >&2; exit 2; }

mkdir -p "${OUTPUT_DIR}"
CANDIDATE_SHA="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
CREATED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "M18 comparative release qualification: ${CANDIDATE_SHA}"

JAVA_HOME="${JAVA25_HOME}" "${ROOT_DIR}/mvnw" \
  -pl viet-template-benchmarks -am test \
  -Dtest=CrossEngineFixtureCorrectnessTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotless.check.skip=true --no-transfer-progress -B
JAVA_HOME="${JAVA21_HOME}" "${ROOT_DIR}/mvnw" \
  -pl viet-template-benchmarks -am package -DskipTests -Dspotless.check.skip=true \
  --no-transfer-progress -B

run_profile() {
  local profile="$1"
  local java_home="$2"
  "${ROOT_DIR}/scripts/record-benchmark-env.sh" \
    --profile "${profile}" --java "${java_home}/bin/java" \
    --output "${OUTPUT_DIR}/environment-${profile}.json"
  "${java_home}/bin/java" -server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC \
    -jar "${ROOT_DIR}/viet-template-benchmarks/target/benchmarks.jar" \
    '.*ComparativeEngineBenchmark.*' -f 3 -wi 5 -i 10 -w 1s -r 1s -prof gc \
    -rf json -rff "${OUTPUT_DIR}/comparative-${profile}.json"
}

run_profile J21-G1 "${JAVA21_HOME}"
run_profile J25-G1 "${JAVA25_HOME}"

python3 "${SCRIPT_DIR}/generate-benchmark-report.py" \
  --input-dir "${OUTPUT_DIR}" --output "${OUTPUT_DIR}/report.md" --generated-at "${CREATED_AT}"
python3 "${SCRIPT_DIR}/build-m18-evidence-package.py" \
  --evidence-dir "${OUTPUT_DIR}" --created-at "${CREATED_AT}"
python3 "${SCRIPT_DIR}/verify-m18-evidence.py" \
  --evidence-dir "${OUTPUT_DIR}" --expected-sha "${CANDIDATE_SHA}"
python3 "${SCRIPT_DIR}/generate-benchmark-report.py" \
  --input-dir "${OUTPUT_DIR}" --output "${OUTPUT_DIR}/report.md" \
  --generated-at "${CREATED_AT}" --verify
