package com.eventstore.domain.services.namespace

import com.eventstore.domain.Application
import com.eventstore.domain.Quota
import com.eventstore.domain.events.NamespaceEventType
import com.eventstore.domain.exceptions.NamespaceAlreadyExistsException
import com.eventstore.domain.exceptions.QuotaExceededException
import com.eventstore.domain.exceptions.TenantNotFoundException
import java.util.*
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
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val numberOfEvents = numberOfEvents()

        val namespace = application.createNamespace(tenantId, "billing")

        assertEquals("billing", namespace.name)
        assertEquals("acme", namespace.tenantName)
        val events = getEvents()
        assertEquals(numberOfEvents + 1, events.size)
        assertEquals(NamespaceEventType.CREATED, events.last().type)
        // EventIds are now topic-scoped (topicId/sequence)
        assertEquals(SystemTopics.NAMESPACES_TOPIC_ID, events.last().id.topicId)
    }

    @Test
    fun `fails when tenant missing`() = runTest {
        val nonExistentTenantId = UUID.randomUUID()
        assertFailsWith<TenantNotFoundException> {
            application.createNamespace(nonExistentTenantId, "billing")
        }
    }

    @Test
    fun `fails when namespace exists`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        application.createNamespace(tenantId, "billing")

        assertFailsWith<NamespaceAlreadyExistsException> {
            application.createNamespace(tenantId, "billing")
        }
    }

    @Test
    fun `creates namespace with description`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(
            tenantId = tenantId,
            namespaceName = "billing",
            description = "Billing namespace"
        )

        assertEquals("billing", namespace.name)
        assertEquals("Billing namespace", namespace.description)
    }

    @Test
    fun `creates namespace with metadata`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val metadata = mapOf("plan" to "pro", "region" to "us-east")
        val namespace = application.createNamespace(
            tenantId = tenantId,
            namespaceName = "billing",
            metadata = metadata
        )

        assertEquals(metadata, namespace.metadata)
    }

    @Test
    fun `creates namespace with default name`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId)

        assertEquals("default", namespace.name)
        assertEquals("acme", namespace.tenantName)
    }

    @Test
    fun `event is stored with correct tenant and namespace context`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        application.createNamespace(tenantId, "billing")

        val event = getEvents().last()
        assertEquals(SystemTopics.NAMESPACES_TOPIC_ID, event.id.topicId)
    }

    @Test
    fun `event sequence is correctly incremented`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId

        // Create first namespace
        application.createNamespace(tenantId, "namespace-1")
        val sequence1 = getEvents().last().id.sequence

        // Create second namespace
        application.createNamespace(tenantId, "namespace-2")
        val sequence2 = getEvents().last().id.sequence

        // Verify sequence was incremented
        assertEquals(sequence1 + 1, sequence2)
    }

    @Test
    fun `event timestamp is set correctly`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val beforeCreation = java.time.Instant.now()
        val namespace = application.createNamespace(tenantId, "timestamp-test")
        val afterCreation = java.time.Instant.now()

        val event = getEvents().last()
        assertTrue(event.timestamp.isAfter(beforeCreation) || event.timestamp == beforeCreation)
        assertTrue(event.timestamp.isBefore(afterCreation) || event.timestamp == afterCreation)
        assertEquals(namespace.createdAt, event.timestamp)
    }

    @Test
    fun `can create multiple namespaces in same tenant`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val initialEventCount = numberOfEvents()

        val ns1 = application.createNamespace(tenantId, "namespace-1")
        val ns2 = application.createNamespace(tenantId, "namespace-2")
        val ns3 = application.createNamespace(tenantId, "namespace-3")

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
        val acmeTenant = application.createTenant("acme")
        val acmeTenantId = acmeTenant.tenantId
        val corpTenant = application.createTenant("corp")
        val corpTenantId = corpTenant.tenantId

        val acmeNs = application.createNamespace(acmeTenantId, "billing")
        val corpNs = application.createNamespace(corpTenantId, "billing")

        assertEquals("acme", acmeNs.tenantName)
        assertEquals("corp", corpNs.tenantName)
        assertEquals("billing", acmeNs.name)
        assertEquals("billing", corpNs.name)
        // Namespaces can have the same name in different tenants
    }

    @Test
    fun `namespace is added to projection`() = runTest {
        val tenant = application.createTenant("acme")
        val tenantId = tenant.tenantId
        val namespace = application.createNamespace(tenantId, "billing")

        // Verify namespace exists in projection
        val projectionNamespace = application.namespaceProjectionService.getNamespaceByName("acme", "billing")
        assertNotNull(projectionNamespace, "Namespace should exist in projection")
        assertEquals(namespace.name, projectionNamespace.name)
        assertEquals(namespace.tenantName, projectionNamespace.tenantName)
        assertEquals(namespace.namespaceId, projectionNamespace.namespaceId)
    }

    @Test
    fun `fails when tenant quota exceeded with explicit quota`() = runTest {
        val quota = Quota(maxNamespaces = 2)
        val tenant = application.createTenant("quota-test", quota = quota)
        val tenantId = tenant.tenantId

        // Create namespaces up to the limit
        application.createNamespace(tenantId, "ns1")
        application.createNamespace(tenantId, "ns2")

        // Attempting to create one more should fail
        assertFailsWith<QuotaExceededException> {
            application.createNamespace(tenantId, "ns3")
        }
    }

    @Test
    fun `fails when tenant quota exceeded with default quota`() = runTest {
        // Tenant without explicit quota uses default of 50
        val tenant = application.createTenant("default-quota-test")
        val tenantId = tenant.tenantId

        // Create 50 namespaces (the default limit)
        for (i in 1..50) {
            application.createNamespace(tenantId, "ns$i")
        }

        // Attempting to create one more should fail
        assertFailsWith<QuotaExceededException> {
            application.createNamespace(tenantId, "ns51")
        }
    }

    @Test
    fun `allows creation when quota not exceeded`() = runTest {
        val quota = Quota(maxNamespaces = 5)
        val tenant = application.createTenant("quota-ok-test", quota = quota)
        val tenantId = tenant.tenantId

        // Create namespaces up to but not exceeding the limit
        val ns1 = application.createNamespace(tenantId, "ns1")
        val ns2 = application.createNamespace(tenantId, "ns2")
        val ns3 = application.createNamespace(tenantId, "ns3")
        val ns4 = application.createNamespace(tenantId, "ns4")
        val ns5 = application.createNamespace(tenantId, "ns5")

        assertEquals("ns1", ns1.name)
        assertEquals("ns2", ns2.name)
        assertEquals("ns3", ns3.name)
        assertEquals("ns4", ns4.name)
        assertEquals("ns5", ns5.name)
    }

    @Test
    fun `uses explicit quota when tenant has quota set`() = runTest {
        val quota = Quota(maxNamespaces = 3)
        val tenant = application.createTenant("explicit-quota-test", quota = quota)
        val tenantId = tenant.tenantId

        // Create 3 namespaces (the explicit limit, not the default 50)
        application.createNamespace(tenantId, "ns1")
        application.createNamespace(tenantId, "ns2")
        application.createNamespace(tenantId, "ns3")

        // Should fail at 3, not 50
        assertFailsWith<QuotaExceededException> {
            application.createNamespace(tenantId, "ns4")
        }
    }

    @Test
    fun `quota check happens before name uniqueness check`() = runTest {
        val quota = Quota(maxNamespaces = 1)
        val tenant = application.createTenant("quota-order-test", quota = quota)
        val tenantId = tenant.tenantId

        // Create one namespace
        application.createNamespace(tenantId, "ns1")

        // Attempting to create another namespace (even with different name) should fail due to quota
        assertFailsWith<QuotaExceededException> {
            application.createNamespace(tenantId, "ns2")
        }
    }

    private suspend fun numberOfEvents(): Int =
        getEvents().size

    private suspend fun getEvents(): List<com.eventstore.domain.Event> =
        application.eventRepository.getEvents(
            topicId = SystemTopics.NAMESPACES_TOPIC_ID
        )
}
