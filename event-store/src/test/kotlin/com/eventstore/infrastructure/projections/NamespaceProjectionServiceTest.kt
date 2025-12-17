package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.NamespaceUpdatedEvent
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NamespaceProjectionServiceTest {
    private lateinit var repository: InMemoryNamespaceRepository
    private lateinit var service: NamespaceProjectionService

    @BeforeEach
    fun setup() {
        repository = InMemoryNamespaceRepository()
        service = NamespaceProjectionService(repository)
    }

    @Test
    fun `applies created and updated events`() = runTest {
        val createdAt = Instant.now()
        val created = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 1, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = createdAt,
            type = NamespaceEventType.CREATED,
            payload = NamespaceCreatedEvent(
                tenantId = "acme",
                namespaceId = "billing",
                name = "Billing",
                createdAt = createdAt
            ).toPayload()
        )
        val updatedAt = createdAt.plusSeconds(10)
        val updated = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 2, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = updatedAt,
            type = NamespaceEventType.UPDATED,
            payload = NamespaceUpdatedEvent(
                tenantId = "acme",
                namespaceId = "billing",
                name = "Billing App",
                description = "desc",
                updatedAt = updatedAt
            ).toPayload()
        )

        service.handleEvents(listOf(created, updated))

        val ns = service.getNamespace("acme", "billing")
        assertNotNull(ns)
        assertEquals("Billing App", ns.name)
        assertEquals("desc", ns.description)
    }

    @Test
    fun `applies delete hides namespace`() = runTest {
        val createdAt = Instant.now()
        val created = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 1, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = createdAt,
            type = NamespaceEventType.CREATED,
            payload = NamespaceCreatedEvent(
                tenantId = "acme",
                namespaceId = "billing",
                name = "Billing",
                createdAt = createdAt
            ).toPayload()
        )
        val deletedAt = createdAt.plusSeconds(5)
        val deleted = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 2, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = deletedAt,
            type = NamespaceEventType.DELETED,
            payload = NamespaceDeletedEvent(
                tenantId = "acme",
                namespaceId = "billing",
                deletedAt = deletedAt
            ).toPayload()
        )

        service.handleEvents(listOf(created, deleted))

        assertNull(service.getNamespace("acme", "billing"))
    }
}

