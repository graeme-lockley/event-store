package com.eventstore.infrastructure.projections

import com.eventstore.domain.Namespace
import com.eventstore.domain.ports.outbound.NamespaceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class InMemoryNamespaceRepository : NamespaceRepository {
    private val mutex = Mutex()
    private val namespacesByName = mutableMapOf<String, Namespace>()
    private val namespacesById = mutableMapOf<Pair<UUID, UUID>, Namespace>()
    private val namespacesByNamespaceId = mutableMapOf<UUID, Namespace>()

    private fun nameKey(
        tenantName: String,
        name: String,
    ): String = "$tenantName/$name"

    private fun idKey(
        tenantId: UUID,
        namespaceId: UUID,
    ): Pair<UUID, UUID> = Pair(tenantId, namespaceId)

    override suspend fun save(namespace: Namespace) {
        mutex.withLock {
            // If updating an existing namespace and name changed, remove old name entry
            val existing = namespacesById[idKey(namespace.tenantId, namespace.namespaceId)]
            if (existing != null && (existing.tenantName != namespace.tenantName || existing.name != namespace.name)) {
                namespacesByName.remove(nameKey(existing.tenantName, existing.name))
            }
            namespacesByName[nameKey(namespace.tenantName, namespace.name)] = namespace
            namespacesById[idKey(namespace.tenantId, namespace.namespaceId)] = namespace
            namespacesByNamespaceId[namespace.namespaceId] = namespace
        }
    }

    override suspend fun findByName(
        tenantName: String,
        name: String,
    ): Namespace? {
        return mutex.withLock { namespacesByName[nameKey(tenantName, name)] }
    }

    override suspend fun findById(
        tenantId: UUID,
        namespaceId: UUID,
    ): Namespace? {
        return mutex.withLock { namespacesById[idKey(tenantId, namespaceId)] }
    }

    override suspend fun findById(namespaceId: UUID): Namespace? {
        return mutex.withLock { namespacesByNamespaceId[namespaceId] }
    }

    override suspend fun findAll(): List<Namespace> {
        return mutex.withLock { namespacesByName.values.toList() }
    }

    override suspend fun findById(
        tenantId: String,
        namespaceId: String,
    ): Namespace? {
        return findByName(tenantId, namespaceId)
    }
}
