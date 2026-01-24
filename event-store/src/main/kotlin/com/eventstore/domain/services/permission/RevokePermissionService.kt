package com.eventstore.domain.services.permission

import com.eventstore.Config
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.*

data class RevokePermissionRequest(
    val principalId: String,
    val principalType: PrincipalType,
    val resourceType: ResourceType,
    // UUID string for the target resource (namespaceId, topicId, etc.)
    val resourceId: String? = null,
    val tenantId: UUID,
    val permissions: Set<Permission>,
    val revokedBy: String,
    val reason: String? = null,
)

class RevokePermissionService(
    config: Config,
    eventPublisher: SystemEventPublisher,
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: RevokePermissionRequest): PermissionRevokedEvent {
        // Use tenantId directly (no resolution needed)
        val tenantResourceId = request.tenantId

        // Resolve target resourceId based on resourceType
        // If resourceId is provided, use it directly (must be UUID string)
        // Otherwise, use tenantId for TENANT resource type
        val targetResourceId =
            request.resourceId?.let {
                // Resource ID provided - use it as the specific resourceId (must be UUID)
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("resourceId must be a valid UUID: $it")
                }
            } ?: when (request.resourceType) {
                ResourceType.TENANT -> tenantResourceId
                ResourceType.NAMESPACE -> throw IllegalArgumentException("resourceId is required for NAMESPACE resource type")
                ResourceType.TOPIC -> throw IllegalArgumentException("resourceId is required for TOPIC resource type")
                else -> null
            }

        val now = Instant.now()
        val event =
            PermissionRevokedEvent(
                principalId = request.principalId,
                principalType = request.principalType,
                resourceType = request.resourceType,
                resourceId = targetResourceId?.toString(),
                tenantResourceId = tenantResourceId.toString(),
                namespaceResourceId =
                    when (request.resourceType) {
                        ResourceType.NAMESPACE -> targetResourceId?.toString()
                        ResourceType.TOPIC -> {
                            // For TOPIC, we'd need to get namespaceId from the topic - but we don't have that context
                            // For now, we'll leave it null and let the event store handle it
                            null
                        }
                        else -> null
                    },
                topicId =
                    when (request.resourceType) {
                        ResourceType.TOPIC -> targetResourceId?.toString()
                        else -> null
                    },
                permissions = request.permissions,
                revokedBy = request.revokedBy,
                revokedAt = now,
                reason = request.reason,
            )

        val eventPayload = event.toPayload()

        eventPublisher.publishEvent(
            topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
            eventType = PermissionEventType.REVOKED,
            payload = eventPayload,
            timestamp = now,
        )

        return event
    }
}
