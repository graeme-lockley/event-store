package com.eventstore.domain

import com.eventstore.domain.ports.outbound.DeliveryResult

/**
 * Abstract base class for consumers that receive events via different protocols.
 */
abstract class Consumer(
    val id: String,
    val topics: Map<String, String?> // topic -> lastEventId (null if starting from beginning)
) {
    init {
        require(id.isNotBlank()) { "Consumer ID is required" }
        require(topics.isNotEmpty()) { "Consumer must subscribe to at least one topic" }
    }

    /**
     * Returns the consumer type.
     */
    abstract fun getType(): ConsumerType

    /**
     * Delivers events to the consumer using the specific protocol implementation.
     */
    abstract suspend fun deliver(events: List<Event>): DeliveryResult

    /**
     * Returns a string representation of the consumer's configuration.
     */
    abstract override fun toString(): String

    /**
     * Creates a copy with updated last event ID.
     * Subclasses must implement this to return their specific type.
     */
    abstract fun withUpdatedLastEventId(topic: String, eventId: String): Consumer
}
