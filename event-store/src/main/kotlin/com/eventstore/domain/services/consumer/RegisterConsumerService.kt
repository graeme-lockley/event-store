package com.eventstore.domain.services.consumer

import com.eventstore.domain.exceptions.InvalidConsumerRegistrationException
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.ConsumerFactory
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.TopicRepository
import java.util.*

class RegisterConsumerService(
    private val consumerRepository: ConsumerRepository,
    private val topicRepository: TopicRepository,
    private val consumerFactory: ConsumerFactory,
    private val eventDispatcher: EventDispatcher,
) {
    suspend fun execute(request: ConsumerRegistrationRequest): String {
        val topicIds = request.topics.keys.toSet()

        // Validate topics exist
        for (topicId in topicIds) {
            if (!topicRepository.topicExists(topicId)) {
                throw TopicNotFoundException(topicId.toString())
            }
        }

        // Use factory to create consumer with topicIds
        val consumer =
            try {
                consumerFactory.create(request)
            } catch (e: IllegalArgumentException) {
                throw InvalidConsumerRegistrationException(e.message ?: "Invalid consumer configuration")
            } catch (e: UnsupportedOperationException) {
                throw InvalidConsumerRegistrationException(e.message ?: "Consumer type not supported")
            }

        // Save consumer
        consumerRepository.save(consumer)

        // Ensure dispatchers are running for the consumer's topics (using topicIds)
        // This will also trigger immediate delivery check for catchup scenarios
        eventDispatcher.ensureDispatchersRunning(topicIds)

        return consumer.id
    }
}
