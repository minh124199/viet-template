#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Viet Template Quarkus AOT Integration & Parity Verification ==="

FORCE_NATIVE=false
JVM_ONLY=false
for arg in "$@"; do
    case "${arg}" in
        --force-native)
            FORCE_NATIVE=true
            ;;
        --jvm-only)
            JVM_ONLY=true
            ;;
    esac
done

if [ "${FORCE_NATIVE_BUILD:-false}" = "true" ]; then
    FORCE_NATIVE=true
fi

if [ "${JVM_ONLY}" = "true" ] && [ "${FORCE_NATIVE}" = "true" ]; then
    echo "[FAIL] --jvm-only cannot be combined with --force-native or FORCE_NATIVE_BUILD=true."
    exit 2
fi

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

    # 5. Unauthenticated request to /hello/secured returns 401
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello/secured")
    if [ "${code}" != "401" ]; then
        echo "[FAIL] Expected HTTP 401 from unauthenticated /hello/secured on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi

    # 6. Authenticated user request to /hello/secured returns 200 with identity details
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -H "X-Test-User: user" "http://localhost:${port}/hello/secured")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/secured (user) on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "authenticated=true" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "name=user" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "admin=false" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/secured (user) response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 7. Authenticated admin request to /hello/secured returns 200 with admin=true
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -H "X-Test-User: admin" "http://localhost:${port}/hello/secured")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/secured (admin) on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "authenticated=true" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "name=admin" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "admin=true" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/secured (admin) response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 8. Forbidden request to /hello/admin with non-admin user returns 403
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -H "X-Test-User: user" "http://localhost:${port}/hello/admin")
    if [ "${code}" != "403" ]; then
        echo "[FAIL] Expected HTTP 403 from /hello/admin (user) on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi

    # 9. Allowed request to /hello/admin with admin returns 200
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -H "X-Test-User: admin" "http://localhost:${port}/hello/admin")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/admin (admin) on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q "admin=true" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/admin (admin) response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        return 1
    fi

    # 10. CSRF GET /hello/csrf returns 200 with token metadata and sets cookie
    local cookie_jar="/tmp/quarkus-csrf-cookie-jar.txt"
    rm -f "${cookie_jar}"
    code=$(curl -s -c "${cookie_jar}" -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" "http://localhost:${port}/hello/csrf")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/csrf on port ${port}, got ${code}!"
        cat "${log_file}"
        rm -f "${cookie_jar}"
        return 1
    fi
    if ! grep -q "available=true" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "parameter=csrf-token" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "header=X-CSRF-TOKEN" /tmp/quarkus-endpoint-response.html || \
       ! grep -q "hasToken=true" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/csrf response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        rm -f "${cookie_jar}"
        return 1
    fi

    local csrf_token
    csrf_token=$(awk '$6 == "csrf-token" {print $7}' "${cookie_jar}" | tr -d '\r\n')
    if [ -z "${csrf_token}" ]; then
        echo "[FAIL] csrf-token cookie not found in cookie jar!"
        rm -f "${cookie_jar}"
        return 1
    fi

    # 11. CSRF POST submission rejected without token (HTTP 400)
    code=$(curl -s -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -X POST -d "message=test" "http://localhost:${port}/hello/csrf-submit")
    if [ "${code}" != "400" ]; then
        echo "[FAIL] Expected HTTP 400 from /hello/csrf-submit without token on port ${port}, got ${code}!"
        cat "${log_file}"
        rm -f "${cookie_jar}"
        return 1
    fi

    # 12. CSRF POST submission succeeds with valid cookie and form parameter
    code=$(curl -s -b "${cookie_jar}" -o /tmp/quarkus-endpoint-response.html -w "%{http_code}" -X POST --data-urlencode "csrf-token=${csrf_token}" --data-urlencode "message=NativeVerified" "http://localhost:${port}/hello/csrf-submit")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/csrf-submit on port ${port}, got ${code}!"
        cat "${log_file}"
        rm -f "${cookie_jar}"
        return 1
    fi
    if ! grep -q "Received: NativeVerified" /tmp/quarkus-endpoint-response.html; then
        echo "[FAIL] /hello/csrf-submit response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.html
        rm -f "${cookie_jar}"
        return 1
    fi
    rm -f "${cookie_jar}"

    # 13. Client-data serialization with SimpleJsonSerializer via GET /hello/client-data
    code=$(curl -s -o /tmp/quarkus-endpoint-response.json -w "%{http_code}" "http://localhost:${port}/hello/client-data")
    if [ "${code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 from /hello/client-data on port ${port}, got ${code}!"
        cat "${log_file}"
        return 1
    fi
    if ! grep -q '"username":"native-user"' /tmp/quarkus-endpoint-response.json || \
       ! grep -q '"roleLevel":99' /tmp/quarkus-endpoint-response.json || \
       ! grep -q '"team":"core"' /tmp/quarkus-endpoint-response.json || \
       ! grep -q '"lead":true' /tmp/quarkus-endpoint-response.json; then
        echo "[FAIL] /hello/client-data response body missing expected content:"
        cat /tmp/quarkus-endpoint-response.json
        return 1
    fi

    return 0
}

# Robust GraalVM / Mandrel detection
if command -v native-image >/dev/null 2>&1; then
    :
elif [ -n "${GRAALVM_HOME:-}" ] && [ -x "${GRAALVM_HOME}/bin/native-image" ]; then
    export PATH="${GRAALVM_HOME}/bin:${PATH}"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/native-image" ]; then
    export GRAALVM_HOME="${JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
else
    for candidate in \
        "${HOME:-}/.graalvm"/mandrel-java25* \
        "${HOME:-}/.graalvm"/mandrel* \
        "${HOME:-}/opt"/graalvm-jdk-25* \
        "${HOME:-}/opt"/graalvm* \
        "/usr/lib/jvm"/graalvm-jdk-25* \
        "/usr/lib/jvm"/graalvm* \
        "/opt"/graalvm-jdk-25* \
        "/opt"/graalvm*; do
        if [ -d "${candidate}" ] && [ -x "${candidate}/bin/native-image" ]; then
            export GRAALVM_HOME="${candidate}"
            export PATH="${GRAALVM_HOME}/bin:${PATH}"
            break
        fi
    done
fi

MAVEN_FIXTURE_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>/\1/p' "${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/pom.xml" | head -n 1)"
GRADLE_FIXTURE_VERSION="$(sed -n 's/^[[:space:]]*version = "\([^"]*\)"/\1/p' "${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build.gradle.kts" | head -n 1)"
if [ -z "${MAVEN_FIXTURE_VERSION}" ] || [ "${MAVEN_FIXTURE_VERSION}" != "${GRADLE_FIXTURE_VERSION}" ]; then
    echo "[FAIL] Quarkus fixture versions are missing or differ (Maven=${MAVEN_FIXTURE_VERSION}, Gradle=${GRADLE_FIXTURE_VERSION})."
    exit 1
fi

MAVEN_NATIVE_RUNNER="${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/target/maven-quarkus-aot-${MAVEN_FIXTURE_VERSION}-runner"
GRADLE_NATIVE_RUNNER="${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot/build/gradle-quarkus-aot-${GRADLE_FIXTURE_VERSION}-runner"

# Native binaries are executable release evidence. Never restore them from a
# shared cache: every invocation must rebuild from the checked-out sources.
echo "[INFO] Native verification always rebuilds both fixture executables from source."
rm -f "${MAVEN_NATIVE_RUNNER}" "${GRADLE_NATIVE_RUNNER}"

# Step 0: Ensure staged reactor artifacts in local repository
echo "[STEP 0] Ensuring staged reactor artifacts in local repository..."
if [ -d "${ROOT_DIR}/build/rc-repository/io/github/minh124199" ]; then
    mkdir -p "${HOME}/.m2/repository/io/github"
    cp -rn "${ROOT_DIR}/build/rc-repository/io/github/minh124199" "${HOME}/.m2/repository/io/github/" 2>/dev/null || cp -r "${ROOT_DIR}/build/rc-repository/io/github/minh124199" "${HOME}/.m2/repository/io/github/"
else
    "${ROOT_DIR}/gradlew" publishToMavenLocal --no-daemon -x test
    "${ROOT_DIR}/mvnw" install -DskipTests -Dspotless.check.skip=true --no-transfer-progress -B
fi
echo "[PASS] Reactor artifacts verified in local repository."

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

verify_endpoints "${MAVEN_PORT}" "QuarkusMaven" "/tmp/quarkus-maven.log"

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Maven Quarkus runner verified successfully across all endpoints."

# Step 7: Verify executable Gradle Quarkus runner JAR
echo "[STEP 7] Verifying executable Gradle Quarkus runner execution..."
GRADLE_PORT=18093
java -Dquarkus.http.port="${GRADLE_PORT}" -jar "${GRADLE_APP_JAR}" > /tmp/quarkus-gradle.log 2>&1 &
APP_PID=$!

wait_for_server "${GRADLE_PORT}"

verify_endpoints "${GRADLE_PORT}" "QuarkusGradle" "/tmp/quarkus-gradle.log"

kill -9 "${APP_PID}" 2>/dev/null || true
wait "${APP_PID}" 2>/dev/null || true
APP_PID=""
echo "[PASS] Gradle Quarkus runner verified successfully across all endpoints."

if [ "${JVM_ONLY}" = "true" ]; then
    echo "[PASS] Quarkus JVM integration complete; native coverage is provided by exact-SHA CI evidence."
    exit 0
fi

# Native builds use clean to guarantee fresh executables. Run them only after
# JVM parity and live-server checks, since clean removes their packaged JARs.
echo "[INFO] Building fresh Maven native executable..."
"${ROOT_DIR}/mvnw" clean package -Dquarkus.package.type=native -Dquarkus.native.container-build=false -DskipTests -f "${ROOT_DIR}/integration-tests/quarkus/maven-quarkus-aot/pom.xml" -B

echo "[INFO] Building fresh Gradle native executable..."
"${ROOT_DIR}/gradlew" clean build -Dquarkus.package.type=native -Dquarkus.native.container-build=false -x test --project-dir "${ROOT_DIR}/integration-tests/quarkus/gradle-quarkus-aot" --no-daemon

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
echo "[PASS] Maven Quarkus native runner verified successfully across all endpoints (/hello, /hello/page, /hello/stream, /hello/undefined, /hello/secured, /hello/admin, /hello/csrf, /hello/csrf-submit)."

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
echo "[PASS] Gradle Quarkus native runner verified successfully across all endpoints (/hello, /hello/page, /hello/stream, /hello/undefined, /hello/secured, /hello/admin, /hello/csrf, /hello/csrf-submit)."

echo ""
echo "[SUCCESS] Quarkus AOT Dual-Build Parity & Verification PASSED across all fixtures!"
exit 0
