package com.eventstore.domain.services.tenant

import com.eventstore.Config
import com.eventstore.domain.EventId
import com.eventstore.domain.Quota
import com.eventstore.domain.Tenant
import com.eventstore.domain.events.TenantEventType
import com.eventstore.domain.exceptions.TenantAlreadyExistsException
import com.eventstore.infrastructure.persistence.InMemoryEventRepository
import com.eventstore.infrastructure.persistence.InMemoryTopicRepository
import com.eventstore.infrastructure.projections.InMemoryTenantRepository
import com.eventstore.infrastructure.projections.TenantProjectionService
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CreateTenantServiceTest {

    private lateinit var topicRepository: InMemoryTopicRepository
    private lateinit var eventRepository: InMemoryEventRepository
    private lateinit var projectionService: TenantProjectionService
    private lateinit var service: CreateTenantService
    private lateinit var config: Config

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup(@TempDir tempDir: java.nio.file.Path) {
        topicRepository = InMemoryTopicRepository()
        eventRepository = InMemoryEventRepository()
        val tenantRepo = InMemoryTenantRepository()
        projectionService = TenantProjectionService(tenantRepo)
        config = Config(
            port = 0,
            dataDir = "./data",
            configDir = "./config",
            maxBodyBytes = 1024,
            rateLimitPerMinute = 10,
            multiTenantEnabled = true,
            authEnabled = false
        )
        service = CreateTenantService(eventRepository, topicRepository, projectionService, config)

        runBlocking {
            topicRepository.createTopic(
                resourceId = java.util.UUID.randomUUID(),
                tenantResourceId = java.util.UUID.randomUUID(),
                namespaceResourceId = java.util.UUID.randomUUID(),
                name = SystemTopics.TENANTS_TOPIC,
                schemas = emptyList(),
                tenantName = SystemTopics.SYSTEM_TENANT_ID,
                namespaceName = SystemTopics.MANAGEMENT_NAMESPACE_ID
            )
        }
    }

    @Test
    fun `creates tenant and emits event`() = runTest {
        val quota = Quota(maxTopics = 10, maxNamespaces = 5, maxEventsPerDay = 1000, maxConsumers = 2, maxUsers = 3, maxEventSizeBytes = 512)
        val tenant = service.execute(
            CreateTenantRequest(
                name = "acme",
                quota = quota,
                metadata = mapOf("plan" to "pro")
            )
        )

        assertEquals("acme", tenant.name)
        val events = eventRepository.getEvents(
            SystemTopics.TENANTS_TOPIC,
            tenantId = SystemTopics.SYSTEM_TENANT_ID,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
        )
        assertEquals(1, events.size)
        assertEquals(TenantEventType.CREATED, events.first().type)
        assertTrue(events.first().id.isTenantScoped)
    }

    @Test
    fun `throws when multi tenant disabled`() = runTest {
        val disabledService = CreateTenantService(
            eventRepository,
            topicRepository,
            projectionService,
            config.copy(multiTenantEnabled = false)
        )

        assertFailsWith<IllegalStateException> {
            disabledService.execute(CreateTenantRequest(name = "acme"))
        }
    }

    @Test
    fun `throws when tenant already exists`() = runTest {
        val resourceId = java.util.UUID.randomUUID()
        val createdAt = java.time.Instant.now()
        projectionService.handleEvents(
            listOf(
                com.eventstore.domain.Event(
                    id = EventId.create(
                        topic = SystemTopics.TENANTS_TOPIC,
                        sequence = 1,
                        tenantId = SystemTopics.SYSTEM_TENANT_ID,
                        namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_ID
                    ),
                    timestamp = createdAt,
                    type = TenantEventType.CREATED,
                    payload = mapOf(
                        "resourceId" to resourceId.toString(),
                        "name" to "acme",
                        "createdAt" to createdAt.toString(),
                        "createdBy" to "test",
                        "metadata" to emptyMap<String, Any>()
                    )
                )
            )
        )

        assertFailsWith<TenantAlreadyExistsException> {
            service.execute(CreateTenantRequest(name = "acme"))
        }
    }
}

