package com.eventstore.domain.consumers

import com.eventstore.domain.Consumer
import com.eventstore.domain.ConsumerType
import com.eventstore.domain.Event
import com.eventstore.domain.ports.outbound.DeliveryResult
import java.util.*

class InMemoryConsumer(
    id: String,
    private val handler: suspend (List<Event>) -> DeliveryResult,
    topics: Map<UUID, String?>
) : Consumer(id, topics) {

    override fun getType(): ConsumerType = ConsumerType.IN_MEMORY

    override suspend fun deliver(events: List<Event>): DeliveryResult {
        return handler(events)
    }

    override fun toString(): String {
        return "InMemoryConsumer(id=$id, topics=$topics)"
    }

    override fun withUpdatedLastEventId(topicId: UUID, eventId: String): Consumer {
        return InMemoryConsumer(
            id = id,
            handler = handler,
            topics = topics + (topicId to eventId)
        )
    }
}

