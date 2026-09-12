#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

TARGET=""
JAVA_BIN="${JAVA_CMD:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --java)
      JAVA_BIN="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [TARGET_VERSION_OR_PROFILE] [--java <PATH_TO_JAVA>]"
      echo "Example: $0 17"
      echo "         $0 J21-G1"
      echo "         $0 J25-G1-COH --java /opt/jdk-25/bin/java"
      exit 0
      ;;
    *)
      if [[ -z "$TARGET" ]]; then
        TARGET="$1"
        shift
      else
        echo "Unknown argument: $1" >&2
        exit 1
      fi
      ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  TARGET="17"
fi

# Resolve expected version from profile or direct version number
EXPECTED_VERSION=""
if [[ "$TARGET" =~ ^[0-9]+$ ]]; then
  EXPECTED_VERSION="$TARGET"
else
  # Treat as profile name
  if [[ -f "$REPO_ROOT/config/benchmark-runtime-profiles.json" ]]; then
    EXPECTED_VERSION=$(python3 "$SCRIPT_DIR/runtime_profiles.py" --get-version "$TARGET" 2>/dev/null || echo "")
  fi
  if [[ -z "$EXPECTED_VERSION" ]]; then
    echo "Error: Unknown profile or invalid version '$TARGET'" >&2
    exit 1
  fi
fi

if [[ -z "$JAVA_BIN" ]]; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
  else
    echo "Error: No java executable found in PATH or JAVA_HOME" >&2
    exit 1
  fi
fi

if [[ ! -x "$JAVA_BIN" ]]; then
  echo "Error: Java binary '$JAVA_BIN' is not executable" >&2
  exit 1
fi

JAVA_RESOLVED=$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")

# Probe Java metadata
VERSION_OUTPUT=$("$JAVA_BIN" -version 2>&1)
FULL_VERSION=$(echo "$VERSION_OUTPUT" | awk -F '"' '/version/ {print $2}')
VM_NAME=$(echo "$VERSION_OUTPUT" | head -n 3 | grep -E "VM|Runtime" | head -n 1 | sed -e 's/^[[:space:]]*//')

# Extract major version
# Handles "17.0.2", "21.0.12", "25-ea", etc.
DETECTED_MAJOR=$(echo "$FULL_VERSION" | sed -E 's/^1\.//' | sed -E 's/^([0-9]+).*/\1/')

PROPS_OUTPUT=$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 || true)
VENDOR=$(echo "$PROPS_OUTPUT" | awk -F '=' '/java.vendor =/ {print $2}' | sed -e 's/^[[:space:]]*//' | head -n 1)
ARCH=$(echo "$PROPS_OUTPUT" | awk -F '=' '/os.arch =/ {print $2}' | sed -e 's/^[[:space:]]*//' | head -n 1)

if [[ -z "$VENDOR" ]]; then VENDOR="Unknown"; fi
if [[ -z "$ARCH" ]]; then ARCH="$(uname -m 2>/dev/null || echo 'Unknown')"; fi

echo "============================================================"
echo "Java Runtime Validation"
echo "============================================================"
echo "  Target Profile / Spec : $TARGET"
echo "  Expected Major Version: $EXPECTED_VERSION"
echo "  Detected Major Version: $DETECTED_MAJOR"
echo "  Full Version String   : $FULL_VERSION"
echo "  Java Binary Path      : $JAVA_BIN ($JAVA_RESOLVED)"
echo "  JVM Vendor            : $VENDOR"
echo "  JVM Name              : $VM_NAME"
echo "  Architecture          : $ARCH"
echo "------------------------------------------------------------"

if [[ "$DETECTED_MAJOR" -ne "$EXPECTED_VERSION" ]]; then
  echo "STATUS: MISMATCH! Expected Java major version $EXPECTED_VERSION, but found $DETECTED_MAJOR." >&2
  exit 1
else
  echo "STATUS: PASSED (Java major version matches $EXPECTED_VERSION)"
fi
