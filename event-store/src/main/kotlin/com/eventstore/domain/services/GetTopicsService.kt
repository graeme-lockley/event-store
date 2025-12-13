package com.eventstore.domain.services

import com.eventstore.domain.Topic
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.TopicRepository

class GetTopicsService(
    private val topicRepository: TopicRepository
) {
    suspend fun execute(): List<Topic> {
        return topicRepository.getAllTopics()
    }

    suspend fun execute(topicName: String): Topic {
        return topicRepository.getTopic(topicName)
            ?: throw TopicNotFoundException(topicName)
    }
}

