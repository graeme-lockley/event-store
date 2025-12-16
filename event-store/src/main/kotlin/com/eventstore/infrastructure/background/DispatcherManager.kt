package com.eventstore.infrastructure.background

import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DispatcherManager(
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository
) : EventDispatcher {
    private val dispatchers = mutableMapOf<String, TopicDispatcher>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun startDispatcher(topic: String): Boolean {
        return mutex.withLock {
            if (dispatchers.containsKey(topic)) {
                return false // Dispatcher already existed
            }

            val dispatcher = TopicDispatcher(
                topic = topic,
                consumerRepository = consumerRepository,
                eventRepository = eventRepository
            )

            dispatcher.start(scope)
            dispatchers[topic] = dispatcher
            true // New dispatcher was started
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

    override suspend fun notifyEventsPublished(topics: Set<String>) {
        for (topic in topics) {
            triggerDelivery(topic)
        }
    }
    
    override suspend fun ensureDispatchersRunning(topics: Set<String>) {
        for (topic in topics) {
            val wasNew = startDispatcher(topic)
            
            // If we just started a new dispatcher, trigger immediate delivery check
            // This ensures catchup happens immediately when a consumer is registered
            if (wasNew) {
                triggerDelivery(topic)
            }
        }
    }
}
