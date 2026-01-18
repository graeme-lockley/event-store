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

import java.util.*

class TopicDispatcher(
    private val topicId: UUID,
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
                // JobCancellationException is expected during graceful shutdown - don't log with stack trace
                if (e is CancellationException) {
                    // Skip logging for expected cancellation during shutdown
                    return@launch
                }
                logger.error("Dispatcher for topic $topicId encountered an error and will stop", e)
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
        val consumers = consumerRepository.findByTopic(topicId)

        for (consumer in consumers) {
            try {
                deliverPendingEvents(consumer)
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $topicId", e)
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
        val topicToLatestEventId = mutableMapOf<UUID, String>()

        // Check each topic the consumer is interested in
        for ((consumerTopicId, lastEventIdStr) in consumer.topics) {
            if (consumerTopicId != topicId) continue

            try {
                // ConsumerRegistrationRequestMapper already normalizes empty strings to null,
                // so lastEventIdStr will be either null or a valid EventId string.
                // If EventId construction fails, it indicates data corruption and should not be silently ignored.
                val lastEventId = lastEventIdStr?.let { EventId.fromString(it) }
                val events = eventRepository.getEvents(
                    topicId = topicId,
                    sinceEventId = lastEventId
                )

                if (events.isNotEmpty()) {
                    eventsToDeliver.addAll(events)
                    // Track the latest event ID for this topic (will update consumer state only after successful delivery)
                    topicToLatestEventId[topicId] = events.last().id.value
                }
            } catch (e: Exception) {
                logger.error("Failed to deliver events to consumer ${consumer.id} for topic $consumerTopicId", e)
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
            for ((consumerTopicId, latestEventId) in topicToLatestEventId) {
                updatedConsumer = updatedConsumer.withUpdatedLastEventId(consumerTopicId, latestEventId)
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
}
