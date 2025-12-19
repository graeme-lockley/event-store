package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Consumer
import com.eventstore.domain.ports.outbound.ConsumerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    override suspend fun findByTopic(topic: String): List<Consumer> {
        return mutex.withLock {
            consumers.values.filter { topic in it.topics }
        }
    }

    override suspend fun findByTenantAndNamespace(tenantName: String, namespaceName: String): List<Consumer> {
        // Note: Consumer domain model doesn't currently store tenant/namespace directly.
        // Topics are stored as qualified names (tenant/namespace/topic) in the topics map.
        // Filter consumers whose topics belong to the specified tenant/namespace.
        val prefix = "$tenantName/$namespaceName/"
        return mutex.withLock {
            consumers.values.filter { consumer ->
                consumer.topics.keys.any { it.startsWith(prefix) }
            }
        }
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

