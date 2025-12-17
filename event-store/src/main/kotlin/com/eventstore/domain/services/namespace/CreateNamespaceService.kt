package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class CreateNamespaceRequest(
    val tenantId: String,
    val namespaceId: String,
    val name: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdBy: String = "system"
)

class CreateNamespaceService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val config: Config
) {
    suspend fun execute(request: CreateNamespaceRequest): Namespace {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (!tenantProjectionService.tenantExists(request.tenantId)) {
            throw TenantNotFoundException(request.tenantId)
        }

        if (namespaceProjectionService.namespaceExists(request.tenantId, request.namespaceId)) {
            throw NamespaceAlreadyExistsException(request.namespaceId)
        }

        val now = Instant.now()
        val payload = NamespaceCreatedEvent(
            tenantId = request.tenantId,
            namespaceId = request.namespaceId,
            name = request.name,
            description = request.description,
            createdBy = request.createdBy,
            createdAt = now,
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
            type = NamespaceEventType.CREATED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        namespaceProjectionService.handleEvents(listOf(event))

        return Namespace(
            tenantId = request.tenantId,
            id = request.namespaceId,
            name = request.name,
            description = request.description,
            createdAt = now,
            metadata = request.metadata
        )
    }
}

