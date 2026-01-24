package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.Namespace
import com.eventstore.domain.Quota
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.QuotaExceededException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.services.tenant.TenantUsageService
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.time.Instant
import java.util.*

data class CreateNamespaceRequest(
    val tenantId: UUID,
    val name: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdBy: String = "system",
)

class CreateNamespaceService(
    private val tenantProjectionService: TenantProjectionService,
    private val namespaceProjectionService: NamespaceProjectionService,
    private val tenantUsageService: TenantUsageService,
    config: Config,
    eventPublisher: SystemEventPublisher,
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: CreateNamespaceRequest): Namespace {
        val tenant =
            tenantProjectionService.getTenantById(request.tenantId)
                ?: throw TenantNotFoundException(request.tenantId)

        // Check tenant quota limit (Rule C-10)
        val usage = tenantUsageService.getUsage(tenant.tenantId, tenant.name)
        val effectiveQuota = tenant.quota?.maxNamespaces ?: Quota().maxNamespaces
        if (usage.namespaces >= effectiveQuota) {
            throw QuotaExceededException(
                tenant.tenantId,
                "namespaces",
                usage.namespaces,
                effectiveQuota,
            )
        }

        if (namespaceProjectionService.namespaceExistsByName(tenant.name, request.name)) {
            throw NamespaceAlreadyExistsException(request.name)
        }

        val now = Instant.now()
        val namespaceId = UUID.randomUUID()
        val payload =
            NamespaceCreatedEvent(
                namespaceId = namespaceId,
                tenantId = tenant.tenantId,
                name = request.name,
                description = request.description,
                createdBy = request.createdBy,
                createdAt = now,
                metadata = request.metadata,
            )

        val eventPayload = payload.toPayload().toMutableMap()
        eventPayload["tenantName"] = tenant.name // Include tenantName for projection service

        eventPublisher.publishEvent(
            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
            eventType = NamespaceEventType.CREATED,
            payload = eventPayload,
            timestamp = now,
        )

        return Namespace(
            namespaceId = namespaceId,
            tenantId = tenant.tenantId,
            tenantName = tenant.name,
            name = request.name,
            description = request.description,
            createdAt = now,
            metadata = request.metadata,
        )
    }
}
