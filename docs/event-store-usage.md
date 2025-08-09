# 📚 **Event Store API Documentation**

## **Overview**

The Event Store is a lightweight, file-backed message recording and delivery
system that provides:

- **Topic-based event partitioning** with JSON Schema validation
- **Asynchronous event dispatching** to registered consumers
- **Globally unique event IDs** in format `<topic>-<sequence>`
- **RESTful HTTP API** for all operations
- **Webhook-based consumer delivery** with automatic retry logic
- **Client library** for easy integration
- **Embedded testing support** for reliable test automation

## **Base URL**

```
http://localhost:8000
```

## **Authentication**

Currently, no authentication is required. All endpoints are publicly accessible.

---

## **🚀 Getting Started**

### **Starting the Event Store Server**

#### **Option 1: Direct Deno Run**

```bash
# From the event-store directory
cd event-store
deno run -A mod.ts
```

#### **Option 2: Environment Configuration**

```bash
# Custom port and data directories
PORT=9000 DATA_DIR=./data CONFIG_DIR=./config deno run -A mod.ts
```

#### **Option 3: Background Process**

```bash
# Start in background
deno run -A mod.ts &
# Stop with: kill %1
```

### **Stopping the Server**

```bash
# If running in foreground: Ctrl+C
# If running in background: kill <process_id>
# Or find and kill: pkill -f "deno run.*mod.ts"
```

---

## **📦 Client Library**

The Event Store provides a TypeScript client library for easy integration:

### **Installation**

```typescript
// Import from GitHub repository
import {
  EventStoreClient,
  type EventStoreConfig,
} from "https://raw.githubusercontent.com/graeme-lockley/event-store/main/event-store/client.ts";
```

### **Basic Usage**

```typescript
// Create client configuration (baseUrl required unless EVENT_STORE_BASE_URL is set)
const config: EventStoreConfig = {
  baseUrl: "http://localhost:8000",
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
};

// Initialize client
const client = new EventStoreClient(config);

// Wait for server to be ready (useful for tests)
await client.waitForServer({
  maxWaitTime: 5000,
  pollInterval: 100,
  throwOnTimeout: true,
});

// Use the client
const health = await client.getHealth();
const topics = await client.getTopics();
```

### **Client Methods**

#### **Health & Status**

```typescript
// Check server health
const health = await client.getHealth();
// Returns: { status: "healthy", consumers: number, runningDispatchers: string[] }

// Wait for server to be ready
await client.waitForServer({ maxWaitTime: 5000 });
```

#### **Topic Management**

```typescript
// Create topic with schemas
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

// List all topics
const topics = await client.getTopics();
// Returns: string[]

// Get topic details
const topic = await client.getTopic("user-events");
// Returns: { name: string, sequence: number, schemas: Schema[] }
```

#### **Event Publishing**

```typescript
// Publish single event (payload must be a JSON object)
const eventId = await client.publishEvent(
  "user-events",
  "user.created",
  { id: "123", name: "Alice", email: "alice@example.com" },
);

// Publish multiple events
const eventIds = await client.publishEvents([
  {
    topic: "user-events",
    type: "user.created",
    payload: { id: "123", name: "Alice" },
  },
  {
    topic: "audit-events",
    type: "user.verified",
    payload: { id: "123", verified: true },
  },
]);
```

#### **Event Retrieval**

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

#### **Consumer Management**

```typescript
// Register consumer
const consumerId = await client.registerConsumer({
  callback: "https://your-service.com/webhook",
  topics: {
    "user-events": null, // All events
    "audit-events": "audit-events-5", // Since specific event
  },
});

// List consumers
const consumers = await client.getConsumers();

// Unregister consumer
await client.unregisterConsumer(consumerId);
```

#### **Connection Testing**

```typescript
// Test connection to Event Store
const ok = await client.testConnection();
// Returns: true/false
```

### **Error Handling**

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

---

## **🧪 Embedded Testing**

The Event Store client library includes built-in support for embedded testing,
making it easy to write reliable integration tests.

### **Test-Optimized Client**

```typescript
// Create test-optimized client with shorter timeouts
const testClient = new EventStoreClient({
  baseUrl: "http://localhost:9000",
  timeout: 5000, // 5 seconds for tests (vs 30s)
  retries: 1, // Only 1 retry for tests (vs 3)
  retryDelay: 100, // 100ms delay for tests (vs 1000ms)
});
```

### **Server Management in Tests**

```typescript
import { TestSetup } from "https://raw.githubusercontent.com/graeme-lockley/event-store/main/event-store/tests/helpers/test-setup.ts";

describe("Event Store Integration", () => {
  let client: EventStoreClient;
  let serverProcess: Deno.ChildProcess;
  let testSetup: TestSetup;

  beforeAll(async () => {
    // Set up test environment with dedicated directories
    testSetup = new TestSetup("my-test");
    await testSetup.setup();

    // Start Event Store server with test configuration
    serverProcess = new Deno.Command("deno", {
      args: ["run", "-A", "mod.ts"],
      cwd: Deno.cwd(),
      env: {
        "PORT": "9000",
        "DATA_DIR": testSetup.getDataDir(),
        "CONFIG_DIR": testSetup.getConfigDir(),
      },
      stdout: "piped",
      stderr: "piped",
    }).spawn();

    // Wait for server to be ready
    client = new EventStoreClient({
      baseUrl: "http://localhost:9000",
      timeout: 5000,
      retries: 1,
      retryDelay: 100,
    });

    await client.waitForServer();
  });

  afterAll(async () => {
    // Clean up server process
    serverProcess.kill();
    await serverProcess.status;

    // Clean up test directories
    await testSetup.cleanup();
  });

  it("should publish and retrieve events", async () => {
    // Create topic
    await client.createTopic("test-topic", [/* schemas */]);

    // Publish event
    const eventId = await client.publishEvent(
      "test-topic",
      "test.event",
      { message: "Hello World" },
    );

    // Retrieve events
    const events = await client.getEvents("test-topic");
    assertEquals(events.length, 1);
    assertEquals(events[0].id, eventId);
  });
});
```

### **Integration Test Helpers**

```typescript
// Helper for starting Event Store server
async function startEventStoreServer(
  port: number,
  dataDir: string,
  configDir: string,
) {
  const process = new Deno.Command("deno", {
    args: ["run", "-A", "mod.ts"],
    env: {
      "PORT": port.toString(),
      "DATA_DIR": dataDir,
      "CONFIG_DIR": configDir,
    },
    stdout: "piped",
    stderr: "piped",
  }).spawn();

  const client = new EventStoreClient({
    baseUrl: `http://localhost:${port}`,
    timeout: 5000,
    retries: 1,
    retryDelay: 100,
  });

  await client.waitForServer();

  return { process, client };
}

describe("Full Integration", () => {
  let serverProcess: Deno.ChildProcess;
  let client: EventStoreClient;

  beforeAll(async () => {
    const result = await startEventStoreServer(
      9000,
      "./test-data",
      "./test-config",
    );
    serverProcess = result.process;
    client = result.client;
  });

  afterAll(async () => {
    serverProcess.kill();
    await serverProcess.status;
  });

  it("should work end-to-end", async () => {
    const health = await client.getHealth();
    assertEquals(health.status, "healthy");
  });
});
```

### **Best Practices for Embedded Testing**

#### **1. Use Dedicated Test Ports**

```typescript
// Avoid conflicts with running applications
const testPort = 8000 + Math.floor(Math.random() * 1000) + 1000; // 9000-9999 range
```

#### **2. Use Temporary Directories**

```typescript
// Ensure clean test environment
const testSetup = new TestSetup("unique-test-name");
await testSetup.setup();
// ... run tests ...
await testSetup.cleanup();
```

#### **3. Implement Robust Startup Detection**

```typescript
// Don't use fixed timeouts
await client.waitForServer({
  maxWaitTime: 5000,
  pollInterval: 100,
  throwOnTimeout: true,
});
```

#### **4. Clean Up Resources**

```typescript
afterAll(async () => {
  // Always clean up processes and files
  serverProcess.kill();
  await serverProcess.status;
  await testSetup.cleanup();
});
```

#### **5. Use Test-Optimized Settings**

```typescript
// Faster timeouts for tests
const testConfig: EventStoreConfig = {
  baseUrl: "http://localhost:9000",
  timeout: 5000, // 5 seconds for tests
  retries: 1, // Only 1 retry for tests
  retryDelay: 100, // 100ms delay for tests
};
```

---

## **📋 API Endpoints**

### **Topics Management**

#### **Create Topic**

```http
POST /topics
Content-Type: application/json
```

**Request Body:**

```json
{
  "name": "user-events",
  "schemas": [
    {
      "eventType": "user.created",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" },
        "email": { "type": "string" }
      },
      "required": ["id", "name", "email"]
    }
  ]
}
```

**Response (201):**

```json
{
  "message": "Topic 'user-events' created successfully"
}
```

**Error Responses:**

- `400` - Invalid request body (missing name, schemas, or invalid schema format)
- `400` - Topic already exists

#### **List Topics**

```http
GET /topics
```

**Response (200):**

```json
{
  "topics": ["user-events", "audit-events", "notifications"]
}
```

#### **Get Topic Details**

```http
GET /topics/{topic}
```

**Response (200):**

```json
{
  "name": "user-events",
  "sequence": 42,
  "schemas": [
    {
      "eventType": "user.created",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": { ... },
      "required": ["id", "name", "email"]
    }
  ]
}
```

**Error Responses:**

- `404` - Topic not found

---

### **Event Publishing**

#### **Publish Events**

```http
POST /events
Content-Type: application/json
```

**Request Body:**

```json
[
  {
    "topic": "user-events",
    "type": "user.created",
    "payload": {
      "id": "123",
      "name": "Alice Johnson",
      "email": "alice@example.com"
    }
  },
  {
    "topic": "audit-events",
    "type": "user.verified",
    "payload": {
      "id": "123",
      "verified": true
    }
  }
]
```

**Response (201):**

```json
{
  "eventIds": ["user-events-1", "audit-events-1"]
}
```

**Error Responses:**

- `400` - Invalid request (not an array, empty array, missing required fields)
- `400` - Schema validation failed for any event
- `400` - Topic does not exist

---

### **Event Retrieval**

#### **Get Events from Topic**

```http
GET /topics/{topic}/events
```

**Query Parameters:**

- `sinceEventId` (optional) - Get events after this ID
- `date` (optional) - Get events from this date (YYYY-MM-DD format)
- `limit` (optional) - Maximum number of events to return

**Examples:**

```http
GET /topics/user-events/events
GET /topics/user-events/events?sinceEventId=user-events-5
GET /topics/user-events/events?date=2025-01-15
GET /topics/user-events/events?limit=10
GET /topics/user-events/events?sinceEventId=user-events-5&limit=20
```

**Response (200):**

```json
{
  "events": [
    {
      "id": "user-events-1",
      "timestamp": "2025-01-15T10:30:00.000Z",
      "type": "user.created",
      "payload": {
        "id": "123",
        "name": "Alice Johnson",
        "email": "alice@example.com"
      }
    }
  ]
}
```

**Error Responses:**

- `404` - Topic not found
- `500` - Internal server error

---

### **Consumer Management**

#### **Register Consumer**

```http
POST /consumers/register
Content-Type: application/json
```

**Request Body:**

```json
{
  "callback": "https://your-service.com/webhook",
  "topics": {
    "user-events": null,
    "audit-events": "audit-events-5"
  }
}
```

**Notes:**

- `callback` - URL where events will be delivered via POST
- `topics` - Object mapping topic names to last processed event ID (or `null`
  for all events)

**Response (201):**

```json
{
  "consumerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses:**

- `400` - Invalid request (missing callback or topics)
- `400` - Topic not found

#### **List Consumers**

```http
GET /consumers
```

**Response (200):**

```json
{
  "consumers": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "callback": "https://your-service.com/webhook",
      "topics": {
        "user-events": "user-events-10",
        "audit-events": "audit-events-5"
      }
    }
  ]
}
```

#### **Unregister Consumer**

```http
DELETE /consumers/{consumerId}
```

**Response (200):**

```json
{
  "message": "Consumer 550e8400-e29b-41d4-a716-446655440000 unregistered"
}
```

**Error Responses:**

- `404` - Consumer not found

---

### **Health Check**

#### **Get Health Status**

```http
GET /health
```

**Response (200):**

```json
{
  "status": "healthy",
  "consumers": 5,
  "runningDispatchers": ["user-events", "audit-events"]
}
```

---

## **🔄 Consumer Webhook Integration**

### **Webhook Payload Format**

When events are delivered to your webhook, you'll receive:

```http
POST https://your-service.com/webhook
Content-Type: application/json
```

**Request Body:**

```json
{
  "consumerId": "550e8400-e29b-41d4-a716-446655440000",
  "events": [
    {
      "id": "user-events-1",
      "timestamp": "2025-01-15T10:30:00.000Z",
      "type": "user.created",
      "payload": {
        "id": "123",
        "name": "Alice Johnson",
        "email": "alice@example.com"
      }
    }
  ]
}
```

### **Webhook Response Requirements**

Your webhook must return:

- **HTTP 200-299** - Event processed successfully
- **Any other status** - Event processing failed (consumer will be removed)

### **Consumer Lifecycle**

1. **Registration** - Consumer is registered and starts receiving events
2. **Delivery** - Events are sent to your webhook URL
3. **Success** - Your webhook returns 2xx status
4. **Failure** - Your webhook returns non-2xx status or times out
5. **Removal** - Failed consumers are automatically removed

**Timeout:** 30 seconds per webhook call

---

## **📊 Data Models**

### **Event Structure**

```typescript
interface Event {
  id: string; // Format: <topic>-<sequence>
  timestamp: string; // ISO8601 format
  type: string; // Event type (e.g., "user.created")
  payload: any; // Valid JSON payload
}
```

### **Schema Definition**

```typescript
interface Schema {
  eventType: string; // Event type this schema validates
  type: string; // Always "object"
  $schema: string; // JSON Schema version
  properties: Record<string, any>; // Schema properties
  required: string[]; // Required fields
}
```

### **Consumer Registration**

```typescript
interface ConsumerRegistration {
  callback: string; // Webhook URL
  topics: Record<string, string | null>; // Topic → last event ID
}
```

### **Client Configuration**

```typescript
interface EventStoreConfig {
  baseUrl: string; // Event Store server URL
  timeout: number; // Request timeout in milliseconds
  retries: number; // Number of retry attempts
  retryDelay: number; // Delay between retries in milliseconds
}
```

---

## **💡 Usage Patterns**

### **1. Event-Driven Architecture**

```typescript
// Using the client library
const client = new EventStoreClient({
  baseUrl: "http://localhost:8000",
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
});

// 1. Create topic with schemas
await client.createTopic("user-events", [/* schema definitions */]);

// 2. Register consumer
const consumerId = await client.registerConsumer({
  callback: "https://your-service.com/webhook",
  topics: { "user-events": null },
});

// 3. Publish events
const eventId = await client.publishEvent(
  "user-events",
  "user.created",
  { id: "123", name: "Alice", email: "alice@example.com" },
);
```

### **2. Event Sourcing**

```typescript
// Retrieve all events from a topic
const events = await client.getEvents("user-events");

// Get events since a specific point
const events = await client.getEvents("user-events", {
  sinceEventId: "user-events-100",
});
```

### **3. Audit Trail**

```typescript
// Get events from a specific date
const events = await client.getEvents("audit-events", {
  date: "2025-01-15",
});
```

### **4. Integration Testing**

```typescript
// Test-optimized client for fast, reliable tests
const testClient = new EventStoreClient({
  baseUrl: "http://localhost:9000",
  timeout: 5000,
  retries: 1,
  retryDelay: 100,
});

// Wait for server to be ready
await testClient.waitForServer();

// Run your tests
const health = await testClient.getHealth();
assertEquals(health.status, "healthy");
```

---

## **⚠️ Important Notes**

### **Event ID Format**

- Format: `<topic>-<sequence>`
- Examples: `user-events-1`, `audit-events-42`
- Sequential within each topic
- Globally unique across the system

### **Schema Validation**

- All events must conform to their topic's schemas
- Validation happens at publish time
- Invalid events are rejected with 400 status

### **Consumer Reliability**

- Deliveries use exponential backoff on failures (up to max attempts)
- Consumers are unregistered only after repeated failures
- Implement idempotency in your webhook

### **File Storage**

- Events are stored as individual JSON files
- Organized by topic, date, and grouping
- No database required

### **Performance**

- Near-instantaneous event delivery
- File-based storage for durability
- Background dispatchers for each topic

### **Testing Best Practices**

- Use dedicated test ports (9000-9999 range)
- Use temporary directories for test data
- Implement robust startup detection with polling
- Always clean up resources after tests
- Use test-optimized client settings for faster feedback

---

## **🚨 Error Handling**

### **Common Error Responses**

```json
// 400 - Bad Request
{
  "error": "Invalid request body. Required: name, schemas array"
}

// 404 - Not Found
{
  "error": "Topic 'non-existent' not found"
}

// 500 - Internal Server Error
{
  "error": "Failed to store event"
}
```

### **Client Error Handling**

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

### **Best Practices**

1. **Always check response status** before processing JSON
2. **Handle 404s gracefully** for missing topics/consumers
3. **Validate event payloads** against schemas before publishing
4. **Implement idempotent webhooks** to handle duplicate deliveries
5. **Monitor consumer health** via the `/health` endpoint
6. **Use the client library** for type safety and error handling
7. **Implement robust testing** with embedded server management
8. **Use test-optimized settings** for faster test feedback

---

This documentation provides everything needed to effectively use the Event Store
API and client library, including comprehensive testing practices for reliable
integration testing.
