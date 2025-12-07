package com.eventstore.domain

/**
 * Value object representing a globally unique event ID.
 * Format: <topic>-<sequence>
 * Example: "user-events-42"
 */
data class EventId(val value: String) {
    init {
        require(value.matches(EVENT_ID_PATTERN)) {
            "Event ID must be in format '<topic>-<sequence>'"
        }
    }

    val topic: String
        get() = value.substringBeforeLast("-")

    val sequence: Long
        get() = value.substringAfterLast("-").toLong()

    override fun toString(): String = value

    companion object {
        private val EVENT_ID_PATTERN = Regex("^.+-[0-9]+$")
        
        fun create(topic: String, sequence: Long): EventId {
            return EventId("$topic-$sequence")
        }
    }
}

