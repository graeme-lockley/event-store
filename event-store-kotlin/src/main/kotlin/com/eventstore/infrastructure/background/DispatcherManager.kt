package com.eventstore.infrastructure.background

import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.ConsumerDeliveryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DispatcherManager(
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository,
    private val deliveryService: ConsumerDeliveryService
) {
    private val dispatchers = mutableMapOf<String, TopicDispatcher>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun startDispatcher(topic: String) {
        mutex.withLock {
            if (dispatchers.containsKey(topic)) {
                return
            }

            val dispatcher = TopicDispatcher(
                topic = topic,
                consumerRepository = consumerRepository,
                eventRepository = eventRepository,
                deliveryService = deliveryService
            )

            dispatcher.start(scope)
            dispatchers[topic] = dispatcher
        }
    }

    suspend fun stopDispatcher(topic: String) {
        mutex.withLock {
            dispatchers[topic]?.stop()
            dispatchers.remove(topic)
        }
    }

    suspend fun stopAllDispatchers() {
        mutex.withLock {
            dispatchers.values.forEach { it.stop() }
            dispatchers.clear()
        }
    }

    suspend fun isDispatcherRunning(topic: String): Boolean {
        return mutex.withLock {
            dispatchers[topic]?.isRunning?.value == true
        }
    }

    suspend fun triggerDelivery(topic: String) {
        mutex.withLock {
            dispatchers[topic]?.triggerDelivery()
        }
    }

    suspend fun getRunningDispatchers(): List<String> {
        return mutex.withLock {
            dispatchers.filter { it.value.isRunning.value }.keys.toList()
        }
    }
}

