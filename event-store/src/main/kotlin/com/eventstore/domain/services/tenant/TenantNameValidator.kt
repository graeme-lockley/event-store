package com.eventstore.domain.services.tenant

import com.eventstore.domain.exceptions.InvalidTenantNameException
import com.eventstore.domain.tenants.SystemTopics

object TenantNameValidator {
    private val VALID_NAME_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9-]{0,62}[a-zA-Z0-9]$")
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 64

    fun validate(tenantName: String) {
        when {
            tenantName.isBlank() -> throw InvalidTenantNameException(
                tenantName,
                "Tenant name cannot be blank"
            )
            tenantName.length < MIN_LENGTH -> throw InvalidTenantNameException(
                tenantName,
                "Tenant name must be at least $MIN_LENGTH characters long"
            )
            tenantName.length > MAX_LENGTH -> throw InvalidTenantNameException(
                tenantName,
                "Tenant name must be at most $MAX_LENGTH characters long"
            )
            tenantName.equals(SystemTopics.SYSTEM_TENANT_NAME, ignoreCase = true) -> throw InvalidTenantNameException(
                tenantName,
                "Tenant name '$tenantName' is reserved for system operations"
            )
            !VALID_NAME_PATTERN.matches(tenantName) -> throw InvalidTenantNameException(
                tenantName,
                "Tenant name must contain only alphanumeric characters and hyphens, " +
                        "must start and end with alphanumeric characters, " +
                        "and match pattern: ^[a-zA-Z0-9][a-zA-Z0-9-]{0,62}[a-zA-Z0-9]$"
            )
        }
    }
}
