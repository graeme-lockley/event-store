package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TenantProjectionServiceTest {

    private lateinit var tenantRepository: InMemoryTenantRepository
    private lateinit var service: TenantProjectionService

    @BeforeEach
    fun setup() {
        tenantRepository = InMemoryTenantRepository()
        service = TenantProjectionService(tenantRepository)
    }

    @Test
    fun `applies created and updated events`() = runTest {
        val resourceId = UUID.randomUUID()
        val createdAt = Instant.now()
        val createdEvent = Event(
            id = EventId.create(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                sequence = 1
            ),
            timestamp = createdAt,
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                tenantId = resourceId,
                name = "acme",
                createdBy = "system",
                createdAt = createdAt
            ).toPayload()
        )

        val updatedAt = createdAt.plusSeconds(60)
        val updatedEvent = Event(
            id = EventId.create(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                sequence = 2
            ),
            timestamp = updatedAt,
            type = TenantEventType.UPDATED,
            payload = TenantUpdatedEvent(
                tenantId = resourceId,
                name = "Acme Corp",
                updatedBy = "system",
                updatedAt = updatedAt
            ).toPayload()
        )

        service.handleEvents(listOf(createdEvent, updatedEvent))

        // After update, look up by the new name
        val tenant = service.getTenantByName("Acme Corp")
        assertNotNull(tenant)
        assertEquals("Acme Corp", tenant.name)
        assertEquals(updatedAt, tenant.updatedAt)
        // Verify old name no longer works
        assertNull(service.getTenantByName("acme"))
    }

    @Test
    fun `applies delete event and hides tenant`() = runTest {
        val resourceId = UUID.randomUUID()
        val createdAt = Instant.now()
        val createdEvent = Event(
            id = EventId.create(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                sequence = 1
            ),
            timestamp = createdAt,
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                tenantId = resourceId,
                name = "acme",
                createdBy = "system",
                createdAt = createdAt
            ).toPayload()
        )

        val deletedAt = createdAt.plusSeconds(30)
        val deletedEvent = Event(
            id = EventId.create(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                sequence = 2
            ),
            timestamp = deletedAt,
            type = TenantEventType.DELETED,
            payload = TenantDeletedEvent(
                tenantId = resourceId,
                deletedBy = "system",
                deletedAt = deletedAt
            ).toPayload()
        )

        service.handleEvents(listOf(createdEvent, deletedEvent))

        assertNull(service.getTenantByName("acme"))
    }
}

