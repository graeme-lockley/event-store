# Tenant & Namespace Requirements Document

## Document Information

**Version**: 1.0  
**Date**: 2025-01-15  
**Status**: Draft  
**Author**: Architecture Review

## Executive Summary

This document specifies the requirements for implementing tenant and namespace management in the Event Store system. The implementation uses an event-sourced approach where tenant and namespace lifecycle is managed through events stored in a special `$system` tenant and `$management` namespace.

## Architecture Decisions

### Decision 1: Event-Sourced Management
**Decision**: Tenant and namespace management will be implemented using event streams stored in `$system/$management` namespace.

**Rationale**:
- Consistent with event store architecture
- Provides complete audit trail
- Enables time-travel queries
- Unified security model (permissions also event-sourced)

**Implementation**: All tenant and namespace lifecycle operations publish events to `$system/$management/tenants` and `$system/$management/namespaces` topics.

---

### Decision 2: URL Path Structure
**Decision**: Tenant and namespace identifiers are included in URL paths for all operations.

**Rationale**:
- Explicit tenant/namespace context in every request
- Clear resource hierarchy
- Easy to understand and debug
- RESTful resource organization

**URL Pattern**: `/tenants/{tenantId}/namespaces/{namespaceId}/...`

---

### Decision 3: System-Wide Users
**Decision**: Users exist at system level but are associated with tenants. User management operations are scoped to tenants.

**Rationale**:
- Global user identity (unique emails)
- Strong tenant isolation through permissions
- Flexible for future cross-tenant needs
- Clear management boundaries

**Implementation**: Users are created system-wide, but all user management operations require tenant context.

---

## Domain Model

### Tenant

```kotlin
data class Tenant(
    val id: String,                    // Unique tenant identifier
    val name: String,                  // Display name
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,   // Soft delete
    val quota: Quota? = null,         // Resource limits
    val metadata: Map<String, Any> = emptyMap()
) {
    val isActive: Boolean
        get() = deletedAt == null
}

data class Quota(
    val maxTopics: Int = 100,
    val maxNamespaces: Int = 50,
    val maxEventsPerDay: Long = 1_000_000,
    val maxConsumers: Int = 100,
    val maxUsers: Int = 50,
    val maxEventSizeBytes: Long = 1024 * 1024  // 1MB
)
```

### Namespace

```kotlin
data class Namespace(
    val tenantId: String,              // Parent tenant
    val id: String,                    // Unique within tenant
    val name: String,                  // Display name
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,   // Soft delete
    val metadata: Map<String, Any> = emptyMap()
) {
    val isActive: Boolean
        get() = deletedAt == null
    
    fun qualifiedName(): String = "$tenantId/$id"
}
```

### Topic (Updated)

```kotlin
data class Topic(
    val tenantId: String,              // Parent tenant
    val namespaceId: String,          // Parent namespace
    val name: String,                  // Unique within namespace
    val sequence: Long,
    val schemas: List<Schema>,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
) {
    fun qualifiedName(): String = "$tenantId/$namespaceId/$name"
    
    fun nextSequence(): Long = sequence + 1
    
    fun updateSequence(newSequence: Long): Topic {
        return copy(sequence = newSequence)
    }
    
    fun updateSchemas(newSchemas: List<Schema>): Topic {
        return copy(schemas = newSchemas, updatedAt = Instant.now())
    }
}
```

### Consumer (Updated)

```kotlin
data class Consumer(
    val tenantId: String,              // Parent tenant
    val namespaceId: String,          // Parent namespace
    val id: String,                    // Unique identifier
    val callback: URL,
    val topics: Map<String, String?>,  // topic name -> lastEventId
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
) {
    init {
        require(tenantId.isNotBlank()) { "Tenant ID is required" }
        require(namespaceId.isNotBlank()) { "Namespace ID is required" }
        require(id.isNotBlank()) { "Consumer ID is required" }
        require(topics.isNotEmpty()) { "Consumer must subscribe to at least one topic" }
    }
    
    fun updateLastEventId(topic: String, eventId: String): Consumer {
        return copy(
            topics = topics + (topic to eventId),
            updatedAt = Instant.now()
        )
    }
}
```

### User

```kotlin
data class User(
    val id: String,                    // Global unique ID
    val email: String,                 // Globally unique
    val name: String,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val emailVerified: Boolean = false,
    val primaryTenantId: String? = null,  // Default tenant
    val metadata: Map<String, Any> = emptyMap()
)

enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
    PENDING_ACTIVATION
}

data class UserTenantAssociation(
    val userId: String,                // References system user
    val tenantId: String,              // Tenant context
    val role: String? = null,          // Default role in tenant
    val assignedAt: Instant,
    val assignedBy: String,            // Who assigned (user ID)
    val isPrimary: Boolean = false
)
```

### EventId (Updated)

```kotlin
data class EventId(val value: String) {
    init {
        require(value.matches(EVENT_ID_PATTERN)) {
            "Event ID must be in format '<tenant>/<namespace>/<topic>-<sequence>'"
        }
    }
    
    val tenantId: String
        get() = value.split("/")[0]
    
    val namespaceId: String
        get() = value.split("/")[1]
    
    val topic: String
        get() = value.split("/")[2].substringBeforeLast("-")
    
    val sequence: Long
        get() = value.split("/")[2].substringAfterLast("-").toLong()
    
    override fun toString(): String = value
    
    companion object {
        private val EVENT_ID_PATTERN = Regex("^[^/]+/[^/]+/.+-[0-9]+$")
        
        fun create(tenantId: String, namespaceId: String, topic: String, sequence: Long): EventId {
            return EventId("$tenantId/$namespaceId/$topic-$sequence")
        }
    }
}
```

---

## Event Schema Definitions

### System Namespace

All management events are stored in:
- **Tenant**: `$system` (reserved system tenant)
- **Namespace**: `$management` (reserved management namespace)

### Tenant Events

**Topic**: `tenants` (in `$system/$management`)

#### tenant.created
```json
{
  "tenantId": "acme-corp",
  "name": "Acme Corporation",
  "quota": {
    "maxTopics": 100,
    "maxNamespaces": 50,
    "maxEventsPerDay": 1000000,
    "maxConsumers": 100,
    "maxUsers": 50
  },
  "createdBy": "system",
  "createdAt": "2025-01-15T10:00:00Z",
  "metadata": {}
}
```

#### tenant.updated
```json
{
  "tenantId": "acme-corp",
  "name": "Acme Corporation Inc.",
  "quota": {
    "maxTopics": 200,
    "maxNamespaces": 100,
    "maxEventsPerDay": 2000000
  },
  "updatedBy": "admin-user-1",
  "updatedAt": "2025-01-20T14:00:00Z"
}
```

#### tenant.deleted
```json
{
  "tenantId": "acme-corp",
  "deletedBy": "admin-user-1",
  "deletedAt": "2025-01-25T09:00:00Z",
  "reason": "Account closure"
}
```

### Namespace Events

**Topic**: `namespaces` (in `$system/$management`)

#### namespace.created
```json
{
  "tenantId": "acme-corp",
  "namespaceId": "billing-app",
  "name": "Billing Application",
  "description": "Handles all billing-related events",
  "createdBy": "admin-user-1",
  "createdAt": "2025-01-15T10:05:00Z"
}
```

#### namespace.updated
```json
{
  "tenantId": "acme-corp",
  "namespaceId": "billing-app",
  "name": "Billing Application",
  "description": "Handles all billing and payment events",
  "updatedBy": "admin-user-1",
  "updatedAt": "2025-01-20T14:05:00Z"
}
```

#### namespace.deleted
```json
{
  "tenantId": "acme-corp",
  "namespaceId": "billing-app",
  "deletedBy": "admin-user-1",
  "deletedAt": "2025-01-25T10:00:00Z",
  "reason": "Application deprecated"
}
```

### User Events

**Topic**: `users` (in `$system/$management`)

#### user.created
```json
{
  "userId": "user-123",
  "email": "john.doe@acme.com",
  "name": "John Doe",
  "createdBy": "system",
  "createdAt": "2025-01-15T10:00:00Z",
  "status": "active"
}
```

#### user.tenant.assigned
```json
{
  "userId": "user-123",
  "tenantId": "acme-corp",
  "role": "developer",
  "assignedBy": "admin-user-1",
  "assignedAt": "2025-01-15T10:05:00Z",
  "isPrimary": true
}
```

#### user.tenant.removed
```json
{
  "userId": "user-123",
  "tenantId": "acme-corp",
  "removedBy": "admin-user-1",
  "removedAt": "2025-01-25T10:00:00Z",
  "reason": "User left organization"
}
```

### Permission Events

**Topic**: `permissions` (in `$system/$management`)

#### permission.granted
```json
{
  "principalId": "user-123",
  "principalType": "user",
  "resourceType": "topic",
  "resourceId": "invoices",
  "tenantId": "acme-corp",
  "namespaceId": "billing-app",
  "topicName": "invoices",
  "permissions": ["event:read", "event:write"],
  "constraints": {
    "eventTypes": ["invoice.created"],
    "maxAgeDays": 30
  },
  "grantedBy": "admin-user-1",
  "grantedAt": "2025-01-15T10:10:00Z",
  "expiresAt": "2025-12-31T23:59:59Z"
}
```

#### permission.revoked
```json
{
  "principalId": "user-123",
  "principalType": "user",
  "resourceType": "topic",
  "resourceId": "invoices",
  "tenantId": "acme-corp",
  "namespaceId": "billing-app",
  "topicName": "invoices",
  "permissions": ["event:write"],
  "revokedBy": "admin-user-1",
  "revokedAt": "2025-01-20T14:00:00Z",
  "reason": "API key rotation"
}
```

---

## API Design

### URL Structure

All APIs follow the pattern:
```
/tenants/{tenantId}/namespaces/{namespaceId}/...
```

### Tenant Management

```
POST   /tenants                              # Create tenant (system admin)
GET    /tenants                               # List all tenants (system admin)
GET    /tenants/{tenantId}                    # Get tenant details
PUT    /tenants/{tenantId}                    # Update tenant
DELETE /tenants/{tenantId}                    # Delete tenant (soft delete)
```

### Namespace Management

```
POST   /tenants/{tenantId}/namespaces         # Create namespace
GET    /tenants/{tenantId}/namespaces          # List namespaces in tenant
GET    /tenants/{tenantId}/namespaces/{namespaceId}  # Get namespace details
PUT    /tenants/{tenantId}/namespaces/{namespaceId}   # Update namespace
DELETE /tenants/{tenantId}/namespaces/{namespaceId}   # Delete namespace
```

### Topic Management

```
POST   /tenants/{tenantId}/namespaces/{namespaceId}/topics
GET    /tenants/{tenantId}/namespaces/{namespaceId}/topics
GET    /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}
PUT    /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}
DELETE /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}
```

### Event Operations

```
POST   /tenants/{tenantId}/namespaces/{namespaceId}/events
GET    /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}/events
```

### Consumer Management

```
POST   /tenants/{tenantId}/namespaces/{namespaceId}/consumers/register
GET    /tenants/{tenantId}/namespaces/{namespaceId}/consumers
GET    /tenants/{tenantId}/namespaces/{namespaceId}/consumers/{consumerId}
PUT    /tenants/{tenantId}/namespaces/{namespaceId}/consumers/{consumerId}
DELETE /tenants/{tenantId}/namespaces/{namespaceId}/consumers/{consumerId}
```

### User Management (Tenant-Scoped)

```
POST   /tenants/{tenantId}/users               # Create user or assign existing
GET    /tenants/{tenantId}/users               # List users in tenant
GET    /tenants/{tenantId}/users/{userId}      # Get user in tenant context
PUT    /tenants/{tenantId}/users/{userId}      # Update user
DELETE /tenants/{tenantId}/users/{userId}      # Remove user from tenant
POST   /tenants/{tenantId}/users/{userId}/roles/{roleId}      # Assign role
DELETE /tenants/{tenantId}/users/{userId}/roles/{roleId}      # Remove role
```

### Authentication (System-Wide)

```
POST   /auth/login                             # Login (returns available tenants)
POST   /auth/logout                            # Logout
POST   /auth/password/change                   # Change own password
POST   /auth/password/reset                    # Reset password (admin)
GET    /auth/tenants                           # List tenants user can access
POST   /auth/switch-tenant/{tenantId}          # Switch active tenant context
```

### Permission Management

```
GET    /tenants/{tenantId}/users/{userId}/permissions  # Get effective permissions
POST   /tenants/{tenantId}/users/{userId}/permissions  # Grant permissions
DELETE /tenants/{tenantId}/users/{userId}/permissions  # Revoke permissions
```

---

## Permission Model

### Permission Hierarchy

Permissions follow a hierarchical structure with inheritance:

```
tenant:admin
  └── Inherits: All namespace:* and topic:* permissions

namespace:admin
  └── Inherits: All topic:* permissions within namespace

topic:admin
  └── Inherits: event:read, event:write, event:replay
```

### Permission Categories

#### Tenant Permissions
- `tenant:create`, `tenant:read`, `tenant:list`, `tenant:update`, `tenant:delete`
- `tenant:admin`, `tenant:permission:grant`, `tenant:permission:revoke`
- `tenant:quota:manage`, `tenant:export`, `tenant:audit:read`

#### Namespace Permissions
- `namespace:create`, `namespace:read`, `namespace:list`, `namespace:update`, `namespace:delete`
- `namespace:admin`, `namespace:permission:grant`, `namespace:permission:revoke`

#### Topic Permissions
- `topic:create`, `topic:read`, `topic:list`, `topic:update`, `topic:delete`
- `topic:admin`, `topic:permission:grant`, `topic:schema:manage`

#### Event Permissions
- `event:read`, `event:read:history`, `event:read:export`
- `event:write`, `event:write:admin`
- `event:delete`, `event:replay`, `event:purge`

#### Consumer Permissions
- `consumer:create`, `consumer:read`, `consumer:list`, `consumer:update`, `consumer:delete`
- `consumer:admin`, `consumer:manage`

#### User Permissions
- `user:create`, `user:read`, `user:list`, `user:update`, `user:delete`
- `user:admin`, `user:activate`, `user:suspend`
- `user:password:reset`, `user:permission:grant`, `user:permission:revoke`

---

## Storage Structure

### File System Layout

```
data/
├── $system/
│   └── $management/
│       ├── tenants/
│       │   └── 2025-01-15/
│       ├── namespaces/
│       │   └── 2025-01-15/
│       ├── users/
│       │   └── 2025-01-15/
│       └── permissions/
│           └── 2025-01-15/
│
├── acme-corp/
│   ├── billing-app/
│   │   ├── invoices/
│   │   │   └── 2025-01-15/
│   │   └── payments/
│   │       └── 2025-01-15/
│   └── user-service/
│       └── user-events/
│           └── 2025-01-15/
│
└── startup-xyz/
    └── main-app/
        └── user-events/
            └── 2025-01-15/

config/
├── tenants/
│   ├── $system.json
│   ├── acme-corp.json
│   └── startup-xyz.json
│
├── acme-corp/
│   ├── billing-app/
│   │   ├── invoices.json
│   │   └── payments.json
│   └── user-service/
│       └── user-events.json
│
└── startup-xyz/
    └── main-app/
        └── user-events.json
```

---

## Bootstrap Process

### System Initialization

On application startup:

1. **Check if `$system` tenant exists**
   - Read events from `$system/$management/tenants`
   - If no events exist, bootstrap required

2. **Bootstrap `$system` tenant** (if needed)
   - Create `$system` tenant via special bootstrap service
   - Bypass normal validation (chicken-and-egg problem)
   - Publish `tenant.created` event

3. **Bootstrap `$management` namespace** (if needed)
   - Create `$management` namespace in `$system` tenant
   - Publish `namespace.created` event

4. **Bootstrap system topics** (if needed)
   - Create `tenants` topic in `$system/$management`
   - Create `namespaces` topic in `$system/$management`
   - Create `users` topic in `$system/$management`
   - Create `permissions` topic in `$system/$management`
   - Create `api-keys` topic in `$system/$management`

5. **Load projections**
   - Build tenant projection from events
   - Build namespace projection from events
   - Build user projection from events
   - Build permission projection from events (with caching)

---

## Projection Services

### Tenant Projection Service

Rebuilds tenant state from events:

```kotlin
class TenantProjectionService(
    private val eventRepository: EventRepository
) {
    suspend fun getTenant(tenantId: String): Tenant? {
        val events = eventRepository.getEvents(
            tenantId = "$system",
            namespaceId = "$management",
            topic = "tenants",
            sinceEventId = null
        )
        
        // Filter events for this tenant
        val tenantEvents = events
            .filter { it.type.startsWith("tenant.") }
            .filter { it.payload["tenantId"] == tenantId }
            .sortedBy { it.timestamp }
        
        // Rebuild tenant from events
        return rebuildTenantFromEvents(tenantEvents)
    }
    
    suspend fun getAllTenants(): List<Tenant> {
        // Similar approach, group by tenantId
    }
    
    suspend fun tenantExists(tenantId: String): Boolean {
        return getTenant(tenantId)?.isActive == true
    }
}
```

### Permission Projection Service

Rebuilds permissions with caching:

```kotlin
class PermissionProjectionService(
    private val eventRepository: EventRepository
) {
    private val permissionCache = ConcurrentHashMap<String, Set<String>>()
    
    suspend fun getPermissions(
        principalId: String,
        tenantId: String,
        namespaceId: String? = null,
        topicName: String? = null
    ): Set<String> {
        val cacheKey = buildCacheKey(principalId, tenantId, namespaceId, topicName)
        
        // Check cache first
        permissionCache[cacheKey]?.let { return it }
        
        // Rebuild from events
        val permissions = rebuildPermissionsFromEvents(
            principalId, tenantId, namespaceId, topicName
        )
        
        permissionCache[cacheKey] = permissions
        return permissions
    }
    
    suspend fun onPermissionEvent(event: Event) {
        // Invalidate affected cache entries
        invalidateCacheForEvent(event)
        // Update cache immediately
        updateCacheFromEvent(event)
    }
}
```

---

## Security Considerations

### Tenant Isolation

- All operations require explicit tenant context in URL
- Permissions are always tenant-scoped
- Users can belong to multiple tenants but permissions are per-tenant
- No cross-tenant data access without explicit permission

### Authentication Flow

1. User authenticates (system-wide)
2. System returns available tenants
3. User selects tenant context
4. All subsequent operations are scoped to that tenant
5. Authorization checks use tenant context

### Authorization Middleware

```kotlin
intercept(ApplicationCallPipeline.Call) {
    // 1. Extract tenant/namespace from URL path
    val tenantId = extractTenantFromPath(call.request.path())
    val namespaceId = extractNamespaceFromPath(call.request.path())
    val topicName = extractTopicFromPath(call.request.path())
    
    // 2. Authenticate (API key or session)
    val principal = authenticate(call)
    
    // 3. Check permissions
    val requiredPermission = getRequiredPermission(call.request.httpMethod)
    if (!authorizationService.checkPermission(
        principalId = principal.id,
        tenantId = tenantId,
        namespaceId = namespaceId,
        topicName = topicName,
        requiredPermission = requiredPermission
    )) {
        call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
        return@intercept
    }
    
    // 4. Store context in call attributes
    call.attributes.put(tenantIdKey, tenantId)
    call.attributes.put(namespaceIdKey, namespaceId)
    call.attributes.put(principalKey, principal)
}
```

---

## Migration Strategy

### Existing Data Migration

For existing single-tenant data:

1. **Create default tenant**
   - Create tenant: `default` or `library`
   - Publish `tenant.created` event

2. **Create default namespace**
   - Create namespace: `default` in default tenant
   - Publish `namespace.created` event

3. **Migrate topics**
   - For each existing topic:
     - Create topic in `default/default/{topicName}`
     - Update event storage paths
     - Update event IDs to include tenant/namespace

4. **Migrate consumers**
   - For each existing consumer:
     - Associate with `default/default` namespace
     - Update consumer storage

5. **Update API clients**
   - Update URLs to include tenant/namespace
   - Provide migration guide

---

## Testing Requirements

### Unit Tests
- Projection service tests (rebuild from events)
- Permission calculation tests
- Event schema validation tests

### Integration Tests
- Tenant lifecycle (create, update, delete)
- Namespace lifecycle
- Permission grant/revoke
- User association with tenants
- Bootstrap process

### End-to-End Tests
- Complete tenant setup flow
- User login and tenant selection
- Permission-based access control
- Cross-tenant isolation

---

## Performance Considerations

### Caching Strategy
- Permission projections cached in-memory
- Cache invalidation on permission events
- Periodic cache refresh for missed events
- Cache warming on startup

### Query Optimization
- Index events by tenantId, namespaceId for fast filtering
- Limit event reads with pagination
- Use projections for existence checks (not full event reads)

### Scalability
- Projection services can be scaled horizontally
- Cache can be distributed (Redis) if needed
- Event storage can be partitioned by tenant

---

## Open Questions

1. **Event Retention**: How long to keep management events? Forever for audit?
2. **Projection Refresh**: How often to refresh projections? On-demand or periodic?
3. **Cross-Tenant Operations**: Should system admins be able to perform cross-tenant operations?
4. **Tenant Quotas**: How to enforce quotas? Per-operation checks or background jobs?
5. **Namespace Limits**: Should there be limits on namespaces per tenant?

---

## Appendix

### Event ID Format

**Current**: `user-events-42`  
**New**: `acme-corp/billing-app/invoices-42`

### Qualified Names

- **Tenant**: `acme-corp`
- **Namespace**: `acme-corp/billing-app`
- **Topic**: `acme-corp/billing-app/invoices`
- **Event**: `acme-corp/billing-app/invoices-42`

### Reserved Identifiers

- **Tenant**: `$system` (reserved for system operations)
- **Namespace**: `$management` (reserved for management events)
- **Topics**: `tenants`, `namespaces`, `users`, `permissions`, `api-keys` (in `$system/$management`)

---

**Document End**


