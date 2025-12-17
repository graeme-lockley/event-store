package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class DeleteTenantRequest(
    val tenantId: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val config: Config
) {
    suspend fun execute(request: DeleteTenantRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        val existing = tenantProjectionService.getTenant(request.tenantId)
            ?: throw TenantNotFoundException(request.tenantId)

        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload = TenantDeletedEvent(
            tenantId = request.tenantId,
            deletedBy = request.deletedBy,
            deletedAt = now,
            reason = request.reason
        )

        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = TenantEventType.DELETED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        return true
    }
}
