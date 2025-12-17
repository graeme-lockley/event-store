package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Namespace

interface NamespaceRepository {
    suspend fun save(namespace: Namespace)
    suspend fun findById(tenantId: String, namespaceId: String): Namespace?
    suspend fun findAll(): List<Namespace>
}

