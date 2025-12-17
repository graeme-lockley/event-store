package com.eventstore.domain.services.namespace

import com.eventstore.Config
import com.eventstore.domain.EventId
import com.eventstore.domain.Namespace
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryNamespaceRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.NamespaceProjectionService
import com.eventstore.infrastructure.projections.TenantProjectionService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateNamespaceServiceTest {
    private lateinit var topicRepository: InMemoryTopicRepository
    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var tenantProjection: TenantProjectionService
    private lateinit var namespaceProjection: NamespaceProjectionService
    private lateinit var service: CreateNamespaceService
    private lateinit var config: Config

    @BeforeEach
    fun setup() {
        topicRepository = InMemoryTopicRepository()
        eventRepository = InMemoryEventRepository()
        tenantProjection = TenantProjectionService(InMemoryTenantRepository())
        namespaceProjection = NamespaceProjectionService(InMemoryNamespaceRepository())
        config = Config(0, "./data", "./config", 1024, 10, multiTenantEnabled = true, authEnabled = false)
        service = CreateNamespaceService(eventRepository, topicRepository, tenantProjection, namespaceProjection, config)

        runBlocking {
            // Seed system topics
            topicRepository.createTopic(SystemTopics.NAMESPACES_TOPIC, emptyList(), SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID)
            // Seed tenant in projection
            tenantProjection.handleEvents(
                listOf(
                    com.eventstore.domain.Event(
                        id = EventId.create(SystemTopics.TENANTS_TOPIC, 1, SystemTopics.SYSTEM_TENANT_ID, SystemTopics.MANAGEMENT_NAMESPACE_ID),
                        timestamp = java.time.Instant.now(),
                        type = com.eventstore.domain.events.TenantEventType.CREATED,
                        payload = com.eventstore.domain.events.TenantCreatedEvent("acme", "Acme", createdAt = java.time.Instant.now()).toPayload()
                    )
                )
            )
        }
    }

    @Test
    fun `creates namespace and emits event`() = runTest {
        val ns = service.execute(CreateNamespaceRequest("acme", "billing", "Billing"))
        assertEquals("billing", ns.id)
        val events = eventRepository.getEvents(SystemTopics.NAMESPACES_TOPIC, tenantId = SystemTopics.SYSTEM_TENANT_ID, namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID)
        assertEquals(1, events.size)
        assertEquals(NamespaceEventType.CREATED, events.first().type)
        assertTrue(events.first().id.isTenantScoped)
    }

    @Test
    fun `fails when tenant missing`() = runTest {
        val missingTenantService = CreateNamespaceService(eventRepository, topicRepository, tenantProjection, namespaceProjection, config)
        assertFailsWith<TenantNotFoundException> {
            missingTenantService.execute(CreateNamespaceRequest("unknown", "billing", "Billing"))
        }
    }

    @Test
    fun `fails when namespace exists`() = runTest {
        service.execute(CreateNamespaceRequest("acme", "billing", "Billing"))
        assertFailsWith<NamespaceAlreadyExistsException> {
            service.execute(CreateNamespaceRequest("acme", "billing", "Billing"))
        }
    }
}

