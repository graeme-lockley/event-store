package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.NamespaceUpdatedEvent
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant

data class UpdateNamespaceRequest(
    val tenantName: String,
    val namespaceName: String,
    val name: String? = null,
    val description: String? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateNamespaceService(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: UpdateNamespaceRequest): Namespace {
        requireMultiTenantEnabled()

        val existing = namespaceProjectionService.getNamespaceByName(request.tenantName, request.namespaceName)
            ?: throw NamespaceNotFoundException(request.namespaceName)

        val now = Instant.now()
        val payload = NamespaceUpdatedEvent(
            resourceId = existing.resourceId,
            tenantResourceId = existing.tenantResourceId,
            name = request.name,
            description = request.description,
            updatedBy = request.updatedBy,
            updatedAt = now,
            metadata = request.metadata
        )

        val eventPayload = payload.toPayload()
        
        eventPublisher.publishEvent(
            topic = SystemTopics.NAMESPACES_TOPIC,
            eventType = NamespaceEventType.UPDATED,
            payload = eventPayload,
            timestamp = now
        )

        return existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}

