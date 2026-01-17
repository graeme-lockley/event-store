package com.eventstore.domain

import java.time.Instant
import java.util.*

/**
 * Domain entity representing a namespace.
 * 
 * - namespaceId: Stable UUID that never changes (used in permissions and references)
 * - tenantId: Reference to tenant's stable tenantId
 * - tenantName: Human-readable tenant name (for URLs/display)
 * - name: Human-readable identifier used in URLs and for display (can be renamed with migration)
 */
data class Namespace(
    val namespaceId: UUID,        // Stable GUID, never changes (used in permissions)
    val tenantId: UUID,           // Reference to tenant's tenantId (stable)
    val tenantName: String,       // Human-readable tenant name (for URLs/display)
    val name: String,             // Human-readable identifier (used in URLs and for display)
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(tenantName.isNotBlank()) { "Tenant name is required" }
        require(name.isNotBlank()) { "Namespace name is required" }
    }

    val isActive: Boolean
        get() = deletedAt == null

    fun qualifiedName(): String = "$tenantName/$name"
}

