package com.eventstore.infrastructure.projections

import com.eventstore.domain.ApiKey
import com.eventstore.domain.Event
import com.eventstore.domain.events.ApiKeyCreatedEvent
import com.eventstore.domain.events.ApiKeyEventType
import com.eventstore.domain.events.ApiKeyRevokedEvent
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import com.eventstore.domain.ports.outbound.DeliveryResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class ApiKeyProjectionService(
    private val apiKeyRepository: ApiKeyRepository
) {
    private val logger = LoggerFactory.getLogger(ApiKeyProjectionService::class.java)
    private val mutex = Mutex()

    suspend fun handleEvents(events: List<Event>): DeliveryResult {
        if (events.isEmpty()) return DeliveryResult(success = true)
        return try {
            mutex.withLock { events.forEach { applyEvent(it) } }
            DeliveryResult(success = true)
        } catch (e: Exception) {
            logger.error("Failed to apply API key events", e)
            DeliveryResult(success = false, error = e.message)
        }
    }

    suspend fun getApiKey(id: String): ApiKey? = apiKeyRepository.findById(id)

    suspend fun getApiKeyByKeyHash(keyHash: String): ApiKey? = apiKeyRepository.findByKeyHash(keyHash)

    suspend fun getApiKeysByUserId(userId: String): List<ApiKey> = apiKeyRepository.findByUserId(userId)

    private suspend fun applyEvent(event: Event) {
        when (event.type) {
            ApiKeyEventType.CREATED -> {
                val payload = ApiKeyCreatedEvent.fromPayload(event.payload)
                val apiKey = ApiKey(
                    id = payload.apiKeyId,
                    userId = payload.userId,
                    keyHash = payload.keyHash,
                    name = payload.name,
                    description = payload.description,
                    createdAt = payload.createdAt,
                    expiresAt = payload.expiresAt,
                    lastUsedAt = null,
                    revokedAt = null,
                    scopes = payload.scopes
                )
                apiKeyRepository.save(apiKey)
            }

            ApiKeyEventType.REVOKED -> {
                val payload = ApiKeyRevokedEvent.fromPayload(event.payload)
                val existing = apiKeyRepository.findById(payload.apiKeyId) ?: return
                val updated = existing.copy(
                    revokedAt = payload.revokedAt
                )
                apiKeyRepository.save(updated)
            }

            else -> logger.debug("Ignoring non-API key event type ${event.type}")
        }
    }
}

