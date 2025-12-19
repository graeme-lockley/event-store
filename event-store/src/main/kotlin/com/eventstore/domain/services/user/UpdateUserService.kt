package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.User
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserUpdatedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class UpdateUserRequest(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val metadata: Map<String, Any>? = null,
    val updatedBy: String = "system"
)

class UpdateUserService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: UpdateUserRequest): User {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

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

        val seq = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.USERS_TOPIC,
                sequence = seq,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = UserEventType.UPDATED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        userProjectionService.handleEvents(listOf(event))

        return existing.copy(
            email = request.email ?: existing.email,
            name = request.name ?: existing.name,
            updatedAt = now,
            metadata = request.metadata ?: existing.metadata
        )
    }
}

