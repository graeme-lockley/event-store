package com.eventstore.domain.exceptions

import java.util.UUID

class TenantAlreadyExistsException(tenantName: String) :
    RuntimeException("Tenant '$tenantName' already exists")

class TenantNameNotFoundException(tenantName: String) :
    RuntimeException("Tenant '$tenantName' not found")

class TenantNotFoundException(tenantId: UUID) :
    RuntimeException("Tenant '$tenantId' not found")

class InvalidTenantNameException(tenantName: String, reason: String) :
    RuntimeException("Invalid tenant name '$tenantName': $reason")

class CannotUpdateDeletedTenantException(tenantId: UUID) :
    RuntimeException("Cannot update deleted tenant '$tenantId'")

class QuotaExceededException(
    tenantId: UUID,
    resourceType: String,
    currentUsage: Int,
    requestedQuota: Int
) : RuntimeException(
    "Quota violation for tenant '$tenantId': current $resourceType usage ($currentUsage) exceeds requested quota ($requestedQuota)"
)




