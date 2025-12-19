package com.eventstore.infrastructure.auth

import java.security.MessageDigest

object ApiKeyHasher {
    fun hash(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(key.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(key: String, hash: String): Boolean {
        return hash(key) == hash
    }
}

