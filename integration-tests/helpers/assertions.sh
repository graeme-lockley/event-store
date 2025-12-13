#!/usr/bin/env bash
# Assertion functions for integration tests

# Note: This script doesn't need path variables, but we avoid using SCRIPT_DIR
# to prevent conflicts with the parent script

# Assert that JSON contains a specific value at a path
assert_json_contains() {
    local json="$1"
    local path="$2"
    local expected="$3"
    
    if ! command -v jq &> /dev/null; then
        echo "Error: jq is required for JSON assertions"
        return 1
    fi
    
    local actual=$(echo "$json" | jq -r "$path" 2>/dev/null)
    
    if [ "$actual" != "$expected" ]; then
        echo "Assertion failed: expected '$expected' at path '$path', got '$actual'"
        echo "JSON: $json"
        return 1
    fi
    
    return 0
}

# Assert that JSON array has a specific length
assert_json_array_length() {
    local json="$1"
    local path="$2"
    local expected="$3"
    
    if ! command -v jq &> /dev/null; then
        echo "Error: jq is required for JSON assertions"
        return 1
    fi
    
    local actual=$(echo "$json" | jq -r "$path | length" 2>/dev/null)
    
    if [ "$actual" != "$expected" ]; then
        echo "Assertion failed: expected array length $expected at path '$path', got $actual"
        echo "JSON: $json"
        return 1
    fi
    
    return 0
}

# Assert that JSON contains a key
assert_json_has_key() {
    local json="$1"
    local path="$2"
    
    if ! command -v jq &> /dev/null; then
        echo "Error: jq is required for JSON assertions"
        return 1
    fi
    
    local result=$(echo "$json" | jq -e "$path" > /dev/null 2>&1)
    
    if [ $? -ne 0 ]; then
        echo "Assertion failed: JSON does not contain key at path '$path'"
        echo "JSON: $json"
        return 1
    fi
    
    return 0
}

# Assert that CSV has a specific number of rows (excluding header)
assert_csv_has_rows() {
    local csv="$1"
    local expected_count="$2"
    
    # Count non-empty lines, subtract 1 for header
    local actual_count=$(echo "$csv" | grep -v '^$' | wc -l | tr -d ' ')
    actual_count=$((actual_count - 1))
    
    if [ "$actual_count" -ne "$expected_count" ]; then
        echo "Assertion failed: expected $expected_count rows, got $actual_count"
        echo "CSV:"
        echo "$csv"
        return 1
    fi
    
    return 0
}

# Assert that CSV contains a specific value in a column
assert_csv_contains() {
    local csv="$1"
    local column_name="$2"
    local expected_value="$3"
    
    # Find column index
    local header=$(echo "$csv" | head -n 1)
    local column_index=1
    local found=0
    
    IFS=',' read -ra HEADER_ARRAY <<< "$header"
    for i in "${!HEADER_ARRAY[@]}"; do
        if [ "${HEADER_ARRAY[$i]}" = "$column_name" ]; then
            column_index=$((i + 1))
            found=1
            break
        fi
    done
    
    if [ $found -eq 0 ]; then
        echo "Assertion failed: column '$column_name' not found in CSV"
        return 1
    fi
    
    # Check if value exists in that column
    if ! echo "$csv" | cut -d',' -f"$column_index" | grep -q "^${expected_value}$"; then
        echo "Assertion failed: expected value '$expected_value' in column '$column_name', not found"
        echo "CSV:"
        echo "$csv"
        return 1
    fi
    
    return 0
}

# Assert exit code
assert_exit_code() {
    local actual=$1
    local expected=$2
    
    if [ "$actual" -ne "$expected" ]; then
        echo "Assertion failed: expected exit code $expected, got $actual"
        return 1
    fi
    
    return 0
}

# Assert that command output contains text
assert_output_contains() {
    local output="$1"
    local pattern="$2"
    
    if ! echo "$output" | grep -q "$pattern"; then
        echo "Assertion failed: output does not contain '$pattern'"
        echo "Output: $output"
        return 1
    fi
    
    return 0
}

# Export functions
export -f assert_json_contains
export -f assert_json_array_length
export -f assert_json_has_key
export -f assert_csv_has_rows
export -f assert_csv_contains
export -f assert_exit_code
export -f assert_output_contains

