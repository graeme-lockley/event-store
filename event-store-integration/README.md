# Event Store Integration Tests

Integration test utilities and helpers for testing the event-store backend.

## Purpose

This project provides:
- **Test utilities** - Helper classes for starting/stopping event-store instances in tests
- **Isolation** - Each test gets its own isolated event-store instance
- **Convenience** - Simplified setup for integration testing scenarios

## Project Structure

```
src/
├── main/kotlin/com/eventstore/integration/
│   └── Main.kt                    # Placeholder main class
└── test/kotlin/com/eventstore/integration/
    ├── EventStoreTestHelper.kt    # Helper for managing event-store instances
    └── ExampleIntegrationTest.kt   # Example test demonstrating usage
```

## Building and Testing

This is part of a **multi-project Gradle build**. Always run builds from the repository root.

### Run Tests

```bash
# From repository root
./gradlew :event-store-integration:test

# Run specific test class
./gradlew :event-store-integration:test --tests "ExampleIntegrationTest"
```

### Build

```bash
./gradlew :event-store-integration:build
```

## Usage

### EventStoreTestHelper

The `EventStoreTestHelper` class provides a convenient way to start and stop event-store instances in tests.

#### Basic Usage

```kotlin
import com.eventstore.integration.EventStoreTestHelper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MyIntegrationTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var eventStoreHelper: EventStoreTestHelper
    
    @BeforeEach
    fun setUp() {
        val dataDir = tempDir.resolve("data")
        val configDir = tempDir.resolve("config")
        
        eventStoreHelper = EventStoreTestHelper(
            dataDir = dataDir,
            configDir = configDir
        )
        
        eventStoreHelper.start()
    }
    
    @AfterEach
    fun tearDown() {
        if (eventStoreHelper.isRunning()) {
            eventStoreHelper.stop()
        }
    }
    
    @Test
    fun `test event store functionality`() {
        val baseUrl = eventStoreHelper.getBaseUrl()
        // Use baseUrl to make HTTP requests to the event-store instance
    }
}
```

#### Custom Port

```kotlin
eventStoreHelper = EventStoreTestHelper(
    dataDir = dataDir,
    configDir = configDir,
    port = 9001  // Specify custom port
)
```

#### Custom Configuration

```kotlin
val config = Config(
    port = 9000,
    dataDir = dataDir.toString(),
    configDir = configDir.toString(),
    maxBodyBytes = 1048576L,
    rateLimitPerMinute = 600,
    multiTenantEnabled = false,
    authEnabled = false
)

eventStoreHelper = EventStoreTestHelper(
    dataDir = dataDir,
    configDir = configDir,
    config = config
)
```

### API Reference

#### EventStoreTestHelper

**Constructor:**
```kotlin
EventStoreTestHelper(
    dataDir: Path,
    configDir: Path,
    port: Int = findAvailablePort(),
    config: Config? = null
)
```

**Methods:**
- `start()` - Starts the event-store instance
- `stop()` - Stops the event-store instance gracefully
- `isRunning(): Boolean` - Checks if the instance is running
- `getBaseUrl(): String` - Gets the base URL (e.g., "http://localhost:9000")
- `getPort(): Int` - Gets the port the instance is running on

### Test Isolation

Each test gets its own isolated event-store instance with:
- **Dedicated data directory** - Events stored separately
- **Dedicated config directory** - Topic/tenant configs isolated
- **Automatic port assignment** - Ports start at 9000 and increment
- **Clean shutdown** - Instances are stopped after each test

### Example: Full Integration Test

```kotlin
import com.eventstore.integration.EventStoreTestHelper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EventStoreIntegrationTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var helper: EventStoreTestHelper
    private lateinit var client: HttpClient
    
    @BeforeEach
    fun setUp() {
        helper = EventStoreTestHelper(
            dataDir = tempDir.resolve("data"),
            configDir = tempDir.resolve("config")
        )
        helper.start()
        
        client = HttpClient {
            baseUrl = helper.getBaseUrl()
        }
    }
    
    @AfterEach
    fun tearDown() {
        client.close()
        if (helper.isRunning()) {
            helper.stop()
        }
    }
    
    @Test
    fun `should create topic and publish events`() = runBlocking {
        // Create topic
        client.post("${helper.getBaseUrl()}/tenants/system/namespaces/management/topics") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "name": "test-topic",
                    "schemas": [{
                        "eventType": "test.event",
                        "type": "object",
                        "properties": {
                            "message": { "type": "string" }
                        }
                    }]
                }
            """.trimIndent())
        }
        
        // Publish event
        val response = client.post("${helper.getBaseUrl()}/tenants/system/namespaces/management/events") {
            contentType(ContentType.Application.Json)
            setBody("""
                [{
                    "topic": "test-topic",
                    "type": "test.event",
                    "payload": { "message": "Hello World" }
                }]
            """.trimIndent())
        }
        
        assert(response.status == HttpStatusCode.OK)
    }
}
```

## Dependencies

This project depends on:
- **event-store** - The main backend project (via `implementation(project(":event-store"))`)
- **Ktor Test Host** - For testing HTTP endpoints
- **JUnit 5** - Test framework
- **Kotlinx Coroutines Test** - For async test support

All dependencies are configured in the root `build.gradle.kts` and inherited by this project.

## Running Tests in IntelliJ

1. Open the repository root in IntelliJ IDEA
2. Navigate to test files in `event-store-integration/src/test/kotlin/`
3. Right-click on test classes or methods and select "Run"
4. IntelliJ will automatically resolve the `:event-store` dependency

## Related Documentation

- **[../README.md](../README.md)** - Project overview and build instructions
- **[../event-store/README.md](../event-store/README.md)** - Backend architecture and API endpoints
- **[../docs/TESTING.md](../docs/TESTING.md)** - Testing strategies and best practices
