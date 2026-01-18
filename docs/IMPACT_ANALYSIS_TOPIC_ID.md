# Impact Analysis: Using TopicId Instead of Topic Name

## Proposed Changes

1. **Rename `resourceId` to `topicId`** - `topicId` replaces `resourceId` (same field, renamed)
2. **Rename `namespaceResourceId` to `namespaceId`** - Simpler naming convention
3. **Remove `tenantResourceId` from Topic model** - Not needed, topicId is globally unique
4. **Remove `tenantName` and `namespaceName` from Topic model** - Not needed for internal operations
5. **Replace `topicResourceId` with `topicId`** - In permission contexts, use `topicId` instead of `topicResourceId`
6. **Topic lookup pattern changes** - No more triple `(name, tenantName, namespaceName)`, just `topicId: UUID` (globally unique)
7. **EventId format changes to `topicId/sequence`** - Simplified format, removing tenant and namespace components
8. **API design changes to UUID-based URLs** - URLs use `topicId` UUIDs directly
9. **No backward compatibility** - Only support the new format, no legacy format support
10. **Breaking changes acceptable** - No migration needed, all changes can be breaking

## Current Architecture

### Topic Model (Current)
```kotlin
data class Topic(
    val resourceId: UUID,              // ❌ Will be renamed to topicId
    val namespaceResourceId: UUID,     // ✅ Already globally unique (stays as is)
    val tenantResourceId: UUID,        // Stays as is
    val name: String,                  // Stays for display/URL purposes, but not used for lookup
    val sequence: Long,
    val schemas: List<Schema>,
    val tenantName: String,
    val namespaceName: String
)
```

### Topic Model (Proposed)
```kotlin
data class Topic(
    val topicId: UUID,                 // ✅ Renamed from resourceId - globally unique
    val namespaceId: UUID,             // ✅ Renamed from namespaceResourceId - globally unique
    val name: String,                  // For display only, optional for internal operations
    val sequence: Long,
    val schemas: List<Schema>
)
```

**Key Simplifications:**
- ✅ Removed `tenantResourceId` - not needed (topicId is globally unique)
- ✅ Renamed `namespaceResourceId` to `namespaceId` - simpler naming
- ✅ Removed `tenantName` and `namespaceName` - not needed for internal operations
- ✅ Removed `qualifiedName()` - no longer needed

### New Topic Lookup Pattern
- Topics are identified by `topicId: UUID` only (globally unique)
- No more triple `(name, tenantName, namespaceName)` lookup
- All repository methods use single `topicId: UUID` parameter
- Simplified and more efficient lookups

---

## Impact Areas

### 1. **Domain Model Changes**

**File:** `event-store/src/main/kotlin/com/eventstore/domain/Topic.kt`

**Impact:**
- Rename `resourceId: UUID` to `topicId: UUID` (same field, different name)
- Rename `namespaceResourceId: UUID` to `namespaceId: UUID`
- Remove `tenantResourceId: UUID` field (not needed)
- Remove `tenantName: String` and `namespaceName: String` fields (not needed)
- Remove `qualifiedName()` method (no longer needed)
- Remove validation for `tenantName` and `namespaceName` from `init` block
- Update all references throughout codebase

**Simplified Model:**
```kotlin
data class Topic(
    val topicId: UUID,
    val namespaceId: UUID,
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>
) {
    init {
        require(name.isNotBlank()) { "Topic name is required" }
        require(sequence >= 0) { "Sequence must be non-negative" }
        // No tenantName/namespaceName validation needed
    }
}
```

**Lines Affected:** 15-43 (entire model - significant simplification)

---

### 2. **TopicRepository Interface**

**File:** `event-store/src/main/kotlin/com/eventstore/domain/ports/outbound/TopicRepository.kt`

**Impact:** 🔴 **CRITICAL** - All method signatures must change

**Current Methods Using `name` Parameter:**
```kotlin
suspend fun getTopic(name: String, tenantName: String, namespaceName: String): Topic?
suspend fun topicExists(name: String, tenantName: String, namespaceName: String): Boolean
suspend fun updateSequence(name: String, sequence: Long, tenantName: String, namespaceName: String)
suspend fun getAndIncrementSequence(topicName: String, tenantName: String, namespaceName: String): Long
suspend fun updateSchemas(name: String, schemas: List<Schema>, tenantName: String, namespaceName: String): Topic
```

**Proposed Methods (Simplified):**
```kotlin
suspend fun getTopic(topicId: UUID): Topic?
suspend fun topicExists(topicId: UUID): Boolean
suspend fun updateSequence(topicId: UUID, sequence: Long)
suspend fun getAndIncrementSequence(topicId: UUID): Long
suspend fun updateSchemas(topicId: UUID, schemas: List<Schema>): Topic
suspend fun createTopic(
    topicId: UUID,
    namespaceId: UUID,
    name: String,
    schemas: List<Schema>
): Topic
```

**Key Changes:**
- All methods use single `topicId: UUID` parameter (globally unique)
- No more `tenantName` and `namespaceName` parameters needed
- `createTopic` method signature simplified (removes tenantResourceId, tenantName, namespaceName)
- Much simpler interface - 1 parameter instead of 3

**Lines Affected:** 11-54 (all interface methods)

---

### 3. **Repository Implementations**

#### 3a. InMemoryTopicRepository
**File:** `event-store/src/main/kotlin/com/eventstore/infrastructure/persistence/InMemoryTopicRepository.kt`

**Impact:**
- Change internal storage key from `"$tenantName/$namespaceName/$name"` to `topicId: UUID`
- Update all CRUD operations to use `topicId: UUID` as the key (single parameter)
- Remove all `tenantName` and `namespaceName` parameters from method signatures
- Internal map becomes: `Map<UUID, Topic>` instead of `Map<String, Topic>`
- Remove `key(name, tenantName, namespaceName)` helper method
- No migration needed - breaking changes acceptable

**Simplified Implementation:**
```kotlin
private val topics = mutableMapOf<UUID, Topic>()  // topicId -> Topic

override suspend fun getTopic(topicId: UUID): Topic? {
    return mutex.withLock {
        topics[topicId]
    }
}
```

**Lines Affected:** 11-103 (entire implementation)

#### 3b. FileSystemTopicRepository
**File:** `event-store/src/main/kotlin/com/eventstore/infrastructure/persistence/FileSystemTopicRepository.kt`

**Impact:**
- Change file naming/storage structure to use `topicId: UUID`
- Update `TopicConfig` data class:
  - Rename `resourceId` to `topicId`
  - Rename `namespaceResourceId` to `namespaceId`
  - Remove `tenantResourceId` field
  - Remove `tenantId` and `namespaceId` string fields (keep only UUID)
- File naming: Use `topicId` UUID as filename instead of qualified name
- Storage structure simplified significantly
- No migration needed - breaking changes acceptable

**Simplified TopicConfig:**
```kotlin
data class TopicConfig(
    val topicId: String,          // UUID as string
    val namespaceId: String,      // UUID as string
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>
)
```

**Lines Affected:** ~33-350+ (entire implementation + storage format)

---

### 4. **Service Layer Changes**

#### 4a. CreateTopicService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/topic/CreateTopicService.kt`

**Impact:**
- Generate `topicId` (UUID) during topic creation (same as current `resourceId` generation)
- Method signature changes: Remove `tenantName` and `namespaceName` parameters
- Need `namespaceId: UUID` parameter to validate namespace exists
- Uniqueness check: UUIDs are globally unique by definition, no name-based check needed
- Update `topicExists()` to use `topicId` instead of triple lookup
- Schema validator registration needs to use `topicId` instead of topic name
- Remove tenant/namespace resolution logic (just validate namespaceId exists)

**Simplified Method:**
```kotlin
suspend fun execute(
    name: String,
    schemas: List<Schema>,
    namespaceId: UUID  // Only namespaceId needed, not tenant/namespace names
): Topic
```

**Lines Affected:** 18-66 (entire execute method)

#### 4b. GetTopicsService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/topic/GetTopicsService.kt`

**Impact:**
- Change `get(topicName: String, ...)` to `get(topicId: UUID)` (single parameter)
- List method can filter by `namespaceId: UUID` if namespace filtering needed
- Update exception messages to reference `topicId` instead of topic name
- Remove all `tenantName` and `namespaceName` parameters from methods

**Simplified Methods:**
```kotlin
suspend fun get(topicId: UUID): Topic
suspend fun list(namespaceId: UUID? = null): List<Topic>  // Optional namespace filter
```

**Lines Affected:** 10-15 (all methods)

#### 4c. UpdateTopicSchemasService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/topic/UpdateTopicSchemasService.kt`

**Impact:**
- Change method signature from `topicName: String, tenantName, namespaceName` to `topicId: UUID` (single parameter)
- Update repository calls to use `topicId` only
- Update schema validator registration to use `topicId` instead of topic name

**Lines Affected:** 13-54 (entire execute method)

---

### 5. **Event Management Services**

#### 5a. PublishEventsService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/event/PublishEventsService.kt`

**Impact:**
- `EventRequest` currently uses `topic: String, tenantId: String, namespaceId: String`
- Change to `topicId: UUID` (single field, removes tenant/namespace strings)
- Update `topicExists()` to use `topicId: UUID` (single parameter)
- Update `getAndIncrementSequence()` to use `topicId: UUID` (single parameter)
- `EventId.create()` signature: `create(topicId: UUID, sequence: Long)` (already simplified)
- Event dispatcher notification uses `topicId` instead of topic names

**Simplified EventRequest:**
```kotlin
data class EventRequest(
    val topicId: UUID,        // Single UUID field
    val type: String,
    val payload: Map<String, Any>
    // Removed: tenantId, namespaceId (String fields)
)
```

**Lines Affected:** 13-74 (entire execute method)

#### 5b. GetEventsService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/event/GetEventsService.kt`

**Impact:**
- Change `topic: String, tenantName, namespaceName` parameters to `topicId: UUID` (single parameter)
- Update `topicExists()` check to use `topicId` only
- Event repository `getEvents()` signature: change `topic: String` to `topicId: UUID`
- Remove `tenantId` and `namespaceId` String parameters (no longer needed)
- Since EventId format simplified, event retrieval by topicId is straightforward

**Simplified Method:**
```kotlin
suspend fun execute(
    topicId: UUID,
    sinceEventId: String? = null,
    date: String? = null,
    limit: Int? = null
): List<Event>
```

**Lines Affected:** 13-37 (entire execute method)

---

### 6. **EventId Structure**

**File:** `event-store/src/main/kotlin/com/eventstore/domain/EventId.kt`

**Current:**
```kotlin
data class EventId(
    val tenantId: String,      // Human-readable name
    val namespaceId: String,   // Human-readable name
    val topicId: String,       // Human-readable name ❌
    val sequence: Long
)
// Format: "tenant/namespace/topic/sequence"
// Example: "default/default/user-events/42"
```

**Proposed:**
```kotlin
data class EventId(
    val topicId: UUID,         // ✅ UUID directly, no tenant/namespace needed
    val sequence: Long
)
// Format: "topicId/sequence"
// Example: "550e8400-e29b-41d4-a716-446655440000/42"
```

**Impact:** 🔴 **CRITICAL** - Simplified structure, breaking change

**Key Changes:**
- Remove `tenantId` and `namespaceId` fields (no longer needed)
- Change `topicId` from `String` to `UUID`
- Simplified format: `"<topicId>/<sequence>"` (only 2 components instead of 4)
- Event ID parsing logic simplified (only need to split once)
- `toString()` format changes from 4-part to 2-part string

**Format Examples:**
- Current: `"default/default/user-events/42"` (4 components)
- Proposed: `"550e8400-e29b-41d4-a716-446655440000/42"` (2 components)

**Storage Impact:**
- Event repository key generation simplified (only needs `topicId`)
- No migration needed - breaking changes acceptable
- `EventId.fromString()` parsing logic simplified (split by "/" once instead of twice)

**Lines Affected:** 8-67 (entire EventId class - significant simplification)

---

### 7. **Event Repository**

**Files:** 
- `InMemoryEventRepository.kt`
- `FileSystemEventRepository.kt`

**Impact:**
- `topicKey()` method currently uses topic name string
- Event storage keys need to use `topicId: UUID`
- Migration of all stored events
- `getEvents()` method signature changes

**Key Changes:**
```kotlin
// Current
private fun topicKey(topic: String, tenantId: String?, namespaceId: String?, eventId: EventId?): String
// Returns: "$tenantName/$namespaceName/$topicName"

// Proposed
private fun topicKey(topicId: UUID, eventId: EventId?): String
// Returns: topicId.toString() (UUID string representation)
// Or: eventId?.topicId?.toString() if eventId is provided
```

**Simplification:**
- EventId now contains only `topicId` and `sequence`, so topic key is just the `topicId`
- No need for tenant/namespace in storage key
- Storage structure simplified significantly

**Lines Affected:** Significant portions of both implementations, but simplified

---

### 8. **Consumer Management**

#### 8a. RegisterConsumerService
**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/consumer/RegisterConsumerService.kt`

**Impact:**
- `ConsumerRegistrationRequest.topics` currently: `Map<String, String?>` (topicName -> lastEventId)
- Needs to change to: `Map<UUID, String?>` (topicId -> lastEventId)
- Topic validation uses `topicExists(topicId: UUID)` instead of name-based lookup
- Consumer registration simplified - no need to construct qualified names

**Consumer Model:**
**File:** `event-store/src/main/kotlin/com/eventstore/domain/Consumer.kt`

```kotlin
// Current
abstract class Consumer(
    val topics: Map<String, String?> // topic name -> lastEventId
)

// Proposed
abstract class Consumer(
    val topics: Map<UUID, String?> // topicId -> lastEventId
)
```

**Impact:**
- Consumer storage/retrieval uses `topicId: UUID` as keys
- Event dispatcher uses `topicId: UUID` instead of topic names
- Consumer update methods use `topicId` instead of topic name
- No migration needed - breaking changes acceptable

**Lines Affected:** 
- RegisterConsumerService: 16-74
- Consumer: 9-11
- All Consumer implementations (HttpConsumer, InMemoryConsumer, etc.)

---

### 9. **Schema Validator**

**Interface:** `SchemaValidator`

**Impact:**
- Currently registers schemas by topic name: `registerSchemas(topicName: String, schemas)`
- Needs to use `topicId: UUID` instead
- Validation method `validateEvent(topic: String, ...)` needs update

**Search Pattern:** `schemaValidator.registerSchemas` and `schemaValidator.validateEvent`

**Affected Locations:**
- CreateTopicService:62
- UpdateTopicSchemasService:51
- PublishEventsService:45

---

### 10. **HTTP API & Routes**

**File:** `event-store/src/main/kotlin/com/eventstore/interfaces/http/routes/TopicRoutes.kt`

**Impact:** 🔴 **BREAKING API CHANGE**

**Current URL Pattern:**
```
GET /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topicName}
PUT /tenants/{tenantName}/namespaces/{namespaceName}/topics/{topicName}/schemas
```

**New URL Pattern (UUID-based):**
```
GET /topics/{topicId}
PUT /topics/{topicId}/schemas
POST /topics (body contains namespaceId, name, schemas)
GET /topics (query param: namespaceId for filtering)
```

**Key Changes:**
- ✅ Remove tenant/namespace from URL path (topicId is globally unique)
- ✅ Use UUID directly in path: `/topics/550e8400-e29b-41d4-a716-446655440000`
- ✅ No backward compatibility - only support UUID-based URLs
- ✅ Simpler, cleaner API design

**API Response Changes:**
- Topic responses must include `topicId` and `namespaceId` (UUIDs)
- Topic responses may optionally include `name` for display
- DTOs need update: `TopicResponse`, `TopicCreationRequest`
- Remove `tenantName` and `namespaceName` from DTOs (use UUIDs instead)

**Simplified DTOs:**
```kotlin
data class TopicResponse(
    val topicId: String,      // UUID as string
    val namespaceId: String,  // UUID as string
    val name: String,
    val sequence: Long,
    val schemas: List<SchemaDto>
)

data class TopicCreationRequest(
    val namespaceId: String,  // UUID as string (required)
    val name: String,         // Required
    val schemas: List<SchemaDto>
)
```

**Lines Affected:** 16-164 (entire route definitions)

---

### 11. **Event Routes**

**File:** `event-store/src/main/kotlin/com/eventstore/interfaces/http/routes/EventRoutes.kt`

**Impact:**
- Event publishing endpoints use `topicId: UUID` instead of topic names
- Event retrieval endpoints use `topicId: UUID` in path or query params
- Request DTOs simplified: remove `tenantId` and `namespaceId` String fields
- URL pattern: `/events?topicId={topicId}` or `/topics/{topicId}/events`

**Simplified Event Publishing:**
```kotlin
POST /topics/{topicId}/events
Body: { type: String, payload: Map<String, Any> }
```

**Simplified Event Retrieval:**
```kotlin
GET /topics/{topicId}/events?sinceEventId={eventId}&limit={n}
```

---

### 12. **Authorization & Permissions**

**File:** `event-store/src/main/kotlin/com/eventstore/domain/services/auth/ResourceResolver.kt`

**Current Flow:**
1. Receives `topicName: String` from URL/request
2. Resolves to `topic.resourceId: UUID` for permission checks
3. Uses `resourceId` (will become `topicId`) for permission matching

**Proposed Flow (Simplified):**
1. Receives `topicId: UUID` directly from URL path (API uses UUIDs)
2. Uses `topicId` directly for permission checks (no resolution needed)
3. Permission grants use `topicId` instead of `topicResourceId`
4. No `ResourceResolver.resolveTopicName()` needed for topics (UUID provided directly)

**Impact:**
- `ResourceResolver.resolveTopicName()` may be removed or deprecated (UUID provided directly)
- Permission model: Replace `topicResourceId: UUID?` with `topicId: UUID?`
- Authorization checks use `topicId` directly (simplified - no name resolution)
- Still need to validate topic exists (simple topicId lookup)

**Simplified Authorization:**
- If API provides `topicId` UUID in URL, no resolution step needed
- Direct permission check using `topicId`
- Much simpler authorization flow

**Files:**
- ResourceResolver:35-47 (may be removed if only UUIDs used)
- AuthorizationService:43-49 (simplified - direct topicId usage)
- GrantPermissionService:44-46 (use topicId instead of topicResourceId, no resolution)
- RevokePermissionService:44-46 (use topicId instead of topicResourceId)
- GetPermissionsService:28-30 (use topicId instead of topicResourceId)
- PermissionGrant/Permission model: Replace `topicResourceId` with `topicId`

---

### 13. **Event Dispatcher**

**Files:**
- `AsyncDispatcherManager.kt`
- `SyncDispatcherManager.kt`
- `TopicDispatcher.kt`

**Impact:**
- Dispatchers currently track topics by qualified name string: `"tenant/namespace/topic"`
- Change to use `topicId: UUID` as the registry key
- Consumer topic subscriptions use `topicId` instead of qualified names
- Dispatcher registry simplified: `Map<UUID, Dispatcher>` instead of `Map<String, Dispatcher>`

**Search Pattern:** `dispatcherManager.startDispatcher(topic.name)` → `dispatcherManager.startDispatcher(topic.topicId)`

**Affected Locations:**
- Application.kt:348
- RegisterConsumerService:70

---

### 14. **Application.kt Facade**

**File:** `event-store/src/main/kotlin/com/eventstore/domain/Application.kt`

**Impact:**
- All public methods using `topicName: String` need signature changes:
  - `createTopic(name: String, namespaceId: UUID, schemas: ...)` - simplified
  - `getTopic(topicId: UUID)` - single parameter
  - `listTopics(namespaceId: UUID? = null)` - optional namespace filter
  - `updateTopicSchemas(topicId: UUID, ...)` - single parameter
  - `publishEvents()` - EventRequest uses `topicId: UUID`
  - `getEvents(topicId: UUID, ...)` - single parameter
  - `registerConsumer()` - ConsumerRegistrationRequest uses `topicId: UUID`
- Remove all `tenantName` and `namespaceName` parameters from Application methods

**Lines Affected:** ~340-450 (multiple method signatures)

---

### 15. **Testing**

**Impact:** 🔴 **EXTENSIVE** - Nearly all tests affected

**Files to Update:**
- All `*ServiceTest.kt` files (30+ test files)
- `TopicRepositoryTest.kt`
- `FileSystemTopicRepositoryTest.kt`
- HTTP route integration tests
- Authorization tests
- Consumer registration tests

**Test Data:**
- All test fixtures using topic names
- Mock data and test helpers

**Estimated Test Files Affected:** 50+ test files

---

### 16. **CLI Client**

**File:** `cli/internal/client/client.go`

**Impact:**
- Go client uses topic names in API calls
- Topic struct and request/response types need update
- Client methods need refactoring

**Lines Affected:** 38-84, 422-486 (Topic struct and API methods)

---

### 17. **Admin UI**

**Files:**
- `admin-ui/client.ts`
- `admin-ui/routes/*.tsx`

**Impact:**
- TypeScript client interface needs update
- UI components displaying/selecting topics
- API route handlers

**Lines Affected:** Multiple TypeScript files

---

### 18. **Bootstrap Service**

**File:** `event-store/src/main/kotlin/com/eventstore/infrastructure/bootstrap/BootstrapService.kt`

**Impact:**
- System topic creation uses topic names - needs to change to use `topicId: UUID`
- Event publishing uses topic names - needs to change to use `topicId: UUID`
- Need to store system topic UUIDs (e.g., `SystemTopics.USERS_TOPIC_ID: UUID`) instead of names
- Update all system topic references to use UUIDs

**Lines Affected:** 25-250+ (system topic initialization)

---

### 19. **Documentation**

**Impact:**
- API documentation
- Business rules documents
- Architecture diagrams
- Usage examples

**Files:**
- `docs/API.md`
- `docs/Tenant and Namespace Requirements.md`
- `docs/business-rules/*.md`

---

## Key Decisions (Confirmed)

✅ **1. TopicId vs ResourceId:**
   - `topicId` is the same as `resourceId` - it's a rename, not a new field
   - All references to `resourceId` in Topic context become `topicId`
   - All references to `topicResourceId` in Permission context become `topicId`

✅ **2. Uniqueness:**
   - `topicId` is a UUID, so it's globally unique by definition
   - No need for namespace-scoped uniqueness checks
   - UUID generation ensures uniqueness across all topics

✅ **3. EventId Format:**
   - Format: `"topicId/sequence"` (simplified from `"tenant/namespace/topic/sequence"`)
   - Only 2 components: `topicId` (UUID) and `sequence` (Long)
   - String representation: `"550e8400-e29b-41d4-a716-446655440000/42"`

✅ **4. Topic Model Simplification:**
   - Remove `tenantResourceId` from Topic model
   - Rename `namespaceResourceId` to `namespaceId`
   - Remove `tenantName` and `namespaceName` from Topic model
   - Topic lookup uses only `topicId: UUID` (globally unique)

✅ **5. Topic Lookup Pattern:**
   - No more triple `(name, tenantName, namespaceName)` lookup
   - Single `topicId: UUID` parameter for all operations
   - Globally unique identifiers simplify all operations

✅ **6. API Design:**
   - UUID-based URLs: `/topics/{topicId}`
   - No backward compatibility - only support new format
   - Simplified API design

✅ **7. Migration:**
   - No migration needed - all changes are breaking changes
   - No requirement to process former event formats
   - Clean break from old structure

---

## Remaining Decision Points

✅ **API Design:**
   - URLs with UUIDs: `/topics/550e8400-e29b-41d4-a716-446655440000`
   - No backward compatibility - only support UUID-based URLs
   - Clean, simple API design with globally unique identifiers

---

## Summary of Impact

### High Impact Areas (🔴 Critical)
1. **TopicRepository Interface & Implementations** - Core lookup mechanism
2. **EventId Structure** - Affects all stored events
3. **Event Repository** - Event storage and retrieval
4. **Service Layer** - All topic-related services
5. **HTTP API** - Breaking changes to public interface
6. **Consumer Model** - Topic subscription storage

### Medium Impact Areas (🟡 Significant)
7. **Schema Validator** - Topic-based schema registration
8. **Event Dispatcher** - Topic tracking and routing
9. **Application Facade** - Public API methods
10. **Authorization** - Topic resolution for permissions
11. **CLI/Admin UI** - Client implementations

### Low Impact Areas (🟢 Minor)
12. **Documentation** - Updates needed but not blocking
13. **Testing** - Extensive but straightforward updates

### Estimated Effort (Revised - No Migration Needed)
- **Core Domain Changes:** 2-3 days
  - Topic model rename: `resourceId` → `topicId`
  - EventId simplification to `topicId/sequence`
- **Repository Implementations:** 3-4 days
  - Switch to `topicId`-based lookups
  - Simplified storage keys
- **Service Layer Updates:** 3-4 days
  - Update all method signatures
  - Simplify logic (no qualified names needed)
- **API & HTTP Layer:** 2-3 days
  - Decide on UUID vs name-based URLs
  - Update DTOs and routes
- **Event System Changes:** 3-4 days
  - EventId structure simplification
  - Simplified event storage/retrieval
  - No migration needed - saves 2-3 days
- **Consumer System:** 2-3 days
  - Update to use `topicId` in subscriptions
- **Permission System:** 1-2 days
  - Replace `topicResourceId` with `topicId`
- **Testing Updates:** 5-7 days
  - Update all tests (simpler due to no migration complexity)
- **CLI/Admin UI:** 2-3 days
  - Update to use `topicId`

**Total Estimated Effort:** 23-31 days (4.5-6 weeks for one developer)
*Note: Reduced from 25-38 days due to no migration requirements*

---

## Recommendations

1. **Implementation Approach:**
   - Since no migration is needed, can do a clean big-bang refactor
   - All breaking changes can be made simultaneously
   - Simplifies the codebase significantly

2. **Implementation Order:**
   - Phase 1: Domain Model Changes
     - Rename `resourceId` to `topicId` in Topic model
     - Rename `namespaceResourceId` to `namespaceId` in Topic model
     - Remove `tenantResourceId` from Topic model
     - Remove `tenantName` and `namespaceName` from Topic model
     - Simplify EventId to `topicId/sequence` format
     - Update Permission model: `topicResourceId` → `topicId`
   - Phase 2: Repository Layer
     - Update TopicRepository interface: single `topicId: UUID` parameters
     - Update TopicRepository implementations (simplified lookups)
     - Update EventRepository to use `topicId`-based keys
     - Update TopicConfig DTOs
   - Phase 3: Service Layer
     - Update all services: remove tenantName/namespaceName parameters
     - Use `topicId: UUID` for all topic operations
     - Simplify all lookup logic (no triple parameters)
   - Phase 4: Infrastructure
     - Update Event Dispatcher to use `topicId`
     - Update Schema Validator to use `topicId`
     - Update Consumer model to use `topicId: UUID` in topics map
     - Update system topics to use UUIDs
   - Phase 5: API & External Interfaces
     - Update HTTP routes to UUID-based URLs: `/topics/{topicId}`
     - Update DTOs (remove tenantName/namespaceName, use UUIDs)
     - Update CLI and Admin UI to use UUID-based APIs

3. **Testing Strategy:**
   - Update tests incrementally with each phase
   - Simplified test data (no need for qualified names)
   - Focus on `topicId`-based operations
   - Integration tests for new EventId format

4. **Benefits of This Change:**
   - Much simpler codebase:
     - No qualified name construction (`tenantName/namespaceName/topicName`)
     - No triple parameter lookups `(name, tenantName, namespaceName)`
     - Single `topicId: UUID` parameter everywhere
   - Better performance:
     - UUID-based lookups (single key)
     - No string concatenation for keys
     - Simpler storage structures
   - Cleaner EventId format (2 components instead of 4)
   - Globally unique topic identification
   - Simpler Topic model (4 fewer fields)
   - Cleaner API design (UUID-based URLs)
   - No migration complexity (clean break)
   - Reduced code complexity throughout the system
