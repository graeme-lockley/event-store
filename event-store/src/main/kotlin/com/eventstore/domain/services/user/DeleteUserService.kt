package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.User
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserStatusChangedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant

data class DeleteUserRequest(
    val userId: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteUserService(
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: DeleteUserRequest): User {
        val existing = userProjectionService.getUser(request.userId)
            ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserStatusChangedEvent(
            userId = request.userId,
            status = UserStatus.DELETED,
            changedBy = request.deletedBy,
            changedAt = now
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC_NAME,
            eventType = UserEventType.STATUS_CHANGED,
            payload = payload.toPayload(),
            timestamp = now
        )

        return existing.copy(status = UserStatus.DELETED, updatedAt = now)
    }
}
