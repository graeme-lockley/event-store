package com.eventstore.domain.services.permission

import com.eventstore.Config
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.*

data class RevokePermissionRequest(
    val principalId: String,
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    val resourceName: String? = null,  // Human-readable name, will be resolved to UUID
    val tenantName: String,
    val namespaceName: String? = null,
    val topicName: String? = null,
    val permissions: Set<Permission>,
    val revokedBy: String,
    val reason: String? = null
)

class RevokePermissionService(
    private val resourceResolver: ResourceResolver,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: RevokePermissionRequest): PermissionRevokedEvent {
        // Resolve tenant resourceId
        val tenantResourceId = resourceResolver.resolveTenantName(request.tenantName)

        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceName(tenantResourceId, it)
        }

        // Resolve topic resourceId if provided
        val topicResourceId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when revoking topic permissions" }
            resourceResolver.resolveTopicName(tenantResourceId, namespaceResourceId, it)
        }

        // Resolve target resourceId based on resourceType
        val targetResourceId = when (request.resourceType) {
            ResourceType.TENANT -> tenantResourceId
            ResourceType.NAMESPACE -> namespaceResourceId
            ResourceType.TOPIC -> topicResourceId
            else -> request.resourceName?.let { UUID.fromString(it) }
        }

        val now = Instant.now()
        val event = PermissionRevokedEvent(
            principalId = request.principalId,
            principalType = request.principalType,
            resourceType = request.resourceType,
            resourceId = targetResourceId?.toString(),
            tenantResourceId = tenantResourceId.toString(),
            namespaceResourceId = namespaceResourceId?.toString(),
            topicResourceId = topicResourceId?.toString(),
            permissions = request.permissions,
            revokedBy = request.revokedBy,
            revokedAt = now,
            reason = request.reason
        )

        val eventPayload = event.toPayload()

        eventPublisher.publishEvent(
            topic = SystemTopics.PERMISSIONS_TOPIC_NAME,
            eventType = PermissionEventType.REVOKED,
            payload = eventPayload,
            timestamp = now
        )

        return event
    }
}



