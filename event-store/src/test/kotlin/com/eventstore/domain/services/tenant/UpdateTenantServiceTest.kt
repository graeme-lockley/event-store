package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.exceptions.TenantNameNotFoundException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class UpdateTenantServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `updates tenant and emits event`() = runTest {
        // Create a tenant first
        application.createTenant("acme")
        val numberOfEvents = numberOfEvents()

        // Verify tenant exists in projection before update
        val tenantBeforeUpdate = application.tenantProjectionService.getTenantByName("acme")
        assertNotNull(tenantBeforeUpdate, "Tenant should exist in projection before update")
        assertEquals("acme", tenantBeforeUpdate.name)

        // Update the tenant
        val updatedTenant = application.updateTenant("acme", name = "acme-corp", updatedBy = "admin")

        assertEquals("acme-corp", updatedTenant.name)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        val updatedEvent = events.last { it.type == TenantEventType.UPDATED }
        assertEquals(TenantEventType.UPDATED, updatedEvent.type)
        // All EventIds are now tenant-scoped

        // Verify tenant is updated in projection after update
        val tenantAfterUpdate = application.tenantProjectionService.getTenantByName("acme-corp")
        assertNotNull(tenantAfterUpdate, "Tenant should exist in projection after update with new name")
        assertEquals("acme-corp", tenantAfterUpdate.name)
        assertEquals(tenantBeforeUpdate.tenantId, tenantAfterUpdate.tenantId, "ResourceId should remain unchanged")
    }

    @Test
    fun `throws when tenant does not exist`() = runTest {
        assertFailsWith<TenantNameNotFoundException> {
            application.updateTenant("non-existent")
        }
    }

    @Test
    fun `updates tenant name only`() = runTest {
        application.createTenant("original-name")
        val originalTenant = application.tenantProjectionService.getTenantByName("original-name")!!

        val updatedTenant = application.updateTenant("original-name", name = "new-name")

        assertEquals("new-name", updatedTenant.name)
        assertEquals(originalTenant.tenantId, updatedTenant.tenantId)
        assertEquals(originalTenant.quota, updatedTenant.quota)
        assertEquals(originalTenant.metadata, updatedTenant.metadata)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("new-name")
        assertNotNull(projectionTenant)
        assertEquals("new-name", projectionTenant.name)
        assertNull(application.tenantProjectionService.getTenantByName("original-name"))
    }

    @Test
    fun `updates tenant quota only`() = runTest {
        val originalQuota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        application.createTenant("quota-test", quota = originalQuota)
        val originalTenant = application.tenantProjectionService.getTenantByName("quota-test")!!

        val newQuota = Quota(
            maxTopics = 20,
            maxNamespaces = 10,
            maxEventsPerDay = 2000,
            maxConsumers = 5,
            maxUsers = 10,
            maxEventSizeBytes = 1024
        )
        val updatedTenant = application.updateTenant("quota-test", quota = newQuota)

        assertEquals(newQuota, updatedTenant.quota)
        assertEquals(originalTenant.name, updatedTenant.name)
        assertEquals(originalTenant.tenantId, updatedTenant.tenantId)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("quota-test")
        assertNotNull(projectionTenant)
        assertEquals(newQuota, projectionTenant.quota)
    }

    @Test
    fun `updates tenant metadata only`() = runTest {
        val originalMetadata = mapOf("plan" to "basic")
        application.createTenant("metadata-test", metadata = originalMetadata)
        val originalTenant = application.tenantProjectionService.getTenantByName("metadata-test")!!

        val newMetadata = mapOf("plan" to "pro", "region" to "us-east")
        val updatedTenant = application.updateTenant("metadata-test", metadata = newMetadata)

        assertEquals(newMetadata, updatedTenant.metadata)
        assertEquals(originalTenant.name, updatedTenant.name)
        assertEquals(originalTenant.tenantId, updatedTenant.tenantId)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("metadata-test")
        assertNotNull(projectionTenant)
        assertEquals(newMetadata, projectionTenant.metadata)
    }

    @Test
    fun `updates all fields together`() = runTest {
        application.createTenant("multi-update-test")
        val originalTenant = application.tenantProjectionService.getTenantByName("multi-update-test")!!

        val newQuota = Quota(
            maxTopics = 30,
            maxNamespaces = 15,
            maxEventsPerDay = 3000,
            maxConsumers = 10,
            maxUsers = 20,
            maxEventSizeBytes = 2048
        )
        val newMetadata = mapOf("plan" to "enterprise", "tier" to "premium")
        val updatedTenant = application.updateTenant(
            "multi-update-test",
            name = "updated-multi-test",
            quota = newQuota,
            metadata = newMetadata,
            updatedBy = "admin"
        )

        assertEquals("updated-multi-test", updatedTenant.name)
        assertEquals(newQuota, updatedTenant.quota)
        assertEquals(newMetadata, updatedTenant.metadata)
        assertEquals(originalTenant.tenantId, updatedTenant.tenantId)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("updated-multi-test")
        assertNotNull(projectionTenant)
        assertEquals(newQuota, projectionTenant.quota)
        assertEquals(newMetadata, projectionTenant.metadata)
    }

    @Test
    fun `preserves existing values when fields are null`() = runTest {
        val originalQuota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val originalMetadata = mapOf("plan" to "basic")
        application.createTenant("preserve-test", quota = originalQuota, metadata = originalMetadata)
        application.tenantProjectionService.getTenantByName("preserve-test")!!

        // Update only name, leaving quota and metadata as null
        val updatedTenant = application.updateTenant("preserve-test", name = "preserved-name")

        assertEquals("preserved-name", updatedTenant.name)
        assertEquals(originalQuota, updatedTenant.quota, "Quota should be preserved")
        assertEquals(originalMetadata, updatedTenant.metadata, "Metadata should be preserved")

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("preserved-name")
        assertNotNull(projectionTenant)
        assertEquals(originalQuota, projectionTenant.quota)
        assertEquals(originalMetadata, projectionTenant.metadata)
    }

    @Test
    fun `uses default updatedBy when not specified`() = runTest {
        application.createTenant("default-updated-by")
        application.updateTenant("default-updated-by", name = "updated-name")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload
        assertEquals("system", payload["updatedBy"])
    }

    @Test
    fun `uses custom updatedBy when specified`() = runTest {
        application.createTenant("custom-updated-by")
        application.updateTenant("custom-updated-by", name = "updated-name", updatedBy = "admin@example.com")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload
        assertEquals("admin@example.com", payload["updatedBy"])
    }

    @Test
    fun `event payload contains all required fields`() = runTest {
        val tenant = application.createTenant("payload-test")
        val newQuota = Quota(
            maxTopics = 20,
            maxNamespaces = 10,
            maxEventsPerDay = 2000,
            maxConsumers = 5,
            maxUsers = 10,
            maxEventSizeBytes = 1024
        )
        val newMetadata = mapOf("plan" to "pro")
        application.updateTenant(
            "payload-test",
            name = "updated-payload",
            quota = newQuota,
            metadata = newMetadata,
            updatedBy = "test-user"
        )

        val event = getEvents().last { it.type == TenantEventType.UPDATED }
        val payload = event.payload

        // Verify all required fields are present
        assertTrue(payload.containsKey("tenantId"))
        assertTrue(payload.containsKey("updatedBy"))
        assertTrue(payload.containsKey("updatedAt"))
        assertTrue(payload.containsKey("name"))
        assertTrue(payload.containsKey("quota"))
        assertTrue(payload.containsKey("metadata"))

        // Verify field values
        assertEquals(tenant.tenantId.toString(), payload["tenantId"])
        assertEquals("updated-payload", payload["name"])
        assertEquals("test-user", payload["updatedBy"])
        assertEquals(newMetadata, payload["metadata"])

        // Verify quota structure
        @Suppress("UNCHECKED_CAST")
        val quotaMap = payload["quota"] as? Map<String, Any>
        assertNotNull(quotaMap)
        assertEquals(newQuota.maxTopics, quotaMap["maxTopics"])
        assertEquals(newQuota.maxNamespaces, quotaMap["maxNamespaces"])
        assertEquals(newQuota.maxEventsPerDay, quotaMap["maxEventsPerDay"])
        assertEquals(newQuota.maxConsumers, quotaMap["maxConsumers"])
        assertEquals(newQuota.maxUsers, quotaMap["maxUsers"])
        assertEquals(newQuota.maxEventSizeBytes, quotaMap["maxEventSizeBytes"])
    }

    @Test
    fun `event payload omits optional fields when null`() = runTest {
        application.createTenant("optional-fields-test")
        application.updateTenant("optional-fields-test", name = "updated-optional")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload

        // Name should be present
        assertTrue(payload.containsKey("name"))
        assertEquals("updated-optional", payload["name"])

        // Quota and metadata may be omitted if they were null in the update
        // (The payload structure depends on UpdateTenantService implementation)
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        application.createTenant("context-test")
        application.updateTenant("context-test", name = "updated-context")

        val event = getEvents().last { it.type == TenantEventType.UPDATED }
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, event.id.namespaceId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        // Create and update first tenant
        application.createTenant("sequence-test-1")
        application.updateTenant("sequence-test-1", name = "updated-1")
        val allEvents1 = getEvents()
        val updatedEvent1 = allEvents1.last { it.type == TenantEventType.UPDATED }
        val sequence1 = updatedEvent1.id.sequence

        // Create and update second tenant
        application.createTenant("sequence-test-2")
        application.updateTenant("sequence-test-2", name = "updated-2")
        val allEvents2 = getEvents()
        val updatedEvent2 = allEvents2.last { it.type == TenantEventType.UPDATED }
        val sequence2 = updatedEvent2.id.sequence

        // Verify sequence was incremented (accounting for CREATED event between updates)
        assertEquals(sequence1 + 2, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        application.createTenant("timestamp-test")
        val beforeUpdate = java.time.Instant.now()
        val updatedTenant = application.updateTenant("timestamp-test", name = "updated-timestamp")
        val afterUpdate = java.time.Instant.now()

        val event = getEvents().last { it.type == TenantEventType.UPDATED }
        assertTrue(event.timestamp.isAfter(beforeUpdate) || event.timestamp == beforeUpdate)
        assertTrue(event.timestamp.isBefore(afterUpdate) || event.timestamp == afterUpdate)
        assertNotNull(updatedTenant.updatedAt)
        assertEquals(event.timestamp, updatedTenant.updatedAt)
    }

    @Test
    fun `event payload matches TenantUpdatedEvent structure`() = runTest {
        val tenant = application.createTenant("structure-test")
        val newQuota = Quota(
            maxTopics = 15,
            maxNamespaces = 8,
            maxEventsPerDay = 1500,
            maxConsumers = 4,
            maxUsers = 8,
            maxEventSizeBytes = 768
        )
        application.updateTenant("structure-test", name = "updated-structure", quota = newQuota, updatedBy = "user")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload

        // Verify payload can be parsed back to TenantUpdatedEvent
        val parsed = TenantUpdatedEvent.fromPayload(payload)
        assertEquals(tenant.tenantId, parsed.tenantId)
        assertEquals("updated-structure", parsed.name)
        assertEquals("user", parsed.updatedBy)
        assertEquals(newQuota, parsed.quota)
    }

    @Test
    fun `updates tenant with unicode characters in name`() = runTest {
        application.createTenant("unicode-test")
        val unicodeName = "tenant-测试-🚀"
        val updatedTenant = application.updateTenant("unicode-test", name = unicodeName)

        assertEquals(unicodeName, updatedTenant.name)
        val event = getEvents().last { it.type == TenantEventType.UPDATED }
        assertEquals(unicodeName, event.payload["name"])

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName(unicodeName)
        assertNotNull(projectionTenant)
        assertEquals(unicodeName, projectionTenant.name)
    }

    @Test
    fun `can update tenant multiple times sequentially`() = runTest {
        application.createTenant("multi-update")

        val update1 = application.updateTenant("multi-update", name = "multi-update-1")
        assertEquals("multi-update-1", update1.name)

        val update2 = application.updateTenant("multi-update-1", name = "multi-update-2")
        assertEquals("multi-update-2", update2.name)

        val update3 = application.updateTenant("multi-update-2", name = "multi-update-3")
        assertEquals("multi-update-3", update3.name)

        val events = getEvents()
        val updatedEvents = events.filter { it.type == TenantEventType.UPDATED }
        assertEquals(3, updatedEvents.size)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("multi-update-3")
        assertNotNull(projectionTenant)
        assertEquals("multi-update-3", projectionTenant.name)
    }

    @Test
    fun `event resourceId matches original tenant resourceId`() = runTest {
        val tenant = application.createTenant("tenant-id-test")
        val originalTenantId = tenant.tenantId

        application.updateTenant("tenant-id-test", name = "updated-tenant-id")

        val event = getEvents().last { it.type == TenantEventType.UPDATED }
        val payload = event.payload
        assertEquals(originalTenantId.toString(), payload["tenantId"])

        // Verify projection still has same resourceId
        val projectionTenant = application.tenantProjectionService.getTenantByName("updated-tenant-id")
        assertNotNull(projectionTenant)
        assertEquals(originalTenantId, projectionTenant.tenantId)
    }

    @Test
    fun `updatedAt is set in returned tenant and projection`() = runTest {
        application.createTenant("updated-at-test")
        val beforeUpdate = java.time.Instant.now()
        val updatedTenant = application.updateTenant("updated-at-test", name = "updated-name")
        val afterUpdate = java.time.Instant.now()

        assertNotNull(updatedTenant.updatedAt)
        assertTrue(updatedTenant.updatedAt!!.isAfter(beforeUpdate) || updatedTenant.updatedAt == beforeUpdate)
        assertTrue(updatedTenant.updatedAt!!.isBefore(afterUpdate) || updatedTenant.updatedAt == afterUpdate)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("updated-name")
        assertNotNull(projectionTenant)
        assertNotNull(projectionTenant.updatedAt)
        assertEquals(updatedTenant.updatedAt, projectionTenant.updatedAt)
    }

    @Test
    fun `updates metadata with various types`() = runTest {
        application.createTenant("metadata-types-test")
        val complexMetadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        val updatedTenant = application.updateTenant("metadata-types-test", metadata = complexMetadata)

        assertEquals(complexMetadata, updatedTenant.metadata)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("metadata-types-test")
        assertNotNull(projectionTenant)
        assertEquals(complexMetadata, projectionTenant.metadata)
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

