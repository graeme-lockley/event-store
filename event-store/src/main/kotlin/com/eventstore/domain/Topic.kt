package com.eventstore.domain

import java.util.UUID

/**
 * Domain entity representing a topic with its configuration.
 * 
 * - resourceId: Stable UUID that never changes (used in permissions and references)
 * - tenantResourceId: Reference to tenant's stable resourceId
 * - namespaceResourceId: Reference to namespace's stable resourceId
 * - tenantName: Human-readable tenant name (for URLs/display)
 * - namespaceName: Human-readable namespace name (for URLs/display)
 * - name: Human-readable topic name (used in URLs and for display, can be renamed with migration)
 */
data class Topic(
    val resourceId: UUID,        // Stable GUID, never changes (used in permissions)
    val tenantResourceId: UUID,   // Reference to tenant's resourceId (stable)
    val namespaceResourceId: UUID, // Reference to namespace's resourceId (stable)
    val name: String,            // Human-readable topic name (used in URLs and for display)
    val sequence: Long,
    val schemas: List<Schema>,
    val tenantName: String = "default",
    val namespaceName: String = "default"
) {
    init {
        require(name.isNotBlank()) { "Topic name is required" }
        require(sequence >= 0) { "Sequence must be non-negative" }
        require(tenantName.isNotBlank()) { "Tenant name is required" }
        require(namespaceName.isNotBlank()) { "Namespace name is required" }
    }

    fun nextSequence(): Long = sequence + 1

    fun updateSequence(newSequence: Long): Topic {
        return copy(sequence = newSequence)
    }

    fun updateSchemas(newSchemas: List<Schema>): Topic {
        return copy(schemas = newSchemas)
    }

    fun qualifiedName(): String = "$tenantName/$namespaceName/$name"
}

