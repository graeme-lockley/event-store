package com.eventstore.interfaces.dto

data class EventResponse(
    val eventIds: List<String>
)

data class EventDto(
    val id: String,
    val timestamp: String,
    val type: String,
    val payload: Map<String, Any>
)

data class EventsResponse(
    val events: List<EventDto>
)

