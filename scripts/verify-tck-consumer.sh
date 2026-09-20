#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "================================================================================"
echo "VIET TEMPLATE INDEPENDENT TCK CONSUMER VERIFICATION"
echo "================================================================================"

# Step 0: Ensure reactor snapshot artifacts (including viet-template-tck) are installed to local m2
echo "[STEP 0] Installing reactor dependencies and TCK to Maven local..."
"${ROOT_DIR}/mvnw" -pl viet-template-api,viet-template-runtime,viet-template-language-vtl,viet-template-vtl-interpreter,viet-template-tck -am install -DskipTests -Dspotless.check.skip=true -B -q
echo "[PASS] Reactor TCK artifacts installed to Maven local."

# Step 1: Run Maven standalone TCK consumer fixture
echo "[STEP 1] Running Maven TCK consumer fixture..."
"${ROOT_DIR}/mvnw" clean test -f "${ROOT_DIR}/integration-tests/tck-consumer/maven/pom.xml" -B
echo "[PASS] Maven TCK consumer tests passed."

# Step 2: Run Gradle standalone TCK consumer fixture
echo "[STEP 2] Running Gradle TCK consumer fixture..."
"${ROOT_DIR}/gradlew" clean test --project-dir "${ROOT_DIR}/integration-tests/tck-consumer/gradle" --no-daemon
echo "[PASS] Gradle TCK consumer tests passed."

echo "================================================================================"
echo "ALL TCK CONSUMER FIXTURES VERIFIED SUCCESSFULLY (100% PASS RATE, ZERO REACTOR IMPORTS)"
echo "================================================================================"
