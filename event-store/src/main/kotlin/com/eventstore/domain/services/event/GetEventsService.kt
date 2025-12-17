package com.eventstore.domain.services.event

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository

class GetEventsService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository
) {
    suspend fun execute(
        topic: String,
        sinceEventId: String? = null,
        date: String? = null,
        limit: Int? = null
    ): List<Event> {
        // Validate topic exists
        if (!topicRepository.topicExists(topic)) {
            throw TopicNotFoundException(topic)
        }

        val sinceId = sinceEventId?.let { EventId(it) }

        return eventRepository.getEvents(
            topic = topic,
            sinceEventId = sinceId,
            date = date,
            limit = limit
        )
    }
}

