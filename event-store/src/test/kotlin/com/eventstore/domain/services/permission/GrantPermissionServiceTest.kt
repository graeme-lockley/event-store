package com.eventstore.domain.services.permission

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.Permission
import com.eventstore.domain.PrincipalType
import com.eventstore.domain.ResourceType
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.PermissionEventType
import com.eventstore.domain.events.TenantCreatedEvent
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.services.auth.ResourceResolverImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GrantPermissionServiceTest {
    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var topicRepository: InMemoryTopicRepository
    private lateinit var tenantProjectionService: TenantProjectionService
    private lateinit var namespaceProjectionService: NamespaceProjectionService
    private lateinit var resourceResolver: ResourceResolverImpl
    private lateinit var service: GrantPermissionService
    private lateinit var tenantResourceId: UUID
    private lateinit var tenantName: String

    @BeforeEach
    fun setup() {
        runBlocking {
        eventRepository = InMemoryEventRepository()
        topicRepository = InMemoryTopicRepository()
        tenantProjectionService = TenantProjectionService(InMemoryTenantRepository())
        namespaceProjectionService = NamespaceProjectionService(InMemoryNamespaceRepository())
        resourceResolver = ResourceResolverImpl(
            tenantProjectionService = tenantProjectionService,
            namespaceProjectionService = namespaceProjectionService,
            topicRepository = topicRepository
        )
        service = GrantPermissionService(
            eventRepository = eventRepository,
            topicRepository = topicRepository,
            resourceResolver = resourceResolver,
            tenantProjectionService = tenantProjectionService,
            namespaceProjectionService = namespaceProjectionService
        )

        // Create system topics
        topicRepository.createTopic(
            resourceId = UUID.randomUUID(),
            tenantResourceId = UUID.randomUUID(),
            namespaceResourceId = UUID.randomUUID(),
            name = SystemTopics.PERMISSIONS_TOPIC,
            schemas = emptyList(),
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        topicRepository.createTopic(
            resourceId = UUID.randomUUID(),
            tenantResourceId = UUID.randomUUID(),
            namespaceResourceId = UUID.randomUUID(),
            name = SystemTopics.TENANTS_TOPIC,
            schemas = emptyList(),
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        // Create a test tenant
        tenantName = "test-tenant"
        tenantResourceId = UUID.randomUUID()
        val tenantEvent = Event(
            id = EventId.create(
                topic = SystemTopics.TENANTS_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = Instant.now(),
            type = TenantEventType.CREATED,
            payload = TenantCreatedEvent(
                resourceId = tenantResourceId,
                name = tenantName,
                createdAt = Instant.now()
            ).toPayload()
        )
        tenantProjectionService.handleEvents(listOf(tenantEvent))
        }
    }

    @Test
    fun `grants permission and emits event`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val permissions = setOf(Permission.READ, Permission.UPDATE)

        val event = service.execute(
            GrantPermissionRequest(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.TENANT,
                tenantName = tenantName,
                permissions = permissions,
                grantedBy = "admin"
            )
        )

        assertEquals(principalId, event.principalId)
        assertEquals(PrincipalType.USER, event.principalType)
        assertEquals(ResourceType.TENANT, event.resourceType)
        assertEquals(tenantResourceId.toString(), event.tenantResourceId)
        assertEquals(permissions, event.permissions)

        // Verify event was stored
        val storedEvents = eventRepository.getEvents(
            topic = SystemTopics.PERMISSIONS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, storedEvents.size)
        assertEquals(PermissionEventType.GRANTED, storedEvents.first().type)
    }

    @Test
    fun `grants permission for specific resource`() = runTest {
        val principalId = UUID.randomUUID().toString()
        val resourceId = UUID.randomUUID()

        val event = service.execute(
            GrantPermissionRequest(
                principalId = principalId,
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
        // Create a namespace first
        val namespaceName = "test-namespace"
        val namespaceResourceId = UUID.randomUUID()
        
        topicRepository.createTopic(
            resourceId = UUID.randomUUID(),
            tenantResourceId = UUID.randomUUID(),
            namespaceResourceId = UUID.randomUUID(),
            name = SystemTopics.NAMESPACES_TOPIC,
            schemas = emptyList(),
            tenantName = SystemTopics.SYSTEM_TENANT_ID,
            namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )

        val namespaceEvent = Event(
            id = EventId.create(
                topic = SystemTopics.NAMESPACES_TOPIC,
                sequence = 1,
                tenantId = SystemTopics.SYSTEM_TENANT_ID,
                namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
            ),
            timestamp = Instant.now(),
            type = com.eventstore.domain.events.NamespaceEventType.CREATED,
            payload = com.eventstore.domain.events.NamespaceCreatedEvent(
                resourceId = namespaceResourceId,
                tenantResourceId = tenantResourceId,
                tenantName = tenantName,
                name = namespaceName,
                createdAt = Instant.now()
            ).toPayload()
        )
        namespaceProjectionService.handleEvents(listOf(namespaceEvent))

        val principalId = UUID.randomUUID().toString()

        val event = service.execute(
            GrantPermissionRequest(
                principalId = principalId,
                principalType = PrincipalType.USER,
                resourceType = ResourceType.NAMESPACE,
                tenantName = tenantName,
                namespaceName = namespaceName,
                permissions = setOf(Permission.READ),
                grantedBy = "admin"
            )
        )

        assertEquals(ResourceType.NAMESPACE, event.resourceType)
        assertEquals(namespaceResourceId.toString(), event.namespaceResourceId)
    }
}

