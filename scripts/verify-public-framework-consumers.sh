#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

VERSION="${1:-1.0.0}"

if [[ "$VERSION" == *-SNAPSHOT ]]; then
  echo "ERROR: SNAPSHOT version '$VERSION' is not eligible for Maven Central consumer verification." >&2
  exit 2
fi

echo "================================================================================"
echo "VERIFYING PUBLIC FRAMEWORK CONSUMERS AGAINST MAVEN CENTRAL"
echo "Target Version: $VERSION"
echo "================================================================================"

scratch="$(mktemp -d)"

cleanup() {
  local exit_code=$?
  # Terminate any remaining child background processes if present
  jobs -p 2>/dev/null | xargs -r kill 2>/dev/null || true
  rm -rf "$scratch"
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

M2_REPO="$scratch/m2"
GRADLE_HOME="$scratch/gradle"
FIXTURES_DIR="$scratch/fixtures"

mkdir -p "$M2_REPO" "$GRADLE_HOME" "$FIXTURES_DIR"

# Link wrapper dists to reuse cached gradle binary, keeping dependencies 100% isolated
if [ -d "${HOME}/.gradle/wrapper/dists" ]; then
  mkdir -p "$GRADLE_HOME/wrapper"
  ln -s "${HOME}/.gradle/wrapper/dists" "$GRADLE_HOME/wrapper/dists"
fi

# Copy fixture templates
cp -r "$SCRIPT_DIR/public-consumers/spring-boot-mvc" "$FIXTURES_DIR/spring-boot-mvc"
cp -r "$SCRIPT_DIR/public-consumers/spring-boot-security" "$FIXTURES_DIR/spring-boot-security"
cp -r "$SCRIPT_DIR/public-consumers/quarkus" "$FIXTURES_DIR/quarkus"

# Substitute target consumer version in all pom.xml and build.gradle.kts
find "$FIXTURES_DIR" -type f \( -name "pom.xml" -o -name "build.gradle.kts" \) \
  -exec sed -i "s/@CONSUMER_VERSION@/$VERSION/g" {} +

echo "--------------------------------------------------------------------------------"
echo "[1/3] Executing Fixture 1: Spring Boot MVC (Gradle)..."
echo "--------------------------------------------------------------------------------"
GRADLE_USER_HOME="$GRADLE_HOME" "$REPO_ROOT/gradlew" \
  -p "$FIXTURES_DIR/spring-boot-mvc" \
  test --no-daemon -PconsumerVersion="$VERSION"

echo "--------------------------------------------------------------------------------"
echo "[2/3] Executing Fixture 2: Spring Boot Security (Maven)..."
echo "--------------------------------------------------------------------------------"
"$REPO_ROOT/mvnw" -f "$FIXTURES_DIR/spring-boot-security/pom.xml" \
  -B -Dmaven.repo.local="$M2_REPO" \
  -Dviet-template.version="$VERSION" \
  test

echo "--------------------------------------------------------------------------------"
echo "[3/3] Executing Fixture 3: Quarkus (Maven)..."
echo "--------------------------------------------------------------------------------"
"$REPO_ROOT/mvnw" -f "$FIXTURES_DIR/quarkus/pom.xml" \
  -B -Dmaven.repo.local="$M2_REPO" \
  -Dviet-template.version="$VERSION" \
  test

echo "================================================================================"
echo "PROVENANCE AND CLEAN-ROOM ISOLATION VERIFICATION"
echo "================================================================================"

# 1. Verify isolated Gradle cache resolved published viet-template jars from Central
gradle_vt_jars=()
while IFS= read -r -d '' jar; do
  gradle_vt_jars+=("$jar")
done < <(find "$GRADLE_HOME/caches" -name "viet-template-*.jar" -print0 2>/dev/null || true)

if [ ${#gradle_vt_jars[@]} -eq 0 ]; then
  echo "PROVENANCE FAILURE: No viet-template-*.jar resolved in isolated Gradle home $GRADLE_HOME" >&2
  exit 1
fi

# 2. Verify isolated Maven cache resolved published viet-template jars from Central
m2_vt_jars=()
while IFS= read -r -d '' jar; do
  m2_vt_jars+=("$jar")
done < <(find "$M2_REPO" -name "viet-template-*.jar" -print0 2>/dev/null || true)

if [ ${#m2_vt_jars[@]} -eq 0 ]; then
  echo "PROVENANCE FAILURE: No viet-template-*.jar resolved in isolated Maven repository $M2_REPO" >&2
  exit 1
fi

# 3. Check that no local reactor, workspace files, or rc-repository were referenced
forbidden_references=()
for fixture in spring-boot-mvc spring-boot-security quarkus; do
  fix_dir="$FIXTURES_DIR/$fixture"
  for target_dir in "$fix_dir/target" "$fix_dir/build"; do
    if [ -d "$target_dir" ]; then
      if grep -rq "build/rc-repository" "$target_dir" 2>/dev/null; then
        forbidden_references+=("$fixture referenced build/rc-repository")
      fi
      if grep -rqE "viet-template-[a-z-]+/(target|build)/" "$target_dir" 2>/dev/null; then
        forbidden_references+=("$fixture referenced local reactor build artifacts")
      fi
      if grep -rq "${HOME}/.m2/repository" "$target_dir" 2>/dev/null; then
        forbidden_references+=("$fixture referenced user local maven repository ~/.m2")
      fi
    fi
  done
done

if [ ${#forbidden_references[@]} -gt 0 ]; then
  echo "PROVENANCE FAILURE: Workspace substitution or local reactor leak detected:" >&2
  printf '  - %s\n' "${forbidden_references[@]}" >&2
  exit 1
fi

# 4. Output required diagnostic headers and summary
echo "Viet Template version resolved: $VERSION"
echo "Repository source: Maven Central"
echo "Workspace substitution: disabled"
echo "Local repository fallback: disabled"
echo "--------------------------------------------------------------------------------"
echo "Resolved isolated Gradle artifacts (${#gradle_vt_jars[@]} jars):"
for jar in "${gradle_vt_jars[@]}"; do
  echo "  - $(basename "$jar")"
done
echo "Resolved isolated Maven artifacts (${#m2_vt_jars[@]} jars):"
for jar in "${m2_vt_jars[@]}"; do
  echo "  - $(basename "$jar")"
done
echo "================================================================================"
echo "ALL PUBLIC FRAMEWORK CONSUMER FIXTURES VERIFIED SUCCESSFULLY"
echo "================================================================================"
