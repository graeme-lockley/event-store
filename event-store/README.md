# 🚀 Event Store Backend (Kotlin)

A lightweight, file-backed, API-driven message recording and delivery system built with Kotlin, Gradle, and Ktor using pure hexagonal architecture (Ports & Adapters).

## ✨ Features

- **File-backed storage** with per-event files organized by topic, date, and grouping
- **JSON Schema validation** for all incoming events using networknt/json-schema-validator
- **Topic-based partitioning** with explicit topic creation and configuration
- **Asynchronous event dispatching** with background dispatchers per topic using Kotlin coroutines
- **Ephemeral consumer registration** with automatic removal on failure
- **Globally unique event IDs** in format `<topic>-<sequence>`
- **Near-instantaneous delivery** via consumer nudge mechanism
- **Pure Hexagonal Architecture** (Ports & Adapters pattern)

## 🏗 Architecture

The implementation follows **pure hexagonal architecture** (Ports & Adapters pattern) with the following structure:

```
event-store/
├── src/main/kotlin/com/eventstore/
│   ├── Application.kt              # Entry point and Ktor setup
│   ├── Config.kt                   # Configuration from environment
│   ├── domain/                     # Domain (the hexagon - core business logic)
│   │   ├── Event.kt                # Domain entities
│   │   ├── Topic.kt
│   │   ├── Consumer.kt
│   │   ├── Schema.kt
│   │   ├── EventId.kt
│   │   ├── exceptions/             # Domain exceptions
│   │   ├── services/              # Domain services (use cases)
│   │   │   ├── CreateTopicService.kt
│   │   │   ├── PublishEventsService.kt
│   │   │   └── ...
│   │   └── ports/                  # Ports (interfaces)
│   │       └── outbound/          # Outbound ports (what domain needs)
│   │           ├── TopicRepository.kt
│   │           ├── EventRepository.kt
│   │           ├── ConsumerRepository.kt
│   │           ├── SchemaValidator.kt
│   │           └── ConsumerDeliveryService.kt
│   ├── infrastructure/             # Infrastructure (adapters)
│   │   ├── persistence/           # Adapters implementing repository ports
│   │   ├── external/              # Adapters implementing service ports
│   │   └── background/           # Background dispatchers
│   └── interfaces/                 # Primary adapters (HTTP API)
│       ├── routes/                 # Ktor routes (inbound adapter)
│       └── dto/                   # Data transfer objects
└── build.gradle.kts
```

### Hexagonal Architecture Principles

- **Domain (Core)**: Contains business logic, entities, domain services, and ports (interfaces). The domain defines what it needs through ports.
- **Infrastructure (Adapters)**: Implements the ports defined by the domain. Can be swapped without changing domain code.
- **Interfaces (Primary Adapters)**: Entry points like HTTP routes that drive the application.

The domain is independent and defines its needs through ports. Infrastructure provides implementations of those ports.

## 🚀 Quick Start

### Prerequisites

- Java 17 or higher
- Gradle 8.5 or higher (wrapper included)

### Build the Project

```bash
./gradlew build
```

### Run the Event Store

```bash
./gradlew run
```

Or build and run the JAR directly:

```bash
./gradlew jar
java -jar build/libs/event-store-1.0.0.jar
```

The JAR includes all dependencies (fat JAR) and can be run standalone.

The server will start on port 8000 (or the port specified in the `PORT` environment variable).

### Environment Variables

- `PORT` - Server port (default: 8000)
- `DATA_DIR` - Event storage directory (default: `./data`)
- `CONFIG_DIR` - Topic config directory (default: `./config`)
- `MAX_BODY_BYTES` - Max request body size in bytes (default: 1048576)
- `RATE_LIMIT_PER_MINUTE` - Rate limit per IP per route (default: 600)

### Create a Topic

```bash
curl -X POST http://localhost:8000/topics \
  -H "Content-Type: application/json" \
  -d '{
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
      },
      {
        "eventType": "user.updated",
        "type": "object",
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "properties": {
          "id": { "type": "string" },
          "name": { "type": "string" }
        },
        "required": ["id"]
      }
    ]
  }'
```

### Publish Events

```bash
curl -X POST http://localhost:8000/events \
  -H "Content-Type: application/json" \
  -d '[
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
      "topic": "user-events",
      "type": "user.updated",
      "payload": {
        "id": "123",
        "name": "Alice Smith"
      }
    }
  ]'
```

### Register a Consumer

```bash
curl -X POST http://localhost:8000/consumers/register \
  -H "Content-Type: application/json" \
  -d '{
    "callback": "https://your-service.com/webhook",
    "topics": {
      "user-events": null
    }
  }'
```

### Retrieve Events

```bash
# Get all events from a topic
curl http://localhost:8000/topics/user-events/events

# Get events since a specific event ID
curl "http://localhost:8000/topics/user-events/events?sinceEventId=user-events-5"

# Get events from a specific date
curl "http://localhost:8000/topics/user-events/events?date=2025-01-15"

# Limit the number of events
curl "http://localhost:8000/topics/user-events/events?limit=10"
```

## 📋 API Reference

### Topics

#### `POST /topics`

Create a new topic with schemas.

**Request Body:**

```json
{
  "name": "topic-name",
  "schemas": [
    {
      "eventType": "event.type",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": { ... },
      "required": [ ... ]
    }
  ]
}
```

#### `GET /topics`

List all topics.

#### `GET /topics/{topic}`

Get topic details.

#### `PUT /topics/{topic}`

Update schemas for an existing topic. Schema updates are **additive only** - you cannot remove schemas, only add or update them.

**Request Body:**

```json
{
  "schemas": [
    {
      "eventType": "event.type",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": { ... },
      "required": [ ... ]
    }
  ]
}
```

**Note:** All existing `eventType`s must be present in the update request. New schemas can be added, and existing schemas are updated by matching `eventType`.

### Events

#### `POST /events`

Publish one or more events.

**Request Body:**

```json
[
  {
    "topic": "topic-name",
    "type": "event.type",
    "payload": { ... }
  }
]
```

**Response:**

```json
{
  "eventIds": ["topic-name-1", "topic-name-2"]
}
```

#### `GET /topics/{topic}/events`

Retrieve events from a topic.

**Query Parameters:**

- `sinceEventId` - Get events after this ID
- `date` - Get events from this date (YYYY-MM-DD)
- `limit` - Maximum number of events to return

### Consumers

#### `POST /consumers/register`

Register a new consumer.

**Request Body:**

```json
{
  "callback": "https://your-service.com/webhook",
  "topics": {
    "topic-name": "last-event-id-or-null"
  }
}
```

**Response:**

```json
{
  "consumerId": "uuid"
}
```

#### `GET /consumers`

List all registered consumers.

#### `DELETE /consumers/{id}`

Unregister a consumer.

### Health

#### `GET /health`

Health check endpoint.

**Response:**

```json
{
  "status": "healthy",
  "consumers": 5,
  "runningDispatchers": ["topic1", "topic2"]
}
```

## 🔧 Configuration

### Environment Variables

- `PORT` - Server port (default: 8000)
- `DATA_DIR` - Event storage directory (default: `./data`)
- `CONFIG_DIR` - Topic config directory (default: `./config`)
- `MAX_BODY_BYTES` - Max request body size (default: 1048576)
- `RATE_LIMIT_PER_MINUTE` - Rate limit (default: 600)

### File Structure

- `config/` - Topic configuration files
- `data/` - Event storage directory

## 📁 File Storage Structure

Each event is stored in its own file organized by:

```
data/
├── user-events/
│   ├── 2025-01-15/
│   │   ├── 0000/
│   │   │   ├── user-events-1.json
│   │   │   ├── user-events-2.json
│   │   ├── 0001/
│   │   │   ├── user-events-1001.json
├── audit-events/
│   ├── 2025-01-15/
│   │   ├── 0000/
│   │   │   ├── audit-events-1.json
```

## 🧪 Testing

Run the test suite:

```bash
./gradlew test
```

## 🔄 Consumer Webhook Format

When events are delivered to consumers, the webhook receives:

```json
{
  "consumerId": "uuid",
  "events": [
    {
      "id": "topic-name-1",
      "timestamp": "2025-01-15T10:30:00.000Z",
      "type": "event.type",
      "payload": { ... }
    }
  ]
}
```

## 🚫 Consumer Removal

Consumers are automatically removed if:

- The callback returns a non-2xx status code
- The callback throws an error or times out
- The callback fails to respond within 30 seconds
- After 5 failed delivery attempts with exponential backoff

## 🔍 Event ID Format

Event IDs follow the pattern: `<topic>-<sequence>`

Examples:

- `user-events-1`
- `audit-events-42`
- `notifications-1001`

## 📝 Logging

The system logs:

- Topic creation and schema registration
- Event publishing with IDs
- Consumer registration and removal
- Event delivery attempts and failures
- Dispatcher start/stop events

## 🛠 Technology Stack

- **Kotlin** 1.9.22
- **Ktor** 2.3.8 - Web framework
- **Kotlinx Coroutines** - Asynchronous programming
- **Jackson** - JSON serialization
- **networknt/json-schema-validator** - JSON Schema validation
- **SLF4J + Logback** - Logging
- **Gradle** 8.5 - Build system

## 🏛 Architecture Principles

This implementation follows **Pure Hexagonal Architecture** (Ports & Adapters):

- **Domain (Core)**: 
  - Pure business logic with no framework dependencies
  - Domain entities (Event, Topic, Consumer, Schema)
  - Domain services (business use cases)
  - Ports (interfaces) defining what the domain needs from outside
- **Infrastructure (Adapters)**: 
  - Implements the ports defined by the domain
  - File system repositories, HTTP clients, validators
  - Can be swapped without changing domain code
- **Interfaces (Primary Adapters)**: 
  - HTTP routes (Ktor) that drive the application
  - DTOs for API communication

### Key Benefits:

- **Domain Independence**: Domain code has no dependencies on infrastructure
- **Testability**: Domain can be tested with mock adapters
- **Flexibility**: Easy to swap implementations (e.g., database instead of files)
- **Maintainability**: Clear separation of concerns, changes isolated to specific layers
- **Port-Driven**: Domain defines what it needs (ports), infrastructure provides it (adapters)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is open source and available under the MIT License.

