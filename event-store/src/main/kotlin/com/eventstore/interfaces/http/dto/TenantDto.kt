package com.eventstore.interfaces.http.dto

data class QuotaDto(
    val maxTopics: Int = 100,
    val maxNamespaces: Int = 50,
    val maxEventsPerDay: Long = 1_000_000,
    val maxConsumers: Int = 100,
    val maxUsers: Int = 50,
    val maxEventSizeBytes: Long = 1024 * 1024
)

data class TenantCreateRequest(
    val id: String,
    val name: String,
    val quota: QuotaDto? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class TenantUpdateRequest(
    val name: String? = null,
    val quota: QuotaDto? = null,
    val metadata: Map<String, Any>? = null
)

data class TenantResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String?,
    val deletedAt: String?,
    val quota: QuotaDto?,
    val metadata: Map<String, Any>
)

data class TenantListResponse(
    val tenants: List<TenantResponse>
)

