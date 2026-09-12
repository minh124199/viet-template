#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

JFR_FILE=""
OUTPUT_DIR=""
SPECIFIC_VIEW=""
JFR_BIN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir|-o)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --view|-v)
      SPECIFIC_VIEW="$2"
      shift 2
      ;;
    --jfr)
      JFR_BIN="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 <recording.jfr> [OPTIONS]"
      echo "Options:"
      echo "  --output-dir <DIR>      Directory to write view report text files"
      echo "  --view <VIEW>           Extract only a specific view (e.g. hot-methods, allocation-by-class)"
      echo "  --jfr <PATH>            Override path to jfr binary (JDK 25)"
      exit 0
      ;;
    *)
      if [[ -z "$JFR_FILE" && ! "$1" =~ ^- ]]; then
        JFR_FILE="$1"
        shift
      else
        echo "Unknown argument: $1" >&2
        exit 1
      fi
      ;;
  esac
done

if [[ -z "$JFR_FILE" ]]; then
  echo "Error: No JFR recording file specified." >&2
  echo "Usage: $0 <recording.jfr> [--output-dir <DIR>]" >&2
  exit 1
fi

if [[ ! -f "$JFR_FILE" ]]; then
  echo "Error: JFR file not found: $JFR_FILE" >&2
  exit 1
fi

# Locate JDK 25 jfr tool
REQUIRED_VERSION="25"
if [[ -z "$JFR_BIN" ]]; then
  CANDIDATES=(
    "${JAVA_HOME:-}/bin/jfr"
    "${HOME}/opt/jdk${REQUIRED_VERSION}-pkg/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/jfr"
    "${HOME}/opt/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/jfr"
    "/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk/bin/jfr"
    "/usr/lib/jvm/java-${REQUIRED_VERSION}-openjdk-amd64/bin/jfr"
    "/usr/lib/jvm/temurin-${REQUIRED_VERSION}-jdk/bin/jfr"
    "$(command -v jfr 2>/dev/null || echo "")"
  )
  for cand in "${CANDIDATES[@]}"; do
    if [[ -n "$cand" && -x "$cand" ]]; then
      VER=$("$cand" --version 2>&1 | awk -F '.' '{print $1}' | sed -E 's/[^0-9]//g')
      if [[ "$VER" == "$REQUIRED_VERSION" ]]; then
        JFR_BIN="$cand"
        break
      fi
    fi
  done
fi

if [[ -z "$JFR_BIN" ]]; then
  for cand in "${CANDIDATES[@]}"; do
    if [[ -n "$cand" && -x "$cand" ]]; then
      JFR_BIN="$cand"
      break
    fi
  done
fi

if [[ -z "$JFR_BIN" || ! -x "$JFR_BIN" ]]; then
  echo "Error: Could not locate JDK 25 'jfr' tool. Specify with --jfr <PATH>" >&2
  exit 1
fi

VIEWS=(
  "hot-methods"
  "allocation-by-class"
  "contention-by-site"
  "gc-pauses"
  "thread-allocation"
  "pinned-threads"
)

if [[ -n "$SPECIFIC_VIEW" ]]; then
  VIEWS=("$SPECIFIC_VIEW")
fi

echo "============================================================"
echo "JFR Recording Diagnostic Summary"
echo "============================================================"
echo "  JFR File  : $JFR_FILE ($(du -h "$JFR_FILE" | cut -f1))"
echo "  JFR Tool  : $JFR_BIN"
if [[ -n "$OUTPUT_DIR" ]]; then
  echo "  Output Dir: $OUTPUT_DIR"
fi
echo "============================================================"

if [[ -n "$OUTPUT_DIR" ]]; then
  mkdir -p "$OUTPUT_DIR"
  FULL_REPORT="$OUTPUT_DIR/jfr-full-report.txt"
  echo "=== JFR RECORDING SUMMARY ===" > "$FULL_REPORT"
  "$JFR_BIN" summary "$JFR_FILE" | tee "$OUTPUT_DIR/summary.txt" >> "$FULL_REPORT"

  for v in "${VIEWS[@]}"; do
    VIEW_FILE="$OUTPUT_DIR/${v}.txt"
    echo "" >> "$FULL_REPORT"
    echo "=== JFR VIEW: $v ===" >> "$FULL_REPORT"
    echo "Extracting view: $v -> $VIEW_FILE"
    set +e
    "$JFR_BIN" view "$v" "$JFR_FILE" > "$VIEW_FILE" 2>&1
    EXIT_V=$?
    set -e
    if [[ $EXIT_V -eq 0 ]]; then
      cat "$VIEW_FILE" >> "$FULL_REPORT"
    else
      echo "  (View '$v' returned no events or is unsupported in this recording)" >> "$FULL_REPORT"
    fi
  done
  echo "Summary and views successfully written to: $OUTPUT_DIR"
else
  echo "--- JFR SUMMARY ---"
  "$JFR_BIN" summary "$JFR_FILE"

  for v in "${VIEWS[@]}"; do
    echo ""
    echo "--- JFR VIEW: $v ---"
    set +e
    "$JFR_BIN" view "$v" "$JFR_FILE" 2>&1 || true
    set -e
  done
fi
