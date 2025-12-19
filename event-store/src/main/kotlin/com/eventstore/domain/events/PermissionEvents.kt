package com.eventstore.domain.events

import com.eventstore.domain.Permission
import com.eventstore.domain.PermissionConstraints
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.TimeBasedConstraint
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
    val principalId: String,              // UUID of user/API key/role/group
    val principalType: PrincipalType,     // USER, API_KEY, ROLE, GROUP
    val resourceType: ResourceType,       // TENANT, NAMESPACE, TOPIC, EVENT, CONSUMER, USER
    val resourceId: String? = null,       // UUID of specific resource, or null for all resources of this type
    val tenantResourceId: String,         // UUID of tenant (for context and inheritance)
    val namespaceResourceId: String? = null, // UUID of namespace (for context and inheritance)
    val topicResourceId: String? = null,   // UUID of topic (for context and inheritance)
    val permissions: Set<Permission>,
    val constraints: PermissionConstraints? = null,
    val grantedBy: String,                // UUID of user who granted permission
    val grantedAt: Instant,
    val expiresAt: Instant? = null
) : PermissionEventPayload {
    override val type: String = PermissionEventType.GRANTED

    override fun toPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "principalId" to principalId,
            "principalType" to principalType.name,
            "resourceType" to resourceType.name,
            "tenantResourceId" to tenantResourceId,
            "permissions" to permissions.map { it.name },
            "grantedBy" to grantedBy,
            "grantedAt" to grantedAt.toString()
        )
        resourceId?.let { payload["resourceId"] = it }
        namespaceResourceId?.let { payload["namespaceResourceId"] = it }
        topicResourceId?.let { payload["topicResourceId"] = it }
        constraints?.let { payload["constraints"] = constraintsToMap(it) }
        expiresAt?.let { payload["expiresAt"] = it.toString() }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): PermissionGrantedEvent {
            val principalId = payload["principalId"] as? String ?: error("principalId missing")
            val principalType = PrincipalType.valueOf(
                payload["principalType"] as? String ?: error("principalType missing")
            )
            val resourceType = ResourceType.valueOf(
                payload["resourceType"] as? String ?: error("resourceType missing")
            )
            val resourceId = payload["resourceId"] as? String
            val tenantResourceId = payload["tenantResourceId"] as? String ?: error("tenantResourceId missing")
            val namespaceResourceId = payload["namespaceResourceId"] as? String
            val topicResourceId = payload["topicResourceId"] as? String
            val permissions = (payload["permissions"] as? List<*>)?.map { 
                Permission.valueOf(it as String) 
            }?.toSet() ?: error("permissions missing")
            val constraints = (payload["constraints"] as? Map<*, *>)?.let { mapToConstraints(it) }
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
                topicResourceId = topicResourceId,
                permissions = permissions,
                constraints = constraints,
                grantedBy = grantedBy,
                grantedAt = grantedAt,
                expiresAt = expiresAt
            )
        }
    }
}

data class PermissionRevokedEvent(
    val principalId: String,              // UUID of user/API key/role/group
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    val resourceId: String? = null,       // UUID of specific resource, or null for all resources
    val tenantResourceId: String,
    val namespaceResourceId: String? = null,
    val topicResourceId: String? = null,
    val permissions: Set<Permission>,
    val revokedBy: String,                // UUID of user who revoked permission
    val revokedAt: Instant,
    val reason: String? = null
) : PermissionEventPayload {
    override val type: String = PermissionEventType.REVOKED

    override fun toPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "principalId" to principalId,
            "principalType" to principalType.name,
            "resourceType" to resourceType.name,
            "tenantResourceId" to tenantResourceId,
            "permissions" to permissions.map { it.name },
            "revokedBy" to revokedBy,
            "revokedAt" to revokedAt.toString()
        )
        resourceId?.let { payload["resourceId"] = it }
        namespaceResourceId?.let { payload["namespaceResourceId"] = it }
        topicResourceId?.let { payload["topicResourceId"] = it }
        reason?.let { payload["reason"] = it }
        return payload
    }

    companion object {
        fun fromPayload(payload: Map<String, Any?>): PermissionRevokedEvent {
            val principalId = payload["principalId"] as? String ?: error("principalId missing")
            val principalType = PrincipalType.valueOf(
                payload["principalType"] as? String ?: error("principalType missing")
            )
            val resourceType = ResourceType.valueOf(
                payload["resourceType"] as? String ?: error("resourceType missing")
            )
            val resourceId = payload["resourceId"] as? String
            val tenantResourceId = payload["tenantResourceId"] as? String ?: error("tenantResourceId missing")
            val namespaceResourceId = payload["namespaceResourceId"] as? String
            val topicResourceId = payload["topicResourceId"] as? String
            val permissions = (payload["permissions"] as? List<*>)?.map { 
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
                topicResourceId = topicResourceId,
                permissions = permissions,
                revokedBy = revokedBy,
                revokedAt = revokedAt,
                reason = reason
            )
        }
    }
}

private fun constraintsToMap(constraints: PermissionConstraints): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    constraints.eventTypes?.let { map["eventTypes"] = it.toList() }
    constraints.maxAgeDays?.let { map["maxAgeDays"] = it }
    constraints.timeBased?.let { 
        val timeMap = mutableMapOf<String, Any>()
        it.startTime?.let { timeMap["startTime"] = it }
        it.endTime?.let { timeMap["endTime"] = it }
        if (timeMap.isNotEmpty()) {
            map["timeBased"] = timeMap
        }
    }
    return map
}

private fun mapToConstraints(map: Map<*, *>): PermissionConstraints {
    val eventTypes = (map["eventTypes"] as? List<*>)?.map { it as String }?.toSet()
    val maxAgeDays = (map["maxAgeDays"] as? Number)?.toInt()
    val timeBased = (map["timeBased"] as? Map<*, *>)?.let {
        TimeBasedConstraint(
            startTime = it["startTime"] as? String,
            endTime = it["endTime"] as? String
        )
    }
    return PermissionConstraints(
        eventTypes = eventTypes,
        maxAgeDays = maxAgeDays,
        timeBased = timeBased
    )
}

private fun parseInstant(value: Any?): Instant {
    val text = value as? String ?: error("timestamp value is required")
    return Instant.parse(text)
}

