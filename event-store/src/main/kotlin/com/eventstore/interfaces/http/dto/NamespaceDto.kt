package com.eventstore.interfaces.http.dto

data class NamespaceCreateRequest(
    val id: String,
    val name: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class NamespaceUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val metadata: Map<String, Any>? = null
)

data class NamespaceResponse(
    val tenantId: String,
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: String,
    val updatedAt: String?,
    val deletedAt: String?,
    val metadata: Map<String, Any>
)

data class NamespaceListResponse(
    val namespaces: List<NamespaceResponse>
)

