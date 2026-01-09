package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.SchemaValidator
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant

data class DeleteTenantRequest(
    val tenantName: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val config: Config,
    private val eventDispatcher: EventDispatcher,
    private val schemaValidator: SchemaValidator
) {
    suspend fun execute(request: DeleteTenantRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        val existing = tenantProjectionService.getTenantByName(request.tenantName)
            ?: throw TenantNotFoundException(request.tenantName)

        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload = TenantDeletedEvent(
            resourceId = existing.resourceId,
            deletedBy = request.deletedBy,
            deletedAt = now,
            reason = request.reason
        )

        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.TENANTS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val eventPayload = payload.toPayload()
        
        // Validate event payload against schema
        schemaValidator.validateEvent(SystemTopics.TENANTS_TOPIC, TenantEventType.DELETED, eventPayload)

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = TenantEventType.DELETED,
            payload = eventPayload
        )

        eventRepository.storeEvents(listOf(event))
        eventDispatcher.notifyEventsPublished(setOf(event.id.qualifiedTopic))

        return true
    }
}
