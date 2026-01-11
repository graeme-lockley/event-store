package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.events.NamespaceDeletedEvent
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class DeleteNamespaceServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `deletes namespace and emits event`() = runTest {
        // Create tenant and namespace first
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val numberOfEvents = numberOfEvents()

        // Verify namespace exists in projection before deletion
        val namespaceBeforeDeletion = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(namespaceBeforeDeletion, "Namespace should exist in projection before deletion")
        assertEquals("billing", namespaceBeforeDeletion.name)

        // Delete the namespace
        val result = application.deleteNamespace("acme", "billing", deletedBy = "admin")

        assertTrue(result)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val deletedEvent = events.last { it.type == NamespaceEventType.DELETED }
        assertEquals(NamespaceEventType.DELETED, deletedEvent.type)
        // All EventIds are now tenant-scoped

        // Verify namespace is no longer in projection after deletion
        val namespaceAfterDeletion = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNull(namespaceAfterDeletion, "Namespace should not exist in projection after deletion")
    }

    @Test
    fun `throws when namespace does not exist`() = runTest {
        application.createTenant("acme")

        assertFailsWith<NamespaceNotFoundException> {
            application.deleteNamespace("acme", "non-existent")
        }
    }

    @Test
    fun `throws when tenant does not exist`() = runTest {
        assertFailsWith<NamespaceNotFoundException> {
            application.deleteNamespace("unknown", "billing")
        }
    }

    @Test
    fun `returns false when namespace already deleted`() = runTest {
        // Create and delete namespace
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing")

        // Try to delete again - should return false because projection filters out deleted namespaces
        assertFailsWith<NamespaceNotFoundException> {
            application.deleteNamespace("acme", "billing")
        }
    }

    @Test
    fun `uses default deletedBy when not specified`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals("system", payload["deletedBy"])
    }

    @Test
    fun `uses custom deletedBy when specified`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing", deletedBy = "admin@example.com")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals("admin@example.com", payload["deletedBy"])
    }

    @Test
    fun `includes reason when provided`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val reason = "Namespace no longer needed"
        application.deleteNamespace("acme", "billing", reason = reason)

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals(reason, payload["reason"])
    }

    @Test
    fun `omits reason when not provided`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertTrue(!payload.containsKey("reason") || payload["reason"] == null)
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing", deletedBy = "test-user", reason = "test reason")

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("resourceId"))
        assertTrue(payload.containsKey("tenantResourceId"))
        assertTrue(payload.containsKey("deletedBy"))
        assertTrue(payload.containsKey("deletedAt"))
        assertTrue(payload.containsKey("reason"))

        // Verify field values
        assertEquals(namespace.resourceId.toString(), payload["resourceId"])
        assertEquals(tenant.tenantId.toString(), payload["tenantResourceId"])
        assertEquals("test-user", payload["deletedBy"])
        assertEquals("test reason", payload["reason"])
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing")

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, event.id.namespaceId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create and delete first namespace
        application.createTenant("acme")
        application.createNamespace("acme", "namespace-1")
        application.deleteNamespace("acme", "namespace-1")
        val allEvents1 = getEvents()
        val deletedEvent1 = allEvents1.last { it.type == NamespaceEventType.DELETED }
        val sequence1 = deletedEvent1.id.sequence

        // Create and delete second namespace
        application.createNamespace("acme", "namespace-2")
        application.deleteNamespace("acme", "namespace-2")
        val allEvents2 = getEvents()
        val deletedEvent2 = allEvents2.last { it.type == NamespaceEventType.DELETED }
        val sequence2 = deletedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between deletions)
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val beforeDeletion = java.time.Instant.now()
        application.deleteNamespace("acme", "billing")
        val afterDeletion = java.time.Instant.now()

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        assertTrue(event.timestamp.isAfter(beforeDeletion) || event.timestamp == beforeDeletion)
        assertTrue(event.timestamp.isBefore(afterDeletion) || event.timestamp == afterDeletion)
    }

    @Test
    fun `event payload matches NamespaceDeletedEvent structure`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing", deletedBy = "user", reason = "test")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload

        // Verify payload can be parsed back to NamespaceDeletedEvent
        val parsed = NamespaceDeletedEvent.fromPayload(payload)
        assertEquals(namespace.resourceId, parsed.resourceId)
        assertEquals(tenant.tenantId, parsed.tenantResourceId)
        assertEquals("user", parsed.deletedBy)
        assertEquals("test", parsed.reason)
    }

    @Test
    fun `deletes namespace with unicode characters in name`() = runTest {
        val unicodeName = "namespace-测试-🚀"
        application.createTenant("acme")
        application.createNamespace("acme", unicodeName)
        val result = application.deleteNamespace("acme", unicodeName)

        assertTrue(result)
        val events = getEvents()
        val deletedEvent = events.last { it.type == NamespaceEventType.DELETED }
        assertEquals(NamespaceEventType.DELETED, deletedEvent.type)
    }

    @Test
    fun `can delete multiple namespaces sequentially`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "ns-1")
        application.createNamespace("acme", "ns-2")
        application.createNamespace("acme", "ns-3")

        val result1 = application.deleteNamespace("acme", "ns-1")
        val result2 = application.deleteNamespace("acme", "ns-2")
        val result3 = application.deleteNamespace("acme", "ns-3")

        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)

        val events = getEvents()
        val deletedEvents = events.filter { it.type == NamespaceEventType.DELETED }
        assertEquals(3, deletedEvents.size)
    }

    @Test
    fun `event resourceId matches original namespace resourceId`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        val originalResourceId = namespace.resourceId

        application.deleteNamespace("acme", "billing")

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        val payload = event.payload
        assertEquals(originalResourceId.toString(), payload["resourceId"])
        assertEquals(tenant.tenantId.toString(), payload["tenantResourceId"])
    }

    @Test
    fun `can delete namespaces in different tenants`() = runTest {
        application.createTenant("acme")
        application.createTenant("corp")
        application.createNamespace("acme", "billing")
        application.createNamespace("corp", "billing")

        val result1 = application.deleteNamespace("acme", "billing")
        val result2 = application.deleteNamespace("corp", "billing")

        assertTrue(result1)
        assertTrue(result2)

        // Verify both namespaces are deleted
        assertNull(application.namespaceProjectionService.getNamespaceByName("acme", "billing"))
        assertNull(application.namespaceProjectionService.getNamespaceByName("corp", "billing"))
    }

    @Test
    fun `deletes default namespace`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme") // Creates "default" namespace
        val result = application.deleteNamespace("acme", "default")

        assertTrue(result)
        assertNull(application.namespaceProjectionService.getNamespaceByName("acme", "default"))
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            SystemTopics.NAMESPACES_TOPIC_NAME,
            tenantId = SystemTopics.SYSTEM_TENANT_NAME,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_NAME
        )
}

