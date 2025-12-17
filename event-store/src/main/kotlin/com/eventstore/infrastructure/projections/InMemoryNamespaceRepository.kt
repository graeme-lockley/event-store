package com.eventstore.infrastructure.projections

import com.eventstore.domain.Namespace
import com.eventstore.domain.ports.outbound.NamespaceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryNamespaceRepository : NamespaceRepository {
    private val mutex = Mutex()
    private val namespaces = mutableMapOf<String, Namespace>()

    private fun key(tenantId: String, namespaceId: String): String = "$tenantId/$namespaceId"

    override suspend fun save(namespace: Namespace) {
        mutex.withLock {
            namespaces[key(namespace.tenantId, namespace.id)] = namespace
        }
    }

    override suspend fun findById(tenantId: String, namespaceId: String): Namespace? {
        return mutex.withLock { namespaces[key(tenantId, namespaceId)] }
    }

    override suspend fun findAll(): List<Namespace> {
        return mutex.withLock { namespaces.values.toList() }
    }
}

