package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryTopicRepository : TopicRepository {
    private val topics = mutableMapOf<String, Topic>()
    private val mutex = Mutex()

    override suspend fun createTopic(
        resourceId: UUID,
        tenantResourceId: UUID,
        namespaceResourceId: UUID,
        name: String,
        schemas: List<Schema>,
        tenantName: String,
        namespaceName: String
    ): Topic {
        return mutex.withLock {
            val key = key(name, tenantName, namespaceName)
            if (topics.containsKey(key)) {
                throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
            }

            val topic = Topic(
                resourceId = resourceId,
                tenantResourceId = tenantResourceId,
                namespaceResourceId = namespaceResourceId,
                name = name,
                sequence = 0,
                schemas = schemas,
                tenantName = tenantName,
                namespaceName = namespaceName
            )
            topics[key] = topic
            topic
        }
    }

    override suspend fun getTopic(name: String, tenantName: String, namespaceName: String): Topic? {
        return mutex.withLock {
            topics[key(name, tenantName, namespaceName)]
        }
    }

    override suspend fun topicExists(name: String, tenantName: String, namespaceName: String): Boolean {
        return mutex.withLock {
            topics.containsKey(key(name, tenantName, namespaceName))
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long, tenantName: String, namespaceName: String) {
        mutex.withLock {
            val key = key(name, tenantName, namespaceName)
            val current = topics[key]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

            topics[key] = current.copy(sequence = sequence)
        }
    }

    override suspend fun getAndIncrementSequence(topicName: String, tenantName: String, namespaceName: String): Long {
        return mutex.withLock {
            val key = key(topicName, tenantName, namespaceName)
            val current = topics[key]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

            val nextSequence = current.sequence + 1
            topics[key] = current.copy(sequence = nextSequence)
            nextSequence
        }
    }

    override suspend fun updateSchemas(name: String, schemas: List<Schema>, tenantName: String, namespaceName: String): Topic {
        return mutex.withLock {
            val key = key(name, tenantName, namespaceName)
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

    private fun key(name: String, tenantName: String, namespaceName: String): String =
        "$tenantName/$namespaceName/$name"
}

