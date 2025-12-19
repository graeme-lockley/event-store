package com.eventstore.domain

import java.time.Instant

data class User(
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val lastLoginAt: Instant? = null,
    val emailVerified: Boolean = false,
    val primaryTenantId: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
    PENDING_ACTIVATION
}

data class UserTenantAssociation(
    val userId: String,
    val tenantId: String,
    val role: String? = null,
    val assignedAt: Instant,
    val assignedBy: String,
    val isPrimary: Boolean = false
)

