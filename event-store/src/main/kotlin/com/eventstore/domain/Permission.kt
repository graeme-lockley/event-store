package com.eventstore.domain

/**
 * Simplified permission model with generic CRUD operations that apply to any resource type.
 */
enum class Permission {
    // Generic CRUD operations (apply to any resource type)
    CREATE,
    READ,
    LIST,
    UPDATE,
    DELETE,
    
    // Admin permission (full control over resource type)
    ADMIN,
    
    // Resource-specific permissions
    PERMISSION_GRANT,      // For tenants, namespaces, topics
    PERMISSION_REVOKE,     // For tenants, namespaces, topics
    SCHEMA_MANAGE,         // For topics (can also be granted at namespace/tenant level)
    READ_HISTORY,          // For events only
    READ_EXPORT,           // For events only
    WRITE_ADMIN,           // For events only
    REPLAY,                // For events only
    PURGE,                 // For events only
    ACTIVATE,              // For users only
    SUSPEND,               // For users only
    PASSWORD_RESET,        // For users only
    MANAGE                 // For consumers only
}

/**
 * Type of principal that can have permissions granted.
 */
enum class PrincipalType {
    USER,
    API_KEY,
    ROLE,
    GROUP
}

/**
 * Type of resource that permissions can be granted for.
 */
enum class ResourceType {
    TENANT,
    NAMESPACE,
    TOPIC,
    EVENT,
    CONSUMER,
    USER
}

/**
 * Constraints that can be applied to permissions.
 */
data class PermissionConstraints(
    val eventTypes: Set<String>? = null,
    val maxAgeDays: Int? = null,
    val timeBased: TimeBasedConstraint? = null
)

/**
 * Time-based constraint for permissions.
 */
data class TimeBasedConstraint(
    val startTime: String? = null,  // ISO 8601 time
    val endTime: String? = null     // ISO 8601 time
)

/**
 * Represents a permission grant to a principal for a specific resource.
 *
 * - resourceId: UUID of specific resource, or null for all resources of this type
 * - tenantResourceId: UUID of tenant (for context and inheritance)
 * - namespaceResourceId: UUID of namespace (for context and inheritance)
 * - topicResourceId: UUID of topic (for context and inheritance)
 */
data class PermissionGrant(
    val principalId: String,              // UUID of user/API key/role/group
    val principalType: PrincipalType,     // USER, API_KEY, ROLE, GROUP
    val resourceType: ResourceType,       // TENANT, NAMESPACE, TOPIC, EVENT, CONSUMER, USER
    val resourceId: String? = null,       // UUID of specific resource, or null for all resources of this type
    val tenantResourceId: String,         // UUID of tenant (for context and inheritance)
    val namespaceResourceId: String? = null, // UUID of namespace (for context and inheritance)
    val topicResourceId: String? = null,   // UUID of topic (for context and inheritance)
    val permissions: Set<Permission>,
    val constraints: PermissionConstraints? = null,
    val grantedBy: String,                // UUID of user who granted permission
    val grantedAt: java.time.Instant,
    val expiresAt: java.time.Instant? = null
)

