#!/usr/bin/env bash
# Test event querying: list events, show event details

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../helpers/cli.sh"
source "$SCRIPT_DIR/../helpers/assertions.sh"

test_event_listing() {
    # Create a topic
    local topic_name="test-topic-events-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Creating topic for event test: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Publish events via CLI
    echo "  Publishing test events..."
    local events_json='[{"topic":"'$topic_name'","type":"user.created","payload":{"id":"1","name":"Alice","email":"alice@example.com"}},{"topic":"'$topic_name'","type":"user.created","payload":{"id":"2","name":"Bob","email":"bob@example.com"}}]'
    
    es_json event publish --json "$events_json" > /dev/null
    
    # Wait a moment for events to be processed
    sleep 1
    
    echo "  Listing events..."
    local list_output=$(es_json event list "$topic_name")
    
    if ! assert_json_array_length "$list_output" '.events' '2'; then
        echo "  Expected 2 events, got:"
        echo "$list_output" | jq '.events | length'
        return 1
    fi
    
    # Test with limit
    echo "  Testing event list with limit..."
    local list_limit_output=$(es_json event list "$topic_name" --limit 1)
    
    if ! assert_json_array_length "$list_limit_output" '.events' '1'; then
        return 1
    fi
    
    # Test CSV output
    echo "  Testing CSV output..."
    local csv_output=$(es_csv event list "$topic_name")
    if ! assert_csv_has_rows "$csv_output" '2'; then
        return 1
    fi
    
    echo "  ✓ Event listing test passed"
    return 0
}

test_event_show() {
    # Create a topic and publish an event
    local topic_name="test-topic-show-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Creating topic for event show test: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Publish event via CLI
    local event_json='[{"topic":"'$topic_name'","type":"user.created","payload":{"id":"1","name":"Alice","email":"alice@example.com"}}]'
    es_json event publish --json "$event_json" > /dev/null
    
    sleep 1
    
    # Get first event ID
    local list_output=$(es_json event list "$topic_name" --limit 1)
    local event_id=$(echo "$list_output" | jq -r '.events[0].id' 2>/dev/null)
    
    if [ -z "$event_id" ] || [ "$event_id" = "null" ]; then
        echo "  Failed to get event ID"
        return 1
    fi
    
    echo "  Showing event: $event_id"
    local show_output=$(es_json event show "$topic_name" "$event_id")
    
    if ! assert_json_contains "$show_output" '.id' "$event_id"; then
        return 1
    fi
    
    if ! assert_json_contains "$show_output" '.type' "user.created"; then
        return 1
    fi
    
    if ! assert_json_has_key "$show_output" '.payload'; then
        return 1
    fi
    
    echo "  ✓ Event show test passed"
    return 0
}

test_event_list_empty() {
    # Create a topic without events
    local topic_name="test-topic-empty-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Testing empty event list..."
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    local list_output=$(es_json event list "$topic_name")
    
    if ! assert_json_array_length "$list_output" '.events' '0'; then
        return 1
    fi
    
    echo "  ✓ Empty event list test passed"
    return 0
}

# Run tests
test_event_list_empty
test_event_listing
test_event_show

