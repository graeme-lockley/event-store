package com.eventstore.interfaces.dto

data class TopicResponse(
    val name: String,
    val sequence: Long,
    val schemas: List<SchemaDto>
)

data class TopicsResponse(
    val topics: List<TopicResponse>
)

