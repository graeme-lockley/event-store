package com.eventstore.infrastructure.auth

import com.eventstore.domain.exceptions.InvalidApiKeyException
import com.eventstore.infrastructure.persistence.InMemoryApiKeyRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ApiKeyAuthenticatorTest {

    private lateinit var apiKeyRepository: InMemoryApiKeyRepository
    private lateinit var apiKeyAuthenticator: ApiKeyAuthenticator

    @BeforeEach
    fun setUp() {
        apiKeyRepository = InMemoryApiKeyRepository()
        apiKeyAuthenticator = ApiKeyAuthenticator(apiKeyRepository)
    }

    @Test
    fun `authenticates valid API key`() = runBlocking {
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = keyHash,
            name = "Test Key",
            createdAt = Instant.now()
        )

        apiKeyRepository.save(apiKey)

        val result = apiKeyAuthenticator.authenticate(plainKey)

        assertEquals("user-id", result.userId)
        assertEquals("test-id", result.apiKeyId)
    }

    @Test
    fun `throws exception for invalid API key`() = runBlocking {
        try {
            apiKeyAuthenticator.authenticate("es_invalidkey")
            fail("Should have thrown InvalidApiKeyException")
        } catch (e: InvalidApiKeyException) {
            // Expected
        }
    }

    @Test
    fun `throws exception for revoked API key`() = runBlocking {
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = keyHash,
            name = "Test Key",
            createdAt = Instant.now(),
            revokedAt = Instant.now()
        )

        apiKeyRepository.save(apiKey)

        try {
            apiKeyAuthenticator.authenticate(plainKey)
            fail("Should have thrown InvalidApiKeyException")
        } catch (e: InvalidApiKeyException) {
            // Expected
        }
    }

    @Test
    fun `throws exception for expired API key`() = runBlocking {
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = keyHash,
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(7200),
            expiresAt = Instant.now().minusSeconds(3600)
        )

        apiKeyRepository.save(apiKey)

        try {
            apiKeyAuthenticator.authenticate(plainKey)
            fail("Should have thrown InvalidApiKeyException")
        } catch (e: InvalidApiKeyException) {
            // Expected
        }
    }

    @Test
    fun `updates lastUsedAt timestamp`() = runBlocking {
        val plainKey = ApiKeyGenerator.generate()
        val keyHash = ApiKeyHasher.hash(plainKey)

        val apiKey = com.eventstore.domain.ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = keyHash,
            name = "Test Key",
            createdAt = Instant.now(),
            lastUsedAt = null
        )

        apiKeyRepository.save(apiKey)

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10)

        apiKeyAuthenticator.authenticate(plainKey)

        // Wait for async update
        Thread.sleep(100)

        val retrieved = apiKeyRepository.findById("test-id")
        assertNotNull(retrieved?.lastUsedAt)
    }
}

