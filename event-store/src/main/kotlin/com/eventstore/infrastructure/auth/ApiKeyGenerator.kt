package com.eventstore.infrastructure.auth

import java.security.SecureRandom
import java.util.Base64

object ApiKeyGenerator {
    private const val PREFIX = "es_"
    private const val KEY_LENGTH = 32 // 32 bytes = 256 bits
    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(KEY_LENGTH)
        secureRandom.nextBytes(bytes)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "$PREFIX$encoded"
    }
}

