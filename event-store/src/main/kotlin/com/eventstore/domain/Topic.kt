package com.eventstore.domain

/**
 * Domain entity representing a topic with its configuration.
 */
data class Topic(
    val name: String,
    val sequence: Long,
    val schemas: List<Schema>,
    val tenantId: String = "default",
    val namespaceId: String = "default"
) {
    init {
        require(name.isNotBlank()) { "Topic name is required" }
        require(sequence >= 0) { "Sequence must be non-negative" }
        require(tenantId.isNotBlank()) { "Tenant ID is required" }
        require(namespaceId.isNotBlank()) { "Namespace ID is required" }
    }

    fun nextSequence(): Long = sequence + 1

    fun updateSequence(newSequence: Long): Topic {
        return copy(sequence = newSequence)
    }

    fun updateSchemas(newSchemas: List<Schema>): Topic {
        return copy(schemas = newSchemas)
    }

    fun qualifiedName(): String = "$tenantId/$namespaceId/$name"
}

