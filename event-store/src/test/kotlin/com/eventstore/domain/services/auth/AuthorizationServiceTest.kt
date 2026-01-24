package com.eventstore.domain.services.auth

import com.eventstore.domain.*
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizationServiceTest {
    private lateinit var permissionRepository: InMemoryPermissionRepository
    private lateinit var permissionProjectionService: PermissionProjectionService
    private lateinit var tenantProjectionService: TenantProjectionService
    private lateinit var namespaceProjectionService: NamespaceProjectionService
    private lateinit var topicRepository: InMemoryTopicRepository
    private lateinit var resourceResolver: ResourceResolverImpl
    private lateinit var authorizationService: AuthorizationService
    private lateinit var tenantResourceId: UUID
    private lateinit var tenantName: String
    private lateinit var principalId: String

    @BeforeEach
    fun setup() {
        runBlocking {
            permissionRepository = InMemoryPermissionRepository()
            permissionProjectionService = PermissionProjectionService(permissionRepository)
            tenantProjectionService = TenantProjectionService(InMemoryTenantRepository())
            namespaceProjectionService = NamespaceProjectionService(InMemoryNamespaceRepository(), tenantProjectionService)
            topicRepository = InMemoryTopicRepository()
            resourceResolver =
                ResourceResolverImpl(
                    tenantProjectionService = tenantProjectionService,
                    namespaceProjectionService = namespaceProjectionService,
                    topicRepository = topicRepository,
                )
            authorizationService =
                AuthorizationService(
                    permissionProjectionService = permissionProjectionService,
                    resourceResolver = resourceResolver,
                )

            // Create system topics
            topicRepository.createTopic(
                topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID,
                name = SystemTopics.PERMISSIONS_TOPIC_NAME,
                schemas = emptyList(),
            )
            topicRepository.createTopic(
                topicId = SystemTopics.TENANTS_TOPIC_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID,
                name = SystemTopics.TENANTS_TOPIC_NAME,
                schemas = emptyList(),
            )

            // Create a test tenant
            tenantName = "test-tenant"
            tenantResourceId = UUID.randomUUID()
            principalId = UUID.randomUUID().toString()

            val tenantEvent =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.TENANTS_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = Instant.now(),
                    type = TenantEventType.CREATED,
                    payload =
                        TenantCreatedEvent(
                            tenantId = tenantResourceId,
                            name = tenantName,
                            createdAt = Instant.now(),
                        ).toPayload(),
                )
            tenantProjectionService.handleEvents(listOf(tenantEvent))
        }
    }

    @Test
    fun `returns true when permission granted`() =
        runTest {
            // Grant permission
            val grantEvent =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = Instant.now(),
                    type = PermissionEventType.GRANTED,
                    payload =
                        PermissionGrantedEvent(
                            principalId = principalId,
                            principalType = PrincipalType.USER,
                            resourceType = ResourceType.TENANT,
                            resourceId = tenantResourceId.toString(),
                            tenantResourceId = tenantResourceId.toString(),
                            permissions = setOf(Permission.READ, Permission.UPDATE),
                            grantedBy = "admin",
                            grantedAt = Instant.now(),
                        ).toPayload(),
                )
            permissionProjectionService.handleEvents(listOf(grantEvent))

            val hasPermission =
                authorizationService.checkPermission(
                    principalId = principalId,
                    resourceType = ResourceType.TENANT,
                    resourceName = tenantName,
                    requiredPermission = Permission.READ,
                    tenantName = tenantName,
                )

            assertTrue(hasPermission)
        }

    @Test
    fun `returns false when permission not granted`() =
        runTest {
            val hasPermission =
                authorizationService.checkPermission(
                    principalId = principalId,
                    resourceType = ResourceType.TENANT,
                    resourceName = tenantName,
                    requiredPermission = Permission.READ,
                    tenantName = tenantName,
                )

            assertFalse(hasPermission)
        }

    @Test
    fun `returns true for ADMIN permission`() =
        runTest {
            // Grant ADMIN permission
            val grantEvent =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = Instant.now(),
                    type = PermissionEventType.GRANTED,
                    payload =
                        PermissionGrantedEvent(
                            principalId = principalId,
                            principalType = PrincipalType.USER,
                            resourceType = ResourceType.TENANT,
                            resourceId = tenantResourceId.toString(),
                            tenantResourceId = tenantResourceId.toString(),
                            permissions = setOf(Permission.ADMIN),
                            grantedBy = "admin",
                            grantedAt = Instant.now(),
                        ).toPayload(),
                )
            permissionProjectionService.handleEvents(listOf(grantEvent))

            // ADMIN should grant all permissions
            assertTrue(
                authorizationService.checkPermission(
                    principalId = principalId,
                    resourceType = ResourceType.TENANT,
                    resourceName = tenantName,
                    requiredPermission = Permission.READ,
                    tenantName = tenantName,
                ),
            )
            assertTrue(
                authorizationService.checkPermission(
                    principalId = principalId,
                    resourceType = ResourceType.TENANT,
                    resourceName = tenantName,
                    requiredPermission = Permission.DELETE,
                    tenantName = tenantName,
                ),
            )
        }

    @Test
    fun `returns true for global permission`() =
        runTest {
            // Grant permission for all tenants (resourceId = null)
            val grantEvent =
                Event(
                    id =
                        EventId.create(
                            topicId = SystemTopics.PERMISSIONS_TOPIC_ID,
                            sequence = 1,
                        ),
                    timestamp = Instant.now(),
                    type = PermissionEventType.GRANTED,
                    payload =
                        PermissionGrantedEvent(
                            principalId = principalId,
                            principalType = PrincipalType.USER,
                            resourceType = ResourceType.TENANT,
                            // null = all tenants
                            resourceId = null,
                            tenantResourceId = tenantResourceId.toString(),
                            permissions = setOf(Permission.READ),
                            grantedBy = "admin",
                            grantedAt = Instant.now(),
                        ).toPayload(),
                )
            permissionProjectionService.handleEvents(listOf(grantEvent))

            val hasPermission =
                authorizationService.checkPermission(
                    principalId = principalId,
                    resourceType = ResourceType.TENANT,
                    resourceName = tenantName,
                    requiredPermission = Permission.READ,
                    tenantName = tenantName,
                )

            assertTrue(hasPermission)
        }
}
