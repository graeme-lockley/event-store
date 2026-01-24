package com.eventstore.domain

import java.util.*

/**
 * Domain entity representing a topic with its configuration.
 *
 * - topicId: Stable UUID that never changes (used in permissions and references). Globally unique.
 * - namespaceId: Reference to namespace's stable UUID (globally unique)
 * - name: Human-readable topic name (used for display purposes)
 * - sequence: Current sequence number for event ordering
 * - schemas: List of schemas defining event types for this topic
 */
data class Topic(
    // Stable GUID, never changes (used in permissions). Globally unique.
    val topicId: UUID,
    // Reference to namespace's UUID (globally unique)
    val namespaceId: UUID,
    // Human-readable topic name (used for display)
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>,
) {
    init {
        require(name.isNotBlank()) { "Topic name is required" }
        require(sequence >= 0) { "Sequence must be non-negative" }
    }

    fun nextSequence(): Long = sequence + 1

    fun updateSequence(newSequence: Long): Topic {
        return copy(sequence = newSequence)
    }

    fun updateSchemas(newSchemas: List<Schema>): Topic {
        return copy(schemas = newSchemas)
    }
}
