package com.eventstore.interfaces.dto

data class ConsumerRegistrationRequest(
    val callback: String,
    val topics: Map<String, String?> // topic -> lastEventId
)

