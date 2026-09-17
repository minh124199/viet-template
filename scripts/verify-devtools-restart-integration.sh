#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-${VT_DEVTOOLS_M2_REPO:-/tmp/viet-template-devtools-m2}}"

echo "[BOOTSTRAP] Preparing Viet Template 0.2.2-SNAPSHOT reactor artifacts in ${MAVEN_REPO_LOCAL}..."
"${ROOT_DIR}/gradlew" publishToMavenLocal --no-daemon -x test -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -q
"${ROOT_DIR}/mvnw" install -DskipTests -Dspotless.check.skip=true -B -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -q

# Pre-flight assertions verifying snapshot artifacts exist in ${MAVEN_REPO_LOCAL}
REQUIRED_MODULES=(
    "viet-template-parent"
    "viet-template-api"
    "viet-template-runtime"
    "viet-template-language-vtl"
    "viet-template-vtl-interpreter"
    "viet-template-spring"
    "viet-template-spring-security"
    "viet-template-spring-boot-autoconfigure"
    "viet-template-spring-boot-starter"
    "viet-template-maven-plugin"
    "viet-template-gradle-plugin"
)

for mod in "${REQUIRED_MODULES[@]}"; do
    if [ "${mod}" = "viet-template-parent" ]; then
        artifact_path="${MAVEN_REPO_LOCAL}/io/github/minh124199/${mod}/0.2.2-SNAPSHOT/${mod}-0.2.2-SNAPSHOT.pom"
    else
        artifact_path="${MAVEN_REPO_LOCAL}/io/github/minh124199/${mod}/0.2.2-SNAPSHOT/${mod}-0.2.2-SNAPSHOT.jar"
    fi
    if [ ! -f "${artifact_path}" ]; then
        echo "[FAIL] Pre-flight assertion failed: missing ${artifact_path}"
        exit 1
    fi
done
echo "[PASS] All 11 required 0.2.2-SNAPSHOT reactor artifacts verified in ${MAVEN_REPO_LOCAL}."

APP_PID=""
CURRENT_FIXTURE_DIR=""

cleanup() {
    if [ -n "${APP_PID}" ] && kill -0 "${APP_PID}" 2>/dev/null; then
        kill -9 "${APP_PID}" 2>/dev/null || true
    fi
    pkill -f "maven-boot3-devtools" 2>/dev/null || true
    pkill -f "gradle-boot3-devtools" 2>/dev/null || true
    pkill -f "maven-boot4-devtools" 2>/dev/null || true
    pkill -f "gradle-boot4-devtools" 2>/dev/null || true
    if [ -n "${CURRENT_FIXTURE_DIR}" ]; then
        if [ -f "${CURRENT_FIXTURE_DIR}/src/main/viet-template/stale.vtl.bak" ]; then
            mv "${CURRENT_FIXTURE_DIR}/src/main/viet-template/stale.vtl.bak" "${CURRENT_FIXTURE_DIR}/src/main/viet-template/stale.vtl" 2>/dev/null || true
        fi
        sed -i '/<!-- mode-b-recompile -->/d' "${CURRENT_FIXTURE_DIR}/src/main/viet-template/public.vtl" 2>/dev/null || true
        sed -i 's/dynamic-v2-hot-reloaded/dynamic-v1/' "${CURRENT_FIXTURE_DIR}/target/classes/dynamic-page.vtl" 2>/dev/null || true
        sed -i 's/dynamic-v2-hot-reloaded/dynamic-v1/' "${CURRENT_FIXTURE_DIR}/build/resources/main/dynamic-page.vtl" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

wait_for_server() {
    local port="$1"
    local max_wait=40
    local count=0
    while ! curl -s "http://localhost:${port}/__test/restart-generation" >/dev/null 2>&1; do
        sleep 1
        count=$((count + 1))
        if [ "${count}" -ge "${max_wait}" ]; then
            echo "[FAIL] Server on port ${port} failed to start within ${max_wait}s!"
            return 1
        fi
    done
    return 0
}

wait_for_restart() {
    local port="$1"
    local old_cl="$2"
    local max_wait=30
    local count=0
    while true; do
        sleep 0.5
        local res
        res=$(curl -s "http://localhost:${port}/__test/restart-generation" || true)
        local cur_cl
        cur_cl=$(echo "${res}" | grep -o '"classLoaderId":"[^"]*"' | cut -d'"' -f4 || true)
        if [ -n "${cur_cl}" ] && [ "${cur_cl}" != "${old_cl}" ]; then
            echo "${cur_cl}"
            return 0
        fi
        count=$((count + 1))
        if [ "${count}" -ge "${max_wait}" ]; then
            echo "[FAIL] Server on port ${port} failed to restart within ${max_wait} intervals!" >&2
            return 1
        fi
    done
}

verify_security() {
    local port="$1"
    echo "  [SECURITY] Verifying Spring Security & CSRF isolation on port ${port}..."

    # Anonymous access to /public
    local pub_code
    pub_code=$(curl -s -o /tmp/devtools-pub.html -w "%{http_code}" "http://localhost:${port}/public")
    if [ "${pub_code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 on /public, got ${pub_code}!"
        exit 1
    fi
    if ! grep -q "Welcome, Alice!" /tmp/devtools-pub.html; then
        echo "[FAIL] Missing Welcome, Alice! on /public"
        exit 1
    fi

    # Anonymous access to /admin
    local unauth_code
    unauth_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/admin")
    if [ "${unauth_code}" != "401" ]; then
        echo "[FAIL] Expected HTTP 401 on unauthenticated /admin, got ${unauth_code}!"
        exit 1
    fi

    # Non-admin access to /admin (Forbidden)
    local forbid_code
    forbid_code=$(curl -s -u user:password -o /dev/null -w "%{http_code}" "http://localhost:${port}/admin")
    if [ "${forbid_code}" != "403" ]; then
        echo "[FAIL] Expected HTTP 403 for user on /admin, got ${forbid_code}!"
        exit 1
    fi

    # Admin access to /admin
    local admin_code
    admin_code=$(curl -s -u admin:admin -o /tmp/devtools-admin.html -w "%{http_code}" "http://localhost:${port}/admin")
    if [ "${admin_code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 for admin on /admin, got ${admin_code}!"
        exit 1
    fi
    if ! grep -q "admin" /tmp/devtools-admin.html; then
        echo "[FAIL] Missing admin principal on /admin"
        exit 1
    fi

    # Authenticated dashboard access with CSRF token verification
    local dash_code
    dash_code=$(curl -s -u user:password -o /tmp/devtools-dash.html -w "%{http_code}" "http://localhost:${port}/dashboard")
    if [ "${dash_code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 for user on /dashboard, got ${dash_code}!"
        exit 1
    fi
    if ! grep -q 'id="csrf-token"' /tmp/devtools-dash.html || grep -q '\$csrf\.token' /tmp/devtools-dash.html; then
        echo "[FAIL] Missing or unresolved CSRF token in authenticated dashboard view!"
        exit 1
    fi
    echo "  [PASS] Spring Security authentication, role authorization, and CSRF isolation verified."
}

verify_fixture() {
    local build_tool="$1"
    local boot_version="$2"
    local rel_dir="$3"
    local port="$4"
    local fixture_dir="${ROOT_DIR}/${rel_dir}"
    CURRENT_FIXTURE_DIR="${fixture_dir}"

    echo ""
    echo "================================================================================"
    echo ">>> Verifying Fixture: [${build_tool}] ${rel_dir} (Port ${port}) <<<"
    echo "================================================================================"

    # Step 1: Run fixture tests
    echo "[STEP 1] Running fixture unit & MockMvc test suite..."
    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" test -f "${fixture_dir}/pom.xml" -B -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
    else
        "${ROOT_DIR}/gradlew" test --project-dir "${fixture_dir}" --no-daemon -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
    fi
    echo "[PASS] Fixture test suite passed."

    # Step 2: Start server with Spring Boot DevTools enabled
    echo "[STEP 2] Starting ${rel_dir} with DevTools enabled on port ${port}..."
    local log_file="/tmp/${rel_dir//\//_}.log"
    rm -f "${log_file}"

    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" compile process-classes -f "${fixture_dir}/pom.xml" -B -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        "${ROOT_DIR}/mvnw" spring-boot:run -f "${fixture_dir}/pom.xml" -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" -Dspring-boot.run.arguments="--server.port=${port}" > "${log_file}" 2>&1 &
        APP_PID=$!
    else
        "${ROOT_DIR}/gradlew" classes --project-dir "${fixture_dir}" --no-daemon -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        "${ROOT_DIR}/gradlew" bootRun --project-dir "${fixture_dir}" --args="--server.port=${port}" --no-daemon -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" > "${log_file}" 2>&1 &
        APP_PID=$!
    fi

    wait_for_server "${port}"

    local initial_meta
    initial_meta=$(curl -s "http://localhost:${port}/__test/restart-generation")
    local cl_name
    cl_name=$(echo "${initial_meta}" | grep -o '"classLoaderName":"[^"]*"' | cut -d'"' -f4)
    local cur_cl
    cur_cl=$(echo "${initial_meta}" | grep -o '"classLoaderId":"[^"]*"' | cut -d'"' -f4)
    local cur_engine
    cur_engine=$(echo "${initial_meta}" | grep -o '"engineId":"[^"]*"' | cut -d'"' -f4)

    if [[ "${cl_name}" != *"RestartClassLoader"* ]]; then
        echo "[FAIL] Expected RestartClassLoader, got ${cl_name}!"
        exit 1
    fi
    echo "[PASS] App started with DevTools RestartClassLoader (${cl_name}, id: ${cur_cl}, engineId: ${cur_engine})."

    # Step 3: Verify Spring Security & CSRF isolation
    verify_security "${port}"

    # Step 4: Mode A (Dynamic hot reload without restart)
    echo "[STEP 4] Verifying Mode A (Dynamic hot reload without restart)..."
    local dyn_target
    if [ "${build_tool}" = "maven" ]; then
        dyn_target="${fixture_dir}/target/classes/dynamic-page.vtl"
    else
        dyn_target="${fixture_dir}/build/resources/main/dynamic-page.vtl"
    fi

    local dyn_initial
    dyn_initial=$(curl -s "http://localhost:${port}/dynamic-page")
    if ! echo "${dyn_initial}" | grep -q "dynamic-v1"; then
        echo "[FAIL] Expected dynamic-v1 on /dynamic-page!"
        exit 1
    fi

    sed -i 's/dynamic-v1/dynamic-v2-hot-reloaded/' "${dyn_target}"
    sleep 0.5

    local dyn_reloaded
    dyn_reloaded=$(curl -s "http://localhost:${port}/dynamic-page")
    if ! echo "${dyn_reloaded}" | grep -q "dynamic-v2-hot-reloaded"; then
        echo "[FAIL] Expected dynamic-v2-hot-reloaded after hot reload!"
        exit 1
    fi

    local dyn_meta
    dyn_meta=$(curl -s "http://localhost:${port}/__test/restart-generation")
    local dyn_cl
    dyn_cl=$(echo "${dyn_meta}" | grep -o '"classLoaderId":"[^"]*"' | cut -d'"' -f4)

    if [ "${dyn_cl}" != "${cur_cl}" ]; then
        echo "[FAIL] ClassLoader unexpectedly changed during Mode A dynamic hot reload: ${cur_cl} -> ${dyn_cl}!"
        exit 1
    fi
    echo "[PASS] Mode A dynamic hot reload succeeded: content updated without RestartClassLoader turnover (${cur_cl})."

    # Restore dynamic-page.vtl
    sed -i 's/dynamic-v2-hot-reloaded/dynamic-v1/' "${dyn_target}"

    # Step 5: Mode B (AOT recompile + trigger-file restart with RestartClassLoader turnover)
    echo "[STEP 5] Verifying Mode B (AOT recompile + trigger-file restart)..."
    echo "<!-- mode-b-recompile -->" >> "${fixture_dir}/src/main/viet-template/public.vtl"

    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" process-classes -f "${fixture_dir}/pom.xml" -B -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/target/classes/.restart-trigger"
    else
        "${ROOT_DIR}/gradlew" classes --project-dir "${fixture_dir}" --no-daemon -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/build/classes/java/main/.restart-trigger"
    fi

    local new_cl
    new_cl=$(wait_for_restart "${port}" "${cur_cl}")
    local b_meta
    b_meta=$(curl -s "http://localhost:${port}/__test/restart-generation")
    local new_engine
    new_engine=$(echo "${b_meta}" | grep -o '"engineId":"[^"]*"' | cut -d'"' -f4)

    if [ "${new_cl}" = "${cur_cl}" ]; then
        echo "[FAIL] ClassLoader did not turnover after Mode B AOT recompile!"
        exit 1
    fi
    if [ "${new_engine}" = "${cur_engine}" ]; then
        echo "[FAIL] TemplateEngine bean did not refresh after Mode B restart!"
        exit 1
    fi
    echo "[PASS] Mode B AOT recompile + restart triggered: ClassLoader ${cur_cl} -> ${new_cl}, Engine ${cur_engine} -> ${new_engine}."
    cur_cl="${new_cl}"
    cur_engine="${new_engine}"

    # Restore public.vtl
    sed -i '/<!-- mode-b-recompile -->/d' "${fixture_dir}/src/main/viet-template/public.vtl"
    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" process-classes -f "${fixture_dir}/pom.xml" -B -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
    else
        "${ROOT_DIR}/gradlew" classes --project-dir "${fixture_dir}" --no-daemon -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
    fi

    # Step 6: Stale template deletion
    echo "[STEP 6] Verifying stale template deletion handling..."
    local stale_init_code
    stale_init_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/stale")
    if [ "${stale_init_code}" != "200" ]; then
        echo "[FAIL] Expected HTTP 200 for /stale before deletion, got ${stale_init_code}!"
        exit 1
    fi

    mv "${fixture_dir}/src/main/viet-template/stale.vtl" "${fixture_dir}/src/main/viet-template/stale.vtl.bak"
    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" process-classes -f "${fixture_dir}/pom.xml" -B -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/target/classes/.restart-trigger"
    else
        "${ROOT_DIR}/gradlew" classes --project-dir "${fixture_dir}" --no-daemon -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/build/classes/java/main/.restart-trigger"
    fi

    cur_cl=$(wait_for_restart "${port}" "${cur_cl}")
    local stale_deleted_code
    stale_deleted_code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/stale")
    if [ "${stale_deleted_code}" = "200" ]; then
        echo "[FAIL] Expected non-200 for /stale after template deletion, got ${stale_deleted_code}!"
        exit 1
    fi
    echo "[PASS] Deleted template correctly rejected by engine after restart (HTTP ${stale_deleted_code})."

    # Restore stale.vtl
    mv "${fixture_dir}/src/main/viet-template/stale.vtl.bak" "${fixture_dir}/src/main/viet-template/stale.vtl"
    if [ "${build_tool}" = "maven" ]; then
        "${ROOT_DIR}/mvnw" process-classes -f "${fixture_dir}/pom.xml" -B -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/target/classes/.restart-trigger"
    else
        "${ROOT_DIR}/gradlew" classes --project-dir "${fixture_dir}" --no-daemon -q -Dmaven.repo.local="${MAVEN_REPO_LOCAL}"
        touch "${fixture_dir}/build/classes/java/main/.restart-trigger"
    fi
    cur_cl=$(wait_for_restart "${port}" "${cur_cl}")

    # Step 7: 10x Restart stress test & ClassLoader reclaimability
    echo "[STEP 7] Performing restart stress test & verifying ClassLoader reclaimability..."
    for i in {1..7}; do
        if [ "${build_tool}" = "maven" ]; then
            touch "${fixture_dir}/target/classes/io/github/minh124199/test/devtools/DevToolsTestController.class"
            touch "${fixture_dir}/target/classes/.restart-trigger"
        else
            touch "${fixture_dir}/build/classes/java/main/io/github/minh124199/test/devtools/DevToolsTestController.class"
            touch "${fixture_dir}/build/classes/java/main/.restart-trigger"
        fi
        cur_cl=$(wait_for_restart "${port}" "${cur_cl}")
    done

    local leak_res
    leak_res=$(curl -s "http://localhost:${port}/__test/classloader-leak-check")
    echo "  Leak check report: ${leak_res}"
    if ! echo "${leak_res}" | grep -q '"leakFree":true'; then
        echo "[FAIL] ClassLoader leak detected during DevTools restart stress test!"
        exit 1
    fi
    echo "[PASS] ClassLoader turnover and full garbage collection confirmed (leakFree: true)."

    # Step 8: Shutdown server
    echo "[STEP 8] Stopping fixture server..."
    kill -9 "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
    APP_PID=""
    pkill -f "${rel_dir}" 2>/dev/null || true
    CURRENT_FIXTURE_DIR=""
    echo "[PASS] Fixture [${build_tool}] ${rel_dir} successfully verified."
}

# Run all 4 DevTools fixtures
verify_fixture "maven"  "boot3" "integration-tests/devtools/maven-boot3-devtools"  19081
verify_fixture "gradle" "boot3" "integration-tests/devtools/gradle-boot3-devtools" 19082
verify_fixture "maven"  "boot4" "integration-tests/devtools/maven-boot4-devtools"  19083
verify_fixture "gradle" "boot4" "integration-tests/devtools/gradle-boot4-devtools" 19084

echo ""
echo "================================================================================"
echo "[SUCCESS] Spring Boot DevTools Restart & ClassLoader Lifecycle Hardening Parity PASSED!"
echo "================================================================================"
exit 0
