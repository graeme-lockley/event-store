package com.eventstore.domain.services.permission

import com.eventstore.domain.Application
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrantPermissionServiceTest {
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
    fun `grants permission and emits event`() = runTest {
        val permissions = setOf(Permission.READ, Permission.UPDATE)

        val event = application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = permissions,
                grantedBy = "admin"
            )
        )

        assertEquals(userId, event.principalId)
        assertEquals(PrincipalType.USER, event.principalType)
        assertEquals(ResourceType.TENANT, event.resourceType)
        assertEquals(permissions, event.permissions)

        // Verify event was stored
        val storedEvents = application.getEvents(
            topic = SystemTopics.PERMISSIONS_TOPIC_NAME,
            tenantName = SystemTopics.SYSTEM_TENANT_NAME,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_NAME
        )
        val permissionEvents = storedEvents.filter { it.type == PermissionEventType.GRANTED }
        assertTrue(permissionEvents.isNotEmpty())
        assertEquals(PermissionEventType.GRANTED, permissionEvents.last().type)
    }

    @Test
    fun `grants permission for specific resource`() = runTest {
        val resourceId = UUID.randomUUID()

        val event = application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceName = resourceId.toString(),
                tenantName = tenantName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )

        assertEquals(resourceId.toString(), event.resourceId)
    }

    @Test
    fun `grants permission for namespace`() = runTest {
        val namespaceName = "test-namespace"

        // Create namespace
        val tenant = application.getTenant(tenantName)!!
        application.createNamespace(tenant.tenantId, namespaceName)

        val event = application.grantPermission(
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

        assertEquals(ResourceType.NAMESPACE, event.resourceType)
        assertNotNull(event.namespaceResourceId)
    }

    @Test
    fun `grants permission for topic`() = runTest {
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

        val event = application.grantPermission(
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

        assertEquals(ResourceType.TOPIC, event.resourceType)
        assertNotNull(event.topicResourceId)
    }

    @Test
    fun `grants permission with expiration`() = runTest {
        val expiresAt = Instant.now().plusSeconds(3600)

        val event = application.grantPermission(
            GrantPermissionRequest(
                principalId = userId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = setOf(Permission.READ),
                expiresAt = expiresAt,
                grantedBy = "admin"
            )
        )

        assertEquals(expiresAt, event.expiresAt)
    }

    @Test
    fun `grants permission for API key principal`() = runTest {
        val apiKeyId = UUID.randomUUID().toString()

        val event = application.grantPermission(
            GrantPermissionRequest(
                principalId = apiKeyId,
                principalType = PrincipalType.API_KEY,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )

        assertEquals(apiKeyId, event.principalId)
        assertEquals(PrincipalType.API_KEY, event.principalType)
    }
}
