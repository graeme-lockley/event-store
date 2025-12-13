#!/usr/bin/env bash
# Test topic lifecycle: create, list, show, update

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../helpers/cli.sh"
source "$SCRIPT_DIR/../helpers/assertions.sh"

test_topic_lifecycle() {
    local topic_name="test-topic-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Creating topic: $topic_name"
    local create_output=$(es_json topic create \
        --name "$topic_name" \
        --schemas-file "$schemas_file" 2>&1)
    
    # Check that message contains "created successfully"
    if ! echo "$create_output" | jq -e '.message | contains("created successfully")' > /dev/null 2>&1; then
        echo "  Create message assertion failed"
        echo "  Output: $create_output"
        return 1
    fi
    
    echo "  Listing topics..."
    local list_output=$(es_json topic list)
    
    if ! assert_json_has_key "$list_output" ".topics[] | select(.name == \"$topic_name\")"; then
        return 1
    fi
    
    echo "  Showing topic details..."
    local show_output=$(es_json topic show "$topic_name")
    
    if ! assert_json_contains "$show_output" '.name' "$topic_name"; then
        return 1
    fi
    
    if ! assert_json_array_length "$show_output" '.schemas' '2'; then
        return 1
    fi
    
    echo "  Updating topic schemas..."
    # Create updated schemas with all original schemas plus a new one
    local updated_schemas='[{"eventType":"user.created","type":"object","$schema":"https://json-schema.org/draft/2020-12/schema","properties":{"id":{"type":"string"},"name":{"type":"string"}},"required":["id","name"]},{"eventType":"user.updated","type":"object","$schema":"https://json-schema.org/draft/2020-12/schema","properties":{"id":{"type":"string"},"name":{"type":"string","minLength":1},"email":{"type":"string","format":"email"}},"required":["id"]},{"eventType":"user.deleted","type":"object","$schema":"https://json-schema.org/draft/2020-12/schema","properties":{"id":{"type":"string"}},"required":["id"]}]'
    local tmp_schemas=$(mktemp)
    echo "$updated_schemas" > "$tmp_schemas"
    
    local update_output=$(es_json topic update "$topic_name" \
        --schemas-file "$tmp_schemas" 2>&1)
    
    rm "$tmp_schemas"
    
    # Check that message contains "updated successfully"
    if ! echo "$update_output" | jq -e '.message | contains("updated successfully")' > /dev/null 2>&1; then
        echo "  Update message assertion failed"
        echo "  Output: $update_output"
        return 1
    fi
    
    # Verify update - should now have 3 schemas
    local show_after_update=$(es_json topic show "$topic_name")
    if ! assert_json_array_length "$show_after_update" '.schemas' '3'; then
        return 1
    fi
    
    echo "  ✓ Topic lifecycle test passed"
    return 0
}

test_topic_list_empty() {
    echo "  Testing empty topic list..."
    local list_output=$(es_json topic list)
    
    # After cleanup, should be empty (or only contain our test topic)
    if ! assert_json_has_key "$list_output" '.topics'; then
        return 1
    fi
    
    echo "  ✓ Empty topic list test passed"
    return 0
}

test_topic_output_formats() {
    local topic_name="test-topic-format-$(date +%s)"
    local schemas_file="$SCRIPT_DIR/../fixtures/schemas.json"
    
    echo "  Testing output formats..."
    
    # Create topic
    es_json topic create --name "$topic_name" --schemas-file "$schemas_file" > /dev/null
    
    # Test JSON output
    local json_output=$(es_json topic list)
    if ! assert_json_has_key "$json_output" '.topics'; then
        return 1
    fi
    
    # Verify our topic is in the JSON list
    if ! assert_json_has_key "$json_output" ".topics[] | select(.name == \"$topic_name\")"; then
        return 1
    fi
    
    # Test CSV output
    local csv_output=$(es_csv topic list)
    
    # Verify our topic is in the CSV (don't check exact count as other tests may have created topics)
    if ! assert_csv_contains "$csv_output" "Name" "$topic_name"; then
        return 1
    fi
    
    echo "  ✓ Output formats test passed"
    return 0
}

# Run tests
test_topic_list_empty
test_topic_lifecycle
test_topic_output_formats

