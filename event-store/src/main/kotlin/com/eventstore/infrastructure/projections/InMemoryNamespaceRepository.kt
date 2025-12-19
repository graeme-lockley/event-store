package com.eventstore.infrastructure.projections

import com.eventstore.domain.Namespace
import com.eventstore.domain.ports.outbound.NamespaceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryNamespaceRepository : NamespaceRepository {
    private val mutex = Mutex()
    private val namespacesByName = mutableMapOf<String, Namespace>()
    private val namespacesByResourceId = mutableMapOf<Pair<UUID, UUID>, Namespace>()

    private fun nameKey(tenantName: String, name: String): String = "$tenantName/$name"
    private fun resourceIdKey(tenantResourceId: UUID, resourceId: UUID): Pair<UUID, UUID> = Pair(tenantResourceId, resourceId)

    override suspend fun save(namespace: Namespace) {
        mutex.withLock {
            // If updating an existing namespace and name changed, remove old name entry
            val existing = namespacesByResourceId[resourceIdKey(namespace.tenantResourceId, namespace.resourceId)]
            if (existing != null && (existing.tenantName != namespace.tenantName || existing.name != namespace.name)) {
                namespacesByName.remove(nameKey(existing.tenantName, existing.name))
            }
            namespacesByName[nameKey(namespace.tenantName, namespace.name)] = namespace
            namespacesByResourceId[resourceIdKey(namespace.tenantResourceId, namespace.resourceId)] = namespace
        }
    }

    override suspend fun findByName(tenantName: String, name: String): Namespace? {
        return mutex.withLock { namespacesByName[nameKey(tenantName, name)] }
    }

    override suspend fun findByResourceId(tenantResourceId: UUID, resourceId: UUID): Namespace? {
        return mutex.withLock { namespacesByResourceId[resourceIdKey(tenantResourceId, resourceId)] }
    }

    override suspend fun findAll(): List<Namespace> {
        return mutex.withLock { namespacesByName.values.toList() }
    }

    override suspend fun findById(tenantId: String, namespaceId: String): Namespace? {
        return findByName(tenantId, namespaceId)
    }
}

