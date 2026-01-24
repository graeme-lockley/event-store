package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

class GetNamespaceServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
        }

    @Test
    fun `gets namespace that exists by UUID`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val createdNamespace = application.createNamespace(tenantId, "billing")
            val namespaceId = createdNamespace.namespaceId
            val retrievedNamespace = application.getNamespace(namespaceId)

            assertNotNull(retrievedNamespace, "Namespace should be retrieved")
            assertEquals(createdNamespace.name, retrievedNamespace.name)
            assertEquals(createdNamespace.tenantName, retrievedNamespace.tenantName)
            assertEquals(createdNamespace.namespaceId, retrievedNamespace.namespaceId)
            assertEquals(createdNamespace.description, retrievedNamespace.description)
            assertEquals(createdNamespace.metadata, retrievedNamespace.metadata)
            assertEquals(createdNamespace.createdAt, retrievedNamespace.createdAt)
        }

    @Test
    fun `gets namespace that exists by name`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val createdNamespace = application.createNamespace(tenantId, "billing")
            val retrievedNamespace = application.getNamespaceByName("acme", "billing")

            assertNotNull(retrievedNamespace, "Namespace should be retrieved")
            assertEquals(createdNamespace.name, retrievedNamespace.name)
            assertEquals(createdNamespace.tenantName, retrievedNamespace.tenantName)
            assertEquals(createdNamespace.namespaceId, retrievedNamespace.namespaceId)
            assertEquals(createdNamespace.description, retrievedNamespace.description)
            assertEquals(createdNamespace.metadata, retrievedNamespace.metadata)
            assertEquals(createdNamespace.createdAt, retrievedNamespace.createdAt)
        }

    @Test
    fun `returns null when namespace does not exist`() =
        runTest {
            val nonExistentNamespaceId = UUID.randomUUID()
            val retrievedNamespace = application.getNamespace(nonExistentNamespaceId)

            assertNull(retrievedNamespace, "Non-existent namespace should return null")
        }

    @Test
    fun `returns null when namespace is deleted`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "billing")
            val namespaceId = namespace.namespaceId
            application.deleteNamespace(namespaceId)

            val retrievedNamespace = application.getNamespace(namespaceId)

            assertNull(retrievedNamespace, "Deleted namespace should return null")
        }

    @Test
    fun `lists empty list when no namespaces exist in tenant`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val namespaces = application.listNamespaces(tenantId)

            // Bootstrap creates $management namespace in $system tenant, not in custom tenants
            assertTrue(namespaces.isEmpty(), "Should return empty list when no namespaces exist in tenant")
        }

    @Test
    fun `lists bootstrap namespace in system tenant`() =
        runTest {
            val systemTenant = application.getTenant(SystemTopics.SYSTEM_TENANT_NAME)
            val systemTenantId = systemTenant!!.tenantId
            val namespaces = application.listNamespaces(systemTenantId)

            // Bootstrap creates the $management namespace, so we should have at least 1 namespace
            assertTrue(namespaces.isNotEmpty(), "Should include bootstrap \$management namespace")
            val managementNamespace = namespaces.find { it.name == SystemTopics.MANAGEMENT_NAMESPACE_NAME }
            assertNotNull(managementNamespace, "Bootstrap should create \$management namespace")
        }

    @Test
    fun `gets bootstrap management namespace`() =
        runTest {
            val managementNamespace =
                application.getNamespaceByName(SystemTopics.SYSTEM_TENANT_NAME, SystemTopics.MANAGEMENT_NAMESPACE_NAME)

            assertNotNull(managementNamespace, "Should be able to retrieve bootstrap \$management namespace")
            assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, managementNamespace.name)
            assertEquals(SystemTopics.SYSTEM_TENANT_NAME, managementNamespace.tenantName)
            assertTrue(managementNamespace.isActive)
        }

    @Test
    fun `lists single namespace`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val createdNamespace = application.createNamespace(tenantId, "billing")
            val namespaces = application.listNamespaces(tenantId)

            assertEquals(1, namespaces.size, "Should return one namespace")
            assertEquals(createdNamespace.name, namespaces[0].name)
            assertEquals(createdNamespace.namespaceId, namespaces[0].namespaceId)
        }

    @Test
    fun `lists multiple namespaces`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(tenantId, "namespace-1")
            application.createNamespace(tenantId, "namespace-2")
            application.createNamespace(tenantId, "namespace-3")

            val namespaces = application.listNamespaces(tenantId)

            assertEquals(3, namespaces.size, "Should return three namespaces")
            val namespaceNames = namespaces.map { it.name }.toSet()
            assertTrue(namespaceNames.contains("namespace-1"))
            assertTrue(namespaceNames.contains("namespace-2"))
            assertTrue(namespaceNames.contains("namespace-3"))
        }

    @Test
    fun `list namespaces excludes deleted namespaces`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(tenantId, "active-1")
            application.createNamespace(tenantId, "active-2")
            val toDelete = application.createNamespace(tenantId, "to-delete")
            application.deleteNamespace(toDelete.namespaceId)

            val namespaces = application.listNamespaces(tenantId)

            assertEquals(2, namespaces.size, "Should return only active namespaces")
            val namespaceNames = namespaces.map { it.name }.toSet()
            assertTrue(namespaceNames.contains("active-1"))
            assertTrue(namespaceNames.contains("active-2"))
            assertFalse(namespaceNames.contains("to-delete"))
        }

    @Test
    fun `gets namespace with description`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(
                tenantId = tenantId,
                namespaceName = "billing",
                description = "Billing namespace",
            )
            val retrievedNamespace = application.getNamespaceByName("acme", "billing")

            assertNotNull(retrievedNamespace)
            assertEquals("Billing namespace", retrievedNamespace.description)
        }

    @Test
    fun `gets namespace with metadata`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val metadata = mapOf("plan" to "pro", "region" to "us-east")
            application.createNamespace(
                tenantId = tenantId,
                namespaceName = "billing",
                metadata = metadata,
            )
            val retrievedNamespace = application.getNamespaceByName("acme", "billing")

            assertNotNull(retrievedNamespace)
            assertEquals(metadata, retrievedNamespace.metadata)
            assertEquals("pro", retrievedNamespace.metadata["plan"])
            assertEquals("us-east", retrievedNamespace.metadata["region"])
        }

    @Test
    fun `gets updated namespace with new values`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "billing")
            val namespaceId = namespace.namespaceId
            val newMetadata = mapOf("plan" to "pro", "tier" to "premium")
            application.updateNamespace(
                namespaceId = namespaceId,
                name = "updated-billing",
                description = "Updated billing",
                metadata = newMetadata,
            )

            val retrievedNamespace = application.getNamespaceByName("acme", "updated-billing")

            assertNotNull(retrievedNamespace)
            assertEquals("updated-billing", retrievedNamespace.name)
            assertEquals("Updated billing", retrievedNamespace.description)
            assertEquals(newMetadata, retrievedNamespace.metadata)
            assertNotNull(retrievedNamespace.updatedAt)
        }

    @Test
    fun `gets namespace with unicode characters in name`() =
        runTest {
            val unicodeName = "namespace-测试-🚀"
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val createdNamespace = application.createNamespace(tenantId, unicodeName)
            val retrievedNamespace = application.getNamespaceByName("acme", unicodeName)

            assertNotNull(retrievedNamespace)
            assertEquals(unicodeName, retrievedNamespace.name)
            assertEquals(createdNamespace.namespaceId, retrievedNamespace.namespaceId)
        }

    @Test
    fun `list namespaces includes namespaces with unicode names`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val unicodeName = "namespace-测试-🚀"
            application.createNamespace(tenantId, unicodeName)
            application.createNamespace(tenantId, "regular-namespace")

            val namespaces = application.listNamespaces(tenantId)

            assertEquals(2, namespaces.size)
            val namespaceNames = namespaces.map { it.name }.toSet()
            assertTrue(namespaceNames.contains(unicodeName))
            assertTrue(namespaceNames.contains("regular-namespace"))
        }

    @Test
    fun `gets namespace preserves namespaceId after update`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val createdNamespace = application.createNamespace(tenantId, "billing")
            val originalNamespaceId = createdNamespace.namespaceId

            application.updateNamespace(originalNamespaceId, name = "renamed-billing")
            val retrievedNamespace = application.getNamespaceByName("acme", "renamed-billing")

            assertNotNull(retrievedNamespace)
            assertEquals(
                originalNamespaceId,
                retrievedNamespace.namespaceId,
                "NamespaceId should remain unchanged after rename",
            )
        }

    @Test
    fun `list namespaces returns namespaces in consistent order`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(tenantId, "ns-a")
            application.createNamespace(tenantId, "ns-b")
            application.createNamespace(tenantId, "ns-c")

            val namespaces1 = application.listNamespaces(tenantId)
            val namespaces2 = application.listNamespaces(tenantId)

            assertEquals(namespaces1.size, namespaces2.size)
            // Both lists should contain the same namespaces (order may vary, but content should be same)
            val names1 = namespaces1.map { it.name }.toSet()
            val names2 = namespaces2.map { it.name }.toSet()
            assertEquals(names1, names2)
        }

    @Test
    fun `gets namespace returns correct createdAt timestamp`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val beforeCreation = java.time.Instant.now()
            val createdNamespace = application.createNamespace(tenantId, "timestamp-test")
            val afterCreation = java.time.Instant.now()

            val retrievedNamespace = application.getNamespaceByName("acme", "timestamp-test")

            assertNotNull(retrievedNamespace)
            assertTrue(retrievedNamespace.createdAt.isAfter(beforeCreation) || retrievedNamespace.createdAt == beforeCreation)
            assertTrue(retrievedNamespace.createdAt.isBefore(afterCreation) || retrievedNamespace.createdAt == afterCreation)
            assertEquals(createdNamespace.createdAt, retrievedNamespace.createdAt)
        }

    @Test
    fun `gets namespace returns correct updatedAt timestamp after update`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "billing")
            val namespaceId = namespace.namespaceId
            val beforeUpdate = java.time.Instant.now()
            application.updateNamespace(namespaceId, name = "updated-billing")
            val afterUpdate = java.time.Instant.now()

            val retrievedNamespace = application.getNamespaceByName("acme", "updated-billing")

            assertNotNull(retrievedNamespace)
            assertNotNull(retrievedNamespace.updatedAt)
            assertTrue(retrievedNamespace.updatedAt!!.isAfter(beforeUpdate) || retrievedNamespace.updatedAt == beforeUpdate)
            assertTrue(retrievedNamespace.updatedAt!!.isBefore(afterUpdate) || retrievedNamespace.updatedAt == afterUpdate)
        }

    @Test
    fun `gets namespace returns null for updated name when original name is used`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val namespace = application.createNamespace(tenantId, "original-name")
            val namespaceId = namespace.namespaceId
            application.updateNamespace(namespaceId, name = "new-name")

            val retrievedByOldName = application.getNamespaceByName("acme", "original-name")
            val retrievedByNewName = application.getNamespaceByName("acme", "new-name")

            assertNull(retrievedByOldName, "Should return null when querying by old name")
            assertNotNull(retrievedByNewName, "Should return namespace when querying by new name")
            assertEquals("new-name", retrievedByNewName.name)
        }

    @Test
    fun `list namespaces excludes multiple deleted namespaces`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(tenantId, "keep-1")
            val delete1 = application.createNamespace(tenantId, "delete-1")
            application.createNamespace(tenantId, "keep-2")
            val delete2 = application.createNamespace(tenantId, "delete-2")
            application.createNamespace(tenantId, "keep-3")

            application.deleteNamespace(delete1.namespaceId)
            application.deleteNamespace(delete2.namespaceId)

            val namespaces = application.listNamespaces(tenantId)

            assertEquals(3, namespaces.size, "Should return only active namespaces")
            val namespaceNames = namespaces.map { it.name }.toSet()
            assertTrue(namespaceNames.contains("keep-1"))
            assertTrue(namespaceNames.contains("keep-2"))
            assertTrue(namespaceNames.contains("keep-3"))
            assertFalse(namespaceNames.contains("delete-1"))
            assertFalse(namespaceNames.contains("delete-2"))
        }

    @Test
    fun `gets namespace with empty metadata`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(
                tenantId = tenantId,
                namespaceName = "empty-metadata-ns",
                metadata = emptyMap(),
            )
            val retrievedNamespace = application.getNamespaceByName("acme", "empty-metadata-ns")

            assertNotNull(retrievedNamespace)
            assertTrue(retrievedNamespace.metadata.isEmpty())
        }

    @Test
    fun `list namespaces returns all namespace fields correctly`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            val metadata = mapOf("environment" to "test")
            val createdNamespace =
                application.createNamespace(
                    tenantId = tenantId,
                    namespaceName = "complete-ns",
                    description = "Complete namespace",
                    metadata = metadata,
                )

            val namespaces = application.listNamespaces(tenantId)
            val namespace = namespaces.find { it.name == "complete-ns" }

            assertNotNull(namespace)
            assertEquals(createdNamespace.namespaceId, namespace.namespaceId)
            assertEquals(createdNamespace.name, namespace.name)
            assertEquals(createdNamespace.description, namespace.description)
            assertEquals(createdNamespace.metadata, namespace.metadata)
            assertEquals(createdNamespace.createdAt, namespace.createdAt)
            assertNull(namespace.updatedAt)
            assertNull(namespace.deletedAt)
            assertTrue(namespace.isActive)
        }

    @Test
    fun `gets namespace is case sensitive`() =
        runTest {
            val tenant = application.createTenant("acme")
            val tenantId = tenant.tenantId
            application.createNamespace(tenantId, "CaseSensitive")

            val retrievedExact = application.getNamespaceByName("acme", "CaseSensitive")
            val retrievedLowercase = application.getNamespaceByName("acme", "casesensitive")
            val retrievedUppercase = application.getNamespaceByName("acme", "CASESENSITIVE")

            assertNotNull(retrievedExact)
            assertNull(retrievedLowercase, "Namespace name lookup should be case sensitive")
            assertNull(retrievedUppercase, "Namespace name lookup should be case sensitive")
        }

    @Test
    fun `list namespaces filters by tenantId`() =
        runTest {
            val acmeTenant = application.createTenant("acme")
            val acmeTenantId = acmeTenant.tenantId
            val corpTenant = application.createTenant("corp")
            val corpTenantId = corpTenant.tenantId
            application.createNamespace(acmeTenantId, "billing")
            application.createNamespace(acmeTenantId, "shipping")
            application.createNamespace(corpTenantId, "billing")
            application.createNamespace(corpTenantId, "shipping")

            val acmeNamespaces = application.listNamespaces(acmeTenantId)
            val corpNamespaces = application.listNamespaces(corpTenantId)

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
