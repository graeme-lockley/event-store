package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserPasswordChangedEvent
import com.eventstore.domain.exceptions.InvalidCredentialsException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

data class ChangePasswordRequest(
    val userId: String,
    val oldPassword: String,
    val newPassword: String,
    val changedBy: String = "self"
)

class ChangePasswordService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: ChangePasswordRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

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

        val seq = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(SystemTopics.USERS_TOPIC, seq, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = now,
            type = UserEventType.PASSWORD_CHANGED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        userProjectionService.handleEvents(listOf(event))

        return true
    }
}

