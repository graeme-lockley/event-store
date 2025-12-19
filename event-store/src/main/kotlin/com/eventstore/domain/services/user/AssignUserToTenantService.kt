package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserTenantAssignedEvent
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import java.time.Instant

data class AssignUserRequest(
    val userId: String,
    val tenantId: String,
    val role: String? = null,
    val isPrimary: Boolean = false,
    val assignedBy: String = "system"
)

class AssignUserToTenantService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: AssignUserRequest): Boolean {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (!tenantProjectionService.tenantExistsByName(request.tenantId)) {
            throw TenantNotFoundException(request.tenantId)
        }

        val user = userProjectionService.getUser(request.userId) ?: throw UserNotFoundException(request.userId)

        val now = Instant.now()
        val payload = UserTenantAssignedEvent(
            userId = request.userId,
            tenantId = request.tenantId,
            role = request.role,
            assignedBy = request.assignedBy,
            assignedAt = now,
            isPrimary = request.isPrimary
        )

        val seq = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.USERS_TOPIC,
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(SystemTopics.USERS_TOPIC, seq, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = now,
            type = UserEventType.TENANT_ASSIGNED,
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
