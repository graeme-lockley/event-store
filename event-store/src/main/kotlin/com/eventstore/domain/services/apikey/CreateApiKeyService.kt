package com.eventstore.domain.services.apikey

import com.eventstore.domain.ApiKey
import com.eventstore.domain.exceptions.UserNotFoundException
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import com.eventstore.infrastructure.auth.ApiKeyGenerator
import com.eventstore.infrastructure.auth.ApiKeyHasher
import com.eventstore.infrastructure.projections.UserProjectionService
import java.time.Instant
import java.util.UUID

data class CreateApiKeyRequest(
    val userId: String,
    val name: String,
    val description: String? = null,
    val expiresAt: Instant? = null,
    val scopes: Set<String>? = null
)

class CreateApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val userProjectionService: UserProjectionService
) {
    suspend fun execute(request: CreateApiKeyRequest): Pair<ApiKey, String> {
        // Validate user exists
        val user = userProjectionService.getUser(request.userId)
            ?: throw UserNotFoundException(request.userId)

        // Generate API key
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        // Create API key domain object
        val now = Instant.now()
        val apiKey = ApiKey(
            id = UUID.randomUUID().toString(),
            userId = request.userId,
            keyHash = keyHash,
            name = request.name,
            description = request.description,
            createdAt = now,
            expiresAt = request.expiresAt,
            scopes = request.scopes
        )

        // Save to repository
        apiKeyRepository.save(apiKey)

        // Return both domain object and plain key (only time plain key is returned)
        return apiKey to plainKey
    }
}

