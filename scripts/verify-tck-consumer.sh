#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
M18_MAVEN_REPOSITORY="${M18_MAVEN_REPOSITORY:-${M18_M2_REPO:-}}"
M18_GRADLE_USER_HOME="${M18_GRADLE_USER_HOME:-${GRADLE_USER_HOME:-}}"

if [[ -z "${M18_MAVEN_REPOSITORY}" ]]; then
  M18_MAVEN_REPOSITORY="$(mktemp -d /tmp/viet-m18-consumer-m2.XXXXXX)"
fi
if [[ -z "${M18_GRADLE_USER_HOME}" ]]; then
  M18_GRADLE_USER_HOME="$(mktemp -d /tmp/viet-m18-consumer-gradle.XXXXXX)"
fi

MAVEN_REPO_ARG="-Dmaven.repo.local=${M18_MAVEN_REPOSITORY}"

echo "================================================================================"
echo "VIET TEMPLATE INDEPENDENT TCK CONSUMER VERIFICATION"
echo "================================================================================"

# Step 0: Ensure reactor snapshot artifacts (including viet-template-tck) are installed to local m2
echo "[STEP 0] Installing reactor dependencies and TCK to Maven local..."
if [[ "${M18_REACTOR_BOOTSTRAPPED:-false}" != "true" ]]; then
  "${ROOT_DIR}/mvnw" "${MAVEN_REPO_ARG}" \
    -pl viet-template-api,viet-template-runtime,viet-template-language-vtl,viet-template-vtl-interpreter,viet-template-tck \
    -am install -DskipTests -Dspotless.check.skip=true -B -q
fi
echo "[PASS] Reactor TCK artifacts installed to Maven local."

# Step 1: Run Maven standalone TCK consumer fixture
echo "[STEP 1] Running Maven TCK consumer fixture..."
"${ROOT_DIR}/mvnw" "${MAVEN_REPO_ARG}" clean test \
  -f "${ROOT_DIR}/integration-tests/tck-consumer/maven/pom.xml" -B
echo "[PASS] Maven TCK consumer tests passed."

# Step 2: Run Gradle standalone TCK consumer fixture
echo "[STEP 2] Running Gradle TCK consumer fixture..."
GRADLE_USER_HOME="${M18_GRADLE_USER_HOME}" \
  "${ROOT_DIR}/gradlew" clean test \
  --project-dir "${ROOT_DIR}/integration-tests/tck-consumer/gradle" \
  -Pm18MavenRepository="${M18_MAVEN_REPOSITORY}" --no-daemon
echo "[PASS] Gradle TCK consumer tests passed."

echo "================================================================================"
echo "ALL TCK CONSUMER FIXTURES VERIFIED SUCCESSFULLY"
echo "Consumers used packaged artifacts and public APIs; no reactor source output was on their classpaths."
echo "================================================================================"
