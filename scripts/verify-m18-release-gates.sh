#!/usr/bin/env bash
# verify-m18-release-gates.sh — Master M18 TCK & Performance Release Gates
#
# Runs all M18 release gates in sequence. Every gate must pass for the script to exit 0.
# Non-zero exit signals that M18 qualification is NOT complete.
#
# Usage:
#   ./scripts/verify-m18-release-gates.sh [--clean-room] [--require-evidence]
#
# Gates:
#   1. Language feature claim matrix — 100% coverage (python3 scripts/verify-tck-coverage.py)
#   2. Public surface classification
#   3. API compatibility baseline — 0 breaking changes (python3 scripts/verify-api-compatibility.py)
#   4. TCK conformance suite
#   5. Cross-engine fixture correctness
#   6. Independent consumer fixture — Maven + Gradle (./scripts/verify-tck-consumer.sh)
#   7. Feature matrix semantic validity
#   8. Benchmark manifest semantic validity
#   9. Formal evidence contract (when --require-evidence is used)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
M19_BASELINE_SHA="af8142c8e5b8155a79a7379a39a0dc009336815e"
CLEAN_ROOM=false
REQUIRE_EVIDENCE=false
for argument in "$@"; do
  case "${argument}" in
    --clean-room) CLEAN_ROOM=true ;;
    --require-evidence) REQUIRE_EVIDENCE=true ;;
    *) echo "Unknown argument: ${argument}" >&2; exit 2 ;;
  esac
done

if [[ "${CLEAN_ROOM}" == "true" ]]; then
  M18_M2_REPO="$(mktemp -d /tmp/viet-m18-m2.XXXXXX)"
  M18_GRADLE_USER_HOME="$(mktemp -d /tmp/viet-m18-gradle.XXXXXX)"
  if [[ -d "${HOME}/.m2/repository/dev/gradleplugins" ]]; then
    mkdir -p "${M18_M2_REPO}/dev"
    cp -r "${HOME}/.m2/repository/dev/gradleplugins" "${M18_M2_REPO}/dev/"
  fi
else
  M18_M2_REPO="${M18_M2_REPO:-${HOME}/.m2/repository}"
  M18_GRADLE_USER_HOME="${M18_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-${HOME}/.gradle}}"
fi
MAVEN_REPO_ARG="-Dmaven.repo.local=${M18_M2_REPO}"
export M18_MAVEN_REPOSITORY="${M18_M2_REPO}"
export M18_GRADLE_USER_HOME

# ---- colours ----
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
declare -a RESULTS=()

gate_pass() {
  local label="$1"
  PASS_COUNT=$((PASS_COUNT + 1))
  RESULTS+=("  ${GREEN}[PASS]${NC} ${label}")
}

gate_fail() {
  local label="$1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  RESULTS+=("  ${RED}[FAIL]${NC} ${label}")
}

echo "================================================================================"
echo " VIET TEMPLATE M18 RELEASE GATES"
echo "================================================================================"
echo " M19.3c performance baseline SHA: ${M19_BASELINE_SHA}"
echo " M18 candidate SHA:              $(git -C "${ROOT_DIR}" rev-parse HEAD)"
echo " Clean-room mode:                ${CLEAN_ROOM}"
echo " Timestamp:    $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "================================================================================"

echo "[Bootstrap] Installing reactor artifacts once for isolated downstream gates..."
"${ROOT_DIR}/mvnw" "${MAVEN_REPO_ARG}" \
  install \
  -DskipTests -Dspotless.check.skip=true --no-transfer-progress -B
echo "[Bootstrap] Compiling the Gradle public surface for API classification gates..."
GRADLE_USER_HOME="${M18_GRADLE_USER_HOME}" "${ROOT_DIR}/gradlew" \
  classes --no-daemon --console=plain

# ---------- Gate 1: TCK coverage ----------
echo ""
echo "[Gate 1] Language feature claim matrix — 100% coverage..."
if python3 "${ROOT_DIR}/scripts/verify-tck-coverage.py" > /tmp/m18-gate1.log 2>&1; then
  FEAT_COUNT=$(python3 -c "import json; print(len(json.load(open('${ROOT_DIR}/config/tck/vtl-feature-matrix.json'))['features']))")
  gate_pass "Gate 1: Language feature claim matrix (${FEAT_COUNT}/${FEAT_COUNT} current claims covered)"
else
  gate_fail "Gate 1: Language feature claim matrix FAILED — see /tmp/m18-gate1.log"
  cat /tmp/m18-gate1.log
fi

# ---------- Gate 2: Public surface classification ----------
echo "[Gate 2] Public surface classification..."
if python3 "${ROOT_DIR}/scripts/verify-public-surface-classification.py" > /tmp/m18-gate2.log 2>&1; then
  gate_pass "Gate 2: Public surface classification"
else
  gate_fail "Gate 2: Public surface classification FAILED — see /tmp/m18-gate2.log"
  cat /tmp/m18-gate2.log
fi

# ---------- Gate 3: API compatibility ----------
echo "[Gate 3] API compatibility baseline — 0 breaking changes..."
if python3 "${ROOT_DIR}/scripts/verify-api-compatibility.py" > /tmp/m18-gate3.log 2>&1; then
  gate_pass "Gate 3: API compatibility baseline (0 breaking changes)"
else
  gate_fail "Gate 3: API compatibility baseline FAILED — see /tmp/m18-gate3.log"
  cat /tmp/m18-gate3.log
fi

# ---------- Gate 4: TCK conformance suite ----------
echo "[Gate 4] TCK conformance suite — running all tests (viet-template-tck)..."
if "${ROOT_DIR}/mvnw" "${MAVEN_REPO_ARG}" test -pl viet-template-tck --no-transfer-progress -q > /tmp/m18-gate4.log 2>&1; then
  gate_pass "Gate 4: TCK conformance suite"
else
  gate_fail "Gate 4: TCK conformance suite FAILED — see /tmp/m18-gate4.log"
  tail -20 /tmp/m18-gate4.log
fi

# ---------- Gate 5: Cross-engine fixture correctness ----------
echo "[Gate 5] Cross-engine fixture correctness — running CrossEngineFixtureCorrectnessTest..."
if "${ROOT_DIR}/mvnw" "${MAVEN_REPO_ARG}" test -pl viet-template-benchmarks \
    -Dtest=CrossEngineFixtureCorrectnessTest \
    --no-transfer-progress -q > /tmp/m18-gate5.log 2>&1; then
  gate_pass "Gate 5: Cross-engine fixture correctness"
else
  gate_fail "Gate 5: Cross-engine fixture correctness FAILED — see /tmp/m18-gate5.log"
  tail -20 /tmp/m18-gate5.log
fi

# ---------- Gate 6: Independent consumer fixture ----------
echo "[Gate 6] Independent TCK consumer fixture (Maven + Gradle)..."
if M18_REACTOR_BOOTSTRAPPED=true "${ROOT_DIR}/scripts/verify-tck-consumer.sh" > /tmp/m18-gate6.log 2>&1; then
  gate_pass "Gate 6: Independent packaged-artifact consumer fixture (Maven + Gradle)"
else
  gate_fail "Gate 6: Independent consumer fixture FAILED — see /tmp/m18-gate6.log"
  tail -20 /tmp/m18-gate6.log
fi

# ---------- Gate 7: Feature matrix JSON validity ----------
echo "[Gate 7] Feature matrix JSON validity..."
MATRIX="${ROOT_DIR}/config/tck/vtl-feature-matrix.json"
if [ -f "${MATRIX}" ] && python3 "${ROOT_DIR}/scripts/verify-tck-coverage.py" > /tmp/m18-gate7.log 2>&1; then
  FEAT_COUNT=$(python3 -c "import json; d=json.load(open('${MATRIX}')); print(len(d['features']))")
  gate_pass "Gate 7: Feature matrix JSON valid (${FEAT_COUNT} features)"
else
  gate_fail "Gate 7: Feature matrix JSON FAILED — see /tmp/m18-gate7.log"
fi

# ---------- Gate 8: Benchmark manifest JSON validity ----------
echo "[Gate 8] Benchmark manifest JSON validity..."
MANIFEST="${ROOT_DIR}/config/benchmark-manifest.json"
if [ -f "${MANIFEST}" ] && python3 "${ROOT_DIR}/scripts/verify-benchmark-manifest.py" > /tmp/m18-gate8.log 2>&1; then
  BENCH_COUNT=$(python3 -c "import json; d=json.load(open('${MANIFEST}')); print(len(d['workloads']))")
  gate_pass "Gate 8: Benchmark manifest semantically valid (${BENCH_COUNT} current workloads)"
else
  gate_fail "Gate 8: Benchmark manifest JSON FAILED — see /tmp/m18-gate8.log"
fi

# ---------- Gate 9: formal evidence contract ----------
if [[ "${REQUIRE_EVIDENCE}" == "true" ]]; then
  echo "[Gate 9] Formal M18 benchmark evidence contract..."
  if python3 "${ROOT_DIR}/scripts/perf/verify-m18-evidence.py" \
      --expected-sha "$(git -C "${ROOT_DIR}" rev-parse HEAD)" > /tmp/m18-gate9.log 2>&1; then
    gate_pass "Gate 9: Formal evidence manifest, checksums, SHA, and report"
  else
    gate_fail "Gate 9: Formal evidence contract FAILED — see /tmp/m18-gate9.log"
    cat /tmp/m18-gate9.log
  fi
fi

# ---------- Summary ----------
echo ""
echo "================================================================================"
echo " M18 RELEASE GATE RESULTS"
echo "================================================================================"
for result in "${RESULTS[@]}"; do
  echo -e "${result}"
done
echo "--------------------------------------------------------------------------------"
echo " Passed: ${PASS_COUNT} / $((PASS_COUNT + FAIL_COUNT))"
echo "--------------------------------------------------------------------------------"

if [ "${FAIL_COUNT}" -eq 0 ]; then
  echo -e " ${GREEN}[M18 RELEASE GATES: ALL PASS]${NC}"
  echo "================================================================================"
  exit 0
else
  echo -e " ${RED}[M18 RELEASE GATES: FAILED (${FAIL_COUNT} gate(s) failed)]${NC}"
  echo "================================================================================"
  exit 1
fi
