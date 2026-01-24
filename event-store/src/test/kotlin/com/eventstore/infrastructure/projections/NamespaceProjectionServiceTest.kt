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
import java.util.*
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
    fun `applies created and updated events`() =
        runTest {
            val tenantId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val createdAt = Instant.now()
            val createdPayload =
                NamespaceCreatedEvent(
                    namespaceId = namespaceId,
                    tenantId = tenantId,
                    name = "billing",
                    createdAt = createdAt,
                ).toPayload().toMutableMap()
            createdPayload["tenantName"] = "acme" // Include tenantName for projection service
            val created =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = createdAt,
                    type = NamespaceEventType.CREATED,
                    payload = createdPayload,
                )
            val updatedAt = createdAt.plusSeconds(10)
            val updated =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = 2,
                        ),
                    timestamp = updatedAt,
                    type = NamespaceEventType.UPDATED,
                    payload =
                        NamespaceUpdatedEvent(
                            namespaceId = namespaceId,
                            name = "Billing App",
                            description = "desc",
                            updatedAt = updatedAt,
                        ).toPayload(),
                )

            service.handleEvents(listOf(created, updated))

            // After update, look up by the new name
            val ns = service.getNamespaceByName("acme", "Billing App")
            assertNotNull(ns)
            assertEquals("Billing App", ns.name)
            assertEquals("desc", ns.description)
            // Verify old name no longer works
            assertNull(service.getNamespaceByName("acme", "billing"))
        }

    @Test
    fun `applies delete hides namespace`() =
        runTest {
            val tenantId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val createdAt = Instant.now()
            val createdPayload =
                NamespaceCreatedEvent(
                    namespaceId = namespaceId,
                    tenantId = tenantId,
                    name = "billing",
                    createdAt = createdAt,
                ).toPayload().toMutableMap()
            createdPayload["tenantName"] = "acme" // Include tenantName for projection service
            val created =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = createdAt,
                    type = NamespaceEventType.CREATED,
                    payload = createdPayload,
                )
            val deletedAt = createdAt.plusSeconds(5)
            val deleted =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = 2,
                        ),
                    timestamp = deletedAt,
                    type = NamespaceEventType.DELETED,
                    payload =
                        NamespaceDeletedEvent(
                            namespaceId = namespaceId,
                            deletedAt = deletedAt,
                        ).toPayload(),
                )

            service.handleEvents(listOf(created, deleted))

            assertNull(service.getNamespaceByName("acme", "billing"))
        }

    @Test
    fun `getNamespaceById returns namespace by single UUID`() =
        runTest {
            val tenantId = UUID.randomUUID()
            val namespaceId = UUID.randomUUID()
            val createdAt = Instant.now()
            val createdPayload =
                NamespaceCreatedEvent(
                    namespaceId = namespaceId,
                    tenantId = tenantId,
                    name = "billing",
                    createdAt = createdAt,
                ).toPayload().toMutableMap()
            createdPayload["tenantName"] = "acme"
            val created =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.NAMESPACES_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = createdAt,
                    type = NamespaceEventType.CREATED,
                    payload = createdPayload,
                )

            service.handleEvents(listOf(created))

            val ns = service.getNamespaceById(namespaceId)
            assertNotNull(ns)
            assertEquals(namespaceId, ns.namespaceId)
            assertEquals("billing", ns.name)
        }

    @Test
    fun `getNamespaceById returns null for non-existent namespace`() =
        runTest {
            val nonExistentId = UUID.randomUUID()
            assertNull(service.getNamespaceById(nonExistentId))
        }
}
