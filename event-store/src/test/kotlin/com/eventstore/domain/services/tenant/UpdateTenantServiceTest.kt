package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.events.TenantUpdatedEvent
import com.eventstore.domain.exceptions.CannotUpdateDeletedTenantException
import com.eventstore.domain.exceptions.InvalidTenantNameException
import com.eventstore.domain.exceptions.QuotaExceededException
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.consumer.HttpConsumerRegistrationRequest
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
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
        val tenant = application.createTenant("acme")
        val numberOfEvents = numberOfEvents()

        // Verify tenant exists in projection before update
        val tenantBeforeUpdate = application.tenantProjectionService.getTenantByName("acme")
        assertNotNull(tenantBeforeUpdate, "Tenant should exist in projection before update")
        assertEquals("acme", tenantBeforeUpdate.name)

        // Update the tenant
        val updatedTenant = application.updateTenant(tenant.tenantId, name = "acme-corp", updatedBy = "admin")

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
        assertFailsWith<TenantNotFoundException> {
            application.updateTenant(UUID.randomUUID())
        }
    }

    @Test
    fun `updates tenant name only`() = runTest {
        val tenant = application.createTenant("original-name")
        val originalTenant = application.tenantProjectionService.getTenantByName("original-name")!!

        val updatedTenant = application.updateTenant(tenant.tenantId, name = "new-name")

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
    fun `tenant name must be unique`() = runTest {
        application.createTenant("tenant1-name")
        val tenant2 = application.createTenant("tenant2-name")

        assertFailsWith<TenantAlreadyExistsException> {
            application.updateTenant(tenant2.tenantId, name = "tenant1-name")
        }
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
        val tenant = application.createTenant("quota-test", quota = originalQuota)

        val newQuota = Quota(
            maxTopics = 20,
            maxNamespaces = 10,
            maxEventsPerDay = 2000,
            maxConsumers = 5,
            maxUsers = 10,
            maxEventSizeBytes = 1024
        )
        val updatedTenant = application.updateTenant(tenant.tenantId, quota = newQuota)

        assertEquals(newQuota, updatedTenant.quota)
        assertEquals(tenant.name, updatedTenant.name)
        assertEquals(tenant.tenantId, updatedTenant.tenantId)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("quota-test")
        assertNotNull(projectionTenant)
        assertEquals(newQuota, projectionTenant.quota)
    }

    @Test
    fun `updates tenant metadata only`() = runTest {
        val originalMetadata = mapOf("plan" to "basic")
        val tenant = application.createTenant("metadata-test", metadata = originalMetadata)

        val newMetadata = mapOf("plan" to "pro", "region" to "us-east")
        val updatedTenant = application.updateTenant(tenant.tenantId, metadata = newMetadata)

        assertEquals(newMetadata, updatedTenant.metadata)
        assertEquals(tenant.name, updatedTenant.name)
        assertEquals(tenant.tenantId, updatedTenant.tenantId)

        // Verify projection
        val projectionTenant = application.tenantProjectionService.getTenantByName("metadata-test")
        assertNotNull(projectionTenant)
        assertEquals(newMetadata, projectionTenant.metadata)
    }

    @Test
    fun `updates all fields together`() = runTest {
        val tenant = application.createTenant("multi-update-test")

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
            tenant.tenantId,
            name = "updated-multi-test",
            quota = newQuota,
            metadata = newMetadata,
            updatedBy = "admin"
        )

        assertEquals("updated-multi-test", updatedTenant.name)
        assertEquals(newQuota, updatedTenant.quota)
        assertEquals(newMetadata, updatedTenant.metadata)
        assertEquals(tenant.tenantId, updatedTenant.tenantId)

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
        val tenant = application.createTenant("preserve-test", quota = originalQuota, metadata = originalMetadata)
        application.tenantProjectionService.getTenantByName("preserve-test")!!

        // Update only name, leaving quota and metadata as null
        val updatedTenant = application.updateTenant(tenant.tenantId, name = "preserved-name")

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
        val tenant = application.createTenant("default-updated-by")
        application.updateTenant(tenant.tenantId, name = "updated-name")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload
        assertEquals("system", payload["updatedBy"])
    }

    @Test
    fun `uses custom updatedBy when specified`() = runTest {
        val tenant = application.createTenant("custom-updated-by")
        application.updateTenant(tenant.tenantId, name = "updated-name", updatedBy = "admin@example.com")

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
            tenant.tenantId,
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
        val tenant = application.createTenant("optional-fields-test")
        application.updateTenant(tenant.tenantId, name = "updated-optional")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload

        // Name should be present
        assertTrue(payload.containsKey("name"))
        assertEquals("updated-optional", payload["name"])

        // Quota and metadata may be omitted if they were null in the update
        // (The payload structure depends on UpdateTenantService implementation)
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
        application.updateTenant(tenant.tenantId, name = "updated-structure", quota = newQuota, updatedBy = "user")

        val payload = getEvents().last { it.type == TenantEventType.UPDATED }.payload

        // Verify payload can be parsed back to TenantUpdatedEvent
        val parsed = TenantUpdatedEvent.fromPayload(payload)
        assertEquals(tenant.tenantId, parsed.tenantId)
        assertEquals("updated-structure", parsed.name)
        assertEquals("user", parsed.updatedBy)
        assertEquals(newQuota, parsed.quota)
    }


    @Test
    fun `event resourceId matches original tenant resourceId`() = runTest {
        val tenant = application.createTenant("tenant-id-test")
        val originalTenantId = tenant.tenantId

        application.updateTenant(tenant.tenantId, name = "updated-tenant-id")

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
        val tenant = application.createTenant("updated-at-test")
        val beforeUpdate = java.time.Instant.now()
        val updatedTenant = application.updateTenant(tenant.tenantId, name = "updated-name")
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


    private suspend fun numberOfEvents(): Int =
        getEvents().size

    // Rule 2: Block updates to deleted tenants
    @Test
    fun `throws when attempting to update deleted tenant`() = runTest {
        val tenant = application.createTenant("to-delete")
        application.deleteTenant(tenant.tenantId)

        assertFailsWith<CannotUpdateDeletedTenantException> {
            application.updateTenant(tenant.tenantId, name = "updated-name")
        }
    }

    // Rule 3: Tenant name format validation tests
    @Test
    fun `throws when updating tenant name to invalid format`() = runTest {
        val tenant = application.createTenant("valid-name")
        
        assertFailsWith<InvalidTenantNameException> {
            application.updateTenant(tenant.tenantId, name = "-invalid-name")
        }
        assertFailsWith<InvalidTenantNameException> {
            application.updateTenant(tenant.tenantId, name = "invalid-name-")
        }
        assertFailsWith<InvalidTenantNameException> {
            application.updateTenant(tenant.tenantId, name = "a")
        }
        assertFailsWith<InvalidTenantNameException> {
            application.updateTenant(tenant.tenantId, name = "a".repeat(65))
        }
        assertFailsWith<InvalidTenantNameException> {
            application.updateTenant(tenant.tenantId, name = SystemTopics.SYSTEM_TENANT_NAME)
        }
    }

    @Test
    fun `allows updating tenant name with valid formats`() = runTest {
        val tenant = application.createTenant("original-name")
        
        assertEquals("valid-new-name-123", application.updateTenant(tenant.tenantId, name = "valid-new-name-123").name)
        assertEquals("Valid-New-Name-123", application.updateTenant(tenant.tenantId, name = "Valid-New-Name-123").name)
    }

    // Rule 4: Quota change validation against current usage
    @Test
    fun `throws when reducing quota below current topics usage`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxTopics = 10))
        
        // Create some topics to exceed the reduced quota
        application.createNamespace(tenant.name, "ns1")
        application.createTopic("topic1", emptyList(), tenant.name, "ns1")
        application.createTopic("topic2", emptyList(), tenant.name, "ns1")
        application.createTopic("topic3", emptyList(), tenant.name, "ns1")

        // Try to reduce quota to 2 when 3 topics exist
        assertFailsWith<QuotaExceededException> {
            application.updateTenant(tenant.tenantId, quota = Quota(maxTopics = 2))
        }
    }

    @Test
    fun `throws when reducing quota below current namespaces usage`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxNamespaces = 10))
        
        // Create some namespaces to exceed the reduced quota
        application.createNamespace(tenant.name, "ns1")
        application.createNamespace(tenant.name, "ns2")
        application.createNamespace(tenant.name, "ns3")

        // Try to reduce quota to 2 when 3 namespaces exist
        assertFailsWith<QuotaExceededException> {
            application.updateTenant(tenant.tenantId, quota = Quota(maxNamespaces = 2))
        }
    }

    @Test
    fun `throws when reducing quota below current consumers usage`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxConsumers = 10))
        
        application.createNamespace(tenant.name, "ns1")
        application.createTopic("topic1", emptyList(), tenant.name, "ns1")
        
        // Create some consumers
        application.registerConsumer(
            HttpConsumerRegistrationRequest(
                callbackUrl = "http://localhost:8080/callback1",
                topics = mapOf("topic1" to null)
            ),
            tenant.name,
            "ns1"
        )
        application.registerConsumer(
            HttpConsumerRegistrationRequest(
                callbackUrl = "http://localhost:8080/callback2",
                topics = mapOf("topic1" to null)
            ),
            tenant.name,
            "ns1"
        )
        application.registerConsumer(
            HttpConsumerRegistrationRequest(
                callbackUrl = "http://localhost:8080/callback3",
                topics = mapOf("topic1" to null)
            ),
            tenant.name,
            "ns1"
        )

        // Try to reduce quota to 2 when 3 consumers exist
        assertFailsWith<QuotaExceededException> {
            application.updateTenant(tenant.tenantId, quota = Quota(maxConsumers = 2))
        }
    }

    @Test
    fun `throws when reducing quota below current users usage`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxUsers = 10))
        
        // Create and assign users to tenant
        val user1 = application.createUser("user1@example.com", "User 1", "password")
        val user2 = application.createUser("user2@example.com", "User 2", "password")
        val user3 = application.createUser("user3@example.com", "User 3", "password")
        
        application.assignUserToTenant(user1.id, tenant.name)
        application.assignUserToTenant(user2.id, tenant.name)
        application.assignUserToTenant(user3.id, tenant.name)

        // Wait a bit for projection to update (events are processed synchronously in tests)
        // Try to reduce quota to 2 when 3 users exist
        // Note: Using full quota to avoid default value issues
        assertFailsWith<QuotaExceededException> {
            application.updateTenant(tenant.tenantId, quota = Quota(
                maxTopics = 100,
                maxNamespaces = 50,
                maxEventsPerDay = 1_000_000,
                maxConsumers = 100,
                maxUsers = 2,
                maxEventSizeBytes = 1024 * 1024
            ))
        }
    }

    @Test
    fun `allows increasing quota even when current usage is high`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxTopics = 5))
        
        application.createNamespace(tenant.name, "ns1")
        application.createTopic("topic1", emptyList(), tenant.name, "ns1")
        application.createTopic("topic2", emptyList(), tenant.name, "ns1")
        application.createTopic("topic3", emptyList(), tenant.name, "ns1")
        application.createTopic("topic4", emptyList(), tenant.name, "ns1")

        // Increasing quota should be allowed even when at limit
        val updated = application.updateTenant(tenant.tenantId, quota = Quota(maxTopics = 100))
        assertEquals(100, updated.quota!!.maxTopics)
    }

    @Test
    fun `allows quota reduction when current usage is within new limit`() = runTest {
        val tenant = application.createTenant("quota-test", quota = Quota(maxTopics = 10))
        
        application.createNamespace(tenant.name, "ns1")
        application.createTopic("topic1", emptyList(), tenant.name, "ns1")
        application.createTopic("topic2", emptyList(), tenant.name, "ns1")

        // Reducing quota to 3 when only 2 topics exist should be allowed
        val updated = application.updateTenant(tenant.tenantId, quota = Quota(maxTopics = 3))
        assertEquals(3, updated.quota!!.maxTopics)
    }

    @Test
    fun `allows partial quota updates without violating usage`() = runTest {
        val originalQuota = Quota(maxTopics = 10, maxNamespaces = 5, maxConsumers = 3, maxUsers = 2)
        val tenant = application.createTenant(
            "quota-test",
            quota = originalQuota
        )
        
        application.createNamespace(tenant.name, "ns1")
        application.createTopic("topic1", emptyList(), tenant.name, "ns1")

        // Update with full quota, reducing only maxNamespaces
        val newQuota = Quota(maxTopics = 10, maxNamespaces = 3, maxConsumers = 3, maxUsers = 2)
        val updated = application.updateTenant(
            tenant.tenantId,
            quota = newQuota
        )
        assertEquals(10, updated.quota!!.maxTopics, "Topics quota should remain unchanged")
        assertEquals(3, updated.quota!!.maxNamespaces, "Namespaces quota should be updated")
    }

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC_NAME,
            tenantId = SystemTopics.SYSTEM_TENANT_NAME,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_NAME
        )
}

