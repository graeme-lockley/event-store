package com.eventstore.domain.tenants

import java.util.UUID

object SystemTopics {
    const val SYSTEM_TENANT_NAME = "\$system"
    val SYSTEM_TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    const val MANAGEMENT_NAMESPACE_NAME = "\$management"
    val MANAGEMENT_NAMESPACE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    const val TENANTS_TOPIC_NAME = "tenants"
    val TENANTS_TOPIC_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
    const val NAMESPACES_TOPIC_NAME = "namespaces"
    val NAMESPACES_TOPIC_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
    const val USERS_TOPIC_NAME = "users"
    val USERS_TOPIC_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")
    const val PERMISSIONS_TOPIC_NAME = "permissions"
    val PERMISSIONS_TOPIC_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000013")
    const val API_KEYS_TOPIC_NAME = "api-keys"
    val API_KEYS_TOPIC_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000014")
}




