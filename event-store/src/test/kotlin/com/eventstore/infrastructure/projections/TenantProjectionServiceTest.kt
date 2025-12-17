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
        val createdAt = Instant.now()
        val createdEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = createdAt,
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                tenantId = "acme",
                name = "Acme",
                createdBy = "system",
                createdAt = createdAt
            ).toPayload()
        )

        val updatedAt = createdAt.plusSeconds(60)
        val updatedEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 2,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = updatedAt,
            type = TenantEventType.UPDATED,
            payload = TenantUpdatedEvent(
                tenantId = "acme",
                name = "Acme Corp",
                updatedBy = "system",
                updatedAt = updatedAt
            ).toPayload()
        )

        service.handleEvents(listOf(createdEvent, updatedEvent))

        val tenant = service.getTenant("acme")
        assertNotNull(tenant)
        assertEquals("Acme Corp", tenant.name)
        assertEquals(updatedAt, tenant.updatedAt)
    }

    @Test
    fun `applies delete event and hides tenant`() = runTest {
        val createdAt = Instant.now()
        val createdEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = createdAt,
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                tenantId = "acme",
                name = "Acme",
                createdBy = "system",
                createdAt = createdAt
            ).toPayload()
        )

        val deletedAt = createdAt.plusSeconds(30)
        val deletedEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 2,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = deletedAt,
            type = TenantEventType.DELETED,
            payload = TenantDeletedEvent(
                tenantId = "acme",
                deletedBy = "system",
                deletedAt = deletedAt
            ).toPayload()
        )

        service.handleEvents(listOf(createdEvent, deletedEvent))

        assertNull(service.getTenant("acme"))
    }
}

