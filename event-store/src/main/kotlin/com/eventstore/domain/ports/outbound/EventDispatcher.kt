package com.eventstore.domain.ports.outbound

import java.util.*

/**
 * Outbound port for notifying the event dispatcher that new events have been published.
 * This allows the domain layer to trigger event delivery to consumers without
 * depending on infrastructure implementation details.
 */
interface EventDispatcher {
    /**
     * Notifies the dispatcher that an event has been published for the given topic.
     * This triggers immediate delivery checks for consumers subscribed to this topic.
     *
     * @param topicId The topic ID (UUID) that received a new event
     */
    suspend fun notifyEventPublished(topicId: UUID)

    /**
     * Notifies the dispatcher that events have been published for the given topics.
     * This triggers immediate delivery checks for consumers subscribed to these topics.
     *
     * @param topicIds The set of topic IDs (UUIDs) that received new events
     */
    suspend fun notifyEventsPublished(topicIds: Set<UUID>)

    /**
     * Ensures dispatchers are running for the given topics.
     * This should be called when consumers are registered to ensure
     * events can be delivered to them. For newly started dispatchers,
     * this will trigger an immediate delivery check to handle catchup scenarios.
     *
     * @param topicIds The set of topic IDs (UUIDs) that need dispatchers running
     */
    suspend fun ensureDispatchersRunning(topicIds: Set<UUID>)
}

