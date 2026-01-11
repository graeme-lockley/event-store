package com.eventstore.domain.tenants

object SystemTopics {
    const val SYSTEM_TENANT_NAME = "\$system"
    const val MANAGEMENT_NAMESPACE_NAME = "\$management"

    const val TENANTS_TOPIC_NAME = "tenants"
    const val NAMESPACES_TOPIC_NAME = "namespaces"
    const val USERS_TOPIC_NAME = "users"
    const val PERMISSIONS_TOPIC_NAME = "permissions"
    const val API_KEYS_TOPIC_NAME = "api-keys"

    fun qualified(topicName: String): String = "$SYSTEM_TENANT_NAME/$MANAGEMENT_NAMESPACE_NAME/$topicName"
}




