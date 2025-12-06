package com.eventstore.domain

/**
 * Value object representing a globally unique event ID.
 * Format: <topic>-<sequence>
 * Example: "user-events-42"
 */
data class EventId(val value: String) {
    init {
        require(value.matches(Regex("^.+-[0-9]+$"))) {
            "Event ID must be in format '<topic>-<sequence>'"
        }
    }

    val topic: String
        get() = value.substringBeforeLast("-")

    val sequence: Long
        get() = value.substringAfterLast("-").toLong()

    companion object {
        fun create(topic: String, sequence: Long): EventId {
            return EventId("$topic-$sequence")
        }
    }
}

