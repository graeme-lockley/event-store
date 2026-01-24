package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class InMemoryTopicRepository : TopicRepository {
    private val topics = mutableMapOf<UUID, Topic>()
    private val mutex = Mutex()

    override suspend fun createTopic(
        topicId: UUID,
        namespaceId: UUID,
        name: String,
        schemas: List<Schema>,
    ): Topic {
        return mutex.withLock {
            if (topics.containsKey(topicId)) {
                throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
            }

            val topic =
                Topic(
                    topicId = topicId,
                    namespaceId = namespaceId,
                    name = name,
                    sequence = 0,
                    schemas = schemas,
                )
            topics[topicId] = topic
            topic
        }
    }

    override suspend fun getTopic(topicId: UUID): Topic? {
        return mutex.withLock {
            topics[topicId]
        }
    }

    override suspend fun topicExists(topicId: UUID): Boolean {
        return mutex.withLock {
            topics.containsKey(topicId)
        }
    }

    override suspend fun updateSequence(
        topicId: UUID,
        sequence: Long,
    ) {
        mutex.withLock {
            val current =
                topics[topicId]
                    ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

            topics[topicId] = current.copy(sequence = sequence)
        }
    }

    override suspend fun getAndIncrementSequence(topicId: UUID): Long {
        return mutex.withLock {
            val current =
                topics[topicId]
                    ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

            val nextSequence = current.sequence + 1
            topics[topicId] = current.copy(sequence = nextSequence)
            nextSequence
        }
    }

    override suspend fun updateSchemas(
        topicId: UUID,
        schemas: List<Schema>,
    ): Topic {
        return mutex.withLock {
            val current =
                topics[topicId]
                    ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicId.toString())

            val updated = current.copy(schemas = schemas)
            topics[topicId] = updated
            updated
        }
    }

    override suspend fun getAllTopics(): List<Topic> {
        return mutex.withLock {
            topics.values.toList()
        }
    }
}
