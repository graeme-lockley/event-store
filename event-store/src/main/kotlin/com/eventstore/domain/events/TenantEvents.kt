package com.eventstore.domain.events

import com.eventstore.domain.Quota
import java.time.Instant
import java.util.UUID

/**
 * Event payload helpers for tenant lifecycle events.
 */
object TenantEventType {
    const val CREATED = "tenant.created"
    const val UPDATED = "tenant.updated"
    const val DELETED = "tenant.deleted"
}

sealed interface TenantEventPayload {
    val type: String
    fun toPayload(): Map<String, Any>
}

data class TenantCreatedEvent(
    val resourceId: UUID,        // Stable GUID, never changes (used in permissions)
    val name: String,            // Human-readable identifier (used in URLs and for display)
    val quota: Quota? = null,
    val createdBy: String = "system",
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) : TenantEventPayload {
    init {
        require(name.isNotBlank()) { "name is required" }
    }

    override val type: String = TenantEventType.CREATED

    override fun toPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "resourceId" to resourceId.toString(),
            "name" to name,
            "createdBy" to createdBy,
            "createdAt" to createdAt.toString(),
            "metadata" to metadata
        )
        quota?.let { payload["quota"] = it.toMap() }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): TenantCreatedEvent {
            // Support both old format (without resourceId) and new format (with resourceId)
            val resourceId = (payload["resourceId"] as? String)?.let { UUID.fromString(it) }
                ?: UUID.randomUUID() // Generate UUID for backward compatibility
            // Support old format with tenantId field for backward compatibility
            val name = (payload["name"] as? String) ?: (payload["tenantId"] as? String) 
                ?: error("name is required")
            val createdBy = payload["createdBy"] as? String ?: "system"
            val createdAt = parseInstant(payload["createdAt"])
            val metadata = payload["metadata"] as? Map<String, Any> ?: emptyMap()
            val quota = (payload["quota"] as? Map<*, *>)?.let { mapToQuota(it) }

            return TenantCreatedEvent(
                resourceId = resourceId,
                name = name,
                quota = quota,
                createdBy = createdBy,
                createdAt = createdAt,
                metadata = metadata
            )
        }
    }
}

data class TenantUpdatedEvent(
    val resourceId: UUID,        // Stable GUID reference (used to identify tenant)
    val name: String?,           // Human-readable identifier (may change on rename)
    val quota: Quota? = null,
    val updatedBy: String = "system",
    val updatedAt: Instant,
    val metadata: Map<String, Any>? = null
) : TenantEventPayload {
    override val type: String = TenantEventType.UPDATED

    override fun toPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "resourceId" to resourceId.toString(),
            "updatedBy" to updatedBy,
            "updatedAt" to updatedAt.toString()
        )
        name?.let { payload["name"] = it }
        metadata?.let { payload["metadata"] = it }
        quota?.let { payload["quota"] = it.toMap() }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): TenantUpdatedEvent {
            // Support both old format (without resourceId) and new format (with resourceId)
            val resourceId = (payload["resourceId"] as? String)?.let { UUID.fromString(it) }
                ?: error("resourceId missing - cannot update tenant without stable identifier")
            val name = payload["name"] as? String
            val updatedBy = payload["updatedBy"] as? String ?: "system"
            val updatedAt = parseInstant(payload["updatedAt"])
            val metadata = payload["metadata"] as? Map<String, Any>
            val quota = (payload["quota"] as? Map<*, *>)?.let { mapToQuota(it) }

            return TenantUpdatedEvent(
                resourceId = resourceId,
                name = name,
                quota = quota,
                updatedBy = updatedBy,
                updatedAt = updatedAt,
                metadata = metadata
            )
        }
    }
}

data class TenantDeletedEvent(
    val resourceId: UUID,        // Stable GUID reference (used to identify tenant)
    val deletedBy: String = "system",
    val deletedAt: Instant,
    val reason: String? = null
) : TenantEventPayload {
    override val type: String = TenantEventType.DELETED

    override fun toPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "resourceId" to resourceId.toString(),
            "deletedBy" to deletedBy,
            "deletedAt" to deletedAt.toString()
        )
        reason?.let { payload["reason"] = it }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): TenantDeletedEvent {
            // Support both old format (without resourceId) and new format (with resourceId)
            val resourceId = (payload["resourceId"] as? String)?.let { UUID.fromString(it) }
                ?: error("resourceId missing - cannot delete tenant without stable identifier")
            val deletedBy = payload["deletedBy"] as? String ?: "system"
            val deletedAt = parseInstant(payload["deletedAt"])
            val reason = payload["reason"] as? String

            return TenantDeletedEvent(
                resourceId = resourceId,
                deletedBy = deletedBy,
                deletedAt = deletedAt,
                reason = reason
            )
        }
    }
}

private fun Quota.toMap(): Map<String, Any> = mapOf(
    "maxTopics" to maxTopics,
    "maxNamespaces" to maxNamespaces,
    "maxEventsPerDay" to maxEventsPerDay,
    "maxConsumers" to maxConsumers,
    "maxUsers" to maxUsers,
    "maxEventSizeBytes" to maxEventSizeBytes
)

private fun mapToQuota(map: Map<*, *>): Quota {
    fun numberToInt(value: Any?, field: String): Int {
        val number = value as? Number ?: error("$field must be a number")
        return number.toInt()
    }

    fun numberToLong(value: Any?, field: String): Long {
        val number = value as? Number ?: error("$field must be a number")
        return number.toLong()
    }

    return Quota(
        maxTopics = numberToInt(map["maxTopics"], "maxTopics"),
        maxNamespaces = numberToInt(map["maxNamespaces"], "maxNamespaces"),
        maxEventsPerDay = numberToLong(map["maxEventsPerDay"], "maxEventsPerDay"),
        maxConsumers = numberToInt(map["maxConsumers"], "maxConsumers"),
        maxUsers = numberToInt(map["maxUsers"], "maxUsers"),
        maxEventSizeBytes = numberToLong(map["maxEventSizeBytes"], "maxEventSizeBytes")
    )
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}

