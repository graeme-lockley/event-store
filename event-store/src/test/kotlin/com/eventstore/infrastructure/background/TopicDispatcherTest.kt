package com.eventstore.infrastructure.background

import com.eventstore.domain.Event
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.createEventStore
import com.eventstore.domain.services.event.InMemoryEventDispatcher
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TopicDispatcherTest {
    private val stubEventDispatcher = InMemoryEventDispatcher()
    @Test
    fun `events are dispatched with the event state being saved after a successful dispatch`() = runTest {
        val topicName = "user-events"
        val helper = createEventStore(topicName)

        val deliveredEvents = mutableListOf<List<Event>>()
        val handler: suspend (List<Event>) -> DeliveryResult = { events ->
            deliveredEvents.add(events)
            DeliveryResult(success = true)
        }

        val consumerFactory = ConsumerFactoryImpl()
        val registrationRequest = InMemoryConsumerRegistrationRequest(
            handler = handler,
            topics = mapOf(topicName to null)
        )
        
        val consumerId = RegisterConsumerService(
            helper.consumerRepository,
            helper.topicRepository,
            consumerFactory,
            stubEventDispatcher
        ).execute(registrationRequest, "default", "default")

        // After registration, topics are stored as qualified names (tenant/namespace/topic)
        val qualifiedTopicName = "default/default/$topicName"
        
        val dispatcher = TopicDispatcher(
            qualifiedTopicName,
            helper.consumerRepository,
            helper.eventRepository
        )

        dispatcher.triggerDelivery()

        // Verify events were delivered
        assertEquals(1, deliveredEvents.size)
        assertEquals(3, deliveredEvents[0].size) // 3 events were created in createEventStore

        // Verify consumer was updated with last event ID
        val consumer = helper.findConsumer(consumerId)
        assertNotNull(consumer)
        // Consumer stores topics as qualified names
        val lastEventId = consumer.topics[qualifiedTopicName]
        assertNotNull(lastEventId)
        assertEquals(lastEventId, deliveredEvents[0].last().id.value)
    }
}
