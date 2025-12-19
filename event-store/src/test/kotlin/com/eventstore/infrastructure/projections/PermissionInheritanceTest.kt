package com.eventstore.infrastructure.projections

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.PermissionGrantedEvent
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Integration tests for permission inheritance.
 * Tests that permissions granted at tenant level are inherited by namespace and topic operations.
 */
class PermissionInheritanceTest {
    private lateinit var repository: InMemoryPermissionRepository
    private lateinit var service: PermissionProjectionService

    @BeforeEach
    fun setup() {
        repository = InMemoryPermissionRepository()
        service = PermissionProjectionService(repository)
    }

    @Test
    fun `tenant admin permission grants all tenant permissions`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val grantedAt = Instant.now()

        // Grant ADMIN permission at tenant level
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
                resourceId = tenantResourceId.toString(),
                tenantResourceId = tenantResourceId.toString(),
                permissions = setOf(Permission.ADMIN),
                grantedBy = "admin",
                grantedAt = grantedAt
            ).toPayload()
        )

        service.handleEvents(listOf(event))

        // ADMIN permission should grant all tenant-level permissions
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = tenantResourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = tenantResourceId,
                permission = Permission.UPDATE,
                tenantResourceId = tenantResourceId
            )
        )
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = tenantResourceId,
                permission = Permission.DELETE,
                tenantResourceId = tenantResourceId
            )
        )
    }

    @Test
    fun `global tenant permission applies to all tenants`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val tenantResourceId = UUID.randomUUID()
        val otherTenantResourceId = UUID.randomUUID()
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

        // Should have permission for any tenant
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = tenantResourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
        assertTrue(
            service.hasPermission(
                principalId = principalId,
                resourceType = ResourceType.TENANT,
                resourceId = otherTenantResourceId,
                permission = Permission.READ,
                tenantResourceId = tenantResourceId
            )
        )
    }
}

