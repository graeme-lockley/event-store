# Event Store API Reference

## Overview

The Event Store provides a RESTful API for managing tenants, namespaces, topics, publishing events, and registering consumers. The API follows RESTful principles and returns JSON responses.

All endpoints are scoped by tenant and namespace, providing multi-tenant isolation.

## Base URL

```
http://localhost:8000
```

## Authentication

The API supports two authentication methods:

1. **Session-based authentication** - Login via `/auth/login` to receive a session cookie
2. **API Key authentication** - Include API key in `Authorization` header: `Bearer <api-key>`

When authentication is enabled, most endpoints require authentication. See [Authentication](#authentication-1) section for details.

## Endpoints

### Tenant Management

#### `POST /tenants`

Create a new tenant.

**Request Body:**
```json
{
  "name": "acme-corp",
  "quota": {
    "maxTopics": 100,
    "maxNamespaces": 10,
    "maxEventsPerDay": 1000000,
    "maxConsumers": 50,
    "maxUsers": 20,
    "maxEventSizeBytes": 1048576
  },
  "metadata": {
    "contact": "admin@acme-corp.com"
  }
}
```

**Response (201 Created):**
```json
{
  "id": "acme-corp",
  "name": "acme-corp",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "quota": { ... },
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Tenant already exists (`TENANT_EXISTS`)
- `400 Bad Request` - Feature disabled (`FEATURE_DISABLED`)

#### `GET /tenants`

List all tenants.

**Response (200 OK):**
```json
{
  "tenants": [
    {
      "id": "acme-corp",
      "name": "acme-corp",
      "createdAt": "2025-01-15T10:30:00.000Z",
      ...
    }
  ]
}
```

#### `GET /tenants/{tenantName}`

Get tenant details.

**Response (200 OK):**
```json
{
  "id": "acme-corp",
  "name": "acme-corp",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T11:00:00.000Z",
  "deletedAt": null,
  "quota": { ... },
  "metadata": { ... }
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Tenant not found",
  "code": "TENANT_NOT_FOUND"
}
```

#### `PUT /tenants/{tenantName}`

Update tenant.

**Request Body:**
```json
{
  "name": "acme-corp-updated",
  "quota": { ... },
  "metadata": { ... }
}
```

#### `DELETE /tenants/{tenantName}`

Delete tenant (soft delete).

**Request Body (optional):**
```json
{
  "reason": "Account closed"
}
```

### Namespace Management

#### `POST /tenants/{tenantName}/namespaces`

Create a namespace within a tenant.

**Request Body:**
```json
{
  "name": "production",
  "description": "Production namespace",
  "metadata": {
    "environment": "prod"
  }
}
```

**Response (201 Created):**
```json
{
  "tenantId": "acme-corp",
  "id": "production",
  "name": "production",
  "description": "Production namespace",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Tenant not found (`TENANT_NOT_FOUND`)
- `400 Bad Request` - Namespace already exists (`NAMESPACE_EXISTS`)

#### `GET /tenants/{tenantName}/namespaces`

List all namespaces in a tenant.

**Response (200 OK):**
```json
{
  "namespaces": [
    {
      "tenantId": "acme-corp",
      "id": "production",
      "name": "production",
      ...
    }
  ]
}
```

#### `GET /tenants/{tenantName}/namespaces/{namespaceName}`

Get namespace details.

**Response (200 OK):**
```json
{
  "tenantId": "acme-corp",
  "id": "production",
  "name": "production",
  "description": "Production namespace",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T11:00:00.000Z",
  "deletedAt": null,
  "metadata": { ... }
}
```

#### `PUT /tenants/{tenantName}/namespaces/{namespaceName}`

Update namespace.

**Request Body:**
```json
{
  "name": "production-updated",
  "description": "Updated description",
  "metadata": { ... }
}
```

#### `DELETE /tenants/{tenantName}/namespaces/{namespaceName}`

Delete namespace.

**Request Body (optional):**
```json
{
  "reason": "Namespace deprecated"
}
```

### Topic Management

#### `POST /tenants/{tenantName}/namespaces/{namespaceName}/topics`

Create a new topic with schemas.

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
}
```

**Response (201 Created):**
```json
{
  "message": "Topic 'user-events' created in acme-corp/production"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request body (`INVALID_REQUEST`)
- `400 Bad Request` - Topic already exists (`TOPIC_CREATION_FAILED`)

#### `GET /tenants/{tenantName}/namespaces/{namespaceName}/topics`

List all topics in a namespace.

**Response (200 OK):**
```json
{
  "topics": [
    {
      "name": "user-events",
      "sequence": 42,
      "schemas": [ ... ]
    }
  ]
}
```

#### `GET /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}`

Get topic details.

**Response (200 OK):**
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
      "required": [ ... ]
    }
  ]
}
```

**Error Response (404 Not Found):**
```json
{
  "error": "Topic not found",
  "code": "TOPIC_NOT_FOUND"
}
```

#### `PUT /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}`

Update schemas for an existing topic. Schema updates are **additive only** - you can add new schemas or update existing ones (by `eventType`), but you cannot remove schemas.

**Request Body:**
```json
{
  "schemas": [
    {
      "eventType": "user.created",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
        "id": { "type": "string" },
        "name": { "type": "string" },
        "email": { "type": "string" },
        "phone": { "type": "string" }
      },
      "required": ["id", "name", "email", "phone"]
    },
    {
      "eventType": "user.deleted",
      "type": "object",
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "properties": {
        "id": { "type": "string" }
      },
      "required": ["id"]
    }
  ]
}
```

**Response (200 OK):**
```json
{
  "message": "Topic 'user-events' schemas updated successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request body (`INVALID_REQUEST`)
- `400 Bad Request` - Cannot remove schemas (`SCHEMA_REMOVAL_NOT_ALLOWED`)
- `404 Not Found` - Topic not found (`TOPIC_NOT_FOUND`)

**Important Notes:**
- All existing `eventType`s must be present in the update request (additive constraint)
- New schemas can be added alongside existing ones
- Existing schemas are updated by matching `eventType`
- Schema updates are immediately effective for new events
- The topic's sequence number is preserved during schema updates

### Event Operations

#### `POST /tenants/{tenantName}/namespaces/{namespaceName}/events`

Publish one or more events.

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
    "topic": "user-events",
    "type": "user.updated",
    "payload": {
      "id": "123",
      "name": "Alice Smith"
    }
  }
]
```

**Response (201 Created):**
```json
{
  "eventIds": ["user-events-1", "user-events-2"]
}
```

**Error Responses:**
- `400 Bad Request` - Request body must be a non-empty array (`INVALID_REQUEST`)
- `400 Bad Request` - Each event must have topic, type, and payload (`INVALID_EVENT`)
- `400 Bad Request` - Topic not found (`EVENT_PUBLISH_FAILED`)
- `400 Bad Request` - Schema validation failed (`EVENT_PUBLISH_FAILED`)

#### `GET /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}/events`

Retrieve events from a topic.

**Query Parameters:**
- `sinceEventId` (optional): Get events after this event ID
- `date` (optional): Get events from a specific date (YYYY-MM-DD format)
- `limit` (optional): Number of events to return (default: 100)

**Response (200 OK):**
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
- `404 Not Found` - Topic not found (`TOPIC_NOT_FOUND`)
- `500 Internal Server Error` - Events fetch failed (`EVENTS_FETCH_FAILED`)

### Consumer Management

#### `POST /tenants/{tenantName}/namespaces/{namespaceName}/consumers/register`

Register a new consumer.

**Request Body:**
```json
{
  "callback": "https://your-service.com/webhook",
  "topics": {
    "user-events": null
  }
}
```

The `topics` object maps topic names to the last event ID the consumer has processed. Use `null` to start from the beginning.

**Response (201 Created):**
```json
{
  "consumerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request (`INVALID_REQUEST`)
- `400 Bad Request` - Topic not found (`TOPIC_NOT_FOUND`)
- `400 Bad Request` - Invalid registration (`CONSUMER_REGISTRATION_FAILED`)

#### `GET /tenants/{tenantName}/namespaces/{namespaceName}/consumers`

List all consumers in a namespace.

**Response (200 OK):**
```json
{
  "consumers": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "callback": "https://your-service.com/webhook",
      "topics": {
        "user-events": "user-events-42"
      }
    }
  ]
}
```

#### `DELETE /tenants/{tenantName}/namespaces/{namespaceName}/consumers/{id}`

Unregister a consumer.

**Response (200 OK):**
```json
{
  "message": "Consumer 550e8400-e29b-41d4-a716-446655440000 unregistered"
}
```

**Error Responses:**
- `400 Bad Request` - Consumer ID is required (`INVALID_REQUEST`)
- `404 Not Found` - Consumer not found (`CONSUMER_NOT_FOUND`)

### User Management

#### `POST /tenants/{tenantId}/users`

Create a user.

**Request Body:**
```json
{
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "password": "secure-password",
  "primaryTenantId": "acme-corp",
  "metadata": {
    "department": "Engineering"
  }
}
```

**Response (201 Created):**
```json
{
  "id": "user-123",
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "primaryTenantId": "acme-corp",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "metadata": { ... }
}
```

**Error Response (400 Bad Request):**
```json
{
  "error": "User exists",
  "code": "USER_EXISTS"
}
```

#### `GET /tenants/{tenantId}/users`

List all users.

**Response (200 OK):**
```json
{
  "users": [
    {
      "id": "user-123",
      "email": "alice@example.com",
      "name": "Alice Johnson",
      ...
    }
  ]
}
```

#### `GET /tenants/{tenantId}/users/{userId}`

Get user details.

**Response (200 OK):**
```json
{
  "id": "user-123",
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "primaryTenantId": "acme-corp",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T11:00:00.000Z",
  "metadata": { ... }
}
```

#### `PUT /tenants/{tenantId}/users/{userId}`

Update user.

**Request Body:**
```json
{
  "email": "alice.new@example.com",
  "name": "Alice Smith",
  "metadata": { ... }
}
```

#### `DELETE /tenants/{tenantId}/users/{userId}`

Delete user.

**Response (200 OK):**
```json
{
  "message": "User 'user-123' deleted"
}
```

#### `POST /tenants/{tenantId}/users/{userId}/tenants`

Assign user to tenant.

**Request Body:**
```json
{
  "tenantId": "another-tenant",
  "role": "member",
  "isPrimary": false
}
```

#### `DELETE /tenants/{tenantId}/users/{userId}/tenants/{tenantId}`

Remove user from tenant.

**Response (200 OK):**
```json
{
  "message": "User 'user-123' removed from tenant 'another-tenant'"
}
```

### API Key Management

#### `POST /tenants/{tenantId}/users/{userId}/api-keys`

Create an API key for a user.

**Request Body:**
```json
{
  "name": "Production API Key",
  "description": "API key for production services",
  "expiresAt": "2026-01-15T10:30:00.000Z"
}
```

**Response (201 Created):**
```json
{
  "id": "key-123",
  "name": "Production API Key",
  "key": "es_live_abc123...",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "expiresAt": "2026-01-15T10:30:00.000Z"
}
```

**Note:** The `key` field is only returned once on creation. Store it securely.

**Error Responses:**
- `400 Bad Request` - name is required (`INVALID_INPUT`)
- `400 Bad Request` - name exceeds maximum length (`INVALID_INPUT`)
- `400 Bad Request` - Invalid expiresAt format (`INVALID_DATE_FORMAT`)
- `400 Bad Request` - expiresAt must be in the future (`INVALID_DATE`)

#### `GET /tenants/{tenantId}/users/{userId}/api-keys`

List all API keys for a user.

**Response (200 OK):**
```json
{
  "apiKeys": [
    {
      "id": "key-123",
      "name": "Production API Key",
      "createdAt": "2025-01-15T10:30:00.000Z",
      "expiresAt": "2026-01-15T10:30:00.000Z",
      "revokedAt": null
    }
  ]
}
```

#### `GET /tenants/{tenantId}/users/{userId}/api-keys/{keyId}`

Get API key details.

**Response (200 OK):**
```json
{
  "id": "key-123",
  "name": "Production API Key",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "expiresAt": "2026-01-15T10:30:00.000Z",
  "revokedAt": null
}
```

#### `DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId}`

Revoke an API key.

**Response (200 OK):**
```json
{
  "id": "key-123",
  "revokedAt": "2025-01-15T12:00:00.000Z"
}
```

### Authentication

#### `POST /auth/login`

Login and create a session.

**Request Body:**
```json
{
  "email": "alice@example.com",
  "password": "secure-password"
}
```

**Response (200 OK):**
```json
{
  "sessionId": "session-123",
  "userId": "user-123",
  "tenants": ["acme-corp"]
}
```

A session cookie is also set in the response.

**Error Response (401 Unauthorized):**
```json
{
  "error": "Invalid credentials",
  "code": "INVALID_CREDENTIALS"
}
```

#### `POST /auth/logout`

Logout and invalidate session.

**Response (200 OK):**
```json
{
  "message": "Logged out"
}
```

#### `POST /auth/password/change`

Change user password (requires authentication).

**Request Body:**
```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed"
}
```

**Error Response (401 Unauthorized):**
```json
{
  "error": "Invalid credentials",
  "code": "INVALID_CREDENTIALS"
}
```

### Permission Management

#### `GET /tenants/{tenantName}/users/{userId}/permissions`

Get permissions for a user.

**Response (200 OK):**
```json
[
  {
    "principalId": "user-123",
    "principalType": "USER",
    "resourceType": "TOPIC",
    "resourceId": "user-events",
    "permissions": ["READ", "WRITE"],
    "grantedAt": "2025-01-15T10:30:00.000Z",
    "grantedBy": "admin-user"
  }
]
```

#### `POST /tenants/{tenantName}/users/{userId}/permissions`

Grant permissions (requires authentication).

**Request Body:**
```json
{
  "principalId": "user-123",
  "principalType": "USER",
  "resourceType": "TOPIC",
  "resourceName": "user-events",
  "namespaceName": "production",
  "topicName": "user-events",
  "permissions": ["READ", "WRITE"],
  "expiresAt": "2026-01-15T10:30:00.000Z"
}
```

**Response (201 Created):**
```json
{
  "message": "Permission granted"
}
```

#### `DELETE /tenants/{tenantName}/users/{userId}/permissions`

Revoke permissions (requires authentication).

**Request Body:**
```json
{
  "principalId": "user-123",
  "principalType": "USER",
  "resourceType": "TOPIC",
  "resourceName": "user-events",
  "namespaceName": "production",
  "topicName": "user-events",
  "permissions": ["READ"],
  "reason": "Access revoked"
}
```

**Response (200 OK):**
```json
{
  "message": "Permission revoked"
}
```

### Health

#### `GET /health`

Health check endpoint.

**Response (200 OK):**
```json
{
  "status": "healthy",
  "consumers": 5,
  "runningDispatchers": ["user-events", "audit-events"]
}
```

## Error Responses

All error responses follow this format:

```json
{
  "error": "Error message",
  "code": "ERROR_CODE"
}
```

## Status Codes

- `200` - Success
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `404` - Not Found
- `500` - Internal Server Error

## Example Usage

### Create Tenant and Namespace

```bash
# Create tenant
curl -X POST http://localhost:8000/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "acme-corp",
    "quota": {
      "maxTopics": 100,
      "maxNamespaces": 10
    }
  }'

# Create namespace
curl -X POST http://localhost:8000/tenants/acme-corp/namespaces \
  -H "Content-Type: application/json" \
  -d '{
    "name": "production",
    "description": "Production namespace"
  }'
```

### Create Topic

```bash
curl -X POST http://localhost:8000/tenants/acme-corp/namespaces/production/topics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-events",
    "schemas": [
      {
        "eventType": "user.created",
        "type": "object",
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
curl -X POST http://localhost:8000/tenants/acme-corp/namespaces/production/events \
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

### Register Consumer

```bash
curl -X POST http://localhost:8000/tenants/acme-corp/namespaces/production/consumers/register \
  -H "Content-Type: application/json" \
  -d '{
    "callback": "http://localhost:3000/webhook",
    "topics": {
      "user-events": null
    }
  }'
```

### Get Events

```bash
# Get all events
curl "http://localhost:8000/tenants/acme-corp/namespaces/production/topics/user-events/events"

# Get events with filters
curl "http://localhost:8000/tenants/acme-corp/namespaces/production/topics/user-events/events?limit=10&sinceEventId=user-events-5"
```

### Authentication

```bash
# Login
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "password"
  }' \
  -c cookies.txt

# Use session cookie for authenticated requests
curl http://localhost:8000/tenants/acme-corp/namespaces/production/topics \
  -b cookies.txt

# Or use API key
curl http://localhost:8000/tenants/acme-corp/namespaces/production/topics \
  -H "Authorization: Bearer es_live_abc123..."
```

## Consumer Webhook Format

When events are delivered to consumers, the webhook receives:

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
        "name": "John Doe"
      }
    }
  ]
}
```

## Consumer Removal

Consumers are automatically removed if:
- The callback returns a non-2xx status code
- The callback times out (30 seconds)
- After 5 failed delivery attempts with exponential backoff

## Event ID Format

Event IDs follow the pattern: `<topic>-<sequence>`

Examples:
- `user-events-1`
- `audit-events-42`
- `notifications-1001`

## Related Documentation

- **[../README.md](../README.md)** - Project overview
- **[../event-store/README.md](../event-store/README.md)** - Backend architecture and code structure
