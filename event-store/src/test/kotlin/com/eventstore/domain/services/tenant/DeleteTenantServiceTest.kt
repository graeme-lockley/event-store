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
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class DeleteTenantServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `deletes tenant and emits event`() =
        runTest {
            // Create a tenant first
            val tenant = application.createTenant("acme")
            val numberOfEvents = numberOfEvents()

            // Verify tenant exists in projection before deletion
            val tenantBeforeDeletion = application.tenantProjectionService.getTenantByName("acme")
            assertNotNull(tenantBeforeDeletion, "Tenant should exist in projection before deletion")
            assertEquals("acme", tenantBeforeDeletion.name)

            // Delete the tenant
            val result = application.deleteTenant(tenant.tenantId)

            assertTrue(result)
            val events = getEvents()
            assertEquals(numberOfEvents + 1, events.size)
            val deletedEvent = events.last { it.type == TenantEventType.DELETED }
            assertEquals(TenantEventType.DELETED, deletedEvent.type)
            // All EventIds are now tenant-scoped

            // Verify tenant is no longer in projection after deletion
            val tenantAfterDeletion = application.tenantProjectionService.getTenantByName("acme")
            assertNull(tenantAfterDeletion, "Tenant should not exist in projection after deletion")
        }

    @Test
    fun `throws when tenant does not exist`() =
        runTest {
            assertFailsWith<TenantNotFoundException> {
                application.deleteTenant(UUID.randomUUID(), deletedBy = "admin")
            }
        }

    @Test
    fun `returns false when tenant already deleted`() =
        runTest {
            // Create and delete tenant
            val tenant = application.createTenant("acme")
            val firstDeleteResult = application.deleteTenant(tenant.tenantId)
            assertTrue(firstDeleteResult, "First deletion should succeed")

            // Try to delete again - should return false without error (Rule D-3: Idempotent deletion)
            val secondDeleteResult = application.deleteTenant(tenant.tenantId)
            assertFalse(secondDeleteResult, "Deleting already-deleted tenant should return false")
        }

    @Test
    fun `uses default deletedBy when not specified`() =
        runTest {
            val tenant = application.createTenant("acme")
            application.deleteTenant(tenant.tenantId)

            val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
            assertEquals("system", payload["deletedBy"])
        }

    @Test
    fun `uses custom deletedBy when specified`() =
        runTest {
            val tenant = application.createTenant("acme")
            application.deleteTenant(tenant.tenantId, deletedBy = "admin@example.com")

            val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
            assertEquals("admin@example.com", payload["deletedBy"])
        }

    @Test
    fun `includes reason when provided`() =
        runTest {
            val tenant = application.createTenant("acme")
            val reason = "Account closed by user request"
            application.deleteTenant(tenant.tenantId, reason = reason)

            val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
            assertEquals(reason, payload["reason"])
        }

    @Test
    fun `omits reason when not provided`() =
        runTest {
            val tenant = application.createTenant("acme")
            application.deleteTenant(tenant.tenantId)

            val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload
            assertTrue(!payload.containsKey("reason") || payload["reason"] == null)
        }

    @Test
    fun `event payload contains all required fields`() =
        runTest {
            val tenant = application.createTenant("payload-test")
            application.deleteTenant(tenant.tenantId, deletedBy = "test-user", reason = "test reason")

            val event = getEvents().last { it.type == TenantEventType.DELETED }
            val payload = event.payload

            // Verify all required fields are present
            assertTrue(payload.containsKey("tenantId"))
            assertTrue(payload.containsKey("deletedBy"))
            assertTrue(payload.containsKey("deletedAt"))
            assertTrue(payload.containsKey("reason"))

            // Verify field values
            assertEquals(tenant.tenantId.toString(), payload["tenantId"])
            assertEquals("test-user", payload["deletedBy"])
            assertEquals("test reason", payload["reason"])
        }

    @Test
    fun `event payload matches TenantDeletedEvent structure`() =
        runTest {
            val tenant = application.createTenant("structure-test")
            application.deleteTenant(tenant.tenantId, deletedBy = "user", reason = "test")

            val payload = getEvents().last { it.type == TenantEventType.DELETED }.payload

            // Verify payload can be parsed back to TenantDeletedEvent
            val parsed = TenantDeletedEvent.fromPayload(payload)
            assertEquals(tenant.tenantId, parsed.tenantId)
            assertEquals("user", parsed.deletedBy)
            assertEquals("test", parsed.reason)
        }

    @Test
    fun `event resourceId matches original tenant resourceId`() =
        runTest {
            val tenant = application.createTenant("tenant-id-test")
            val originalTenantId = tenant.tenantId

            application.deleteTenant(originalTenantId)

            val event = getEvents().last { it.type == TenantEventType.DELETED }
            val payload = event.payload
            assertEquals(originalTenantId.toString(), payload["tenantId"])
        }

    private suspend fun numberOfEvents(): Int = getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            topicId = SystemTopics.TENANTS_TOPIC_ID,
        )
}
