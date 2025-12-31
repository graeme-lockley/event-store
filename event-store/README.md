# Event Store Backend

Kotlin-based event store backend implementing a multi-tenant, file-backed event storage and delivery system using hexagonal architecture.

## Purpose

The Event Store backend provides:
- **Event storage** - Persistent, file-backed storage of events organized by tenant, namespace, and topic
- **Event delivery** - Asynchronous webhook-based delivery to registered consumers
- **Schema validation** - JSON Schema validation for all published events
- **Multi-tenancy** - Tenant and namespace isolation for data separation
- **REST API** - HTTP endpoints for all operations

## Architecture

The implementation follows **Hexagonal Architecture** (Ports & Adapters pattern), ensuring the domain layer is independent of infrastructure and frameworks.

### Code Structure

```
src/main/kotlin/com/eventstore/
├── Application.kt              # Entry point and Ktor server setup
├── Config.kt                   # Configuration from environment variables
│
├── domain/                     # Domain Layer (Core Business Logic)
│   ├── Event.kt                # Domain entities
│   ├── Topic.kt
│   ├── Consumer.kt
│   ├── Schema.kt
│   ├── Tenant.kt
│   ├── Namespace.kt
│   ├── User.kt
│   ├── ApiKey.kt
│   ├── Permission.kt
│   │
│   ├── services/              # Domain Services (Use Cases)
│   │   ├── topic/
│   │   │   ├── CreateTopicService.kt
│   │   │   ├── GetTopicsService.kt
│   │   │   └── UpdateTopicSchemasService.kt
│   │   ├── event/
│   │   │   ├── PublishEventsService.kt
│   │   │   └── GetEventsService.kt
│   │   ├── consumer/
│   │   │   ├── RegisterConsumerService.kt
│   │   │   └── UnregisterConsumerService.kt
│   │   ├── tenant/
│   │   ├── namespace/
│   │   ├── user/
│   │   ├── apikey/
│   │   ├── auth/
│   │   └── permission/
│   │
│   ├── ports/                  # Ports (Interfaces)
│   │   └── outbound/          # Outbound Ports (What domain needs)
│   │       ├── TopicRepository.kt
│   │       ├── EventRepository.kt
│   │       ├── ConsumerRepository.kt
│   │       ├── SchemaValidator.kt
│   │       └── ConsumerDeliveryService.kt
│   │
│   └── exceptions/             # Domain exceptions
│
├── infrastructure/             # Infrastructure Layer (Adapters)
│   ├── persistence/           # Repository implementations
│   │   ├── FileSystemTopicRepository.kt
│   │   ├── FileSystemEventRepository.kt
│   │   ├── FileSystemApiKeyRepository.kt
│   │   └── InMemoryConsumerRepository.kt
│   │
│   ├── external/              # External service adapters
│   │   └── JsonSchemaValidator.kt
│   │
│   ├── background/            # Background processing
│   │   └── DispatcherManager.kt
│   │
│   ├── projections/          # Read model projections
│   │   ├── TenantProjectionService.kt
│   │   ├── NamespaceProjectionService.kt
│   │   ├── UserProjectionService.kt
│   │   └── PermissionProjectionService.kt
│   │
│   └── auth/                  # Authentication adapters
│       ├── SessionManager.kt
│       └── ApiKeyAuthenticator.kt
│
└── interfaces/                 # Interface Layer (Primary Adapters)
    ├── http/
    │   ├── routes/            # Ktor HTTP routes
    │   │   ├── TopicRoutes.kt
    │   │   ├── EventRoutes.kt
    │   │   ├── ConsumerRoutes.kt
    │   │   ├── TenantRoutes.kt
    │   │   ├── NamespaceRoutes.kt
    │   │   ├── UserRoutes.kt
    │   │   ├── ApiKeyRoutes.kt
    │   │   ├── AuthRoutes.kt
    │   │   ├── PermissionRoutes.kt
    │   │   └── HealthRoutes.kt
    │   │
    │   ├── dto/               # Data Transfer Objects
    │   │   ├── EventRequest.kt
    │   │   ├── TopicRequest.kt
    │   │   └── ...
    │   │
    │   └── middleware/         # HTTP middleware
    │       ├── AuthenticationMiddleware.kt
    │       └── AuthorizationMiddleware.kt
```

### Architecture Principles

1. **Domain Independence**: Domain code has zero dependencies on frameworks or infrastructure
2. **Port-Driven Design**: Domain defines what it needs through ports (interfaces)
3. **Adapter Implementation**: Infrastructure implements ports, can be swapped without changing domain
4. **Testability**: Domain can be tested with mock adapters

## Building and Running

### Prerequisites

- Java 17+
- Gradle 8.5+ (wrapper included)

### Build

```bash
# From repository root
./gradlew :event-store:build

# Or from this directory
cd event-store
../gradlew build
```

### Run

```bash
# Run from Gradle
./gradlew :event-store:run

# Or run the JAR
./gradlew :event-store:jar
java -jar build/libs/event-store-1.0.0.jar
```

### Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8000` | HTTP server port |
| `DATA_DIR` | `./data` | Event storage directory |
| `CONFIG_DIR` | `./config` | Topic/tenant configuration directory |
| `MAX_BODY_BYTES` | `1048576` | Maximum request body size (1MB) |
| `RATE_LIMIT_PER_MINUTE` | `600` | Rate limit per IP per route |
| `MULTI_TENANT_ENABLED` | `false` | Enable multi-tenant mode |
| `AUTH_ENABLED` | `false` | Enable authentication |
| `CREATE_TEST_API_KEY` | `false` | Create test API key on startup |

## API Endpoints

All endpoints are prefixed with tenant and namespace when multi-tenancy is enabled:
`/tenants/{tenantName}/namespaces/{namespaceName}/...`

### Tenant Management

```
POST   /tenants                              # Create tenant
GET    /tenants/{tenantName}                 # Get tenant
PUT    /tenants/{tenantName}                 # Update tenant
DELETE /tenants/{tenantName}                 # Delete tenant
```

### Namespace Management

```
POST   /tenants/{tenantName}/namespaces                    # Create namespace
GET    /tenants/{tenantName}/namespaces/{namespaceName}    # Get namespace
PUT    /tenants/{tenantName}/namespaces/{namespaceName}    # Update namespace
DELETE /tenants/{tenantName}/namespaces/{namespaceName}    # Delete namespace
```

### Topic Management

```
POST   /tenants/{tenantName}/namespaces/{namespaceName}/topics           # Create topic
GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics           # List topics
GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}   # Get topic
PUT    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}   # Update schemas
```

**Create Topic Request:**
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
        "name": { "type": "string" }
      },
      "required": ["id", "name"]
    }
  ]
}
```

### Event Operations

```
POST   /tenants/{tenantName}/namespaces/{namespaceName}/events                    # Publish events
GET    /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topic}/events     # Retrieve events
```

**Publish Events Request:**
```json
[
  {
    "topic": "user-events",
    "type": "user.created",
    "payload": {
      "id": "123",
      "name": "Alice"
    }
  }
]
```

**Query Parameters for GET events:**
- `sinceEventId` - Get events after this ID
- `date` - Get events from this date (YYYY-MM-DD)
- `limit` - Maximum number of events to return

### Consumer Management

```
POST   /tenants/{tenantName}/namespaces/{namespaceName}/consumers/register        # Register consumer
GET    /tenants/{tenantName}/namespaces/{namespaceName}/consumers                 # List consumers
DELETE /tenants/{tenantName}/namespaces/{namespaceName}/consumers/{id}            # Unregister consumer
```

**Register Consumer Request:**
```json
{
  "callback": "https://your-service.com/webhook",
  "topics": {
    "user-events": null
  }
}
```

### User Management

```
POST   /tenants/{tenantId}/users                           # Create user
GET    /tenants/{tenantId}/users                           # List users
GET    /tenants/{tenantId}/users/{userId}                  # Get user
PUT    /tenants/{tenantId}/users/{userId}                  # Update user
DELETE /tenants/{tenantId}/users/{userId}                  # Delete user
POST   /tenants/{tenantId}/users/{userId}/tenants           # Assign user to tenant
DELETE /tenants/{tenantId}/users/{userId}/tenants/{tenantId}  # Remove user from tenant
```

### API Key Management

```
POST   /tenants/{tenantId}/users/{userId}/api-keys         # Create API key
GET    /tenants/{tenantId}/users/{userId}/api-keys         # List API keys
GET    /tenants/{tenantId}/users/{userId}/api-keys/{keyId}  # Get API key
DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId} # Revoke API key
```

### Authentication

```
POST   /auth/login                    # Login (session-based)
POST   /auth/logout                   # Logout
POST   /auth/password/change          # Change password
```

### Permission Management

```
GET    /tenants/{tenantName}/users/{userId}/permissions    # Get permissions
POST   /tenants/{tenantName}/users/{userId}/permissions    # Grant permissions
DELETE /tenants/{tenantName}/users/{userId}/permissions   # Revoke permissions
```

### Health

```
GET    /health                        # Health check
```

**Response:**
```json
{
  "status": "healthy",
  "consumers": 5,
  "runningDispatchers": ["topic1", "topic2"]
}
```

For complete API documentation, see [docs/API.md](../docs/API.md).

## Testing

### Unit Tests

```bash
# Run all unit tests
./gradlew :event-store:test

# Run with coverage
./gradlew :event-store:test jacocoTestReport
```

### Integration Tests

See [event-store-integration/README.md](../event-store-integration/README.md) for integration testing utilities.

## File Storage Structure

Events are stored as individual JSON files organized hierarchically:

```
data/
└── {tenant}/
    └── {namespace}/
        └── {topic}/
            └── YYYY-MM-DD/
                └── {group}/
                    └── {topic}-{sequence}.json
```

Example:
```
data/
└── acme-corp/
    └── production/
        └── user-events/
            └── 2025-01-15/
                └── 0000/
                    ├── user-events-1.json
                    ├── user-events-2.json
                    └── ...
```

## Technology Stack

- **Kotlin** 1.9.22
- **Ktor** 2.3.8 - Web framework
- **Kotlinx Coroutines** 1.7.3 - Asynchronous programming
- **Jackson** 2.15.2 - JSON serialization
- **networknt/json-schema-validator** 1.0.87 - Schema validation
- **SLF4J + Logback** - Logging
- **Gradle** 8.5 - Build system
- **JUnit 5** - Testing

## Key Concepts

### Event IDs

Event IDs follow the pattern: `<topic>-<sequence>`

Examples:
- `user-events-1`
- `audit-events-42`
- `notifications-1001`

### Consumer Webhook Format

When events are delivered to consumers:

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

### Consumer Removal

Consumers are automatically removed if:
- Callback returns non-2xx status code
- Callback times out (30 seconds)
- After 5 failed delivery attempts with exponential backoff

## Related Documentation

- **[../README.md](../README.md)** - Project overview and build instructions
- **[../event-store-integration/README.md](../event-store-integration/README.md)** - Integration testing guide
- **[../docs/API.md](../docs/API.md)** - Complete API reference
- **[../docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)** - Deployment guide
