#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAW_SOURCE_DIR="${VIET_QUALIFICATION_SOURCE_DIR:-${ROOT_DIR}}"
if [[ ! -d "${RAW_SOURCE_DIR}" ]]; then
  echo "VIET_QUALIFICATION_SOURCE_DIR is not a directory: ${RAW_SOURCE_DIR}" >&2
  exit 2
fi
SOURCE_DIR="$(cd "${RAW_SOURCE_DIR}" && pwd)"
OUTPUT_DIR="${M18_EVIDENCE_DIR:-${ROOT_DIR}/benchmark-evidence/m18}"
JAVA21_HOME="${JAVA21_HOME:-}"
JAVA25_HOME="${JAVA25_HOME:-}"

for variable in JAVA21_HOME JAVA25_HOME; do
  value="${!variable}"
  [[ -x "${value}/bin/java" ]] || { echo "${variable} must point to a JDK containing bin/java" >&2; exit 2; }
done

TMP_ID_J21="$(mktemp)"
TMP_ID_J25="$(mktemp)"
cleanup() {
  rm -f "${TMP_ID_J21:-}" "${TMP_ID_J25:-}"
}
trap cleanup EXIT

python3 "${SCRIPT_DIR}/jdk_identity.py" \
  --java "${JAVA21_HOME}/bin/java" \
  --major 21 \
  --profile J21-G1 \
  --json-out "${TMP_ID_J21}" || {
    echo "J21-G1 runtime identity validation failed" >&2
    exit 2
  }

python3 "${SCRIPT_DIR}/jdk_identity.py" \
  --java "${JAVA25_HOME}/bin/java" \
  --major 25 \
  --profile J25-G1 \
  --json-out "${TMP_ID_J25}" || {
    echo "J25-G1 runtime identity validation failed" >&2
    exit 2
  }

git -C "${SOURCE_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
  echo "VIET_QUALIFICATION_SOURCE_DIR is not a git work tree: ${SOURCE_DIR}" >&2
  exit 2
}
[[ -z "$(git -C "${SOURCE_DIR}" status --porcelain)" ]] || {
  echo "Formal M18 qualification refuses a dirty product working tree: ${SOURCE_DIR}" >&2
  exit 2
}
[[ -z "$(git -C "${ROOT_DIR}" status --porcelain)" ]] || {
  echo "Formal M18 qualification refuses a dirty tooling working tree: ${ROOT_DIR}" >&2
  exit 2
}

mkdir -p "${OUTPUT_DIR}"
cp "${TMP_ID_J21}" "${OUTPUT_DIR}/runtime-identity-J21-G1.json"
cp "${TMP_ID_J25}" "${OUTPUT_DIR}/runtime-identity-J25-G1.json"

CANDIDATE_SHA="$(git -C "${SOURCE_DIR}" rev-parse HEAD)"
CREATED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "M18 comparative release qualification: ${CANDIDATE_SHA}"

python3 - "${OUTPUT_DIR}/tooling-provenance.json" "${CREATED_AT}" "${ROOT_DIR}" "${SOURCE_DIR}" "${SCRIPT_DIR}" <<'PY'
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

out_file, created_at, root_dir, source_dir, script_dir = sys.argv[1:6]

def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

tooling_sha = subprocess.check_output(["git", "-C", root_dir, "rev-parse", "HEAD"], text=True).strip()
tooling_tree = subprocess.check_output(["git", "-C", root_dir, "rev-parse", "HEAD^{tree}"], text=True).strip()

runner_sha256 = file_sha256(Path(script_dir) / "run-m18-comparative-qualification.sh")
validator_sha256 = file_sha256(Path(script_dir) / "jdk_identity.py")

product_sha = subprocess.check_output(["git", "-C", source_dir, "rev-parse", "HEAD"], text=True).strip()
product_tree = subprocess.check_output(["git", "-C", source_dir, "rev-parse", "HEAD^{tree}"], text=True).strip()

try:
    product_exact_tag = subprocess.check_output(
        ["git", "-C", source_dir, "describe", "--tags", "--exact-match"],
        text=True,
        stderr=subprocess.DEVNULL,
    ).strip() or None
except subprocess.CalledProcessError:
    product_exact_tag = None

product_version = None
pom_path = Path(source_dir) / "pom.xml"
if pom_path.is_file():
    try:
        root_elem = ET.parse(pom_path).getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        v_elem = root_elem.find("m:version", ns)
        if v_elem is None:
            v_elem = root_elem.find("version")
        if v_elem is not None and v_elem.text:
            product_version = v_elem.text.strip()
    except Exception:
        pass

provenance = {
    "schemaVersion": "1.0.0",
    "createdAt": created_at,
    "toolingSha": tooling_sha,
    "toolingTree": tooling_tree,
    "runnerSha256": runner_sha256,
    "jdkIdentityValidatorSha256": validator_sha256,
    "productSourceDir": str(Path(source_dir).resolve()),
    "productSha": product_sha,
    "productTree": product_tree,
    "productExactTag": product_exact_tag,
    "productVersion": product_version,
    "protocol": {
        "forks": 3,
        "warmupIterations": 5,
        "measurementIterations": 10,
        "iterationSeconds": 1,
        "jvmFlags": [
            "-server",
            "-Xms2g",
            "-Xmx2g",
            "-XX:+AlwaysPreTouch",
            "-XX:+UseG1GC",
        ],
        "benchmarkRegex": ".*ComparativeEngineBenchmark.*",
        "profilerGc": True,
    },
}

Path(out_file).write_text(json.dumps(provenance, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

JAVA_HOME="${JAVA25_HOME}" "${SOURCE_DIR}/mvnw" -f "${SOURCE_DIR}/pom.xml" \
  -pl viet-template-benchmarks -am test \
  -Dtest=CrossEngineFixtureCorrectnessTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotless.check.skip=true --no-transfer-progress -B
JAVA_HOME="${JAVA21_HOME}" "${SOURCE_DIR}/mvnw" -f "${SOURCE_DIR}/pom.xml" \
  -pl viet-template-benchmarks -am package -DskipTests -Dspotless.check.skip=true \
  --no-transfer-progress -B

run_profile() {
  local profile="$1"
  local java_home="$2"
  "${SOURCE_DIR}/scripts/record-benchmark-env.sh" \
    --profile "${profile}" --java "${java_home}/bin/java" \
    --output "${OUTPUT_DIR}/environment-${profile}.json"
  "${java_home}/bin/java" -server -Xms2g -Xmx2g -XX:+AlwaysPreTouch -XX:+UseG1GC \
    -jar "${SOURCE_DIR}/viet-template-benchmarks/target/benchmarks.jar" \
    '.*ComparativeEngineBenchmark.*' -f 3 -wi 5 -i 10 -w 1s -r 1s -prof gc \
    -rf json -rff "${OUTPUT_DIR}/comparative-${profile}.json"
}

run_profile J21-G1 "${JAVA21_HOME}"
run_profile J25-G1 "${JAVA25_HOME}"

python3 "${SOURCE_DIR}/scripts/perf/generate-benchmark-report.py" \
  --input-dir "${OUTPUT_DIR}" --output "${OUTPUT_DIR}/report.md" --generated-at "${CREATED_AT}"
python3 "${SOURCE_DIR}/scripts/perf/build-m18-evidence-package.py" \
  --evidence-dir "${OUTPUT_DIR}" --created-at "${CREATED_AT}"
python3 "${SOURCE_DIR}/scripts/perf/verify-m18-evidence.py" \
  --evidence-dir "${OUTPUT_DIR}" --expected-sha "${CANDIDATE_SHA}"
python3 "${SOURCE_DIR}/scripts/perf/generate-benchmark-report.py" \
  --input-dir "${OUTPUT_DIR}" --output "${OUTPUT_DIR}/report.md" \
  --generated-at "${CREATED_AT}" --verify

python3 - "${OUTPUT_DIR}" <<'PY'
import hashlib
import sys
from pathlib import Path

out_dir = Path(sys.argv[1]).resolve()
sums = []
for p in sorted(out_dir.iterdir()):
    if p.is_file() and p.name != "SHA256SUMS":
        h = hashlib.sha256(p.read_bytes()).hexdigest()
        sums.append(f"{h}  {p.name}\n")
(out_dir / "SHA256SUMS").write_text("".join(sums), encoding="utf-8")
PY

python3 "${SOURCE_DIR}/scripts/perf/verify-m18-evidence.py" \
  --evidence-dir "${OUTPUT_DIR}" --expected-sha "${CANDIDATE_SHA}"
