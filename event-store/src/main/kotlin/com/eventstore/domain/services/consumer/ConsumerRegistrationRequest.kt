package com.eventstore.domain.services.consumer

/**
 * Sealed interface for consumer registration requests.
 * Each consumer type has its own request subclass with type-specific parameters.
 */
sealed interface ConsumerRegistrationRequest {
    val topics: Map<String, String?> // topic -> lastEventId (null if starting from beginning)
}

/**
 * Request to register an HTTP consumer that receives events via webhook.
 */
data class HttpConsumerRegistrationRequest(
    val callbackUrl: String,
    override val topics: Map<String, String?>
) : ConsumerRegistrationRequest

/**
 * Request to register an in-memory consumer that uses a closure/function for delivery.
 * Note: This is typically used in testing scenarios where events can be handled programmatically.
 */
data class InMemoryConsumerRegistrationRequest(
    val handler: suspend (List<com.eventstore.domain.Event>) -> com.eventstore.domain.ports.outbound.DeliveryResult,
    override val topics: Map<String, String?>
) : ConsumerRegistrationRequest

/**
 * Request to register an Azure Event Grid consumer (for future implementation).
 */
data class AzureEventGridConsumerRegistrationRequest(
    val endpointUrl: String,
    val accessKey: String,
    override val topics: Map<String, String?>
) : ConsumerRegistrationRequest

