package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.UUID

data class DeleteTenantRequest(
    val tenantId: UUID,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteTenantService(
    private val tenantProjectionService: TenantProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: DeleteTenantRequest): Boolean {
        // Use getTenantByIdIncludingDeleted to check if tenant exists at all (including deleted)
        val existing = tenantProjectionService.getTenantByIdIncludingDeleted(request.tenantId)
            ?: throw TenantNotFoundException(request.tenantId)

        // Rule D-3: Idempotent deletion - return false if already deleted, no error
        if (!existing.isActive) {
            return false
        }

        val now = Instant.now()
        val payload = TenantDeletedEvent(
            tenantId = existing.tenantId,
            deletedBy = request.deletedBy,
            deletedAt = now,
            reason = request.reason
        )

        val eventPayload = payload.toPayload()

        eventPublisher.publishEvent(
            topic = SystemTopics.TENANTS_TOPIC_NAME,
            eventType = TenantEventType.DELETED,
            payload = eventPayload,
            timestamp = now
        )

        return true
    }
}
