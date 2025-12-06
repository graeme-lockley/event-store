package com.eventstore.domain

import java.time.Instant

/**
 * Domain entity representing an event.
 */
data class Event(
    val id: EventId,
    val timestamp: Instant,
    val type: String,
    val payload: Map<String, Any>
) {
    init {
        require(type.isNotBlank()) { "Event type is required" }
    }
}

