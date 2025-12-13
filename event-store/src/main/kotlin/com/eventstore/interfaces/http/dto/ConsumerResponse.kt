package com.eventstore.interfaces.http.dto

data class ConsumerResponse(
    val id: String,
    val callback: String,
    val topics: Map<String, String?>
)

data class ConsumersResponse(
    val consumers: List<ConsumerResponse>
)

data class ConsumerRegistrationResponse(
    val consumerId: String
)

