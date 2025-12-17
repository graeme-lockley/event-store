package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTopicRepository : TopicRepository {
    private val topics = mutableMapOf<String, Topic>()
    private val mutex = Mutex()

    override suspend fun createTopic(
        name: String,
        schemas: List<Schema>,
        tenantId: String,
        namespaceId: String
    ): Topic {
        return mutex.withLock {
            val key = key(name, tenantId, namespaceId)
            if (topics.containsKey(key)) {
                throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
            }

            val topic = Topic(name, 0, schemas, tenantId, namespaceId)
            topics[key] = topic
            topic
        }
    }

    override suspend fun getTopic(name: String, tenantId: String, namespaceId: String): Topic? {
        return mutex.withLock {
            topics[key(name, tenantId, namespaceId)]
        }
    }

    override suspend fun topicExists(name: String, tenantId: String, namespaceId: String): Boolean {
        return mutex.withLock {
            topics.containsKey(key(name, tenantId, namespaceId))
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long, tenantId: String, namespaceId: String) {
        mutex.withLock {
            val key = key(name, tenantId, namespaceId)
            val current = topics[key]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

            topics[key] = current.copy(sequence = sequence)
        }
    }

    override suspend fun getAndIncrementSequence(topicName: String, tenantId: String, namespaceId: String): Long {
        return mutex.withLock {
            val key = key(topicName, tenantId, namespaceId)
            val current = topics[key]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

            val nextSequence = current.sequence + 1
            topics[key] = current.copy(sequence = nextSequence)
            nextSequence
        }
    }

    override suspend fun updateSchemas(name: String, schemas: List<Schema>, tenantId: String, namespaceId: String): Topic {
        return mutex.withLock {
            val key = key(name, tenantId, namespaceId)
            val current = topics[key]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

            val updated = current.copy(schemas = schemas)
            topics[key] = updated
            updated
        }
    }

    override suspend fun getAllTopics(): List<Topic> {
        return mutex.withLock {
            topics.values.toList()
        }
    }

    private fun key(name: String, tenantId: String, namespaceId: String): String =
        "$tenantId/$namespaceId/$name"
}

