package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.User
import com.eventstore.domain.UserStatus
import com.eventstore.domain.UserTenantAssociation
import com.eventstore.domain.events.UserCreatedEvent
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.events.UserPasswordChangedEvent
import com.eventstore.domain.events.UserStatusChangedEvent
import com.eventstore.domain.events.UserTenantAssignedEvent
import com.eventstore.domain.events.UserTenantRemovedEvent
import com.eventstore.domain.events.UserUpdatedEvent
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.ports.outbound.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class UserProjectionService(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(UserProjectionService::class.java)
    private val mutex = Mutex()

    suspend fun handleEvents(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) return DeliveryResult(success = true)
        return try {
            mutex.withLock { events.forEach { applyEvent(it) } }
            DeliveryResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to apply user events", e)
            DeliveryResult(success = false, error = e.message)
        }
    }

    suspend fun getUser(id: String): User? = userRepository.findById(id)?.takeIf { it.status != UserStatus.DELETED }
    suspend fun getUserByEmail(email: String): User? = userRepository.findByEmail(email)?.takeIf { it.status != UserStatus.DELETED }
    suspend fun getAllUsers(): List<User> = userRepository.findAll().filter { it.status != UserStatus.DELETED }
    suspend fun getAssociations(userId: String): List<UserTenantAssociation> = userRepository.getAssociations(userId)
    suspend fun userExistsByEmail(email: String): Boolean = getUserByEmail(email) != null

    private suspend fun applyEvent(event: Event) {
        when (event.type) {
            UserEventType.CREATED -> {
                val payload = UserCreatedEvent.fromPayload(event.payload)
                val user = User(
                    id = payload.userId,
                    email = payload.email,
                    name = payload.name,
                    passwordHash = payload.passwordHash,
                    status = payload.status,
                    createdAt = payload.createdAt,
                    metadata = payload.metadata
                )
                userRepository.save(user)
            }

            UserEventType.UPDATED -> {
                val payload = UserUpdatedEvent.fromPayload(event.payload)
                val existing = userRepository.findById(payload.userId) ?: return
                val updated = existing.copy(
                    email = payload.email ?: existing.email,
                    name = payload.name ?: existing.name,
                    updatedAt = payload.updatedAt,
                    metadata = payload.metadata ?: existing.metadata
                )
                userRepository.save(updated)
            }

            UserEventType.STATUS_CHANGED -> {
                val payload = UserStatusChangedEvent.fromPayload(event.payload)
                val existing = userRepository.findById(payload.userId) ?: return
                val updated = existing.copy(
                    status = payload.status,
                    updatedAt = payload.changedAt
                )
                userRepository.save(updated)
            }

            UserEventType.PASSWORD_CHANGED -> {
                val payload = UserPasswordChangedEvent.fromPayload(event.payload)
                val existing = userRepository.findById(payload.userId) ?: return
                val updated = existing.copy(
                    passwordHash = payload.passwordHash,
                    updatedAt = payload.changedAt
                )
                userRepository.save(updated)
            }

            UserEventType.TENANT_ASSIGNED -> {
                val payload = UserTenantAssignedEvent.fromPayload(event.payload)
                val association = UserTenantAssociation(
                    userId = payload.userId,
                    tenantId = payload.tenantId,
                    role = payload.role,
                    assignedAt = payload.assignedAt,
                    assignedBy = payload.assignedBy,
                    isPrimary = payload.isPrimary
                )
                userRepository.saveAssociation(association)
            }

            UserEventType.TENANT_REMOVED -> {
                val payload = UserTenantRemovedEvent.fromPayload(event.payload)
                val existing = userRepository.getAssociations(payload.userId).toMutableList()
                existing.removeIf { it.tenantId == payload.tenantId }
                // replace associations
                existing.forEach { userRepository.saveAssociation(it) }
            }

            else -> logger.debug("Ignoring non-user event type ${event.type}")
        }
    }
}

