package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.exceptions.CannotUpdateDeletedTenantException
import com.eventstore.domain.exceptions.QuotaExceededException
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
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
    private val tenantUsageService: TenantUsageService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: UpdateTenantRequest): Tenant {
        // Get existing tenant (including deleted ones for the check)
        val existing = tenantProjectionService.getTenantByIdIncludingDeleted(request.tenantId)
            ?: throw TenantNotFoundException(request.tenantId)

        // Rule 2: Block updates to deleted tenants
        if (!existing.isActive) {
            throw CannotUpdateDeletedTenantException(request.tenantId)
        }

        // Rule 3: Validate tenant name format if provided
        if (request.name != null) {
            TenantNameValidator.validate(request.name)

            // Check uniqueness if name is changing
            if (request.name != existing.name) {
                val tenantWithSameName = tenantProjectionService.getTenantByName(request.name)
                if (tenantWithSameName != null) {
                    throw TenantAlreadyExistsException(request.name)
                }
            }
        }

        // Rule 4: Validate quota changes against current usage
        if (request.quota != null) {
            val usage = tenantUsageService.getUsage(existing.tenantId, existing.name)
            val currentQuota = existing.quota

            // Validate each quota field that is being reduced
            if (currentQuota == null || request.quota.maxTopics < currentQuota.maxTopics) {
                if (request.quota.maxTopics < usage.topics) {
                    throw QuotaExceededException(
                        existing.tenantId,
                        "topics",
                        usage.topics,
                        request.quota.maxTopics
                    )
                }
            }

            if (currentQuota == null || request.quota.maxNamespaces < currentQuota.maxNamespaces) {
                if (request.quota.maxNamespaces < usage.namespaces) {
                    throw QuotaExceededException(
                        existing.tenantId,
                        "namespaces",
                        usage.namespaces,
                        request.quota.maxNamespaces
                    )
                }
            }

            if (currentQuota == null || request.quota.maxConsumers < currentQuota.maxConsumers) {
                if (request.quota.maxConsumers < usage.consumers) {
                    throw QuotaExceededException(
                        existing.tenantId,
                        "consumers",
                        usage.consumers,
                        request.quota.maxConsumers
                    )
                }
            }

            if (currentQuota == null || request.quota.maxUsers < currentQuota.maxUsers) {
                if (request.quota.maxUsers < usage.users) {
                    throw QuotaExceededException(
                        existing.tenantId,
                        "users",
                        usage.users,
                        request.quota.maxUsers
                    )
                }
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
