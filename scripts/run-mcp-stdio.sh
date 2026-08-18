#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
MAVEN_COMMAND=${MVN_BIN:-mvn}
CLASSPATH_FILE="$PROJECT_DIR/target/mcp-classpath.txt"

"$MAVEN_COMMAND" -q -f "$PROJECT_DIR/pom.xml" -DskipTests compile \
  dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE" 1>&2

PROJECT_CLASSPATH="$PROJECT_DIR/target/classes:$(tr -d '\r\n' < "$CLASSPATH_FILE")"
exec java -cp "$PROJECT_CLASSPATH" \
  cn.bugstack.application.external.mcp.McpStdioServerMain "$@"
