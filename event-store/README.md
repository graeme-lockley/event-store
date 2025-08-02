# 🚀 Event Store Backend

A lightweight, file-backed, API-driven message recording and delivery system
built with Deno.

## ✨ Features

- **File-backed storage** with per-event files organized by topic, date, and
  grouping
- **JSON Schema validation** for all incoming events
- **Topic-based partitioning** with explicit topic creation and configuration
- **Asynchronous event dispatching** with background dispatchers per topic
- **Ephemeral consumer registration** with automatic removal on failure
- **Globally unique event IDs** in format `<topic>-<sequence>`
- **Near-instantaneous delivery** via consumer `nudge()` mechanism
- **No external dependencies** beyond the Deno runtime

## 🏗 Architecture

```
event-store/
├── mod.ts                   # Entry point
├── api/
│   └── routes.ts            # HTTP routes
├── core/
│   ├── topics.ts            # Topic & schema management
│   ├── events.ts            # Event persistence & reading
│   ├── dispatcher.ts        # Asynchronous dispatchers
│   └── consumers.ts         # Consumer registry and logic
├── utils/
│   └── validate.ts          # Schema validation
├── types.ts                 # Type definitions
└── deps.ts                  # Centralized imports
```

## 🚀 Quick Start

### 1. Install Deno

Make sure you have [Deno](https://deno.land/) installed on your system.

### 2. Run the Event Store

```bash
deno run --allow-net --allow-read --allow-write --allow-env mod.ts
```

The server will start on port 8000 (or the port specified in the `PORT`
environment variable).

### 3. Create a Topic

```bash
curl -X POST http://localhost:8000/topics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-events",
    "schemas": [
      {
        "type": "user.created",
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "properties": {
          "id": { "type": "string" },
          "name": { "type": "string" },
          "email": { "type": "string" }
        },
        "required": ["id", "name", "email"]
      },
      {
        "type": "user.updated",
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

### 4. Publish Events

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

### 5. Register a Consumer

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

### 6. Retrieve Events

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
      "type": "event.type",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": { ... },
      "required": [ ... ]
    }
  ]
}
```

#### `GET /topics`

List all topics.

#### `GET /topics/:topic`

Get topic details.

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

#### `GET /topics/:topic/events`

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

#### `DELETE /consumers/:id`

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
deno test --allow-net --allow-read --allow-write
```

Or run the example:

```bash
deno run --allow-net --allow-read --allow-write --allow-env test_example.ts
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

## 🛡 Permissions

The following Deno permissions are required:

- `--allow-net` - For HTTP server and webhook delivery
- `--allow-read` - For reading configuration and event files
- `--allow-write` - For writing event files and configuration

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is open source and available under the MIT License.
