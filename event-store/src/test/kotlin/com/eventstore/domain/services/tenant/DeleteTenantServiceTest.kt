package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.events.TenantDeletedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class DeleteTenantServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `deletes tenant and emits event`() = runTest {
        // Create a tenant first
        application.createTenant("acme")
        val numberOfEvents = numberOfEvents()

        // Verify tenant exists in projection before deletion
        val tenantBeforeDeletion = application.tenantProjectionService.getTenantByName("acme")
        assertNotNull(tenantBeforeDeletion, "Tenant should exist in projection before deletion")
        assertEquals("acme", tenantBeforeDeletion.name)

        // Delete the tenant
        val result = application.deleteTenant("acme", deletedBy = "admin")

        assertTrue(result)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val deletedEvent = events.last { it.type == TenantEventType.DELETED }
        assertEquals(TenantEventType.DELETED, deletedEvent.type)
        assertTrue(deletedEvent.id.isTenantScoped)

        // Verify tenant is no longer in projection after deletion
        val tenantAfterDeletion = application.tenantProjectionService.getTenantByName("acme")
        assertNull(tenantAfterDeletion, "Tenant should not exist in projection after deletion")
    }

    @Test
    fun `throws when tenant does not exist`() = runTest {
        assertFailsWith<TenantNotFoundException> {
            application.deleteTenant("non-existent")
        }
    }

    @Test
    fun `throws when tenant already deleted`() = runTest {
        // Create and delete tenant
        application.createTenant("acme")
        application.deleteTenant("acme")

        // Try to delete again - should throw because projection filters out deleted tenants
        assertFailsWith<TenantNotFoundException> {
            application.deleteTenant("acme")
        }
    }

    @Test
    fun `uses default deletedBy when not specified`() = runTest {
        application.createTenant("acme")
        application.deleteTenant("acme")

        val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
        assertEquals("system", payload["deletedBy"])
    }

    @Test
    fun `uses custom deletedBy when specified`() = runTest {
        application.createTenant("acme")
        application.deleteTenant("acme", deletedBy = "admin@example.com")

        val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
        assertEquals("admin@example.com", payload["deletedBy"])
    }

    @Test
    fun `includes reason when provided`() = runTest {
        application.createTenant("acme")
        val reason = "Account closed by user request"
        application.deleteTenant("acme", reason = reason)

        val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
        assertEquals(reason, payload["reason"])
    }

    @Test
    fun `omits reason when not provided`() = runTest {
        application.createTenant("acme")
        application.deleteTenant("acme")

        val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
        assertTrue(!payload.containsKey("reason") || payload["reason"] == null)
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("payload-test")
        application.deleteTenant("payload-test", deletedBy = "test-user", reason = "test reason")

        val event = getEvents().last { it.type == TenantEventType.DELETED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("resourceId"))
        assertTrue(payload.containsKey("deletedBy"))
        assertTrue(payload.containsKey("deletedAt"))
        assertTrue(payload.containsKey("reason"))

        // Verify field values
        assertEquals(tenant.resourceId.toString(), payload["resourceId"])
        assertEquals("test-user", payload["deletedBy"])
        assertEquals("test reason", payload["reason"])
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        application.createTenant("context-test")
        application.deleteTenant("context-test")

        val event = getEvents().last { it.type == TenantEventType.DELETED }
        assertEquals(SystemTopics.SYSTEM_TENANT_ID, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_ID, event.id.namespaceId)
        assertTrue(event.id.isTenantScoped)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create and delete first tenant
        application.createTenant("sequence-test-1")
        application.deleteTenant("sequence-test-1")
        val allEvents1 = getEvents()
        val deletedEvent1 = allEvents1.last { it.type == TenantEventType.DELETED }
        val sequence1 = deletedEvent1.id.sequence

        // Create and delete second tenant
        application.createTenant("sequence-test-2")
        application.deleteTenant("sequence-test-2")
        val allEvents2 = getEvents()
        val deletedEvent2 = allEvents2.last { it.type == TenantEventType.DELETED }
        val sequence2 = deletedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between deletions)
        // sequence1 = first DELETED, then CREATED (+1), then second DELETED should be +2
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        application.createTenant("timestamp-test")
        val beforeDeletion = java.time.Instant.now()
        application.deleteTenant("timestamp-test")
        val afterDeletion = java.time.Instant.now()

        val event = getEvents().last { it.type == TenantEventType.DELETED }
        assertTrue(event.timestamp.isAfter(beforeDeletion) || event.timestamp == beforeDeletion)
        assertTrue(event.timestamp.isBefore(afterDeletion) || event.timestamp == afterDeletion)
    }

    @Test
    fun `event payload matches TenantDeletedEvent structure`() = runTest {
        val tenant = application.createTenant("structure-test")
        application.deleteTenant("structure-test", deletedBy = "user", reason = "test")

        val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload

        // Verify payload can be parsed back to TenantDeletedEvent
        val parsed = TenantDeletedEvent.fromPayload(payload)
        assertEquals(tenant.resourceId, parsed.resourceId)
        assertEquals("user", parsed.deletedBy)
        assertEquals("test", parsed.reason)
    }

    @Test
    fun `deletes tenant with unicode characters in name`() = runTest {
        val unicodeName = "tenant-测试-🚀"
        application.createTenant(unicodeName)
        val result = application.deleteTenant(unicodeName)

        assertTrue(result)
        val events = getEvents()
        val deletedEvent = events.last { it.type == TenantEventType.DELETED }
        assertEquals(TenantEventType.DELETED, deletedEvent.type)
    }

    @Test
    fun `can delete multiple tenants sequentially`() = runTest {
        application.createTenant("multi-1")
        application.createTenant("multi-2")
        application.createTenant("multi-3")

        val result1 = application.deleteTenant("multi-1")
        val result2 = application.deleteTenant("multi-2")
        val result3 = application.deleteTenant("multi-3")

        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)

        val events = getEvents()
        val deletedEvents = events.filter { it.type == TenantEventType.DELETED }
        assertEquals(3, deletedEvents.size)
    }

    @Test
    fun `event resourceId matches original tenant resourceId`() = runTest {
        val tenant = application.createTenant("resource-id-test")
        val originalResourceId = tenant.resourceId

        application.deleteTenant("resource-id-test")

        val event = getEvents().last { it.type == TenantEventType.DELETED }
        val payload = event.payload
        assertEquals(originalResourceId.toString(), payload["resourceId"])
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
}

