package com.eventstore.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a namespace.
 * 
 * - resourceId: Stable UUID that never changes (used in permissions and references)
 * - tenantResourceId: Reference to tenant's stable resourceId
 * - tenantName: Human-readable tenant name (for URLs/display)
 * - name: Human-readable identifier used in URLs and for display (can be renamed with migration)
 */
data class Namespace(
    val resourceId: UUID,        // Stable GUID, never changes (used in permissions)
    val tenantResourceId: UUID,   // Reference to tenant's resourceId (stable)
    val tenantName: String,      // Human-readable tenant name (for URLs/display)
    val name: String,            // Human-readable identifier (used in URLs and for display)
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

