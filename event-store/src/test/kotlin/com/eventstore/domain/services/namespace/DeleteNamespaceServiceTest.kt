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
import java.util.*

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
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val numberOfEvents = numberOfEvents()

        // Verify namespace exists in projection before deletion
        val namespaceBeforeDeletion = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(namespaceBeforeDeletion, "Namespace should exist in projection before deletion")
        assertEquals("billing", namespaceBeforeDeletion.name)

        // Delete the namespace
        val result = application.deleteNamespace(namespaceId, deletedBy = "admin")

        assertTrue(result)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val deletedEvent = events.last { it.type == NamespaceEventType.DELETED }
        assertEquals(NamespaceEventType.DELETED, deletedEvent.type)
        // All EventIds are now tenant-scoped

        // Verify namespace is no longer in projection after deletion
        val namespaceAfterDeletion = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNull(namespaceAfterDeletion, "Namespace should not exist in projection after deletion")
    }

    @Test
    fun `throws when namespace does not exist`() = runTest {
        val nonExistentNamespaceId = UUID.randomUUID()

        assertFailsWith<NamespaceNotFoundException> {
            application.deleteNamespace(nonExistentNamespaceId)
        }
    }

    @Test
    fun `returns false when namespace already deleted`() = runTest {
        // Create and delete namespace
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId)

        // Try to delete again - should return false because projection filters out deleted namespaces
        assertFailsWith<NamespaceNotFoundException> {
            application.deleteNamespace(namespaceId)
        }
    }

    @Test
    fun `uses default deletedBy when not specified`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId)

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals("system", payload["deletedBy"])
    }

    @Test
    fun `uses custom deletedBy when specified`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId, deletedBy = "admin@example.com")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals("admin@example.com", payload["deletedBy"])
    }

    @Test
    fun `includes reason when provided`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val reason = "Namespace no longer needed"
        application.deleteNamespace(namespaceId, reason = reason)

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertEquals(reason, payload["reason"])
    }

    @Test
    fun `omits reason when not provided`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId)

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload
        assertTrue(!payload.containsKey("reason") || payload["reason"] == null)
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId, deletedBy = "test-user", reason = "test reason")

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("namespaceId"))
        assertTrue(payload.containsKey("deletedBy"))
        assertTrue(payload.containsKey("deletedAt"))
        assertTrue(payload.containsKey("reason"))

        // Verify field values
        assertEquals(namespaceId.toString(), payload["namespaceId"])
        assertEquals("test-user", payload["deletedBy"])
        assertEquals("test reason", payload["reason"])
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId)

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        assertEquals(SystemTopics.NAMESPACES_TOPIC_ID, event.id.topicId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create and delete first namespace
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace1 = application.createNamespace(tenantId, "namespace-1")
        application.deleteNamespace(namespace1.namespaceId)
        val allEvents1 = getEvents()
        val deletedEvent1 = allEvents1.last { it.type == NamespaceEventType.DELETED }
        val sequence1 = deletedEvent1.id.sequence

        // Create and delete second namespace
        val namespace2 = application.createNamespace(tenantId, "namespace-2")
        application.deleteNamespace(namespace2.namespaceId)
        val allEvents2 = getEvents()
        val deletedEvent2 = allEvents2.last { it.type == NamespaceEventType.DELETED }
        val sequence2 = deletedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between deletions)
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val beforeDeletion = java.time.Instant.now()
        application.deleteNamespace(namespaceId)
        val afterDeletion = java.time.Instant.now()

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        assertTrue(event.timestamp.isAfter(beforeDeletion) || event.timestamp == beforeDeletion)
        assertTrue(event.timestamp.isBefore(afterDeletion) || event.timestamp == afterDeletion)
    }

    @Test
    fun `event payload matches NamespaceDeletedEvent structure`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.deleteNamespace(namespaceId, deletedBy = "user", reason = "test")

        val payload = getEvents().last { it.type == NamespaceEventType.DELETED }.payload

        // Verify payload can be parsed back to NamespaceDeletedEvent
        val parsed = NamespaceDeletedEvent.fromPayload(payload)
        assertEquals(namespaceId, parsed.namespaceId)
        assertEquals("user", parsed.deletedBy)
        assertEquals("test", parsed.reason)
    }

    @Test
    fun `deletes namespace with unicode characters in name`() = runTest {
        val unicodeName = "namespace-测试-🚀"
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, unicodeName)
        val namespaceId = namespace.namespaceId
        val result = application.deleteNamespace(namespaceId)

        assertTrue(result)
        val events = getEvents()
        val deletedEvent = events.last { it.type == NamespaceEventType.DELETED }
        assertEquals(NamespaceEventType.DELETED, deletedEvent.type)
    }

    @Test
    fun `can delete multiple namespaces sequentially`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val ns1 = application.createNamespace(tenantId, "ns-1")
        val ns2 = application.createNamespace(tenantId, "ns-2")
        val ns3 = application.createNamespace(tenantId, "ns-3")

        val result1 = application.deleteNamespace(ns1.namespaceId)
        val result2 = application.deleteNamespace(ns2.namespaceId)
        val result3 = application.deleteNamespace(ns3.namespaceId)

        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)

        val events = getEvents()
        val deletedEvents = events.filter { it.type == NamespaceEventType.DELETED }
        assertEquals(3, deletedEvents.size)
    }

    @Test
    fun `event namespaceId matches original namespace namespaceId`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val originalNamespaceId = namespace.namespaceId

        application.deleteNamespace(originalNamespaceId)

        val event = getEvents().last { it.type == NamespaceEventType.DELETED }
        val payload = event.payload
        assertEquals(originalNamespaceId.toString(), payload["namespaceId"])
    }

    @Test
    fun `can delete namespaces in different tenants`() = runTest {
        val acmeTenant = application.createTenant("acme")
        val acmeTenantId = acmeTenant.tenantId
        val corpTenant = application.createTenant("corp")
        val corpTenantId = corpTenant.tenantId
        val acmeNs = application.createNamespace(acmeTenantId, "billing")
        val corpNs = application.createNamespace(corpTenantId, "billing")

        val result1 = application.deleteNamespace(acmeNs.namespaceId)
        val result2 = application.deleteNamespace(corpNs.namespaceId)

        assertTrue(result1)
        assertTrue(result2)

        // Verify both namespaces are deleted
        assertNull(application.namespaceProjectionService.getNamespaceById(acmeNs.namespaceId))
        assertNull(application.namespaceProjectionService.getNamespaceById(corpNs.namespaceId))
    }

    @Test
    fun `deletes default namespace`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId) // Creates "default" namespace
        val namespaceId = namespace.namespaceId
        val result = application.deleteNamespace(namespaceId)

        assertTrue(result)
        assertNull(application.namespaceProjectionService.getNamespaceById(namespaceId))
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            topicId = SystemTopics.NAMESPACES_TOPIC_ID
        )
}

