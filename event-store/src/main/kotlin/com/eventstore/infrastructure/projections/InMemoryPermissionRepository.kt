package com.eventstore.infrastructure.projections

import com.eventstore.domain.PermissionGrant
import com.eventstore.domain.ports.outbound.PermissionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryPermissionRepository : PermissionRepository {
    private val grants = mutableListOf<PermissionGrant>()
    private val mutex = Mutex()

    override suspend fun save(grant: PermissionGrant) {
        mutex.withLock {
            // Remove existing grant if it matches (to update)
            grants.removeAll { 
                it.principalId == grant.principalId &&
                it.resourceType == grant.resourceType &&
                it.resourceId == grant.resourceId &&
                it.tenantResourceId == grant.tenantResourceId &&
                it.namespaceResourceId == grant.namespaceResourceId &&
                it.topicResourceId == grant.topicResourceId
            }
            grants.add(grant)
        }
    }

    override suspend fun findByPrincipal(principalId: String): List<PermissionGrant> {
        return mutex.withLock {
            grants.filter { it.principalId == principalId }
        }
    }

    override suspend fun findByPrincipalAndTenant(principalId: String, tenantResourceId: UUID): List<PermissionGrant> {
        return mutex.withLock {
            grants.filter { 
                it.principalId == principalId && 
                it.tenantResourceId == tenantResourceId.toString() 
            }
        }
    }

    override suspend fun findAll(): List<PermissionGrant> {
        return mutex.withLock {
            grants.toList()
        }
    }

    override suspend fun delete(grant: PermissionGrant) {
        mutex.withLock {
            grants.removeAll { 
                it.principalId == grant.principalId &&
                it.resourceType == grant.resourceType &&
                it.resourceId == grant.resourceId &&
                it.tenantResourceId == grant.tenantResourceId &&
                it.namespaceResourceId == grant.namespaceResourceId &&
                it.topicResourceId == grant.topicResourceId &&
                it.permissions == grant.permissions &&
                it.grantedAt == grant.grantedAt
            }
        }
    }
}

