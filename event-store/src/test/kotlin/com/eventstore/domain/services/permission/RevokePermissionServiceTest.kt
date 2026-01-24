package com.eventstore.domain.services.permission

import com.eventstore.domain.Application
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.Schema
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevokePermissionServiceTest {
    private lateinit var application: Application
    private lateinit var tenantName: String
    private lateinit var userId: String

    @BeforeEach
    fun setup() =
        runTest {
            application = createApplication()
            tenantName = "test-tenant"
            userId = UUID.randomUUID().toString()

            // Create tenant
            application.createTenant(tenantName)
        }

    @Test
    fun `revokes permission and emits event`() =
        runTest {
            val permissions = setOf(Permission.READ, Permission.UPDATE)

            val tenant = application.getTenant(tenantName)!!

            // First grant permission
            application.grantPermission(
                GrantPermissionRequest(
                    principalId = userId,
                    principalType = PrincipalType.USER,
                    resourceType = ResourceType.TENANT,
                    tenantId = tenant.tenantId,
                    permissions = permissions,
                    grantedBy = "admin",
                ),
            )

            // Then revoke permission
            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = userId,
                        principalType = PrincipalType.USER,
                        resourceType = ResourceType.TENANT,
                        tenantId = tenant.tenantId,
                        permissions = permissions,
                        revokedBy = "admin",
                    ),
                )

            assertEquals(userId, event.principalId)
            assertEquals(PrincipalType.USER, event.principalType)
            assertEquals(ResourceType.TENANT, event.resourceType)
            assertEquals(permissions, event.permissions)

            // Verify event was stored
            val storedEvents =
                application.getEvents(
                    topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                )
            val revokedEvents = storedEvents.filter { it.type == PermissionEventType.REVOKED }
            assertTrue(revokedEvents.isNotEmpty())
            assertEquals(PermissionEventType.REVOKED, revokedEvents.last().type)
        }

    @Test
    fun `revokes permission for specific resource`() =
        runTest {
            val tenant = application.getTenant(tenantName)!!
            // RevokePermissionService doesn't use resourceName parameter the same way as GrantPermissionService
            // It resolves resourceId based on resourceType, so we test that it resolves correctly
            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = userId,
                        principalType = PrincipalType.USER,
                        resourceType = ResourceType.TENANT,
                        tenantId = tenant.tenantId,
                        permissions = setOf(Permission.READ),
                        revokedBy = "admin",
                    ),
                )

            // Verify event was created with correct tenant resourceId
            assertNotNull(event.tenantResourceId)
            assertEquals(ResourceType.TENANT, event.resourceType)
        }

    @Test
    fun `revokes permission for namespace`() =
        runTest {
            val namespaceName = "test-namespace"

            // Create namespace
            val tenant = application.getTenant(tenantName)!!
            val namespace = application.createNamespace(tenant.tenantId, namespaceName)

            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = userId,
                        principalType = PrincipalType.USER,
                        resourceType = ResourceType.NAMESPACE,
                        tenantId = tenant.tenantId,
                        resourceId = namespace.namespaceId.toString(),
                        permissions = setOf(Permission.READ),
                        revokedBy = "admin",
                    ),
                )

            assertEquals(ResourceType.NAMESPACE, event.resourceType)
            assertNotNull(event.namespaceResourceId)
            assertEquals(namespace.namespaceId.toString(), event.namespaceResourceId)
        }

    @Test
    fun `revokes permission for topic`() =
        runTest {
            val namespaceName = "test-namespace"
            val topicName = "test-topic"

            // Create namespace and topic
            val tenant = application.getTenant(tenantName)!!
            val namespace = application.createNamespace(tenant.tenantId, namespaceName)
            val topic =
                application.createTopic(
                    name = topicName,
                    schemas = listOf(Schema(eventType = "test.event")),
                    namespaceId = namespace.namespaceId,
                )

            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = userId,
                        principalType = PrincipalType.USER,
                        resourceType = ResourceType.TOPIC,
                        tenantId = tenant.tenantId,
                        resourceId = topic.topicId.toString(),
                        permissions = setOf(Permission.READ),
                        revokedBy = "admin",
                    ),
                )

            assertEquals(ResourceType.TOPIC, event.resourceType)
            assertNotNull(event.topicId)
            assertEquals(topic.topicId.toString(), event.topicId)
        }

    @Test
    fun `revokes permission with reason`() =
        runTest {
            val tenant = application.getTenant(tenantName)!!
            val reason = "Access no longer needed"

            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = userId,
                        principalType = PrincipalType.USER,
                        resourceType = ResourceType.TENANT,
                        tenantId = tenant.tenantId,
                        permissions = setOf(Permission.READ),
                        revokedBy = "admin",
                        reason = reason,
                    ),
                )

            assertEquals(reason, event.reason)
        }

    @Test
    fun `revokes permission for API key principal`() =
        runTest {
            val tenant = application.getTenant(tenantName)!!
            val apiKeyId = UUID.randomUUID().toString()

            val event =
                application.revokePermission(
                    RevokePermissionRequest(
                        principalId = apiKeyId,
                        principalType = PrincipalType.API_KEY,
                        resourceType = ResourceType.TENANT,
                        tenantId = tenant.tenantId,
                        permissions = setOf(Permission.READ),
                        revokedBy = "admin",
                    ),
                )

            assertEquals(apiKeyId, event.principalId)
            assertEquals(PrincipalType.API_KEY, event.principalType)
        }
}
