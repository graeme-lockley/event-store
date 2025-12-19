package com.eventstore.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class ApiKeyTest {

    @Test
    fun `isActive returns true when not revoked and not expired`() {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(3600),
            expiresAt = null,
            revokedAt = null
        )
        assertTrue(apiKey.isActive)
    }

    @Test
    fun `isActive returns false when revoked`() {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(3600),
            expiresAt = null,
            revokedAt = Instant.now()
        )
        assertFalse(apiKey.isActive)
    }

    @Test
    fun `isActive returns false when expired`() {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(7200),
            expiresAt = Instant.now().minusSeconds(3600),
            revokedAt = null
        )
        assertFalse(apiKey.isActive)
    }

    @Test
    fun `isActive returns true when expiresAt is in the future`() {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(3600),
            expiresAt = Instant.now().plusSeconds(3600),
            revokedAt = null
        )
        assertTrue(apiKey.isActive)
    }

    @Test
    fun `isActive returns false when both revoked and expired`() {
        val apiKey = ApiKey(
            id = "test-id",
            userId = "user-id",
            keyHash = "hash",
            name = "Test Key",
            createdAt = Instant.now().minusSeconds(7200),
            expiresAt = Instant.now().minusSeconds(3600),
            revokedAt = Instant.now()
        )
        assertFalse(apiKey.isActive)
    }
}

