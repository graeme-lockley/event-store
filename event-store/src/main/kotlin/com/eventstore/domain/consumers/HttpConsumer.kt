package com.eventstore.domain.consumers

import com.eventstore.domain.Consumer
import com.eventstore.domain.ConsumerType
import com.eventstore.domain.Event
import com.eventstore.domain.ports.outbound.DeliveryResult
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.URL
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

class HttpConsumer(
    id: String,
    val callbackUrl: URL,
    topics: Map<String, String?>,
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val timeoutSeconds: Long = 30
) : Consumer(id, topics) {

    override fun getType(): ConsumerType = ConsumerType.HTTP

    override suspend fun deliver(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) {
            return DeliveryResult(success = true)
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
                consumerId = id,
                events = eventDtos
            )

            val response = withTimeout(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                httpClient.post(callbackUrl.toString()) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            }

            if (response.status.isSuccess()) {
                return DeliveryResult(success = true)
            } else {
                return DeliveryResult(
                    success = false,
                    error = "HTTP ${response.status.value}: ${response.status.description}"
                )
            }
        } catch (e: TimeoutCancellationException) {
            return DeliveryResult(
                success = false,
                error = "Request timeout after ${timeoutSeconds}s"
            )
        } catch (e: Exception) {
            return DeliveryResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    override fun toString(): String {
        return "HttpConsumer(id=$id, callbackUrl=$callbackUrl, topics=$topics)"
    }

    override fun withUpdatedLastEventId(topic: String, eventId: String): Consumer {
        return HttpConsumer(
            id = id,
            callbackUrl = callbackUrl,
            topics = topics + (topic to eventId),
            httpClient = httpClient,
            timeoutSeconds = timeoutSeconds
        )
    }

    companion object {
        private fun createDefaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(ContentNegotiation) {
                    jackson()
                }
                engine {
                    requestTimeout = 30_000
                }
            }
        }
    }
}

