package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Consumer
import java.util.*

/**
 * Outbound port for consumer management operations.
 */
interface ConsumerRepository {
    suspend fun save(consumer: Consumer)
    suspend fun findById(id: String): Consumer?
    suspend fun findAll(): List<Consumer>
    suspend fun findByTopic(topicId: UUID): List<Consumer>
    suspend fun findByTenantAndNamespace(tenantName: String, namespaceName: String): List<Consumer>
    suspend fun delete(id: String): Boolean
    suspend fun count(): Int
}

