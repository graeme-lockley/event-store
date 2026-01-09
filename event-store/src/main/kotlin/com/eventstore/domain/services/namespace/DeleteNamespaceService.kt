package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
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
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val config: Config,
    private val eventDispatcher: EventDispatcher,
    private val schemaValidator: SchemaValidator
) {
    suspend fun execute(request: DeleteNamespaceRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

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

        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.NAMESPACES_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val eventPayload = payload.toPayload()
        
        // Validate event payload against schema
        schemaValidator.validateEvent(SystemTopics.NAMESPACES_TOPIC, NamespaceEventType.DELETED, eventPayload)

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.NAMESPACES_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = NamespaceEventType.DELETED,
            payload = eventPayload
        )

        eventRepository.storeEvents(listOf(event))
        eventDispatcher.notifyEventsPublished(setOf(event.id.qualifiedTopic))

        return true
    }
}

