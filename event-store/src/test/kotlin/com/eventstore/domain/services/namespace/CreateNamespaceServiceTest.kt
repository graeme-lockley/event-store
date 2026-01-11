package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.TenantNotFoundException
import com.eventstore.domain.services.createApplication
import com.eventstore.domain.tenants.SystemTopics
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateNamespaceServiceTest {
    private lateinit var application: Application

    @OptIn(ExperimentalPathApi::class)
    @BeforeEach
    fun setup() = runTest {
        application = createApplication()
    }

    @Test
    fun `creates namespace and emits event`() = runTest {
        // Create tenant first
        application.createTenant("acme")
        val numberOfEvents = numberOfEvents()

        val namespace = application.createNamespace("acme", "billing")

        assertEquals("billing", namespace.name)
        assertEquals("acme", namespace.tenantName)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        assertEquals(NamespaceEventType.CREATED, events.last().type)
        // All EventIds are now tenant-scoped
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, events.last().id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, events.last().id.namespaceId)
    }

    @Test
    fun `fails when tenant missing`() = runTest {
        assertFailsWith<TenantNotFoundException> {
            application.createNamespace("unknown", "billing")
        }
    }

    @Test
    fun `fails when namespace exists`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")

        assertFailsWith<NamespaceAlreadyExistsException> {
            application.createNamespace("acme", "billing")
        }
    }

    @Test
    fun `creates namespace with description`() = runTest {
        application.createTenant("acme")
        val namespace = application.createNamespace(
            tenantName = "acme",
            namespaceName = "billing",
            description = "Billing namespace"
        )

        assertEquals("billing", namespace.name)
        assertEquals("Billing namespace", namespace.description)
    }

    @Test
    fun `creates namespace with metadata`() = runTest {
        application.createTenant("acme")
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        val namespace = application.createNamespace(
            tenantName = "acme",
            namespaceName = "billing",
            metadata = metadata
        )

        assertEquals(metadata, namespace.metadata)
    }

    @Test
    fun `creates namespace with default name`() = runTest {
        application.createTenant("acme")
        val namespace = application.createNamespace("acme")

        assertEquals("default", namespace.name)
        assertEquals("acme", namespace.tenantName)
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        application.createTenant("acme")
        application.createNamespace("acme", "billing")

        val event = getEvents().last()
        assertEquals(SystemTopics.SYSTEM_TENANT_NAME, event.id.tenantId)
        assertEquals(SystemTopics.MANAGEMENT_NAMESPACE_NAME, event.id.namespaceId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        application.createTenant("acme")

        // Create first namespace
        application.createNamespace("acme", "namespace-1")
        val sequence1 = getEvents().last().id.sequence

        // Create second namespace
        application.createNamespace("acme", "namespace-2")
        val sequence2 = getEvents().last().id.sequence

        // Verify sequence was incremented
        assertEquals(sequence1 + 1, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        application.createTenant("acme")
        val beforeCreation = java.time.Instant.now()
        val namespace = application.createNamespace("acme", "timestamp-test")
        val afterCreation = java.time.Instant.now()

        val event = getEvents().last()
        assertTrue(event.timestamp.isAfter(beforeCreation) || event.timestamp == beforeCreation)
        assertTrue(event.timestamp.isBefore(afterCreation) || event.timestamp == afterCreation)
        assertEquals(namespace.createdAt, event.timestamp)
    }

    @Test
    fun `can create multiple namespaces in same tenant`() = runTest {
        application.createTenant("acme")
        val initialEventCount = numberOfEvents()

        val ns1 = application.createNamespace("acme", "namespace-1")
        val ns2 = application.createNamespace("acme", "namespace-2")
        val ns3 = application.createNamespace("acme", "namespace-3")

        assertEquals("namespace-1", ns1.name)
        assertEquals("namespace-2", ns2.name)
        assertEquals("namespace-3", ns3.name)
        assertEquals("acme", ns1.tenantName)
        assertEquals("acme", ns2.tenantName)
        assertEquals("acme", ns3.tenantName)

        val events = getEvents()
        val createdEvents = events.filter { it.type == NamespaceEventType.CREATED }
        // Bootstrap creates $management namespace, so we should have initial + 3
        assertEquals(initialEventCount + 3, createdEvents.size)
    }

    @Test
    fun `can create namespaces in different tenants`() = runTest {
        application.createTenant("acme")
        application.createTenant("corp")

        val acmeNs = application.createNamespace("acme", "billing")
        val corpNs = application.createNamespace("corp", "billing")

        assertEquals("acme", acmeNs.tenantName)
        assertEquals("corp", corpNs.tenantName)
        assertEquals("billing", acmeNs.name)
        assertEquals("billing", corpNs.name)
        // Namespaces can have the same name in different tenants
    }

    @Test
    fun `namespace is added to projection`() = runTest {
        application.createTenant("acme")
        val namespace = application.createNamespace("acme", "billing")

        // Verify namespace exists in projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace, "Namespace should exist in projection")
        assertEquals(namespace.name, projectionNamespace.name)
        assertEquals(namespace.tenantName, projectionNamespace.tenantName)
        assertEquals(namespace.resourceId, projectionNamespace.resourceId)
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            SystemTopics.NAMESPACES_TOPIC_NAME,
            tenantId = SystemTopics.SYSTEM_TENANT_NAME,
            namespaceId = SystemTopics.MANAGEMENT_NAMESPACE_NAME
        )
}
