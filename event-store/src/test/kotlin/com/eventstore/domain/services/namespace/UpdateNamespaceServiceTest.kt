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
import java.util.*

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
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val numberOfEvents = numberOfEvents()

        // Verify namespace exists in projection before update
        val namespaceBeforeUpdate = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(namespaceBeforeUpdate, "Namespace should exist in projection before update")
        assertEquals("billing", namespaceBeforeUpdate.name)

        // Update the namespace
        val updatedNamespace = application.updateNamespace(namespaceId, name = "billing-v2", updatedBy = "admin")

        assertEquals("billing-v2", updatedNamespace.name)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val updatedEvent = events.last { it.type == NamespaceEventType.UPDATED }
        assertEquals(NamespaceEventType.UPDATED, updatedEvent.type)
        // All EventIds are now tenant-scoped

        // Verify namespace is updated in projection after update
        val namespaceAfterUpdate = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(namespaceAfterUpdate, "Namespace should exist in projection after update with new name")
        assertEquals("billing-v2", namespaceAfterUpdate.name)
        assertEquals(
            namespaceBeforeUpdate.namespaceId,
            namespaceAfterUpdate.namespaceId,
            "NamespaceId should remain unchanged"
        )
    }

    @Test
    fun `throws when namespace does not exist`() = runTest {
        val nonExistentNamespaceId = UUID.randomUUID()

        assertFailsWith<NamespaceNotFoundException> {
            application.updateNamespace(nonExistentNamespaceId)
        }
    }

    @Test
    fun `updates namespace name only`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "original-name")
        val namespaceId = namespace.namespaceId
        val originalNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)!!

        val updatedNamespace = application.updateNamespace(namespaceId, name = "new-name")

        assertEquals("new-name", updatedNamespace.name)
        assertEquals(originalNamespace.namespaceId, updatedNamespace.namespaceId)
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
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val originalNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)!!

        val updatedNamespace = application.updateNamespace(namespaceId, description = "Updated billing namespace")

        assertEquals("Updated billing namespace", updatedNamespace.description)
        assertEquals(originalNamespace.name, updatedNamespace.name)
        assertEquals(originalNamespace.namespaceId, updatedNamespace.namespaceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace)
        assertEquals("Updated billing namespace", projectionNamespace.description)
    }

    @Test
    fun `updates namespace metadata only`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val originalMetadata = mapOf("plan" to "basic")
        val namespace = application.createNamespace(
            tenantId = tenantId,
            namespaceName = "billing",
            metadata = originalMetadata
        )
        val namespaceId = namespace.namespaceId
        val originalNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)!!

        val newMetadata = mapOf("plan" to "pro", "region" to "us-east")
        val updatedNamespace = application.updateNamespace(namespaceId, metadata = newMetadata)

        assertEquals(newMetadata, updatedNamespace.metadata)
        assertEquals(originalNamespace.name, updatedNamespace.name)
        assertEquals(originalNamespace.namespaceId, updatedNamespace.namespaceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace)
        assertEquals(newMetadata, projectionNamespace.metadata)
    }

    @Test
    fun `updates all fields together`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val originalNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)!!

        val newMetadata = mapOf("plan" to "enterprise", "tier" to "premium")
        val updatedNamespace = application.updateNamespace(
            namespaceId = namespaceId,
            name = "billing-v2",
            description = "Enterprise billing namespace",
            metadata = newMetadata,
            updatedBy = "admin"
        )

        assertEquals("billing-v2", updatedNamespace.name)
        assertEquals("Enterprise billing namespace", updatedNamespace.description)
        assertEquals(newMetadata, updatedNamespace.metadata)
        assertEquals(originalNamespace.namespaceId, updatedNamespace.namespaceId)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing-v2")
        assertNotNull(projectionNamespace)
        assertEquals("Enterprise billing namespace", projectionNamespace.description)
        assertEquals(newMetadata, projectionNamespace.metadata)
    }

    @Test
    fun `preserves existing values when fields are null`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val originalMetadata = mapOf("plan" to "basic")
        val namespace = application.createNamespace(
            tenantId = tenantId,
            namespaceName = "billing",
            description = "Original description",
            metadata = originalMetadata
        )
        val namespaceId = namespace.namespaceId
        application.namespaceProjectionService.getNamespaceById(namespaceId)!!

        // Update only name, leaving description and metadata as null
        val updatedNamespace = application.updateNamespace(namespaceId, name = "renamed-billing")

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
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.updateNamespace(namespaceId, name = "updated-name")

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload
        assertEquals("system", payload["updatedBy"])
    }

    @Test
    fun `uses custom updatedBy when specified`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.updateNamespace(namespaceId, name = "updated-name", updatedBy = "admin@example.com")

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload
        assertEquals("admin@example.com", payload["updatedBy"])
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val newMetadata = mapOf("plan" to "pro")
        application.updateNamespace(
            namespaceId = namespaceId,
            name = "updated-billing",
            description = "Updated",
            metadata = newMetadata,
            updatedBy = "test-user"
        )

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("namespaceId"))
        assertTrue(payload.containsKey("updatedBy"))
        assertTrue(payload.containsKey("updatedAt"))
        assertTrue(payload.containsKey("name"))
        assertTrue(payload.containsKey("description"))
        assertTrue(payload.containsKey("metadata"))

        // Verify field values
        assertEquals(namespaceId.toString(), payload["namespaceId"])
        assertEquals("updated-billing", payload["name"])
        assertEquals("Updated", payload["description"])
        assertEquals("test-user", payload["updatedBy"])
        assertEquals(newMetadata, payload["metadata"])
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.updateNamespace(namespaceId, name = "updated-billing")

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        assertEquals(SystemTopics.NAMESPACES_TOPIC_ID, event.id.topicId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId

        // Create and update first namespace
        val ns1 = application.createNamespace(tenantId, "namespace-1")
        application.updateNamespace(ns1.namespaceId, name = "updated-1")
        val allEvents1 = getEvents()
        val updatedEvent1 = allEvents1.last { it.type == NamespaceEventType.UPDATED }
        val sequence1 = updatedEvent1.id.sequence

        // Create and update second namespace
        val ns2 = application.createNamespace(tenantId, "namespace-2")
        application.updateNamespace(ns2.namespaceId, name = "updated-2")
        val allEvents2 = getEvents()
        val updatedEvent2 = allEvents2.last { it.type == NamespaceEventType.UPDATED }
        val sequence2 = updatedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between updates)
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val beforeUpdate = java.time.Instant.now()
        val updatedNamespace = application.updateNamespace(namespaceId, name = "updated-billing")
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
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        application.updateNamespace(
            namespaceId = namespaceId,
            name = "updated-billing",
            description = "Updated",
            updatedBy = "user"
        )

        val payload = getEvents().last { it.type == NamespaceEventType.UPDATED }.payload

        // Verify payload can be parsed back to NamespaceUpdatedEvent
        val parsed = NamespaceUpdatedEvent.fromPayload(payload)
        assertEquals(namespaceId, parsed.namespaceId)
        assertEquals("updated-billing", parsed.name)
        assertEquals("Updated", parsed.description)
        assertEquals("user", parsed.updatedBy)
    }

    @Test
    fun `updates namespace with unicode characters in name`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "unicode-test")
        val namespaceId = namespace.namespaceId
        val unicodeName = "namespace-测试-🚀"
        val updatedNamespace = application.updateNamespace(namespaceId, name = unicodeName)

        assertEquals(unicodeName, updatedNamespace.name)
        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        assertEquals(unicodeName, event.payload["name"])

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(projectionNamespace)
        assertEquals(unicodeName, projectionNamespace.name)
    }

    @Test
    fun `can update namespace multiple times sequentially`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "multi-update")
        val namespaceId = namespace.namespaceId

        val update1 = application.updateNamespace(namespaceId, name = "multi-update-1")
        assertEquals("multi-update-1", update1.name)

        val update2 = application.updateNamespace(namespaceId, name = "multi-update-2")
        assertEquals("multi-update-2", update2.name)

        val update3 = application.updateNamespace(namespaceId, name = "multi-update-3")
        assertEquals("multi-update-3", update3.name)

        val events = getEvents()
        val updatedEvents = events.filter { it.type == NamespaceEventType.UPDATED }
        assertEquals(3, updatedEvents.size)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(projectionNamespace)
        assertEquals("multi-update-3", projectionNamespace.name)
    }

    @Test
    fun `event namespaceId matches original namespace namespaceId`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val originalNamespaceId = namespace.namespaceId

        application.updateNamespace(originalNamespaceId, name = "updated-billing")

        val event = getEvents().last { it.type == NamespaceEventType.UPDATED }
        val payload = event.payload
        assertEquals(originalNamespaceId.toString(), payload["namespaceId"])

        // Verify projection still has same namespaceId
        val projectionNamespace = application.namespaceProjectionService.getNamespaceById(originalNamespaceId)
        assertNotNull(projectionNamespace)
        assertEquals(originalNamespaceId, projectionNamespace.namespaceId)
    }

    @Test
    fun `updatedAt is set in returned namespace and projection`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val beforeUpdate = java.time.Instant.now()
        val updatedNamespace = application.updateNamespace(namespaceId, name = "updated-name")
        val afterUpdate = java.time.Instant.now()

        assertNotNull(updatedNamespace.updatedAt)
        assertTrue(updatedNamespace.updatedAt!!.isAfter(beforeUpdate) || updatedNamespace.updatedAt == beforeUpdate)
        assertTrue(updatedNamespace.updatedAt!!.isBefore(afterUpdate) || updatedNamespace.updatedAt == afterUpdate)

        // Verify projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceById(namespaceId)
        assertNotNull(projectionNamespace)
        assertNotNull(projectionNamespace.updatedAt)
        assertEquals(updatedNamespace.updatedAt, projectionNamespace.updatedAt)
    }

    @Test
    fun `updates metadata with various types`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")
        val namespaceId = namespace.namespaceId
        val complexMetadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        val updatedNamespace = application.updateNamespace(namespaceId, metadata = complexMetadata)

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
            topicId = SystemTopics.NAMESPACES_TOPIC_ID
        )
}

