# Event Store

A multi-tenant, file-backed event store system with REST API, built using Kotlin and following hexagonal architecture principles.

## Project Overview

This repository contains a complete event store implementation with the following components:

- **event-store** - Core Kotlin backend implementing the event store API (see [event-store/README.md](event-store/README.md))
- **event-store-integration** - Integration test utilities and helpers (see [event-store-integration/README.md](event-store-integration/README.md))
- **admin-ui** - Web-based admin interface (Deno/Fresh)
- **cli** - Command-line interface (Go)
- **integration-tests** - End-to-end integration test scenarios

## Project Structure

```
event-store/
├── event-store/                    # Core Kotlin backend
│   ├── src/main/kotlin/           # Source code
│   │   └── com/eventstore/
│   │       ├── Application.kt     # Entry point
│   │       ├── Config.kt          # Configuration
│   │       ├── domain/            # Domain layer (hexagonal architecture)
│   │       ├── infrastructure/    # Infrastructure adapters
│   │       └── interfaces/        # HTTP API routes
│   ├── src/test/kotlin/           # Unit tests
│   └── build.gradle.kts           # Build configuration
├── event-store-integration/        # Integration test utilities
│   ├── src/test/kotlin/           # Integration test helpers
│   └── build.gradle.kts
├── admin-ui/                       # Web admin interface (Deno/Fresh)
├── cli/                            # CLI tool (Go)
├── integration-tests/              # E2E test scenarios
├── docs/                           # Documentation
│   ├── API.md                     # Complete API reference
│   ├── DEPLOYMENT.md              # Deployment guide
│   └── TESTING.md                 # Testing guide
├── build.gradle.kts                # Root build configuration
├── settings.gradle.kts             # Multi-project settings
└── gradlew                         # Gradle wrapper

```

## Building the Project

This is a **multi-project Gradle build**. All builds should be run from the repository root.

### Prerequisites

- **Java 17+** (for Kotlin projects)
- **Gradle 8.5+** (wrapper included)
- **Deno 1.40+** (for admin-ui)
- **Go 1.21+** (for CLI)

### Build All Projects

```bash
# Build all Kotlin projects
./gradlew build

# Build only the event-store backend
./gradlew :event-store:build

# Build only the integration test project
./gradlew :event-store-integration:build
```

### Run the Event Store Server

```bash
# Run from Gradle
./gradlew :event-store:run

# Or build and run the JAR
./gradlew :event-store:jar
java -jar event-store/build/libs/event-store-1.0.0.jar
```

The server starts on port 8000 by default (configurable via `PORT` environment variable).

## Testing

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for a specific project
./gradlew :event-store:test
./gradlew :event-store-integration:test
```

### Integration Tests

The `event-store-integration` project provides utilities for integration testing. See [event-store-integration/README.md](event-store-integration/README.md) for details.

```bash
# Run integration tests
./gradlew :event-store-integration:test
```

### End-to-End Tests

```bash
# Run CLI integration test scenarios
cd integration-tests
./run.sh
```

## Development Setup

### IntelliJ IDEA

1. Open the repository root directory in IntelliJ IDEA
2. IntelliJ will automatically detect the multi-project Gradle build
3. Both `event-store` and `event-store-integration` will be available as modules
4. You can run tests directly from IntelliJ

### Building from Subdirectories

While you can run Gradle from subdirectories, it's recommended to run from the root:

```bash
# From root (recommended)
./gradlew :event-store:test

# From subdirectory (works but less efficient)
cd event-store
../gradlew test
```

## API Documentation

For complete API endpoint documentation, see:
- **[docs/API.md](docs/API.md)** - Complete REST API reference
- **[event-store/README.md](event-store/README.md)** - Backend architecture and endpoints

## Key Features

- **Multi-tenant architecture** - Tenant and namespace isolation
- **File-backed storage** - No database required, events stored as JSON files
- **JSON Schema validation** - Events validated against schemas at publish time
- **Asynchronous event dispatching** - Background delivery to webhook consumers
- **RESTful HTTP API** - Standard HTTP endpoints for all operations
- **Hexagonal architecture** - Clean separation of domain, infrastructure, and interfaces
- **Authentication & Authorization** - Session-based auth and API key support
- **Permission management** - Fine-grained access control

## Technology Stack

- **Backend**: Kotlin 1.9.22, Ktor 2.3.8, Gradle 8.5
- **Admin UI**: Deno, Fresh framework
- **CLI**: Go 1.21+
- **Testing**: JUnit 5, Kotlin Test

## Documentation

- **[event-store/README.md](event-store/README.md)** - Backend architecture, code structure, and API endpoints
- **[event-store-integration/README.md](event-store-integration/README.md)** - Integration testing guide
- **[docs/API.md](docs/API.md)** - Complete API reference
- **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** - Production deployment guide
- **[docs/TESTING.md](docs/TESTING.md)** - Testing strategies

## License

MIT License - see [LICENSE](LICENSE) file for details.
