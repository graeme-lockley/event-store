# Service Review: BaseSystemService Eligibility

## Criteria for BaseSystemService
A service should use `BaseSystemService` if it:
1. **Publishes events to system topics** (`$system/$management/*`)
2. **Requires multi-tenant mode check** (`requireMultiTenantEnabled()`)
3. **Manages system-level entities** (tenants, namespaces, users, permissions)

## Services Currently Using BaseSystemService ✅

### Tenant Services
- ✅ `CreateTenantService` - Publishes to `$system/$management/tenants`
- ✅ `UpdateTenantService` - Publishes to `$system/$management/tenants`
- ✅ `DeleteTenantService` - Publishes to `$system/$management/tenants`

### Namespace Services
- ✅ `CreateNamespaceService` - Publishes to `$system/$management/namespaces`
- ✅ `UpdateNamespaceService` - Publishes to `$system/$management/namespaces`
- ✅ `DeleteNamespaceService` - Publishes to `$system/$management/namespaces`

### Permission Services
- ✅ `GrantPermissionService` - Publishes to `$system/$management/permissions`
- ✅ `RevokePermissionService` - Publishes to `$system/$management/permissions`

### User Services
- ✅ `CreateUserService` - Publishes to `$system/$management/users`
- ✅ `UpdateUserService` - Publishes to `$system/$management/users`
- ✅ `DeleteUserService` - Publishes to `$system/$management/users`
- ✅ `AssignUserToTenantService` - Publishes to `$system/$management/users`
- ✅ `RemoveUserFromTenantService` - Publishes to `$system/$management/users`
- ✅ `ChangePasswordService` - Publishes to `$system/$management/users`

## Services That Should NOT Use BaseSystemService ❌

### Query Services (CQRS Query Side - No State Changes)
These services only read data and don't publish events, so they don't need `BaseSystemService`.

#### Projection Query Services (Query Read Models)
- ❌ `GetTenantService` - Queries `TenantProjectionService`
- ❌ `GetNamespaceService` - Queries `NamespaceProjectionService`
- ❌ `GetUserService` - Queries `UserProjectionService`
- ❌ `GetPermissionsService` - Queries `PermissionProjectionService`

#### Repository Query Services (Query Aggregates Directly)
- ❌ `GetApiKeyService` - Queries `ApiKeyRepository`
- ❌ `GetTopicsService` - Queries `TopicRepository`

#### Event Store Query Services (Query Raw Events)
- ❌ `GetEventsService` - Queries `EventRepository` directly

#### Infrastructure Query Services
- ❌ `GetHealthStatusService` - Queries `ConsumerRepository` and dispatcher status

### Command Services (CQRS Command Side - State Changes)
These services modify state but don't publish to system topics, so they don't need `BaseSystemService`.

#### Domain Command Services (User Topics, Not System Topics)
- ❌ `CreateTopicService` - Creates user topics (not system topics), works in both single/multi-tenant
- ❌ `UpdateTopicSchemasService` - Updates user topics (not system topics)
- ❌ `PublishEventsService` - Publishes to user topics (not system topics)

#### Repository-Based Command Services (No Event Sourcing)
- ❌ `CreateApiKeyService` - Stores in `ApiKeyRepository`, **does not publish events**
- ❌ `RevokeApiKeyService` - Stores in `ApiKeyRepository`, **does not publish events**

#### Infrastructure Command Services (No Events)
- ❌ `RegisterConsumerService` - Manages consumers in `ConsumerRepository`, no events published
- ❌ `UnregisterConsumerService` - Manages consumers in `ConsumerRepository`, no events published

### Application Services (Business Logic, No Events)
- ❌ `AuthenticationService` - Authentication logic, no events published
- ❌ `AuthorizationService` - Authorization logic, no events published

## Potential Future Considerations 🤔

### API Key Services
**Current State:** `CreateApiKeyService` and `RevokeApiKeyService` do NOT publish events - they only store in repository.

**Question:** Should API key lifecycle be event-sourced? If so:
- Would need to publish to `$system/$management/api-keys` topic
- Would need schemas for `api-key.created` and `api-key.revoked` events
- Would need `ApiKeyProjectionService` to consume events
- **Then** they should use `BaseSystemService`

**Recommendation:** Keep as-is unless there's a requirement for event sourcing API key lifecycle.

## Summary

**Total Services Reviewed:** 35

### By CQRS Pattern
- **Command Services (State Changes):** 21
  - System Commands (use BaseSystemService): 14 ✅
  - Domain/Infrastructure Commands (don't use BaseSystemService): 7 ❌
- **Query Services (Read-Only):** 8 ❌
- **Application Services (Business Logic):** 2 ❌

### By BaseSystemService Usage
- **Using BaseSystemService:** 14 ✅ (All system command services)
- **Correctly NOT using BaseSystemService:** 21 ❌
- **Status:** All services are correctly categorized!

### Key Insight
`BaseSystemService` is specifically for **system command services** that:
1. Publish events to `$system/$management/*` topics
2. Require multi-tenant mode enforcement
3. Manage system infrastructure entities

All other services (queries, domain commands, infrastructure commands, application logic) correctly do NOT use it.

