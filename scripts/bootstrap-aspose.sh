#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORDS_JAR="$PROJECT_ROOT/lib/aspose-words-26.6-jdk17.jar"
DIAGRAM_JAR="$PROJECT_ROOT/lib/aspose-diagram-26.6.jar"

if [[ -s "$WORDS_JAR" && -s "$DIAGRAM_JAR" ]]; then
  echo "Aspose dependencies already exist; preserving local files."
  exit 0
fi

if [[ -e "$WORDS_JAR" || -e "$DIAGRAM_JAR" ]]; then
  echo "Refusing to replace a partial local Aspose dependency set." >&2
  exit 1
fi
if [[ -z "${ASPOSE_WORDS_JAR_URL:-}" || -z "${ASPOSE_DIAGRAM_JAR_URL:-}" ]]; then
  echo "Provide both Aspose JAR files locally or configure the two private artifact URLs." >&2
  exit 1
fi

DEPENDENCY_TEMP="$(mktemp -d)"
trap 'rm -rf "$DEPENDENCY_TEMP"' EXIT
CURL_ARGS=(--fail --location --silent --show-error)
if [[ -n "${ASPOSE_ARTIFACT_TOKEN:-}" ]]; then
  CURL_ARGS+=(--header "Authorization: Bearer $ASPOSE_ARTIFACT_TOKEN")
fi
curl "${CURL_ARGS[@]}" "$ASPOSE_WORDS_JAR_URL" --output "$DEPENDENCY_TEMP/aspose-words.jar"
curl "${CURL_ARGS[@]}" "$ASPOSE_DIAGRAM_JAR_URL" --output "$DEPENDENCY_TEMP/aspose-diagram.jar"
test -s "$DEPENDENCY_TEMP/aspose-words.jar"
test -s "$DEPENDENCY_TEMP/aspose-diagram.jar"
mkdir -p "$PROJECT_ROOT/lib"
install -m 600 "$DEPENDENCY_TEMP/aspose-words.jar" "$WORDS_JAR"
install -m 600 "$DEPENDENCY_TEMP/aspose-diagram.jar" "$DIAGRAM_JAR"
