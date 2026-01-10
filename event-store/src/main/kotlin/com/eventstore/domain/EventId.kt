package com.eventstore.domain

private val TENANT_PATTERN = Regex("^[^/]+/[^/]+/.+-[0-9]+$")

/**
 * Value object representing a globally unique event ID.
 *
 * Format: <tenant>/<namespace>/<topic>-<sequence> (e.g., "acme/default/users-42")
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

    /**
     * Secondary constructor to parse from string format.
     * Maintains backward compatibility with EventId("string") syntax.
     */
    constructor(value: String) : this(
        tenantId = value.substringBefore("/"),
        namespaceId = value.substringAfter("/").substringBefore("/"),
        topicId = value.substringAfter("/").substringAfter("/").substringBeforeLast("-"),
        sequence = value.substringAfterLast("-").toLong()
    ) {
        require(value.matches(TENANT_PATTERN)) {
            "Event ID must be in format '<tenant>/<namespace>/<topic>-<sequence>'"
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

    override fun toString(): String = "$tenantId/$namespaceId/$topicId-$sequence"

    companion object {
        /**
         * Creates an EventId from its components.
         */
        fun create(topic: String, sequence: Long, tenantId: String, namespaceId: String): EventId {
            return EventId(tenantId, namespaceId, topic, sequence)
        }

        /**
         * Parses an EventId from a string in format <tenant>/<namespace>/<topic>-<sequence>
         */
        fun parse(value: String): EventId {
            return EventId(value)
        }
    }
}

