package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.EventId
import com.eventstore.domain.Quota
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateTenantServiceTest {

    private lateinit var topicRepository: InMemoryTopicRepository
    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var projectionService: TenantProjectionService
    private lateinit var service: CreateTenantService
    private lateinit var config: Config

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup(@TempDir @Suppress("UNUSED_PARAMETER") tempDir: java.nio.file.Path) {
        topicRepository = InMemoryTopicRepository()
        eventRepository = InMemoryEventRepository()
        val tenantRepo = InMemoryTenantRepository()
        projectionService = TenantProjectionService(tenantRepo)
        config = Config(
            port = 0,
            dataDir = "./data",
            configDir = "./config",
            maxBodyBytes = 1024,
            rateLimitPerMinute = 10,
            multiTenantEnabled = true,
            authEnabled = false
        )
        service = CreateTenantService(eventRepository, topicRepository, projectionService, config)

        runBlocking {
            topicRepository.createTopic(
                resourceId = java.util.UUID.randomUUID(),
                tenantResourceId = java.util.UUID.randomUUID(),
                namespaceResourceId = java.util.UUID.randomUUID(),
                name = SystemTopics.TENANTS_TOPIC,
                schemas = emptyList(),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
        }
    }

    @Test
    fun `creates tenant and emits event`() = runTest {
        val quota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val tenant = service.execute(
            CreateTenantRequest(
                name = "acme",
                quota = quota,
                metadata = mapOf("plan" to "pro")
            )
        )

        assertEquals("acme", tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, events.size)
        assertEquals(TenantEventType.CREATED, events.first().type)
        assertTrue(events.first().id.isTenantScoped)
    }

    @Test
    fun `throws when multi tenant disabled`() = runTest {
        val disabledService = CreateTenantService(
            eventRepository,
            topicRepository,
            projectionService,
            config.copy(multiTenantEnabled = false)
        )

        assertFailsWith<IllegalStateException> {
            disabledService.execute(CreateTenantRequest(name = "acme"))
        }
    }

    @Test
    fun `throws when tenant already exists`() = runTest {
        val resourceId = java.util.UUID.randomUUID()
        val createdAt = java.time.Instant.now()
        projectionService.handleEvents(
            listOf(
                com.eventstore.domain.Event(
                    id = EventId.create(
                        topic = SystemTopics.TENANTS_TOPIC,
                        sequence = 1,
                        tenantId = SystemTopics.SYSTEM_TENANT_ID,
                        namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                    ),
                    timestamp = createdAt,
                    type = TenantEventType.CREATED,
                    payload = mapOf(
                        "resourceId" to resourceId.toString(),
                        "name" to "acme",
                        "createdAt" to createdAt.toString(),
                        "createdBy" to "test",
                        "metadata" to emptyMap<String, Any>()
                    )
                )
            )
        )

        assertFailsWith<TenantAlreadyExistsException> {
            service.execute(CreateTenantRequest(name = "acme"))
        }
    }

    @Test
    fun `throws when name is empty`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            service.execute(CreateTenantRequest(name = ""))
        }
    }

    @Test
    fun `throws when name is blank`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            service.execute(CreateTenantRequest(name = "   "))
        }
    }

    @Test
    fun `creates tenant without quota`() = runTest {
        val tenant = service.execute(
            CreateTenantRequest(
                name = "no-quota-tenant",
                quota = null
            )
        )

        assertEquals("no-quota-tenant", tenant.name)
        assertNull(tenant.quota)
        
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, events.size)
        val payload = events.first().payload
        assertTrue(!payload.containsKey("quota") || payload["quota"] == null)
    }

    @Test
    fun `creates tenant without metadata`() = runTest {
        val tenant = service.execute(
            CreateTenantRequest(
                name = "no-metadata-tenant",
                metadata = emptyMap()
            )
        )

        assertEquals("no-metadata-tenant", tenant.name)
        assertTrue(tenant.metadata.isEmpty())
        
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val payload = events.first().payload
        @Suppress("UNCHECKED_CAST")
        val metadata = payload["metadata"] as? Map<String, Any>
        assertEquals(metadata?.isEmpty(), true)
    }

    @Test
    fun `creates tenant with various metadata types`() = runTest {
        val metadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        val tenant = service.execute(
            CreateTenantRequest(
                name = "metadata-tenant",
                metadata = metadata
            )
        )

        assertEquals(metadata, tenant.metadata)
        
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        @Suppress("UNCHECKED_CAST")
        val payloadMetadata = events.first().payload["metadata"] as? Map<String, Any>
        assertEquals(metadata, payloadMetadata)
    }

    @Test
    fun `uses default createdBy when not specified`() = runTest {
        val tenant = service.execute(
            CreateTenantRequest(
                name = "default-created-by"
            )
        )

        assertEquals("default-created-by", tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val payload = events.first().payload
        assertEquals("system", payload["createdBy"])
    }

    @Test
    fun `uses custom createdBy when specified`() = runTest {
        val tenant = service.execute(
            CreateTenantRequest(
                name = "custom-created-by",
                createdBy = "admin@example.com"
            )
        )

        assertEquals("custom-created-by", tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val payload = events.first().payload
        assertEquals("admin@example.com", payload["createdBy"])
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val quota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        val tenant = service.execute(
            CreateTenantRequest(
                name = "payload-test",
                quota = quota,
                metadata = metadata,
                createdBy = "test-user"
            )
        )

        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val event = events.first()
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("resourceId"))
        assertTrue(payload.containsKey("name"))
        assertTrue(payload.containsKey("createdBy"))
        assertTrue(payload.containsKey("createdAt"))
        assertTrue(payload.containsKey("metadata"))
        assertTrue(payload.containsKey("quota"))

        // Verify field values
        assertEquals(tenant.resourceId.toString(), payload["resourceId"])
        assertEquals("payload-test", payload["name"])
        assertEquals("test-user", payload["createdBy"])
        assertEquals(metadata, payload["metadata"])
        
        // Verify quota structure
        @Suppress("UNCHECKED_CAST")
        val quotaMap = payload["quota"] as? Map<String, Any>
        assertNotNull(quotaMap)
        assertEquals(quota.maxTopics, quotaMap["maxTopics"])
        assertEquals(quota.maxNamespaces, quotaMap["maxNamespaces"])
        assertEquals(quota.maxEventsPerDay, quotaMap["maxEventsPerDay"])
        assertEquals(quota.maxConsumers, quotaMap["maxConsumers"])
        assertEquals(quota.maxUsers, quotaMap["maxUsers"])
        assertEquals(quota.maxEventSizeBytes, quotaMap["maxEventSizeBytes"])
    }

    @Test
    fun `returned tenant object has all fields correctly set`() = runTest {
        val quota = Quota(
            maxTopics = 20,
            maxNamespaces = 10,
            maxEventsPerDay = 2000,
            maxConsumers = 5,
            maxUsers = 10,
            maxEventSizeBytes = 1024
        )
        val metadata = mapOf("environment" to "production", "version" to "1.0")
        val beforeCreation = java.time.Instant.now()
        
        val tenant = service.execute(
            CreateTenantRequest(
                name = "complete-tenant",
                quota = quota,
                metadata = metadata,
                createdBy = "admin"
            )
        )
        
        val afterCreation = java.time.Instant.now()

        // Verify all fields
        assertNotNull(tenant.resourceId)
        assertEquals("complete-tenant", tenant.name)
        assertTrue(tenant.createdAt.isAfter(beforeCreation) || tenant.createdAt.equals(beforeCreation))
        assertTrue(tenant.createdAt.isBefore(afterCreation) || tenant.createdAt.equals(afterCreation))
        assertNull(tenant.updatedAt)
        assertNull(tenant.deletedAt)
        assertEquals(quota, tenant.quota)
        assertEquals(metadata, tenant.metadata)
        assertTrue(tenant.isActive)
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        val tenant = service.execute(
            CreateTenantRequest(name = "context-test")
        )

        assertEquals("context-test", tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, events.size)
        
        val event = events.first()
        assertEquals(SystemTopics.SYSTEM_TENANT_ID, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_ID, event.id.namespaceId)
        assertTrue(event.id.isTenantScoped)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create first tenant
        val tenant1 = service.execute(CreateTenantRequest(name = "sequence-test-1"))
        assertEquals("sequence-test-1", tenant1.name)
        val events1 = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val sequence1 = events1.first().id.sequence

        // Create second tenant
        val tenant2 = service.execute(CreateTenantRequest(name = "sequence-test-2"))
        assertEquals("sequence-test-2", tenant2.name)
        val events2 = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val sequence2 = events2.last().id.sequence

        // Verify sequence was incremented
        assertEquals(sequence1 + 1, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        val beforeCreation = java.time.Instant.now()
        val tenant = service.execute(CreateTenantRequest(name = "timestamp-test"))
        val afterCreation = java.time.Instant.now()

        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val event = events.first()
        
        assertTrue(event.timestamp.isAfter(beforeCreation) || event.timestamp.equals(beforeCreation))
        assertTrue(event.timestamp.isBefore(afterCreation) || event.timestamp.equals(afterCreation))
        assertEquals(tenant.createdAt, event.timestamp)
    }

    @Test
    fun `throws when tenants topic does not exist`() = runTest {
        val emptyTopicRepo = InMemoryTopicRepository()
        val serviceWithoutTopic = CreateTenantService(
            eventRepository,
            emptyTopicRepo,
            projectionService,
            config
        )

        assertFailsWith<TopicNotFoundException> {
            serviceWithoutTopic.execute(CreateTenantRequest(name = "missing-topic"))
        }
    }

    @Test
    fun `each tenant gets unique resource ID`() = runTest {
        val tenant1 = service.execute(CreateTenantRequest(name = "unique-1"))
        val tenant2 = service.execute(CreateTenantRequest(name = "unique-2"))
        val tenant3 = service.execute(CreateTenantRequest(name = "unique-3"))

        val resourceIds = setOf(tenant1.resourceId, tenant2.resourceId, tenant3.resourceId)
        assertEquals(3, resourceIds.size, "Each tenant should have a unique resource ID")
    }

    @Test
    fun `creates tenant with long name`() = runTest {
        val longName = "a".repeat(255)
        val tenant = service.execute(CreateTenantRequest(name = longName))

        assertEquals(longName, tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(longName, events.first().payload["name"])
    }

    @Test
    fun `creates tenant with special characters in name`() = runTest {
        val specialName = "tenant-123_test.456"
        val tenant = service.execute(CreateTenantRequest(name = specialName))

        assertEquals(specialName, tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(specialName, events.first().payload["name"])
    }

    @Test
    fun `creates tenant with unicode characters in name`() = runTest {
        val unicodeName = "tenant-测试-🚀"
        val tenant = service.execute(CreateTenantRequest(name = unicodeName))

        assertEquals(unicodeName, tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(unicodeName, events.first().payload["name"])
    }

    @Test
    fun `tenant name is case sensitive`() = runTest {
        val tenant1 = service.execute(CreateTenantRequest(name = "CaseSensitive"))
        val tenant2 = service.execute(CreateTenantRequest(name = "casesensitive"))

        assertEquals("CaseSensitive", tenant1.name)
        assertEquals("casesensitive", tenant2.name)
        assertTrue(tenant1.resourceId != tenant2.resourceId)
    }

    @Test
    fun `creates tenant with minimal request`() = runTest {
        val tenant = service.execute(CreateTenantRequest(name = "minimal"))

        assertEquals("minimal", tenant.name)
        assertNull(tenant.quota)
        assertTrue(tenant.metadata.isEmpty())
        assertNotNull(tenant.resourceId)
        assertNotNull(tenant.createdAt)
        assertNull(tenant.updatedAt)
        assertNull(tenant.deletedAt)
    }

    @Test
    fun `event payload matches TenantCreatedEvent structure`() = runTest {
        val quota = Quota(
            maxTopics = 5,
            maxNamespaces = 3,
            maxEventsPerDay = 500,
            maxConsumers = 1,
            maxUsers = 2,
            maxEventSizeBytes = 256
        )
        val metadata = mapOf("key" to "value")
        val tenant = service.execute(
            CreateTenantRequest(
                name = "structure-test",
                quota = quota,
                metadata = metadata,
                createdBy = "user"
            )
        )

        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        val payload = events.first().payload

        // Verify payload can be parsed back to TenantCreatedEvent
        val parsed = com.eventstore.domain.events.TenantCreatedEvent.fromPayload(payload)
        assertEquals(tenant.resourceId, parsed.resourceId)
        assertEquals("structure-test", parsed.name)
        assertEquals(quota, parsed.quota)
        assertEquals("user", parsed.createdBy)
        assertEquals(metadata, parsed.metadata)
    }
}

