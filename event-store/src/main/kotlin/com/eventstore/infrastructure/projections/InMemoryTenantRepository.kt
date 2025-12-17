package com.eventstore.infrastructure.projections

import com.eventstore.domain.Tenant
import com.eventstore.domain.ports.outbound.TenantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTenantRepository : TenantRepository {
    private val mutex = Mutex()
    private val tenants = mutableMapOf<String, Tenant>()

    override suspend fun save(tenant: Tenant) {
        mutex.withLock {
            tenants[tenant.id] = tenant
        }
    }

    override suspend fun findById(id: String): Tenant? {
        return mutex.withLock {
            tenants[id]
        }
    }

    override suspend fun findAll(): List<Tenant> {
        return mutex.withLock {
            tenants.values.toList()
        }
    }
}

