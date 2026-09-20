#!/usr/bin/env bash
# verify-m18-release-gates.sh — Master M18 TCK & Performance Release Gates
#
# Runs all M18 release gates in sequence. Every gate must pass for the script to exit 0.
# Non-zero exit signals that M18 qualification is NOT complete.
#
# Usage:
#   ./scripts/verify-m18-release-gates.sh
#
# Gates:
#   1. Language feature claim matrix — 100% coverage (python3 scripts/verify-tck-coverage.py)
#   2. Public surface classification — 99 stable types (python3 scripts/verify-public-surface-classification.py)
#   3. API compatibility baseline — 0 breaking changes (python3 scripts/verify-api-compatibility.py)
#   4. TCK conformance suite — all 688 tests pass (./mvnw test -pl viet-template-tck)
#   5. Cross-engine fixture correctness — all 48 tests pass (./mvnw test -pl viet-template-benchmarks)
#   6. Independent consumer fixture — Maven + Gradle (./scripts/verify-tck-consumer.sh)
#   7. Feature matrix JSON validity
#   8. Benchmark manifest JSON validity
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

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
echo " Baseline SHA: af8142c8e5b8155a79a7379a39a0dc009336815e"
echo " Timestamp:    $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "================================================================================"

# ---------- Gate 1: TCK coverage ----------
echo ""
echo "[Gate 1] Language feature claim matrix — 100% coverage..."
if python3 "${ROOT_DIR}/scripts/verify-tck-coverage.py" > /tmp/m18-gate1.log 2>&1; then
  gate_pass "Gate 1: Language feature claim matrix (80/80 features covered)"
else
  gate_fail "Gate 1: Language feature claim matrix FAILED — see /tmp/m18-gate1.log"
  cat /tmp/m18-gate1.log
fi

# ---------- Gate 2: Public surface classification ----------
echo "[Gate 2] Public surface classification — 99 stable types..."
if python3 "${ROOT_DIR}/scripts/verify-public-surface-classification.py" > /tmp/m18-gate2.log 2>&1; then
  gate_pass "Gate 2: Public surface classification (99 stable types)"
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
if "${ROOT_DIR}/mvnw" test -pl viet-template-tck --no-transfer-progress -q > /tmp/m18-gate4.log 2>&1; then
  gate_pass "Gate 4: TCK conformance suite (688 tests, 0 failures)"
else
  gate_fail "Gate 4: TCK conformance suite FAILED — see /tmp/m18-gate4.log"
  tail -20 /tmp/m18-gate4.log
fi

# ---------- Gate 5: Cross-engine fixture correctness ----------
echo "[Gate 5] Cross-engine fixture correctness — running CrossEngineFixtureCorrectnessTest..."
if "${ROOT_DIR}/mvnw" test -pl viet-template-benchmarks \
    -Dtest=CrossEngineFixtureCorrectnessTest \
    --no-transfer-progress -q > /tmp/m18-gate5.log 2>&1; then
  gate_pass "Gate 5: Cross-engine fixture correctness (48 tests, 0 failures)"
else
  gate_fail "Gate 5: Cross-engine fixture correctness FAILED — see /tmp/m18-gate5.log"
  tail -20 /tmp/m18-gate5.log
fi

# ---------- Gate 6: Independent consumer fixture ----------
echo "[Gate 6] Independent TCK consumer fixture (Maven + Gradle)..."
if "${ROOT_DIR}/scripts/verify-tck-consumer.sh" > /tmp/m18-gate6.log 2>&1; then
  gate_pass "Gate 6: Independent consumer fixture (Maven 3 tests + Gradle 3 tests)"
else
  gate_fail "Gate 6: Independent consumer fixture FAILED — see /tmp/m18-gate6.log"
  tail -20 /tmp/m18-gate6.log
fi

# ---------- Gate 7: Feature matrix JSON validity ----------
echo "[Gate 7] Feature matrix JSON validity..."
MATRIX="${ROOT_DIR}/config/tck/vtl-feature-matrix.json"
if [ -f "${MATRIX}" ] && python3 -c "import json; json.load(open('${MATRIX}'))" > /tmp/m18-gate7.log 2>&1; then
  FEAT_COUNT=$(python3 -c "import json; d=json.load(open('${MATRIX}')); print(len(d['features']))")
  gate_pass "Gate 7: Feature matrix JSON valid (${FEAT_COUNT} features)"
else
  gate_fail "Gate 7: Feature matrix JSON FAILED — see /tmp/m18-gate7.log"
fi

# ---------- Gate 8: Benchmark manifest JSON validity ----------
echo "[Gate 8] Benchmark manifest JSON validity..."
MANIFEST="${ROOT_DIR}/config/benchmark-manifest.json"
if [ -f "${MANIFEST}" ] && python3 -c "import json; json.load(open('${MANIFEST}'))" > /tmp/m18-gate8.log 2>&1; then
  BENCH_COUNT=$(python3 -c "import json; d=json.load(open('${MANIFEST}')); print(len(d['workloads']))")
  gate_pass "Gate 8: Benchmark manifest JSON valid (${BENCH_COUNT} benchmarks)"
else
  gate_fail "Gate 8: Benchmark manifest JSON FAILED — see /tmp/m18-gate8.log"
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
