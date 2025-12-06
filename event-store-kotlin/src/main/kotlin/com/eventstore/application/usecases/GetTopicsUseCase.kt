package com.eventstore.application.usecases

import com.eventstore.application.repositories.TopicRepository
import com.eventstore.domain.Topic

class GetTopicsUseCase(
    private val topicRepository: TopicRepository
) {
    suspend fun execute(): List<Topic> {
        return topicRepository.getAllTopics()
    }

    suspend fun execute(topicName: String): Topic {
        return topicRepository.getTopic(topicName)
            ?: throw com.eventstore.domain.exceptions.TopicNotFoundException(topicName)
    }
}

