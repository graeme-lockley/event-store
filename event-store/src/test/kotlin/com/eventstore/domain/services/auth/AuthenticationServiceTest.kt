package com.eventstore.domain.services.auth

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.UserCreatedEvent
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.auth.SessionManager
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.UserProjectionService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

class AuthenticationServiceTest {

    private fun buildProjection(password: String): UserProjectionService {
        val repo = InMemoryUserRepository()
        val projection = UserProjectionService(repo)
        val now = Instant.now()
        val event = Event(
            id = EventId.create(
                topic = SystemTopics.USERS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = now,
            type = UserEventType.CREATED,
            payload = UserCreatedEvent(
                userId = "u1",
                email = "user@example.com",
                name = "Test User",
                passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
                status = UserStatus.ACTIVE,
                createdBy = "test",
                createdAt = now,
                metadata = emptyMap()
            ).toPayload()
        )
        runBlocking { projection.handleEvents(listOf(event)) }
        return projection
    }

    @Test
    fun `login succeeds for active user`() = runTest {
        val projection = buildProjection("secret")
        val authService = AuthenticationService(projection, SessionManager())

        val (session, tenants) = authService.login("user@example.com", "secret")

        assertNotNull(session)
        assertEquals("u1", session.userId)
        assertEquals(emptyList<String>(), tenants)
    }

    @Test
    fun `login fails for invalid password`() = runTest {
        val projection = buildProjection("secret")
        val authService = AuthenticationService(projection, SessionManager())

        assertFailsWith<com.eventstore.domain.exceptions.InvalidCredentialsException> {
            authService.login("user@example.com", "wrong")
        }
    }
}

