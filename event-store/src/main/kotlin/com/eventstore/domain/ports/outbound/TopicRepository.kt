package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Schema
import com.eventstore.domain.Topic
import java.util.UUID

/**
 * Outbound port for topic persistence operations.
 */
interface TopicRepository {
    suspend fun createTopic(
        resourceId: UUID,
        tenantResourceId: UUID,
        namespaceResourceId: UUID,
        name: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic

    suspend fun getTopic(
        name: String,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic?

    suspend fun topicExists(
        name: String,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Boolean

    suspend fun updateSequence(
        name: String,
        sequence: Long,
        tenantName: String = "default",
        namespaceName: String = "default"
    )

    suspend fun getAndIncrementSequence(
        topicName: String,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Long

    suspend fun updateSchemas(
        name: String,
        schemas: List<Schema>,
        tenantName: String = "default",
        namespaceName: String = "default"
    ): Topic

    suspend fun getAllTopics(): List<Topic>
}

