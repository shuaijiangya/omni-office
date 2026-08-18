#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MAVEN_EXECUTABLE="${MVN_BIN:-mvn}"
cd "$PROJECT_DIR"
"$MAVEN_EXECUTABLE" -q -DskipTests package dependency:build-classpath \
  -Dmdep.outputFile=target/runtime-classpath.txt >&2
RUNTIME_CLASSPATH="target/classes:$(<target/runtime-classpath.txt):lib/*"
exec java -cp "$RUNTIME_CLASSPATH" \
  cn.bugstack.application.template.governance.TemplateAdminMain "$@"
