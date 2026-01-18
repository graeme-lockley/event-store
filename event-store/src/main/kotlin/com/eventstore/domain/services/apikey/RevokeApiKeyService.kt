package com.eventstore.domain.services.apikey

import com.eventstore.Config
import com.eventstore.domain.events.ApiKeyEventType
import com.eventstore.domain.events.ApiKeyRevokedEvent
import com.eventstore.domain.exceptions.ApiKeyAlreadyRevokedException
import com.eventstore.domain.exceptions.ApiKeyNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.projections.ApiKeyProjectionService
import java.time.Instant

data class RevokeApiKeyRequest(
    val keyId: String,
    val revokedBy: String = "system",
    val reason: String? = null
)

class RevokeApiKeyService(
    private val apiKeyProjectionService: ApiKeyProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: RevokeApiKeyRequest) {
        val apiKey = apiKeyProjectionService.getApiKey(request.keyId)
            ?: throw ApiKeyNotFoundException(request.keyId)

        if (apiKey.revokedAt != null) {
            throw ApiKeyAlreadyRevokedException(request.keyId)
        }

        val now = Instant.now()
        val payload = ApiKeyRevokedEvent(
            apiKeyId = request.keyId,
            revokedBy = request.revokedBy,
            revokedAt = now,
            reason = request.reason
        )

        eventPublisher.publishEvent(
            topicId = SystemTopics.API_KEYS_TOPIC_ID,
            eventType = ApiKeyEventType.REVOKED,
            payload = payload.toPayload(),
            timestamp = now
        )
    }
}



