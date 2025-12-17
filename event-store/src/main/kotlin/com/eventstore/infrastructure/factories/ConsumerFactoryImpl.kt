package com.eventstore.infrastructure.factories

import com.eventstore.domain.Consumer
import com.eventstore.domain.consumers.HttpConsumer
import com.eventstore.domain.consumers.InMemoryConsumer
import com.eventstore.domain.ports.outbound.ConsumerFactory
import com.eventstore.domain.services.consumer.AzureEventGridConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.ConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.HttpConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import java.net.URI
import java.util.*

/**
 * Default implementation of ConsumerFactory.
 * Creates the appropriate Consumer subclass based on the registration request type.
 */
class ConsumerFactoryImpl : ConsumerFactory {

    override fun create(request: ConsumerRegistrationRequest): Consumer {
        val consumerId = UUID.randomUUID().toString()
        
        return when (request) {
            is HttpConsumerRegistrationRequest -> {
                val callbackUrl = try {
                    URI(request.callbackUrl).toURL()
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid callback URL: ${e.message}", e)
                }
                HttpConsumer(
                    id = consumerId,
                    callbackUrl = callbackUrl,
                    topics = request.topics
                )
            }
            
            is InMemoryConsumerRegistrationRequest -> {
                InMemoryConsumer(
                    id = consumerId,
                    handler = request.handler,
                    topics = request.topics
                )
            }
            
            is AzureEventGridConsumerRegistrationRequest -> {
                // Future implementation
                throw UnsupportedOperationException("Azure Event Grid consumers not yet implemented")
            }
        }
    }
}

