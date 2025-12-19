package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.User
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserStatusChangedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class DeleteUserRequest(
    val userId: String,
    val deletedBy: String = "system",
    val reason: String? = null
)

class DeleteUserService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: DeleteUserRequest): User {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        val existing = userProjectionService.getUser(request.userId)
            ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserStatusChangedEvent(
            userId = request.userId,
            status = UserStatus.DELETED,
            changedBy = request.deletedBy,
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
            type = UserEventType.STATUS_CHANGED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        userProjectionService.handleEvents(listOf(event))

        return existing.copy(status = UserStatus.DELETED, updatedAt = now)
    }
}
