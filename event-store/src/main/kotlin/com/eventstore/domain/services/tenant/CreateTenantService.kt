package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant
import java.util.UUID

data class CreateTenantRequest(
    val name: String,
    val quota: Quota? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdBy: String = "system"
)

class CreateTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val config: Config
) {
    suspend fun execute(request: CreateTenantRequest): Tenant {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (tenantProjectionService.tenantExistsByName(request.name)) {
            throw TenantAlreadyExistsException(request.name)
        }

        val now = Instant.now()
        val resourceId = UUID.randomUUID()
        val tenantCreated = TenantCreatedEvent(
            resourceId = resourceId,
            name = request.name,
            quota = request.quota,
            createdBy = request.createdBy,
            createdAt = now,
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
            type = TenantEventType.CREATED,
            payload = tenantCreated.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        return Tenant(
            resourceId = resourceId,
            name = request.name,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
            quota = request.quota,
            metadata = request.metadata
        )
    }
}

