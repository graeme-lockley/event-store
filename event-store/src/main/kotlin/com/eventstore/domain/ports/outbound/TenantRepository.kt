package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Tenant
import java.util.UUID

/**
 * Storage port for tenant projection state.
 */
interface TenantRepository {
    suspend fun save(tenant: Tenant)
    suspend fun findByName(name: String): Tenant?
    suspend fun findByResourceId(resourceId: UUID): Tenant?
    suspend fun findAll(): List<Tenant>
    
    // Backward compatibility - deprecated
    @Deprecated("Use findByName instead", ReplaceWith("findByName(id)"))
    suspend fun findById(id: String): Tenant? = findByName(id)
}

