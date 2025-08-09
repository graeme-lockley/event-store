# 📄 Product Specification: Lightweight Event Store

## 📘 Overview

The Event Store is a lightweight, file-backed, API-driven message recording and
delivery system. It allows clients to define **topics**, register **event
schemas**, **publish events**, and **register consumers** who receive events
through **callback URLs**. The system ensures schema validation, durable
storage, and **asynchronous event dispatching**, with consumers being
**ephemeral** and automatically removed upon failure.

---

## 🎯 Goals

- Enable durable event publishing with strict schema validation
- Support topic-based event segregation
- Allow external services to consume events through callback registration
- Deliver events asynchronously and independently from publishing
- Ensure globally unique and traceable event IDs
- Provide a simple and composable Deno-based implementation

---

## 🧱 Features

### 1. **API-Driven Architecture**

All operations are exposed via HTTP endpoints:

- Topic management
- Event publishing
- Consumer registration

### 2. **File-Backed Storage with Per-Event Files**

- Each event is stored in its **own file**.
- Events are organized by:
  - **Topic**
  - **Date (YYYY-MM-DD)**
  - **1,000-event subdirectory groupings**

Example structure:

```
data/
├── user-events/
│   ├── 2025-07-06/
│   │   ├── 0000/
│   │   │   ├── user-events-1.json
│   │   │   ├── user-events-2.json
│   │   ├── 0001/
│   │   │   ├── user-events-1001.json
├── audit-events/
│   ├── 2025-07-06/
│   │   ├── 0000/
│   │   │   ├── audit-events-1.json
```

- Each file contains a complete serialized JSON event.
- Grouping by 1,000-event folders avoids performance issues with large flat
  directories.

### 3. **Globally Unique Event IDs**

- Event ID format: `<topic>-<sequence>`
- Example: `user-events-42`
- Sequence is a monotonic counter per topic, incremented by the system.
- Event ID also determines filename and placement in the file structure.

### 4. **Topic-Based Partitioning and Configuration**

- Topics must be explicitly created before use.
- Each topic has an associated configuration file stored in `config/` as
  `<topic>.json`.
- Example: `config/user-events.json`
- The configuration file contains:
  - Topic name
  - Sequence counter (to generate next event ID)
  - Array of JSON Schemas with embedded `type` names for validation routing

Example:

```json
{
  "name": "user-events",
  "sequence": 1287,
  "schemas": [
    {
      "type": "user.created",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" }
      },
      "required": ["id", "name"]
    }
  ]
}
```

### 5. **JSON Schema Validation**

- When a topic is created, a set of one or more **JSON Schemas** is registered.
- Each incoming event is validated against one of the schemas before acceptance.

### 6. **Ephemeral Consumer Registration**

- Consumers register with:
  - A callback URL
  - A list of topics
  - Optionally, last consumed event IDs per topic
- Consumers are stored in-memory only and not persisted.
- Each consumer is represented as an object that encapsulates:
  - Registration details
  - State tracking per topic
  - A `nudge()` method, which is triggered whenever an event is published to a
    relevant topic, prompting the consumer to check whether it is up-to-date and
    initiate delivery if necessary. In this way, consumers can be brought
    up-to-date near instantaneously.

### 7. **Asynchronous Dispatching**

- Events are dispatched to registered consumers **out of band** from publishing.
- Each topic has a dedicated **dispatcher** running in the background.
- Consumers are removed automatically if their callback returns an error or
  fails to respond.

---

## ⚙️ Technical Design

### 📁 Directory Structure

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
├── schemas/
│   └── schemaStore.ts       # Schema management per topic
├── data/                    # Topic/date/grouped event files
├── utils/
│   └── validate.ts          # Schema validation
└── deps.ts                  # Centralized imports
```

### 🛠 Data Models

#### Event

```ts
type Event = {
  id: string; // Generated as <topic>-<sequence>
  timestamp: string; // ISO8601
  type: string; // e.g. "user.created"
  payload: any; // Valid JSON
};
```

#### Consumer

```ts
type Consumer = {
  id: string; // UUID
  callback: string; // URL
  topics: Record<string, string | null>; // topic → lastEventId
  nudge(): Promise<void>; // Triggered when a new event is published
};
```

---

## 🔁 Dispatcher Logic (Per Topic)

- Runs on its own interval (e.g., 500ms)
- Tracks the latest known event ID per topic
- Uses file system walk to locate new events after a given ID
- Triggers each relevant consumer’s `nudge()` method
- Within `nudge()`, if events are pending:
  - Deliver event via `fetch()`
  - On callback failure: unregister consumer

---

## 🌐 API Endpoints

### `POST /topics`

Create a new topic and register associated schemas.

```json
{
  "name": "user-events",
  "schemas": [{/* JSON Schema 1 */}, {/* Schema 2 */}]
}
```

### `POST /events`

Publish one or more events across one or more topics in a single request. Each
event must conform to its topic’s schema. If any event fails validation or
references an unknown topic, the entire batch is rejected.

**Request:**

```json
[
  {
    "topic": "user-events",
    "type": "user.created",
    "payload": {
      "id": "123",
      "name": "Alice"
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

**Response:**

```json
{
  "eventIds": [
    "user-events-1",
    "audit-events-1"
  ]
}
```

### `POST /consumers/register`

Register a new consumer.

```json
{
  "callback": "https://example.com/webhook",
  "topics": {
    "user-events": null,
    "audit-events": "audit-events-3"
  }
}
```

### `GET /topics/:topic/events`

Retrieve events from a topic since a specific event ID. Optional query params:
`sinceEventId`, `date`, `limit`

---

## 🚫 Consumer Removal Logic

- A consumer is removed if:
  - Its callback returns a non-2xx status
  - The callback throws or times out
- Removal is logged (console or future audit topic)

---

## 🧪 Testing Strategy

- Unit tests for:
  - Schema validation
  - Event persistence and ID generation
  - Dispatcher delivery logic
- Integration tests:
  - Publish → Dispatch → Callback → Removal on error
- Deno native testing tools (`deno test`)

---

## 🧭 Future Enhancements (V2+ Ideas)

- Optional persistence of consumer registry
- Retry/backoff mechanism for delivery
- Delivery filtering by event type or criteria
- File compaction or archival
- Prometheus-style metrics

---

## 📌 Summary

This event store provides a minimalist, file-backed, schema-validated,
asynchronous message bus with no dependencies outside of the Deno runtime. Each
event is uniquely identified and stored as its own file in a structured
directory layout to facilitate efficient retrieval and scalability. The system
is ideal for simple pipelines, event replay scenarios, and auditability use
cases.
