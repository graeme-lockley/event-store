#!/usr/bin/env bash
# Quality Gate Script
# 
# Purpose: Enforce progressive quality improvement by comparing current metrics
# against baseline and failing if quality regresses.
#
# Requirements implemented:
# - REQ-11: Quality Gate Script
# - REQ-5: Progressive Quality Gates
# - REQ-4: Quality Baseline Tracking
# - REQ-6: Baseline Updates
#
# Usage:
#   ./quality-gate.sh [--update-baseline]
#
# Arguments:
#   --update-baseline  Update the baseline file if metrics improved (main branch only)
#
# Exit codes:
#   0 - Quality maintained or improved
#   1 - Quality regressed or error occurred

set -eo pipefail

# Ensure consistent locale for numeric operations (avoid comma as decimal separator)
export LC_ALL=C
export LC_NUMERIC=C

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BASELINE_FILE="$PROJECT_ROOT/.quality-baseline.json"

# Module list
MODULES="event-store event-store-integration"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Flags
UPDATE_BASELINE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --update-baseline)
            UPDATE_BASELINE=true
            shift
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# ============================================================================
# Helper Functions
# ============================================================================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get report path for a module
get_report_path() {
    local module="$1"
    echo "$PROJECT_ROOT/$module/build/reports/jacoco/test/jacocoTestReport.xml"
}

# Check if required tools are available
check_dependencies() {
    local missing=()
    
    if ! command -v jq &> /dev/null; then
        missing+=("jq")
    fi
    
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing required dependencies: ${missing[*]}"
        log_error "Please install: ${missing[*]}"
        exit 1
    fi
}

# ============================================================================
# Coverage Extraction (REQ-11.2)
# ============================================================================

# Extract coverage percentage from JaCoCo XML report
# Arguments: $1 = report path, $2 = metric type (LINE, BRANCH, INSTRUCTION)
extract_coverage() {
    local report_path="$1"
    local metric_type="$2"
    
    if [ ! -f "$report_path" ]; then
        echo "0.00"
        return
    fi
    
    # Extract covered and missed counts for the specified metric type
    # Use grep and sed to extract counter values (compatible with both Linux and macOS)
    local counter_line
    counter_line=$(grep -E "<counter type=\"${metric_type}\"" "$report_path" | tail -1 || echo "")
    
    if [ -z "$counter_line" ]; then
        echo "0.00"
        return
    fi
    
    local missed covered total percentage
    missed=$(echo "$counter_line" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p')
    covered=$(echo "$counter_line" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p')
    
    if [ -z "$missed" ] || [ -z "$covered" ]; then
        echo "0.00"
        return
    fi
    
    total=$((missed + covered))
    
    if [ "$total" -eq 0 ]; then
        echo "0.00"
        return
    fi
    
    # Calculate percentage with 2 decimal places using awk
    percentage=$(awk "BEGIN {printf \"%.2f\", ($covered / $total) * 100}")
    echo "$percentage"
}

# Get all coverage metrics for a module as JSON
get_module_coverage() {
    local module="$1"
    local report_path
    report_path=$(get_report_path "$module")
    
    local line_coverage branch_coverage instruction_coverage
    line_coverage=$(extract_coverage "$report_path" "LINE")
    branch_coverage=$(extract_coverage "$report_path" "BRANCH")
    instruction_coverage=$(extract_coverage "$report_path" "INSTRUCTION")
    
    echo "{\"line\": $line_coverage, \"branch\": $branch_coverage, \"instruction\": $instruction_coverage}"
}

# ============================================================================
# Linting Violation Count (REQ-11.3)
# ============================================================================

count_ktlint_violations() {
    local violations=0
    
    # Check ktlint report files in both modules
    for module in $MODULES; do
        local report_dir="$PROJECT_ROOT/$module/build/reports/ktlint"
        
        if [ -d "$report_dir" ]; then
            # Count violations from ktlint XML reports
            local module_violations
            module_violations=$(find "$report_dir" -name "*.xml" -exec grep -c '<error ' {} \; 2>/dev/null | awk '{sum+=$1} END {print sum+0}' || echo "0")
            violations=$((violations + module_violations))
        fi
    done
    
    echo "$violations"
}

# ============================================================================
# Baseline Operations (REQ-11.4, REQ-11.5)
# ============================================================================

# Read baseline from file (REQ-13.1)
read_baseline() {
    if [ ! -f "$BASELINE_FILE" ]; then
        log_error "Baseline file not found: $BASELINE_FILE"
        log_error "Please create the baseline file or run with initial values."
        exit 1
    fi
    
    cat "$BASELINE_FILE"
}

# Get baseline value for a specific path
get_baseline_value() {
    local json="$1"
    local path="$2"
    
    echo "$json" | jq -r "$path // 0"
}

# ============================================================================
# Quality Gate Check (REQ-5, REQ-11.5, REQ-11.6, REQ-11.7)
# ============================================================================

check_quality_gates() {
    local baseline
    baseline=$(read_baseline)
    
    local has_regression=false
    local has_improvement=false
    local improvements=""
    local regressions=""
    
    echo ""
    echo "=========================================="
    echo "Quality Gate Check"
    echo "=========================================="
    echo ""
    
    # Check coverage for each module (REQ-5.2)
    for module in $MODULES; do
        log_info "Checking coverage for $module..."
        
        local report_path
        report_path=$(get_report_path "$module")
        
        if [ ! -f "$report_path" ]; then
            log_warn "Coverage report not found for $module: $report_path"
            continue
        fi
        
        for metric in "line" "branch" "instruction"; do
            local metric_upper
            metric_upper=$(echo "$metric" | tr '[:lower:]' '[:upper:]')
            
            local current_value baseline_value
            current_value=$(extract_coverage "$report_path" "$metric_upper")
            baseline_value=$(get_baseline_value "$baseline" ".coverage[\"$module\"].$metric")
            
            echo "  $metric coverage: $current_value% (baseline: $baseline_value%)"
            
            # Compare using awk for floating point comparison
            local comparison
            comparison=$(awk "BEGIN {if ($current_value < $baseline_value) print \"regression\"; else if ($current_value > $baseline_value) print \"improvement\"; else print \"same\"}")
            
            if [ "$comparison" = "regression" ]; then
                has_regression=true
                regressions="$regressions\n  - $module $metric coverage: $current_value% < $baseline_value%"
            elif [ "$comparison" = "improvement" ]; then
                has_improvement=true
                improvements="$improvements\n  - $module $metric coverage: $baseline_value% → $current_value%"
            fi
        done
        echo ""
    done
    
    # Check linting violations (REQ-5.3)
    log_info "Checking linting violations..."
    local current_violations baseline_violations
    current_violations=$(count_ktlint_violations)
    baseline_violations=$(get_baseline_value "$baseline" ".linting.violations")
    
    echo "  Violations: $current_violations (baseline: $baseline_violations)"
    echo ""
    
    if [ "$current_violations" -gt "$baseline_violations" ]; then
        has_regression=true
        regressions="$regressions\n  - Linting violations increased: $baseline_violations → $current_violations"
    elif [ "$current_violations" -lt "$baseline_violations" ]; then
        has_improvement=true
        improvements="$improvements\n  - Linting violations decreased: $baseline_violations → $current_violations"
    fi
    
    # Report results
    echo "=========================================="
    echo "Quality Gate Results"
    echo "=========================================="
    echo ""
    
    # Report improvements
    if [ -n "$improvements" ]; then
        log_info "Improvements detected:"
        echo -e "$improvements"
        echo ""
    fi
    
    # Report regressions (REQ-5.6)
    if [ "$has_regression" = true ]; then
        log_error "Quality regressions detected:"
        echo -e "$regressions"
        echo ""
        log_error "Action required: Fix the regressions before merging."
        log_error "- For coverage: Add more tests to increase coverage"
        log_error "- For linting: Run './gradlew ktlintFormat' to fix style issues"
        echo ""
        return 1
    fi
    
    # Update baseline if improvements detected and flag is set (REQ-6)
    if [ "$has_improvement" = true ] && [ "$UPDATE_BASELINE" = true ]; then
        update_baseline "$baseline" "$current_violations"
    fi
    
    log_info "Quality gate passed!"
    return 0
}

# ============================================================================
# Baseline Update (REQ-6)
# ============================================================================

update_baseline() {
    local old_baseline="$1"
    local current_violations="$2"
    
    log_info "Updating baseline with improved metrics..."
    
    # Build new baseline JSON
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    # Get current coverage for all modules
    local event_store_coverage event_store_integration_coverage
    event_store_coverage=$(get_module_coverage "event-store")
    event_store_integration_coverage=$(get_module_coverage "event-store-integration")
    
    # Create new baseline JSON
    local new_baseline
    new_baseline=$(cat <<EOF
{
  "lastUpdated": "$timestamp",
  "coverage": {
    "event-store": $event_store_coverage,
    "event-store-integration": $event_store_integration_coverage
  },
  "linting": {
    "violations": $current_violations
  }
}
EOF
)
    
    # Write new baseline (REQ-6.4)
    echo "$new_baseline" | jq '.' > "$BASELINE_FILE"
    
    # Get old coverage for commit message
    local old_line_coverage new_line_coverage
    old_line_coverage=$(get_baseline_value "$old_baseline" ".coverage[\"event-store\"].line")
    new_line_coverage=$(extract_coverage "$(get_report_path "event-store")" "LINE")
    
    local old_violations
    old_violations=$(get_baseline_value "$old_baseline" ".linting.violations")
    
    log_info "Baseline updated successfully"
    echo "  Coverage: $old_line_coverage% → $new_line_coverage%"
    echo "  Linting: $old_violations → $current_violations violations"
    
    # Output commit message for CI to use (REQ-6.3)
    echo ""
    echo "BASELINE_COMMIT_MESSAGE=chore: update quality baseline [coverage: $old_line_coverage% → $new_line_coverage%, linting: $old_violations → $current_violations violations]"
}

# ============================================================================
# Summary Report
# ============================================================================

print_summary() {
    echo ""
    echo "=========================================="
    echo "Quality Metrics Summary"
    echo "=========================================="
    echo ""
    
    for module in $MODULES; do
        local report_path
        report_path=$(get_report_path "$module")
        
        echo "Module: $module"
        echo "----------------------------------------"
        
        if [ -f "$report_path" ]; then
            local line branch instruction
            line=$(extract_coverage "$report_path" "LINE")
            branch=$(extract_coverage "$report_path" "BRANCH")
            instruction=$(extract_coverage "$report_path" "INSTRUCTION")
            
            echo "  Line Coverage:        $line%"
            echo "  Branch Coverage:      $branch%"
            echo "  Instruction Coverage: $instruction%"
        else
            echo "  (No coverage report found)"
        fi
        echo ""
    done
    
    local violations
    violations=$(count_ktlint_violations)
    echo "Linting Violations: $violations"
    echo ""
}

# ============================================================================
# Main
# ============================================================================

main() {
    log_info "Starting quality gate check..."
    echo ""
    
    # Check dependencies
    check_dependencies
    
    # Print summary
    print_summary
    
    # Run quality gate check
    check_quality_gates
    local exit_code=$?
    
    exit $exit_code
}

# Run main function
main
