package com.eventstore.interfaces.http.dto

data class TopicResponse(
    val name: String,
    val sequence: Long,
    val schemas: List<SchemaDto>
)

data class TopicsResponse(
    val topics: List<TopicResponse>
)

