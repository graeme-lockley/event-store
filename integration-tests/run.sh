#!/usr/bin/env bash
# Main test runner for CLI integration tests

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source helper functions
source "$SCRIPT_DIR/helpers/server.sh"
source "$SCRIPT_DIR/helpers/cli.sh"
source "$SCRIPT_DIR/helpers/assertions.sh"

# Configuration
CLI_BIN="$PROJECT_ROOT/cli/es"
EVENT_STORE_JAR="$PROJECT_ROOT/event-store/build/libs/event-store-1.0.0.jar"
EVENT_STORE_GRADLE="$PROJECT_ROOT/event-store/gradlew"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

main() {
    # Build CLI
    echo -e "${YELLOW}Building CLI...${NC}"
    if ! (cd "$PROJECT_ROOT/cli" && go build -o es .); then
        echo -e "${RED}Failed to build CLI${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ CLI built successfully${NC}"
    echo ""
    
    # Build Event Store JAR if needed
    if [ ! -f "$EVENT_STORE_JAR" ]; then
        echo -e "${YELLOW}Building Event Store JAR...${NC}"
        if ! (cd "$PROJECT_ROOT/event-store" && chmod +x gradlew && ./gradlew jar); then
            echo -e "${RED}Failed to build Event Store JAR${NC}"
            exit 1
        fi
        echo -e "${GREEN}✓ Event Store JAR built successfully${NC}"
        echo ""
    fi
    
    # Start event store server
    echo -e "${YELLOW}Starting event store server...${NC}"
    if ! start_event_store; then
        echo -e "${RED}Failed to start event store server${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Event store server started${NC}"
    echo ""
    
    # Trap to ensure cleanup on exit
    trap stop_event_store EXIT
    
    # Run test scenarios
    local failed=0
    local passed=0
    local total=0
    
    for scenario in "$SCRIPT_DIR/scenarios"/*.sh; do
        if [ ! -f "$scenario" ]; then
            continue
        fi
        
        total=$((total + 1))
        local scenario_name=$(basename "$scenario" .sh)
        
        echo -e "${YELLOW}Running scenario: $scenario_name${NC}"
        
        if bash "$scenario"; then
            echo -e "${GREEN}✓ $scenario_name passed${NC}"
            passed=$((passed + 1))
        else
            echo -e "${RED}✗ $scenario_name failed${NC}"
            failed=$((failed + 1))
        fi
        echo ""
    done
    
    # Summary
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo "Total:  $total"
    echo -e "${GREEN}Passed: $passed${NC}"
    if [ $failed -gt 0 ]; then
        echo -e "${RED}Failed: $failed${NC}"
    else
        echo "Failed: $failed"
    fi
    echo ""
    
    # Cleanup
    stop_event_store
    trap - EXIT
    
    if [ $failed -gt 0 ]; then
        exit 1
    fi
    
    echo -e "${GREEN}All tests passed${NC}"
}

main "$@"

