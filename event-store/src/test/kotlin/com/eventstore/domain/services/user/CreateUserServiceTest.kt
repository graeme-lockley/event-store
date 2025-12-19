package com.eventstore.domain.services.user

import com.eventstore.Config
import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.UserStatus
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.UserEventType
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.InMemoryUserRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.infrastructure.projections.UserProjectionService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class CreateUserServiceTest {

    private val config = Config(
        port = 0,
        dataDir = "",
        configDir = "",
        maxBodyBytes = 0,
        rateLimitPerMinute = 0,
        multiTenantEnabled = true,
        authEnabled = true
    )

    @Test
    fun `creates user and emits events`() = runBlocking {
        val topicRepo = InMemoryTopicRepository()
        val eventRepo = InMemoryEventRepository()
        val tenantProjection = TenantProjectionService(InMemoryTenantRepository())
        val userProjection = UserProjectionService(InMemoryUserRepository())

        topicRepo.createTopic(
            resourceId = java.util.UUID.randomUUID(),
            tenantResourceId = java.util.UUID.randomUUID(),
            namespaceResourceId = java.util.UUID.randomUUID(),
            name = SystemTopics.USERS_TOPIC,
            schemas = emptyList(),
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        // Seed tenant
        val tenantEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = Instant.now(),
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                resourceId = java.util.UUID.randomUUID(),
                name = "t-1",
                createdBy = "test",
                createdAt = Instant.now(),
                metadata = emptyMap()
            ).toPayload()
        )
        tenantProjection.handleEvents(listOf(tenantEvent))

        val service = CreateUserService(eventRepo, topicRepo, tenantProjection, userProjection, config)

        val created = service.execute(
            CreateUserRequest(
                email = "alice@example.com",
                name = "Alice",
                password = "secret",
                primaryTenantId = "t-1"
            )
        )

        assertNotNull(created.id)
        assertEquals("alice@example.com", created.email)
        assertEquals(UserStatus.ACTIVE, created.status)

        val storedEvents = eventRepo.getEvents(
            topic = SystemTopics.USERS_TOPIC,
            limit = 10,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, storedEvents.size)
        assertEquals(UserEventType.CREATED, storedEvents.first().type)
    }
}

