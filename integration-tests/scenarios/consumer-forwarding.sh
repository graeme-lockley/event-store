#!/usr/bin/env bash
# Test event forwarding to consumers

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_HELPER_DIR="$(cd "$SCRIPT_DIR/../helpers" && pwd)"
_PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

source "$_HELPER_DIR/cli.sh"
source "$_HELPER_DIR/assertions.sh"

# Helper to start consumer listener
start_consumer_listener() {
    local port=$1
    local data_file=$2
    local silent=${3:-false}
    
    # Kill any existing process on this port
    lsof -ti:$port | xargs kill -9 2>/dev/null || true
    sleep 0.5
    
    # Build command arguments array
    local args=("consumer" "listen" "--port" "$port")
    if [ -n "$data_file" ]; then
        args+=("--data-file" "$data_file")
    fi
    if [ "$silent" = "true" ]; then
        args+=("--silent")
    fi
    
    # Start the listener in background
    local log_file="/tmp/listener-$port.log"
    "$CLI_BIN" "${args[@]}" > "$log_file" 2>&1 &
    local pid=$!
    
    # Wait for server to be ready
    local elapsed=0
    while [ $elapsed -lt 10 ]; do
        # Check if process is still running
        if ! ps -p "$pid" > /dev/null 2>&1; then
            echo "    Listener process died. Logs:" >&2
            cat "$log_file" >&2 2>/dev/null || true
            return 1
        fi
        
        if curl -sf "http://127.0.0.1:$port/health" > /dev/null 2>&1; then
            # Give it a moment to fully initialize
            sleep 0.5
            echo "$pid"
            return 0
        fi
        sleep 0.5
        elapsed=$((elapsed + 1))
    done
    
    # If health check failed, kill the process and show logs
    kill "$pid" 2>/dev/null || true
    echo "    Listener failed health check. Logs:" >&2
    cat "$log_file" >&2 2>/dev/null || true
    return 1
}

# Helper to stop consumer listener
stop_consumer_listener() {
    local pid=$1
    if [ -n "$pid" ]; then
        kill "$pid" 2>/dev/null || true
        wait "$pid" 2>/dev/null || true
    fi
}

# Helper to get webhook calls from file
get_webhook_calls() {
    local data_file=$1
    if [ ! -f "$data_file" ]; then
        echo "[]"
        return
    fi
    # Check if file is readable and has content
    if [ ! -r "$data_file" ]; then
        echo "[]"
        return
    fi
    local content=$(cat "$data_file" 2>/dev/null || echo "[]")
    if [ -z "$content" ]; then
        echo "[]"
        return
    fi
    echo "$content"
}

# Helper to wait for webhook calls
wait_for_webhook_calls() {
    local data_file=$1
    local expected_count=$2
    local timeout=${3:-15}
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        local count=$(get_webhook_calls "$data_file" | jq 'length' 2>/dev/null || echo "0")
        if [ "$count" -ge "$expected_count" ]; then
            return 0
        fi
        sleep 0.5
        elapsed=$((elapsed + 1))
    done
    
    return 1
}

test_event_forwarding_after_registration() {
    echo "  Test: Event forwarding after consumer registration"
    
    local topic_name="test-topic-forward-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    local webhook_port=19000
    local webhook_data_file="/tmp/webhook-calls-$(date +%s).json"
    local webhook_url="http://localhost:$webhook_port/webhook"
    
    # Clean up any existing file
    rm -f "$webhook_data_file"
    
    # Create topic
    echo "    Creating topic: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Start consumer listener
    echo "    Starting consumer listener on port $webhook_port"
    local listener_pid=$(start_consumer_listener $webhook_port "$webhook_data_file" true)
    if [ -z "$listener_pid" ]; then
        echo "    Failed to start consumer listener"
        return 1
    fi
    
    # Ensure cleanup
    trap "stop_consumer_listener $listener_pid; rm -f $webhook_data_file" EXIT
    
    # Register consumer
    echo "    Registering consumer..."
    local register_output=$(es_json consumer register \
        --callback "$webhook_url" \
        --topics "$topic_name:null" 2>&1)
    
    local consumer_id=$(echo "$register_output" | jq -r '.consumerId' 2>/dev/null)
    if [ -z "$consumer_id" ] || [ "$consumer_id" = "null" ]; then
        echo "    Failed to register consumer"
        echo "    Output: $register_output"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    Consumer registered with ID: $consumer_id"
    
    # Verify consumer is registered and check callback URL
    local consumer_list=$(es_json consumer list)
    if ! echo "$consumer_list" | jq -e "[.consumers[] | select(.id == \"$consumer_id\")] | length > 0" > /dev/null; then
        echo "    Warning: Consumer not found in list after registration"
    else
        local consumer_details=$(echo "$consumer_list" | jq -c "[.consumers[] | select(.id == \"$consumer_id\")][0]" 2>/dev/null)
        local registered_callback=$(echo "$consumer_details" | jq -r '.callback' 2>/dev/null)
        local topics=$(echo "$consumer_details" | jq -c '.topics' 2>/dev/null)
        echo "    Registered callback URL: $registered_callback"
        echo "    Expected callback URL: $webhook_url"
        echo "    Consumer topics: $topics"
        if [ "$registered_callback" != "$webhook_url" ]; then
            echo "    WARNING: Callback URL mismatch!"
        fi
    fi
    
    # Check health to see if dispatcher is running
    local health=$(es_json health show)
    local running_dispatchers=$(echo "$health" | jq -r '.runningDispatchers[]?' 2>/dev/null || echo "")
    echo "    Running dispatchers: $running_dispatchers"
    
    # Give dispatcher time to pick up the consumer and start
    echo "    Waiting for dispatcher to start and register consumer..."
    # Wait for dispatcher to be running and consumer to be registered
    local max_wait=15
    local waited=0
    while [ $waited -lt $max_wait ]; do
        local health=$(es_json health show 2>/dev/null)
        local dispatchers=$(echo "$health" | jq -r '.runningDispatchers[]?' 2>/dev/null | grep -q "$topic_name" && echo "found" || echo "not found")
        if [ "$dispatchers" = "found" ]; then
            # Also verify consumer is still registered
            local consumer_check=$(es_json consumer list 2>/dev/null)
            if echo "$consumer_check" | jq -e "[.consumers[] | select(.id == \"$consumer_id\")] | length > 0" > /dev/null; then
                echo "    Dispatcher is running and consumer is registered"
                break
            fi
        fi
        sleep 1
        waited=$((waited + 1))
    done
    # Give dispatcher time to process the consumer registration
    # The dispatcher checks every 500ms, so wait for at least one check cycle
    sleep 3  # Increased wait time
    
    # Publish event
    echo "    Publishing event..."
    local event_payload='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"1","name":"Alice","email":"alice@example.com"}}'
    local publish_output=$(es_json event publish --json "[$event_payload]")
    local published_event_id=$(echo "$publish_output" | jq -r '.eventIds[0]' 2>/dev/null)
    
    if [ -z "$published_event_id" ] || [ "$published_event_id" = "null" ]; then
        echo "    Failed to publish event"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    Published event ID: $published_event_id"
    
    # Verify event exists in the store
    local events_list=$(es_json event list "$topic_name" --limit 10 2>/dev/null)
    local event_count=$(echo "$events_list" | jq '.events | length' 2>/dev/null || echo "0")
    echo "    Events in store: $event_count"
    
    # Wait for webhook call (give more time for dispatcher to process)
    echo "    Waiting for webhook call..."
    # Give dispatcher a moment to process after publishing
    # The dispatcher checks every 500ms, and notifyEventsPublished should trigger immediate check
    # But give it a bit of time for the async operation
    sleep 5  # Increased wait time
    if ! wait_for_webhook_calls "$webhook_data_file" 1 35; then
        echo "    Webhook was not called within timeout"
        echo "    Data file: $webhook_data_file"
        echo "    File exists: $([ -f "$webhook_data_file" ] && echo "yes" || echo "no")"
        if [ -f "$webhook_data_file" ]; then
            echo "    File content: $(cat "$webhook_data_file" 2>/dev/null || echo "unreadable")"
        fi
        echo "    Checking if listener is still running..."
        if ps -p "$listener_pid" > /dev/null 2>&1; then
            echo "    Listener process is running (PID: $listener_pid)"
        else
            echo "    Listener process is NOT running!"
        fi
        echo "    Testing webhook endpoint directly..."
        curl -X POST "http://127.0.0.1:$webhook_port/webhook" \
             -H "Content-Type: application/json" \
             -d '{"test":"direct"}' > /dev/null 2>&1 && echo "    Direct POST succeeded" || echo "    Direct POST failed"
        sleep 1
        if [ -f "$webhook_data_file" ]; then
            echo "    Calls after direct test: $(get_webhook_calls "$webhook_data_file" | jq 'length' 2>/dev/null || echo "0")"
        fi
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Verify webhook call
    local calls=$(get_webhook_calls "$webhook_data_file")
    local call_count=$(echo "$calls" | jq 'length')
    
    if [ "$call_count" -ne 1 ]; then
        echo "    Expected 1 webhook call, got $call_count"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    local first_call_payload=$(echo "$calls" | jq -c '.[0].payload')
    
    if ! assert_json_contains "$first_call_payload" '.consumerId' "$consumer_id"; then
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    if ! assert_json_array_length "$first_call_payload" '.events' '1'; then
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    if ! assert_json_contains "$first_call_payload" '.events[0].id' "$published_event_id"; then
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    if ! assert_json_contains "$first_call_payload" '.events[0].type' "user.created"; then
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    ✓ Event forwarding test passed"
    stop_consumer_listener "$listener_pid"
    trap - EXIT
    rm -f "$webhook_data_file"
    return 0
}

test_consumer_catchup() {
    echo "  Test: Consumer catchup with existing events"
    
    local topic_name="test-topic-catchup-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    local webhook_port=19001
    local webhook_data_file="/tmp/webhook-catchup-$(date +%s).json"
    local webhook_url="http://localhost:$webhook_port/webhook"
    
    # Clean up any existing file
    rm -f "$webhook_data_file"
    
    # Create topic
    echo "    Creating topic: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Publish events BEFORE registering consumer
    echo "    Publishing 3 events before consumer registration..."
    local event1='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"1","name":"User1"}}'
    local event2='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"2","name":"User2"}}'
    local event3='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"3","name":"User3"}}'
    
    local publish_output=$(es_json event publish --json "[$event1,$event2,$event3]")
    local event_id1=$(echo "$publish_output" | jq -r '.eventIds[0]' 2>/dev/null)
    local event_id2=$(echo "$publish_output" | jq -r '.eventIds[1]' 2>/dev/null)
    local event_id3=$(echo "$publish_output" | jq -r '.eventIds[2]' 2>/dev/null)
    
    # Start consumer listener
    echo "    Starting consumer listener on port $webhook_port"
    local listener_pid=$(start_consumer_listener $webhook_port "$webhook_data_file" true)
    if [ -z "$listener_pid" ]; then
        echo "    Failed to start consumer listener"
        return 1
    fi
    
    # Ensure cleanup
    trap "stop_consumer_listener $listener_pid; rm -f $webhook_data_file" EXIT
    
    # Register consumer AFTER events are published
    echo "    Registering consumer (should catch up on existing events)..."
    local register_output=$(es_json consumer register \
        --callback "$webhook_url" \
        --topics "$topic_name:null" 2>&1)
    
    local consumer_id=$(echo "$register_output" | jq -r '.consumerId' 2>/dev/null)
    if [ -z "$consumer_id" ] || [ "$consumer_id" = "null" ]; then
        echo "    Failed to register consumer"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    Consumer registered with ID: $consumer_id"
    
    # Verify consumer is registered
    local consumer_list=$(es_json consumer list 2>/dev/null)
    local consumer_details=$(echo "$consumer_list" | jq -c "[.consumers[] | select(.id == \"$consumer_id\")][0]" 2>/dev/null)
    local topics=$(echo "$consumer_details" | jq -c '.topics' 2>/dev/null)
    echo "    Consumer topics: $topics"
    
    # Give dispatcher time to catch up (needs to process all 3 events)
    # Dispatcher checks every 500ms, so give it time to process
    echo "    Waiting for dispatcher to start..."
    local max_wait=15
    local waited=0
    while [ $waited -lt $max_wait ]; do
        local health=$(es_json health show 2>/dev/null)
        local dispatchers=$(echo "$health" | jq -r '.runningDispatchers[]?' 2>/dev/null | grep -q "$topic_name" && echo "found" || echo "not found")
        if [ "$dispatchers" = "found" ]; then
            echo "    Dispatcher is running"
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    
    # Verify listener is still running
    if ! ps -p "$listener_pid" > /dev/null 2>&1; then
        echo "    ERROR: Listener process died!"
        local log_file="/tmp/listener-$webhook_port.log"
        if [ -f "$log_file" ]; then
            echo "    Listener logs:"
            cat "$log_file"
        fi
        return 1
    fi
    
    # Test webhook endpoint directly to ensure it's working
    echo "    Testing webhook endpoint..."
    local test_response=$(curl -s -X POST "http://localhost:$webhook_port/test" \
        -H "Content-Type: application/json" \
        -d '{"test":"direct"}' 2>&1)
    sleep 1
    if [ -f "$webhook_data_file" ]; then
        local test_calls=$(get_webhook_calls "$webhook_data_file")
        local test_count=$(echo "$test_calls" | jq 'length' 2>/dev/null || echo "0")
        if [ "$test_count" -gt 0 ]; then
            echo "    ✓ Webhook endpoint is working (received $test_count test call(s))"
            # Clear test calls
            echo "[]" > "$webhook_data_file"
        else
            echo "    WARNING: Webhook endpoint test failed - no calls received"
            echo "    Webhook file: $webhook_data_file"
            echo "    File exists: $([ -f "$webhook_data_file" ] && echo "yes" || echo "no")"
        fi
    else
        echo "    WARNING: Webhook data file not created: $webhook_data_file"
    fi
    
    # Check if consumer is still registered (might have been removed after failed webhook calls)
    local consumer_check=$(es_json consumer list 2>/dev/null)
    local consumer_still_registered=$(echo "$consumer_check" | jq -e "[.consumers[] | select(.id == \"$consumer_id\")] | length > 0" > /dev/null 2>&1 && echo "yes" || echo "no")
    echo "    Consumer still registered: $consumer_still_registered"
    if [ "$consumer_still_registered" = "no" ]; then
        echo "    WARNING: Consumer was removed - likely due to failed webhook calls"
    fi
    
    # Give dispatcher time to process all existing events
    # When consumer is registered with null, it should receive all events from the beginning
    # The dispatcher checks every 500ms, and needs to process 3 events
    echo "    Waiting for dispatcher to process catchup events (up to 20 seconds)..."
    
    # Wait for webhook calls (should receive all 3 events)
    # Check periodically if events are still in store
    local check_count=0
    while [ $check_count -lt 40 ]; do  # 40 * 0.5 = 20 seconds max
        # Check if we have webhook calls
        if [ -f "$webhook_data_file" ]; then
            local calls=$(get_webhook_calls "$webhook_data_file")
            local call_count=$(echo "$calls" | jq 'length' 2>/dev/null || echo "0")
            if [ "$call_count" -gt 0 ]; then
                # Count total events received
                local total_events=0
                for i in $(seq 0 $((call_count - 1))); do
                    local call_payload=$(echo "$calls" | jq -c ".[$i].payload" 2>/dev/null)
                    local call_events=$(echo "$call_payload" | jq '.events | length' 2>/dev/null || echo "0")
                    total_events=$((total_events + call_events))
                done
                if [ "$total_events" -ge 3 ]; then
                    echo "    ✓ Received $total_events events via webhook"
                    break
                fi
            fi
        fi
        
        # Check if events are still in store (if 0, they've been consumed)
        local events_list=$(es_json event list "$topic_name" --limit 10 2>/dev/null)
        local event_count=$(echo "$events_list" | jq '.events | length' 2>/dev/null || echo "0")
        if [ "$check_count" -eq 0 ] || [ $((check_count % 10)) -eq 0 ]; then
            echo "    Events in store: $event_count (check $check_count/40)"
        fi
        
        sleep 0.5
        check_count=$((check_count + 1))
    done
    
    if [ "$check_count" -ge 40 ]; then
        echo "    Webhook was not called within timeout"
        echo "    Data file: $webhook_data_file"
        echo "    File exists: $([ -f "$webhook_data_file" ] && echo "yes" || echo "no")"
        if [ -f "$webhook_data_file" ]; then
            echo "    File content: $(cat "$webhook_data_file" 2>/dev/null || echo "unreadable")"
        fi
        echo "    Calls: $(get_webhook_calls "$webhook_data_file")"
        echo "    Listener PID: $listener_pid"
        echo "    Listener running: $(ps -p "$listener_pid" > /dev/null 2>&1 && echo "yes" || echo "no")"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Verify webhook call contains all 3 events
    local calls=$(get_webhook_calls "$webhook_data_file")
    local call_count=$(echo "$calls" | jq 'length')
    
    if [ "$call_count" -lt 1 ]; then
        echo "    Expected at least 1 webhook call, got $call_count"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Check the first call (should contain all events or be batched)
    local first_call_payload=$(echo "$calls" | jq -c '.[0].payload')
    local events_count=$(echo "$first_call_payload" | jq '.events | length' 2>/dev/null || echo "0")
    
    # The events might be in one call or multiple calls, but should total 3
    local total_events=0
    for i in $(seq 0 $((call_count - 1))); do
        local call_payload=$(echo "$calls" | jq -c ".[$i].payload")
        local call_events=$(echo "$call_payload" | jq '.events | length' 2>/dev/null || echo "0")
        total_events=$((total_events + call_events))
    done
    
    if [ "$total_events" -ne 3 ]; then
        echo "    Expected 3 events total, got $total_events"
        echo "    Calls: $calls"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Verify event IDs are present
    local all_events=$(echo "$calls" | jq -c '[.[].payload.events[]]')
    if ! echo "$all_events" | jq -e "[.[] | select(.id == \"$event_id1\")] | length > 0" > /dev/null; then
        echo "    Event ID $event_id1 not found in webhook calls"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    ✓ Consumer catchup test passed"
    stop_consumer_listener "$listener_pid"
    trap - EXIT
    rm -f "$webhook_data_file"
    return 0
}

test_consumer_with_last_event_id() {
    echo "  Test: Consumer with lastEventId receives only subsequent events"
    
    local topic_name="test-topic-lastid-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    local webhook_port=19002
    local webhook_data_file="/tmp/webhook-lastid-$(date +%s).json"
    local webhook_url="http://localhost:$webhook_port/webhook"
    
    # Clean up any existing file
    rm -f "$webhook_data_file"
    
    # Create topic
    echo "    Creating topic: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Publish 3 events
    echo "    Publishing 3 events..."
    local event_a='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"A","name":"User A","email":"a@example.com"}}'
    local event_b='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"B","name":"User B","email":"b@example.com"}}'
    local event_c='{"topic":"'$topic_name'","type":"user.created","payload":{"id":"C","name":"User C","email":"c@example.com"}}'
    
    local publish_output=$(es_json event publish --json "[$event_a,$event_b,$event_c]")
    local event_a_id=$(echo "$publish_output" | jq -r '.eventIds[0]' 2>/dev/null)
    local event_b_id=$(echo "$publish_output" | jq -r '.eventIds[1]' 2>/dev/null)
    local event_c_id=$(echo "$publish_output" | jq -r '.eventIds[2]' 2>/dev/null)
    
    if [ -z "$event_a_id" ] || [ "$event_a_id" = "null" ] || \
       [ -z "$event_b_id" ] || [ "$event_b_id" = "null" ] || \
       [ -z "$event_c_id" ] || [ "$event_c_id" = "null" ]; then
        echo "    Failed to publish events or extract event IDs"
        echo "    Publish output: $publish_output"
        return 1
    fi
    
    echo "    Published events: $event_a_id, $event_b_id, $event_c_id"
    
    # Start consumer listener
    echo "    Starting consumer listener on port $webhook_port"
    local listener_pid=$(start_consumer_listener $webhook_port "$webhook_data_file" true)
    if [ -z "$listener_pid" ]; then
        echo "    Failed to start consumer listener"
        return 1
    fi
    
    # Ensure cleanup
    trap "stop_consumer_listener $listener_pid; rm -f $webhook_data_file" EXIT
    
    # Register consumer starting from event_b_id (should receive only event_c)
    echo "    Registering consumer with lastEventId: $event_b_id"
    local register_output=$(es_json consumer register \
        --callback "$webhook_url" \
        --topics "$topic_name:$event_b_id" 2>&1)
    
    local consumer_id=$(echo "$register_output" | jq -r '.consumerId' 2>/dev/null)
    if [ -z "$consumer_id" ] || [ "$consumer_id" = "null" ]; then
        echo "    Failed to register consumer"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Give dispatcher time to process
    echo "    Waiting for dispatcher to process..."
    local max_wait=15
    local waited=0
    while [ $waited -lt $max_wait ]; do
        local health=$(es_json health show 2>/dev/null)
        local dispatchers=$(echo "$health" | jq -r '.runningDispatchers[]?' 2>/dev/null | grep -q "$topic_name" && echo "found" || echo "not found")
        if [ "$dispatchers" = "found" ]; then
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    sleep 3  # Give dispatcher time to process events
    
    # Wait for webhook call
    echo "    Waiting for webhook call (expecting 1 event: $event_c_id)..."
    if ! wait_for_webhook_calls "$webhook_data_file" 1 20; then
        echo "    Webhook was not called within timeout"
        echo "    Calls: $(get_webhook_calls "$webhook_data_file")"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Verify webhook call
    local calls=$(get_webhook_calls "$webhook_data_file")
    local call_count=$(echo "$calls" | jq 'length')
    
    if [ "$call_count" -lt 1 ]; then
        echo "    Expected at least 1 webhook call, got $call_count"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    # Count total events received
    local total_events=0
    local found_event_c=false
    local found_event_a=false
    local found_event_b=false
    
    for i in $(seq 0 $((call_count - 1))); do
        local call_payload=$(echo "$calls" | jq -c ".[$i].payload")
        local call_events=$(echo "$call_payload" | jq '.events | length' 2>/dev/null || echo "0")
        total_events=$((total_events + call_events))
        
        # Check which events are present
        local events_json=$(echo "$call_payload" | jq -c '.events')
        if echo "$events_json" | jq -e "[.[] | select(.id == \"$event_a_id\")] | length > 0" > /dev/null; then
            found_event_a=true
        fi
        if echo "$events_json" | jq -e "[.[] | select(.id == \"$event_b_id\")] | length > 0" > /dev/null; then
            found_event_b=true
        fi
        if echo "$events_json" | jq -e "[.[] | select(.id == \"$event_c_id\")] | length > 0" > /dev/null; then
            found_event_c=true
        fi
    done
    
    # Should receive only event_c (after event_b_id)
    if [ "$total_events" -ne 1 ]; then
        echo "    Expected 1 event, got $total_events"
        echo "    Calls: $calls"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    if [ "$found_event_a" = "true" ] || [ "$found_event_b" = "true" ]; then
        echo "    Received events before lastEventId (should only receive event_c)"
        echo "    Calls: $calls"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    if [ "$found_event_c" != "true" ]; then
        echo "    Did not receive event_c (expected after event_b_id)"
        stop_consumer_listener "$listener_pid"
        return 1
    fi
    
    echo "    ✓ Consumer with lastEventId test passed"
    stop_consumer_listener "$listener_pid"
    trap - EXIT
    rm -f "$webhook_data_file"
    return 0
}

# Main test function
test_consumer_forwarding() {
    echo "Testing consumer event forwarding..."
    echo ""
    
    local failed=0
    
    if ! test_event_forwarding_after_registration; then
        failed=$((failed + 1))
    fi
    echo ""
    
    if ! test_consumer_catchup; then
        failed=$((failed + 1))
    fi
    echo ""
    
    if ! test_consumer_with_last_event_id; then
        failed=$((failed + 1))
    fi
    echo ""
    
    if [ $failed -eq 0 ]; then
        echo "✓ All consumer forwarding tests passed"
        return 0
    else
        echo "✗ $failed test(s) failed"
        return 1
    fi
}

# Run tests
test_consumer_forwarding
