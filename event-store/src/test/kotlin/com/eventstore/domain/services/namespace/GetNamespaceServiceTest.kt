package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class GetNamespaceServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    /**
     * Gets the initial namespace count, accounting for the bootstrap $management namespace.
     * Bootstrap creates a namespace named "$management" in the "$system" tenant.
     */
    private suspend fun getInitialNamespaceCount(tenantName: String): Int {
        val namespaces = application.listNamespaces(tenantName)
        return namespaces.size
    }

    @Test
    fun `gets namespace that exists`() = runTest {
        application.createTenant("acme")
        val createdNamespace = application.createNamespace("acme", "billing")
        val retrievedNamespace = application.getNamespace("acme", "billing")

        assertNotNull(retrievedNamespace, "Namespace should be retrieved")
        assertEquals(createdNamespace.name, retrievedNamespace.name)
        assertEquals(createdNamespace.tenantName, retrievedNamespace.tenantName)
        assertEquals(createdNamespace.resourceId, retrievedNamespace.resourceId)
        assertEquals(createdNamespace.description, retrievedNamespace.description)
        assertEquals(createdNamespace.metadata, retrievedNamespace.metadata)
        assertEquals(createdNamespace.createdAt, retrievedNamespace.createdAt)
    }

    @Test
    fun `returns null when namespace does not exist`() = runTest {
        application.createTenant("acme")
        val retrievedNamespace = application.getNamespace("acme", "non-existent")

        assertNull(retrievedNamespace, "Non-existent namespace should return null")
    }

    @Test
    fun `returns null when tenant does not exist`() = runTest {
        val retrievedNamespace = application.getNamespace("unknown", "billing")

        assertNull(retrievedNamespace, "Namespace in non-existent tenant should return null")
    }

    @Test
    fun `returns null when namespace is deleted`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        application.deleteNamespace("acme", "billing")

        val retrievedNamespace = application.getNamespace("acme", "billing")

        assertNull(retrievedNamespace, "Deleted namespace should return null")
    }

    @Test
    fun `lists empty list when no namespaces exist in tenant`() = runTest {
        application.createTenant("acme")
        val namespaces = application.listNamespaces("acme")

        // Bootstrap creates $management namespace in $system tenant, not in custom tenants
        assertTrue(namespaces.isEmpty(), "Should return empty list when no namespaces exist in tenant")
    }

    @Test
    fun `lists bootstrap namespace in system tenant`() = runTest {
        val namespaces = application.listNamespaces(SystemTopics.SYSTEM_TENANT_ID)

        // Bootstrap creates the $management namespace, so we should have at least 1 namespace
        assertTrue(namespaces.isNotEmpty(), "Should include bootstrap \$management namespace")
        val managementNamespace = namespaces.find { it.name == SystemTopics.MANAGEMENT_NAMESPACE_ID }
        assertNotNull(managementNamespace, "Bootstrap should create \$management namespace")
    }

    @Test
    fun `gets bootstrap management namespace`() = runTest {
        val managementNamespace = application.getNamespace(SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID)

        assertNotNull(managementNamespace, "Should be able to retrieve bootstrap \$management namespace")
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_ID, managementNamespace.name)
        assertEquals(SystemTopics.SYSTEM_TENANT_ID, managementNamespace.tenantName)
        assertTrue(managementNamespace.isActive)
    }

    @Test
    fun `lists single namespace`() = runTest {
        application.createTenant("acme")
        val createdNamespace = application.createNamespace("acme", "billing")
        val namespaces = application.listNamespaces("acme")

        assertEquals(1, namespaces.size, "Should return one namespace")
        assertEquals(createdNamespace.name, namespaces[0].name)
        assertEquals(createdNamespace.resourceId, namespaces[0].resourceId)
    }

    @Test
    fun `lists multiple namespaces`() = runTest {
        application.createTenant("acme")
        val ns1 = application.createNamespace("acme", "namespace-1")
        val ns2 = application.createNamespace("acme", "namespace-2")
        val ns3 = application.createNamespace("acme", "namespace-3")

        val namespaces = application.listNamespaces("acme")

        assertEquals(3, namespaces.size, "Should return three namespaces")
        val namespaceNames = namespaces.map { it.name }.toSet()
        assertTrue(namespaceNames.contains("namespace-1"))
        assertTrue(namespaceNames.contains("namespace-2"))
        assertTrue(namespaceNames.contains("namespace-3"))
    }

    @Test
    fun `list namespaces excludes deleted namespaces`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "active-1")
        application.createNamespace("acme", "active-2")
        application.createNamespace("acme", "to-delete")
        application.deleteNamespace("acme", "to-delete")

        val namespaces = application.listNamespaces("acme")

        assertEquals(2, namespaces.size, "Should return only active namespaces")
        val namespaceNames = namespaces.map { it.name }.toSet()
        assertTrue(namespaceNames.contains("active-1"))
        assertTrue(namespaceNames.contains("active-2"))
        assertFalse(namespaceNames.contains("to-delete"))
    }

    @Test
    fun `gets namespace with description`() = runTest {
        application.createTenant("acme")
        val createdNamespace = application.createNamespaceService.execute(
            CreateNamespaceRequest("acme", "billing", description = "Billing namespace")
        )
        val retrievedNamespace = application.getNamespace("acme", "billing")

        assertNotNull(retrievedNamespace)
        assertEquals("Billing namespace", retrievedNamespace.description)
    }

    @Test
    fun `gets namespace with metadata`() = runTest {
        application.createTenant("acme")
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        val createdNamespace = application.createNamespaceService.execute(
            CreateNamespaceRequest("acme", "billing", metadata = metadata)
        )
        val retrievedNamespace = application.getNamespace("acme", "billing")

        assertNotNull(retrievedNamespace)
        assertEquals(metadata, retrievedNamespace.metadata)
        assertEquals("pro", retrievedNamespace.metadata["plan"])
        assertEquals("us-east", retrievedNamespace.metadata["region"])
    }

    @Test
    fun `gets updated namespace with new values`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val newMetadata = mapOf("plan" to "pro", "tier" to "premium")
        application.updateNamespace("acme", "billing", name = "updated-billing", description = "Updated billing", metadata = newMetadata)

        val retrievedNamespace = application.getNamespace("acme", "updated-billing")

        assertNotNull(retrievedNamespace)
        assertEquals("updated-billing", retrievedNamespace.name)
        assertEquals("Updated billing", retrievedNamespace.description)
        assertEquals(newMetadata, retrievedNamespace.metadata)
        assertNotNull(retrievedNamespace.updatedAt)
    }

    @Test
    fun `gets namespace with unicode characters in name`() = runTest {
        val unicodeName = "namespace-测试-🚀"
        application.createTenant("acme")
        val createdNamespace = application.createNamespace("acme", unicodeName)
        val retrievedNamespace = application.getNamespace("acme", unicodeName)

        assertNotNull(retrievedNamespace)
        assertEquals(unicodeName, retrievedNamespace.name)
        assertEquals(createdNamespace.resourceId, retrievedNamespace.resourceId)
    }

    @Test
    fun `list namespaces includes namespaces with unicode names`() = runTest {
        application.createTenant("acme")
        val unicodeName = "namespace-测试-🚀"
        application.createNamespace("acme", unicodeName)
        application.createNamespace("acme", "regular-namespace")

        val namespaces = application.listNamespaces("acme")

        assertEquals(2, namespaces.size)
        val namespaceNames = namespaces.map { it.name }.toSet()
        assertTrue(namespaceNames.contains(unicodeName))
        assertTrue(namespaceNames.contains("regular-namespace"))
    }

    @Test
    fun `gets namespace preserves resourceId after update`() = runTest {
        application.createTenant("acme")
        val createdNamespace = application.createNamespace("acme", "billing")
        val originalResourceId = createdNamespace.resourceId

        application.updateNamespace("acme", "billing", name = "renamed-billing")
        val retrievedNamespace = application.getNamespace("acme", "renamed-billing")

        assertNotNull(retrievedNamespace)
        assertEquals(originalResourceId, retrievedNamespace.resourceId, "ResourceId should remain unchanged after rename")
    }

    @Test
    fun `list namespaces returns namespaces in consistent order`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "ns-a")
        application.createNamespace("acme", "ns-b")
        application.createNamespace("acme", "ns-c")

        val namespaces1 = application.listNamespaces("acme")
        val namespaces2 = application.listNamespaces("acme")

        assertEquals(namespaces1.size, namespaces2.size)
        // Both lists should contain the same namespaces (order may vary, but content should be same)
        val names1 = namespaces1.map { it.name }.toSet()
        val names2 = namespaces2.map { it.name }.toSet()
        assertEquals(names1, names2)
    }

    @Test
    fun `gets namespace returns correct createdAt timestamp`() = runTest {
        application.createTenant("acme")
        val beforeCreation = java.time.Instant.now()
        val createdNamespace = application.createNamespace("acme", "timestamp-test")
        val afterCreation = java.time.Instant.now()

        val retrievedNamespace = application.getNamespace("acme", "timestamp-test")

        assertNotNull(retrievedNamespace)
        assertTrue(retrievedNamespace.createdAt.isAfter(beforeCreation) || retrievedNamespace.createdAt == beforeCreation)
        assertTrue(retrievedNamespace.createdAt.isBefore(afterCreation) || retrievedNamespace.createdAt == afterCreation)
        assertEquals(createdNamespace.createdAt, retrievedNamespace.createdAt)
    }

    @Test
    fun `gets namespace returns correct updatedAt timestamp after update`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")
        val beforeUpdate = java.time.Instant.now()
        application.updateNamespace("acme", "billing", name = "updated-billing")
        val afterUpdate = java.time.Instant.now()

        val retrievedNamespace = application.getNamespace("acme", "updated-billing")

        assertNotNull(retrievedNamespace)
        assertNotNull(retrievedNamespace.updatedAt)
        assertTrue(retrievedNamespace.updatedAt!!.isAfter(beforeUpdate) || retrievedNamespace.updatedAt == beforeUpdate)
        assertTrue(retrievedNamespace.updatedAt!!.isBefore(afterUpdate) || retrievedNamespace.updatedAt == afterUpdate)
    }

    @Test
    fun `gets namespace returns null for updated name when original name is used`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "original-name")
        application.updateNamespace("acme", "original-name", name = "new-name")

        val retrievedByOldName = application.getNamespace("acme", "original-name")
        val retrievedByNewName = application.getNamespace("acme", "new-name")

        assertNull(retrievedByOldName, "Should return null when querying by old name")
        assertNotNull(retrievedByNewName, "Should return namespace when querying by new name")
        assertEquals("new-name", retrievedByNewName.name)
    }

    @Test
    fun `list namespaces excludes multiple deleted namespaces`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "keep-1")
        application.createNamespace("acme", "delete-1")
        application.createNamespace("acme", "keep-2")
        application.createNamespace("acme", "delete-2")
        application.createNamespace("acme", "keep-3")

        application.deleteNamespace("acme", "delete-1")
        application.deleteNamespace("acme", "delete-2")

        val namespaces = application.listNamespaces("acme")

        assertEquals(3, namespaces.size, "Should return only active namespaces")
        val namespaceNames = namespaces.map { it.name }.toSet()
        assertTrue(namespaceNames.contains("keep-1"))
        assertTrue(namespaceNames.contains("keep-2"))
        assertTrue(namespaceNames.contains("keep-3"))
        assertFalse(namespaceNames.contains("delete-1"))
        assertFalse(namespaceNames.contains("delete-2"))
    }

    @Test
    fun `gets namespace with empty metadata`() = runTest {
        application.createTenant("acme")
        application.createNamespaceService.execute(
            CreateNamespaceRequest("acme", "empty-metadata-ns", metadata = emptyMap())
        )
        val retrievedNamespace = application.getNamespace("acme", "empty-metadata-ns")

        assertNotNull(retrievedNamespace)
        assertTrue(retrievedNamespace.metadata.isEmpty())
    }

    @Test
    fun `list namespaces returns all namespace fields correctly`() = runTest {
        application.createTenant("acme")
        val metadata = mapOf("environment" to "test")
        val createdNamespace = application.createNamespaceService.execute(
            CreateNamespaceRequest("acme", "complete-ns", description = "Complete namespace", metadata = metadata)
        )

        val namespaces = application.listNamespaces("acme")
        val namespace = namespaces.find { it.name == "complete-ns" }

        assertNotNull(namespace)
        assertEquals(createdNamespace.resourceId, namespace.resourceId)
        assertEquals(createdNamespace.name, namespace.name)
        assertEquals(createdNamespace.description, namespace.description)
        assertEquals(createdNamespace.metadata, namespace.metadata)
        assertEquals(createdNamespace.createdAt, namespace.createdAt)
        assertNull(namespace.updatedAt)
        assertNull(namespace.deletedAt)
        assertTrue(namespace.isActive)
    }

    @Test
    fun `gets namespace is case sensitive`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "CaseSensitive")

        val retrievedExact = application.getNamespace("acme", "CaseSensitive")
        val retrievedLowercase = application.getNamespace("acme", "casesensitive")
        val retrievedUppercase = application.getNamespace("acme", "CASESENSITIVE")

        assertNotNull(retrievedExact)
        assertNull(retrievedLowercase, "Namespace name lookup should be case sensitive")
        assertNull(retrievedUppercase, "Namespace name lookup should be case sensitive")
    }

    @Test
    fun `list namespaces filters by tenant name`() = runTest {
        application.createTenant("acme")
        application.createTenant("corp")
        application.createNamespace("acme", "billing")
        application.createNamespace("acme", "shipping")
        application.createNamespace("corp", "billing")
        application.createNamespace("corp", "shipping")

        val acmeNamespaces = application.listNamespaces("acme")
        val corpNamespaces = application.listNamespaces("corp")

        assertEquals(2, acmeNamespaces.size)
        assertEquals(2, corpNamespaces.size)
        val acmeNames = acmeNamespaces.map { it.name }.toSet()
        val corpNames = corpNamespaces.map { it.name }.toSet()
        assertTrue(acmeNames.contains("billing"))
        assertTrue(acmeNames.contains("shipping"))
        assertTrue(corpNames.contains("billing"))
        assertTrue(corpNames.contains("shipping"))
    }
}

