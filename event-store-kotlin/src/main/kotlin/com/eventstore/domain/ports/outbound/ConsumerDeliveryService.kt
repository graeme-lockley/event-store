package com.eventstore.domain.ports.outbound

import com.eventstore.domain.Consumer
import com.eventstore.domain.Event

/**
 * Outbound port for delivering events to consumers via webhooks.
 */
interface ConsumerDeliveryService {
    suspend fun deliverEvents(consumer: Consumer, events: List<Event>): DeliveryResult
}

data class DeliveryResult(
    val success: Boolean,
    val error: String? = null
)

