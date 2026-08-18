#!/usr/bin/env bash
set -euo pipefail

MCP_URL="${OMNI_OFFICE_MCP_URL:-http://127.0.0.1:8080/mcp}"
API_KEY="${OMNI_OFFICE_API_KEY:-local-dev-key}"

HEADERS_FILE="$(mktemp)"
trap 'rm -f "$HEADERS_FILE"' EXIT

curl --fail-with-body -sS -D "$HEADERS_FILE" "$MCP_URL" \
  -H "X-API-Key: $API_KEY" \
  -H 'Accept: application/json, text/event-stream' \
  -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl-example","version":"1.0"}}}'

SESSION_ID="$(awk 'BEGIN{IGNORECASE=1} /^MCP-Session-Id:/{gsub("\\r", "", $2); print $2}' "$HEADERS_FILE")"
curl --fail-with-body -sS "$MCP_URL" \
  -H "X-API-Key: $API_KEY" -H "MCP-Session-Id: $SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-11-25' \
  -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null

curl --fail-with-body -sS "$MCP_URL" \
  -H "X-API-Key: $API_KEY" -H "MCP-Session-Id: $SESSION_ID" \
  -H 'MCP-Protocol-Version: 2025-11-25' \
  -H 'Accept: application/json, text/event-stream' -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
