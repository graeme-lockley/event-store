package com.eventstore.domain.events

import com.eventstore.domain.UserStatus
import java.time.Instant

object UserEventType {
    const val CREATED = "user.created"
    const val UPDATED = "user.updated"
    const val STATUS_CHANGED = "user.status.changed"
    const val PASSWORD_CHANGED = "user.password.changed"
    const val TENANT_ASSIGNED = "user.tenant.assigned"
    const val TENANT_REMOVED = "user.tenant.removed"
}

sealed interface UserEventPayload {
    val type: String
    fun toPayload(): Map<String, Any>
}

data class UserCreatedEvent(
    val userId: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdBy: String = "system",
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) : UserEventPayload {
    override val type: String = UserEventType.CREATED

    override fun toPayload(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email,
        "name" to name,
        "passwordHash" to passwordHash,
        "status" to status.name,
        "createdBy" to createdBy,
        "createdAt" to createdAt.toString(),
        "metadata" to metadata
    )

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserCreatedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val email = payload["email"] as? String ?: error("email missing")
            val name = payload["name"] as? String ?: error("name missing")
            val passwordHash = payload["passwordHash"] as? String ?: error("passwordHash missing")
            val status = (payload["status"] as? String)?.let { UserStatus.valueOf(it) } ?: UserStatus.ACTIVE
            val createdBy = payload["createdBy"] as? String ?: "system"
            val createdAt = parseInstant(payload["createdAt"])
            val metadata = payload["metadata"] as? Map<String, Any> ?: emptyMap()
            return UserCreatedEvent(userId, email, name, passwordHash, status, createdBy, createdAt, metadata)
        }
    }
}

data class UserUpdatedEvent(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val updatedBy: String = "system",
    val updatedAt: Instant,
    val metadata: Map<String, Any>? = null
) : UserEventPayload {
    override val type: String = UserEventType.UPDATED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("userId", userId)
        email?.let { put("email", it) }
        name?.let { put("name", it) }
        put("updatedBy", updatedBy)
        put("updatedAt", updatedAt.toString())
        metadata?.let { put("metadata", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserUpdatedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val email = payload["email"] as? String
            val name = payload["name"] as? String
            val updatedBy = payload["updatedBy"] as? String ?: "system"
            val updatedAt = parseInstant(payload["updatedAt"])
            val metadata = payload["metadata"] as? Map<String, Any>
            return UserUpdatedEvent(userId, email, name, updatedBy, updatedAt, metadata)
        }
    }
}

data class UserStatusChangedEvent(
    val userId: String,
    val status: UserStatus,
    val changedBy: String = "system",
    val changedAt: Instant
) : UserEventPayload {
    override val type: String = UserEventType.STATUS_CHANGED

    override fun toPayload(): Map<String, Any> = mapOf(
        "userId" to userId,
        "status" to status.name,
        "changedBy" to changedBy,
        "changedAt" to changedAt.toString()
    )

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserStatusChangedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val status = (payload["status"] as? String)?.let { UserStatus.valueOf(it) } ?: error("status missing")
            val changedBy = payload["changedBy"] as? String ?: "system"
            val changedAt = parseInstant(payload["changedAt"])
            return UserStatusChangedEvent(userId, status, changedBy, changedAt)
        }
    }
}

data class UserPasswordChangedEvent(
    val userId: String,
    val passwordHash: String,
    val changedBy: String = "system",
    val changedAt: Instant
) : UserEventPayload {
    override val type: String = UserEventType.PASSWORD_CHANGED

    override fun toPayload(): Map<String, Any> = mapOf(
        "userId" to userId,
        "passwordHash" to passwordHash,
        "changedBy" to changedBy,
        "changedAt" to changedAt.toString()
    )

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserPasswordChangedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val passwordHash = payload["passwordHash"] as? String ?: error("passwordHash missing")
            val changedBy = payload["changedBy"] as? String ?: "system"
            val changedAt = parseInstant(payload["changedAt"])
            return UserPasswordChangedEvent(userId, passwordHash, changedBy, changedAt)
        }
    }
}

data class UserTenantAssignedEvent(
    val userId: String,
    val tenantId: String,
    val role: String? = null,
    val assignedBy: String = "system",
    val assignedAt: Instant,
    val isPrimary: Boolean = false
) : UserEventPayload {
    override val type: String = UserEventType.TENANT_ASSIGNED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("tenantId", tenantId)
        role?.let { put("role", it) }
        put("assignedBy", assignedBy)
        put("assignedAt", assignedAt.toString())
        put("isPrimary", isPrimary)
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserTenantAssignedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val tenantId = payload["tenantId"] as? String ?: error("tenantId missing")
            val role = payload["role"] as? String
            val assignedBy = payload["assignedBy"] as? String ?: "system"
            val assignedAt = parseInstant(payload["assignedAt"])
            val isPrimary = payload["isPrimary"] as? Boolean ?: false
            return UserTenantAssignedEvent(userId, tenantId, role, assignedBy, assignedAt, isPrimary)
        }
    }
}

data class UserTenantRemovedEvent(
    val userId: String,
    val tenantId: String,
    val removedBy: String = "system",
    val removedAt: Instant,
    val reason: String? = null
) : UserEventPayload {
    override val type: String = UserEventType.TENANT_REMOVED

    override fun toPayload(): Map<String, Any> = buildMap {
        put("userId", userId)
        put("tenantId", tenantId)
        put("removedBy", removedBy)
        put("removedAt", removedAt.toString())
        reason?.let { put("reason", it) }
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): UserTenantRemovedEvent {
            val userId = payload["userId"] as? String ?: error("userId missing")
            val tenantId = payload["tenantId"] as? String ?: error("tenantId missing")
            val removedBy = payload["removedBy"] as? String ?: "system"
            val removedAt = parseInstant(payload["removedAt"])
            val reason = payload["reason"] as? String
            return UserTenantRemovedEvent(userId, tenantId, removedBy, removedAt, reason)
        }
    }
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}

