# Event Store API Reference

## Overview

The Event Store provides a RESTful API for managing tenants, namespaces, topics, publishing events, and registering consumers. The API follows RESTful principles and returns JSON responses.

**Resource Identification**: Most resources are identified by UUIDs (`tenantId`, `namespaceId`, `topicId`), which are globally unique. Some endpoints still use names for backward compatibility (e.g., `GET /tenants/{tenantName}`), but UUID-based endpoints are preferred for create, update, and delete operations.

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

Get tenant details by name.

**Path Parameters:**
- `tenantName` (string): The tenant name

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

**Note**: The `id` field contains the tenant name, not the `tenantId` UUID. For PUT/DELETE operations and namespace creation, you need the `tenantId` UUID, which must be tracked separately or obtained from the system during tenant creation.

**Error Response (404 Not Found):**
```json
{
  "error": "Tenant not found",
  "code": "TENANT_NOT_FOUND"
}
```

#### `PUT /tenants/{tenantId}`

Update tenant.

**Path Parameters:**
- `tenantId` (UUID): The tenant UUID

**Request Body:**
```json
{
  "name": "acme-corp-updated",
  "quota": { ... },
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Invalid tenantId format (`INVALID_REQUEST`)
- `404 Not Found` - Tenant not found (`TENANT_NOT_FOUND`)

#### `DELETE /tenants/{tenantId}`

Delete tenant (soft delete).

**Path Parameters:**
- `tenantId` (UUID): The tenant UUID

**Request Body (optional):**
```json
{
  "reason": "Account closed"
}
```

### Namespace Management

#### `POST /namespaces`

Create a namespace within a tenant.

**Request Body:**
```json
{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "name": "production",
  "description": "Production namespace",
  "metadata": {
    "environment": "prod"
  }
}
```

**Note**: `tenantId` must be a valid UUID. Namespaces are globally identified by `namespaceId` (UUID), not by tenant/namespace names.

**Response (201 Created):**
```json
{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "id": "223e4567-e89b-12d3-a456-426614174001",
  "name": "production",
  "description": "Production namespace",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Tenant not found (`TENANT_NOT_FOUND`)
- `400 Bad Request` - Invalid tenantId format (`INVALID_REQUEST`)
- `400 Bad Request` - Namespace already exists (`NAMESPACE_EXISTS`)
- `400 Bad Request` - Quota exceeded (`QUOTA_EXCEEDED`)

#### `GET /namespaces`

List all namespaces. Optionally filter by tenant.

**Query Parameters:**
- `tenantId` (optional, UUID): Filter namespaces by tenant UUID

**Response (200 OK):**
```json
{
  "namespaces": [
    {
      "tenantId": "123e4567-e89b-12d3-a456-426614174000",
      "id": "223e4567-e89b-12d3-a456-426614174001",
      "name": "production",
      ...
    }
  ]
}
```

#### `GET /namespaces/{namespaceId}`

Get namespace details.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID

**Response (200 OK):**
```json
{
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "id": "223e4567-e89b-12d3-a456-426614174001",
  "name": "production",
  "description": "Production namespace",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T11:00:00.000Z",
  "deletedAt": null,
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Invalid namespaceId format (`INVALID_REQUEST`)
- `404 Not Found` - Namespace not found (`NAMESPACE_NOT_FOUND`)

#### `PUT /namespaces/{namespaceId}`

Update namespace.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID

**Request Body:**
```json
{
  "name": "production-updated",
  "description": "Updated description",
  "metadata": { ... }
}
```

**Error Responses:**
- `400 Bad Request` - Invalid namespaceId format (`INVALID_REQUEST`)
- `404 Not Found` - Namespace not found (`NAMESPACE_NOT_FOUND`)

#### `DELETE /namespaces/{namespaceId}`

Delete namespace.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID

**Request Body (optional):**
```json
{
  "reason": "Namespace deprecated"
}
```

### Topic Management

#### `POST /topics`

Create a new topic with schemas.

**Request Body:**
```json
{
  "namespaceId": "223e4567-e89b-12d3-a456-426614174001",
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

**Note**: `namespaceId` must be a valid UUID. Topics are globally identified by `topicId` (UUID), not by tenant/namespace/topic names.

**Response (201 Created):**
```json
{
  "message": "Topic 'user-events' created",
  "topicId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request body (`INVALID_REQUEST`)
- `400 Bad Request` - Invalid namespaceId format (`INVALID_NAMESPACE_ID`)
- `400 Bad Request` - Topic already exists (`TOPIC_CREATION_FAILED`)

#### `GET /topics`

List all topics. Optionally filter by namespace.

**Query Parameters:**
- `namespaceId` (optional, UUID): Filter topics by namespace UUID

**Response (200 OK):**
```json
{
  "topics": [
    {
      "topicId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "namespaceId": "223e4567-e89b-12d3-a456-426614174001",
      "name": "user-events",
      "sequence": 42,
      "schemas": [ ... ]
    }
  ]
}
```

#### `GET /topics/{topicId}`

Get topic details.

**Path Parameters:**
- `topicId` (UUID): The topic UUID

**Response (200 OK):**
```json
{
  "topicId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "namespaceId": "223e4567-e89b-12d3-a456-426614174001",
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

**Error Responses:**
- `400 Bad Request` - Invalid topicId format (`INVALID_TOPIC_ID`)
- `404 Not Found` - Topic not found (`TOPIC_NOT_FOUND`)

#### `PUT /topics/{topicId}/schemas`

Update schemas for an existing topic. Schema updates are **additive only** - you can add new schemas or update existing ones (by `eventType`), but you cannot remove schemas.

**Path Parameters:**
- `topicId` (UUID): The topic UUID

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
  "message": "Topic schemas updated successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request body (`INVALID_REQUEST`)
- `400 Bad Request` - Invalid topicId format (`INVALID_TOPIC_ID`)
- `400 Bad Request` - Cannot remove schemas (`SCHEMA_REMOVAL_NOT_ALLOWED`)
- `404 Not Found` - Topic not found (`TOPIC_NOT_FOUND`)

**Important Notes:**
- All existing `eventType`s must be present in the update request (additive constraint)
- New schemas can be added alongside existing ones
- Existing schemas are updated by matching `eventType`
- Schema updates are immediately effective for new events
- The topic's sequence number is preserved during schema updates

### Event Operations

#### `POST /topics/{topicId}/events`

Publish one or more events to a topic.

**Path Parameters:**
- `topicId` (UUID): The topic UUID

**Request Body:**
```json
[
  {
    "type": "user.created",
    "payload": {
      "id": "123",
      "name": "Alice Johnson",
      "email": "alice@example.com"
    }
  },
  {
    "type": "user.updated",
    "payload": {
      "id": "123",
      "name": "Alice Smith"
    }
  }
]
```

**Note**: Each event in the array must specify `type` (event type) and `payload` (event data). The `topicId` is taken from the path parameter, so all events in a batch are published to the same topic.

**Response (201 Created):**
```json
{
  "eventIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7-1", "7c9e6679-7425-40de-944b-e07fc1f90ae7-2"]
}
```

**Error Responses:**
- `400 Bad Request` - Request body must be a non-empty array (`INVALID_REQUEST`)
- `400 Bad Request` - Invalid topicId format (`INVALID_TOPIC_ID`)
- `400 Bad Request` - Each event must have type and payload (`INVALID_EVENT`)
- `400 Bad Request` - Topic not found (`EVENT_PUBLISH_FAILED`)
- `400 Bad Request` - Schema validation failed (`EVENT_PUBLISH_FAILED`)

**Response (201 Created):**
```json
{
  "eventIds": [
    "7c9e6679-7425-40de-944b-e07fc1f90ae7/1",
    "7c9e6679-7425-40de-944b-e07fc1f90ae7/2"
  ]
}
```

#### `GET /topics/{topicId}/events`

Retrieve events from a topic.

**Path Parameters:**
- `topicId` (UUID): The topic UUID

**Query Parameters:**
- `sinceEventId` (optional): Get events after this event ID
- `date` (optional): Get events from a specific date (YYYY-MM-DD format)
- `limit` (optional): Number of events to return (default: 100)

**Response (200 OK):**
```json
{
  "events": [
    {
      "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7/1",
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

**Note**: The `id` field uses the format `<topicId>/<sequence>` where `topicId` is a UUID.

**Error Responses:**
- `404 Not Found` - Topic not found (`TOPIC_NOT_FOUND`)
- `500 Internal Server Error` - Events fetch failed (`EVENTS_FETCH_FAILED`)

### Consumer Management

#### `POST /namespaces/{namespaceId}/consumers/register`

Register a new consumer.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID (for context; consumers register by topicId UUIDs)

**Request Body:**
```json
{
  "callback": "https://your-service.com/webhook",
  "topics": {
    "7c9e6679-7425-40de-944b-e07fc1f90ae7": null
  }
}
```

**Note**: The `topics` object maps **topic UUIDs** (not topic names) to the last event ID the consumer has processed. Use `null` to start from the beginning. Since `namespaceId` is globally unique, it's the only path parameter needed (no `tenantId` required).

**Response (201 Created):**
```json
{
  "consumerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid namespaceId format (`INVALID_NAMESPACE_ID`)
- `400 Bad Request` - Invalid request (`INVALID_REQUEST`)
- `400 Bad Request` - Topic not found (`TOPIC_NOT_FOUND`)
- `400 Bad Request` - Invalid registration (`CONSUMER_REGISTRATION_FAILED`)

#### `GET /namespaces/{namespaceId}/consumers`

List all consumers.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID (for context)

**Response (200 OK):**
```json
{
  "consumers": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "callback": "https://your-service.com/webhook",
      "topics": {
        "7c9e6679-7425-40de-944b-e07fc1f90ae7": "7c9e6679-7425-40de-944b-e07fc1f90ae7-42"
      }
    }
  ]
}
```

**Note**: The `topics` object uses topic UUIDs as keys. The `namespaceId` parameter is validated but not used for filtering (consumers are identified by topicId UUIDs).

#### `DELETE /namespaces/{namespaceId}/consumers/{id}`

Unregister a consumer.

**Path Parameters:**
- `namespaceId` (UUID): The namespace UUID (for context)
- `id` (string): The consumer ID

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

#### `POST /users`

Create a user.

**Request Body:**
```json
{
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "password": "secure-password",
  "primaryTenantId": "123e4567-e89b-12d3-a456-426614174000",
  "metadata": {
    "department": "Engineering"
  }
}
```

**Note**: Users are managed globally, not scoped to tenants. The `primaryTenantId` is optional and must be a valid UUID if provided.

**Response (201 Created):**
```json
{
  "id": "user-123",
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "primaryTenantId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "ACTIVE",
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

#### `GET /users`

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

#### `GET /users/{userId}`

Get user details.

**Path Parameters:**
- `userId` (string): The user ID

**Response (200 OK):**
```json
{
  "id": "user-123",
  "email": "alice@example.com",
  "name": "Alice Johnson",
  "primaryTenantId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "ACTIVE",
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T11:00:00.000Z",
  "metadata": { ... }
}
```

#### `PUT /users/{userId}`

Update user.

**Path Parameters:**
- `userId` (string): The user ID

**Request Body:**
```json
{
  "email": "alice.new@example.com",
  "name": "Alice Smith",
  "metadata": { ... }
}
```

#### `DELETE /users/{userId}`

Delete user.

**Path Parameters:**
- `userId` (string): The user ID

**Response (200 OK):**
```json
{
  "message": "User 'user-123' deleted"
}
```

#### `POST /users/{userId}/tenants/{tenantId}`

Assign user to tenant.

**Path Parameters:**
- `userId` (string): The user ID
- `tenantId` (UUID): The tenant UUID

**Request Body (optional):**
```json
{
  "role": "member",
  "isPrimary": false
}
```

#### `DELETE /users/{userId}/tenants/{tenantId}`

Remove user from tenant.

**Path Parameters:**
- `userId` (string): The user ID
- `tenantId` (UUID): The tenant UUID

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

#### `GET /tenants/{tenantId}/users/{userId}/permissions`

Get permissions for a user.

**Path Parameters:**
- `tenantId` (UUID): The tenant UUID
- `userId` (string): The user ID

**Response (200 OK):**
```json
[
  {
    "principalId": "user-123",
    "principalType": "USER",
    "resourceType": "TOPIC",
    "resourceId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "permissions": ["READ", "WRITE"],
    "grantedAt": "2025-01-15T10:30:00.000Z",
    "grantedBy": "admin-user"
  }
]
```

**Error Responses:**
- `400 Bad Request` - Invalid tenantId format (`INVALID_TENANT_ID`)

#### `POST /tenants/{tenantId}/users/{userId}/permissions`

Grant permissions (requires authentication).

**Path Parameters:**
- `tenantId` (UUID): The tenant UUID
- `userId` (string): The user ID

**Request Body:**
```json
{
  "principalId": "user-123",
  "principalType": "USER",
  "resourceType": "TOPIC",
  "resourceName": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "namespaceName": "production",
  "topicName": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "permissions": ["READ", "WRITE"],
  "expiresAt": "2026-01-15T10:30:00.000Z"
}
```

**Note**: `resourceName` and `topicName` should be UUIDs for topic-level permissions. `namespaceName` is still a name for backward compatibility.

**Response (201 Created):**
```json
{
  "message": "Permission granted"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid tenantId format (`INVALID_TENANT_ID`)

#### `DELETE /tenants/{tenantId}/users/{userId}/permissions`

Revoke permissions (requires authentication).

**Path Parameters:**
- `tenantId` (UUID): The tenant UUID
- `userId` (string): The user ID

**Request Body:**
```json
{
  "principalId": "user-123",
  "principalType": "USER",
  "resourceType": "TOPIC",
  "resourceName": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "namespaceName": "production",
  "topicName": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
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

**Error Responses:**
- `400 Bad Request` - Invalid tenantId format (`INVALID_TENANT_ID`)

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

# Note: Tenant responses use 'id' which is the name, not the tenantId UUID
# For PUT/DELETE operations, you need the tenantId UUID
# You may need to track tenantId separately or store it in metadata
# For this example, assume you have the tenantId UUID from creation
TENANT_ID="123e4567-e89b-12d3-a456-426614174000"  # UUID from tenant creation

# Create namespace (requires tenantId UUID in body)
curl -X POST http://localhost:8000/namespaces \
  -H "Content-Type: application/json" \
  -d "{
    \"tenantId\": \"$TENANT_ID\",
    \"name\": \"production\",
    \"description\": \"Production namespace\"
  }"

# Get namespace to retrieve namespaceId (UUID)
# Response will include namespaceId in the response
NAMESPACE_ID=$(curl -s "http://localhost:8000/namespaces?tenantId=$TENANT_ID" | jq -r '.namespaces[0].id')
```

### Create Topic

```bash
# Create topic (requires namespaceId UUID in body)
curl -X POST http://localhost:8000/topics \
  -H "Content-Type: application/json" \
  -d "{
    \"namespaceId\": \"$NAMESPACE_ID\",
    \"name\": \"user-events\",
    \"schemas\": [
      {
        \"eventType\": \"user.created\",
        \"type\": \"object\",
        \"\$schema\": \"https://json-schema.org/draft/2020-12/schema\",
        \"properties\": {
          \"id\": {\"type\": \"string\"},
          \"name\": {\"type\": \"string\"}
        },
        \"required\": [\"id\", \"name\"]
      }
    ]
  }"

# Get topic to retrieve topicId (UUID)
TOPIC_ID=$(curl -s "http://localhost:8000/topics?namespaceId=$NAMESPACE_ID" | jq -r '.topics[0].topicId')
```

### Publish Events

```bash
# Publish events (topicId is in path, not in event body)
curl -X POST "http://localhost:8000/topics/$TOPIC_ID/events" \
  -H "Content-Type: application/json" \
  -d "[
    {
      \"type\": \"user.created\",
      \"payload\": {
        \"id\": \"123\",
        \"name\": \"John Doe\"
      }
    }
  ]"
```

### Register Consumer

```bash
# Register consumer (topics map uses topic UUIDs as keys)
curl -X POST "http://localhost:8000/namespaces/$NAMESPACE_ID/consumers/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"callback\": \"http://localhost:3000/webhook\",
    \"topics\": {
      \"$TOPIC_ID\": null
    }
  }"
```

### Get Events

```bash
# Get all events (uses topicId UUID in path)
curl "http://localhost:8000/topics/$TOPIC_ID/events"

# Get events with filters (eventId format: topicId/sequence)
curl "http://localhost:8000/topics/$TOPIC_ID/events?limit=10&sinceEventId=$TOPIC_ID/5"
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
curl "http://localhost:8000/topics?namespaceId=$NAMESPACE_ID" \
  -b cookies.txt

# Or use API key
curl "http://localhost:8000/topics?namespaceId=$NAMESPACE_ID" \
  -H "Authorization: Bearer es_live_abc123..."
```

## Consumer Webhook Format

When events are delivered to consumers, the webhook receives:

```json
{
  "consumerId": "550e8400-e29b-41d4-a716-446655440000",
  "events": [
    {
      "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7/1",
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

**Note**: The `id` field uses the format `<topicId>/<sequence>` where `topicId` is a UUID.

## Consumer Removal

Consumers are automatically removed if:
- The callback returns a non-2xx status code
- The callback times out (30 seconds)
- After 5 failed delivery attempts with exponential backoff

## Event ID Format

Event IDs follow the pattern: `<topicId>/<sequence>`

Examples:
- `7c9e6679-7425-40de-944b-e07fc1f90ae7/1`
- `8d0f7780-8536-51ef-a55c-f18gd2g01bf8/42`
- `9e1g8891-9647-62fg-b66d-g29he3h12cg9/1001`

Note: Event IDs use the topic UUID separated by a forward slash, not the topic name. The format is `{topicId}/{sequence}`.

## Related Documentation

- **[../README.md](../README.md)** - Project overview
- **[../event-store/README.md](../event-store/README.md)** - Backend architecture and code structure
