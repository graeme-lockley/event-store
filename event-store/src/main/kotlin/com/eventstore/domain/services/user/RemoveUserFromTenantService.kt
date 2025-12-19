package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserTenantRemovedEvent
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class RemoveUserTenantRequest(
    val userId: String,
    val tenantId: String,
    val removedBy: String = "system",
    val reason: String? = null
)

class RemoveUserFromTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: RemoveUserTenantRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        userProjectionService.getUser(request.userId) ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserTenantRemovedEvent(
            userId = request.userId,
            tenantId = request.tenantId,
            removedBy = request.removedBy,
            removedAt = now,
            reason = request.reason
        )

        val seq = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.USERS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(SystemTopics.USERS_TOPIC, seq, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = now,
            type = UserEventType.TENANT_REMOVED,
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

