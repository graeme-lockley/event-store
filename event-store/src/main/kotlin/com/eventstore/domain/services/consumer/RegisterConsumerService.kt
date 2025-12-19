package com.eventstore.domain.services.consumer

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
    suspend fun execute(request: ConsumerRegistrationRequest, tenantName: String, namespaceName: String): String {
        val topics = request.topics.keys.toSet()

        // Validate topics exist in the tenant/namespace context
        for (topic in topics) {
            if (!topicRepository.topicExists(topic, tenantName, namespaceName)) {
                throw TopicNotFoundException(topic)
            }
        }

        // Convert topic names to qualified names (tenant/namespace/topic)
        val qualifiedTopics = request.topics.mapKeys { (topicName, _) ->
            "$tenantName/$namespaceName/$topicName"
        }

        // Create a new request with qualified topic names
        val qualifiedRequest = when (request) {
            is HttpConsumerRegistrationRequest -> {
                HttpConsumerRegistrationRequest(
                    callbackUrl = request.callbackUrl,
                    topics = qualifiedTopics
                )
            }
            is InMemoryConsumerRegistrationRequest -> {
                InMemoryConsumerRegistrationRequest(
                    handler = request.handler,
                    topics = qualifiedTopics
                )
            }
            is AzureEventGridConsumerRegistrationRequest -> {
                AzureEventGridConsumerRegistrationRequest(
                    endpointUrl = request.endpointUrl,
                    accessKey = request.accessKey,
                    topics = qualifiedTopics
                )
            }
        }

        // Use factory to create consumer with qualified topic names
        val consumer = try {
            consumerFactory.create(qualifiedRequest)
        } catch (e: IllegalArgumentException) {
            throw InvalidConsumerRegistrationException(e.message ?: "Invalid consumer configuration")
        } catch (e: UnsupportedOperationException) {
            throw InvalidConsumerRegistrationException(e.message ?: "Consumer type not supported")
        }

        // Save consumer
        consumerRepository.save(consumer)

        // Ensure dispatchers are running for the consumer's topics (using qualified names)
        // This will also trigger immediate delivery check for catchup scenarios
        eventDispatcher.ensureDispatchersRunning(qualifiedTopics.keys.toSet())

        return consumer.id
    }
}
