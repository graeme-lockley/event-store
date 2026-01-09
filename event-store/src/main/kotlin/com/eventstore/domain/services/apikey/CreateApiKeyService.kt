package com.eventstore.domain.services.apikey

import com.eventstore.Config
import com.eventstore.domain.ApiKey
import com.eventstore.domain.events.ApiKeyCreatedEvent
import com.eventstore.domain.events.ApiKeyEventType
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.services.BaseSystemService
import com.eventstore.domain.services.SystemEventPublisher
import com.eventstore.domain.tenants.SystemTopics
import com.eventstore.infrastructure.auth.ApiKeyGenerator
import com.eventstore.infrastructure.auth.ApiKeyHasher
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant
import java.util.*

data class CreateApiKeyRequest(
    val userId: String,
    val name: String,
    val description: String? = null,
    val expiresAt: Instant? = null,
    val scopes: Set<String>? = null,
    val createdBy: String = "system"
)

class CreateApiKeyService(
    private val userProjectionService: UserProjectionService,
    config: Config,
    eventPublisher: SystemEventPublisher
) : BaseSystemService(config, eventPublisher) {
    suspend fun execute(request: CreateApiKeyRequest): Pair<ApiKey, String> {
        // Validate user exists
        userProjectionService.getUser(request.userId)
            ?: throw UserNotFoundException(request.userId)

        // Generate API key
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        // Create API key domain object
        val now = Instant.now()
        val apiKeyId = UUID.randomUUID().toString()
        val apiKey = ApiKey(
            id = apiKeyId,
            userId = request.userId,
            keyHash = keyHash,
            name = request.name,
            description = request.description,
            createdAt = now,
            expiresAt = request.expiresAt,
            scopes = request.scopes
        )

        // Publish event
        val payload = ApiKeyCreatedEvent(
            apiKeyId = apiKeyId,
            userId = request.userId,
            keyHash = keyHash,
            name = request.name,
            description = request.description,
            createdAt = now,
            expiresAt = request.expiresAt,
            scopes = request.scopes,
            createdBy = request.createdBy
        )

        eventPublisher.publishEvent(
            topic = SystemTopics.API_KEYS_TOPIC,
            eventType = ApiKeyEventType.CREATED,
            payload = payload.toPayload(),
            timestamp = now
        )

        // Return both domain object and plain key (only time plain key is returned)
        return apiKey to plainKey
    }
}
