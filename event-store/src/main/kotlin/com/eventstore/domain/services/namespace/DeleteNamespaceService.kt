package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import java.time.Instant
import java.util.*

data class DeleteNamespaceRequest(
    val namespaceId: UUID,
    val deletedBy: String = "system",
    val reason: String? = null,
)

class DeleteNamespaceService(
    private val namespaceProjectionService: NamespaceProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher,
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: DeleteNamespaceRequest): Boolean {
        val existing =
            namespaceProjectionService.getNamespaceById(request.namespaceId)
                ?: throw NamespaceNotFoundException(request.namespaceId.toString())

        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload =
            NamespaceDeletedEvent(
                namespaceId = existing.namespaceId,
                deletedBy = request.deletedBy,
                deletedAt = now,
                reason = request.reason,
            )

        val eventPayload = payload.toPayload()

        eventPublisher.publishEvent(
            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
            eventType = NamespaceEventType.DELETED,
            payload = eventPayload,
            timestamp = now,
        )

        return true
    }
}
