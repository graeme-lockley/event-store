package com.eventstore.domain.services.tenant

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class GetTenantServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    /**
     * Gets the initial tenant count, accounting for the bootstrap $system tenant.
     * Bootstrap creates a tenant named "$system", so the initial count is 1.
     */
    private suspend fun getInitialTenantCount(): Int {
        val tenants = application.listTenants()
        return tenants.size
    }

    @Test
    fun `gets tenant that exists`() = runTest {
        val createdTenant = application.createTenant("acme")
        val retrievedTenant = application.getTenant("acme")

        assertNotNull(retrievedTenant, "Tenant should be retrieved")
        assertEquals(createdTenant.name, retrievedTenant.name)
        assertEquals(createdTenant.tenantId, retrievedTenant.tenantId)
        assertEquals(createdTenant.quota, retrievedTenant.quota)
        assertEquals(createdTenant.metadata, retrievedTenant.metadata)
        assertEquals(createdTenant.createdAt, retrievedTenant.createdAt)
    }

    @Test
    fun `returns null when tenant does not exist`() = runTest {
        val retrievedTenant = application.getTenant("non-existent")

        assertNull(retrievedTenant, "Non-existent tenant should return null")
    }

    @Test
    fun `returns null when tenant is deleted`() = runTest {
        val tenant = application.createTenant("deleted-tenant")
        application.deleteTenant(tenant.tenantId)

        val retrievedTenant = application.getTenant("deleted-tenant")

        assertNull(retrievedTenant, "Deleted tenant should return null")
    }

    @Test
    fun `lists bootstrap tenant when no custom tenants exist`() = runTest {
        val tenants = application.listTenants()

        // Bootstrap creates the $system tenant, so we should have at least 1 tenant
        assertTrue(tenants.isNotEmpty(), "Should include bootstrap \$system tenant")
        val systemTenant = tenants.find { it.name == SystemTopics.SYSTEM_TENANT_NAME }
        assertNotNull(systemTenant, "Bootstrap should create \$system tenant")
    }

    @Test
    fun `gets bootstrap system tenant`() = runTest {
        val systemTenant = application.getTenant(SystemTopics.SYSTEM_TENANT_NAME)

        assertNotNull(systemTenant, "Should be able to retrieve bootstrap \$system tenant")
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, systemTenant.name)
        assertTrue(systemTenant.isActive)
    }

    @Test
    fun `lists single custom tenant`() = runTest {
        val initialCount = getInitialTenantCount()
        val createdTenant = application.createTenant("single-tenant")
        val tenants = application.listTenants()

        assertEquals(initialCount + 1, tenants.size, "Should return bootstrap tenant plus one custom tenant")
        val foundTenant = tenants.find { it.name == "single-tenant" }
        assertNotNull(foundTenant, "Should find the created tenant")
        assertEquals(createdTenant.name, foundTenant.name)
        assertEquals(createdTenant.tenantId, foundTenant.tenantId)
    }

    @Test
    fun `lists multiple tenants`() = runTest {
        val initialCount = getInitialTenantCount()

        application.createTenant("tenant-1")
        application.createTenant("tenant-2")
        application.createTenant("tenant-3")

        val tenants = application.listTenants()

        assertEquals(initialCount + 3, tenants.size, "Should return bootstrap tenant plus three custom tenants")
        val tenantNames = tenants.map { it.name }.toSet()
        assertTrue(tenantNames.contains("tenant-1"))
        assertTrue(tenantNames.contains("tenant-2"))
        assertTrue(tenantNames.contains("tenant-3"))
    }

    @Test
    fun `list tenants excludes deleted tenants`() = runTest {
        val initialCount = getInitialTenantCount()
        application.createTenant("active-1")
        application.createTenant("active-2")
        val tenant3 = application.createTenant("to-delete")
        application.deleteTenant(tenant3.tenantId)

        val tenants = application.listTenants()

        assertEquals(initialCount + 2, tenants.size, "Should return bootstrap tenant plus only active custom tenants")
        val tenantNames = tenants.map { it.name }.toSet()
        assertTrue(tenantNames.contains("active-1"))
        assertTrue(tenantNames.contains("active-2"))
        assertFalse(tenantNames.contains("to-delete"))
    }

    @Test
    fun `gets tenant with quota`() = runTest {
        val quota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        application.createTenant("quota-tenant", quota = quota)
        val retrievedTenant = application.getTenant("quota-tenant")

        assertNotNull(retrievedTenant)
        assertNotNull(retrievedTenant.quota)
        assertEquals(quota, retrievedTenant.quota)
        assertEquals(quota.maxTopics, retrievedTenant.quota!!.maxTopics)
        assertEquals(quota.maxNamespaces, retrievedTenant.quota!!.maxNamespaces)
        assertEquals(quota.maxEventsPerDay, retrievedTenant.quota!!.maxEventsPerDay)
        assertEquals(quota.maxConsumers, retrievedTenant.quota!!.maxConsumers)
        assertEquals(quota.maxUsers, retrievedTenant.quota!!.maxUsers)
        assertEquals(quota.maxEventSizeBytes, retrievedTenant.quota!!.maxEventSizeBytes)
    }

    @Test
    fun `gets tenant with metadata`() = runTest {
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        application.createTenant("metadata-tenant", metadata = metadata)
        val retrievedTenant = application.getTenant("metadata-tenant")

        assertNotNull(retrievedTenant)
        assertEquals(metadata, retrievedTenant.metadata)
        assertEquals("pro", retrievedTenant.metadata["plan"])
        assertEquals("us-east", retrievedTenant.metadata["region"])
    }

    @Test
    fun `gets tenant with complex metadata`() = runTest {
        val metadata = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "nested" to mapOf("key" to "value"),
            "list" to listOf(1, 2, 3)
        )
        application.createTenant("complex-metadata-tenant", metadata = metadata)
        val retrievedTenant = application.getTenant("complex-metadata-tenant")

        assertNotNull(retrievedTenant)
        assertEquals(metadata, retrievedTenant.metadata)
    }

    @Test
    fun `gets updated tenant with new values`() = runTest {
        val originalQuota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val tenant = application.createTenant("update-test", quota = originalQuota, metadata = mapOf("plan" to "basic"))

        val newQuota = Quota(
            maxTopics = 20,
            maxNamespaces = 10,
            maxEventsPerDay = 2000,
            maxConsumers = 5,
            maxUsers = 10,
            maxEventSizeBytes = 1024
        )
        val newMetadata = mapOf("plan" to "pro", "tier" to "premium")
        application.updateTenant(tenant.tenantId, name = "updated-test", quota = newQuota, metadata = newMetadata)

        val retrievedTenant = application.getTenant("updated-test")

        assertNotNull(retrievedTenant)
        assertEquals("updated-test", retrievedTenant.name)
        assertEquals(newQuota, retrievedTenant.quota)
        assertEquals(newMetadata, retrievedTenant.metadata)
        assertNotNull(retrievedTenant.updatedAt)
    }

    @Test
    fun `gets tenant with unicode characters in name`() = runTest {
        val unicodeName = "tenant-测试-🚀"
        val createdTenant = application.createTenant(unicodeName)
        val retrievedTenant = application.getTenant(unicodeName)

        assertNotNull(retrievedTenant)
        assertEquals(unicodeName, retrievedTenant.name)
        assertEquals(createdTenant.tenantId, retrievedTenant.tenantId)
    }

    @Test
    fun `list tenants includes tenants with unicode names`() = runTest {
        val initialCount = getInitialTenantCount()
        val unicodeName = "tenant-测试-🚀"
        application.createTenant(unicodeName)
        application.createTenant("regular-tenant")

        val tenants = application.listTenants()

        assertEquals(initialCount + 2, tenants.size)
        val tenantNames = tenants.map { it.name }.toSet()
        assertTrue(tenantNames.contains(unicodeName))
        assertTrue(tenantNames.contains("regular-tenant"))
    }

    @Test
    fun `gets tenant preserves resourceId after update`() = runTest {
        val createdTenant = application.createTenant("resource-id-test")
        val originalTenantId = createdTenant.tenantId

        application.updateTenant(originalTenantId, name = "renamed-test")
        val retrievedTenant = application.getTenant("renamed-test")

        assertNotNull(retrievedTenant)
        assertEquals(originalTenantId, retrievedTenant.tenantId, "TenantId should remain unchanged after rename")
    }

    @Test
    fun `list tenants returns tenants in consistent order`() = runTest {
        application.createTenant("tenant-a")
        application.createTenant("tenant-b")
        application.createTenant("tenant-c")

        val tenants1 = application.listTenants()
        val tenants2 = application.listTenants()

        assertEquals(tenants1.size, tenants2.size)
        // Both lists should contain the same tenants (order may vary, but content should be same)
        val names1 = tenants1.map { it.name }.toSet()
        val names2 = tenants2.map { it.name }.toSet()
        assertEquals(names1, names2)
    }

    @Test
    fun `gets tenant returns correct createdAt timestamp`() = runTest {
        val beforeCreation = java.time.Instant.now()
        val createdTenant = application.createTenant("timestamp-test")
        val afterCreation = java.time.Instant.now()

        val retrievedTenant = application.getTenant("timestamp-test")

        assertNotNull(retrievedTenant)
        assertTrue(retrievedTenant.createdAt.isAfter(beforeCreation) || retrievedTenant.createdAt == beforeCreation)
        assertTrue(retrievedTenant.createdAt.isBefore(afterCreation) || retrievedTenant.createdAt == afterCreation)
        assertEquals(createdTenant.createdAt, retrievedTenant.createdAt)
    }

    @Test
    fun `gets tenant returns correct updatedAt timestamp after update`() = runTest {
        val tenant= application.createTenant("updated-at-test")
        val beforeUpdate = java.time.Instant.now()
        application.updateTenant(tenant.tenantId, name = "updated-name")
        val afterUpdate = java.time.Instant.now()

        val retrievedTenant = application.getTenant("updated-name")

        assertNotNull(retrievedTenant)
        assertNotNull(retrievedTenant.updatedAt)
        assertTrue(retrievedTenant.updatedAt!!.isAfter(beforeUpdate) || retrievedTenant.updatedAt == beforeUpdate)
        assertTrue(retrievedTenant.updatedAt!!.isBefore(afterUpdate) || retrievedTenant.updatedAt == afterUpdate)
    }

    @Test
    fun `gets tenant returns null for updated name when original name is used`() = runTest {
        val tenant = application.createTenant("original-name")
        application.updateTenant(tenant.tenantId, name = "new-name")

        val retrievedByOldName = application.getTenant("original-name")
        val retrievedByNewName = application.getTenant("new-name")

        assertNull(retrievedByOldName, "Should return null when querying by old name")
        assertNotNull(retrievedByNewName, "Should return tenant when querying by new name")
        assertEquals("new-name", retrievedByNewName.name)
    }

    @Test
    fun `list tenants excludes multiple deleted tenants`() = runTest {
        val initialCount = getInitialTenantCount()
        application.createTenant("keep-1")
        val tenant2 = application.createTenant("delete-1")
        application.createTenant("keep-2")
        val tenant4 = application.createTenant("delete-2")
        application.createTenant("keep-3")

        application.deleteTenant(tenant2.tenantId)
        application.deleteTenant(tenant4.tenantId)

        val tenants = application.listTenants()

        assertEquals(initialCount + 3, tenants.size, "Should return bootstrap tenant plus only active custom tenants")
        val tenantNames = tenants.map { it.name }.toSet()
        assertTrue(tenantNames.contains("keep-1"))
        assertTrue(tenantNames.contains("keep-2"))
        assertTrue(tenantNames.contains("keep-3"))
        assertFalse(tenantNames.contains("delete-1"))
        assertFalse(tenantNames.contains("delete-2"))
    }

    @Test
    fun `gets tenant with empty metadata`() = runTest {
        application.createTenant("empty-metadata-tenant", metadata = emptyMap())
        val retrievedTenant = application.getTenant("empty-metadata-tenant")

        assertNotNull(retrievedTenant)
        assertTrue(retrievedTenant.metadata.isEmpty())
    }

    @Test
    fun `gets tenant with null quota`() = runTest {
        application.createTenant("no-quota-tenant", quota = null)
        val retrievedTenant = application.getTenant("no-quota-tenant")

        assertNotNull(retrievedTenant)
        assertNull(retrievedTenant.quota)
    }

    @Test
    fun `list tenants returns all tenant fields correctly`() = runTest {
        val quota = Quota(
            maxTopics = 15,
            maxNamespaces = 8,
            maxEventsPerDay = 1500,
            maxConsumers = 4,
            maxUsers = 8,
            maxEventSizeBytes = 768
        )
        val metadata = mapOf("environment" to "test")
        val createdTenant = application.createTenant("complete-tenant", quota = quota, metadata = metadata)

        val tenants = application.listTenants()
        val tenant = tenants.find { it.name == "complete-tenant" }

        assertNotNull(tenant)
        assertEquals(createdTenant.tenantId, tenant.tenantId)
        assertEquals(createdTenant.name, tenant.name)
        assertEquals(createdTenant.quota, tenant.quota)
        assertEquals(createdTenant.metadata, tenant.metadata)
        assertEquals(createdTenant.createdAt, tenant.createdAt)
        assertNull(tenant.updatedAt)
        assertNull(tenant.deletedAt)
        assertTrue(tenant.isActive)
    }

    @Test
    fun `gets tenant is case sensitive`() = runTest {
        application.createTenant("CaseSensitive")

        val retrievedExact = application.getTenant("CaseSensitive")
        val retrievedLowercase = application.getTenant("casesensitive")
        val retrievedUppercase = application.getTenant("CASESENSITIVE")

        assertNotNull(retrievedExact)
        assertNull(retrievedLowercase, "Tenant name lookup should be case sensitive")
        assertNull(retrievedUppercase, "Tenant name lookup should be case sensitive")
    }
}

