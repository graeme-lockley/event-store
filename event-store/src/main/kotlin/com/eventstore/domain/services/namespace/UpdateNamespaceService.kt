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
import java.time.Instant
import java.util.*

data class UpdateNamespaceRequest(
    val namespaceId: UUID,
    val name: String? = null,
    val description: String? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system",
)

class UpdateNamespaceService(
    private val namespaceProjectionService: NamespaceProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher,
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: UpdateNamespaceRequest): Namespace {
        val existing =
            namespaceProjectionService.getNamespaceById(request.namespaceId)
                ?: throw NamespaceNotFoundException(request.namespaceId.toString())

        val now = Instant.now()
        val payload =
            NamespaceUpdatedEvent(
                namespaceId = existing.namespaceId,
                name = request.name,
                description = request.description,
                updatedBy = request.updatedBy,
                updatedAt = now,
                metadata = request.metadata,
            )

        val eventPayload = payload.toPayload()

        eventPublisher.publishEvent(
            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
            eventType = NamespaceEventType.UPDATED,
            payload = eventPayload,
            timestamp = now,
        )

        return existing.copy(
            name = request.name ?: existing.name,
            description = request.description ?: existing.description,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata,
        )
    }
}
