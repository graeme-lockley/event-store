package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant

data class DeleteNamespaceRequest(
    val tenantName: String,
    val namespaceName: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteNamespaceService(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: DeleteNamespaceRequest): Boolean {
        val existing = namespaceProjectionService.getNamespaceByName(request.tenantName, request.namespaceName)
            ?: throw NamespaceNotFoundException(request.namespaceName)

        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload = NamespaceDeletedEvent(
            resourceId = existing.resourceId,
            tenantResourceId = existing.tenantResourceId,
            deletedBy = request.deletedBy,
            deletedAt = now,
            reason = request.reason
        )

        val eventPayload = payload.toPayload()

        eventPublisher.publishEvent(
            topic = SystemTopics.NAMESPACES_TOPIC_NAME,
            eventType = NamespaceEventType.DELETED,
            payload = eventPayload,
            timestamp = now
        )

        return true
    }
}

