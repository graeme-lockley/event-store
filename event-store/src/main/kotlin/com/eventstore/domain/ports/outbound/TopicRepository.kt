package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic

/**
 * Outbound port for topic persistence operations.
 */
interface TopicRepository {
    suspend fun createTopic(
        name: String,
        schemas: List<Schema>,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Topic

    suspend fun getTopic(
        name: String,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Topic?

    suspend fun topicExists(
        name: String,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Boolean

    suspend fun updateSequence(
        name: String,
        sequence: Long,
        tenantId: String = "default",
        namespaceId: String = "default"
    )

    suspend fun getAndIncrementSequence(
        topicName: String,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Long

    suspend fun updateSchemas(
        name: String,
        schemas: List<Schema>,
        tenantId: String = "default",
        namespaceId: String = "default"
    ): Topic

    suspend fun getAllTopics(): List<Topic>
}

