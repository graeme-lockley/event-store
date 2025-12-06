package com.eventstore.domain

import java.net.URL

/**
 * Domain entity representing a consumer that receives events via webhook.
 */
data class Consumer(
    val id: String,
    val callback: URL,
    val topics: Map<String, String?> // topic -> lastEventId (null if starting from beginning)
) {
    init {
        require(id.isNotBlank()) { "Consumer ID is required" }
        require(topics.isNotEmpty()) { "Consumer must subscribe to at least one topic" }
    }

    fun updateLastEventId(topic: String, eventId: String): Consumer {
        return copy(topics = topics + (topic to eventId))
    }
}

