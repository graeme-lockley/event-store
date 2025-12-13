package com.eventstore.interfaces.http.dto

data class ErrorResponse(
    val error: String,
    val code: String? = null
)

