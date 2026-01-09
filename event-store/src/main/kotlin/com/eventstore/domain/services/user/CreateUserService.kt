package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.User
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserCreatedEvent
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.exceptions.UserAlreadyExistsException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.*

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
    private val tenantProjectionService: TenantProjectionService,
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: CreateUserRequest): User {
        if (userProjectionService.userExistsByEmail(request.email)) {
            throw UserAlreadyExistsException(request.email)
        }

        request.primaryTenantId?.let {
            if (!tenantProjectionService.tenantExistsByName(it)) {
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

        eventPublisher.publishEvent(
            topic = SystemTopics.USERS_TOPIC,
            eventType = UserEventType.CREATED,
            payload = payload.toPayload(),
            timestamp = now
        )

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

