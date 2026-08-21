#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_COMMAND="${MAVEN_BIN:-mvn}"

cd "$PROJECT_ROOT"
jq empty src/main/resources/omni-service/1.0/openapi.json
jq empty src/main/resources/omni-service/1.0/capabilities.json
jq empty quota-config.example.json
jq empty webhook-config.example.json
"$MAVEN_COMMAND" -q test
git diff --check

if command -v docker >/dev/null 2>&1 && [[ -f .env ]]; then
  docker compose -f docker-compose.yml config --quiet
  OMNI_OFFICE_DATABASE_PASSWORD=release-check \
    docker compose -f docker-compose.yml -f docker-compose.postgres.yml config --quiet
fi

echo "Release checks passed."
