#!/usr/bin/env bash
# CLI wrapper functions for integration tests

_HELPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_PROJECT_ROOT="$(cd "$_HELPER_DIR/../.." && pwd)"

CLI_BIN="$_PROJECT_ROOT/cli/es"
SERVER_URL="${SERVER_URL:-http://localhost:18000}"

# Test tenant/namespace/user/API key (set by setup_test_context)
# Preserve values if already set in environment
TEST_TENANT_ID="${TEST_TENANT_ID:-}"
TEST_NAMESPACE_ID="${TEST_NAMESPACE_ID:-}"
TEST_USER_ID="${TEST_USER_ID:-}"
TEST_API_KEY="${TEST_API_KEY:-}"

# Execute CLI command with server URL
es() {
    local args=("--server-url" "$SERVER_URL")
    
    # Add tenant/namespace if set
    if [ -n "$TEST_TENANT_ID" ]; then
        args+=("--tenant" "$TEST_TENANT_ID")
    fi
    if [ -n "$TEST_NAMESPACE_ID" ]; then
        args+=("--namespace" "$TEST_NAMESPACE_ID")
    fi
    if [ -n "$TEST_API_KEY" ]; then
        args+=("--api-key" "$TEST_API_KEY")
    fi
    
    args+=("$@")
    "$CLI_BIN" "${args[@]}"
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

# Setup test context: create tenant, namespace, user, and API key
# For integration tests, we use the system tenant and management namespace
# which are created during bootstrap, so we can create resources there
setup_test_context() {
    echo "  Setting up test context (tenant, namespace, user, API key)..." >&2
    
    # Wait for server to be ready
    local max_wait=30
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if "$CLI_BIN" --server-url "$SERVER_URL" health show --output json > /dev/null 2>&1; then
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    
    if [ $waited -ge $max_wait ]; then
        echo "  ERROR: Server not ready after ${max_wait}s" >&2
        return 1
    fi
    
    # Wait for projections to process bootstrap events (API key authentication requires user projection)
    echo "  Waiting for projections to process bootstrap events..." >&2
    sleep 2
    
    # Use system tenant and management namespace for creating test resources
    # These are created during bootstrap and don't require authentication
    local system_tenant_id="\$system"
    local management_namespace_id="\$management"
    
    # Get config dir from server helper if available
    local config_dir="${EVENT_STORE_CONFIG_DIR:-$_HELPER_DIR/../test-data/event-store-config}"
    
    # For integration tests, use system tenant and management namespace directly
    # They are created during bootstrap and available for all operations
    TEST_TENANT_ID="$system_tenant_id"
    TEST_NAMESPACE_ID="$management_namespace_id"
    
    # Read test API key from file created by bootstrap (if CREATE_TEST_API_KEY was set)
    local test_api_key_file="$config_dir/test-api-key.txt"
    if [ -f "$test_api_key_file" ]; then
        TEST_API_KEY=$(cat "$test_api_key_file" 2>/dev/null | tr -d '\n\r' || echo "")
        if [ -n "$TEST_API_KEY" ]; then
            echo "  Using test API key from bootstrap" >&2
            TEST_USER_ID="admin-system"
        else
            echo "  WARNING: Test API key file exists but is empty" >&2
            TEST_API_KEY=""
        fi
    else
        echo "  WARNING: Test API key file not found at $test_api_key_file" >&2
        echo "  Make sure CREATE_TEST_API_KEY=true is set when starting the server" >&2
        TEST_API_KEY=""
    fi
    
    echo "  Test context setup complete:" >&2
    echo "    Tenant: $TEST_TENANT_ID" >&2
    echo "    Namespace: $TEST_NAMESPACE_ID" >&2
    if [ -n "$TEST_USER_ID" ]; then
        echo "    User: $TEST_USER_ID" >&2
    fi
    if [ -n "$TEST_API_KEY" ]; then
        echo "    API Key: ${TEST_API_KEY:0:10}..." >&2
    fi
    
    # Export for use in other scripts
    export TEST_TENANT_ID
    export TEST_NAMESPACE_ID
    export TEST_USER_ID
    export TEST_API_KEY
    
    return 0
}

# Export functions
export -f es
export -f es_json
export -f es_csv
export -f es_table
export -f setup_test_context

