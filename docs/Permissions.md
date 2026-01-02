# Permissions Specification

This document defines the complete permission model for the Event Store system. This is the authoritative specification against which all authorization logic is implemented.

## Table of Contents

1. [Overview](#overview)
2. [Permission Types](#permission-types)
3. [Resource Types](#resource-types)
4. [Permission Scoping](#permission-scoping)
5. [Permission Context and Cascading](#permission-context-and-cascading)
6. [Principal Types](#principal-types)
7. [Permission Checking Logic](#permission-checking-logic)
8. [Common Permission Patterns](#common-permission-patterns)
9. [Future Development](#future-development)

---

## Overview

The Event Store uses a **context-aware, hierarchical permission system** that supports:

- **Multi-tenancy**: All permissions are scoped to a tenant context
- **Hierarchical resources**: Tenant → Namespace → Topic
- **Fine-grained control**: Permissions can be granted at any level with appropriate scope
- **Resource-specific permissions**: Some permissions only apply to specific resource types

### Key Concepts

- **Principal**: A user, API key, role, or group that can have permissions granted
- **Resource**: A tenant, namespace, topic, event, consumer, or user
- **Permission Grant**: A record that grants specific permissions to a principal for a resource within a context
- **Context**: The tenant (and optionally namespace/topic) in which a permission is granted or checked

**Important Note on Users:**
- Users are **system-wide** (global) entities with globally unique identities
- Users can be associated with **multiple tenants** simultaneously
- User management operations require **tenant context** for permission checking
- Permissions on USER resources are **tenant-scoped** (a user can have different permissions in different tenants)

---

## Permission Types

### Generic CRUD Permissions

These permissions apply to **all resource types**:

#### `CREATE`
**Grants:**
- Create new resources of the specified type
- For tenants: Create new tenants
- For namespaces: Create namespaces within the tenant
- For topics: Create topics within the namespace
- For users: Create new users (users are system-wide but user management operations require tenant context)
- For consumers: Register new consumers
- For events: Publish events to topics

**Required for:**
- `POST /tenants` - Create tenant (tenant/CreateTenantService.execute)
- `POST /tenants/{tenantId}/namespaces` - Create namespace (namespace/CreateNamespaceService.execute)
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/topics` - Create topic (topic/CreateTopicService.execute)
- `POST /tenants/{tenantId}/users` - Create user (user/CreateUserService.execute)
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/events` - Publish events (event/PublishEventsService.execute)

#### `READ`
**Grants:**
- View resource details
- Read resource metadata
- For events: Read/query events from topics
- For users: View user information
- For API keys: List and view API keys

**Required for:**
- `GET /tenants/{tenantId}` - Get tenant details (tenant/GetTenantService.getTenant)
- `GET /tenants/{tenantId}/namespaces/{namespaceId}` - Get namespace details (namespace/GetNamespaceService.getNamespace)
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicId}` - Get topic details (topic/GetTopicsService.get)
- `GET /tenants/{tenantId}/users/{userId}` - Get user details (user/GetUserService.getById)
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/events` - Query events (event/GetEventsService.execute)
- `GET /tenants/{tenantId}/users/{userId}/api-keys` - List API keys (apikey/GetApiKeyService.getByUserId)
- `GET /tenants/{tenantId}/users/{userId}/api-keys/{keyId}` - Get API key details (apikey/GetApiKeyService.getById)

#### `LIST`
**Grants:**
- List all resources of the specified type within scope
- Browse available resources

**Required for:**
- `GET /tenants` - List all tenants (tenant/GetTenantService.listTenants)
- `GET /tenants/{tenantId}/namespaces` - List namespaces in tenant (namespace/GetNamespaceService.listNamespaces)
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/topics` - List topics in namespace (topic/GetTopicsService.list)
- `GET /tenants/{tenantId}/users` - List users associated with tenant (user/GetUserService.list)
  - Note: Users are system-wide, but this lists users that have associations/permissions within the tenant context

#### `UPDATE`
**Grants:**
- Modify resource properties
- Update resource metadata
- For tenants: Update tenant configuration
- For namespaces: Update namespace settings
- For topics: Update topic configuration
- For users: Update user information (name, email, metadata)

**Required for:**
- `PUT /tenants/{tenantId}` - Update tenant (tenant/UpdateTenantService.execute)
- `PUT /tenants/{tenantId}/namespaces/{namespaceId}` - Update namespace (namespace/UpdateNamespaceService.execute)
- `PUT /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicId}` - Update topic (topic/UpdateTopicSchemasService.execute)
- `PUT /tenants/{tenantId}/users/{userId}` - Update user (user/UpdateUserService.execute)

#### `DELETE`
**Grants:**
- Remove resources
- Delete tenants, namespaces, topics, users, or consumers

**Required for:**
- `DELETE /tenants/{tenantId}` - Delete tenant (tenant/DeleteTenantService.execute)
- `DELETE /tenants/{tenantId}/namespaces/{namespaceId}` - Delete namespace (namespace/DeleteNamespaceService.execute)
- `DELETE /tenants/{tenantId}/users/{userId}` - Delete user (user/DeleteUserService.execute)

### Admin Permission

#### `ADMIN`
**Grants:**
- **All permissions** for the specified resource type within scope
- Equivalent to having `CREATE`, `READ`, `LIST`, `UPDATE`, `DELETE`, and all resource-specific permissions
- Does **not** automatically grant `PERMISSION_GRANT` or `PERMISSION_REVOKE` (these must be granted separately)

**Behavior:**
- When checking for any permission, if `ADMIN` is present, the check passes
- `ADMIN` at tenant level does **not** automatically cascade to namespaces/topics (see [Permission Context and Cascading](#permission-context-and-cascading))
- `ADMIN` is resource-type specific: `ADMIN` on `TENANT` does not grant `ADMIN` on `NAMESPACE`

**Example:**
```kotlin
// Grant ADMIN on tenant
PermissionGrant(
    resourceType = ResourceType.TENANT,
    resourceId = tenantId,
    permissions = setOf(Permission.ADMIN)
)

// This grants:
// - CREATE, READ, LIST, UPDATE, DELETE on the tenant
// - But NOT permissions on namespaces or topics within that tenant
// - To access namespaces/topics, separate grants are needed
```

### Resource-Specific Permissions

#### `PERMISSION_GRANT`
**Applies to:** `TENANT`, `NAMESPACE`, `TOPIC`

**Grants:**
- Grant permissions to other principals
- Delegate authority to manage access

**Required for:**
- `POST /tenants/{tenantId}/permissions` - Grant permissions (permission/GrantPermissionService.execute)
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/permissions` - Grant permissions (permission/GrantPermissionService.execute)
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicId}/permissions` - Grant permissions (permission/GrantPermissionService.execute)

**Note:** Cannot grant permissions you don't have. You can only grant permissions that you yourself possess.

#### `PERMISSION_REVOKE`
**Applies to:** `TENANT`, `NAMESPACE`, `TOPIC`

**Grants:**
- Revoke permissions from other principals
- Remove access previously granted

**Required for:**
- `DELETE /tenants/{tenantId}/permissions/{grantId}` - Revoke permissions (permission/RevokePermissionService.execute)

#### `SCHEMA_MANAGE`
**Applies to:** `TOPIC` (can be granted at `NAMESPACE` or `TENANT` level with context)

**Grants:**
- Create, update, and delete topic schemas
- Manage schema versions
- Validate event schemas

**Required for:**
- `PUT /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicId}/schemas` - Update topic schema (topic/UpdateTopicSchemasService.execute)

**Cascading Behavior:**
- Can be granted at tenant level: applies to all topics in the tenant
- Can be granted at namespace level: applies to all topics in the namespace
- Can be granted at topic level: applies only to that specific topic

#### `READ_HISTORY`
**Applies to:** `EVENT` only

**Grants:**
- Read historical events (beyond current retention)
- Access archived event data
- Query events with time-based filters

#### `READ_EXPORT`
**Applies to:** `EVENT` only

**Grants:**
- Export events in bulk
- Download event data
- Generate event reports

#### `WRITE_ADMIN`
**Applies to:** `EVENT` only

**Grants:**
- Bypass normal event validation
- Write events with special privileges
- Override normal event validation rules

#### `REPLAY`
**Applies to:** `EVENT` only

**Grants:**
- Replay events to topics
- Reprocess event streams
- Trigger event replay operations

#### `PURGE`
**Applies to:** `EVENT` only

**Grants:**
- Delete events from topics
- Purge event history
- Remove events permanently

#### `ACTIVATE`
**Applies to:** `USER` only

**Grants:**
- Activate suspended users
- Restore user access

#### `SUSPEND`
**Applies to:** `USER` only

**Grants:**
- Suspend user accounts
- Temporarily disable user access

#### `PASSWORD_RESET`
**Applies to:** `USER` only

**Grants:**
- Reset user passwords
- Force password changes

#### `API_KEY_MANAGE`
**Applies to:** `USER` only

**Grants:**
- Create API keys for users
- List API keys for users
- View API key details
- Revoke API keys for users

**Self-Service Behavior:**
- Users automatically have `API_KEY_MANAGE` permission for their own user resource
- This allows users to manage their own API keys without explicit permission grants
- To manage API keys for other users, explicit `API_KEY_MANAGE` permission must be granted

**Required for:**
- `POST /tenants/{tenantId}/users/{userId}/api-keys` - Create API key (apikey/CreateApiKeyService.execute)
- `GET /tenants/{tenantId}/users/{userId}/api-keys` - List API keys (apikey/GetApiKeyService.getByUserId)
- `GET /tenants/{tenantId}/users/{userId}/api-keys/{keyId}` - Get API key details (apikey/GetApiKeyService.getById)
- `DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId}` - Revoke API key (apikey/RevokeApiKeyService.execute)

**Note:** The self-service behavior means that when a user attempts to manage API keys for themselves (where `callerUserId == targetUserId`), the permission check automatically passes. For managing API keys for other users, the caller must have `API_KEY_MANAGE` permission on the target user's resource.

#### `MANAGE`
**Applies to:** `CONSUMER` only

**Grants:**
- Register and unregister consumers
- Update consumer configuration
- Manage consumer state

**Required for:**
- `POST /tenants/{tenantName}/namespaces/{namespaceName}/consumers/register` - Register consumer (consumer/RegisterConsumerService.execute)
- `DELETE /tenants/{tenantName}/namespaces/{namespaceName}/consumers/{id}` - Unregister consumer (consumer/UnregisterConsumerService.execute)

---

## Resource Types

### `TENANT`
Top-level organizational unit. All other resources exist within a tenant context.

**Operations:**
- Create, read, update, delete tenants
- List tenants (requires system-level permissions)

### `NAMESPACE`
Logical grouping of topics within a tenant. Provides isolation and organization.

**Operations:**
- Create, read, update, delete namespaces
- List namespaces within a tenant

### `TOPIC`
Event stream within a namespace. Events are published to and consumed from topics.

**Operations:**
- Create, read, update, delete topics
- List topics within a namespace
- Manage schemas
- Publish and consume events

### `EVENT`
Individual events within topics. Events are the core data unit.

**Operations:**
- Publish events (CREATE)
- Query/read events (READ)
- Export events (READ_EXPORT)
- Replay events (REPLAY)
- Purge events (PURGE)

### `CONSUMER`
Event consumers that read from topics.

**Operations:**
- Register consumers (MANAGE)
- Unregister consumers (MANAGE)
- Update consumer configuration (MANAGE)

### `USER`
User accounts are **system-wide** (global) entities, but user management operations are **tenant-scoped** for permission purposes.

**Important:** Users exist at the system level and can be associated with multiple tenants. However, all user management operations require tenant context, and permissions on USER resources are scoped to specific tenants.

**User Model:**
- Users are stored system-wide (in `$system/$management/users`)
- Users have globally unique email addresses and IDs
- Users can be associated with multiple tenants via `UserTenantAssociation`
- Users have an optional `primaryTenantId` (default tenant)

**Operations:**
- Create users (CREATE) - Creates system-wide user, but operation requires tenant context
- View user details (READ) - Requires tenant context
- Update user information (UPDATE) - Requires tenant context
- Delete users (DELETE) - Requires tenant context
- Activate/suspend users (ACTIVATE/SUSPEND) - Requires tenant context
- Reset passwords (PASSWORD_RESET) - Requires tenant context
- Manage API keys (API_KEY_MANAGE) - Requires tenant context

**Permission Scoping:**
- Permissions on USER resources are **tenant-scoped**
- A user can have different permissions in different tenants
- Example: User Alice can have ADMIN in Tenant A and READ in Tenant B

**Note on API Key Management:**
Users can manage their own API keys without explicit permission grants (self-service). To manage API keys for other users, `API_KEY_MANAGE` permission must be granted on the target user's resource within the tenant context.

---

## Permission Scoping

Permissions can be scoped at two levels:

### 1. Global Scope (`resourceId = null`)

When `resourceId` is `null`, the permission applies to **all resources of that type** within the tenant context.

**Example:**
```kotlin
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // null = all topics
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    permissions = setOf(Permission.READ)
)
```

**Grants:** Read access to **all topics** in the specified namespace.

**Use Cases:**
- Granting access to all topics in a namespace
- Granting access to all users associated with a tenant (note: users are system-wide but permissions are tenant-scoped)
- System administrators who need broad access

### 2. Specific Resource Scope (`resourceId = UUID`)

When `resourceId` is a UUID, the permission applies **only to that specific resource**.

**Example:**
```kotlin
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = "123e4567-e89b-12d3-a456-426614174000",  // Specific topic UUID
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    permissions = setOf(Permission.READ)
)
```

**Grants:** Read access to **only that specific topic**.

**Use Cases:**
- Fine-grained access control
- Granting access to specific resources
- Limiting access to sensitive data

### Resource ID Stability

**Important:** Resource IDs are stable UUIDs that never change, even if a resource is renamed. This ensures permissions remain valid after resource name changes.

**Example:**
- Tenant named "acme-corp" has UUID `abc-123`
- Tenant is renamed to "acme-inc"
- Permission grants referencing `abc-123` continue to work
- Only the human-readable name changed, not the resource ID

---

## Permission Context and Cascading

### Understanding Context

Every permission grant has a **context** that includes:
- `tenantResourceId` (required): The tenant in which the permission is granted
- `namespaceResourceId` (optional): The namespace context
- `topicResourceId` (optional): The topic context

### How Context Matching Works

When checking permissions, the system looks for grants that **match the operation's context**:

1. **Tenant must match**: The grant's `tenantResourceId` must match the operation's tenant
2. **Namespace must match** (if specified): If the operation is in a namespace, the grant must either:
   - Have the same `namespaceResourceId`, OR
   - Have `namespaceResourceId = null` (applies to all namespaces in tenant)
3. **Topic must match** (if specified): If the operation is on a topic, the grant must either:
   - Have the same `topicResourceId`, OR
   - Have `topicResourceId = null` (applies to all topics in namespace)

### Cascading Behavior

**Important Subtlety:** Permissions do **not** automatically cascade from higher to lower levels. Instead, the system uses **context matching** to find applicable grants.

#### Example 1: Tenant-Level Grant

```kotlin
// Grant READ on all topics in tenant
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = null,  // All namespaces
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.READ)
)
```

**What this grants:**
- Read access to **all topics** in **all namespaces** within the tenant
- Works because `namespaceResourceId = null` matches any namespace
- Works because `topicResourceId = null` matches any topic

#### Example 2: Namespace-Level Grant

```kotlin
// Grant READ on all topics in a specific namespace
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = specificNamespaceId,  // Specific namespace
    topicResourceId = null,  // All topics in that namespace
    permissions = setOf(Permission.READ)
)
```

**What this grants:**
- Read access to **all topics** in the **specific namespace**
- Does **not** grant access to topics in other namespaces
- Works because `topicResourceId = null` matches any topic in that namespace

#### Example 3: Topic-Level Grant

```kotlin
// Grant READ on a specific topic
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = specificTopicId,  // Specific topic
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    topicResourceId = specificTopicId,  // Specific topic
    permissions = setOf(Permission.READ)
)
```

**What this grants:**
- Read access to **only that specific topic**
- Does **not** grant access to other topics

### ADMIN Permission and Context

`ADMIN` permission follows the same context matching rules:

```kotlin
// ADMIN on tenant
PermissionGrant(
    resourceType = ResourceType.TENANT,
    resourceId = tenantId,
    tenantResourceId = tenantId,
    permissions = setOf(Permission.ADMIN)
)
```

**What this grants:**
- Full control over the **tenant resource itself**
- Does **not** automatically grant access to namespaces or topics
- To access namespaces/topics, you need separate grants with appropriate context

**To grant access to all resources in a tenant:**
```kotlin
// Grant ADMIN on all namespaces in tenant
PermissionGrant(
    resourceType = ResourceType.NAMESPACE,
    resourceId = null,  // All namespaces
    tenantResourceId = tenantId,
    namespaceResourceId = null,  // All namespaces
    permissions = setOf(Permission.ADMIN)
)

// Grant ADMIN on all topics in tenant
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = null,  // All namespaces
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.ADMIN)
)
```

### SCHEMA_MANAGE Cascading

`SCHEMA_MANAGE` is special because it can be granted at different levels and applies to topics:

```kotlin
// Grant SCHEMA_MANAGE at tenant level
PermissionGrant(
    resourceType = ResourceType.TOPIC,  // Note: resourceType is TOPIC
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = null,  // All namespaces
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.SCHEMA_MANAGE)
)
```

**What this grants:**
- Schema management for **all topics** in **all namespaces** in the tenant

```kotlin
// Grant SCHEMA_MANAGE at namespace level
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = specificNamespaceId,  // Specific namespace
    topicResourceId = null,  // All topics in that namespace
    permissions = setOf(Permission.SCHEMA_MANAGE)
)
```

**What this grants:**
- Schema management for **all topics** in the **specific namespace**

---

## Principal Types

Permissions can be granted to different types of principals:

### `USER`
Individual user accounts. Most common principal type.

### `API_KEY`
API keys associated with users. API keys inherit the permissions of their associated user.

### `ROLE`
Named roles that can be assigned to multiple users. Permissions granted to a role apply to all users with that role.

**Note:** Role support may be implemented in future versions.

### `GROUP`
Groups of users. Permissions granted to a group apply to all members.

**Note:** Group support may be implemented in future versions.

---

## Permission Checking Logic

### Algorithm

When checking if a principal has permission to perform an action:

1. **Extract context** from the operation:
   - Tenant (required)
   - Namespace (if applicable)
   - Topic (if applicable)

2. **Find matching grants:**
   - Grants where `tenantResourceId` matches
   - Grants where `namespaceResourceId` matches or is `null`
   - Grants where `topicResourceId` matches or is `null`
   - Grants that are not expired

3. **Check permission:**
   - Grant's `resourceType` must match the operation's resource type
   - Grant's `resourceId` must match the operation's resource ID, or be `null` (global scope)
   - Grant's `permissions` must include the required permission, OR include `ADMIN`

4. **Result:**
   - If any matching grant satisfies the permission check, return `true`
   - Otherwise, return `false`

### Example Permission Check

**Operation:** Read topic `topic-123` in namespace `ns-456` in tenant `tenant-789`

**Grants to check:**
```kotlin
// Grant 1: READ on all topics in namespace
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenant-789,
    namespaceResourceId = ns-456,
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.READ)
)
// ✅ MATCHES: tenant matches, namespace matches, topicResourceId=null matches any topic

// Grant 2: READ on specific topic
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = topic-123,
    tenantResourceId = tenant-789,
    namespaceResourceId = ns-456,
    topicResourceId = topic-123,
    permissions = setOf(Permission.READ)
)
// ✅ MATCHES: tenant matches, namespace matches, topic matches

// Grant 3: ADMIN on all topics in tenant
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,
    tenantResourceId = tenant-789,
    namespaceResourceId = null,  // All namespaces
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.ADMIN)
)
// ✅ MATCHES: tenant matches, namespaceResourceId=null matches any namespace, topicResourceId=null matches any topic

// Grant 4: READ on topic in different namespace
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,
    tenantResourceId = tenant-789,
    namespaceResourceId = other-namespace,  // Different namespace
    topicResourceId = null,
    permissions = setOf(Permission.READ)
)
// ❌ DOES NOT MATCH: namespace doesn't match

// Grant 5: READ on topic in different tenant
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,
    tenantResourceId = other-tenant,  // Different tenant
    namespaceResourceId = null,
    topicResourceId = null,
    permissions = setOf(Permission.READ)
)
// ❌ DOES NOT MATCH: tenant doesn't match
```

**Result:** Permission granted (Grant 1, 2, or 3 would satisfy the check)

---

## Common Permission Patterns

### Pattern 1: Tenant Administrator

Grant full control over a tenant and all its resources:

```kotlin
// Admin on tenant
PermissionGrant(
    resourceType = ResourceType.TENANT,
    resourceId = tenantId,
    tenantResourceId = tenantId,
    permissions = setOf(Permission.ADMIN, Permission.PERMISSION_GRANT, Permission.PERMISSION_REVOKE)
)

// Admin on all namespaces
PermissionGrant(
    resourceType = ResourceType.NAMESPACE,
    resourceId = null,
    tenantResourceId = tenantId,
    namespaceResourceId = null,
    permissions = setOf(Permission.ADMIN)
)

// Admin on all topics
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,
    tenantResourceId = tenantId,
    namespaceResourceId = null,
    topicResourceId = null,
    permissions = setOf(Permission.ADMIN)
)

// Admin on all users (within tenant context)
// Note: Users are system-wide, but this grants ADMIN on all users
// that have permissions/associations within this tenant
PermissionGrant(
    resourceType = ResourceType.USER,
    resourceId = null,
    tenantResourceId = tenantId,
    permissions = setOf(Permission.ADMIN)
)
```

### Pattern 2: Read-Only Access to All Topics

Grant read access to all topics in a namespace:

```kotlin
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.READ)
)
```

### Pattern 3: Event Publisher

Grant permission to publish events to specific topics:

```kotlin
PermissionGrant(
    resourceType = ResourceType.EVENT,
    resourceId = null,  // All events
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    topicResourceId = topicId,  // Specific topic
    permissions = setOf(Permission.CREATE)
)
```

### Pattern 4: Schema Manager

Grant schema management for all topics in a namespace:

```kotlin
PermissionGrant(
    resourceType = ResourceType.TOPIC,
    resourceId = null,  // All topics
    tenantResourceId = tenantId,
    namespaceResourceId = namespaceId,
    topicResourceId = null,  // All topics
    permissions = setOf(Permission.SCHEMA_MANAGE)
)
```

### Pattern 5: User Self-Service

Users automatically have `API_KEY_MANAGE` permission for their own user resource, allowing them to manage their own API keys without explicit permission grants.

**Self-Service (Automatic):**
- Users can create, list, view, and revoke their own API keys
- No explicit permission grant is required
- The system automatically allows API key management when `callerUserId == targetUserId`

**Managing Other Users' API Keys:**
To allow a user to manage API keys for other users, grant `API_KEY_MANAGE` permission:

```kotlin
// Allow user to manage API keys for specific other user
PermissionGrant(
    resourceType = ResourceType.USER,
    resourceId = targetUserId,  // Specific user whose keys can be managed
    tenantResourceId = tenantId,
    permissions = setOf(Permission.API_KEY_MANAGE)
)
```

**Or grant for all users associated with a tenant:**
```kotlin
// Allow user to manage API keys for all users associated with this tenant
// Note: Users are system-wide, but this permission applies to all users
// that have permissions/associations within this tenant context
PermissionGrant(
    resourceType = ResourceType.USER,
    resourceId = null,  // All users (within tenant context)
    tenantResourceId = tenantId,
    permissions = setOf(Permission.API_KEY_MANAGE)
)
```

---

## Summary

This permission system provides:

1. **Flexibility**: Permissions can be granted at any level with appropriate scope
2. **Security**: Context-aware checking ensures permissions are only valid in the right tenant/namespace/topic
3. **Granularity**: Fine-grained control over what principals can do
4. **Scalability**: Efficient permission checking with caching

**Key Takeaways:**

- Permissions are **context-aware**: They must match the tenant/namespace/topic context of the operation
- Permissions do **not automatically cascade**: You must grant permissions at the appropriate level with appropriate context
- `ADMIN` grants all permissions for a resource type, but does not automatically grant access to child resources
- Global scope (`resourceId = null`) allows access to all resources of that type within the context

This specification should be used as the authoritative reference for all permission-related implementation decisions.

---

## Future Development

### Permission Constraints

**Status:** Not currently implemented. This section describes planned functionality for future releases.

Permissions may have constraints that limit their scope in future versions:

#### Event Type Constraints

Limit permissions to specific event types:

```kotlin
PermissionGrant(
    resourceType = ResourceType.EVENT,
    permissions = setOf(Permission.READ),
    constraints = PermissionConstraints(
        eventTypes = setOf("OrderCreated", "OrderCancelled")
    )
)
```

**Grants:** Read access to events, but **only** events of type "OrderCreated" or "OrderCancelled".

#### Max Age Constraints

Limit access to recent events:

```kotlin
PermissionGrant(
    resourceType = ResourceType.EVENT,
    permissions = setOf(Permission.READ),
    constraints = PermissionConstraints(
        maxAgeDays = 30  // Only events from last 30 days
    )
)
```

**Grants:** Read access to events, but **only** events from the last 30 days.

#### Time-Based Constraints

Limit permissions to specific time windows:

```kotlin
PermissionGrant(
    resourceType = ResourceType.EVENT,
    permissions = setOf(Permission.READ),
    constraints = PermissionConstraints(
        timeBased = TimeBasedConstraint(
            startTime = "2025-01-01T00:00:00Z",
            endTime = "2025-12-31T23:59:59Z"
        )
    )
)
```

**Grants:** Read access to events, but **only** during the specified time window.

#### Combining Constraints

Constraints can be combined:

```kotlin
PermissionGrant(
    resourceType = ResourceType.EVENT,
    permissions = setOf(Permission.READ),
    constraints = PermissionConstraints(
        eventTypes = setOf("OrderCreated"),
        maxAgeDays = 7,
        timeBased = TimeBasedConstraint(
            startTime = "09:00:00",
            endTime = "17:00:00"
        )
    )
)
```

**Grants:** Read access to "OrderCreated" events from the last 7 days, but only during business hours (9 AM - 5 PM).

**Note:** When constraints are implemented, they will be evaluated at query time to filter results, not as permission denials. Users with constrained permissions will receive filtered result sets rather than access errors.

