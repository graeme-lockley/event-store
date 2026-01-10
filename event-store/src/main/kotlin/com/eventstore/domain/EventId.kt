package com.eventstore.domain

/**
 * Value object representing a globally unique event ID.
 *
 * Format: <tenant>/<namespace>/<topic>-<sequence> (e.g., "acme/default/users-42")
 */
data class EventId(val value: String) {
    init {
        require(value.matches(TENANT_PATTERN)) {
            "Event ID must be in format '<tenant>/<namespace>/<topic>-<sequence>'"
        }
    }

    val tenantId: String
        get() = value.substringBefore("/")

    val namespaceId: String
        get() = value.substringAfter("/").substringBefore("/")

    val topic: String
        get() = value.substringAfter("$tenantId/$namespaceId/").substringBeforeLast("-")

    val qualifiedTopic: String
        get() = value.substringBeforeLast("-")

    val sequence: Long
        get() = value.substringAfterLast("-").toLong()

    override fun toString(): String = value

    companion object {
        private val TENANT_PATTERN = Regex("^[^/]+/[^/]+/.+-[0-9]+$")

        fun create(topic: String, sequence: Long, tenantId: String, namespaceId: String): EventId {
            return EventId("$tenantId/$namespaceId/$topic-$sequence")
        }
    }
}

