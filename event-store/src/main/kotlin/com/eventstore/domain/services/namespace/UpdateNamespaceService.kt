package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.NamespaceUpdatedEvent
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class UpdateNamespaceRequest(
    val tenantId: String,
    val namespaceId: String,
    val name: String? = null,
    val description: String? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateNamespaceService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val config: Config
) {
    suspend fun execute(request: UpdateNamespaceRequest): Namespace {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (!tenantProjectionService.tenantExists(request.tenantId)) {
            throw TenantNotFoundException(request.tenantId)
        }

        val existing = namespaceProjectionService.getNamespace(request.tenantId, request.namespaceId)
            ?: throw NamespaceNotFoundException(request.namespaceId)

        val now = Instant.now()
        val payload = NamespaceUpdatedEvent(
            tenantId = request.tenantId,
            namespaceId = request.namespaceId,
            name = request.name,
            description = request.description,
            updatedBy = request.updatedBy,
            updatedAt = now,
            metadata = request.metadata
        )

        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.NAMESPACES_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.NAMESPACES_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = NamespaceEventType.UPDATED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        namespaceProjectionService.handleEvents(listOf(event))

        return existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}

