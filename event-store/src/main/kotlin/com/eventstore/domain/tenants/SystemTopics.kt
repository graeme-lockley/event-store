package com.eventstore.domain.tenants

object SystemTopics {
    const val SYSTEM_TENANT_ID = "\$system"
    const val MANAGEMENT_NAMESPACE_ID = "\$management"

    const val TENANTS_TOPIC = "tenants"
    const val NAMESPACES_TOPIC = "namespaces"
    const val USERS_TOPIC = "users"
    const val PERMISSIONS_TOPIC = "permissions"
    const val API_KEYS_TOPIC = "api-keys"

    fun qualified(topicName: String): String = "$SYSTEM_TENANT_ID/$MANAGEMENT_NAMESPACE_ID/$topicName"
}

