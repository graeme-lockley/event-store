package com.eventstore.infrastructure.background

import com.eventstore.domain.ports.outbound.ConsumerRepository
import com.eventstore.domain.ports.outbound.EventDispatcher
import com.eventstore.domain.ports.outbound.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class AsyncDispatcherManager(
    private val consumerRepository: ConsumerRepository,
    private val eventRepository: EventRepository
) : EventDispatcher {
    private val dispatchers = mutableMapOf<UUID, TopicDispatcher>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun startDispatcher(topicId: UUID): Boolean {
        return mutex.withLock {
            if (dispatchers.containsKey(topicId)) {
                return false // Dispatcher already existed
            }

            val dispatcher = TopicDispatcher(
                topicId = topicId,
                consumerRepository = consumerRepository,
                eventRepository = eventRepository
            )

            dispatcher.start(scope)
            dispatchers[topicId] = dispatcher
            true // New dispatcher was started
        }
    }

    suspend fun stopDispatcher(topicId: UUID) {
        mutex.withLock {
            dispatchers[topicId]?.stop()
            dispatchers.remove(topicId)
        }
    }

    suspend fun stopAllDispatchers() {
        mutex.withLock {
            dispatchers.values.forEach { it.stop() }
            dispatchers.clear()
        }
    }

    suspend fun isDispatcherRunning(topicId: UUID): Boolean {
        return mutex.withLock {
            dispatchers[topicId]?.isRunning?.value == true
        }
    }

    suspend fun triggerDelivery(topicId: UUID) {
        mutex.withLock {
            dispatchers[topicId]?.triggerDelivery()
        }
    }

    suspend fun getRunningDispatchers(): List<UUID> {
        return mutex.withLock {
            dispatchers.filter { it.value.isRunning.value }.keys.toList()
        }
    }

    override suspend fun notifyEventPublished(topicId: UUID) {
        triggerDelivery(topicId)
    }

    override suspend fun notifyEventsPublished(topicIds: Set<UUID>) {
        for (topicId in topicIds) {
            triggerDelivery(topicId)
        }
    }

    override suspend fun ensureDispatchersRunning(topicIds: Set<UUID>) {
        for (topicId in topicIds) {
            val wasNew = startDispatcher(topicId)

            // If we just started a new dispatcher, trigger immediate delivery check
            // This ensures catchup happens immediately when a consumer is registered
            if (wasNew) {
                triggerDelivery(topicId)
            }
        }
    }
}
