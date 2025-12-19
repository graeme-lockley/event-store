package com.eventstore.domain.services.tenant

import com.eventstore.infrastructure.projections.TenantProjectionService

class GetTenantService(
    private val tenantProjectionService: TenantProjectionService
) {
    suspend fun getTenant(tenantId: String) = tenantProjectionService.getTenantByName(tenantId)

    suspend fun listTenants() = tenantProjectionService.getAllTenants()
}

