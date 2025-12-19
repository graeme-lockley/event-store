package com.eventstore.domain.services.permission

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PermissionConstraints
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.UUID

data class GrantPermissionRequest(
    val principalId: String,
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    val resourceName: String? = null,  // Human-readable name, will be resolved to UUID
    val tenantName: String,
    val namespaceName: String? = null,
    val topicName: String? = null,
    val permissions: Set<Permission>,
    val constraints: PermissionConstraints? = null,
    val expiresAt: Instant? = null,
    val grantedBy: String
)

class GrantPermissionService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val resourceResolver: ResourceResolver,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService
) {
    suspend fun execute(request: GrantPermissionRequest): PermissionGrantedEvent {
        // Resolve tenant resourceId
        val tenantResourceId = resourceResolver.resolveTenantResourceId(request.tenantName)
        
        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceResourceId(tenantResourceId, it)
        }
        
        // Resolve topic resourceId if provided
        val topicResourceId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when granting topic permissions" }
            resourceResolver.resolveTopicResourceId(tenantResourceId, namespaceResourceId, it)
        }
        
        // Resolve target resourceId based on resourceType
        // If resourceName is provided, use it (for specific resource targeting)
        // Otherwise, use the resolved resourceId for the resource type
        val targetResourceId = request.resourceName?.let {
            // Resource name provided - use it as the specific resourceId
            UUID.fromString(it)
        } ?: when (request.resourceType) {
            ResourceType.TENANT -> tenantResourceId
            ResourceType.NAMESPACE -> namespaceResourceId
            ResourceType.TOPIC -> topicResourceId
            else -> null
        }
        
        val now = Instant.now()
        val event = PermissionGrantedEvent(
            principalId = request.principalId,
            principalType = request.principalType,
            resourceType = request.resourceType,
            resourceId = targetResourceId?.toString(),
            tenantResourceId = tenantResourceId.toString(),
            namespaceResourceId = namespaceResourceId?.toString(),
            topicResourceId = topicResourceId?.toString(),
            permissions = request.permissions,
            constraints = request.constraints,
            grantedBy = request.grantedBy,
            grantedAt = now,
            expiresAt = request.expiresAt
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
            type = PermissionEventType.GRANTED,
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

