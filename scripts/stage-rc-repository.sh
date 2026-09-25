#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RC_REPO="${ROOT_DIR}/build/rc-repository"

echo "=== Staging Release Candidate Repository ==="
echo "Cleaning staging directory: ${RC_REPO}"
rm -rf "${RC_REPO}"
mkdir -p "${RC_REPO}"

cd "${ROOT_DIR}"

SETTINGS_ARG=()
if [ ! -f "${HOME}/.m2/settings.xml" ]; then
    SETTINGS_ARG=(-s "${ROOT_DIR}/.github/maven-settings.xml")
fi

echo "Step 1: Deploying 13 Maven reactor artifacts into build/rc-repository..."
./mvnw deploy \
    -DaltDeploymentRepository="rcRepository::default::file://${RC_REPO}" \
    -Dgpg.skip=true \
    -DskipTests \
    -Dspotless.check.skip=true \
    "${SETTINGS_ARG[@]}" \
    -B


echo "Step 2: Publishing Gradle plugin marker artifact to build/rc-repository..."
./gradlew :viet-template-gradle-plugin:publishVietTemplatePluginMarkerMavenPublicationToRcRepositoryRepository \
    --no-daemon \
    -Dspotless.check.skip=true

echo "Step 3: Generating missing SHA-256 and MD5 checksums..."
find "${RC_REPO}" -type f ! -name "*.sha1" ! -name "*.sha256" ! -name "*.sha512" ! -name "*.md5" ! -name "*.asc" | while read -r file; do
    if [ ! -f "${file}.sha256" ]; then
        sha256sum "${file}" | awk '{print $1}' > "${file}.sha256"
    fi
    if [ ! -f "${file}.md5" ]; then
        md5sum "${file}" | awk '{print $1}' > "${file}.md5"
    fi
done

echo "Step 4: Verifying staged coordinates..."
PRIMARY_COORDINATES=(
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
    "viet-template-quarkus"
    "viet-template-quarkus-deployment"
)

MARKER_COORDINATE="io.github.minh124199.viet-template.gradle.plugin"
EXCLUDED_COORDINATES=(
    "viet-template-tck"
    "viet-template-benchmarks"
)

# Extract version from root pom.xml
VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>/\1/p' "${ROOT_DIR}/pom.xml" | head -n 1)"
echo "Target version: ${VERSION}"

# Verify 13 primary coordinates
for coord in "${PRIMARY_COORDINATES[@]}"; do
    coord_dir="${RC_REPO}/io/github/minh124199/${coord}/${VERSION}"
    coord_pom="${coord_dir}/${coord}-${VERSION}.pom"
    if [ ! -f "${coord_pom}" ]; then
        echo "ERROR: Expected coordinate POM missing: ${coord_pom}" >&2
        exit 1
    fi
    echo "  [PASS] Staged primary coordinate: io.github.minh124199:${coord}:${VERSION}"
done

# Verify marker coordinate
marker_dir="${RC_REPO}/io/github/minh124199/viet-template/${MARKER_COORDINATE}/${VERSION}"
marker_pom="${marker_dir}/${MARKER_COORDINATE}-${VERSION}.pom"
if [ ! -f "${marker_pom}" ]; then
    echo "ERROR: Expected marker POM missing: ${marker_pom}" >&2
    exit 1
fi
echo "  [PASS] Staged marker coordinate: io.github.minh124199.viet-template:${MARKER_COORDINATE}:${VERSION}"

# Verify non-published modules are absent
for excl in "${EXCLUDED_COORDINATES[@]}"; do
    excl_dir="${RC_REPO}/io/github/minh124199/${excl}"
    if [ -d "${excl_dir}" ]; then
        echo "ERROR: Non-published module leaked into staging repository: ${excl_dir}" >&2
        exit 1
    fi
    echo "  [PASS] Verified exclusion of internal module: ${excl}"
done

# Verify total public coordinates count is exactly 14
TOTAL_COORDINATES="$(find "${RC_REPO}" -name "*-${VERSION}.pom" | wc -l)"
if [ "${TOTAL_COORDINATES}" -ne 14 ]; then
    echo "ERROR: Expected exactly 14 public coordinates, but found ${TOTAL_COORDINATES}!" >&2
    find "${RC_REPO}" -name "*-${VERSION}.pom" >&2
    exit 1
fi

echo "=== SUCCESS: Exactly 14 public coordinates staged and verified in build/rc-repository ==="
