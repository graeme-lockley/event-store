package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNameNotFoundException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.UUID

data class UpdateTenantRequest(
    val tenantId: UUID,
    val name: String? = null,
    val quota: Quota? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateTenantService(
    private val tenantProjectionService: TenantProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: UpdateTenantRequest): Tenant {
        val existing = tenantProjectionService.getTenantById(request.tenantId)
            ?: throw TenantNotFoundException(request.tenantId)

        if (request.name != null && request.name != existing.name) {
            val tenantWithSameName = tenantProjectionService.getTenantByName(request.name)
            if (tenantWithSameName != null) {
                throw TenantAlreadyExistsException(request.name)
            }
        }

        val now = Instant.now()
        val eventPayload = TenantUpdatedEvent(
            tenantId = existing.tenantId,
            name = request.name,
            quota = request.quota,
            updatedBy = request.updatedBy,
            updatedAt = now,
            metadata = request.metadata
        )

        val payload = eventPayload.toPayload()

        eventPublisher.publishEvent(
            topic = SystemTopics.TENANTS_TOPIC_NAME,
            eventType = TenantEventType.UPDATED,
            payload = payload,
            timestamp = now
        )

        return existing.copy(
            name = request.name ?: existing.name,
            quota = request.quota ?: existing.quota,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}
