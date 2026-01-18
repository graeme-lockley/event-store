#!/usr/bin/env bash
# Display integration test results summary and details
# Usage: ./display-test-results.sh [test-results-dir]

set -euo pipefail

# Default test results directory (relative to project root)
DEFAULT_TEST_RESULTS_DIR="event-store-integration/build/test-results/test"

# Get the test results directory from argument or use default
TEST_RESULTS_DIR="${1:-$DEFAULT_TEST_RESULTS_DIR}"

# If relative path, resolve from project root
if [[ ! "$TEST_RESULTS_DIR" = /* ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
    TEST_RESULTS_DIR="$PROJECT_ROOT/$TEST_RESULTS_DIR"
fi

# Function to display test summary
display_test_summary() {
    echo "=========================================="
    echo "Integration Test Summary"
    echo "=========================================="
    echo ""
    
    # Check if test results exist
    if [ -d "$TEST_RESULTS_DIR" ]; then
        # Count tests
        total_tests=$(find "$TEST_RESULTS_DIR" -name "*.xml" -exec grep -h "tests=" {} \; | sed 's/.*tests="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        failed_tests=$(find "$TEST_RESULTS_DIR" -name "*.xml" -exec grep -h "failures=" {} \; | sed 's/.*failures="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        errors=$(find "$TEST_RESULTS_DIR" -name "*.xml" -exec grep -h "errors=" {} \; | sed 's/.*errors="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        skipped=$(find "$TEST_RESULTS_DIR" -name "*.xml" -exec grep -h "skipped=" {} \; | sed 's/.*skipped="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
        
        passed=$((total_tests - failed_tests - errors - skipped))
        
        echo "Total Tests: ${total_tests:-0}"
        echo "Passed: ${passed:-0}"
        echo "Failed: ${failed_tests:-0}"
        echo "Errors: ${errors:-0}"
        echo "Skipped: ${skipped:-0}"
        echo ""
        
        # List all test classes and their results
        echo "Test Scenarios:"
        echo "----------------------------------------"
        for xml_file in "$TEST_RESULTS_DIR"/*.xml; do
            if [ -f "$xml_file" ]; then
                classname=$(grep -o 'classname="[^"]*"' "$xml_file" | head -1 | sed 's/classname="\([^"]*\)".*/\1/')
                testname=$(grep -o 'name="[^"]*"' "$xml_file" | head -1 | sed 's/name="\([^"]*\)".*/\1/')
                failures=$(grep -o 'failures="[0-9]*"' "$xml_file" | head -1 | sed 's/failures="\([0-9]*\)".*/\1/')
                errors=$(grep -o 'errors="[0-9]*"' "$xml_file" | head -1 | sed 's/errors="\([0-9]*\)".*/\1/')
                
                if [ "${failures:-0}" = "0" ] && [ "${errors:-0}" = "0" ]; then
                    echo "  ✓ $classname.$testname"
                else
                    echo "  ✗ $classname.$testname (FAILED)"
                fi
            fi
        done
        echo ""
    else
        echo "No test results found at: $TEST_RESULTS_DIR"
    fi
    
    echo "=========================================="
}

# Function to display detailed test results
display_test_details() {
    echo "=========================================="
    echo "Detailed Test Results"
    echo "=========================================="
    echo ""
    
    # Show details for each test
    for xml_file in "$TEST_RESULTS_DIR"/*.xml; do
        if [ -f "$xml_file" ]; then
            classname=$(grep -o 'classname="[^"]*"' "$xml_file" | head -1 | sed 's/classname="\([^"]*\)".*/\1/')
            echo "Test Class: $classname"
            echo "----------------------------------------"
            
            # Extract test cases
            grep -o '<testcase[^>]*>' "$xml_file" | while read -r testcase; do
                testname=$(echo "$testcase" | grep -o 'name="[^"]*"' | sed 's/name="\([^"]*\)".*/\1/')
                time=$(echo "$testcase" | grep -o 'time="[^"]*"' | sed 's/time="\([^"]*\)".*/\1/')
                echo "  - $testname (${time}s)"
            done
            
            # Show failures if any
            if grep -q '<failure' "$xml_file"; then
                echo ""
                echo "  Failures:"
                grep -A 5 '<failure' "$xml_file" | head -20
            fi
            
            # Show errors if any
            if grep -q '<error' "$xml_file"; then
                echo ""
                echo "  Errors:"
                grep -A 5 '<error' "$xml_file" | head -20
            fi
            
            echo ""
        fi
    done
}

# Main execution
main() {
    display_test_summary
    echo ""
    display_test_details
}

# Run main function
main
