# Namespace Management Business Rules

This document comprehensively describes all business rules governing namespace lifecycle and management operations in the event store system.

## Table of Contents

1. [Domain Model Rules](#domain-model-rules)
2. [Creation Rules](#creation-rules)
3. [Update Rules](#update-rules)
4. [Deletion Rules](#deletion-rules)
5. [Retrieval Rules](#retrieval-rules)
6. [Event Sourcing Rules](#event-sourcing-rules)
7. [Audit and Tracking Rules](#audit-and-tracking-rules)

---

## Domain Model Rules

### Rule DM-1: Namespace Identity

**Rule**: Each namespace has multiple types of identifiers:
- **namespaceId (UUID)**: Stable, immutable identifier that never changes after creation. Used for programmatic access, permissions, and internal references. Globally unique across all tenants.
- **tenantId (UUID)**: Reference to the tenant that owns the namespace. Stable and immutable.
- **name (String)**: Human-readable identifier that can be changed. Used in URLs and for display purposes. Unique within a tenant.
- **tenantName (String)**: Human-readable tenant name (derived from tenant lookup). Used for display and qualified name construction.

**Rationale**: Separates stable programmatic references from user-friendly names that may need to change over time. The globally unique namespaceId enables flat API routes and simplifies permissions.

---

### Rule DM-2: Namespace Name Required

**Rule**: Namespace name must not be blank or empty.

**Rationale**: A namespace must have an identifier for display and URL purposes.

**Violation**: An error is raised when attempting to create a namespace with a blank or empty name.

---

### Rule DM-3: Namespace Active Status

**Rule**: A namespace is considered active if and only if it has not been deleted. Deleted namespaces are inactive.

**Rationale**: Enables soft-delete functionality while maintaining clear active/inactive distinction.

---

### Rule DM-4: Immutable Namespace ID

**Rule**: The `namespaceId` (UUID) never changes after namespace creation. It is immutable and globally unique.

**Rationale**: Maintains stable references in permissions, relationships, and audit trails even when namespace names change. Global uniqueness enables flat API routes.

---

### Rule DM-5: Immutable Tenant ID Reference

**Rule**: The `tenantId` (UUID) reference never changes after namespace creation. It is immutable.

**Rationale**: Maintains stable relationship to the owning tenant throughout the namespace lifecycle.

---

### Rule DM-6: Qualified Name Construction

**Rule**: A namespace's qualified name is constructed as `{tenantName}/{name}` for display and URL purposes.

**Rationale**: Provides human-readable fully-qualified namespace identifiers while maintaining separate tenant and namespace name components.

---

## Creation Rules

### Rule C-1: Unique Namespace Name Within Tenant

**Rule**: Namespace names must be unique within a tenant. A namespace with the same name cannot already exist in the same tenant.

**Rationale**: Prevents naming conflicts within a tenant and ensures unambiguous identification by name within tenant context.

**Violation**: An error is raised when attempting to create a namespace with a name that already exists in the tenant.

**Note**: Namespaces with the same name can exist in different tenants.

---

### Rule C-2: Namespace Name Format Validation

**Rule**: Namespace names must conform to a specific format to ensure URL safety, readability, and prevent conflicts with reserved names.

**Requirements**:
1. **Format**: 
   - Must contain only alphanumeric characters (uppercase and lowercase letters, and digits) and hyphens
   - Must start and end with an alphanumeric character (cannot start or end with a hyphen)
   - Must be between 2 and 64 characters in length
2. **Reserved names**: Cannot be `$management` (case-insensitive, reserved for system operations)
3. **Minimum length**: At least 2 characters
4. **Maximum length**: At most 64 characters

**Rationale**: 
- URL-safe names ensure compatibility with REST APIs and web interfaces
- Prevents conflicts with system-reserved identifiers
- Maintains consistency with tenant naming conventions

**Violation**: An error is raised with a specific reason when namespace name violates format rules.

---

### Rule C-3: Tenant Must Exist

**Rule**: A namespace can only be created within an existing tenant. The tenant must exist and be active.

**Rationale**: Ensures namespaces are always associated with a valid tenant.

**Violation**: An error is raised when attempting to create a namespace for a tenant that does not exist.

---

### Rule C-4: UUID Generation on Creation

**Rule**: Each new namespace is assigned a unique UUID as its `namespaceId` at creation time. This identifier is automatically generated and cannot be specified during creation.

**Rationale**: Ensures globally unique identifiers for stable references across all tenants.

---

### Rule C-5: Timestamp Initialization

**Rule**: On creation, a creation timestamp is automatically set to the current time. The namespace has no update timestamp or deletion timestamp initially.

**Rationale**: Establishes clear creation timestamp while indicating the namespace has never been updated or deleted.

---

### Rule C-6: Optional Description on Creation

**Rule**: A description can be optionally provided during namespace creation. If omitted, the namespace is created with no description.

**Rationale**: Supports flexible namespace configuration without requiring descriptions.

---

### Rule C-7: Optional Metadata on Creation

**Rule**: Metadata can be optionally provided during namespace creation. If omitted, the namespace is created with no metadata.

**Rationale**: Supports flexible namespace configuration without requiring metadata.

---

### Rule C-8: Event Sourcing on Creation

**Rule**: Namespace creation must generate an event that is recorded in the namespace management event stream.

**Rationale**: Enables event-sourced namespace management with full audit trail and time-travel capabilities.

---

### Rule C-9: Audit Tracking on Creation

**Rule**: Creation must track who created the namespace and when. The creator identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule C-10: Tenant Quota Enforcement

**Rule**: Creation must ensure that the tenant quota limit on number of namespaces is honoured.

**Rationale**: Tenant limits are used to ensure that resources are not exhausted.

**Violation**: An error is raised when attempting to create a namespace and the number of namespaces dictated by the tenant's quota limit is exceeded.

---

## Update Rules

### Rule U-1: Namespace Must Exist for Update

**Rule**: A namespace must exist before it can be updated. Updates to non-existent namespaces are not allowed.

**Rationale**: Prevents updates to non-existent resources.

**Violation**: An error is raised when attempting to update a namespace that does not exist.

---

### Rule U-2: Block Updates to Deleted Namespaces

**Rule**: Updates to a namespace are only allowed if the namespace is active (not deleted).

**Rationale**: Deleted namespaces are soft-deleted for audit purposes, but should not be modified after deletion to maintain data integrity.

**Violation**: An error is raised when attempting to update a namespace that has been deleted.

---

### Rule U-3: Namespace Name Format Validation on Update

**Rule**: When updating a namespace's name, it must conform to the same format validation rules as creation (see Rule C-2).

**Requirements**: Same as Rule C-2 - must be URL-safe alphanumeric with hyphens, 2-64 characters, and cannot be the reserved name `$management`.

**Rationale**: Maintains consistency and URL safety across all namespace names.

**Violation**: An error is raised with a specific reason when the updated namespace name violates format rules.

---

### Rule U-4: Unique Namespace Name on Update

**Rule**: When updating a namespace's name, the new name must be unique within the tenant. If the name is not changing, this check is skipped.

**Rationale**: Prevents naming conflicts when renaming namespaces within a tenant.

**Violation**: An error is raised when attempting to update a namespace name to one that already exists in the tenant.

---

### Rule U-5: Partial Updates Allowed

**Rule**: All namespace fields (`name`, `description`, `metadata`) are optional in update requests. Only provided fields are updated; others remain unchanged.

**Rationale**: Enables fine-grained updates without requiring all fields to be specified.

---

### Rule U-6: Timestamp Update on Modification

**Rule**: An update timestamp is automatically set to the current time whenever a namespace is successfully updated.

**Rationale**: Tracks when namespaces were last modified for audit and operational tracking.

---

### Rule U-7: Event Sourcing on Update

**Rule**: Namespace updates must generate an event that is recorded in the namespace management event stream.

**Rationale**: Maintains event-sourced namespace management with full audit trail.

---

### Rule U-8: Audit Tracking on Update

**Rule**: Updates must track who updated the namespace and when. The updater identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

## Deletion Rules

### Rule D-1: Namespace Must Exist for Deletion

**Rule**: A namespace must exist and be active before it can be deleted. Deletions of non-existent or already-deleted namespaces are handled gracefully.

**Rationale**: Prevents attempts to delete resources that don't exist and makes deletion operations safe to retry.

**Behavior**: 
- An error is raised when attempting to delete a namespace that has never existed
- Deleting an already-deleted namespace returns a failure indicator without error

---

### Rule D-2: Soft Delete Implementation

**Rule**: Namespace deletion is a soft delete operation. The namespace record is preserved with a deletion timestamp, rather than being physically removed from the system.

**Rationale**: Preserves audit history and allows recovery if needed, while marking the namespace as inactive.

---

### Rule D-3: Idempotent Deletion

**Rule**: Deleting an already-deleted namespace returns a failure indicator without raising an error.

**Rationale**: Makes deletion operations safe to retry without error and maintains idempotency.

---

### Rule D-4: Active Check Before Deletion

**Rule**: Deletion only proceeds if the namespace is active (not already deleted).

**Rationale**: Prevents duplicate deletion processing and maintains idempotency.

---

### Rule D-5: Event Sourcing on Deletion

**Rule**: Namespace deletion must generate an event that is recorded in the namespace management event stream.

**Rationale**: Maintains event-sourced namespace management with full audit trail.

---

### Rule D-6: Optional Deletion Reason

**Rule**: A deletion reason can be optionally provided for audit purposes.

**Rationale**: Supports compliance and operational tracking by recording why a namespace was deleted.

---

### Rule D-7: Audit Tracking on Deletion

**Rule**: Deletions must track who deleted the namespace and when. The deleter identity defaults to "system" but can be specified.

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule D-8: Deletion Success Indicator

**Rule**: Deletion operations return a success indicator if the namespace was successfully deleted, and a failure indicator if the namespace was already deleted.

**Rationale**: Provides clear feedback about the operation outcome.

---

## Retrieval Rules

### Rule R-1: Retrieve by UUID

**Rule**: Namespaces can be retrieved by their stable UUID identifier (`namespaceId`). This lookup is globally unique across all tenants.

**Rationale**: UUID-based retrieval is more reliable for programmatic access and doesn't depend on name changes. Global uniqueness enables flat API routes.

**Behavior**: Returns the namespace if found and active, or nothing if the namespace does not exist or has been deleted.

---

### Rule R-2: Retrieve by Name

**Rule**: Namespaces can be retrieved by their human-readable name within a tenant context. Requires both tenant name and namespace name.

**Rationale**: Name-based retrieval is more user-friendly for API consumers and display purposes.

**Behavior**: Returns the namespace if found and active, or nothing if the namespace does not exist or has been deleted.

---

### Rule R-3: List All Namespaces

**Rule**: All active namespaces can be retrieved in a list, with an optional filter by `tenantId`.

**Rationale**: Supports administrative operations and namespace discovery. The optional tenant filter enables tenant-scoped queries while allowing system-wide queries.

**Behavior**: 
- If `tenantId` is provided, returns only active namespaces for that tenant
- If `tenantId` is not provided (null), returns all active namespaces across all tenants
- Deleted namespaces are excluded from the results

**Note**: This method may be rethought in the future to support more sophisticated filtering.

---

### Rule R-4: Active-Only Retrieval

**Rule**: Retrieval operations only return active namespaces. Deleted namespaces are excluded from query results.

**Rationale**: Prevents deleted namespaces from appearing in normal operations while preserving them for audit purposes.

---

### Rule R-5: Null Return for Non-Existent Namespaces

**Rule**: Retrieval operations return nothing (no result) when a namespace does not exist or is deleted, rather than raising an error.

**Rationale**: Distinguishes between "not found" and error conditions, enabling graceful handling in application code.

---

## Event Sourcing Rules

### Rule E-1: All Mutations Generate Events

**Rule**: All namespace mutations (create, update, delete) must generate corresponding events that are recorded in the namespace management event stream.

**Events**:
- Namespace created event - recorded on namespace creation
- Namespace updated event - recorded on namespace update
- Namespace deleted event - recorded on namespace deletion

**Rationale**: Enables event-sourced namespace management with full audit trail, time-travel queries, and the ability to rebuild namespace state from event history.

---

### Rule E-2: Event Payload Completeness

**Rule**: Event payloads must contain all information necessary to rebuild namespace state from events.

**Rationale**: Ensures namespace state can be accurately reconstructed from event history.

**Content**: Events include namespace identifier, tenant identifier, name, description (if present), metadata, timestamps, and audit information (who performed the operation).

---

### Rule E-3: Event Timestamp Alignment

**Rule**: Event timestamps must match the operation timestamps (creation, update, deletion).

**Rationale**: Ensures consistency between namespace state and event history.

---

## Audit and Tracking Rules

### Rule A-1: Creation Audit Tracking

**Rule**: Namespace creation must track who created the namespace and when.

**Tracking Fields**:
- Creation timestamp: When the namespace was created
- Creator identity: Who created the namespace (defaults to "system" if not specified)

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-2: Update Audit Tracking

**Rule**: Namespace updates must track who updated the namespace and when.

**Tracking Fields**:
- Update timestamp: When the namespace was last updated
- Updater identity: Who updated the namespace (defaults to "system" if not specified)

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-3: Deletion Audit Tracking

**Rule**: Namespace deletions must track who deleted the namespace, when, and optionally why.

**Tracking Fields**:
- Deletion timestamp: When the namespace was deleted
- Deleter identity: Who deleted the namespace (defaults to "system" if not specified)
- Deletion reason: Optional explanation for why the namespace was deleted

**Rationale**: Provides audit trail for compliance and operational tracking.

---

### Rule A-4: Immutable Audit Fields

**Rule**: Creation audit fields (timestamp and creator) are immutable after namespace creation. Update audit fields are updated only on successful updates.

**Rationale**: Maintains accurate historical record of namespace lifecycle.

---

## Summary of Rule Violations

When business rules are violated, errors are raised to prevent the invalid operation. The following table summarizes common violation scenarios:

| Violation | When Raised | Related Rules |
|-----------|-------------|---------------|
| Namespace name already exists | Attempting to create or rename to an existing namespace name within the tenant | C-1, U-4 |
| Invalid namespace name format | Namespace name violates format requirements | C-2, U-3 |
| Tenant does not exist | Attempting to create a namespace for a non-existent tenant | C-3 |
| Tenant quota exceeded | Attempting to create a namespace when tenant's namespace quota limit is exceeded | C-10 |
| Namespace does not exist | Attempting to update or delete a non-existent namespace | U-1, D-1 |
| Cannot update deleted namespace | Attempting to update a namespace that has been deleted | U-2 |
| Blank namespace name | Namespace name is blank or empty | DM-2 |

---

## Related Documentation

- `/docs/business-rules/TenantManagementBusinessRules.md` - Tenant management business rules
- `/docs/Tenant and Namespace Requirements.md` - System design and architecture decisions
