package com.eventstore.domain

/**
 * Value object representing a globally unique event ID.
 *
 * Format: <tenant>/<namespace>/<topic>/<sequence> (e.g., "acme/default/users/42")
 */
data class EventId(
    val tenantId: String,
    val namespaceId: String,
    val topicId: String,
    val sequence: Long
) {
    init {
        require(tenantId.isNotBlank()) { "Tenant ID is required" }
        require(namespaceId.isNotBlank()) { "Namespace ID is required" }
        require(topicId.isNotBlank()) { "Topic ID is required" }
        require(sequence >= 0) { "Sequence must be non-negative" }
    }

    companion object {
        private val TENANT_PATTERN = Regex("^[^/]+/[^/]+/[^/]+/[0-9]+$")

        /**
         * Parses an EventId from a string format.
         * The string is scanned once and parsed once for maximum efficiency.
         * Format: <tenant>/<namespace>/<topic>/<sequence> (e.g., "acme/default/users/42")
         */
        fun fromString(value: String): EventId {
            require(value.matches(TENANT_PATTERN)) {
                "Event ID must be in format '<tenant>/<namespace>/<topic>/<sequence>'"
            }

            // Single scan and parse: split once and extract all components
            val parts = value.split("/", limit = 4)
            require(parts.size == 4) {
                "Event ID must have exactly 4 parts separated by '/': '<tenant>/<namespace>/<topic>/<sequence>'"
            }

            return EventId(
                tenantId = parts[0],
                namespaceId = parts[1],
                topicId = parts[2],
                sequence = parts[3].toLong()
            )
        }

        /**
         * Creates an EventId from its components.
         */
        fun create(topic: String, sequence: Long, tenantId: String, namespaceId: String): EventId {
            return EventId(tenantId, namespaceId, topic, sequence)
        }
    }

    /**
     * Computed property for backward compatibility.
     * Returns the formatted string representation.
     */
    val value: String
        get() = toString()

    val qualifiedTopic: String
        get() = "$tenantId/$namespaceId/$topicId"

    override fun toString(): String = "$tenantId/$namespaceId/$topicId/$sequence"
}

