package com.eventstore.infrastructure.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiKeyHasherTest {

    @Test
    fun `hash produces consistent output`() {
        val key = "es_test123456789"
        val hash1 = ApiKeyHasher.hash(key)
        val hash2 = ApiKeyHasher.hash(key)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hash produces different output for different keys`() {
        val key1 = "es_test123456789"
        val key2 = "es_test987654321"
        val hash1 = ApiKeyHasher.hash(key1)
        val hash2 = ApiKeyHasher.hash(key2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hash produces hex string`() {
        val key = "es_test123456789"
        val hash = ApiKeyHasher.hash(key)
        // SHA-256 produces 256 bits = 32 bytes = 64 hex characters
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `verify returns true for correct key and hash`() {
        val key = "es_test123456789"
        val hash = ApiKeyHasher.hash(key)
        assertTrue(ApiKeyHasher.verify(key, hash))
    }

    @Test
    fun `verify returns false for incorrect key`() {
        val key1 = "es_test123456789"
        val key2 = "es_test987654321"
        val hash = ApiKeyHasher.hash(key1)
        assertFalse(ApiKeyHasher.verify(key2, hash))
    }

    @Test
    fun `verify returns false for incorrect hash`() {
        val key = "es_test123456789"
        val hash = ApiKeyHasher.hash(key)
        val wrongHash = hash.reversed()
        assertFalse(ApiKeyHasher.verify(key, wrongHash))
    }
}

