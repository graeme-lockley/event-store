package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserPasswordChangedEvent
import com.eventstore.domain.exceptions.InvalidCredentialsException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.UserProjectionService
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

data class ChangePasswordRequest(
    val userId: String,
    val oldPassword: String,
    val newPassword: String,
    val changedBy: String = "self"
)

class ChangePasswordService(
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: ChangePasswordRequest): Boolean {
        val user = userProjectionService.getUser(request.userId) ?: throw UserNotFoundException(request.userId)
        if (!BCrypt.checkpw(request.oldPassword, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val now = Instant.now()
        val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt())
        val payload = UserPasswordChangedEvent(
            userId = request.userId,
            passwordHash = newHash,
            changedBy = request.changedBy,
            changedAt = now
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC,
            eventType = UserEventType.PASSWORD_CHANGED,
            payload = payload.toPayload(),
            timestamp = now
        )

        return true
    }
}

