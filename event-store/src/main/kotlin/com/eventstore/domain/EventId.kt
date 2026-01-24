package com.eventstore.domain

import java.util.*

/**
 * Value object representing a globally unique event ID.
 *
 * Format: <topicId>/<sequence> (e.g., "550e8400-e29b-41d4-a716-446655440000/42")
 */
data class EventId(
    val topicId: UUID,
    val sequence: Long,
) {
    init {
        require(sequence >= 0) { "Sequence must be non-negative" }
    }

    companion object {
        private val EVENT_ID_PATTERN = Regex("^[^/]+/[0-9]+$")

        /**
         * Parses an EventId from a string format.
         * Format: <topicId>/<sequence> (e.g., "550e8400-e29b-41d4-a716-446655440000/42")
         */
        fun fromString(value: String): EventId {
            require(value.matches(EVENT_ID_PATTERN)) {
                "Event ID must be in format '<topicId>/<sequence>'"
            }

            val parts = value.split("/", limit = 2)
            require(parts.size == 2) {
                "Event ID must have exactly 2 parts separated by '/': '<topicId>/<sequence>'"
            }

            return EventId(
                topicId = UUID.fromString(parts[0]),
                sequence = parts[1].toLong(),
            )
        }

        /**
         * Creates an EventId from its components.
         */
        fun create(
            topicId: UUID,
            sequence: Long,
        ): EventId {
            return EventId(topicId, sequence)
        }
    }

    /**
     * Computed property for backward compatibility.
     * Returns the formatted string representation.
     */
    val value: String
        get() = toString()

    override fun toString(): String = "$topicId/$sequence"
}
