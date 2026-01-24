package com.eventstore.domain.events

import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import java.time.Instant

object PermissionEventType {
    const val GRANTED = "permission.granted"
    const val REVOKED = "permission.revoked"
}

sealed interface PermissionEventPayload {
    val type: String

    fun toPayload(): Map<String, Any>
}

data class PermissionGrantedEvent(
    // UUID of user/API key/role/group
    val principalId: String,
    // USER, API_KEY, ROLE, GROUP
    val principalType: PrincipalType,
    // TENANT, NAMESPACE, TOPIC, EVENT, CONSUMER, USER
    val resourceType: ResourceType,
    // UUID of specific resource, or null for all resources of this type
    val resourceId: String? = null,
    // UUID of tenant (for context and inheritance)
    val tenantResourceId: String,
    // UUID of namespace (for context and inheritance)
    val namespaceResourceId: String? = null,
    // UUID of topic (for context and inheritance)
    val topicId: String? = null,
    val permissions: Set<Permission>,
    // UUID of user who granted permission
    val grantedBy: String,
    val grantedAt: Instant,
    val expiresAt: Instant? = null,
) : PermissionEventPayload {
    override val type: String = PermissionEventType.GRANTED

    override fun toPayload(): Map<String, Any> {
        val payload =
            mutableMapOf<String, Any>(
                "principalId" to principalId,
                "principalType" to principalType.name,
                "resourceType" to resourceType.name,
                "tenantResourceId" to tenantResourceId,
                "permissions" to permissions.map { it.name },
                "grantedBy" to grantedBy,
                "grantedAt" to grantedAt.toString(),
            )
        resourceId?.let { payload["resourceId"] = it }
        namespaceResourceId?.let { payload["namespaceResourceId"] = it }
        topicId?.let { payload["topicId"] = it }
        expiresAt?.let { payload["expiresAt"] = it.toString() }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): PermissionGrantedEvent {
            val principalId = payload["principalId"] as? String ?: error("principalId missing")
            val principalType =
                PrincipalType.valueOf(
                    payload["principalType"] as? String ?: error("principalType missing"),
                )
            val resourceType =
                ResourceType.valueOf(
                    payload["resourceType"] as? String ?: error("resourceType missing"),
                )
            val resourceId = payload["resourceId"] as? String
            val tenantResourceId = payload["tenantResourceId"] as? String ?: error("tenantResourceId missing")
            val namespaceResourceId = payload["namespaceResourceId"] as? String
            val topicId = payload["topicId"] as? String
            val permissions =
                (payload["permissions"] as? List<*>)?.map {
                    Permission.valueOf(it as String)
                }?.toSet() ?: error("permissions missing")
            val grantedBy = payload["grantedBy"] as? String ?: error("grantedBy missing")
            val grantedAt = parseInstant(payload["grantedAt"])
            val expiresAt = (payload["expiresAt"] as? String)?.let { Instant.parse(it) }

            return PermissionGrantedEvent(
                principalId = principalId,
                principalType = principalType,
                resourceType = resourceType,
                resourceId = resourceId,
                tenantResourceId = tenantResourceId,
                namespaceResourceId = namespaceResourceId,
                topicId = topicId,
                permissions = permissions,
                grantedBy = grantedBy,
                grantedAt = grantedAt,
                expiresAt = expiresAt,
            )
        }
    }
}

data class PermissionRevokedEvent(
    // UUID of user/API key/role/group
    val principalId: String,
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    // UUID of specific resource, or null for all resources
    val resourceId: String? = null,
    val tenantResourceId: String,
    val namespaceResourceId: String? = null,
    val topicId: String? = null,
    val permissions: Set<Permission>,
    // UUID of user who revoked permission
    val revokedBy: String,
    val revokedAt: Instant,
    val reason: String? = null,
) : PermissionEventPayload {
    override val type: String = PermissionEventType.REVOKED

    override fun toPayload(): Map<String, Any> {
        val payload =
            mutableMapOf<String, Any>(
                "principalId" to principalId,
                "principalType" to principalType.name,
                "resourceType" to resourceType.name,
                "tenantResourceId" to tenantResourceId,
                "permissions" to permissions.map { it.name },
                "revokedBy" to revokedBy,
                "revokedAt" to revokedAt.toString(),
            )
        resourceId?.let { payload["resourceId"] = it }
        namespaceResourceId?.let { payload["namespaceResourceId"] = it }
        topicId?.let { payload["topicId"] = it }
        reason?.let { payload["reason"] = it }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): PermissionRevokedEvent {
            val principalId = payload["principalId"] as? String ?: error("principalId missing")
            val principalType =
                PrincipalType.valueOf(
                    payload["principalType"] as? String ?: error("principalType missing"),
                )
            val resourceType =
                ResourceType.valueOf(
                    payload["resourceType"] as? String ?: error("resourceType missing"),
                )
            val resourceId = payload["resourceId"] as? String
            val tenantResourceId = payload["tenantResourceId"] as? String ?: error("tenantResourceId missing")
            val namespaceResourceId = payload["namespaceResourceId"] as? String
            val topicId = payload["topicId"] as? String
            val permissions =
                (payload["permissions"] as? List<*>)?.map {
                    Permission.valueOf(it as String)
                }?.toSet() ?: error("permissions missing")
            val revokedBy = payload["revokedBy"] as? String ?: error("revokedBy missing")
            val revokedAt = parseInstant(payload["revokedAt"])
            val reason = payload["reason"] as? String

            return PermissionRevokedEvent(
                principalId = principalId,
                principalType = principalType,
                resourceType = resourceType,
                resourceId = resourceId,
                tenantResourceId = tenantResourceId,
                namespaceResourceId = namespaceResourceId,
                topicId = topicId,
                permissions = permissions,
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
