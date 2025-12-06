package com.eventstore.domain.services

import com.eventstore.domain.Consumer
import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import java.net.URL
import java.util.*

data class ConsumerRegistrationRequest(
    val callback: String,
    val topics: Map<String, String?> // topic -> lastEventId
)

class RegisterConsumerService(
    private val consumerRepository: ConsumerRepository,
    private val topicRepository: TopicRepository
) {
    suspend fun execute(request: ConsumerRegistrationRequest): String {
        // Validate callback URL
        val callbackUrl = try {
            URL(request.callback)
        } catch (e: Exception) {
            throw InvalidConsumerRegistrationException("Invalid callback URL: ${e.message}")
        }

        // Validate topics exist
        for (topic in request.topics.keys) {
            if (!topicRepository.topicExists(topic)) {
                throw TopicNotFoundException(topic)
            }
        }

        // Create consumer
        val consumerId = UUID.randomUUID().toString()
        val consumer = Consumer(
            id = consumerId,
            callback = callbackUrl,
            topics = request.topics
        )

        consumerRepository.save(consumer)

        return consumerId
    }
}

