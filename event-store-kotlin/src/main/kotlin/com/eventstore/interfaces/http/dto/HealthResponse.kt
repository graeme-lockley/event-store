package com.eventstore.interfaces.http.dto

data class HealthResponse(
    val status: String,
    val consumers: Int,
    val runningDispatchers: List<String>
)

