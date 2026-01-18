package com.eventstore.interfaces.http.dto

data class EventRequest(
    val topicId: String,
    val type: String,
    val payload: Map<String, Any>
)

