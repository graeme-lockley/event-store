package com.eventstore.interfaces.http.dto

data class EventRequest(
    val topic: String,
    val type: String,
    val payload: Map<String, Any>
)

