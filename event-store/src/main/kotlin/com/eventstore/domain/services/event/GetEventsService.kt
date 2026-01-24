package com.eventstore.domain.services.event

import com.eventstore.domain.Event
import com.eventstore.domain.EventId
import com.eventstore.domain.exceptions.TopicNotFoundException
import com.eventstore.domain.ports.outbound.EventRepository
import com.eventstore.domain.ports.outbound.TopicRepository
import java.util.*

class GetEventsService(
    private val eventRepository: EventRepository,
    private val topicRepository: TopicRepository,
) {
    suspend fun execute(
        topicId: UUID,
        sinceEventId: String? = null,
        date: String? = null,
        limit: Int? = null,
    ): List<Event> {
        // Validate topic exists
        if (!topicRepository.topicExists(topicId)) {
            throw TopicNotFoundException(topicId.toString())
        }

        val sinceId = sinceEventId?.let { EventId.fromString(it) }

        return eventRepository.getEvents(
            topicId = topicId,
            sinceEventId = sinceId,
            date = date,
            limit = limit,
        )
    }
}
