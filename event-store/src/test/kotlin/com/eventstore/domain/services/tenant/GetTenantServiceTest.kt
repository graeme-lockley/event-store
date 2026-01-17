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
    fun `gets tenant with quota and metadata`() = runTest {
        val quota = Quota(
            maxTopics = 10,
            maxNamespaces = 5,
            maxEventsPerDay = 1000,
            maxConsumers = 2,
            maxUsers = 3,
            maxEventSizeBytes = 512
        )
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        application.createTenant("tenant-with-data", quota = quota, metadata = metadata)
        
        val retrievedTenant = application.getTenant("tenant-with-data")
        assertNotNull(retrievedTenant)
        assertEquals(quota, retrievedTenant.quota)
        assertEquals(metadata, retrievedTenant.metadata)
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
        val retrievedDifferent = application.getTenant("casesensitive")
        val retrievedDifferent2 = application.getTenant("case-sensitive")

        assertNotNull(retrievedExact)
        assertNull(retrievedDifferent, "Tenant name lookup should be exact match (case sensitive)")
        assertNull(retrievedDifferent2, "Tenant name lookup should be exact match")
    }

    // Rule 5: Clear retrieval methods by ID and name
    @Test
    fun `getTenantByName retrieves tenant by name`() = runTest {
        val createdTenant = application.createTenant("by-name-test")
        val retrievedTenant = application.getTenantService.getTenantByName("by-name-test")

        assertNotNull(retrievedTenant)
        assertEquals(createdTenant.name, retrievedTenant.name)
        assertEquals(createdTenant.tenantId, retrievedTenant.tenantId)
    }

    @Test
    fun `getTenantByName returns null when tenant does not exist`() = runTest {
        val retrievedTenant = application.getTenantService.getTenantByName("non-existent")

        assertNull(retrievedTenant)
    }

    @Test
    fun `getTenant retrieves tenant by UUID`() = runTest {
        val createdTenant = application.createTenant("by-id-test")
        val retrievedTenant = application.getTenantService.getTenant(createdTenant.tenantId)

        assertNotNull(retrievedTenant)
        assertEquals(createdTenant.name, retrievedTenant.name)
        assertEquals(createdTenant.tenantId, retrievedTenant.tenantId)
    }

    @Test
    fun `getTenant returns null when tenant ID does not exist`() = runTest {
        val nonExistentId = java.util.UUID.randomUUID()
        val retrievedTenant = application.getTenantService.getTenant(nonExistentId)

        assertNull(retrievedTenant)
    }

    @Test
    fun `getTenant returns null when tenant is deleted`() = runTest {
        val createdTenant = application.createTenant("to-delete-by-id")
        application.deleteTenant(createdTenant.tenantId)

        val retrievedTenant = application.getTenantService.getTenant(createdTenant.tenantId)

        assertNull(retrievedTenant, "Deleted tenant should not be retrievable by ID")
    }

    @Test
    fun `getTenantByName returns null when tenant is deleted`() = runTest {
        val createdTenant = application.createTenant("to-delete-by-name")
        application.deleteTenant(createdTenant.tenantId)

        val retrievedTenant = application.getTenantService.getTenantByName("to-delete-by-name")

        assertNull(retrievedTenant, "Deleted tenant should not be retrievable by name")
    }

    @Test
    fun `getTenant and getTenantByName return same tenant for active tenant`() = runTest {
        val createdTenant = application.createTenant("same-tenant-test")

        val byId = application.getTenantService.getTenant(createdTenant.tenantId)
        val byName = application.getTenantService.getTenantByName("same-tenant-test")

        assertNotNull(byId)
        assertNotNull(byName)
        assertEquals(byId.tenantId, byName.tenantId)
        assertEquals(byId.name, byName.name)
        assertEquals(byId.quota, byName.quota)
        assertEquals(byId.metadata, byName.metadata)
    }

    @Test
    fun `getTenant works after tenant name is updated`() = runTest {
        val createdTenant = application.createTenant("original-name")
        application.updateTenant(createdTenant.tenantId, name = "updated-name")

        val byId = application.getTenantService.getTenant(createdTenant.tenantId)
        val byOldName = application.getTenantService.getTenantByName("original-name")
        val byNewName = application.getTenantService.getTenantByName("updated-name")

        assertNotNull(byId, "Should still be retrievable by UUID after rename")
        assertNull(byOldName, "Should not be retrievable by old name")
        assertNotNull(byNewName, "Should be retrievable by new name")
        assertEquals(createdTenant.tenantId, byId.tenantId)
        assertEquals(createdTenant.tenantId, byNewName.tenantId)
        assertEquals("updated-name", byId.name)
        assertEquals("updated-name", byNewName.name)
    }
}

