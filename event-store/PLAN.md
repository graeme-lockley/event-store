# Event Store - Multi-Tenancy Implementation Plan

This document provides a detailed, phased implementation plan for adding tenant & namespace management, user management, permissions & authorization, and API key management to the Event Store. The plan follows the event-sourced architecture specified in `TENANT_NAMESPACE_REQUIREMENTS.md`.

## Document Information

**Version**: 1.0  
**Date**: 2025-01-15  
**Status**: Active  
**Related Documents**: 
- `TENANT_NAMESPACE_REQUIREMENTS.md` - Architecture and requirements
- `FEATURES.md` - Feature roadmap

---

## Overview

This plan implements the critical features (CRIT-001 through CRIT-004) in a phased approach that:
- Maintains system functionality at each step
- Uses event-sourcing from the start (per requirements)
- Builds incrementally (one system at a time)
- Allows incremental deployment (feature flags)

### Implementation Phases

1. **Phase 1A**: Bootstrap & Tenant Management (Week 1-2)
2. **Phase 1B**: Namespace Management (Week 2-3)
3. **Phase 1C**: User Management (Week 3-4)
4. **Phase 1D**: Permissions & Authorization (Week 4-5)
5. **Phase 1E**: API Key Management (Week 5-6)

**Total Estimated Timeline**: 5-6 weeks

---

## Phase 1A: Bootstrap & Tenant Management

**Timeline**: Week 1-2 (10 working days)  
**Goal**: Establish the foundation with bootstrap process and basic tenant management using event-sourcing.

### Objectives

1. Create bootstrap service to initialize `$system` tenant and `$management` namespace
2. Implement tenant domain model and event schemas
3. Create tenant projection service
4. Implement tenant management operations (CRUD)
5. Add feature flags for gradual rollout

### Tasks

#### Task 1.1: Domain Model & Events

**Files to create/modify:**
- `src/main/kotlin/com/eventstore/domain/Tenant.kt` (new)
- `src/main/kotlin/com/eventstore/domain/events/TenantEvents.kt` (new)

**Implementation:**

```kotlin
// Tenant.kt
data class Tenant(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val quota: Quota? = null,
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
    val maxEventSizeBytes: Long = 1024 * 1024
)
```

**Event schemas:**
- `tenant.created`
- `tenant.updated`
- `tenant.deleted`

**Acceptance Criteria:**
- [ ] Tenant domain model matches requirements document
- [ ] Event schemas defined with proper JSON structure
- [ ] Unit tests for domain model validation

---

#### Task 1.2: Bootstrap Service

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/bootstrap/BootstrapService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/bootstrap/BootstrapService.kt` (new - interface)

**Implementation:**

The bootstrap service must:
1. Check if `$system` tenant exists (read events from `$system/$management/tenants`)
2. If not, create `$system` tenant (bypass normal validation)
3. Create `$management` namespace in `$system` tenant
4. Create system topics: `tenants`, `namespaces`, `users`, `permissions`, `api-keys`
5. Publish initial events

**Key challenge**: Chicken-and-egg problem - need to create tenant before tenant system exists.

**Solution**: Special bootstrap mode that:
- Directly writes to event storage (bypasses normal topic validation)
- Creates topics directly in file system
- Uses special bootstrap user context

**Acceptance Criteria:**
- [ ] Bootstrap service runs on application startup
- [ ] Creates `$system` tenant if it doesn't exist
- [ ] Creates `$management` namespace if it doesn't exist
- [ ] Creates all system topics if they don't exist
- [ ] Idempotent (can run multiple times safely)
- [ ] Unit tests for bootstrap logic
- [ ] Integration test for bootstrap process

---

#### Task 1.3: Tenant Projection Service

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/projections/TenantProjectionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/ports/outbound/TenantRepository.kt` (new - interface)

**Implementation:**

```kotlin
class TenantProjectionService(
    private val eventRepository: EventRepository
) {
    suspend fun getTenant(tenantId: String): Tenant? {
        // Read events from $system/$management/tenants
        // Filter by tenantId
        // Rebuild tenant state from events
    }
    
    suspend fun getAllTenants(): List<Tenant> {
        // Read all events, group by tenantId, rebuild
    }
    
    suspend fun tenantExists(tenantId: String): Boolean {
        return getTenant(tenantId)?.isActive == true
    }
}
```

**Acceptance Criteria:**
- [ ] Can rebuild tenant state from events
- [ ] Handles tenant.created, tenant.updated, tenant.deleted events
- [ ] Returns null for non-existent tenants
- [ ] Returns only active tenants (deletedAt == null)
- [ ] Unit tests for projection logic
- [ ] Integration tests with real events

---

#### Task 1.4: Tenant Management Services

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/tenant/CreateTenantService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/tenant/GetTenantService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/tenant/UpdateTenantService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/tenant/DeleteTenantService.kt` (new)

**Implementation:**

Each service:
1. Validates input
2. Checks permissions (initially bypassed, added in Phase 1D)
3. Publishes event to `$system/$management/tenants`
4. Returns result

**Acceptance Criteria:**
- [ ] Create tenant publishes `tenant.created` event
- [ ] Update tenant publishes `tenant.updated` event
- [ ] Delete tenant publishes `tenant.deleted` event (soft delete)
- [ ] Validation prevents duplicate tenant IDs
- [ ] Unit tests for each service
- [ ] Integration tests for full lifecycle

---

#### Task 1.5: Tenant API Routes

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/routes/tenantRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/TenantDto.kt` (new)

**Endpoints:**
- `POST /tenants` - Create tenant
- `GET /tenants` - List all tenants
- `GET /tenants/{tenantId}` - Get tenant details
- `PUT /tenants/{tenantId}` - Update tenant
- `DELETE /tenants/{tenantId}` - Delete tenant (soft delete)

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper HTTP status codes
- [ ] Error handling with appropriate error responses
- [ ] Request/response DTOs match domain model
- [ ] Integration tests for all endpoints

---

#### Task 1.6: Feature Flags

**Files to modify:**
- `src/main/kotlin/com/eventstore/Config.kt`

**Add configuration:**
```kotlin
data class Config(
    // ... existing fields
    val multiTenantEnabled: Boolean = false,
    val authEnabled: Boolean = false
)
```

**Usage:**
- Check `multiTenantEnabled` before tenant operations
- If disabled, use default tenant behavior
- Allows gradual rollout

**Acceptance Criteria:**
- [ ] Feature flags added to Config
- [ ] Flags can be set via environment variables
- [ ] System works with flags disabled (backward compatible)
- [ ] System works with flags enabled (new behavior)

---

#### Task 1.7: Update Event Repository for Multi-Tenancy

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/ports/outbound/EventRepository.kt`
- `src/main/kotlin/com/eventstore/infrastructure/persistence/FileSystemEventRepository.kt`

**Changes:**
- Add `tenantId` and `namespaceId` parameters to methods (optional for backward compatibility)
- Update file paths to include tenant/namespace structure
- Support both old and new paths during migration

**Acceptance Criteria:**
- [ ] EventRepository interface updated with optional tenant/namespace params
- [ ] FileSystemEventRepository supports new path structure
- [ ] Backward compatible with existing events (default tenant/namespace)
- [ ] Unit tests for path generation
- [ ] Integration tests for event storage/retrieval

---

#### Task 1.8: Update Topic Model for Multi-Tenancy

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/Topic.kt`
- `src/main/kotlin/com/eventstore/infrastructure/persistence/FileSystemTopicRepository.kt`

**Changes:**
- Add `tenantId` and `namespaceId` to Topic (default to "default")
- Update topic storage paths
- Support both old and new topic paths

**Acceptance Criteria:**
- [ ] Topic model includes tenantId and namespaceId
- [ ] Backward compatible (defaults to "default")
- [ ] Topic storage supports new structure
- [ ] Existing topics continue to work
- [ ] Unit tests for topic model
- [ ] Integration tests for topic operations

---

#### Task 1.9: Update EventId for Multi-Tenancy

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/EventId.kt`

**Changes:**
- Update EventId format from `<topic>-<sequence>` to `<tenant>/<namespace>/<topic>-<sequence>`
- Support parsing both old and new formats
- Update EventId creation logic

**Acceptance Criteria:**
- [ ] EventId supports new format: `tenant/namespace/topic-sequence`
- [ ] Can parse old format: `topic-sequence` (backward compatible)
- [ ] Can parse new format
- [ ] Unit tests for EventId parsing and creation
- [ ] Integration tests for event ID generation

---

#### Task 1.10: Testing

**Test files to create:**
- `src/test/kotlin/com/eventstore/infrastructure/bootstrap/BootstrapServiceTest.kt`
- `src/test/kotlin/com/eventstore/infrastructure/projections/TenantProjectionServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/tenant/CreateTenantServiceTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/routes/TenantRoutesTest.kt`
- `src/test/kotlin/com/eventstore/infrastructure/persistence/TenantRepositoryTest.kt` (integration)

**Acceptance Criteria:**
- [ ] Unit tests for all new services
- [ ] Integration tests for bootstrap process
- [ ] Integration tests for tenant CRUD operations
- [ ] Integration tests for projection service
- [ ] All tests pass
- [ ] Test coverage > 80%

---

### Phase 1A Deliverables

- [ ] Bootstrap service implemented and tested
- [ ] Tenant domain model and events defined
- [ ] Tenant projection service implemented
- [ ] Tenant management services (CRUD) implemented
- [ ] Tenant API routes implemented
- [ ] Feature flags added
- [ ] Event/Topic/EventId models updated for multi-tenancy
- [ ] All tests passing
- [ ] Documentation updated

---

## Phase 1B: Namespace Management

**Timeline**: Week 2-3 (10 working days)  
**Goal**: Add namespace management using event-sourcing, building on tenant foundation.

### Objectives

1. Implement namespace domain model and event schemas
2. Create namespace projection service
3. Implement namespace management operations (CRUD)
4. Update topic operations to require namespace
5. Migrate existing topics to default namespace

### Tasks

#### Task 2.1: Namespace Domain Model & Events

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/Namespace.kt` (new)
- `src/main/kotlin/com/eventstore/domain/events/NamespaceEvents.kt` (new)

**Implementation:**

```kotlin
data class Namespace(
    val tenantId: String,
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    val isActive: Boolean
        get() = deletedAt == null
    
    fun qualifiedName(): String = "$tenantId/$id"
}
```

**Event schemas:**
- `namespace.created`
- `namespace.updated`
- `namespace.deleted`

**Acceptance Criteria:**
- [ ] Namespace domain model matches requirements
- [ ] Event schemas defined
- [ ] Unit tests for domain model

---

#### Task 2.2: Namespace Projection Service

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/projections/NamespaceProjectionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/ports/outbound/NamespaceRepository.kt` (new)

**Implementation:**

Similar to TenantProjectionService:
- Read events from `$system/$management/namespaces`
- Filter by tenantId and namespaceId
- Rebuild namespace state from events

**Acceptance Criteria:**
- [ ] Can rebuild namespace state from events
- [ ] Handles all namespace event types
- [ ] Returns only active namespaces
- [ ] Unit tests for projection logic
- [ ] Integration tests with real events

---

#### Task 2.3: Namespace Management Services

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/namespace/CreateNamespaceService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/namespace/GetNamespaceService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/namespace/UpdateNamespaceService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/namespace/DeleteNamespaceService.kt` (new)

**Implementation:**

Each service:
1. Validates tenant exists
2. Validates input
3. Publishes event to `$system/$management/namespaces`
4. Returns result

**Acceptance Criteria:**
- [ ] All CRUD operations implemented
- [ ] Validates tenant exists
- [ ] Prevents duplicate namespace IDs within tenant
- [ ] Unit tests for each service
- [ ] Integration tests for full lifecycle

---

#### Task 2.4: Namespace API Routes

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/routes/namespaceRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/NamespaceDto.kt` (new)

**Endpoints:**
- `POST /tenants/{tenantId}/namespaces` - Create namespace
- `GET /tenants/{tenantId}/namespaces` - List namespaces in tenant
- `GET /tenants/{tenantId}/namespaces/{namespaceId}` - Get namespace
- `PUT /tenants/{tenantId}/namespaces/{namespaceId}` - Update namespace
- `DELETE /tenants/{tenantId}/namespaces/{namespaceId}` - Delete namespace

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper error handling
- [ ] Integration tests for all endpoints

---

#### Task 2.5: Update Topic Operations for Namespace

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/services/topic/CreateTopicService.kt`
- `src/main/kotlin/com/eventstore/interfaces/http/routes/topicRoutes.kt`
- `src/main/kotlin/com/eventstore/infrastructure/persistence/FileSystemTopicRepository.kt`

**Changes:**
- Topics now require tenantId and namespaceId
- Update topic creation to validate namespace exists
- Update topic paths to include tenant/namespace
- Support both old paths (backward compatibility) and new paths

**New topic endpoints:**
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/topics`
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/topics`
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}`
- `PUT /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}`

**Legacy endpoints (backward compatibility):**
- `POST /topics` - Creates in `default/default` namespace
- `GET /topics` - Lists topics in `default/default` namespace

**Acceptance Criteria:**
- [ ] Topics require namespace
- [ ] New endpoints work with tenant/namespace
- [ ] Legacy endpoints still work (backward compatible)
- [ ] Topic validation checks namespace exists
- [ ] Integration tests for both old and new endpoints

---

#### Task 2.6: Migration Service for Existing Topics

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/migration/TopicMigrationService.kt` (new)

**Implementation:**

Service that:
1. Creates `default` tenant if it doesn't exist
2. Creates `default` namespace in default tenant if it doesn't exist
3. Migrates all existing topics to `default/default/{topicName}`
4. Updates topic storage paths
5. Updates event storage paths (if needed)

**Acceptance Criteria:**
- [ ] Migration service can migrate existing topics
- [ ] Creates default tenant/namespace if needed
- [ ] Updates all topic paths
- [ ] Idempotent (can run multiple times)
- [ ] Integration test for migration
- [ ] Can rollback if needed

---

#### Task 2.7: Update Event Operations for Namespace

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/services/event/PublishEventsService.kt`
- `src/main/kotlin/com/eventstore/domain/services/event/GetEventsService.kt`
- `src/main/kotlin/com/eventstore/interfaces/http/routes/eventRoutes.kt`

**Changes:**
- Events now require tenant/namespace context
- Update event publishing to use qualified topic names
- Update event retrieval to support tenant/namespace paths

**New event endpoints:**
- `POST /tenants/{tenantId}/namespaces/{namespaceId}/events`
- `GET /tenants/{tenantId}/namespaces/{namespaceId}/topics/{topicName}/events`

**Legacy endpoints:**
- `POST /events` - Publishes to `default/default` namespace
- `GET /topics/{topicName}/events` - Reads from `default/default` namespace

**Acceptance Criteria:**
- [ ] Events work with new tenant/namespace structure
- [ ] Legacy endpoints still work
- [ ] Event IDs use new format
- [ ] Integration tests for both old and new endpoints

---

#### Task 2.8: Testing

**Test files to create:**
- `src/test/kotlin/com/eventstore/infrastructure/projections/NamespaceProjectionServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/namespace/CreateNamespaceServiceTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/routes/NamespaceRoutesTest.kt`
- `src/test/kotlin/com/eventstore/infrastructure/migration/TopicMigrationServiceTest.kt`

**Acceptance Criteria:**
- [ ] Unit tests for all new services
- [ ] Integration tests for namespace CRUD
- [ ] Integration tests for topic migration
- [ ] Integration tests for event operations with namespaces
- [ ] All tests pass

---

### Phase 1B Deliverables

- [ ] Namespace domain model and events defined
- [ ] Namespace projection service implemented
- [ ] Namespace management services (CRUD) implemented
- [ ] Namespace API routes implemented
- [ ] Topic operations updated for namespace
- [ ] Event operations updated for namespace
- [ ] Migration service for existing topics
- [ ] All tests passing
- [ ] Documentation updated

---

## Phase 1C: User Management

**Timeline**: Week 3-4 (10 working days)  
**Goal**: Implement system-wide user management with tenant associations using event-sourcing.

### Objectives

1. Implement user domain model and event schemas
2. Create user projection service
3. Implement user management operations
4. Implement authentication (login/logout)
5. Implement password management
6. Implement user-tenant associations

### Tasks

#### Task 3.1: User Domain Model & Events

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/User.kt` (new)
- `src/main/kotlin/com/eventstore/domain/events/UserEvents.kt` (new)

**Implementation:**

```kotlin
data class User(
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val emailVerified: Boolean = false,
    val primaryTenantId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
    PENDING_ACTIVATION
}

data class UserTenantAssociation(
    val userId: String,
    val tenantId: String,
    val role: String? = null,
    val assignedAt: Instant,
    val assignedBy: String,
    val isPrimary: Boolean = false
)
```

**Event schemas:**
- `user.created`
- `user.updated`
- `user.status.changed`
- `user.password.changed`
- `user.tenant.assigned`
- `user.tenant.removed`

**Acceptance Criteria:**
- [ ] User domain model matches requirements
- [ ] Event schemas defined
- [ ] Password hashing strategy defined (bcrypt)
- [ ] Unit tests for domain model

---

#### Task 3.2: User Projection Service

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/projections/UserProjectionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/ports/outbound/UserRepository.kt` (new)

**Implementation:**

- Read events from `$system/$management/users`
- Rebuild user state from events
- Handle user-tenant associations
- Cache user lookups by email

**Acceptance Criteria:**
- [ ] Can rebuild user state from events
- [ ] Handles all user event types
- [ ] Tracks user-tenant associations
- [ ] Unit tests for projection logic
- [ ] Integration tests with real events

---

#### Task 3.3: User Management Services

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/user/CreateUserService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/user/GetUserService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/user/UpdateUserService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/user/DeleteUserService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/user/AssignUserToTenantService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/user/RemoveUserFromTenantService.kt` (new)

**Implementation:**

- Create user: validates email uniqueness, hashes password, publishes `user.created`
- Update user: publishes `user.updated`
- Change password: validates old password, hashes new password, publishes `user.password.changed`
- Assign to tenant: publishes `user.tenant.assigned`
- Remove from tenant: publishes `user.tenant.removed`

**Acceptance Criteria:**
- [ ] All user management operations implemented
- [ ] Password hashing uses bcrypt
- [ ] Email uniqueness enforced
- [ ] Unit tests for each service
- [ ] Integration tests for full lifecycle

---

#### Task 3.4: Authentication Service

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/auth/AuthenticationService.kt` (new)
- `src/main/kotlin/com/eventstore/infrastructure/auth/SessionManager.kt` (new)

**Implementation:**

- Login: validates credentials, creates session, updates `lastLoginAt`
- Logout: invalidates session
- Session management: in-memory initially (can be upgraded to Redis later)
- Returns available tenants for user

**Session format:**
- JWT token or session ID
- Contains user ID and available tenant IDs
- Stored in HTTP-only cookie or Authorization header

**Acceptance Criteria:**
- [ ] Login validates credentials
- [ ] Login creates session
- [ ] Logout invalidates session
- [ ] Returns available tenants
- [ ] Unit tests for authentication
- [ ] Integration tests for login/logout flow

---

#### Task 3.5: Authentication Middleware

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/middleware/AuthenticationMiddleware.kt` (new)

**Implementation:**

- Extracts session from request (cookie or Authorization header)
- Validates session
- Extracts user from session
- Stores user in call attributes
- Can be bypassed for public endpoints

**Acceptance Criteria:**
- [ ] Extracts and validates session
- [ ] Stores user in call attributes
- [ ] Can be bypassed for specific routes
- [ ] Returns 401 for invalid sessions
- [ ] Integration tests for middleware

---

#### Task 3.6: User API Routes

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/routes/userRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/routes/authRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/UserDto.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/AuthDto.kt` (new)

**Endpoints:**

**Authentication:**
- `POST /auth/login` - Login
- `POST /auth/logout` - Logout
- `POST /auth/password/change` - Change own password
- `GET /auth/tenants` - List available tenants

**User Management (tenant-scoped):**
- `POST /tenants/{tenantId}/users` - Create user or assign existing
- `GET /tenants/{tenantId}/users` - List users in tenant
- `GET /tenants/{tenantId}/users/{userId}` - Get user
- `PUT /tenants/{tenantId}/users/{userId}` - Update user
- `DELETE /tenants/{tenantId}/users/{userId}` - Remove user from tenant

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper error handling
- [ ] Integration tests for all endpoints

---

#### Task 3.7: Update Bootstrap for Default User

**Files to modify:**
- `src/main/kotlin/com/eventstore/infrastructure/bootstrap/BootstrapService.kt`

**Changes:**
- Create default admin user during bootstrap
- User: `admin@system` (or configurable)
- Password: from environment variable or default (must be changed on first login)
- Assign to `$system` tenant

**Acceptance Criteria:**
- [ ] Default admin user created during bootstrap
- [ ] Password from environment variable
- [ ] Assigned to `$system` tenant
- [ ] Integration test for bootstrap with user

---

#### Task 3.8: Testing

**Test files to create:**
- `src/test/kotlin/com/eventstore/infrastructure/projections/UserProjectionServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/user/CreateUserServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/auth/AuthenticationServiceTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/routes/AuthRoutesTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/routes/UserRoutesTest.kt`

**Acceptance Criteria:**
- [ ] Unit tests for all new services
- [ ] Integration tests for authentication flow
- [ ] Integration tests for user management
- [ ] Integration tests for user-tenant associations
- [ ] All tests pass

---

### Phase 1C Deliverables

- [ ] User domain model and events defined
- [ ] User projection service implemented
- [ ] User management services implemented
- [ ] Authentication service implemented
- [ ] Authentication middleware implemented
- [ ] User and auth API routes implemented
- [ ] Default admin user created during bootstrap
- [ ] All tests passing
- [ ] Documentation updated

---

## Phase 1D: Permissions & Authorization

**Timeline**: Week 4-5 (10 working days)  
**Goal**: Implement event-sourced permission system with authorization middleware.

### Objectives

1. Implement permission domain model and event schemas
2. Create permission projection service with caching
3. Implement permission management operations
4. Implement authorization middleware
5. Update all routes to use authorization

### Tasks

#### Task 4.1: Permission Domain Model & Events

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/Permission.kt` (new)
- `src/main/kotlin/com/eventstore/domain/events/PermissionEvents.kt` (new)

**Implementation:**

```kotlin
enum class Permission {
    // Tenant permissions
    TENANT_CREATE, TENANT_READ, TENANT_LIST, TENANT_UPDATE, TENANT_DELETE,
    TENANT_ADMIN, TENANT_PERMISSION_GRANT, TENANT_PERMISSION_REVOKE,
    
    // Namespace permissions
    NAMESPACE_CREATE, NAMESPACE_READ, NAMESPACE_LIST, NAMESPACE_UPDATE, NAMESPACE_DELETE,
    NAMESPACE_ADMIN,
    
    // Topic permissions
    TOPIC_CREATE, TOPIC_READ, TOPIC_LIST, TOPIC_UPDATE, TOPIC_DELETE,
    TOPIC_ADMIN, TOPIC_SCHEMA_MANAGE,
    
    // Event permissions
    EVENT_READ, EVENT_READ_HISTORY, EVENT_READ_EXPORT,
    EVENT_WRITE, EVENT_WRITE_ADMIN,
    EVENT_DELETE, EVENT_REPLAY, EVENT_PURGE,
    
    // Consumer permissions
    CONSUMER_CREATE, CONSUMER_READ, CONSUMER_LIST, CONSUMER_UPDATE, CONSUMER_DELETE,
    CONSUMER_ADMIN, CONSUMER_MANAGE,
    
    // User permissions
    USER_CREATE, USER_READ, USER_LIST, USER_UPDATE, USER_DELETE,
    USER_ADMIN, USER_ACTIVATE, USER_SUSPEND,
    USER_PASSWORD_RESET, USER_PERMISSION_GRANT, USER_PERMISSION_REVOKE
}

data class PermissionGrant(
    val principalId: String,
    val principalType: PrincipalType, // USER, API_KEY, ROLE, GROUP
    val resourceType: ResourceType, // TENANT, NAMESPACE, TOPIC, EVENT, CONSUMER, USER
    val resourceId: String? = null, // Specific resource or null for all
    val tenantId: String,
    val namespaceId: String? = null,
    val topicName: String? = null,
    val permissions: Set<Permission>,
    val constraints: PermissionConstraints? = null,
    val grantedBy: String,
    val grantedAt: Instant,
    val expiresAt: Instant? = null
)

enum class PrincipalType {
    USER, API_KEY, ROLE, GROUP
}

enum class ResourceType {
    TENANT, NAMESPACE, TOPIC, EVENT, CONSUMER, USER
}

data class PermissionConstraints(
    val eventTypes: Set<String>? = null,
    val maxAgeDays: Int? = null,
    val timeBased: TimeBasedConstraint? = null
)
```

**Event schemas:**
- `permission.granted`
- `permission.revoked`

**Acceptance Criteria:**
- [ ] Permission model matches requirements
- [ ] Event schemas defined
- [ ] Unit tests for permission model

---

#### Task 4.2: Permission Projection Service

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/projections/PermissionProjectionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/ports/outbound/PermissionRepository.kt` (new)

**Implementation:**

- Read events from `$system/$management/permissions`
- Rebuild permission state with caching
- Calculate effective permissions (including inheritance)
- Cache invalidation on permission events

**Caching strategy:**
- In-memory cache (ConcurrentHashMap)
- Cache key: `principalId:tenantId:namespaceId:topicName`
- Invalidate on permission.granted/permission.revoked events
- Periodic cache refresh for missed events

**Acceptance Criteria:**
- [ ] Can rebuild permissions from events
- [ ] Caching implemented
- [ ] Cache invalidation on events
- [ ] Effective permission calculation
- [ ] Unit tests for projection logic
- [ ] Integration tests with real events

---

#### Task 4.3: Permission Management Services

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/permission/GrantPermissionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/permission/RevokePermissionService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/permission/GetPermissionsService.kt` (new)

**Implementation:**

- Grant permission: validates principal and resource exist, publishes `permission.granted`
- Revoke permission: publishes `permission.revoked`
- Get permissions: uses projection service to calculate effective permissions

**Acceptance Criteria:**
- [ ] All permission operations implemented
- [ ] Validates principal and resource exist
- [ ] Unit tests for each service
- [ ] Integration tests for grant/revoke

---

#### Task 4.4: Authorization Service

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/auth/AuthorizationService.kt` (new)

**Implementation:**

```kotlin
class AuthorizationService(
    private val permissionProjectionService: PermissionProjectionService
) {
    suspend fun checkPermission(
        principalId: String,
        tenantId: String,
        namespaceId: String? = null,
        topicName: String? = null,
        requiredPermission: Permission
    ): Boolean {
        val permissions = permissionProjectionService.getPermissions(
            principalId, tenantId, namespaceId, topicName
        )
        return permissions.contains(requiredPermission) || 
               permissions.contains(getAdminPermission(requiredPermission))
    }
    
    private fun getAdminPermission(permission: Permission): Permission? {
        // Returns admin permission that grants this permission
        // e.g., TENANT_ADMIN grants all tenant:* permissions
    }
}
```

**Acceptance Criteria:**
- [ ] Checks permissions correctly
- [ ] Handles permission inheritance
- [ ] Handles admin permissions
- [ ] Unit tests for authorization logic
- [ ] Integration tests for various permission scenarios

---

#### Task 4.5: Authorization Middleware

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/middleware/AuthorizationMiddleware.kt` (new)

**Implementation:**

- Extracts tenant/namespace/topic from URL path
- Gets user from authentication middleware
- Determines required permission from HTTP method and route
- Checks permission using authorization service
- Returns 403 if permission denied
- Stores tenant/namespace context in call attributes

**Permission mapping:**
- `POST /tenants` → `TENANT_CREATE`
- `GET /tenants` → `TENANT_LIST`
- `GET /tenants/{id}` → `TENANT_READ`
- `PUT /tenants/{id}` → `TENANT_UPDATE`
- `DELETE /tenants/{id}` → `TENANT_DELETE`
- etc.

**Acceptance Criteria:**
- [ ] Extracts context from URL path
- [ ] Maps HTTP methods to permissions
- [ ] Checks permissions correctly
- [ ] Returns 403 for denied requests
- [ ] Can be bypassed for public endpoints
- [ ] Integration tests for middleware

---

#### Task 4.6: Update All Routes for Authorization

**Files to modify:**
- All route files to add authorization middleware

**Changes:**
- Add authorization middleware to all routes
- Remove any existing permission checks (replace with middleware)
- Ensure tenant/namespace context is available

**Acceptance Criteria:**
- [ ] All routes protected by authorization
- [ ] Tenant/namespace context available in routes
- [ ] Integration tests verify authorization works
- [ ] Can still access with proper permissions

---

#### Task 4.7: Permission API Routes

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/routes/permissionRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/PermissionDto.kt` (new)

**Endpoints:**
- `GET /tenants/{tenantId}/users/{userId}/permissions` - Get effective permissions
- `POST /tenants/{tenantId}/users/{userId}/permissions` - Grant permissions
- `DELETE /tenants/{tenantId}/users/{userId}/permissions` - Revoke permissions

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper error handling
- [ ] Integration tests for all endpoints

---

#### Task 4.8: Update Bootstrap for Default Permissions

**Files to modify:**
- `src/main/kotlin/com/eventstore/infrastructure/bootstrap/BootstrapService.kt`

**Changes:**
- Grant default admin user all permissions in `$system` tenant
- Publish `permission.granted` events

**Acceptance Criteria:**
- [ ] Default admin has all permissions
- [ ] Permissions granted via events
- [ ] Integration test for bootstrap with permissions

---

#### Task 4.9: Testing

**Test files to create:**
- `src/test/kotlin/com/eventstore/infrastructure/projections/PermissionProjectionServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/permission/GrantPermissionServiceTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/auth/AuthorizationServiceTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/middleware/AuthorizationMiddlewareTest.kt`

**Acceptance Criteria:**
- [ ] Unit tests for all new services
- [ ] Integration tests for permission grant/revoke
- [ ] Integration tests for authorization checks
- [ ] Integration tests for permission inheritance
- [ ] All tests pass

---

### Phase 1D Deliverables

- [ ] Permission domain model and events defined
- [ ] Permission projection service with caching implemented
- [ ] Permission management services implemented
- [ ] Authorization service implemented
- [ ] Authorization middleware implemented
- [ ] All routes updated for authorization
- [ ] Permission API routes implemented
- [ ] Default permissions for admin user
- [ ] All tests passing
- [ ] Documentation updated

---

## Phase 1E: API Key Management

**Timeline**: Week 5-6 (10 working days)  
**Goal**: Implement API key management for programmatic access.

### Objectives

1. Implement API key domain model
2. Create API key storage (can be file-based or event-sourced)
3. Implement API key management operations
4. Implement API key authentication
5. Update authorization to support API keys

### Tasks

#### Task 5.1: API Key Domain Model

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/ApiKey.kt` (new)

**Implementation:**

```kotlin
data class ApiKey(
    val id: String,
    val userId: String,
    val keyHash: String, // Hashed API key (never store plain key)
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val scopes: Set<String>? = null // Optional scoping
) {
    val isActive: Boolean
        get() = revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()))
}
```

**Note**: API keys can be stored file-based initially (simpler), or event-sourced in `$system/$management/api-keys` topic.

**Acceptance Criteria:**
- [ ] API key domain model defined
- [ ] Key hashing strategy defined (SHA-256 or bcrypt)
- [ ] Unit tests for domain model

---

#### Task 5.2: API Key Repository

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/ports/outbound/ApiKeyRepository.kt` (new)
- `src/main/kotlin/com/eventstore/infrastructure/persistence/FileSystemApiKeyRepository.kt` (new)

**Implementation:**

- Store API keys in `config/api-keys.json` (file-based)
- Hash API keys before storage
- Support create, read, update, delete operations

**Acceptance Criteria:**
- [ ] API key storage implemented
- [ ] Keys are hashed before storage
- [ ] Can create, read, update, delete API keys
- [ ] Unit tests for repository
- [ ] Integration tests for storage

---

#### Task 5.3: API Key Management Services

**Files to create:**
- `src/main/kotlin/com/eventstore/domain/services/apikey/CreateApiKeyService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/apikey/GetApiKeyService.kt` (new)
- `src/main/kotlin/com/eventstore/domain/services/apikey/RevokeApiKeyService.kt` (new)

**Implementation:**

- Create API key: generates random key, hashes it, stores it, returns plain key once
- Get API key: returns API key metadata (never returns plain key)
- Revoke API key: marks as revoked

**Key generation:**
- Format: `es_` + 32 random characters (base64)
- Example: `es_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`

**Acceptance Criteria:**
- [ ] Can create API keys
- [ ] Plain key returned only once (on creation)
- [ ] Can revoke API keys
- [ ] Unit tests for each service
- [ ] Integration tests for full lifecycle

---

#### Task 5.4: API Key Authentication

**Files to create:**
- `src/main/kotlin/com/eventstore/infrastructure/auth/ApiKeyAuthenticator.kt` (new)

**Implementation:**

- Extracts API key from Authorization header: `Authorization: Bearer es_...`
- Hashes the provided key
- Looks up API key by hash
- Returns user associated with API key
- Updates `lastUsedAt` timestamp

**Acceptance Criteria:**
- [ ] Extracts API key from header
- [ ] Validates API key
- [ ] Returns associated user
- [ ] Updates last used timestamp
- [ ] Unit tests for authenticator
- [ ] Integration tests for authentication flow

---

#### Task 5.5: Update Authentication Middleware

**Files to modify:**
- `src/main/kotlin/com/eventstore/interfaces/http/middleware/AuthenticationMiddleware.kt`

**Changes:**
- Support both session-based and API key authentication
- Check for API key first, then session
- Store principal (user or API key) in call attributes

**Acceptance Criteria:**
- [ ] Supports both session and API key auth
- [ ] API key takes precedence
- [ ] Integration tests for both auth methods

---

#### Task 5.6: Update Authorization for API Keys

**Files to modify:**
- `src/main/kotlin/com/eventstore/domain/services/auth/AuthorizationService.kt`
- `src/main/kotlin/com/eventstore/infrastructure/projections/PermissionProjectionService.kt`

**Changes:**
- API keys inherit permissions from associated user
- Support API key-specific permissions (if scopes are used)

**Acceptance Criteria:**
- [ ] API keys inherit user permissions
- [ ] Can have API key-specific permissions
- [ ] Integration tests for API key authorization

---

#### Task 5.7: API Key API Routes

**Files to create:**
- `src/main/kotlin/com/eventstore/interfaces/http/routes/apiKeyRoutes.kt` (new)
- `src/main/kotlin/com/eventstore/interfaces/http/dto/ApiKeyDto.kt` (new)

**Endpoints:**
- `POST /tenants/{tenantId}/users/{userId}/api-keys` - Create API key
- `GET /tenants/{tenantId}/users/{userId}/api-keys` - List API keys for user
- `GET /tenants/{tenantId}/users/{userId}/api-keys/{keyId}` - Get API key
- `DELETE /tenants/{tenantId}/users/{userId}/api-keys/{keyId}` - Revoke API key

**Acceptance Criteria:**
- [ ] All endpoints implemented
- [ ] Proper error handling
- [ ] Integration tests for all endpoints

---

#### Task 5.8: Testing

**Test files to create:**
- `src/test/kotlin/com/eventstore/infrastructure/persistence/FileSystemApiKeyRepositoryTest.kt`
- `src/test/kotlin/com/eventstore/domain/services/apikey/CreateApiKeyServiceTest.kt`
- `src/test/kotlin/com/eventstore/infrastructure/auth/ApiKeyAuthenticatorTest.kt`
- `src/test/kotlin/com/eventstore/interfaces/http/routes/ApiKeyRoutesTest.kt`

**Acceptance Criteria:**
- [ ] Unit tests for all new services
- [ ] Integration tests for API key creation
- [ ] Integration tests for API key authentication
- [ ] Integration tests for API key authorization
- [ ] All tests pass

---

### Phase 1E Deliverables

- [ ] API key domain model defined
- [ ] API key repository implemented
- [ ] API key management services implemented
- [ ] API key authentication implemented
- [ ] Authorization updated for API keys
- [ ] API key API routes implemented
- [ ] All tests passing
- [ ] Documentation updated

---

## Testing Strategy

### Unit Tests

- All domain services
- All projection services
- All repositories
- All middleware
- Domain models and validation

### Integration Tests

- Bootstrap process
- Tenant CRUD operations
- Namespace CRUD operations
- User management and authentication
- Permission grant/revoke
- Authorization checks
- API key lifecycle
- End-to-end flows

### Test Coverage Requirements

- Minimum 80% code coverage
- All critical paths must be tested
- All error cases must be tested

---

## Migration Strategy

### Existing Data Migration

1. **Bootstrap default tenant/namespace**
   - Create `default` tenant
   - Create `default` namespace in default tenant
   - All via events

2. **Migrate existing topics**
   - Move topics to `default/default/{topicName}`
   - Update topic storage paths
   - Update event storage paths

3. **Migrate existing events**
   - Update event IDs to new format
   - Update event file paths
   - Migration can be done incrementally

4. **Update API clients**
   - Provide migration guide
   - Support both old and new endpoints during transition
   - Deprecate old endpoints after migration period

### Rollout Strategy

1. **Feature flags enabled, but system works without them**
   - System continues to work with old APIs
   - New APIs available but optional

2. **Gradual migration**
   - Migrate topics one at a time
   - Test each migration
   - Rollback capability

3. **Deprecation period**
   - Old endpoints marked as deprecated
   - Documentation updated
   - Migration guide provided

4. **Remove old endpoints**
   - After migration period (e.g., 3 months)
   - Remove legacy endpoints
   - Update all clients

---

## Documentation Requirements

### Code Documentation

- All public APIs documented
- Complex logic explained
- Architecture decisions documented

### User Documentation

- API documentation updated
- Migration guide
- Authentication guide
- Permission model guide

### Developer Documentation

- Architecture overview
- Projection service patterns
- Event sourcing patterns
- Testing patterns

---

## Risk Mitigation

### Technical Risks

1. **Bootstrap complexity**
   - **Risk**: Chicken-and-egg problem with `$system` tenant
   - **Mitigation**: Special bootstrap service that bypasses normal validation

2. **Performance of projections**
   - **Risk**: Rebuilding state from events on every request
   - **Mitigation**: Caching with invalidation on events

3. **Migration complexity**
   - **Risk**: Migrating existing data is complex
   - **Mitigation**: Incremental migration, rollback capability

4. **Breaking changes**
   - **Risk**: Changes break existing clients
   - **Mitigation**: Backward compatibility, feature flags, deprecation period

### Process Risks

1. **Timeline pressure**
   - **Risk**: Phases take longer than estimated
   - **Mitigation**: Prioritize critical features, can defer non-critical

2. **Testing coverage**
   - **Risk**: Insufficient testing
   - **Mitigation**: Test coverage requirements, code reviews

---

## Success Criteria

### Phase 1A Success

- [ ] Bootstrap service works correctly
- [ ] Tenants can be created and managed
- [ ] System works with feature flags disabled (backward compatible)
- [ ] All tests passing

### Phase 1B Success

- [ ] Namespaces can be created and managed
- [ ] Topics work with tenant/namespace structure
- [ ] Existing topics migrated successfully
- [ ] All tests passing

### Phase 1C Success

- [ ] Users can be created and managed
- [ ] Authentication works (login/logout)
- [ ] Users can be assigned to tenants
- [ ] All tests passing

### Phase 1D Success

- [ ] Permissions can be granted and revoked
- [ ] Authorization middleware works correctly
- [ ] All routes protected by authorization
- [ ] All tests passing

### Phase 1E Success

- [ ] API keys can be created and managed
- [ ] API key authentication works
- [ ] API keys inherit user permissions
- [ ] All tests passing

### Overall Success

- [ ] All critical features implemented (CRIT-001 through CRIT-004)
- [ ] System is production-ready
- [ ] Documentation complete
- [ ] Migration guide available
- [ ] All tests passing
- [ ] Code coverage > 80%

---

## Appendix

### Event Schema Examples

See `TENANT_NAMESPACE_REQUIREMENTS.md` for complete event schema definitions.

### API Endpoint Summary

See `TENANT_NAMESPACE_REQUIREMENTS.md` for complete API endpoint definitions.

### File Structure

```
event-store/
├── src/main/kotlin/com/eventstore/
│   ├── domain/
│   │   ├── Tenant.kt
│   │   ├── Namespace.kt
│   │   ├── User.kt
│   │   ├── Permission.kt
│   │   ├── ApiKey.kt
│   │   ├── events/
│   │   │   ├── TenantEvents.kt
│   │   │   ├── NamespaceEvents.kt
│   │   │   ├── UserEvents.kt
│   │   │   └── PermissionEvents.kt
│   │   ├── services/
│   │   │   ├── tenant/          # Tenant lifecycle services
│   │   │   ├── namespace/       # Namespace lifecycle services
│   │   │   ├── topic/           # Topic lifecycle services
│   │   │   ├── event/           # Event publish/query services
│   │   │   ├── consumer/        # Consumer registration services
│   │   │   ├── user/            # User lifecycle services
│   │   │   ├── permission/      # Permission management services
│   │   │   ├── auth/            # AuthN/AuthZ services
│   │   │   └── apikey/          # API key services
│   │   └── ports/outbound/
│   │       ├── TenantRepository.kt
│   │       ├── NamespaceRepository.kt
│   │       ├── UserRepository.kt
│   │       ├── PermissionRepository.kt
│   │       └── ApiKeyRepository.kt
│   ├── infrastructure/
│   │   ├── bootstrap/
│   │   │   └── BootstrapService.kt
│   │   ├── projections/
│   │   │   ├── TenantProjectionService.kt
│   │   │   ├── NamespaceProjectionService.kt
│   │   │   ├── UserProjectionService.kt
│   │   │   └── PermissionProjectionService.kt
│   │   ├── persistence/
│   │   │   └── FileSystemApiKeyRepository.kt
│   │   ├── auth/
│   │   │   ├── SessionManager.kt
│   │   │   └── ApiKeyAuthenticator.kt
│   │   └── migration/
│   │       └── TopicMigrationService.kt
│   └── interfaces/http/
│       ├── routes/
│       │   ├── tenantRoutes.kt
│       │   ├── namespaceRoutes.kt
│       │   ├── userRoutes.kt
│       │   ├── authRoutes.kt
│       │   ├── permissionRoutes.kt
│       │   └── apiKeyRoutes.kt
│       ├── middleware/
│       │   ├── AuthenticationMiddleware.kt
│       │   └── AuthorizationMiddleware.kt
│       └── dto/
│           ├── TenantDto.kt
│           ├── NamespaceDto.kt
│           ├── UserDto.kt
│           ├── AuthDto.kt
│           ├── PermissionDto.kt
│           └── ApiKeyDto.kt
```

---

**Document End**

