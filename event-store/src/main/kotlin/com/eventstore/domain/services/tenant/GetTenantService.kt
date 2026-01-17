package com.eventstore.domain.services.tenant

import com.eventstore.domain.Tenant
import com.eventstore.infrastructure.projections.TenantProjectionService
import java.util.UUID

class GetTenantService(
    private val tenantProjectionService: TenantProjectionService
) {
    suspend fun getTenant(tenantId: UUID): Tenant? = tenantProjectionService.getTenantById(tenantId)

    suspend fun getTenantByName(name: String): Tenant? = tenantProjectionService.getTenantByName(name)

    suspend fun listTenants(): List<Tenant> = tenantProjectionService.getAllTenants()
}

