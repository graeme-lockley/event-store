package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Namespace
import java.util.*

interface NamespaceRepository {
    suspend fun save(namespace: Namespace)
    suspend fun findByName(tenantName: String, name: String): Namespace?
    suspend fun findById(tenantId: UUID, namespaceId: UUID): Namespace?
    suspend fun findById(namespaceId: UUID): Namespace?
    suspend fun findAll(): List<Namespace>

    // Backward compatibility - deprecated
    @Deprecated("Use findByName instead", ReplaceWith("findByName(tenantId, namespaceId)"))
    suspend fun findById(tenantId: String, namespaceId: String): Namespace? = findByName(tenantId, namespaceId)
}

