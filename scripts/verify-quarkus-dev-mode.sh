#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Viet Template Quarkus Dev Mode Live Reload Verification ==="

PORT=18098
FIXTURE_DIR="${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot"
TEMPLATES_DIR="${FIXTURE_DIR}/src/main/resources/templates"
HELLO_VTL="${TEMPLATES_DIR}/hello.vtl"
HEADER_VTL="${TEMPLATES_DIR}/header.vtl"
ORIG_HELLO_CONTENT="<h1>Hello, \$name!</h1>"

DEV_PID=""
cleanup() {
    echo "[CLEANUP] Restoring template files and terminating dev mode process..."
    if [ -n "${DEV_PID}" ] && kill -0 "${DEV_PID}" 2>/dev/null; then
        kill -15 "${DEV_PID}" 2>/dev/null || true
        sleep 2
        kill -9 "${DEV_PID}" 2>/dev/null || true
    fi
    echo "${ORIG_HELLO_CONTENT}" > "${HELLO_VTL}"
    rm -f "${HEADER_VTL}"
    echo "[CLEANUP] Done."
}
trap cleanup EXIT INT TERM

wait_for_endpoint() {
    local max_attempts=60
    local attempt=0
    while ! curl -s "http://localhost:${PORT}/hello?name=DevCheck" >/dev/null 2>&1; do
        sleep 1
        attempt=$((attempt + 1))
        if [ "${attempt}" -ge "${max_attempts}" ]; then
            echo "[FAIL] Quarkus dev server failed to start within ${max_attempts}s!"
            if [ -f /tmp/quarkus-dev.log ]; then
                cat /tmp/quarkus-dev.log
            fi
            return 1
        fi
    done
    return 0
}

# Step 1: Ensure clean starting state
echo "${ORIG_HELLO_CONTENT}" > "${HELLO_VTL}"
rm -f "${HEADER_VTL}"

# Step 2: Start Quarkus dev mode
echo "[STEP 1] Starting Quarkus dev mode on port ${PORT}..."
"${ROOT_DIR}/mvnw" -f "${FIXTURE_DIR}/pom.xml" quarkus:dev -Dquarkus.http.port="${PORT}" > /tmp/quarkus-dev.log 2>&1 &
DEV_PID=$!
echo "Quarkus dev process started with PID ${DEV_PID}"

wait_for_endpoint
echo "[PASS] Quarkus dev mode started successfully."

# Step 3: Verify Version A
echo "[STEP 2] Verifying initial template response (Version A)..."
RESP_A=$(curl -s "http://localhost:${PORT}/hello?name=DevUser")
if ! echo "${RESP_A}" | grep -q "<h1>Hello, DevUser!</h1>"; then
    echo "[FAIL] Expected '<h1>Hello, DevUser!</h1>', got: ${RESP_A}"
    exit 1
fi
echo "[PASS] Version A rendered correctly: ${RESP_A}"

# Step 4: Modify template source directly and verify live reload
echo "[STEP 3] Modifying hello.vtl to Version B..."
echo "<h1>Hello V2, \$name!</h1>" > "${HELLO_VTL}"
sleep 2

echo "[STEP 4] Sending HTTP request to verify live-reload of modified template..."
RESP_B=$(curl -s "http://localhost:${PORT}/hello?name=DevUser")
if ! echo "${RESP_B}" | grep -q "<h1>Hello V2, DevUser!</h1>"; then
    echo "[FAIL] Expected '<h1>Hello V2, DevUser!</h1>', got: ${RESP_B}"
    cat /tmp/quarkus-dev.log
    exit 1
fi
echo "[PASS] Version B rendered correctly without restarting application: ${RESP_B}"

# Step 5: Test transitive dependency invalidation (#parse subtemplate)
echo "[STEP 5] Testing transitive dependency reload (#parse header.vtl)..."
echo "<h2>Header V1</h2>" > "${HEADER_VTL}"
echo '#parse("header.vtl")<h1>Hello V2, $name!</h1>' > "${HELLO_VTL}"
sleep 2

RESP_C=$(curl -s "http://localhost:${PORT}/hello?name=DevUser")
if ! echo "${RESP_C}" | grep -q "<h2>Header V1</h2>"; then
    echo "[FAIL] Expected '<h2>Header V1</h2>', got: ${RESP_C}"
    exit 1
fi
echo "[PASS] Subtemplate header.vtl rendered on initial include."

echo "[STEP 6] Modifying dependency header.vtl to V2..."
echo "<h2>Header V2 Transitive</h2>" > "${HEADER_VTL}"
sleep 2

RESP_D=$(curl -s "http://localhost:${PORT}/hello?name=DevUser")
if ! echo "${RESP_D}" | grep -q "<h2>Header V2 Transitive</h2>"; then
    echo "[FAIL] Expected transitive propagation of '<h2>Header V2 Transitive</h2>', got: ${RESP_D}"
    exit 1
fi
echo "[PASS] Transitive dependency change propagated seamlessly: ${RESP_D}"

echo ""
echo "[SUCCESS] Quarkus dev mode live reload & transitive dependency verification PASSED!"
exit 0
