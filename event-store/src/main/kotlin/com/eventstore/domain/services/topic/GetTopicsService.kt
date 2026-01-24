package com.eventstore.domain.services.topic

import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository
import java.util.*

class GetTopicsService(
    private val topicRepository: TopicRepository,
) {
    suspend fun list(namespaceId: UUID? = null): List<Topic> {
        val allTopics = topicRepository.getAllTopics()
        return if (namespaceId != null) {
            allTopics.filter { it.namespaceId == namespaceId }
        } else {
            allTopics
        }
    }

    suspend fun get(topicId: UUID): Topic = topicRepository.getTopic(topicId) ?: throw TopicNotFoundException(topicId.toString())
}
