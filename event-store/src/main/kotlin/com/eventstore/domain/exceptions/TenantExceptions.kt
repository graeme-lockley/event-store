package com.eventstore.domain.exceptions

import java.util.UUID

class TenantAlreadyExistsException(tenantName: String) :
    RuntimeException("Tenant '$tenantName' already exists")

class TenantNameNotFoundException(tenantName: String) :
    RuntimeException("Tenant '$tenantName' not found")

class TenantNotFoundException(tenantId: UUID) :
    RuntimeException("Tenant '$tenantId' not found")




