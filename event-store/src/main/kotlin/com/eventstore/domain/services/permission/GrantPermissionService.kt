package com.eventstore.domain.services.permission

import com.eventstore.Config
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.ports.outbound.ResourceResolver
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.*

data class GrantPermissionRequest(
    val principalId: String,
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    val resourceName: String? = null,  // Human-readable name, will be resolved to UUID
    val tenantId: UUID,
    val namespaceName: String? = null,
    val topicName: String? = null,
    val permissions: Set<Permission>,
    val expiresAt: Instant? = null,
    val grantedBy: String
)

class GrantPermissionService(
    private val resourceResolver: ResourceResolver,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: GrantPermissionRequest): PermissionGrantedEvent {
        // Use tenantId directly (no resolution needed)
        val tenantResourceId = request.tenantId

        // Resolve namespace resourceId if provided
        val namespaceResourceId = request.namespaceName?.let {
            resourceResolver.resolveNamespaceName(tenantResourceId, it)
        }

        // If topicName is provided, it should be a UUID string (API uses UUIDs)
        val topicId = request.topicName?.let {
            requireNotNull(namespaceResourceId) { "Namespace required when granting topic permissions" }
            // topicName is actually a UUID string when provided from API
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                throw com.eventstore.domain.exceptions.TopicNotFoundException(it)
            }
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
            ResourceType.TOPIC -> topicId
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
            topicId = topicId?.toString(),
            permissions = request.permissions,
            grantedBy = request.grantedBy,
            grantedAt = now,
            expiresAt = request.expiresAt
        )

        val eventPayload = event.toPayload()

        eventPublisher.publishEvent(
            topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
            eventType = PermissionEventType.GRANTED,
            payload = eventPayload,
            timestamp = now
        )

        return event
    }
}

