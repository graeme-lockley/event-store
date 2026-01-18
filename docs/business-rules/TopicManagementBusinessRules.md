# Topic Management Business Rules

This document comprehensively describes all business rules governing topic lifecycle and management operations in the event store system.

## Table of Contents

1. [Domain Model Rules](#domain-model-rules)
2. [Creation Rules](#creation-rules)
3. [Update Rules](#update-rules)
4. [Deletion Rules](#deletion-rules)
5. [Schema Management Rules](#schema-management-rules)
6. [Retrieval Rules](#retrieval-rules)
7. [Sequence Management Rules](#sequence-management-rules)
8. [Event Sourcing Rules](#event-sourcing-rules)
9. [Audit and Tracking Rules](#audit-and-tracking-rules)

---

## Domain Model Rules

### Rule DM-1: Topic Identity

**Rule**: Each topic has a globally unique identifier:
- **topicId (UUID)**: Stable, immutable identifier that never changes after creation. Globally unique across all tenants and namespaces. Used for programmatic access, permissions, and internal references.
- **namespaceId (UUID)**: Reference to the namespace this topic belongs to. Used for organizational and filtering purposes.
- **name (String)**: Human-readable identifier for display purposes. Not used for programmatic identification.

**Rationale**: Globally unique topicId eliminates the need for complex multi-component lookups (tenant/namespace/name) and simplifies the architecture. The name is retained for display purposes only.

**Example**:
```kotlin
data class Topic(
    val topicId: UUID,              // Globally unique identifier
    val namespaceId: UUID,          // Namespace reference
    val name: String,               // Display name
    val sequence: Long,
    val schemas: List<Schema>
)
```

---

### Rule DM-2: Topic Name Required

**Rule**: Topic name must not be blank or empty.

**Rationale**: A topic must have a name for display and user interface purposes.

**Violation**: An error is raised when attempting to create a topic with a blank or empty name.

---

### Rule DM-3: Globally Unique Topic ID

**Rule**: The `topicId` (UUID) is globally unique across all tenants and namespaces. No two topics can have the same `topicId`.

**Rationale**: Enables direct lookup by UUID without requiring tenant or namespace context. Simplifies event identification and reduces lookup complexity.

---

### Rule DM-4: Namespace Association

**Rule**: Every topic must be associated with a namespace via `namespaceId` (UUID). The namespace must exist.

**Rationale**: Provides organizational context and enables namespace-scoped operations.

**Violation**: An error is raised when attempting to create a topic with a non-existent `namespaceId`.

---

### Rule DM-5: Sequence Requirements

**Rule**: The sequence number must be non-negative and is initialized to 0 when a topic is created.

**Rationale**: Sequence numbers are used for event ordering and must start at a valid value.

**Violation**: An error is raised when attempting to set a sequence to a negative value.

---

### Rule DM-6: Schemas Required

**Rule**: A topic must have at least one schema defined.

**Rationale**: Topics require schemas to validate events. Without schemas, event validation cannot occur.

**Violation**: An error is raised when attempting to create a topic with an empty schemas list.

---

## Creation Rules

### Rule C-1: Globally Unique Topic ID

**Rule**: Each topic is assigned a UUID (`topicId`) upon creation. Since UUIDs are globally unique by design, no uniqueness check is required beyond UUID generation.

**Rationale**: UUID generation ensures uniqueness without requiring database lookups or name-based validation.

---

### Rule C-2: Topic Name Format Validation

**Rule**: Topic names must conform to a specific format to ensure URL safety, readability, and prevent conflicts with reserved names.

**Requirements**:
1. **Format**: 
   - Must contain only alphanumeric characters (uppercase and lowercase letters, and digits) and hyphens
   - Must start and end with an alphanumeric character (cannot start or end with a hyphen)
   - Must be between 2 and 64 characters in length
2. **Reserved names**: None currently reserved (system topics use standard names like "tenants", "namespaces", "users", "permissions", "api-keys" but these are not reserved)
3. **Minimum length**: At least 2 characters
4. **Maximum length**: At most 64 characters

**Rationale**: 
- URL-safe names ensure compatibility with REST APIs and web interfaces
- Maintains consistency with tenant and namespace naming conventions
- Prevents naming conflicts and ensures unambiguous identification

**Violation**: An error is raised with a specific reason when topic name violates format rules.

---

### Rule C-3: Topic Name Uniqueness (Optional)

**Rule**: Topic names are not required to be unique within a namespace or globally. Multiple topics can share the same name within the same namespace or across different namespaces.

**Rationale**: Since topics are identified by `topicId` (UUID) rather than by name, name uniqueness is not required for identification. The `topicId` provides the globally unique identifier needed for all operations.

**Note**: This differs from tenant and namespace naming where names must be unique within their scope. Topic names are primarily for display purposes only.

---

### Rule C-4: Namespace Must Exist

**Rule**: The `namespaceId` (UUID) specified during topic creation must reference an existing, active namespace.

**Rationale**: Topics must belong to a valid namespace context.

**Violation**: An error is raised when attempting to create a topic with a non-existent `namespaceId`.

---

### Rule C-5: Tenant Quota Enforcement

**Rule**: Topic creation must ensure that the tenant's quota limit on number of topics is honoured.

**Rationale**: Tenant limits ensure that resources are not exhausted.

**Violation**: An error is raised when attempting to create a topic and the number of topics dictated by the tenant's quota limit (`maxTopics`) is exceeded.

---

### Rule C-6: Schemas Required

**Rule**: At least one schema must be provided when creating a topic.

**Rationale**: Topics require schemas for event validation.

**Violation**: An error is raised when attempting to create a topic with an empty schemas list.

---

### Rule C-7: Schema EventType Uniqueness

**Rule**: All schema `eventType` values within a topic must be unique. No two schemas in the same topic can have the same `eventType`.

**Rationale**: Ensures unambiguous event type identification within a topic.

**Violation**: An error is raised when attempting to create a topic with duplicate `eventType` values across schemas.

---

## Update Rules

### Rule U-1: Topic Must Exist (by topicId)

**Rule**: Topics are identified by `topicId` (UUID) for update operations. The topic must exist.

**Rationale**: Enables direct topic updates using the globally unique identifier.

**Violation**: An error is raised when attempting to update a non-existent topic.

---

### Rule U-2: Topic Name Format Validation on Update

**Rule**: When updating a topic's name (if name updates are supported), it must conform to the same format validation rules as creation (see Rule C-2).

**Requirements**: Same as Rule C-2 - must be URL-safe alphanumeric with hyphens, 2-64 characters.

**Rationale**: Maintains consistency and URL safety across all topic names.

**Violation**: An error is raised with a specific reason when the updated topic name violates format rules.

**Note**: Currently, topic name updates are not supported. If implemented in the future, this rule applies.

---

### Rule U-3: Schema Additive Updates Only

**Rule**: When updating topic schemas, only new schemas may be added. Existing schemas cannot be removed.

**Rationale**: Preserves backward compatibility and prevents breaking changes to event validation.

**Violation**: An error is raised when attempting to remove an existing schema from a topic.

---

### Rule U-4: Schema EventType Preservation

**Rule**: When updating schemas, all existing `eventType` values must be preserved. New `eventType` values may be added, but existing ones cannot be removed.

**Rationale**: Maintains backward compatibility with existing events and consumers.

**Violation**: An error is raised when an update would remove an existing `eventType`.

---

### Rule U-5: Timestamp Update on Modification

**Rule**: If topics track update timestamps, an update timestamp is automatically set to the current time whenever a topic is successfully updated.

**Rationale**: Tracks when topics were last modified for audit and operational tracking.

**Note**: Currently, topics may not track update timestamps. If implemented in the future, this rule applies.

---

## Deletion Rules

### Rule D-1: Topics Are Not Deletable

**Rule**: Topics cannot be deleted once created. There is no deletion operation available for topics.

**Rationale**: 
- Preserves event history and maintains referential integrity
- Topics are permanent records of event streams and schema definitions
- Prevents accidental data loss and maintains audit trails

**Note**: This design decision ensures that all events and schemas remain accessible throughout the system's lifetime. If topic deletion is required in the future, it would need to be implemented with careful consideration of data retention policies and event history preservation.

---

## Schema Management Rules

### Rule SM-1: Schema Additive Constraint

**Rule**: Schema updates can only add new schemas. Existing schemas (identified by `eventType`) cannot be removed or modified.

**Rationale**: Preserves backward compatibility and prevents breaking changes to event validation logic.

**Violation**: An error is raised when attempting to remove a schema.

---

### Rule SM-2: Schema EventType Uniqueness

**Rule**: Within a single topic, all schema `eventType` values must be unique. No two schemas can share the same `eventType`.

**Rationale**: Ensures unambiguous event type identification for validation and routing.

**Violation**: An error is raised when attempting to add a schema with a duplicate `eventType`.

---

### Rule SM-3: Schema Validation Requirements

**Rule**: Each schema must have both `eventType` and `schema` (JSON schema definition) fields. The `eventType` must be non-blank.

**Rationale**: Schemas require both identification and validation rules to function properly.

**Violation**: An error is raised when a schema is missing required fields.

---

## Retrieval Rules

### Rule R-1: Retrieve by Topic ID

**Rule**: Topics are retrieved using `topicId` (UUID). This provides direct, globally unique lookup without requiring tenant or namespace context.

**Rationale**: UUID-based lookup is simpler and more efficient than multi-component lookups.

**Violation**: An error is raised when attempting to retrieve a topic with a non-existent `topicId`.

---

### Rule R-2: List Topics by Namespace

**Rule**: Topics can be listed with an optional `namespaceId` (UUID) filter. If `namespaceId` is provided, only topics belonging to that namespace are returned. If `namespaceId` is null, all topics are returned.

**Rationale**: Enables namespace-scoped topic listing while also supporting system-wide listing.

---

### Rule R-3: Topic Not Found Handling

**Rule**: Operations that reference a topic by `topicId` return an appropriate error when the `topicId` does not exist.

**Rationale**: Provides clear feedback when operations reference non-existent topics.

**Violation**: An error (e.g., `TopicNotFoundException`) is raised when a `topicId` does not exist.

---

## Sequence Management Rules

### Rule S-1: Sequence Atomicity

**Rule**: Sequence increments must be atomic. Only one sequence increment can occur at a time per topic.

**Rationale**: Prevents race conditions and ensures unique sequence numbers for events.

---

### Rule S-2: Sequence Increment Only

**Rule**: Sequence numbers can only increase, never decrease.

**Rationale**: Ensures monotonic event ordering and prevents sequence reuse.

**Violation**: An error is raised when attempting to set a sequence to a value less than the current sequence.

---

### Rule S-3: Sequence Initialization

**Rule**: New topics initialize with sequence 0. The first event published to a topic will have sequence 1.

**Rationale**: Provides a consistent starting point for event sequencing.

---

## Event Sourcing Rules

### Rule ES-1: Event ID Format

**Rule**: Event IDs use the format `"topicId/sequence"` where:
- `topicId` is the UUID of the topic (e.g., `"550e8400-e29b-41d4-a716-446655440000"`)
- `sequence` is the sequence number (e.g., `42`)

**Example**: `"550e8400-e29b-41d4-a716-446655440000/42"`

**Rationale**: Simplifies event identification by removing tenant and namespace components. The `topicId` is globally unique, so no additional context is needed.

---

### Rule ES-2: Event Sequence Generation

**Rule**: Event sequences are generated atomically using `getAndIncrementSequence(topicId)`. Each call returns the next sequence number and increments it in a single atomic operation.

**Rationale**: Ensures unique, monotonic sequence numbers even under concurrent event publishing.

---

### Rule ES-3: Event Topic Reference

**Rule**: Events reference topics via `topicId` (UUID) only. No tenant or namespace information is stored in the event.

**Rationale**: Simplifies event storage and lookup by using the globally unique topic identifier.

---

## Audit and Tracking Rules

### Rule AT-1: Creation Audit Tracking

**Rule**: Topic creation must track who created the topic and when.

**Tracking Fields**:
- Creation timestamp: When the topic was created (if tracked)
- Creator identity: Who created the topic (defaults to "system" if not specified, if tracked)

**Rationale**: Provides audit trail for compliance and operational tracking.

**Note**: Currently, topics may not track detailed creation metadata. If implemented in the future, these fields should be tracked.

---

### Rule AT-2: Update Audit Tracking

**Rule**: Topic schema updates must track who updated the topic and when.

**Tracking Fields**:
- Update timestamp: When the topic was last updated (if tracked)
- Updater identity: Who updated the topic (defaults to "system" if not specified, if tracked)

**Rationale**: Provides audit trail for compliance and operational tracking.

**Note**: Currently, topics may not track detailed update metadata. If implemented in the future, these fields should be tracked.

---

### Rule AT-3: Immutable Audit Fields

**Rule**: Creation audit fields (timestamp and creator) are immutable after topic creation. Update audit fields are updated only on successful updates.

**Rationale**: Maintains accurate historical record of topic lifecycle.

---

## Summary Table

| Rule ID | Category | Description | Violation Behavior |
|---------|----------|-------------|-------------------|
| DM-1 | Domain Model | Topic has globally unique `topicId` (UUID) | N/A |
| DM-2 | Domain Model | Topic name must not be blank | Error raised |
| DM-3 | Domain Model | `topicId` is globally unique | N/A (UUID generation) |
| DM-4 | Domain Model | Topic must have valid `namespaceId` | Error raised |
| DM-5 | Domain Model | Sequence must be non-negative | Error raised |
| DM-6 | Domain Model | Topic must have at least one schema | Error raised |
| C-1 | Creation | UUID ensures uniqueness | N/A |
| C-2 | Creation | Topic name format validation (alphanumeric + hyphens, 2-64 chars) | Error raised |
| C-3 | Creation | Topic names are not required to be unique | N/A |
| C-4 | Creation | `namespaceId` must exist | Error raised |
| C-5 | Creation | Tenant quota (`maxTopics`) must not be exceeded | Error raised |
| C-6 | Creation | At least one schema required | Error raised |
| C-7 | Creation | Schema `eventType` must be unique | Error raised |
| U-1 | Update | Topic must exist by `topicId` | Error raised |
| U-2 | Update | Topic name must follow format rules (if name updates supported) | Error raised |
| U-3 | Update | Schemas can only be added | Error raised |
| U-4 | Update | Existing `eventType` values must be preserved | Error raised |
| U-5 | Update | Update timestamp set on modification (if tracked) | N/A |
| SM-1 | Schema | Schema updates are additive only | Error raised |
| SM-2 | Schema | `eventType` values must be unique | Error raised |
| SM-3 | Schema | Schema must have `eventType` and `schema` fields | Error raised |
| R-1 | Retrieval | Topics retrieved by `topicId` (UUID) | Error if not found |
| R-2 | Retrieval | Optional namespace filtering via `namespaceId` | N/A |
| R-3 | Retrieval | Non-existent `topicId` returns error | Error raised |
| S-1 | Sequence | Sequence increments are atomic | N/A (atomic operation) |
| S-2 | Sequence | Sequence can only increase | Error if violated |
| S-3 | Sequence | New topics initialize with sequence 0 | N/A |
| ES-1 | Event Sourcing | Event ID format is `"topicId/sequence"` | N/A |
| ES-2 | Event Sourcing | Sequence generation is atomic | N/A (atomic operation) |
| ES-3 | Event Sourcing | Events reference topics via `topicId` only | N/A |
| D-1 | Deletion | Topics are not deletable | N/A (operation not available) |
| AT-1 | Audit | Creation tracking (if implemented) | N/A |
| AT-2 | Audit | Update tracking (if implemented) | N/A |
| AT-3 | Audit | Audit fields are immutable | N/A |

---

## Summary of Rule Violations

When business rules are violated, errors are raised to prevent the invalid operation. The following table summarizes common violation scenarios:

| Violation | When Raised | Related Rules |
|-----------|-------------|---------------|
| Invalid topic name format | Topic name violates format requirements | C-2, U-2 |
| Topic name too short or too long | Topic name is less than 2 or more than 64 characters | C-2, U-2 |
| Namespace does not exist | Attempting to create a topic with a non-existent `namespaceId` | C-4 |
| Tenant quota exceeded | Attempting to create a topic when tenant's topic quota limit is exceeded | C-5 |
| Blank topic name | Topic name is blank or empty | DM-2 |
| No schemas provided | Attempting to create a topic with an empty schemas list | C-6 |
| Duplicate schema eventType | Attempting to create a topic with duplicate `eventType` values | C-7 |
| Topic does not exist | Attempting to update a non-existent topic | U-1 |
| Schema removal attempted | Attempting to remove an existing schema from a topic | U-3 |
| EventType removal attempted | Attempting to remove an existing `eventType` when updating schemas | U-4 |
| Topic deletion attempted | Attempting to delete a topic (operation not supported) | D-1 |

---

## Related Documentation

- [Tenant Management Business Rules](./TenantManagementBusinessRules.md)
- [Namespace Management Business Rules](./NamespaceManagementBusinessRules.md)

---

## Notes

- Topics are globally identified by `topicId` (UUID), eliminating the need for tenant/namespace/name triple lookups.
- Event IDs use the simplified format `"topicId/sequence"` instead of the previous `"tenant/namespace/topic/sequence"` format.
- Topic lookup patterns have been simplified from `(name, tenantName, namespaceName)` to a single `topicId: UUID` parameter.
- All topic operations now use UUIDs directly, simplifying the API and reducing complexity.
