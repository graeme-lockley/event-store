package com.eventstore.infrastructure.external

import com.eventstore.domain.ports.outbound.ConsumerDeliveryService
import com.eventstore.domain.Consumer
import com.eventstore.domain.Event
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

data class DeliveryPayload(
    val consumerId: String,
    val events: List<EventDto>
)

data class EventDto(
    val id: String,
    val timestamp: String,
    val type: String,
    val payload: Map<String, Any>
)

class HttpConsumerDeliveryService(
    private val objectMapper: ObjectMapper,
    private val timeoutSeconds: Long = 30
) : ConsumerDeliveryService {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        engine {
            requestTimeout = timeoutSeconds * 1000
        }
    }

    override suspend fun deliverEvents(
        consumer: Consumer,
        events: List<Event>
    ): com.eventstore.domain.ports.outbound.DeliveryResult {
        if (events.isEmpty()) {
            return com.eventstore.domain.ports.outbound.DeliveryResult(success = true)
        }

        try {
            val eventDtos = events.map { event ->
                EventDto(
                    id = event.id.value,
                    timestamp = event.timestamp.toString(),
                    type = event.type,
                    payload = event.payload
                )
            }

            val payload = DeliveryPayload(
                consumerId = consumer.id,
                events = eventDtos
            )

            val response = withTimeout(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                httpClient.post(consumer.callback.toString()) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            }

            if (response.status.isSuccess()) {
                return com.eventstore.domain.ports.outbound.DeliveryResult(success = true)
            } else {
                return com.eventstore.domain.ports.outbound.DeliveryResult(
                    success = false,
                    error = "HTTP ${response.status.value}: ${response.status.description}"
                )
            }
        } catch (e: TimeoutCancellationException) {
            return com.eventstore.domain.ports.outbound.DeliveryResult(
                success = false,
                error = "Request timeout after ${timeoutSeconds}s"
            )
        } catch (e: Exception) {
            return com.eventstore.domain.ports.outbound.DeliveryResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
}

