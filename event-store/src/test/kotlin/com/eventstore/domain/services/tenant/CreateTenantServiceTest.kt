package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.InvalidTenantNameException
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
        // EventIds are now topic-scoped (topicId/sequence)
        assertEquals(SystemTopics.TENANTS_TOPIC_ID, events.last().id.topicId)
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
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("")
        }
    }

    @Test
    fun `throws when name is blank`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
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
    fun `creates tenant with metadata including complex types`() = runTest {
        val metadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        val tenant = application.createTenant("metadata-tenant", metadata = metadata)
        assertEquals(metadata, tenant.metadata)
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
        assertTrue(payload.containsKey("tenantId"))
        assertTrue(payload.containsKey("name"))
        assertTrue(payload.containsKey("createdBy"))
        assertTrue(payload.containsKey("createdAt"))
        assertTrue(payload.containsKey("metadata"))
        assertTrue(payload.containsKey("quota"))

        // Verify field values
        assertEquals(tenant.tenantId.toString(), payload["tenantId"])
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
        assertNotNull(tenant.tenantId)
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
    fun `each tenant gets unique resource ID`() = runTest {
        val tenant1 = application.createTenant("unique-1")
        val tenant2 = application.createTenant("unique-2")
        val tenant3 = application.createTenant("unique-3")

        val resourceIds = setOf(tenant1.tenantId, tenant2.tenantId, tenant3.tenantId)
        assertEquals(3, resourceIds.size, "Each tenant should have a unique resource ID")
    }


    // Rule 3: Tenant name format validation tests
    @Test
    fun `throws when tenant name starts with hyphen`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("-invalid-name")
        }
    }

    @Test
    fun `throws when tenant name ends with hyphen`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("invalid-name-")
        }
    }

    @Test
    fun `throws when tenant name contains special characters`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("invalid_name")
        }
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("invalid.name")
        }
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("invalid@name")
        }
    }

    @Test
    fun `throws when tenant name is too short`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant("a")
        }
    }

    @Test
    fun `throws when tenant name is too long`() = runTest {
        val longName = "a".repeat(65) // 65 characters
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant(longName)
        }
    }

    @Test
    fun `throws when tenant name is reserved system name`() = runTest {
        assertFailsWith<InvalidTenantNameException> {
            application.createTenant(SystemTopics.SYSTEM_TENANT_NAME)
        }
    }

    @Test
    fun `creates tenant with valid name formats`() = runTest {
        // Test various valid formats: alphanumeric, mixed case, hyphens, boundaries
        assertEquals("validname123", application.createTenant("validname123").name)
        assertEquals("ValidName123", application.createTenant("ValidName123").name)
        assertEquals("valid-name-123", application.createTenant("valid-name-123").name)
        assertEquals("ab", application.createTenant("ab").name) // minimum length
        assertEquals("a".repeat(64), application.createTenant("a".repeat(64)).name) // maximum length
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            topicId = SystemTopics.TENANTS_TOPIC_ID
        )
}
