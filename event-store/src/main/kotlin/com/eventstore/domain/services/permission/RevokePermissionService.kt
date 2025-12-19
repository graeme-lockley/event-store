package com.eventstore.domain.services.permission

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.UUID

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
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val resourceResolver: ResourceResolver
) {
    suspend fun execute(request: RevokePermissionRequest): PermissionRevokedEvent {
        // Resolve tenant resourceId
        val tenantResourceId = resourceResolver.resolveTenantResourceId(request.tenantName)
        
        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceResourceId(tenantResourceId, it)
        }
        
        // Resolve topic resourceId if provided
        val topicResourceId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when revoking topic permissions" }
            resourceResolver.resolveTopicResourceId(tenantResourceId, namespaceResourceId, it)
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
        
        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.PERMISSIONS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        
        val storedEvent = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = PermissionEventType.REVOKED,
            payload = event.toPayload()
        )
        
        eventRepository.storeEvents(
            listOf(storedEvent),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        
        return event
    }
}

