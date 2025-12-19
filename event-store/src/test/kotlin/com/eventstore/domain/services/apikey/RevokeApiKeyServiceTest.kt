package com.eventstore.domain.services.apikey

import com.eventstore.domain.exceptions.ApiKeyAlreadyRevokedException
import com.eventstore.domain.exceptions.ApiKeyNotFoundException
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class RevokeApiKeyServiceTest {

    private lateinit var apiKeyRepository: InMemoryApiKeyRepository
    private lateinit var revokeApiKeyService: RevokeApiKeyService

    @BeforeEach
    fun setUp() {
        apiKeyRepository = InMemoryApiKeyRepository()
        revokeApiKeyService = RevokeApiKeyService(apiKeyRepository)
    }

    @Test
    fun `revokes API key successfully`() = runBlocking {
        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now(),
            revokedAt = null
        )

        apiKeyRepository.save(apiKey)
        revokeApiKeyService.execute(RevokeApiKeyRequest(keyId = "test-id"))

        val retrieved = apiKeyRepository.findById("test-id")
        assertNotNull(retrieved)
        assertNotNull(retrieved?.revokedAt)
        assertFalse(retrieved?.isActive ?: true)
    }

    @Test
    fun `throws exception when API key does not exist`() = runBlocking {
        try {
            revokeApiKeyService.execute(RevokeApiKeyRequest(keyId = "non-existent"))
            fail("Should have thrown ApiKeyNotFoundException")
        } catch (e: ApiKeyNotFoundException) {
            // Expected
        }
    }

    @Test
    fun `throws exception when API key already revoked`() = runBlocking {
        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now(),
            revokedAt = Instant.now()
        )

        apiKeyRepository.save(apiKey)

        try {
            revokeApiKeyService.execute(RevokeApiKeyRequest(keyId = "test-id"))
            fail("Should have thrown ApiKeyAlreadyRevokedException")
        } catch (e: ApiKeyAlreadyRevokedException) {
            // Expected
        }
    }
}

