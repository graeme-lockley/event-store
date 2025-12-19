package com.eventstore.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a tenant.
 * 
 * - resourceId: Stable UUID that never changes (used in permissions and references)
 * - name: Human-readable identifier used in URLs and for display (can be renamed with migration)
 */
data class Tenant(
    val resourceId: UUID,        // Stable GUID, never changes (used in permissions)
    val name: String,            // Human-readable identifier (used in URLs and for display)
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val quota: Quota? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { "Tenant name is required" }
    }

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
) {
    init {
        require(maxTopics > 0) { "Quota maxTopics must be positive" }
        require(maxNamespaces > 0) { "Quota maxNamespaces must be positive" }
        require(maxEventsPerDay > 0) { "Quota maxEventsPerDay must be positive" }
        require(maxConsumers > 0) { "Quota maxConsumers must be positive" }
        require(maxUsers > 0) { "Quota maxUsers must be positive" }
        require(maxEventSizeBytes > 0) { "Quota maxEventSizeBytes must be positive" }
    }
}

