package com.eventstore.domain.events

import java.time.Instant
import java.util.*

object NamespaceEventType {
    const val CREATED = "namespace.created"
    const val UPDATED = "namespace.updated"
    const val DELETED = "namespace.deleted"
}

sealed interface NamespaceEventPayload {
    val type: String
    fun toPayload(): Map<String, Any>
}

data class NamespaceCreatedEvent(
    val namespaceId: UUID,        // Stable GUID, never changes (used in permissions)
    val tenantId: UUID,           // Reference to tenant's tenantId (stable)
    val name: String,             // Human-readable identifier (used in URLs and for display)
    val description: String? = null,
    val createdBy: String = "system",
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) : NamespaceEventPayload {
    init {
        require(name.isNotBlank()) { "name is required" }
    }

    override val type: String = NamespaceEventType.CREATED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("namespaceId", namespaceId.toString())
        put("tenantId", tenantId.toString())
        put("name", name)
        description?.let { put("description", it) }
        put("createdBy", createdBy)
        put("createdAt", createdAt.toString())
        put("metadata", metadata)
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceCreatedEvent {
            val namespaceId = (payload["namespaceId"] as? String)?.let { UUID.fromString(it) }
                ?: error("namespaceId missing - cannot create namespace without stable identifier")
            val tenantId = (payload["tenantId"] as? String)?.let { UUID.fromString(it) }
                ?: error("tenantId missing - cannot create namespace without tenant reference")
            val name = payload["name"] as? String
                ?: error("name is required")
            val description = payload["description"] as? String
            val createdBy = payload["createdBy"] as? String ?: "system"
            val createdAt = parseInstant(payload["createdAt"])
            val metadata = payload["metadata"] as? Map<String, Any> ?: emptyMap()
            return NamespaceCreatedEvent(
                namespaceId,
                tenantId,
                name,
                description,
                createdBy,
                createdAt,
                metadata
            )
        }
    }
}

data class NamespaceUpdatedEvent(
    val namespaceId: UUID,        // Stable GUID reference (used to identify namespace)
    val name: String? = null,     // Human-readable identifier (may change on rename)
    val description: String? = null,
    val updatedBy: String = "system",
    val updatedAt: Instant,
    val metadata: Map<String, Any>? = null
) : NamespaceEventPayload {
    override val type: String = NamespaceEventType.UPDATED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("namespaceId", namespaceId.toString())
        name?.let { put("name", it) }
        description?.let { put("description", it) }
        put("updatedBy", updatedBy)
        put("updatedAt", updatedAt.toString())
        metadata?.let { put("metadata", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceUpdatedEvent {
            val namespaceId = (payload["namespaceId"] as? String)?.let { UUID.fromString(it) }
                ?: error("namespaceId missing - cannot update namespace without stable identifier")
            val name = payload["name"] as? String
            val description = payload["description"] as? String
            val updatedBy = payload["updatedBy"] as? String ?: "system"
            val updatedAt = parseInstant(payload["updatedAt"])
            val metadata = payload["metadata"] as? Map<String, Any>
            return NamespaceUpdatedEvent(
                namespaceId,
                name,
                description,
                updatedBy,
                updatedAt,
                metadata
            )
        }
    }
}

data class NamespaceDeletedEvent(
    val namespaceId: UUID,        // Stable GUID reference (used to identify namespace)
    val deletedBy: String = "system",
    val deletedAt: Instant,
    val reason: String? = null
) : NamespaceEventPayload {
    override val type: String = NamespaceEventType.DELETED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("namespaceId", namespaceId.toString())
        put("deletedBy", deletedBy)
        put("deletedAt", deletedAt.toString())
        reason?.let { put("reason", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceDeletedEvent {
            val namespaceId = (payload["namespaceId"] as? String)?.let { UUID.fromString(it) }
                ?: error("namespaceId missing - cannot delete namespace without stable identifier")
            val deletedBy = payload["deletedBy"] as? String ?: "system"
            val deletedAt = parseInstant(payload["deletedAt"])
            val reason = payload["reason"] as? String
            return NamespaceDeletedEvent(namespaceId, deletedBy, deletedAt, reason)
        }
    }
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}
