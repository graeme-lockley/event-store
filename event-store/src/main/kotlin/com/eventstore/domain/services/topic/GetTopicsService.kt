package com.eventstore.domain.services.topic

import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository

class GetTopicsService(
    private val topicRepository: TopicRepository
) {
    suspend fun list(tenantName: String = "default", namespaceName: String = "default"): List<Topic> =
        topicRepository.getAllTopics().filter { it.tenantName == tenantName && it.namespaceName == namespaceName }

    suspend fun get(topicName: String, tenantName: String = "default", namespaceName: String = "default"): Topic =
        topicRepository.getTopic(topicName, tenantName, namespaceName) ?: throw TopicNotFoundException(topicName)
}

