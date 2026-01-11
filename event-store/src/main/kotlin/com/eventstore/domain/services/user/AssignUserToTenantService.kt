package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserTenantAssignedEvent
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant

data class AssignUserRequest(
    val userId: String,
    val tenantId: String,
    val role: String? = null,
    val isPrimary: Boolean = false,
    val assignedBy: String = "system"
)

class AssignUserToTenantService(
    private val tenantProjectionService: TenantProjectionService,
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: AssignUserRequest): Boolean {
        if (!tenantProjectionService.tenantExistsByName(request.tenantId)) {
            throw TenantNotFoundException(request.tenantId)
        }

        userProjectionService.getUser(request.userId) ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserTenantAssignedEvent(
            userId = request.userId,
            tenantId = request.tenantId,
            role = request.role,
            assignedBy = request.assignedBy,
            assignedAt = now,
            isPrimary = request.isPrimary
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC_NAME,
            eventType = UserEventType.TENANT_ASSIGNED,
            payload = payload.toPayload(),
            timestamp = now
        )

        return true
    }
}
