package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class DeleteNamespaceRequest(
    val tenantId: String,
    val namespaceId: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteNamespaceService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val config: Config
) {
    suspend fun execute(request: DeleteNamespaceRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (!tenantProjectionService.tenantExists(request.tenantId)) {
            throw TenantNotFoundException(request.tenantId)
        }

        val existing = namespaceProjectionService.getNamespace(request.tenantId, request.namespaceId)
            ?: throw NamespaceNotFoundException(request.namespaceId)

        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload = NamespaceDeletedEvent(
            tenantId = request.tenantId,
            namespaceId = request.namespaceId,
            deletedBy = request.deletedBy,
            deletedAt = now,
            reason = request.reason
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
            type = NamespaceEventType.DELETED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        namespaceProjectionService.handleEvents(listOf(event))

        return true
    }
}

