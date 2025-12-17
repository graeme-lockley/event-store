package com.eventstore.domain

import java.time.Instant

data class Namespace(
    val tenantId: String,
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(tenantId.isNotBlank()) { "Tenant ID is required" }
        require(id.isNotBlank()) { "Namespace ID is required" }
        require(name.isNotBlank()) { "Namespace name is required" }
    }

    val isActive: Boolean
        get() = deletedAt == null

    fun qualifiedName(): String = "$tenantId/$id"
}

