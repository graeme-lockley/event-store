package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class UpdateTenantRequest(
    val tenantName: String,
    val name: String? = null,
    val quota: Quota? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val config: Config
) {
    suspend fun execute(request: UpdateTenantRequest): Tenant {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        val existing = tenantProjectionService.getTenantByName(request.tenantName)
            ?: throw TenantNotFoundException(request.tenantName)

        val now = Instant.now()
        val eventPayload = TenantUpdatedEvent(
            resourceId = existing.resourceId,
            name = request.name,
            quota = request.quota,
            updatedBy = request.updatedBy,
            updatedAt = now,
            metadata = request.metadata
        )

        val sequence = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.TENANTS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = sequence,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = TenantEventType.UPDATED,
            payload = eventPayload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        return existing.copy(
            name = request.name ?: existing.name,
            quota = request.quota ?: existing.quota,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}
