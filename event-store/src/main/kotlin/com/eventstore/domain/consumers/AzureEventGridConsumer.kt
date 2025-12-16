package com.eventstore.domain.consumers

import com.eventstore.domain.Consumer
import com.eventstore.domain.ConsumerType
import com.eventstore.domain.Event
import com.eventstore.domain.ports.outbound.DeliveryResult

class AzureEventGridConsumer(
    id: String,
    val endpointUrl: String,
    val accessKey: String,
    topics: Map<String, String?>
) : Consumer(id, topics) {

    override fun getType(): ConsumerType = ConsumerType.AZURE_EVENT_GRID

    override suspend fun deliver(events: List<Event>): DeliveryResult {
        // TODO: Azure Event Grid delivery logic

        return DeliveryResult(
            success = false,
            error = "Azure Event Grid delivery not yet implemented"
        )
    }

    override fun toString(): String {
        return "AzureEventGridConsumer(id=$id, endpointUrl=$endpointUrl, topics=$topics)"
    }

    override fun withUpdatedLastEventId(topic: String, eventId: String): Consumer {
        return AzureEventGridConsumer(
            id = id,
            endpointUrl = endpointUrl,
            accessKey = accessKey,
            topics = topics + (topic to eventId)
        )
    }
}

