package com.eventstore.infrastructure.background

import com.eventstore.domain.Consumer
import com.eventstore.domain.EventId
import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TopicDispatcher(
    private val topic: String,
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository,
    private val checkIntervalMs: Long = 500
) {
    private var job: Job? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val retryState = mutableMapOf<String, RetryState>()
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
                // Log error but continue with other consumers
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

        // Check each topic the consumer is interested in
        for ((topicName, lastEventIdStr) in consumer.topics) {
            if (topicName != topic) continue

            try {
                val lastEventId = lastEventIdStr?.let { EventId(it) }
                val events = eventRepository.getEvents(
                    topic = topicName,
                    sinceEventId = lastEventId
                )

                if (events.isNotEmpty()) {
                    eventsToDeliver.addAll(events)

                    // Update the last consumed event ID
                    val latestEventId = events.last().id.value
                    val updatedConsumer = consumer.withUpdatedLastEventId(topicName, latestEventId)
                    consumerRepository.save(updatedConsumer)
                }
            } catch (e: Exception) {
                // Log error but continue
            }
        }

        if (eventsToDeliver.isEmpty()) {
            return
        }

        // Sort events by ID to ensure proper ordering
        eventsToDeliver.sortWith { a, b -> compareEventIds(a.id, b.id) }

        // Deliver events using the consumer's deliver method
        val result = consumer.deliver(eventsToDeliver)

        if (result.success) {
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

    private fun compareEventIds(id1: EventId, id2: EventId): Int {
        if (id1.topic != id2.topic) {
            return id1.topic.compareTo(id2.topic)
        }
        return id1.sequence.compareTo(id2.sequence)
    }
}
