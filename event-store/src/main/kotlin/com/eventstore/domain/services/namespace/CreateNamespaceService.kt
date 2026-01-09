package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.*

data class CreateNamespaceRequest(
    val tenantName: String,
    val name: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdBy: String = "system"
)

class CreateNamespaceService(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: CreateNamespaceRequest): Namespace {
        requireMultiTenantEnabled()

        val tenant = tenantProjectionService.getTenantByName(request.tenantName)
            ?: throw TenantNotFoundException(request.tenantName)

        if (namespaceProjectionService.namespaceExistsByName(request.tenantName, request.name)) {
            throw NamespaceAlreadyExistsException(request.name)
        }

        val now = Instant.now()
        val resourceId = UUID.randomUUID()
        val payload = NamespaceCreatedEvent(
            resourceId = resourceId,
            tenantResourceId = tenant.resourceId,
            tenantName = request.tenantName,
            name = request.name,
            description = request.description,
            createdBy = request.createdBy,
            createdAt = now,
            metadata = request.metadata
        )

        val eventPayload = payload.toPayload()
        
        eventPublisher.publishEvent(
            topic = SystemTopics.NAMESPACES_TOPIC,
            eventType = NamespaceEventType.CREATED,
            payload = eventPayload,
            timestamp = now
        )

        return Namespace(
            resourceId = resourceId,
            tenantResourceId = tenant.resourceId,
            tenantName = request.tenantName,
            name = request.name,
            description = request.description,
            createdAt = now,
            metadata = request.metadata
        )
    }
}

