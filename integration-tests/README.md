# Integration Tests

Shell-driven integration tests for the Event Store CLI and server.

## Running Tests

```bash
./run.sh
```

## Test Structure

- `run.sh` - Main test runner that orchestrates all tests
- `helpers/` - Reusable helper functions
  - `server.sh` - Event store server management
  - `cli.sh` - CLI wrapper functions
  - `assertions.sh` - Output validation utilities
- `scenarios/` - Test scenarios organized by feature
- `fixtures/` - Test data files

## Prerequisites

- Java 17+
- Go 1.21+
- `jq` (for JSON parsing)

## Test Ports

Tests use port `18000` for the event store to avoid conflicts with development servers.

