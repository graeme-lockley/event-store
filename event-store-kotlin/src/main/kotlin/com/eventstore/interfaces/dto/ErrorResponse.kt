package com.eventstore.interfaces.dto

data class ErrorResponse(
    val error: String,
    val code: String? = null
)

