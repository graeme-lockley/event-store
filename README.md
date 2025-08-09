# 🚀 Event Store

A lightweight, file-backed, API-driven message recording and delivery system
with TypeScript client library for easy integration and a modern web-based admin
interface.

## ✨ Features

- **📦 TypeScript Client Library** - Easy integration with type safety
- **📁 File-backed Storage** - No database required, events stored as JSON files
- **🔍 JSON Schema Validation** - Validate events against schemas at publish
  time
- **⚡ Asynchronous Event Dispatching** - Background delivery to webhook
  consumers
- **🌐 RESTful HTTP API** - Standard HTTP endpoints for all operations
- **🔄 Webhook-based Delivery** - Automatic retry logic with backoff for
  consumer delivery
- **🧪 Embedded Testing Support** - Built-in testing utilities for reliable
  integration tests
- **📊 Health Monitoring** - Real-time health status and metrics
- **🖥️ Web Admin Interface** - Modern web UI for managing Event Store instances

## 🏗️ Architecture

The Event Store provides a simple yet powerful event-driven architecture:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Your App      │    │  Event Store    │    │   Consumers     │
│                 │    │                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │ Client      │ │───▶│ │ Topics      │ │    │ │ Webhook     │ │
│ │ Library     │ │    │ │ & Events    │ │───▶│ │ Endpoints   │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
│                 │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│                 │    │ │ Dispatchers │ │    │ │ Your        │ │
│                 │    │ │ & Consumers │ │    │ │ Services    │ │
│                 │    │ └─────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   Admin UI      │
                       │                 │
                       │ ┌─────────────┐ │
                       │ │ Web         │ │
                       │ │ Interface   │ │
                       │ └─────────────┘ │
                       │ ┌─────────────┐ │
                       │ │ Health      │ │
                       │ │ Monitoring  │ │
                       │ └─────────────┘ │
                       └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites

- [Deno](https://deno.land/) 1.40+

### 1. Start the Event Store Server

```bash
# Clone the repository
git clone https://github.com/graeme-lockley/event-store.git
cd event-store

# Start the Event Store server (reads PORT, DATA_DIR, CONFIG_DIR, MAX_BODY_BYTES, RATE_LIMIT_PER_MINUTE)
cd event-store
deno run -A mod.ts
```

The server will start on `http://localhost:8000` by default.

### 2. Use the Client Library

```typescript
// Import the client library
import {
  EventStoreClient,
  type EventStoreConfig,
} from "https://raw.githubusercontent.com/graeme-lockley/event-store/main/event-store/client.ts";

// Create client configuration
const config: EventStoreConfig = {
  baseUrl: "http://localhost:8000",
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
};

// Initialize client (baseUrl required; falls back to EVENT_STORE_BASE_URL or location.origin)
const client = new EventStoreClient(config);

// Create a topic with schema validation
await client.createTopic("user-events", [
  {
    eventType: "user.created",
    type: "object",
    $schema: "https://json-schema.org/draft/2020-12/schema",
    properties: {
      id: { type: "string" },
      name: { type: "string" },
      email: { type: "string" },
    },
    required: ["id", "name", "email"],
  },
]);

// Publish an event
const eventId = await client.publishEvent(
  "user-events",
  "user.created",
  { id: "123", name: "Alice", email: "alice@example.com" },
);

// Register a consumer
const consumerId = await client.registerConsumer({
  callback: "https://your-service.com/webhook",
  topics: { "user-events": null },
});

console.log(`Event published: ${eventId}`);
console.log(`Consumer registered: ${consumerId}`);
```

### 3. Start the Admin UI (Optional)

The Admin UI provides a web interface for managing Event Store instances:

```bash
# In a new terminal, start the Admin UI
cd admin-ui
deno run -A main.ts
```

The Admin UI will be available at `http://localhost:8000` (default port).

## 🖥️ Admin UI

### Features

The Admin UI provides a modern web interface for managing Event Store instances:

- **📊 Dashboard** - Overview of all Event Store instances
- **🔍 Event Browser** - Browse and search events across topics
- **📈 Topic Statistics** - Real-time statistics and metrics
- **👥 Consumer Management** - Monitor and manage consumers
- **🔧 Store Configuration** - Add and configure Event Store connections
- **🧪 Connection Testing** - Test Event Store connections
- **📋 User Management** - Manage admin users and permissions

### Usage

#### 1. Adding Event Store Instances

1. Navigate to the Admin UI dashboard
2. Click "Add Store" to add a new Event Store instance
3. Enter the store details:
   - **Name**: A friendly name for the store
   - **URL**: The Event Store server URL (e.g., `http://localhost`)
   - **Port**: The Event Store server port (default: 8000)
4. Click "Test Connection" to verify the connection
5. Click "Add Store" to save the configuration

#### 2. Browsing Events

1. Navigate to "Events" in the sidebar
2. Select a store from the dropdown
3. Choose a topic to browse
4. View events with filtering options:
   - **Date range**: Filter by specific dates
   - **Event type**: Filter by event type
   - **Limit**: Set maximum number of events to display

#### 3. Topic Management

1. Navigate to "Topics" in the sidebar
2. View all topics across all stores
3. See topic statistics and event counts
4. Browse events within each topic

#### 4. Consumer Monitoring

1. Navigate to "Consumers" in the sidebar
2. View all registered consumers
3. Monitor consumer health and status
4. See delivery statistics and failures

#### 5. Health Monitoring

1. Navigate to "Dashboard" to see overall health
2. View running dispatchers and consumer counts
3. Monitor store connectivity and performance

### Configuration

The Admin UI can be configured using environment variables:

```bash
# Custom port
PORT=8001 deno run -A main.ts

# Custom data directory
DATA_DIR=./admin-data deno run -A main.ts

# Debug mode
DEBUG=1 deno run -A main.ts
```

## 📦 Client Library

### Installation

The client library is available as a remote import from GitHub:

```typescript
import {
  EventStoreClient,
  type EventStoreConfig,
} from "https://raw.githubusercontent.com/graeme-lockley/event-store/main/event-store/client.ts";
```

### Basic Usage

```typescript
// Initialize client
const client = new EventStoreClient({
  baseUrl: "http://localhost:8000",
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
});

// Wait for server to be ready (useful for tests)
await client.waitForServer();

// Health check
const health = await client.getHealth();
console.log(`Status: ${health.status}, Consumers: ${health.consumers}`);

// List topics
const topics = await client.getTopics();
console.log(`Available topics: ${topics.join(", ")}`);
```

### Available Methods

#### Topic Management

```typescript
// Create topic with schemas
await client.createTopic("user-events", [/* schemas */]);

// List all topics
const topics = await client.getTopics();

// Get topic details
const topic = await client.getTopic("user-events");
```

#### Event Publishing

```typescript
// Publish single event
const eventId = await client.publishEvent(
  "user-events",
  "user.created",
  payload,
);

// Publish multiple events
const eventIds = await client.publishEvents([/* event array */]);
```

#### Event Retrieval

```typescript
// Get all events from topic
const events = await client.getEvents("user-events");

// Get events with filters
const events = await client.getEvents("user-events", {
  sinceEventId: "user-events-5",
  limit: 10,
  date: "2025-01-15",
});
```

#### Consumer Management

```typescript
// Register consumer
const consumerId = await client.registerConsumer({
  callback: "https://your-service.com/webhook",
  topics: { "user-events": null },
});

// List consumers
const consumers = await client.getConsumers();

// Unregister consumer
await client.unregisterConsumer(consumerId);
```

## 🧪 Testing

### Integration Testing

The client library includes built-in support for embedded testing:

```typescript
import { TestSetup } from "https://raw.githubusercontent.com/graeme-lockley/event-store/main/event-store/tests/helpers/test-setup.ts";

describe("Event Store Integration", () => {
  let client: EventStoreClient;
  let serverProcess: Deno.ChildProcess;
  let testSetup: TestSetup;

  beforeAll(async () => {
    // Set up test environment
    testSetup = new TestSetup("my-test");
    await testSetup.setup();

    // Start Event Store server
    serverProcess = new Deno.Command("deno", {
      args: ["run", "-A", "mod.ts"],
      env: {
        "PORT": "9000",
        "DATA_DIR": testSetup.getDataDir(),
        "CONFIG_DIR": testSetup.getConfigDir(),
      },
      stdout: "piped",
      stderr: "piped",
    }).spawn();

    // Initialize client and wait for server
    client = new EventStoreClient({
      baseUrl: "http://localhost:9000",
      timeout: 5000,
      retries: 1,
      retryDelay: 100,
    });

    await client.waitForServer();
  });

  afterAll(async () => {
    serverProcess.kill();
    await serverProcess.status;
    await testSetup.cleanup();
  });

  it("should publish and retrieve events", async () => {
    await client.createTopic("test-topic", [/* schemas */]);

    const eventId = await client.publishEvent(
      "test-topic",
      "test.event",
      { message: "Hello World" },
    );

    const events = await client.getEvents("test-topic");
    assertEquals(events.length, 1);
    assertEquals(events[0].id, eventId);
  });
});
```

### Running Tests

```bash
# Test Event Store Core (requires --allow-all for file system access)
cd event-store
deno test --allow-all

# Test with coverage
deno test --allow-all --coverage=coverage

# Test Admin UI
cd admin-ui
deno test --allow-all
```

## 📚 Documentation

- **[API Documentation](docs/API.md)** - Complete REST API reference
- **[Event Store Usage Guide](docs/event-store-usage.md)** - Comprehensive usage
  guide with client library examples
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Production deployment
  instructions
- **[Testing Guide](docs/TESTING.md)** - Testing strategies and best practices

## 🔧 Configuration

### Environment Variables

#### Event Store Server

| Variable     | Default    | Description                       |
| ------------ | ---------- | --------------------------------- |
| `PORT`       | `8000`     | Server port                       |
| `DATA_DIR`   | `./data`   | Directory for event storage       |
| `CONFIG_DIR` | `./config` | Directory for configuration files |

#### Admin UI

| Variable               | Default  | Description                                                       |
| ---------------------- | -------- | ----------------------------------------------------------------- |
| `PORT`                 | `8000`   | Admin UI port                                                     |
| `DATA_DIR`             | `./data` | Directory for admin data                                          |
| `DEBUG`                | `false`  | Enable debug mode                                                 |
| `EVENT_STORE_BASE_URL` | unset    | Optional override to force a single base URL for EventStoreClient |

### Custom Configuration

```bash
# Event Store with custom settings
PORT=9000 DATA_DIR=./my-data CONFIG_DIR=./my-config deno run -A mod.ts

# Admin UI with custom settings
PORT=8001 DATA_DIR=./admin-data DEBUG=1 deno run -A main.ts
```

## 🏗️ Project Structure

```
event-store/
├── event-store/           # Core Event Store implementation
│   ├── client.ts         # TypeScript client library
│   ├── types.ts          # Type definitions
│   ├── api/              # REST API implementation
│   ├── core/             # Core business logic
│   ├── tests/            # Test suite
│   └── mod.ts            # Server entry point
├── admin-ui/             # Web-based admin interface
│   ├── components/       # React components
│   ├── routes/           # API routes
│   ├── islands/          # Interactive components
│   ├── tests/            # Admin UI tests
│   └── main.ts           # Admin UI entry point
├── docs/                 # Documentation
├── LICENSE               # MIT License
└── README.md            # This file
```

## 💡 Usage Patterns

### Event-Driven Architecture

```typescript
// 1. Create topic with schemas
await client.createTopic("user-events", [/* schemas */]);

// 2. Register consumer
const consumerId = await client.registerConsumer({
  callback: "https://your-service.com/webhook",
  topics: { "user-events": null },
});

// 3. Publish events
const eventId = await client.publishEvent(
  "user-events",
  "user.created",
  { id: "123", name: "Alice" },
);
```

### Event Sourcing

```typescript
// Retrieve all events from a topic
const events = await client.getEvents("user-events");

// Get events since a specific point
const events = await client.getEvents("user-events", {
  sinceEventId: "user-events-100",
});
```

### Audit Trail

```typescript
// Get events from a specific date
const events = await client.getEvents("audit-events", {
  date: "2025-01-15",
});
```

## 🚨 Error Handling

```typescript
try {
  const health = await client.getHealth();
} catch (error) {
  if (error instanceof EventStoreError) {
    console.error(`Event Store Error: ${error.message}`);
    console.error(`Status: ${error.status}`);
  } else {
    console.error(`Unexpected error: ${error}`);
  }
}
```

## ⚡ Performance

- **Near-instantaneous event delivery** - Events are processed immediately
- **File-based storage** - No database overhead, direct file I/O
- **Background dispatchers** - Asynchronous consumer delivery
- **Efficient event retrieval** - Optimized file reading with filtering

## 🔒 Reliability

- **Schema validation** - All events validated against schemas
- **Consumer delivery retry** - Exponential backoff and eventual unregister on
  repeated failures
- **Event durability** - Events stored as individual JSON files
- **Webhook retry logic** - Automatic retry for failed deliveries

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`deno test --allow-all`)
6. Commit your changes (`git commit -m 'Add amazing feature'`)
7. Push to the branch (`git push origin feature/amazing-feature`)
8. Open a Pull Request

### Development Setup

```bash
# Clone the repository
git clone https://github.com/graeme-lockley/event-store.git
cd event-store

# Install Deno (if not already installed)
# See: https://deno.land/#installation

# Run Event Store tests
cd event-store
deno test --allow-all

# Start Event Store development server
deno run -A mod.ts

# In another terminal, run Admin UI tests
cd admin-ui
deno test --allow-all

# Start Admin UI development server
deno run -A main.ts
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file
for details.

## 🙏 Acknowledgments

- Built with [Deno](https://deno.land/) - A modern runtime for JavaScript and
  TypeScript
- Admin UI built with [Fresh](https://fresh.deno.dev/) - The next-gen web
  framework
- File-based storage for simplicity and reliability
- JSON Schema for robust event validation
- RESTful API design for easy integration

---

**Ready to build event-driven applications?** Start with the
[Event Store Usage Guide](docs/event-store-usage.md) for comprehensive examples
and best practices! 🚀
