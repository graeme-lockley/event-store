package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class CreateTenantServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `creates tenant and emits event`() = runTest {
        val numberOfEvents = numberOfEvents()

        val quota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val tenant = application.createTenant("acme", quota, mapOf("plan" to "pro"))

        assertEquals("acme", tenant.name)
        val events = getEvents()

        assertEquals(numberOfEvents + 1, events.size)
        assertEquals(TenantEventType.CREATED, events.last().type)
        // All EventIds are now tenant-scoped
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, events.last().id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, events.last().id.namespaceId)
    }

    @Test
    fun `throws when tenant already exists`() = runTest {
        val tenant = application.createTenant("acme")

        assertEquals("acme", tenant.name)

        assertFailsWith<TenantAlreadyExistsException> {
            application.createTenant("acme")
        }
    }

    @Test
    fun `throws when name is empty`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            application.createTenant("")
        }
    }

    @Test
    fun `throws when name is blank`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            application.createTenant("   ")
        }
    }

    @Test
    fun `creates tenant without quota`() = runTest {
        val tenant = application.createTenant("no-quota-tenant", quota = null)

        assertEquals("no-quota-tenant", tenant.name)
        assertNull(tenant.quota)

        val payload = getEvents().last().payload
        assertTrue(!payload.containsKey("quota") || payload["quota"] == null)
    }

    @Test
    fun `creates tenant without metadata`() = runTest {
        val tenant = application.createTenant("no-metadata-tenant", metadata = emptyMap())

        assertEquals("no-metadata-tenant", tenant.name)
        assertTrue(tenant.metadata.isEmpty())

        val payload = getEvents().last().payload

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
        val tenant = application.createTenant("metadata-tenant", metadata = metadata)

        assertEquals(metadata, tenant.metadata)

        @Suppress("UNCHECKED_CAST")
        val payloadMetadata = getEvents().last().payload["metadata"] as? Map<String, Any>
        assertEquals(metadata, payloadMetadata)
    }

    @Test
    fun `uses default createdBy when not specified`() = runTest {
        val tenant = application.createTenant("default-created-by")

        assertEquals("default-created-by", tenant.name)

        val payload = getEvents().last().payload
        assertEquals("system", payload["createdBy"])
    }

    @Test
    fun `uses custom createdBy when specified`() = runTest {
        val tenant = application.createTenant("custom-created-by", createdBy = "admin@example.com")

        assertEquals("custom-created-by", tenant.name)

        val payload = getEvents().last().payload
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
        val tenant = application.createTenant(
            "payload-test",
            quota = quota,
            metadata = metadata,
            createdBy = "test-user"
        )

        val event = getEvents().last()
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

        val tenant = application.createTenant(
            "complete-tenant",
            quota = quota,
            metadata = metadata,
            createdBy = "admin"
        )

        val afterCreation = java.time.Instant.now()

        // Verify all fields
        assertNotNull(tenant.resourceId)
        assertEquals("complete-tenant", tenant.name)
        assertTrue(tenant.createdAt.isAfter(beforeCreation) || tenant.createdAt == beforeCreation)
        assertTrue(tenant.createdAt.isBefore(afterCreation) || tenant.createdAt == afterCreation)
        assertNull(tenant.updatedAt)
        assertNull(tenant.deletedAt)
        assertEquals(quota, tenant.quota)
        assertEquals(metadata, tenant.metadata)
        assertTrue(tenant.isActive)
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        val tenant = application.createTenant("context-test")
        assertEquals("context-test", tenant.name)

        val event = getEvents().last()
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, event.id.namespaceId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create first tenant
        val tenant1 = application.createTenant("sequence-test-1")
        assertEquals("sequence-test-1", tenant1.name)
        val sequence1 = getEvents().last().id.sequence

        // Create second tenant
        val tenant2 = application.createTenant("sequence-test-2")
        assertEquals("sequence-test-2", tenant2.name)
        val sequence2 = getEvents().last().id.sequence

        // Verify sequence was incremented
        assertEquals(sequence1 + 1, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        val beforeCreation = java.time.Instant.now()
        val tenant = application.createTenant("timestamp-test")
        val afterCreation = java.time.Instant.now()

        val event = getEvents().last()
        assertTrue(event.timestamp.isAfter(beforeCreation) || event.timestamp == beforeCreation)
        assertTrue(event.timestamp.isBefore(afterCreation) || event.timestamp == afterCreation)
        assertEquals(tenant.createdAt, event.timestamp)
    }

    @Test
    fun `each tenant gets unique resource ID`() = runTest {
        val tenant1 = application.createTenant("unique-1")
        val tenant2 = application.createTenant("unique-2")
        val tenant3 = application.createTenant("unique-3")

        val resourceIds = setOf(tenant1.resourceId, tenant2.resourceId, tenant3.resourceId)
        assertEquals(3, resourceIds.size, "Each tenant should have a unique resource ID")
    }

    @Test
    fun `creates tenant with unicode characters in name`() = runTest {
        val unicodeName = "tenant-测试-🚀"
        val tenant = application.createTenant(unicodeName)

        assertEquals(unicodeName, tenant.name)
        assertEquals(unicodeName, getEvents().last().payload["name"])
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC_NAME,
            tenantId = SystemTopics.SYSTEM_TENANT_NAME,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_NAME
        )
}
