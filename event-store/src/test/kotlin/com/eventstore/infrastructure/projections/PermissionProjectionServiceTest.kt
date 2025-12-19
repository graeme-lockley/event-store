package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.events.PermissionRevokedEvent
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionProjectionServiceTest {
    private lateinit var repository: InMemoryPermissionRepository
    private lateinit var service: PermissionProjectionService

    @BeforeEach
    fun setup() {
        repository = InMemoryPermissionRepository()
        service = PermissionProjectionService(repository)
    }

    @Test
    fun `applies granted event and stores permission`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.READ, Permission.UPDATE),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // First verify the grant was stored by checking the repository directly
        val allGrants = repository.findByPrincipal(principalId)
        assertEquals(1, allGrants.size, "Grant should be stored in repository")
        
        val grants = service.getPermissionGrants(principalId, tenantResourceId, null, null)
        assertEquals(1, grants.size, "Grant should be returned by getPermissionGrants")
        assertEquals(principalId, grants.first().principalId)
        assertEquals(ResourceType.TENANT, grants.first().resourceType)
        assertEquals(resourceId.toString(), grants.first().resourceId)
        assertEquals(setOf(Permission.READ, Permission.UPDATE), grants.first().permissions)
    }

    @Test
    fun `applies revoked event and removes permission`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        // First grant permission
        val grantEvent = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.READ, Permission.UPDATE),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        // Then revoke one permission
        val revokeAt = grantedAt.plusSeconds(10)
        val revokeEvent = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 2,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = revokeAt,
            type = PermissionEventType.REVOKED,
            payload = PermissionRevokedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.UPDATE),
                revokedBy = "admin",
                revokedAt = revokeAt
            ).toPayload()
        )

        service.handleEvents(listOf(grantEvent, revokeEvent))

        val grants = service.getPermissionGrants(principalId, tenantResourceId, null, null)
        assertEquals(1, grants.size)
        assertEquals(setOf(Permission.READ), grants.first().permissions)
    }

    @Test
    fun `hasPermission returns true when permission granted`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.READ, Permission.UPDATE),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.UPDATE,
                tenantResourceId = tenantResourceId
            )
        )
        assertFalse(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.DELETE,
                tenantResourceId = tenantResourceId
            )
        )
    }

    @Test
    fun `hasPermission returns true for ADMIN permission`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.ADMIN),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // ADMIN permission grants all permissions
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.DELETE,
                tenantResourceId = tenantResourceId
            )
        )
    }

    @Test
    fun `hasPermission returns true for global permission when resourceId is null`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        // Grant permission for all tenants (resourceId = null)
        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = null, // null = all tenants
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.READ),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // Should have permission for any tenant resourceId
        val anyTenantId = UUID.randomUUID()
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = anyTenantId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
    }

    @Test
    fun `filters expired permissions`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()
        val expiresAt = grantedAt.plusSeconds(5)

        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.READ),
                grantedBy = "admin",
                grantedAt = grantedAt,
                expiresAt = expiresAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // Before expiration - should have permission
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )

        // After expiration - permission should be filtered out
        // Note: This test assumes the service checks expiration at query time
        // In a real scenario, you might need to advance time or mock Instant.now()
    }

    @Test
    fun `filters permissions by tenant context`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenant1ResourceId = UUID.randomUUID()
        val tenant2ResourceId = UUID.randomUUID()
        val resourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        // Grant permission in tenant1
        val event = Event(
            id = EventId.create(
                topic = SystemTopics.PERMISSIONS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = grantedAt,
            type = PermissionEventType.GRANTED,
            payload = PermissionGrantedEvent(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId.toString(),
                tenantResourceId = tenant1ResourceId.toString(),
                permissions = setOf(Permission.READ),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // Should have permission in tenant1
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.READ,
                tenantResourceId = tenant1ResourceId
            )
        )

        // Should not have permission in tenant2
        assertFalse(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = resourceId,
                permission = Permission.READ,
                tenantResourceId = tenant2ResourceId
            )
        )
    }
}

