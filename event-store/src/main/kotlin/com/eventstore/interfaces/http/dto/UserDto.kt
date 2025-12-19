package com.eventstore.interfaces.http.dto

data class UserCreateRequest(
    val email: String,
    val name: String,
    val password: String,
    val metadata: Map<String, Any> = emptyMap(),
    val primaryTenantId: String? = null
)

data class UserUpdateRequest(
    val email: String? = null,
    val name: String? = null,
    val metadata: Map<String, Any>? = null
)

data class AssignUserTenantRequest(
    val tenantId: String,
    val role: String? = null,
    val isPrimary: Boolean = false
)

data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String?,
    val lastLoginAt: String?,
    val emailVerified: Boolean,
    val primaryTenantId: String?,
    val metadata: Map<String, Any>
)

data class UserListResponse(
    val users: List<UserResponse>
)

