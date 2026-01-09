package com.eventstore.infrastructure.background

import com.eventstore.domain.Consumer
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import org.slf4j.LoggerFactory

/**
 * Synchronous event dispatcher for use in tests.
 * Processes all events synchronously and sequentially, ensuring that when an event
 * is published, all consumers have processed it before the publish call returns.
 */
class SyncDispatcherManager(
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository
) : EventDispatcher {
    private val logger = LoggerFactory.getLogger(SyncDispatcherManager::class.java)

    override suspend fun notifyEventsPublished(topics: Set<String>) {
        // Process events synchronously for each topic, one after another
        for (topic in topics) {
            processEventsForTopic(topic)
        }
    }

    override suspend fun ensureDispatchersRunning(topics: Set<String>) {
        // For synchronous dispatcher, we just process events immediately
        // No need to track running state or start background jobs
        for (topic in topics) {
            processEventsForTopic(topic)
        }
    }

    /**
     * Process all pending events for a topic synchronously.
     * Events are delivered to consumers sequentially, one consumer at a time.
     */
    private suspend fun processEventsForTopic(topic: String) {
        val consumers = consumerRepository.findByTopic(topic)

        // Process each consumer sequentially (one after another)
        for (consumer in consumers) {
            try {
                deliverPendingEvents(consumer, topic)
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $topic", e)
            }
        }
    }

    /**
     * Deliver pending events to a consumer synchronously.
     * This is similar to TopicDispatcher.deliverPendingEvents but without retry logic
     * and backoff, as we want immediate synchronous processing in tests.
     */
    private suspend fun deliverPendingEvents(consumer: Consumer, topic: String) {
        val eventsToDeliver = mutableListOf<com.eventstore.domain.Event>()
        val topicToLatestEventId = mutableMapOf<String, String>()

        // Parse tenant/namespace/topic from the topic (which may be qualified)
        val (simpleTopicName, tenantId, namespaceId) = parseTopicName(topic)

        // Check each topic the consumer is interested in
        for ((topicName, lastEventIdStr) in consumer.topics) {
            if (topicName != topic) continue

            try {
                val lastEventId = lastEventIdStr?.let { EventId(it) }
                val events = eventRepository.getEvents(
                    topic = simpleTopicName,
                    sinceEventId = lastEventId,
                    tenantId = tenantId,
                    namespaceId = namespaceId
                )

                if (events.isNotEmpty()) {
                    eventsToDeliver.addAll(events)
                    // Track the latest event ID for this topic
                    topicToLatestEventId[topicName] = events.last().id.value
                }
            } catch (e: Exception) {
                logger.error("Failed to get events for consumer ${consumer.id} for topic $topicName", e)
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
            for ((topicName, latestEventId) in topicToLatestEventId) {
                updatedConsumer = updatedConsumer.withUpdatedLastEventId(topicName, latestEventId)
            }
            consumerRepository.save(updatedConsumer)
        } else {
            // In synchronous mode, we don't retry - just log the failure
            logger.warn("Failed to deliver events to consumer ${consumer.id} for topic $topic: ${result.error ?: "Unknown error"}")
        }
    }

    /**
     * Parse a topic name that may be in qualified format (tenant/namespace/topic) or simple format (topic).
     * Returns (simpleTopicName, tenantId, namespaceId).
     * For qualified names, extracts tenant/namespace. For simple names, uses null (legacy format).
     */
    private fun parseTopicName(topicName: String): Triple<String, String?, String?> {
        val parts = topicName.split("/")
        return when (parts.size) {
            3 -> {
                // Qualified name: tenant/namespace/topic
                val tenant = if (parts[0] == "default") null else parts[0]
                val namespace = if (parts[1] == "default") null else parts[1]
                Triple(parts[2], tenant, namespace)
            }

            1 -> Triple(parts[0], null, null) // topic (legacy format)
            else -> Triple(topicName, null, null) // fallback to original
        }
    }
}

