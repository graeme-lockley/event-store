package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.User
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserUpdatedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant

data class UpdateUserRequest(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateUserService(
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: UpdateUserRequest): User {
        val existing = userProjectionService.getUser(request.userId)
            ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserUpdatedEvent(
            userId = request.userId,
            email = request.email,
            name = request.name,
            updatedBy = request.updatedBy,
            updatedAt = now,
            metadata = request.metadata
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC,
            eventType = UserEventType.UPDATED,
            payload = payload.toPayload(),
            timestamp = now
        )

        return existing.copy(
            email = request.email ?: existing.email,
            name = request.name ?: existing.name,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}

