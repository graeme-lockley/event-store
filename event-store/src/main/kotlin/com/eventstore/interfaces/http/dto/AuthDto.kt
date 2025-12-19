package com.eventstore.interfaces.http.dto

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val sessionId: String,
    val userId: String,
    val tenants: List<String>
)

data class ChangePasswordRequestDto(
    val oldPassword: String,
    val newPassword: String
)

