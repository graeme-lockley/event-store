package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import java.util.*

/**
 * Outbound port for topic persistence operations.
 */
interface TopicRepository {
    suspend fun createTopic(
        topicId: UUID,
        namespaceId: UUID,
        name: String,
        schemas: List<Schema>,
    ): Topic

    suspend fun getTopic(topicId: UUID): Topic?

    suspend fun topicExists(topicId: UUID): Boolean

    suspend fun updateSequence(
        topicId: UUID,
        sequence: Long,
    )

    suspend fun getAndIncrementSequence(topicId: UUID): Long

    suspend fun updateSchemas(
        topicId: UUID,
        schemas: List<Schema>,
    ): Topic

    suspend fun getAllTopics(): List<Topic>
}
