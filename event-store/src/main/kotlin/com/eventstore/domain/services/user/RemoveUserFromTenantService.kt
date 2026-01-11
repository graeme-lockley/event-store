package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserTenantRemovedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant

data class RemoveUserTenantRequest(
    val userId: String,
    val tenantId: String,
    val removedBy: String = "system",
    val reason: String? = null
)

class RemoveUserFromTenantService(
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: RemoveUserTenantRequest): Boolean {
        userProjectionService.getUser(request.userId) ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserTenantRemovedEvent(
            userId = request.userId,
            tenantId = request.tenantId,
            removedBy = request.removedBy,
            removedAt = now,
            reason = request.reason
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC_NAME,
            eventType = UserEventType.TENANT_REMOVED,
            payload = payload.toPayload(),
            timestamp = now
        )

        return true
    }
}

