package com.eventstore.infrastructure.projections

import com.eventstore.domain.Tenant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryTenantRepositoryTest {
    private lateinit var repository: InMemoryTenantRepository

    @BeforeEach
    fun setup() {
        repository = InMemoryTenantRepository()
    }

    @Test
    fun `save creates tenant in both name and resourceId indexes`() =
        runTest {
            val tenantId = UUID.randomUUID()
            val tenant =
                Tenant(
                    tenantId = tenantId,
                    name = "test-tenant",
                    createdAt = Instant.now(),
                )

            repository.save(tenant)

            assertNotNull(repository.findByName("test-tenant"))
            assertNotNull(repository.findByResourceId(tenantId))
            assertEquals(tenantId, repository.findByName("test-tenant")?.tenantId)
            assertEquals("test-tenant", repository.findByResourceId(tenantId)?.name)
        }

    @Test
    fun `findByResourceId returns tenant by UUID`() =
        runTest {
            val tenantId = UUID.randomUUID()
            val tenant =
                Tenant(
                    tenantId = tenantId,
                    name = "by-id-test",
                    createdAt = Instant.now(),
                )
            repository.save(tenant)

            val found = repository.findByResourceId(tenantId)

            assertNotNull(found)
            assertEquals(tenantId, found.tenantId)
            assertEquals("by-id-test", found.name)
        }

    @Test
    fun `findByResourceId returns null when tenant not found`() =
        runTest {
            val nonExistentId = UUID.randomUUID()
            assertNull(repository.findByResourceId(nonExistentId))
        }

    @Test
    fun `findAll returns all saved tenants`() =
        runTest {
            val tenant1 =
                Tenant(
                    tenantId = UUID.randomUUID(),
                    name = "tenant-1",
                    createdAt = Instant.now(),
                )
            val tenant2 =
                Tenant(
                    tenantId = UUID.randomUUID(),
                    name = "tenant-2",
                    createdAt = Instant.now(),
                )
            repository.save(tenant1)
            repository.save(tenant2)

            val all = repository.findAll()

            assertEquals(2, all.size)
            assertNotNull(all.find { it.tenantId == tenant1.tenantId })
            assertNotNull(all.find { it.tenantId == tenant2.tenantId })
        }
}
