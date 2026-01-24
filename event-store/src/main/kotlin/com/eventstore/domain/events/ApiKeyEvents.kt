package com.eventstore.domain.events

import java.time.Instant

object ApiKeyEventType {
    const val CREATED = "api-key.created"
    const val REVOKED = "api-key.revoked"
}

sealed interface ApiKeyEventPayload {
    val type: String

    fun toPayload(): Map<String, Any>
}

data class ApiKeyCreatedEvent(
    val apiKeyId: String,
    val userId: String,
    val keyHash: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val scopes: Set<String>? = null,
    val createdBy: String = "system",
) : ApiKeyEventPayload {
    override val type: String = ApiKeyEventType.CREATED

    override fun toPayload(): Map<String, Any> =
        buildMap {
            put("apiKeyId", apiKeyId)
            put("userId", userId)
            put("keyHash", keyHash)
            put("name", name)
            description?.let { put("description", it) }
            put("createdAt", createdAt.toString())
            expiresAt?.let { put("expiresAt", it.toString()) }
            scopes?.let { put("scopes", it.toList()) }
            put("createdBy", createdBy)
        }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): ApiKeyCreatedEvent {
            val apiKeyId = payload["apiKeyId"] as? String ?: error("apiKeyId missing")
            val userId = payload["userId"] as? String ?: error("userId missing")
            val keyHash = payload["keyHash"] as? String ?: error("keyHash missing")
            val name = payload["name"] as? String ?: error("name missing")
            val description = payload["description"] as? String
            val createdAt = parseInstant(payload["createdAt"])
            val expiresAt = payload["expiresAt"]?.let { parseInstant(it) }
            val scopes = (payload["scopes"] as? List<*>)?.mapNotNull { it as? String }?.toSet()
            val createdBy = payload["createdBy"] as? String ?: "system"
            return ApiKeyCreatedEvent(
                apiKeyId = apiKeyId,
                userId = userId,
                keyHash = keyHash,
                name = name,
                description = description,
                createdAt = createdAt,
                expiresAt = expiresAt,
                scopes = scopes,
                createdBy = createdBy,
            )
        }
    }
}

data class ApiKeyRevokedEvent(
    val apiKeyId: String,
    val revokedBy: String = "system",
    val revokedAt: Instant,
    val reason: String? = null,
) : ApiKeyEventPayload {
    override val type: String = ApiKeyEventType.REVOKED

    override fun toPayload(): Map<String, Any> =
        buildMap {
            put("apiKeyId", apiKeyId)
            put("revokedBy", revokedBy)
            put("revokedAt", revokedAt.toString())
            reason?.let { put("reason", it) }
        }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): ApiKeyRevokedEvent {
            val apiKeyId = payload["apiKeyId"] as? String ?: error("apiKeyId missing")
            val revokedBy = payload["revokedBy"] as? String ?: "system"
            val revokedAt = parseInstant(payload["revokedAt"])
            val reason = payload["reason"] as? String
            return ApiKeyRevokedEvent(
                apiKeyId = apiKeyId,
                revokedBy = revokedBy,
                revokedAt = revokedAt,
                reason = reason,
            )
        }
    }
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}
