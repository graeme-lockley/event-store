package com.eventstore.infrastructure.background

import com.eventstore.domain.Consumer
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class TopicDispatcher(
    private val topic: String,
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository,
    private val checkIntervalMs: Long = 500
) {
    private val logger = LoggerFactory.getLogger(TopicDispatcher::class.java)
    private var job: Job? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val retryState = ConcurrentHashMap<String, RetryState>()
    private val maxRetries = 5
    private val baseRetryDelayMs = 1000L

    data class RetryState(
        val attempts: Int,
        val nextRetryAt: Long
    )

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) {
            return
        }

        job = scope.launch {
            _isRunning.value = true
            try {
                while (isActive) {
                    checkAndDeliverEvents()
                    delay(checkIntervalMs)
                }
            } catch (e: Exception) {
                logger.error("Dispatcher for topic $topic encountered an error and will stop", e)
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun triggerDelivery() {
        checkAndDeliverEvents()
    }

    private suspend fun checkAndDeliverEvents() {
        val consumers = consumerRepository.findByTopic(topic)

        for (consumer in consumers) {
            try {
                deliverPendingEvents(consumer)
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $topic", e)
            }
        }
    }

    private suspend fun deliverPendingEvents(consumer: Consumer) {
        // Respect backoff window if set
        val state = retryState[consumer.id]
        if (state != null && System.currentTimeMillis() < state.nextRetryAt) {
            return
        }

        val eventsToDeliver = mutableListOf<com.eventstore.domain.Event>()
        val topicToLatestEventId = mutableMapOf<String, String>()

        // Parse tenant/namespace/topic from the dispatcher's topic (which may be qualified)
        val (simpleTopicName, tenantId, namespaceId) = parseTopicName(topic)

        // Check each topic the consumer is interested in
        for ((topicName, lastEventIdStr) in consumer.topics) {
            if (topicName != topic) continue

            try {
                // ConsumerRegistrationRequestMapper already normalizes empty strings to null,
                // so lastEventIdStr will be either null or a valid EventId string.
                // If EventId construction fails, it indicates data corruption and should not be silently ignored.
                val lastEventId = lastEventIdStr?.let { EventId(it) }
                val events = eventRepository.getEvents(
                    topic = simpleTopicName,
                    sinceEventId = lastEventId,
                    tenantId = tenantId,
                    namespaceId = namespaceId
                )

                if (events.isNotEmpty()) {
                    eventsToDeliver.addAll(events)
                    // Track the latest event ID for this topic (will update consumer state only after successful delivery)
                    topicToLatestEventId[topicName] = events.last().id.value
                }
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $topicName", e)
            }
        }

        if (eventsToDeliver.isEmpty()) {
            return
        }

        // Deliver events using the consumer's deliver method
        val result = consumer.deliver(eventsToDeliver)

        if (result.success) {
            // Update consumer state only after successful delivery
            var updatedConsumer = consumer
            for ((topicName, latestEventId) in topicToLatestEventId) {
                updatedConsumer = updatedConsumer.withUpdatedLastEventId(topicName, latestEventId)
            }
            consumerRepository.save(updatedConsumer)
            
            // Reset retry state on success
            retryState.remove(consumer.id)
        } else {
            // Apply exponential backoff
            val current = retryState[consumer.id] ?: RetryState(0, 0)
            val attempts = current.attempts + 1
            val delay = minOf(
                baseRetryDelayMs * (1L shl (attempts - 1)),
                60_000L
            )
            val nextRetryAt = System.currentTimeMillis() + delay
            retryState[consumer.id] = RetryState(attempts, nextRetryAt)

            if (attempts >= maxRetries) {
                // Unregister consumer after max retries
                retryState.remove(consumer.id)
                consumerRepository.delete(consumer.id)
            }
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
                // For "default/default/topic", we still pass "default"/"default" to match events stored with those values
                // But if events are stored in legacy format (null/null), we need to handle that
                val tenant = if (parts[0] == "default") null else parts[0]
                val namespace = if (parts[1] == "default") null else parts[1]
                Triple(parts[2], tenant, namespace)
            }
            1 -> Triple(parts[0], null, null) // topic (legacy format)
            else -> Triple(topicName, null, null) // fallback to original
        }
    }
}
