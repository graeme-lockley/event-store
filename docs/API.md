# Event Store API Reference

## Overview

The Event Store provides a RESTful API for managing topics, publishing events,
and registering consumers. The API follows RESTful principles and returns JSON
responses.

## Base URL

```
http://localhost:8000
```

## Endpoints

### Topics

#### POST /topics

Create a new topic and register associated schemas

**Request Body:**

```json
{
  "name": "string",
  "schemas": [
    {
      "eventType": "string",
      "type": "string",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
        "propertyName": {
          "type": "string"
        }
      },
      "required": ["propertyName"]
    }
  ]
}
```

**Response (201 Created):**

```json
{
  "message": "Topic '{name}' created successfully"
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Invalid request body. Required: name, schemas array"
}
```

#### GET /topics

List all topics

**Response (200 OK):**

```json
{
  "topics": ["user-events", "audit-events"]
}
```

#### GET /topics/{topic}

Get detailed information about a specific topic

**Response (200 OK):**

```json
{
  "name": "string",
  "sequence": "number",
  "schemas": [...]
}
```

**Error Response (404 Not Found):**

```json
{
  "error": "Topic '{topic}' not found",
  "code": "TOPIC_NOT_FOUND"
}
```

### Events

#### POST /events

Publish one or more events

**Request Body:**

```json
[
  {
    "topic": "string",
    "type": "string",
    "payload": { "...": "object" }
  }
]
```

**Response (201 Created):**

```json
{
  "eventIds": ["string"]
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Request body must be a non-empty array of events",
  "code": "INVALID_REQUEST"
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Each event must have topic, type, and payload",
  "code": "INVALID_EVENT"
}
```

#### GET /topics/{topic}/events

Retrieve events from a topic

**Query Parameters:**

- `sinceEventId` (optional): Get events after this event ID
- `date` (optional): Get events from a specific date (YYYY-MM-DD format)
- `limit` (optional): Number of events to return (default: 100)

**Response (200 OK):**

```json
{
  "events": [
    {
      "id": "string",
      "timestamp": "string",
      "type": "string",
      "payload": "any"
    }
  ]
}
```

**Error Response (404 Not Found):**

```json
{
  "error": "Topic '{topic}' not found",
  "code": "TOPIC_NOT_FOUND"
}
```

### Consumers

#### POST /consumers/register

Register a new consumer

**Request Body:**

```json
{
  "callback": "string (valid URL)",
  "topics": {
    "topicName": "lastEventId|null"
  }
}
```

**Response (201 Created):**

```json
{
  "consumerId": "string"
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Invalid request body. Required: callback URL and topics object",
  "code": "INVALID_REQUEST"
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Topic '{topic}' not found",
  "code": "TOPIC_NOT_FOUND"
}
```

#### GET /consumers

List all consumers

**Response (200 OK):**

```json
{
  "consumers": [
    {
      "id": "string",
      "callback": "string",
      "topics": {
        "topicName": "lastEventId|null"
      }
    }
  ]
}
```

#### DELETE /consumers/{id}

Unregister a consumer

**Response (200 OK):**

```json
{
  "message": "Consumer {id} unregistered"
}
```

**Error Response (400 Bad Request):**

```json
{
  "error": "Consumer ID is required",
  "code": "INVALID_REQUEST"
}
```

**Error Response (404 Not Found):**

```json
{
  "error": "Consumer {id} not found",
  "code": "CONSUMER_NOT_FOUND"
}
```

### Health

#### GET /health

Check service health

**Response (200 OK):**

```json
{
  "status": "healthy",
  "consumers": "number",
  "runningDispatchers": ["string"]
}
```

## Error Responses

All error responses follow this format:

```json
{
  "error": "string",
  "code": "OPTIONAL_CODE"
}
```

## Status Codes

- `200`: Success
- `201`: Created
- `400`: Bad Request
- `404`: Not Found
- `500`: Internal Server Error

## Example Usage

### Create a Topic

```bash
curl -X POST http://localhost:8000/topics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-events",
    "schemas": [
      {
        "eventType": "user.created",
        "type": "user.created",
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "properties": {
          "id": {"type": "string"},
          "name": {"type": "string"}
        },
        "required": ["id", "name"]
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
        "name": "John Doe"
      }
    }
  ]'
```

### Register a Consumer

```bash
curl -X POST http://localhost:8000/consumers/register \
  -H "Content-Type: application/json" \
  -d '{
    "callback": "http://localhost:3000/webhook",
    "topics": {
      "user-events": null
    }
  }'
```

### Get Events from a Topic

```bash
curl "http://localhost:8000/topics/user-events/events?limit=10&sinceEventId=event123"
```

### Check Health

```bash
curl http://localhost:8000/health
```
