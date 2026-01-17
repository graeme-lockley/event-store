# Tenant Management Business Rules

This document comprehensively describes all business rules governing tenant lifecycle and management operations in the event store system.

## Table of Contents

1. [Domain Model Rules](#domain-model-rules)
2. [Creation Rules](#creation-rules)
3. [Update Rules](#update-rules)
4. [Deletion Rules](#deletion-rules)
5. [Retrieval Rules](#retrieval-rules)
6. [Quota Rules](#quota-rules)
7. [Event Sourcing Rules](#event-sourcing-rules)
8. [Audit and Tracking Rules](#audit-and-tracking-rules)

---

## Domain Model Rules

### Rule DM-1: Tenant Identity

**Rule**: Each tenant has two types of identifiers:
- **tenantId (UUID)**: Stable, immutable identifier that never changes after creation. Used for programmatic access, permissions, and internal references.
- **name (String)**: Human-readable identifier that can be changed. Used in URLs and for display purposes.

**Rationale**: Separates stable programmatic references from user-friendly names that may need to change over time.

---

### Rule DM-2: Tenant Name Required

**Rule**: Tenant name must not be blank or empty.

**Rationale**: A tenant must have an identifier for display and URL purposes.

**Violation**: An error is raised when attempting to create a tenant with a blank or empty name.

---

### Rule DM-3: Tenant Active Status

**Rule**: A tenant is considered active if and only if it has not been deleted. Deleted tenants are inactive.

**Rationale**: Enables soft-delete functionality while maintaining clear active/inactive distinction.

---

### Rule DM-4: Quota Values Must Be Positive

**Rule**: All quota fields must have positive values (greater than zero).

**Requirements**:
- `maxTopics > 0`
- `maxNamespaces > 0`
- `maxEventsPerDay > 0`
- `maxConsumers > 0`
- `maxUsers > 0`
- `maxEventSizeBytes > 0`

**Rationale**: Negative or zero quotas are nonsensical and would cause system errors.

**Violation**: An error is raised when attempting to set any quota value to zero or negative.

---

### Rule DM-5: Quota Default Values

**Rule**: When a quota is not explicitly provided, default values are used:
- `maxTopics`: 100
- `maxNamespaces`: 50
- `maxEventsPerDay`: 1,000,000
- `maxConsumers`: 100
- `maxUsers`: 50
- `maxEventSizeBytes`: 1,024 * 1,024 (1MB)

**Rationale**: Provides reasonable defaults for tenants without explicit quota configuration.

---

### Rule DM-6: Immutable Tenant ID

**Rule**: The `tenantId` (UUID) never changes after tenant creation. It is immutable.

**Rationale**: Maintains stable references in permissions, relationships, and audit trails even when tenant names change.

---

## Creation Rules

### Rule C-1: Unique Tenant Name

**Rule**: Tenant names must be unique across all tenants. A tenant with the same name cannot already exist.

**Rationale**: Prevents naming conflicts and ensures unambiguous identification by name.

**Violation**: An error is raised when attempting to create a tenant with a name that already exists.

---

### Rule C-2: Tenant Name Format Validation

**Rule**: Tenant names must conform to a specific format to ensure URL safety, readability, and prevent conflicts with reserved names.

**Requirements**:
1. **Format**: 
   - Must contain only alphanumeric characters (uppercase and lowercase letters, and digits) and hyphens
   - Must start and end with an alphanumeric character (cannot start or end with a hyphen)
   - Must be between 2 and 64 characters in length
2. **Reserved names**: Cannot be `$system` (case-insensitive, reserved for system operations)
3. **Minimum length**: At least 2 characters
4. **Maximum length**: At most 64 characters

**Rationale**: 
- URL-safe names ensure compatibility with REST APIs and web interfaces
- Prevents conflicts with system-reserved identifiers
- Maintains consistency with namespace naming conventions

**Violation**: An error is raised with a specific reason when tenant name violates format rules.

---

### Rule C-3: UUID Generation on Creation

**Rule**: Each new tenant is assigned a unique UUID as its `tenantId` at creation time. This identifier is automatically generated and cannot be specified during creation.

**Rationale**: Ensures globally unique identifiers for stable references.

---

### Rule C-4: Timestamp Initialization

**Rule**: On creation, a creation timestamp is automatically set to the current time. The tenant has no update timestamp or deletion timestamp initially.

**Rationale**: Establishes clear creation timestamp while indicating the tenant has never been updated or deleted.

---

### Rule C-5: Optional Quota on Creation

**Rule**: A quota can be optionally provided during tenant creation. If omitted, no quota is assigned to the tenant.

**Rationale**: Allows tenants to be created without explicit quota limits, relying on system defaults or future quota assignment.

---

### Rule C-6: Optional Metadata on Creation

**Rule**: Metadata can be optionally provided during tenant creation. If omitted, the tenant is created with no metadata.

**Rationale**: Supports flexible tenant configuration without requiring metadata.

---

### Rule C-7: Event Sourcing on Creation

**Rule**: Tenant creation must generate an event that is recorded in the tenant management event stream.

**Rationale**: Enables event-sourced tenant management with full audit trail and time-travel capabilities.

---

### Rule C-8: Audit Tracking on Creation

**Rule**: Creation must track who created the tenant and when. The creator identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

## Update Rules

### Rule U-1: Tenant Must Exist for Update

**Rule**: A tenant must exist before it can be updated. Updates to non-existent tenants are not allowed.

**Rationale**: Prevents updates to non-existent resources.

**Violation**: An error is raised when attempting to update a tenant that does not exist.

---

### Rule U-2: Block Updates to Deleted Tenants

**Rule**: Updates to a tenant are only allowed if the tenant is active (not deleted).

**Rationale**: Deleted tenants are soft-deleted for audit purposes, but should not be modified after deletion to maintain data integrity.

**Violation**: An error is raised when attempting to update a tenant that has been deleted.

---

### Rule U-3: Tenant Name Format Validation on Update

**Rule**: When updating tenant name, it must conform to the same format validation rules as creation (see Rule C-2).

**Requirements**: Same as Rule C-2 - must be URL-safe alphanumeric with hyphens, 2-64 characters, and cannot be the reserved name `$system`.

**Rationale**: Maintains consistency and URL safety across all tenant names.

**Violation**: An error is raised with a specific reason when the updated tenant name violates format rules.

---

### Rule U-4: Unique Tenant Name on Update

**Rule**: When updating a tenant's name, the new name must be unique across all tenants. If the name is not changing, this check is skipped.

**Rationale**: Prevents naming conflicts when renaming tenants.

**Violation**: An error is raised when attempting to update a tenant name to one that already exists.

---

### Rule U-5: Partial Updates Allowed

**Rule**: All tenant fields (`name`, `quota`, `metadata`) are optional in update requests. Only provided fields are updated; others remain unchanged.

**Rationale**: Enables fine-grained updates without requiring all fields to be specified.

---

### Rule U-6: Timestamp Update on Modification

**Rule**: An update timestamp is automatically set to the current time whenever a tenant is successfully updated.

**Rationale**: Tracks when tenants were last modified for audit and operational tracking.

---

### Rule U-7: Quota Change Validation Against Current Usage

**Rule**: When updating a tenant's quota, the new quota values must not be less than the tenant's current usage of those resources.

**Requirements**:
- **Topics**: `newQuota.maxTopics` must be >= current active topics count for the tenant
- **Namespaces**: `newQuota.maxNamespaces` must be >= current active namespaces count for the tenant
- **Consumers**: `newQuota.maxConsumers` must be >= current active consumers count for the tenant
- **Users**: `newQuota.maxUsers` must be >= current users associated with the tenant

**Rationale**: Prevents quota reductions that would violate existing resource usage, ensuring tenants can continue operating after quota updates.

**Validation Logic**:
- Current usage of each resource type is calculated for the tenant
- Each quota field is validated independently
- Validation only occurs when a quota field is being reduced below its current value
- Quota increases are always allowed without validation

**Violation**: An error is raised indicating which resource type and the current usage vs. requested quota when a quota reduction would violate current usage.

**Note**: Only validates quota reductions, not increases. Increases are always allowed.

---

### Rule U-8: Event Sourcing on Update

**Rule**: Tenant updates must generate an event that is recorded in the tenant management event stream.

**Rationale**: Maintains event-sourced tenant management with full audit trail.

---

### Rule U-9: Audit Tracking on Update

**Rule**: Updates must track who updated the tenant and when. The updater identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

## Deletion Rules

### Rule D-1: Tenant Must Exist for Deletion

**Rule**: A tenant must exist and be active before it can be deleted. Deletions of non-existent or already-deleted tenants are handled gracefully.

**Rationale**: Prevents attempts to delete resources that don't exist and makes deletion operations safe to retry.

**Behavior**: 
- An error is raised when attempting to delete a tenant that has never existed
- Deleting an already-deleted tenant returns a failure indicator without error

---

### Rule D-2: Soft Delete Implementation

**Rule**: Tenant deletion is a soft delete operation. The tenant record is preserved with a deletion timestamp, rather than being physically removed from the system.

**Rationale**: Preserves audit history and allows recovery if needed, while marking the tenant as inactive.

---

### Rule D-3: Idempotent Deletion

**Rule**: Deleting an already-deleted tenant returns a failure indicator without raising an error.

**Rationale**: Makes deletion operations safe to retry without error and maintains idempotency.

---

### Rule D-4: Active Check Before Deletion

**Rule**: Deletion only proceeds if the tenant is active (not already deleted).

**Rationale**: Prevents duplicate deletion processing and maintains idempotency.

---

### Rule D-5: Event Sourcing on Deletion

**Rule**: Tenant deletion must generate an event that is recorded in the tenant management event stream.

**Rationale**: Maintains event-sourced tenant management with full audit trail.

---

### Rule D-6: Optional Deletion Reason

**Rule**: A deletion reason can be optionally provided for audit purposes.

**Rationale**: Supports compliance and operational tracking by recording why a tenant was deleted.

---

### Rule D-7: Audit Tracking on Deletion

**Rule**: Deletions must track who deleted the tenant and when. The deleter identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule D-8: Deletion Success Indicator

**Rule**: Deletion operations return a success indicator if the tenant was successfully deleted, and a failure indicator if the tenant was already deleted.

**Rationale**: Provides clear feedback about the operation outcome.

---

## Retrieval Rules

### Rule R-1: Retrieve by UUID

**Rule**: Tenants can be retrieved by their stable UUID identifier.

**Rationale**: UUID-based retrieval is more reliable for programmatic access and doesn't depend on name changes.

**Behavior**: Returns the tenant if found and active, or nothing if the tenant does not exist or has been deleted.

---

### Rule R-2: Retrieve by Name

**Rule**: Tenants can be retrieved by their human-readable name.

**Rationale**: Name-based retrieval is more user-friendly for API consumers and display purposes.

**Behavior**: Returns the tenant if found and active, or nothing if the tenant does not exist or has been deleted.

---

### Rule R-3: List All Tenants

**Rule**: All active tenants can be retrieved in a list.

**Rationale**: Supports administrative operations and tenant discovery.

**Behavior**: Returns all active tenants (deleted tenants are excluded from the results).

---

### Rule R-4: Active-Only Retrieval

**Rule**: Retrieval operations only return active tenants. Deleted tenants are excluded from query results.

**Rationale**: Prevents deleted tenants from appearing in normal operations while preserving them for audit purposes.

---

### Rule R-5: Null Return for Non-Existent Tenants

**Rule**: Retrieval operations return nothing (no result) when a tenant does not exist or is deleted, rather than raising an error.

**Rationale**: Distinguishes between "not found" and error conditions, enabling graceful handling in application code.

---

## Quota Rules

### Rule Q-1: Quota as Optional Resource Limits

**Rule**: Quotas are optional resource limits that can be applied to tenants. Tenants can exist without quotas.

**Rationale**: Provides flexibility for tenants that don't need explicit limits or rely on system defaults.

---

### Rule Q-2: Quota Validation on Reduction

**Rule**: When reducing a quota field, the new value must not be less than the current usage of that resource.

**Rationale**: Prevents quota reductions that would violate existing resource usage.

**Violation**: An error is raised when a quota reduction would violate current usage.

---

### Rule Q-3: Quota Increases Always Allowed

**Rule**: Quota increases are always allowed, regardless of current usage.

**Rationale**: Enables tenants to scale up without restrictions.

---

### Rule Q-4: Independent Quota Field Validation

**Rule**: Each quota field (topics, namespaces, consumers, users, events per day, event size) is validated independently. Reducing one field does not require checking others.

**Rationale**: Allows flexible quota management where different resource types can be adjusted independently.

---

### Rule Q-5: Quota Replacement on Update

**Rule**: When updating quota, the entire quota configuration is replaced with the new quota provided. All quota fields must be specified in the new quota (using default values where appropriate). Quota cannot be partially updated at the field level.

**Rationale**: Maintains consistency in quota configuration and simplifies quota management semantics.

**Note**: Quota can be updated independently of other tenant fields (name, metadata), but the quota itself must be provided as a complete configuration.

---

## Event Sourcing Rules

### Rule E-1: All Mutations Generate Events

**Rule**: All tenant mutations (create, update, delete) must generate corresponding events that are recorded in the tenant management event stream.

**Events**:
- Tenant created event - recorded on tenant creation
- Tenant updated event - recorded on tenant update
- Tenant deleted event - recorded on tenant deletion

**Rationale**: Enables event-sourced tenant management with full audit trail, time-travel queries, and the ability to rebuild tenant state from event history.

---

### Rule E-2: Event Payload Completeness

**Rule**: Event payloads must contain all information necessary to rebuild tenant state from events.

**Rationale**: Ensures tenant state can be accurately reconstructed from event history.

**Content**: Events include tenant identifier, name, quota (if present), metadata, timestamps, and audit information (who performed the operation).

---

### Rule E-3: Event Timestamp Alignment

**Rule**: Event timestamps must match the operation timestamps (creation, update, deletion).

**Rationale**: Ensures consistency between tenant state and event history.

---

## Audit and Tracking Rules

### Rule A-1: Creation Audit Tracking

**Rule**: Tenant creation must track who created the tenant and when.

**Tracking Fields**:
- Creation timestamp: When the tenant was created
- Creator identity: Who created the tenant (defaults to "system" if not specified)

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-2: Update Audit Tracking

**Rule**: Tenant updates must track who updated the tenant and when.

**Tracking Fields**:
- Update timestamp: When the tenant was last updated
- Updater identity: Who updated the tenant (defaults to "system" if not specified)

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-3: Deletion Audit Tracking

**Rule**: Tenant deletions must track who deleted the tenant, when, and optionally why.

**Tracking Fields**:
- Deletion timestamp: When the tenant was deleted
- Deleter identity: Who deleted the tenant (defaults to "system" if not specified)
- Deletion reason: Optional explanation for why the tenant was deleted

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-4: Immutable Audit Fields

**Rule**: Creation audit fields (timestamp and creator) are immutable after tenant creation. Update audit fields are updated only on successful updates.

**Rationale**: Maintains accurate historical record of tenant lifecycle.

---

## Summary of Rule Violations

When business rules are violated, errors are raised to prevent the invalid operation. The following table summarizes common violation scenarios:

| Violation | When Raised | Related Rules |
|-----------|-------------|---------------|
| Tenant name already exists | Attempting to create or rename to an existing tenant name | C-1, U-4 |
| Invalid tenant name format | Tenant name violates format requirements | C-2, U-3 |
| Tenant does not exist | Attempting to update or delete a non-existent tenant | U-1, D-1 |
| Cannot update deleted tenant | Attempting to update a tenant that has been deleted | U-2 |
| Quota exceeded | Quota reduction would violate current resource usage | Q-2 |
| Invalid quota values | Quota values are zero or negative | DM-4 |
| Blank tenant name | Tenant name is blank or empty | DM-2 |

---

## Related Documentation

- `/docs/Tenant and Namespace Requirements.md` - System design and architecture decisions
