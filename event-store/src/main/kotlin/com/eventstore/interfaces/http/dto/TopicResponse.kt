package com.eventstore.interfaces.http.dto

data class TopicResponse(
    val topicId: String,
    val namespaceId: String,
    val name: String,
    val sequence: Long,
    val schemas: List<SchemaDto>
)

data class TopicsResponse(
    val topics: List<TopicResponse>
)

