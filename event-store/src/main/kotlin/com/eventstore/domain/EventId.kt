package com.eventstore.domain

/**
 * Value object representing a globally unique event ID.
 *
 * Supported formats (backward compatible):
 * - Legacy: <topic>-<sequence> (e.g., "user-events-42")
 * - Tenant-scoped: <tenant>/<namespace>/<topic>-<sequence> (e.g., "acme/default/users-42")
 */
data class EventId(val value: String) {
    init {
        require(value.matches(LEGACY_PATTERN) || value.matches(TENANT_PATTERN)) {
            "Event ID must be in format '<topic>-<sequence>' or '<tenant>/<namespace>/<topic>-<sequence>'"
        }
    }

    val tenantId: String?
        get() = if (TENANT_PATTERN.matches(value)) value.substringBefore("/") else null

    val namespaceId: String?
        get() = if (TENANT_PATTERN.matches(value)) {
            value.substringAfter("/").substringBefore("/")
        } else {
            null
        }

    val topic: String
        get() = if (TENANT_PATTERN.matches(value)) {
            value.substringAfter("$tenantId/$namespaceId/").substringBeforeLast("-")
        } else {
            value.substringBeforeLast("-")
        }

    val sequence: Long
        get() = value.substringAfterLast("-").toLong()

    val isTenantScoped: Boolean
        get() = TENANT_PATTERN.matches(value)

    override fun toString(): String = value

    companion object {
        private val LEGACY_PATTERN = Regex("^.+-[0-9]+$")
        private val TENANT_PATTERN = Regex("^[^/]+/[^/]+/.+-[0-9]+$")

        fun create(topic: String, sequence: Long, tenantId: String? = null, namespaceId: String? = null): EventId {
            return if (tenantId != null && namespaceId != null) {
                EventId("$tenantId/$namespaceId/$topic-$sequence")
            } else {
                EventId("$topic-$sequence")
            }
        }
    }
}

