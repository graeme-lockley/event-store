package com.eventstore.domain.ports.outbound

import com.eventstore.domain.PermissionGrant
import java.util.UUID

/**
 * Repository interface for permission grants.
 * Used by PermissionProjectionService to store and retrieve permission grants.
 */
interface PermissionRepository {
    suspend fun save(grant: PermissionGrant)
    suspend fun findByPrincipal(principalId: String): List<PermissionGrant>
    suspend fun findByPrincipalAndTenant(principalId: String, tenantResourceId: UUID): List<PermissionGrant>
    suspend fun findAll(): List<PermissionGrant>
    suspend fun delete(grant: PermissionGrant)
}

