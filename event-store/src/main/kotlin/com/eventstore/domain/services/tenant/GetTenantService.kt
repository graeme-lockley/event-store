package com.eventstore.domain.services.tenant

import com.eventstore.infrastructure.projections.TenantProjectionService

class GetTenantService(
    private val tenantProjectionService: TenantProjectionService
) {
    suspend fun getTenant(tenantId: String) = tenantProjectionService.getTenant(tenantId)

    suspend fun listTenants() = tenantProjectionService.getAllTenants()
}

