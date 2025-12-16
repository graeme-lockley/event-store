package com.eventstore.infrastructure.persistence

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import com.eventstore.domain.ports.outbound.TopicRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTopicRepository : TopicRepository {
    private val topics = mutableMapOf<String, Topic>()
    private val mutex = Mutex()

    override suspend fun createTopic(name: String, schemas: List<Schema>): Topic {
        return mutex.withLock {
            if (topics.containsKey(name)) {
                throw com.eventstore.domain.exceptions.TopicAlreadyExistsException(name)
            }

            val topic = Topic(name, 0, schemas)
            topics[name] = topic
            topic
        }
    }

    override suspend fun getTopic(name: String): Topic? {
        return mutex.withLock {
            topics[name]
        }
    }

    override suspend fun topicExists(name: String): Boolean {
        return mutex.withLock {
            topics.containsKey(name)
        }
    }

    override suspend fun updateSequence(name: String, sequence: Long) {
        mutex.withLock {
            val current = topics[name]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

            topics[name] = current.copy(sequence = sequence)
        }
    }

    override suspend fun getAndIncrementSequence(topicName: String): Long {
        return mutex.withLock {
            val current = topics[topicName]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)

            val nextSequence = current.sequence + 1
            topics[topicName] = current.copy(sequence = nextSequence)
            nextSequence
        }
    }

    override suspend fun updateSchemas(name: String, schemas: List<Schema>): Topic {
        return mutex.withLock {
            val current = topics[name]
                ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(name)

            val updated = current.copy(schemas = schemas)
            topics[name] = updated
            updated
        }
    }

    override suspend fun getAllTopics(): List<Topic> {
        return mutex.withLock {
            topics.values.toList()
        }
    }
}

