package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.events.NamespaceUpdatedEvent
import com.eventstore.domain.exceptions.NamespaceNotFoundException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class UpdateNamespaceServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `updates namespace and emits event`() = runTest {
        // Create tenant and namespace first
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val numberOfEvents = numberOfEvents()

        // Verify namespace exists in projection before update
        val namespaceBeforeUpdate = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(namespaceBeforeUpdate, "Namespace should exist in projection before update")
        assertEquals("billing", namespaceBeforeUpdate.name)

        // Update the namespace
        val updatedNamespace = application.updateNamespace("acme", "billing", name = "billing-v2", updatedBy = "admin")

        assertEquals("billing-v2", updatedNamespace.name)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val updatedEvent = events.last { it.type == NamespaceEventType.UPDATED }
        assertEquals(NamespaceEventType.UPDATED, updatedEvent.type)
        // All EventIds are now tenant-scoped

        // Verify namespace is updated in projection after update
        val namespaceAfterUpdate = application.namespaceProjectionService.getNamespaceByName("acme", "billing-v2")
        assertNotNull(namespaceAfterUpdate, "Namespace should exist in projection after update with new name")
        assertEquals("billing-v2", namespaceAfterUpdate.name)
        assertEquals(
            namespaceBeforeUpdate.resourceId,
            namespaceAfterUpdate.resourceId,
            "ResourceId should remain unchanged"
        )
    }

    @Test
    fun `throws when namespace does not exist`() = runTest {
        application.createTenant("acme")

        assertFailsWith<NamespaceNotFoundException> {
            application.updateNamespace("acme", "non-existent")
        }
    }

    @Test
    fun `throws when tenant does not exist`() = runTest {
        assertFailsWith<NamespaceNotFoundException> {
            application.updateNamespace("unknown", "billing")
        }
    }

    @Test
    fun `updates namespace name only`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "original-name")
        val originalNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "original-name")!!

        val updatedNamespace = application.updateNamespace("acme", "original-name", name = "new-name")

        assertEquals("new-name", updatedNamespace.name)
        assertEquals(originalNamespace.resourceId, updatedNamespace.resourceId)
        assertEquals(originalNamespace.description, updatedNamespace.description)
        assertEquals(originalNamespace.metadata, updatedNamespace.metadata)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "new-name")
        assertNotNull(projectionNamespace)
        assertEquals("new-name", projectionNamespace.name)
        assertNull(application.namespaceProjectionService.getNamespaceByName("acme", "original-name"))
    }

    @Test
    fun `updates namespace description only`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val originalNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")!!

        val updatedNamespace = application.updateNamespace("acme", "billing", description = "Updated billing namespace")

        assertEquals("Updated billing namespace", updatedNamespace.description)
        assertEquals(originalNamespace.name, updatedNamespace.name)
        assertEquals(originalNamespace.resourceId, updatedNamespace.resourceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace)
        assertEquals("Updated billing namespace", projectionNamespace.description)
    }

    @Test
    fun `updates namespace metadata only`() = runTest {
        application.createTenant("acme")
        val originalMetadata = mapOf("plan" to "basic")
        application.createNamespace(
            tenantName = "acme",
            namespaceName = "billing",
            metadata = originalMetadata
        )
        val originalNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")!!

        val newMetadata = mapOf("plan" to "pro", "region" to "us-east")
        val updatedNamespace = application.updateNamespace("acme", "billing", metadata = newMetadata)

        assertEquals(newMetadata, updatedNamespace.metadata)
        assertEquals(originalNamespace.name, updatedNamespace.name)
        assertEquals(originalNamespace.resourceId, updatedNamespace.resourceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace)
        assertEquals(newMetadata, projectionNamespace.metadata)
    }

    @Test
    fun `updates all fields together`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val originalNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")!!

        val newMetadata = mapOf("plan" to "enterprise", "tier" to "premium")
        val updatedNamespace = application.updateNamespace(
            "acme", "billing",
            name = "billing-v2",
            description = "Enterprise billing namespace",
            metadata = newMetadata,
            updatedBy = "admin"
        )

        assertEquals("billing-v2", updatedNamespace.name)
        assertEquals("Enterprise billing namespace", updatedNamespace.description)
        assertEquals(newMetadata, updatedNamespace.metadata)
        assertEquals(originalNamespace.resourceId, updatedNamespace.resourceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing-v2")
        assertNotNull(projectionNamespace)
        assertEquals("Enterprise billing namespace", projectionNamespace.description)
        assertEquals(newMetadata, projectionNamespace.metadata)
    }

    @Test
    fun `preserves existing values when fields are null`() = runTest {
        application.createTenant("acme")
        val originalMetadata = mapOf("plan" to "basic")
        application.createNamespace(
            tenantName = "acme",
            namespaceName = "billing",
            description = "Original description",
            metadata = originalMetadata
        )
        application.namespaceProjectionService.getNamespaceByName("acme", "billing")!!

        // Update only name, leaving description and metadata as null
        val updatedNamespace = application.updateNamespace("acme", "billing", name = "renamed-billing")

        assertEquals("renamed-billing", updatedNamespace.name)
        assertEquals("Original description", updatedNamespace.description, "Description should be preserved")
        assertEquals(originalMetadata, updatedNamespace.metadata, "Metadata should be preserved")

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "renamed-billing")
        assertNotNull(projectionNamespace)
        assertEquals("Original description", projectionNamespace.description)
        assertEquals(originalMetadata, projectionNamespace.metadata)
    }

    @Test
    fun `uses default updatedBy when not specified`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.updateNamespace("acme", "billing", name = "updated-name")

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload
        assertEquals("system", payload["updatedBy"])
    }

    @Test
    fun `uses custom updatedBy when specified`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.updateNamespace("acme", "billing", name = "updated-name", updatedBy = "admin@example.com")

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload
        assertEquals("admin@example.com", payload["updatedBy"])
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        val newMetadata = mapOf("plan" to "pro")
        application.updateNamespace(
            "acme",
            "billing",
            name = "updated-billing",
            description = "Updated",
            metadata = newMetadata,
            updatedBy = "test-user"
        )

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("resourceId"))
        assertTrue(payload.containsKey("tenantResourceId"))
        assertTrue(payload.containsKey("updatedBy"))
        assertTrue(payload.containsKey("updatedAt"))
        assertTrue(payload.containsKey("name"))
        assertTrue(payload.containsKey("description"))
        assertTrue(payload.containsKey("metadata"))

        // Verify field values
        assertEquals(namespace.resourceId.toString(), payload["resourceId"])
        assertEquals(tenant.resourceId.toString(), payload["tenantResourceId"])
        assertEquals("updated-billing", payload["name"])
        assertEquals("Updated", payload["description"])
        assertEquals("test-user", payload["updatedBy"])
        assertEquals(newMetadata, payload["metadata"])
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.updateNamespace("acme", "billing", name = "updated-billing")

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, event.id.namespaceId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        application.createTenant("acme")

        // Create and update first namespace
        application.createNamespace("acme", "namespace-1")
        application.updateNamespace("acme", "namespace-1", name = "updated-1")
        val allEvents1 = getEvents()
        val updatedEvent1 = allEvents1.last { it.type == NamespaceEventType.UPDATED }
        val sequence1 = updatedEvent1.id.sequence

        // Create and update second namespace
        application.createNamespace("acme", "namespace-2")
        application.updateNamespace("acme", "namespace-2", name = "updated-2")
        val allEvents2 = getEvents()
        val updatedEvent2 = allEvents2.last { it.type == NamespaceEventType.UPDATED }
        val sequence2 = updatedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between updates)
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val beforeUpdate = java.time.Instant.now()
        val updatedNamespace = application.updateNamespace("acme", "billing", name = "updated-billing")
        val afterUpdate = java.time.Instant.now()

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        assertTrue(event.timestamp.isAfter(beforeUpdate) || event.timestamp == beforeUpdate)
        assertTrue(event.timestamp.isBefore(afterUpdate) || event.timestamp == afterUpdate)
        assertNotNull(updatedNamespace.updatedAt)
        assertEquals(event.timestamp, updatedNamespace.updatedAt)
    }

    @Test
    fun `event payload matches NamespaceUpdatedEvent structure`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        application.updateNamespace(
            "acme",
            "billing",
            name = "updated-billing",
            description = "Updated",
            updatedBy = "user"
        )

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload

        // Verify payload can be parsed back to NamespaceUpdatedEvent
        val parsed = NamespaceUpdatedEvent.fromPayload(payload)
        assertEquals(namespace.resourceId, parsed.resourceId)
        assertEquals(tenant.resourceId, parsed.tenantResourceId)
        assertEquals("updated-billing", parsed.name)
        assertEquals("Updated", parsed.description)
        assertEquals("user", parsed.updatedBy)
    }

    @Test
    fun `updates namespace with unicode characters in name`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "unicode-test")
        val unicodeName = "namespace-测试-🚀"
        val updatedNamespace = application.updateNamespace("acme", "unicode-test", name = unicodeName)

        assertEquals(unicodeName, updatedNamespace.name)
        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        assertEquals(unicodeName, event.payload["name"])

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", unicodeName)
        assertNotNull(projectionNamespace)
        assertEquals(unicodeName, projectionNamespace.name)
    }

    @Test
    fun `can update namespace multiple times sequentially`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "multi-update")

        val update1 = application.updateNamespace("acme", "multi-update", name = "multi-update-1")
        assertEquals("multi-update-1", update1.name)

        val update2 = application.updateNamespace("acme", "multi-update-1", name = "multi-update-2")
        assertEquals("multi-update-2", update2.name)

        val update3 = application.updateNamespace("acme", "multi-update-2", name = "multi-update-3")
        assertEquals("multi-update-3", update3.name)

        val events = getEvents()
        val updatedEvents = events.filter { it.type == NamespaceEventType.UPDATED }
        assertEquals(3, updatedEvents.size)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "multi-update-3")
        assertNotNull(projectionNamespace)
        assertEquals("multi-update-3", projectionNamespace.name)
    }

    @Test
    fun `event resourceId matches original namespace resourceId`() = runTest {
        val tenant = application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")
        val originalResourceId = namespace.resourceId

        application.updateNamespace("acme", "billing", name = "updated-billing")

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        val payload = event.payload
        assertEquals(originalResourceId.toString(), payload["resourceId"])
        assertEquals(tenant.resourceId.toString(), payload["tenantResourceId"])

        // Verify projection still has same resourceId
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "updated-billing")
        assertNotNull(projectionNamespace)
        assertEquals(originalResourceId, projectionNamespace.resourceId)
    }

    @Test
    fun `updatedAt is set in returned namespace and projection`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val beforeUpdate = java.time.Instant.now()
        val updatedNamespace = application.updateNamespace("acme", "billing", name = "updated-name")
        val afterUpdate = java.time.Instant.now()

        assertNotNull(updatedNamespace.updatedAt)
        assertTrue(updatedNamespace.updatedAt!!.isAfter(beforeUpdate) || updatedNamespace.updatedAt == beforeUpdate)
        assertTrue(updatedNamespace.updatedAt!!.isBefore(afterUpdate) || updatedNamespace.updatedAt == afterUpdate)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "updated-name")
        assertNotNull(projectionNamespace)
        assertNotNull(projectionNamespace.updatedAt)
        assertEquals(updatedNamespace.updatedAt, projectionNamespace.updatedAt)
    }

    @Test
    fun `updates metadata with various types`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val complexMetadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        val updatedNamespace = application.updateNamespace("acme", "billing", metadata = complexMetadata)

        assertEquals(complexMetadata, updatedNamespace.metadata)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace)
        assertEquals(complexMetadata, projectionNamespace.metadata)
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

