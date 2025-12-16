package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic

/**
 * Outbound port for topic persistence operations.
 */
interface TopicRepository {
    suspend fun createTopic(name: String, schemas: List<Schema>): Topic
    suspend fun getTopic(name: String): Topic?
    suspend fun topicExists(name: String): Boolean
    suspend fun updateSequence(name: String, sequence: Long)
    suspend fun getAndIncrementSequence(topicName: String): Long
    suspend fun updateSchemas(name: String, schemas: List<Schema>): Topic
    suspend fun getAllTopics(): List<Topic>
}

