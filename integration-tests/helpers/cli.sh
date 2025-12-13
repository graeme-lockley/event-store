#!/usr/bin/env bash
# CLI wrapper functions for integration tests

_HELPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_PROJECT_ROOT="$(cd "$_HELPER_DIR/../.." && pwd)"

CLI_BIN="$_PROJECT_ROOT/cli/es"
SERVER_URL="${SERVER_URL:-http://localhost:18000}"

# Execute CLI command with server URL
es() {
    "$CLI_BIN" --server-url "$SERVER_URL" "$@"
}

# Execute CLI command with JSON output
es_json() {
    es --output json "$@"
}

# Execute CLI command with CSV output
es_csv() {
    es --output csv "$@"
}

# Execute CLI command with table output (default)
es_table() {
    es --output table "$@"
}

# Export functions
export -f es
export -f es_json
export -f es_csv
export -f es_table

