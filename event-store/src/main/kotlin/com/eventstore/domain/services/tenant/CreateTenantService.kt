package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.*

data class CreateTenantRequest(
    val name: String,
    val quota: Quota? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdBy: String = "system"
)

class CreateTenantService(
    private val tenantProjectionService: TenantProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: CreateTenantRequest): Tenant {
        // Validate tenant name format
        TenantNameValidator.validate(request.name)

        if (tenantProjectionService.tenantExistsByName(request.name)) {
            throw TenantAlreadyExistsException(request.name)
        }

        val now = Instant.now()
        val resourceId = UUID.randomUUID()
        val tenantCreated = TenantCreatedEvent(
            tenantId = resourceId,
            name = request.name,
            quota = request.quota,
            createdBy = request.createdBy,
            createdAt = now,
            metadata = request.metadata
        )

        val payload = tenantCreated.toPayload()

        eventPublisher.publishEvent(
            topic = SystemTopics.TENANTS_TOPIC_NAME,
            eventType = TenantEventType.CREATED,
            payload = payload,
            timestamp = now
        )

        return Tenant(
            tenantId = resourceId,
            name = request.name,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
            quota = request.quota,
            metadata = request.metadata
        )
    }
}
