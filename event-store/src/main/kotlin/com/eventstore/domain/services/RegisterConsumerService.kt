package com.eventstore.domain.services

import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerFactory
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.TopicRepository

class RegisterConsumerService(
    private val consumerRepository: ConsumerRepository,
    private val topicRepository: TopicRepository,
    private val consumerFactory: ConsumerFactory,
    private val eventDispatcher: EventDispatcher
) {
    suspend fun execute(request: ConsumerRegistrationRequest): String {
        // Validate topics exist
        for (topic in request.topics.keys) {
            if (!topicRepository.topicExists(topic)) {
                throw TopicNotFoundException(topic)
            }
        }

        // Use factory to create consumer
        val consumer = try {
            consumerFactory.create(request)
        } catch (e: IllegalArgumentException) {
            throw InvalidConsumerRegistrationException(e.message ?: "Invalid consumer configuration")
        } catch (e: UnsupportedOperationException) {
            throw InvalidConsumerRegistrationException(e.message ?: "Consumer type not supported")
        }

        // Save consumer
        consumerRepository.save(consumer)

        // Ensure dispatchers are running for the consumer's topics
        eventDispatcher.ensureDispatchersRunning(request.topics.keys.toSet())

        return consumer.id
    }
}
