package com.eventstore.domain.services.topic

import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository

class GetTopicsService(
    private val topicRepository: TopicRepository
) {
    suspend fun list(tenantId: String = "default", namespaceId: String = "default"): List<Topic> =
        topicRepository.getAllTopics().filter { it.tenantId == tenantId && it.namespaceId == namespaceId }

    suspend fun get(topicName: String, tenantId: String = "default", namespaceId: String = "default"): Topic =
        topicRepository.getTopic(topicName, tenantId, namespaceId) ?: throw TopicNotFoundException(topicName)
}

