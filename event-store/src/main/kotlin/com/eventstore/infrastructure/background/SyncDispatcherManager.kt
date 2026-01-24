package com.eventstore.infrastructure.background

import com.eventstore.domain.Consumer
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Synchronous event dispatcher for use in tests.
 * Processes all events synchronously and sequentially, ensuring that when an event
 * is published, all consumers have processed it before the publish call returns.
 */
class SyncDispatcherManager(
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository,
) : EventDispatcher {
    private val logger = LoggerFactory.getLogger(SyncDispatcherManager::class.java)
    private val processedTopics = mutableSetOf<UUID>()

    suspend fun getRunningDispatchers(): List<UUID> {
        // For synchronous dispatcher, return topic IDs that have been processed
        // This is a simplified implementation for tests
        return processedTopics.toList()
    }

    override suspend fun notifyEventPublished(topicId: UUID) {
        processEventsForTopic(topicId)
        processedTopics.add(topicId)
    }

    override suspend fun notifyEventsPublished(topicIds: Set<UUID>) {
        // Process events synchronously for each topic, one after another
        for (topicId in topicIds) {
            processEventsForTopic(topicId)
            processedTopics.add(topicId)
        }
    }

    override suspend fun ensureDispatchersRunning(topicIds: Set<UUID>) {
        // For synchronous dispatcher, we just process events immediately
        // No need to track running state or start background jobs
        for (topicId in topicIds) {
            processEventsForTopic(topicId)
            processedTopics.add(topicId)
        }
    }

    /**
     * Process all pending events for a topic synchronously.
     * Events are delivered to consumers sequentially, one consumer at a time.
     */
    private suspend fun processEventsForTopic(topicId: UUID) {
        val consumers = consumerRepository.findByTopic(topicId)

        // Process each consumer sequentially (one after another)
        for (consumer in consumers) {
            try {
                deliverPendingEvents(consumer, topicId)
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $topicId", e)
            }
        }
    }

    /**
     * Deliver pending events to a consumer synchronously.
     * This is similar to TopicDispatcher.deliverPendingEvents but without retry logic
     * and backoff, as we want immediate synchronous processing in tests.
     */
    private suspend fun deliverPendingEvents(
        consumer: Consumer,
        topicId: UUID,
    ) {
        val eventsToDeliver = mutableListOf<com.eventstore.domain.Event>()
        val topicToLatestEventId = mutableMapOf<UUID, String>()

        // Check each topic the consumer is interested in
        for ((consumerTopicId, lastEventIdStr) in consumer.topics) {
            if (consumerTopicId != topicId) continue

            try {
                val lastEventId = lastEventIdStr?.let { EventId.fromString(it) }
                val events =
                    eventRepository.getEvents(
                        topicId = topicId,
                        sinceEventId = lastEventId,
                    )

                if (events.isNotEmpty()) {
                    eventsToDeliver.addAll(events)
                    // Track the latest event ID for this topic
                    topicToLatestEventId[topicId] = events.last().id.value
                }
            } catch (e: Exception) {
                logger.error("Failed to get events for consumer ${consumer.id} for topic $consumerTopicId", e)
            }
        }

        if (eventsToDeliver.isEmpty()) {
            return
        }

        // Deliver events synchronously - this blocks until delivery is complete
        val result = consumer.deliver(eventsToDeliver)

        if (result.success) {
            // Update consumer state only after successful delivery
            var updatedConsumer = consumer
            for ((consumerTopicId, latestEventId) in topicToLatestEventId) {
                updatedConsumer = updatedConsumer.withUpdatedLastEventId(consumerTopicId, latestEventId)
            }
            consumerRepository.save(updatedConsumer)
        } else {
            // In synchronous mode, we don't retry - just log the failure
            logger.warn("Failed to deliver events to consumer ${consumer.id} for topic $topicId: ${result.error ?: "Unknown error"}")
        }
    }
}
