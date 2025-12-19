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
import java.util.UUID
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
        val tenantResourceId = UUID.randomUUID()
        val namespaceResourceId = UUID.randomUUID()
        val createdAt = Instant.now()
        val created = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 1, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = createdAt,
            type = NamespaceEventType.CREATED,
            payload = NamespaceCreatedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                tenantName = "acme",
                name = "billing",
                createdAt = createdAt
            ).toPayload()
        )
        val updatedAt = createdAt.plusSeconds(10)
        val updated = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 2, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = updatedAt,
            type = NamespaceEventType.UPDATED,
            payload = NamespaceUpdatedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                name = "Billing App",
                description = "desc",
                updatedAt = updatedAt
            ).toPayload()
        )

        service.handleEvents(listOf(created, updated))

        // After update, look up by the new name
        val ns = service.getNamespace("acme", "Billing App")
        assertNotNull(ns)
        assertEquals("Billing App", ns.name)
        assertEquals("desc", ns.description)
        // Verify old name no longer works
        assertNull(service.getNamespace("acme", "billing"))
    }

    @Test
    fun `applies delete hides namespace`() = runTest {
        val tenantResourceId = UUID.randomUUID()
        val namespaceResourceId = UUID.randomUUID()
        val createdAt = Instant.now()
        val created = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 1, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = createdAt,
            type = NamespaceEventType.CREATED,
            payload = NamespaceCreatedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                tenantName = "acme",
                name = "billing",
                createdAt = createdAt
            ).toPayload()
        )
        val deletedAt = createdAt.plusSeconds(5)
        val deleted = Event(
            id = EventId.create(SystemTopics.NAMESPACES_TOPIC, 2, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
            timestamp = deletedAt,
            type = NamespaceEventType.DELETED,
            payload = NamespaceDeletedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                deletedAt = deletedAt
            ).toPayload()
        )

        service.handleEvents(listOf(created, deleted))

        assertNull(service.getNamespace("acme", "billing"))
    }
}

