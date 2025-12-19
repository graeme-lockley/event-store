package com.eventstore.interfaces.http.dto

data class CreateApiKeyRequestDto(
    val name: String,
    val description: String? = null,
    val expiresAt: String? = null, // ISO 8601 format
    val scopes: Set<String>? = null
)

data class ApiKeyResponseDto(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val createdAt: String,
    val expiresAt: String?,
    val lastUsedAt: String?,
    val revokedAt: String?,
    val scopes: Set<String>?,
    val isActive: Boolean,
    val key: String? = null // Only included on creation
)

data class ApiKeyListResponseDto(
    val apiKeys: List<ApiKeyResponseDto>
)

data class ApiKeyRevokeResponseDto(
    val message: String,
    val keyId: String,
    val revokedAt: String
)

