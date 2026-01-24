package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Consumer
import com.eventstore.domain.ports.outbound.ConsumerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class InMemoryConsumerRepository : ConsumerRepository {
    private val consumers = mutableMapOf<String, Consumer>()
    private val mutex = Mutex()

    override suspend fun save(consumer: Consumer) {
        mutex.withLock {
            consumers[consumer.id] = consumer
        }
    }

    override suspend fun findById(id: String): Consumer? {
        return mutex.withLock {
            consumers[id]
        }
    }

    override suspend fun findAll(): List<Consumer> {
        return mutex.withLock {
            consumers.values.toList()
        }
    }

    override suspend fun findByTopic(topicId: UUID): List<Consumer> {
        return mutex.withLock {
            consumers.values.filter { topicId in it.topics }
        }
    }

    override suspend fun findByTenantAndNamespace(
        tenantName: String,
        namespaceName: String,
    ): List<Consumer> {
        // Note: Topics are now identified by UUIDs (topicId), not qualified names.
        // This method cannot filter by tenant/namespace anymore since consumers don't store that information.
        // Return empty list as this method is deprecated with the topicId refactoring.
        return emptyList()
    }

    override suspend fun delete(id: String): Boolean {
        return mutex.withLock {
            consumers.remove(id) != null
        }
    }

    override suspend fun count(): Int {
        return mutex.withLock {
            consumers.size
        }
    }
}
