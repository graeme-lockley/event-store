package com.eventstore.domain.ports.outbound

/**
 * Outbound port for notifying the event dispatcher that new events have been published.
 * This allows the domain layer to trigger event delivery to consumers without
 * depending on infrastructure implementation details.
 */
interface EventDispatcher {
    /**
     * Notifies the dispatcher that events have been published for the given topics.
     * This triggers immediate delivery checks for consumers subscribed to these topics.
     *
     * @param topics The set of topic names that received new events
     */
    suspend fun notifyEventsPublished(topics: Set<String>)
    
    /**
     * Ensures dispatchers are running for the given topics.
     * This should be called when consumers are registered to ensure
     * events can be delivered to them. For newly started dispatchers,
     * this will trigger an immediate delivery check to handle catchup scenarios.
     *
     * @param topics The set of topic names that need dispatchers running
     */
    suspend fun ensureDispatchersRunning(topics: Set<String>)
}

