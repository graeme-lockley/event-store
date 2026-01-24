package com.eventstore.infrastructure.background

import com.eventstore.domain.Event
import com.eventstore.domain.ports.outbound.DeliveryResult
import com.eventstore.domain.services.InMemoryEventDispatcher
import com.eventstore.domain.services.consumer.InMemoryConsumerRegistrationRequest
import com.eventstore.domain.services.consumer.RegisterConsumerService
import com.eventstore.domain.services.createEventStore
import com.eventstore.infrastructure.factories.ConsumerFactoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TopicDispatcherTest {
    private val stubEventDispatcher = InMemoryEventDispatcher()

    @Test
    fun `events are dispatched with the event state being saved after a successful dispatch`() =
        runTest {
            val topicName = "user-events"
            val helper = createEventStore(topicName)
            val topicId = helper.topicId ?: UUID.randomUUID()

            val deliveredEvents = mutableListOf<List<Event>>()
            val handler: suspend (List<Event>) -> DeliveryResult = { events ->
                deliveredEvents.add(events)
                DeliveryResult(success = true)
            }

            val consumerFactory = ConsumerFactoryImpl()
            val registrationRequest =
                InMemoryConsumerRegistrationRequest(
                    handler = handler,
                    topics = mapOf(topicId to null),
                )

            val consumerId =
                RegisterConsumerService(
                    helper.consumerRepository,
                    helper.topicRepository,
                    consumerFactory,
                    stubEventDispatcher,
                ).execute(registrationRequest)

            val dispatcher =
                TopicDispatcher(
                    topicId = topicId,
                    consumerRepository = helper.consumerRepository,
                    eventRepository = helper.eventRepository,
                )

            dispatcher.triggerDelivery()

            // Verify events were delivered
            assertEquals(1, deliveredEvents.size)
            assertEquals(3, deliveredEvents[0].size) // 3 events were created in createEventStore

            // Verify consumer was updated with last event ID
            val consumer = helper.findConsumer(consumerId)
            assertNotNull(consumer)
            // Consumer stores topics as Map<UUID, String?>
            val lastEventId = consumer.topics[topicId]
            assertNotNull(lastEventId)
            assertEquals(lastEventId, deliveredEvents[0].last().id.value)
        }
}
