package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Tenant

/**
 * Storage port for tenant projection state.
 */
interface TenantRepository {
    suspend fun save(tenant: Tenant)
    suspend fun findById(id: String): Tenant?
    suspend fun findAll(): List<Tenant>
}

