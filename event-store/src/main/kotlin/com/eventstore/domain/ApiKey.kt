package com.eventstore.domain

import java.time.Instant

data class ApiKey(
    val id: String,
    val userId: String,
    val keyHash: String, // Hashed API key (never store plain key)
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val scopes: Set<String>? = null // Optional scoping
) {
    val isActive: Boolean
        get() = revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()))
}

