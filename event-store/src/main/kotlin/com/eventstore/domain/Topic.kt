package com.eventstore.domain

/**
 * Domain entity representing a topic with its configuration.
 */
data class Topic(
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>
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

