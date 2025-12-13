#!/usr/bin/env bash
# Event Store server management for integration tests

_HELPER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_PROJECT_ROOT="$(cd "$_HELPER_DIR/../.." && pwd)"

EVENT_STORE_PORT=18000
EVENT_STORE_DATA_DIR="$_HELPER_DIR/../test-data/event-store-data"
EVENT_STORE_CONFIG_DIR="$_HELPER_DIR/../test-data/event-store-config"
EVENT_STORE_JAR="$_PROJECT_ROOT/event-store/build/libs/event-store-1.0.0.jar"
EVENT_STORE_PID=""

start_event_store() {
    # Clean up old test data
    rm -rf "$EVENT_STORE_DATA_DIR" "$EVENT_STORE_CONFIG_DIR"
    mkdir -p "$EVENT_STORE_DATA_DIR" "$EVENT_STORE_CONFIG_DIR"
    
    # Start server in background
    PORT=$EVENT_STORE_PORT \
    DATA_DIR="$EVENT_STORE_DATA_DIR" \
    CONFIG_DIR="$EVENT_STORE_CONFIG_DIR" \
    java -jar "$EVENT_STORE_JAR" \
        > /dev/null 2>&1 &
    
    EVENT_STORE_PID=$!
    
    # Wait for server to be ready
    if ! wait_for_health "http://localhost:$EVENT_STORE_PORT/health" 30; then
        echo "Event store server failed to start"
        if [ -n "$EVENT_STORE_PID" ]; then
            kill "$EVENT_STORE_PID" 2>/dev/null || true
        fi
        return 1
    fi
    
    # Export for use in other scripts
    export EVENT_STORE_PID
    export SERVER_URL="http://localhost:$EVENT_STORE_PORT"
    
    return 0
}

stop_event_store() {
    if [ -n "$EVENT_STORE_PID" ]; then
        kill "$EVENT_STORE_PID" 2>/dev/null || true
        wait "$EVENT_STORE_PID" 2>/dev/null || true
    fi
    rm -rf "$EVENT_STORE_DATA_DIR" "$EVENT_STORE_CONFIG_DIR"
}

wait_for_health() {
    local url=$1
    local timeout=$2
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    
    return 1
}

# Export functions
export -f start_event_store
export -f stop_event_store
export -f wait_for_health

