#!/usr/bin/env bash
# Test consumer lifecycle: register, list, show, delete

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../helpers/cli.sh"
source "$SCRIPT_DIR/../helpers/assertions.sh"

test_consumer_lifecycle() {
    # First, create a topic
    local topic_name="test-topic-consumer-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Creating topic for consumer test: $topic_name"
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    echo "  Registering consumer..."
    local register_output=$(es_json consumer register \
        --callback "http://localhost:3000/webhook" \
        --topics "$topic_name:null" 2>&1)
    
    local consumer_id=$(echo "$register_output" | jq -r '.consumerId' 2>/dev/null)
    
    if [ -z "$consumer_id" ] || [ "$consumer_id" = "null" ]; then
        echo "  Failed to register consumer or get consumer ID"
        echo "  Output: $register_output"
        return 1
    fi
    
    echo "  Consumer ID: $consumer_id"
    
    echo "  Listing consumers..."
    local list_output=$(es_json consumer list)
    
    if ! assert_json_has_key "$list_output" ".consumers[] | select(.id == \"$consumer_id\")"; then
        return 1
    fi
    
    echo "  Showing consumer details..."
    local show_output=$(es_json consumer show "$consumer_id")
    
    if ! assert_json_contains "$show_output" '.id' "$consumer_id"; then
        return 1
    fi
    
    if ! assert_json_contains "$show_output" '.callback' "http://localhost:3000/webhook"; then
        return 1
    fi
    
    echo "  Deleting consumer..."
    local delete_output=$(es_json consumer delete "$consumer_id" 2>&1)
    
    # Check that message contains "unregistered"
    if ! echo "$delete_output" | jq -e '.message | contains("unregistered")' > /dev/null 2>&1; then
        echo "  Delete message assertion failed"
        echo "  Output: $delete_output"
        return 1
    fi
    
    # Verify deletion
    local list_after_delete=$(es_json consumer list)
    if echo "$list_after_delete" | jq -e ".consumers[] | select(.id == \"$consumer_id\")" > /dev/null 2>&1; then
        echo "  Consumer still exists after deletion"
        return 1
    fi
    
    echo "  ✓ Consumer lifecycle test passed"
    return 0
}

test_consumer_list_empty() {
    echo "  Testing empty consumer list..."
    local list_output=$(es_json consumer list)
    
    if ! assert_json_has_key "$list_output" '.consumers'; then
        return 1
    fi
    
    echo "  ✓ Empty consumer list test passed"
    return 0
}

# Run tests
test_consumer_list_empty
test_consumer_lifecycle

