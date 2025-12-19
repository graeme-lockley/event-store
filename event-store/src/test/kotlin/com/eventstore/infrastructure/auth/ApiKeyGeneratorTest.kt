package com.eventstore.infrastructure.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiKeyGeneratorTest {

    @Test
    fun `generates key with correct prefix`() {
        val key = ApiKeyGenerator.generate()
        assertTrue(key.startsWith("es_"))
    }

    @Test
    fun `generates unique keys`() {
        val key1 = ApiKeyGenerator.generate()
        val key2 = ApiKeyGenerator.generate()
        assertNotEquals(key1, key2)
    }

    @Test
    fun `generates keys of correct length`() {
        val key = ApiKeyGenerator.generate()
        // es_ prefix (3 chars) + 32 bytes base64url encoded (43 chars) = 46 chars
        assertEquals(46, key.length)
    }

    @Test
    fun `generates multiple unique keys`() {
        val keys = (1..100).map { ApiKeyGenerator.generate() }.toSet()
        assertEquals(100, keys.size) // All should be unique
    }
}

