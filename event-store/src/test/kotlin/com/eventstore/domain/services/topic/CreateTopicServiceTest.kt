package com.eventstore.domain.services.topic

import com.eventstore.domain.services.PopulateEventStoreState
import com.eventstore.domain.services.createEventStore

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.events.NamespaceCreatedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TopicAlreadyExistsException
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateTopicServiceTest {
    val topicName = "user-events"

    private lateinit var helper: PopulateEventStoreState
    private lateinit var service: CreateTopicService

    @BeforeEach
    fun setup() = runBlocking {
        helper = createEventStore(topicName)
        val tenantProjectionService = TenantProjectionService(InMemoryTenantRepository())
        val namespaceProjectionService = NamespaceProjectionService(InMemoryNamespaceRepository())
        
        // Set up default tenant
        val tenantResourceId = UUID.randomUUID()
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
                resourceId = tenantResourceId,
                name = "default",
                createdAt = Instant.now()
            ).toPayload()
        )
        tenantProjectionService.handleEvents(listOf(tenantEvent))
        
        // Set up default namespace
        val namespaceResourceId = UUID.randomUUID()
        val namespaceEvent = Event(
            id = EventId.create(
                topic = SystemTopics.NAMESPACES_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = Instant.now(),
            type = NamespaceEventType.CREATED,
            payload = NamespaceCreatedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                tenantName = "default",
                name = "default",
                createdAt = Instant.now()
            ).toPayload()
        )
        namespaceProjectionService.handleEvents(listOf(namespaceEvent))
        
        service = CreateTopicService(helper.topicRepository, helper.schemaValidator, tenantProjectionService, namespaceProjectionService)
    }

    @Test
    fun `should create topic successfully`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        val result = service.execute(name, schemas)

        val retrieved = helper.findTopic(name)
        assertNotNull(retrieved)
        assertEquals(name, result.name)
        assertEquals(0L, result.sequence)
        assertEquals(schemas, result.schemas)
        assertEquals(name, retrieved!!.name)
        assertEquals(0L, retrieved.sequence)
        assertEquals(schemas, retrieved.schemas)
    }

    @Test
    fun `should throw exception when topic already exists`() = runTest {
        assertNotNull(helper.findTopic(topicName))
        assertThrows<TopicAlreadyExistsException> {
            service.execute(topicName, listOf(Schema(eventType = "user.created")))
        }
    }

    @Test
    fun `should handle multiple schemas`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created"),
            Schema(eventType = "user.updated")
        )

        val topic = service.execute(name, schemas)

        assertEquals(name, topic.name)
        assertEquals(0L, topic.sequence)
        assertEquals(schemas, topic.schemas)
        val retrieved = helper.findTopic(name)
        assertNotNull(retrieved)
        assertEquals(name, retrieved!!.name)
        assertEquals(0L, retrieved.sequence)
        assertEquals(schemas, retrieved.schemas)

        assertTrue(helper.hasSchema(name, "user.created"))
        assertTrue(helper.hasSchema(name, "user.updated"))
    }

    @Test
    fun `should throw exception when duplicate event types in schemas`() = runTest {
        val name = "new-${topicName}"
        val schemas = listOf(
            Schema(eventType = "user.created", properties = mapOf("id" to "string")),
            Schema(eventType = "user.created", properties = mapOf("id" to "string"))
        )

        assertThrows<IllegalArgumentException> {
            service.execute(name, schemas)
        }
    }
}

