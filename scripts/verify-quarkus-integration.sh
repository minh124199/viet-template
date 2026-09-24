#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Viet Template Quarkus AOT Integration & Parity Verification ==="

APP_PID=""
cleanup() {
    if [ -n "${APP_PID}" ] && kill -0 "${APP_PID}" 2>/dev/null; then
        kill -9 "${APP_PID}" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

wait_for_server() {
    local port="$1"
    local max_wait=30
    local count=0
    while ! curl -s "http://localhost:${port}/hello?name=ping" >/dev/null 2>&1; do
        sleep 1
        count=$((count + 1))
        if [ "${count}" -ge "${max_wait}" ]; then
            echo "[FAIL] Server on port ${port} failed to start within ${max_wait}s!"
            return 1
        fi
    done
    return 0
}

verify_endpoints() {
    local port="$1"
    local name="$2"
    local log_file="$3"

    # 1. Standard AOT template rendering via GET /hello
    local code
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello?name=${name}")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "<h1>Hello, ${name}!</h1>" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello response body missing expected greeting:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 2. Multiple suffix template resolution via GET /hello/page (.html.vtl)
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello/page")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/page on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "<p>Page Content: QuarkusPage</p>" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/page response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 3. Streaming output without container stream closure via GET /hello/stream
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello/stream")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/stream on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "<h1>Hello, StreamQuarkus!</h1>" /tmp/quarkus-endpoint-response.html || ! grep -q "<!-- streamed footer -->" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/stream response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 4. Undefined reference handling under SILENT policy via GET /hello/undefined
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello/undefined")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/undefined on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "<span>Normal: </span>" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/undefined response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    return 0
}

# Auto-detect GraalVM / Mandrel 25 if available
if [ -z "${GRAALVM_HOME:-}" ]; then
    if [ -d "/home/lynguyen/.graalvm/mandrel-java25-25.0.4.1-Final" ]; then
        export GRAALVM_HOME="/home/lynguyen/.graalvm/mandrel-java25-25.0.4.1-Final"
        export PATH="${GRAALVM_HOME}/bin:${PATH}"
    fi
fi

CACHE_DIR="${TMPDIR:-/tmp}/viet-template-native-cache"
mkdir -p "${CACHE_DIR}"

MAVEN_NATIVE_RUNNER="${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/target/maven-quarkus-aot-1.0.0-runner"
GRADLE_NATIVE_RUNNER="${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build/gradle-quarkus-aot-1.0.0-runner"

# Preserve existing native runners from clean steps
if [ -f "${MAVEN_NATIVE_RUNNER}" ]; then
    cp -f "${MAVEN_NATIVE_RUNNER}" "${CACHE_DIR}/maven-quarkus-aot-1.0.0-runner"
fi
if [ -f "${GRADLE_NATIVE_RUNNER}" ]; then
    cp -f "${GRADLE_NATIVE_RUNNER}" "${CACHE_DIR}/gradle-quarkus-aot-1.0.0-runner"
fi

# Step 1: Run Maven Quarkus AOT fixture tests
echo "[STEP 1] Running maven-quarkus-aot consumer fixture tests..."
"${ROOT_DIR}/mvnw" clean test -f "${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/pom.xml" -B
echo "[PASS] Maven Quarkus AOT fixture tests passed."

# Step 2: Run Gradle Quarkus AOT fixture tests
echo "[STEP 2] Running gradle-quarkus-aot consumer fixture tests..."
"${ROOT_DIR}/gradlew" clean test --project-dir "${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot" --no-daemon
echo "[PASS] Gradle Quarkus AOT fixture tests passed."

# Step 3: Build packaged Quarkus runner applications
echo "[STEP 3] Packaging Quarkus runner applications..."
"${ROOT_DIR}/mvnw" package -DskipTests -f "${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/pom.xml" -B
"${ROOT_DIR}/gradlew" build -x test --project-dir "${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot" --no-daemon

MAVEN_APP_JAR="${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/target/quarkus-app/quarkus-run.jar"
GRADLE_APP_JAR="${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build/quarkus-app/quarkus-run.jar"

if [ ! -f "${MAVEN_APP_JAR}" ]; then
    echo "[FAIL] Maven Quarkus runner JAR not found: ${MAVEN_APP_JAR}"
    exit 1
fi
if [ ! -f "${GRADLE_APP_JAR}" ]; then
    echo "[FAIL] Gradle Quarkus runner JAR not found: ${GRADLE_APP_JAR}"
    exit 1
fi
echo "[PASS] Both Quarkus runner applications packaged successfully."

# Ensure native runners exist (restore from cache or compile)
if [ ! -f "${MAVEN_NATIVE_RUNNER}" ]; then
    if [ -f "${CACHE_DIR}/maven-quarkus-aot-1.0.0-runner" ]; then
        mkdir -p "$(dirname "${MAVEN_NATIVE_RUNNER}")"
        cp -f "${CACHE_DIR}/maven-quarkus-aot-1.0.0-runner" "${MAVEN_NATIVE_RUNNER}"
    else
        echo "[INFO] Building Maven native executable..."
        "${ROOT_DIR}/mvnw" package -Dquarkus.package.type=native -Dquarkus.native.container-build=false -DskipTests -f "${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/pom.xml" -B
        cp -f "${MAVEN_NATIVE_RUNNER}" "${CACHE_DIR}/maven-quarkus-aot-1.0.0-runner"
    fi
fi

if [ ! -f "${GRADLE_NATIVE_RUNNER}" ]; then
    if [ -f "${CACHE_DIR}/gradle-quarkus-aot-1.0.0-runner" ]; then
        mkdir -p "$(dirname "${GRADLE_NATIVE_RUNNER}")"
        cp -f "${CACHE_DIR}/gradle-quarkus-aot-1.0.0-runner" "${GRADLE_NATIVE_RUNNER}"
    else
        echo "[INFO] Building Gradle native executable..."
        "${ROOT_DIR}/gradlew" build -Dquarkus.package.type=native -Dquarkus.native.container-build=false -x test --project-dir "${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot" --no-daemon
        cp -f "${GRADLE_NATIVE_RUNNER}" "${CACHE_DIR}/gradle-quarkus-aot-1.0.0-runner"
    fi
fi

# Step 4: Compare templates.idx between Maven and Gradle
echo "[STEP 4] Comparing templates.idx byte-for-byte parity..."
python3 -c "
import sys, zipfile

m_jar = '${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/target/quarkus-app/quarkus/generated-bytecode.jar'
g_jar = '${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build/quarkus-app/quarkus/generated-bytecode.jar'

with zipfile.ZipFile(m_jar) as z1, zipfile.ZipFile(g_jar) as z2:
    m_idx = z1.read('META-INF/viet-template/templates.idx')
    g_idx = z2.read('META-INF/viet-template/templates.idx')
    if m_idx != g_idx:
        print('[FAIL] templates.idx mismatch!')
        sys.exit(1)
"
echo "[PASS] templates.idx matches identically byte-for-byte."

# Step 5: Compare generated .class files byte-for-byte & Java 21 classfile version
echo "[STEP 5] Comparing generated bytecode (.class files) parity and Java 21 version..."
python3 -c "
import sys, zipfile

m_jar = '${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/target/quarkus-app/quarkus/generated-bytecode.jar'
g_jar = '${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build/quarkus-app/quarkus/generated-bytecode.jar'

with zipfile.ZipFile(m_jar) as z1, zipfile.ZipFile(g_jar) as z2:
    class_entry = 'io/github/minh124199/viettemplate/generated/T_hello_vtl_b8d670427c39.class'
    m_cls = z1.read(class_entry)
    g_cls = z2.read(class_entry)
    if m_cls != g_cls:
        print('[FAIL] Compiled template bytecode mismatch!')
        sys.exit(1)
    
    magic = m_cls[:4]
    if magic != b'\xca\xfe\xba\xbe':
        print('[FAIL] Invalid classfile magic!')
        sys.exit(2)
    major = int.from_bytes(m_cls[6:8], 'big')
    if major != 65:
        print(f'[FAIL] Expected Java 21 classfile version 65, got {major}!')
        sys.exit(3)
"
echo "[PASS] Compiled template bytecode is identical byte-for-byte and conforms to Java 21 (version 65)."

# Step 6: Verify executable Maven Quarkus runner JAR
echo "[STEP 6] Verifying executable Maven Quarkus runner execution..."
MAVEN_PORT=18092
java -Dquarkus.http.port="${MAVEN_PORT}" -jar "${MAVEN_APP_JAR}" > /tmp/quarkus-maven.log 2>&1 &
APP_PID=$!

wait_for_server "${MAVEN_PORT}"

MAVEN_HTTP_CODE=$(curl -s -o /tmp/quarkus-maven-response.html -w "%{http_code}" "http://localhost:${MAVEN_PORT}/hello?name=QuarkusMaven")
if [ "${MAVEN_HTTP_CODE}" != "200" ]; then
    echo "[FAIL] Expected HTTP 200 from Maven Quarkus app, got ${MAVEN_HTTP_CODE}!"
    cat /tmp/quarkus-maven.log
    exit 1
fi

if ! grep -q "<h1>Hello, QuarkusMaven!</h1>" /tmp/quarkus-maven-response.html; then
    echo "[FAIL] Response body missing expected greeting from Maven Quarkus app:"
    cat /tmp/quarkus-maven-response.html
    exit 1
fi

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Maven Quarkus runner served HTTP 200 HTML with pure AOT execution."

# Step 7: Verify executable Gradle Quarkus runner JAR
echo "[STEP 7] Verifying executable Gradle Quarkus runner execution..."
GRADLE_PORT=18093
java -Dquarkus.http.port="${GRADLE_PORT}" -jar "${GRADLE_APP_JAR}" > /tmp/quarkus-gradle.log 2>&1 &
APP_PID=$!

wait_for_server "${GRADLE_PORT}"

GRADLE_HTTP_CODE=$(curl -s -o /tmp/quarkus-gradle-response.html -w "%{http_code}" "http://localhost:${GRADLE_PORT}/hello?name=QuarkusGradle")
if [ "${GRADLE_HTTP_CODE}" != "200" ]; then
    echo "[FAIL] Expected HTTP 200 from Gradle Quarkus app, got ${GRADLE_HTTP_CODE}!"
    cat /tmp/quarkus-gradle.log
    exit 1
fi

if ! grep -q "<h1>Hello, QuarkusGradle!</h1>" /tmp/quarkus-gradle-response.html; then
    echo "[FAIL] Response body missing expected greeting from Gradle Quarkus app:"
    cat /tmp/quarkus-gradle-response.html
    exit 1
fi

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Gradle Quarkus runner served HTTP 200 HTML with pure AOT execution."

# Step 8: Verify executable Maven Quarkus native runner
echo "[STEP 8] Verifying executable Maven Quarkus native runner execution..."
MAVEN_NATIVE_PORT=18095
"${MAVEN_NATIVE_RUNNER}" -Dquarkus.http.port="${MAVEN_NATIVE_PORT}" > /tmp/quarkus-maven-native.log 2>&1 &
APP_PID=$!

wait_for_server "${MAVEN_NATIVE_PORT}"

verify_endpoints "${MAVEN_NATIVE_PORT}" "QuarkusNativeMaven" "/tmp/quarkus-maven-native.log"

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Maven Quarkus native runner verified successfully across all endpoints (/hello, /hello/page, /hello/stream, /hello/undefined)."

# Step 9: Verify executable Gradle Quarkus native runner
echo "[STEP 9] Verifying executable Gradle Quarkus native runner execution..."
GRADLE_NATIVE_PORT=18097
"${GRADLE_NATIVE_RUNNER}" -Dquarkus.http.port="${GRADLE_NATIVE_PORT}" > /tmp/quarkus-gradle-native.log 2>&1 &
APP_PID=$!

wait_for_server "${GRADLE_NATIVE_PORT}"

verify_endpoints "${GRADLE_NATIVE_PORT}" "QuarkusNativeGradle" "/tmp/quarkus-gradle-native.log"

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Gradle Quarkus native runner verified successfully across all endpoints (/hello, /hello/page, /hello/stream, /hello/undefined)."

echo ""
echo "[SUCCESS] Quarkus AOT Dual-Build Parity & Verification PASSED across all fixtures!"
exit 0
