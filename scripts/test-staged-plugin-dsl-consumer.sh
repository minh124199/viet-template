#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

VERSION="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RC_REPO="${ROOT_DIR}/build/rc-repository"

if [ ! -d "${RC_REPO}" ]; then
  echo "ERROR: Staged repository not found at ${RC_REPO}. Run scripts/stage-rc-repository.sh first." >&2
  exit 1
fi

echo "=== Testing Staged Plugin DSL Consumer with version: ${VERSION} ==="

CONSUMER_SCRATCH="$(mktemp -d)"
trap 'rm -rf "${CONSUMER_SCRATCH}"' EXIT

CONSUMER_PROJECT="${CONSUMER_SCRATCH}/project"
GRADLE_HOME="${CONSUMER_SCRATCH}/gradle-home"

mkdir -p "${CONSUMER_PROJECT}/src/main/viet-template" "${GRADLE_HOME}/wrapper"

# Reuse gradle wrapper binary to avoid redownloading 150MB gradle zip, keeping all caches isolated
if [ -d "${HOME}/.gradle/wrapper/dists" ]; then
    ln -s "${HOME}/.gradle/wrapper/dists" "${GRADLE_HOME}/wrapper/dists"
fi

# Absolute file URI for RC repository
RC_REPO_URI="file://${RC_REPO}"

# Create settings.gradle.kts with rcRepository strictly first for release artifacts
cat > "${CONSUMER_PROJECT}/settings.gradle.kts" << EOF
pluginManagement {
    repositories {
        maven {
            name = "rcRepository"
            url = uri("${RC_REPO_URI}")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "rcRepository"
            url = uri("${RC_REPO_URI}")
        }
        mavenCentral()
    }
}
rootProject.name = "staged-plugin-dsl-consumer"
EOF


# Create build.gradle.kts using standard Plugin DSL without workarounds
cat > "${CONSUMER_PROJECT}/build.gradle.kts" << EOF
plugins {
    java
    id("io.github.minh124199.viet-template") version "${VERSION}"
}
EOF

# Create sample template
cat > "${CONSUMER_PROJECT}/src/main/viet-template/welcome.vtl" << 'EOF'
Welcome $user to Viet Template!
EOF

echo "Invoking compileVietTemplates with isolated GRADLE_USER_HOME..."
GRADLE_USER_HOME="${GRADLE_HOME}" "${ROOT_DIR}/gradlew" \
    -p "${CONSUMER_PROJECT}" \
    compileVietTemplates \
    --no-daemon \
    --stacktrace

# Assert compilation output exists
INDEX_FILE="${CONSUMER_PROJECT}/build/generated/viet-template/resources/META-INF/viet-template/templates.idx"
if [ ! -f "${INDEX_FILE}" ]; then
    echo "ERROR: Expected template index file missing: ${INDEX_FILE}" >&2
    exit 1
fi

echo "Template index content:"
cat "${INDEX_FILE}"

echo "=== SUCCESS: Staged Plugin DSL consumer test PASSED for version ${VERSION} ==="
