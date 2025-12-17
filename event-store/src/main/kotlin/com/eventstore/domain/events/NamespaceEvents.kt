package com.eventstore.domain.events

import java.time.Instant

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
    val tenantId: String,
    val namespaceId: String,
    val name: String,
    val description: String? = null,
    val createdBy: String = "system",
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) : NamespaceEventPayload {
    init {
        require(tenantId.isNotBlank()) { "tenantId is required" }
        require(namespaceId.isNotBlank()) { "namespaceId is required" }
        require(name.isNotBlank()) { "name is required" }
    }

    override val type: String = NamespaceEventType.CREATED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("tenantId", tenantId)
        put("namespaceId", namespaceId)
        put("name", name)
        description?.let { put("description", it) }
        put("createdBy", createdBy)
        put("createdAt", createdAt.toString())
        put("metadata", metadata)
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceCreatedEvent {
            val tenantId = payload["tenantId"] as? String ?: error("tenantId missing")
            val namespaceId = payload["namespaceId"] as? String ?: error("namespaceId missing")
            val name = payload["name"] as? String ?: error("name missing")
            val description = payload["description"] as? String
            val createdBy = payload["createdBy"] as? String ?: "system"
            val createdAt = parseInstant(payload["createdAt"])
            val metadata = payload["metadata"] as? Map<String, Any> ?: emptyMap()
            return NamespaceCreatedEvent(tenantId, namespaceId, name, description, createdBy, createdAt, metadata)
        }
    }
}

data class NamespaceUpdatedEvent(
    val tenantId: String,
    val namespaceId: String,
    val name: String? = null,
    val description: String? = null,
    val updatedBy: String = "system",
    val updatedAt: Instant,
    val metadata: Map<String, Any>? = null
) : NamespaceEventPayload {
    init {
        require(tenantId.isNotBlank()) { "tenantId is required" }
        require(namespaceId.isNotBlank()) { "namespaceId is required" }
    }

    override val type: String = NamespaceEventType.UPDATED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("tenantId", tenantId)
        put("namespaceId", namespaceId)
        name?.let { put("name", it) }
        description?.let { put("description", it) }
        put("updatedBy", updatedBy)
        put("updatedAt", updatedAt.toString())
        metadata?.let { put("metadata", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceUpdatedEvent {
            val tenantId = payload["tenantId"] as? String ?: error("tenantId missing")
            val namespaceId = payload["namespaceId"] as? String ?: error("namespaceId missing")
            val name = payload["name"] as? String
            val description = payload["description"] as? String
            val updatedBy = payload["updatedBy"] as? String ?: "system"
            val updatedAt = parseInstant(payload["updatedAt"])
            val metadata = payload["metadata"] as? Map<String, Any>
            return NamespaceUpdatedEvent(tenantId, namespaceId, name, description, updatedBy, updatedAt, metadata)
        }
    }
}

data class NamespaceDeletedEvent(
    val tenantId: String,
    val namespaceId: String,
    val deletedBy: String = "system",
    val deletedAt: Instant,
    val reason: String? = null
) : NamespaceEventPayload {
    init {
        require(tenantId.isNotBlank()) { "tenantId is required" }
        require(namespaceId.isNotBlank()) { "namespaceId is required" }
    }

    override val type: String = NamespaceEventType.DELETED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("tenantId", tenantId)
        put("namespaceId", namespaceId)
        put("deletedBy", deletedBy)
        put("deletedAt", deletedAt.toString())
        reason?.let { put("reason", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): NamespaceDeletedEvent {
            val tenantId = payload["tenantId"] as? String ?: error("tenantId missing")
            val namespaceId = payload["namespaceId"] as? String ?: error("namespaceId missing")
            val deletedBy = payload["deletedBy"] as? String ?: "system"
            val deletedAt = parseInstant(payload["deletedAt"])
            val reason = payload["reason"] as? String
            return NamespaceDeletedEvent(tenantId, namespaceId, deletedBy, deletedAt, reason)
        }
    }
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}

