package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.User
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserCreatedEvent
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.UserAlreadyExistsException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import com.eventstore.domain.tenants.SystemTopics
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

data class CreateUserRequest(
    val email: String,
    val name: String,
    val password: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdBy: String = "system",
    val metadata: Map<String, Any> = emptyMap(),
    val primaryTenantId: String? = null
)

class CreateUserService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
    private val tenantProjectionService: TenantProjectionService,
    private val userProjectionService: UserProjectionService,
    private val config: Config
) {
    suspend fun execute(request: CreateUserRequest): User {
        if (!config.multiTenantEnabled) {
            throw IllegalStateException("Multi-tenant support is disabled")
        }

        if (userProjectionService.userExistsByEmail(request.email)) {
            throw UserAlreadyExistsException(request.email)
        }

        request.primaryTenantId?.let {
            if (!tenantProjectionService.tenantExists(it)) {
                throw TenantNotFoundException(it)
            }
        }

        val now = Instant.now()
        val userId = UUID.randomUUID().toString()
        val passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt())

        val payload = UserCreatedEvent(
            userId = userId,
            email = request.email,
            name = request.name,
            passwordHash = passwordHash,
            status = request.status,
            createdBy = request.createdBy,
            createdAt = now,
            metadata = request.metadata
        )

        val seq = topicRepository.getAndIncrementSequence(
            topicName = SystemTopics.USERS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.USERS_TOPIC,
                sequence = seq,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = UserEventType.CREATED,
            payload = payload.toPayload()
        )

        eventRepository.storeEvents(
            listOf(event),
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        userProjectionService.handleEvents(listOf(event))

        return User(
            id = userId,
            email = request.email,
            name = request.name,
            passwordHash = passwordHash,
            status = request.status,
            createdAt = now,
            metadata = request.metadata,
            primaryTenantId = request.primaryTenantId
        )
    }
}

