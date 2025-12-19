package com.eventstore.infrastructure.projections

import com.eventstore.domain.Tenant
import com.eventstore.domain.ports.outbound.TenantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryTenantRepository : TenantRepository {
    private val mutex = Mutex()
    private val tenantsByName = mutableMapOf<String, Tenant>()
    private val tenantsByResourceId = mutableMapOf<UUID, Tenant>()

    override suspend fun save(tenant: Tenant) {
        mutex.withLock {
            // If updating an existing tenant and name changed, remove old name entry
            val existing = tenantsByResourceId[tenant.resourceId]
            if (existing != null && existing.name != tenant.name) {
                tenantsByName.remove(existing.name)
            }
            tenantsByName[tenant.name] = tenant
            tenantsByResourceId[tenant.resourceId] = tenant
        }
    }

    override suspend fun findByName(name: String): Tenant? {
        return mutex.withLock {
            tenantsByName[name]
        }
    }

    override suspend fun findByResourceId(resourceId: UUID): Tenant? {
        return mutex.withLock {
            tenantsByResourceId[resourceId]
        }
    }

    override suspend fun findAll(): List<Tenant> {
        return mutex.withLock {
            tenantsByName.values.toList()
        }
    }

    override suspend fun findById(id: String): Tenant? {
        return findByName(id)
    }
}

