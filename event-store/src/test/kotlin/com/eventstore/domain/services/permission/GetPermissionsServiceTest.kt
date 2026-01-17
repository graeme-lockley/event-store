package com.eventstore.domain.services.permission

import com.eventstore.domain.Application
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.services.createApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetPermissionsServiceTest {
    private lateinit var application: Application
    private lateinit var tenantName: String
    private lateinit var userId: String

    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
        tenantName = "test-tenant"
        userId = UUID.randomUUID().toString()

        // Create tenant
        application.createTenant(tenantName)
    }

    @Test
    fun `gets permissions for tenant`() = runTest {
        val permissions = setOf(Permission.READ, Permission.UPDATE)

        // Grant permission
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = permissions,
                grantedBy = "admin"
            )
        )

        // Get permissions
        val result = application.getPermissions(
            GetPermissionsRequest(
                principalId = userId,
                tenantName = tenantName
            )
        )

        assertTrue(result.isNotEmpty())
        val tenantPermission = result.find { it.resourceType == ResourceType.TENANT }
        assertNotNull(tenantPermission)
        assertEquals(permissions, tenantPermission.permissions)
    }

    @Test
    fun `gets permissions for namespace`() = runTest {
        val namespaceName = "test-namespace"

        // Create namespace
        val tenant = application.getTenant(tenantName)!!
        application.createNamespace(tenant.tenantId, namespaceName)

        // Grant permission
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.NAMESPACE,
                tenantName = tenantName,
                namespaceName = namespaceName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )

        // Get permissions
        val result = application.getPermissions(
            GetPermissionsRequest(
                principalId = userId,
                tenantName = tenantName,
                namespaceName = namespaceName
            )
        )

        assertTrue(result.isNotEmpty())
        val namespacePermission = result.find { it.resourceType == ResourceType.NAMESPACE }
        assertNotNull(namespacePermission)
    }

    @Test
    fun `gets permissions for topic`() = runTest {
        val namespaceName = "test-namespace"
        val topicName = "test-topic"

        // Create namespace and topic
        val tenant = application.getTenant(tenantName)!!
        application.createNamespace(tenant.tenantId, namespaceName)
        application.createTopic(
            name = topicName,
            schemas = emptyList(),
            tenantName = tenantName,
            namespaceName = namespaceName
        )

        // Grant permission
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TOPIC,
                tenantName = tenantName,
                namespaceName = namespaceName,
                topicName = topicName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )

        // Get permissions
        val result = application.getPermissions(
            GetPermissionsRequest(
                principalId = userId,
                tenantName = tenantName,
                namespaceName = namespaceName,
                topicName = topicName
            )
        )

        assertTrue(result.isNotEmpty())
        val topicPermission = result.find { it.resourceType == ResourceType.TOPIC }
        assertNotNull(topicPermission)
    }

    @Test
    fun `returns empty list when no permissions granted`() = runTest {
        val result = application.getPermissions(
            GetPermissionsRequest(
                principalId = userId,
                tenantName = tenantName
            )
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun `gets multiple permissions for same principal`() = runTest {
        val namespaceName = "test-namespace"

        // Create namespace
        val tenant = application.getTenant(tenantName)!!
        application.createNamespace(tenant.tenantId, namespaceName)

        // Grant multiple permissions
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )
        application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.NAMESPACE,
                tenantName = tenantName,
                namespaceName = namespaceName,
                permissions = setOf(Permission.UPDATE),
                grantedBy = "admin"
            )
        )

        // Get permissions
        val result = application.getPermissions(
            GetPermissionsRequest(
                principalId = userId,
                tenantName = tenantName
            )
        )

        assertTrue(result.size >= 2)
        assertTrue(result.any { it.resourceType == ResourceType.TENANT })
        assertTrue(result.any { it.resourceType == ResourceType.NAMESPACE })
    }
}

