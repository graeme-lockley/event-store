package com.eventstore.infrastructure.projections

import com.eventstore.domain.Namespace
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryNamespaceRepositoryTest {
    private lateinit var repository: InMemoryNamespaceRepository

    @BeforeEach
    fun setup() {
        repository = InMemoryNamespaceRepository()
    }

    @Test
    fun `save creates namespace in all indexes`() = runTest {
        val tenantId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val namespace = Namespace(
            namespaceId = namespaceId,
            tenantId = tenantId,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )

        repository.save(namespace)

        assertNotNull(repository.findByName("acme", "billing"))
        assertNotNull(repository.findById(tenantId, namespaceId))
        assertNotNull(repository.findById(namespaceId))
    }

    @Test
    fun `findById returns namespace by tenantId and namespaceId`() = runTest {
        val tenantId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val namespace = Namespace(
            namespaceId = namespaceId,
            tenantId = tenantId,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )
        repository.save(namespace)

        val found = repository.findById(tenantId, namespaceId)

        assertNotNull(found)
        assertEquals(namespaceId, found.namespaceId)
    }

    @Test
    fun `findById returns namespace by namespaceId only`() = runTest {
        val tenantId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val namespace = Namespace(
            namespaceId = namespaceId,
            tenantId = tenantId,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )
        repository.save(namespace)

        val found = repository.findById(namespaceId)

        assertNotNull(found)
        assertEquals(namespaceId, found.namespaceId)
        assertEquals(tenantId, found.tenantId)
    }

    @Test
    fun `findById returns null when namespace not found`() = runTest {
        val nonExistentId = UUID.randomUUID()
        assertNull(repository.findById(nonExistentId))
    }

    @Test
    fun `findByName returns namespace by tenant and namespace name`() = runTest {
        val tenantId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val namespace = Namespace(
            namespaceId = namespaceId,
            tenantId = tenantId,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )
        repository.save(namespace)

        val found = repository.findByName("acme", "billing")

        assertNotNull(found)
        assertEquals("billing", found.name)
    }

    @Test
    fun `save updates existing namespace and maintains index consistency`() = runTest {
        val tenantId = UUID.randomUUID()
        val namespaceId = UUID.randomUUID()
        val original = Namespace(
            namespaceId = namespaceId,
            tenantId = tenantId,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )
        repository.save(original)

        val updated = original.copy(
            name = "billing-v2",
            updatedAt = Instant.now()
        )
        repository.save(updated)

        assertNull(repository.findByName("acme", "billing"))
        assertNotNull(repository.findByName("acme", "billing-v2"))
        assertNotNull(repository.findById(namespaceId))
    }

    @Test
    fun `findAll returns all saved namespaces`() = runTest {
        val tenantId1 = UUID.randomUUID()
        val tenantId2 = UUID.randomUUID()
        val namespace1 = Namespace(
            namespaceId = UUID.randomUUID(),
            tenantId = tenantId1,
            tenantName = "acme",
            name = "billing",
            createdAt = Instant.now()
        )
        val namespace2 = Namespace(
            namespaceId = UUID.randomUUID(),
            tenantId = tenantId2,
            tenantName = "corp",
            name = "sales",
            createdAt = Instant.now()
        )
        repository.save(namespace1)
        repository.save(namespace2)

        val all = repository.findAll()

        assertEquals(2, all.size)
    }
}
