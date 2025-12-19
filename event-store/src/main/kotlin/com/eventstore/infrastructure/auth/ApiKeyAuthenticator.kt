package com.eventstore.infrastructure.auth

import com.eventstore.domain.exceptions.InvalidApiKeyException
import com.eventstore.domain.ports.outbound.ApiKeyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

data class ApiKeyAuthResult(
    val userId: String,
    val apiKeyId: String
)

class ApiKeyAuthenticator(
    private val apiKeyRepository: ApiKeyRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    suspend fun authenticate(apiKey: String): ApiKeyAuthResult {
        // Hash the provided key
        val keyHash = ApiKeyHasher.hash(apiKey)

        // Look up API key by hash
        val storedKey = apiKeyRepository.findByKeyHash(keyHash)
            ?: throw InvalidApiKeyException("API key not found")

        // Validate key is active (not revoked, not expired)
        if (!storedKey.isActive) {
            throw InvalidApiKeyException(
                when {
                    storedKey.revokedAt != null -> "API key has been revoked"
                    storedKey.expiresAt != null && storedKey.expiresAt.isBefore(Instant.now()) -> "API key has expired"
                    else -> "API key is not active"
                }
            )
        }

        // Update lastUsedAt timestamp (async, non-blocking)
        scope.launch {
            try {
                val updatedKey = storedKey.copy(lastUsedAt = Instant.now())
                apiKeyRepository.save(updatedKey)
            } catch (e: Exception) {
                // Log but don't fail authentication if update fails
                // This is a best-effort update
            }
        }

        return ApiKeyAuthResult(
            userId = storedKey.userId,
            apiKeyId = storedKey.id
        )
    }
}

